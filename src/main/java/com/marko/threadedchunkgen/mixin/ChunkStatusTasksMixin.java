package com.marko.threadedchunkgen.mixin;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksMixin {

    @Unique
    private static final ReentrantLock[] FEATURE_LOCKS = Stream
            .generate(ReentrantLock::new)
            .limit(4096)
            .toArray(ReentrantLock[]::new);

    @Unique
    private static final int MAX_TRYLOCK_ATTEMPTS = 5;

    /**
     * @author Marko
     * @reason Offload structure generation to background worker pool
     */
    @Overwrite
    static CompletableFuture<ChunkAccess> generateStructureStarts(
            WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        return CompletableFuture.supplyAsync(() -> {
            ServerLevel serverLevel = context.level();
            if (serverLevel.getServer().getWorldData().worldGenOptions().generateStructures()) {
                context.generator().createStructures(
                        serverLevel.registryAccess(),
                        serverLevel.getChunkSource().getGeneratorState(),
                        serverLevel.structureManager(),
                        chunk,
                        context.structureManager()
                );
            }
            serverLevel.onStructureStartsAvailable(chunk);
            return chunk;
        }, net.minecraft.Util.backgroundExecutor());
    }

    /**
     * @author Marko
     * @reason Offload structure references to background worker pool
     */
    @Overwrite
    static CompletableFuture<ChunkAccess> generateStructureReferences(
            WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        return CompletableFuture.supplyAsync(() -> {
            ServerLevel serverLevel = context.level();
            WorldGenRegion region = new WorldGenRegion(serverLevel, cache, step, chunk);
            context.generator().createReferences(region, serverLevel.structureManager().forWorldGenRegion(region), chunk);
            return chunk;
        }, net.minecraft.Util.backgroundExecutor());
    }

    /**
     * @author Marko
     * @reason Offload surface generation to background worker pool
     */
    @Overwrite
    static CompletableFuture<ChunkAccess> generateSurface(
            WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        return CompletableFuture.supplyAsync(() -> {
            ServerLevel serverLevel = context.level();
            WorldGenRegion region = new WorldGenRegion(serverLevel, cache, step, chunk);
            context.generator().buildSurface(
                    region,
                    serverLevel.structureManager().forWorldGenRegion(region),
                    serverLevel.getChunkSource().randomState(),
                    chunk
            );
            return chunk;
        }, net.minecraft.Util.backgroundExecutor());
    }

    /**
     * @author Marko
     * @reason Offload cave carvers to background worker pool
     */
    @Overwrite
    static CompletableFuture<ChunkAccess> generateCarvers(
            WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        return CompletableFuture.supplyAsync(() -> {
            ServerLevel serverLevel = context.level();
            WorldGenRegion region = new WorldGenRegion(serverLevel, cache, step, chunk);
            if (chunk instanceof ProtoChunk protoChunk) {
                Blender.addAroundOldChunksCarvingMaskFilter(region, protoChunk);
            }
            context.generator().applyCarvers(
                    region,
                    serverLevel.getSeed(),
                    serverLevel.getChunkSource().randomState(),
                    serverLevel.getBiomeManager(),
                    serverLevel.structureManager().forWorldGenRegion(region),
                    chunk,
                    GenerationStep.Carving.AIR
            );
            return chunk;
        }, net.minecraft.Util.backgroundExecutor());
    }

    /**
     * @author Marko
     * @reason Parallel feature generation using non-blocking 3x3 striped neighborhood try-locks
     */
    @Overwrite
    static CompletableFuture<ChunkAccess> generateFeatures(
            WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        CompletableFuture<ChunkAccess> future = new CompletableFuture<>();
        scheduleFeatureGeneration(context, step, cache, chunk, future, 0);
        return future;
    }

    @Unique
    private static void scheduleFeatureGeneration(
            WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
            CompletableFuture<ChunkAccess> future, int attempts
    ) {
        net.minecraft.Util.backgroundExecutor().execute(() -> {
            ChunkPos chunkPos = chunk.getPos();
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

            int[] locks = new int[distinctCount];
            int dIdx = 0;
            for (int i = 0; i < rawLocks.length; i++) {
                if (i == 0 || rawLocks[i] != rawLocks[i - 1]) {
                    locks[dIdx++] = rawLocks[i];
                }
            }

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
                net.minecraft.Util.backgroundExecutor().execute(() ->
                        scheduleFeatureGeneration(context, step, cache, chunk, future, attempts + 1)
                );
                return;
            }

            try {
                ServerLevel serverLevel = context.level();
                Heightmap.primeHeightmaps(
                        chunk,
                        EnumSet.of(
                                Heightmap.Types.MOTION_BLOCKING,
                                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                Heightmap.Types.OCEAN_FLOOR,
                                Heightmap.Types.WORLD_SURFACE
                        )
                );
                WorldGenRegion region = new WorldGenRegion(serverLevel, cache, step, chunk);
                context.generator().applyBiomeDecoration(region, chunk, serverLevel.structureManager().forWorldGenRegion(region));
                Blender.generateBorderTicks(region, chunk);
                future.complete(chunk);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            } finally {
                for (int i = acquired - 1; i >= 0; i--) {
                    FEATURE_LOCKS[locks[i]].unlock();
                }
            }
        });
    }
}
