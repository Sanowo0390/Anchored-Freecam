package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.Location;

record FreecamSession(
        Location anchor,
        boolean allowFlight,
        boolean flying,
        boolean invulnerable,
        boolean collidable,
        boolean gliding,
        float fallDistance
) {
}
