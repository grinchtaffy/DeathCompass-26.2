package dev.deathcompass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Read-only, client-side hazard estimates from loaded chunks and nearby tracked entities.
 * These are warnings, not a simulation of all future fluid or falling-block updates.
 */
public final class HazardScanner {
    private HazardScanner() {}

    public record Warning(String message, int color) {}
    private static final int RED=0xFFFF5555, ORANGE=0xFFFFBB55, YELLOW=0xFFFFDE87;
    private static String lastWorld="";
    private static final Set<Long> previousLava = new HashSet<>();
    private static List<Warning> current = List.of();
    private static int tickCount = 0;

    public static List<Warning> warnings() {return current;}

    public static void reset() {
        lastWorld="";
        previousLava.clear();
        current=List.of();
    }

    public static void tick(Minecraft mc, String worldKey) {
        TrackerData c=DeathCompass.data;
        if(mc.player==null || mc.level==null || !c.enabled || !c.hazardAlerts) {
            reset(); return;
        }
        String identity=worldKey+"/"+DeathCompass.dimension(mc);
        if(!identity.equals(lastWorld)) {lastWorld=identity;previousLava.clear();}
        if(++tickCount % 5 != 0) return;

        List<Warning> important=new ArrayList<>();
        List<Warning> ambient=new ArrayList<>();
        BlockPos feet=mc.player.blockPosition();

        // Inspect the block currently under the crosshair BEFORE mining it.
        if(mc.hitResult instanceof BlockHitResult hit && mc.level.hasChunkAt(hit.getBlockPos())) {
            BlockPos target=hit.getBlockPos();
            if(c.lavaWarning) {
                if(lava(mc,target.above())) important.add(new Warning("MINING WARNING: LAVA ABOVE TARGET - MAY POUR DOWN!",RED));
                else if(lava(mc,target.north())||lava(mc,target.south())||lava(mc,target.east())||lava(mc,target.west()))
                    important.add(new Warning("MINING WARNING: LAVA AGAINST TARGET - MAY FLOW OUT!",RED));
                else if(lava(mc,target.below())) important.add(new Warning("MINING WARNING: LAVA DIRECTLY UNDER TARGET!",ORANGE));
            }
            if(c.gravelWarning) {
                // Breakable target may be the support for a vertical column of gravity blocks.
                BlockPos above=target.above();
                int count=0;
                while(count<16 && mc.level.hasChunkAt(above) && isFalling(mc.level.getBlockState(above))) {
                    count++;above=above.above();
                }
                if(count>0) important.add(new Warning("MINING WARNING: "+count+" FALLING SAND/GRAVEL BLOCK(S) ABOVE!",RED));
            }
        }

        int radius=Math.max(2,Math.min(8,c.hazardRadius));
        int radiusSq=radius*radius;
        Set<Long> lavaNow=new HashSet<>();
        int nearbyGravel=0;
        boolean activeLava=false, hotSurface=false, hazardBlock=false;
        boolean overheadFalling=false;
        int minLavaSq=Integer.MAX_VALUE;
        boolean flowingCloser=false;
        for(int dx=-radius;dx<=radius;dx++) for(int dy=-radius;dy<=radius;dy++) for(int dz=-radius;dz<=radius;dz++) {
            int d2=dx*dx+dy*dy+dz*dz;
            if(d2>radiusSq) continue;
            BlockPos p=feet.offset(dx,dy,dz);
            if(p.getY()<mc.level.getMinY() || p.getY()>=mc.level.getMaxY() || !mc.level.hasChunkAt(p))continue;
            BlockState state=mc.level.getBlockState(p);
            if(c.lavaWarning && state.getFluidState().is(FluidTags.LAVA)) {
                lavaNow.add(p.asLong());
                if(d2<minLavaSq)minLavaSq=d2;
                // Only announce newly occupied positions when close to the player.
                if(d2<=16 && !previousLava.isEmpty() && !previousLava.contains(p.asLong()) && !state.getFluidState().isSource()) flowingCloser=true;
                if(d2<=4)activeLava=true;
            }
            if(c.gravelWarning && isFalling(state)) {
                if(d2<=25)nearbyGravel++;
                // A gravity block above head with no full support is a potential falling hazard.
                if(dx==0 && dz==0 && dy>=2 && dy<=5 && mc.level.hasChunkAt(p.below()) && mc.level.getBlockState(p.below()).isAir())
                    overheadFalling=true;
            }
            if(c.environmentWarning && d2<=9) {
                if(state.is(Blocks.FIRE)||state.is(Blocks.SOUL_FIRE)||state.is(Blocks.CAMPFIRE)||state.is(Blocks.SOUL_CAMPFIRE))hazardBlock=true;
                if(state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.CACTUS)||state.is(Blocks.SWEET_BERRY_BUSH))hotSurface=true;
            }
        }
        if(c.lavaWarning) {
            if(flowingCloser && c.lavaSpreadWarning) ambient.add(new Warning("LAVA SPREADING NEAR YOU - WATCH YOUR FEET!",RED));
            else if(activeLava)ambient.add(new Warning("LAVA WITHIN 2 BLOCKS - BE CAREFUL!",ORANGE));
            else if(minLavaSq<=radiusSq && c.showNearbyLava)ambient.add(new Warning("LAVA NEARBY (CAN BE BEHIND WALLS)",YELLOW));
        }
        if(c.gravelWarning) {
            if(overheadFalling)ambient.add(new Warning("UNSUPPORTED GRAVEL/SAND ABOVE YOU!",RED));
            else if(nearbyGravel>0)ambient.add(new Warning("GRAVEL/SAND NEARBY: "+nearbyGravel+" BLOCK(S)",YELLOW));
        }
        if(c.environmentWarning) {
            if(hazardBlock)ambient.add(new Warning("FIRE OR CAMPFIRE NEARBY",ORANGE));
            else if(hotSurface)ambient.add(new Warning("DAMAGE BLOCK NEARBY (MAGMA/CACTUS/BERRY)",YELLOW));
        }
        if(c.mobWarning || c.environmentWarning || c.gravelWarning) {
            var entities=mc.level.getEntities(mc.player,mc.player.getBoundingBox().inflate(radius), e -> e.isAlive());
            if(c.environmentWarning && entities.stream().anyMatch(e -> e.getType()==EntityType.TNT))
                important.add(new Warning("PRIMED TNT NEARBY!",RED));
            if(c.gravelWarning && entities.stream().anyMatch(e -> e.getType()==EntityType.FALLING_BLOCK))
                important.add(new Warning("FALLING BLOCK ENTITY NEARBY!",RED));
            if(c.mobWarning) {
                var hostile=entities.stream().filter(e -> e.getType().getCategory()==MobCategory.MONSTER).toList();
                if(!hostile.isEmpty()) {
                    boolean creeper=hostile.stream().anyMatch(e -> e.getType()==EntityType.CREEPER);
                    ambient.add(new Warning(creeper?"CREEPER NEARBY!":"HOSTILE MOB(S) NEARBY: "+hostile.size(),creeper?RED:ORANGE));
                }
            }
        }
        previousLava.clear(); previousLava.addAll(lavaNow);
        ambient.sort((a,b) -> Integer.compare(severity(b.color()),severity(a.color())));
        important.addAll(ambient);
        // Two lines maximum; mining-specific warnings take precedence over ambient warnings.
        current=List.copyOf(important.subList(0,Math.min(2,important.size())));
    }

    private static int severity(int color) {
        return color==RED?3:color==ORANGE?2:1;
    }

    private static boolean lava(Minecraft mc,BlockPos p) {
        return p.getY()>=mc.level.getMinY() && p.getY()<mc.level.getMaxY() &&
            mc.level.hasChunkAt(p) && mc.level.getFluidState(p).is(FluidTags.LAVA);
    }

    private static boolean isFalling(BlockState s) {
        Block b=s.getBlock();
        return b instanceof net.minecraft.world.level.block.FallingBlock;
    }
}
