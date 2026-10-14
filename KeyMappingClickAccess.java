package dev.deathcompass;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingClickAccess {
    @Accessor("clickCount") int deathcompass$getClicks();
    @Accessor("clickCount") void deathcompass$setClicks(int clicks);
}
