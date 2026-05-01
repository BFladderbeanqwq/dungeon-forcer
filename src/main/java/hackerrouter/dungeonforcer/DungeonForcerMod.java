package hackerrouter.dungeonforcer;

import hackerrouter.dungeonforcer.command.ClientCommands;
import hackerrouter.dungeonforcer.render.Renderer;
import net.fabricmc.api.ClientModInitializer;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class DungeonForcerMod implements ClientModInitializer {
	public static final String MOD_ID = "dungeonforcer";
	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {

		ClientCommandRegistrationCallback.EVENT.register(
				             (dispatcher, registryAccess) -> ClientCommands.register(dispatcher));
          Renderer.init();
	}
}