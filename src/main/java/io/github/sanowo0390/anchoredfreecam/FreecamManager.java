package io.github.sanowo0390.anchoredfreecam;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class FreecamManager {
    private final AnchoredFreecamPlugin plugin;
    private final Map<UUID, FreecamSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> bodyOwners = new HashMap<>();
    private final Set<UUID> internalTeleports = new HashSet<>();
    private final Set<UUID> forwardedBodyDamage = new HashSet<>();
    private final Map<UUID, Long> lastBoundaryNotice = new HashMap<>();

    FreecamManager(AnchoredFreecamPlugin plugin) {
        this.plugin = plugin;
    }

    boolean isActive(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    FreecamSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    Mannequin getBody(Player player) {
        FreecamSession session = getSession(player);
        if (session == null || session.bodyUuid() == null) {
            return null;
        }
        Entity entity = plugin.getServer().getEntity(session.bodyUuid());
        return entity instanceof Mannequin mannequin ? mannequin : null;
    }

    double getMaxDistance() {
        if (plugin.getConfig().contains("max-distance-blocks")) {
            return Math.max(0.1D, plugin.getConfig().getDouble("max-distance-blocks", 5.0D));
        }
        return Math.max(0.1D, plugin.getConfig().getDouble("max-distance", 5.0D));
    }

    boolean start(Player player) {
        if (isActive(player)) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage(message(
                    "Spectatorではブロック衝突を維持できないため開始できません。",
                    NamedTextColor.RED));
            return false;
        }
        if (player.isInsideVehicle()) {
            player.sendMessage(message("乗り物から降りてから使用してください。", NamedTextColor.RED));
            return false;
        }

        Location anchor = player.getLocation().clone();
        Mannequin body = null;
        if (plugin.getConfig().getBoolean("leave-body-at-anchor", true)) {
            try {
                body = spawnBody(player, anchor);
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("Failed to create freecam body for " + player.getName() + ": " + ex.getMessage());
                player.sendMessage(message("本体を生成できなかったためFreecamを開始できません。", NamedTextColor.RED));
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
                player.isGliding(),
                player.getFallDistance()
        );
        sessions.put(player.getUniqueId(), session);
        if (bodyUuid != null) {
            bodyOwners.put(bodyUuid, player.getUniqueId());
        }

        if (player.isGliding()) {
            player.setGliding(false);
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFallDistance(0.0F);
        player.setInvisible(true);

        if (plugin.getConfig().getBoolean("protect-camera-player", true)) {
            player.setInvulnerable(true);
        }
        if (plugin.getConfig().getBoolean("disable-camera-entity-collision", true)) {
            player.setCollidable(false);
        }
        applyCameraVisibility(player);

        player.sendMessage(message(
                "Freecam ON — 本体を残したまま、開始地点から "
                        + trimDistance(getMaxDistance()) + " マス以内を移動できます。",
                NamedTextColor.GREEN));
        return true;
    }

    boolean stop(Player player, boolean returnToAnchor, boolean sendMessage) {
        FreecamSession session = sessions.remove(player.getUniqueId());
        lastBoundaryNotice.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }

        removeBody(session);

        if (returnToAnchor) {
            internalTeleports.add(player.getUniqueId());
            try {
                player.teleport(session.anchor().clone(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            } finally {
                internalTeleports.remove(player.getUniqueId());
            }
        }

        restoreState(player, session);
        if (sendMessage && player.isOnline()) {
            player.sendMessage(message(
                    returnToAnchor ? "Freecam OFF — 本体の位置へ戻りました。" : "Freecamを終了しました。",
                    NamedTextColor.YELLOW));
        }
        return true;
    }

    void stopWithoutReturn(Player player, boolean sendMessage) {
        FreecamSession session = sessions.remove(player.getUniqueId());
        lastBoundaryNotice.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        removeBody(session);
        restoreState(player, session);
        if (sendMessage && player.isOnline()) {
            player.sendMessage(message("Freecamを終了しました。", NamedTextColor.YELLOW));
        }
    }

    boolean isInternalTeleport(Player player) {
        return internalTeleports.contains(player.getUniqueId());
    }

    boolean isForwardedBodyDamage(Player player) {
        return forwardedBodyDamage.contains(player.getUniqueId());
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

        double damage = event.getDamage();
        DamageSource damageSource = event.getDamageSource();

        if (plugin.getConfig().getBoolean("exit-on-body-damage", true)) {
            stop(player, true, false);
            player.sendMessage(message("本体がダメージを受けたためFreecamを終了しました。", NamedTextColor.RED));
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
                "Freecamの範囲は本体から " + trimDistance(getMaxDistance()) + " マスです",
                NamedTextColor.RED));
    }

    void shutdown() {
        for (UUID uuid : Set.copyOf(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                stop(player, true, false);
            } else {
                FreecamSession session = sessions.remove(uuid);
                if (session != null) {
                    removeBody(session);
                }
            }
        }
        bodyOwners.clear();
    }

    private Mannequin spawnBody(Player player, Location anchor) {
        Mannequin body = player.getWorld().spawn(anchor, Mannequin.class, mannequin -> {
            mannequin.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            mannequin.setMainHand(player.getMainHand());
            mannequin.setImmovable(true);
            mannequin.setAI(false);
            mannequin.setCanPickupItems(false);
            mannequin.setCollidable(true);
            mannequin.setInvulnerable(false);
            mannequin.setPersistent(false);
            mannequin.setRemoveWhenFarAway(false);
            mannequin.setSilent(true);

            if (Mannequin.validPoses().contains(player.getPose())) {
                mannequin.setPose(player.getPose());
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
        });
        body.setRotation(anchor.getYaw(), anchor.getPitch());
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
        player.setAllowFlight(session.allowFlight());
        player.setFlying(session.allowFlight() && session.flying());
        player.setGliding(session.gliding());
        player.setFallDistance(session.fallDistance());
    }

    private void restoreVisibility(Player player) {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                viewer.showPlayer(plugin, player);
            }
        }
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
