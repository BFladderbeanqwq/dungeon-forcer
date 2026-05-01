package hackerrouter.dungeonforcer.render;

import hackerrouter.dungeonforcer.Spawner;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Renders spawner highlights and good chunk markers using 26.1 Gizmos API.
 * Gizmos automatically handles camera offset, blend state, and depth testing.
 */
public class Renderer {

    // Block modification colors
    private static final int PLACE_STROKE = ARGB.color(230, 50, 150, 255);   // Blue-purple — needs placement
    private static final int PLACE_FILL   = ARGB.color(100, 50, 150, 255);
    private static final int BREAK_STROKE = ARGB.color(230, 255, 165, 0);    // Orange — needs breaking
    private static final int BREAK_FILL   = ARGB.color(100, 255, 165, 0);

    public static void init() {
        // BEFORE_GIZMOS fires while GizmoCollector is active, safe to call Gizmos API
        LevelRenderEvents.BEFORE_GIZMOS.register(Renderer::onWorldRender);
    }

    private static void onWorldRender(LevelRenderContext context) {
        if (RenderQueue.isEmpty()) return;

        for (Spawner spawner : RenderQueue.getSpawnerHighlights()) {
            renderSpawnerBox(spawner);
            renderBlockModifications(spawner);
        }

        for (int[] chunkCross : RenderQueue.getChunkCrosses()) {
            renderChunkCross(chunkCross[0], chunkCross[1]);
        }
    }

    // Gizmos added in BEFORE_GIZMOS are drained immediately by finalizeGizmoCollection().
    // expireTimeMillis defaults to 0, which is always < currentMillis, so they get removed
    // before rendering. Use persistForMillis(1) so they survive exactly one drain cycle.
    private static final int PERSIST_MS = 1;

    private static GizmoProperties g(GizmoProperties p) {
        return p.persistForMillis(PERSIST_MS).setAlwaysOnTop();
    }

    private static void renderSpawnerBox(Spawner spawner) {
        int strokeColor;
        int fillColor;
        switch (spawner.type) {
            case SKELETON:
                strokeColor = ARGB.color(204, 77, 204, 255);  // Cyan
                fillColor   = ARGB.color(64, 77, 204, 255);
                break;
            case ZOMBIE:
                strokeColor = ARGB.color(204, 77, 255, 77);   // Green
                fillColor   = ARGB.color(64, 77, 255, 77);
                break;
            case SPIDER:
                strokeColor = ARGB.color(204, 255, 77, 77);   // Red
                fillColor   = ARGB.color(64, 255, 77, 77);
                break;
            default:
                strokeColor = ARGB.color(204, 255, 255, 255);
                fillColor   = ARGB.color(64, 255, 255, 255);
                break;
        }

        // Spawner block highlight (fill + stroke)
        AABB spawnerBlock = new AABB(
                spawner.x, spawner.y, spawner.z,
                spawner.x + 1, spawner.y + 1, spawner.z + 1);
        g(Gizmos.cuboid(spawnerBlock, GizmoStyle.strokeAndFill(strokeColor, 2.5f, fillColor)));

        int minX = spawner.x - spawner.sizeX - 1;
        int maxX = spawner.x + spawner.sizeX + 2;
        int minZ = spawner.z - spawner.sizeZ - 1;
        int maxZ = spawner.z + spawner.sizeZ + 2;
        int minY = spawner.y - 1;
        int maxY = spawner.y + 5;

        AABB dungeonBounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        g(Gizmos.cuboid(dungeonBounds, GizmoStyle.stroke(
                ARGB.color(77, ARGB.red(strokeColor), ARGB.green(strokeColor), ARGB.blue(strokeColor)), 1.5f)));
    }

    /**
     * Render block positions that player needs to modify.
     * Blue-purple = needs block placement, Orange = needs block breaking.
     * Uses setAlwaysOnTop() to ensure visibility through blocks.
     */
    private static void renderBlockModifications(Spawner spawner) {
        if (spawner.blockModifications == null || spawner.blockModifications.isEmpty()) {
            return;
        }

        for (int[] mod : spawner.blockModifications) {
            int wx = mod[0], wy = mod[1], wz = mod[2], action = mod[3];

            AABB blockBox = new AABB(wx, wy, wz, wx + 1, wy + 1, wz + 1);

            if (action == Spawner.ACTION_PLACE) {
                g(Gizmos.cuboid(blockBox, GizmoStyle.strokeAndFill(PLACE_STROKE, 1.5f, PLACE_FILL)));
            } else if (action == Spawner.ACTION_BREAK) {
                g(Gizmos.cuboid(blockBox, GizmoStyle.strokeAndFill(BREAK_STROKE, 1.5f, BREAK_FILL)));
            }
        }
    }

    private static void renderChunkCross(int chunkX, int chunkZ) {
        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;
        int y = 64;
        int yellow = ARGB.color(204, 255, 255, 0);

        // X axis
        g(Gizmos.line(new Vec3(x - 8, y, z), new Vec3(x + 8, y, z), yellow, 2.0f));
        g(Gizmos.line(new Vec3(x, y, z - 8), new Vec3(x, y, z + 8), yellow, 2.0f));
        g(Gizmos.line(new Vec3(x, y - 8, z), new Vec3(x, y + 8, z), yellow, 2.0f));
    }
}
