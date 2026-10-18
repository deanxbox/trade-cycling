package de.maxhenkel.tradecycling.net;

import de.maxhenkel.tradecycling.AutoTradeHandler;
import de.maxhenkel.tradecycling.TradeCyclingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record ServerAutoCycleRequestPacket(boolean matchAny, int maxCycles, boolean sendResultToServer, List<ItemTarget> itemTargets, List<EnchantTarget> enchantTargets) implements CustomPacketPayload {

    public static final Type<ServerAutoCycleRequestPacket> SERVER_AUTOCYCLE_REQUEST = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(TradeCyclingMod.MODID, "server_autocycle_request_v4"));
    public static final int MAX_TARGETS = 64;

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerAutoCycleRequestPacket> CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, ServerAutoCycleRequestPacket packet) {
            buf.writeBoolean(packet.matchAny());
            buf.writeVarInt(packet.maxCycles());
            buf.writeBoolean(packet.sendResultToServer());
            writeItemTargets(buf, packet.itemTargets());
            writeEnchantTargets(buf, packet.enchantTargets());
        }

        @Override
        public ServerAutoCycleRequestPacket decode(RegistryFriendlyByteBuf buf) {
            boolean matchAny = buf.readBoolean();
            int maxCycles = buf.readVarInt();
            boolean sendResultToServer = buf.readBoolean();
            List<ItemTarget> itemTargets = readItemTargets(buf);
            List<EnchantTarget> enchantTargets = readEnchantTargets(buf);
            return new ServerAutoCycleRequestPacket(matchAny, maxCycles, sendResultToServer, itemTargets, enchantTargets);
        }
    };

    public ServerAutoCycleRequestPacket {
        itemTargets = List.copyOf(itemTargets);
        enchantTargets = List.copyOf(enchantTargets);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return SERVER_AUTOCYCLE_REQUEST;
    }

    private static void writeItemTargets(RegistryFriendlyByteBuf buf, List<ItemTarget> targets) {
        buf.writeVarInt(Math.min(targets.size(), MAX_TARGETS));
        for (int i = 0; i < targets.size() && i < MAX_TARGETS; i++) {
            ItemTarget target = targets.get(i);
            buf.writeUtf(target.itemId().toString());
            buf.writeVarInt(target.maxEmeralds());
            buf.writeVarInt(target.tradeSide().ordinal());
            buf.writeBoolean(target.cheapest());
        }
    }

    private static List<ItemTarget> readItemTargets(RegistryFriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), MAX_TARGETS);
        List<ItemTarget> targets = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            targets.add(new ItemTarget(Identifier.parse(buf.readUtf(256)), buf.readVarInt(), readTradeSide(buf), buf.readBoolean()));
        }
        return targets;
    }

    private static AutoTradeHandler.ItemTradeSide readTradeSide(RegistryFriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        AutoTradeHandler.ItemTradeSide[] values = AutoTradeHandler.ItemTradeSide.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return AutoTradeHandler.ItemTradeSide.TRADED;
        }
        return values[ordinal];
    }

    private static void writeEnchantTargets(RegistryFriendlyByteBuf buf, List<EnchantTarget> targets) {
        buf.writeVarInt(Math.min(targets.size(), MAX_TARGETS));
        for (int i = 0; i < targets.size() && i < MAX_TARGETS; i++) {
            EnchantTarget target = targets.get(i);
            buf.writeUtf(target.enchantId().toString());
            buf.writeVarInt(target.minLevel());
            buf.writeVarInt(target.maxEmeralds());
            buf.writeBoolean(target.cheapest());
        }
    }

    private static List<EnchantTarget> readEnchantTargets(RegistryFriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), MAX_TARGETS);
        List<EnchantTarget> targets = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            targets.add(new EnchantTarget(Identifier.parse(buf.readUtf(256)), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        }
        return targets;
    }

    public record ItemTarget(Identifier itemId, int maxEmeralds, AutoTradeHandler.ItemTradeSide tradeSide, boolean cheapest) {
    }

    public record EnchantTarget(Identifier enchantId, int minLevel, int maxEmeralds, boolean cheapest) {
    }
}
