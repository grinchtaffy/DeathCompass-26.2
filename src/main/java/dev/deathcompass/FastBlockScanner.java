
package dev.deathcompass;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loaded-chunk-only palette scan. Shows the first confirmed match immediately,
 * then examines nearby ore veins in the background without changing the lock.
 * No commands, seeds, off-thread world reads or unloaded chunk access.
 */
final class FastBlockScanner {
    private FastBlockScanner() {}

    static final int MAX_RADIUS = 256;
    private static final long SCAN_TIME_BUDGET_NS = 4_000_000L;
    private static final long VEIN_TIME_BUDGET_NS = 1_500_000L;
    private static final int MAX_VEIN_SIZE = 128;
    private static final int NEARBY_VEIN_BLOCKS = 32;
    private static final int FURTHER_DISTANCE_ALLOWANCE = 24;

    private static Search search;

    private record SectionBox(int cx, int sy, int cz, int lowerBoundSq) {}

    private static final class Search {
        final TrackerData.WorldRecord record;
        final Object level;
        final String signature;
        final String dimension;
        final String targetId;
        final String oreFamily;
        final boolean exactBlock;
        final int ox, oy, oz, radiusSq;
        final List<SectionBox> sections;
        final Map<Block, Boolean> matchCache = new IdentityHashMap<>();
        final Deque<BlockPos> pendingVeins = new ArrayDeque<>();
        final Set<Long> queuedSeeds = new HashSet<>();
        final Set<Long> analyzedVeinBlocks = new HashSet<>();

        LevelChunkSection currentSection;
        SectionBox currentBox;
        int sectionIndex, blockIndex, idleTicks;
        boolean blocksCompleted;
        Probe probe;
        int selectedVeinSize;

        Search(Minecraft mc, TrackerData.WorldRecord w, BlockPos origin,
               int radius, String dim, String signature) {
            record = w;
            level = mc.level;
            this.signature = signature;
            dimension = dim;
            targetId = w.searchId;
            exactBlock = "block".equals(w.targetMode);
            oreFamily = exactBlock ? oreFamily(targetId) : null;
            ox = origin.getX(); oy = origin.getY(); oz = origin.getZ();
            radiusSq = radius * radius;
            sections = sectionList(mc, radius);
        }

        boolean matches(Block block) {
            return matchCache.computeIfAbsent(block, b -> {
                String id = BuiltInRegistries.BLOCK.getKey(b).toString();
                if (!exactBlock) return DeathCompass.isStructureClue(targetId, id);
                return oreFamily != null ? oreFamily.equals(oreFamily(id)) : targetId.equals(id);
            });
        }

        private List<SectionBox> sectionList(Minecraft mc, int radius) {
            List<SectionBox> boxes = new ArrayList<>();
            int minY = Math.floorDiv(Math.max(mc.level.getMinY(), oy - radius), 16);
            int maxY = Math.floorDiv(Math.min(mc.level.getMaxY() - 1, oy + radius), 16);
            int minX = Math.floorDiv(ox - radius, 16);
            int maxX = Math.floorDiv(ox + radius, 16);
            int minZ = Math.floorDiv(oz - radius, 16);
            int maxZ = Math.floorDiv(oz + radius, 16);

            for (int cx = minX; cx <= maxX; cx++) {
                int dx = axisDistance(ox, cx << 4, (cx << 4) + 15);
                for (int cz = minZ; cz <= maxZ; cz++) {
                    // Large search radius does not imply reading unloaded chunks.
                    if (!mc.level.hasChunkAt(new BlockPos(cx << 4, oy, cz << 4))) continue;
                    int dz = axisDistance(oz, cz << 4, (cz << 4) + 15);
                    for (int sy = minY; sy <= maxY; sy++) {
                        int dy = axisDistance(oy, sy << 4, (sy << 4) + 15);
                        int lower = dx * dx + dy * dy + dz * dz;
                        if (lower <= radiusSq) boxes.add(new SectionBox(cx, sy, cz, lower));
                    }
                }
            }
            boxes.sort(Comparator.comparingInt(SectionBox::lowerBoundSq));
            return boxes;
        }

