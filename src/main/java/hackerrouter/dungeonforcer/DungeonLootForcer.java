package hackerrouter.dungeonforcer;

import hackerrouter.dungeonforcer.loot.SimpleDungeonLootSimulator;
import hackerrouter.dungeonforcer.rng.WorldgenRandom;
import hackerrouter.dungeonforcer.rng.XoroshiroRandomSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DungeonLootForcer {
    private final byte[] buffer = new byte[DungeonFinder.BUFFER_SIZE];
    private int chunkBlockX;
    private int chunkBlockZ;

    public List<DungeonLootBlueprint> runForChunk(
            int chunkX, int chunkZ, long worldSeed,
            int featureIndexNormal, int featureIndexDeep,
            String targetItem, int maxResults,
            int maxFloorBreaks, int maxChestBlockers,
            DungeonFinder.BlockReader blockReader) {

        this.chunkBlockX = chunkX * 16;
        this.chunkBlockZ = chunkZ * 16;
        fillBuffer(blockReader);

        List<DungeonLootBlueprint> results = new ArrayList<>();
        WorldgenRandom random = new WorldgenRandom(0L);
        long decorationSeed = random.setDecorationSeed(worldSeed, chunkBlockX, chunkBlockZ);

        searchFeature(results, decorationSeed, featureIndexNormal, false,
                DungeonFinder.NORMAL_ATTEMPTS, DungeonFinder.NORMAL_Y_MIN, DungeonFinder.NORMAL_Y_MAX,
                targetItem, maxResults, maxFloorBreaks, maxChestBlockers);

        if (featureIndexDeep >= 0 && results.size() < maxResults) {
            searchFeature(results, decorationSeed, featureIndexDeep, true,
                    DungeonFinder.DEEP_ATTEMPTS, DungeonFinder.DEEP_Y_MIN, DungeonFinder.DEEP_Y_MAX,
                    targetItem, maxResults, maxFloorBreaks, maxChestBlockers);
        }

        return results;
    }

    private void searchFeature(List<DungeonLootBlueprint> results, long decorationSeed, int featureIndex,
                               boolean deep, int attempts, int yMin, int yMax,
                               String targetItem, int maxResults,
                               int maxFloorBreaks, int maxChestBlockers) {
        WorldgenRandom rng = new WorldgenRandom(0L);
        rng.setFeatureSeed(decorationSeed, featureIndex, DungeonFinder.STEP_ORDINAL);
        int yRange = yMax - yMin + 1;
        FeatureSimulator featureSimulator = new FeatureSimulator(buffer, chunkBlockX, chunkBlockZ);

        for (int attempt = 0; attempt < attempts && results.size() < maxResults; attempt++) {
            int px = rng.nextInt(16);
            int pz = rng.nextInt(16);
            int py = rng.nextInt(yRange) + yMin;
            long attemptLo = rng.getSeedLo();
            long attemptHi = rng.getSeedHi();

            int originX = chunkBlockX + px;
            int originZ = chunkBlockZ + pz;

            WorldgenRandom attemptRng = new WorldgenRandom(new XoroshiroRandomSource(attemptLo, attemptHi));
            FeatureSimulator.PlaceResult placed = featureSimulator.simulate(attemptRng, originX, py, originZ, deep, attempt);

            if (placed.passed()) {
                AttemptGeometry geometry = buildGeometry(originX, py, originZ, placed.sizeX, placed.sizeZ);
                enumerateFloorMasks(results, attemptLo, attemptHi, geometry, deep, attempt,
                        targetItem, maxResults, maxFloorBreaks, maxChestBlockers);
            }

            rng = attemptRng;
            featureSimulator.undoAll();
        }
    }

    private void enumerateFloorMasks(List<DungeonLootBlueprint> results,
                                     long attemptLo, long attemptHi, AttemptGeometry geometry,
                                     boolean deep, int attemptIndex, String targetItem, int maxResults,
                                     int maxFloorBreaks, int maxChestBlockers) {
        int floorLimit = Math.min(maxFloorBreaks, geometry.floorCells.size());
        for (int breaks = 0; breaks <= floorLimit && results.size() < maxResults; breaks++) {
            enumerateFloorCombination(results, attemptLo, attemptHi, geometry, deep, attemptIndex, targetItem,
                    maxResults, maxChestBlockers, 0, breaks, new ArrayList<>());
        }
    }

    private void enumerateFloorCombination(List<DungeonLootBlueprint> results,
                                           long attemptLo, long attemptHi, AttemptGeometry geometry,
                                           boolean deep, int attemptIndex, String targetItem, int maxResults,
                                           int maxChestBlockers, int start, int remaining, List<Cell> floorBreaks) {
        if (results.size() >= maxResults) return;
        if (remaining == 0) {
            enumerateBlockerMasks(results, attemptLo, attemptHi, geometry, deep, attemptIndex, targetItem,
                    maxResults, maxChestBlockers, floorBreaks);
            return;
        }
        for (int i = start; i <= geometry.floorCells.size() - remaining; i++) {
            floorBreaks.add(geometry.floorCells.get(i));
            enumerateFloorCombination(results, attemptLo, attemptHi, geometry, deep, attemptIndex, targetItem,
                    maxResults, maxChestBlockers, i + 1, remaining - 1, floorBreaks);
            floorBreaks.remove(floorBreaks.size() - 1);
            if (results.size() >= maxResults) return;
        }
    }

    private void enumerateBlockerMasks(List<DungeonLootBlueprint> results,
                                       long attemptLo, long attemptHi, AttemptGeometry geometry,
                                       boolean deep, int attemptIndex, String targetItem, int maxResults,
                                       int maxChestBlockers, List<Cell> floorBreaks) {
        int blockerLimit = Math.min(maxChestBlockers, geometry.blockerCells.size());
        for (int blockers = 0; blockers <= blockerLimit && results.size() < maxResults; blockers++) {
            enumerateBlockerCombination(results, attemptLo, attemptHi, geometry, deep, attemptIndex, targetItem,
                    maxResults, 0, blockers, floorBreaks, new ArrayList<>());
        }
    }

    private void enumerateBlockerCombination(List<DungeonLootBlueprint> results,
                                             long attemptLo, long attemptHi, AttemptGeometry geometry,
                                             boolean deep, int attemptIndex, String targetItem, int maxResults,
                                             int start, int remaining, List<Cell> floorBreaks, List<Cell> blockers) {
        if (results.size() >= maxResults) return;
        if (remaining == 0) {
            SimulationResult sim = simulateLayout(attemptLo, attemptHi, geometry, floorBreaks, blockers);
            if (sim.firstLootSeed != 0L && SimpleDungeonLootSimulator.contains(sim.firstLootSeed, targetItem)) {
                results.add(toBlueprint(geometry, deep, attemptIndex, 1, sim, floorBreaks, blockers));
            } else if (sim.secondLootSeed != 0L && SimpleDungeonLootSimulator.contains(sim.secondLootSeed, targetItem)) {
                results.add(toBlueprint(geometry, deep, attemptIndex, 2, sim, floorBreaks, blockers));
            }
            return;
        }
        for (int i = start; i <= geometry.blockerCells.size() - remaining; i++) {
            blockers.add(geometry.blockerCells.get(i));
            enumerateBlockerCombination(results, attemptLo, attemptHi, geometry, deep, attemptIndex, targetItem,
                    maxResults, i + 1, remaining - 1, floorBreaks, blockers);
            blockers.remove(blockers.size() - 1);
            if (results.size() >= maxResults) return;
        }
    }

    private SimulationResult simulateLayout(long attemptLo, long attemptHi, AttemptGeometry geometry,
                                            List<Cell> floorBreaks, List<Cell> blockers) {
        WorldgenRandom rng = new WorldgenRandom(new XoroshiroRandomSource(attemptLo, attemptHi));
        int sizeX = rng.nextInt(2) + 2;
        int sizeZ = rng.nextInt(2) + 2;
        if (sizeX != geometry.sizeX || sizeZ != geometry.sizeZ) {
            return new SimulationResult();
        }

        Set<Long> floorAir = toKeySet(floorBreaks);
        Set<Long> forcedSolid = toKeySet(blockers);
        Set<Long> generatedChests = new HashSet<>();

        for (int dx = -geometry.sizeX - 1; dx <= geometry.sizeX + 1; dx++) {
            for (int dy = 3; dy >= -1; dy--) {
                for (int dz = -geometry.sizeZ - 1; dz <= geometry.sizeZ + 1; dz++) {
                    boolean shell = dx == -geometry.sizeX - 1 || dy == -1 || dz == -geometry.sizeZ - 1
                            || dx == geometry.sizeX + 1 || dy == 4 || dz == geometry.sizeZ + 1;
                    if (shell && dy == -1 && !floorAir.contains(Cell.key(dx, dz))) {
                        rng.nextInt(4);
                    }
                }
            }
        }

        SimulationResult result = new SimulationResult();
        int placed = 0;
        for (int cc = 0; cc < 2; cc++) {
            for (int i = 0; i < 3; i++) {
                int cx = rng.nextInt(geometry.sizeX * 2 + 1) - geometry.sizeX;
                int cz = rng.nextInt(geometry.sizeZ * 2 + 1) - geometry.sizeZ;
                long key = Cell.key(cx, cz);
                if (generatedChests.contains(key) || forcedSolid.contains(key)) {
                    continue;
                }
                if (wallCount(cx, cz, geometry, forcedSolid) == 1) {
                    generatedChests.add(key);
                    long lootSeed = rng.nextLong();
                    placed++;
                    result.chests.add(new Cell(cx, cz));
                    if (placed == 1) result.firstLootSeed = lootSeed;
                    if (placed == 2) result.secondLootSeed = lootSeed;
                    break;
                }
            }
        }

        result.spawnerType = SpawnerType.fromMobIndex(rng.nextInt(4));
        return result;
    }

    private int wallCount(int dx, int dz, AttemptGeometry geometry, Set<Long> forcedSolid) {
        int count = 0;
        if (isSolidAtChestY(dx + 1, dz, geometry, forcedSolid)) count++;
        if (isSolidAtChestY(dx - 1, dz, geometry, forcedSolid)) count++;
        if (isSolidAtChestY(dx, dz + 1, geometry, forcedSolid)) count++;
        if (isSolidAtChestY(dx, dz - 1, geometry, forcedSolid)) count++;
        return count;
    }

    private boolean isSolidAtChestY(int dx, int dz, AttemptGeometry geometry, Set<Long> forcedSolid) {
        if (dx == -geometry.sizeX - 1 || dx == geometry.sizeX + 1
                || dz == -geometry.sizeZ - 1 || dz == geometry.sizeZ + 1) {
            return true;
        }
        return forcedSolid.contains(Cell.key(dx, dz));
    }

    private DungeonLootBlueprint toBlueprint(AttemptGeometry geometry, boolean deep, int attemptIndex,
                                             int hitChestIndex, SimulationResult sim,
                                             List<Cell> floorBreaks, List<Cell> blockers) {
        DungeonLootBlueprint blueprint = new DungeonLootBlueprint();
        blueprint.originX = geometry.originX;
        blueprint.originY = geometry.originY;
        blueprint.originZ = geometry.originZ;
        blueprint.sizeX = geometry.sizeX;
        blueprint.sizeZ = geometry.sizeZ;
        blueprint.isDeep = deep;
        blueprint.attemptIndex = attemptIndex;
        blueprint.spawnerType = sim.spawnerType;
        blueprint.firstChestLootSeed = sim.firstLootSeed;
        blueprint.secondChestLootSeed = sim.secondLootSeed;
        blueprint.hitChestIndex = hitChestIndex;

        for (Cell cell : geometry.wallCells) {
            blueprint.wallBlocks.add(worldPos(geometry, cell, 0));
        }
        for (Cell cell : floorBreaks) {
            blueprint.floorBreaks.add(worldPos(geometry, cell, -1));
        }
        for (Cell cell : blockers) {
            blueprint.chestBlockers.add(worldPos(geometry, cell, 0));
        }
        for (Cell cell : sim.chests) {
            blueprint.chestPositions.add(worldPos(geometry, cell, 0));
        }
        return blueprint;
    }

    private int[] worldPos(AttemptGeometry geometry, Cell cell, int dy) {
        return new int[]{geometry.originX + cell.dx, geometry.originY + dy, geometry.originZ + cell.dz};
    }

    private AttemptGeometry buildGeometry(int originX, int originY, int originZ, int sizeX, int sizeZ) {
        AttemptGeometry geometry = new AttemptGeometry(originX, originY, originZ, sizeX, sizeZ);
        for (int dx = -sizeX - 1; dx <= sizeX + 1; dx++) {
            for (int dz = -sizeZ - 1; dz <= sizeZ + 1; dz++) {
                Cell cell = new Cell(dx, dz);
                geometry.floorCells.add(cell);
                boolean wall = dx == -sizeX - 1 || dx == sizeX + 1 || dz == -sizeZ - 1 || dz == sizeZ + 1;
                if (wall) {
                    geometry.wallCells.add(cell);
                } else {
                    geometry.blockerCells.add(cell);
                }
            }
        }
        return geometry;
    }

    private Set<Long> toKeySet(List<Cell> cells) {
        Set<Long> keys = new HashSet<>();
        for (Cell cell : cells) {
            keys.add(cell.key());
        }
        return keys;
    }

    private void fillBuffer(DungeonFinder.BlockReader blockReader) {
        for (int bx = 0; bx < DungeonFinder.BUFFER_XZ; bx++) {
            for (int by = 0; by < DungeonFinder.BUFFER_Y; by++) {
                for (int bz = 0; bz < DungeonFinder.BUFFER_XZ; bz++) {
                    int wx = chunkBlockX - 4 + bx;
                    int wy = DungeonFinder.WORLD_MIN_Y + by;
                    int wz = chunkBlockZ - 4 + bz;
                    buffer[bx * DungeonFinder.BUFFER_XZ * DungeonFinder.BUFFER_Y + by * DungeonFinder.BUFFER_XZ + bz] =
                            blockReader.getBlockState(wx, wy, wz);
                }
            }
        }
    }

    private static final class AttemptGeometry {
        final int originX;
        final int originY;
        final int originZ;
        final int sizeX;
        final int sizeZ;
        final List<Cell> floorCells = new ArrayList<>();
        final List<Cell> blockerCells = new ArrayList<>();
        final List<Cell> wallCells = new ArrayList<>();

        AttemptGeometry(int originX, int originY, int originZ, int sizeX, int sizeZ) {
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
        }
    }

    private static final class SimulationResult {
        long firstLootSeed;
        long secondLootSeed;
        SpawnerType spawnerType = SpawnerType.ZOMBIE;
        final List<Cell> chests = new ArrayList<>();
    }

    private static final class Cell {
        final int dx;
        final int dz;

        Cell(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        long key() {
            return key(dx, dz);
        }

        static long key(int dx, int dz) {
            return (((long) dx) << 32) ^ (dz & 0xffffffffL);
        }
    }
}
