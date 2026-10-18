package de.maxhenkel.tradecycling.gui;

import de.maxhenkel.tradecycling.TradeCyclingClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class AutoTradeProgressWidget extends AbstractWidget {

    public AutoTradeProgressWidget(int x, int y, int width) {
        super(x, y, width, 16, Component.empty());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        TradeCyclingClientMod clientMod = TradeCyclingClientMod.INSTANCE;
        if (clientMod == null || !clientMod.hasServerAutoCycleProgress()) {
            return;
        }

        int attempts = clientMod.getServerAutoCycleAttempts();
        int maxAttempts = clientMod.getServerAutoCycleMaxAttempts();
        float progress = maxAttempts <= 0 ? 0F : Math.min(1F, attempts / (float) maxAttempts);
        int fillWidth = Math.round((width - 2) * progress);

        guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, 0xCC000000);
        guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFF303030);
        guiGraphics.fill(getX() + 1, getY() + 1, getX() + 1 + fillWidth, getY() + height - 1, 0xFF5BC06A);

        String text = String.format(Locale.ROOT, "%d / %d", attempts, maxAttempts);
        Minecraft minecraft = Minecraft.getInstance();
        guiGraphics.text(minecraft.font, text, getX() + width / 2 - minecraft.font.width(text) / 2, getY() + 4, 0xFFFFFFFF);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }
}
