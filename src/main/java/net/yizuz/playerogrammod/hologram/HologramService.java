package net.yizuz.playerogrammod.hologram;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.yizuz.playerogrammod.entity.HologramEntity;
import net.yizuz.playerogrammod.entity.ModEntities;
import net.yizuz.playerogrammod.ring.RingSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * Server-side hologram lifecycle. The ring session manager owns activation;
 * this service maps each active owner to one hologram and handles spawning,
 * dematerialization, config refreshes, and cleanup.
 */
public final class HologramService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HologramService.class);
    private static final HologramService INSTANCE = new HologramService();
    private static final double SPAWN_LOOK_RANGE = 8.0D;
    private static final double SPAWN_FORWARD_DISTANCE = 2.5D;
    private static final double MIN_SPAWN_DISTANCE_SQ = 0.75D * 0.75D;

    private final HologramRegistry registry = new HologramRegistry();
    private final Map<UUID, ServerLevel> levels = new HashMap<>();
    private final Map<UUID, HologramEntity> fading = new HashMap<>();

    private volatile MinecraftServer server;
    private int tickCounter;

    private HologramService() {
    }

    public static HologramService get() {
        return INSTANCE;
    }

    public void onServerStarting(MinecraftServer server) {
        this.server = server;
        this.registry.clear();
        this.levels.clear();
        this.fading.clear();
        this.tickCounter = 0;
    }

    public void onServerStopping() {
        this.removeAll();
        this.server = null;
    }

    public void spawn(MinecraftServer server, ServerPlayer owner, UUID token) {
        if (server == null || owner == null || token == null
                || owner.isRemoved() || !owner.isAlive()) {
            return;
        }
        this.server = server;
        HologramSettings settings = HologramSettings.current();
        if (!settings.enabled()) {
            return;
        }

        UUID ownerId = owner.getUUID();
        this.removeNow(ownerId);

        ServerLevel level = owner.serverLevel();
        HologramEntity entity = new HologramEntity(ModEntities.HOLOGRAM.get(), level);
        entity.setOwner(ownerId, token);

        String skinName = settings.resolveSkinUsername(owner.getGameProfile().getName());
        String displayName = settings.resolveDisplayName(skinName);
        entity.setSkinUsername(skinName);
        entity.setDisplayName(displayName, settings.nameplate());

        Vec3 spawn = findSpawn(level, owner, entity);
        entity.moveTo(spawn.x, spawn.y, spawn.z, owner.getYRot(), 0.0F);
        if (!level.addFreshEntity(entity)) {
            LOGGER.warn("Could not add the hologram for {} to the world.", ownerId);
            return;
        }

        this.registry.put(ownerId, token, entity.getUUID());
        this.levels.put(ownerId, level);
        this.applyProfile(entity, owner, skinName);
        LOGGER.info("Spawned hologram {} for {} (skin={}, display={}).",
                entity.getUUID(), ownerId, skinName, displayName);
    }

    public void beginDespawn(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        HologramRegistry.Entry entry = this.registry.remove(ownerId);
        ServerLevel level = this.levels.remove(ownerId);
        HologramEntity entity = entry == null || level == null ? null : find(level, entry.entityId());
        if (entity != null) {
            this.fading.put(ownerId, entity);
            entity.beginDematerializing();
            LOGGER.info("Hologram {} for {} is dematerializing.", entity.getUUID(), ownerId);
        }
    }

    public void removeNow(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        HologramRegistry.Entry entry = this.registry.remove(ownerId);
        ServerLevel level = this.levels.remove(ownerId);
        HologramEntity entity = entry == null || level == null ? null : find(level, entry.entityId());
        if (entity != null) {
            entity.discard();
        }
        HologramEntity fadingEntity = this.fading.remove(ownerId);
        if (fadingEntity != null) {
            fadingEntity.discard();
        }
    }

    public void onServerTick(MinecraftServer server) {
        if (server == null || this.server == null) {
            return;
        }
        if (++this.tickCounter % 20 != 0) {
            return;
        }
        this.fading.entrySet().removeIf(entry -> entry.getValue().isRemoved());

        HologramSettings settings = HologramSettings.current();
        if (!settings.enabled()) {
            return;
        }

        List<UUID> active = RingSessionManager.get().activePlayers();
        for (UUID ownerId : active) {
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner == null || owner.isRemoved() || !owner.isAlive()) {
                continue;
            }
            HologramRegistry.Entry entry = this.registry.get(ownerId);
            if (entry != null) {
                ServerLevel level = this.levels.get(ownerId);
                HologramEntity entity = level == null ? null : find(level, entry.entityId());
                if (entity != null && !entity.isRemoved()) {
                    continue;
                }
                this.registry.remove(ownerId);
                this.levels.remove(ownerId);
            }
            this.spawn(server, owner, RingSessionManager.get().token(ownerId));
        }
    }

    public void onConfigChanged() {
        MinecraftServer current = this.server;
        if (current == null) {
            return;
        }
        try {
            current.execute(this::applyConfigNow);
        } catch (RejectedExecutionException e) {
            LOGGER.debug("Could not apply the hologram config because the server is stopping.");
        }
    }

    private void applyConfigNow() {
        MinecraftServer current = this.server;
        if (current == null) {
            return;
        }
        HologramSettings settings = HologramSettings.current();
        if (!settings.enabled()) {
            for (UUID ownerId : List.copyOf(this.registry.owners())) {
                this.beginDespawn(ownerId);
            }
            return;
        }

        for (HologramRegistry.Entry entry : List.copyOf(this.registry.entries())) {
            ServerPlayer owner = current.getPlayerList().getPlayer(entry.owner());
            ServerLevel level = this.levels.get(entry.owner());
            HologramEntity entity = level == null ? null : find(level, entry.entityId());
            if (owner == null || owner.isRemoved() || !owner.isAlive() || entity == null) {
                continue;
            }
            String skinName = settings.resolveSkinUsername(owner.getGameProfile().getName());
            entity.setDisplayName(settings.resolveDisplayName(skinName), settings.nameplate());
            this.applyProfile(entity, owner, skinName);
        }
    }

    private void removeAll() {
        for (UUID ownerId : List.copyOf(this.registry.owners())) {
            this.removeNow(ownerId);
        }
        for (HologramEntity entity : List.copyOf(this.fading.values())) {
            entity.discard();
        }
        this.registry.clear();
        this.levels.clear();
        this.fading.clear();
    }

    private void applyProfile(HologramEntity entity, ServerPlayer owner, String skinName) {
        entity.setSkinUsername(skinName);
        GameProfile ownerProfile = owner.getGameProfile();
        if (skinName.equalsIgnoreCase(ownerProfile.getName())) {
            entity.setSkinProfile(ownerProfile);
            return;
        }

        MinecraftServer current = this.server;
        if (current == null || skinName.isBlank() || current.getProfileCache() == null) {
            return;
        }
        try {
            current.getProfileCache().getAsync(skinName).whenComplete((optional, error) -> {
                if (error != null) {
                    LOGGER.debug("Could not resolve the hologram skin '{}': {}", skinName, error.getMessage());
                    return;
                }
                optional.ifPresent(profile -> {
                    try {
                        current.execute(() -> {
                            if (!entity.isRemoved()) {
                                entity.setSkinProfile(profile);
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        LOGGER.debug("Could not apply the hologram skin because the server is stopping.");
                    }
                });
            });
        } catch (RuntimeException e) {
            LOGGER.debug("Could not start the hologram skin lookup for '{}': {}", skinName, e.getMessage());
        }
    }

    private static HologramEntity find(ServerLevel level, UUID entityId) {
        Entity entity = level.getEntity(entityId);
        return entity instanceof HologramEntity hologram ? hologram : null;
    }

    private static Vec3 findSpawn(ServerLevel level, ServerPlayer owner, HologramEntity entity) {
        Vec3 lookedAt = lookedAtSpawn(level, owner, entity);
        if (lookedAt != null) {
            return lookedAt;
        }

        Vec3 look = owner.getLookAngle();
        HologramSpawn.Point basePoint = HologramSpawn.forward(
                new HologramSpawn.Point(owner.getX(), owner.getY(), owner.getZ()),
                look.x, look.y, look.z, SPAWN_FORWARD_DISTANCE);
        Vec3 forward = new Vec3(basePoint.x(), basePoint.y(), basePoint.z());
        for (double offset : new double[] {0.0D, 1.0D, -1.0D, 2.0D, -2.0D}) {
            Vec3 candidate = forward.add(0.0D, offset, 0.0D);
            if (candidate.distanceToSqr(owner.position()) > MIN_SPAWN_DISTANCE_SQ
                    && isSafe(level, entity, candidate)) {
                return candidate;
            }
        }

        Vec3 base = owner.position();
        Vec3[] fallbacks = {
                base.add(1.5D, 0.0D, 0.0D),
                base.add(-1.5D, 0.0D, 0.0D),
                base.add(0.0D, 0.0D, 1.5D),
                base.add(0.0D, 0.0D, -1.5D),
                base
        };
        for (Vec3 candidate : fallbacks) {
            if (isSafe(level, entity, candidate)) {
                return candidate;
            }
        }
        return base;
    }

    private static Vec3 lookedAtSpawn(ServerLevel level, ServerPlayer owner, HologramEntity entity) {
        HitResult hit = owner.pick(SPAWN_LOOK_RANGE, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            return null;
        }
        BlockPos block = blockHit.getBlockPos();
        HologramSpawn.Point point = HologramSpawn.faceCenter(
                block.getX(), block.getY(), block.getZ(), spawnFace(blockHit.getDirection()));
        Vec3 candidate = new Vec3(point.x(), point.y(), point.z());
        if (candidate.distanceToSqr(owner.position()) <= MIN_SPAWN_DISTANCE_SQ) {
            return null;
        }
        return isSafe(level, entity, candidate) ? candidate : null;
    }

    private static HologramSpawn.Face spawnFace(Direction direction) {
        return switch (direction) {
            case UP -> HologramSpawn.Face.UP;
            case DOWN -> HologramSpawn.Face.DOWN;
            case NORTH -> HologramSpawn.Face.NORTH;
            case SOUTH -> HologramSpawn.Face.SOUTH;
            case WEST -> HologramSpawn.Face.WEST;
            case EAST -> HologramSpawn.Face.EAST;
        };
    }

    private static boolean isSafe(ServerLevel level, HologramEntity entity, Vec3 candidate) {
        AABB box = entity.getBoundingBox().move(
                candidate.x - entity.getX(),
                candidate.y - entity.getY(),
                candidate.z - entity.getZ());
        return level.noCollision(entity, box);
    }
}
