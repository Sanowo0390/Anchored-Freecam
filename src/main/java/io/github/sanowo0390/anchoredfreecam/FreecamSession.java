package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.Location;

import java.util.UUID;

record FreecamSession(
        Location anchor,
        UUID bodyUuid,
        boolean allowFlight,
        boolean flying,
        boolean invulnerable,
        boolean collidable,
        boolean invisible,
        boolean visibleByDefault,
        boolean gliding,
        boolean gravity,
        int remainingAir,
        float fallDistance,
        boolean bodyHovering
) {
}
