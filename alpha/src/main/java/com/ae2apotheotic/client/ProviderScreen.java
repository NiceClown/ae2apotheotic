package com.ae2apotheotic.client;

import com.ae2apotheotic.blockentity.InfusionProviderBlockEntity;
import com.ae2apotheotic.blockentity.ProviderProblem;
import com.ae2apotheotic.menu.ProviderMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** GUI of the Infusion Pattern Provider. */
public class ProviderScreen extends FlatScreen<ProviderMenu> {
    private static final int ACCENT = 0xFFB0306A;
    private static final int INFO_X = 68;
    private static final int INFO_WIDTH = 176 - INFO_X - 8;

    public ProviderScreen(ProviderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable("gui.ae2apotheotic.title.infusion_provider"), 176, 196, ACCENT);
        this.inventoryLabelY = 104;
    }

    @Override
    protected void init() {
        super.init();
        addGuideButton("ae2apotheotic/infusion_pattern_provider.md");
        button(INFO_X, 76, 32, 12, Component.translatable("gui.ae2apotheotic.xp.deposit_level"),
            Component.translatable("gui.ae2apotheotic.xp.deposit_level.tooltip"), b -> sendButton(ProviderMenu.BUTTON_DEPOSIT_LEVEL));
        button(INFO_X + 34, 76, 32, 12, Component.translatable("gui.ae2apotheotic.xp.deposit_all"),
            Component.translatable("gui.ae2apotheotic.xp.deposit_all.tooltip"), b -> sendButton(ProviderMenu.BUTTON_DEPOSIT_ALL));
        button(INFO_X + 68, 76, 32, 12, Component.translatable("gui.ae2apotheotic.xp.withdraw"),
            Component.translatable("gui.ae2apotheotic.xp.withdraw.tooltip"), b -> sendButton(ProviderMenu.BUTTON_WITHDRAW_ALL));
    }

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, int x, int y) {
        drawInset(g, x + INFO_X - 3, y + 17, x + imageWidth - 6, y + 73);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        boolean online = menu.isOnline();
        fitText(g, Component.translatable(online ? "gui.ae2apotheotic.online" : "gui.ae2apotheotic.offline"), INFO_X, 21, INFO_WIDTH,
            online ? 0xFF3FD04A : 0xFFE05050);

        int flags = menu.machineFlags();
        Component machine;
        int color;
        if ((flags & InfusionProviderBlockEntity.FLAG_RAVEN) != 0) {
            machine = Component.translatable("gui.ae2apotheotic.table.raven");
            color = 0xFF3FD04A;
        } else if ((flags & InfusionProviderBlockEntity.FLAG_TABLE) != 0) {
            machine = Component.translatable("gui.ae2apotheotic.table.shelves");
            color = 0xFFE0B030;
        } else {
            machine = Component.translatable("gui.ae2apotheotic.table.missing");
            color = 0xFFE05050;
        }
        fitText(g, machine, INFO_X, 32, INFO_WIDTH, color);
        fitText(g, Component.translatable("gui.ae2apotheotic.patterns", menu.patternCount()), INFO_X, 43, INFO_WIDTH, TEXT_LIGHT);
        fitText(g, Component.translatable("gui.ae2apotheotic.xp.stored", menu.storedXp()), INFO_X, 54, INFO_WIDTH, 0xFFB8E060);
        fitText(g, Component.translatable("gui.ae2apotheotic.xp.nearby", menu.nearbyXp()), INFO_X, 63, INFO_WIDTH, 0xFFB8E060);

        ProviderProblem problem = menu.problem();
        if (problem != ProviderProblem.NONE) {
            fitText(g, problem.text(), 8, 92, imageWidth - 16, TEXT_RED);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (inside(INFO_X, 52, INFO_WIDTH, 20, mouseX, mouseY)) {
            tooltip(g, Component.translatable("gui.ae2apotheotic.xp.tooltip"), mouseX, mouseY);
        }
    }
}
