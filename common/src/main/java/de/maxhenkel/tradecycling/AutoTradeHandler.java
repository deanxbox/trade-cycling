package de.maxhenkel.tradecycling;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

public class AutoTradeHandler {

    public static class ItemTarget {
        public final Item item;
        public final int maxEmeralds;
        public final ItemTradeSide tradeSide;
        public final boolean cheapest;

        public ItemTarget(Item item, int maxEmeralds) {
            this(item, maxEmeralds, ItemTradeSide.TRADED);
        }

        public ItemTarget(Item item, int maxEmeralds, ItemTradeSide tradeSide) {
            this(item, maxEmeralds, tradeSide, false);
        }

        public ItemTarget(Item item, int maxEmeralds, ItemTradeSide tradeSide, boolean cheapest) {
            this.item = item;
            this.maxEmeralds = maxEmeralds;
            this.tradeSide = tradeSide == null ? ItemTradeSide.TRADED : tradeSide;
            this.cheapest = cheapest;
        }
    }

    public enum ItemTradeSide {
        TRADED,
        TRADING
    }

    public static class EnchantTarget {
        public final ResourceKey<Enchantment> enchantKey;
        public final int minLevel;
        public final int maxEmeralds;
        public final boolean cheapest;
        /** Lowest effective price this target can possibly have for this villager/player; 0 = unknown. */
        public final int floorEmeralds;

        public EnchantTarget(ResourceKey<Enchantment> enchantKey, int minLevel, int maxEmeralds) {
            this(enchantKey, minLevel, maxEmeralds, false, 0);
        }

        public EnchantTarget(ResourceKey<Enchantment> enchantKey, int minLevel, int maxEmeralds, boolean cheapest, int floorEmeralds) {
            this.enchantKey = enchantKey;
            this.minLevel = minLevel;
            this.maxEmeralds = maxEmeralds;
            this.cheapest = cheapest;
            this.floorEmeralds = floorEmeralds;
        }
    }

    public static class TargetMatch {
        public final String targetDescription;
        public final int offerIndex;
        public final int emeraldCost;
        public final boolean cheapest;
        public final int floorEmeralds;

        public TargetMatch(String targetDescription, int offerIndex, int emeraldCost) {
            this(targetDescription, offerIndex, emeraldCost, false, 0);
        }

        public TargetMatch(String targetDescription, int offerIndex, int emeraldCost, boolean cheapest, int floorEmeralds) {
            this.targetDescription = targetDescription;
            this.offerIndex = offerIndex;
            this.emeraldCost = emeraldCost;
            this.cheapest = cheapest;
            this.floorEmeralds = floorEmeralds;
        }

        /** True when this match cannot be improved by searching further. */
        public boolean atFloor() {
            return !cheapest || (floorEmeralds > 0 && emeraldCost <= floorEmeralds);
        }

        public String summary() {
            String offerText = "(offer #" + (offerIndex + 1) + ")";
            if (emeraldCost <= 0) {
                return targetDescription + " " + offerText;
            }
            return targetDescription + " for " + emeraldCost + " emeralds " + offerText;
        }
    }

    public static class MatchSummary {
        private static final MatchSummary NO_MATCH = new MatchSummary(false, Collections.emptyList());

        public final boolean matched;
        public final List<TargetMatch> targetMatches;

        public MatchSummary(boolean matched, List<TargetMatch> targetMatches) {
            this.matched = matched;
            this.targetMatches = targetMatches;
        }

        public static MatchSummary noMatch() {
            return NO_MATCH;
        }

        public static MatchSummary matched(List<TargetMatch> targetMatches) {
            return new MatchSummary(true, List.copyOf(targetMatches));
        }

        public int totalEmeralds() {
            int total = 0;
            for (TargetMatch targetMatch : targetMatches) {
                total += targetMatch.emeraldCost;
            }
            return total;
        }

        public boolean cheapestSatisfied() {
            for (TargetMatch targetMatch : targetMatches) {
                if (!targetMatch.atFloor()) {
                    return false;
                }
            }
            return true;
        }