        void found(Minecraft mc, TrackerData.WorldRecord w, int x, int y, int z) {
            if (!w.targetFound) {
                // The player sees a confirmed block immediately, rather than
                // waiting for a multi-chunk exhaustive nearest-target pass.
                w.targetX = x; w.targetY = y; w.targetZ = z;
                w.targetDimension = dimension;
                w.targetFound = true;
                w.suggestedVeinAvailable = false;
                w.suggestionDismissed = false;
                w.targetVeinSize = 0;
                DeathCompass.save();
            }

            if (oreFamily == null) return;
            // Analyze just veins reasonably close to the selected target.
            long tx = (long)x - w.targetX;
            long ty = (long)y - w.targetY;
            long tz = (long)z - w.targetZ;
            if (tx*tx + ty*ty + tz*tz > NEARBY_VEIN_BLOCKS * NEARBY_VEIN_BLOCKS) return;
            double selectedDistance = Math.sqrt(distanceSq(w.targetX, w.targetY, w.targetZ, ox, oy, oz));
            double contenderDistance = Math.sqrt(distanceSq(x, y, z, ox, oy, oz));
            if (contenderDistance > selectedDistance + FURTHER_DISTANCE_ALLOWANCE) return;
            long key = new BlockPos(x, y, z).asLong();
            if (!analyzedVeinBlocks.contains(key) && queuedSeeds.size() < 512 && queuedSeeds.add(key)) {
                pendingVeins.addLast(new BlockPos(x, y, z));
            }
        }

        void analyzeVeins(Minecraft mc, TrackerData.WorldRecord w) {
            if (oreFamily == null || !w.targetFound || w.suggestionDismissed || !DeathCompass.data.veinSuggestionEnabled) return;
            long deadline = System.nanoTime() + VEIN_TIME_BUDGET_NS;
            int work = 0;
            while (System.nanoTime() < deadline && work < 64) {
                if (probe == null) {
                    BlockPos seed = pendingVeins.pollFirst();
                    if (seed == null) return;
                    if (analyzedVeinBlocks.contains(seed.asLong())) continue;
                    probe = new Probe(seed);
                }
                probe.step(mc, this);
                work++;
                if (!probe.complete) continue;
                analyzedVeinBlocks.addAll(probe.members);
                int amount = probe.count;
                boolean isSelected = probe.members.contains(new BlockPos(w.targetX, w.targetY, w.targetZ).asLong());

                if (isSelected) {
                    selectedVeinSize = Math.max(selectedVeinSize, amount);
                    w.targetVeinSize = selectedVeinSize;
                } else if (selectedVeinSize > 0 && amount >= selectedVeinSize + 2) {
                    int newDistance = distanceSq(probe.seed.getX(), probe.seed.getY(), probe.seed.getZ(), ox, oy, oz);
                    int oldDistance = distanceSq(w.suggestedX, w.suggestedY, w.suggestedZ, ox, oy, oz);
                    if (!w.suggestedVeinAvailable || amount > w.suggestedVeinSize
                            || (amount == w.suggestedVeinSize && newDistance < oldDistance)) {
                        w.suggestedX = probe.seed.getX();
                        w.suggestedY = probe.seed.getY();
                        w.suggestedZ = probe.seed.getZ();
                        w.suggestedVeinSize = amount;
                        w.suggestedVeinAvailable = true;
                    }
                }
                probe = null;
            }
        }
    }

    private static final class Probe {
        final BlockPos seed;
        final Deque<BlockPos> queue = new ArrayDeque<>();
        final Set<Long> visited = new HashSet<>();
        final Set<Long> members = new HashSet<>();
        int count;
        boolean complete;

        Probe(BlockPos start) {
            seed = start;
            queue.add(start);
            visited.add(start.asLong());
        }

        void step(Minecraft mc, Search s) {
            BlockPos at = queue.pollFirst();
            if (at == null || count >= MAX_VEIN_SIZE) {
                complete = true;
                return;
            }
            if (!mc.level.hasChunkAt(at) || !s.matches(mc.level.getBlockState(at).getBlock())) {
                if (queue.isEmpty()) complete = true;
                return;
            }
            members.add(at.asLong());
            count++;
            if (count >= MAX_VEIN_SIZE) {
                complete = true;
                return;
            }

            // 26-connected components include diagonally touching ore blocks.
            for (int dx=-1; dx<=1; dx++)
                for (int dy=-1; dy<=1; dy++)
                    for (int dz=-1; dz<=1; dz++) {
                        if (dx==0 && dy==0 && dz==0) continue;
                        BlockPos next = at.offset(dx, dy, dz);
                        if (next.getY() < mc.level.getMinY() || next.getY() >= mc.level.getMaxY()) continue;
                        if (!visited.add(next.asLong()) || !mc.level.hasChunkAt(next)) continue;
                        if (s.matches(mc.level.getBlockState(next).getBlock())) queue.addLast(next);
                    }
            if (queue.isEmpty()) complete = true;
        }
    }

