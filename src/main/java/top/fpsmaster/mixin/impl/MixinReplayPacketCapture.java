package top.fpsmaster.mixin.impl;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.auxiliary.Replay;

/**
 * Captures clientbound packets for the replay recorder.
 *
 * {@code channelRead0} is where a packet has been decoded and has not yet been handled, which is the
 * only point that gives the recorder the packet object and the moment it arrived. Reading it here
 * also means the pipeline's decompression and decryption have already happened, so what gets
 * re-encoded is the packet and not a wire frame.
 *
 * This runs on the network thread. Everything it does is encode the packet and hand it to a bounded
 * queue; nothing here touches the disk or blocks.
 */
@Mixin(Connection.class)
public class MixinReplayPacketCapture {

    @Inject(method = "channelRead0", at = @At("HEAD"))
    private void fpsmaster$captureClientbound(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        // The same class serves both directions; a server-side connection receives serverbound
        // packets, which a replay has no use for.
        if (((Connection) (Object) this).getReceiving() != PacketFlow.CLIENTBOUND) {
            return;
        }
        try {
            if (top.fpsmaster.diagnostics.Smoke.ENABLED) {
                top.fpsmaster.diagnostics.Smoke.mixin("replay-packet");
                top.fpsmaster.diagnostics.Smoke.feature("replay");
            }
            Replay.onClientboundPacket(packet);
        } catch (Throwable failure) {
            // A recording is never worth dropping a connection over.
        }
    }
}
