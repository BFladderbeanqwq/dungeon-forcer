package hackerrouter.dungeonforcer;

import hackerrouter.dungeonforcer.rng.WorldgenRandom;
import hackerrouter.dungeonforcer.rng.XoroshiroRandomSource;

import java.util.ArrayList;
import java.util.List;

public class FeatureSimulator {

    private static final SpawnerType[] MOB_SPAWNER_ENTITIES = {
            SpawnerType.SKELETON, SpawnerType.ZOMBIE, SpawnerType.ZOMBIE, SpawnerType.SPIDER
    };

    private final byte[] buffer;
    private final int chunkBlockX;
    private final int chunkBlockZ;

    private final List<int[]> shellChanges = new ArrayList<>();
    private final List<int[]> chestChanges = new ArrayList<>();

    public FeatureSimulator(byte[] buffer, int chunkBlockX, int chunkBlockZ) {
        this.buffer = buffer;
        this.chunkBlockX = chunkBlockX;
        this.chunkBlockZ = chunkBlockZ;
    }

    public PlaceResult simulate(WorldgenRandom random, int originX, int originY, int originZ,
                                boolean isDeep, int attemptIndex) {
        shellChanges.clear();
        chestChanges.clear();

        int sizeX = random.nextInt(2) + 2;
        int sizeZ = random.nextInt(2) + 2;

        int lx = originX - chunkBlockX;
        int lz = originZ - chunkBlockZ;
        int sizeX1 = sizeX + 1;
        int sizeZ1 = sizeZ + 1;

        DungeonCheckResult check = checkConditions(lx, lz, originY, sizeX, sizeZ);
        if (!check.pass) {
            return new PlaceResult(null, check, sizeX, sizeZ, lx, lz);
        }

        simulateShell(random, lx, lz, originY, sizeX1, sizeZ1);
        long shellRngLo = random.getSeedLo();
        long shellRngHi = random.getSeedHi();

        simulateChests(random, lx, lz, originY, sizeX, sizeZ);

        int mobIndex = random.nextInt(4);
        SpawnerType type = SpawnerType.fromMobIndex(mobIndex);

        random.nextInt(1);

        int sbx = lx + 4;
        int sby = worldToBufferY(originY);
        int sbz = lz + 4;
        setAndRecordChest(sbx, sby, sbz, DungeonFinder.BLOCK_SPAWNER);

        Spawner spawner = new Spawner(
                originX, originY, originZ,
                sizeX, sizeZ, type,
                check.exitsNeeded, check.floorBlocksNeeded,
                isDeep, attemptIndex);

        addRenderData(spawner, lx, lz, originY, sizeX1, sizeZ1);

        return new PlaceResult(spawner, check, sizeX, sizeZ, lx, lz);
    }

    public void undoAll() {
        undoChests();
        for (int i = shellChanges.size() - 1; i >= 0; i--) {
            int[] c = shellChanges.get(i);
            buffer[c[0]] = (byte) c[1];
        }
        shellChanges.clear();
    }

    public void undoChests() {
        for (int i = chestChanges.size() - 1; i >= 0; i--) {
            int[] c = chestChanges.get(i);
            buffer[c[0]] = (byte) c[1];
        }
        chestChanges.clear();
    }

