# ThreadedChunkGen

A lightweight, server-side mod that parallelizes Minecraft world generation stages across CPU worker threads for dramatically faster chunk generation and smoother exploration.

## Features

- **Parallel Worldgen Stages:** Offloads `STRUCTURE_STARTS`, `STRUCTURE_REFERENCES`, `SURFACE`, `CARVERS`, and `FEATURES` onto `Util.backgroundExecutor()`.
- **Deadlock-Free 3×3 Spatial Locking:** Feature generation uses a fixed 4,096-bucket striped `ReentrantLock` array with globally sorted 3×3 chunk lock ordering and non-blocking `tryLock()` retries, preventing cross-chunk race conditions and deadlocks.
- **Modpack Friendly:** Tested with large Forge 1.20.1 modpacks including All the Mods 9 (ATM9).
- **Server-Side Only (Client-Optional):** Only required on the server (or in singleplayer for the integrated server). Vanilla or unmodded clients can join without installing it.

## Branches

- `main` — NeoForge **1.21.1**
- `1.20.1` — Forge **1.20.1**

## Building from Source

Requires **Java 17** (for `1.20.1` / Forge):

```bash
./gradlew build
```

The compiled JAR will be output to `build/libs/threadedchunkgen-forge-1.20.1-<version>.jar`.
