
package dev.deathcompass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Detached, purely client-side camera. No movement/teleport packets and no server-side entity.
 * Player input is temporarily replaced with an empty input while the camera moves.
 * Disconnecting, dying, changing worlds or disabling the feature always restores it.
 */
public final class FreecamController {
    private FreecamController() {}

    private static RemotePlayer camera;
    private static LocalPlayer owner;
    private static Object originalLevel;
    private static ClientInput originalInput;
    private static float playerYaw, playerPitch;

    public static boolean active() { return camera != null; }

    public static void toggle(Minecraft mc) {
        if (active()) { stop(mc); return; }
        if (mc.player == null || mc.level == null || !DeathCompass.data.enabled
                || !DeathCompass.data.freecamEnabled || mc.gui.screen() != null) return;

        originalLevel = mc.level;
        owner = mc.player;
        originalInput = mc.player.input;
        playerYaw = mc.player.getYRot();
        playerPitch = mc.player.getXRot();
        camera = new RemotePlayer(mc.level, mc.player.getGameProfile());
        camera.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        camera.setYRot(playerYaw);
        camera.setXRot(playerPitch);
        camera.setYHeadRot(playerYaw);
        mc.player.input = new ClientInput();
        mc.setCameraEntity(camera);
    }

    public static void stop(Minecraft mc) {
        if (camera == null) return;
        // Never reset the camera to a stale player while disconnecting.
        if (mc.player != null) {
            // Never install the previous world's input on a new player entity.
            if (mc.player == owner) {
                if (originalInput != null) mc.player.input = originalInput;
                mc.player.setYRot(playerYaw);
                mc.player.setXRot(playerPitch);
            }
            mc.setCameraEntity(mc.player);
        }
        owner = null;
        camera = null;
        originalInput = null;
        originalLevel = null;
    }

    public static Vec3 cameraPosition() {
        return camera == null ? null : camera.position();
    }

    public static void tick(Minecraft mc) {
        if (camera == null) return;
        if (!DeathCompass.data.enabled || !DeathCompass.data.freecamEnabled
                || mc.player == null || mc.level == null || originalLevel != mc.level || owner != mc.player
                || mc.player.isDeadOrDying() || mc.getCameraEntity() != camera) {
            stop(mc);
            return;
        }
        // Vanilla mouse input normally turns the player. Re-route the observed
        // rotation difference to the detached camera and restore player rotation
        // before the next player movement tick.
        float yawChange = net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot() - playerYaw);
        float pitchChange = mc.player.getXRot() - playerPitch;
        if (Math.abs(yawChange) < 90.0f && Math.abs(pitchChange) < 90.0f) {
            camera.setYRot(camera.getYRot() + yawChange);
            camera.setXRot(net.minecraft.util.Mth.clamp(camera.getXRot() + pitchChange, -90f, 90f));
        }
        camera.setYHeadRot(camera.getYRot());
        mc.player.setYRot(playerYaw);
        mc.player.setXRot(playerPitch);

        if (mc.gui.screen() != null) return;

        double forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        double strafe = (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0);
        double vertical = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
        double length = Math.sqrt(forward * forward + strafe * strafe + vertical * vertical);
        if (length < 0.001) return;
        float yaw = (float) Math.toRadians(camera.getYRot());
        double speed = Math.max(0.10, Math.min(3.0, DeathCompass.data.freecamSpeed / 10.0));
        if (mc.options.keySprint.isDown()) speed *= 2.5;
        // Keep the controls horizontal; Space / Shift fly up and down.
        double dx = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) * speed / length;
        double dz = ( Math.cos(yaw) * forward + Math.sin(yaw) * strafe) * speed / length;
        camera.setPos(camera.getX() + dx, camera.getY() + vertical * speed / length, camera.getZ() + dz);
    }
}
