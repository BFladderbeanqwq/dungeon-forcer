package hackerrouter.mineshaftforcer.render;

import java.util.Locale;

public enum CorridorOrientation {
	NORTH,
	SOUTH,
	WEST,
	EAST;

	public static CorridorOrientation parse(String value) {
		return CorridorOrientation.valueOf(value.trim().toUpperCase(Locale.ROOT));
	}
}
