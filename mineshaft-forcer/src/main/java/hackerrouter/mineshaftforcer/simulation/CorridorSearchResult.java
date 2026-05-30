package hackerrouter.mineshaftforcer.simulation;

import java.util.List;

public record CorridorSearchResult(List<CorridorMutation> mutations, CorridorSimulationResult simulationResult) {
	public MinecartChestAttempt firstSpawnedMinecart() {
		return this.simulationResult.firstSpawnedMinecart();
	}
}
