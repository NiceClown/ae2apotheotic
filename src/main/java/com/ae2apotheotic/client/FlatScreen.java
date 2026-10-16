package com.ae2apotheotic.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/** Container screen drawn with plain rectangles (no textures needed). */
abstract class FlatScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected static final int TEXT = 0xFF404040;
    protected static final int TEXT_GREEN = 0xFF1E7A1E;
    protected static final int TEXT_RED = 0xFFB02020;
    protected static final int TEXT_YELLOW = 0xFF9A7400;
    protected static final int TEXT_GRAY = 0xFF707070;

    private final int accent;

    protected FlatScreen(T menu, Inventory inventory, Component title, int width, int height, int accent) {
        super(menu, inventory, title, width, height);
        this.accent = accent;
    }

    protected Button button(int x, int y, int w, int h, Component label, @Nullable Component tooltip, Button.OnPress onPress) {
        Button.Builder builder = Button.builder(label, onPress).bounds(leftPos + x, topPos + y, w, h);
        if (tooltip != null) {
            builder.tooltip(Tooltip.create(tooltip));
        }
        return addRenderableWidget(builder.build());
    }

    protected void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    protected boolean shiftDown() {
        return minecraft != null && minecraft.hasShiftDown();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos;
        int y = topPos;
        // window
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF000000);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFFFFFFFF);
        g.fill(x + 2, y + 2, x + imageWidth - 1, y + imageHeight - 1, 0xFF555555);
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFFC6C6C6);
        // colored strip that tells the blocks apart
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + 4, accent);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                drawSlot(g, x + slot.x - 1, y + slot.y - 1);
            }
        }
        extractPanel(g, x, y);
    }

    /** Extra background decoration, in absolute coordinates. */
    protected void extractPanel(GuiGraphicsExtractor g, int x, int y) {}

    protected static void drawSlot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    protected void centeredText(GuiGraphicsExtractor g, Component text, int centerX, int y, int color) {
        g.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }
}
