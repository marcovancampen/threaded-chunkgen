package com.marko.threadedchunkgen;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ThreadedChunkGen.MODID)
public class ThreadedChunkGen {
    public static final String MODID = "threadedchunkgen";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ThreadedChunkGen() {
        LOGGER.info("ThreadedChunkGen initialized: Multi-threaded chunk generation mixins ACTIVE.");
    }
}
