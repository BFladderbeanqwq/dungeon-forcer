package hackerrouter.dungeonforcer.render;

import hackerrouter.dungeonforcer.Spawner;
import hackerrouter.dungeonforcer.SpawnerType;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 使用 26.1 Gizmos API 渲染刷怪笼高亮和好区块标记。
 * Gizmos 自动处理相机偏移、混合状态和深度测试。
 */
public class Renderer {

    public static void init() {
        // BEFORE_GIZMOS 在 GizmoCollector 活跃期间触发，可以安全调用 Gizmos API
        LevelRenderEvents.BEFORE_GIZMOS.register(Renderer::onWorldRender);
    }

    private static void onWorldRender(LevelRenderContext context) {
        if (RenderQueue.isEmpty()) return;

        // 渲染刷怪笼高亮
        for (Spawner spawner : RenderQueue.getSpawnerHighlights()) {
            renderSpawnerBox(spawner);
        }

        // 渲染好区块标记
        for (int[] chunkCross : RenderQueue.getChunkCrosses()) {
            renderChunkCross(chunkCross[0], chunkCross[1]);
        }
    }

    private static void renderSpawnerBox(Spawner spawner) {
        int strokeColor;
        int fillColor;
        switch (spawner.type) {
            case SKELETON:
                strokeColor = ARGB.color(204, 77, 204, 255);  // 青色
                fillColor   = ARGB.color(64, 77, 204, 255);
                break;
            case ZOMBIE:
                strokeColor = ARGB.color(204, 77, 255, 77);   // 绿色
                fillColor   = ARGB.color(64, 77, 255, 77);
                break;
            case SPIDER:
                strokeColor = ARGB.color(204, 255, 77, 77);   // 红色
                fillColor   = ARGB.color(64, 255, 77, 77);
                break;
            default:
                strokeColor = ARGB.color(204, 255, 255, 255);
                fillColor   = ARGB.color(64, 255, 255, 255);
                break;
        }

        // 刷怪笼方块高亮（实心 + 描边）
        AABB spawnerBlock = new AABB(
                spawner.x, spawner.y, spawner.z,
                spawner.x + 1, spawner.y + 1, spawner.z + 1);
        Gizmos.cuboid(spawnerBlock,
                GizmoStyle.strokeAndFill(strokeColor, 2.5f, fillColor))
                .setAlwaysOnTop();

        // 地牢范围描边
        int minX = spawner.x - spawner.sizeX - 1;
        int maxX = spawner.x + spawner.sizeX + 2;
        int minZ = spawner.z - spawner.sizeZ - 1;
        int maxZ = spawner.z + spawner.sizeZ + 2;
        int minY = spawner.y - 1;
        int maxY = spawner.y + 5;

        AABB dungeonBounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        Gizmos.cuboid(dungeonBounds,
                GizmoStyle.stroke(ARGB.color(77, ARGB.red(strokeColor),
                        ARGB.green(strokeColor), ARGB.blue(strokeColor)), 1.5f))
                .setAlwaysOnTop();
    }

    private static void renderChunkCross(int chunkX, int chunkZ) {
        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;
        int y = 64;
        int yellow = ARGB.color(204, 255, 255, 0);

        // X 轴线
        Gizmos.line(new Vec3(x - 8, y, z), new Vec3(x + 8, y, z), yellow, 2.0f).setAlwaysOnTop();
        // Z 轴线
        Gizmos.line(new Vec3(x, y, z - 8), new Vec3(x, y, z + 8), yellow, 2.0f).setAlwaysOnTop();
        // Y 轴线
        Gizmos.line(new Vec3(x, y - 8, z), new Vec3(x, y + 8, z), yellow, 2.0f).setAlwaysOnTop();
    }
}
