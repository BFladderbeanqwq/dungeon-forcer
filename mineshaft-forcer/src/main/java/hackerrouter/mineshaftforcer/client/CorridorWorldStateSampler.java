package hackerrouter.mineshaftforcer.client;

import hackerrouter.mineshaftforcer.render.BlueprintRenderMapper;
import hackerrouter.mineshaftforcer.render.CorridorOrientation;
import hackerrouter.mineshaftforcer.simulation.BlockKind;
import hackerrouter.mineshaftforcer.simulation.LocalBlockPos;
import hackerrouter.mineshaftforcer.simulation.VirtualCorridorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class CorridorWorldStateSampler {
	private CorridorWorldStateSampler() {
	}

	public static VirtualCorridorState sample(
			int boxMinX,
			int boxMinY,
			int boxMinZ,
			CorridorOrientation orientation,
			int sections
	) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			throw new IllegalStateException("No client level is loaded");
		}

		int length = sections * 5 - 1;
		VirtualCorridorState state = VirtualCorridorState.northSouthCorridor(length, 64);
		for (int x = 0; x <= 2; x++) {
			for (int z = 0; z <= length; z++) {
				LocalBlockPos worldColumn = BlueprintRenderMapper.toWorldBlock(
						new LocalBlockPos(x, 0, z),
						boxMinX,
						boxMinY,
						boxMinZ,
						orientation,
						length);
				state.setOceanFloorHeight(
						x,
						z,
						level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, worldColumn.x(), worldColumn.z()) - boxMinY);

				for (int y = -1; y <= 3; y++) {
					LocalBlockPos world = BlueprintRenderMapper.toWorldBlock(
							new LocalBlockPos(x, y, z),
							boxMinX,
							boxMinY,
							boxMinZ,
							orientation,
							length);
					BlockState blockState = level.getBlockState(new BlockPos(world.x(), world.y(), world.z()));
					state.setBlock(x, y, z, toBlockKind(blockState));
				}
			}
		}
		return state;
	}

	private static BlockKind toBlockKind(BlockState state) {
		if (state.isAir()) {
			return BlockKind.AIR;
		}
		return state.isSolidRender() ? BlockKind.SOLID : BlockKind.NON_SOLID;
	}
}
