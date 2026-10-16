package com.ae2apotheotic.client;

import com.ae2apotheotic.blockentity.GemProviderBlockEntity;
import com.ae2apotheotic.blockentity.InfusionProviderBlockEntity;
import com.ae2apotheotic.blockentity.ProviderProblem;
import com.ae2apotheotic.menu.ProviderMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ProviderScreen extends FlatScreen<ProviderMenu> {
    private static final int GEM_ACCENT = 0xFF2FA8C0;
    private static final int INFUSION_ACCENT = 0xFFB0306A;

    private final List<Button> xpButtons = new ArrayList<>();

    public ProviderScreen(ProviderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable(menu.isInfusion() ? "gui.ae2apotheotic.title.infusion_provider" : "gui.ae2apotheotic.title.gem_provider"),
            176, 196, menu.isInfusion() ? INFUSION_ACCENT : GEM_ACCENT);
        this.inventoryLabelY = 104;
    }

    @Override
    protected void init() {
        super.init();
        xpButtons.clear();
        if (menu.isInfusion()) {
            xpButtons.add(button(70, 72, 32, 12, Component.translatable("gui.ae2apotheotic.xp.deposit_level"),
                Component.translatable("gui.ae2apotheotic.xp.deposit_level.tooltip"), b -> sendButton(ProviderMenu.BUTTON_DEPOSIT_LEVEL)));
            xpButtons.add(button(104, 72, 32, 12, Component.translatable("gui.ae2apotheotic.xp.deposit_all"),
                Component.translatable("gui.ae2apotheotic.xp.deposit_all.tooltip"), b -> sendButton(ProviderMenu.BUTTON_DEPOSIT_ALL)));
            xpButtons.add(button(138, 72, 32, 12, Component.translatable("gui.ae2apotheotic.xp.withdraw"),
                Component.translatable("gui.ae2apotheotic.xp.withdraw.tooltip"), b -> sendButton(ProviderMenu.BUTTON_WITHDRAW_ALL)));
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
            // Gem Provider (27 pattern slots)
            g.text(font, Component.translatable(online ? "gui.ae2apotheotic.online" : "gui.ae2apotheotic.offline"), 8, 17,
                online ? TEXT_GREEN : TEXT_RED, false);

            boolean hasCase = (flags & GemProviderBlockEntity.FLAG_GEM_CASE) != 0;
            Component machine = Component.translatable(hasCase ? "gui.ae2apotheotic.gem_case.found" : "gui.ae2apotheotic.gem_case.missing");
            int machineColor = hasCase ? TEXT_GREEN : TEXT_YELLOW;
            g.text(font, machine, 8, 28, machineColor, false);

            ProviderProblem problem = menu.problem();
            if (problem != ProviderProblem.NONE) {
                g.text(font, problem.text(), 8, 94, TEXT_RED, false);
            }
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (!menu.isInfusion()) {
            boolean hasCase = (menu.machineFlags() & GemProviderBlockEntity.FLAG_GEM_CASE) != 0;
            if (!hasCase) {
                Component machine = Component.translatable("gui.ae2apotheotic.gem_case.missing");
                int width = font.width(machine);
                if (isHovering(8, 27, width, 11, mouseX, mouseY)) {
                    g.setTooltipForNextFrame(font, Component.translatable("gui.ae2apotheotic.gem_case.missing.tooltip"), mouseX, mouseY);
                }
            }
        } else {
            if (isHovering(70, 52, 100, 20, mouseX, mouseY)) {
                g.setTooltipForNextFrame(font, Component.translatable("gui.ae2apotheotic.xp.tooltip"), mouseX, mouseY);
            }
        }
    }
}
