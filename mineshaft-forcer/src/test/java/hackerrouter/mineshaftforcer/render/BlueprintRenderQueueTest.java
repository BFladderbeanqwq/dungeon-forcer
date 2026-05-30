package hackerrouter.mineshaftforcer.render;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintRenderQueueTest {
	@AfterEach
	void clearQueue() {
		BlueprintRenderQueue.clear();
	}

	@Test
	void replaceOverwritesExistingBlocks() {
		BlueprintRenderQueue.replace(List.of(new BlueprintRenderBlock(1, 2, 3, BlueprintRenderKind.PLACE)));
		BlueprintRenderQueue.replace(List.of(
				new BlueprintRenderBlock(4, 5, 6, BlueprintRenderKind.BREAK),
				new BlueprintRenderBlock(7, 8, 9, BlueprintRenderKind.TARGET)));

		assertEquals(2, BlueprintRenderQueue.blocks().size());
		assertEquals(BlueprintRenderKind.BREAK, BlueprintRenderQueue.blocks().getFirst().kind());
	}

	@Test
	void clearRemovesBlocks() {
		BlueprintRenderQueue.replace(List.of(new BlueprintRenderBlock(1, 2, 3, BlueprintRenderKind.PLACE)));

		BlueprintRenderQueue.clear();

		assertTrue(BlueprintRenderQueue.isEmpty());
	}
}
