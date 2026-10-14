package dev.deathcompass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 26.2 port of the *behavior* of SecondaryKeybind 1.1: a second configurable
 * keyboard or mouse binding for each vanilla or modded KeyMapping.
 * Original primary mappings and options.txt are not modified.
 */
public final class SecondaryKeybinds {
    private SecondaryKeybinds() {}
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("deathcompass-secondary-keys.json");
    private static final Set<String> held = new HashSet<>();
    private static Data data = new Data();
    private static KeyMapping screenKey;
    private static KeyMapping[] cachedSource;
    private static List<KeyMapping> cachedList=List.of();
    private static boolean initialized;

    private static final class Data {
        Map<String, KeySpec> bindings = new HashMap<>();
    }
    public static final class KeySpec {
        public String type;
        public int value;
        public KeySpec(String type, int value) { this.type=type; this.value=value; }
    }

    public static void initialize() {
        if(initialized) return;
        initialized=true;
        read();
        screenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.deathcompass.secondary_screen", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("deathcompass", "main"))));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while(screenKey.consumeClick()) {
                Screen parent=mc.screen;
                if(parent==null && mc.player==null) continue;
                mc.gui.setScreen(new SecondaryKeybindsScreen(parent));
            }
            if(mc.player==null || mc.level==null) held.clear();
        });
    }

    public static List<KeyMapping> allBindings() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.options==null || mc.options.keyMappings==null) return List.of();
        if(mc.options.keyMappings==cachedSource) return cachedList;
        List<KeyMapping> bindings=new ArrayList<>(Arrays.asList(mc.options.keyMappings));
        bindings.removeIf(Objects::isNull);
        bindings.sort(Comparator.comparing((KeyMapping k)-> k.getCategory().toString())
                .thenComparing(k -> Component.translatable(k.getName()).getString(), String.CASE_INSENSITIVE_ORDER));
        cachedSource=mc.options.keyMappings;
        cachedList=List.copyOf(bindings);
        return cachedList;
    }

    public static KeySpec get(KeyMapping action) {
        if(action==null || data.bindings==null) return null;
        return data.bindings.get(action.getName());
    }
    public static void assign(KeyMapping action, InputConstants.Key key) {
        if(action==null || key==null) return;
        if(data.bindings==null) data.bindings=new HashMap<>();
        KeySpec spec=new KeySpec(key.getType().name(),key.getValue());
        data.bindings.put(action.getName(),spec);
        held.clear();
        write();
    }
    public static void clear(KeyMapping action) {
        if(action==null || data.bindings==null) return;
        data.bindings.remove(action.getName());
        held.clear();
        write();
    }
    public static void clearAll() {
        if(data.bindings==null) data.bindings=new HashMap<>();
        data.bindings.clear();
        held.clear();
        write();
    }
    public static String label(KeyMapping key) {
        KeySpec spec=get(key);
        if(spec==null) return "Unbound";
        try {
            return InputConstants.Type.valueOf(spec.type).getOrCreate(spec.value).getDisplayName().getString();
        } catch(Exception e) {return "Unknown";}
    }
    private static String keyId(InputConstants.Key key) {
        return key.getType().name()+":"+key.getValue();
    }
    private static String keyId(KeySpec spec) {
        return spec.type+":"+spec.value;
    }
    public static boolean conflicts(KeyMapping action) {
        KeySpec spec=get(action);
        if(spec==null) return false;
        String key=keyId(spec);
        for(KeyMapping other:allBindings()) {
            if(other==action) continue;
            KeySpec secondary=get(other);
            if(secondary!=null && key.equals(keyId(secondary))) return true;
            if(key.equals(keyId(KeyMappingHelper.getBoundKeyOf(other)))) return true;
        }
        return false;
    }

    /** Forward secondary clicks without faking an actual primary key press. */
    public static void onClick(InputConstants.Key key) {
        if(key==null || !DeathCompass.data.enabled || data.bindings==null) return;
        String input=keyId(key);
        for(KeyMapping mapping:allBindings()) {
            KeySpec spec=get(mapping);
            if(spec==null || !input.equals(keyId(spec))) continue;
            if(key.equals(KeyMappingHelper.getBoundKeyOf(mapping))) continue; // prevent duplicate click
            var clicks=(KeyMappingClickAccess)(Object)mapping;
            clicks.deathcompass$setClicks(Math.min(1000000,clicks.deathcompass$getClicks()+1));
        }
    }
    public static void onSet(InputConstants.Key key, boolean pressed) {
        if(key==null) return;
        if(pressed)held.add(keyId(key)); else held.remove(keyId(key));
    }
    public static boolean matches(KeyMapping mapping, InputConstants.Key input) {
        if(!DeathCompass.data.enabled || input==null) return false;
        KeySpec spec=get(mapping);
        return spec!=null && keyId(spec).equals(keyId(input));
    }
    public static boolean isSecondaryDown(KeyMapping mapping) {
        if(!DeathCompass.data.enabled) return false;
        KeySpec spec=get(mapping);
        return spec!=null && held.contains(keyId(spec));
    }
    public static void clearHeld() {held.clear();}

    private static void read() {
        try {
            if(Files.exists(CONFIG)) {
                Data loaded=GSON.fromJson(Files.readString(CONFIG),Data.class);
                if(loaded!=null && loaded.bindings!=null) data=loaded;
            }
        } catch(Exception e) {System.err.println("[DeathCompass] Secondary keys config error: "+e);}
    }
    private static void write() {
        try {
            Files.createDirectories(CONFIG.getParent());
            Files.writeString(CONFIG,GSON.toJson(data));
        } catch(Exception e) {System.err.println("[DeathCompass] Cannot save secondary keys: "+e);}
    }
}
