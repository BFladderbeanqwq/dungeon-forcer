package hackerrouter.mineshaftforcer.capture;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class CorridorCaptureStore {
	private static final int MAX_CAPTURES = 256;
	private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
	private static final ConcurrentMap<Integer, CapturedCorridor> CAPTURES = new ConcurrentHashMap<>();

	private CorridorCaptureStore() {
	}

	public static CapturedCorridor add(CapturedCorridor capture) {
		int id = NEXT_ID.getAndIncrement();
		CapturedCorridor stored = new CapturedCorridor(
				id,
				capture.minX(),
				capture.minY(),
				capture.minZ(),
				capture.maxX(),
				capture.maxY(),
				capture.maxZ(),
				capture.orientation(),
				capture.numSections(),
				capture.hasRails(),
				capture.spiderCorridor(),
				capture.hasPlacedSpider(),
				capture.seedLo(),
				capture.seedHi(),
				capture.initialState().copy());
		CAPTURES.put(id, stored);
		trim();
		return stored;
	}

	public static Optional<CapturedCorridor> get(int id) {
		return Optional.ofNullable(CAPTURES.get(id));
	}

	public static List<CapturedCorridor> latest(int limit) {
		return CAPTURES.values().stream()
				.sorted(Comparator.comparingInt(CapturedCorridor::id).reversed())
				.limit(limit)
				.toList();
	}

	public static void clear() {
		CAPTURES.clear();
	}

	private static void trim() {
		if (CAPTURES.size() <= MAX_CAPTURES) {
			return;
		}
		CAPTURES.keySet().stream()
				.sorted()
				.limit(CAPTURES.size() - MAX_CAPTURES)
				.toList()
				.forEach(CAPTURES::remove);
	}
}
