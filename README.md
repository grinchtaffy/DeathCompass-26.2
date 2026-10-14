# Death Compass 26.2 — Fabric client mod (source, unverified)

Client-side death tracking: records the newest death, retains up to 100 old deaths per server/world, compass heading, horizontal turning degrees, vertical up/down arrows, whole-block distances, dimension instructions, Overworld/Nether coordinate conversion, known portal hints, and a **manual** End Mission button. Open settings with backtick (`), rebindable in Minecraft Controls. HUD size, screen offsets, colors, and components are configurable.

## New: loaded-chunk finder

The Finder settings page lets you enter a block ID such as `minecraft:ancient_debris` (or `ancient_debris`), choose it as the **only** current target, and have the arrows point at the closest detected block, even through walls. Client-side scan checks only currently loaded chunks in a configurable **4–64-block cubic radius**, with scan speed/budget controls. A scan may take several seconds or longer depending on radius and performance. Detection does not work for unloaded chunks, distant areas, or blocks the server has hidden through anti-X-ray chunk obfuscation. Swapping the finder/death mode keeps your death history.

Structure clue mode accepts IDs `ancient_city`, `nether_fortress`, `bastion_remnant`, `stronghold`, `trial_chambers`, `monument`, `desert_pyramid`, `jungle_temple`. It scans for distinctive block types in loaded chunks, **not verified structure boundaries or the actual nearest structure**. Clues may be absent or give false positives. No `/locate`, permissions or cheats are needed.

## Lava safety alert

When your crosshair is pointing at a block and the immediately-below block contains lava fluid in loaded client chunk data, a red **LAVA DIRECTLY UNDER TARGET BLOCK** warning appears near your crosshair. Toggle in Finder settings. This is a proximity warning, **not** a complete hazard detector: it cannot see lava under unloaded/obfuscated data, detect diagonal lava or guarantee mining safety.

## Building

Requires **Java 25**, **Gradle 9.5.1**, and internet access to download Fabric dependencies. Run `gradle build` in the project folder, then take the mod JAR from `build/libs/` and install on the Fabric 26.2 client with Fabric API. This environment lacks Java 25 / Gradle and could not compile or runtime-test the mod. Source uses version-dependent 26.2 APIs and may require compatibility fixes when compiled.

## Other limitations

Known portal mode searches for locally observed portals; it cannot know every portal on the server. Nether coordinate conversion is 8:1; the End has no equivalent. World/server history keys rely on multiplayer address or singleplayer world display name, so singleplayer worlds with duplicate names may share a history. The compass is a minimal direction strip rather than a pixel-perfect vanilla compass item.
