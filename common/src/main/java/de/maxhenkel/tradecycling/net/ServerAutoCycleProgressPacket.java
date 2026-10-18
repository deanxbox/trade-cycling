package de.maxhenkel.tradecycling.net;

import de.maxhenkel.tradecycling.TradeCyclingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerAutoCycleProgressPacket(int attempts, int maxAttempts) implements CustomPacketPayload {

    public static final Type<ServerAutoCycleProgressPacket> SERVER_AUTOCYCLE_PROGRESS = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TradeCyclingMod.MODID, "server_autocycle_progress_v1"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerAutoCycleProgressPacket> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, ServerAutoCycleProgressPacket packet) {
            buf.writeVarInt(packet.attempts());
            buf.writeVarInt(packet.maxAttempts());
        }

        @Override
        public ServerAutoCycleProgressPacket decode(RegistryFriendlyByteBuf buf) {
            return new ServerAutoCycleProgressPacket(buf.readVarInt(), buf.readVarInt());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return SERVER_AUTOCYCLE_PROGRESS;
    }
}
