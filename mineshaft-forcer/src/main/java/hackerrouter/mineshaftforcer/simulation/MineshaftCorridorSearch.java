package hackerrouter.mineshaftforcer.simulation;

import java.util.ArrayList;
import java.util.List;

public final class MineshaftCorridorSearch {
	private final MineshaftCorridorSimulator simulator;

	public MineshaftCorridorSearch() {
		this(new MineshaftCorridorSimulator());
	}

	public MineshaftCorridorSearch(MineshaftCorridorSimulator simulator) {
		this.simulator = simulator;
	}

	public List<CorridorSearchResult> search(
			CorridorConfig config,
			VirtualCorridorState baseState,
			long seedLo,
			long seedHi,
			List<CorridorMutation> candidates,
			int maxMutations,
			int maxResults,
			LootSeedPredicate lootSeedPredicate
	) {
		if (maxMutations < 0) {
			throw new IllegalArgumentException("maxMutations must be non-negative");
		}
		if (maxResults < 1) {
			throw new IllegalArgumentException("maxResults must be positive");
		}

		List<CorridorSearchResult> results = new ArrayList<>();
		searchRecursive(config, baseState, seedLo, seedHi, candidates, maxMutations, maxResults, lootSeedPredicate, 0, new ArrayList<>(), results);
		return List.copyOf(results);
	}

	public static List<CorridorMutation> floorRemovalCandidates(CorridorConfig config) {
		List<CorridorMutation> candidates = new ArrayList<>();
		for (int section = 0; section < config.numSections(); section++) {
			int z = 2 + section * 5;
			candidates.add(CorridorMutation.setBlock(2, -1, z - 1, BlockKind.AIR));
			candidates.add(CorridorMutation.setBlock(0, -1, z + 1, BlockKind.AIR));
		}
		return List.copyOf(candidates);
	}

	public static List<CorridorMutation> supportRoofCandidates(CorridorConfig config) {
		List<CorridorMutation> candidates = new ArrayList<>();
		for (int section = 0; section < config.numSections(); section++) {
			int z = 2 + section * 5;
			candidates.add(CorridorMutation.setBlock(0, 3, z, BlockKind.SOLID));
			candidates.add(CorridorMutation.setBlock(1, 3, z, BlockKind.SOLID));
			candidates.add(CorridorMutation.setBlock(2, 3, z, BlockKind.SOLID));
		}
		return List.copyOf(candidates);
	}

	public static List<CorridorMutation> cobwebShortCircuitCandidates(CorridorConfig config) {
		List<CorridorMutation> candidates = new ArrayList<>();
		for (int section = 0; section < config.numSections(); section++) {
			int z = 2 + section * 5;
			addCobwebShortCircuitCandidate(candidates, 0, 2, z - 1);
			addCobwebShortCircuitCandidate(candidates, 2, 2, z - 1);
			addCobwebShortCircuitCandidate(candidates, 0, 2, z + 1);
			addCobwebShortCircuitCandidate(candidates, 2, 2, z + 1);
			addCobwebShortCircuitCandidate(candidates, 0, 2, z - 2);
			addCobwebShortCircuitCandidate(candidates, 2, 2, z - 2);
			addCobwebShortCircuitCandidate(candidates, 0, 2, z + 2);
			addCobwebShortCircuitCandidate(candidates, 2, 2, z + 2);
		}
		return List.copyOf(candidates);
	}

	private void searchRecursive(
			CorridorConfig config,
			VirtualCorridorState baseState,
			long seedLo,
			long seedHi,
			List<CorridorMutation> candidates,
			int remainingMutations,
			int maxResults,
			LootSeedPredicate lootSeedPredicate,
			int nextCandidate,
			List<CorridorMutation> selected,
			List<CorridorSearchResult> results
	) {
		if (results.size() >= maxResults) {
			return;
		}

		evaluate(config, baseState, seedLo, seedHi, selected, lootSeedPredicate).ifPresent(results::add);
		if (remainingMutations == 0) {
			return;
		}

		for (int i = nextCandidate; i < candidates.size() && results.size() < maxResults; i++) {
			selected.add(candidates.get(i));
			searchRecursive(
					config,
					baseState,
					seedLo,
					seedHi,
					candidates,
					remainingMutations - 1,
					maxResults,
					lootSeedPredicate,
					i + 1,
					selected,
					results);
			selected.removeLast();
		}
	}

	private java.util.Optional<CorridorSearchResult> evaluate(
			CorridorConfig config,
			VirtualCorridorState baseState,
			long seedLo,
			long seedHi,
			List<CorridorMutation> selected,
			LootSeedPredicate lootSeedPredicate
	) {
		VirtualCorridorState state = baseState.copy();
		for (CorridorMutation mutation : selected) {
			LocalBlockPos pos = mutation.pos();
			if (mutation.changesBlock()) {
				state.setBlock(pos.x(), pos.y(), pos.z(), mutation.kind());
			}
			if (mutation.changesOceanFloorHeight()) {
				state.setOceanFloorHeight(pos.x(), pos.z(), mutation.oceanFloorHeight());
			}
		}

		CorridorSimulationResult result = this.simulator.simulate(config, state, seedLo, seedHi);
		MinecartChestAttempt firstMinecart = result.firstSpawnedMinecart();
		if (firstMinecart != null && lootSeedPredicate.test(firstMinecart.lootTableSeed())) {
			return java.util.Optional.of(new CorridorSearchResult(List.copyOf(selected), result));
		}
		return java.util.Optional.empty();
	}

	private static void addCobwebShortCircuitCandidate(List<CorridorMutation> candidates, int x, int y, int z) {
		candidates.add(CorridorMutation.oceanFloor(x, y, z, y + 1));
	}
}
