package hackerrouter.mineshaftforcer.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineshaftCorridorSimulatorTest {
	@Test
	void simulatesBaselineRngConsumptionForOneOpenSection() {
		CorridorConfig config = new CorridorConfig(1, false, false, false);
		VirtualCorridorState state = floorOnlyState(config);

		CorridorSimulationResult result = new MineshaftCorridorSimulator().simulate(config, state, 1L, 2L);

		assertEquals(15, count(result, CorridorEventType.GENERATE_MAYBE_BOX_ROLL));
		assertEquals(8, count(result, CorridorEventType.COBWEB_ROLL));
		assertEquals(2, count(result, CorridorEventType.MINECART_CHANCE_ROLL));
		assertEquals(25L, result.finalDrawCount());
	}

	@Test
	void supportRoofChangesRngConsumptionPath() {
		CorridorConfig config = new CorridorConfig(1, false, false, false);
		VirtualCorridorState state = floorOnlyState(config);
		state.fillBox(0, 3, 2, 2, 3, 2, BlockKind.SOLID);

		CorridorSimulationResult result = new MineshaftCorridorSimulator().simulate(config, state, 1L, 2L);

		assertEquals(1, count(result, CorridorEventType.SUPPORT_STYLE_ROLL));
		assertTrue(result.finalDrawCount() >= 26L);
	}

	@Test
	void loweredOceanFloorShortCircuitsCobwebFloatRoll() {
		CorridorConfig config = new CorridorConfig(1, false, false, false);
		VirtualCorridorState state = floorOnlyState(config);
		state.setOceanFloorHeight(0, 1, 3);

		CorridorSimulationResult result = new MineshaftCorridorSimulator().simulate(config, state, 1L, 2L);

		assertEquals(1, count(result, CorridorEventType.COBWEB_INTERIOR_SKIPPED));
		assertEquals(7, count(result, CorridorEventType.COBWEB_ROLL));
		assertEquals(24L, result.finalDrawCount());
	}

	@Test
	void blockAtCobwebTargetDoesNotShortCircuitInteriorCheck() {
		CorridorConfig config = new CorridorConfig(1, false, false, false);
		VirtualCorridorState state = floorOnlyState(config);
		state.setBlock(0, 2, 1, BlockKind.SOLID);

		CorridorSimulationResult result = new MineshaftCorridorSimulator().simulate(config, state, 1L, 2L);

		assertEquals(0, count(result, CorridorEventType.COBWEB_INTERIOR_SKIPPED));
		assertEquals(8, count(result, CorridorEventType.COBWEB_ROLL));
		assertEquals(25L, result.finalDrawCount());
	}

	@Test
	void floorRemovalSacrificesMinecartAndSkipsRailAndLootSeedDraws() {
		CorridorConfig config = new CorridorConfig(1, false, false, false);
		long seedLo = findSeedLoWithFirstMinecartHit(config);
		VirtualCorridorState base = floorOnlyState(config);
		CorridorSimulationResult spawned = new MineshaftCorridorSimulator().simulate(config, base, seedLo, 2L);
		MinecartChestAttempt firstSpawned = spawned.firstSpawnedMinecart();
		assertNotNull(firstSpawned);

		VirtualCorridorState sacrificedState = floorOnlyState(config);
		LocalBlockPos pos = firstSpawned.pos();
		sacrificedState.setBlock(pos.x(), pos.y() - 1, pos.z(), BlockKind.AIR);
		CorridorSimulationResult sacrificed = new MineshaftCorridorSimulator().simulate(config, sacrificedState, seedLo, 2L);

		assertTrue(sacrificed.events().stream().anyMatch(event -> event.type() == CorridorEventType.MINECART_SACRIFICED));
		assertFalse(sacrificed.minecartAttempts().getFirst().spawned());
		assertEquals(spawned.finalDrawCount() - 2L, sacrificed.finalDrawCount());
	}

	@Test
	void generationBoundsCanSacrificeMinecartBeforeRailAndLootSeedDraws() {
		CorridorConfig config = new CorridorConfig(1, false, false, false);
		VirtualCorridorState clipped = floorOnlyState(config);
		clipped.setGenerationBounds(0, -1, 2, 2, 3, config.length());
		long seedLo = findSeedLoWithFirstMinecartChanceHit(config, clipped);

		CorridorSimulationResult result = new MineshaftCorridorSimulator().simulate(config, clipped, seedLo, 2L);
		MinecartChestAttempt first = result.minecartAttempts().getFirst();

		assertEquals(0, first.chanceRoll());
		assertFalse(first.legalPosition());
		assertFalse(first.spawned());
		assertEquals(1, count(result, CorridorEventType.MINECART_SACRIFICED));
	}

	private static long count(CorridorSimulationResult result, CorridorEventType type) {
		return result.events().stream().filter(event -> event.type() == type).count();
	}

	private static VirtualCorridorState floorOnlyState(CorridorConfig config) {
		VirtualCorridorState state = VirtualCorridorState.northSouthCorridor(config.length(), 64);
		state.fillBox(0, -1, 0, 2, -1, config.length(), BlockKind.SOLID);
		return state;
	}

	private static long findSeedLoWithFirstMinecartHit(CorridorConfig config) {
		MineshaftCorridorSimulator simulator = new MineshaftCorridorSimulator();
		for (long seedLo = 1L; seedLo < 10_000L; seedLo++) {
			CorridorSimulationResult result = simulator.simulate(config, floorOnlyState(config), seedLo, 2L);
			List<MinecartChestAttempt> attempts = result.minecartAttempts();
			if (!attempts.isEmpty() && attempts.getFirst().spawned()) {
				return seedLo;
			}
		}
		throw new AssertionError("No deterministic minecart hit seed found");
	}

	private static long findSeedLoWithFirstMinecartChanceHit(CorridorConfig config, VirtualCorridorState state) {
		MineshaftCorridorSimulator simulator = new MineshaftCorridorSimulator();
		for (long seedLo = 1L; seedLo < 10_000L; seedLo++) {
			CorridorSimulationResult result = simulator.simulate(config, state, seedLo, 2L);
			List<MinecartChestAttempt> attempts = result.minecartAttempts();
			if (!attempts.isEmpty() && attempts.getFirst().chanceRoll() == 0) {
				return seedLo;
			}
		}
		throw new AssertionError("No deterministic minecart chance hit seed found");
	}
}
