package hackerrouter.mineshaftforcer.simulation.rng;

public final class XoroshiroSimulator {
	private static final float FLOAT_UNIT = 5.9604645E-8F;
	private static final double DOUBLE_UNIT = 1.110223E-16F;

	private final Xoroshiro128PlusPlus generator;
	private long drawCount;

	public XoroshiroSimulator(long legacySeed) {
		this(RandomSupport.upgradeSeedTo128bit(legacySeed));
	}

	public XoroshiroSimulator(long seedLo, long seedHi) {
		this.generator = new Xoroshiro128PlusPlus(seedLo, seedHi);
	}

	public XoroshiroSimulator(RandomSupport.Seed128bit seed) {
		this.generator = new Xoroshiro128PlusPlus(seed);
	}

	public int nextInt() {
		return (int)nextLong();
	}

	public int nextInt(int bound) {
		if (bound <= 0) {
			throw new IllegalArgumentException("Bound must be positive");
		}

		long randomBits = Integer.toUnsignedLong(nextInt());
		long multipliedRandomBits = randomBits * bound;
		long fractionalPart = multipliedRandomBits & 4294967295L;
		if (fractionalPart < bound) {
			for (int unbiasedBucketsStartIndex = Integer.remainderUnsigned(~bound + 1, bound);
				 fractionalPart < unbiasedBucketsStartIndex;
				 fractionalPart = multipliedRandomBits & 4294967295L) {
				randomBits = Integer.toUnsignedLong(nextInt());
				multipliedRandomBits = randomBits * bound;
			}
		}

		return (int)(multipliedRandomBits >> 32);
	}

	public long nextLong() {
		this.drawCount++;
		return this.generator.nextLong();
	}

	public boolean nextBoolean() {
		return (nextLong() & 1L) != 0L;
	}

	public float nextFloat() {
		return (float)nextBits(24) * FLOAT_UNIT;
	}

	public double nextDouble() {
		return nextBits(53) * DOUBLE_UNIT;
	}

	public void consumeCount(int rounds) {
		for (int i = 0; i < rounds; i++) {
			nextLong();
		}
	}

	public long drawCount() {
		return this.drawCount;
	}

	public Snapshot snapshot() {
		return new Snapshot(this.generator.seedLo(), this.generator.seedHi(), this.drawCount);
	}

	public void restore(Snapshot snapshot) {
		this.generator.restore(snapshot.seedLo(), snapshot.seedHi());
		this.drawCount = snapshot.drawCount();
	}

	private long nextBits(int bits) {
		return nextLong() >>> 64 - bits;
	}

	public record Snapshot(long seedLo, long seedHi, long drawCount) {
	}
}
