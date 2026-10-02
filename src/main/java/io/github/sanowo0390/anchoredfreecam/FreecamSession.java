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
        boolean gliding,
        float fallDistance
) {
}
