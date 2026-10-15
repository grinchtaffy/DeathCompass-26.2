
#!/usr/bin/env python3
"""Reject broken/empty mod JARs before GitHub uploads an artifact."""
import json
import sys
import zipfile

EXPECTED_CLASSES = {
    "dev/deathcompass/DeathCompass.class",
    "dev/deathcompass/FastBlockScanner.class",
    "dev/deathcompass/CompassHud.class",
    "dev/deathcompass/MinimapHud.class",
    "dev/deathcompass/FreecamController.class",
    "dev/deathcompass/SettingsScreen.class",
    "dev/deathcompass/HazardScanner.class",
    "dev/deathcompass/HazardSounds.class",
    "dev/deathcompass/SecondaryKeybinds.class",
    "dev/deathcompass/SecondaryKeybindsScreen.class",
    "dev/deathcompass/mixin/KeyMappingClickAccess.class",
    "dev/deathcompass/mixin/SecondaryKeysMixin.class",
    "dev/deathcompass/mixin/ControlsScreenMixin.class",
}

def verify(path):
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        for name in ["fabric.mod.json", "deathcompass.mixins.json", *EXPECTED_CLASSES]:
            if name not in names:
                raise ValueError(f"Missing required entry in {path}: {name}")
        mod = json.loads(archive.read("fabric.mod.json"))
        mixins = json.loads(archive.read("deathcompass.mixins.json"))
        if mod.get("id") != "deathcompass":
            raise ValueError("Unexpected mod ID")
        if "dev.deathcompass.DeathCompass" not in mod.get("entrypoints", {}).get("client", []):
            raise ValueError("Missing client entrypoint")
        if "deathcompass.mixins.json" not in mod.get("mixins", []):
            raise ValueError("Mixin configuration not registered")
        mixin_package = mixins.get("package", "")
        if mixin_package != "dev.deathcompass.mixin":
            raise ValueError(f"Unsafe mixin package: {mixin_package!r}; expected dev.deathcompass.mixin")
        for entry in mod.get("entrypoints", {}).get("client", []):
            if entry == mixin_package or entry.startswith(mixin_package + "."):
                raise ValueError("Entrypoint is inside mixin package; this will crash Minecraft")
        for name in mixins.get("mixins", []) + mixins.get("client", []) + mixins.get("server", []):
            entry = (mixin_package + "." + name).replace(".", "/") + ".class"
            if entry not in names:
                raise ValueError(f"Mixin config refers to missing class: {entry}")
        for necessary in ("KeyMappingClickAccess", "SecondaryKeysMixin", "ControlsScreenMixin"):
            if necessary not in mixins.get("client", []):
                raise ValueError(f"Missing client mixin {necessary}")
        if not str(mod.get("version", "")).startswith("1.3"):
            raise ValueError("This is not Death Compass 1.3.x; source files may not be uploaded")
    print("PASS: Death Compass 1.3 metadata, Java classes, sounds, and secondary-key mixins present")

if __name__ == "__main__":
    try:
        verify(sys.argv[1])
    except (IndexError, ValueError, OSError, zipfile.BadZipFile) as exc:
        print("FAIL:", exc, file=sys.stderr)
        raise SystemExit(1)
