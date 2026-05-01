package hackerrouter.dungeonforcer.render;

import hackerrouter.dungeonforcer.Spawner;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RenderQueue {
    private static final List<Spawner> spawnerHighlights = new CopyOnWriteArrayList<>();
    private static final List<int[]> chunkCrosses = new CopyOnWriteArrayList<>();

    public static void addSpawnerHighlight(Spawner spawner) {
        spawnerHighlights.add(spawner.copy());
    }

    public static void addChunkCross(int chunkX, int chunkZ) {
        chunkCrosses.add(new int[]{chunkX, chunkZ});
    }

    public static List<Spawner> getSpawnerHighlights() { return spawnerHighlights; }
    public static List<int[]> getChunkCrosses() { return chunkCrosses; }

    public static boolean isEmpty() {
        return spawnerHighlights.isEmpty() && chunkCrosses.isEmpty();
    }

    public static void clear() {
        spawnerHighlights.clear();
        chunkCrosses.clear();
    }

    public static void clearChunkCrosses() {
        chunkCrosses.clear();
    }
}
