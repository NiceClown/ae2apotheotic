package com.ae2apotheotic.client;

import com.ae2apotheotic.blockentity.GemProviderBlockEntity;
import com.ae2apotheotic.blockentity.ProviderProblem;
import com.ae2apotheotic.menu.GemProviderMenu;
import com.ae2apotheotic.registry.ModRegistry;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.client.PipelinedRenderer;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Gem Pattern Provider GUI: a searchable grid of categories (bulk crushing + one per gem) and the slots of the
 * selected category below it.
 */
public class GemProviderScreen extends FlatScreen<GemProviderMenu> {
    private static final int ACCENT = 0xFF2FA8C0;
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 34;
    private static final int SCROLL_X = 173;
    private static final int HEADER_Y = 91;
    private static final String[] TIER_LABELS = { "I", "II", "III", "IV", "V" };

    private final List<ItemStack> icons = new ArrayList<>();
    private final List<Component> names = new ArrayList<>();
    private final List<String> searchKeys = new ArrayList<>();
    private final List<Integer> visible = new ArrayList<>();
    private @Nullable EditBox search;
    private boolean onlyUsed;
    private int scrollRow;
    private boolean draggingScroll;
    private int lastSelected = -1;

    public GemProviderScreen(GemProviderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable("gui.ae2apotheotic.title.gem_provider"), 188, 266, ACCENT);
        this.inventoryLabelX = 13;
        this.inventoryLabelY = 173;
        for (int cat = 0; cat < menu.categoryCount(); cat++) {
            ItemStack icon;
            Component name;
            if (cat == GemProviderMenu.BULK) {
                icon = new ItemStack(Apoth.Items.GEM_DUST.value());
                name = Component.translatable("gui.ae2apotheotic.category.bulk");
            } else {
                Gem gem = menu.categoryGem(cat);
                Identifier id = menu.categoryId(cat);
                if (gem != null) {
                    icon = GemItem.createStack(gem, Purity.PERFECT, 1);
                    name = Component.translatable(((GemItem) icon.getItem()).getGemDescriptionId(icon));
                } else {
                    icon = new ItemStack(ModRegistry.GEM_UPGRADE_PATTERN.get());
                    name = Component.literal(String.valueOf(id)).withStyle(ChatFormatting.RED);
                }
            }
            icons.add(icon);
            names.add(name);
            searchKeys.add((name.getString() + " " + menu.categoryId(cat)).toLowerCase(Locale.ROOT));
        }
    }

    @Override
    protected void init() {
        super.init();
        addGuideButton("ae2apotheotic/gem_pattern_provider.md");
        search = new EditBox(font, leftPos + 11, topPos + 20, 84, 10, search, Component.translatable("gui.ae2apotheotic.search"));
        search.setBordered(false);
        search.setTextColor(TEXT_LIGHT);
        search.setMaxLength(40);
        search.setHint(Component.translatable("gui.ae2apotheotic.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(s -> scrollRow = 0);
        addRenderableWidget(search);
        rebuildVisible();
        ensureSelectedVisible();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Category list

    private void rebuildVisible() {
        visible.clear();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        for (int cat = 0; cat < menu.categoryCount(); cat++) {
            if (cat != GemProviderMenu.BULK) {
                if (onlyUsed && menu.mask(cat) == 0) {
                    continue;
                }
                if (!query.isEmpty() && !searchKeys.get(cat).contains(query)) {
                    continue;
                }
            }
            visible.add(cat);
        }
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll());
    }

    private int maxScroll() {
        int rows = (visible.size() + COLS - 1) / COLS;
        return Math.max(0, rows - ROWS);
    }

    private void ensureSelectedVisible() {
        int index = visible.indexOf(menu.selected());
        if (index >= 0) {
            int row = index / COLS;
            if (row < scrollRow) {
                scrollRow = row;
            } else if (row >= scrollRow + ROWS) {
                scrollRow = row - ROWS + 1;
            }
        }
    }

    /** Category under the mouse in the grid, or -1. */
    private int cellAt(double mouseX, double mouseY) {
        double rx = mouseX - leftPos - GRID_X;
        double ry = mouseY - topPos - GRID_Y;
        if (rx < 0 || ry < 0 || rx >= COLS * 18 || ry >= ROWS * 18) {
            return -1;
        }
        int index = (scrollRow + (int) (ry / 18)) * COLS + (int) (rx / 18);
        return index < visible.size() ? visible.get(index) : -1;
    }

    private int filled(int cat) {
        return cat == GemProviderMenu.BULK ? menu.bulkCount() : Integer.bitCount(menu.mask(cat));
    }

    private int capacity(int cat) {
        if (cat == GemProviderMenu.BULK) {
            return GemProviderMenu.BULK_SLOTS;
        }
        Gem gem = menu.categoryGem(cat);
        int count = 0;
        for (int tier = 0; tier < GemProviderMenu.GEM_SLOTS; tier++) {
            if (GemProviderBlockEntity.tierExists(gem, tier)) {
                count++;
            }
        }
        return count;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        rebuildVisible();
        if (menu.selected() != lastSelected) {
            lastSelected = menu.selected();
            ensureSelectedVisible();
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Drawing

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, int x, int y) {
        drawInset(g, x + 8, y + 17, x + 98, y + 31);
        drawInset(g, x + GRID_X - 1, y + GRID_Y - 1, x + GRID_X + COLS * 18 + 1, y + GRID_Y + ROWS * 18 + 1);
        // scrollbar
        drawInset(g, x + SCROLL_X, y + GRID_Y - 1, x + SCROLL_X + 8, y + GRID_Y + ROWS * 18 + 1);
        int max = maxScroll();
        int track = ROWS * 18 - 15;
        int knobY = y + GRID_Y + (max == 0 ? 0 : Math.round(track * (scrollRow / (float) max)));
        g.fill(x + SCROLL_X + 1, knobY, x + SCROLL_X + 7, knobY + 15, max == 0 ? 0xFF505050 : 0xFFB8B8B8);
        // header of the selected category
        drawInset(g, x + 7, y + HEADER_Y, x + imageWidth - 7, y + HEADER_Y + 17);
        if (menu.selected() != GemProviderMenu.BULK) {
            // separator between the upgrade chain and the crushing slot
            g.fill(x + 147, y + 113, x + 148, y + 146, 0xFF8B8B8B);
            for (int tier = 0; tier < GemProviderMenu.GEM_SLOTS; tier++) {
                if (!menu.tierExists(tier)) {
                    int sx = x + GemProviderMenu.GEM_SLOT_X[tier] - 1;
                    int sy = y + GemProviderMenu.GEM_SLOT_Y - 1;
                    g.fill(sx, sy, sx + 18, sy + 18, 0xFF5A5A5A);
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF6E6E6E);
                }
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        drawToolbar(g, mouseX, mouseY);
        drawGrid(g, mouseX, mouseY);
        drawHeader(g);
        if (menu.selected() == GemProviderMenu.BULK) {
            return;
        }
        Gem gem = menu.categoryGem(menu.selected());
        for (int tier = 0; tier < GemProviderMenu.GEM_SLOTS; tier++) {
            int sx = GemProviderMenu.GEM_SLOT_X[tier];
            int sy = GemProviderMenu.GEM_SLOT_Y;
            boolean exists = menu.tierExists(tier);
            if (tier < GemProviderBlockEntity.CRUSH_TIER) {
                Purity target = Purity.values()[tier + 1];
                int color = exists ? 0xFF000000 | target.getColor().getValue() : TEXT_DIM;
                centeredText(g, Component.literal(TIER_LABELS[tier]), sx + 8, sy - 11, color);
                if (tier < GemProviderBlockEntity.CRUSH_TIER - 1) {
                    g.text(font, "→", sx + 19, sy + 4, TEXT_GRAY, false);
                }
            } else {
                centeredFit(g, Component.translatable("gui.ae2apotheotic.tier.dust_short"), sx + 8, sy - 11, 30, TEXT);
            }
            if (!exists) {
                centeredText(g, Component.literal("×"), sx + 8, sy + 4, 0xFF4A4A4A);
            } else if (gem != null && !menu.getSlot(tier).hasItem()) {
                ItemStack ghost = tier == GemProviderBlockEntity.CRUSH_TIER
                    ? new ItemStack(Apoth.Items.GEM_DUST.value())
                    : GemItem.createStack(gem, Purity.values()[tier + 1], 1);
                PipelinedRenderer.ghostFakeItem(g, ghost, sx, sy);
            }
        }
        ProviderProblem problem = menu.problem();
        Component hint = problem != ProviderProblem.NONE ? problem.text() : Component.translatable("gui.ae2apotheotic.gem_provider.hint");
        centeredFit(g, hint, imageWidth / 2, 152, imageWidth - 16, problem != ProviderProblem.NONE ? TEXT_RED : TEXT_GRAY);
    }

    private void drawToolbar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        boolean hover = inside(101, 18, 40, 13, mouseX, mouseY);
        drawFlatButton(g, 101, 18, 40, 13,
            Component.translatable(onlyUsed ? "gui.ae2apotheotic.filter.used" : "gui.ae2apotheotic.filter.all"), hover, true, onlyUsed);
        drawIndicator(g, 147, 20, menu.isOnline() ? 0xFF3FD04A : 0xFFD03F3F, "N");
        drawIndicator(g, 159, 20, menu.hasGemCase() ? 0xFF3FD04A : 0xFFE0A030, "G");
        drawIndicator(g, 171, 20, 0xFF4A90D0, "i");
    }

    private void drawIndicator(GuiGraphicsExtractor g, int x, int y, int color, String letter) {
        g.fill(x, y, x + 10, y + 10, 0xFF1A1A1A);
        g.fill(x + 1, y + 1, x + 9, y + 9, color);
        centeredText(g, Component.literal(letter), x + 5, y + 1, 0xFF1A1A1A);
    }

    private void drawGrid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int hovered = cellAt(mouseX, mouseY);
        for (int slot = 0; slot < COLS * ROWS; slot++) {
            int index = scrollRow * COLS + slot;
            if (index >= visible.size()) {
                break;
            }
            int cat = visible.get(index);
            int cx = GRID_X + (slot % COLS) * 18;
            int cy = GRID_Y + (slot / COLS) * 18;
            boolean selected = cat == menu.selected();
            g.fill(cx, cy, cx + 18, cy + 18, selected ? accent : cat == hovered ? 0xFF9A9A9A : 0xFF202024);
            g.fill(cx + 1, cy + 1, cx + 17, cy + 17, selected ? 0xFF35505A : cat == hovered ? 0xFF4A4A50 : 0xFF34343A);
            g.fakeItem(icons.get(cat), cx + 1, cy + 1);
            int filled = filled(cat);
            if (filled > 0) {
                int cap = Math.max(1, capacity(cat));
                int width = Math.max(1, Math.round(14 * Math.min(1F, filled / (float) cap)));
                g.fill(cx + 2, cy + 15, cx + 16, cy + 17, 0xFF101010);
                g.fill(cx + 2, cy + 15, cx + 2 + width, cy + 17, filled >= cap ? 0xFF3FD04A : 0xFFE0B030);
            }
        }
        if (visible.size() <= 1 && search != null && !search.getValue().isEmpty()) {
            centeredText(g, Component.translatable("gui.ae2apotheotic.search.none"), GRID_X + COLS * 9, GRID_Y + 32, TEXT_DIM);
        }
    }

    private void drawHeader(GuiGraphicsExtractor g) {
        int cat = menu.selected();
        g.fakeItem(icons.get(cat), 9, HEADER_Y + 1);
        String count = filled(cat) + "/" + capacity(cat);
        int countWidth = font.width(count);
        fitText(g, names.get(cat), 28, HEADER_Y + 5, imageWidth - 46 - countWidth, TEXT_LIGHT);
        g.text(font, count, imageWidth - 10 - countWidth, HEADER_Y + 5, filled(cat) >= capacity(cat) ? 0xFF3FD04A : TEXT_DIM, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        int cell = cellAt(mouseX, mouseY);
        if (cell >= 0) {
            Component line = Component.translatable("gui.ae2apotheotic.category.patterns", filled(cell), capacity(cell)).withStyle(ChatFormatting.GRAY);
            g.setComponentTooltipForNextFrame(font, List.of(names.get(cell), line), mouseX, mouseY);
            return;
        }
        if (inside(147, 20, 10, 10, mouseX, mouseY)) {
            tooltip(g, Component.translatable(menu.isOnline() ? "gui.ae2apotheotic.online" : "gui.ae2apotheotic.offline"), mouseX, mouseY);
        } else if (inside(159, 20, 10, 10, mouseX, mouseY)) {
            tooltip(g, Component.translatable(menu.hasGemCase() ? "gui.ae2apotheotic.gem_case.found" : "gui.ae2apotheotic.gem_case.missing.tooltip"),
                mouseX, mouseY);
        } else if (inside(171, 20, 10, 10, mouseX, mouseY)) {
            tooltip(g, Component.translatable("gui.ae2apotheotic.gem_provider.terminal_info"), mouseX, mouseY);
        } else if (inside(101, 18, 40, 13, mouseX, mouseY)) {
            tooltip(g, Component.translatable("gui.ae2apotheotic.filter.tooltip"), mouseX, mouseY);
        } else if (menu.selected() != GemProviderMenu.BULK) {
            for (int tier = 0; tier < GemProviderMenu.GEM_SLOTS; tier++) {
                if (inside(GemProviderMenu.GEM_SLOT_X[tier], GemProviderMenu.GEM_SLOT_Y, 16, 16, mouseX, mouseY)
                    && !menu.getSlot(tier).hasItem()) {
                    Component text;
                    if (!menu.tierExists(tier)) {
                        Gem gem = menu.categoryGem(menu.selected());
                        text = Component.translatable("gui.ae2apotheotic.tier.locked", gem == null ? "?" : gem.getMinPurity().toComponent());
                    } else {
                        text = Component.translatable(tier == GemProviderBlockEntity.CRUSH_TIER ? "gui.ae2apotheotic.tier.crush" : "gui.ae2apotheotic.tier." + (tier + 1));
                    }
                    tooltip(g, text, mouseX, mouseY);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Input

    private void select(int cat) {
        if (cat != menu.selected()) {
            clickSound();
            menu.selectLocally(cat);
            sendButton(GemProviderMenu.BTN_SELECT + cat);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int cell = cellAt(event.x(), event.y());
            if (cell >= 0) {
                select(cell);
                return true;
            }
            if (inside(101, 18, 40, 13, event.x(), event.y())) {
                clickSound();
                onlyUsed = !onlyUsed;
                rebuildVisible();
                return true;
            }
            if (inside(SCROLL_X, GRID_Y, 8, ROWS * 18, event.x(), event.y())) {
                draggingScroll = true;
                scrollTo(event.y());
                return true;
            }
        }
        if (event.button() == 1 && search != null && inside(8, 17, 90, 14, event.x(), event.y())) {
            search.setValue("");
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void scrollTo(double mouseY) {
        float t = (float) (mouseY - topPos - GRID_Y - 7) / (ROWS * 18 - 15);
        scrollRow = Mth.clamp(Math.round(t * maxScroll()), 0, maxScroll());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingScroll) {
            scrollTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScroll = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(GRID_X, GRID_Y, COLS * 18 + 12, ROWS * 18, mouseX, mouseY)) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // While typing in the search box, letters must not close the screen or swap hotbar slots.
        if (search != null && search.isFocused() && !event.isEscape()) {
            search.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }
}
