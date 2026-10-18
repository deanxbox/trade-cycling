package de.maxhenkel.tradecycling.compatibility;

import de.maxhenkel.tradecycling.TradeCyclingMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VisibleTraders {

    @Nullable
    private static MethodHandle regenerateTrades;
    @Nullable
    private static MethodHandle requestOffers;
    @Nullable
    private static MethodHandle sendMerchantOffers;
    @Nullable
    private static MethodHandle setLockedTradeData;
    @Nullable
    private static MethodHandle lockedTradeDataConstructor;

    public static void forceTradeGeneration(Villager villager) {
        regenerateTrades(villager);
    }

    public static void regenerateTrades(Villager villager) {
        if (regenerateTrades == null) {
            return;
        }
        try {
            regenerateTrades.invoke(villager);
        } catch (Throwable e) {
            TradeCyclingMod.LOGGER.error("Failed to regenerate visible traders trades", e);
            regenerateTrades = null;
        }
    }

    public static void requestOffers(Villager villager, ServerPlayer player) {
        if (requestOffers == null) {
            return;
        }
        try {
            requestOffers.invoke(villager, player);
        } catch (Throwable e) {
            TradeCyclingMod.LOGGER.error("Failed to request offers", e);
            requestOffers = null;
        }
    }

    public static boolean sendTrades(ServerPlayer player, Merchant merchant, int containerId, MerchantOffers merchantOffers, int level, int villagerXp, boolean showProgressBar, boolean canRestock) {
        if (sendMerchantOffers == null) {
            return false;
        }
        try {
            sendMerchantOffers.invoke(player, merchant, containerId, merchantOffers, level, villagerXp, showProgressBar, canRestock);
            return true;
        } catch (Throwable e) {
            TradeCyclingMod.LOGGER.error("Failed to send visible traders offers", e);
            sendMerchantOffers = null;
            return false;
        }
    }

    public static void setLockedOffers(Villager villager, List<MerchantOffers> lockedOfferSets) {
        if (setLockedTradeData == null || lockedTradeDataConstructor == null) {
            regenerateTrades(villager);
            return;
        }
        try {
            List<MerchantOffers> copies = new ArrayList<>(lockedOfferSets.size());
            for (MerchantOffers lockedOfferSet : lockedOfferSets) {
                copies.add(lockedOfferSet.copy());
            }
            Object lockedTradeData = lockedTradeDataConstructor.invoke(copies);
            setLockedTradeData.invoke(villager, lockedTradeData);
        } catch (Throwable e) {
            TradeCyclingMod.LOGGER.error("Failed to apply visible traders locked offers", e);
            setLockedTradeData = null;
            lockedTradeDataConstructor = null;
            regenerateTrades(villager);
        }
    }

    public static MerchantOffers getOffers(Villager villager) {
        return villager.getOffers();
    }

    public static int getLevel(Villager villager) {
        return villager.getVillagerData().level();
    }

    public static void init(TradeCyclingMod mod) {
        if (!mod.isModLoaded("visibletraders")) {
            return;
        }
        regenerateTrades = getRegenerateMethod();
        requestOffers = requestOffersMethod();
        sendMerchantOffers = sendMerchantOffersMethod();
        lockedTradeDataConstructor = lockedTradeDataConstructor();
        setLockedTradeData = setLockedTradeDataMethod();
    }

    public static boolean isCompatibilityActive() {
        return regenerateTrades != null || requestOffers != null;
    }

    private static MethodHandle getRegenerateMethod() {
        try {
            Method forceTradeGeneration = Arrays.stream(Villager.class.getDeclaredMethods())
                    .filter(method -> method.getName().contains("visibleTrades$regenerateTrades"))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find visible traders regenerate trades method"));
            forceTradeGeneration.setAccessible(true);
            return MethodHandles.lookup().unreflect(forceTradeGeneration);
        } catch (Throwable e) {
            TradeCyclingMod.LOGGER.error("Failed to initialize visible traders integration", e);
            return null;
        }
    }

    private static MethodHandle requestOffersMethod() {
        try {
            Method requestOffers = Arrays.stream(Villager.class.getDeclaredMethods())
                    .filter(method -> method.getName().contains("visibleTraders$requestOffers"))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find visible traders requestOffers method"));
            requestOffers.setAccessible(true);
            return MethodHandles.lookup().unreflect(requestOffers);
        } catch (Throwable e) {
            TradeCyclingMod.LOGGER.error("Failed to initialize visible traders integration", e);
            return null;
        }
    }

    private static MethodHandle sendMerchantOffersMethod() {
        try {
            Method sendMerchantOffers = Arrays.stream(ServerPlayer.class.getDeclaredMethods())
                    .filter(method -> method.getName().contains("visibleTraders")
                            && method.getName().contains("MerchantOffers")
                            && method.getParameterCount() == 7
                            && Merchant.class.isAssignableFrom(method.getParameterTypes()[0])
                            && method.getParameterTypes()[1] == int.class
                            && MerchantOffers.class.isAssignableFrom(method.getParameterTypes()[2])
                            && method.getParameterTypes()[3] == int.class
                            && method.getParameterTypes()[4] == int.class
                            && method.getParameterTypes()[5] == boolean.class
                            && method.getParameterTypes()[6] == boolean.class)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find visible traders sendMerchantOffers method"));
            sendMerchantOffers.setAccessible(true);
            return MethodHandles.lookup().unreflect(sendMerchantOffers);
        } catch (Throwable e) {
            TradeCyclingMod.LOGGER.error("Failed to initialize visible traders offer sender", e);
            return null;
        }
    }

    private static MethodHandle setLockedTradeDataMethod() {
        try {
            Method setLockedTradeData = Arrays.stream(Villager.class.getDeclaredMethods())
                    .filter(method -> method.getName().contains("visibleTraders$setLockedTradeData"))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find visible traders setLockedTradeData method"));
            setLockedTradeData.setAccessible(true);
            return MethodHandles.lookup().unreflect(setLockedTradeData);
        } catch (Throwable e) {
            TradeCyclingMod.LOGGER.error("Failed to initialize visible traders locked trade data setter", e);
            return null;
        }
    }

    private static MethodHandle lockedTradeDataConstructor() {
        try {
            Class<?> lockedTradeDataClass = Class.forName("net.ramixin.visibletraders.LockedTradeData");
            Constructor<?> constructor = lockedTradeDataClass.getDeclaredConstructor(List.class);
            constructor.setAccessible(true);
            return MethodHandles.lookup().unreflectConstructor(constructor);
        } catch (Throwable e) {
            TradeCyclingMod.LOGGER.error("Failed to initialize visible traders locked trade data constructor", e);
            return null;
        }
    }

}
