package net.m1227.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/** Where preview effects from the menu are played: ahead and to the right of the player. */
public final class Preview {
    private Preview() {}

    /** Returns {x, y(feet), z, forwardX, forwardZ} or null when not in a world. */
    public static double[] ahead(double dist, double side) {
        double[] s = PreviewMode.spot();
        if (s != null) return s.clone(); // live preview: centre stage, on the stand-in
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) return null;
        double yaw = Math.toRadians(p.rotationYaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        return new double[]{p.posX + fx * dist + rx * side, p.posY, p.posZ + fz * dist + rz * side, fx, fz};
    }
}
