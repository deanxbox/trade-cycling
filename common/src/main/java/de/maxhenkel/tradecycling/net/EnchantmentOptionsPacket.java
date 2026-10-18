package de.maxhenkel.tradecycling.net;

import de.maxhenkel.tradecycling.TradeCyclingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record EnchantmentOptionsPacket(int reputation, List<Option> options) implements CustomPacketPayload {

    public static final Type<EnchantmentOptionsPacket> ENCHANTMENT_OPTIONS = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TradeCyclingMod.MODID, "enchantment_options_v2"));
    public static final int MAX_OPTIONS = 512;

    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantmentOptionsPacket> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, EnchantmentOptionsPacket packet) {
            buf.writeVarInt(packet.reputation());
            buf.writeVarInt(Math.min(packet.options().size(), MAX_OPTIONS));
            for (int i = 0; i < packet.options().size() && i < MAX_OPTIONS; i++) {
                Option option = packet.options().get(i);
                buf.writeUtf(option.id().toString(), 256);
                buf.writeVarInt(option.maxLevel());
                buf.writeUtf(option.displayName(), 128);
                buf.writeBoolean(option.doublePrice());
            }
        }

        @Override
        public EnchantmentOptionsPacket decode(RegistryFriendlyByteBuf buf) {
            int reputation = buf.readVarInt();
            int size = Math.min(buf.readVarInt(), MAX_OPTIONS);
            List<Option> options = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                options.add(new Option(Identifier.parse(buf.readUtf(256)), buf.readVarInt(), buf.readUtf(128), buf.readBoolean()));
            }
            return new EnchantmentOptionsPacket(reputation, options);
        }
    };

    public EnchantmentOptionsPacket {
        options = List.copyOf(options);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ENCHANTMENT_OPTIONS;
    }

    public record Option(Identifier id, int maxLevel, String displayName, boolean doublePrice) {
    }
}
