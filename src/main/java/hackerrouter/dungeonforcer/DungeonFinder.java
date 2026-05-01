package hackerrouter.dungeonforcer;

import hackerrouter.dungeonforcer.rng.WorldgenRandom;
import hackerrouter.dungeonforcer.rng.XoroshiroRandomSource;
import hackerrouter.dungeonforcer.util.Chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DungeonFinder {

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
    public static final byte BLOCK_SPAWNER = SOLID | SPAWNER;
    public static final byte BLOCK_CHEST = SOLID | CHEST;

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

    private FeatureSimulator featureSim;

    public DungeonFinder() {
        this.buffer = new byte[BUFFER_SIZE];
        this.results = new ArrayList<>();
    }

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
        this.featureSim = new FeatureSimulator(buffer, chunkBlockX, chunkBlockZ);

        WorldgenRandom random = new WorldgenRandom(0L);
        long decorationSeed = random.setDecorationSeed(worldSeed, chunkBlockX, chunkBlockZ);

        random.setFeatureSeed(decorationSeed, featureIndexNormal, STEP_ORDINAL);
        long normalRngLo = random.getSeedLo();
        long normalRngHi = random.getSeedHi();

        long deepRngLo = 0, deepRngHi = 0;
        boolean hasDeep = featureIndexDeep >= 0;
        if (hasDeep) {
            random.setFeatureSeed(decorationSeed, featureIndexDeep, STEP_ORDINAL);
            deepRngLo = random.getSeedLo();
            deepRngHi = random.getSeedHi();
        }

        SpawnerCombination current = new SpawnerCombination();
        recursiveSearchNormal(normalRngLo, normalRngHi,
                0, NORMAL_ATTEMPTS, NORMAL_Y_MIN, NORMAL_Y_MAX,
                hasDeep, deepRngLo, deepRngHi,
                current);

        Collections.sort(results);
        if (results.size() > MAX_RESULTS) {
            results = new ArrayList<>(results.subList(0, MAX_RESULTS));
        }
        return results;
    }

    private void recursiveSearchNormal(long rngLo, long rngHi,
                                       int attemptIndex, int totalAttempts,
                                       int yMin, int yMax,
                                       boolean hasDeep, long deepRngLo, long deepRngHi,
                                       SpawnerCombination current) {
        if (attemptIndex >= totalAttempts) {
            if (hasDeep) {
                recursiveSearchDeep(deepRngLo, deepRngHi,
                        0, DEEP_ATTEMPTS, DEEP_Y_MIN, DEEP_Y_MAX, current);
            } else {
                if (current.spawnerCount > 0) {
                    int minPoints = results.size() >= MAX_RESULTS ?
                            results.get(results.size() - 1).points : 0;
                    if (current.countPoints(minPoints, searchType, preferredType)) {
                        addResult(current.copy());
                    }
                }
            }
            return;
        }

        WorldgenRandom rng = new WorldgenRandom(new XoroshiroRandomSource(rngLo, rngHi));
        int placementX = rng.nextInt(16);
        int placementZ = rng.nextInt(16);
        int yRange = yMax - yMin + 1;
        int placementY = rng.nextInt(yRange) + yMin;

        int originX = chunkBlockX + placementX;
        int originY = placementY;
        int originZ = chunkBlockZ + placementZ;

        FeatureSimulator.PlaceResult result = featureSim.simulate(
                rng, originX, originY, originZ, false, attemptIndex);

        long nextLo = rng.getSeedLo();
        long nextHi = rng.getSeedHi();

        if (!result.passed()) {
            recursiveSearchNormal(nextLo, nextHi, attemptIndex + 1, totalAttempts,
                    yMin, yMax, hasDeep, deepRngLo, deepRngHi, current);
            return;
        }

        WorldgenRandom skipRng = new WorldgenRandom(new XoroshiroRandomSource(rngLo, rngHi));
        skipRng.nextInt(16); skipRng.nextInt(16); skipRng.nextInt(yRange);
        skipRng.nextInt(2); skipRng.nextInt(2);
        long skipLo = skipRng.getSeedLo();
        long skipHi = skipRng.getSeedHi();

        featureSim.undoAll();
        recursiveSearchNormal(skipLo, skipHi, attemptIndex + 1, totalAttempts,
                yMin, yMax, hasDeep, deepRngLo, deepRngHi, current);

        rng = new WorldgenRandom(new XoroshiroRandomSource(rngLo, rngHi));
        rng.nextInt(16); rng.nextInt(16); rng.nextInt(yRange);
        result = featureSim.simulate(rng, originX, originY, originZ, false, attemptIndex);
        nextLo = rng.getSeedLo();
        nextHi = rng.getSeedHi();

        if (result.passed()) {
            Spawner spawner = result.spawner;
            boolean isLastNormal = (attemptIndex == totalAttempts - 1) && !hasDeep;
            if (!isLastNormal || !searchType.hasType || spawner.type == preferredType) {
                if (current.spawnerCount < SpawnerCombination.MAX_SPAWNERS) {
                    current.spawners[current.spawnerCount] = spawner;
                    current.spawnerCount++;
                    recursiveSearchNormal(nextLo, nextHi, attemptIndex + 1, totalAttempts,
                            yMin, yMax, hasDeep, deepRngLo, deepRngHi, current);
                    current.spawnerCount--;
                }
            }
            featureSim.undoAll();
        }
    }

    private void recursiveSearchDeep(long rngLo, long rngHi,
                                     int attemptIndex, int totalAttempts,
                                     int yMin, int yMax,
                                     SpawnerCombination current) {
        if (attemptIndex >= totalAttempts) {
            if (current.spawnerCount > 0) {
                int minPoints = results.size() >= MAX_RESULTS ?
                        results.get(results.size() - 1).points : 0;
                if (current.countPoints(minPoints, searchType, preferredType)) {
                    addResult(current.copy());
                }
            }
            return;
        }

        WorldgenRandom rng = new WorldgenRandom(new XoroshiroRandomSource(rngLo, rngHi));
        int placementX = rng.nextInt(16);
        int placementZ = rng.nextInt(16);
        int yRange = yMax - yMin + 1;
        int placementY = rng.nextInt(yRange) + yMin;

        int originX = chunkBlockX + placementX;
        int originY = placementY;
        int originZ = chunkBlockZ + placementZ;

        FeatureSimulator.PlaceResult result = featureSim.simulate(
                rng, originX, originY, originZ, true, attemptIndex);

        long nextLo = rng.getSeedLo();
        long nextHi = rng.getSeedHi();

        if (!result.passed()) {
            recursiveSearchDeep(nextLo, nextHi, attemptIndex + 1, totalAttempts,
                    yMin, yMax, current);
            return;
        }

        WorldgenRandom skipRng = new WorldgenRandom(new XoroshiroRandomSource(rngLo, rngHi));
        skipRng.nextInt(16); skipRng.nextInt(16); skipRng.nextInt(yRange);
        skipRng.nextInt(2); skipRng.nextInt(2);
        long skipLo = skipRng.getSeedLo();
        long skipHi = skipRng.getSeedHi();

        featureSim.undoAll();
        recursiveSearchDeep(skipLo, skipHi, attemptIndex + 1, totalAttempts,
                yMin, yMax, current);

        rng = new WorldgenRandom(new XoroshiroRandomSource(rngLo, rngHi));
        rng.nextInt(16); rng.nextInt(16); rng.nextInt(yRange);
        result = featureSim.simulate(rng, originX, originY, originZ, true, attemptIndex);
        nextLo = rng.getSeedLo();
        nextHi = rng.getSeedHi();

        if (result.passed()) {
            Spawner spawner = result.spawner;
            boolean isLast = (attemptIndex == totalAttempts - 1);
            if (!isLast || !searchType.hasType || spawner.type == preferredType) {
                if (current.spawnerCount < SpawnerCombination.MAX_SPAWNERS) {
                    current.spawners[current.spawnerCount] = spawner;
                    current.spawnerCount++;
                    recursiveSearchDeep(nextLo, nextHi, attemptIndex + 1, totalAttempts,
                            yMin, yMax, current);
                    current.spawnerCount--;
                }
            }
            featureSim.undoAll();
        }
    }

    private void addResult(SpawnerCombination combo) {
        results.add(combo);
        if (results.size() > MAX_RESULTS * 2) {
            Collections.sort(results);
            results.subList(MAX_RESULTS, results.size()).clear();
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
        public final int localX, localZ;
        public final int sizeX, sizeZ;
        public final boolean isDeep;
        public final int attemptIndex;
        public final long rngSeedLo, rngSeedHi;

        public DungeonAttempt(int originX, int originY, int originZ,
                              int sizeX, int sizeZ,
                              int localX, int localZ,
                              boolean isDeep, int attemptIndex,
                              long rngSeedLo, long rngSeedHi) {
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.localX = localX;
            this.localZ = localZ;
            this.isDeep = isDeep;
            this.attemptIndex = attemptIndex;
            this.rngSeedLo = rngSeedLo;
            this.rngSeedHi = rngSeedHi;
        }
    }

    @FunctionalInterface
    public interface BlockReader {
        byte getBlockState(int worldX, int worldY, int worldZ);
    }
}
