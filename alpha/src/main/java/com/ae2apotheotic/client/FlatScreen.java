package com.ae2apotheotic.client;

import appeng.items.tools.GuideItem;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/** Container screen drawn with plain rectangles (no textures needed), plus small drawing helpers. */
abstract class FlatScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected static final int TEXT = 0xFF404040;
    protected static final int TEXT_GREEN = 0xFF1E7A1E;
    protected static final int TEXT_RED = 0xFFB02020;
    protected static final int TEXT_YELLOW = 0xFF9A7400;
    protected static final int TEXT_GRAY = 0xFF707070;
    protected static final int TEXT_LIGHT = 0xFFE8E8E8;
    protected static final int TEXT_DIM = 0xFFA0A0A0;
    /** Width of tooltips that are wrapped by {@link #tooltip}. */
    protected static final int TOOLTIP_WIDTH = 200;

    protected final int accent;

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

    protected void clickSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    protected boolean shiftDown() {
        return minecraft != null && minecraft.hasShiftDown();
    }

    protected void addGuideButton(String page) {
        button(imageWidth - 17, 4, 12, 11, Component.literal("?"), Component.translatable("gui.ae2apotheotic.guide.tooltip"), b -> {
            try {
                if (minecraft != null && minecraft.player != null) {
                    GuidesCommon.openGuide(
                        minecraft.player,
                        GuideItem.GUIDE_ID,
                        PageAnchor.page(Identifier.fromNamespaceAndPath("ae2", page))
                    );
                }
            } catch (Throwable ignored) {}
        });
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
        extractPanel(g, x, y);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                drawSlot(g, x + slot.x - 1, y + slot.y - 1);
            }
        }
    }

    /** Extra background decoration, in absolute coordinates. */
    protected void extractPanel(GuiGraphicsExtractor g, int x, int y) {}

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        // the title must leave room for the "?" guide button
        fitText(g, title, titleLabelX, titleLabelY, imageWidth - titleLabelX - 20, 0xFF404040);
        fitText(g, playerInventoryTitle, inventoryLabelX, inventoryLabelY, imageWidth - inventoryLabelX - 8, 0xFF404040);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Drawing helpers (coordinates are whatever the current pose uses)

    protected static void drawSlot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    /** Sunken dark panel, like the display of an AE2 terminal. */
    protected static void drawInset(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, 0xFF373737);
        g.fill(x0 + 1, y0 + 1, x1, y1, 0xFFFFFFFF);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF2B2B2E);
    }

    /** Flat clickable button; returns nothing, clicks are hit-tested with {@link #inside}. */
    protected void drawFlatButton(GuiGraphicsExtractor g, int x, int y, int w, int h, Component label, boolean hovered, boolean enabled,
        boolean selected) {
        int border = selected ? accent : hovered && enabled ? 0xFFB0B0B0 : 0xFF1A1A1A;
        int fill = selected ? 0xFF4A4458 : !enabled ? 0xFF3A3A3A : hovered ? 0xFF6A6A6A : 0xFF505050;
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        if (!selected && enabled) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x40FFFFFF);
        }
        int color = !enabled ? 0xFF7A7A7A : TEXT_LIGHT;
        int maxWidth = w - 4;
        int textWidth = Math.min(font.width(label), maxWidth);
        fitText(g, label, x + (w - textWidth) / 2, y + (h - 8) / 2, maxWidth, color);
    }

    /** Draws text, scaling it down (never below 60 %) and then trimming it so that it fits into {@code maxWidth}. */
    protected void fitText(GuiGraphicsExtractor g, Component text, int x, int y, int maxWidth, int color) {
        int width = font.width(text);
        if (width <= maxWidth || maxWidth <= 0) {
            g.text(font, text, x, y, color, false);
            return;
        }
        float scale = Math.max(0.6F, (float) maxWidth / width);
        g.pose().pushMatrix();
        g.pose().translate(x, y + (1 - scale) * 4);
        g.pose().scale(scale, scale);
        int scaledMax = (int) (maxWidth / scale);
        if (width <= scaledMax) {
            g.text(font, text, 0, 0, color, false);
        } else {
            String trimmed = font.plainSubstrByWidth(text.getString(), scaledMax - font.width("…")) + "…";
            g.text(font, Component.literal(trimmed).withStyle(text.getStyle()), 0, 0, color, false);
        }
        g.pose().popMatrix();
    }

    protected void centeredText(GuiGraphicsExtractor g, Component text, int centerX, int y, int color) {
        g.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    /** Centered text that is shrunk to fit into {@code maxWidth}. */
    protected void centeredFit(GuiGraphicsExtractor g, Component text, int centerX, int y, int maxWidth, int color) {
        int width = Math.min(font.width(text), maxWidth);
        fitText(g, text, centerX - width / 2, y, maxWidth, color);
    }

    /** Word-wrapped tooltip. */
    protected void tooltip(GuiGraphicsExtractor g, Component text, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = font.split(text, TOOLTIP_WIDTH);
        g.setTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    /** Hit test in GUI-relative coordinates. */
    protected boolean inside(int x, int y, int w, int h, double mouseX, double mouseY) {
        return isHovering(x, y, w, h, mouseX, mouseY);
    }
}
