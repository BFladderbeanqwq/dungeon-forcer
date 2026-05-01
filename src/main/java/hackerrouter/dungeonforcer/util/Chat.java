package hackerrouter.dungeonforcer.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Utility for sending chat messages to the local player.
 */
public class Chat {

    /**
     * Sends a system-level message to the local player's chat HUD.
     * This does NOT send a chat packet to the server.
     */
    public static void send(String message) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    /**
     * Sends a formatted message (with String.format) to the local player.
     */
    public static void send(String format, Object... args) {
        send(String.format(format, args));
    }
}
