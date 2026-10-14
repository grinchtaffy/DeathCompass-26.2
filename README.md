# Death Compass v1.3 — Minecraft 26.2 Fabric

A **source project** for Death Compass. It includes death history, a top-screen compass,
whole-block distance, custom waypoints, cross-dimensional directions, known portals,
block search, heuristic structure clues, stable target lock, and safety warnings.

## New in v1.3

### Secondary Keybinds (ported behavior from user-supplied SecondaryKeybind 1.1.0 for 1.21.11)

- Bind a second keyboard key **or mouse button** for vanilla and modded `KeyMapping`s.
- Original controls continue to work; editing secondary bindings does not rewrite `options.txt`.
- Press **K** to open the separate secondary-keybind editor, or use **Options > Controls > Secondary Keybinds**, or **Death Compass Settings > Secondary keybinds**.
- Search by mapping name/category; view **and edit original or secondary** keys, see a conflict marker, reset each secondary bind, or confirm Reset All.
- On capture: press **Esc** to cancel, **Backspace/Delete** to clear, any other keyboard/mouse button to assign.
- Saved globally to `config/deathcompass-secondary-keys.json`.
- Uses Fabric Mixin hooks into 26.2 `KeyMapping` input and match methods.

**Note:** This is a newly implemented equivalent workflow, not a byte-identical copy of the original mod's menu or full compatibility guarantee.

### Audible hazard alerts

- **Safety > Warning sounds & volume** lets you toggle sounds globally or independently for lava, falling blocks, hostile mobs, and other urgent dangers.
- Volume configurable 0–100%; alarm interval 1–30 seconds (default 4 seconds); immediate warning changes are debounced 1 second.
- Plays a local note-block bell for **urgent** HUD warnings, not all ambient notices.
- All environmental predictions use loaded client-side data. Hazard alerts are not infallible.

### Finder limitations

- Finder scans **loaded** blocks within your chosen radius; never ungenerated chunks.
- Structure marks are **clues**, not exact boundaries or proof of existence.
- Finder locks its target and only releases when the target changes/disappears, dimensions change, or you move farther than the release distance.

## Build using GitHub Actions

1. Extract the ZIP and upload all root files, `.github/`, `src/` and `scripts/` to your GitHub repository.
2. Commit & push. The included `.github/workflows/build.yml` will attempt a Java 25 / Gradle 9.5.1 Fabric build.
3. A green build with passing JAR verification uploads the artifact **DeathCompass-VERIFIED-JAR**.
4. Extract the artifact ZIP and install the JAR with the matching Fabric API in Prism Launcher.
5. Test with only essential mods first, then in a disposable world with your modpack.

**Build/test status:** Source created and structurally reviewed; cannot complete a full
Minecraft 26.2 build or in-game test here. GitHub may expose API or Mixin errors.
