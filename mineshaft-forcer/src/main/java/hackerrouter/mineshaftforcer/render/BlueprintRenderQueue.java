package hackerrouter.mineshaftforcer.render;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BlueprintRenderQueue {
	private static final List<BlueprintRenderBlock> BLOCKS = new CopyOnWriteArrayList<>();

	private BlueprintRenderQueue() {
	}

	public static void replace(List<BlueprintRenderBlock> blocks) {
		BLOCKS.clear();
		BLOCKS.addAll(blocks);
	}

	public static List<BlueprintRenderBlock> blocks() {
		return List.copyOf(BLOCKS);
	}

	public static boolean isEmpty() {
		return BLOCKS.isEmpty();
	}

	public static void clear() {
		BLOCKS.clear();
	}
}
