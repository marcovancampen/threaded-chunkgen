package com.marko.threadedchunkgen.mixin;

import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkGeneratorStructureState.class)
public abstract class ChunkGeneratorStructureStateMixin {

    @Shadow
    private boolean hasGeneratedPositions;

    @Shadow
    private void generatePositions() {}

    /**
     * @author Marko
     * @reason Thread-safe structure placement calculation via double-checked locking
     */
    @Overwrite
    public void ensureStructuresGenerated() {
        if (!this.hasGeneratedPositions) {
            synchronized (this) {
                if (!this.hasGeneratedPositions) {
                    this.generatePositions();
                    this.hasGeneratedPositions = true;
                }
            }
        }
    }
}

