package de.maxhenkel.tradecycling.net;

import de.maxhenkel.tradecycling.TradeCyclingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class ServerAutoCycleCancelPacket implements CustomPacketPayload {

    public static final Type<ServerAutoCycleCancelPacket> SERVER_AUTOCYCLE_CANCEL = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TradeCyclingMod.MODID, "server_autocycle_cancel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerAutoCycleCancelPacket> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, ServerAutoCycleCancelPacket packet) {
        }

        @Override
        public ServerAutoCycleCancelPacket decode(RegistryFriendlyByteBuf buf) {
            return new ServerAutoCycleCancelPacket();
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return SERVER_AUTOCYCLE_CANCEL;
    }

}
