package hackerrouter.mineshaftforcer.simulation;

import java.util.List;

public record CorridorSimulationResult(
		CorridorConfig config,
		long finalDrawCount,
		List<CorridorEvent> events,
		List<MinecartChestAttempt> minecartAttempts
) {
	public MinecartChestAttempt firstSpawnedMinecart() {
		return this.minecartAttempts.stream()
				.filter(MinecartChestAttempt::spawned)
				.findFirst()
				.orElse(null);
	}
}
