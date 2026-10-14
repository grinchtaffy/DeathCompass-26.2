package dev.deathcompass.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.deathcompass.SecondaryKeybinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mirrors keyboard AND mouse dispatch for secondary keys; never changes primary key settings. */
@Mixin(KeyMapping.class)
public abstract class SecondaryKeysMixin {
    @Inject(method="click",at=@At("TAIL"))
    private static void deathcompass$click(InputConstants.Key key, CallbackInfo ci) {
        SecondaryKeybinds.onClick(key);
    }
    @Inject(method="set",at=@At("TAIL"))
    private static void deathcompass$set(InputConstants.Key key, boolean pressed, CallbackInfo ci) {
        SecondaryKeybinds.onSet(key,pressed);
    }
    @Inject(method="isDown",at=@At("HEAD"),cancellable=true)
    private void deathcompass$down(CallbackInfoReturnable<Boolean> cir) {
        if(SecondaryKeybinds.isSecondaryDown((KeyMapping)(Object)this)) cir.setReturnValue(true);
    }
    @Inject(method="matches",at=@At("HEAD"),cancellable=true)
    private void deathcompass$matchesKeyboard(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if(SecondaryKeybinds.matches((KeyMapping)(Object)this,InputConstants.getKey(event)))cir.setReturnValue(true);
    }
    @Inject(method="matchesMouse",at=@At("HEAD"),cancellable=true)
    private void deathcompass$matchesMouse(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if(SecondaryKeybinds.matches((KeyMapping)(Object)this,InputConstants.Type.MOUSE.getOrCreate(event.button())))cir.setReturnValue(true);
    }
    @Inject(method="releaseAll",at=@At("TAIL"))
    private static void deathcompass$releaseAll(CallbackInfo ci) {
        SecondaryKeybinds.clearHeld();
    }
}
