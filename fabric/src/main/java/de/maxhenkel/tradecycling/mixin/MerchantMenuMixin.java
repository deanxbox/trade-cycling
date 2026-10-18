package de.maxhenkel.tradecycling.mixin;

import de.maxhenkel.tradecycling.FabricTradeCyclingClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantMenu.class)
public class MerchantMenuMixin {

    @Inject(method = "setOffers", at = @At("TAIL"))
    private void onOffersUpdated(MerchantOffers offers, CallbackInfo ci) {
        FabricTradeCyclingClientMod mod = FabricTradeCyclingClientMod.instance();
        if (mod != null) {
            Minecraft.getInstance().execute(() -> mod.onMerchantOffersUpdated((MerchantMenu) (Object) this, offers));
        }
    }
}
