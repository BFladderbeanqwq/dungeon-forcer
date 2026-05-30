package hackerrouter.mineshaftforcer.render;

import hackerrouter.mineshaftforcer.simulation.BlockKind;
import hackerrouter.mineshaftforcer.simulation.CorridorMutation;
import hackerrouter.mineshaftforcer.simulation.CorridorSearchResult;
import hackerrouter.mineshaftforcer.simulation.LocalBlockPos;
import hackerrouter.mineshaftforcer.simulation.MinecartChestAttempt;

import java.util.ArrayList;
import java.util.List;

public final class BlueprintRenderMapper {
	private BlueprintRenderMapper() {
	}

	public static List<BlueprintRenderBlock> toWorldBlocks(
			CorridorSearchResult result,
			int boxMinX,
			int boxMinY,
			int boxMinZ,
			CorridorOrientation orientation,
			int corridorLength
	) {
		List<BlueprintRenderBlock> blocks = new ArrayList<>();
		for (CorridorMutation mutation : result.mutations()) {
			LocalBlockPos world = toWorldBlock(mutation.pos(), boxMinX, boxMinY, boxMinZ, orientation, corridorLength);
			BlueprintRenderKind kind = mutation.changesBlock() && mutation.kind() == BlockKind.AIR
					? BlueprintRenderKind.BREAK
					: BlueprintRenderKind.PLACE;
			blocks.add(new BlueprintRenderBlock(world.x(), world.y(), world.z(), kind));
		}

		MinecartChestAttempt minecart = result.firstSpawnedMinecart();
		if (minecart != null) {
			LocalBlockPos world = toWorldBlock(minecart.pos(), boxMinX, boxMinY, boxMinZ, orientation, corridorLength);
			blocks.add(new BlueprintRenderBlock(world.x(), world.y(), world.z(), BlueprintRenderKind.TARGET));
		}
		return List.copyOf(blocks);
	}

	public static LocalBlockPos toWorldBlock(
			LocalBlockPos local,
			int boxMinX,
			int boxMinY,
			int boxMinZ,
			CorridorOrientation orientation,
			int corridorLength
	) {
		int worldX;
		int worldZ;
		switch (orientation) {
			case NORTH -> {
				worldX = boxMinX + local.x();
				worldZ = boxMinZ + corridorLength - local.z();
			}
			case SOUTH -> {
				worldX = boxMinX + local.x();
				worldZ = boxMinZ + local.z();
			}
			case WEST -> {
				worldX = boxMinX + corridorLength - local.z();
				worldZ = boxMinZ + local.x();
			}
			case EAST -> {
				worldX = boxMinX + local.z();
				worldZ = boxMinZ + local.x();
			}
			default -> throw new IllegalStateException("Unhandled orientation " + orientation);
		}
		return new LocalBlockPos(worldX, boxMinY + local.y(), worldZ);
	}
}
