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
    private List<AttemptGeometry> priorAttempts = List.of();

    public List<DungeonLootBlueprint> runForChunk(
            int chunkX, int chunkZ, long worldSeed,
            int featureIndexNormal, int featureIndexDeep,
            String targetItem, int maxResults,
            int maxFloorBreaks, int maxChestBlockers,
            DungeonFinder.BlockReader blockReader, BiomeReader biomeReader) {

        this.chunkBlockX = chunkX * 16;
        this.chunkBlockZ = chunkZ * 16;
        fillBuffer(blockReader);

        List<DungeonLootBlueprint> results = new ArrayList<>();
        WorldgenRandom random = new WorldgenRandom(0L);
        long decorationSeed = random.setDecorationSeed(worldSeed, chunkBlockX, chunkBlockZ);

        searchFeature(results, decorationSeed, featureIndexNormal, false,
                DungeonFinder.NORMAL_ATTEMPTS, DungeonFinder.NORMAL_Y_MIN, DungeonFinder.NORMAL_Y_MAX,
                targetItem, maxResults, maxFloorBreaks, maxChestBlockers, List.of(), biomeReader);

        if (featureIndexDeep >= 0 && results.size() < maxResults) {
            List<AttemptGeometry> normalAttempts = collectFailurePathAttempts(
                    decorationSeed, featureIndexNormal,
                    DungeonFinder.NORMAL_ATTEMPTS, DungeonFinder.NORMAL_Y_MIN, DungeonFinder.NORMAL_Y_MAX,
                    false, biomeReader);
            searchFeature(results, decorationSeed, featureIndexDeep, true,
                    DungeonFinder.DEEP_ATTEMPTS, DungeonFinder.DEEP_Y_MIN, DungeonFinder.DEEP_Y_MAX,
                    targetItem, maxResults, maxFloorBreaks, maxChestBlockers, normalAttempts, biomeReader);
        }

        return results;
    }

    private void searchFeature(List<DungeonLootBlueprint> results, long decorationSeed, int featureIndex,
                               boolean deep, int attempts, int yMin, int yMax,
                               String targetItem, int maxResults,
                               int maxFloorBreaks, int maxChestBlockers,
                               List<AttemptGeometry> earlierFeatureAttempts, BiomeReader biomeReader) {
        WorldgenRandom rng = new WorldgenRandom(0L);
        rng.setFeatureSeed(decorationSeed, featureIndex, DungeonFinder.STEP_ORDINAL);
        int yRange = yMax - yMin + 1;
        List<AttemptGeometry> previous = new ArrayList<>(earlierFeatureAttempts);

        for (int attempt = 0; attempt < attempts && results.size() < maxResults; attempt++) {
            int px = rng.nextInt(16);
            int pz = rng.nextInt(16);
            int py = rng.nextInt(yRange) + yMin;
            long attemptLo = rng.getSeedLo();
            long attemptHi = rng.getSeedHi();

            int originX = chunkBlockX + px;
            int originZ = chunkBlockZ + pz;
            if (!biomeReader.canPlace(originX, py, originZ, deep)) {
                continue;
            }

            WorldgenRandom attemptRng = new WorldgenRandom(new XoroshiroRandomSource(attemptLo, attemptHi));
            int sizeX = attemptRng.nextInt(2) + 2;
            int sizeZ = attemptRng.nextInt(2) + 2;
            AttemptGeometry geometry = buildGeometry(originX, py, originZ, sizeX, sizeZ);
            if (!crossesSpawnerChunkBoundary(geometry) || !hasSolidInChunkFloorAndCeiling(geometry)) {
                rng = attemptRng;
                previous.add(geometry);
                continue;
            }
            priorAttempts = List.copyOf(previous);
            enumerateFloorMasks(results, attemptLo, attemptHi, geometry, deep, attempt,
                    targetItem, maxResults, maxFloorBreaks, maxChestBlockers);

            rng = attemptRng;
            previous.add(geometry);
        }
    }

    private List<AttemptGeometry> collectFailurePathAttempts(long decorationSeed, int featureIndex,
                                                              int attempts, int yMin, int yMax,
                                                              boolean deep, BiomeReader biomeReader) {
        WorldgenRandom rng = new WorldgenRandom(0L);
        rng.setFeatureSeed(decorationSeed, featureIndex, DungeonFinder.STEP_ORDINAL);
        int yRange = yMax - yMin + 1;
        List<AttemptGeometry> geometries = new ArrayList<>();
        for (int attempt = 0; attempt < attempts; attempt++) {
            int originX = chunkBlockX + rng.nextInt(16);
            int originZ = chunkBlockZ + rng.nextInt(16);
            int originY = rng.nextInt(yRange) + yMin;
            if (!biomeReader.canPlace(originX, originY, originZ, deep)) {
                continue;
            }
            int sizeX = rng.nextInt(2) + 2;
            int sizeZ = rng.nextInt(2) + 2;
            geometries.add(buildGeometry(originX, originY, originZ, sizeX, sizeZ));
        }
        return geometries;
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
        int openingLimit = Math.min(Math.min(maxChestBlockers, 5), geometry.externalWallCells.size());
        if (openingLimit < 1) return;

        List<Cell> relevantOpenings = findRelevantOpenings(attemptLo, attemptHi, geometry, floorBreaks);
        Cell filler = findFillerOpening(geometry, relevantOpenings);
        if (relevantOpenings.isEmpty()) {
            if (filler != null) {
                testOpeningSet(results, attemptLo, attemptHi, geometry, deep, attemptIndex, targetItem,
                        maxResults, floorBreaks, List.of(filler));
            }
            return;
        }

        for (int openings = 0; openings <= Math.min(openingLimit, relevantOpenings.size())
                && results.size() < maxResults; openings++) {
            enumerateRelevantOpenings(results, attemptLo, attemptHi, geometry, deep, attemptIndex, targetItem,
                    maxResults, openingLimit, relevantOpenings, filler, 0, openings, floorBreaks, new ArrayList<>());
        }
    }

    private void enumerateRelevantOpenings(List<DungeonLootBlueprint> results,
                                           long attemptLo, long attemptHi, AttemptGeometry geometry,
                                           boolean deep, int attemptIndex, String targetItem, int maxResults,
                                           int openingLimit, List<Cell> relevantOpenings, Cell filler,
                                           int start, int remaining, List<Cell> floorBreaks, List<Cell> openings) {
        if (results.size() >= maxResults) return;
        if (remaining == 0) {
            List<Cell> actualOpenings = openings;
            if (actualOpenings.isEmpty()) {
                if (filler == null || openingLimit < 1) return;
                actualOpenings = List.of(filler);
            }
            testOpeningSet(results, attemptLo, attemptHi, geometry, deep, attemptIndex, targetItem,
                    maxResults, floorBreaks, actualOpenings);
            return;
        }

        for (int i = start; i <= relevantOpenings.size() - remaining; i++) {
            openings.add(relevantOpenings.get(i));
            enumerateRelevantOpenings(results, attemptLo, attemptHi, geometry, deep, attemptIndex, targetItem,
                    maxResults, openingLimit, relevantOpenings, filler, i + 1, remaining - 1, floorBreaks, openings);
            openings.remove(openings.size() - 1);
            if (results.size() >= maxResults) return;
        }
    }

    private void testOpeningSet(List<DungeonLootBlueprint> results,
                                long attemptLo, long attemptHi, AttemptGeometry geometry,
                                boolean deep, int attemptIndex, String targetItem, int maxResults,
                                List<Cell> floorBreaks, List<Cell> openings) {
        if (results.size() >= maxResults) return;
        if (!allPriorAttemptsFail(geometry, floorBreaks, openings)) return;
        SimulationResult sim = simulateLayout(attemptLo, attemptHi, geometry, floorBreaks, openings);
        if (sim.firstLootSeed != 0L
                && SimpleDungeonLootSimulator.contains(sim.firstLootSeed, targetItem)
                && isChestInSpawnerChunk(geometry, sim, 1)) {
            results.add(toBlueprint(geometry, deep, attemptIndex, 1, sim, floorBreaks, openings));
        } else if (sim.secondLootSeed != 0L
                && SimpleDungeonLootSimulator.contains(sim.secondLootSeed, targetItem)
                && isChestInSpawnerChunk(geometry, sim, 2)) {
            results.add(toBlueprint(geometry, deep, attemptIndex, 2, sim, floorBreaks, openings));
        }
    }

    private boolean isChestInSpawnerChunk(AttemptGeometry geometry, SimulationResult sim, int chestIndex) {
        if (sim.chests.size() < chestIndex) return false;
        Cell chest = sim.chests.get(chestIndex - 1);
        return !isOutsideSpawnerChunk(geometry.originX + chest.dx, geometry.originZ + chest.dz);
    }

    private List<Cell> findRelevantOpenings(long attemptLo, long attemptHi, AttemptGeometry geometry, List<Cell> floorBreaks) {
        Set<Long> keys = new HashSet<>();
        List<Cell> relevant = new ArrayList<>();
        collectRelevantOpenings(keys, relevant, attemptLo, attemptHi, geometry, floorBreaks, -1);
        for (int firstSuccessAttempt = 0; firstSuccessAttempt < 3; firstSuccessAttempt++) {
            collectRelevantOpenings(keys, relevant, attemptLo, attemptHi, geometry, floorBreaks, firstSuccessAttempt);
        }
        return relevant;
    }

    private void collectRelevantOpenings(Set<Long> keys, List<Cell> relevant,
                                         long attemptLo, long attemptHi, AttemptGeometry geometry,
                                         List<Cell> floorBreaks, int firstSuccessAttempt) {
        WorldgenRandom rng = new WorldgenRandom(new XoroshiroRandomSource(attemptLo, attemptHi));
        rng.nextInt(2);
        rng.nextInt(2);
        buildPostShellState(geometry, toKeySet(floorBreaks), Set.of(), rng);

        int attemptsToRead = firstSuccessAttempt < 0 ? 6 : firstSuccessAttempt + 1;
        for (int i = 0; i < attemptsToRead; i++) {
            addAdjacentWallOpenings(keys, relevant, geometry,
                    rng.nextInt(geometry.sizeX * 2 + 1) - geometry.sizeX,
                    rng.nextInt(geometry.sizeZ * 2 + 1) - geometry.sizeZ);
        }
        if (firstSuccessAttempt >= 0) {
            rng.nextLong();
            for (int i = 0; i < 3; i++) {
                addAdjacentWallOpenings(keys, relevant, geometry,
                        rng.nextInt(geometry.sizeX * 2 + 1) - geometry.sizeX,
                        rng.nextInt(geometry.sizeZ * 2 + 1) - geometry.sizeZ);
            }
        }
    }

    private void addAdjacentWallOpenings(Set<Long> keys, List<Cell> relevant, AttemptGeometry geometry, int dx, int dz) {
        addWallOpening(keys, relevant, geometry, dx + 1, dz);
        addWallOpening(keys, relevant, geometry, dx - 1, dz);
        addWallOpening(keys, relevant, geometry, dx, dz + 1);
        addWallOpening(keys, relevant, geometry, dx, dz - 1);
    }

    private void addWallOpening(Set<Long> keys, List<Cell> relevant, AttemptGeometry geometry, int dx, int dz) {
        if (!isWall(geometry, dx, dz) || !isOutsideSpawnerChunk(geometry.originX + dx, geometry.originZ + dz)) return;
        long key = Cell.key(dx, dz);
        if (keys.add(key)) {
            relevant.add(new Cell(dx, dz));
        }
    }

    private Cell findFillerOpening(AttemptGeometry geometry, List<Cell> relevantOpenings) {
        Set<Long> relevant = toKeySet(relevantOpenings);
        for (Cell cell : geometry.externalWallCells) {
            if (!relevant.contains(cell.key())) {
                return cell;
            }
        }
        return geometry.externalWallCells.isEmpty() ? null : geometry.externalWallCells.get(0);
    }

    private SimulationResult simulateLayout(long attemptLo, long attemptHi, AttemptGeometry geometry,
                                            List<Cell> floorBreaks, List<Cell> blockers) {
        WorldgenRandom rng = new WorldgenRandom(new XoroshiroRandomSource(attemptLo, attemptHi));
        int sizeX = rng.nextInt(2) + 2;
        int sizeZ = rng.nextInt(2) + 2;
        if (sizeX != geometry.sizeX || sizeZ != geometry.sizeZ) {
            return new SimulationResult();
        }

        Set<Long> supportAir = toKeySet(floorBreaks);
        Set<Long> wallOpenings = toKeySet(blockers);
        if (!hasValidHoleCount(geometry, wallOpenings)) {
            return new SimulationResult();
        }
        Set<Long> generatedChests = new HashSet<>();
        boolean[][][] solid = buildPostShellState(geometry, supportAir, wallOpenings, rng);

        SimulationResult result = new SimulationResult();
        int placed = 0;
        for (int cc = 0; cc < 2; cc++) {
            for (int i = 0; i < 3; i++) {
                int cx = rng.nextInt(geometry.sizeX * 2 + 1) - geometry.sizeX;
                int cz = rng.nextInt(geometry.sizeZ * 2 + 1) - geometry.sizeZ;
                long key = Cell.key(cx, cz);
                if (generatedChests.contains(key) || isSolid(solid, geometry, cx, 0, cz)) {
                    continue;
                }
                if (wallCount(cx, cz, geometry, solid) == 1) {
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

    private boolean allPriorAttemptsFail(AttemptGeometry targetGeometry, List<Cell> floorBreaks, List<Cell> wallOpenings) {
        Set<Long> supportAir = toKeySet(floorBreaks);
        Set<Long> openings = toKeySet(wallOpenings);
        for (AttemptGeometry prior : priorAttempts) {
            if (passesPreparedPrecheck(prior, targetGeometry, supportAir, openings)) {
                return false;
            }
        }
        return true;
    }

    private boolean passesPreparedPrecheck(AttemptGeometry attempt, AttemptGeometry target,
                                           Set<Long> supportAir, Set<Long> wallOpenings) {
        int holes = 0;
        for (int dx = -attempt.sizeX - 1; dx <= attempt.sizeX + 1; dx++) {
            for (int dz = -attempt.sizeZ - 1; dz <= attempt.sizeZ + 1; dz++) {
                int wx = attempt.originX + dx;
                int wz = attempt.originZ + dz;
                if (!isPreparedSolid(wx, attempt.originY - 1, wz, target, supportAir, wallOpenings)
                        || !isPreparedSolid(wx, attempt.originY + 4, wz, target, supportAir, wallOpenings)) {
                    return false;
                }
                if (isWall(attempt, dx, dz)
                        && !isPreparedSolid(wx, attempt.originY, wz, target, supportAir, wallOpenings)
                        && !isPreparedSolid(wx, attempt.originY + 1, wz, target, supportAir, wallOpenings)) {
                    holes++;
                    if (holes > 5) return false;
                }
            }
        }
        return holes >= 1;
    }

    private boolean isPreparedSolid(int wx, int wy, int wz, AttemptGeometry target,
                                    Set<Long> supportAir, Set<Long> wallOpenings) {
        int dx = wx - target.originX;
        int dz = wz - target.originZ;
        if (dx < -target.sizeX - 1 || dx > target.sizeX + 1
                || dz < -target.sizeZ - 1 || dz > target.sizeZ + 1
                || !isOutsideSpawnerChunk(wx, wz)) {
            return isSolid(wx, wy, wz);
        }
        long key = Cell.key(dx, dz);
        int dy = wy - target.originY;
        if (dy == -2) return !supportAir.contains(key);
        if (dy == -1 || dy == 4) return true;
        if (isWall(target, dx, dz) && dy >= 0 && dy <= 3) {
            return !wallOpenings.contains(key) || dy >= 2;
        }
        return isSolid(wx, wy, wz);
    }

    private boolean hasValidHoleCount(AttemptGeometry geometry, Set<Long> wallOpenings) {
        int holes = 0;
        for (Cell wall : geometry.wallCells) {
            int wx = geometry.originX + wall.dx;
            int wz = geometry.originZ + wall.dz;
            boolean opening;
            if (isOutsideSpawnerChunk(wx, wz)) {
                opening = wallOpenings.contains(wall.key());
            } else {
                opening = !isSolid(wx, geometry.originY, wz)
                        && !isSolid(wx, geometry.originY + 1, wz);
            }
            if (opening && ++holes > 5) {
                return false;
            }
        }
        return holes >= 1;
    }

    private boolean[][][] buildPostShellState(AttemptGeometry geometry, Set<Long> supportAir, Set<Long> wallOpenings,
                                              WorldgenRandom rng) {
        int xLen = geometry.sizeX * 2 + 3;
        int zLen = geometry.sizeZ * 2 + 3;
        boolean[][][] solid = new boolean[xLen][7][zLen];

        for (int dx = -geometry.sizeX - 1; dx <= geometry.sizeX + 1; dx++) {
            for (int dz = -geometry.sizeZ - 1; dz <= geometry.sizeZ + 1; dz++) {
                int wx = geometry.originX + dx;
                int wz = geometry.originZ + dz;
                boolean outside = isOutsideSpawnerChunk(wx, wz);
                long key = Cell.key(dx, dz);

                setSolid(solid, geometry, dx, -2, dz, outside
                        ? !supportAir.contains(key)
                        : isSolid(wx, geometry.originY - 2, wz));
                setSolid(solid, geometry, dx, -1, dz, outside
                        || isSolid(wx, geometry.originY - 1, wz));
                setSolid(solid, geometry, dx, 4, dz, outside
                        || isSolid(wx, geometry.originY + 4, wz));

                boolean wall = isWall(geometry, dx, dz);
                if (wall) {
                    for (int dy = 0; dy <= 3; dy++) {
                        boolean controlledWall = !wallOpenings.contains(key) || dy >= 2;
                        setSolid(solid, geometry, dx, dy, dz, outside
                                ? controlledWall
                                : isSolid(wx, geometry.originY + dy, wz));
                    }
                }
            }
        }

        for (int dx = -geometry.sizeX - 1; dx <= geometry.sizeX + 1; dx++) {
            for (int dy = 3; dy >= -1; dy--) {
                for (int dz = -geometry.sizeZ - 1; dz <= geometry.sizeZ + 1; dz++) {
                    boolean shell = isWall(geometry, dx, dz) || dy == -1 || dy == 4;
                    if (shell) {
                        if (!isSolid(solid, geometry, dx, dy - 1, dz)) {
                            setSolid(solid, geometry, dx, dy, dz, false);
                        } else if (isSolid(solid, geometry, dx, dy, dz)) {
                            if (dy == -1) {
                                rng.nextInt(4);
                            }
                            setSolid(solid, geometry, dx, dy, dz, true);
                        }
                    } else {
                        setSolid(solid, geometry, dx, dy, dz, false);
                    }
                }
            }
        }
        return solid;
    }

    private int wallCount(int dx, int dz, AttemptGeometry geometry, boolean[][][] solid) {
        int count = 0;
        if (isSolid(solid, geometry, dx + 1, 0, dz)) count++;
        if (isSolid(solid, geometry, dx - 1, 0, dz)) count++;
        if (isSolid(solid, geometry, dx, 0, dz + 1)) count++;
        if (isSolid(solid, geometry, dx, 0, dz - 1)) count++;
        return count;
    }

    private boolean isWall(AttemptGeometry geometry, int dx, int dz) {
        return dx == -geometry.sizeX - 1 || dx == geometry.sizeX + 1
                || dz == -geometry.sizeZ - 1 || dz == geometry.sizeZ + 1;
    }

    private boolean isSolid(boolean[][][] solid, AttemptGeometry geometry, int dx, int dy, int dz) {
        if (dy < -2 || dy > 4 || dx < -geometry.sizeX - 1 || dx > geometry.sizeX + 1
                || dz < -geometry.sizeZ - 1 || dz > geometry.sizeZ + 1) {
            return false;
        }
        return solid[dx + geometry.sizeX + 1][dy + 2][dz + geometry.sizeZ + 1];
    }

    private void setSolid(boolean[][][] solid, AttemptGeometry geometry, int dx, int dy, int dz, boolean value) {
        if (dy < -2 || dy > 4) return;
        solid[dx + geometry.sizeX + 1][dy + 2][dz + geometry.sizeZ + 1] = value;
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
            blueprint.floorBreaks.add(worldPos(geometry, cell, -2));
        }
        for (Cell cell : blockers) {
            blueprint.floorBreaks.add(worldPos(geometry, cell, 0));
            blueprint.floorBreaks.add(worldPos(geometry, cell, 1));
        }
        addRequiredPlaceBlocks(blueprint, geometry, floorBreaks, blockers);
        for (Cell cell : sim.chests) {
            blueprint.chestPositions.add(worldPos(geometry, cell, 0));
        }
        return blueprint;
    }

    private void addRequiredPlaceBlocks(DungeonLootBlueprint blueprint, AttemptGeometry geometry,
                                        List<Cell> floorBreaks, List<Cell> wallOpenings) {
        Set<Long> floorBreakSet = toKeySet(floorBreaks);
        Set<Long> openingSet = toKeySet(wallOpenings);
        for (int dx = -geometry.sizeX - 1; dx <= geometry.sizeX + 1; dx++) {
            for (int dz = -geometry.sizeZ - 1; dz <= geometry.sizeZ + 1; dz++) {
                int wx = geometry.originX + dx;
                int wz = geometry.originZ + dz;
                if (!isOutsideSpawnerChunk(wx, wz)) {
                    continue;
                }
                blueprint.requiredPlaceBlocks.add(worldPos(geometry, new Cell(dx, dz), -1));
                blueprint.requiredPlaceBlocks.add(worldPos(geometry, new Cell(dx, dz), 4));
                if (!floorBreakSet.contains(Cell.key(dx, dz))) {
                    blueprint.requiredPlaceBlocks.add(worldPos(geometry, new Cell(dx, dz), -2));
                }

                boolean wall = dx == -geometry.sizeX - 1 || dx == geometry.sizeX + 1
                        || dz == -geometry.sizeZ - 1 || dz == geometry.sizeZ + 1;
                if (wall) {
                    for (int dy = 0; dy <= 3; dy++) {
                        if (openingSet.contains(Cell.key(dx, dz)) && dy <= 1) {
                            continue;
                        }
                        int wy = geometry.originY + dy;
                        blueprint.chestBlockers.add(new int[]{wx, wy, wz});
                    }
                }
            }
        }
    }

    private int[] worldPos(AttemptGeometry geometry, Cell cell, int dy) {
        return new int[]{geometry.originX + cell.dx, geometry.originY + dy, geometry.originZ + cell.dz};
    }

    private AttemptGeometry buildGeometry(int originX, int originY, int originZ, int sizeX, int sizeZ) {
        AttemptGeometry geometry = new AttemptGeometry(originX, originY, originZ, sizeX, sizeZ);
        for (int dx = -sizeX - 1; dx <= sizeX + 1; dx++) {
            for (int dz = -sizeZ - 1; dz <= sizeZ + 1; dz++) {
                Cell cell = new Cell(dx, dz);
                if (isOutsideSpawnerChunk(originX + dx, originZ + dz)) {
                    geometry.floorCells.add(cell);
                }
                boolean wall = dx == -sizeX - 1 || dx == sizeX + 1 || dz == -sizeZ - 1 || dz == sizeZ + 1;
                if (wall) {
                    geometry.wallCells.add(cell);
                    if (isOutsideSpawnerChunk(originX + dx, originZ + dz)) {
                        geometry.externalWallCells.add(cell);
                    }
                } else {
                    geometry.blockerCells.add(cell);
                }
            }
        }
        return geometry;
    }

    private boolean crossesSpawnerChunkBoundary(AttemptGeometry geometry) {
        return geometry.originX - geometry.sizeX - 1 < chunkBlockX
                || geometry.originX + geometry.sizeX + 1 > chunkBlockX + 15
                || geometry.originZ - geometry.sizeZ - 1 < chunkBlockZ
                || geometry.originZ + geometry.sizeZ + 1 > chunkBlockZ + 15;
    }

    private boolean hasSolidInChunkFloorAndCeiling(AttemptGeometry geometry) {
        for (int dx = -geometry.sizeX - 1; dx <= geometry.sizeX + 1; dx++) {
            for (int dz = -geometry.sizeZ - 1; dz <= geometry.sizeZ + 1; dz++) {
                int wx = geometry.originX + dx;
                int wz = geometry.originZ + dz;
                if (isOutsideSpawnerChunk(wx, wz)) continue;
                if (!isSolid(wx, geometry.originY - 1, wz) || !isSolid(wx, geometry.originY + 4, wz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isOutsideSpawnerChunk(int worldX, int worldZ) {
        return worldX < chunkBlockX || worldX > chunkBlockX + 15
                || worldZ < chunkBlockZ || worldZ > chunkBlockZ + 15;
    }

    private Set<Long> toKeySet(List<Cell> cells) {
        Set<Long> keys = new HashSet<>();
        for (Cell cell : cells) {
            keys.add(cell.key());
        }
        return keys;
    }

    private boolean isSolid(int worldX, int worldY, int worldZ) {
        return (getBlock(worldX, worldY, worldZ) & DungeonFinder.SOLID) != 0;
    }

    private byte getBlock(int worldX, int worldY, int worldZ) {
        int bx = worldX - (chunkBlockX - 4);
        int by = worldY - DungeonFinder.WORLD_MIN_Y;
        int bz = worldZ - (chunkBlockZ - 4);
        if (bx < 0 || bx >= DungeonFinder.BUFFER_XZ || by < 0 || by >= DungeonFinder.BUFFER_Y
                || bz < 0 || bz >= DungeonFinder.BUFFER_XZ) {
            return DungeonFinder.UNKNOWN;
        }
        return buffer[bx * DungeonFinder.BUFFER_XZ * DungeonFinder.BUFFER_Y + by * DungeonFinder.BUFFER_XZ + bz];
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
        final List<Cell> externalWallCells = new ArrayList<>();

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

    @FunctionalInterface
    public interface BiomeReader {
        boolean canPlace(int worldX, int worldY, int worldZ, boolean deep);
    }

}
