
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
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;

public final class DeathCompass implements ClientModInitializer {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final Path SAVE = FabricLoader.getInstance().getConfigDir().resolve("deathcompass.json");
    public static TrackerData data = new TrackerData();
    public static final KeyMapping.Category MAIN_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("deathcompass", "main"));
    private static KeyMapping settingsKey;
    private static KeyMapping quickWaypointKey;
    private static KeyMapping acceptVeinKey;
    private static KeyMapping declineVeinKey;
    private static KeyMapping freecamKey;
    private static KeyMapping minimapKey;
    private static String worldKey = "";
    private static boolean wasDead = false;
    private static int ticks = 0;

    @Override public void onInitializeClient() {
        load();
        SecondaryKeybinds.initialize();
        settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.deathcompass.settings",InputConstants.Type.KEYSYM,InputConstants.KEY_GRAVE,MAIN_CATEGORY));
        quickWaypointKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.deathcompass.quick_waypoint", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, MAIN_CATEGORY));
        acceptVeinKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.deathcompass.accept_vein", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, MAIN_CATEGORY));
        declineVeinKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.deathcompass.keep_vein", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, MAIN_CATEGORY));
        freecamKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.deathcompass.freecam",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F4,MAIN_CATEGORY));
        minimapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.deathcompass.minimap",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F8,MAIN_CATEGORY));
        ClientTickEvents.START_CLIENT_TICK.register(FreecamController::tick);
        ClientTickEvents.END_CLIENT_TICK.register(DeathCompass::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Identifier.fromNamespaceAndPath("deathcompass","tracker"),CompassHud::draw);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Identifier.fromNamespaceAndPath("deathcompass","minimap"),MinimapHud::draw);
    }

    static void tick(Minecraft mc) {
        while(settingsKey.consumeClick()) if(mc.player != null) mc.gui.setScreen(new SettingsScreen());
        while(freecamKey.consumeClick()) FreecamController.toggle(mc);
        while(minimapKey.consumeClick()) {
            if(data.enabled && data.minimapEnabled) {data.minimapVisible = !data.minimapVisible; save();}
        }
        if(mc.player == null || mc.level == null) {FreecamController.stop(mc);MinimapHud.invalidate();wasDead=false;worldKey="";HazardScanner.reset();HazardSounds.reset();SecondaryKeybinds.clearHeld();return;}
        String current = key(mc);
        if(!worldKey.equals(current)) {worldKey=current;wasDead=false;HazardScanner.reset();MinimapHud.invalidate();}

        // All three actions are normal Minecraft key mappings: change them in Controls,
        // or give them a second key in the built-in Secondary Keybinds editor.
        while(quickWaypointKey.consumeClick()) {
            if (data.enabled && data.quickWaypointEnabled && mc.player != null && mc.level != null && !mc.player.isDeadOrDying()) quickWaypoint(mc);
        }
        while(acceptVeinKey.consumeClick()) if(data.enabled && data.veinSuggestionEnabled) FastBlockScanner.switchToSuggestedVein();
        while(declineVeinKey.consumeClick()) FastBlockScanner.keepCurrentVein();
        boolean dead=mc.player.isDeadOrDying();
        if(data.enabled && data.deathTrackingEnabled && dead && !wasDead) {
            TrackerData.Death d = new TrackerData.Death(mc.player.blockPosition().getX(),mc.player.blockPosition().getY(),mc.player.blockPosition().getZ(),dimension(mc));
            TrackerData.WorldRecord w=world();w.active=d;w.history.add(0,d);
            if(w.history.size()>100)w.history=w.history.subList(0,100);
            save();
        }
        wasDead=dead;
        if(++ticks%40==0 && data.enabled && data.portalGuidance) scanVisiblePortals(mc);
        if(data.enabled && data.blockFinderEnabled &&
            ("block".equals(world().targetMode) ||
             (data.structureCluesEnabled && "structure".equals(world().targetMode)))) scanBlocks(mc);
        HazardScanner.tick(mc,current);
        HazardSounds.evaluate(mc);
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
        w.targetDimension="";w.scanCandidateFound=false;save();
    }

    /** Structure detection is a heuristic for loaded chunks, not a world-seed /locate. */
    static void trackStructure(String id) {
        TrackerData.WorldRecord w=world();
        w.searchId=id; w.targetMode="structure"; w.targetFound=false;
        w.scanCursor=0;w.scanSignature="";w.scanCandidateFound=false;save();
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

    private static void quickWaypoint(Minecraft mc) {
        TrackerData.WorldRecord w=world();
        if(w.waypoints==null)w.waypoints=new java.util.ArrayList<>();
        int number=1;
        while(true) {
            final String proposed="Quick " + number;
            if(w.waypoints.stream().noneMatch(p -> proposed.equals(p.name))) break;
            number++;
        }
        BlockPos here=mc.player.blockPosition();
        String name="Quick " + number;
        addWaypoint(name,here.getX(),here.getY(),here.getZ(),dimension(mc));
        mc.player.displayClientMessage(Component.literal(
            "Saved " + name + " at " + here.getX() + ", " + here.getY() + ", " + here.getZ()), true);
    }

    static String veinResponseKeys() {
        if (acceptVeinKey == null || declineVeinKey == null) return "J = SWITCH / N = KEEP";
        return acceptVeinKey.getTranslatedKeyMessage().getString() + " = SWITCH / "
             + declineVeinKey.getTranslatedKeyMessage().getString() + " = KEEP";
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

    // Faster, palette-aware, accurate nearest-block finder.
    static void scanBlocks(Minecraft mc) {
        FastBlockScanner.scan(mc);
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
            case "minecraft:end_city" -> block.equals("minecraft:purpur_block")||block.equals("minecraft:end_stone_bricks");
            case "minecraft:woodland_mansion" -> block.equals("minecraft:dark_oak_planks");
            case "minecraft:pillager_outpost" -> block.equals("minecraft:dark_oak_log");
            case "minecraft:shipwreck" -> block.equals("minecraft:oak_planks")||block.equals("minecraft:spruce_planks");
            case "minecraft:ruined_portal" -> block.equals("minecraft:crying_obsidian");
            case "minecraft:mineshaft" -> block.equals("minecraft:oak_fence")||block.equals("minecraft:rail");
            case "minecraft:village" -> block.equals("minecraft:bell");
            case "minecraft:ocean_ruin" -> block.equals("minecraft:prismarine");
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

    static void load(){
        try {
            if (Files.exists(SAVE)) {
                String json=Files.readString(SAVE);
                TrackerData loaded=GSON.fromJson(json,TrackerData.class);
                if (loaded!=null && loaded.worlds!=null) {
                    data=loaded;
                    // Gson preserves Java default initializers for missing JSON fields;
                    // detect old configs by the actual field's absence instead.
                    if (!json.contains("\"expandedSearchRadius\"")) {
                        data.scanRadius=FastBlockScanner.MAX_RADIUS;
                        data.expandedSearchRadius=true;
                        save();
                    }
                }
            }
        } catch(Exception e) {
            System.err.println("[DeathCompass] Could not read config: "+e);
        }
    }

    static void save(){
        try {
            Files.createDirectories(SAVE.getParent());
            Files.writeString(SAVE,GSON.toJson(data),StandardCharsets.UTF_8);
        }catch(Exception e){
            System.err.println("[DeathCompass] Could not save config: "+e);
        }
    }
}
