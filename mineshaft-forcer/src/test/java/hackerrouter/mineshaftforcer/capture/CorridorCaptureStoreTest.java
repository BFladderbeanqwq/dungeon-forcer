package hackerrouter.mineshaftforcer.capture;

import hackerrouter.mineshaftforcer.render.CorridorOrientation;
import hackerrouter.mineshaftforcer.simulation.VirtualCorridorState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorridorCaptureStoreTest {
	@AfterEach
	void clearStore() {
		CorridorCaptureStore.clear();
	}

	@Test
	void assignsIdsAndReturnsLatestFirst() {
		CapturedCorridor first = CorridorCaptureStore.add(capture(0));
		CapturedCorridor second = CorridorCaptureStore.add(capture(0));

		assertTrue(second.id() > first.id());
		assertEquals(second.id(), CorridorCaptureStore.latest(2).getFirst().id());
	}

	@Test
	void canLookupAndClearCapture() {
		CapturedCorridor capture = CorridorCaptureStore.add(capture(0));

		assertTrue(CorridorCaptureStore.get(capture.id()).isPresent());
		CorridorCaptureStore.clear();
		assertTrue(CorridorCaptureStore.get(capture.id()).isEmpty());
	}

	private static CapturedCorridor capture(int id) {
		return new CapturedCorridor(
				id,
				10,
				64,
				20,
				12,
				67,
				44,
				CorridorOrientation.SOUTH,
				5,
				false,
				false,
				false,
				1L,
				2L,
				VirtualCorridorState.northSouthCorridor(24, 64));
	}
}
