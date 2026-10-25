package net.yizuz.playerogrammod.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.yizuz.playerogrammod.hologram.HologramVisuals;
import net.yizuz.playerogrammod.ring.RingSessionManager;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Server-authoritative player-model hologram. It only follows its ring owner;
 * all AI work stays in the existing chat pipeline.
 */
public class HologramEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_SKIN_USERNAME =
            SynchedEntityData.defineId(HologramEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SKIN_TEXTURE =
            SynchedEntityData.defineId(HologramEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SKIN_TEXTURE_SIGNATURE =
            SynchedEntityData.defineId(HologramEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_DEMATERIALIZE_TICKS =
            SynchedEntityData.defineId(HologramEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID ownerUUID;
    @Nullable
    private UUID sessionToken;
    @Nullable
    private UUID ringId;

    public HologramEntity(EntityType<? extends HologramEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.getNavigation().setCanFloat(true);
        this.setCustomNameVisible(false);
        this.setSkinUsername("");
        this.setDematerializeTicks(-1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HologramFollowGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN_USERNAME, "");
        builder.define(DATA_SKIN_TEXTURE, "");
        builder.define(DATA_SKIN_TEXTURE_SIGNATURE, "");
        builder.define(DATA_DEMATERIALIZE_TICKS, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }

        int remaining = this.getDematerializeTicks();
        if (remaining >= 0) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            int next = remaining - 1;
            this.setDematerializeTicks(next);
            if (next < 0) {
                this.discard();
            }
            return;
        }

        if (this.tickCount % 20 == 0 && !this.hasValidSession()) {
            this.beginDematerializing();
        }
    }

    public void setOwner(@Nullable UUID ownerUUID, @Nullable UUID sessionToken) {
        this.ownerUUID = ownerUUID;
        this.sessionToken = sessionToken;
    }

    public void setRingId(@Nullable UUID ringId) {
        this.ringId = ringId;
    }

    @Nullable
    public UUID getRingId() {
        return this.ringId;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    @Nullable
    public ServerPlayer getOwner() {
        if (this.ownerUUID == null || this.level().getServer() == null) {
            return null;
        }
        return this.level().getServer().getPlayerList().getPlayer(this.ownerUUID);
    }

    private boolean hasValidSession() {
        ServerPlayer owner = this.getOwner();
        return owner != null
                && !owner.isRemoved()
                && owner.isAlive()
                && owner.level() == this.level()
                && RingSessionManager.get().isCurrent(this.ownerUUID, this.sessionToken);
    }

    public void beginDematerializing() {
        if (this.level().isClientSide() || this.isDematerializing()) {
            return;
        }
        this.setDematerializeTicks(HologramVisuals.DEMATERIALIZE_TICKS);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setCustomNameVisible(false);
    }

    public boolean isDematerializing() {
        return this.getDematerializeTicks() >= 0;
    }

    public int getDematerializeTicks() {
        return this.entityData.get(DATA_DEMATERIALIZE_TICKS);
    }

    public void setDematerializeTicks(int ticks) {
        this.entityData.set(DATA_DEMATERIALIZE_TICKS, ticks);
    }

    public String getSkinUsername() {
        return this.entityData.get(DATA_SKIN_USERNAME);
    }

    public void setSkinUsername(String username) {
        this.entityData.set(DATA_SKIN_USERNAME, username == null ? "" : username);
    }

    public String getSkinTexture() {
        return this.entityData.get(DATA_SKIN_TEXTURE);
    }

    public void setSkinTexture(String texture) {
        this.entityData.set(DATA_SKIN_TEXTURE, texture == null ? "" : texture);
    }

    public String getSkinTextureSignature() {
        return this.entityData.get(DATA_SKIN_TEXTURE_SIGNATURE);
    }

    public void setSkinTextureSignature(String signature) {
        this.entityData.set(DATA_SKIN_TEXTURE_SIGNATURE, signature == null ? "" : signature);
    }

    public void setSkinProfile(@Nullable GameProfile profile) {
        if (profile == null) {
            return;
        }
        this.setSkinUsername(profile.getName());
        Property textures = null;
        if (profile.getProperties().get("textures") != null) {
            textures = profile.getProperties().get("textures").stream().findFirst().orElse(null);
        }
        this.setSkinTexture(textures == null ? "" : textures.value());
        this.setSkinTextureSignature(textures == null || textures.signature() == null
                ? "" : textures.signature());
    }

    public void setDisplayName(String displayName, boolean nameplate) {
        String text = displayName == null ? "" : displayName.trim();
        if (text.isEmpty()) {
            text = this.getSkinUsername();
        }
        if (text.isEmpty()) {
            text = "Hologram";
        }
        this.setCustomName(Component.literal(text));
        this.setCustomNameVisible(nameplate && !this.isDematerializing());
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(net.minecraft.world.entity.Entity entity) {
        return !(entity instanceof Player) && super.canCollideWith(entity);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // The ring session owns the lifecycle; never despawn naturally.
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerUUID != null) {
            tag.putUUID("Owner", this.ownerUUID);
        }
        if (this.sessionToken != null) {
            tag.putUUID("Session", this.sessionToken);
        }
        if (this.ringId != null) {
            tag.putUUID("RingId", this.ringId);
        }
        tag.putString("SkinUsername", this.getSkinUsername());
        tag.putString("SkinTexture", this.getSkinTexture());
        tag.putString("SkinSignature", this.getSkinTextureSignature());
        tag.putInt("DematerializeTicks", this.getDematerializeTicks());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.ownerUUID = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.sessionToken = tag.hasUUID("Session") ? tag.getUUID("Session") : null;
        this.ringId = tag.hasUUID("RingId") ? tag.getUUID("RingId") : null;
        this.setSkinUsername(tag.getString("SkinUsername"));
        this.setSkinTexture(tag.getString("SkinTexture"));
        this.setSkinTextureSignature(tag.getString("SkinSignature"));
        this.setDematerializeTicks(tag.contains("DematerializeTicks") ? tag.getInt("DematerializeTicks") : -1);
    }
}
