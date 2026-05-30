package hackerrouter.mineshaftforcer.simulation;

public record CorridorEvent(CorridorEventType type, LocalBlockPos pos, long drawCountAfter, String detail) {
	public static CorridorEvent at(CorridorEventType type, int x, int y, int z, long drawCountAfter, String detail) {
		return new CorridorEvent(type, new LocalBlockPos(x, y, z), drawCountAfter, detail);
	}
}
