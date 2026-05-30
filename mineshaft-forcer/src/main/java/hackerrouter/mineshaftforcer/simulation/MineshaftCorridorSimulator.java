package hackerrouter.mineshaftforcer.simulation;

import hackerrouter.mineshaftforcer.simulation.rng.XoroshiroSimulator;

import java.util.ArrayList;
import java.util.List;

public final class MineshaftCorridorSimulator {
	public CorridorSimulationResult simulate(CorridorConfig config, VirtualCorridorState inputState, long seedLo, long seedHi) {
		VirtualCorridorState state = inputState.copy();
		XoroshiroSimulator random = new XoroshiroSimulator(seedLo, seedHi);
		List<CorridorEvent> events = new ArrayList<>();
		List<MinecartChestAttempt> minecarts = new ArrayList<>();

		int length = config.length();
		state.fillGeneratedBox(0, 0, 0, 2, 1, length, BlockKind.AIR);
		generateMaybeBox(state, random, events, 0.8F, 0, 2, 0, 2, 2, length, false, false, BlockKind.AIR);
		if (config.spiderCorridor()) {
			generateMaybeBox(state, random, events, 0.6F, 0, 0, 0, 2, 1, length, false, true, BlockKind.NON_SOLID);
		}

		boolean hasPlacedSpider = config.hasPlacedSpider();
		for (int section = 0; section < config.numSections(); section++) {
			int z = 2 + section * 5;
			placeSupport(state, random, events, z);
			maybePlaceCobWeb(state, random, events, 0.1F, 0, 2, z - 1);
			maybePlaceCobWeb(state, random, events, 0.1F, 2, 2, z - 1);
			maybePlaceCobWeb(state, random, events, 0.1F, 0, 2, z + 1);
			maybePlaceCobWeb(state, random, events, 0.1F, 2, 2, z + 1);
			maybePlaceCobWeb(state, random, events, 0.05F, 0, 2, z - 2);
			maybePlaceCobWeb(state, random, events, 0.05F, 2, 2, z - 2);
			maybePlaceCobWeb(state, random, events, 0.05F, 0, 2, z + 2);
			maybePlaceCobWeb(state, random, events, 0.05F, 2, 2, z + 2);
			minecarts.add(tryMinecartChest(state, random, events, section, 2, 0, z - 1));
			minecarts.add(tryMinecartChest(state, random, events, section, 0, 0, z + 1));

			if (config.spiderCorridor() && !hasPlacedSpider) {
				int newZ = z - 1 + random.nextInt(3);
				events.add(CorridorEvent.at(
						CorridorEventType.SPIDER_SPAWNER_ROLL,
						1,
						0,
						newZ,
						random.drawCount(),
						"nextInt(3)"));
				if (state.isGenerationInside(1, 0, newZ) && state.isInterior(1, 0, newZ)) {
					hasPlacedSpider = true;
					state.placeGeneratedBlock(1, 0, newZ, BlockKind.SOLID);
				}
			}
		}

		if (config.hasRails()) {
			for (int z = 0; z <= length; z++) {
				BlockKind floor = state.getBlock(1, -1, z);
				if (!floor.isAir() && floor.isFaceSturdy()) {
					float probability = state.isInterior(1, 0, z) ? 0.7F : 0.9F;
					float roll = random.nextFloat();
					events.add(CorridorEvent.at(
							CorridorEventType.RAIL_ROLL,
							1,
							0,
							z,
							random.drawCount(),
							"roll=" + roll + ", probability=" + probability));
				}
			}
		}

		return new CorridorSimulationResult(config, random.drawCount(), List.copyOf(events), List.copyOf(minecarts));
	}

