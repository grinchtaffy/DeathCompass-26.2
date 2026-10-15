
package dev.deathcompass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Original, lightweight, top-down terrain minimap. No textures from other mods,
 * no packets, no world-seed queries. Reads only loaded columns on the client.
 */
public final class MinimapHud {
    private MinimapHud() {}
    private static final int GRID = 31;
    private static final int[][] tiles = new int[GRID][GRID];
    private static Object cachedLevel;
    private static int cachedX = Integer.MIN_VALUE, cachedZ = Integer.MIN_VALUE;
    private static int cachedZoom, cacheTicks;
    private static boolean cachedNorth;
    private static int cachedYaw;

    public static void invalidate() { cachedLevel = null; cacheTicks = 0; }

    public static void draw(GuiGraphicsExtractor g, DeltaTracker dt) {
        var config = DeathCompass.data;
        Minecraft mc = Minecraft.getInstance();
        if (!config.enabled || !config.minimapEnabled || !config.minimapVisible
                || mc.level == null || mc.player == null) return;

        int size = Math.max(92, Math.min(164, config.minimapSize));
        int left = g.guiWidth() - size - 9 + config.minimapXOffset;
        int top = 8 + config.minimapYOffset;
        int mid = size / 2;
        int zoom = Math.max(1, Math.min(8, config.minimapZoom));
        double angle = config.minimapNorthUp ? 0 : Math.toRadians(180 - mc.player.getYRot());
        double cosine = Math.cos(angle), sine = Math.sin(angle);
        int px = mc.player.blockPosition().getX(), pz = mc.player.blockPosition().getZ();

        if (config.minimapTerrain) {
            if (cachedLevel != mc.level || cachedX != px || cachedZ != pz || cachedZoom != zoom
                    || cachedNorth != config.minimapNorthUp || (!config.minimapNorthUp && Math.abs(net.minecraft.util.Mth.wrapDegrees(cachedYaw - mc.player.getYRot())) > 8) || ++cacheTicks >= 20) {
                rebuild(mc, px, pz, zoom, cosine, sine);
                cachedLevel = mc.level;
                cachedX = px; cachedZ = pz;
                cachedZoom = zoom; cachedNorth = config.minimapNorthUp; cachedYaw = (int) mc.player.getYRot();
                cacheTicks = 0;
            }
        }
        g.fill(left - 3, top - 3, left + size + 3, top + size + 3, 0xC8081019);
        for (int z = 0; z < GRID; z++) {
            for (int x = 0; x < GRID; x++) {
                if (config.minimapCircle && (x - 15)*(x - 15) + (z - 15)*(z - 15) > 15*15) continue;
                int x1 = left + x * size / GRID;
                int y1 = top + z * size / GRID;
                int x2 = left + (x+1) * size / GRID;
                int y2 = top + (z+1) * size / GRID;
                int rgb = config.minimapTerrain ? tiles[z][x] : 0xFF1B2832;
                g.fill(x1, y1, x2, y2, rgb);
            }
        }
        if (config.minimapWaypoints || config.minimapDeaths) {
            var w = DeathCompass.world();
            String dim = DeathCompass.dimension(mc);
            if (config.minimapWaypoints && w.waypoints != null) {
                for (var waypoint : w.waypoints) {
                    if (!dim.equals(waypoint.dimension)) continue;
                    int color = waypoint.id.equals(w.selectedWaypointId) ? 0xFFFFFF55 : 0xFF76EAFA;
                    marker(g, mc, left, top, size, zoom, cosine, sine, px, pz,
                        waypoint.x, waypoint.z, color, config.minimapWaypointNames ? waypoint.name : null,
                        config.minimapCircle);
                }
            }
            if (config.minimapDeaths && w.active != null && dim.equals(w.active.dimension)) {
                marker(g, mc, left, top, size, zoom, cosine, sine, px, pz,
                        w.active.x, w.active.z, 0xFFFF6464, "Death", config.minimapCircle);
            }
        }
        if (config.minimapEntities) {
            int range = 16 * zoom;
            for (var entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(range), e -> e.isAlive())) {
                int tint = entity instanceof Player ? 0xFF8FD2FF :
                    entity.getType().getCategory() == MobCategory.MONSTER ? 0xFFFF5555 : 0xFF8EF18B;
                marker(g, mc, left, top, size, zoom, cosine, sine, px, pz,
                    entity.getX(), entity.getZ(), tint, null, config.minimapCircle);
            }
        }
        // Center is your real server-side position even while freecam is active.
        int cx = left + mid, cy = top + mid;
        g.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF000000);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
        if (config.minimapDirection) {
            double facing = Math.toRadians(mc.player.getYRot());
            double dx = -Math.sin(facing), dz = Math.cos(facing);
            double sx = cosine * dx - sine * dz;
            double sy = sine * dx + cosine * dz;
            g.fill(cx + (int)Math.round(sx * 5) - 1, cy + (int)Math.round(sy * 5) - 1,
                   cx + (int)Math.round(sx * 5) + 2, cy + (int)Math.round(sy * 5) + 2, 0xFFFFE26A);
            if (config.minimapNorthUp) g.text(mc.font, "N", cx - 3, top + 4, 0xFFFFFFFF, true);
        }
        if (config.minimapCoords) {
            String label = px + ", " + (int)mc.player.getY() + ", " + pz;
            g.fill(left - 3, top + size + 3, left + size + 3, top + size + 17, 0xBA101B26);
            g.text(mc.font, label, left + size/2 - mc.font.width(label)/2,
                    top + size + 5, 0xFFFFFFFF, true);
        }
    }

    private static void rebuild(Minecraft mc, int playerX, int playerZ, int zoom, double cos, double sin) {
        int layerY = mc.player.blockPosition().getY();
        for (int y=0; y<GRID; y++) for (int x=0; x<GRID; x++) {
            // Inverse-rotate the sample position to avoid rotated tile gaps.
            int u = (x - 15) * zoom, v = (y - 15) * zoom;
            int wx = playerX + (int)Math.round(cos * u + sin * v);
            int wz = playerZ + (int)Math.round(-sin * u + cos * v);
            if (!mc.level.hasChunkAt(new BlockPos(wx, layerY, wz))) {
                tiles[y][x] = 0xFF17212D;
                continue;
            }
            int height = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
            BlockPos at = new BlockPos(wx, Math.max(mc.level.getMinY(), height - 1), wz);
            BlockState block = mc.level.getBlockState(at);
            int color = colorFor(block);
            if (height > layerY + 16) color = darken(color, 0.82f);
            if (height < layerY - 20) color = darken(color, 0.75f);
            tiles[y][x] = color;
        }
    }

    private static int darken(int color, float s) {
        int r=(int)(((color >> 16)&255)*s), g=(int)(((color>>8)&255)*s), b=(int)((color&255)*s);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    private static int colorFor(BlockState b) {
        if (b.is(Blocks.WATER)) return 0xFF397DAE;
        if (b.is(Blocks.LAVA)) return 0xFFE66C2C;
        if (b.is(Blocks.GRASS_BLOCK) || b.is(Blocks.MOSS_BLOCK)) return 0xFF6D9A47;
        if (b.is(Blocks.SAND) || b.is(Blocks.RED_SAND)) return 0xFFD6BE83;
        if (b.is(Blocks.SNOW) || b.is(Blocks.SNOW_BLOCK) || b.is(Blocks.ICE)) return 0xFFE4F0F1;
        if (b.is(Blocks.STONE) || b.is(Blocks.COBBLESTONE)) return 0xFF81847E;
        if (b.is(Blocks.DEEPSLATE) || b.is(Blocks.COBBLED_DEEPSLATE)) return 0xFF50545A;
        if (b.is(Blocks.DIRT) || b.is(Blocks.COARSE_DIRT) || b.is(Blocks.MUD)) return 0xFF886947;
        if (b.is(Blocks.OAK_LEAVES) || b.is(Blocks.BIRCH_LEAVES) || b.is(Blocks.SPRUCE_LEAVES)) return 0xFF477B44;
        if (b.isAir()) return 0xFF16202C;
        return 0xFF938B7A;
    }

    private static void marker(GuiGraphicsExtractor g, Minecraft mc, int left, int top, int size,
                               int zoom, double cos, double sin, double playerX, double playerZ,
                               double worldX, double worldZ, int tint, String label, boolean circle) {
        double dx = (worldX - playerX) / zoom, dz = (worldZ - playerZ) / zoom;
        double u = cos*dx - sin*dz, v = sin*dx + cos*dz;
        if (Math.abs(u) > 15 || Math.abs(v) > 15 || circle && u*u+v*v > 210) return;
        int x = left + size/2 + (int)Math.round(u*size/GRID);
        int y = top + size/2 + (int)Math.round(v*size/GRID);
        g.fill(x - 3, y - 3, x + 4, y + 4, 0xD0000000);
        g.fill(x - 2, y - 2, x + 3, y + 3, tint);
        if (label != null && !label.isBlank()) {
            String shortLabel = label.length() > 15 ? label.substring(0, 13) + ".." : label;
            g.text(mc.font, shortLabel, x + 5, y - 4, 0xFFFFFFFF, true);
        }
    }
}
