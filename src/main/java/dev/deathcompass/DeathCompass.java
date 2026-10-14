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
    private static KeyMapping settingsKey;
    private static String worldKey = "";
    private static boolean wasDead = false;
    private static int ticks = 0;

    @Override public void onInitializeClient() {
        load();
        settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.deathcompass.settings",InputConstants.Type.KEYSYM,InputConstants.KEY_GRAVE,KeyMapping.Category.register(Identifier.fromNamespaceAndPath("deathcompass","main"))));
        ClientTickEvents.END_CLIENT_TICK.register(DeathCompass::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Identifier.fromNamespaceAndPath("deathcompass","tracker"),CompassHud::draw);
    }
    static void tick(Minecraft mc) {
        while(settingsKey.consumeClick()) if(mc.player != null) mc.gui.setScreen(new SettingsScreen());
        if(mc.player == null || mc.level == null) {wasDead=false;worldKey="";return;}
        String current = key(mc);
        if(!worldKey.equals(current)) {worldKey=current;wasDead=false;}
        boolean dead=mc.player.isDeadOrDying();
        if(dead && !wasDead) {
            TrackerData.Death d = new TrackerData.Death(mc.player.blockPosition().getX(),mc.player.blockPosition().getY(),mc.player.blockPosition().getZ(),dimension(mc));
            TrackerData.WorldRecord w=world();w.active=d;w.history.add(0,d);
            if(w.history.size()>100)w.history=w.history.subList(0,100);
            save();
        }
        wasDead=dead;
        if(++ticks%40==0 && data.portalGuidance) scanVisiblePortals(mc);
        if("block".equals(world().targetMode) || "structure".equals(world().targetMode)) scanBlocks(mc);
    }
    static String key(Minecraft mc) {
        if(mc.getCurrentServer()!=null) return "server:"+mc.getCurrentServer().ip;
        if(mc.getSingleplayerServer()!=null) return "single:"+mc.getSingleplayerServer().getWorldData().getLevelName();
        return "other:unknown";
    }
    static String dimension(Minecraft mc) {return mc.level.dimension().identifier().toString();}
    static TrackerData.WorldRecord world() {return data.worlds.computeIfAbsent(worldKey,k->new TrackerData.WorldRecord());}
    static TrackerData.Death active(){return worldKey.isEmpty()?null:world().active;}
    static void endMission(){world().active=null;world().targetMode="death";save();}
    static void trackDeath(){world().targetMode="death";save();}
    static void trackBlock(String id) {
        TrackerData.WorldRecord w=world();
        w.searchId=id;w.targetMode="block";w.targetFound=false;w.scanCursor=0;w.scanSignature="";
        w.targetDimension="";save();
    }
    /** Structure detection is a heuristic for loaded chunks, not a world-seed /locate. */
    static void trackStructure(String id) {
        TrackerData.WorldRecord w=world();
        w.searchId=id; w.targetMode="structure"; w.targetFound=false;
        w.scanCursor=0;w.scanSignature="";save();
    }
    static TrackerData.Waypoint selectedWaypoint(){
        if(worldKey.isEmpty()) return null;
        TrackerData.WorldRecord w=world();
        if(w.waypoints==null) w.waypoints=new java.util.ArrayList<>();
        return w.waypoints.stream().filter(p->p.id.equals(w.selectedWaypointId)).findFirst().orElse(null);
    }
    static void addWaypoint(String name,int x,int y,int z,String dim){
        TrackerData.WorldRecord w=world();
        if(w.waypoints==null) w.waypoints=new java.util.ArrayList<>();
        TrackerData.Waypoint p=new TrackerData.Waypoint(java.util.UUID.randomUUID().toString(),name,x,y,z,dim);
        w.waypoints.add(p);w.selectedWaypointId=p.id;w.targetMode="waypoint";save();
    }
    static void chooseWaypoint(String id){
        TrackerData.WorldRecord w=world();
        w.selectedWaypointId=id;w.targetMode="waypoint";save();
    }
    static void deleteWaypoint(String id){
        TrackerData.WorldRecord w=world();
        if(w.waypoints==null)return;
        w.waypoints.removeIf(p->p.id.equals(id));
        if(id.equals(w.selectedWaypointId)){w.selectedWaypointId="";w.targetMode="death";}
        save();
    }
    static void stopWaypoint(){world().targetMode="death";save();}
    static void stopSearch(){world().targetMode="death";save();}
    // Incremental, budgeted loaded-chunk scan. Each cycle covers the configurable
    // cube around the player. Never reads unloaded chunks or sends server commands.
    static void scanBlocks(Minecraft mc) {
        TrackerData.WorldRecord w=world();
        int r=Math.max(4,Math.min(64,data.scanRadius));
        int side=2*r+1;
        int total=side*side*side;
        BlockPos center=mc.player.blockPosition();
        String dim=dimension(mc);
        // Restart if player walks far or switches dimension/target/radius.
        String signature=dim+":"+w.targetMode+":"+w.searchId+":"+r+":"+(center.getX()>>4)+":"+(center.getY()>>4)+":"+(center.getZ()>>4);
        if(!signature.equals(w.scanSignature)) {w.scanCursor=0;w.scanSignature=signature;w.targetFound=false;}
        int remaining=Math.max(100,Math.min(10000,data.scanBudget));
        double best=w.targetFound&&dim.equals(w.targetDimension)?new BlockPos(w.targetX,w.targetY,w.targetZ).distSqr(center):Double.POSITIVE_INFINITY;
        while(remaining-->0){
            if(w.scanCursor>=total){w.scanCursor=0;break;}
            int index=w.scanCursor++;
            int dx=index%side-r;
            int dy=(index/side)%side-r;
            int dz=index/(side*side)-r;
            BlockPos pos=center.offset(dx,dy,dz);
            if(pos.getY()<mc.level.getMinY()||pos.getY()>=mc.level.getMaxY()||!mc.level.hasChunkAt(pos))continue;
            String blockId=BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
            boolean matched="block".equals(w.targetMode)?blockId.equals(w.searchId):isStructureClue(w.searchId,blockId);
            if(!matched)continue;
            double distance=pos.distSqr(center);
            if(distance<best){best=distance;w.targetX=pos.getX();w.targetY=pos.getY();w.targetZ=pos.getZ();w.targetDimension=dim;w.targetFound=true;}
        }
        // A found coordinate is a candidate, not verified if it is no longer loaded.
        if(w.targetFound && dim.equals(w.targetDimension)){
            BlockPos saved=new BlockPos(w.targetX,w.targetY,w.targetZ);
            if(mc.level.hasChunkAt(saved)) {
                String id=BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(saved).getBlock()).toString();
                if(!("block".equals(w.targetMode)?id.equals(w.searchId):isStructureClue(w.searchId,id)))w.targetFound=false;
            }
        }
    }
    static boolean isStructureClue(String structure,String block) {
        // Not definitive structure detection. Points to distinguishing blocks only.
        return switch(structure){
            case "minecraft:ancient_city" -> block.equals("minecraft:reinforced_deepslate");
            case "minecraft:nether_fortress" -> block.equals("minecraft:nether_bricks");
            case "minecraft:bastion_remnant" -> block.equals("minecraft:gilded_blackstone");
            case "minecraft:stronghold" -> block.equals("minecraft:end_portal_frame");
            case "minecraft:trial_chambers" -> block.equals("minecraft:trial_spawner")||block.equals("minecraft:vault");
            case "minecraft:monument" -> block.equals("minecraft:dark_prismarine");
            case "minecraft:desert_pyramid" -> block.equals("minecraft:chiseled_sandstone");
            case "minecraft:jungle_temple" -> block.equals("minecraft:chiseled_stone_bricks");
            default -> false;
        };
    }
    static boolean lavaBeneathAimedBlock(Minecraft mc) {
        if(!data.enabled||!data.lavaWarning||mc.level==null||mc.player==null||!(mc.hitResult instanceof BlockHitResult hit))return false;
        BlockPos below=hit.getBlockPos().below();
        return mc.level.hasChunkAt(below) && mc.level.getFluidState(below).is(net.minecraft.tags.FluidTags.LAVA);
    }
    static void setMission(TrackerData.Death d){world().active=d;world().targetMode="death";save();}
    static void scanVisiblePortals(Minecraft mc) {
        BlockPos p=mc.player.blockPosition();
        TrackerData.WorldRecord w=world();String dim=dimension(mc);
        // A client can only examine blocks currently loaded near the player.
        for(int x=-12;x<=12;x+=2)for(int y=-8;y<=8;y+=2)for(int z=-12;z<=12;z+=2){
            BlockPos b=p.offset(x,y,z);
            if(!mc.level.hasChunkAt(b))continue;
            if(mc.level.getBlockState(b).is(Blocks.NETHER_PORTAL)||mc.level.getBlockState(b).is(Blocks.END_PORTAL)){
                boolean known=w.portals.stream().anyMatch(q->q.dimension.equals(dim) && Math.abs(q.x-b.getX())<12 && Math.abs(q.z-b.getZ())<12);
                if(!known){w.portals.add(new TrackerData.Portal(b.getX(),b.getY(),b.getZ(),dim));save();}
            }
        }
    }
    static TrackerData.Portal nearestPortal(Minecraft mc){
        String dim=dimension(mc);double x=mc.player.getX(),z=mc.player.getZ();
        return world().portals.stream().filter(p->p.dimension.equals(dim)).min(Comparator.comparingDouble(p->Math.pow(p.x-x,2)+Math.pow(p.z-z,2))).orElse(null);
    }
    static void load(){try {if(Files.exists(SAVE)){TrackerData d=GSON.fromJson(Files.readString(SAVE),TrackerData.class);if(d!=null&&d.worlds!=null)data=d;}}catch(Exception e){System.err.println("[DeathCompass] Could not read config: "+e);}}
    static void save(){try{Files.createDirectories(SAVE.getParent());Files.writeString(SAVE,GSON.toJson(data),StandardCharsets.UTF_8);}catch(Exception e){System.err.println("[DeathCompass] Could not save config: "+e);}}
}
