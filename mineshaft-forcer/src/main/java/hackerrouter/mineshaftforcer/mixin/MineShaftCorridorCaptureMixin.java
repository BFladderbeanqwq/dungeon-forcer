package hackerrouter.mineshaftforcer.mixin;

import hackerrouter.mineshaftforcer.capture.CapturedCorridor;
import hackerrouter.mineshaftforcer.capture.CorridorCaptureStore;
import hackerrouter.mineshaftforcer.render.BlueprintRenderMapper;
import hackerrouter.mineshaftforcer.render.CorridorOrientation;
import hackerrouter.mineshaftforcer.simulation.BlockKind;
import hackerrouter.mineshaftforcer.simulation.LocalBlockPos;
import hackerrouter.mineshaftforcer.simulation.VirtualCorridorState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MineshaftPieces.MineShaftCorridor.class)
public abstract class MineShaftCorridorCaptureMixin {
	@Inject(method = "postProcess", at = @At("HEAD"))
	private void mineshaftforcer$capturePostProcessStart(
			WorldGenLevel level,
			StructureManager structureManager,
			ChunkGenerator generator,
			RandomSource random,
			BoundingBox chunkBB,
			ChunkPos chunkPos,
			BlockPos referencePos,
			CallbackInfo ci
	) {
		if (!(random instanceof XoroshiroRandomSource xoroshiro)) {
			return;
		}

		Xoroshiro128PlusPlus generatorState = ((XoroshiroRandomSourceAccessor)xoroshiro).mineshaftforcer$randomNumberGenerator();
		Xoroshiro128PlusPlusAccessor seedAccessor = (Xoroshiro128PlusPlusAccessor)generatorState;
		MineShaftCorridorAccessor corridor = (MineShaftCorridorAccessor)this;
		StructurePiece piece = (StructurePiece)(Object)this;
		BoundingBox box = piece.getBoundingBox();
		CorridorOrientation orientation = toCorridorOrientation(piece.getOrientation());
		int numSections = corridor.mineshaftforcer$numSections();
		VirtualCorridorState initialState = sampleInitialState(level, box, chunkBB, orientation, numSections);

		CorridorCaptureStore.add(new CapturedCorridor(
				0,
				box.minX(),
				box.minY(),
				box.minZ(),
				box.maxX(),
				box.maxY(),
				box.maxZ(),
				orientation,
				numSections,
				corridor.mineshaftforcer$hasRails(),
				corridor.mineshaftforcer$spiderCorridor(),
				corridor.mineshaftforcer$hasPlacedSpider(),
				seedAccessor.mineshaftforcer$seedLo(),
				seedAccessor.mineshaftforcer$seedHi(),
				initialState));
	}

	private static VirtualCorridorState sampleInitialState(
			WorldGenLevel level,
			BoundingBox box,
			BoundingBox chunkBB,
			CorridorOrientation orientation,
			int numSections
	) {
		int length = numSections * 5 - 1;
		VirtualCorridorState state = VirtualCorridorState.northSouthCorridor(length, 64);
		int clipMinX = Integer.MAX_VALUE;
		int clipMinY = Integer.MAX_VALUE;
		int clipMinZ = Integer.MAX_VALUE;
		int clipMaxX = Integer.MIN_VALUE;
		int clipMaxY = Integer.MIN_VALUE;
		int clipMaxZ = Integer.MIN_VALUE;
		for (int x = 0; x <= 2; x++) {
			for (int z = 0; z <= length; z++) {
				LocalBlockPos worldColumn = BlueprintRenderMapper.toWorldBlock(
						new LocalBlockPos(x, 0, z),
						box.minX(),
						box.minY(),
						box.minZ(),
						orientation,
						length);
				state.setOceanFloorHeight(
						x,
						z,
						level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, worldColumn.x(), worldColumn.z()) - box.minY());

				for (int y = -1; y <= 3; y++) {
					LocalBlockPos world = BlueprintRenderMapper.toWorldBlock(
							new LocalBlockPos(x, y, z),
							box.minX(),
							box.minY(),
							box.minZ(),
							orientation,
							length);
					BlockPos worldPos = new BlockPos(world.x(), world.y(), world.z());
					if (chunkBB.isInside(worldPos)) {
						clipMinX = Math.min(clipMinX, x);
						clipMinY = Math.min(clipMinY, y);
						clipMinZ = Math.min(clipMinZ, z);
						clipMaxX = Math.max(clipMaxX, x);
						clipMaxY = Math.max(clipMaxY, y);
						clipMaxZ = Math.max(clipMaxZ, z);
					}
					BlockState blockState = level.getBlockState(worldPos);
					state.setBlock(x, y, z, toBlockKind(blockState));
				}
			}
		}
		if (clipMinX == Integer.MAX_VALUE) {
			state.setEmptyGenerationBounds();
		} else {
			state.setGenerationBounds(clipMinX, clipMinY, clipMinZ, clipMaxX, clipMaxY, clipMaxZ);
		}
		return state;
	}

	private static BlockKind toBlockKind(BlockState state) {
		if (state.isAir()) {
			return BlockKind.AIR;
		}
		return state.isSolidRender() ? BlockKind.SOLID : BlockKind.NON_SOLID;
	}

	private static CorridorOrientation toCorridorOrientation(Direction direction) {
		if (direction == null) {
			return CorridorOrientation.SOUTH;
		}
		return switch (direction) {
			case NORTH -> CorridorOrientation.NORTH;
			case SOUTH -> CorridorOrientation.SOUTH;
			case WEST -> CorridorOrientation.WEST;
			case EAST -> CorridorOrientation.EAST;
			default -> CorridorOrientation.SOUTH;
		};
	}
}
