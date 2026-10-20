package com.ae2apotheotic.client;

import com.ae2apotheotic.blockentity.EncoderBlockEntity;
import com.ae2apotheotic.encoder.EncoderLogic;
import com.ae2apotheotic.encoder.EncoderStatus;
import com.ae2apotheotic.menu.EncoderMenu;
import com.ae2apotheotic.pattern.GemCrushEncoding;
import com.ae2apotheotic.pattern.GemKeys;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apothic_enchanting.Ench;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Pattern Encoder GUI. Everything inside the window is drawn by hand: mode tabs, a dark "display" with the recipe
 * of the pattern, purity chips, stat bars for the Raven table and a status bar.
 */
public class EncoderScreen extends FlatScreen<EncoderMenu> {
    private static final int ACCENT = 0xFF8A4FD0;
    private static final int PANEL_X0 = 7;
    private static final int PANEL_Y0 = 33;
    private static final int PANEL_X1 = 203;
    private static final int PANEL_Y1 = 114;
    private static final int CONTENT_X = 42;
    private static final int[] STAT_ROWS = { 60, 74, 88 };
    private static final String[] STAT_KEYS = { "eterna", "quanta", "arcana" };
    private static final int[] STAT_COLORS = { 0xFF4FC94F, 0xFFE05050, 0xFFB06FF0 };
    private static final int BAR_X = 76;
    private static final int BAR_W = 80;
    private static final String[] MODES = { "upgrade", "crush", "infusion" };

    /** Clickable / hoverable areas, rebuilt every frame while drawing. */
    private record Zone(int x, int y, int w, int h, @Nullable Component tooltip, @Nullable ClickAction action) {}

    @FunctionalInterface
    private interface ClickAction {
        /** @param relX click position inside the zone, 0..1 */
        void click(double relX);
    }

    private final List<Zone> zones = new ArrayList<>();
    private final ItemStack[] tabIcons = new ItemStack[3];

