package hackerrouter.mineshaftforcer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import hackerrouter.mineshaftforcer.capture.CapturedCorridor;
import hackerrouter.mineshaftforcer.capture.CorridorCaptureStore;
import hackerrouter.mineshaftforcer.loot.AbandonedMineshaftLootEvaluator;
import hackerrouter.mineshaftforcer.render.BlueprintRenderMapper;
import hackerrouter.mineshaftforcer.render.BlueprintRenderBlock;
import hackerrouter.mineshaftforcer.render.BlueprintRenderKind;
import hackerrouter.mineshaftforcer.render.BlueprintRenderQueue;
import hackerrouter.mineshaftforcer.render.CorridorOrientation;
import hackerrouter.mineshaftforcer.simulation.BlockKind;
import hackerrouter.mineshaftforcer.simulation.CorridorConfig;
import hackerrouter.mineshaftforcer.simulation.CorridorMutation;
import hackerrouter.mineshaftforcer.simulation.CorridorSearchResult;
import hackerrouter.mineshaftforcer.simulation.MineshaftCorridorSearch;
import hackerrouter.mineshaftforcer.simulation.VirtualCorridorState;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class MineshaftForcerCommands {
	private MineshaftForcerCommands() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("mineshaftforcer")
				.executes(context -> showStatus(context.getSource()))
				.then(debugCommand())
				.then(evalCommand())
				.then(captureCommand())
				.then(searchCommand())
				.then(renderCommand()));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> debugCommand() {
		return literal("debug")
				.then(literal("rng")
						.executes(context -> notImplemented(context.getSource(), "RNG debug")))
				.then(literal("render_test")
						.executes(context -> renderTest(context.getSource())));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> evalCommand() {
		return literal("eval")
				.then(literal("abandoned_mineshaft")
						.then(argument("lootSeed", LongArgumentType.longArg())
								.then(argument("itemId", StringArgumentType.greedyString())
										.executes(context -> evaluateAbandonedMineshaft(
												context.getSource(),
												LongArgumentType.getLong(context, "lootSeed"),
												StringArgumentType.getString(context, "itemId"))))));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> searchCommand() {
		return literal("search")
				.executes(context -> notImplemented(context.getSource(), "Mineshaft search"))
				.then(corridorCommand())
				.then(corridorPreviewCommand())
				.then(corridorPreviewCaptureCommand());
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> captureCommand() {
		return literal("capture")
				.then(literal("list")
						.executes(context -> listCaptures(context.getSource(), 10))
						.then(argument("limit", IntegerArgumentType.integer(1, 50))
								.executes(context -> listCaptures(
										context.getSource(),
										IntegerArgumentType.getInteger(context, "limit")))))
				.then(literal("latest")
						.executes(context -> latestCapture(context.getSource())))
				.then(literal("clear")
						.executes(context -> clearCaptures(context.getSource())));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> corridorCommand() {
		RequiredArgumentBuilder<FabricClientCommandSource, String> itemId = argument("itemId", StringArgumentType.greedyString())
				.executes(context -> searchCorridor(
						context.getSource(),
						IntegerArgumentType.getInteger(context, "sections"),
						LongArgumentType.getLong(context, "seedLo"),
						LongArgumentType.getLong(context, "seedHi"),
						IntegerArgumentType.getInteger(context, "maxMutations"),
						IntegerArgumentType.getInteger(context, "maxResults"),
						StringArgumentType.getString(context, "itemId")));
		RequiredArgumentBuilder<FabricClientCommandSource, Integer> maxResults = argument("maxResults", IntegerArgumentType.integer(1, 20)).then(itemId);
		RequiredArgumentBuilder<FabricClientCommandSource, Integer> maxMutations = argument("maxMutations", IntegerArgumentType.integer(0, 8)).then(maxResults);
		RequiredArgumentBuilder<FabricClientCommandSource, Long> seedHi = argument("seedHi", LongArgumentType.longArg()).then(maxMutations);
		RequiredArgumentBuilder<FabricClientCommandSource, Long> seedLo = argument("seedLo", LongArgumentType.longArg()).then(seedHi);
		RequiredArgumentBuilder<FabricClientCommandSource, Integer> sections = argument("sections", IntegerArgumentType.integer(1, 16)).then(seedLo);
		return literal("corridor").then(sections);
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> corridorPreviewCommand() {
		RequiredArgumentBuilder<FabricClientCommandSource, String> itemId = argument("itemId", StringArgumentType.greedyString())
				.executes(context -> searchCorridorPreview(
						context.getSource(),
						IntegerArgumentType.getInteger(context, "originX"),
						IntegerArgumentType.getInteger(context, "originY"),
						IntegerArgumentType.getInteger(context, "originZ"),
						CorridorOrientation.parse(StringArgumentType.getString(context, "orientation")),
						IntegerArgumentType.getInteger(context, "sections"),
						LongArgumentType.getLong(context, "seedLo"),
						LongArgumentType.getLong(context, "seedHi"),
						IntegerArgumentType.getInteger(context, "maxMutations"),
						StringArgumentType.getString(context, "itemId")));
		RequiredArgumentBuilder<FabricClientCommandSource, Integer> maxMutations = argument("maxMutations", IntegerArgumentType.integer(0, 8)).then(itemId);
		RequiredArgumentBuilder<FabricClientCommandSource, Long> seedHi = argument("seedHi", LongArgumentType.longArg()).then(maxMutations);
		RequiredArgumentBuilder<FabricClientCommandSource, Long> seedLo = argument("seedLo", LongArgumentType.longArg()).then(seedHi);
		RequiredArgumentBuilder<FabricClientCommandSource, Integer> sections = argument("sections", IntegerArgumentType.integer(1, 16)).then(seedLo);
		RequiredArgumentBuilder<FabricClientCommandSource, String> orientation = argument("orientation", StringArgumentType.word()).then(sections);
		RequiredArgumentBuilder<FabricClientCommandSource, Integer> originZ = argument("originZ", IntegerArgumentType.integer()).then(orientation);
		RequiredArgumentBuilder<FabricClientCommandSource, Integer> originY = argument("originY", IntegerArgumentType.integer()).then(originZ);
		RequiredArgumentBuilder<FabricClientCommandSource, Integer> originX = argument("originX", IntegerArgumentType.integer()).then(originY);
		return literal("corridor_preview").then(originX);
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> corridorPreviewCaptureCommand() {
		RequiredArgumentBuilder<FabricClientCommandSource, String> itemId = argument("itemId", StringArgumentType.greedyString())
				.executes(context -> searchCapturedCorridorPreview(
						context.getSource(),
						IntegerArgumentType.getInteger(context, "captureId"),
						IntegerArgumentType.getInteger(context, "maxMutations"),
						StringArgumentType.getString(context, "itemId")));
		RequiredArgumentBuilder<FabricClientCommandSource, Integer> maxMutations = argument("maxMutations", IntegerArgumentType.integer(0, 8)).then(itemId);
		RequiredArgumentBuilder<FabricClientCommandSource, Integer> captureId = argument("captureId", IntegerArgumentType.integer(1)).then(maxMutations);
		return literal("corridor_preview_capture").then(captureId);
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> renderCommand() {
		return literal("render")
				.then(literal("status")
						.executes(context -> renderStatus(context.getSource())))
				.then(literal("clear")
						.executes(context -> clearRender(context.getSource())));
	}

	private static int showStatus(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("[Mineshaft-Forcer] Loaded. Use capture list, eval, search corridor, or search corridor_preview."));
		return 1;
	}

	private static int notImplemented(FabricClientCommandSource source, String featureName) {
		source.sendFeedback(Component.literal("[Mineshaft-Forcer] " + featureName + " is reserved for a later phase."));
		return 1;
	}

	private static int evaluateAbandonedMineshaft(FabricClientCommandSource source, long lootSeed, String itemId) {
		ServerLevel level = integratedOverworld();
		if (level == null) {
			source.sendFeedback(Component.literal("[Mineshaft-Forcer] Vanilla loot evaluation requires an integrated singleplayer server."));
			return 0;
		}

		String normalizedItemId = itemId.trim();
		boolean contains = AbandonedMineshaftLootEvaluator.contains(level, lootSeed, normalizedItemId);
		String loot = AbandonedMineshaftLootEvaluator.describe(level, lootSeed);
		source.sendFeedback(Component.literal("[Mineshaft-Forcer] abandoned_mineshaft seed " + lootSeed + " -> " + loot));
		source.sendFeedback(Component.literal("[Mineshaft-Forcer] " + (contains ? "Contains " : "Does not contain ") + normalizedItemId + "."));
		return contains ? 1 : 0;
	}

	private static int listCaptures(FabricClientCommandSource source, int limit) {
		List<CapturedCorridor> captures = CorridorCaptureStore.latest(limit);
		if (captures.isEmpty()) {
			source.sendFeedback(Component.literal("[Mineshaft-Forcer] No captured mineshaft corridors yet. Generate or approach new chunks in singleplayer to collect captures."));
			return 0;
		}
		source.sendFeedback(Component.literal("[Mineshaft-Forcer] Latest captured corridors:"));
		for (CapturedCorridor capture : captures) {
			source.sendFeedback(Component.literal("[Mineshaft-Forcer] " + capture.summary()));
		}
		return captures.size();
	}

	private static int clearCaptures(FabricClientCommandSource source) {
		CorridorCaptureStore.clear();
		source.sendFeedback(Component.literal("[Mineshaft-Forcer] Cleared captured corridors."));
		return 1;
	}

	private static int latestCapture(FabricClientCommandSource source) {
		List<CapturedCorridor> captures = CorridorCaptureStore.latest(1);
		if (captures.isEmpty()) {
			source.sendFeedback(Component.literal("[Mineshaft-Forcer] No captured mineshaft corridors yet."));
			return 0;
		}
		source.sendFeedback(Component.literal("[Mineshaft-Forcer] Latest captured corridor: " + captures.getFirst().summary()));
		return captures.getFirst().id();
	}

	private static int searchCorridor(
			FabricClientCommandSource source,
			int sections,
			long seedLo,
			long seedHi,
			int maxMutations,
			int maxResults,
			String itemId
	) {
		ServerLevel level = integratedOverworld();
		if (level == null) {
			source.sendFeedback(Component.literal("[Mineshaft-Forcer] Search currently needs an integrated singleplayer server for vanilla loot evaluation."));
			return 0;
		}

		String normalizedItemId = itemId.trim();
		CorridorConfig config = new CorridorConfig(sections, false, false, false);
		VirtualCorridorState state = VirtualCorridorState.northSouthCorridor(config.length(), 64);
		state.fillBox(0, -1, 0, 2, -1, config.length(), BlockKind.SOLID);

		List<CorridorMutation> candidates = new ArrayList<>();
		candidates.addAll(MineshaftCorridorSearch.floorRemovalCandidates(config));
		candidates.addAll(MineshaftCorridorSearch.supportRoofCandidates(config));
		candidates.addAll(MineshaftCorridorSearch.cobwebShortCircuitCandidates(config));

		List<CorridorSearchResult> results = new MineshaftCorridorSearch().search(
				config,
				state,
				seedLo,
				seedHi,
				candidates,
				maxMutations,
				maxResults,
				lootSeed -> AbandonedMineshaftLootEvaluator.contains(level, lootSeed, normalizedItemId));

		if (results.isEmpty()) {
			source.sendFeedback(Component.literal("[Mineshaft-Forcer] No matching corridor mutation set found for " + normalizedItemId + "."));
			return 0;
		}

		source.sendFeedback(Component.literal("[Mineshaft-Forcer] Found " + results.size() + " matching corridor mutation set(s)."));
		int index = 1;
		for (CorridorSearchResult result : results) {
			long lootSeed = result.firstSpawnedMinecart().lootTableSeed();
			source.sendFeedback(Component.literal("[Mineshaft-Forcer] #" + index
					+ " lootSeed=" + lootSeed
					+ ", mutations=" + describeMutations(result.mutations())
					+ ", loot=" + AbandonedMineshaftLootEvaluator.describe(level, lootSeed)));
			index++;
		}
		return results.size();
	}

	private static int searchCorridorPreview(
			FabricClientCommandSource source,
			int originX,
			int originY,
			int originZ,
			CorridorOrientation orientation,
			int sections,
			long seedLo,
			long seedHi,
			int maxMutations,
			String itemId
	) {
		ServerLevel level = integratedOverworld();
		if (level == null) {
			source.sendFeedback(Component.literal("[Mineshaft-Forcer] Preview search currently needs an integrated singleplayer server for vanilla loot evaluation."));
			return 0;
		}

		String normalizedItemId = itemId.trim();
		CorridorConfig config = new CorridorConfig(sections, false, false, false);
		VirtualCorridorState state = VirtualCorridorState.northSouthCorridor(config.length(), 64);
		state.fillBox(0, -1, 0, 2, -1, config.length(), BlockKind.SOLID);
		List<CorridorMutation> candidates = new ArrayList<>();
		candidates.addAll(MineshaftCorridorSearch.floorRemovalCandidates(config));
		candidates.addAll(MineshaftCorridorSearch.supportRoofCandidates(config));
		candidates.addAll(MineshaftCorridorSearch.cobwebShortCircuitCandidates(config));

		List<CorridorSearchResult> results = new MineshaftCorridorSearch().search(
				config,
				state,
				seedLo,
				seedHi,
				candidates,
				maxMutations,
				1,
				lootSeed -> AbandonedMineshaftLootEvaluator.contains(level, lootSeed, normalizedItemId));

		if (results.isEmpty()) {
			BlueprintRenderQueue.clear();
			source.sendFeedback(Component.literal("[Mineshaft-Forcer] No previewable corridor mutation set found for " + normalizedItemId + "."));
			return 0;
		}

		CorridorSearchResult result = results.getFirst();
		BlueprintRenderQueue.replace(BlueprintRenderMapper.toWorldBlocks(result, originX, originY, originZ, orientation, config.length()));
		long lootSeed = result.firstSpawnedMinecart().lootTableSeed();
		source.sendFeedback(Component.literal("[Mineshaft-Forcer] Preview rendered at " + orientation
				+ " bounding-box origin (" + originX + ", " + originY + ", " + originZ + ")."));
		source.sendFeedback(Component.literal("[Mineshaft-Forcer] lootSeed=" + lootSeed
				+ ", mutations=" + describeMutations(result.mutations())
				+ ", loot=" + AbandonedMineshaftLootEvaluator.describe(level, lootSeed)));
		return 1;
	}

	private static int searchCapturedCorridorPreview(
			FabricClientCommandSource source,
			int captureId,
			int maxMutations,
			String itemId
	) {
		CapturedCorridor capture = CorridorCaptureStore.get(captureId).orElse(null);
		if (capture == null) {
			source.sendFeedback(Component.literal("[Mineshaft-Forcer] No captured corridor with id " + captureId + "."));
			return 0;
		}
		ServerLevel level = integratedOverworld();
		if (level == null) {
			source.sendFeedback(Component.literal("[Mineshaft-Forcer] Captured preview search needs an integrated singleplayer server for vanilla loot evaluation."));
			return 0;
		}

		String normalizedItemId = itemId.trim();
		CorridorConfig config = new CorridorConfig(capture.numSections(), capture.hasRails(), capture.spiderCorridor(), capture.hasPlacedSpider());
		VirtualCorridorState state = capture.initialState().copy();
		List<CorridorMutation> candidates = new ArrayList<>();
		candidates.addAll(MineshaftCorridorSearch.floorRemovalCandidates(config));
		candidates.addAll(MineshaftCorridorSearch.supportRoofCandidates(config));
		candidates.addAll(MineshaftCorridorSearch.cobwebShortCircuitCandidates(config));

		List<CorridorSearchResult> results = new MineshaftCorridorSearch().search(
				config,
				state,
				capture.seedLo(),
				capture.seedHi(),
				candidates,
				maxMutations,
				1,
				lootSeed -> AbandonedMineshaftLootEvaluator.contains(level, lootSeed, normalizedItemId));

		if (results.isEmpty()) {
			BlueprintRenderQueue.clear();
			source.sendFeedback(Component.literal("[Mineshaft-Forcer] Captured corridor #" + captureId + " produced no previewable mutation set for " + normalizedItemId + "."));
			return 0;
		}

		CorridorSearchResult result = results.getFirst();
		BlueprintRenderQueue.replace(BlueprintRenderMapper.toWorldBlocks(
				result,
				capture.minX(),
				capture.minY(),
				capture.minZ(),
				capture.orientation(),
				config.length()));
		long lootSeed = result.firstSpawnedMinecart().lootTableSeed();
		source.sendFeedback(Component.literal("[Mineshaft-Forcer] Previewing captured corridor " + capture.summary()));
		source.sendFeedback(Component.literal("[Mineshaft-Forcer] lootSeed=" + lootSeed
				+ ", mutations=" + describeMutations(result.mutations())
				+ ", loot=" + AbandonedMineshaftLootEvaluator.describe(level, lootSeed)));
		return 1;
	}

	private static int clearRender(FabricClientCommandSource source) {
		BlueprintRenderQueue.clear();
		source.sendFeedback(Component.literal("[Mineshaft-Forcer] Cleared blueprint preview."));
		return 1;
	}

	private static int renderStatus(FabricClientCommandSource source) {
		int count = BlueprintRenderQueue.blocks().size();
		source.sendFeedback(Component.literal("[Mineshaft-Forcer] Blueprint render queue contains " + count + " block(s)."));
		return count;
	}

	private static int renderTest(FabricClientCommandSource source) {
		Entity player = Minecraft.getInstance().player;
		if (player == null) {
			source.sendFeedback(Component.literal("[Mineshaft-Forcer] No client player is loaded."));
			return 0;
		}

		int x = player.blockPosition().getX();
		int y = player.blockPosition().getY();
		int z = player.blockPosition().getZ();
		BlueprintRenderQueue.replace(List.of(
				new BlueprintRenderBlock(x + 1, y, z, BlueprintRenderKind.PLACE),
				new BlueprintRenderBlock(x + 2, y, z, BlueprintRenderKind.BREAK),
				new BlueprintRenderBlock(x + 3, y, z, BlueprintRenderKind.TARGET)));
		source.sendFeedback(Component.literal("[Mineshaft-Forcer] Render test queued at your position. Use /mineshaftforcer render clear to remove it."));
		return 3;
	}

	private static String describeMutations(List<CorridorMutation> mutations) {
		if (mutations.isEmpty()) {
			return "none";
		}
		StringBuilder builder = new StringBuilder();
		for (CorridorMutation mutation : mutations) {
			if (builder.length() > 0) {
				builder.append("; ");
			}
			builder.append(mutation.changesBlock() ? mutation.kind() : "HEIGHTMAP_SHORT_CIRCUIT")
					.append(mutation.changesOceanFloorHeight() ? "(height=" + mutation.oceanFloorHeight() + ")" : "")
					.append("@")
					.append(mutation.pos().x())
					.append(",")
					.append(mutation.pos().y())
					.append(",")
					.append(mutation.pos().z());
		}
		return builder.toString();
	}

	private static ServerLevel integratedOverworld() {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		return server == null ? null : server.overworld();
	}
}
