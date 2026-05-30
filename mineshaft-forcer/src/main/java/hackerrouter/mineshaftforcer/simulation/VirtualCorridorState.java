package hackerrouter.mineshaftforcer.simulation;

import java.util.HashMap;
import java.util.Map;

public final class VirtualCorridorState {
	private final int minX;
	private final int minY;
	private final int minZ;
	private final int maxX;
	private final int maxY;
	private final int maxZ;
	private final int defaultOceanFloorHeight;
	private final Map<LocalBlockPos, BlockKind> blocks = new HashMap<>();
	private final Map<Long, Integer> oceanFloorHeights = new HashMap<>();
	private int generationMinX;
	private int generationMinY;
	private int generationMinZ;
	private int generationMaxX;
	private int generationMaxY;
	private int generationMaxZ;
	private boolean generationBoundsEmpty;

	public VirtualCorridorState(
			int minX,
			int minY,
			int minZ,
			int maxX,
			int maxY,
			int maxZ,
			int defaultOceanFloorHeight
	) {
		if (minX > maxX || minY > maxY || minZ > maxZ) {
			throw new IllegalArgumentException("Invalid corridor bounds");
		}
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;
		this.maxX = maxX;
		this.maxY = maxY;
		this.maxZ = maxZ;
		this.defaultOceanFloorHeight = defaultOceanFloorHeight;
		this.generationMinX = minX;
		this.generationMinY = minY;
		this.generationMinZ = minZ;
		this.generationMaxX = maxX;
		this.generationMaxY = maxY;
		this.generationMaxZ = maxZ;
	}

	private VirtualCorridorState(VirtualCorridorState source) {
		this.minX = source.minX;
		this.minY = source.minY;
		this.minZ = source.minZ;
		this.maxX = source.maxX;
		this.maxY = source.maxY;
		this.maxZ = source.maxZ;
		this.defaultOceanFloorHeight = source.defaultOceanFloorHeight;
		this.generationMinX = source.generationMinX;
		this.generationMinY = source.generationMinY;
		this.generationMinZ = source.generationMinZ;
		this.generationMaxX = source.generationMaxX;
		this.generationMaxY = source.generationMaxY;
		this.generationMaxZ = source.generationMaxZ;
		this.generationBoundsEmpty = source.generationBoundsEmpty;
		this.blocks.putAll(source.blocks);
		this.oceanFloorHeights.putAll(source.oceanFloorHeights);
	}

	public static VirtualCorridorState northSouthCorridor(int length, int defaultOceanFloorHeight) {
		return new VirtualCorridorState(0, -1, 0, 2, 3, length, defaultOceanFloorHeight);
	}

	public VirtualCorridorState copy() {
		return new VirtualCorridorState(this);
	}

	public boolean isInside(int x, int y, int z) {
		return x >= this.minX && x <= this.maxX
				&& y >= this.minY && y <= this.maxY
				&& z >= this.minZ && z <= this.maxZ;
	}

	public void setGenerationBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		int clippedMinX = Math.max(this.minX, minX);
		int clippedMinY = Math.max(this.minY, minY);
		int clippedMinZ = Math.max(this.minZ, minZ);
		int clippedMaxX = Math.min(this.maxX, maxX);
		int clippedMaxY = Math.min(this.maxY, maxY);
		int clippedMaxZ = Math.min(this.maxZ, maxZ);
		if (clippedMinX > clippedMaxX || clippedMinY > clippedMaxY || clippedMinZ > clippedMaxZ) {
			setEmptyGenerationBounds();
			return;
		}

		this.generationMinX = clippedMinX;
		this.generationMinY = clippedMinY;
		this.generationMinZ = clippedMinZ;
		this.generationMaxX = clippedMaxX;
		this.generationMaxY = clippedMaxY;
		this.generationMaxZ = clippedMaxZ;
		this.generationBoundsEmpty = false;
	}

	public void setEmptyGenerationBounds() {
		this.generationBoundsEmpty = true;
	}

	public boolean isGenerationInside(int x, int y, int z) {
		return !this.generationBoundsEmpty
				&& isInside(x, y, z)
				&& x >= this.generationMinX && x <= this.generationMaxX
				&& y >= this.generationMinY && y <= this.generationMaxY
				&& z >= this.generationMinZ && z <= this.generationMaxZ;
	}

	public BlockKind getBlock(int x, int y, int z) {
		if (!isGenerationInside(x, y, z)) {
			return BlockKind.AIR;
		}
		return this.blocks.getOrDefault(new LocalBlockPos(x, y, z), BlockKind.AIR);
	}

	public void setBlock(int x, int y, int z, BlockKind kind) {
		if (!isInside(x, y, z)) {
			throw new IllegalArgumentException("Block is outside the virtual corridor bounds");
		}
		LocalBlockPos pos = new LocalBlockPos(x, y, z);
		if (kind == BlockKind.AIR) {
			this.blocks.remove(pos);
		} else {
			this.blocks.put(pos, kind);
		}
	}

	public void fillBox(int x0, int y0, int z0, int x1, int y1, int z1, BlockKind kind) {
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					setBlock(x, y, z, kind);
				}
			}
		}
	}

	public void placeGeneratedBlock(int x, int y, int z, BlockKind kind) {
		if (isGenerationInside(x, y, z)) {
			setBlock(x, y, z, kind);
		}
	}

	public void fillGeneratedBox(int x0, int y0, int z0, int x1, int y1, int z1, BlockKind kind) {
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					placeGeneratedBlock(x, y, z, kind);
				}
			}
		}
	}

	public void setOceanFloorHeight(int x, int z, int height) {
		this.oceanFloorHeights.put(columnKey(x, z), height);
	}

	public int getOceanFloorHeight(int x, int z) {
		return this.oceanFloorHeights.getOrDefault(columnKey(x, z), this.defaultOceanFloorHeight);
	}

	public boolean isInterior(int x, int y, int z) {
		int testY = y + 1;
		if (!isGenerationInside(x, testY, z)) {
			return false;
		}
		return testY < getOceanFloorHeight(x, z);
	}

	public boolean isSupportingBox(int x0, int x1, int y1, int z) {
		for (int x = x0; x <= x1; x++) {
			if (getBlock(x, y1 + 1, z).isAir()) {
				return false;
			}
		}
		return true;
	}

	public boolean hasSturdyNeighbours(int x, int y, int z, int count) {
		int sturdyNeighbours = 0;
		if (getBlock(x, y - 1, z).isFaceSturdy() && ++sturdyNeighbours >= count) {
			return true;
		}
		if (getBlock(x, y + 1, z).isFaceSturdy() && ++sturdyNeighbours >= count) {
			return true;
		}
		if (getBlock(x - 1, y, z).isFaceSturdy() && ++sturdyNeighbours >= count) {
			return true;
		}
		if (getBlock(x + 1, y, z).isFaceSturdy() && ++sturdyNeighbours >= count) {
			return true;
		}
		if (getBlock(x, y, z - 1).isFaceSturdy() && ++sturdyNeighbours >= count) {
			return true;
		}
		return getBlock(x, y, z + 1).isFaceSturdy() && ++sturdyNeighbours >= count;
	}

	private static long columnKey(int x, int z) {
		return ((long)x << 32) ^ Integer.toUnsignedLong(z);
	}
}
