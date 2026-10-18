package de.maxhenkel.tradecycling.net;

import de.maxhenkel.tradecycling.TradeCyclingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ServerAutoCycleResultPacket(boolean matched, int attempts, String message) implements CustomPacketPayload {

    public static final Type<ServerAutoCycleResultPacket> SERVER_AUTOCYCLE_RESULT = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TradeCyclingMod.MODID, "server_autocycle_result_v2"));
    private static final int MAX_MESSAGE_LENGTH = 1024;

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerAutoCycleResultPacket> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, ServerAutoCycleResultPacket packet) {
            buf.writeBoolean(packet.matched());
            buf.writeVarInt(packet.attempts());
            buf.writeUtf(truncate(packet.message()), MAX_MESSAGE_LENGTH);
        }

        @Override
        public ServerAutoCycleResultPacket decode(RegistryFriendlyByteBuf buf) {
            return new ServerAutoCycleResultPacket(buf.readBoolean(), buf.readVarInt(), buf.readUtf(MAX_MESSAGE_LENGTH));
        }
    };

    private static String truncate(String message) {
        if (message == null) {
            return "";
        }
        if (message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return SERVER_AUTOCYCLE_RESULT;
    }
}
