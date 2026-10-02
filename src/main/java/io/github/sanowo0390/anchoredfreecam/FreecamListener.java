package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.projectiles.ProjectileSource;

final class FreecamListener implements Listener {
    private final AnchoredFreecamPlugin plugin;
    private final FreecamManager manager;

    FreecamListener(AnchoredFreecamPlugin plugin, FreecamManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        FreecamSession session = manager.getSession(player);
        if (session == null || event instanceof PlayerTeleportEvent) {
            return;
        }

        Location to = event.getTo();
        if (to == null || to.getWorld() != session.anchor().getWorld()) {
            return;
        }

        double max = manager.getMaxDistance();
        if (to.distanceSquared(session.anchor()) <= max * max) {
            manager.updateLastLegalLocation(player, to);
            return;
        }

        // Never end freecam just because the player crossed the radius.
        // Cancel the illegal move, then hard-correct the client/server position
        // to the last valid location on the next tick.
        event.setCancelled(true);
        manager.boundaryNotice(player);
        manager.queueBoundaryReturn(player, to.getYaw(), to.getPitch());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        FreecamSession session = manager.getSession(player);
        if (session == null || manager.isInternalTeleport(player)) {
            return;
        }

        Location to = event.getTo();
        if (to == null) {
            return;
        }

        // A cross-world teleport cannot keep the anchored body in sync, so keep
        // the old behavior there: end freecam and let the teleport continue.
        if (to.getWorld() != session.anchor().getWorld()) {
            manager.stopWithoutReturn(player, true);
            return;
        }

        double max = manager.getMaxDistance();
        if (to.distanceSquared(session.anchor()) <= max * max) {
            manager.updateLastLegalLocation(player, to);
            return;
        }

        // Plugins, anti-cheat corrections, commands, etc. must not be able to
        // place the camera outside the configured radius while freecam is active.
        event.setCancelled(true);
        manager.boundaryNotice(player);
        manager.queueBoundaryReturn(player, to.getYaw(), to.getPitch());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.hideActiveCamerasFrom(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        if (manager.isActive(event.getPlayer())) {
            manager.stop(event.getPlayer(), true, false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKick(PlayerKickEvent event) {
        if (manager.isActive(event.getPlayer())) {
            manager.stop(event.getPlayer(), true, false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (manager.isActive(event.getPlayer())) {
            manager.stop(event.getPlayer(), true, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!manager.isActive(event.getPlayer()) || event.isFlying()) {
            return;
        }

        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (player.isOnline() && manager.isActive(player)) {
                player.setAllowFlight(true);
                player.setFlying(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (manager.handleBodyDamage(event)) {
            return;
        }

        if (event.getEntity() instanceof Player player
                && manager.isActive(player)
                && !manager.isForwardedBodyDamage(player)
                && plugin.getConfig().getBoolean("protect-camera-player", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (isFreecamActor(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player) || !manager.isActive(player)) {
            return;
        }

        Mannequin body = manager.getBody(player);
        if (body != null && body.isValid()) {
            event.setTarget(body);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && manager.isActive(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && manager.isActive(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && manager.isActive(player)) event.setCancelled(true);
    }

    private boolean isFreecamActor(org.bukkit.entity.Entity entity) {
        if (entity instanceof Player player) {
            return manager.isActive(player);
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player && manager.isActive(player);
        }
        return false;
    }
}
