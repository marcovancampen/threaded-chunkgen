package com.marko.threadedchunkgen.mixin;

import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mixin(ChunkTaskPriorityQueueSorter.class)
public abstract class ChunkTaskPriorityQueueSorterMixin {

    @Unique
    private ExecutorService threadedchunkgen$sorterExecutor;

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/thread/ProcessorMailbox;<init>(Lnet/minecraft/util/thread/StrictQueue;Ljava/util/concurrent/Executor;Ljava/lang/String;)V"
            ),
            index = 1
    )
    private Executor threadedchunkgen$redirectSorterExecutor(Executor original) {
        this.threadedchunkgen$sorterExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Chunk-Priority-Sorter");
            thread.setDaemon(true);
            return thread;
        });
        return this.threadedchunkgen$sorterExecutor;
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void threadedchunkgen$onClose(CallbackInfo ci) {
        if (this.threadedchunkgen$sorterExecutor != null) {
            this.threadedchunkgen$sorterExecutor.shutdown();
        }
    }
}
