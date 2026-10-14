package dev.deathcompass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Debounced local UI beeps. Warns; does not guarantee safety. */
public final class HazardSounds {
    private HazardSounds() {}
    private static long ticks;
    private static long lastPlay=-100000;
    private static String lastAlert="";

    public static void reset(){lastAlert="";lastPlay=-100000;}

    public static void evaluate(Minecraft mc) {
        ticks++;
        TrackerData c=DeathCompass.data;
        if(mc.player==null || !c.enabled || !c.hazardAlerts || !c.warningSounds || c.warningSoundVolume<=0){
            lastAlert="";return;
        }
        String chosen="";
        for(HazardScanner.Warning warning:HazardScanner.warnings()) {
            if(warning.color()!=0xFFFF5555 && !warning.message().startsWith("MINING WARNING"))continue;
            String text=warning.message();
            boolean enabled;
            if(text.contains("LAVA"))enabled=c.soundLava;
            else if(text.contains("GRAVEL")||text.contains("SAND")||text.contains("FALLING"))enabled=c.soundFallingBlocks;
            else if(text.contains("MOB")||text.contains("CREEPER")||text.contains("HOSTILE"))enabled=c.soundHostiles;
            else enabled=c.soundOther;
            if(enabled){chosen=text;break;}
        }
        if(chosen.isEmpty()){lastAlert="";return;}
        boolean newAlert=!chosen.equals(lastAlert);
        int cooldown=Math.max(20,Math.min(600,c.warningSoundCooldownTicks));
        if((newAlert && ticks-lastPlay>=20)||ticks-lastPlay>=cooldown){
            float volume=Math.max(0,Math.min(100,c.warningSoundVolume))/100.0f;
            float pitch=chosen.contains("LAVA")?0.8f:chosen.contains("FALLING")?1.25f:1.1f;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), pitch, volume));
            lastPlay=ticks;
        }
        lastAlert=chosen;
    }
}