        public String completionMessage() {
            if (!matched || targetMatches.isEmpty()) {
                return "Autotrade complete";
            }
            if (targetMatches.size() == 1) {
                return "Autotrade complete: found " + targetMatches.get(0).summary();
            }
            List<String> summaries = new ArrayList<>();
            for (TargetMatch targetMatch : targetMatches) {
                summaries.add(targetMatch.summary());
            }
            return "Autotrade complete: " + String.join("; ", summaries);
        }
    }

    public static class MatchPlan {
        private final List<ItemTarget> itemTargets;
        private final List<EnchantTarget> enchantTargets;
        private final boolean matchAny;
        private final Map<Item, List<Integer>> tradedItemTargetIndexes;
        private final Set<Item> tradedItemTargetItems;
        private final Map<Item, List<Integer>> tradingItemTargetIndexes;
        private final Set<Item> tradingItemTargetItems;
        private final int[] itemOfferIndexes;
        private final int[] itemEmeraldCosts;
        private final int[] enchantOfferIndexes;
        private final int[] enchantEmeraldCosts;
        private final boolean hasCheapest;

        private MatchPlan(List<ItemTarget> itemTargets, List<EnchantTarget> enchantTargets, boolean matchAny) {
            this.itemTargets = List.copyOf(itemTargets);
            this.enchantTargets = List.copyOf(enchantTargets);
            this.matchAny = matchAny;
            this.tradedItemTargetIndexes = new HashMap<>();
            this.tradedItemTargetItems = new HashSet<>();
            this.tradingItemTargetIndexes = new HashMap<>();
            this.tradingItemTargetItems = new HashSet<>();
            this.itemOfferIndexes = new int[this.itemTargets.size()];
            this.itemEmeraldCosts = new int[this.itemTargets.size()];
            this.enchantOfferIndexes = new int[this.enchantTargets.size()];
            this.enchantEmeraldCosts = new int[this.enchantTargets.size()];
            this.hasCheapest = this.itemTargets.stream().anyMatch(target -> target.cheapest) || this.enchantTargets.stream().anyMatch(target -> target.cheapest);

            for (int i = 0; i < this.itemTargets.size(); i++) {
                ItemTarget itemTarget = this.itemTargets.get(i);
                Item item = itemTarget.item;
                if (itemTarget.tradeSide == ItemTradeSide.TRADING) {
                    tradingItemTargetItems.add(item);
                    tradingItemTargetIndexes.computeIfAbsent(item, ignored -> new ArrayList<>()).add(i);
                } else {
                    tradedItemTargetItems.add(item);
                    tradedItemTargetIndexes.computeIfAbsent(item, ignored -> new ArrayList<>()).add(i);
                }
            }
        }