    private DungeonCheckResult checkConditions(int lx, int lz, int oy, int sizeX, int sizeZ) {
        int sizeX1 = sizeX + 1;
        int sizeZ1 = sizeZ + 1;
        int fullMinX = -sizeX1, fullMaxX = sizeX1;
        int fullMinZ = -sizeZ1, fullMaxZ = sizeZ1;

        int clampMinX = Math.max(0, lx + fullMinX);
        int clampMaxX = Math.min(15, lx + fullMaxX);
        int clampMinZ = Math.max(0, lz + fullMinZ);
        int clampMaxZ = Math.min(15, lz + fullMaxZ);

        int totalFloor = (fullMaxX - fullMinX + 1) * (fullMaxZ - fullMinZ + 1);
        int chunkFloor = (clampMaxX - clampMinX + 1) * (clampMaxZ - clampMinZ + 1);
        int floorBlocksNeeded = totalFloor - chunkFloor;
        if (oy == 1) floorBlocksNeeded = 0;

        for (int wx = clampMinX; wx <= clampMaxX; wx++) {
            for (int wz = clampMinZ; wz <= clampMaxZ; wz++) {
                int bx = wx + 4;
                int bz = wz + 4;
                byte floor = getBlock(bx, worldToBufferY(oy - 1), bz);
                if ((floor & DungeonFinder.SOLID) == 0) return DungeonCheckResult.fail();
                byte ceil = getBlock(bx, worldToBufferY(oy + 4), bz);
                if ((ceil & DungeonFinder.SOLID) == 0) return DungeonCheckResult.fail();
            }
        }

        int exitCount = 0;
        for (int wx = lx + fullMinX; wx <= lx + fullMaxX; wx++) {
            for (int wz = lz + fullMinZ; wz <= lz + fullMaxZ; wz++) {
                boolean isWall = (wx == lx + fullMinX || wx == lx + fullMaxX
                        || wz == lz + fullMinZ || wz == lz + fullMaxZ);
                if (!isWall) continue;
                if (wx < 0 || wx > 15 || wz < 0 || wz > 15) continue;
                int bx = wx + 4;
                int bz = wz + 4;
                byte b0 = getBlock(bx, worldToBufferY(oy), bz);
                byte b1 = getBlock(bx, worldToBufferY(oy + 1), bz);
                if (b0 == DungeonFinder.AIR && b1 == DungeonFinder.AIR) {
                    exitCount++;
                    if (exitCount > 5) return DungeonCheckResult.fail();
                }
            }
        }

        boolean pass = exitCount <= 5 && (exitCount >= 1 || floorBlocksNeeded != 0);
        if (!pass) return DungeonCheckResult.fail();

        int exitsNeeded = 5 - exitCount;
        return new DungeonCheckResult(true, exitsNeeded, floorBlocksNeeded);
    }

    private void simulateShell(WorldgenRandom rng, int lx, int lz, int oy, int sizeX1, int sizeZ1) {
        for (int dx = -sizeX1; dx <= sizeX1; dx++) {
            for (int dy = 3; dy >= -1; dy--) {
                for (int dz = -sizeZ1; dz <= sizeZ1; dz++) {
                    int wx = lx + dx;
                    int wz = lz + dz;
                    int bx = wx + 4;
                    int by = worldToBufferY(oy + dy);
                    int bz = wz + 4;

                    boolean isShell = (dx == -sizeX1 || dy == -1 || dz == -sizeZ1
                            || dx == sizeX1 || dy == 4 || dz == sizeZ1);

                    if (isShell) {
                        if (wx >= 0 && wx <= 15 && wz >= 0 && wz <= 15 && by >= 0) {
                            byte below = getBlock(bx, by - 1, bz);
                            if ((below & DungeonFinder.SOLID) == 0) {
                                setAndRecordShell(bx, by, bz, DungeonFinder.AIR);
                            } else {
                                byte cur = getBlock(bx, by, bz);
                                if ((cur & DungeonFinder.SOLID) != 0 && (cur & DungeonFinder.CHEST) == 0) {
                                    if (dy == -1) {
                                        rng.nextInt(4);
                                    }
                                    setAndRecordShell(bx, by, bz, DungeonFinder.SOLID);
                                }
                            }
                        }
                    } else {
                        if (wx >= 0 && wx <= 15 && wz >= 0 && wz <= 15) {
                            byte cur = getBlock(bx, by, bz);
                            if ((cur & DungeonFinder.CHEST) == 0 && (cur & DungeonFinder.SPAWNER) == 0) {
                                setAndRecordShell(bx, by, bz, DungeonFinder.AIR);
                            }
                        }
                    }
                }
            }
        }
    }

    private void simulateChests(WorldgenRandom rng, int lx, int lz, int oy, int sizeX, int sizeZ) {
        for (int cc = 0; cc < 2; cc++) {
            for (int i = 0; i < 3; i++) {
                int cx = lx + rng.nextInt(sizeX * 2 + 1) - sizeX;
                int cz = lz + rng.nextInt(sizeZ * 2 + 1) - sizeZ;
                int cbx = cx + 4;
                int cby = worldToBufferY(oy);
                int cbz = cz + 4;
                if (getBlock(cbx, cby, cbz) == DungeonFinder.AIR) {
                    int wallCount = 0;
                    if ((getBlock(cbx + 1, cby, cbz) & DungeonFinder.SOLID) != 0) wallCount++;
                    if ((getBlock(cbx - 1, cby, cbz) & DungeonFinder.SOLID) != 0) wallCount++;
                    if ((getBlock(cbx, cby, cbz + 1) & DungeonFinder.SOLID) != 0) wallCount++;
                    if ((getBlock(cbx, cby, cbz - 1) & DungeonFinder.SOLID) != 0) wallCount++;
                    if (wallCount == 1) {
                        setAndRecordChest(cbx, cby, cbz, DungeonFinder.BLOCK_CHEST);
                        rng.nextLong();
                        break;
                    }
                }
            }
        }
    }

