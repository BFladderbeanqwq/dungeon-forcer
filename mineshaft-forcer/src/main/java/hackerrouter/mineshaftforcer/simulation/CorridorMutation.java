package hackerrouter.mineshaftforcer.simulation;

public record CorridorMutation(LocalBlockPos pos, BlockKind kind, Integer oceanFloorHeight) {
	public CorridorMutation {
		if (kind == null && oceanFloorHeight == null) {
			throw new IllegalArgumentException("Mutation must change either a block or an ocean-floor height");
		}
	}

	public static CorridorMutation setBlock(int x, int y, int z, BlockKind kind) {
		return new CorridorMutation(new LocalBlockPos(x, y, z), kind, null);
	}

	public static CorridorMutation oceanFloor(int x, int y, int z, int oceanFloorHeight) {
		return new CorridorMutation(new LocalBlockPos(x, y, z), null, oceanFloorHeight);
	}

	public boolean changesBlock() {
		return this.kind != null;
	}

	public boolean changesOceanFloorHeight() {
		return this.oceanFloorHeight != null;
	}
}
