package hackerrouter.dungeonforcer;

import hackerrouter.dungeonforcer.rng.WorldgenRandom;
import hackerrouter.dungeonforcer.rng.XoroshiroRandomSource;
import hackerrouter.dungeonforcer.util.Chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Core search algorithm — matches original 1.17 mod logic, ported to 26.1.
 *
 * Key design: player can only modify blocks outside the target chunk.
 * Dungeon bounds may extend outside the chunk (origin is inside, but sizeX/sizeZ push walls beyond).
 *
 * Buffer fills the full 24x24 area (chunk ±4 blocks),
 * including neighboring chunk edges.
 *
 * Check logic clamps dungeon bounds to chunk (0-15):
 * - Floor/ceiling: only check within chunk, skip outside
 * - Exit count: only count wall positions inside chunk
 * - floorBlocksNeeded = total floor area - chunk overlap area
 *
 * Pass condition: allSolid && exitCount <= 5 && (exitCount >= 1 || floorBlocksNeeded != 0)
 */
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
    /** SOLID | SPAWNER — marks spawner position in buffer */
    public static final byte BLOCK_SPAWNER = SOLID | SPAWNER;
    /** SOLID | CHEST — marks chest position in buffer */
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

    /** block change records per attempt, for backtracking */
    private List<List<int[]>> changedAll;

    private List<SpawnerCombination> results;

    private SearchType searchType;
    private SpawnerType preferredType;
    private int activationRange;

    public DungeonFinder() {
        this.buffer = new byte[BUFFER_SIZE];
        this.results = new ArrayList<>();
        this.activationRange = 16;
    }

    /**
     * Get block from buffer.
     * Out-of-bounds Y returns AIR.
     * Out-of-bounds XZ returns UNKNOWN.
     */
    private byte getBlock(int bx, int by, int bz) {
        if (by < 0 || by >= BUFFER_Y) return AIR;
        if (bx < 0 || bx >= BUFFER_XZ || bz < 0 || bz >= BUFFER_XZ) return UNKNOWN;
        return buffer[bx * BUFFER_XZ * BUFFER_Y + by * BUFFER_XZ + bz];
    }

    private void setBlock(int bx, int by, int bz, byte state) {
        if (bx >= 0 && bx < BUFFER_XZ && by >= 0 && by < BUFFER_Y && bz >= 0 && bz < BUFFER_XZ) {
            buffer[bx * BUFFER_XZ * BUFFER_Y + by * BUFFER_XZ + bz] = state;
        }
    }

    /**
     * Set block and record change (for backtracking).
     * @param attemptIdx current attempt index
     */
    private void setAndRecord(int bx, int by, int bz, byte newState, int attemptIdx) {
        if (bx < 0 || bx >= BUFFER_XZ || by < 0 || by >= BUFFER_Y || bz < 0 || bz >= BUFFER_XZ) return;
        int idx = bx * BUFFER_XZ * BUFFER_Y + by * BUFFER_XZ + bz;
        byte old = buffer[idx];
        if (old != newState) {
            buffer[idx] = newState;
            changedAll.get(attemptIdx).add(new int[]{idx, old});
        }
    }

    /** Undo all block changes for the specified attempt */
    private void undoChanges(int attemptIdx) {
        List<int[]> changes = changedAll.get(attemptIdx);
        for (int i = changes.size() - 1; i >= 0; i--) {
            int[] c = changes.get(i);
            buffer[c[0]] = (byte) c[1];
        }
        changes.clear();
    }

    /** In buffer coords, chunk-local range is [4, 19] (world coords chunkBlockX+0 to chunkBlockX+15) */
    private int worldToBufferX(int worldX) { return worldX - chunkBlockX + 4; }
    private int worldToBufferY(int worldY) { return worldY - WORLD_MIN_Y; }
    private int worldToBufferZ(int worldZ) { return worldZ - chunkBlockZ + 4; }

    private int bufferToWorldX(int bx) { return bx + chunkBlockX - 4; }
    private int bufferToWorldY(int by) { return by + WORLD_MIN_Y; }
    private int bufferToWorldZ(int bz) { return bz + chunkBlockZ - 4; }

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

        // Fill the full 24x24 buffer area
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

        // Initialize change records for each attempt
        changedAll = new ArrayList<>();
        for (int i = 0; i < allAttempts.size(); i++) {
            changedAll.add(new ArrayList<>());
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
                    placementX, placementZ,
                    isDeep, i, random.getSeedLo(), random.getSeedHi());

            // No extra RNG consumed — assumes all attempts fail (matches original mod)
            // Successful RNG consumption handled in simulateDungeonPlace
        }

        return result;
    }

    private void simulateFeatureRNG(WorldgenRandom random,
                                    int originX, int originY, int originZ, int sizeX, int sizeZ) {
        // Empty impl — assumes all attempts fail, no extra RNG consumed
    }

    /**
     * Recursive search for optimal block layout.
     * For each dungeon attempt, checks block state within chunk (clamped to 0-15):
     * - Floor/ceiling must be all SOLID within chunk
     * - Wall exit count within chunk <= 5
     * - exitCount >= 1 or floorBlocksNeeded != 0 (cross-chunk dungeon)
     * If passed, simulates full MonsterRoomFeature.place(),
     * sets blocks in buffer (interior AIR, floor SOLID, chest, spawner),
     * then recurses to next attempt.
     */
    private void recursiveSearch(List<DungeonAttempt> attempts, int index,
                                 SpawnerCombination current) {
        if (index >= attempts.size()) {
            if (current.spawnerCount > 0) {
                // #4: minPoints filter
                int minPoints = 0;
                if (results.size() >= MAX_RESULTS) {
                    minPoints = results.get(results.size() - 1).points;
                }
                if (current.countPoints(minPoints, searchType, preferredType)) {
                    addResult(current.copy());
                }
            }
            return;
        }

        DungeonAttempt attempt = attempts.get(index);
        DungeonCheckResult check = checkDungeonConditions(attempt);

        if (index < 14) {
            int bxO = attempt.localX + 4;
            int byFloor = worldToBufferY(attempt.originY - 1);
            int byRoof = worldToBufferY(attempt.originY + 4);
            int bzO = attempt.localZ + 4;
            byte floorBlock = getBlock(bxO, byFloor, bzO);
            byte roofBlock = getBlock(bxO, byRoof, bzO);
            DungeonForcerMod.LOGGER.info("[DF] [{}] lx={} y={} lz={} sz=({},{}) pass={} exits={} floor={} floorBlk={} roofBlk={}",
                    index, attempt.localX, attempt.originY, attempt.localZ,
                    attempt.sizeX, attempt.sizeZ, check.pass, check.exitCount, check.floorBlocksNeeded,
                    floorBlock, roofBlock);
        }

        if (!check.pass) {
            recursiveSearch(attempts, index + 1, current);
            return;
        }

        // Branch 1: skip this dungeon
        recursiveSearch(attempts, index + 1, current);

        // Branch 2: place this dungeon
        Spawner spawner = simulateDungeonPlace(attempt, check, index);
        if (spawner != null) {
            // #5: last-attempt type filter
            boolean isLastAttempt = (index == attempts.size() - 1);
            if (!isLastAttempt || !searchType.hasType || spawner.type == preferredType) {
                current.spawners[current.spawnerCount] = spawner;
                current.spawnerCount++;
                recursiveSearch(attempts, index + 1, current);
                current.spawnerCount--;
            }
        }
        undoChanges(index);
    }

    /**
     * Check dungeon generation conditions, matching original mod logic.
     * Key: clamp dungeon bounds to chunk (localX 0-15, localZ 0-15).
     * Only checks blocks inside chunk; blocks outside are player-placed.
     * Pass condition:
     *   allSolid (floor/ceiling all solid within chunk)
     *   && exitCount <= 5
     *   && (exitCount >= 1 || floorBlocksNeeded != 0)
     * floorBlocksNeeded = total floor area - chunk overlap area
     *   = number of floor blocks player needs to place outside chunk
     */
    private DungeonCheckResult checkDungeonConditions(DungeonAttempt attempt) {
        // localX/Z = origin offset within chunk (0-15)
        int lx = attempt.localX;
        int lz = attempt.localZ;
        int oy = attempt.originY;
        int sizeX = attempt.sizeX;
        int sizeZ = attempt.sizeZ;

        int sizeX1 = sizeX + 1;
        int sizeZ1 = sizeZ + 1;

        // Full dungeon bounds (relative to origin)
        int fullMinX = -sizeX1;
        int fullMaxX = sizeX1;
        int fullMinZ = -sizeZ1;
        int fullMaxZ = sizeZ1;

        // Clamp to chunk bounds (0-15)
        int clampMinX = Math.max(0, lx + fullMinX);
        int clampMaxX = Math.min(15, lx + fullMaxX);
        int clampMinZ = Math.max(0, lz + fullMinZ);
        int clampMaxZ = Math.min(15, lz + fullMaxZ);

        // Total floor area vs chunk-internal area
        int totalFloor = (fullMaxX - fullMinX + 1) * (fullMaxZ - fullMinZ + 1);
        int chunkFloor = (clampMaxX - clampMinX + 1) * (clampMaxZ - clampMinZ + 1);
        int floorBlocksNeeded = totalFloor - chunkFloor;

        // Special case: y==1 means floor is bedrock, no extra blocks needed
        if (oy == 1) floorBlocksNeeded = 0;

        // Check if floor/ceiling are all solid within chunk
        boolean allSolid = true;
        int failWx = -1, failWz = -1, failDy = -1;
        byte failBlock = -1;
        outer:
        for (int wx = clampMinX; wx <= clampMaxX; wx++) {
            for (int wz = clampMinZ; wz <= clampMaxZ; wz++) {
                int bx = wx + 4; // buffer offset: chunk local 0 → buffer 4
                int bz = wz + 4;
                // Floor dy=-1
                byte floor = getBlock(bx, worldToBufferY(oy - 1), bz);
                if ((floor & SOLID) == 0) {
                    allSolid = false; failWx = wx; failWz = wz; failDy = -1; failBlock = floor;
                    break outer;
                }
                // Ceiling dy=4
                byte ceil = getBlock(bx, worldToBufferY(oy + 4), bz);
                if ((ceil & SOLID) == 0) {
                    allSolid = false; failWx = wx; failWz = wz; failDy = 4; failBlock = ceil;
                    break outer;
                }
            }
        }

        if (!allSolid) {
            DungeonForcerMod.LOGGER.info("[DF] allSolid FAIL at local({},{}) dy={} block={} oy={}",
                    failWx, failWz, failDy, failBlock, oy);
            return DungeonCheckResult.fail();
        }

        // Count wall exits within chunk
        int exitCount = 0;
        for (int wx = lx + fullMinX; wx <= lx + fullMaxX; wx++) {
            for (int wz = lz + fullMinZ; wz <= lz + fullMaxZ; wz++) {
                // Only count perimeter positions
                boolean isWall = (wx == lx + fullMinX || wx == lx + fullMaxX
                        || wz == lz + fullMinZ || wz == lz + fullMaxZ);
                if (!isWall) continue;
                // Only count positions inside chunk
                if (wx < 0 || wx > 15 || wz < 0 || wz > 15) continue;

                int bx = wx + 4;
                int bz = wz + 4;
                int by0 = worldToBufferY(oy);
                int by1 = worldToBufferY(oy + 1);
                byte b0 = getBlock(bx, by0, bz);
                byte b1 = getBlock(bx, by1, bz);
                if (b0 == AIR && b1 == AIR) {
                    exitCount++;
                    if (exitCount > 5) return DungeonCheckResult.fail();
                }
            }
        }

        boolean pass = exitCount <= 5 && (exitCount >= 1 || floorBlocksNeeded != 0);
        if (!pass) return DungeonCheckResult.fail();

        // exitsNeeded = 5 - exitCount (matches original mod line 1118-1122)
        int exitsNeeded = 5 - exitCount;
        return new DungeonCheckResult(true, exitsNeeded, floorBlocksNeeded);
    }

    /**
     * Simulate full MonsterRoomFeature.place() execution, setting blocks in buffer
     * and returning a Spawner object with render data.
     * Render data (blockModifications) records blocks player needs to place/break outside chunk:
     * - Floor/ceiling: outside-chunk portions need player-placed solid blocks
     * - Interior: inside-chunk blocks are cleared by world generator
     * Also simulates dungeon generation in buffer (interior AIR, floor SOLID, chest, spawner)
     * so subsequent attempts can detect already-generated dungeons.
     */
    private Spawner simulateDungeonPlace(DungeonAttempt attempt, DungeonCheckResult check, int attemptIdx) {
        int lx = attempt.localX;
        int lz = attempt.localZ;
        int oy = attempt.originY;
        int sizeX = attempt.sizeX;
        int sizeZ = attempt.sizeZ;
        int sizeX1 = sizeX + 1;
        int sizeZ1 = sizeZ + 1;

        WorldgenRandom rng = new WorldgenRandom(
                new XoroshiroRandomSource(attempt.rngSeedLo, attempt.rngSeedHi));

        Spawner spawner = new Spawner(
                attempt.originX, oy, attempt.originZ,
                sizeX, sizeZ, SpawnerType.ZOMBIE, // type determined later
                check.exitCount, check.floorBlocksNeeded,
                attempt.isDeep, attempt.attemptIndex);

        // === Simulate MonsterRoomFeature.place() block placement phase ===
        // Corresponds to vanilla source line 71-91: for dy=4 downto -1
        for (int dx = -sizeX1; dx <= sizeX1; dx++) {
            for (int dy = 4; dy >= -1; dy--) {
                for (int dz = -sizeZ1; dz <= sizeZ1; dz++) {
                    int wx = lx + dx; // chunk-local coordinate
                    int wy = oy + dy;
                    int wz = lz + dz;
                    int bx = wx + 4;
                    int by = worldToBufferY(wy);
                    int bz = wz + 4;

                    boolean isShell = (dx == -sizeX1 || dy == -1 || dz == -sizeZ1
                            || dx == sizeX1 || dy == 4 || dz == sizeZ1);

                    if (isShell) {
                        // Shell (wall/floor/ceiling)
                        // vanilla: if below not solid → set AIR; else if solid && not chest → set cobblestone
                        // Only simulate within chunk
                        if (wx >= 0 && wx <= 15 && wz >= 0 && wz <= 15) {
                            byte below = getBlock(bx, by - 1, bz);
                            if ((below & SOLID) == 0) {
                                setAndRecord(bx, by, bz, AIR, attemptIdx);
                            } else {
                                byte cur = getBlock(bx, by, bz);
                                if ((cur & SOLID) != 0 && (cur & CHEST) == 0) {
                                    if (dy == -1) {
                                        rng.nextInt(4); // mossy cobblestone RNG
                                    }
                                    setAndRecord(bx, by, bz, SOLID, attemptIdx);
                                }
                            }
                        } else {
                            // Outside-chunk floor/ceiling — player needs to place solid blocks
                            if (dy == -1 || dy == 4) {
                                int wwx = bufferToWorldX(bx);
                                int wwy = bufferToWorldY(by);
                                int wwz = bufferToWorldZ(bz);
                                spawner.blockModifications.add(new int[]{wwx, wwy, wwz, Spawner.ACTION_PLACE});
                            }
                            // Exit positions not rendered here — only printed in chat, player chooses location
                        }
                    } else {
                        // Interior blocks — clear to AIR (within chunk)
                        if (wx >= 0 && wx <= 15 && wz >= 0 && wz <= 15) {
                            byte cur = getBlock(bx, by, bz);
                            if ((cur & CHEST) == 0 && (cur & SPAWNER) == 0) {
                                setAndRecord(bx, by, bz, AIR, attemptIdx);
                            }
                        }
                    }
                }
            }
        }

        // === Simulate chest placement ===
        for (int cc = 0; cc < 2; cc++) {
            for (int i = 0; i < 3; i++) {
                int cx = lx + rng.nextInt(sizeX * 2 + 1) - sizeX;
                int cz = lz + rng.nextInt(sizeZ * 2 + 1) - sizeZ;
                int cbx = cx + 4;
                int cby = worldToBufferY(oy);
                int cbz = cz + 4;
                if (cx >= 0 && cx <= 15 && cz >= 0 && cz <= 15) {
                    if (getBlock(cbx, cby, cbz) == AIR) {
                        // Check exactly 1 horizontal neighbor is solid
                        int wallCount = 0;
                        if ((getBlock(cbx + 1, cby, cbz) & SOLID) != 0) wallCount++;
                        if ((getBlock(cbx - 1, cby, cbz) & SOLID) != 0) wallCount++;
                        if ((getBlock(cbx, cby, cbz + 1) & SOLID) != 0) wallCount++;
                        if ((getBlock(cbx, cby, cbz - 1) & SOLID) != 0) wallCount++;
                        if (wallCount == 1) {
                            setAndRecord(cbx, cby, cbz, BLOCK_CHEST, attemptIdx);
                            break;
                        }
                    }
                }
            }
        }

        // === Determine spawner type ===
        int mobIndex = rng.nextInt(4);
        spawner.type = SpawnerType.fromMobIndex(mobIndex);

        // === Place spawner ===
        int sbx = lx + 4;
        int sby = worldToBufferY(oy);
        int sbz = lz + 4;
        setAndRecord(sbx, sby, sbz, BLOCK_SPAWNER, attemptIdx);

        return spawner;
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
        int solidCount = 0, airCount = 0, unknownCount = 0;
        for (int bx = 0; bx < BUFFER_XZ; bx++) {
            for (int by = 0; by < BUFFER_Y; by++) {
                for (int bz = 0; bz < BUFFER_XZ; bz++) {
                    int wx = chunkBlockX - 4 + bx;
                    int wy = WORLD_MIN_Y + by;
                    int wz = chunkBlockZ - 4 + bz;
                    byte state = blockReader.getBlockState(wx, wy, wz);
                    buffer[bx * BUFFER_XZ * BUFFER_Y + by * BUFFER_XZ + bz] = state;
                    if (state == SOLID) solidCount++;
                    else if (state == AIR) airCount++;
                    else if (state == UNKNOWN) unknownCount++;
                }
            }
        }
        DungeonForcerMod.LOGGER.info("[DF] fillBuffer: solid={} air={} unknown={} chunkBlock=({},{})",
                solidCount, airCount, unknownCount, chunkBlockX, chunkBlockZ);
        // Sample a known underground block at chunk center, Y=50
        int sampleBx = 8 + 4; // local x=8
        int sampleBy = worldToBufferY(50);
        int sampleBz = 8 + 4; // local z=8
        byte sampleBlock = getBlock(sampleBx, sampleBy, sampleBz);
        DungeonForcerMod.LOGGER.info("[DF] sample block at local(8,50,8): {} (bx={},by={},bz={})",
                sampleBlock, sampleBx, sampleBy, sampleBz);
    }

    public static class DungeonAttempt {
        public final int originX, originY, originZ;
        /** Origin's chunk-local coordinate (0-15) */
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

    private static class DungeonCheckResult {
        final boolean pass;
        final int exitCount;
        final int floorBlocksNeeded;

        DungeonCheckResult(boolean pass, int exitCount, int floorBlocksNeeded) {
            this.pass = pass;
            this.exitCount = exitCount;
            this.floorBlocksNeeded = floorBlocksNeeded;
        }

        static DungeonCheckResult fail() {
            return new DungeonCheckResult(false, 0, 0);
        }
    }

    @FunctionalInterface
    public interface BlockReader {
        byte getBlockState(int worldX, int worldY, int worldZ);
    }
}