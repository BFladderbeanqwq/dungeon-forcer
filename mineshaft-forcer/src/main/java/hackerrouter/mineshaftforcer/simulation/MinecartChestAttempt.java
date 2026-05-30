package hackerrouter.mineshaftforcer.simulation;

public record MinecartChestAttempt(
		LocalBlockPos pos,
		int section,
		int chanceRoll,
		boolean legalPosition,
		Boolean railNorthSouth,
		Long lootTableSeed,
		long drawCountAfter
) {
	public boolean spawned() {
		return this.lootTableSeed != null;
	}
}
