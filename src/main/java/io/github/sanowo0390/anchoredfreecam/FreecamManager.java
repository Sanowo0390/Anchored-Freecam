package io.github.sanowo0390.anchoredfreecam;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Monster;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Warden;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class FreecamManager {
    private final AnchoredFreecamPlugin plugin;
    private final Messages messages;
    private final Map<UUID, FreecamSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> bodyOwners = new HashMap<>();
    private final Map<UUID, Location> lastLegalLocations = new HashMap<>();
    private final Set<UUID> internalTeleports = new HashSet<>();
    private final Set<UUID> forwardedBodyDamage = new HashSet<>();
    private record CameraCorrection(FreecamSession session, Location destination) {}
    private final Map<UUID, CameraCorrection> pendingCorrections = new HashMap<>();
    private final Set<UUID> synchronizingAir = new HashSet<>();
    private final Map<UUID, Long> lastMovementTrace = new HashMap<>();
    private int environmentTicks;
    private final Map<UUID, Long> lastBoundaryNotice = new HashMap<>();
    private final BukkitTask aggroTask;
    private final BukkitTask environmentTask;

    FreecamManager(AnchoredFreecamPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.aggroTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::maintainBodyAggro,
                1L,
                2L
        );
        this.environmentTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::maintainBodyEnvironment,
                1L,
                1L
        );
    }

    boolean isActive(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    FreecamSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    boolean ignoresMobAggro(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    static boolean shouldHoverBody(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.isFlying();
    }

    boolean isProtectedMobTarget(LivingEntity target) {
        if (target instanceof Player player) return isActive(player) && ignoresMobAggro(player);
        if (target == null) return false;
        UUID ownerUuid = bodyOwners.get(target.getUniqueId());
        Player owner = ownerUuid == null ? null : plugin.getServer().getPlayer(ownerUuid);
        return owner != null && isActive(owner) && ignoresMobAggro(owner);
    }

    Mannequin getBody(Player player) {
        FreecamSession session = getSession(player);
        if (session == null || session.bodyUuid() == null) {
            return null;
        }
        Entity entity = plugin.getServer().getEntity(session.bodyUuid());
        return entity instanceof Mannequin mannequin ? mannequin : null;
    }

    boolean isOwnBody(Player player, Entity entity) {
        FreecamSession session = getSession(player);
        return session != null
                && session.bodyUuid() != null
                && session.bodyUuid().equals(entity.getUniqueId());
    }

    double getMaxDistance() {
        if (plugin.getConfig().contains("max-distance-blocks")) {
            return Math.max(0.1D, plugin.getConfig().getDouble("max-distance-blocks", 15.0D));
        }
        return Math.max(0.1D, plugin.getConfig().getDouble("max-distance", 15.0D));
    }

    boolean start(Player player) {
        if (isActive(player)) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage(message(messages.text("spectator-denied"), NamedTextColor.RED));
            return false;
        }
        if (player.isInsideVehicle()) {
            player.sendMessage(message(messages.text("vehicle-denied"), NamedTextColor.RED));
            return false;
        }

        Location anchor = player.getLocation().clone();
        Mannequin body = null;
        if (plugin.getConfig().getBoolean("leave-body-at-anchor", true)) {
            try {
                body = spawnBody(player, anchor);
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("Failed to create freecam body for " + player.getName() + ": " + ex.getMessage());
                player.sendMessage(message(messages.text("body-spawn-failed"), NamedTextColor.RED));
                return false;
            }
        }

        UUID bodyUuid = body == null ? null : body.getUniqueId();
        FreecamSession session = new FreecamSession(
                anchor,
                bodyUuid,
                player.getAllowFlight(),
                player.isFlying(),
                player.isInvulnerable(),
                player.isCollidable(),
                player.isInvisible(),
                player.isVisibleByDefault(),
                player.isGliding(),
                player.hasGravity(),
                player.getRemainingAir(),
                player.getFallDistance(),
                shouldHoverBody(player)
        );
        sessions.put(player.getUniqueId(), session);
        lastLegalLocations.put(player.getUniqueId(), anchor.clone());

        if (bodyUuid != null) {
            bodyOwners.put(bodyUuid, player.getUniqueId());
        }

        if (player.isGliding()) {
            player.setGliding(false);
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setGravity(false);
        player.setSwimming(false);
        player.setFallDistance(0.0F);

        // The moving real Player is only the camera. Keep it visually hidden.
        // Mob hostility is maintained against the anchored Mannequin separately.
        player.setInvisible(true);
        if (plugin.getConfig().getBoolean("hide-camera-player-from-others", true)) {
            player.setVisibleByDefault(false);
        }

        if (plugin.getConfig().getBoolean("protect-camera-player", true)) {
            player.setInvulnerable(true);
        }
        if (plugin.getConfig().getBoolean("disable-camera-entity-collision", true)) {
            player.setCollidable(false);
        }

        applyCameraVisibility(player);
        hideCameraHand(player);

        if (body != null) {
            retargetCurrentEnemies(player, body);
        }

        trace(player, "start");

        player.sendMessage(message(
                messages.text("freecam-enabled", "range", trimDistance(getMaxDistance())),
                NamedTextColor.GREEN));
        return true;
    }

    boolean stop(Player player, boolean returnToAnchor, boolean sendMessage) {
        trace(player, "stop return=" + returnToAnchor);
        FreecamSession session = sessions.remove(player.getUniqueId());
        clearTransientState(player.getUniqueId());

        if (session == null) {
            return false;
        }

        Mannequin body = getBody(session);
        int finalAir = getBodyAir(session);
        Location returnLocation = body != null && body.isValid()
                ? body.getLocation().clone()
                : session.anchor().clone();
        Vector bodyVelocity = body != null && body.isValid()
                ? body.getVelocity().clone()
                : new Vector();
        float bodyFallDistance = body != null && body.isValid()
                ? body.getFallDistance()
                : session.fallDistance();
        boolean bodyPhysical = !session.bodyHovering() && body != null && body.isValid()
                && (!body.isOnGround() || WaterContact.touches(body));

        removeBody(session);

        if (returnToAnchor) {
            internalTeleports.add(player.getUniqueId());
            try {
                player.teleport(returnLocation, PlayerTeleportEvent.TeleportCause.PLUGIN);
            } finally {
                internalTeleports.remove(player.getUniqueId());
            }
        }

        restoreState(player, session);
        player.setRemainingAir(finalAir);
        if (returnToAnchor && bodyPhysical) {
            player.setFallDistance(bodyFallDistance);
            player.setVelocity(bodyVelocity);
        }
        if (returnToAnchor && session.bodyHovering()) {
            player.setFallDistance(0.0F);
            player.setVelocity(new Vector());
        }
        if (sendMessage && player.isOnline()) {
            player.sendMessage(message(
                    messages.text(returnToAnchor ? "freecam-disabled-return" : "freecam-disabled"),
                    NamedTextColor.YELLOW));
        }
        return true;
    }

    void stopWithoutReturn(Player player, boolean sendMessage) {
        trace(player, "stop external-teleport");
        FreecamSession session = sessions.remove(player.getUniqueId());
        clearTransientState(player.getUniqueId());

        if (session == null) {
            return;
        }

        syncAnchorToBody(session);
        int finalAir = getBodyAir(session);
        removeBody(session);
        restoreState(player, session);
        player.setRemainingAir(finalAir);

        if (sendMessage && player.isOnline()) {
            player.sendMessage(message(messages.text("freecam-disabled"), NamedTextColor.YELLOW));
        }
    }

    boolean isInternalTeleport(Player player) {
        return internalTeleports.contains(player.getUniqueId());
    }

    boolean isForwardedBodyDamage(Player player) {
        return forwardedBodyDamage.contains(player.getUniqueId());
    }

    void updateLastLegalLocation(Player player, Location location) {
        FreecamSession session = getSession(player);
        if (session == null || location.getWorld() != session.anchor().getWorld()) {
            return;
        }

        double max = getMaxDistance();
        if (location.distanceSquared(session.anchor()) <= max * max) {
            lastLegalLocations.put(player.getUniqueId(), location.clone());
        }
    }

    void queueBoundaryReturn(Player player, float yaw, float pitch) {
        FreecamSession session = getSession(player);
        if (session == null) return;
        Location safe = player.getLocation();
        double max = getMaxDistance();
        if (safe.getWorld() != session.anchor().getWorld()
                || safe.distanceSquared(session.anchor()) > max * max) {
            Location last = lastLegalLocations.get(player.getUniqueId());
            if (last != null && last.getWorld() == session.anchor().getWorld()
                    && last.distanceSquared(session.anchor()) <= max * max) safe = last;
        }
        safe = safe.clone();
        safe.setYaw(yaw);
        safe.setPitch(pitch);
        queueCameraCorrection(player, safe);
    }

    void queueCameraCorrection(Player player, Location destination) {
        FreecamSession session = getSession(player);
        if (session == null) return;
        UUID uuid = player.getUniqueId();
        CameraCorrection previous = pendingCorrections.put(uuid, new CameraCorrection(session, destination.clone()));
        if (previous != null && previous.session() == session) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            CameraCorrection correction = pendingCorrections.get(uuid);
            if (correction == null || correction.session() != session) return;
            pendingCorrections.remove(uuid);
            if (!player.isOnline() || getSession(player) != session) return;
            syncAnchorToBody(session);
            Location safe = correction.destination().clone();
            double max = getMaxDistance();
            if (safe.getWorld() != session.anchor().getWorld()
                    || safe.distanceSquared(session.anchor()) > max * max) {
                safe = nearestRangeLocation(player, session, safe);
                if (safe == null) {
                    trace(player, "boundary-no-clear-destination");
                    return;
                }
            }
            internalTeleports.add(uuid);
            try {
                if (player.teleport(safe, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                    updateLastLegalLocation(player, player.getLocation());
                    player.setFallDistance(0.0F);
                }
            } finally {
                internalTeleports.remove(uuid);
            }
        });
    }

    private Location nearestRangeLocation(Player player, FreecamSession session, Location requested) {
        Location anchor = session.anchor();
        if (requested.getWorld() != anchor.getWorld()) {
            Location result = anchor.clone();
            result.setYaw(requested.getYaw());
            result.setPitch(requested.getPitch());
            return result;
        }
        Vector offset = requested.toVector().subtract(anchor.toVector());
        double distance = offset.length();
        if (distance == 0) return requested.clone();
        Vector direction = offset.multiply(1.0 / distance);
        // Keep the view near the limit even if the body moved or the range was
        // reduced. Never replace an invalid saved edge position with the origin.
        double radius = Math.max(0, getMaxDistance() - Math.min(0.05, getMaxDistance() * 0.01));
        for (double remaining = Math.min(distance, radius); remaining >= 0; remaining -= 0.25) {
            Location candidate = anchor.clone().add(direction.clone().multiply(remaining));
            candidate.setYaw(requested.getYaw());
            candidate.setPitch(requested.getPitch());
            if (!player.collidesAt(candidate)) return candidate;
        }
        return null;
    }

    void hideCameraHand(Player player) {
        if (isActive(player)) {
            // Client-only equipment update: the real inventory and the body's
            // copied equipment remain untouched, including on disconnect/crash.
            player.sendEquipmentChange(player, EquipmentSlot.HAND, ItemStack.empty());
        }
    }

    void refreshCameraHandNextTick(Player player) {
        FreecamSession session = getSession(player);
        if (session == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || getSession(player) != session) return;
            // Restore the previously selected slot, then hide the current one.
            player.updateInventory();
            hideCameraHand(player);
        });
    }

    boolean handleBodyDamage(EntityDamageEvent event) {
        UUID ownerUuid = bodyOwners.get(event.getEntity().getUniqueId());
        if (ownerUuid == null) {
            return false;
        }

        event.setCancelled(true);

        Player player = plugin.getServer().getPlayer(ownerUuid);
        if (player == null || !player.isOnline() || !isActive(player)) {
            bodyOwners.remove(event.getEntity().getUniqueId());
            event.getEntity().remove();
            return true;
        }

        // A creative proxy must not turn an otherwise harmless mob hit into
        // an exit from freecam, even if another plugin targets it directly.
        if (ignoresMobAggro(player)) return true;

        FreecamSession session = getSession(player);
        if (session != null) {
            syncAnchorToBody(session);
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING
                && event.getEntity() instanceof Mannequin body
                && !body.isUnderWater()) {
            trace(player, "body-drowning-ignored");
            body.setRemainingAir(body.getMaximumAir());
            return true;
        }

        trace(player, "body-damage cause=" + event.getCause() + " damage=" + event.getDamage());

        double damage = event.getDamage();
        DamageSource damageSource = event.getDamageSource();

        if (plugin.getConfig().getBoolean("exit-on-body-damage", true)) {
            stop(player, true, false);
            player.sendMessage(message(messages.text("body-damaged"), NamedTextColor.RED));
            player.damage(damage, damageSource);
            return true;
        }

        boolean wasInvulnerable = player.isInvulnerable();
        forwardedBodyDamage.add(player.getUniqueId());
        try {
            player.setInvulnerable(false);
            player.damage(damage, damageSource);
        } finally {
            forwardedBodyDamage.remove(player.getUniqueId());
            if (player.isOnline() && isActive(player)) {
                player.setInvulnerable(wasInvulnerable);
            }
        }
        return true;
    }

    void applyCameraVisibility(Player freecamPlayer) {
        if (!plugin.getConfig().getBoolean("hide-camera-player-from-others", true)) {
            return;
        }

        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(freecamPlayer.getUniqueId())) {
                viewer.hidePlayer(plugin, freecamPlayer);
            }
        }
    }

    void hideActiveCamerasFrom(Player viewer) {
        if (!plugin.getConfig().getBoolean("hide-camera-player-from-others", true)) {
            return;
        }

        for (UUID uuid : sessions.keySet()) {
            Player freecamPlayer = plugin.getServer().getPlayer(uuid);
            if (freecamPlayer != null && !viewer.getUniqueId().equals(uuid)) {
                viewer.hidePlayer(plugin, freecamPlayer);
            }
        }
    }

    void refreshVisibility() {
        for (UUID uuid : sessions.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) {
                continue;
            }
            restoreVisibility(player);
            applyCameraVisibility(player);
        }
    }

    Location redirectedTeleportDestination(Player teleportedPlayer, Location requested) {
        if (!plugin.getConfig().getBoolean("redirect-teleports-to-body", true)) {
            return null;
        }

        double matchRadius = Math.max(0.05D,
                plugin.getConfig().getDouble("teleport-camera-match-radius-blocks", 0.75D));
        double maxSquared = matchRadius * matchRadius;

        for (Map.Entry<UUID, FreecamSession> entry : sessions.entrySet()) {
            if (entry.getKey().equals(teleportedPlayer.getUniqueId())) {
                continue;
            }

            Player freecamPlayer = plugin.getServer().getPlayer(entry.getKey());
            if (freecamPlayer == null || !freecamPlayer.isOnline()) {
                continue;
            }

            Location camera = freecamPlayer.getLocation();
            if (camera.getWorld() != requested.getWorld()) {
                continue;
            }

            if (camera.distanceSquared(requested) <= maxSquared) {
                Mannequin body = getBody(freecamPlayer);
                return body != null && body.isValid()
                        ? body.getLocation().clone()
                        : entry.getValue().anchor().clone();
            }
        }

        return null;
    }

    void enforceCurrentRange() {
        double max = getMaxDistance();
        double maxSquared = max * max;

        for (UUID uuid : sessions.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            FreecamSession session = sessions.get(uuid);
            if (player == null || session == null || !player.isOnline()) {
                continue;
            }

            Location current = player.getLocation();
            if (current.getWorld() != session.anchor().getWorld()
                    || current.distanceSquared(session.anchor()) > maxSquared) {
                boundaryNotice(player);
                queueBoundaryReturn(player, current.getYaw(), current.getPitch());
            }
        }
    }

    void boundaryNotice(Player player) {
        if (!plugin.getConfig().getBoolean("show-boundary-message", true)) {
            return;
        }

        long now = System.currentTimeMillis();
        long previous = lastBoundaryNotice.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 750L) {
            return;
        }

        lastBoundaryNotice.put(player.getUniqueId(), now);
        player.sendActionBar(Component.text(
                messages.text("boundary-return", "range", trimDistance(getMaxDistance())),
                NamedTextColor.RED));
    }

    void shutdown() {
        aggroTask.cancel();
        environmentTask.cancel();

        for (UUID uuid : Set.copyOf(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                stop(player, true, false);
            } else {
                FreecamSession session = sessions.remove(uuid);
                clearTransientState(uuid);
                if (session != null) {
                    removeBody(session);
                }
            }
        }
        bodyOwners.clear();
    }

    private void maintainBodyEnvironment() {
        environmentTicks++;
        for (Map.Entry<UUID, FreecamSession> entry : Map.copyOf(sessions).entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            FreecamSession session = entry.getValue();

            if (player == null || !player.isOnline()) {
                continue;
            }

            Mannequin body = getBody(player);
            if (body != null && body.isValid()) {
                syncAnchorToBody(session);

                // A body in water retains native current/buoyancy physics. Only
                // a dry grounded body is anchored; falling into water stays physical.
                boolean physical = !body.isOnGround() || WaterContact.touches(body);
                if (session.bodyHovering()) {
                    // Use the state captured BEFORE enabling camera flight.
                    // Survival cameras also fly, but their bodies must still fall.
                    body.setVelocity(new Vector());
                    body.setFallDistance(0.0F);
                    body.setGravity(false);
                    body.setImmovable(true);
                } else if (physical) {
                    body.setImmovable(false);
                    body.setGravity(true);
                } else if (!body.isImmovable()) {
                    body.setVelocity(new Vector());
                    body.setGravity(false);
                    body.setImmovable(true);
                    syncAnchorToBody(session);
                }

                // The freecam radius follows the real body while it is falling.
                double max = getMaxDistance();
                Location currentCamera = player.getLocation();
                if (currentCamera.getWorld() == session.anchor().getWorld()
                        && currentCamera.distanceSquared(session.anchor()) <= max * max) {
                    lastLegalLocations.put(player.getUniqueId(), currentCamera.clone());
                } else {
                    queueBoundaryReturn(player, currentCamera.getYaw(), currentCamera.getPitch());
                }
            }

            // The camera is a detached viewpoint. Keep normal flying active and
            // prevent water/gravity from turning the camera into a physical body.
            player.setAllowFlight(true);
            if (!player.isFlying()) {
                player.setFlying(true);
            }
            if (player.hasGravity()) {
                player.setGravity(false);
            }
            if (player.isSwimming()) {
                player.setSwimming(false);
            }

            // Water must not physically carry the detached camera upward.
            // isInWater() can oscillate at the surface, so also check the feet
            // and eye blocks to keep the correction active across the boundary.
            boolean cameraTouchingWater = WaterContact.touches(player);
            if (cameraTouchingWater) {
                Vector velocity = WaterContact.cameraVelocity(player.getCurrentInput(), player.getVelocity());
                if (!velocity.equals(player.getVelocity())) player.setVelocity(velocity);
            }

            player.setFallDistance(0.0F);
            hideCameraHand(player);

            // Survival air belongs to the anchored body, not the moving camera.
            int air = body != null && body.isValid()
                    ? body.getRemainingAir()
                    : session.remainingAir();
            int clampedAir = Math.max(0, Math.min(air, player.getMaximumAir()));

            if (player.getRemainingAir() != clampedAir) {
                synchronizingAir.add(player.getUniqueId());
                try {
                    player.setRemainingAir(clampedAir);
                } finally {
                    synchronizingAir.remove(player.getUniqueId());
                }
            }
            if (environmentTicks % 20 == 0) trace(player, "environment");
        }
    }

    boolean isSynchronizingAir(Player player) {
        return synchronizingAir.contains(player.getUniqueId());
    }

    void trace(Player player, String reason) {
        if (!plugin.getConfig().getBoolean("debug-water", false) || !isActive(player)) return;
        if (reason.equals("water-move-correction")) {
            long now = System.nanoTime();
            Long previous = lastMovementTrace.get(player.getUniqueId());
            if (previous != null && now - previous < 1_000_000_000L) return;
            lastMovementTrace.put(player.getUniqueId(), now);
        }
        Mannequin body = getBody(player);
        Input input = player.getCurrentInput();
        plugin.getLogger().info("[water-debug] player=" + player.getName() + " reason=" + reason
                + " camera=" + player.getLocation() + " velocity=" + player.getVelocity()
                + " flying=" + player.isFlying() + " allowFlight=" + player.getAllowFlight()
                + " swimming=" + player.isSwimming() + " water=" + WaterContact.touches(player)
                + " air=" + player.getRemainingAir() + " jump=" + input.isJump() + " sneak=" + input.isSneak()
                + " forward=" + input.isForward() + " backward=" + input.isBackward()
                + " left=" + input.isLeft() + " right=" + input.isRight()
                + (body == null ? " body=none" : " body=" + body.getLocation() + " velocity=" + body.getVelocity()
                + " ground=" + body.isOnGround() + " immovable=" + body.isImmovable()
                + " underwater=" + body.isUnderWater() + " air=" + body.getRemainingAir()
                + " pose=" + body.getPose() + " fall=" + body.getFallDistance()));
    }

    private Mannequin getBody(FreecamSession session) {
        if (session.bodyUuid() == null) {
            return null;
        }

        Entity entity = plugin.getServer().getEntity(session.bodyUuid());
        return entity instanceof Mannequin mannequin ? mannequin : null;
    }

    private void syncAnchorToBody(FreecamSession session) {
        Mannequin body = getBody(session);
        if (body == null || !body.isValid()) {
            return;
        }

        Location bodyLocation = body.getLocation();
        Location anchor = session.anchor();
        anchor.setWorld(bodyLocation.getWorld());
        anchor.setX(bodyLocation.getX());
        anchor.setY(bodyLocation.getY());
        anchor.setZ(bodyLocation.getZ());
        anchor.setYaw(bodyLocation.getYaw());
        anchor.setPitch(bodyLocation.getPitch());
    }

    private int getBodyAir(FreecamSession session) {
        if (session.bodyUuid() == null) {
            return session.remainingAir();
        }

        Entity entity = plugin.getServer().getEntity(session.bodyUuid());
        if (entity instanceof Mannequin body && body.isValid()) {
            return body.getRemainingAir();
        }

        return session.remainingAir();
    }

    private void maintainBodyAggro() {
        if (!plugin.getConfig().getBoolean("force-hostile-mob-aggro", true)) {
            return;
        }

        double radius = Math.max(1.0D, plugin.getConfig().getDouble("mob-aggro-radius-blocks", 32.0D));

        for (Map.Entry<UUID, FreecamSession> entry : sessions.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }

            Mannequin body = getBody(player);
            if (body == null || !body.isValid()) {
                continue;
            }

            for (Entity entity : body.getNearbyEntities(radius, radius, radius)) {
                if (!(entity instanceof Monster monster) || !monster.isValid() || !monster.isAware()) {
                    continue;
                }

                LivingEntity target = monster.getTarget();

                if (ignoresMobAggro(player)) {
                    if (target == body || target == player) monster.setTarget(null);
                    continue;
                }

                if (target == body) {
                    continue;
                }

                // Do not steal a mob from another legitimate target.
                if (target != null && target != player) {
                    continue;
                }

                // If the mob was already targeting the real camera Player, always
                // redirect it to the body, even for normally neutral monsters.
                if (target == player || shouldForceTargetBody(monster)) {
                    monster.setTarget(body);
                }
            }
        }
    }

    private boolean shouldForceTargetBody(Monster monster) {
        // These mobs are conditionally hostile in vanilla. Do not make them angry
        // just because freecam is enabled; if they were already targeting the
        // player, maintainBodyAggro() still redirects them to the body.
        if (monster instanceof Enderman) {
            return false;
        }
        if (monster instanceof Piglin && !(monster instanceof PiglinBrute)) {
            return false;
        }
        if (monster instanceof PigZombie) {
            return false;
        }
        if (monster instanceof Spider && !(monster instanceof CaveSpider)) {
            return false;
        }
        if (monster instanceof Warden) {
            return false;
        }

        return true;
    }

    private void retargetCurrentEnemies(Player player, Mannequin body) {
        for (Entity entity : player.getNearbyEntities(64.0, 64.0, 64.0)) {
            if (entity instanceof Monster monster && monster.getTarget() == player) {
                monster.setTarget(ignoresMobAggro(player) ? null : body);
            }
        }
    }

    private Mannequin spawnBody(Player player, Location anchor) {
        Mannequin body = player.getWorld().spawn(anchor, Mannequin.class, mannequin -> {
            mannequin.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            mannequin.setMainHand(player.getMainHand());

            boolean airborne = !shouldHoverBody(player)
                    && (!player.isOnGround() || WaterContact.touches(player));
            mannequin.setImmovable(!airborne);
            mannequin.setGravity(airborne);
            mannequin.setAI(false);
            mannequin.setCanPickupItems(false);
            mannequin.setCollidable(true);
            mannequin.setInvulnerable(ignoresMobAggro(player));
            mannequin.setPersistent(false);
            mannequin.setRemoveWhenFarAway(false);
            mannequin.setSilent(true);

            // Keep the player's name, but remove Mannequin's default
            // description line (e.g. "NPC") under the name.
            mannequin.setDescription(null);
            if (plugin.getConfig().getBoolean("show-body-nameplate", true)) {
                mannequin.customName(Component.text(player.getName()));
                mannequin.setCustomNameVisible(true);
            }

            if (Mannequin.validPoses().contains(player.getPose())) {
                mannequin.setPose(player.getPose(), true);
            }

            EntityEquipment equipment = mannequin.getEquipment();
            equipment.setHelmet(copy(player.getInventory().getHelmet()));
            equipment.setChestplate(copy(player.getInventory().getChestplate()));
            equipment.setLeggings(copy(player.getInventory().getLeggings()));
            equipment.setBoots(copy(player.getInventory().getBoots()));
            equipment.setItemInMainHand(copy(player.getInventory().getItemInMainHand()));
            equipment.setItemInOffHand(copy(player.getInventory().getItemInOffHand()));

            mannequin.setMaxHealth(player.getMaxHealth());
            mannequin.setHealth(Math.min(player.getHealth(), mannequin.getMaxHealth()));
            mannequin.setAbsorptionAmount(player.getAbsorptionAmount());
            mannequin.setMaximumAir(player.getMaximumAir());
            mannequin.setRemainingAir(player.getRemainingAir());
            // Preserve starting breathing / slow-falling effects on the physical
            // body as well as its equipment. Native duration ticking remains active.
            mannequin.addPotionEffects(player.getActivePotionEffects());
        });

        body.setRotation(anchor.getYaw(), anchor.getPitch());
        if (!body.isImmovable()) {
            body.setVelocity(player.getVelocity().clone());
            body.setFallDistance(player.getFallDistance());
        }
        return body;
    }

    private ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    private void removeBody(FreecamSession session) {
        UUID bodyUuid = session.bodyUuid();
        if (bodyUuid == null) {
            return;
        }

        bodyOwners.remove(bodyUuid);

        Entity body = plugin.getServer().getEntity(bodyUuid);
        if (body != null) {
            body.remove();
        }
    }

    private void restoreState(Player player, FreecamSession session) {
        restoreVisibility(player);
        player.setInvulnerable(session.invulnerable());
        player.setCollidable(session.collidable());
        player.setInvisible(session.invisible());
        player.setVisibleByDefault(session.visibleByDefault());
        player.setGravity(session.gravity());
        player.setAllowFlight(session.allowFlight());
        player.setFlying(session.allowFlight() && session.flying());
        player.setGliding(session.gliding());
        player.setFallDistance(session.fallDistance());
        player.updateInventory();
    }

    private void restoreVisibility(Player player) {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                viewer.showPlayer(plugin, player);
            }
        }
    }

    private void clearTransientState(UUID uuid) {
        lastLegalLocations.remove(uuid);
        pendingCorrections.remove(uuid);
        lastBoundaryNotice.remove(uuid);
        lastMovementTrace.remove(uuid);
        synchronizingAir.remove(uuid);
    }

    private Component message(String text, NamedTextColor color) {
        return Component.text("[AnchoredFreecam] ", NamedTextColor.AQUA)
                .append(Component.text(text, color));
    }

    private String trimDistance(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }
}
