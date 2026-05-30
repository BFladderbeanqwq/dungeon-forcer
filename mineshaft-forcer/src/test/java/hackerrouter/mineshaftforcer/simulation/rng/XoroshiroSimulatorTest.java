package hackerrouter.mineshaftforcer.simulation.rng;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XoroshiroSimulatorTest {
	@Test
	void nextLongMatchesMojangXoroshiro128PlusPlusSequence() {
		XoroshiroSimulator random = new XoroshiroSimulator(1L, 2L);

		assertEquals(393217L, random.nextLong());
		assertEquals(669327710093319L, random.nextLong());
		assertEquals(1732421326133921491L, random.nextLong());
		assertEquals(-7051953992050424633L, random.nextLong());
		assertEquals(-8891291296936358940L, random.nextLong());
		assertEquals(5L, random.drawCount());
	}

	@Test
	void zeroSeedPairUsesMojangFallbackConstants() {
		XoroshiroSimulator random = new XoroshiroSimulator(0L, 0L);

		assertEquals(6807859099481836695L, random.nextLong());
		assertEquals(1L, random.drawCount());
	}

	@Test
	void snapshotRestoresStateAndDrawCount() {
		XoroshiroSimulator random = new XoroshiroSimulator(1L, 2L);
		random.nextLong();
		XoroshiroSimulator.Snapshot snapshot = random.snapshot();
		long afterSnapshot = random.nextLong();

		random.nextLong();
		random.restore(snapshot);

		assertEquals(1L, random.drawCount());
		assertEquals(afterSnapshot, random.nextLong());
		assertEquals(2L, random.drawCount());
	}

	@Test
	void publicMethodsTrackUnderlyingLongDraws() {
		XoroshiroSimulator random = new XoroshiroSimulator(1L, 2L);

		random.nextInt();
		random.nextFloat();
		random.nextBoolean();
		random.nextDouble();
		random.consumeCount(3);

		assertEquals(7L, random.drawCount());
	}
}
