
package dev.deathcompass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;

public final class DeathCompass implements ClientModInitializer {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final Path SAVE = FabricLoader.getInstance().getConfigDir().resolve("deathcompass.json");
    public static TrackerData data = new TrackerData();

    // Register the category ONCE. Both keybinds share this instance.
    public static final KeyMapping.Category MAIN_CATEGORY =
        KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("deathcompass", "main")
        );

    private static KeyMapping settingsKey;
    private static String worldKey = "";
    private static boolean wasDead = false;
    private static int ticks = 0;

    @Override
    public void onInitializeClient() {
        load();

        SecondaryKeybinds.initialize();

        settingsKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "key.deathcompass.settings",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_GRAVE,
                MAIN_CATEGORY
            )
        );

        ClientTickEvents.END_CLIENT_TICK.register(DeathCompass::tick);

        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("deathcompass", "tracker"),
            CompassHud::draw
        );
    }

    static void tick(Minecraft mc) {
        while (settingsKey.consumeClick()) {
            if (mc.player != null) {
                mc.gui.setScreen(new SettingsScreen());
            }
        }

        if (mc.player == null || mc.level == null) {
            wasDead = false;
            worldKey = "";
            HazardScanner.reset();
            HazardSounds.reset();
            SecondaryKeybinds.clearHeld();
            return;
        }

        String current = key(mc);

        if (!worldKey.equals(current)) {
            worldKey = current;
            wasDead = false;
            HazardScanner.reset();
        }

        boolean dead = mc.player.isDeadOrDying();

        if (dead && !wasDead) {
            TrackerData.Death d = new TrackerData.Death(
                mc.player.blockPosition().getX(),
                mc.player.blockPosition().getY(),
                mc.player.blockPosition().getZ(),
                dimension(mc)
            );

            TrackerData.WorldRecord w = world();
            w.active = d;
            w.history.add(0, d);

            if (w.history.size() > 100) {
                w.history = w.history.subList(0, 100);
            }

            save();
        }

        wasDead = dead;

        if (++ticks % 40 == 0 && data.enabled && data.portalGuidance) {
            scanVisiblePortals(mc);
        }

        if (data.enabled && (
            "block".equals(world().targetMode) ||
            "structure".equals(world().targetMode)
        )) {
            scanBlocks(mc);
        }

        HazardScanner.tick(mc, current);
        HazardSounds.evaluate(mc);
    }

    static String key(Minecraft mc) {
        if (mc.getCurrentServer() != null) {
            return "server:" + mc.getCurrentServer().ip;
        }

        if (mc.getSingleplayerServer() != null) {
            return "single:" +
                mc.getSingleplayerServer().getWorldData().getLevelName();
        }

        return "other:unknown";
    }

    static String dimension(Minecraft mc) {
        return mc.level.dimension().identifier().toString();
    }

    static TrackerData.WorldRecord world() {
        return data.worlds.computeIfAbsent(
            worldKey,
            k -> new TrackerData.WorldRecord()
        );
    }

    static TrackerData.Death active() {
        return worldKey.isEmpty() ? null : world().active;
    }

    static void endMission() {
        world().active = null;
        world().targetMode = "death";
        save();
    }

    static void trackDeath() {
        world().targetMode = "death";
        save();
    }

    static void trackBlock(String id) {
        TrackerData.WorldRecord w = world();

        w.searchId = id;
        w.targetMode = "block";
        w.targetFound = false;
        w.scanCursor = 0;
        w.scanSignature = "";
        w.targetDimension = "";
        w.scanCandidateFound = false;

        save();
    }

    /**
     * Structure detection is a heuristic for loaded chunks,
     * not a world-seed /locate.
     */
    static void trackStructure(String id) {
        TrackerData.WorldRecord w = world();

        w.searchId = id;
        w.targetMode = "structure";
        w.targetFound = false;
        w.scanCursor = 0;
        w.scanSignature = "";
        w.scanCandidateFound = false;

        save();
    }

    static TrackerData.Waypoint selectedWaypoint() {
        if (worldKey.isEmpty()) return null;

        TrackerData.WorldRecord w = world();

        if (w.waypoints == null) {
            w.waypoints = new java.util.ArrayList<>();
        }

        return w.waypoints.stream()
            .filter(p -> p.id.equals(w.selectedWaypointId))
            .findFirst()
            .orElse(null);
    }

    static void addWaypoint(
        String name,
        int x,
        int y,
        int z,
        String dim
    ) {
        TrackerData.WorldRecord w = world();

        if (w.waypoints == null) {
            w.waypoints = new java.util.ArrayList<>();
        }

        TrackerData.Waypoint p = new TrackerData.Waypoint(
            java.util.UUID.randomUUID().toString(),
            name,
            x,
            y,
            z,
            dim
        );

        w.waypoints.add(p);
        w.selectedWaypointId = p.id;
        w.targetMode = "waypoint";

        save();
    }

    static void chooseWaypoint(String id) {
        TrackerData.WorldRecord w = world();

        w.selectedWaypointId = id;
        w.targetMode = "waypoint";

        save();
    }

    static void deleteWaypoint(String id) {
        TrackerData.WorldRecord w = world();

        if (w.waypoints == null) return;

        w.waypoints.removeIf(p -> p.id.equals(id));

        if (id.equals(w.selectedWaypointId)) {
            w.selectedWaypointId = "";
            w.targetMode = "death";
        }

        save();
    }

    static void stopWaypoint() {
        world().targetMode = "death";
        save();
    }

    static void stopSearch() {
        world().targetMode = "death";
        save();
    }

    // A complete nearby scan picks the closest loaded match exactly
    // once, then locks it.
    // Moving between chunks will NEVER switch a locked target.
    // It releases only when the target is mined/replaced, the
    // dimension changes, or the configured distance is exceeded.
    // No server commands and no unloaded chunk reads.

    static void scanBlocks(Minecraft mc) {
        TrackerData.WorldRecord w = world();

        int r = Math.max(4, Math.min(64, data.scanRadius));
        int release = Math.max(
            32,
            Math.min(512, data.lockReleaseDistance)
        );

        int side = r * 2 + 1;
        int total = side * side * side;

        BlockPos center = mc.player.blockPosition();
        String dim = dimension(mc);

        String signature =
            dim + ":" + w.targetMode + ":" + w.searchId + ":" + r;

        if (w.targetFound) {
            BlockPos saved = new BlockPos(
                w.targetX,
                w.targetY,
                w.targetZ
            );

            double distSq = saved.distSqr(center);

            Boolean stillExists = null;

            // Unloaded blocks are unverified, not automatically lost.
            if (dim.equals(w.targetDimension) &&
                mc.level.hasChunkAt(saved)) {

                String id = BuiltInRegistries.BLOCK.getKey(
                    mc.level.getBlockState(saved).getBlock()
                ).toString();

                stillExists = "block".equals(w.targetMode)
                    ? id.equals(w.searchId)
                    : isStructureClue(w.searchId, id);
            }

            if (!TargetLockPolicy.shouldRelease(
                dim,
                w.targetDimension,
                distSq,
                release,
                stillExists
            )) {
                return;
            }

            w.targetFound = false;
            w.scanSignature = "";
        }

        boolean moved =
            w.scanSignature != null &&
            !w.scanSignature.isEmpty() &&
            (
                Math.abs(center.getX() - w.scanOriginX) >
                    Math.max(8, r / 2) ||
                Math.abs(center.getY() - w.scanOriginY) >
                    Math.max(8, r / 2) ||
                Math.abs(center.getZ() - w.scanOriginZ) >
                    Math.max(8, r / 2)
            );

        if (!signature.equals(w.scanSignature) || moved) {
            w.scanSignature = signature;
            w.scanCursor = 0;

            w.scanOriginX = center.getX();
            w.scanOriginY = center.getY();
            w.scanOriginZ = center.getZ();

            w.scanCandidateDistance = Double.POSITIVE_INFINITY;
            w.scanCandidateFound = false;
        }

        int remaining = Math.max(
            100,
            Math.min(10000, data.scanBudget)
        );

        BlockPos origin = new BlockPos(
            w.scanOriginX,
            w.scanOriginY,
            w.scanOriginZ
        );

        while (remaining-- > 0 && w.scanCursor < total) {
            int index = w.scanCursor++;

            int dx = index % side - r;
            int dy = (index / side) % side - r;
            int dz = index / (side * side) - r;

            BlockPos pos = origin.offset(dx, dy, dz);

            if (pos.getY() < mc.level.getMinY() ||
                pos.getY() >= mc.level.getMaxY() ||
                !mc.level.hasChunkAt(pos)) {
                continue;
            }

            String blockId = BuiltInRegistries.BLOCK.getKey(
                mc.level.getBlockState(pos).getBlock()
            ).toString();

            boolean matched =
                "block".equals(w.targetMode)
                    ? blockId.equals(w.searchId)
                    : isStructureClue(w.searchId, blockId);

            if (!matched) continue;

            double d = pos.distSqr(origin);

            if (d < w.scanCandidateDistance) {
                w.scanCandidateFound = true;
                w.scanCandidateDistance = d;
                w.scanCandidateX = pos.getX();
                w.scanCandidateY = pos.getY();
                w.scanCandidateZ = pos.getZ();
            }
        }

        if (w.scanCursor >= total) {
            if (w.scanCandidateFound) {
                w.targetX = w.scanCandidateX;
                w.targetY = w.scanCandidateY;
                w.targetZ = w.scanCandidateZ;
                w.targetDimension = dim;
                w.targetFound = true;

                // Persist selected target to avoid jumping
                // after exiting and reloading.
                save();
            } else {
                // Refresh after moving significantly.
                w.scanCursor = total;
            }
        }
    }

    static boolean isStructureClue(
        String structure,
        String block
    ) {
        // Not definitive structure detection.
        // Points to distinguishing blocks only.

        return switch (structure) {
            case "minecraft:ancient_city" ->
                block.equals("minecraft:reinforced_deepslate");

            case "minecraft:nether_fortress" ->
                block.equals("minecraft:nether_bricks");

            case "minecraft:bastion_remnant" ->
                block.equals("minecraft:gilded_blackstone");

            case "minecraft:stronghold" ->
                block.equals("minecraft:end_portal_frame");

            case "minecraft:trial_chambers" ->
                block.equals("minecraft:trial_spawner") ||
                block.equals("minecraft:vault");

            case "minecraft:monument" ->
                block.equals("minecraft:dark_prismarine");

            case "minecraft:desert_pyramid" ->
                block.equals("minecraft:chiseled_sandstone");

            case "minecraft:jungle_temple" ->
                block.equals("minecraft:chiseled_stone_bricks");

            case "minecraft:end_city" ->
                block.equals("minecraft:purpur_block") ||
                block.equals("minecraft:end_stone_bricks");

            case "minecraft:woodland_mansion" ->
                block.equals("minecraft:dark_oak_planks");

            case "minecraft:pillager_outpost" ->
                block.equals("minecraft:dark_oak_log");

            case "minecraft:shipwreck" ->
                block.equals("minecraft:oak_planks") ||
                block.equals("minecraft:spruce_planks");

            case "minecraft:ruined_portal" ->
                block.equals("minecraft:crying_obsidian");

            case "minecraft:mineshaft" ->
                block.equals("minecraft:oak_fence") ||
                block.equals("minecraft:rail");

            case "minecraft:village" ->
                block.equals("minecraft:bell");

            case "minecraft:ocean_ruin" ->
                block.equals("minecraft:prismarine");

            default -> false;
        };
    }

    static boolean lavaBeneathAimedBlock(Minecraft mc) {
        if (!data.enabled ||
            !data.lavaWarning ||
            mc.level == null ||
            mc.player == null ||
            !(mc.hitResult instanceof BlockHitResult hit)) {
            return false;
        }

        BlockPos below = hit.getBlockPos().below();

        return mc.level.hasChunkAt(below) &&
            mc.level.getFluidState(below).is(
                net.minecraft.tags.FluidTags.LAVA
            );
    }

    static void setMission(TrackerData.Death d) {
        world().active = d;
        world().targetMode = "death";
        save();
    }

    static void scanVisiblePortals(Minecraft mc) {
        BlockPos p = mc.player.blockPosition();
        TrackerData.WorldRecord w = world();

        String dim = dimension(mc);

        // Only blocks currently loaded near the player.
        for (int x = -12; x <= 12; x += 2) {
            for (int y = -8; y <= 8; y += 2) {
                for (int z = -12; z <= 12; z += 2) {
                    BlockPos b = p.offset(x, y, z);

                    if (!mc.level.hasChunkAt(b)) continue;

                    if (
                        mc.level.getBlockState(b).is(Blocks.NETHER_PORTAL) ||
                        mc.level.getBlockState(b).is(Blocks.END_PORTAL)
                    ) {
                        boolean known = w.portals.stream()
                            .anyMatch(q ->
                                q.dimension.equals(dim) &&
                                Math.abs(q.x - b.getX()) < 12 &&
                                Math.abs(q.z - b.getZ()) < 12
                            );

                        if (!known) {
                            w.portals.add(
                                new TrackerData.Portal(
                                    b.getX(),
                                    b.getY(),
                                    b.getZ(),
                                    dim
                                )
                            );

                            save();
                        }
                    }
                }
            }
        }
    }

    static TrackerData.Portal nearestPortal(Minecraft mc) {
        String dim = dimension(mc);

        double x = mc.player.getX();
        double z = mc.player.getZ();

        return world().portals.stream()
            .filter(p -> p.dimension.equals(dim))
            .min(Comparator.comparingDouble(
                p -> Math.pow(p.x - x, 2) +
                     Math.pow(p.z - z, 2)
            ))
            .orElse(null);
    }

    static void load() {
        try {
            if (Files.exists(SAVE)) {
                TrackerData d = GSON.fromJson(
                    Files.readString(SAVE),
                    TrackerData.class
                );

                if (d != null && d.worlds != null) {
                    data = d;
                }
            }
        } catch (Exception e) {
            System.err.println(
                "[DeathCompass] Could not read config: " + e
            );
        }
    }

    static void save() {
        try {
            Files.createDirectories(SAVE.getParent());

            Files.writeString(
                SAVE,
                GSON.toJson(data),
                StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            System.err.println(
                "[DeathCompass] Could not save config: " + e
            );
        }
    }
}
