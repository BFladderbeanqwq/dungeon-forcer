package hackerrouter.mineshaftforcer.simulation;

@FunctionalInterface
public interface LootSeedPredicate {
	boolean test(long lootSeed);
}