        public MatchSummary findVillagerMatch(MerchantOffers offers) {
            if (offers == null || offers.isEmpty()) {
                return MatchSummary.noMatch();
            }
            if (itemTargets.isEmpty() && enchantTargets.isEmpty()) {
                return MatchSummary.noMatch();
            }

            Arrays.fill(itemOfferIndexes, -1);
            Arrays.fill(enchantOfferIndexes, -1);
            int matchedItems = 0;
            int matchedEnchantments = 0;
            boolean hasItemTargets = !itemTargets.isEmpty();
            boolean hasEnchantTargets = !enchantTargets.isEmpty();

            for (int offerIndex = 0; offerIndex < offers.size(); offerIndex++) {
                MerchantOffer offer = offers.get(offerIndex);
                if (offer == null) {
                    continue;
                }

                ItemStack result = offer.getResult();
                int emeraldCost = countEmeralds(offer);

                if (hasItemTargets) {
                    ItemStackMatchResult resultMatches = matchItemStack(tradedItemTargetItems, tradedItemTargetIndexes, result, offerIndex, emeraldCost);
                    if (resultMatches.immediateMatch() != null) {
                        return MatchSummary.matched(List.of(resultMatches.immediateMatch()));
                    }
                    matchedItems += resultMatches.matchedCount();

                    ItemStackMatchResult costAMatches = matchItemStack(tradingItemTargetItems, tradingItemTargetIndexes, offer.getCostA(), offerIndex, emeraldCost);
                    if (costAMatches.immediateMatch() != null) {
                        return MatchSummary.matched(List.of(costAMatches.immediateMatch()));
                    }
                    matchedItems += costAMatches.matchedCount();

                    ItemStackMatchResult costBMatches = matchItemStack(tradingItemTargetItems, tradingItemTargetIndexes, offer.getCostB(), offerIndex, emeraldCost);
                    if (costBMatches.immediateMatch() != null) {
                        return MatchSummary.matched(List.of(costBMatches.immediateMatch()));
                    }
                    matchedItems += costBMatches.matchedCount();
                }

                if (!hasEnchantTargets) {
                    continue;
                }

                ItemEnchantments directEnchantments = result.get(DataComponents.ENCHANTMENTS);
                ItemEnchantments storedEnchantments = result.get(DataComponents.STORED_ENCHANTMENTS);
                if (directEnchantments == null && storedEnchantments == null) {
                    continue;
                }

                for (int enchantIndex = 0; enchantIndex < enchantTargets.size(); enchantIndex++) {
                    EnchantTarget enchantTarget = enchantTargets.get(enchantIndex);
                    boolean alreadyMatched = enchantOfferIndexes[enchantIndex] >= 0;
                    if (alreadyMatched && !(enchantTarget.cheapest && emeraldCost < enchantEmeraldCosts[enchantIndex])) {
                        continue;
                    }
                    if (emeraldCost > enchantTarget.maxEmeralds) {
                        continue;
                    }
                    if (!hasEnchantAtLeast(directEnchantments, enchantTarget) && !hasEnchantAtLeast(storedEnchantments, enchantTarget)) {
                        continue;
                    }

                    if (matchAny) {
                        return MatchSummary.matched(List.of(new TargetMatch(describeEnchantTarget(enchantTarget), offerIndex, emeraldCost, enchantTarget.cheapest, enchantTarget.floorEmeralds)));
                    }
                    enchantOfferIndexes[enchantIndex] = offerIndex;
                    enchantEmeraldCosts[enchantIndex] = emeraldCost;
                    if (!alreadyMatched) {
                        matchedEnchantments++;
                    }
                }
            }

            if (matchAny || matchedItems != itemTargets.size() || matchedEnchantments != enchantTargets.size()) {
                return MatchSummary.noMatch();
            }

            List<TargetMatch> targetMatches = new ArrayList<>(itemTargets.size() + enchantTargets.size());
            for (int i = 0; i < itemTargets.size(); i++) {
                targetMatches.add(new TargetMatch(describeItemTarget(itemTargets.get(i)), itemOfferIndexes[i], itemEmeraldCosts[i], itemTargets.get(i).cheapest, 0));
            }
            for (int i = 0; i < enchantTargets.size(); i++) {
                EnchantTarget enchantTarget = enchantTargets.get(i);
                targetMatches.add(new TargetMatch(describeEnchantTarget(enchantTarget), enchantOfferIndexes[i], enchantEmeraldCosts[i], enchantTarget.cheapest, enchantTarget.floorEmeralds));
            }
            return MatchSummary.matched(targetMatches);
        }

