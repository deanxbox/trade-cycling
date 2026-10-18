package de.maxhenkel.tradecycling.net;

import de.maxhenkel.tradecycling.TradeCyclingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EnchantmentOptionsRequestPacket() implements CustomPacketPayload {

    public static final Type<EnchantmentOptionsRequestPacket> ENCHANTMENT_OPTIONS_REQUEST = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TradeCyclingMod.MODID, "enchantment_options_request_v1"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantmentOptionsRequestPacket> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, EnchantmentOptionsRequestPacket packet) {
        }

        @Override
        public EnchantmentOptionsRequestPacket decode(RegistryFriendlyByteBuf buf) {
            return new EnchantmentOptionsRequestPacket();
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ENCHANTMENT_OPTIONS_REQUEST;
    }
}
