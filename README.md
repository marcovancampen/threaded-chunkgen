# ThreadedChunkGen

A lightweight, server-side mod that parallelizes Minecraft world generation stages across CPU worker threads for faster chunk generation.

## Features

- **Parallel Worldgen Stages:** Offloads `STRUCTURE_STARTS`, `SURFACE`, `CARVERS`, and `FEATURES` from the serial `"worldgen"` mailbox onto `Util.backgroundExecutor()`, while keeping `STRUCTURE_REFERENCES` synchronous to avoid scheduling overhead.
- **Deadlock-Free 3×3 Spatial Locking:** Feature generation uses a fixed 4,096 bucket `ReentrantLock` array with globally sorted 3×3 chunk lock ordering and non-blocking `tryLock()` retries, preventing cross-chunk race conditions and deadlocks.
- **Modpack Friendly:** Uses MixinExtras `@WrapMethod` with zero `@Overwrite`s, preserving compatibility with ModernFix, Terralith, Biomes O' Plenty, Oh The Biomes We've Gone, and large modpacks like All the Mods 10.
- **Server-Side Only (Client-Optional):** Only required on the server (or in singleplayer for the integrated server).

## Branches

- `main` — NeoForge **1.21.1**
- `1.20.1` — Forge **1.20.1**

## Recommended JVM Flags (Heavy Modpacks)

Multi-threaded chunk generation increases allocation throughput on worker threads. If you are running large modpacks (such as ATM10) on **Java 21**, Generational ZGC is recommended to eliminate multi-second Old Gen GC pauses:

```text
-XX:+UseZGC -XX:+ZGenerational -XX:+AlwaysPreTouch -XX:+DisableExplicitGC
```

## Building from Source

Requires **Java 21** (for `main` / NeoForge 1.21.1):

```bash
./gradlew build
```

The compiled JAR will be output to `build/libs/threadedchunkgen-neoforge-1.21.1-<version>.jar`.
