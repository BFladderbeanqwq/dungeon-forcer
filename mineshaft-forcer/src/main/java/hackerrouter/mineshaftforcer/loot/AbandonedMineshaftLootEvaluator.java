package hackerrouter.mineshaftforcer.loot;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class AbandonedMineshaftLootEvaluator {
	private static final BlockPos SYNTHETIC_CHEST_POS = new BlockPos(0, 64, 0);

	private AbandonedMineshaftLootEvaluator() {
	}

	public static List<ItemStack> simulate(ServerLevel level, long lootSeed) {
		LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.ABANDONED_MINESHAFT);
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(SYNTHETIC_CHEST_POS))
				.create(LootContextParamSets.CHEST);
		ObjectArrayList<ItemStack> stacks = lootTable.getRandomItems(params, lootSeed);
		return List.copyOf(stacks);
	}

	public static boolean contains(ServerLevel level, long lootSeed, String itemId) {
		Identifier targetId = Identifier.parse(itemId);
		return simulate(level, lootSeed).stream()
				.anyMatch(stack -> !stack.isEmpty() && targetId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())));
	}

	public static String describe(ServerLevel level, long lootSeed) {
		List<ItemStack> stacks = simulate(level, lootSeed);
		if (stacks.isEmpty()) {
			return "empty";
		}
		StringBuilder builder = new StringBuilder();
		for (ItemStack stack : stacks) {
			if (builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(stack.getCount())
					.append("x ")
					.append(BuiltInRegistries.ITEM.getKey(stack.getItem()));
		}
		return builder.toString();
	}
}
