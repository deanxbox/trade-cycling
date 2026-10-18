package de.maxhenkel.tradecycling;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.maxhenkel.tradecycling.gui.TradeCyclingOptionsScreen;

public class FabricModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TradeCyclingOptionsScreen::new;
    }
}
