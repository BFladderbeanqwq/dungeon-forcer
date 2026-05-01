package hackerrouter.dungeonforcer.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import hackerrouter.dungeonforcer.Spawner;
import hackerrouter.dungeonforcer.SpawnerType;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class Renderer {

    public static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(Renderer::onWorldRender);
    }

    private static void onWorldRender(WorldRenderContext context) {
        if (RenderQueue.isEmpty()) return;

        Camera camera = context.camera();
        PoseStack poseStack = context.matrixStack();

        poseStack.pushPose();
        poseStack.translate(
                -camera.getPosition().x,
                -camera.getPosition().y,
                -camera.getPosition().z
        );

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // 渲染刷怪笼高亮
        for (Spawner spawner : RenderQueue.getSpawnerHighlights()) {
            renderSpawnerBox(poseStack, spawner);
        }

        // 渲染好区块标记
        for (int[] chunkCross : RenderQueue.getChunkCrosses()) {
            renderChunkCross(poseStack, chunkCross[0], chunkCross[1]);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void renderSpawnerBox(PoseStack poseStack, Spawner spawner) {
        float r, g, b;
        switch (spawner.type) {
            case SKELETON: r = 0.3f; g = 0.8f; b = 1.0f; break; // 青色
            case ZOMBIE:   r = 0.3f; g = 1.0f; b = 0.3f; break; // 绿色
            case SPIDER:   r = 1.0f; g = 0.3f; b = 0.3f; break; // 红色
            default:       r = 1.0f; g = 1.0f; b = 1.0f; break;
        }
        float alpha = 0.3f;

        renderBox(poseStack, spawner.x, spawner.y, spawner.z,
                spawner.x + 1, spawner.y + 1, spawner.z + 1,
                r, g, b, 0.8f);

        int minX = spawner.x - spawner.sizeX - 1;
        int maxX = spawner.x + spawner.sizeX + 2;
        int minZ = spawner.z - spawner.sizeZ - 1;
        int maxZ = spawner.z + spawner.sizeZ + 2;
        int minY = spawner.y - 1;
        int maxY = spawner.y + 5;

        renderBoxOutline(poseStack, minX, minY, minZ, maxX, maxY, maxZ,
                r, g, b, alpha);
    }

    private static void renderChunkCross(PoseStack poseStack, int chunkX, int chunkZ) {
        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;
        int y = 64;

        renderLine(poseStack, x - 8, y, z, x + 8, y, z, 1.0f, 1.0f, 0.0f, 0.8f);
        renderLine(poseStack, x, y, z - 8, x, y, z + 8, 1.0f, 1.0f, 0.0f, 0.8f);
        renderLine(poseStack, x, y - 8, z, x, y + 8, z, 1.0f, 1.0f, 0.0f, 0.8f);
    }

    private static void renderBox(PoseStack poseStack,
                                  float x1, float y1, float z1, float x2, float y2, float z2,
                                  float r, float g, float b, float a) {
        Matrix4f matrix = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // Bottom
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a);
        // Top
        buffer.addVertex(matrix, x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z1).setColor(r, g, b, a);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void renderBoxOutline(PoseStack poseStack,
                                         float x1, float y1, float z1, float x2, float y2, float z2,
                                         float r, float g, float b, float a) {
        Matrix4f matrix = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        // Bottom face
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void renderLine(PoseStack poseStack,
                                   float x1, float y1, float z1, float x2, float y2, float z2,
                                   float r, float g, float b, float a) {
        Matrix4f matrix = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}