    private static String oreFamily(String id) {
        if (id == null || !id.startsWith("minecraft:")) return null;
        String normalized = id.replace("minecraft:deepslate_", "minecraft:");
        return normalized.endsWith("_ore") || normalized.equals("minecraft:ancient_debris")
            ? normalized : null;
    }

    private static int axisDistance(int val, int min, int max) {
        return val < min ? min - val : Math.max(0, val - max);
    }

    private static int distanceSq(int x,int y,int z,int ox,int oy,int oz) {
        long dx=(long)x-ox, dy=(long)y-oy, dz=(long)z-oz;
        return (int)Math.min(Integer.MAX_VALUE,dx*dx+dy*dy+dz*dz);
    }

    static void scan(Minecraft mc) {
        TrackerData.WorldRecord w = DeathCompass.world();
        int radius = Math.max(4, Math.min(MAX_RADIUS, DeathCompass.data.scanRadius));
        int release = Math.max(32, Math.min(512, DeathCompass.data.lockReleaseDistance));
        BlockPos player = mc.player.blockPosition();
        String dim = DeathCompass.dimension(mc);
        String key = dim + ":" + w.targetMode + ":" + w.searchId + ":" + radius;

        // A locked block should remain stable until mined or out of range.
        if (w.targetFound) {
            BlockPos target = new BlockPos(w.targetX, w.targetY, w.targetZ);
            Boolean exists = null;

            if (dim.equals(w.targetDimension) && mc.level.hasChunkAt(target)) {
                String id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(target).getBlock()).toString();
                exists = "block".equals(w.targetMode)
                    ? (oreFamily(w.searchId) != null ? oreFamily(w.searchId).equals(oreFamily(id)) : id.equals(w.searchId))
                    : DeathCompass.isStructureClue(w.searchId, id);
            }

            if (TargetLockPolicy.shouldRelease(dim, w.targetDimension, target.distSqr(player), release, exists)) {
                // Preserve a connected ore vein after a mined block, rather than
                // jumping back to an unrelated, nearer vein on the next tick.
                if (Boolean.FALSE.equals(exists) && "block".equals(w.targetMode) && DeathCompass.data.autoNextOreEnabled) {
                    BlockPos adjacent = nextConnectedOre(mc, target, player, w.searchId);
                    if (adjacent != null) {
                        w.targetX = adjacent.getX();
                        w.targetY = adjacent.getY();
                        w.targetZ = adjacent.getZ();
                        w.targetVeinSize = Math.max(0, w.targetVeinSize - 1);
                        w.suggestedVeinAvailable = false;
                        w.suggestionDismissed = true;
                        search = null;
                        DeathCompass.save();
                        return;
                    }
                }

                w.targetFound = false;
                if (Boolean.FALSE.equals(exists) && !DeathCompass.data.autoNextOreEnabled) {
                    // Opting out of auto-next means the mining mission ends here.
                    w.targetMode = "death";
                    search = null;
                    DeathCompass.save();
                    return;
                }

                w.scanSignature = "";
                w.targetVeinSize = 0;
                w.suggestedVeinAvailable = false;
                w.suggestionDismissed = false;
                search = null;  // Automatically find the next ore after mining.
            } else if (search == null) {
                return;
            }
        }

        boolean moved = w.scanSignature != null && !w.scanSignature.isEmpty()
            && (Math.abs(player.getX()-w.scanOriginX)>Math.max(8,radius/2)
                || Math.abs(player.getY()-w.scanOriginY)>Math.max(8,radius/2)
                || Math.abs(player.getZ()-w.scanOriginZ)>Math.max(8,radius/2));

        if (search == null || search.record != w || search.level != mc.level
            || !key.equals(search.signature) || !key.equals(w.scanSignature) || moved) {
            begin(mc, w, player, radius, dim, key);
        } else if (search.blocksCompleted && search.pendingVeins.isEmpty() && search.probe == null) {
            if (w.targetFound) { search = null; return; }
            if (++search.idleTicks < 30) return; // Newly loaded chunks become visible.
            begin(mc, w, player, radius, dim, key);
        }

