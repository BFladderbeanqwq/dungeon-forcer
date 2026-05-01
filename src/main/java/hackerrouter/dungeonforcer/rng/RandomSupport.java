package hackerrouter.dungeonforcer.rng;

public class RandomSupport {

    public static final long GOLDEN_RATIO_64 = -7046029254386353131L;

    public static final long SILVER_RATIO_64 = 7640891576956012809L;

    public static long mixStafford13(long z) {
        z = (z ^ (z >>> 30)) * (-4658895280553007687L);
        z = (z ^ (z >>> 27)) * (-7723592293110705685L);
        return z ^ (z >>> 31);
    }

    public static long[] upgradeSeedTo128bitUnmixed(long legacySeed) {
        long lowBits = legacySeed ^ SILVER_RATIO_64;
        long highBits = lowBits + GOLDEN_RATIO_64;
        return new long[]{lowBits, highBits};
    }

    public static long[] upgradeSeedTo128bit(long legacySeed) {
        long[] unmixed = upgradeSeedTo128bitUnmixed(legacySeed);
        return new long[]{mixStafford13(unmixed[0]), mixStafford13(unmixed[1])};
    }
}
