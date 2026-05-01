package hackerrouter.dungeonforcer;

import hackerrouter.dungeonforcer.rng.WorldgenRandom;
import hackerrouter.dungeonforcer.rng.XoroshiroRandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DungeonFinder {

    private static final SpawnerType[] MOB_SPAWNER_ENTITIES = {
            SpawnerType.SKELETON, SpawnerType.ZOMBIE, SpawnerType.ZOMBIE, SpawnerType.SPIDER
    };

    public static final int WORLD_MIN_Y = -64;
    public static final int WORLD_MAX_Y = 319;
    public static final int WORLD_HEIGHT = 384;

    public static final int BUFFER_XZ = 24;
    public static final int BUFFER_Y = WORLD_HEIGHT;
    public static final int BUFFER_SIZE = BUFFER_XZ * BUFFER_XZ * BUFFER_Y;

    public static final byte UNKNOWN = 0;
    public static final byte SOLID = 1;
    public static final byte AIR = 2;
    public static final byte CHEST = 4;
    public static final byte SPAWNER = 8;

    public static final int NORMAL_ATTEMPTS = 10;
    public static final int DEEP_ATTEMPTS = 4;

    public static final int NORMAL_Y_MIN = 0;
    public static final int NORMAL_Y_MAX = 319;
    public static final int DEEP_Y_MIN = -58;
    public static final int DEEP_Y_MAX = -1;

    public static final int STEP_ORDINAL = 3;

    public static final int MAX_RESULTS = 50;

    private byte[] buffer;

    private int chunkBlockX;
    private int chunkBlockZ;

    private List<SpawnerCombination> results;

    private SearchType searchType;
    private SpawnerType preferredType;
    private int activationRange;

    public DungeonFinder() {
        this.buffer = new byte[BUFFER_SIZE];
        this.results = new ArrayList<>();
        this.activationRange = 16; // 默认激活范围
    }

    private byte getBlock(int bx, int by, int bz) {
        if (bx < 0 || bx >= BUFFER_XZ || by < 0 || by >= BUFFER_Y || bz < 0 || bz >= BUFFER_XZ) {
            return UNKNOWN;
        }
        return buffer[bx * BUFFER_XZ * BUFFER_Y + by * BUFFER_XZ + bz];
    }

    private void setBlock(int bx, int by, int bz, byte state) {
        if (bx >= 0 && bx < BUFFER_XZ && by >= 0 && by < BUFFER_Y && bz >= 0 && bz < BUFFER_XZ) {
            buffer[bx * BUFFER_XZ * BUFFER_Y + by * BUFFER_XZ + bz] = state;
        }
    }

    private int worldToBufferX(int worldX) { return worldX - chunkBlockX + 4; }
    private int worldToBufferY(int worldY) { return worldY - WORLD_MIN_Y; }
    private int worldToBufferZ(int worldZ) { return worldZ - chunkBlockZ + 4; }

    public List<SpawnerCombination> runForChunk(
            int chunkX, int chunkZ, long worldSeed,
            int featureIndexNormal, int featureIndexDeep,
            SearchType searchType, SpawnerType preferredType,
            BlockReader blockReader) {

        this.searchType = searchType;
        this.preferredType = preferredType;
        this.chunkBlockX = chunkX * 16;
        this.chunkBlockZ = chunkZ * 16;
        this.results.clear();

        fillBuffer(blockReader);

        WorldgenRandom random = new WorldgenRandom(0L);
        long decorationSeed = random.setDecorationSeed(worldSeed, chunkBlockX, chunkBlockZ);

        DungeonAttempt[] normalAttempts = simulatePlacement(
                random, decorationSeed, featureIndexNormal, STEP_ORDINAL,
                NORMAL_ATTEMPTS, NORMAL_Y_MIN, NORMAL_Y_MAX, false);

        DungeonAttempt[] deepAttempts = null;
        if (featureIndexDeep >= 0) {
            deepAttempts = simulatePlacement(
                    random, decorationSeed, featureIndexDeep, STEP_ORDINAL,
                    DEEP_ATTEMPTS, DEEP_Y_MIN, DEEP_Y_MAX, true);
        }

        List<DungeonAttempt> allAttempts = new ArrayList<>();
        for (DungeonAttempt a : normalAttempts) allAttempts.add(a);
        if (deepAttempts != null) {
            for (DungeonAttempt a : deepAttempts) allAttempts.add(a);
        }

        SpawnerCombination current = new SpawnerCombination();
        recursiveSearch(allAttempts, 0, current);

        Collections.sort(results);
        if (results.size() > MAX_RESULTS) {
            results = new ArrayList<>(results.subList(0, MAX_RESULTS));
        }
        return results;
    }

    private DungeonAttempt[] simulatePlacement(
            WorldgenRandom random, long decorationSeed,
            int featureIndex, int stepOrdinal,
            int attempts, int yMin, int yMax, boolean isDeep) {

        random.setFeatureSeed(decorationSeed, featureIndex, stepOrdinal);

        int yRange = yMax - yMin + 1;
        DungeonAttempt[] result = new DungeonAttempt[attempts];

        for (int i = 0; i < attempts; i++) {
            int placementX = random.nextInt(16);
            int placementZ = random.nextInt(16);
            int placementY = random.nextInt(yRange) + yMin;

            int originX = chunkBlockX + placementX;
            int originY = placementY;
            int originZ = chunkBlockZ + placementZ;

            int sizeX = random.nextInt(2) + 2;
            int sizeZ = random.nextInt(2) + 2;

            result[i] = new DungeonAttempt(
                    originX, originY, originZ, sizeX, sizeZ,
                    isDeep, i, random.getSeedLo(), random.getSeedHi());

            simulateFeatureRNG(random, originX, originY, originZ, sizeX, sizeZ);
        }

        return result;
    }

    private void simulateFeatureRNG(WorldgenRandom random,
                                    int originX, int originY, int originZ, int sizeX, int sizeZ) {
    }

    private void recursiveSearch(List<DungeonAttempt> attempts, int index,
                                 SpawnerCombination current) {
        if (index >= attempts.size()) {
            if (current.spawnerCount > 0) {
                current.countPoints(activationRange, searchType, preferredType);
                addResult(current.copy());
            }
            return;
        }

        DungeonAttempt attempt = attempts.get(index);

        DungeonCheckResult check = checkDungeonConditions(attempt);

        if (check.status == CheckStatus.DEFINITELY_FAIL) {
            recursiveSearch(attempts, index + 1, current);
        } else if (check.status == CheckStatus.DEFINITELY_PASS) {
            Spawner spawner = createSpawner(attempt, check);
            current.spawners[current.spawnerCount] = spawner;
            current.spawnerCount++;
            recursiveSearch(attempts, index + 1, current);
            current.spawnerCount--;
        } else {
            recursiveSearch(attempts, index + 1, current);

            if (check.unknownPositions != null && check.unknownPositions.size() <= 20) {
                byte[] savedBlocks = saveUnknownBlocks(check.unknownPositions);

                if (trySetBlocks(check)) {
                    Spawner spawner = createSpawner(attempt, check);
                    current.spawners[current.spawnerCount] = spawner;
                    current.spawnerCount++;
                    recursiveSearch(attempts, index + 1, current);
                    current.spawnerCount--;
                }

                restoreUnknownBlocks(check.unknownPositions, savedBlocks);
            }
        }
    }

    private DungeonCheckResult checkDungeonConditions(DungeonAttempt attempt) {
        int ox = attempt.originX;
        int oy = attempt.originY;
        int oz = attempt.originZ;
        int sizeX = attempt.sizeX;
        int sizeZ = attempt.sizeZ;

        int minX = -sizeX - 1;
        int maxX = sizeX + 1;
        int minZ = -sizeZ - 1;
        int maxZ = sizeZ + 1;

        DungeonCheckResult result = new DungeonCheckResult();
        result.unknownPositions = new ArrayList<>();

        int knownHoles = 0;
        int unknownHolePositions = 0;
        boolean definitelyFail = false;

        for (int dx = minX; dx <= maxX; dx++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    int wx = ox + dx;
                    int wy = oy + dy;
                    int wz = oz + dz;
                    int bx = worldToBufferX(wx);
                    int by = worldToBufferY(wy);
                    int bz = worldToBufferZ(wz);
                    byte block = getBlock(bx, by, bz);

                    if (dy == -1) {
                        if (block == AIR) {
                            definitelyFail = true;
                            break;
                        } else if (block == UNKNOWN) {
                            result.unknownPositions.add(new int[]{bx, by, bz, SOLID});
                        }
                    }
                    else if (dy == 4) {
                        if (block == AIR) {
                            definitelyFail = true;
                            break;
                        } else if (block == UNKNOWN) {
                            result.unknownPositions.add(new int[]{bx, by, bz, SOLID});
                        }
                    }
                    else if ((dx == minX || dx == maxX || dz == minZ || dz == maxZ)
                            && dy == 0) {
                        byte blockAbove = getBlock(bx, by + 1, bz);
                        if (block == AIR && blockAbove == AIR) {
                            knownHoles++;
                        } else if (block == UNKNOWN || blockAbove == UNKNOWN) {
                            unknownHolePositions++;
                            result.unknownPositions.add(new int[]{bx, by, bz, -1});
                        }
                    }
                }
                if (definitelyFail) break;
            }
            if (definitelyFail) break;
        }

        if (definitelyFail) {
            result.status = CheckStatus.DEFINITELY_FAIL;
            return result;
        }

        result.knownHoles = knownHoles;
        result.unknownHolePositions = unknownHolePositions;

        if (knownHoles > 5) {
            result.status = CheckStatus.DEFINITELY_FAIL;
        } else if (knownHoles >= 1 && knownHoles <= 5 && unknownHolePositions == 0
                && result.unknownPositions.isEmpty()) {
            result.status = CheckStatus.DEFINITELY_PASS;
            result.exitCount = knownHoles;
        } else {
            result.status = CheckStatus.UNCERTAIN;
        }

        return result;
    }

    /**
     * 创建 Spawner 对象，通过模拟 MonsterRoomFeature.place() 的 RNG 序列
     * 来计算 spawner type。
     *
     * RNG 序列（在 sizeX/sizeZ 之后）：
     * 1. 地板 mossy 循环：每个地板方块调用 nextInt(4)
     *    地板方块数 = (2*sizeX+3) * (2*sizeZ+3)
     * 2. Chest 放置循环：2 次外循环 × 最多 3 次内循环
     *    每次内循环消耗 nextInt(sizeX*2+1) + nextInt(sizeZ*2+1)
     *    假设全部失败（最大 RNG 消耗 = 12 次调用）
     * 3. Util.getRandom(MOBS, random) = MOBS[nextInt(4)]
     */
    private Spawner createSpawner(DungeonAttempt attempt, DungeonCheckResult check) {
        // 从保存的 RNG 状态恢复（sizeX/sizeZ 之后的状态）
        WorldgenRandom rng = new WorldgenRandom(
                new XoroshiroRandomSource(attempt.rngSeedLo, attempt.rngSeedHi));

        int sizeX = attempt.sizeX;
        int sizeZ = attempt.sizeZ;

        // 1. 模拟地板 mossy cobblestone 循环
        // MonsterRoomFeature line 71-91: for dx in minX..maxX, dy in 3..-1, dz in minZ..maxZ
        // 只有 dy == -1 且方块实心且非 chest 时调用 nextInt(4)
        // 地牢成功时地板必须全部实心，所以每个地板方块都消耗 1 次 nextInt(4)
        int floorWidth = (sizeX + 1) * 2 + 1;  // maxX - minX + 1 = (sizeX+1) - (-sizeX-1) + 1
        int floorDepth = (sizeZ + 1) * 2 + 1;  // maxZ - minZ + 1
        int floorBlocks = floorWidth * floorDepth;
        for (int i = 0; i < floorBlocks; i++) {
            rng.nextInt(4);
        }

        // 2. 模拟 chest 放置循环（假设全部失败，最大 RNG 消耗）
        // 2 次外循环 × 3 次内循环 = 6 次，每次 2 个 RNG 调用
        for (int cc = 0; cc < 2; cc++) {
            for (int i = 0; i < 3; i++) {
                rng.nextInt(sizeX * 2 + 1);
                rng.nextInt(sizeZ * 2 + 1);
            }
        }

        // 3. Spawner type: Util.getRandom(MOBS, random) = MOBS[nextInt(4)]
        int mobIndex = rng.nextInt(4);
        SpawnerType type = SpawnerType.fromMobIndex(mobIndex);

        return new Spawner(
                attempt.originX, attempt.originY, attempt.originZ,
                attempt.sizeX, attempt.sizeZ, type,
                check.exitCount, check.unknownPositions != null ? check.unknownPositions.size() : 0,
                attempt.isDeep, attempt.attemptIndex);
    }

    private boolean trySetBlocks(DungeonCheckResult check) {
        for (int[] pos : check.unknownPositions) {
            if (pos[3] == SOLID) {
                setBlock(pos[0], pos[1], pos[2], SOLID);
            }
        }
        int totalHoles = check.knownHoles;
        return totalHoles >= 1 && totalHoles <= 5;
    }

    private byte[] saveUnknownBlocks(List<int[]> positions) {
        byte[] saved = new byte[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            int[] pos = positions.get(i);
            saved[i] = getBlock(pos[0], pos[1], pos[2]);
        }
        return saved;
    }

    private void restoreUnknownBlocks(List<int[]> positions, byte[] saved) {
        for (int i = 0; i < positions.size(); i++) {
            int[] pos = positions.get(i);
            setBlock(pos[0], pos[1], pos[2], saved[i]);
        }
    }

    private void addResult(SpawnerCombination combo) {
        results.add(combo);
        if (results.size() > MAX_RESULTS * 2) {
            Collections.sort(results);
            results = new ArrayList<>(results.subList(0, MAX_RESULTS));
        }
    }

    private void fillBuffer(BlockReader blockReader) {
        java.util.Arrays.fill(buffer, UNKNOWN);
        for (int bx = 0; bx < BUFFER_XZ; bx++) {
            for (int by = 0; by < BUFFER_Y; by++) {
                for (int bz = 0; bz < BUFFER_XZ; bz++) {
                    int wx = chunkBlockX - 4 + bx;
                    int wy = WORLD_MIN_Y + by;
                    int wz = chunkBlockZ - 4 + bz;
                    byte state = blockReader.getBlockState(wx, wy, wz);
                    buffer[bx * BUFFER_XZ * BUFFER_Y + by * BUFFER_XZ + bz] = state;
                }
            }
        }
    }

    public static class DungeonAttempt {
        public final int originX, originY, originZ;
        public final int sizeX, sizeZ;
        public final boolean isDeep;
        public final int attemptIndex;
        public final long rngSeedLo, rngSeedHi;

        public DungeonAttempt(int originX, int originY, int originZ,
                              int sizeX, int sizeZ, boolean isDeep, int attemptIndex,
                              long rngSeedLo, long rngSeedHi) {
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.isDeep = isDeep;
            this.attemptIndex = attemptIndex;
            this.rngSeedLo = rngSeedLo;
            this.rngSeedHi = rngSeedHi;
        }

        @Override
        public String toString() {
            return String.format("Attempt{pos=(%d,%d,%d), size=(%d,%d), deep=%s, idx=%d}",
                    originX, originY, originZ, sizeX, sizeZ, isDeep, attemptIndex);
        }
    }

    private static class DungeonCheckResult {
        CheckStatus status;
        int knownHoles;
        int unknownHolePositions;
        int exitCount;
        List<int[]> unknownPositions;
    }

    private enum CheckStatus {
        DEFINITELY_PASS,
        DEFINITELY_FAIL,
        UNCERTAIN
    }

    @FunctionalInterface
    public interface BlockReader {
        byte getBlockState(int worldX, int worldY, int worldZ);
    }
}