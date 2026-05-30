package hackerrouter.mineshaftforcer.capture;

import hackerrouter.mineshaftforcer.render.CorridorOrientation;
import hackerrouter.mineshaftforcer.simulation.VirtualCorridorState;

public record CapturedCorridor(
		int id,
		int minX,
		int minY,
		int minZ,
		int maxX,
		int maxY,
		int maxZ,
		CorridorOrientation orientation,
		int numSections,
		boolean hasRails,
		boolean spiderCorridor,
		boolean hasPlacedSpider,
		long seedLo,
		long seedHi,
		VirtualCorridorState initialState
) {
	public int length() {
		return this.numSections * 5 - 1;
	}

	public String summary() {
		return "#" + this.id
				+ " origin=(" + this.minX + ", " + this.minY + ", " + this.minZ + ")"
				+ " box=(" + this.minX + "," + this.minY + "," + this.minZ + " -> " + this.maxX + "," + this.maxY + "," + this.maxZ + ")"
				+ " orientation=" + this.orientation
				+ " sections=" + this.numSections
				+ " rails=" + this.hasRails
				+ " spider=" + this.spiderCorridor
				+ " seedLo=" + this.seedLo
				+ " seedHi=" + this.seedHi;
	}
}
