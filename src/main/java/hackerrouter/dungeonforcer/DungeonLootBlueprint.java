package hackerrouter.dungeonforcer;

import java.util.ArrayList;
import java.util.List;

public class DungeonLootBlueprint {
    public int originX;
    public int originY;
    public int originZ;
    public int sizeX;
    public int sizeZ;
    public boolean isDeep;
    public int attemptIndex;
    public SpawnerType spawnerType;
    public long firstChestLootSeed;
    public long secondChestLootSeed;
    public int hitChestIndex;

    public final List<int[]> wallBlocks = new ArrayList<>();
    public final List<int[]> requiredPlaceBlocks = new ArrayList<>();
    public final List<int[]> floorBreaks = new ArrayList<>();
    public final List<int[]> chestBlockers = new ArrayList<>();
    public final List<int[]> chestPositions = new ArrayList<>();

    public Spawner toSpawnerHighlight() {
        Spawner spawner = new Spawner(originX, originY, originZ, sizeX, sizeZ, spawnerType, 0, 0, isDeep, attemptIndex);
        for (int[] pos : requiredPlaceBlocks) {
            spawner.blockModifications.add(new int[]{pos[0], pos[1], pos[2], Spawner.ACTION_PLACE});
        }
        for (int[] pos : chestBlockers) {
            spawner.blockModifications.add(new int[]{pos[0], pos[1], pos[2], Spawner.ACTION_CHEST_CONTROL});
        }
        for (int[] pos : floorBreaks) {
            spawner.blockModifications.add(new int[]{pos[0], pos[1], pos[2], Spawner.ACTION_BREAK});
        }
        return spawner;
    }
}
