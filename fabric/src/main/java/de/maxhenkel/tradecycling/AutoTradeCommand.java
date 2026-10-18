package de.maxhenkel.tradecycling;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class AutoTradeCommand {

    public static void initializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(AutoTradeCommand::registerCommands);
    }

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                        CommandBuildContext registryAccess) {
        registerCommand(dispatcher, registryAccess, "clientautotrade");
        registerCommand(dispatcher, registryAccess, "autotradeclient");
    }

    private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                        CommandBuildContext registryAccess,
                                        String name) {
        dispatcher.register(literal(name)
                .executes(ctx -> sendOverview(ctx.getSource()))
                .then(literal("status")
                        .executes(ctx -> sendStatus(ctx.getSource())))
                .then(literal("clear")
                        .executes(ctx -> clearTargets(ctx.getSource())))
                .then(literal("delay")
                        .executes(ctx -> showDelay(ctx.getSource()))
                        .then(argument("ticks", IntegerArgumentType.integer(1))
                                .executes(ctx -> setDelay(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "ticks")))))
                .then(literal("mode")
                        .then(literal("all")
                                .executes(ctx -> setMode(ctx.getSource(), false)))
                        .then(literal("any")
                                .executes(ctx -> setMode(ctx.getSource(), true))))
                .then(literal("item")
                        .then(argument("item_id", IdentifierArgument.id())
                                .suggests((context, builder) -> suggestItems(builder))
                                .executes(ctx -> addItemTarget(
                                        ctx.getSource(),
                                        registryAccess,
                                        getResourceId(ctx, "item_id").toString(),
                                        Integer.MAX_VALUE
                                ))
                                .then(argument("max_emeralds", IntegerArgumentType.integer(1))
                                        .executes(ctx -> addItemTarget(
                                                ctx.getSource(),
                                                registryAccess,
                                                getResourceId(ctx, "item_id").toString(),
                                                IntegerArgumentType.getInteger(ctx, "max_emeralds")
                                        )))))
                .then(literal("enchant")
                        .then(argument("enchant_id", IdentifierArgument.id())
                                .suggests((context, builder) -> suggestEnchantments(registryAccess, builder))
                                .executes(ctx -> addEnchantTarget(
                                        ctx.getSource(),
                                        registryAccess,
                                        getResourceId(ctx, "enchant_id").toString(),
                                        1,
                                        Integer.MAX_VALUE
                                ))
                                .then(argument("min_level", IntegerArgumentType.integer(1))
                                        .executes(ctx -> addEnchantTarget(
                                                ctx.getSource(),
                                                registryAccess,
                                                getResourceId(ctx, "enchant_id").toString(),
                                                IntegerArgumentType.getInteger(ctx, "min_level"),
                                                Integer.MAX_VALUE
                                        ))
                                        .then(argument("max_emeralds", IntegerArgumentType.integer(1))
                                                .executes(ctx -> addEnchantTarget(
                                                        ctx.getSource(),
                                                        registryAccess,
                                                        getResourceId(ctx, "enchant_id").toString(),
                                                        IntegerArgumentType.getInteger(ctx, "min_level"),
                                                        IntegerArgumentType.getInteger(ctx, "max_emeralds")
                                                )))))));
    }

    private static int addItemTarget(FabricClientCommandSource src, CommandBuildContext registryAccess, String rawItem, int maxEmeralds) {
        Item item = resolveItem(registryAccess, rawItem);
        if (item == null) {
            src.sendFeedback(Component.literal("Unknown item: " + rawItem));
            return 0;
        }

        AutoTradeHandler.start(item, maxEmeralds);
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (maxEmeralds == Integer.MAX_VALUE) {
            src.sendFeedback(Component.literal("Target added: " + id + " | Press C in a villager screen to start"));
        } else {
            src.sendFeedback(Component.literal("Target added: " + id + " up to " + maxEmeralds + " emeralds | Press C to start"));
        }
        return 1;
    }

    private static int addEnchantTarget(FabricClientCommandSource src, CommandBuildContext registryAccess, String rawEnchant, int minLevel, int maxEmeralds) {
        ResourceKey<Enchantment> enchantmentKey = resolveEnchantmentKey(registryAccess, rawEnchant);
        if (enchantmentKey == null) {
            src.sendFeedback(Component.literal("Unknown enchantment: " + rawEnchant));
            return 0;
        }

        AutoTradeHandler.start(enchantmentKey, minLevel, maxEmeralds);

        String message = "Added enchantment target: " + AutoTradeHandler.keyToIdString(enchantmentKey) + " level " + Math.max(1, minLevel) + "+";
        if (maxEmeralds != Integer.MAX_VALUE) {
            message += " up to " + maxEmeralds + " emeralds";
        }
        src.sendFeedback(Component.literal(message + " | Press C in a villager screen to start"));
        return 1;
    }

    private static Item resolveItem(CommandBuildContext registryAccess, String rawItem) {
        HolderLookup.RegistryLookup<Item> itemRegistry = registryAccess.lookupOrThrow(Registries.ITEM);
        Identifier parsed = parseId(rawItem);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, parsed);
        var direct = itemRegistry.get(key);
        if (direct.isPresent()) {
            return direct.get().value();
        }

        String wanted = rawItem.toLowerCase(java.util.Locale.ROOT);
        return itemRegistry.listElements()
                .filter(ref -> BuiltInRegistries.ITEM.getKey(ref.value()).toString().equals(wanted)
                        || BuiltInRegistries.ITEM.getKey(ref.value()).toString().endsWith(":" + wanted))
                .map(ref -> ref.value())
                .findFirst()
                .orElse(null);
    }

    private static ResourceKey<Enchantment> resolveEnchantmentKey(CommandBuildContext registryAccess, String rawEnchant) {
        HolderLookup.RegistryLookup<Enchantment> enchantRegistry = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        Identifier parsed = parseId(rawEnchant);
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, parsed);
        var direct = enchantRegistry.get(key);
        if (direct.isPresent()) {
            return direct.get().key();
        }

        String wanted = rawEnchant.toLowerCase(java.util.Locale.ROOT);
        return enchantRegistry.listElements()
                .filter(ref -> {
                    String id = AutoTradeHandler.keyToIdString(ref.key());
                    String desc = ref.value().description().getString().toLowerCase(java.util.Locale.ROOT);
                    return id.equals(wanted) || id.endsWith(":" + wanted) || desc.contains(wanted);
                })
                .map(ref -> ref.key())
                .findFirst()
                .orElse(null);
    }

    private static int clearTargets(FabricClientCommandSource src) {
        AutoTradeHandler.clearTargets();
        src.sendFeedback(Component.literal("Autotrade targets cleared"));
        return 1;
    }

    private static int showDelay(FabricClientCommandSource src) {
        src.sendFeedback(Component.literal("Current retry timeout: " + AutoTradeHandler.getTradeCycleDelay() + " ticks"));
        return 1;
    }

    private static int setDelay(FabricClientCommandSource src, int ticks) {
        AutoTradeHandler.setTradeCycleDelay(ticks);
        TradeCyclingClientMod.CONFIG.setTradeCycleDelayTicks(ticks);
        src.sendFeedback(Component.literal("Retry timeout set to " + ticks + " ticks"));
        return 1;
    }

    private static int setMode(FabricClientCommandSource src, boolean matchAny) {
        AutoTradeHandler.setMatchAny(matchAny);
        TradeCyclingClientMod.CONFIG.setAutoTradeMatchAny(matchAny);
        src.sendFeedback(Component.literal("Autotrade mode: " + (matchAny ? "match any target" : "match all targets")));
        return 1;
    }

    private static int sendOverview(FabricClientCommandSource src) {
        src.sendFeedback(Component.literal("Autotrade setup"));
        src.sendFeedback(Component.literal("Targets: " + AutoTradeHandler.describeTarget()));
        src.sendFeedback(Component.literal("Use /clientautotrade item <item_id> [max_emeralds]"));
        src.sendFeedback(Component.literal("Use /clientautotrade enchant <enchant_id> [min_level] [max_emeralds]"));
        src.sendFeedback(Component.literal("Press C in a novice villager trading screen to start or stop auto-cycling"));
        return 1;
    }

    private static int sendStatus(FabricClientCommandSource src) {
        String active = AutoTradeHandler.isActive() ? "running" : "stopped";
        String matched = AutoTradeHandler.isTradeMatched() ? "yes" : "no";
        String mode = AutoTradeHandler.isMatchAny() ? "ANY match" : "ALL targets";
        src.sendFeedback(Component.literal("Autotrade status"));
        src.sendFeedback(Component.literal("Targets: " + AutoTradeHandler.describeTarget()));
        src.sendFeedback(Component.literal("State: " + active + " | Matched: " + matched));
        src.sendFeedback(Component.literal("Mode: " + mode + " | Retry timeout: " + AutoTradeHandler.getTradeCycleDelay() + " ticks | Max cycles: " + AutoTradeHandler.getMaxCycleCount()));
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestItems(SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        BuiltInRegistries.ITEM.keySet().stream()
                .map(Identifier::toString)
                .filter(id -> id.startsWith(remaining) || id.endsWith(":" + remaining))
                .sorted()
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestEnchantments(CommandBuildContext registryAccess, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        registryAccess.lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .map(reference -> AutoTradeHandler.keyToIdString(reference.key()))
                .filter(id -> id.startsWith(remaining) || id.endsWith(":" + remaining))
                .sorted()
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static Identifier parseId(String raw) {
        String lowered = raw.toLowerCase(Locale.ROOT);
        Identifier id = Identifier.tryParse(lowered);
        return id != null ? id : Identifier.tryParse("minecraft:" + lowered);
    }

    private static Identifier getResourceId(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, String name) {
        return context.getArgument(name, Identifier.class);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, com.mojang.brigadier.arguments.ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }
}
