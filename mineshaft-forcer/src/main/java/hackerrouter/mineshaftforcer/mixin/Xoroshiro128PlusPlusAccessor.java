package hackerrouter.mineshaftforcer.mixin;

import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Xoroshiro128PlusPlus.class)
public interface Xoroshiro128PlusPlusAccessor {
	@Accessor("seedLo")
	long mineshaftforcer$seedLo();

	@Accessor("seedHi")
	long mineshaftforcer$seedHi();
}
