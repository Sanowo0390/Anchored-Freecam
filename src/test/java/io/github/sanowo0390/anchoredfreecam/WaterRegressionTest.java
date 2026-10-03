package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.Input;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Spider;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mockito.MockedStatic;

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
    private Runnable aggro;
    private MockedStatic<ItemStack> itemStacks;
    private ItemStack emptyHand;

    @AfterEach
    void closeItemStackMock() {
        if (itemStacks != null) itemStacks.close();
    }

    @BeforeEach
    void setup() throws Exception {
        emptyHand = mock(ItemStack.class);
        itemStacks = mockStatic(ItemStack.class);
        itemStacks.when(ItemStack::empty).thenReturn(emptyHand);
        plugin = mock(AnchoredFreecamPlugin.class);
        server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            if ((long) call.getArgument(3) == 1L) environment = call.getArgument(1);
            if ((long) call.getArgument(3) == 2L) aggro = call.getArgument(1);
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
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
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
        installSession(false);
    }

    private void installSession(boolean hovering) throws Exception {
        session = new FreecamSession(bodyLocation.clone(), body.getUniqueId(), false, false,
                false, true, false, true, false, true, 300, 0, hovering);
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
        assertNotEquals(bodyLocation, camera);
        assertEquals(14.95, camera.distance(bodyLocation), 1e-6);
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

    @Test
    void crossingBoundaryUsesCurrentEdgeInsteadOfStaleOrigin() {
        config.set("show-boundary-message", false);
        camera.setX(14.95);
        PlayerMoveEvent move = new PlayerMoveEvent(player, camera.clone(), camera.clone().add(0.1, 0, 0));
        listener.onMove(move);
        runQueued();
        assertEquals(14.95, camera.getX(), 1e-9);
        assertTrue(manager.isActive(player));
    }

    @Test
    void environmentDoesNotOverwriteSavedEdgeWithBodyPosition() throws Exception {
        Location edge = camera.clone().add(14.95, 0, 0);
        state("lastLegalLocations").put(player.getUniqueId(), edge);
        camera.setX(15.05);
        config.set("show-boundary-message", false);
        environment.run();
        assertEquals(edge, state("lastLegalLocations").get(player.getUniqueId()));
        runQueued();
        assertEquals(edge, camera);
        assertTrue(manager.isActive(player));
    }

    @Test
    void invalidSavedEdgeProjectsNearLimitAfterBodyMovement() throws Exception {
        camera.setX(15);
        state("lastLegalLocations").put(player.getUniqueId(), camera.clone());
        bodyLocation.setX(-0.1);
        config.set("show-boundary-message", false);
        environment.run();
        runQueued();
        assertEquals(14.85, camera.getX(), 1e-9);
        assertEquals(14.95, camera.distance(bodyLocation), 1e-9);
    }

    @Test
    void shrinkingRangeProjectsToNewEdgeAndPreservesLook() {
        camera.setX(14);
        camera.setYaw(75);
        camera.setPitch(-25);
        config.set("max-distance-blocks", 10);
        manager.queueCameraCorrection(player, camera.clone());
        runQueued();
        assertEquals(9.95, camera.getX(), 1e-9);
        assertEquals(75, camera.getYaw());
        assertEquals(-25, camera.getPitch());
    }

    @Test
    void projectedCorrectionAvoidsCollidingEndpoint() {
        camera.setX(15.1);
        when(player.collidesAt(any(Location.class))).thenAnswer(call -> ((Location) call.getArgument(0)).getX() > 14.8);
        manager.queueCameraCorrection(player, camera.clone());
        runQueued();
        assertEquals(14.7, camera.getX(), 1e-9);
    }

    @Test
    void cameraAttackCannotExitVictimEvenWhenGeneralHandlerRunsFirst() throws Exception {
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        state("sessions").put(attacker.getUniqueId(), session);
        EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
        when(hit.getDamager()).thenReturn(attacker);
        when(hit.getEntity()).thenReturn(body);
        listener.onDamage(hit);
        verify(hit).setCancelled(true);
        assertTrue(manager.isActive(player));
        verify(body, never()).remove();
        verify(player, never()).damage(anyDouble(), any(org.bukkit.damage.DamageSource.class));
    }

    @Test
    void cameraProjectileCannotExitVictim() throws Exception {
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        state("sessions").put(attacker.getUniqueId(), session);
        org.bukkit.entity.Projectile projectile = mock(org.bukkit.entity.Projectile.class);
        when(projectile.getShooter()).thenReturn(attacker);
        EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
        when(hit.getDamager()).thenReturn(projectile);
        when(hit.getEntity()).thenReturn(body);
        listener.onDamageByEntity(hit);
        listener.onDamage(hit);
        verify(hit, times(2)).setCancelled(true);
        assertTrue(manager.isActive(player));
        verify(body, never()).remove();
    }

    @Test
    void normalPlayerCanStillDamageBodyAndEndFreecam() {
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
        org.bukkit.damage.DamageSource source = mock(org.bukkit.damage.DamageSource.class);
        when(hit.getDamager()).thenReturn(attacker);
        when(hit.getEntity()).thenReturn(body);
        when(hit.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        when(hit.getDamage()).thenReturn(3.0);
        when(hit.getDamageSource()).thenReturn(source);
        listener.onDamage(hit);
        assertFalse(manager.isActive(player));
        verify(player).damage(3.0, source);
    }

    @Test
    void handMaskOnlySendsOwnMainHandAndNeverMutatesInventory() {
        manager.hideCameraHand(player);
        verify(player).sendEquipmentChange(player, EquipmentSlot.HAND, emptyHand);
        verify(player, never()).getInventory();
        verify(body, never()).getEquipment();
    }

    @Test
    void hotbarSwitchRestoresPreviousSlotBeforeMaskingCurrentHand() {
        listener.onItemHeld(new PlayerItemHeldEvent(player, 0, 1));
        runQueued();
        org.mockito.InOrder order = inOrder(player);
        order.verify(player).updateInventory();
        order.verify(player).sendEquipmentChange(player, EquipmentSlot.HAND, emptyHand);
        verify(player, never()).getInventory();
    }

    @Test
    void stoppingRestoresInventoryAndDiscardsPendingHandMask() {
        manager.refreshCameraHandNextTick(player);
        manager.stopWithoutReturn(player, false);
        runQueued();
        verify(player).updateInventory();
        verify(player, never()).sendEquipmentChange(any(), any(EquipmentSlot.class), any(ItemStack.class));
    }

    @Test
    void staleHandRefreshCannotAffectNewSession() throws Exception {
        manager.refreshCameraHandNextTick(player);
        manager.stopWithoutReturn(player, false);
        installSession();
        runQueued();
        verify(player, times(1)).updateInventory();
        verify(player, never()).sendEquipmentChange(any(), any(EquipmentSlot.class), any(ItemStack.class));
    }

    private Monster nearbyMonster() {
        Monster monster = mock(Monster.class);
        when(monster.isValid()).thenReturn(true);
        when(monster.isAware()).thenReturn(true);
        when(body.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(monster));
        return monster;
    }

    @Test
    void creativeBodyDoesNotAcquireAggroAndClearsExistingTarget() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        Monster monster = nearbyMonster();
        aggro.run();
        verify(monster, never()).setTarget(any());
        when(monster.getTarget()).thenReturn(body);
        aggro.run();
        verify(monster).setTarget(null);
        verify(monster, never()).setTarget(body);
    }

    @Test
    void creativeAggroCleanupDoesNotStealAnotherPlayersTarget() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        Monster monster = nearbyMonster();
        when(monster.getTarget()).thenReturn(mock(Player.class));
        aggro.run();
        verify(monster, never()).setTarget(any());
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "ADVENTURE"})
    void survivalAndAdventureStillAttractHostileMobs(GameMode mode) {
        when(player.getGameMode()).thenReturn(mode);
        Monster monster = nearbyMonster();
        aggro.run();
        verify(monster).setTarget(body);
    }

    @Test
    void creativeCameraAndBodyCannotBeTargetedEvenWithAggroOptionOff() {
        config.set("force-hostile-mob-aggro", false);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        for (LivingEntity target : new LivingEntity[]{player, body}) {
            EntityTargetLivingEntityEvent event = mock(EntityTargetLivingEntityEvent.class);
            when(event.getTarget()).thenReturn(target);
            listener.onTarget(event);
            verify(event).setTarget(null);
        }
    }

    @Test
    void survivalCameraStillRedirectsMobsToBody() {
        EntityTargetLivingEntityEvent event = mock(EntityTargetLivingEntityEvent.class);
        when(event.getTarget()).thenReturn(player);
        listener.onTarget(event);
        verify(event).setTarget(body);
    }

    @Test
    void creativeBodyDamageDoesNotExitOrForwardDamage() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(body);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        listener.onDamage(event);
        verify(event).setCancelled(true);
        assertTrue(manager.isActive(player));
        verify(body, never()).remove();
        verify(player, never()).damage(anyDouble(), any(org.bukkit.damage.DamageSource.class));
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"CREATIVE", "SURVIVAL"})
    void startingRetargetRespectsGameMode(GameMode mode) throws Exception {
        when(player.getGameMode()).thenReturn(mode);
        Monster monster = nearbyMonster();
        when(monster.getTarget()).thenReturn(player);
        when(player.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(monster));
        var method = FreecamManager.class.getDeclaredMethod("retargetCurrentEnemies", Player.class, Mannequin.class);
        method.setAccessible(true);
        method.invoke(manager, player, body);
        verify(monster).setTarget(mode == GameMode.CREATIVE ? null : body);
    }

    @Test
    void onlyCreativeAlreadyFlyingAtStartGetsHoveringBody() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.isFlying()).thenReturn(true);
        assertTrue(FreecamManager.shouldHoverBody(player));
        when(player.isFlying()).thenReturn(false);
        assertFalse(FreecamManager.shouldHoverBody(player));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.isFlying()).thenReturn(true);
        assertFalse(FreecamManager.shouldHoverBody(player));
    }

    @Test
    void hoveringBodyKeepsGravityOffAcrossTicksAndWaterContact() throws Exception {
        installSession(true);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.isFlying()).thenReturn(true);
        when(body.isOnGround()).thenReturn(false);
        when(body.isInWaterOrBubbleColumn()).thenReturn(true);
        for (int i = 0; i < 3; i++) environment.run();
        verify(body, times(3)).setGravity(false);
        verify(body, times(3)).setImmovable(true);
        verify(body, times(3)).setVelocity(new Vector());
        verify(body, never()).setGravity(true);
        assertEquals(bodyLocation, session.anchor());
    }

    @Test
    void cameraFlightDoesNotMakeNonFlyingCreativeBodyHover() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.isFlying()).thenReturn(true);
        // Session was captured before camera flight: bodyHovering remains false.
        environment.run();
        verify(body).setGravity(true);
        verify(body).setImmovable(false);
    }

    @Test
    void hoveringExitRestoresCreativeFlightWithoutCameraMomentum() throws Exception {
        session = new FreecamSession(bodyLocation.clone(), body.getUniqueId(), true, true,
                false, true, false, true, false, true, 300, 0, true);
        state("sessions").put(player.getUniqueId(), session);
        when(body.getVelocity()).thenReturn(new Vector(0, -0.8, 0));
        when(body.getFallDistance()).thenReturn(10f);
        manager.stop(player, true, false);
        verify(player).setAllowFlight(true);
        verify(player).setFlying(true);
        verify(player).setVelocity(new Vector());
        verify(player, never()).setVelocity(new Vector(0, -0.8, 0));
        verify(player, never()).setFallDistance(10f);
    }

    private Spider nearbySpider(LivingEntity initialTarget) {
        Spider spider = mock(Spider.class);
        when(spider.getUniqueId()).thenReturn(UUID.randomUUID());
        when(spider.isValid()).thenReturn(true);
        when(spider.isAware()).thenReturn(true);
        when(spider.getLocation()).thenAnswer(call -> bodyLocation.clone().add(2, 0, 0));
        var target = new java.util.concurrent.atomic.AtomicReference<>(initialTarget);
        when(spider.getTarget()).thenAnswer(call -> target.get());
        doAnswer(call -> { target.set(call.getArgument(0)); return null; }).when(spider).setTarget(any());
        when(body.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(spider));
        when(player.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(spider));
        when(server.getEntity(spider.getUniqueId())).thenReturn(spider);
        return spider;
    }

    @Test
    void hostileSpiderReacquiresBodyAfterLosingProxyTarget() {
        Spider spider = nearbySpider(player);
        aggro.run();
        assertSame(body, spider.getTarget());
        spider.setTarget(null);
        aggro.run();
        assertSame(body, spider.getTarget());
        spider.setTarget(null);
        aggro.run();
        assertSame(body, spider.getTarget());
    }

    @Test
    void startingSpiderAggroSurvivesLossBeforeFirstMaintenanceTick() throws Exception {
        Spider spider = nearbySpider(player);
        var method = FreecamManager.class.getDeclaredMethod("retargetCurrentEnemies", Player.class, Mannequin.class);
        method.setAccessible(true);
        method.invoke(manager, player, body);
        spider.setTarget(null);
        aggro.run();
        assertSame(body, spider.getTarget());
    }

    @Test
    void targetingEventRecordsNewlyHostileSpiderWithoutAngeringNeutralSpiders() {
        Spider spider = nearbySpider(null);
        aggro.run();
        verify(spider, never()).setTarget(any());
        EntityTargetLivingEntityEvent event = mock(EntityTargetLivingEntityEvent.class);
        when(event.getEntity()).thenReturn(spider);
        when(event.getTarget()).thenReturn(body);
        listener.onTargetResult(event);
        aggro.run();
        assertSame(body, spider.getTarget());
    }

    @Test
    void spiderThatSwitchesToAnotherPlayerIsNotStolenBack() {
        Spider spider = nearbySpider(player);
        aggro.run();
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        spider.setTarget(other);
        aggro.run();
        assertSame(other, spider.getTarget());
        spider.setTarget(null);
        aggro.run();
        assertNull(spider.getTarget());
    }

    @Test
    void spiderMemoryExpiresOutsideBodyScanRange() {
        Spider spider = nearbySpider(player);
        aggro.run();
        when(body.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        aggro.run();
        when(body.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(spider));
        spider.setTarget(null);
        aggro.run();
        assertNull(spider.getTarget());
    }

    @Test
    void creativeAndDisabledAggroDoNotRestoreRememberedSpider() {
        Spider spider = nearbySpider(player);
        aggro.run();
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        aggro.run();
        assertNull(spider.getTarget());
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        aggro.run();
        assertNull(spider.getTarget());
        spider.setTarget(player);
        aggro.run();
        config.set("force-hostile-mob-aggro", false);
        aggro.run();
        config.set("force-hostile-mob-aggro", true);
        spider.setTarget(null);
        aggro.run();
        assertNull(spider.getTarget());
    }

    @Test
    void returningFromFreecamTransfersSpiderBackToPlayer() throws Exception {
        Spider spider = nearbySpider(player);
        aggro.run();
        manager.stop(player, true, false);
        assertSame(player, spider.getTarget());
        assertTrue(state("hostileSpiderOwners").isEmpty());
    }

    @Test
    void externalTeleportForgetsSpiderWithoutTargetingCameraLocation() throws Exception {
        Spider spider = nearbySpider(player);
        aggro.run();
        manager.stopWithoutReturn(player, false);
        verify(spider, never()).setTarget(player);
        assertTrue(state("hostileSpiderOwners").isEmpty());
    }
}
