package de.maxhenkel.tradecycling.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractVillager.class)
public interface AbstractVillagerAccessor {

    @Invoker("addOffersFromTradeSet")
    void invokeAddOffersFromTradeSet(ServerLevel level, MerchantOffers offers, ResourceKey<TradeSet> tradeSet);

}
