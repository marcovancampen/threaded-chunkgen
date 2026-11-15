package com.marko.threadedchunkgen.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.marko.threadedchunkgen.feature.ParallelFeatureExecutor;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksMixin {

    @WrapMethod(method = "generateStructureStarts")
    private static CompletableFuture<ChunkAccess> threadedchunkgen$wrapStructureStarts(
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            Operation<CompletableFuture<ChunkAccess>> original
    ) {
        return ParallelFeatureExecutor.runAsyncTask(context, step, cache, chunk, original);
    }

    @WrapMethod(method = "generateSurface")
    private static CompletableFuture<ChunkAccess> threadedchunkgen$wrapSurface(
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            Operation<CompletableFuture<ChunkAccess>> original
    ) {
        return ParallelFeatureExecutor.runAsyncTask(context, step, cache, chunk, original);
    }

    @WrapMethod(method = "generateCarvers")
    private static CompletableFuture<ChunkAccess> threadedchunkgen$wrapCarvers(
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            Operation<CompletableFuture<ChunkAccess>> original
    ) {
        return ParallelFeatureExecutor.runAsyncTask(context, step, cache, chunk, original);
    }

    @WrapMethod(method = "generateFeatures")
    private static CompletableFuture<ChunkAccess> threadedchunkgen$wrapFeatures(
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            Operation<CompletableFuture<ChunkAccess>> original
    ) {
        return ParallelFeatureExecutor.generateFeatures(context, step, cache, chunk, original);
    }
}
