package net.yizuz.playerogrammod.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.yizuz.playerogrammod.entity.HologramEntity;

/**
 * Resolves the hologram skin through vanilla's SkinManager. The server syncs the
 * configured username plus the signed textures property; the client never does
 * its own HTTP skin lookup.
 */
final class HologramSkins {
    static PlayerSkin skin(HologramEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        String username = entity.getSkinUsername();
        String name = username == null || username.isBlank() ? "Hologram" : username;
        String texture = entity.getSkinTexture();
        String signature = entity.getSkinTextureSignature();

        if (texture != null && !texture.isBlank()) {
            GameProfile profile = new GameProfile(entity.getUUID(), name);
            profile.getProperties().put("textures", new Property("textures", texture,
                    signature == null || signature.isBlank() ? null : signature));
            PlayerSkin skin = minecraft.getSkinManager().getInsecureSkin(profile);
            return skin != null ? skin : DefaultPlayerSkin.get(entity.getUUID());
        }

        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(username);
            if (info != null) {
                return info.getSkin();
            }
        }

        GameProfile profile = new GameProfile(entity.getUUID(), name);
        PlayerSkin skin = minecraft.getSkinManager().getInsecureSkin(profile);
        return skin != null ? skin : DefaultPlayerSkin.get(entity.getUUID());
    }

    private HologramSkins() {
    }
}
