package de.maxhenkel.tradecycling.gui;

import de.maxhenkel.tradecycling.AutoTradeHandler;
import de.maxhenkel.tradecycling.TradeCyclingClientMod;
import de.maxhenkel.tradecycling.config.TradeCyclingClientConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class TradeCyclingOptionsScreen extends Screen {

    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 220;
    private static final int LABEL_COLOR = 0xFFE0E0E0;
    private static final int PANEL_COLOR = 0xD0202020;
    private static final int PANEL_BORDER = 0xFF8A8A8A;
    private static final int[] SPEED_PRESETS = new int[]{1, 2, 3, 5, 10, 20};

    private final Screen parent;

    private Button locationButton;
    private Button modeButton;
    private Button speedButton;
    private Button chatLogButton;
    private Button serverLogButton;
    private EditBox maxAttemptsBox;

    public TradeCyclingOptionsScreen(Screen parent) {
        super(Component.translatable("gui.trade_cycling.options"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = getPanelLeft();
        int top = getPanelTop();
        int contentLeft = left + 20;
        int controlLeft = left + 160;

        locationButton = addRenderableWidget(Button.builder(Component.empty(), button -> cycleButtonLocation())
                .bounds(controlLeft, top + 34, 180, 20)
                .build());
        updateLocationButton();

        modeButton = addRenderableWidget(Button.builder(Component.empty(), button -> toggleMode())
                .bounds(controlLeft, top + 60, 180, 20)
                .build());
        updateModeButton();

        speedButton = addRenderableWidget(Button.builder(Component.empty(), button -> cycleSpeed())
                .bounds(controlLeft, top + 86, 180, 20)
                .build());
        updateSpeedButton();

        maxAttemptsBox = addRenderableWidget(new EditBox(font, controlLeft, top + 112, 180, 20, Component.translatable("gui.trade_cycling.max_attempts")));
        maxAttemptsBox.setMaxLength(Integer.toString(Integer.MAX_VALUE).length());
        maxAttemptsBox.setValue(Integer.toString(AutoTradeHandler.getMaxCycleCount()));
        maxAttemptsBox.setResponder(value -> {
            String numericValue = filterDigits(value);
            if (!numericValue.equals(value)) {
                maxAttemptsBox.setValue(numericValue);
                return;
            }
            if (value.isBlank()) {
                return;
            }
            int maxAttempts = parsePositiveInt(value, AutoTradeHandler.getMaxCycleCount());
            AutoTradeHandler.setMaxCycleCount(maxAttempts);
            TradeCyclingClientMod.CONFIG.setMaxAutoTradeCycles(maxAttempts);
        });

        chatLogButton = addRenderableWidget(Button.builder(Component.empty(), button -> toggleChatLog())
                .bounds(controlLeft, top + 138, 180, 20)
                .build());
        updateChatLogButton();

        serverLogButton = addRenderableWidget(Button.builder(Component.empty(), button -> toggleServerLog())
                .bounds(controlLeft, top + 164, 180, 20)
                .build());
        updateServerLogButton();

        addRenderableWidget(Button.builder(Component.translatable("gui.trade_cycling.done"), button -> onClose())
                .bounds(left + PANEL_WIDTH / 2 - 50, top + PANEL_HEIGHT - 28, 100, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        int left = getPanelLeft();
        int top = getPanelTop();
        int right = left + PANEL_WIDTH;

        guiGraphics.fill(left, top, right, top + PANEL_HEIGHT, PANEL_COLOR);
        guiGraphics.horizontalLine(left, right - 1, top, PANEL_BORDER);
        guiGraphics.horizontalLine(left, right - 1, top + PANEL_HEIGHT - 1, PANEL_BORDER);
        guiGraphics.verticalLine(left, top, top + PANEL_HEIGHT - 1, PANEL_BORDER);
        guiGraphics.verticalLine(right - 1, top, top + PANEL_HEIGHT - 1, PANEL_BORDER);

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);

        int contentLeft = left + 20;
        guiGraphics.centeredText(font, title, left + PANEL_WIDTH / 2, top + 10, LABEL_COLOR);
        guiGraphics.text(font, Component.translatable("gui.trade_cycling.button_location"), contentLeft, top + 40, LABEL_COLOR);
        guiGraphics.text(font, Component.translatable("gui.trade_cycling.match_mode"), contentLeft, top + 66, LABEL_COLOR);
        guiGraphics.text(font, Component.translatable("gui.trade_cycling.speed"), contentLeft, top + 92, LABEL_COLOR);
        guiGraphics.text(font, Component.translatable("gui.trade_cycling.max_attempts"), contentLeft, top + 118, LABEL_COLOR);
        guiGraphics.text(font, Component.translatable("gui.trade_cycling.chat_log"), contentLeft, top + 144, LABEL_COLOR);
        guiGraphics.text(font, Component.translatable("gui.trade_cycling.server_log"), contentLeft, top + 170, LABEL_COLOR);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    private void cycleButtonLocation() {
        TradeCyclingClientConfig.CycleTradesButtonLocation current = TradeCyclingClientMod.CONFIG.getCycleTradesButtonLocation();
        TradeCyclingClientConfig.CycleTradesButtonLocation[] values = TradeCyclingClientConfig.CycleTradesButtonLocation.values();
        int nextIndex = (current.ordinal() + 1) % values.length;
        TradeCyclingClientMod.CONFIG.setCycleTradesButtonLocation(values[nextIndex]);
        updateLocationButton();
    }

    private void updateLocationButton() {
        locationButton.setMessage(Component.translatable("gui.trade_cycling.button_location." + TradeCyclingClientMod.CONFIG.getCycleTradesButtonLocation().name().toLowerCase(Locale.ROOT)));
    }

    private void toggleMode() {
        boolean matchAny = !AutoTradeHandler.isMatchAny();
        AutoTradeHandler.setMatchAny(matchAny);
        TradeCyclingClientMod.CONFIG.setAutoTradeMatchAny(matchAny);
        updateModeButton();
    }

    private void updateModeButton() {
        modeButton.setMessage(Component.translatable(AutoTradeHandler.isMatchAny() ? "gui.trade_cycling.mode_any" : "gui.trade_cycling.mode_all"));
    }

    private void cycleSpeed() {
        int presetIndex = getSpeedPresetIndex();
        int delay = SPEED_PRESETS[(presetIndex + 1) % SPEED_PRESETS.length];
        AutoTradeHandler.setTradeCycleDelay(delay);
        TradeCyclingClientMod.CONFIG.setTradeCycleDelayTicks(delay);
        updateSpeedButton();
    }

    private int getSpeedPresetIndex() {
        int delay = AutoTradeHandler.getTradeCycleDelay();
        for (int i = 0; i < SPEED_PRESETS.length; i++) {
            if (SPEED_PRESETS[i] == delay) {
                return i;
            }
        }
        return 0;
    }

    private void updateSpeedButton() {
        int delay = AutoTradeHandler.getTradeCycleDelay();
        speedButton.setMessage(Component.translatable(delay == 1 ? "gui.trade_cycling.speed_fastest" : "gui.trade_cycling.speed_ticks", delay));
    }

    private void toggleChatLog() {
        TradeCyclingClientMod.CONFIG.setAnnounceAutoTradeResultsInChat(!TradeCyclingClientMod.CONFIG.getAnnounceAutoTradeResultsInChat());
        updateChatLogButton();
    }

    private void updateChatLogButton() {
        chatLogButton.setMessage(Component.translatable(TradeCyclingClientMod.CONFIG.getAnnounceAutoTradeResultsInChat() ? "gui.trade_cycling.enabled" : "gui.trade_cycling.disabled"));
    }

    private void toggleServerLog() {
        TradeCyclingClientMod.CONFIG.setSendAutoTradeResultsToServer(!TradeCyclingClientMod.CONFIG.getSendAutoTradeResultsToServer());
        updateServerLogButton();
    }

    private void updateServerLogButton() {
        serverLogButton.setMessage(Component.translatable(TradeCyclingClientMod.CONFIG.getSendAutoTradeResultsToServer() ? "gui.trade_cycling.enabled" : "gui.trade_cycling.disabled"));
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String filterDigits(String value) {
        return value.chars()
                .filter(Character::isDigit)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private int getPanelLeft() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int getPanelTop() {
        return Math.max(12, (height - PANEL_HEIGHT) / 2);
    }
}
