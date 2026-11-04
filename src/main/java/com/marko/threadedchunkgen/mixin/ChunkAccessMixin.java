package com.marko.threadedchunkgen.mixin;

import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Supplier;

@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin {

    @Shadow
    private BiomeGenerationSettings carverBiomeSettings;

    /**
     * @author Marko
     * @reason Synchronized carverBiome calculation for thread safety across chunk generation threads
     */
    @Overwrite
    public synchronized BiomeGenerationSettings carverBiome(Supplier<BiomeGenerationSettings> supplier) {
        if (this.carverBiomeSettings == null) {
            this.carverBiomeSettings = supplier.get();
        }
        return this.carverBiomeSettings;
    }
}
