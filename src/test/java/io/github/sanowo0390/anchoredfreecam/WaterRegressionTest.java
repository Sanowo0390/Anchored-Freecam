package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WaterRegressionTest {
    private AnchoredFreecamPlugin plugin;
    private FreecamManager manager;
    private FreecamListener listener;
    private Player player;
    private Mannequin body;
    private Server server;
    private World world;
    private Block block;
    private Input input;
    private YamlConfiguration config;
    private Location camera;
    private Location bodyLocation;
    private FreecamSession session;
    private final List<Runnable> queued = new ArrayList<>();
    private Runnable environment;

    @BeforeEach
    void setup() throws Exception {
        plugin = mock(AnchoredFreecamPlugin.class);
        server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            if ((long) call.getArgument(3) == 1L) environment = call.getArgument(1);
            return mock(BukkitTask.class);
        });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            queued.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        world = mock(World.class);
        block = mock(Block.class);
        when(block.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        camera = new Location(world, 0, 64, 0);
        bodyLocation = camera.clone();
        player = mock(Player.class);
        body = mock(Mannequin.class);
        input = mock(Input.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(body.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getCurrentInput()).thenReturn(input);
        when(player.getLocation()).thenAnswer(call -> camera.clone());
        when(player.getEyeLocation()).thenAnswer(call -> camera.clone().add(0, 1.62, 0));
        when(player.getVelocity()).thenReturn(new Vector());
        when(player.getMaximumAir()).thenReturn(300);
        when(body.getLocation()).thenAnswer(call -> bodyLocation.clone());
        when(body.getEyeLocation()).thenAnswer(call -> bodyLocation.clone().add(0, 1.62, 0));
        when(body.getVelocity()).thenReturn(new Vector());
        when(body.isValid()).thenReturn(true);
        when(body.getRemainingAir()).thenReturn(240);
        when(body.getMaximumAir()).thenReturn(300);
        when(server.getPlayer(player.getUniqueId())).thenReturn(player);
        when(server.getEntity(body.getUniqueId())).thenReturn(body);
        Messages messages = mock(Messages.class);
        when(messages.text(anyString())).thenReturn("message");
        manager = new FreecamManager(plugin, messages);
        listener = new FreecamListener(plugin, manager);
        installSession();
        when(player.teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenAnswer(call -> {
            Location target = call.getArgument(0);
            PlayerTeleportEvent event = teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
            assertTrue(manager.isInternalTeleport(player));
            listener.onTeleport(event);
            listener.onTeleportResult(event);
            if (event.isCancelled()) return false;
            camera = event.getTo().clone();
            return true;
        });
    }

    @SuppressWarnings("unchecked")
    private <T> Map<UUID, T> state(String name) throws Exception {
        Field field = FreecamManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<UUID, T>) field.get(manager);
    }

    private void installSession() throws Exception {
        session = new FreecamSession(bodyLocation.clone(), body.getUniqueId(), false, false,
                false, true, false, true, false, true, 300, 0);
        state("sessions").put(player.getUniqueId(), session);
        state("bodyOwners").put(body.getUniqueId(), player.getUniqueId());
        state("lastLegalLocations").put(player.getUniqueId(), camera.clone());
    }

    private PlayerTeleportEvent teleport(Location to, PlayerTeleportEvent.TeleportCause cause) {
        return new PlayerTeleportEvent(player, camera.clone(), to, cause);
    }

    private void runQueued() {
        List<Runnable> tasks = List.copyOf(queued);
        queued.clear();
        tasks.forEach(Runnable::run);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.04, -0.08, 0.3})
    void passiveWaterMovementUsesOwnedCorrection(double lift) {
        when(input.isForward()).thenReturn(true);
        when(player.isInWaterOrBubbleColumn()).thenReturn(true);
        Location to = camera.clone().add(0.2, lift, 0.1);
        PlayerMoveEvent event = new PlayerMoveEvent(player, camera.clone(), to);
        listener.onMove(event);
        assertTrue(event.isCancelled());
        assertEquals(64 + lift, event.getTo().getY(), 1e-9, "No unowned setTo teleport");
        runQueued();
        assertTrue(manager.isActive(player));
        assertEquals(64, camera.getY());
        assertEquals(0.2, camera.getX());
        assertFalse(manager.isInternalTeleport(player));
    }

    @Test
    void surfaceUsesWaterBlockEvenWhenEntityFlagIsFalse() {
        when(block.getType()).thenReturn(Material.WATER);
        PlayerMoveEvent event = new PlayerMoveEvent(player, camera.clone(), camera.clone().add(0.2, 0.04, 0));
        listener.onMove(event);
        assertTrue(event.isCancelled());
        runQueued();
        assertTrue(manager.isActive(player));
    }

    @Test
    void intentionalVerticalInputIsPreserved() {
        when(player.isInWaterOrBubbleColumn()).thenReturn(true);
        when(input.isJump()).thenReturn(true);
        PlayerMoveEvent up = new PlayerMoveEvent(player, camera.clone(), camera.clone().add(0, 0.2, 0));
        listener.onMove(up);
        assertFalse(up.isCancelled());
        when(input.isJump()).thenReturn(false);
        when(input.isSneak()).thenReturn(true);
        PlayerMoveEvent down = new PlayerMoveEvent(player, camera.clone(), camera.clone().add(0, -0.2, 0));
        listener.onMove(down);
        assertFalse(down.isCancelled());
        assertTrue(queued.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 1, 3, 4, 40})
    void externalPluginTeleportEndsFreecamAtEveryDistance(double distance) {
        PlayerTeleportEvent event = teleport(camera.clone().add(distance, 0, 0), PlayerTeleportEvent.TeleportCause.PLUGIN);
        listener.onTeleport(event);
        assertTrue(manager.isActive(player), "Wait until final cancellation state");
        listener.onTeleportResult(event);
        assertFalse(manager.isActive(player));
        assertFalse(event.isCancelled());
        verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void cancelledExternalTeleportKeepsBodyAndSession() {
        PlayerTeleportEvent event = teleport(camera.clone().add(30, 0, 0), PlayerTeleportEvent.TeleportCause.COMMAND);
        listener.onTeleport(event);
        event.setCancelled(true);
        listener.onTeleportResult(event);
        assertTrue(manager.isActive(player));
        verify(body, never()).remove();
    }

    @Test
    void incomingTpaRedirectsToBody() {
        Player visitor = mock(Player.class);
        when(visitor.getUniqueId()).thenReturn(UUID.randomUUID());
        camera.add(7, 0, 0);
        PlayerTeleportEvent event = new PlayerTeleportEvent(visitor, new Location(world, 30, 64, 0), camera.clone(),
                PlayerTeleportEvent.TeleportCause.PLUGIN);
        listener.onTeleport(event);
        assertEquals(bodyLocation, event.getTo());
        assertTrue(manager.isActive(player));
    }

    @Test
    void staleCorrectionCannotMoveNewSession() throws Exception {
        manager.queueCameraCorrection(player, camera.clone().add(1, 0, 0));
        manager.stopWithoutReturn(player, false);
        installSession();
        manager.queueCameraCorrection(player, camera.clone().add(2, 0, 0));
        runQueued();
        assertEquals(2, camera.getX());
        verify(player, times(1)).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void movingBodyRevalidatesQueuedDestination() {
        manager.queueCameraCorrection(player, camera.clone().add(14, 0, 0));
        bodyLocation.setY(40);
        runQueued();
        assertEquals(bodyLocation, camera);
        assertTrue(manager.isActive(player));
    }

    @Test
    void cancelledCorrectionDoesNotCommitLastLegalPosition() throws Exception {
        doReturn(false).when(player).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
        Location original = camera.clone();
        manager.queueCameraCorrection(player, camera.clone().add(2, 0, 0));
        runQueued();
        assertEquals(original, state("lastLegalLocations").get(player.getUniqueId()));
    }

    @Test
    void defaultRangeBoundaryReturnsWithoutEndingSession() {
        assertEquals(15, manager.getMaxDistance());
        config.set("show-boundary-message", false);
        PlayerMoveEvent event = new PlayerMoveEvent(player, camera.clone(), camera.clone().add(16, 0, 0));
        listener.onMove(event);
        assertTrue(event.isCancelled());
        runQueued();
        assertTrue(manager.isActive(player));
        assertTrue(camera.distanceSquared(session.anchor()) <= 225);
    }

    @Test
    void cancelledMovesDoNotAdvanceLastLegalLocation() throws Exception {
        PlayerMoveEvent event = new PlayerMoveEvent(player, camera.clone(), camera.clone().add(2, 0, 0));
        listener.onMove(event);
        assertEquals(camera, state("lastLegalLocations").get(player.getUniqueId()));
        listener.onMoveResult(event);
        assertEquals(event.getTo(), state("lastLegalLocations").get(player.getUniqueId()));
    }

    @Test
    void cameraCannotChangeAirOrEnterSwimmingPose() {
        EntityAirChangeEvent air = new EntityAirChangeEvent(player, 299);
        listener.onAirChange(air);
        assertTrue(air.isCancelled());
        EntityAirChangeEvent bodyAir = new EntityAirChangeEvent(body, 239);
        listener.onAirChange(bodyAir);
        assertFalse(bodyAir.isCancelled());
        EntityToggleSwimEvent swim = new EntityToggleSwimEvent(player, true);
        listener.onSwim(swim);
        assertTrue(swim.isCancelled());
    }

    @Test
    void flightDisableIsCancelledAndReasserted() {
        PlayerToggleFlightEvent flight = new PlayerToggleFlightEvent(player, false);
        listener.onToggleFlight(flight);
        assertTrue(flight.isCancelled());
        runQueued();
        verify(player).setAllowFlight(true);
        verify(player).setFlying(true);
    }

    @Test
    void waterVelocityPreservesHorizontalMotionAndIntentionalVerticalMotion() {
        when(input.isForward()).thenReturn(true);
        when(player.isInWaterOrBubbleColumn()).thenReturn(true);
        PlayerVelocityEvent passive = new PlayerVelocityEvent(player, new Vector(0.1, 0.04, 0.2));
        listener.onVelocity(passive);
        assertEquals(new Vector(0.1, 0, 0.2), passive.getVelocity());
        when(input.isJump()).thenReturn(true);
        PlayerVelocityEvent deliberate = new PlayerVelocityEvent(player, new Vector(0, 0.2, 0));
        listener.onVelocity(deliberate);
        assertEquals(0.2, deliberate.getVelocity().getY());
    }

    @Test
    void waterloggedAndBubbleBlocksCountAsWaterButLavaDoesNot() {
        when(block.getType()).thenReturn(Material.LAVA);
        assertFalse(WaterContact.at(camera));
        when(block.getType()).thenReturn(Material.BUBBLE_COLUMN);
        assertTrue(WaterContact.at(camera));
        when(block.getType()).thenReturn(Material.OAK_SLAB);
        Waterlogged data = mock(Waterlogged.class);
        when(data.isWaterlogged()).thenReturn(true);
        when(block.getBlockData()).thenReturn(data);
        assertTrue(WaterContact.at(camera));
    }

    @Test
    void surfaceDrowningUsesNativeEyeSubmersionInsteadOfLiquidBlock() {
        when(block.getType()).thenReturn(Material.WATER);
        when(body.isUnderWater()).thenReturn(false);
        EntityDamageEvent damage = mock(EntityDamageEvent.class);
        when(damage.getEntity()).thenReturn(body);
        when(damage.getCause()).thenReturn(EntityDamageEvent.DamageCause.DROWNING);
        assertTrue(manager.handleBodyDamage(damage));
        assertTrue(manager.isActive(player));
        verify(body).setRemainingAir(300);
        verify(player, never()).damage(anyDouble(), any(org.bukkit.damage.DamageSource.class));
    }

    @Test
    void manualExitRestoresFallingBodyPositionVelocityAndDistance() {
        bodyLocation.setY(50);
        when(body.getVelocity()).thenReturn(new Vector(0, -0.8, 0));
        when(body.getFallDistance()).thenReturn(14f);
        // stop() intentionally removes the session before its return teleport.
        doReturn(true).when(player).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
        manager.stop(player, true, false);
        verify(player).teleport(bodyLocation, PlayerTeleportEvent.TeleportCause.PLUGIN);
        verify(player).setVelocity(new Vector(0, -0.8, 0));
        verify(player).setFallDistance(14f);
        verify(player).setRemainingAir(240);
        verify(player).setInvisible(false);
        verify(player).setVisibleByDefault(true);
        verify(player).setAllowFlight(false);
    }

    @Test
    void idleCameraDoesNotDriftWithHorizontalWaterCurrent() {
        when(player.isInWaterOrBubbleColumn()).thenReturn(true);
        Location original = camera.clone();
        PlayerMoveEvent event = new PlayerMoveEvent(player, camera.clone(), camera.clone().add(0.08, 0.04, -0.1));
        listener.onMove(event);
        assertTrue(event.isCancelled());
        runQueued();
        assertEquals(original, camera);
        PlayerVelocityEvent velocity = new PlayerVelocityEvent(player, new Vector(0.08, 0.04, -0.1));
        listener.onVelocity(velocity);
        assertEquals(new Vector(), velocity.getVelocity());
    }

    @Test
    void waterBodyStaysPhysicalAndCameraAirComesOnlyFromBody() {
        when(body.isInWaterOrBubbleColumn()).thenReturn(true);
        when(body.isOnGround()).thenReturn(true);
        doAnswer(call -> {
            EntityAirChangeEvent air = new EntityAirChangeEvent(player, call.getArgument(0));
            listener.onAirChange(air);
            assertFalse(air.isCancelled(), "Explicit body air sync must be allowed");
            return null;
        }).when(player).setRemainingAir(anyInt());
        environment.run();
        verify(body).setImmovable(false);
        verify(body).setGravity(true);
        verify(player).setRemainingAir(240);
        assertFalse(manager.isSynchronizingAir(player));
        assertTrue(manager.isActive(player));
    }

    @Test
    void dryLandingFreezesBodyAndFallingBodyMovesAnchor() {
        bodyLocation.setY(60);
        environment.run();
        verify(body).setImmovable(false);
        verify(body).setGravity(true);
        assertEquals(bodyLocation, session.anchor());
        when(body.isOnGround()).thenReturn(true);
        environment.run();
        verify(body).setImmovable(true);
        verify(body).setGravity(false);
        verify(body).setVelocity(new Vector());
    }

    @Test
    void realUnderwaterBodyDamageStillEndsFreecamAndIsForwarded() {
        when(body.isUnderWater()).thenReturn(true);
        EntityDamageEvent damage = mock(EntityDamageEvent.class);
        org.bukkit.damage.DamageSource source = mock(org.bukkit.damage.DamageSource.class);
        when(damage.getEntity()).thenReturn(body);
        when(damage.getCause()).thenReturn(EntityDamageEvent.DamageCause.DROWNING);
        when(damage.getDamage()).thenReturn(2.0);
        when(damage.getDamageSource()).thenReturn(source);
        assertTrue(manager.handleBodyDamage(damage));
        assertFalse(manager.isActive(player));
        verify(player).damage(2.0, source);
    }

    @Test
    void ownBodyRightClickEndsFreecamButOtherBodyDoesNot() {
        Mannequin other = mock(Mannequin.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerInteractEntityEvent foreignClick = new PlayerInteractEntityEvent(player, other);
        listener.onInteractEntity(foreignClick);
        assertTrue(foreignClick.isCancelled());
        assertTrue(manager.isActive(player));
        PlayerInteractEntityEvent ownClick = new PlayerInteractEntityEvent(player, body);
        listener.onInteractEntity(ownClick);
        assertTrue(ownClick.isCancelled());
        assertFalse(manager.isActive(player));
        verify(body).remove();
    }

    @Test
    void crossWorldTeleportEndsFreecamWithoutReturningToBody() {
        Location target = new Location(mock(World.class), 0, 70, 0);
        PlayerTeleportEvent event = teleport(target, PlayerTeleportEvent.TeleportCause.UNKNOWN);
        listener.onTeleport(event);
        listener.onTeleportResult(event);
        assertFalse(manager.isActive(player));
        assertEquals(target, event.getTo());
        verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void groundedWaterBodyRetainsCurrentVelocityOnExit() {
        when(body.isOnGround()).thenReturn(true);
        when(body.isInWaterOrBubbleColumn()).thenReturn(true);
        when(body.getVelocity()).thenReturn(new Vector(0.08, 0, 0.02));
        manager.stop(player, true, false);
        verify(player).setVelocity(new Vector(0.08, 0, 0.02));
    }
}
