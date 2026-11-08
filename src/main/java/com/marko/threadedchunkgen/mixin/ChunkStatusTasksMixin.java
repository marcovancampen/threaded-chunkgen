package com.marko.threadedchunkgen.mixin;

import com.marko.threadedchunkgen.feature.ParallelFeatureExecutor;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksMixin {

    /**
     * @author Marko
     * @reason Multi-threaded feature generation using non-blocking 3x3 striped neighborhood try-locks.
     * All scheduling and lambdas are isolated in ParallelFeatureExecutor to prevent synthetic method collisions.
     */
    @Overwrite
    static CompletableFuture<ChunkAccess> generateFeatures(
            WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk
    ) {
        return ParallelFeatureExecutor.generateFeatures(context, step, cache, chunk);
    }
}
