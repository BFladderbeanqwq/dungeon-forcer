package hackerrouter.mineshaftforcer.simulation.rng;

public final class Xoroshiro128PlusPlus {
	private long seedLo;
	private long seedHi;

	public Xoroshiro128PlusPlus(RandomSupport.Seed128bit seed) {
		this(seed.seedLo(), seed.seedHi());
	}

	public Xoroshiro128PlusPlus(long seedLo, long seedHi) {
		this.seedLo = seedLo;
		this.seedHi = seedHi;
		if ((this.seedLo | this.seedHi) == 0L) {
			this.seedLo = RandomSupport.GOLDEN_RATIO_64;
			this.seedHi = RandomSupport.SILVER_RATIO_64;
		}
	}

	public long nextLong() {
		long s0 = this.seedLo;
		long s1 = this.seedHi;
		long result = Long.rotateLeft(s0 + s1, 17) + s0;
		s1 ^= s0;
		this.seedLo = Long.rotateLeft(s0, 49) ^ s1 ^ s1 << 21;
		this.seedHi = Long.rotateLeft(s1, 28);
		return result;
	}

	public long seedLo() {
		return this.seedLo;
	}

	public long seedHi() {
		return this.seedHi;
	}

	public void restore(long seedLo, long seedHi) {
		this.seedLo = seedLo;
		this.seedHi = seedHi;
	}
}
