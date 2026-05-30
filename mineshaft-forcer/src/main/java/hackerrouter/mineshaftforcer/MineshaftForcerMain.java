package hackerrouter.mineshaftforcer;

import hackerrouter.mineshaftforcer.command.MineshaftForcerCommands;
import hackerrouter.mineshaftforcer.render.BlueprintRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MineshaftForcerMain implements ClientModInitializer {
	public static final String MOD_ID = "mineshaftforcer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess) -> MineshaftForcerCommands.register(dispatcher));
		BlueprintRenderer.init();
		LOGGER.info("Mineshaft-Forcer initialized.");
	}
}
