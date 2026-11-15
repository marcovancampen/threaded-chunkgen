package com.marko.threadedchunkgen.feature;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public final class ParallelFeatureExecutor {

    private static final ReentrantLock[] FEATURE_LOCKS = Stream
            .generate(ReentrantLock::new)
            .limit(4096)
            .toArray(ReentrantLock[]::new);

    private static final int MAX_TRYLOCK_ATTEMPTS = 5;

    private ParallelFeatureExecutor() {}

    public static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> generateStructureStarts(
            ChunkStatus status,
            ServerLevel level,
            ChunkGenerator generator,
            StructureTemplateManager structureManager,
            List<ChunkAccess> chunks
    ) {
        return runSimpleAsync(status, level, chunks, chunk -> {
            if (level.getServer().getWorldData().worldGenOptions().generateStructures()) {
                generator.createStructures(
                        level.registryAccess(),
                        level.getChunkSource().getGeneratorState(),
                        level.structureManager(),
                        chunk,
                        structureManager
                );
            }
            level.onStructureStartsAvailable(chunk);
        });
    }

    public static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> generateStructureReferences(
            ChunkStatus status,
            ServerLevel level,
            ChunkGenerator generator,
            List<ChunkAccess> chunks
    ) {
        return runSimpleAsync(status, level, chunks, chunk -> {
            WorldGenRegion region = new WorldGenRegion(level, chunks, status, -1);
            generator.createReferences(region, level.structureManager().forWorldGenRegion(region), chunk);
        });
    }

    public static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> generateSurface(
            ChunkStatus status,
            ServerLevel level,
            ChunkGenerator generator,
            List<ChunkAccess> chunks
    ) {
        return runSimpleAsync(status, level, chunks, chunk -> {
            WorldGenRegion region = new WorldGenRegion(level, chunks, status, 0);
            generator.buildSurface(
                    region,
                    level.structureManager().forWorldGenRegion(region),
                    level.getChunkSource().randomState(),
                    chunk
            );
        });
    }

    public static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> generateCarvers(
            ChunkStatus status,
            ServerLevel level,
            ChunkGenerator generator,
            List<ChunkAccess> chunks
    ) {
        return runSimpleAsync(status, level, chunks, chunk -> {
            WorldGenRegion region = new WorldGenRegion(level, chunks, status, 0);
            if (chunk instanceof ProtoChunk protoChunk) {
                Blender.addAroundOldChunksCarvingMaskFilter(region, protoChunk);
            }
            generator.applyCarvers(
                    region,
                    level.getSeed(),
                    level.getChunkSource().randomState(),
                    level.getBiomeManager(),
                    level.structureManager().forWorldGenRegion(region),
                    chunk,
                    GenerationStep.Carving.AIR
            );
        });
    }

    private static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> runSimpleAsync(
            ChunkStatus status,
            ServerLevel level,
            List<ChunkAccess> chunks,
            java.util.function.Consumer<ChunkAccess> work
    ) {
        ChunkAccess chunk = chunks.get(chunks.size() / 2);
        return CompletableFuture.supplyAsync(() -> {
            ProfiledDuration profiledDuration = JvmProfiler.INSTANCE.onChunkGenerate(chunk.getPos(), level.dimension(), status.toString());
            try {
                work.accept(chunk);
                if (chunk instanceof ProtoChunk protoChunk) {
                    if (!protoChunk.getStatus().isOrAfter(status)) {
                        protoChunk.setStatus(status);
                    }
                }
                return Either.<ChunkAccess, ChunkHolder.ChunkLoadingFailure>left(chunk);
            } finally {
                if (profiledDuration != null) {
                    profiledDuration.finish();
                }
            }
        }, net.minecraft.Util.backgroundExecutor());
    }

    public static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> generateFeatures(
            ChunkStatus status,
            Executor executor,
            ServerLevel level,
            ChunkGenerator generator,
            StructureTemplateManager structureManager,
            ThreadedLevelLightEngine lightEngine,
            List<ChunkAccess> chunks
    ) {
        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = new CompletableFuture<>();
        scheduleFeatureGeneration(status, executor, level, generator, structureManager, lightEngine, chunks, null, future, 0);
        return future;
    }

    private static int[] computeLocks(ChunkPos chunkPos) {
        int[] rawLocks = new int[9];
        int idx = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                rawLocks[idx++] = ChunkPos.hash(chunkPos.x + dx, chunkPos.z + dz) & (FEATURE_LOCKS.length - 1);
            }
        }
        Arrays.sort(rawLocks);

        int distinctCount = 0;
        for (int i = 0; i < rawLocks.length; i++) {
            if (i == 0 || rawLocks[i] != rawLocks[i - 1]) {
                distinctCount++;
            }
        }

        if (distinctCount == rawLocks.length) {
            return rawLocks;
        }

        int[] locks = new int[distinctCount];
        int dIdx = 0;
        for (int i = 0; i < rawLocks.length; i++) {
            if (i == 0 || rawLocks[i] != rawLocks[i - 1]) {
                locks[dIdx++] = rawLocks[i];
            }
        }
        return locks;
    }

    private static void scheduleFeatureGeneration(
            ChunkStatus status,
            Executor executor,
            ServerLevel level,
            ChunkGenerator generator,
            StructureTemplateManager structureManager,
            ThreadedLevelLightEngine lightEngine,
            List<ChunkAccess> chunks,
            int[] cachedLocks,
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future,
            int attempts
    ) {
        net.minecraft.Util.backgroundExecutor().execute(() -> {
            ChunkAccess chunk = chunks.get(chunks.size() / 2);
            ChunkPos chunkPos = chunk.getPos();
            int[] locks = cachedLocks != null ? cachedLocks : computeLocks(chunkPos);

            int acquired = 0;
            boolean allAcquired = true;
            for (int i = 0; i < locks.length; i++) {
                if (attempts < MAX_TRYLOCK_ATTEMPTS) {
                    if (FEATURE_LOCKS[locks[i]].tryLock()) {
                        acquired++;
                    } else {
                        allAcquired = false;
                        break;
                    }
                } else {
                    FEATURE_LOCKS[locks[i]].lock();
                    acquired++;
                }
            }

            if (!allAcquired) {
                for (int i = acquired - 1; i >= 0; i--) {
                    FEATURE_LOCKS[locks[i]].unlock();
                }
                scheduleFeatureGeneration(status, executor, level, generator, structureManager, lightEngine, chunks, locks, future, attempts + 1);
                return;
            }

            ProfiledDuration profiledDuration = JvmProfiler.INSTANCE.onChunkGenerate(chunkPos, level.dimension(), status.toString());
            try {
                Heightmap.primeHeightmaps(
                        chunk,
                        EnumSet.of(
                                Heightmap.Types.MOTION_BLOCKING,
                                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                Heightmap.Types.OCEAN_FLOOR,
                                Heightmap.Types.WORLD_SURFACE
                        )
                );
                WorldGenRegion region = new WorldGenRegion(level, chunks, status, 1);
                generator.applyBiomeDecoration(region, chunk, level.structureManager().forWorldGenRegion(region));
                Blender.generateBorderTicks(region, chunk);
                if (chunk instanceof ProtoChunk protoChunk) {
                    if (!protoChunk.getStatus().isOrAfter(status)) {
                        protoChunk.setStatus(status);
                    }
                }
                future.complete(Either.left(chunk));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            } finally {
                if (profiledDuration != null) {
                    profiledDuration.finish();
                }
                for (int i = acquired - 1; i >= 0; i--) {
                    FEATURE_LOCKS[locks[i]].unlock();
                }
            }
        });
    }
}
