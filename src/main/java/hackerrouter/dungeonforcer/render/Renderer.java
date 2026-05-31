package hackerrouter.dungeonforcer.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import hackerrouter.dungeonforcer.Spawner;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static net.minecraft.client.renderer.RenderPipelines.DEBUG_FILLED_SNIPPET;

public class Renderer {
    private static final RenderPipeline SEE_THROUGH_DEBUG_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(DEBUG_FILLED_SNIPPET)
                    .withLocation("pipeline/see_through_debug_quads")
                    .withCull(true)
                    .withDepthStencilState(Optional.empty())
                    .build());
    private static final RenderType SEE_THROUGH_DEBUG_QUADS = RenderType.create(
            "see_through_debug_quads",
            RenderSetup.builder(SEE_THROUGH_DEBUG_QUADS_PIPELINE)
                    .sortOnUpload()
                    .createRenderSetup());

    private static final int PLACE_FILL = ARGB.color(100, 50, 150, 255);
    private static final int BREAK_FILL = ARGB.color(100, 255, 165, 0);
    private static final int REF_FILL = ARGB.color(28, 180, 180, 180);
    private static final int TARGET_FILL = ARGB.color(96, 255, 255, 255);
    private static final int CHEST_CONTROL_FILL = ARGB.color(120, 55, 255, 135);
    private static final float[][][] FACE_VERTS = {
            {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}},
            {{1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {0, 0, 1}},
            {{0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {0, 0, 0}},
            {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}},
    };

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(Renderer::onWorldRender);
    }

    private static void onWorldRender(LevelRenderContext context) {
        if (RenderQueue.isEmpty()) return;
        for (Spawner spawner : RenderQueue.getSpawnerHighlights()) {
            renderSpawnerBox(spawner);
            renderBlockModifications(spawner, context);
        }
        for (int[] chunkCross : RenderQueue.getChunkCrosses()) {
            renderChunkCross(chunkCross[0], chunkCross[1]);
        }
    }

    private static GizmoProperties g(GizmoProperties properties) {
        return properties.persistForMillis(1).setAlwaysOnTop();
    }

    private static void renderSpawnerBox(Spawner spawner) {
        int strokeColor;
        int fillColor;
        switch (spawner.type) {
            case SKELETON:
                strokeColor = ARGB.color(204, 77, 204, 255);
                fillColor = ARGB.color(64, 77, 204, 255);
                break;
            case ZOMBIE:
                strokeColor = ARGB.color(204, 77, 255, 77);
                fillColor = ARGB.color(64, 77, 255, 77);
                break;
            case SPIDER:
                strokeColor = ARGB.color(204, 255, 77, 77);
                fillColor = ARGB.color(64, 255, 77, 77);
                break;
            default:
                strokeColor = ARGB.color(204, 255, 255, 255);
                fillColor = ARGB.color(64, 255, 255, 255);
        }
        g(Gizmos.cuboid(
                new AABB(spawner.x, spawner.y, spawner.z, spawner.x + 1, spawner.y + 1, spawner.z + 1),
                GizmoStyle.strokeAndFill(strokeColor, 2.5f, fillColor)));
        g(Gizmos.cuboid(
                new AABB(
                        spawner.x - spawner.sizeX - 1, spawner.y - 1, spawner.z - spawner.sizeZ - 1,
                        spawner.x + spawner.sizeX + 2, spawner.y + 5, spawner.z + spawner.sizeZ + 2),
                GizmoStyle.stroke(
                        ARGB.color(77, ARGB.red(strokeColor), ARGB.green(strokeColor), ARGB.blue(strokeColor)),
                        1.5f)));
    }

    private static void renderBlockModifications(Spawner spawner, LevelRenderContext context) {
        if (spawner.blockModifications == null || spawner.blockModifications.isEmpty()) return;
        Vec3 cameraPos = context.gameRenderer().getMainCamera().position();
        var level = Minecraft.getInstance().level;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), SEE_THROUGH_DEBUG_QUADS, (pose, buffer) -> {
            Map<Long, Integer> blockActions = new HashMap<>();
            for (int[] mod : spawner.blockModifications) {
                blockActions.put(BlockPos.asLong(mod[0], mod[1], mod[2]), mod[3]);
            }
            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
            for (int[] mod : spawner.blockModifications) {
                int wx = mod[0], wy = mod[1], wz = mod[2], action = mod[3];
                mutablePos.set(wx, wy, wz);
                if (action == Spawner.ACTION_BREAK && level != null && level.getBlockState(mutablePos).isAir()) {
                    continue;
                }
                int fillColor = fillColor(action);
                if (fillColor == 0) continue;
                float alpha = ARGB.alpha(fillColor) / 255f;
                float red = ARGB.red(fillColor) / 255f;
                float green = ARGB.green(fillColor) / 255f;
                float blue = ARGB.blue(fillColor) / 255f;
                for (Direction direction : Direction.values()) {
                    long neighborKey = BlockPos.asLong(
                            wx + direction.getStepX(), wy + direction.getStepY(), wz + direction.getStepZ());
                    Integer neighborAction = blockActions.get(neighborKey);
                    if (neighborAction != null && neighborAction == action) continue;
                    for (float[] vertex : FACE_VERTS[direction.get3DDataValue()]) {
                        buffer.addVertex(
                                        pose.pose(),
                                        (float) (wx - cameraPos.x() + vertex[0]),
                                        (float) (wy - cameraPos.y() + vertex[1]),
                                        (float) (wz - cameraPos.z() + vertex[2]))
                                .setColor(red, green, blue, alpha);
                    }
                }
            }
        });
    }

    private static int fillColor(int action) {
        if (action == Spawner.ACTION_PLACE) return PLACE_FILL;
        if (action == Spawner.ACTION_BREAK) return BREAK_FILL;
        if (action == Spawner.ACTION_REFERENCE) return REF_FILL;
        if (action == Spawner.ACTION_TARGET) return TARGET_FILL;
        if (action == Spawner.ACTION_CHEST_CONTROL) return CHEST_CONTROL_FILL;
        return 0;
    }

    private static void renderChunkCross(int chunkX, int chunkZ) {
        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;
        int y = 64;
        int yellow = ARGB.color(204, 255, 255, 0);
        g(Gizmos.line(new Vec3(x - 8, y, z), new Vec3(x + 8, y, z), yellow, 2.0f));
        g(Gizmos.line(new Vec3(x, y, z - 8), new Vec3(x, y, z + 8), yellow, 2.0f));
        g(Gizmos.line(new Vec3(x, y - 8, z), new Vec3(x, y + 8, z), yellow, 2.0f));
    }
}
