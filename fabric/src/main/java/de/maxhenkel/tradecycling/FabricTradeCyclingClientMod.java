package de.maxhenkel.tradecycling;

import de.maxhenkel.configbuilder.ConfigBuilder;
import de.maxhenkel.tradecycling.gui.AutoTradeConfigScreen;
import de.maxhenkel.tradecycling.config.FabricTradeCyclingClientConfig;
import de.maxhenkel.tradecycling.config.TradeCyclingClientConfig;
import de.maxhenkel.tradecycling.net.CycleTradesPacket;
import de.maxhenkel.tradecycling.net.EnchantmentOptionsPacket;
import de.maxhenkel.tradecycling.net.EnchantmentOptionsRequestPacket;
import de.maxhenkel.tradecycling.net.ServerAutoCycleCancelPacket;
import de.maxhenkel.tradecycling.net.ServerAutoCycleProgressPacket;
import de.maxhenkel.tradecycling.net.ServerAutoCycleRequestPacket;
import de.maxhenkel.tradecycling.net.ServerAutoCycleResultPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FabricTradeCyclingClientMod extends TradeCyclingClientMod implements ClientModInitializer {

    private enum AutoCycleState {
        IDLE,
        QUEUED,
        AWAITING_REFRESH
    }

    private static final int OFFER_REFRESH_TIMEOUT_TICKS = 40;

    private static FabricTradeCyclingClientMod instance;
    private int offerRefreshTimeout = 0;
    private int ticksUntilNextCycle = 0;
    private AutoCycleState autoCycleState = AutoCycleState.IDLE;
    private int completedCycles = 0;
    private String lastSeenOfferSignature = "empty";
    private boolean serverAutoCycleActive = false;
    private long autoCycleStartedNanos = 0L;
    private boolean serverAutoCycleReceiversRegistered = false;
    private boolean enchantmentOptionsReceiverRegistered = false;

    public FabricTradeCyclingClientMod() {
        instance = this;
        TradeCyclingClientMod.INSTANCE = this;
        CONFIG = createClientConfig();
    }

    @Override
    public void onInitializeClient() {
        clientInit();
        AutoTradeCommand.initializeClient();

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            onOpenScreen(screen, guiEventListener -> Screens.getWidgets(screen).add(guiEventListener));
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(ServerAutoCycleRequestPacket.SERVER_AUTOCYCLE_REQUEST)) {
                ensureServerAutoCycleReceivers();
            }
            if (ClientPlayNetworking.canSend(EnchantmentOptionsRequestPacket.ENCHANTMENT_OPTIONS_REQUEST)) {
                ensureEnchantmentOptionsReceiver();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            serverAutoCycleReceiversRegistered = false;
            enchantmentOptionsReceiverRegistered = false;
            stopAutoCycle();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!AutoTradeHandler.isActive()) {
                return;
            }
            if (serverAutoCycleActive) {
                return;
            }

            if (!(client.gui.screen() instanceof MerchantScreen screen)) {
                stopAutoCycle();
                return;
            }

            tickAutoCycle(screen);
        });
    }

    @Override
    protected void startAutoCycle(MerchantScreen screen) {
        if (startServerAutoCycle(screen)) {
            return;
        }

        AutoTradeHandler.startCycleSession();
        serverAutoCycleActive = false;
        autoCycleState = AutoCycleState.IDLE;
        completedCycles = 0;
        autoCycleStartedNanos = System.nanoTime();
        offerRefreshTimeout = 0;
        ticksUntilNextCycle = 0;
        lastSeenOfferSignature = AutoTradeHandler.createOfferSignature(screen.getMenu().getOffers());

        if (handleVisibleOffers(screen.getMenu().getOffers())) {
            return;
        }

        notifyStatus("Autotrade started");
        queueNextCycle();
    }

    @Override
    protected void stopAutoCycle() {
        AutoTradeHandler.stop();
        if (serverAutoCycleActive && ClientPlayNetworking.canSend(ServerAutoCycleCancelPacket.SERVER_AUTOCYCLE_CANCEL)) {
            ClientPlayNetworking.send(new ServerAutoCycleCancelPacket());
        }
        serverAutoCycleActive = false;
        autoCycleState = AutoCycleState.IDLE;
        completedCycles = 0;
        autoCycleStartedNanos = 0L;
        offerRefreshTimeout = 0;
        ticksUntilNextCycle = 0;
        clearServerAutoCycleProgress();
    }

    @Override
    public void registerKeyBindings() {
        KeyMappingHelper.registerKeyMapping(CYCLE_TRADES_KEY);
        KeyMappingHelper.registerKeyMapping(OPEN_AUTOTRADE_CONFIG_KEY);
    }

    @Override
    public void sendCycleTradesPacket() {
        if (!ClientPlayNetworking.canSend(CycleTradesPacket.CYCLE_TRADES)) {
            notifyStatus("Trade Cycling is not available on this server");
            stopAutoCycle();
            return;
        }
        ClientPlayNetworking.send(new CycleTradesPacket());
    }

    @Override
    public TradeCyclingClientConfig createClientConfig() {
        return ConfigBuilder.builder(FabricTradeCyclingClientConfig::new)
                .path(FabricLoader.getInstance().getConfigDir().resolve(TradeCyclingMod.MODID).resolve("trade_cycling.properties"))
                .build();
    }

    @Override
    public void requestServerEnchantmentOptions() {
        if (ClientPlayNetworking.canSend(EnchantmentOptionsRequestPacket.ENCHANTMENT_OPTIONS_REQUEST)) {
            ensureEnchantmentOptionsReceiver();
            ClientPlayNetworking.send(new EnchantmentOptionsRequestPacket());
        }
    }

    public static FabricTradeCyclingClientMod instance() {
        return instance;
    }

    public boolean startAutoCycleFromCommand() {
        if (!AutoTradeHandler.hasTargets()) {
            return false;
        }

        MerchantScreen screen = getCurrentMerchantScreen();
        if (screen == null || !canAutoCycle(screen)) {
            return false;
        }

        startAutoCycle(screen);
        return true;
    }

    private boolean startServerAutoCycle(MerchantScreen screen) {
        if (!ClientPlayNetworking.canSend(ServerAutoCycleRequestPacket.SERVER_AUTOCYCLE_REQUEST)) {
            return false;
        }
        if (!AutoTradeHandler.hasTargets() || !canAutoCycle(screen)) {
            return false;
        }
        if (!ensureServerAutoCycleReceivers()) {
            return false;
        }

        AutoTradeHandler.startCycleSession();
        serverAutoCycleActive = true;
        autoCycleState = AutoCycleState.IDLE;
        completedCycles = 0;
        autoCycleStartedNanos = System.nanoTime();
        offerRefreshTimeout = 0;
        ticksUntilNextCycle = 0;
        ClientPlayNetworking.send(createServerAutoCycleRequest());
        updateServerAutoCycleProgress(0, AutoTradeHandler.getMaxCycleCount());
        notifyStatus("Server autotrade started");
        return true;
    }

    private boolean ensureServerAutoCycleReceivers() {
        if (serverAutoCycleReceiversRegistered) {
            return true;
        }
        try {
            ClientPlayNetworking.registerReceiver(ServerAutoCycleResultPacket.SERVER_AUTOCYCLE_RESULT, (payload, context) -> context.client().execute(() -> onServerAutoCycleResult(payload)));
            ClientPlayNetworking.registerReceiver(ServerAutoCycleProgressPacket.SERVER_AUTOCYCLE_PROGRESS, (payload, context) -> context.client().execute(() -> onServerAutoCycleProgress(payload)));
            serverAutoCycleReceiversRegistered = true;
            return true;
        } catch (Exception e) {
            TradeCyclingMod.LOGGER.warn("Failed to register server autotrade receivers", e);
            return false;
        }
    }

    private boolean ensureEnchantmentOptionsReceiver() {
        if (enchantmentOptionsReceiverRegistered) {
            return true;
        }
        try {
            ClientPlayNetworking.registerReceiver(EnchantmentOptionsPacket.ENCHANTMENT_OPTIONS, (payload, context) -> context.client().execute(() -> onEnchantmentOptions(payload)));
            enchantmentOptionsReceiverRegistered = true;
            return true;
        } catch (Exception e) {
            TradeCyclingMod.LOGGER.warn("Failed to register enchantment option receiver", e);
            return false;
        }
    }

    private ServerAutoCycleRequestPacket createServerAutoCycleRequest() {
        List<ServerAutoCycleRequestPacket.ItemTarget> itemTargets = new ArrayList<>();
        for (AutoTradeHandler.ItemTarget target : AutoTradeHandler.getItemTargets()) {
            itemTargets.add(new ServerAutoCycleRequestPacket.ItemTarget(BuiltInRegistries.ITEM.getKey(target.item), target.maxEmeralds, target.tradeSide, target.cheapest));
        }

        List<ServerAutoCycleRequestPacket.EnchantTarget> enchantTargets = new ArrayList<>();
        for (AutoTradeHandler.EnchantTarget target : AutoTradeHandler.getEnchantTargets()) {
            enchantTargets.add(new ServerAutoCycleRequestPacket.EnchantTarget(net.minecraft.resources.Identifier.parse(AutoTradeHandler.keyToIdString(target.enchantKey)), target.minLevel, target.maxEmeralds, target.cheapest));
        }

        return new ServerAutoCycleRequestPacket(
                AutoTradeHandler.isMatchAny(),
                AutoTradeHandler.getMaxCycleCount(),
                CONFIG != null && CONFIG.getSendAutoTradeResultsToServer(),
                itemTargets,
                enchantTargets
        );
    }

    private void onServerAutoCycleResult(ServerAutoCycleResultPacket packet) {
        serverAutoCycleActive = false;
        autoCycleState = AutoCycleState.IDLE;
        offerRefreshTimeout = 0;
        ticksUntilNextCycle = 0;
        completedCycles = packet.attempts();
        clearServerAutoCycleProgress();

        if (packet.matched()) {
            AutoTradeHandler.markMatched();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1F, 1F));
        } else {
            AutoTradeHandler.stop();
        }
        notifyStatus(packet.message());
    }

    private void onServerAutoCycleProgress(ServerAutoCycleProgressPacket packet) {
        if (!AutoTradeHandler.isActive()) {
            return;
        }
        updateServerAutoCycleProgress(packet.attempts(), packet.maxAttempts());
        showActionBarMessage("Autotrade cycling: " + packet.attempts() + " / " + packet.maxAttempts());
    }

    private void onEnchantmentOptions(EnchantmentOptionsPacket packet) {
        List<ServerEnchantmentOption> options = new ArrayList<>();
        for (EnchantmentOptionsPacket.Option option : packet.options()) {
            options.add(new ServerEnchantmentOption(option.id(), option.maxLevel(), option.displayName(), option.doublePrice()));
        }
        setServerReputation(packet.reputation());
        setServerEnchantmentOptions(options);

        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof AutoTradeConfigScreen screen) {
            screen.reloadEnchantmentOptions();
        }
    }

    public boolean stopAutoCycleFromCommand() {
        if (!AutoTradeHandler.isActive()) {
            return false;
        }

        stopAutoCycle();
        notifyStatus("Autotrade stopped");
        return true;
    }

    public void onMerchantOffersUpdated(MerchantMenu menu, MerchantOffers offers) {
        if (!AutoTradeHandler.isActive()) {
            return;
        }
        if (serverAutoCycleActive) {
            return;
        }

        MerchantScreen screen = getCurrentMerchantScreen();
        if (screen == null || screen.getMenu() != menu) {
            return;
        }

        String signature = AutoTradeHandler.createOfferSignature(offers);
        autoCycleState = AutoCycleState.IDLE;
        offerRefreshTimeout = 0;
        lastSeenOfferSignature = signature;
        continueOrComplete(offers);
    }

    private boolean handleVisibleOffers(MerchantOffers offers) {
        if (!AutoTradeHandler.isActive()) {
            return true;
        }

        AutoTradeHandler.MatchSummary matchSummary = AutoTradeHandler.findVillagerMatch(offers);
        if (matchSummary.matched) {
            AutoTradeHandler.markMatched();
            autoCycleState = AutoCycleState.IDLE;
            offerRefreshTimeout = 0;
            notifyStatus(matchSummary.completionMessage() + formatTryCount(completedCycles) + formatStats(completedCycles));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1F, 1F));
            return true;
        }

        return false;
    }

    private void continueOrComplete(MerchantOffers offers) {
        if (handleVisibleOffers(offers)) {
            return;
        }
        queueNextCycle();
    }

    private void queueNextCycle() {
        if (!AutoTradeHandler.isActive()) {
            return;
        }

        autoCycleState = AutoCycleState.QUEUED;
        offerRefreshTimeout = 0;
        ticksUntilNextCycle = Math.max(0, AutoTradeHandler.getTradeCycleDelay() - 1);
    }

    private void sendQueuedCycle() {
        if (!AutoTradeHandler.isActive()) {
            return;
        }
        if (completedCycles >= AutoTradeHandler.getMaxCycleCount()) {
            String message = "Autotrade stopped after " + completedCycles + " cycles without a match" + formatStats(completedCycles);
            stopAutoCycle();
            notifyStatus(message);
            return;
        }

        autoCycleState = AutoCycleState.AWAITING_REFRESH;
        offerRefreshTimeout = OFFER_REFRESH_TIMEOUT_TICKS;
        ticksUntilNextCycle = 0;
        completedCycles++;
        AutoTradeHandler.doCycleOnce();
    }

    private void tickAutoCycle(MerchantScreen screen) {
        switch (autoCycleState) {
            case IDLE:
                return;
            case QUEUED:
                if (ticksUntilNextCycle > 0) {
                    ticksUntilNextCycle--;
                    return;
                }
                sendQueuedCycle();
                return;
            case AWAITING_REFRESH:
                awaitOfferRefresh(screen);
                return;
            default:
        }
    }

    private void awaitOfferRefresh(MerchantScreen screen) {
        String currentSignature = AutoTradeHandler.createOfferSignature(screen.getMenu().getOffers());
        if (!currentSignature.equals(lastSeenOfferSignature)) {
            autoCycleState = AutoCycleState.IDLE;
            lastSeenOfferSignature = currentSignature;
            continueOrComplete(screen.getMenu().getOffers());
            return;
        }

        if (offerRefreshTimeout > 0) {
            offerRefreshTimeout--;
        }
        if (offerRefreshTimeout > 0) {
            return;
        }

        queueNextCycle();
    }

    private MerchantScreen getCurrentMerchantScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof MerchantScreen screen) {
            return screen;
        }
        return null;
    }

    private void notifyStatus(String message) {
        showStatusMessage(message);
    }

    private String formatTryCount(int tries) {
        return " after " + tries + " " + (tries == 1 ? "try" : "tries");
    }

    private String formatStats(int tries) {
        if (autoCycleStartedNanos <= 0L) {
            return "";
        }
        double elapsedMillis = (System.nanoTime() - autoCycleStartedNanos) / 1_000_000D;
        String average = tries <= 0 ? "n/a" : String.format(Locale.ROOT, "%.3f ms/trade", elapsedMillis / tries);
        return String.format(Locale.ROOT, " | Time: %.2f ms | Avg: %s", elapsedMillis, average);
    }
}
