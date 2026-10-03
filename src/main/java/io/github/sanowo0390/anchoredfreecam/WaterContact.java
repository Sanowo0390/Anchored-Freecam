package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.Location;
import org.bukkit.Input;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/** Broad contact check for movement only; breathing uses native isUnderWater(). */
final class WaterContact {
    private WaterContact() {}

    static boolean touches(LivingEntity entity) {
        return entity.isInWaterOrBubbleColumn()
                || at(entity.getLocation()) || at(entity.getEyeLocation());
    }

    static boolean at(Location location) {
        Block block = location.getBlock();
        return block.getType() == Material.WATER || block.getType() == Material.BUBBLE_COLUMN
                || block.getBlockData() instanceof Waterlogged data && data.isWaterlogged();
    }

    static boolean horizontalInput(Input input) {
        return input.isForward() || input.isBackward() || input.isLeft() || input.isRight();
    }

    static Vector cameraVelocity(Input input, Vector original) {
        Vector velocity = original.clone();
        if (!horizontalInput(input)) {
            velocity.setX(0);
            velocity.setZ(0);
        }
        if (!input.isJump() && !input.isSneak()) velocity.setY(0);
        return velocity;
    }
}