        Search s = search;
        if (!s.blocksCompleted) {
            int checked = 0;
            int maxBlocks = Math.max(4096, Math.min(50000, DeathCompass.data.scanBudget * 12));
            long start = System.nanoTime();
            final int total = (radius * 2 + 1) * (radius * 2 + 1) * (radius * 2 + 1);

            while (checked < maxBlocks) {
                if (checked > 0 && (checked & 255) == 0 && System.nanoTime()-start >= SCAN_TIME_BUDGET_NS) break;

                if (s.currentSection == null) {
                    if (s.sectionIndex >= s.sections.size()) {
                        s.blocksCompleted = true;
                        w.scanCursor = total;
                        break;
                    }

                    SectionBox box = s.sections.get(s.sectionIndex++);
                    checked++;
                    BlockPos chunkPos = new BlockPos(box.cx()<<4, s.oy, box.cz()<<4);
                    if (!mc.level.hasChunkAt(chunkPos)) continue;
                    LevelChunk chunk = mc.level.getChunk(box.cx(), box.cz());
                    int index = chunk.getSectionIndexFromSectionY(box.sy());
                    if (index < 0 || index >= chunk.getSections().length) continue;
                    LevelChunkSection section = chunk.getSection(index);
                    if (section == null || !section.maybeHas(st -> s.matches(st.getBlock()))) continue;
                    s.currentSection = section;
                    s.currentBox = box;
                    s.blockIndex = 0;
                }

                SectionBox box = s.currentBox;
                while (s.blockIndex < 4096 && checked < maxBlocks) {
                    int n=s.blockIndex++;
                    int x=(box.cx()<<4)+(n&15);
                    int z=(box.cz()<<4)+((n>>>4)&15);
                    int y=(box.sy()<<4)+((n>>>8)&15);
                    long dx=(long)x-s.ox, dy=(long)y-s.oy, dz=(long)z-s.oz;

                    if (dx*dx+dy*dy+dz*dz <= s.radiusSq && y >= mc.level.getMinY() && y < mc.level.getMaxY()) {
                        if (s.matches(s.currentSection.getBlockState(n&15,(n>>>8)&15,(n>>>4)&15).getBlock())) {
                            s.found(mc, w, x,y,z);
                        }
                    }
                    checked++;
                    if ((checked & 255)==0 && System.nanoTime()-start >= SCAN_TIME_BUDGET_NS) break;
                }

                if (s.blockIndex == 4096) {
                    s.currentSection=null;
                    s.currentBox=null;
                    s.blockIndex=0;
                } else break;
            }

            if (!s.blocksCompleted) {
                double sectionsDone = s.sectionIndex - (s.currentSection == null ? 0 : 1.0 - s.blockIndex/4096.0);
                w.scanCursor = Math.min(total-1, (int)(total * sectionsDone / Math.max(1, s.sections.size())));
            }
        }

        s.analyzeVeins(mc, w);
    }

    private static void begin(Minecraft mc, TrackerData.WorldRecord w, BlockPos position,
                              int radius, String dim, String key) {
        w.scanSignature=key;
        w.scanOriginX=position.getX();
        w.scanOriginY=position.getY();
        w.scanOriginZ=position.getZ();
        w.scanCursor=0;
        search=new Search(mc,w,position,radius,dim,key);

        if (w.targetFound && search.oreFamily != null) {
            // Re-check the locked vein, but never silently switch targets.
            search.found(mc,w,w.targetX,w.targetY,w.targetZ);
        }
    }

    private static BlockPos nextConnectedOre(Minecraft mc, BlockPos mined, BlockPos player, String targetId) {
        String family = oreFamily(targetId);
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (int dx=-1;dx<=1;dx++)
            for(int dy=-1;dy<=1;dy++)
                for(int dz=-1;dz<=1;dz++) {
                    if(dx==0 && dy==0 && dz==0) continue;
                    BlockPos pos = mined.offset(dx,dy,dz);
                    if(pos.getY()<mc.level.getMinY() || pos.getY()>=mc.level.getMaxY() || !mc.level.hasChunkAt(pos)) continue;
                    String id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
                    boolean same = family == null ? targetId.equals(id) : family.equals(oreFamily(id));

                    if (same && pos.distSqr(player) < bestDistance) {
                        best = pos;
                        bestDistance = pos.distSqr(player);
                    }
                }
        return best;
    }

    static void switchToSuggestedVein() {
        TrackerData.WorldRecord w = DeathCompass.world();
        if (!"block".equals(w.targetMode) || !w.suggestedVeinAvailable || !w.targetFound) return;
        w.targetX=w.suggestedX;
        w.targetY=w.suggestedY;
        w.targetZ=w.suggestedZ;
        w.targetVeinSize=w.suggestedVeinSize;
        w.suggestedVeinAvailable=false;
        w.suggestionDismissed=true;
        search=null;
        DeathCompass.save();
    }

    static void keepCurrentVein() {
        TrackerData.WorldRecord w=DeathCompass.world();
        if (!"block".equals(w.targetMode)) return;
        w.suggestedVeinAvailable=false;
        w.suggestionDismissed=true;
        if (search != null) {
            search.pendingVeins.clear();
            search.probe=null;
        }
    }
}
