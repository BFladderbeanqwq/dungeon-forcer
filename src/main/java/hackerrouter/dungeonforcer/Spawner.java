package hackerrouter.dungeonforcer;

import java.util.ArrayList;
import java.util.List;

public class Spawner {
    public int x;
    public int y;
    public int z;
    public int sizeX;
    public int sizeZ;
    public SpawnerType type;
    public int exitsNeeded;
    public int floorBlocksNeeded;
    public boolean isDeep;
    public int attemptIndex;

    public List<int[]> blockModifications;

    public static final int ACTION_PLACE = 1;
    public static final int ACTION_BREAK = -1;

    public Spawner() {
        this.blockModifications = new ArrayList<>();
    }

    public Spawner(int x, int y, int z, int sizeX, int sizeZ, SpawnerType type,
                   int exitsNeeded, int floorBlocksNeeded, boolean isDeep, int attemptIndex) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.type = type;
        this.exitsNeeded = exitsNeeded;
        this.floorBlocksNeeded = floorBlocksNeeded;
        this.isDeep = isDeep;
        this.attemptIndex = attemptIndex;
        this.blockModifications = new ArrayList<>();
    }

    public Spawner copy() {
        Spawner s = new Spawner(x, y, z, sizeX, sizeZ, type, exitsNeeded, floorBlocksNeeded, isDeep, attemptIndex);
        for (int[] mod : blockModifications) {
            s.blockModifications.add(new int[]{mod[0], mod[1], mod[2], mod[3]});
        }
        return s;
    }

    @Override
    public String toString() {
        return String.format("Spawner{type=%s, pos=(%d,%d,%d), size=(%d,%d), exits=%d, floor=%d, deep=%s, attempt=%d, mods=%d}",
                type, x, y, z, sizeX, sizeZ, exitsNeeded, floorBlocksNeeded, isDeep, attemptIndex,
                blockModifications.size());
    }
}
