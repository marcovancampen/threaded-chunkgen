package com.marko.threadedchunkgen.mixin;

import com.marko.threadedchunkgen.feature.ParallelFeatureExecutor;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Mixin(ChunkStatus.class)
public abstract class ChunkStatusMixin {

    @Shadow @Final public static ChunkStatus FEATURES;

    @Inject(
            method = "generate",
            at = @At("HEAD"),
            cancellable = true
    )
    private void threadedchunkgen$onGenerate(
            Executor executor,
            ServerLevel level,
            ChunkGenerator generator,
            StructureTemplateManager structureManager,
            ThreadedLevelLightEngine lightEngine,
            Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> taskFunction,
            List<ChunkAccess> chunks,
            CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir
    ) {
        if ((Object) this == FEATURES) {
            cir.setReturnValue(ParallelFeatureExecutor.generateFeatures(
                    FEATURES,
                    executor,
                    level,
                    generator,
                    structureManager,
                    lightEngine,
                    chunks
            ));
        }
    }
}
