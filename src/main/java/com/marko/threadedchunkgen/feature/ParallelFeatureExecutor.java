package com.marko.threadedchunkgen.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public final class ParallelFeatureExecutor {

    private static final ReentrantLock[] FEATURE_LOCKS = Stream
            .generate(ReentrantLock::new)
            .limit(4096)
            .toArray(ReentrantLock[]::new);

    private static final int MAX_TRYLOCK_ATTEMPTS = 5;

    private ParallelFeatureExecutor() {}

    public static CompletableFuture<ChunkAccess> runAsyncTask(
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            Operation<CompletableFuture<ChunkAccess>> original
    ) {
        CompletableFuture<ChunkAccess> future = new CompletableFuture<>();
        net.minecraft.Util.backgroundExecutor().execute(() -> {
            try {
                CompletableFuture<ChunkAccess> result = original.call(context, step, cache, chunk);
                if (result.isDone()) {
                    future.complete(result.join());
                } else {
                    result.whenComplete((res, err) -> {
                        if (err != null) {
                            future.completeExceptionally(err);
                        } else {
                            future.complete(res);
                        }
                    });
                }
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public static CompletableFuture<ChunkAccess> generateFeatures(
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            Operation<CompletableFuture<ChunkAccess>> original
    ) {
        CompletableFuture<ChunkAccess> future = new CompletableFuture<>();
        scheduleFeatureGeneration(context, step, cache, chunk, original, null, future, 0);
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
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            Operation<CompletableFuture<ChunkAccess>> original,
            int[] cachedLocks,
            CompletableFuture<ChunkAccess> future,
            int attempts
    ) {
        net.minecraft.Util.backgroundExecutor().execute(() -> {
            int[] locks = cachedLocks != null ? cachedLocks : computeLocks(chunk.getPos());

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
                scheduleFeatureGeneration(context, step, cache, chunk, original, locks, future, attempts + 1);
                return;
            }

            try {
                CompletableFuture<ChunkAccess> result = original.call(context, step, cache, chunk);
                future.complete(result.join());
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

