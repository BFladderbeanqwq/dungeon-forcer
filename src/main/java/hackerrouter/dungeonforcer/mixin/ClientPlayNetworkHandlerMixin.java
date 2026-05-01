package hackerrouter.dungeonforcer.mixin;

import hackerrouter.dungeonforcer.util.Chat;
import hackerrouter.dungeonforcer.util.World;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {


    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void onHandleLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        CommonPlayerSpawnInfo spawnInfo = packet.commonPlayerSpawnInfo();
        long hashedSeed = spawnInfo.seed();
        World.setHashedSeed(hashedSeed);

        if (!World.hasIntegratedServer()) {
            Chat.send("§6[DungeonForcer] §7Joined server. Hashed seed captured: " + hashedSeed);
            Chat.send("§6[DungeonForcer] §7Use §f/dungeonforcer seed <value>§7 to set the real world seed.");
        }
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void onHandleRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        CommonPlayerSpawnInfo spawnInfo = packet.commonPlayerSpawnInfo();
        long hashedSeed = spawnInfo.seed();
        World.setHashedSeed(hashedSeed);
    }
}