        public boolean hasPriceConstraints() {
            if (hasCheapest) {
                return true;
            }
            for (ItemTarget target : itemTargets) {
                if (target.maxEmeralds != Integer.MAX_VALUE) {
                    return true;
                }
            }
            for (EnchantTarget target : enchantTargets) {
                if (target.maxEmeralds != Integer.MAX_VALUE) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasCheapest() {
            return hasCheapest;
        }

        public MatchPlan copy() {
            return new MatchPlan(itemTargets, enchantTargets, matchAny);
        }

        private ItemStackMatchResult matchItemStack(Set<Item> targetItems, Map<Item, List<Integer>> targetIndexes, ItemStack stack, int offerIndex, int emeraldCost) {
            if (stack == null || stack.isEmpty() || !targetItems.contains(stack.getItem())) {
                return ItemStackMatchResult.none();
            }

            List<Integer> indexes = targetIndexes.get(stack.getItem());
            if (indexes == null) {
                return ItemStackMatchResult.none();
            }

            int matchedCount = 0;
            for (int itemIndex : indexes) {
                ItemTarget itemTarget = itemTargets.get(itemIndex);
                boolean alreadyMatched = itemOfferIndexes[itemIndex] >= 0;
                if (alreadyMatched && !(itemTarget.cheapest && emeraldCost < itemEmeraldCosts[itemIndex])) {
                    continue;
                }
                if (emeraldCost > itemTarget.maxEmeralds) {
                    continue;
                }

                TargetMatch targetMatch = new TargetMatch(describeItemTarget(itemTarget), offerIndex, emeraldCost, itemTarget.cheapest, 0);
                if (matchAny) {
                    return ItemStackMatchResult.immediate(targetMatch);
                }

                itemOfferIndexes[itemIndex] = offerIndex;
                itemEmeraldCosts[itemIndex] = emeraldCost;
                if (!alreadyMatched) {
                    matchedCount++;
                }
            }

            return ItemStackMatchResult.count(matchedCount);
        }
    }

    private record ItemStackMatchResult(TargetMatch immediateMatch, int matchedCount) {

        private static ItemStackMatchResult none() {
            return new ItemStackMatchResult(null, 0);
        }

        private static ItemStackMatchResult immediate(TargetMatch targetMatch) {
            return new ItemStackMatchResult(targetMatch, 0);
        }

        private static ItemStackMatchResult count(int matchedCount) {
            return new ItemStackMatchResult(null, matchedCount);
        }
    }

    private static final List<ItemTarget> ITEM_TARGETS = new ArrayList<>();
    private static final List<EnchantTarget> ENCHANT_TARGETS = new ArrayList<>();

    private static boolean active = false;
    private static boolean tradeMatched = false;

    private static boolean matchAny = false;
    private static int tradeCycleDelayTicks = 1;
    private static int maxCycleCount = 1000;

    public static boolean hasTargets() {
        return !ITEM_TARGETS.isEmpty() || !ENCHANT_TARGETS.isEmpty();
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isTradeMatched() {
        return tradeMatched;
    }

    public static List<ItemTarget> getItemTargets() {
        return Collections.unmodifiableList(ITEM_TARGETS);
    }

    public static List<EnchantTarget> getEnchantTargets() {
        return Collections.unmodifiableList(ENCHANT_TARGETS);
    }

    public static void clearTargets() {
        ITEM_TARGETS.clear();
        ENCHANT_TARGETS.clear();
        resetMatchState();
        stop();
    }

    public static void removeItemTarget(int index) {
        if (index < 0 || index >= ITEM_TARGETS.size()) {
            return;
        }
        ITEM_TARGETS.remove(index);
        resetMatchState();
    }

    public static void removeEnchantTarget(int index) {
        if (index < 0 || index >= ENCHANT_TARGETS.size()) {
            return;
        }
        ENCHANT_TARGETS.remove(index);
        resetMatchState();
    }

    public static void replaceItemTarget(int index, Item item, int maxEmeralds) {
        replaceItemTarget(index, item, maxEmeralds, ItemTradeSide.TRADED);
    }

    public static void replaceItemTarget(int index, Item item, int maxEmeralds, ItemTradeSide tradeSide) {
        replaceItemTarget(index, item, maxEmeralds, tradeSide, false);
    }

    public static void replaceItemTarget(int index, Item item, int maxEmeralds, ItemTradeSide tradeSide, boolean cheapest) {
        if (index < 0 || index >= ITEM_TARGETS.size() || item == Items.AIR) {
            return;
        }
        ITEM_TARGETS.set(index, new ItemTarget(item, sanitizeMaxEmeralds(maxEmeralds), tradeSide, cheapest));
        resetMatchState();
    }

    public static void replaceEnchantTarget(int index, ResourceKey<Enchantment> enchantKey, int minLevel, int maxEmeralds) {
        replaceEnchantTarget(index, enchantKey, minLevel, maxEmeralds, false);
    }

    public static void replaceEnchantTarget(int index, ResourceKey<Enchantment> enchantKey, int minLevel, int maxEmeralds, boolean cheapest) {
        if (index < 0 || index >= ENCHANT_TARGETS.size() || enchantKey == null) {
            return;
        }
        ENCHANT_TARGETS.set(index, new EnchantTarget(enchantKey, Math.max(1, minLevel), sanitizeMaxEmeralds(maxEmeralds), cheapest, 0));
        resetMatchState();
    }

    private static int sanitizeMaxEmeralds(int maxEmeralds) {
        return maxEmeralds <= 0 ? Integer.MAX_VALUE : maxEmeralds;
    }

    public static boolean isMatchAny() {
        return matchAny;
    }

    public static void setMatchAny(boolean value) {
        matchAny = value;
    }

    public static void start(Item item) {
        start(item, Integer.MAX_VALUE);
    }

    public static void start(Item item, int maxEmeralds) {
        start(item, maxEmeralds, ItemTradeSide.TRADED);
    }

    public static void start(Item item, int maxEmeralds, ItemTradeSide tradeSide) {
        start(item, maxEmeralds, tradeSide, false);
    }

    public static void start(Item item, int maxEmeralds, ItemTradeSide tradeSide, boolean cheapest) {
        if (item == Items.AIR) {
            stop();
            return;
        }
        ITEM_TARGETS.add(new ItemTarget(item, sanitizeMaxEmeralds(maxEmeralds), tradeSide, cheapest));
        resetMatchState();
    }

    public static void start(ResourceKey<Enchantment> enchantKey, int minLevel, int maxEmeralds) {
        start(enchantKey, minLevel, maxEmeralds, false);
    }

    public static void start(ResourceKey<Enchantment> enchantKey, int minLevel, int maxEmeralds, boolean cheapest) {
        if (enchantKey == null) {
            return;
        }
        int level = Math.max(1, minLevel);
        int cappedMaxEmeralds = sanitizeMaxEmeralds(maxEmeralds);
        ENCHANT_TARGETS.add(new EnchantTarget(enchantKey, level, cappedMaxEmeralds, cheapest, 0));
        resetMatchState();
    }

    public static void startCycleSession() {
        active = true;
        tradeMatched = false;
    }

    public static void stop() {
        active = false;
    }

    public static void resetMatchState() {
        tradeMatched = false;
    }

    public static void markMatched() {
        tradeMatched = true;
        active = false;
    }

    private static int countEmeralds(MerchantOffer offer) {
        int emeralds = 0;

        ItemStack costA = offer.getCostA();
        if (!costA.isEmpty() && costA.is(Items.EMERALD)) {
            emeralds += costA.getCount();
        }

        ItemStack costB = offer.getCostB();
        if (!costB.isEmpty() && costB.is(Items.EMERALD)) {
            emeralds += costB.getCount();
        }

        return emeralds;
    }

    public static String keyToIdString(ResourceKey<Enchantment> enchantKey) {
        return enchantKey.identifier().toString();
    }

    private static String stackSignature(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount() + "#" + stack.getComponentsPatch();
    }

    private static void printOfferInfo(ItemStack out, int index) {
        String itemName = out.toString();
        List<String> enchantmentStrings = new ArrayList<>();

        ItemEnchantments directEnchantments = out.get(DataComponents.ENCHANTMENTS);
        ItemEnchantments storedEnchantments = out.get(DataComponents.STORED_ENCHANTMENTS);

        if (directEnchantments != null) {
            for (var entry : directEnchantments.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                enchantmentStrings.add(holder.value().description().getString() + "=" + entry.getIntValue());
            }
        }

        if (storedEnchantments != null) {
            for (var entry : storedEnchantments.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                enchantmentStrings.add(holder.value().description().getString() + "=" + entry.getIntValue());
            }
        }

        String enchantmentList = enchantmentStrings.isEmpty() ? "no enchantments" : String.join(", ", enchantmentStrings);
        TradeCyclingMod.LOGGER.debug("Offer #{}: sells {} {{{}}}", index, itemName, enchantmentList);
    }

    private static boolean hasEnchantAtLeast(ItemEnchantments enchantments, EnchantTarget target) {
        if (enchantments == null || target.enchantKey == null) {
            return false;
        }
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getIntValue();
            if (level < target.minLevel) {
                continue;
            }
            if (holder.is(target.enchantKey)) {
                return true;
            }
        }
        return false;
    }

    public static void doCycleOnce() {
        TradeCyclingClientMod.INSTANCE.sendCycleTradesPacket();
    }

    public static String describeTarget() {
        if (ITEM_TARGETS.isEmpty() && ENCHANT_TARGETS.isEmpty()) {
            return "(none)";
        }

        List<String> parts = new ArrayList<>();

        if (!ITEM_TARGETS.isEmpty()) {
            List<String> items = new ArrayList<>();
            for (ItemTarget target : ITEM_TARGETS) {
                items.add(describeItemTarget(target));
            }
            parts.add("Items: " + String.join(", ", items));
        }

        if (!ENCHANT_TARGETS.isEmpty()) {
            List<String> enchantments = new ArrayList<>();
            for (EnchantTarget target : ENCHANT_TARGETS) {
                enchantments.add(describeEnchantTarget(target));
            }
            parts.add("Enchants: " + String.join(", ", enchantments));
        }

        return String.join(" | ", parts);
    }

    private static String describeItemTarget(ItemTarget target) {
        String id = BuiltInRegistries.ITEM.getKey(target.item).toString();
        String description = (target.tradeSide == ItemTradeSide.TRADING ? "trading " : "traded ") + id + (target.cheapest ? " (cheapest)" : "");
        if (target.maxEmeralds == Integer.MAX_VALUE) {
            return description;
        }
        return description + " (<= " + target.maxEmeralds + " emeralds)";
    }

    private static String describeEnchantTarget(EnchantTarget target) {
        String id = keyToIdString(target.enchantKey);
        String description = id + " >= " + target.minLevel + (target.cheapest ? " (cheapest)" : "");
        if (target.maxEmeralds == Integer.MAX_VALUE) {
            return description;
        }
        return description + " (<= " + target.maxEmeralds + " emeralds)";
    }

    public static String createOfferSignature(MerchantOffers offers) {
        if (offers == null || offers.isEmpty()) {
            return "empty";
        }

        List<String> parts = new ArrayList<>();
        for (MerchantOffer offer : offers) {
            if (offer == null) {
                parts.add("null");
                continue;
            }

            StringJoiner joiner = new StringJoiner("|");
            joiner.add(stackSignature(offer.getResult()));
            joiner.add(stackSignature(offer.getCostA()));
            joiner.add(stackSignature(offer.getCostB()));
            joiner.add(Boolean.toString(offer.isOutOfStock()));
            joiner.add(Integer.toString(offer.getUses()));
            joiner.add(Integer.toString(offer.getMaxUses()));
            parts.add(joiner.toString());
        }

        return String.join(";", parts);
    }

    public static void setTradeCycleDelay(int ticks) {
        tradeCycleDelayTicks = Math.max(1, ticks);
    }

    public static int getTradeCycleDelay() {
        return tradeCycleDelayTicks;
    }

    public static void setMaxCycleCount(int maxCycles) {
        maxCycleCount = Math.max(1, maxCycles);
    }

    public static int getMaxCycleCount() {
        return maxCycleCount;
    }

    public static MatchSummary findVillagerMatch(MerchantOffers offers) {
        return findVillagerMatch(offers, ITEM_TARGETS, ENCHANT_TARGETS, matchAny);
    }

    public static MatchSummary findVillagerMatch(MerchantOffers offers, List<ItemTarget> itemTargets, List<EnchantTarget> enchantTargets, boolean matchAny) {
        return createMatchPlan(itemTargets, enchantTargets, matchAny).findVillagerMatch(offers);
    }

    public static MatchPlan createMatchPlan(List<ItemTarget> itemTargets, List<EnchantTarget> enchantTargets, boolean matchAny) {
        return new MatchPlan(itemTargets, enchantTargets, matchAny);
    }

}
