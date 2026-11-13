package net.m1227.client.ui;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;

/**
 * Your own skin outside the world (main menu, cosmetics preview): requested once from your
 * signed-in profile through Minecraft's skin manager; the default skin is used until it arrives.
 */
public final class LocalSkin {
    private static ResourceLocation skin;
    private static boolean slim, requested;

    private LocalSkin() {}

    public static void request() {
        if (requested) return;
        requested = true;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            GameProfile profile = mc.getSession().getProfile();
            skin = DefaultPlayerSkin.getDefaultSkin(profile.getId());
            slim = "slim".equals(DefaultPlayerSkin.getSkinType(profile.getId()));
            mc.getSkinManager().loadProfileTextures(profile, new SkinManager.SkinAvailableCallback() {
                @Override
                public void skinAvailable(MinecraftProfileTexture.Type type, ResourceLocation location, MinecraftProfileTexture tex) {
                    if (type != MinecraftProfileTexture.Type.SKIN) return;
                    skin = location;
                    slim = "slim".equals(tex == null ? null : tex.getMetadata("model"));
                }
            }, false);
        } catch (Throwable t) {
            skin = DefaultPlayerSkin.getDefaultSkinLegacy();
            slim = false;
        }
    }

    public static ResourceLocation skin() {
        request();
        return skin;
    }

    public static boolean slim() {
        return slim;
    }
}
