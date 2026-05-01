package hackerrouter.dungeonforcer;

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

    public Spawner() {}

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
    }

    public Spawner copy() {
        return new Spawner(x, y, z, sizeX, sizeZ, type, exitsNeeded, floorBlocksNeeded, isDeep, attemptIndex);
    }

    @Override
    public String toString() {
        return String.format("Spawner{type=%s, pos=(%d,%d,%d), size=(%d,%d), exits=%d, floor=%d, deep=%s, attempt=%d}",
                type, x, y, z, sizeX, sizeZ, exitsNeeded, floorBlocksNeeded, isDeep, attemptIndex);
    }
}
