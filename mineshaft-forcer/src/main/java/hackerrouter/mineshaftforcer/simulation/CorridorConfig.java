package hackerrouter.mineshaftforcer.simulation;

public record CorridorConfig(int numSections, boolean hasRails, boolean spiderCorridor, boolean hasPlacedSpider) {
	public CorridorConfig {
		if (numSections < 1) {
			throw new IllegalArgumentException("numSections must be positive");
		}
	}

	public int length() {
		return this.numSections * 5 - 1;
	}
}