    private void addRenderData(Spawner spawner, int lx, int lz, int oy, int sizeX1, int sizeZ1) {
        for (int dx = -sizeX1; dx <= sizeX1; dx++) {
            for (int dz = -sizeZ1; dz <= sizeZ1; dz++) {
                int wx = lx + dx;
                int wz = lz + dz;
                if (wx >= 0 && wx <= 15 && wz >= 0 && wz <= 15) continue;
                for (int dy : new int[]{-1, 4}) {
                    int bx = wx + 4;
                    int by = worldToBufferY(oy + dy);
                    int bz = wz + 4;
                    int wwx = bx + chunkBlockX - 4;
                    int wwy = by + DungeonFinder.WORLD_MIN_Y;
                    int wwz = bz + chunkBlockZ - 4;
                    spawner.blockModifications.add(new int[]{wwx, wwy, wwz, Spawner.ACTION_PLACE});
                }
            }
        }
    }

    private byte getBlock(int bx, int by, int bz) {
        if (by < 0 || by >= DungeonFinder.BUFFER_Y) return DungeonFinder.AIR;
        if (bx < 0 || bx >= DungeonFinder.BUFFER_XZ || bz < 0 || bz >= DungeonFinder.BUFFER_XZ)
            return DungeonFinder.UNKNOWN;
        return buffer[bx * DungeonFinder.BUFFER_XZ * DungeonFinder.BUFFER_Y + by * DungeonFinder.BUFFER_XZ + bz];
    }

    private void setAndRecordShell(int bx, int by, int bz, byte newState) {
        if (bx < 0 || bx >= DungeonFinder.BUFFER_XZ || by < 0 || by >= DungeonFinder.BUFFER_Y
                || bz < 0 || bz >= DungeonFinder.BUFFER_XZ) return;
        int idx = bx * DungeonFinder.BUFFER_XZ * DungeonFinder.BUFFER_Y + by * DungeonFinder.BUFFER_XZ + bz;
        byte old = buffer[idx];
        if (old != newState) {
            buffer[idx] = newState;
            shellChanges.add(new int[]{idx, old});
        }
    }

    private void setAndRecordChest(int bx, int by, int bz, byte newState) {
        if (bx < 0 || bx >= DungeonFinder.BUFFER_XZ || by < 0 || by >= DungeonFinder.BUFFER_Y
                || bz < 0 || bz >= DungeonFinder.BUFFER_XZ) return;
        int idx = bx * DungeonFinder.BUFFER_XZ * DungeonFinder.BUFFER_Y + by * DungeonFinder.BUFFER_XZ + bz;
        byte old = buffer[idx];
        if (old != newState) {
            buffer[idx] = newState;
            chestChanges.add(new int[]{idx, old});
        }
    }

    private int worldToBufferY(int worldY) { return worldY - DungeonFinder.WORLD_MIN_Y; }

    public static class PlaceResult {
        public final Spawner spawner;
        public final DungeonCheckResult check;
        public final int sizeX, sizeZ;
        public final int localX, localZ;

        PlaceResult(Spawner spawner, DungeonCheckResult check, int sizeX, int sizeZ, int localX, int localZ) {
            this.spawner = spawner;
            this.check = check;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.localX = localX;
            this.localZ = localZ;
        }

        public boolean passed() { return spawner != null; }
    }

    public static class DungeonCheckResult {
        public final boolean pass;
        public final int exitsNeeded;
        public final int floorBlocksNeeded;

        DungeonCheckResult(boolean pass, int exitsNeeded, int floorBlocksNeeded) {
            this.pass = pass;
            this.exitsNeeded = exitsNeeded;
            this.floorBlocksNeeded = floorBlocksNeeded;
        }

        static DungeonCheckResult fail() {
            return new DungeonCheckResult(false, 0, 0);
        }
    }
}
