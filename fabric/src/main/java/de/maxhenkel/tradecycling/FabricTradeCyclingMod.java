package de.maxhenkel.tradecycling;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import de.maxhenkel.configbuilder.ConfigBuilder;
import de.maxhenkel.tradecycling.config.FabricTradeCyclingServerConfig;
import de.maxhenkel.tradecycling.net.CycleTradesPacket;
import de.maxhenkel.tradecycling.net.EnchantmentOptionsPacket;
import de.maxhenkel.tradecycling.net.EnchantmentOptionsRequestPacket;
import de.maxhenkel.tradecycling.net.ServerAutoCycleCancelPacket;
import de.maxhenkel.tradecycling.net.ServerAutoCycleProgressPacket;
import de.maxhenkel.tradecycling.net.ServerAutoCycleRequestPacket;
import de.maxhenkel.tradecycling.net.ServerAutoCycleResultPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FabricTradeCyclingMod extends TradeCyclingMod implements ModInitializer {

    private static FabricTradeCyclingServerConfig serverConfig;

    @Override
    public void onInitialize() {
        reloadServerConfig();
        registerCommands();
        registerPacket();
        init();
    }

    private static FabricTradeCyclingServerConfig createServerConfig() {
        return ConfigBuilder.builder(FabricTradeCyclingServerConfig::new)
                .path(FabricLoader.getInstance().getConfigDir().resolve(TradeCyclingMod.MODID).resolve("trade_cycling_server.properties"))
                .build();
    }

    private static void reloadServerConfig() {
        serverConfig = createServerConfig();
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerServerAutoTradeCommand(dispatcher, "autotrade");
            registerServerAutoTradeCommand(dispatcher, "serverautotrade");
            registerServerAutoTradeCommand(dispatcher, "autotradeserver");
        });
    }

    private static void registerServerAutoTradeCommand(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        dispatcher.register(
                Commands.literal(name)
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("reload")
                                .executes(context -> {
                                    reloadServerConfig();
                                    context.getSource().sendSuccess(() -> Component.literal(formatServerConfigMessage("Trade Cycling server config reloaded")), false);
                                    return 1;
                                }))
                        .then(Commands.literal("maxattempts")
                                .then(Commands.argument("attempts", IntegerArgumentType.integer(1))
                                        .executes(FabricTradeCyclingMod::setServerAutoCycleMaxAttemptsCommand)))
                        .then(Commands.literal("maxtrades")
                                .then(Commands.argument("attempts", IntegerArgumentType.integer(1))
                                        .executes(FabricTradeCyclingMod::setServerAutoCycleMaxAttemptsCommand)))
                        .then(Commands.literal("batchtime")
                                .then(Commands.argument("milliseconds", IntegerArgumentType.integer(1, 50))
                                        .executes(FabricTradeCyclingMod::setServerAutoCycleBatchTimeCommand)))
                        .then(Commands.literal("batchdelay")
                                .then(Commands.argument("milliseconds", IntegerArgumentType.integer(0, 100))
                                        .executes(FabricTradeCyclingMod::setServerAutoCycleBatchDelayCommand)))
        );
    }

    private static int setServerAutoCycleMaxAttemptsCommand(CommandContext<CommandSourceStack> context) {
        int attempts = IntegerArgumentType.getInteger(context, "attempts");
        setServerAutoCycleMaxAttempts(attempts);
        context.getSource().sendSuccess(() -> Component.literal(formatServerConfigMessage("Trade Cycling server autotrade max attempts updated")), true);
        return attempts;
    }

    private static int setServerAutoCycleBatchTimeCommand(CommandContext<CommandSourceStack> context) {
        int milliseconds = IntegerArgumentType.getInteger(context, "milliseconds");
        if (serverConfig == null) {
            reloadServerConfig();
        }
        serverConfig.setServerAutoTradeBatchTimeMillis(milliseconds);
        context.getSource().sendSuccess(() -> Component.literal(formatServerConfigMessage("Trade Cycling server autotrade batch time updated")), true);
        return milliseconds;
    }

    private static int setServerAutoCycleBatchDelayCommand(CommandContext<CommandSourceStack> context) {
        int milliseconds = IntegerArgumentType.getInteger(context, "milliseconds");
        if (serverConfig == null) {
            reloadServerConfig();
        }
        serverConfig.setServerAutoTradeBatchDelayMillis(milliseconds);
        context.getSource().sendSuccess(() -> Component.literal(formatServerConfigMessage("Trade Cycling server autotrade batch delay updated")), true);
        return milliseconds;
    }

    private static void setServerAutoCycleMaxAttempts(int attempts) {
        if (serverConfig == null) {
            reloadServerConfig();
        }
        serverConfig.setMaxServerAutoTradeCycles(attempts);
    }

    private static String formatServerConfigMessage(String prefix) {
        return prefix
                + ". Max attempts: " + (serverConfig == null ? Integer.MAX_VALUE : serverConfig.getMaxServerAutoTradeCycles())
                + ", batch time: " + (serverConfig == null ? 10 : serverConfig.getServerAutoTradeBatchTimeMillis()) + "ms"
                + ", batch delay: " + (serverConfig == null ? 0 : serverConfig.getServerAutoTradeBatchDelayMillis()) + "ms";
    }

    private void registerPacket() {
        PayloadTypeRegistry.serverboundPlay().register(CycleTradesPacket.CYCLE_TRADES, CycleTradesPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerAutoCycleCancelPacket.SERVER_AUTOCYCLE_CANCEL, ServerAutoCycleCancelPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerAutoCycleRequestPacket.SERVER_AUTOCYCLE_REQUEST, ServerAutoCycleRequestPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EnchantmentOptionsRequestPacket.ENCHANTMENT_OPTIONS_REQUEST, EnchantmentOptionsRequestPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerAutoCycleResultPacket.SERVER_AUTOCYCLE_RESULT, ServerAutoCycleResultPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerAutoCycleProgressPacket.SERVER_AUTOCYCLE_PROGRESS, ServerAutoCycleProgressPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EnchantmentOptionsPacket.ENCHANTMENT_OPTIONS, EnchantmentOptionsPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CycleTradesPacket.CYCLE_TRADES, (payload, context) -> context.player().level().getServer().execute(() -> onCycleTrades(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(ServerAutoCycleRequestPacket.SERVER_AUTOCYCLE_REQUEST, (payload, context) -> context.player().level().getServer().execute(() -> onServerAutoCycle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ServerAutoCycleCancelPacket.SERVER_AUTOCYCLE_CANCEL, (payload, context) -> context.player().level().getServer().execute(() -> onServerAutoCycleCancel(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(EnchantmentOptionsRequestPacket.ENCHANTMENT_OPTIONS_REQUEST, (payload, context) -> context.player().level().getServer().execute(() -> sendEnchantmentOptions(context.player())));
    }

    private void sendEnchantmentOptions(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, EnchantmentOptionsPacket.ENCHANTMENT_OPTIONS)) {
            return;
        }

        List<EnchantmentOptionsPacket.Option> options = new ArrayList<>();
        player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(reference -> options.add(new EnchantmentOptionsPacket.Option(
                net.minecraft.resources.Identifier.parse(AutoTradeHandler.keyToIdString(reference.key())),
                Math.max(1, reference.value().getMaxLevel()),
                reference.value().description().getString(),
                reference.is(net.minecraft.tags.EnchantmentTags.DOUBLE_TRADE_PRICE)
        )));
        int reputation = player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu
                && ((de.maxhenkel.tradecycling.mixin.MerchantMenuAccessor) player.containerMenu).getTrader() instanceof net.minecraft.world.entity.npc.villager.Villager villager
                ? villager.getPlayerReputation(player) : 0;
        options.sort(Comparator.comparing(option -> option.id().toString()));
        ServerPlayNetworking.send(player, new EnchantmentOptionsPacket(reputation, options));
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public void sendServerAutoCycleResult(ServerPlayer player, ServerAutoCycleResultPacket packet) {
        if (ServerPlayNetworking.canSend(player, ServerAutoCycleResultPacket.SERVER_AUTOCYCLE_RESULT)) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    @Override
    public void sendServerAutoCycleProgress(ServerPlayer player, ServerAutoCycleProgressPacket packet) {
        if (ServerPlayNetworking.canSend(player, ServerAutoCycleProgressPacket.SERVER_AUTOCYCLE_PROGRESS)) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    @Override
    public int getServerAutoCycleMaxAttempts() {
        return serverConfig == null ? Integer.MAX_VALUE : serverConfig.getMaxServerAutoTradeCycles();
    }

    @Override
    public int getServerAutoCycleBatchTimeMillis() {
        return serverConfig == null ? super.getServerAutoCycleBatchTimeMillis() : serverConfig.getServerAutoTradeBatchTimeMillis();
    }

    @Override
    public int getServerAutoCycleBatchDelayMillis() {
        return serverConfig == null ? super.getServerAutoCycleBatchDelayMillis() : serverConfig.getServerAutoTradeBatchDelayMillis();
    }
}

