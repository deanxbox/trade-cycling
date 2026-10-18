package de.maxhenkel.tradecycling;

import de.maxhenkel.tradecycling.config.TradeCyclingClientConfig;
import de.maxhenkel.tradecycling.gui.AutoTradeConfigButton;
import de.maxhenkel.tradecycling.gui.AutoTradeProgressWidget;
import de.maxhenkel.tradecycling.gui.AutoTradeConfigScreen;
import de.maxhenkel.tradecycling.gui.CycleTradesButton;
import de.maxhenkel.tradecycling.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

public abstract class TradeCyclingClientMod {

    public static TradeCyclingClientMod INSTANCE;
    public static TradeCyclingClientConfig CONFIG;
    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(TradeCyclingMod.MODID, "main"));
    public static final KeyMapping CYCLE_TRADES_KEY = new KeyMapping("key.trade_cycling.cycle_trades", SDLScancode.SDL_SCANCODE_C, KEY_CATEGORY);
    public static final KeyMapping OPEN_AUTOTRADE_CONFIG_KEY = new KeyMapping("key.trade_cycling.open_autotrade_config", SDLScancode.SDL_SCANCODE_V, KEY_CATEGORY);

    private int serverAutoCycleAttempts;
    private int serverAutoCycleMaxAttempts;
    private List<ServerEnchantmentOption> serverEnchantmentOptions;
    private int serverReputation;

    public TradeCyclingClientMod() {
        serverAutoCycleAttempts = 0;
        serverAutoCycleMaxAttempts = 0;
        serverEnchantmentOptions = List.of();
    }

    public void clientInit() {
        registerKeyBindings();
        if (CONFIG != null) {
            AutoTradeHandler.setTradeCycleDelay(CONFIG.getTradeCycleDelayTicks());
            AutoTradeHandler.setMatchAny(CONFIG.getAutoTradeMatchAny());
            AutoTradeHandler.setMaxCycleCount(CONFIG.getMaxAutoTradeCycles());
        }
    }

    public abstract void sendCycleTradesPacket();

    public abstract TradeCyclingClientConfig createClientConfig();

    public void requestServerEnchantmentOptions() {
    }

    public int getServerReputation() {
        return serverReputation;
    }

    public void setServerReputation(int reputation) {
        serverReputation = reputation;
    }

    public void setServerEnchantmentOptions(List<ServerEnchantmentOption> options) {
        serverEnchantmentOptions = List.copyOf(options);
    }

    public List<ServerEnchantmentOption> getServerEnchantmentOptions() {
        return serverEnchantmentOptions;
    }

    public void registerKeyBindings() {

    }

    protected void startAutoCycle(MerchantScreen screen) {
        AutoTradeHandler.startCycleSession();
    }

    protected void stopAutoCycle() {
        AutoTradeHandler.stop();
        clearServerAutoCycleProgress();
    }

    public void updateServerAutoCycleProgress(int attempts, int maxAttempts) {
        serverAutoCycleAttempts = Math.max(0, attempts);
        serverAutoCycleMaxAttempts = Math.max(1, maxAttempts);
    }

    public void clearServerAutoCycleProgress() {
        serverAutoCycleAttempts = 0;
        serverAutoCycleMaxAttempts = 0;
    }

    public boolean hasServerAutoCycleProgress() {
        return serverAutoCycleMaxAttempts > 0 && AutoTradeHandler.isActive();
    }

    public int getServerAutoCycleAttempts() {
        return serverAutoCycleAttempts;
    }

    public int getServerAutoCycleMaxAttempts() {
        return serverAutoCycleMaxAttempts;
    }

    protected void showActionBarMessage(String message) {
        Minecraft.getInstance().gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.literal(message), false);
    }

    protected void showStatusMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.network.chat.Component component = net.minecraft.network.chat.Component.literal(message);
        mc.gui.hud.setOverlayMessage(component, false);
        if (mc.player != null && (CONFIG == null || CONFIG.getAnnounceAutoTradeResultsInChat())) {
            mc.player.sendSystemMessage(component);
        }
    }

    protected boolean canAutoCycle(MerchantScreen screen) {
        return CycleTradesButton.canCycle(screen.getMenu());
    }

    public <T extends AbstractWidget> void onOpenScreen(Screen screen, Consumer<T> eventConsumer) {
        if (!(screen instanceof MerchantScreen merchantScreen)) {
            return;
        }

        if (!(screen instanceof AbstractContainerScreenAccessor s)) {
            return;
        }

        TradeCyclingClientConfig.CycleTradesButtonLocation loc = CONFIG.getCycleTradesButtonLocation();

        if (loc.equals(TradeCyclingClientConfig.CycleTradesButtonLocation.NONE)) {
            return;
        }

        int posX;

        switch (loc) {
            case TOP_LEFT:
            default:
                posX = s.getLeftPos() + 107;
                break;
            case TOP_RIGHT:
                posX = s.getLeftPos() + 250;
                break;
        }

        int posY = s.getTopPos() + 8;
        eventConsumer.accept((T) new CycleTradesButton(posX, posY, b -> sendCycleTradesPacket(), merchantScreen));
        eventConsumer.accept((T) new AutoTradeConfigButton(posX, posY + CycleTradesButton.HEIGHT + 2));
        eventConsumer.accept((T) new AutoTradeProgressWidget(s.getLeftPos() + 50, Math.max(2, s.getTopPos() - 18), 176));
    }

    public void onKeyBindingPressed(KeyMapping keyMapping) {
        if (keyMapping == OPEN_AUTOTRADE_CONFIG_KEY) {
            Minecraft mc = Minecraft.getInstance();
            Screen currentScreen = mc.gui.screen();

            if (currentScreen == null || currentScreen instanceof MerchantScreen) {
                mc.gui.setScreen(new AutoTradeConfigScreen());
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
            }
            return;
        }

        if (keyMapping != CYCLE_TRADES_KEY) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Screen currentScreen = mc.gui.screen();

        if (!(currentScreen instanceof MerchantScreen screen)) {
            return;
        }

        if (!canAutoCycle(screen)) {
            return;
        }

        if (AutoTradeHandler.isActive()) {
            stopAutoCycle();
            showStatusMessage("Autotrade stopped");
        } else {
            startAutoCycle(screen);
        }

        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
    }

    public void onCycleKeyPressed(KeyEvent keyEvent, int action) {
        if (action != 1) {
            return;
        }
        if (OPEN_AUTOTRADE_CONFIG_KEY.matches(keyEvent)) {
            onKeyBindingPressed(OPEN_AUTOTRADE_CONFIG_KEY);
        } else if (CYCLE_TRADES_KEY.matches(keyEvent)) {
            onKeyBindingPressed(CYCLE_TRADES_KEY);
        }
    }

    public record ServerEnchantmentOption(Identifier id, int maxLevel, String displayName, boolean doublePrice) {
    }


}