    public EncoderScreen(EncoderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable("gui.ae2apotheotic.title.encoder"), 210, 248, ACCENT);
        this.inventoryLabelX = 24;
        this.inventoryLabelY = 156;
        List<Gem> gems = GemKeys.sortedGems();
        tabIcons[0] = gems.isEmpty() ? new ItemStack(Apoth.Items.GEM.value()) : GemItem.createStack(gems.getFirst(), Purity.FLAWLESS, 1);
        tabIcons[1] = new ItemStack(Apoth.Items.GEM_DUST.value());
        tabIcons[2] = new ItemStack(Ench.Items.RAVEN_ENCHANTING_TABLE.value());
    }

    @Override
    protected void init() {
        super.init();
        addGuideButton("ae2apotheotic/pattern_encoder.md");
    }

    // ------------------------------------------------------------------------------------------------------------
    // Background

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, int x, int y) {
        drawInset(g, x + PANEL_X0, y + PANEL_Y0, x + PANEL_X1, y + PANEL_Y1);
        // sample column
        g.fill(x + 37, y + PANEL_Y0 + 3, x + 38, y + PANEL_Y1 - 3, 0xFF4A4A50);
        // tabs
        int mode = menu.mode();
        for (int i = 0; i < 3; i++) {
            int tx = x + 8 + i * 66;
            int ty = y + 17;
            if (i == mode) {
                g.fill(tx, ty, tx + 62, ty + 17, 0xFF373737);
                g.fill(tx + 1, ty + 1, tx + 61, ty + 17, 0xFF2B2B2E);
                g.fill(tx + 1, ty + 1, tx + 61, ty + 3, accent);
            } else {
                g.fill(tx, ty + 2, tx + 62, ty + 16, 0xFF373737);
                g.fill(tx + 1, ty + 3, tx + 61, ty + 16, 0xFF8B8B8B);
                g.fill(tx + 1, ty + 3, tx + 61, ty + 4, 0xFFAAAAAA);
            }
        }
        // status bar
        int[] colors = statusColors();
        g.fill(x + PANEL_X0, y + 117, x + PANEL_X1, y + 129, 0xFF373737);
        g.fill(x + PANEL_X0 + 1, y + 118, x + PANEL_X1 - 1, y + 128, colors[0]);
    }

    /** {background, text} of the status bar. */
    private int[] statusColors() {
        EncoderStatus status = menu.status();
        if (!status.isOk()) {
            return new int[] { 0xFF4D2323, 0xFFFF9C9C };
        }
        if (!menu.canEncode()) {
            return new int[] { 0xFF4D4223, 0xFFF5DC8C };
        }
        return new int[] { 0xFF234D2A, 0xFF9CF0A8 };
    }

    // ------------------------------------------------------------------------------------------------------------
    // Foreground

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        zones.clear();
        drawTabs(g, mouseX, mouseY);
        fitText(g, Component.translatable("gui.ae2apotheotic.sample"), 10, 60, 26, TEXT_DIM);
        switch (menu.mode()) {
            case EncoderBlockEntity.MODE_UPGRADE -> drawUpgrade(g, mouseX, mouseY);
            case EncoderBlockEntity.MODE_CRUSH -> drawCrush(g, mouseX, mouseY);
            default -> drawInfusion(g, mouseX, mouseY);
        }
        drawStatus(g);
        drawEncodeButton(g, mouseX, mouseY);
    }

    private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int mode = menu.mode();
        for (int i = 0; i < 3; i++) {
            int tx = 8 + i * 66;
            boolean active = i == mode;
            boolean hover = !active && inside(tx, 17, 62, 16, mouseX, mouseY);
            int iy = active ? 17 : 18;
            g.fakeItem(tabIcons[i], tx + 2, iy);
            int color = active ? TEXT_LIGHT : hover ? 0xFFFFFFFF : 0xFF2A2A2A;
            fitText(g, Component.translatable("gui.ae2apotheotic.mode." + MODES[i]), tx + 20, iy + 5, 40, color);
            int target = i;
            zones.add(new Zone(tx, 17, 62, 16, Component.translatable("gui.ae2apotheotic.mode." + MODES[i] + ".tooltip"),
                active ? null : rel -> sendButton(EncoderMenu.BTN_MODE + target)));
        }
    }

    // ---- upgrade ----

    private void drawUpgrade(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int ordinal = menu.get(EncoderMenu.D_PURITY);
        if (ordinal >= 0) {
            drawPurityChips(g, mouseX, mouseY, ordinal, true);
            if (ordinal < Purity.values().length - 1) {
                Purity from = Purity.values()[ordinal];
                MutableComponent text = from.toComponent()
                    .append(Component.literal("  →  ").withStyle(ChatFormatting.GRAY))
                    .append(from.next().toComponent());
                centeredFit(g, text, 121, 51, 156, TEXT_LIGHT);
            }
        }
        g.text(font, "+", 67, 71, TEXT_DIM, false);
        g.text(font, "+", 101, 71, TEXT_DIM, false);
        drawArrow(g, 134, 170, 74);
        drawSelector(g, mouseX, mouseY, 78, EncoderMenu.D_LEFT_INDEX, EncoderMenu.D_LEFT_COUNT,
            EncoderMenu.BTN_LEFT_PREV, EncoderMenu.BTN_LEFT_NEXT, "gui.ae2apotheotic.material.left");
        drawSelector(g, mouseX, mouseY, 112, EncoderMenu.D_RIGHT_INDEX, EncoderMenu.D_RIGHT_COUNT,
            EncoderMenu.BTN_RIGHT_PREV, EncoderMenu.BTN_RIGHT_NEXT, "gui.ae2apotheotic.material.right");
        drawSelector(g, mouseX, mouseY, 176, EncoderMenu.D_RECIPE_INDEX, EncoderMenu.D_RECIPE_COUNT,
            EncoderMenu.BTN_RECIPE_PREV, EncoderMenu.BTN_RECIPE_NEXT, "gui.ae2apotheotic.recipe.next");
        boolean selectors = menu.get(EncoderMenu.D_LEFT_COUNT) > 1 || menu.get(EncoderMenu.D_RIGHT_COUNT) > 1 || menu.get(EncoderMenu.D_RECIPE_COUNT) > 1;
        if (menu.get(EncoderMenu.D_PURITY) >= 0 && !selectors) {
            fitText(g, Component.translatable("gui.ae2apotheotic.chain_hint"), CONTENT_X, 104, 160, 0xFF7A7A82);
        }
    }

    /** Under a preview slot at x: "‹ i/n ›" when the slot has several options. */
    private void drawSelector(GuiGraphicsExtractor g, int mouseX, int mouseY, int slotX, int indexData, int countData, int prevBtn,
        int nextBtn, String tooltipKey) {
        int count = menu.get(countData);
        if (count <= 1) {
            return;
        }
        Component tooltip = Component.translatable(tooltipKey);
        flatZone(g, mouseX, mouseY, slotX - 2, 86, 9, 10, Component.literal("‹"), tooltip, rel -> sendButton(prevBtn), false);
        flatZone(g, mouseX, mouseY, slotX + 9, 86, 9, 10, Component.literal("›"), tooltip, rel -> sendButton(nextBtn), false);
        centeredText(g, Component.literal((menu.get(indexData) + 1) + "/" + count), slotX + 8, 98, TEXT_DIM);
    }

    private void drawPurityChips(GuiGraphicsExtractor g, int mouseX, int mouseY, int selected, boolean upgrade) {
        int min = menu.get(EncoderMenu.D_PURITY_MIN);
        int max = menu.get(EncoderMenu.D_PURITY_MAX);
        Purity[] purities = Purity.values();
        for (int i = 0; i < purities.length; i++) {
            int cx = CONTENT_X + 2 + i * 26;
            int cy = 38;
            boolean enabled = i >= min && i <= max;
            boolean isFrom = i == selected;
            boolean isTo = upgrade && i == selected + 1;
            boolean hover = enabled && inside(cx, cy, 22, 10, mouseX, mouseY);
            int border = isFrom ? 0xFFFFFFFF : isTo ? accent : hover ? 0xFFB0B0B0 : 0xFF101010;
            g.fill(cx, cy, cx + 22, cy + 10, border);
            g.fill(cx + 1, cy + 1, cx + 21, cy + 9, 0xFF000000 | purities[i].getColor().getValue());
            if (!enabled && !isTo) {
                g.fill(cx + 1, cy + 1, cx + 21, cy + 9, 0xB0202024);
            }
            if (isFrom || isTo) {
                g.fill(cx + 1, cy + 8, cx + 21, cy + 9, isFrom ? 0xFFFFFFFF : accent);
            }
            int target = i;
            zones.add(new Zone(cx, cy, 22, 10, purities[i].toComponent(), enabled ? rel -> sendButton(EncoderMenu.BTN_PURITY_SET + target) : null));
        }
    }

    private void drawArrow(GuiGraphicsExtractor g, int x0, int x1, int y) {
        g.fill(x0, y, x1 - 3, y + 1, 0xFFB0B0B8);
        for (int i = 0; i < 4; i++) {
            g.fill(x1 - 4 + i - 3, y - 3 + i, x1 - 3 + i - 3, y + 4 - i, 0xFFB0B0B8);
        }
    }

    // ---- crush ----

    private void drawCrush(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int ordinal = menu.get(EncoderMenu.D_PURITY);
        int yield;
        if (ordinal >= 0 && ordinal < Purity.values().length) {
            drawPurityChips(g, mouseX, mouseY, ordinal, false);
            centeredFit(g, Purity.values()[ordinal].toComponent(), 121, 51, 156, TEXT_LIGHT);
            yield = GemCrushEncoding.dustYield(Purity.values()[ordinal]);
        } else {
            centeredFit(g, Component.translatable("gui.ae2apotheotic.crush_mode.any"), 121, 44, 156, TEXT_LIGHT);
            yield = GemCrushEncoding.dustYield(Purity.CRACKED);
        }
        Component ratio = yield == 1
            ? Component.translatable("gui.ae2apotheotic.crush.ratio.one")
            : Component.translatable("gui.ae2apotheotic.crush.ratio.many", yield);
        centeredFit(g, ratio, 117, 64, 100, TEXT_DIM);
        drawArrow(g, 66, 170, 76);

        GemCrushEncoding.CrushMode current = GemCrushEncoding.CrushMode.byId(menu.get(EncoderMenu.D_CRUSH_MODE));
        String[] keys = { "exact", "same_purity", "any" };
        for (int i = 0; i < 3; i++) {
            GemCrushEncoding.CrushMode mode = GemCrushEncoding.CrushMode.byId(i);
            Component full = switch (mode) {
                case EXACT -> Component.translatable("gui.ae2apotheotic.crush_mode.exact");
                case SAME_PURITY -> Component.translatable("gui.ae2apotheotic.crush_mode.same_purity", purityName());
                case ANY -> Component.translatable("gui.ae2apotheotic.crush_mode.any");
            };
            Component tooltip = Component.empty().append(full).append("\n")
                .append(Component.translatable("gui.ae2apotheotic.crush_mode.tooltip").withStyle(ChatFormatting.GRAY));
            int target = i;
            flatZone(g, mouseX, mouseY, CONTENT_X + 2 + i * 53, 92, 51, 14,
                Component.translatable("gui.ae2apotheotic.crush_mode.short." + keys[i]), tooltip,
                rel -> sendButton(EncoderMenu.BTN_CRUSH_SET + target), mode == current);
        }
    }

    private Component purityName() {
        int ordinal = menu.get(EncoderMenu.D_PURITY);
        return ordinal >= 0 && ordinal < Purity.values().length ? Purity.values()[ordinal].toComponent() : Component.literal("?");
    }

    // ---- infusion ----

    private void drawInfusion(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int count = menu.get(EncoderMenu.D_RECIPE_COUNT);
        if (count > 1) {
            flatZone(g, mouseX, mouseY, CONTENT_X, 41, 10, 12, Component.literal("‹"), Component.translatable("gui.ae2apotheotic.recipe.prev"),
                rel -> sendButton(EncoderMenu.BTN_RECIPE_PREV), false);
            flatZone(g, mouseX, mouseY, 142, 41, 10, 12, Component.literal("›"), Component.translatable("gui.ae2apotheotic.recipe.next"),
                rel -> sendButton(EncoderMenu.BTN_RECIPE_NEXT), false);
        }
        if (count > 0) {
            centeredFit(g, Component.translatable("gui.ae2apotheotic.recipe.counter", menu.get(EncoderMenu.D_RECIPE_INDEX) + 1, count),
                97, 43, 86, TEXT_LIGHT);
        } else {
            centeredFit(g, Component.translatable("gui.ae2apotheotic.infusion.hint"), 97, 43, 110, TEXT_DIM);
            return;
        }
        g.text(font, "⇒", 176, 44, TEXT_DIM, false);

        int[] values = { menu.get(EncoderMenu.D_ETERNA), menu.get(EncoderMenu.D_QUANTA), menu.get(EncoderMenu.D_ARCANA) };
        int[] mins = { menu.get(EncoderMenu.D_REQ_ETERNA), menu.get(EncoderMenu.D_REQ_QUANTA), menu.get(EncoderMenu.D_REQ_ARCANA) };
        int[] maxs = { menu.get(EncoderMenu.D_MAX_ETERNA), menu.get(EncoderMenu.D_MAX_QUANTA), menu.get(EncoderMenu.D_MAX_ARCANA) };
        for (int stat = 0; stat < 3; stat++) {
            drawStatBar(g, mouseX, mouseY, stat, values[stat], mins[stat], maxs[stat]);
        }
        fitText(g, Component.translatable("gui.ae2apotheotic.xp_cost", menu.xpCost()), CONTENT_X, 102, 112, 0xFFE8D060);
        flatZone(g, mouseX, mouseY, 160, 100, 40, 12, Component.translatable("gui.ae2apotheotic.auto"),
            Component.translatable("gui.ae2apotheotic.auto.tooltip"), rel -> sendButton(EncoderMenu.BTN_AUTO_STATS), false);
    }

    private void drawStatBar(GuiGraphicsExtractor g, int mouseX, int mouseY, int stat, int value, int min10, int max10) {
        int y = STAT_ROWS[stat];
        int color = STAT_COLORS[stat];
        fitText(g, Component.translatable("gui.ae2apotheotic." + STAT_KEYS[stat]), CONTENT_X, y, 32, color);

        // bar with the range the recipe accepts
        g.fill(BAR_X - 1, y - 1, BAR_X + BAR_W + 1, y + 8, 0xFF000000);
        g.fill(BAR_X, y, BAR_X + BAR_W, y + 7, 0xFF18181C);
        int lo = min10 == EncoderLogic.NO_VALUE ? 0 : min10;
        int hi = max10 == EncoderLogic.NO_VALUE ? 1000 : max10;
        int zx0 = BAR_X + Math.round(BAR_W * Math.min(lo, 1000) / 1000F);
        int zx1 = BAR_X + Math.round(BAR_W * Math.min(hi, 1000) / 1000F);
        if (zx1 > zx0) {
            g.fill(zx0, y, zx1, y + 7, 0x4040E060);
            g.fill(zx0, y + 6, zx1, y + 7, 0xFF40C060);
        }
        int vx = BAR_X + Math.round(BAR_W * value / 100F);
        g.fill(BAR_X, y + 1, vx, y + 5, (color & 0x00FFFFFF) | 0xA0000000);
        if (stat == 0) {
            int maxEterna = menu.get(EncoderMenu.D_PLAYER_MAX_ETERNA);
            if (maxEterna < 100) {
                int mx = BAR_X + Math.round(BAR_W * maxEterna / 100F);
                g.fill(mx, y - 1, mx + 1, y + 8, 0xFFFF4040);
            }
        }
        g.fill(Math.min(vx, BAR_X + BAR_W - 1), y - 1, Math.min(vx, BAR_X + BAR_W - 1) + 1, y + 8, 0xFFFFFFFF);
        boolean inRange = value * 10 >= lo && value * 10 <= hi;
        Component tooltip = Component.translatable("gui.ae2apotheotic.stat.bar", Component.translatable("gui.ae2apotheotic." + STAT_KEYS[stat]),
            rangeText(min10, max10));
        zones.add(new Zone(BAR_X, y - 1, BAR_W, 9, tooltip,
            rel -> sendButton(EncoderMenu.BTN_STAT_SET + stat * 101 + Math.round((float) Math.clamp(rel, 0, 1) * 100))));

        String text = String.valueOf(value);
        g.text(font, text, 172 - font.width(text), y, inRange ? TEXT_LIGHT : 0xFFFF7070, false);
        Component stepTip = Component.translatable("gui.ae2apotheotic.stat.tooltip");
        flatZone(g, mouseX, mouseY, 175, y - 2, 12, 11, Component.literal("-"), stepTip,
            rel -> sendButton(EncoderMenu.BTN_STAT + stat * 4 + (shiftDown() ? 2 : 0)), false);
        flatZone(g, mouseX, mouseY, 188, y - 2, 12, 11, Component.literal("+"), stepTip,
            rel -> sendButton(EncoderMenu.BTN_STAT + stat * 4 + (shiftDown() ? 3 : 1)), false);
    }

    private static String fmt(int tenths) {
        return tenths % 10 == 0 ? String.valueOf(tenths / 10) : String.format("%.1f", tenths / 10.0F);
    }

    private static String rangeText(int min10, int max10) {
        String min = min10 == EncoderLogic.NO_VALUE ? "0" : fmt(min10);
        return max10 == EncoderLogic.NO_VALUE ? "≥ " + min : min + " – " + fmt(max10);
    }

    // ---- status and encode ----

    private void drawStatus(GuiGraphicsExtractor g) {
        EncoderStatus status = menu.status();
        Component text = status.isOk() && !menu.canEncode() ? Component.translatable("gui.ae2apotheotic.status.need_blank") : status.text();
        centeredFit(g, text, imageWidth / 2, 119, PANEL_X1 - PANEL_X0 - 8, statusColors()[1]);
    }

    private void drawEncodeButton(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = 48;
        int y = 131;
        int w = 112;
        int h = 20;
        boolean enabled = menu.canEncode();
        boolean hover = enabled && inside(x, y, w, h, mouseX, mouseY);
        boolean chain = shiftDown() && menu.mode() == EncoderBlockEntity.MODE_UPGRADE;
        g.fill(x, y, x + w, y + h, 0xFF1A1A1A);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, !enabled ? 0xFF6A6A6A : hover ? 0xFF9A64E6 : 0xFF7A48C4);
        if (enabled) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x60FFFFFF);
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x40000000);
        }
        Component label = Component.translatable(chain ? "gui.ae2apotheotic.encode.chain" : "gui.ae2apotheotic.encode");
        centeredFit(g, label, x + w / 2, y + 6, w - 8, enabled ? 0xFFFFFFFF : 0xFF3A3A3A);
        // arrows from the blank pattern to the button and from the button to the output
        g.text(font, "›", 43, 137, TEXT_GRAY, false);
        g.text(font, "›", 162, 137, TEXT_GRAY, false);
        zones.add(new Zone(x, y, w, h, Component.translatable("gui.ae2apotheotic.encode.tooltip"),
            enabled ? rel -> sendButton(chain ? EncoderMenu.BTN_ENCODE_CHAIN : EncoderMenu.BTN_ENCODE) : null));
    }

    /** Draws a flat button and registers it as a zone. */
    private void flatZone(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, Component label, @Nullable Component tooltip,
        ClickAction action, boolean selected) {
        drawFlatButton(g, x, y, w, h, label, inside(x, y, w, h, mouseX, mouseY), true, selected);
        zones.add(new Zone(x, y, w, h, tooltip, selected ? null : action));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Input and tooltips

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (Zone zone : zones) {
                if (zone.action() != null && inside(zone.x(), zone.y(), zone.w(), zone.h(), event.x(), event.y())) {
                    clickSound();
                    double rel = (event.x() - leftPos - zone.x()) / Math.max(1, zone.w());
                    zone.action().click(rel);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            return;
        }
        for (Zone zone : zones) {
            if (zone.tooltip() != null && inside(zone.x(), zone.y(), zone.w(), zone.h(), mouseX, mouseY)) {
                tooltip(g, zone.tooltip(), mouseX, mouseY);
                return;
            }
        }
        if (inside(14, 40, 16, 16, mouseX, mouseY) && menu.getSlot(EncoderMenu.SLOT_SAMPLE).getItem().isEmpty()) {
            tooltip(g, Component.translatable("gui.ae2apotheotic.sample.tooltip"), mouseX, mouseY);
        } else if (inside(24, 133, 16, 16, mouseX, mouseY) && menu.getSlot(EncoderMenu.SLOT_BLANK).getItem().isEmpty()) {
            tooltip(g, Component.translatable("gui.ae2apotheotic.blank.tooltip"), mouseX, mouseY);
        }
    }
}
