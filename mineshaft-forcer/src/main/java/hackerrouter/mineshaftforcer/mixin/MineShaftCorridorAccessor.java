package hackerrouter.mineshaftforcer.mixin;

import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MineshaftPieces.MineShaftCorridor.class)
public interface MineShaftCorridorAccessor {
	@Accessor("hasRails")
	boolean mineshaftforcer$hasRails();

	@Accessor("spiderCorridor")
	boolean mineshaftforcer$spiderCorridor();

	@Accessor("hasPlacedSpider")
	boolean mineshaftforcer$hasPlacedSpider();

	@Accessor("numSections")
	int mineshaftforcer$numSections();
}
