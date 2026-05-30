package hackerrouter.mineshaftforcer.simulation;

public enum BlockKind {
	AIR,
	NON_SOLID,
	SOLID;

	public boolean isAir() {
		return this == AIR;
	}

	public boolean isFaceSturdy() {
		return this == SOLID;
	}
}
