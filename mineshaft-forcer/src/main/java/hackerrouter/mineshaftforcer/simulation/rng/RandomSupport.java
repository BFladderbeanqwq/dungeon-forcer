package hackerrouter.mineshaftforcer.simulation.rng;

public final class RandomSupport {
	public static final long GOLDEN_RATIO_64 = -7046029254386353131L;
	public static final long SILVER_RATIO_64 = 7640891576956012809L;

	private RandomSupport() {
	}

	public static long mixStafford13(long z) {
		z = (z ^ z >>> 30) * -4658895280553007687L;
		z = (z ^ z >>> 27) * -7723592293110705685L;
		return z ^ z >>> 31;
	}

	public static Seed128bit upgradeSeedTo128bitUnmixed(long legacySeed) {
		long lowBits = legacySeed ^ SILVER_RATIO_64;
		long highBits = lowBits + GOLDEN_RATIO_64;
		return new Seed128bit(lowBits, highBits);
	}

	public static Seed128bit upgradeSeedTo128bit(long legacySeed) {
		return upgradeSeedTo128bitUnmixed(legacySeed).mixed();
	}

	public record Seed128bit(long seedLo, long seedHi) {
		public Seed128bit mixed() {
			return new Seed128bit(RandomSupport.mixStafford13(this.seedLo), RandomSupport.mixStafford13(this.seedHi));
		}
	}
}
