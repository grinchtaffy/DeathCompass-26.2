package dev.deathcompass.mixin;

import dev.deathcompass.SecondaryKeybindsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds an editor button after ControlsScreen builds its options list. */
@Mixin(ControlsScreen.class)
public abstract class ControlsScreenMixin extends Screen {
    protected ControlsScreenMixin(Component title) {super(title);}

    @Inject(method="addOptions", at=@At("TAIL"))
    private void deathcompass$controlsButton(CallbackInfo ci) {
        addRenderableWidget(Button.builder(Component.literal("Secondary Keybinds"), button ->
            Minecraft.getInstance().gui.setScreen(new SecondaryKeybindsScreen(this)))
            .bounds(Math.max(6,width-140),6,132,20).build());
    }
}
