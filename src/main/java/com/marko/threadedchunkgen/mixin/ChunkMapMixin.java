package com.marko.threadedchunkgen.mixin;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.ProcessorMailbox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Shadow
    @Final
    ServerLevel level;

    @Unique
    private ExecutorService threadedchunkgen$lightExecutor;

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/thread/ProcessorMailbox;create(Ljava/util/concurrent/Executor;Ljava/lang/String;)Lnet/minecraft/util/thread/ProcessorMailbox;"
            )
    )
    private ProcessorMailbox<Runnable> threadedchunkgen$createMailbox(Executor executor, String name) {
        if ("light".equals(name)) {
            String dimensionPath = (this.level != null && this.level.dimension() != null)
                    ? this.level.dimension().location().getPath()
                    : "unknown";
            this.threadedchunkgen$lightExecutor = Executors.newSingleThreadExecutor(
                    new ThreadFactoryBuilder()
                            .setNameFormat("Chunk-Light-Engine-" + dimensionPath)
                            .setDaemon(true)
                            .build()
            );
            return ProcessorMailbox.create(this.threadedchunkgen$lightExecutor, name);
        }
        return ProcessorMailbox.create(executor, name);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void threadedchunkgen$onClose(CallbackInfo ci) {
        if (this.threadedchunkgen$lightExecutor != null) {
            this.threadedchunkgen$lightExecutor.shutdown();
        }
    }
}

