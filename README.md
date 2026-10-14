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


## Custom waypoint tracking (v1.1.0)
Open settings with the grave/backtick key and select **Custom waypoints**. Click **Create waypoint**, enter its name and integer X/Y/Z coordinates, cycle through Overworld/Nether/End, then **Save and track waypoint**. The default example is My Base (1250, 64, -830) in the Overworld; it is a form example, not pre-saved. Saved waypoints are listed per world/server, selectable one at a time and removable with Delete. **Resume death** switches back without deleting waypoints or death history. Cross-dimensional target guidance tells you to change dimension and optionally points to known portals. No commands or cheats required.

**Build caveat:** this is an updated source project for GitHub Actions, not a locally compile-tested playable JAR. Build verification checks the Fabric metadata and main entrypoint, not in-game behavior.
