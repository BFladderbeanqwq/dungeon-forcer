package hackerrouter.mineshaftforcer.render;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;

public final class BlueprintRenderer {
	private static final int PLACE_STROKE = ARGB.color(230, 50, 150, 255);
	private static final int PLACE_FILL = ARGB.color(100, 50, 150, 255);
	private static final int BREAK_STROKE = ARGB.color(230, 255, 165, 0);
	private static final int BREAK_FILL = ARGB.color(100, 255, 165, 0);
	private static final int TARGET_STROKE = ARGB.color(240, 255, 255, 255);
	private static final int TARGET_FILL = ARGB.color(96, 255, 255, 255);
	private static final int PERSIST_MS = 1;

	private BlueprintRenderer() {
	}

	public static void init() {
		LevelRenderEvents.BEFORE_GIZMOS.register(BlueprintRenderer::onWorldRender);
	}

	private static void onWorldRender(LevelRenderContext context) {
		if (BlueprintRenderQueue.isEmpty()) {
			return;
		}
		for (BlueprintRenderBlock block : BlueprintRenderQueue.blocks()) {
			renderBlock(block);
		}
	}

	private static void renderBlock(BlueprintRenderBlock block) {
		AABB blockBox = new AABB(block.x(), block.y(), block.z(), block.x() + 1, block.y() + 1, block.z() + 1);
		switch (block.kind()) {
			case PLACE -> g(Gizmos.cuboid(blockBox, GizmoStyle.strokeAndFill(PLACE_STROKE, 1.5F, PLACE_FILL)));
			case BREAK -> g(Gizmos.cuboid(blockBox, GizmoStyle.strokeAndFill(BREAK_STROKE, 1.5F, BREAK_FILL)));
			case TARGET -> g(Gizmos.cuboid(blockBox, GizmoStyle.strokeAndFill(TARGET_STROKE, 2.2F, TARGET_FILL)));
		}
	}

	private static GizmoProperties g(GizmoProperties properties) {
		return properties.persistForMillis(PERSIST_MS).setAlwaysOnTop();
	}
}
