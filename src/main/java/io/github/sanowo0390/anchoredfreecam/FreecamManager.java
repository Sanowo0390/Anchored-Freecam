package io.github.sanowo0390.anchoredfreecam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class FreecamManager {
    private final AnchoredFreecamPlugin plugin;
    private final Map<UUID, FreecamSession> sessions = new HashMap<>();
    private final Set<UUID> internalTeleports = new java.util.HashSet<>();
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

    double getMaxDistance() {
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

        FreecamSession session = new FreecamSession(
                player.getLocation().clone(),
                player.getAllowFlight(),
                player.isFlying(),
                player.isInvulnerable(),
                player.isCollidable(),
                player.isGliding(),
                player.getFallDistance()
        );
        sessions.put(player.getUniqueId(), session);

        if (player.isGliding()) {
            player.setGliding(false);
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFallDistance(0.0F);

        if (plugin.getConfig().getBoolean("invulnerable", true)) {
            player.setInvulnerable(true);
        }
        if (plugin.getConfig().getBoolean("disable-entity-collision", true)) {
            player.setCollidable(false);
        }
        applyVisibility(player);

        player.sendMessage(message(
                "Freecam ON — 開始地点から " + trimDistance(getMaxDistance()) + " ブロック以内。",
                NamedTextColor.GREEN));
        return true;
    }

    boolean stop(Player player, boolean returnToAnchor, boolean sendMessage) {
        FreecamSession session = sessions.remove(player.getUniqueId());
        lastBoundaryNotice.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }

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
                    returnToAnchor ? "Freecam OFF — 元の位置へ戻りました。" : "Freecamを終了しました。",
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

        restoreState(player, session);
        if (sendMessage && player.isOnline()) {
            player.sendMessage(message("Freecamを終了しました。", NamedTextColor.YELLOW));
        }
    }

    boolean isInternalTeleport(Player player) {
        return internalTeleports.contains(player.getUniqueId());
    }

    void applyVisibility(Player freecamPlayer) {
        if (!plugin.getConfig().getBoolean("hide-player-from-others", true)) {
            return;
        }
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(freecamPlayer.getUniqueId())) {
                viewer.hidePlayer(plugin, freecamPlayer);
            }
        }
    }

    void hideActivePlayersFrom(Player viewer) {
        if (!plugin.getConfig().getBoolean("hide-player-from-others", true)) {
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
            applyVisibility(player);
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
                "Freecamの範囲は開始地点から " + trimDistance(getMaxDistance()) + " ブロックです",
                NamedTextColor.RED));
    }

    void shutdown() {
        for (UUID uuid : Set.copyOf(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                stop(player, true, false);
            } else {
                sessions.remove(uuid);
            }
        }
    }

    private void restoreState(Player player, FreecamSession session) {
        restoreVisibility(player);
        player.setInvulnerable(session.invulnerable());
        player.setCollidable(session.collidable());
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
