package de.maxhenkel.tradecycling.gui;

import de.maxhenkel.tradecycling.AutoTradeHandler;
import de.maxhenkel.tradecycling.TradeCyclingClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AutoTradeConfigScreen extends Screen {

    private static final int PANEL_WIDTH = 460;
    private static final int PANEL_HEIGHT = 464;
    private static final int CONTENT_WIDTH = 436;
    private static final int SUGGESTION_ROWS = 4;
    private static final int TARGET_ROWS = 4;
    private static final int LABEL_COLOR = 0xFFE0E0E0;
    private static final int PANEL_COLOR = 0xD0202020;
    private static final int PANEL_BORDER = 0xFF8A8A8A;
    private static final int[] SPEED_PRESETS = new int[]{1, 2, 3, 5, 10, 20};

    private final Screen parent;

    private List<ItemOption> allItems;
    private List<EnchantmentOption> allEnchantments;
    private List<ItemOption> filteredItems;
    private List<EnchantmentOption> filteredEnchantments;

    private Button modeButton;
    private Button speedButton;
    private Button itemTradeSideButton;
    private Button itemSubmitButton;
    private Button enchantSubmitButton;
    private Button enchantLevelButton;
    private EditBox maxAttemptsBox;
    private EditBox itemSearchBox;
    private EditBox itemCostBox;
    private EditBox enchantSearchBox;
    private EditBox enchantCostBox;

    private final List<Button> itemSuggestionButtons;
    private final List<Button> enchantSuggestionButtons;
    private final List<Button> targetLabelButtons;
    private final List<Button> targetEditButtons;
    private final List<Button> targetRemoveButtons;

    private final List<TargetRow> targetRows;

    private ItemOption selectedItem;
    private AutoTradeHandler.ItemTradeSide selectedItemTradeSide;
    private EnchantmentOption selectedEnchantment;
    private int selectedEnchantLevel;
    private int editingItemIndex;
    private int editingEnchantIndex;
    private int itemSuggestionScroll;
    private int enchantSuggestionScroll;
    private int targetScroll;
    private String lastItemFilter;
    private String lastEnchantFilter;

    public AutoTradeConfigScreen() {
        this(Minecraft.getInstance().gui.screen());
    }

    public AutoTradeConfigScreen(Screen parent) {
        super(Component.translatable("gui.trade_cycling.autotrade_config"));
        this.parent = parent;
        this.allItems = List.of();
        this.allEnchantments = List.of();
        this.filteredItems = List.of();
        this.filteredEnchantments = List.of();
        this.itemSuggestionButtons = new ArrayList<>();
        this.enchantSuggestionButtons = new ArrayList<>();
        this.targetLabelButtons = new ArrayList<>();
        this.targetEditButtons = new ArrayList<>();
        this.targetRemoveButtons = new ArrayList<>();
        this.targetRows = new ArrayList<>();
        this.selectedItemTradeSide = AutoTradeHandler.ItemTradeSide.TRADED;
        this.selectedEnchantLevel = 1;
        this.editingItemIndex = -1;
        this.editingEnchantIndex = -1;
        this.lastItemFilter = "";
        this.lastEnchantFilter = "";
    }

    @Override
    protected void init() {
        if (TradeCyclingClientMod.INSTANCE != null) {
            TradeCyclingClientMod.INSTANCE.requestServerEnchantmentOptions();
        }
        allItems = loadItems();
        allEnchantments = loadEnchantments(Minecraft.getInstance());
        filteredItems = List.of();
        filteredEnchantments = List.of();
        targetRows.clear();

        int left = getPanelLeft();
        int top = getPanelTop();
        int contentLeft = left + 12;

        modeButton = addRenderableWidget(Button.builder(Component.empty(), button -> toggleMode())
                .bounds(contentLeft, top + 38, 142, 20)
                .build());
        updateModeButtonLabel();

        speedButton = addRenderableWidget(Button.builder(Component.empty(), button -> cycleSpeed())
                .bounds(contentLeft + 152, top + 38, 132, 20)
                .build());
        updateSpeedButtonLabel();

        maxAttemptsBox = createNumberBox(contentLeft + 294, top + 38, 142, Integer.toString(AutoTradeHandler.getMaxCycleCount()), value -> {
            if (!value.isBlank()) {
                int maxAttempts = parsePositiveInt(value, AutoTradeHandler.getMaxCycleCount());
                AutoTradeHandler.setMaxCycleCount(maxAttempts);
                TradeCyclingClientMod.CONFIG.setMaxAutoTradeCycles(maxAttempts);
            }
        });

        itemSearchBox = createTextBox(contentLeft, top + 82, 136, Component.translatable("gui.trade_cycling.search_items"));
        itemSearchBox.setResponder(value -> refreshItemSuggestions());

        itemTradeSideButton = addRenderableWidget(Button.builder(Component.empty(), button -> toggleItemTradeSide())
                .bounds(contentLeft + 142, top + 82, 64, 20)
                .build());
        updateItemTradeSideButton();

        itemCostBox = createNumberBox(contentLeft + 212, top + 82, 52, "", value -> {
        });
        itemCostBox.setHint(Component.translatable("gui.trade_cycling.max_cost"));

        itemSubmitButton = addRenderableWidget(Button.builder(Component.empty(), button -> addOrSaveItemTarget())
                .bounds(contentLeft + 352, top + 82, 84, 20)
                .build());

        itemSuggestionButtons.clear();
        for (int i = 0; i < SUGGESTION_ROWS; i++) {
            final int index = i;
            itemSuggestionButtons.add(addRenderableWidget(Button.builder(Component.empty(), button -> selectItemSuggestion(index))
                    .bounds(contentLeft, top + 110 + i * 20, CONTENT_WIDTH, 18)
                    .build()));
        }

        enchantSearchBox = createTextBox(contentLeft, top + 216, 136, Component.translatable("gui.trade_cycling.search_enchants"));
        enchantSearchBox.setResponder(value -> refreshEnchantmentSuggestions());

        enchantLevelButton = addRenderableWidget(Button.builder(Component.empty(), button -> cycleEnchantLevel())
                .bounds(contentLeft + 142, top + 216, 64, 20)
                .build());
        updateEnchantLevelButton();

        enchantCostBox = createNumberBox(contentLeft + 212, top + 216, 52, "", value -> {
        });
        enchantCostBox.setHint(Component.translatable("gui.trade_cycling.max_cost"));

        enchantSubmitButton = addRenderableWidget(Button.builder(Component.empty(), button -> addOrSaveEnchantmentTarget())
                .bounds(contentLeft + 352, top + 216, 84, 20)
                .build());

        enchantSuggestionButtons.clear();
        for (int i = 0; i < SUGGESTION_ROWS; i++) {
            final int index = i;
            enchantSuggestionButtons.add(addRenderableWidget(Button.builder(Component.empty(), button -> selectEnchantmentSuggestion(index))
                    .bounds(contentLeft, top + 244 + i * 20, CONTENT_WIDTH, 18)
                    .build()));
        }

        targetLabelButtons.clear();
        targetEditButtons.clear();
        targetRemoveButtons.clear();
        for (int i = 0; i < TARGET_ROWS; i++) {
            final int index = i;
            int rowY = top + 346 + i * 22;
            Button labelButton = addRenderableWidget(Button.builder(Component.empty(), button -> editTarget(index))
                    .bounds(contentLeft, rowY, 300, 20)
                    .build());
            Button editButton = addRenderableWidget(Button.builder(Component.literal("Edit"), button -> editTarget(index))
                    .bounds(contentLeft + 308, rowY, 60, 20)
                    .build());
            Button removeButton = addRenderableWidget(Button.builder(Component.literal("Remove"), button -> removeTarget(index))
                    .bounds(contentLeft + 376, rowY, 60, 20)
                    .build());
            targetLabelButtons.add(labelButton);
            targetEditButtons.add(editButton);
            targetRemoveButtons.add(removeButton);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.trade_cycling.done"), button -> onClose())
                .bounds(left + PANEL_WIDTH / 2 - 50, top + PANEL_HEIGHT - 28, 100, 20)
                .build());

        refreshItemSubmitButton();
        refreshEnchantmentSubmitButton();
        refreshItemSuggestions();
        refreshEnchantmentSuggestions();
        refreshTargetRows();
        setInitialFocus(itemSearchBox);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        int left = getPanelLeft();
        int top = getPanelTop();
        int right = left + PANEL_WIDTH;
        int contentLeft = left + 12;

        guiGraphics.fill(left, top, right, top + PANEL_HEIGHT, PANEL_COLOR);
        guiGraphics.horizontalLine(left, right - 1, top, PANEL_BORDER);
        guiGraphics.horizontalLine(left, right - 1, top + PANEL_HEIGHT - 1, PANEL_BORDER);
        guiGraphics.verticalLine(left, top, top + PANEL_HEIGHT - 1, PANEL_BORDER);
        guiGraphics.verticalLine(right - 1, top, top + PANEL_HEIGHT - 1, PANEL_BORDER);

        drawSectionBox(guiGraphics, contentLeft, top + 108, CONTENT_WIDTH, 84);
        drawSectionBox(guiGraphics, contentLeft, top + 242, CONTENT_WIDTH, 84);
        drawSectionBox(guiGraphics, contentLeft, top + 344, CONTENT_WIDTH, 90);

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);

        drawCentered(guiGraphics, title, left + PANEL_WIDTH / 2, top + 10);
        drawCentered(guiGraphics, Component.translatable("gui.trade_cycling.match_mode"), contentLeft + 71, top + 26);
        drawCentered(guiGraphics, Component.translatable("gui.trade_cycling.speed"), contentLeft + 218, top + 26);
        drawCentered(guiGraphics, Component.translatable("gui.trade_cycling.max_attempts"), contentLeft + 365, top + 26);

        drawCentered(guiGraphics, Component.translatable("gui.trade_cycling.item_target"), left + PANEL_WIDTH / 2, top + 62);
        guiGraphics.text(font, Component.translatable("gui.trade_cycling.search_items_label"), contentLeft, top + 72, 0xB8B8B8);
        guiGraphics.text(font, Component.translatable("gui.trade_cycling.item_side"), contentLeft + 142, top + 72, 0xB8B8B8);
        guiGraphics.text(font, Component.translatable("gui.trade_cycling.max_cost"), contentLeft + 212, top + 72, 0xB8B8B8);
        drawCentered(guiGraphics, Component.translatable("gui.trade_cycling.enchant_target"), left + PANEL_WIDTH / 2, top + 196);
        guiGraphics.text(font, Component.translatable("gui.trade_cycling.search_enchants_label"), contentLeft, top + 206, 0xB8B8B8);
        guiGraphics.text(font, Component.translatable("gui.trade_cycling.enchant_level"), contentLeft + 142, top + 206, 0xB8B8B8);
        guiGraphics.text(font, Component.translatable("gui.trade_cycling.max_cost"), contentLeft + 212, top + 206, 0xB8B8B8);
        EnchantmentOption shown = selectedEnchantment;
        int min = minPrice(shown, selectedEnchantLevel);
        int typed = parseOptionalCost(enchantCostBox.getValue());
        boolean impossible = min > 0 && typed < min;
        enchantCostBox.setTextColor(impossible ? 0xFFFF5555 : 0xFFE0E0E0);
        if (min > 0) {
            guiGraphics.text(font, Component.translatable(impossible ? "gui.trade_cycling.below_min" : "gui.trade_cycling.min_price", min), contentLeft + 270, top + 222, impossible ? 0xFFFF5555 : 0xFF8FD694);
        }
        drawCentered(guiGraphics, Component.translatable("gui.trade_cycling.targets"), left + PANEL_WIDTH / 2, top + 332);

        if (targetRows.size() > TARGET_ROWS) {
            int first = targetScroll + 1;
            int last = Math.min(targetScroll + TARGET_ROWS, targetRows.size());
            guiGraphics.text(font, Component.literal(first + "-" + last + " / " + targetRows.size()), contentLeft + 350, top + 332, 0xB0B0B0);
        } else if (!targetRows.isEmpty()) {
            guiGraphics.text(font, Component.literal(targetRows.size() + (targetRows.size() == 1 ? " target" : " targets")), contentLeft + 350, top + 332, 0xB0B0B0);
        }

        extractTargetRows(guiGraphics, contentLeft, top, mouseX, mouseY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0D) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        int direction = scrollY > 0D ? -1 : 1;
        int left = getPanelLeft();
        int top = getPanelTop();
        int contentLeft = left + 12;

        if (isInBounds(mouseX, mouseY, contentLeft, top + 108, CONTENT_WIDTH, 84)) {
            int newScroll = clampScroll(itemSuggestionScroll + direction, filteredItems.size(), SUGGESTION_ROWS);
            if (newScroll != itemSuggestionScroll) {
                itemSuggestionScroll = newScroll;
                refreshItemSuggestions();
                return true;
            }
        }

        if (isInBounds(mouseX, mouseY, contentLeft, top + 242, CONTENT_WIDTH, 84)) {
            int newScroll = clampScroll(enchantSuggestionScroll + direction, filteredEnchantments.size(), SUGGESTION_ROWS);
            if (newScroll != enchantSuggestionScroll) {
                enchantSuggestionScroll = newScroll;
                refreshEnchantmentSuggestions();
                return true;
            }
        }

        if (isInBounds(mouseX, mouseY, contentLeft, top + 344, CONTENT_WIDTH, 90)) {
            int newScroll = clampScroll(targetScroll + direction, targetRows.size(), TARGET_ROWS);
            if (newScroll != targetScroll) {
                targetScroll = newScroll;
                updateTargetButtons();
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void drawSectionBox(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, 0x30000000);
        guiGraphics.horizontalLine(x, x + width - 1, y, 0xFF5A5A5A);
        guiGraphics.horizontalLine(x, x + width - 1, y + height - 1, 0xFF5A5A5A);
        guiGraphics.verticalLine(x, y, y + height - 1, 0xFF5A5A5A);
        guiGraphics.verticalLine(x + width - 1, y, y + height - 1, 0xFF5A5A5A);
    }

    private boolean isInBounds(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int clampScroll(int scroll, int totalRows, int visibleRows) {
        return Math.clamp(scroll, 0, Math.max(0, totalRows - visibleRows));
    }

    private void toggleMode() {
        boolean matchAny = !AutoTradeHandler.isMatchAny();
        AutoTradeHandler.setMatchAny(matchAny);
        TradeCyclingClientMod.CONFIG.setAutoTradeMatchAny(matchAny);
        updateModeButtonLabel();
    }

    private void updateModeButtonLabel() {
        modeButton.setMessage(Component.translatable(AutoTradeHandler.isMatchAny()
                ? "gui.trade_cycling.mode_any"
                : "gui.trade_cycling.mode_all"));
    }

    private void toggleItemTradeSide() {
        selectedItemTradeSide = selectedItemTradeSide == AutoTradeHandler.ItemTradeSide.TRADED
                ? AutoTradeHandler.ItemTradeSide.TRADING
                : AutoTradeHandler.ItemTradeSide.TRADED;
        updateItemTradeSideButton();
    }

    private void updateItemTradeSideButton() {
        if (itemTradeSideButton == null) {
            return;
        }
        itemTradeSideButton.setMessage(Component.translatable(selectedItemTradeSide == AutoTradeHandler.ItemTradeSide.TRADING
                ? "gui.trade_cycling.item_side.trading"
                : "gui.trade_cycling.item_side.traded"));
    }

    private void cycleSpeed() {
        int presetIndex = getSpeedPresetIndex();
        int delay = SPEED_PRESETS[(presetIndex + 1) % SPEED_PRESETS.length];
        AutoTradeHandler.setTradeCycleDelay(delay);
        TradeCyclingClientMod.CONFIG.setTradeCycleDelayTicks(delay);
        updateSpeedButtonLabel();
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

    private void updateSpeedButtonLabel() {
        int delay = AutoTradeHandler.getTradeCycleDelay();
        Component label;
        if (delay == 1) {
            label = Component.translatable("gui.trade_cycling.speed_fastest", delay);
        } else {
            label = Component.translatable("gui.trade_cycling.speed_ticks", delay);
        }
        speedButton.setMessage(label);
    }

    private void addOrSaveItemTarget() {
        ItemOption option = resolveSelectedItem();
        if (option == null) {
            return;
        }

        int maxCost = parseOptionalCost(itemCostBox.getValue());
        if (editingItemIndex >= 0) {
            AutoTradeHandler.replaceItemTarget(editingItemIndex, option.item, maxCost, selectedItemTradeSide, false);
        } else {
            AutoTradeHandler.start(option.item, maxCost, selectedItemTradeSide, false);
        }

        clearItemEditor();
        refreshTargetRows();
    }

    private void addOrSaveEnchantmentTarget() {
        EnchantmentOption option = resolveSelectedEnchantment();
        if (option == null) {
            return;
        }

        int minLevel = clampEnchantLevel(selectedEnchantLevel, option);
        int maxCost = parseOptionalCost(enchantCostBox.getValue());
        if (editingEnchantIndex >= 0) {
            AutoTradeHandler.replaceEnchantTarget(editingEnchantIndex, option.key, minLevel, maxCost, false);
        } else {
            AutoTradeHandler.start(option.key, minLevel, maxCost, false);
        }

        clearEnchantmentEditor();
        refreshTargetRows();
    }

    private void clearItemEditor() {
        editingItemIndex = -1;
        selectedItem = null;
        selectedItemTradeSide = AutoTradeHandler.ItemTradeSide.TRADED;
        itemSearchBox.setValue("");
        itemCostBox.setValue("");
        updateItemTradeSideButton();
        refreshItemSubmitButton();
        refreshItemSuggestions();
    }

    private void clearEnchantmentEditor() {
        editingEnchantIndex = -1;
        selectedEnchantment = null;
        enchantSearchBox.setValue("");
        selectedEnchantLevel = 1;
        enchantCostBox.setValue("");
        updateEnchantLevelButton();
        refreshEnchantmentSubmitButton();
        refreshEnchantmentSuggestions();
    }

    private void refreshItemSubmitButton() {
        itemSubmitButton.setMessage(Component.literal(editingItemIndex >= 0 ? "Save Item" : "Add Item"));
    }

    private void refreshEnchantmentSubmitButton() {
        enchantSubmitButton.setMessage(Component.literal(editingEnchantIndex >= 0 ? "Save Enchant" : "Add Enchant"));
    }

    private void refreshItemSuggestions() {
        String filter = normalize(itemSearchBox.getValue());
        if (!filter.equals(lastItemFilter)) {
            itemSuggestionScroll = 0;
            lastItemFilter = filter;
        }
        filteredItems = allItems.stream()
                .filter(option -> option.matches(filter))
                .toList();
        itemSuggestionScroll = clampScroll(itemSuggestionScroll, filteredItems.size(), SUGGESTION_ROWS);

        if (selectedItem != null && !filteredItems.contains(selectedItem)) {
            selectedItem = null;
        }

        refreshSuggestionButtons(itemSuggestionButtons, filteredItems, itemSuggestionScroll, selectedItem == null ? null : selectedItem.id.toString());
    }

    private void refreshEnchantmentSuggestions() {
        String filter = normalize(enchantSearchBox.getValue());
        if (!filter.equals(lastEnchantFilter)) {
            enchantSuggestionScroll = 0;
            lastEnchantFilter = filter;
        }
        filteredEnchantments = allEnchantments.stream()
                .filter(option -> option.matches(filter))
                .toList();
        enchantSuggestionScroll = clampScroll(enchantSuggestionScroll, filteredEnchantments.size(), SUGGESTION_ROWS);

        if (selectedEnchantment != null && !filteredEnchantments.contains(selectedEnchantment)) {
            selectedEnchantment = null;
            selectedEnchantLevel = 1;
        }

        refreshSuggestionButtons(enchantSuggestionButtons, filteredEnchantments, enchantSuggestionScroll, selectedEnchantment == null ? null : selectedEnchantment.idString);
        updateEnchantLevelButton();
    }

    private void refreshSuggestionButtons(List<Button> buttons, List<? extends SuggestionOption> options, int scroll, String selectedId) {
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            int optionIndex = scroll + i;
            if (optionIndex >= options.size()) {
                button.visible = false;
                button.active = false;
                button.setMessage(Component.empty());
                continue;
            }

            SuggestionOption option = options.get(optionIndex);
            String label = option.displayId();
            if (label.equals(selectedId)) {
                label = "> " + label;
            }

            button.visible = true;
            button.active = true;
            button.setMessage(Component.literal(font.plainSubstrByWidth(label, button.getWidth() - 10)));
        }
    }

    private void selectItemSuggestion(int index) {
        int optionIndex = itemSuggestionScroll + index;
        if (optionIndex < 0 || optionIndex >= filteredItems.size()) {
            return;
        }
        selectedItem = filteredItems.get(optionIndex);
        itemSearchBox.setValue(selectedItem.id.toString());
        refreshItemSuggestions();
    }

    private void selectEnchantmentSuggestion(int index) {
        int optionIndex = enchantSuggestionScroll + index;
        if (optionIndex < 0 || optionIndex >= filteredEnchantments.size()) {
            return;
        }
        selectedEnchantment = filteredEnchantments.get(optionIndex);
        enchantSearchBox.setValue(selectedEnchantment.idString);
        selectedEnchantLevel = selectedEnchantment.maxLevel;
        updateEnchantLevelButton();
        fillCheapestCost();
        refreshEnchantmentSuggestions();
    }

    private void refreshTargetRows() {
        targetRows.clear();

        for (int i = 0; i < AutoTradeHandler.getItemTargets().size(); i++) {
            final int index = i;
            AutoTradeHandler.ItemTarget target = AutoTradeHandler.getItemTargets().get(i);
            ItemStack icon = new ItemStack(target.item);
            List<Chip> chips = new ArrayList<>();
            chips.add(new Chip(Component.translatable(target.tradeSide == AutoTradeHandler.ItemTradeSide.TRADING ? "gui.trade_cycling.item_side.trading" : "gui.trade_cycling.item_side.traded").getString(), 0xFF6FA8DC));
            if (target.maxEmeralds != Integer.MAX_VALUE) {
                chips.add(new Chip("<= " + target.maxEmeralds + "e", 0xFFE6C34A));
            }
            targetRows.add(new TargetRow(
                    icon,
                    icon.getHoverName().getString(),
                    BuiltInRegistries.ITEM.getKey(target.item).toString(),
                    chips,
                    () -> beginEditItem(index),
                    () -> {
                        AutoTradeHandler.removeItemTarget(index);
                        if (editingItemIndex == index) {
                            clearItemEditor();
                        } else if (editingItemIndex > index) {
                            editingItemIndex--;
                        }
                        refreshTargetRows();
                    }
            ));
        }

        for (int i = 0; i < AutoTradeHandler.getEnchantTargets().size(); i++) {
            final int index = i;
            AutoTradeHandler.EnchantTarget target = AutoTradeHandler.getEnchantTargets().get(i);
            EnchantmentOption option = findEnchantmentOption(target.enchantKey);
            String id = AutoTradeHandler.keyToIdString(target.enchantKey);
            List<Chip> chips = new ArrayList<>();
            chips.add(new Chip("Lv " + target.minLevel + "+", 0xFFC792EA));
            int min = minPrice(option, target.minLevel);
            if (target.maxEmeralds != Integer.MAX_VALUE) {
                chips.add(new Chip("<= " + target.maxEmeralds + "e", min > 0 && target.maxEmeralds < min ? 0xFFFF5555 : 0xFFE6C34A));
            }
            if (min > 0 && target.maxEmeralds < min) {
                chips.add(new Chip(Component.translatable("gui.trade_cycling.below_min", min).getString(), 0xFFFF5555));
            }
            targetRows.add(new TargetRow(
                    new ItemStack(Items.ENCHANTED_BOOK),
                    option == null ? id : option.displayName,
                    id,
                    chips,
                    () -> beginEditEnchant(index),
                    () -> {
                        AutoTradeHandler.removeEnchantTarget(index);
                        if (editingEnchantIndex == index) {
                            clearEnchantmentEditor();
                        } else if (editingEnchantIndex > index) {
                            editingEnchantIndex--;
                        }
                        refreshTargetRows();
                    }
            ));
        }

        targetScroll = clampScroll(targetScroll, targetRows.size(), TARGET_ROWS);
        updateTargetButtons();
    }

    private void updateTargetButtons() {
        for (int i = 0; i < TARGET_ROWS; i++) {
            Button labelButton = targetLabelButtons.get(i);
            Button editButton = targetEditButtons.get(i);
            Button removeButton = targetRemoveButtons.get(i);

            int targetIndex = targetScroll + i;
            if (targetIndex >= targetRows.size()) {
                labelButton.visible = false;
                editButton.visible = false;
                removeButton.visible = false;
                continue;
            }

            TargetRow row = targetRows.get(targetIndex);
            labelButton.visible = true;
            labelButton.active = true;
            labelButton.setMessage(Component.empty());
            editButton.visible = true;
            editButton.active = true;
            removeButton.visible = true;
            removeButton.active = true;
        }
    }

    private void editTarget(int rowIndex) {
        int targetIndex = targetScroll + rowIndex;
        if (targetIndex < 0 || targetIndex >= targetRows.size()) {
            return;
        }
        targetRows.get(targetIndex).editAction.run();
    }

    private void removeTarget(int rowIndex) {
        int targetIndex = targetScroll + rowIndex;
        if (targetIndex < 0 || targetIndex >= targetRows.size()) {
            return;
        }
        targetRows.get(targetIndex).removeAction.run();
    }

    private void beginEditItem(int index) {
        if (index < 0 || index >= AutoTradeHandler.getItemTargets().size()) {
            return;
        }
        AutoTradeHandler.ItemTarget target = AutoTradeHandler.getItemTargets().get(index);
        editingItemIndex = index;
        selectedItem = findItemOption(target.item);
        selectedItemTradeSide = target.tradeSide;
        itemSearchBox.setValue(BuiltInRegistries.ITEM.getKey(target.item).toString());
        itemCostBox.setValue(target.maxEmeralds == Integer.MAX_VALUE ? "" : Integer.toString(target.maxEmeralds));
        updateItemTradeSideButton();
        refreshItemSubmitButton();
        refreshItemSuggestions();
    }

    private void beginEditEnchant(int index) {
        if (index < 0 || index >= AutoTradeHandler.getEnchantTargets().size()) {
            return;
        }
        AutoTradeHandler.EnchantTarget target = AutoTradeHandler.getEnchantTargets().get(index);
        editingEnchantIndex = index;
        selectedEnchantment = findEnchantmentOption(target.enchantKey);
        enchantSearchBox.setValue(AutoTradeHandler.keyToIdString(target.enchantKey));
        selectedEnchantLevel = selectedEnchantment == null ? Math.max(1, target.minLevel) : clampEnchantLevel(target.minLevel, selectedEnchantment);
        updateEnchantLevelButton();
        enchantCostBox.setValue(target.maxEmeralds == Integer.MAX_VALUE ? "" : Integer.toString(target.maxEmeralds));
        refreshEnchantmentSubmitButton();
        refreshEnchantmentSuggestions();
    }

    private ItemOption resolveSelectedItem() {
        if (selectedItem != null) {
            return selectedItem;
        }
        String value = normalize(itemSearchBox.getValue());
        for (ItemOption option : allItems) {
            if (option.id.toString().equals(value)) {
                return option;
            }
        }
        return filteredItems.isEmpty() ? null : filteredItems.get(0);
    }

    private EnchantmentOption resolveSelectedEnchantment() {
        if (selectedEnchantment != null) {
            return selectedEnchantment;
        }
        String value = normalize(enchantSearchBox.getValue());
        for (EnchantmentOption option : allEnchantments) {
            if (option.idString.equals(value)) {
                return option;
            }
        }
        return filteredEnchantments.isEmpty() ? null : filteredEnchantments.get(0);
    }

    public void reloadEnchantmentOptions() {
        allEnchantments = loadEnchantments(Minecraft.getInstance());
        if (selectedEnchantment != null) {
            selectedEnchantment = findEnchantmentOption(selectedEnchantment.key);
            if (selectedEnchantment != null) {
                selectedEnchantLevel = clampEnchantLevel(selectedEnchantLevel, selectedEnchantment);
            }
        }
        refreshEnchantmentSuggestions();
        updateEnchantLevelButton();
    }

    private ItemOption findItemOption(Item item) {
        for (ItemOption option : allItems) {
            if (option.item == item) {
                return option;
            }
        }
        return null;
    }

    private EnchantmentOption findEnchantmentOption(ResourceKey<Enchantment> key) {
        for (EnchantmentOption option : allEnchantments) {
            if (option.key.equals(key)) {
                return option;
            }
        }
        return null;
    }

    private EditBox createTextBox(int x, int y, int width, Component hint) {
        EditBox editBox = addRenderableWidget(new EditBox(font, x, y, width, 20, hint));
        editBox.setHint(hint);
        editBox.setMaxLength(128);
        return editBox;
    }

    private EditBox createNumberBox(int x, int y, int width, String value, java.util.function.Consumer<String> responder) {
        EditBox editBox = addRenderableWidget(new EditBox(font, x, y, width, 20, Component.empty()));
        editBox.setMaxLength(Integer.toString(Integer.MAX_VALUE).length());
        editBox.setValue(value);
        editBox.setResponder(text -> {
            String numericText = filterDigits(text);
            if (!numericText.equals(text)) {
                editBox.setValue(numericText);
                return;
            }
            responder.accept(text);
        });
        return editBox;
    }

    private void cycleEnchantLevel() {
        EnchantmentOption option = resolveSelectedEnchantment();
        if (option == null) {
            selectedEnchantLevel = 1;
        } else {
            selectedEnchantLevel++;
            if (selectedEnchantLevel > option.maxLevel) {
                selectedEnchantLevel = 1;
            }
        }
        updateEnchantLevelButton();
        fillCheapestCost();
    }

    /** Lowest price this player can possibly be offered for the book (discounts included), or 0 if unknown. */
    private int minPrice(EnchantmentOption option, int level) {
        if (option == null) {
            return 0;
        }
        int base = Math.min(64, (2 + 3 * level) * (option.doublePrice ? 2 : 1));
        int diff = TradeCyclingClientMod.INSTANCE == null ? 0 : -Mth.floor(TradeCyclingClientMod.INSTANCE.getServerReputation() * 0.2F);
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        MobEffectInstance hero = player == null ? null : player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        if (hero != null) {
            diff -= Math.max(Mth.floor((0.3F + 0.0625F * hero.getAmplifier()) * base), 1);
        }
        return Math.clamp(base + diff, 1, 64);
    }

    private void fillCheapestCost() {
        int min = minPrice(selectedEnchantment, selectedEnchantLevel);
        if (min > 0) {
            enchantCostBox.setValue(Integer.toString(min));
        }
    }

    private void updateEnchantLevelButton() {
        if (enchantLevelButton == null) {
            return;
        }
        EnchantmentOption option = selectedEnchantment;
        int maxLevel = option == null ? 1 : option.maxLevel;
        selectedEnchantLevel = Math.clamp(selectedEnchantLevel, 1, maxLevel);
        enchantLevelButton.active = option != null;
        enchantLevelButton.setMessage(Component.literal(selectedEnchantLevel + "/" + maxLevel));
    }

    private int clampEnchantLevel(int level, EnchantmentOption option) {
        return Math.clamp(level, 1, Math.max(1, option.maxLevel));
    }

    private int parseOptionalCost(String value) {
        if (value == null || value.isBlank()) {
            return Integer.MAX_VALUE;
        }
        return parsePositiveInt(value, Integer.MAX_VALUE);
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void extractTargetRows(GuiGraphicsExtractor guiGraphics, int contentLeft, int top, int mouseX, int mouseY) {
        if (targetRows.isEmpty()) {
            int boxTop = top + 346;
            guiGraphics.centeredText(font, Component.translatable("gui.trade_cycling.no_targets"), contentLeft + CONTENT_WIDTH / 2, boxTop + 18, 0xFF9A9A9A);
            guiGraphics.centeredText(font, Component.translatable("gui.trade_cycling.no_targets_hint"), contentLeft + CONTENT_WIDTH / 2, boxTop + 32, 0xFF707070);
            return;
        }

        for (int i = 0; i < TARGET_ROWS; i++) {
            int targetIndex = targetScroll + i;
            if (targetIndex >= targetRows.size()) {
                break;
            }
            TargetRow row = targetRows.get(targetIndex);
            int rowY = top + 346 + i * 22;
            boolean hovered = isInBounds(mouseX, mouseY, contentLeft, rowY, 300, 20);
            boolean editing = (row.icon.is(Items.ENCHANTED_BOOK) ? editingEnchantIndex : editingItemIndex) >= 0 && isEditingRow(targetIndex);

            guiGraphics.fill(contentLeft, rowY, contentLeft + 300, rowY + 20, editing ? 0xFF3C4A3A : hovered ? 0xFF3A3A3A : 0xFF262626);
            guiGraphics.fill(contentLeft, rowY, contentLeft + 2, rowY + 20, row.chips.isEmpty() ? 0xFF5A5A5A : row.chips.get(0).color);
            guiGraphics.fakeItem(row.icon, contentLeft + 5, rowY + 2);

            int chipRight = contentLeft + 296;
            for (int c = row.chips.size() - 1; c >= 0; c--) {
                Chip chip = row.chips.get(c);
                int chipWidth = font.width(chip.text) + 8;
                int chipLeft = chipRight - chipWidth;
                guiGraphics.fill(chipLeft, rowY + 4, chipRight, rowY + 16, 0xFF141414);
                guiGraphics.fill(chipLeft, rowY + 4, chipLeft + 1, rowY + 16, chip.color);
                guiGraphics.text(font, chip.text, chipLeft + 4, rowY + 6, chip.color, false);
                chipRight = chipLeft - 3;
            }

            int textLeft = contentLeft + 26;
            int textWidth = Math.max(20, chipRight - textLeft - 4);
            guiGraphics.text(font, font.plainSubstrByWidth(row.title, textWidth), textLeft, rowY + 6, 0xFFEDEDED, false);
        }
    }

    private boolean isEditingRow(int rowIndex) {
        int itemCount = AutoTradeHandler.getItemTargets().size();
        return rowIndex < itemCount ? rowIndex == editingItemIndex : rowIndex - itemCount == editingEnchantIndex;
    }

    private int getPanelLeft() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int getPanelTop() {
        return Math.max(12, (height - PANEL_HEIGHT) / 2);
    }

    private void drawCentered(GuiGraphicsExtractor guiGraphics, Component text, int centerX, int y) {
        guiGraphics.centeredText(font, text, centerX, y, LABEL_COLOR);
    }

    private String filterDigits(String value) {
        return value.chars()
                .filter(Character::isDigit)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private List<ItemOption> loadItems() {
        List<ItemOption> options = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            String displayName = new ItemStack(item).getHoverName().getString();
            options.add(new ItemOption(item, id, displayName));
        }
        options.sort(Comparator.comparing(option -> option.id.toString()));
        return options;
    }

    private List<EnchantmentOption> loadEnchantments(Minecraft minecraft) {
        List<EnchantmentOption> options = new ArrayList<>();
        List<TradeCyclingClientMod.ServerEnchantmentOption> serverOptions = TradeCyclingClientMod.INSTANCE == null ? List.of() : TradeCyclingClientMod.INSTANCE.getServerEnchantmentOptions();
        if (!serverOptions.isEmpty()) {
            for (TradeCyclingClientMod.ServerEnchantmentOption serverOption : serverOptions) {
                ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, serverOption.id());
                options.add(new EnchantmentOption(key, serverOption.id().toString(), serverOption.displayName(), Math.max(1, serverOption.maxLevel()), serverOption.doublePrice()));
            }
            options.sort(Comparator.comparing(option -> option.idString));
            return options;
        }
        if (minecraft.level == null) {
            return options;
        }
        minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(reference -> {
            ResourceKey<Enchantment> key = reference.key();
            options.add(new EnchantmentOption(key, AutoTradeHandler.keyToIdString(key), reference.value().description().getString(), Math.max(1, reference.value().getMaxLevel()), reference.is(EnchantmentTags.DOUBLE_TRADE_PRICE)));
        });
        options.sort(Comparator.comparing(option -> option.idString));
        return options;
    }

    private interface SuggestionOption {
        String displayId();
    }

    private static class ItemOption implements SuggestionOption {
        private final Item item;
        private final Identifier id;
        private final String displayName;

        private ItemOption(Item item, Identifier id, String displayName) {
            this.item = item;
            this.id = id;
            this.displayName = displayName;
        }

        private boolean matches(String filter) {
            return filter.isEmpty()
                    || id.toString().contains(filter)
                    || id.getPath().contains(filter)
                    || displayName.toLowerCase(Locale.ROOT).contains(filter);
        }

        @Override
        public String displayId() {
            return id.toString();
        }
    }

    private static class EnchantmentOption implements SuggestionOption {
        private final ResourceKey<Enchantment> key;
        private final String idString;
        private final String displayName;
        private final int maxLevel;
        private final boolean doublePrice;

        private EnchantmentOption(ResourceKey<Enchantment> key, String idString, String displayName, int maxLevel, boolean doublePrice) {
            this.doublePrice = doublePrice;
            this.key = key;
            this.idString = idString;
            this.displayName = displayName;
            this.maxLevel = maxLevel;
        }

        private boolean matches(String filter) {
            return filter.isEmpty()
                    || idString.contains(filter)
                    || displayName.toLowerCase(Locale.ROOT).contains(filter);
        }

        @Override
        public String displayId() {
            return idString;
        }
    }

    private record Chip(String text, int color) {
    }

    private static class TargetRow {
        private final ItemStack icon;
        private final String title;
        private final String id;
        private final List<Chip> chips;
        private final Runnable editAction;
        private final Runnable removeAction;

        private TargetRow(ItemStack icon, String title, String id, List<Chip> chips, Runnable editAction, Runnable removeAction) {
            this.icon = icon;
            this.title = title;
            this.id = id;
            this.chips = chips;
            this.editAction = editAction;
            this.removeAction = removeAction;
        }
    }
}
