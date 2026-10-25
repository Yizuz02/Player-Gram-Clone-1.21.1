package net.yizuz.playerogrammod.hologram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HologramSpawnTest {
    private static final double EPSILON = 1.0E-6;

    @Test
    void forwardUsesHorizontalLookDirection() {
        HologramSpawn.Point spawn = HologramSpawn.forward(
                new HologramSpawn.Point(0.0, 64.0, 0.0), 0.0, 0.7, 0.7, 2.5);
        assertEquals(0.0, spawn.x(), EPSILON);
        assertEquals(64.0, spawn.y(), EPSILON);
        assertEquals(2.5, spawn.z(), EPSILON);
    }

    @Test
    void forwardUsesVerticalLookWhenLookingStraightUp() {
        HologramSpawn.Point spawn = HologramSpawn.forward(
                new HologramSpawn.Point(0.0, 64.0, 0.0), 0.0, 1.0, 0.0, 2.5);
        assertEquals(66.5, spawn.y(), EPSILON);
    }

    @Test
    void forwardKeepsOwnerForZeroLook() {
        HologramSpawn.Point owner = new HologramSpawn.Point(4.0, 70.0, -3.0);
        assertEquals(owner, HologramSpawn.forward(owner, 0.0, 0.0, 0.0, 2.5));
    }

    @Test
    void topFaceTargetsTheBlockAbove() {
        HologramSpawn.Point spawn = HologramSpawn.faceCenter(10, 64, 10, HologramSpawn.Face.UP);
        assertEquals(10.5, spawn.x(), EPSILON);
        assertEquals(65.0, spawn.y(), EPSILON);
        assertEquals(10.5, spawn.z(), EPSILON);
    }

    @Test
    void sideFaceTargetsTheAdjacentBlock() {
        HologramSpawn.Point spawn = HologramSpawn.faceCenter(10, 64, 10, HologramSpawn.Face.NORTH);
        assertEquals(10.5, spawn.x(), EPSILON);
        assertEquals(64.0, spawn.y(), EPSILON);
        assertEquals(9.5, spawn.z(), EPSILON);
    }

    @Test
    void bottomFaceTargetsTheBlockBelow() {
        HologramSpawn.Point spawn = HologramSpawn.faceCenter(10, 64, 10, HologramSpawn.Face.DOWN);
        assertEquals(10.5, spawn.x(), EPSILON);
        assertEquals(63.0, spawn.y(), EPSILON);
        assertEquals(10.5, spawn.z(), EPSILON);
    }
}
