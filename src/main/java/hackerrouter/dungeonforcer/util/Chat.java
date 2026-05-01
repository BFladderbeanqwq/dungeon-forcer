package hackerrouter.dungeonforcer.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public class Chat {

    public static void send(String message) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    public static void send(String format, Object... args) {
        send(String.format(format, args));
    }
}
