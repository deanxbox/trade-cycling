package de.maxhenkel.tradecycling.gui;

import de.maxhenkel.tradecycling.TradeCyclingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Collections;

public class AutoTradeConfigButton extends AbstractButton {

    private static final Identifier CONFIG_BUTTON = Identifier.fromNamespaceAndPath(TradeCyclingMod.MODID, "textures/autotrade_config.png");

    public static final int WIDTH = 18;
    public static final int HEIGHT = 14;

    public AutoTradeConfigButton(int x, int y) {
        super(x, y, WIDTH, HEIGHT, Component.empty());
    }

    @Override
    public void onPress(InputWithModifiers inputWithModifiers) {
        Minecraft.getInstance().gui.setScreen(new AutoTradeConfigScreen());
    }

    @Override
    public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (isHovered) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CONFIG_BUTTON, getX(), getY(), 0, 14, WIDTH, HEIGHT, 32, 32);
            guiGraphics.setTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    Collections.singletonList(Component.translatable("tooltip.trade_cycling.autotrade_config").getVisualOrderText()),
                    mouseX,
                    mouseY
            );
        } else {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CONFIG_BUTTON, getX(), getY(), 0, 0, WIDTH, HEIGHT, 32, 32);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }
}
