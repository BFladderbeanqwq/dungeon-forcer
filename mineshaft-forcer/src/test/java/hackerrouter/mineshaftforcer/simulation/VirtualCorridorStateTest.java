package hackerrouter.mineshaftforcer.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualCorridorStateTest {
	@Test
	void isInteriorUsesOceanFloorHeightAtYPlusOne() {
		VirtualCorridorState state = new VirtualCorridorState(0, -1, 0, 2, 3, 9, 64);

		assertTrue(state.isInterior(1, 0, 4));

		state.setOceanFloorHeight(1, 4, 1);

		assertFalse(state.isInterior(1, 0, 4));
	}

	@Test
	void isInteriorIgnoresWhetherTargetBlockIsAir() {
		VirtualCorridorState state = new VirtualCorridorState(0, -1, 0, 2, 3, 9, 64);

		state.setBlock(1, 0, 4, BlockKind.SOLID);

		assertTrue(state.isInterior(1, 0, 4));
	}

	@Test
	void isInteriorRequiresYPlusOneInsideBounds() {
		VirtualCorridorState state = new VirtualCorridorState(0, -1, 0, 2, 3, 9, 64);

		assertFalse(state.isInterior(1, 3, 4));
	}

	@Test
	void generationBoundsClipReadsInteriorAndGeneratedPlacement() {
		VirtualCorridorState state = new VirtualCorridorState(0, -1, 0, 2, 3, 9, 64);
		state.setBlock(2, 0, 4, BlockKind.SOLID);
		state.setGenerationBounds(0, -1, 0, 1, 3, 9);

		assertTrue(state.isInside(2, 0, 4));
		assertFalse(state.isGenerationInside(2, 0, 4));
		assertEquals(BlockKind.AIR, state.getBlock(2, 0, 4));
		assertFalse(state.isInterior(2, 0, 4));

		state.placeGeneratedBlock(2, 0, 4, BlockKind.NON_SOLID);

		assertEquals(BlockKind.AIR, state.getBlock(2, 0, 4));
	}

	@Test
	void isSupportingBoxChecksForNonAirBlocksAboveTheBeam() {
		VirtualCorridorState state = new VirtualCorridorState(0, -1, 0, 2, 3, 9, 64);

		assertFalse(state.isSupportingBox(0, 2, 2, 4));

		state.fillBox(0, 3, 4, 2, 3, 4, BlockKind.SOLID);

		assertTrue(state.isSupportingBox(0, 2, 2, 4));
	}

	@Test
	void hasSturdyNeighboursCountsOnlySolidNeighbours() {
		VirtualCorridorState state = new VirtualCorridorState(0, -1, 0, 2, 3, 9, 64);
		state.setBlock(0, 0, 4, BlockKind.SOLID);
		state.setBlock(2, 0, 4, BlockKind.NON_SOLID);
		state.setBlock(1, 0, 3, BlockKind.SOLID);

		assertTrue(state.hasSturdyNeighbours(1, 0, 4, 2));
		assertFalse(state.hasSturdyNeighbours(1, 0, 4, 3));
	}
}
