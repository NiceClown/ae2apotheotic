package com.ae2apotheotic.client;

import com.ae2apotheotic.blockentity.GemProviderBlockEntity;
import com.ae2apotheotic.blockentity.InfusionProviderBlockEntity;
import com.ae2apotheotic.blockentity.ProviderProblem;
import com.ae2apotheotic.menu.ProviderMenu;
import com.ae2apotheotic.pattern.GemKeys;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class ProviderScreen extends FlatScreen<ProviderMenu> {
    private static final int GEM_ACCENT = 0xFF2FA8C0;
    private static final int INFUSION_ACCENT = 0xFFB0306A;
    private static final int GEMS_PER_PAGE = 8;

    private static final String[] TIER_LABELS = { "I", "II", "III", "IV", "V", "Dust" };

    private final List<Button> xpButtons = new ArrayList<>();
    private int gemPage = 0;

    public ProviderScreen(ProviderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable(menu.isInfusion() ? "gui.ae2apotheotic.title.infusion_provider" : "gui.ae2apotheotic.title.gem_provider"),
            menu.isInfusion() ? 176 : 210, menu.isInfusion() ? 196 : 202, menu.isInfusion() ? INFUSION_ACCENT : GEM_ACCENT);
        this.inventoryLabelY = menu.isInfusion() ? 104 : 108;
        this.inventoryLabelX = menu.isInfusion() ? 8 : 24;
    }

    @Override
    protected void init() {
        super.init();
        xpButtons.clear();
        if (menu.isInfusion()) {
            addGuideButton("ae2apotheotic/infusion_pattern_provider.md");
            xpButtons.add(button(70, 72, 32, 12, Component.translatable("gui.ae2apotheotic.xp.deposit_level"),
                Component.translatable("gui.ae2apotheotic.xp.deposit_level.tooltip"), b -> sendButton(ProviderMenu.BUTTON_DEPOSIT_LEVEL)));
            xpButtons.add(button(104, 72, 32, 12, Component.translatable("gui.ae2apotheotic.xp.deposit_all"),
                Component.translatable("gui.ae2apotheotic.xp.deposit_all.tooltip"), b -> sendButton(ProviderMenu.BUTTON_DEPOSIT_ALL)));
            xpButtons.add(button(138, 72, 32, 12, Component.translatable("gui.ae2apotheotic.xp.withdraw"),
                Component.translatable("gui.ae2apotheotic.xp.withdraw.tooltip"), b -> sendButton(ProviderMenu.BUTTON_WITHDRAW_ALL)));
        } else {
            addGuideButton("ae2apotheotic/gem_pattern_provider.md");
            // Initialize page to the selected category
            int cat = menu.getSelectedCategory();
            gemPage = cat / GEMS_PER_PAGE;
        }
    }

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, int x, int y) {
        if (!menu.isInfusion()) {
            // Gem category selector strip background
            g.fill(x + 6, y + 35, x + 204, y + 55, 0xFF353535);
            g.fill(x + 7, y + 36, x + 203, y + 54, 0xFF2A2A2A);

            // Tier slots container background
            g.fill(x + 39, y + 78, x + 171, y + 103, 0xFF353535);
            g.fill(x + 40, y + 79, x + 170, y + 102, 0xFF282828);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        boolean online = menu.isOnline();
        int flags = menu.machineFlags();

        if (menu.isInfusion()) {
            int x = 70;
            g.text(font, Component.translatable(online ? "gui.ae2apotheotic.online" : "gui.ae2apotheotic.offline"), x, 20,
                online ? TEXT_GREEN : TEXT_RED, false);

            Component machine;
            int machineColor;
            if ((flags & InfusionProviderBlockEntity.FLAG_RAVEN) != 0) {
                machine = Component.translatable("gui.ae2apotheotic.table.raven");
                machineColor = TEXT_GREEN;
            } else if ((flags & InfusionProviderBlockEntity.FLAG_TABLE) != 0) {
                machine = Component.translatable("gui.ae2apotheotic.table.shelves");
                machineColor = TEXT_YELLOW;
            } else {
                machine = Component.translatable("gui.ae2apotheotic.table.missing");
                machineColor = TEXT_RED;
            }
            g.text(font, machine, x, 31, machineColor, false);
            g.text(font, Component.translatable("gui.ae2apotheotic.patterns", menu.patternCount()), x, 42, TEXT, false);

            g.text(font, Component.translatable("gui.ae2apotheotic.xp.stored", menu.storedXp()), x, 53, TEXT, false);
            g.text(font, Component.translatable("gui.ae2apotheotic.xp.nearby", menu.nearbyXp()), x, 63, TEXT, false);

            ProviderProblem problem = menu.problem();
            if (problem != ProviderProblem.NONE) {
                g.text(font, problem.text(), 8, 94, TEXT_RED, false);
            }
        } else {
            // Gem Provider: Network status on top right
            Component netComp = Component.translatable(online ? "gui.ae2apotheotic.online" : "gui.ae2apotheotic.offline");
            g.text(font, netComp, 190 - font.width(netComp), 6, online ? TEXT_GREEN : TEXT_RED, false);

            // Gem Case status at y = 17
            boolean hasCase = (flags & GemProviderBlockEntity.FLAG_GEM_CASE) != 0;
            Component machine = Component.translatable(hasCase ? "gui.ae2apotheotic.gem_case.found" : "gui.ae2apotheotic.gem_case.missing");
            int machineColor = hasCase ? TEXT_GREEN : TEXT_YELLOW;
            g.text(font, machine, 8, 17, machineColor, false);

            // Problem alert at y = 27
            ProviderProblem problem = menu.problem();
            if (problem != ProviderProblem.NONE) {
                g.text(font, problem.text(), 8, 27, TEXT_RED, false);
            }

            // Gem category strip
            List<Gem> gems = GemKeys.sortedGems();
            int totalCategories = 1 + gems.size();
            int totalPages = Math.max(1, (totalCategories + GEMS_PER_PAGE - 1) / GEMS_PER_PAGE);
            if (gemPage >= totalPages) {
                gemPage = 0;
            }

            // Strip Page Arrows
            drawSmallButton(g, 8, 37, 12, 17, "<", isHovering(8, 37, 12, 17, mouseX, mouseY));
            drawSmallButton(g, 169, 37, 12, 17, ">", isHovering(169, 37, 12, 17, mouseX, mouseY));

            // Page text
            String pageStr = (gemPage + 1) + "/" + totalPages;
            g.text(font, pageStr, 183, 42, TEXT_GRAY, false);

            // 8 Gem buttons
            int selectedCat = menu.getSelectedCategory();
            for (int i = 0; i < GEMS_PER_PAGE; i++) {
                int catIdx = gemPage * GEMS_PER_PAGE + i;
                int btnX = 23 + i * 18;
                int btnY = 37;
                if (catIdx < totalCategories) {
                    boolean hovered = isHovering(btnX, btnY, 18, 17, mouseX, mouseY);
                    boolean isSelected = (catIdx == selectedCat);

                    int bg = isSelected ? 0xFF3B5E6B : (hovered ? 0xFF555555 : 0xFF404040);
                    int border = isSelected ? GEM_ACCENT : (hovered ? 0xFF888888 : 0xFF222222);
                    g.fill(btnX, btnY, btnX + 18, btnY + 17, border);
                    g.fill(btnX + 1, btnY + 1, btnX + 17, btnY + 16, bg);

                    ItemStack icon = getCategoryIcon(catIdx, gems);
                    g.fakeItem(icon, btnX + 1, btnY + 1);

                    // Show badge if has patterns
                    int count = menu.getCategoryPatternCount(catIdx);
                    if (count > 0) {
                        g.fill(btnX + 14, btnY + 1, btnX + 17, btnY + 4, count >= (catIdx == 0 ? 6 : 5) ? 0xFF28A745 : 0xFFE0A800);
                    }
                }
            }

            // Category Title row at y = 57..70
            drawSmallButton(g, 8, 57, 12, 12, "<", isHovering(8, 57, 12, 12, mouseX, mouseY));
            drawSmallButton(g, 190, 57, 12, 12, ">", isHovering(190, 57, 12, 12, mouseX, mouseY));

            ItemStack curIcon = getCategoryIcon(selectedCat, gems);
            g.fakeItem(curIcon, 24, 55);

            Component catName = getCategoryName(selectedCat, gems);
            g.text(font, catName, 44, 59, TEXT, false);

            int installed = menu.getCategoryPatternCount(selectedCat);
            int maxTiers = (selectedCat == 0 ? 6 : 5);
            Component countComp = Component.translatable("gui.ae2apotheotic.tier.installed", installed, maxTiers);
            int countWidth = font.width(countComp);
            g.text(font, countComp, 186 - countWidth, 59, installed >= maxTiers ? TEXT_GREEN : TEXT_GRAY, false);

            // Tier slot labels (above slots at y = 71)
            for (int t = 0; t < 6; t++) {
                int slotX = 43 + t * 21;
                centeredText(g, Component.literal(TIER_LABELS[t]), slotX + 9, 71, TEXT_GRAY);

                // If empty slot, draw faint ghost tier number at y = 87
                int slotIdx = selectedCat * GemProviderBlockEntity.SLOTS_PER_CATEGORY + t;
                if (slotIdx < menu.slots.size() && !menu.slots.get(slotIdx).hasItem()) {
                    centeredText(g, Component.literal(TIER_LABELS[t]), slotX + 9, 87, 0xFFA0A0A0);
                }
            }
        }
    }

    private void drawSmallButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, boolean hovered) {
        g.fill(x, y, x + w, y + h, hovered ? 0xFF888888 : 0xFF303030);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hovered ? 0xFF666666 : 0xFF4A4A4A);
        centeredText(g, Component.literal(text), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    private ItemStack getCategoryIcon(int catIdx, List<Gem> gems) {
        if (catIdx == 0) {
            return new ItemStack(Apoth.Items.GEM_DUST.value());
        }
        int gemIdx = catIdx - 1;
        if (gemIdx < gems.size()) {
            return GemItem.createStack(gems.get(gemIdx), Purity.NORMAL, 1);
        }
        return ItemStack.EMPTY;
    }

    private Component getCategoryName(int catIdx, List<Gem> gems) {
        if (catIdx == 0) {
            return Component.translatable("gui.ae2apotheotic.category.general");
        }
        int gemIdx = catIdx - 1;
        if (gemIdx < gems.size()) {
            return gems.get(gemIdx).toStack(Purity.NORMAL).getHoverName();
        }
        return Component.literal("?");
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (menu.isInfusion()) {
            if (isHovering(70, 52, 100, 20, mouseX, mouseY)) {
                g.setTooltipForNextFrame(font, Component.translatable("gui.ae2apotheotic.xp.tooltip"), mouseX, mouseY);
            }
        } else {
            // Gem Provider tooltips
            boolean hasCase = (menu.machineFlags() & GemProviderBlockEntity.FLAG_GEM_CASE) != 0;
            if (!hasCase) {
                Component machine = Component.translatable("gui.ae2apotheotic.gem_case.missing");
                int width = font.width(machine);
                if (isHovering(8, 17, width, 11, mouseX, mouseY)) {
                    g.setTooltipForNextFrame(font, Component.translatable("gui.ae2apotheotic.gem_case.missing.tooltip"), mouseX, mouseY);
                }
            }

            List<Gem> gems = GemKeys.sortedGems();
            int totalCategories = 1 + gems.size();

            // Gem strip button tooltips
            for (int i = 0; i < GEMS_PER_PAGE; i++) {
                int catIdx = gemPage * GEMS_PER_PAGE + i;
                int btnX = 23 + i * 18;
                int btnY = 37;
                if (catIdx < totalCategories && isHovering(btnX, btnY, 18, 17, mouseX, mouseY)) {
                    Component name = getCategoryName(catIdx, gems);
                    int count = menu.getCategoryPatternCount(catIdx);
                    int maxTiers = (catIdx == 0 ? 6 : 5);
                    Component tip = Component.literal(name.getString() + " (" + count + "/" + maxTiers + ")");
                    g.setTooltipForNextFrame(font, tip, mouseX, mouseY);
                }
            }

            // Empty tier slots tooltips
            int selectedCat = menu.getSelectedCategory();
            for (int t = 0; t < 6; t++) {
                int slotX = 43 + t * 21;
                if (isHovering(slotX, 82, 18, 18, mouseX, mouseY)) {
                    int slotIdx = selectedCat * GemProviderBlockEntity.SLOTS_PER_CATEGORY + t;
                    if (slotIdx < menu.slots.size() && !menu.slots.get(slotIdx).hasItem()) {
                        Component tierTip;
                        if (selectedCat == 0) {
                            tierTip = Component.translatable("gui.ae2apotheotic.tier.crush");
                        } else {
                            tierTip = switch (t) {
                                case 0 -> Component.translatable("gui.ae2apotheotic.tier.1");
                                case 1 -> Component.translatable("gui.ae2apotheotic.tier.2");
                                case 2 -> Component.translatable("gui.ae2apotheotic.tier.3");
                                case 3 -> Component.translatable("gui.ae2apotheotic.tier.4");
                                case 4 -> Component.translatable("gui.ae2apotheotic.tier.5");
                                default -> Component.translatable("gui.ae2apotheotic.tier.crush");
                            };
                        }
                        g.setTooltipForNextFrame(font, tierTip, mouseX, mouseY);
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        if (!menu.isInfusion()) {
            double mouseX = event.x();
            double mouseY = event.y();
            List<Gem> gems = GemKeys.sortedGems();
            int totalCategories = 1 + gems.size();
            int totalPages = Math.max(1, (totalCategories + GEMS_PER_PAGE - 1) / GEMS_PER_PAGE);

            // Strip Page Arrows
            if (isHovering(8, 37, 12, 17, mouseX, mouseY)) {
                gemPage = Math.floorMod(gemPage - 1, totalPages);
                return true;
            }
            if (isHovering(169, 37, 12, 17, mouseX, mouseY)) {
                gemPage = Math.floorMod(gemPage + 1, totalPages);
                return true;
            }

            // 8 Gem buttons
            for (int i = 0; i < GEMS_PER_PAGE; i++) {
                int catIdx = gemPage * GEMS_PER_PAGE + i;
                int btnX = 23 + i * 18;
                int btnY = 37;
                if (catIdx < totalCategories && isHovering(btnX, btnY, 18, 17, mouseX, mouseY)) {
                    sendButton(catIdx);
                    menu.setSelectedCategory(catIdx);
                    return true;
                }
            }

            // Category Cycle Arrows
            if (isHovering(8, 57, 12, 12, mouseX, mouseY)) {
                int nextCat = Math.floorMod(menu.getSelectedCategory() - 1, totalCategories);
                sendButton(nextCat);
                menu.setSelectedCategory(nextCat);
                gemPage = nextCat / GEMS_PER_PAGE;
                return true;
            }
            if (isHovering(190, 57, 12, 12, mouseX, mouseY)) {
                int nextCat = Math.floorMod(menu.getSelectedCategory() + 1, totalCategories);
                sendButton(nextCat);
                menu.setSelectedCategory(nextCat);
                gemPage = nextCat / GEMS_PER_PAGE;
                return true;
            }
        }
        return super.mouseClicked(event, isFocused);
    }
}