	private static void generateMaybeBox(
			VirtualCorridorState state,
			XoroshiroSimulator random,
			List<CorridorEvent> events,
			float probability,
			int x0,
			int y0,
			int z0,
			int x1,
			int y1,
			int z1,
			boolean skipAir,
			boolean hasToBeInside,
			BlockKind placedKind
	) {
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					float roll = random.nextFloat();
					events.add(CorridorEvent.at(
							CorridorEventType.GENERATE_MAYBE_BOX_ROLL,
							x,
							y,
							z,
							random.drawCount(),
							"roll=" + roll + ", probability=" + probability));
					if (!(roll > probability)
							&& (!skipAir || !state.getBlock(x, y, z).isAir())
							&& (!hasToBeInside || state.isInterior(x, y, z))) {
						state.placeGeneratedBlock(x, y, z, placedKind);
					}
				}
			}
		}
	}

	private static void placeSupport(VirtualCorridorState state, XoroshiroSimulator random, List<CorridorEvent> events, int z) {
		boolean supported = state.isSupportingBox(0, 2, 2, z);
		events.add(CorridorEvent.at(
				CorridorEventType.SUPPORT_CHECK,
				1,
				2,
				z,
				random.drawCount(),
				"supported=" + supported));
		if (!supported) {
			return;
		}

		state.fillGeneratedBox(0, 0, z, 0, 1, z, BlockKind.NON_SOLID);
		state.fillGeneratedBox(2, 0, z, 2, 1, z, BlockKind.NON_SOLID);
		int style = random.nextInt(4);
		events.add(CorridorEvent.at(
				CorridorEventType.SUPPORT_STYLE_ROLL,
				1,
				2,
				z,
				random.drawCount(),
				"nextInt(4)=" + style));
		if (style == 0) {
			state.placeGeneratedBlock(0, 2, z, BlockKind.SOLID);
			state.placeGeneratedBlock(2, 2, z, BlockKind.SOLID);
		} else {
			state.fillGeneratedBox(0, 2, z, 2, 2, z, BlockKind.SOLID);
			float southTorch = random.nextFloat();
			events.add(CorridorEvent.at(
					CorridorEventType.SUPPORT_TORCH_ROLL,
					1,
					2,
					z - 1,
					random.drawCount(),
					"roll=" + southTorch + ", probability=0.05"));
			float northTorch = random.nextFloat();
			events.add(CorridorEvent.at(
					CorridorEventType.SUPPORT_TORCH_ROLL,
					1,
					2,
					z + 1,
					random.drawCount(),
					"roll=" + northTorch + ", probability=0.05"));
		}
	}

	private static void maybePlaceCobWeb(
			VirtualCorridorState state,
			XoroshiroSimulator random,
			List<CorridorEvent> events,
			float probability,
			int x,
			int y,
			int z
	) {
		if (!state.isInterior(x, y, z)) {
			events.add(CorridorEvent.at(
					CorridorEventType.COBWEB_INTERIOR_SKIPPED,
					x,
					y,
					z,
					random.drawCount(),
					"isInterior=false"));
			return;
		}

		float roll = random.nextFloat();
		boolean sturdy = roll < probability && state.hasSturdyNeighbours(x, y, z, 2);
		events.add(CorridorEvent.at(
				CorridorEventType.COBWEB_ROLL,
				x,
				y,
				z,
				random.drawCount(),
				"roll=" + roll + ", probability=" + probability + ", sturdy=" + sturdy));
		if (sturdy) {
			state.placeGeneratedBlock(x, y, z, BlockKind.NON_SOLID);
		}
	}

	private static MinecartChestAttempt tryMinecartChest(
			VirtualCorridorState state,
			XoroshiroSimulator random,
			List<CorridorEvent> events,
			int section,
			int x,
			int y,
			int z
	) {
		int chance = random.nextInt(100);
		events.add(CorridorEvent.at(
				CorridorEventType.MINECART_CHANCE_ROLL,
				x,
				y,
				z,
				random.drawCount(),
				"nextInt(100)=" + chance));
		if (chance != 0) {
			return new MinecartChestAttempt(new LocalBlockPos(x, y, z), section, chance, false, null, null, random.drawCount());
		}

		boolean legal = state.isGenerationInside(x, y, z) && state.getBlock(x, y, z).isAir() && !state.getBlock(x, y - 1, z).isAir();
		if (!legal) {
			events.add(CorridorEvent.at(
					CorridorEventType.MINECART_SACRIFICED,
					x,
					y,
					z,
					random.drawCount(),
					"position rejected before rail orientation and loot seed"));
			return new MinecartChestAttempt(new LocalBlockPos(x, y, z), section, chance, false, null, null, random.drawCount());
		}

		boolean northSouth = random.nextBoolean();
		long lootSeed = random.nextLong();
		events.add(CorridorEvent.at(
				CorridorEventType.MINECART_LOOT_SEED,
				x,
				y,
				z,
				random.drawCount(),
				"railNorthSouth=" + northSouth + ", lootSeed=" + lootSeed));
		return new MinecartChestAttempt(new LocalBlockPos(x, y, z), section, chance, true, northSouth, lootSeed, random.drawCount());
	}
}
