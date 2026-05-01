package hackerrouter.dungeonforcer;

public enum SpawnerType {
    SKELETON,
    ZOMBIE,
    SPIDER;

    public static SpawnerType fromMobIndex(int index) {
        switch (index) {
            case 0: return SKELETON;
            case 1: return ZOMBIE;
            case 2: return ZOMBIE;
            case 3: return SPIDER;
            default: throw new IllegalArgumentException("Invalid mob index: " + index);
        }
    }
}
