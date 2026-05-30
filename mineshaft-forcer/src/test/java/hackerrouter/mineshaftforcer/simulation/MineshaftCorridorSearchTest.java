package hackerrouter.mineshaftforcer.simulation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MineshaftCorridorSearchTest {
	@Test
	void searchFindsMutationCombinationForWantedLootSeed() {
		CorridorConfig config = new CorridorConfig(1, false, false, false);
		VirtualCorridorState baseState = VirtualCorridorState.northSouthCorridor(config.length(), 64);
		baseState.fillBox(0, -1, 0, 2, -1, config.length(), BlockKind.SOLID);

		List<CorridorMutation> candidates = new ArrayList<>();
		candidates.addAll(MineshaftCorridorSearch.floorRemovalCandidates(config));
		candidates.addAll(MineshaftCorridorSearch.supportRoofCandidates(config));
		candidates.addAll(MineshaftCorridorSearch.cobwebShortCircuitCandidates(config));

		SearchFixture fixture = findSearchableTarget(config, baseState, candidates);
		CorridorSearchResult target = fixture.result();
		long wantedLootSeed = target.firstSpawnedMinecart().lootTableSeed();

		List<CorridorSearchResult> results = new MineshaftCorridorSearch().search(
				config,
				baseState,
				fixture.seedLo(),
				2L,
				candidates,
				3,
				1,
				seed -> seed == wantedLootSeed);

		assertFalse(results.isEmpty());
		assertEquals(wantedLootSeed, results.getFirst().firstSpawnedMinecart().lootTableSeed());
	}

	@Test
	void defaultCandidatesIncludeFloorRemovalAndSupportRoofControls() {
		CorridorConfig config = new CorridorConfig(2, false, false, false);

		List<CorridorMutation> candidates = new ArrayList<>();
		candidates.addAll(MineshaftCorridorSearch.floorRemovalCandidates(config));
		candidates.addAll(MineshaftCorridorSearch.supportRoofCandidates(config));
		candidates.addAll(MineshaftCorridorSearch.cobwebShortCircuitCandidates(config));

		assertEquals(26, candidates.size());
		assertFalse(candidates.stream().noneMatch(mutation -> mutation.pos().y() == -1 && mutation.kind() == BlockKind.AIR));
		assertFalse(candidates.stream().noneMatch(mutation -> mutation.pos().y() == 3 && mutation.kind() == BlockKind.SOLID));
		assertFalse(candidates.stream().noneMatch(CorridorMutation::changesOceanFloorHeight));
	}

	private static SearchFixture findSearchableTarget(
			CorridorConfig config,
			VirtualCorridorState baseState,
			List<CorridorMutation> candidates
	) {
		MineshaftCorridorSearch search = new MineshaftCorridorSearch();
		for (long seedLo = 1L; seedLo < 10_000L; seedLo++) {
			List<CorridorSearchResult> results = search.search(
					config,
					baseState,
					seedLo,
					2L,
					candidates,
					3,
					1,
					seed -> true);
			if (!results.isEmpty()) {
				return new SearchFixture(seedLo, results.getFirst());
			}
		}
		throw new AssertionError("No search target found");
	}

	private record SearchFixture(long seedLo, CorridorSearchResult result) {
	}
}
