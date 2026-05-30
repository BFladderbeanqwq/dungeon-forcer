package hackerrouter.mineshaftforcer.render;

import hackerrouter.mineshaftforcer.simulation.LocalBlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintRenderMapperTest {
	@Test
	void mapsNorthSouthLikeStructurePiece() {
		LocalBlockPos local = new LocalBlockPos(2, 0, 7);

		assertEquals(new LocalBlockPos(102, 64, 207),
				BlueprintRenderMapper.toWorldBlock(local, 100, 64, 200, CorridorOrientation.SOUTH, 24));
		assertEquals(new LocalBlockPos(102, 64, 217),
				BlueprintRenderMapper.toWorldBlock(local, 100, 64, 200, CorridorOrientation.NORTH, 24));
	}

	@Test
	void mapsEastWestLikeStructurePiece() {
		LocalBlockPos local = new LocalBlockPos(2, 0, 7);

		assertEquals(new LocalBlockPos(107, 64, 202),
				BlueprintRenderMapper.toWorldBlock(local, 100, 64, 200, CorridorOrientation.EAST, 24));
		assertEquals(new LocalBlockPos(117, 64, 202),
				BlueprintRenderMapper.toWorldBlock(local, 100, 64, 200, CorridorOrientation.WEST, 24));
	}
}
