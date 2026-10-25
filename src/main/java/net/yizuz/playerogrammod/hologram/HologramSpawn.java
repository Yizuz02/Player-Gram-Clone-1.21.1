package net.yizuz.playerogrammod.hologram;

/**
 * Pure spawn-position math for the hologram. The server converts these points to
 * world vectors and resolves collision safety; keeping the math free of Minecraft
 * classes lets the direction and block-face rules be unit tested.
 */
public final class HologramSpawn {
    public record Point(double x, double y, double z) {
        public Point add(double dx, double dy, double dz) {
            return new Point(this.x + dx, this.y + dy, this.z + dz);
        }
    }

    public enum Face {
        UP(0, 1, 0),
        DOWN(0, -1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);

        private final int dx;
        private final int dy;
        private final int dz;

        Face(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        public int dx() {
            return this.dx;
        }

        public int dy() {
            return this.dy;
        }

        public int dz() {
            return this.dz;
        }
    }

    public static Point forward(Point ownerFeet, double lookX, double lookY, double lookZ, double distance) {
        if (ownerFeet == null || distance <= 0.0) {
            return ownerFeet;
        }
        double flatLength = Math.sqrt(lookX * lookX + lookZ * lookZ);
        double length = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        if (flatLength > 1.0E-4) {
            return ownerFeet.add(lookX / flatLength * distance, 0.0, lookZ / flatLength * distance);
        }
        if (length > 1.0E-4) {
            return ownerFeet.add(lookX / length * distance, lookY / length * distance,
                    lookZ / length * distance);
        }
        return ownerFeet;
    }

    /**
     * The empty block directly adjacent to the looked-at block face. For the top
     * face this is the space above the block; for a side face it is the space in
     * front of that face.
     */
    public static Point faceCenter(int blockX, int blockY, int blockZ, Face face) {
        if (face == null) {
            return new Point(blockX + 0.5, blockY, blockZ + 0.5);
        }
        return new Point(blockX + 0.5 + face.dx(), blockY + face.dy(), blockZ + 0.5 + face.dz());
    }

    private HologramSpawn() {
    }
}
