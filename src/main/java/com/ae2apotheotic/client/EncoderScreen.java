package com.ae2apotheotic.client;

import com.ae2apotheotic.blockentity.EncoderBlockEntity;
import com.ae2apotheotic.encoder.EncoderLogic;
import com.ae2apotheotic.encoder.EncoderStatus;
import com.ae2apotheotic.menu.EncoderMenu;
import com.ae2apotheotic.pattern.GemCrushEncoding;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;

public class EncoderScreen extends FlatScreen<EncoderMenu> {
    private static final int ACCENT = 0xFF8A4FD0;
    private static final int[] STAT_ROWS = { 56, 68, 80 };
    private static final String[] STAT_KEYS = { "eterna", "quanta", "arcana" };
    private static final int[] STAT_COLORS = { 0xFF2E8B2E, 0xFFB03030, 0xFF7A3FB0 };

    private final Button[] modeButtons = new Button[3];
    private Button purityPrev;
    private Button purityNext;
    private Button recipePrevTop;
    private Button recipeNextTop;
    private Button recipePrevSmall;
    private Button recipeNextSmall;
    private Button leftPrev;
    private Button leftNext;
    private Button rightPrev;
    private Button rightNext;
    private Button crushMode;
    private Button autoStats;
    private Button encode;
    private final List<Button> statButtons = new ArrayList<>();

    public EncoderScreen(EncoderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.translatable("gui.ae2apotheotic.title.encoder"), 210, 236, ACCENT);
        this.inventoryLabelX = 24;
    }

    @Override
    protected void init() {
        super.init();
        addGuideButton("ae2apotheotic/pattern_encoder.md");
        String[] modes = { "upgrade", "crush", "infusion" };
        for (int i = 0; i < 3; i++) {
            int mode = i;
            modeButtons[i] = button(8 + i * 66, 17, 62, 14, Component.translatable("gui.ae2apotheotic.mode." + modes[i]),
                Component.translatable("gui.ae2apotheotic.mode." + modes[i] + ".tooltip"), b -> sendButton(EncoderMenu.BTN_MODE + mode));
        }
        purityPrev = button(30, 42, 12, 12, Component.literal("<"), null, b -> sendButton(EncoderMenu.BTN_PURITY_PREV));
        purityNext = button(186, 42, 12, 12, Component.literal(">"), null, b -> sendButton(EncoderMenu.BTN_PURITY_NEXT));
        recipePrevTop = button(30, 42, 12, 12, Component.literal("<"), Component.translatable("gui.ae2apotheotic.recipe.prev"),
            b -> sendButton(EncoderMenu.BTN_RECIPE_PREV));
        recipeNextTop = button(186, 42, 12, 12, Component.literal(">"), Component.translatable("gui.ae2apotheotic.recipe.next"),
            b -> sendButton(EncoderMenu.BTN_RECIPE_NEXT));
        recipePrevSmall = button(144, 86, 11, 12, Component.literal("<"), Component.translatable("gui.ae2apotheotic.recipe.prev"),
            b -> sendButton(EncoderMenu.BTN_RECIPE_PREV));
        recipeNextSmall = button(178, 86, 11, 12, Component.literal(">"), Component.translatable("gui.ae2apotheotic.recipe.next"),
            b -> sendButton(EncoderMenu.BTN_RECIPE_NEXT));
        leftPrev = button(68, 86, 11, 12, Component.literal("<"), Component.translatable("gui.ae2apotheotic.material.left"),
            b -> sendButton(EncoderMenu.BTN_LEFT_PREV));
        leftNext = button(83, 86, 11, 12, Component.literal(">"), Component.translatable("gui.ae2apotheotic.material.left"),
            b -> sendButton(EncoderMenu.BTN_LEFT_NEXT));
        rightPrev = button(104, 86, 11, 12, Component.literal("<"), Component.translatable("gui.ae2apotheotic.material.right"),
            b -> sendButton(EncoderMenu.BTN_RIGHT_PREV));
        rightNext = button(119, 86, 11, 12, Component.literal(">"), Component.translatable("gui.ae2apotheotic.material.right"),
            b -> sendButton(EncoderMenu.BTN_RIGHT_NEXT));
        crushMode = button(24, 86, 162, 14, Component.empty(), Component.translatable("gui.ae2apotheotic.crush_mode.tooltip"),
            b -> sendButton(EncoderMenu.BTN_CRUSH_MODE));
        statButtons.clear();
        for (int stat = 0; stat < 3; stat++) {
            int s = stat;
            int y = STAT_ROWS[stat] - 2;
            statButtons.add(button(56, y, 11, 12, Component.literal("-"), Component.translatable("gui.ae2apotheotic.stat.tooltip"),
                b -> sendButton(EncoderMenu.BTN_STAT + s * 4 + (shiftDown() ? 2 : 0))));
            statButtons.add(button(69, y, 11, 12, Component.literal("+"), Component.translatable("gui.ae2apotheotic.stat.tooltip"),
                b -> sendButton(EncoderMenu.BTN_STAT + s * 4 + (shiftDown() ? 3 : 1))));
        }
        autoStats = button(94, 90, 36, 12, Component.translatable("gui.ae2apotheotic.auto"),
            Component.translatable("gui.ae2apotheotic.auto.tooltip"), b -> sendButton(EncoderMenu.BTN_AUTO_STATS));
        encode = button(64, 120, 82, 20, Component.translatable("gui.ae2apotheotic.encode"),
            Component.translatable("gui.ae2apotheotic.encode.tooltip"),
            b -> sendButton(shiftDown() && menu.mode() == EncoderBlockEntity.MODE_UPGRADE ? EncoderMenu.BTN_ENCODE_CHAIN : EncoderMenu.BTN_ENCODE));
        updateWidgets();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateWidgets();
    }

    private void updateWidgets() {
        if (encode == null) {
            return;
        }
        int mode = menu.mode();
        boolean upgrade = mode == EncoderBlockEntity.MODE_UPGRADE;
        boolean crush = mode == EncoderBlockEntity.MODE_CRUSH;
        boolean infusion = mode == EncoderBlockEntity.MODE_INFUSION;
        for (int i = 0; i < 3; i++) {
            modeButtons[i].active = i != mode;
        }
        boolean purityVisible = (upgrade || crush) && menu.get(EncoderMenu.D_PURITY) >= 0;
        purityPrev.visible = purityVisible;
        purityNext.visible = purityVisible;
        boolean manyRecipes = menu.get(EncoderMenu.D_RECIPE_COUNT) > 1;
        recipePrevTop.visible = infusion && manyRecipes;
        recipeNextTop.visible = infusion && manyRecipes;
        recipePrevSmall.visible = upgrade && manyRecipes;
        recipeNextSmall.visible = upgrade && manyRecipes;
        boolean manyLeft = menu.get(EncoderMenu.D_LEFT_COUNT) > 1;
        boolean manyRight = menu.get(EncoderMenu.D_RIGHT_COUNT) > 1;
        leftPrev.visible = upgrade && manyLeft;
        leftNext.visible = upgrade && manyLeft;
        rightPrev.visible = upgrade && manyRight;
        rightNext.visible = upgrade && manyRight;
        crushMode.visible = crush;
        crushMode.setMessage(crushModeText());
        boolean hasRecipe = infusion && menu.get(EncoderMenu.D_RECIPE_COUNT) > 0;
        for (Button b : statButtons) {
            b.visible = hasRecipe;
        }
        autoStats.visible = hasRecipe;
        encode.active = menu.canEncode();
    }

    private Component crushModeText() {
        GemCrushEncoding.CrushMode mode = GemCrushEncoding.CrushMode.byId(menu.get(EncoderMenu.D_CRUSH_MODE));
        Component name = switch (mode) {
            case EXACT -> Component.translatable("gui.ae2apotheotic.crush_mode.exact");
            case SAME_PURITY -> Component.translatable("gui.ae2apotheotic.crush_mode.same_purity", purityName());
            case ANY -> Component.translatable("gui.ae2apotheotic.crush_mode.any");
        };
        return Component.translatable("gui.ae2apotheotic.crush_mode.button", name);
    }

    private Component purityName() {
        int ordinal = menu.get(EncoderMenu.D_PURITY);
        if (ordinal < 0 || ordinal >= Purity.values().length) {
            return Component.literal("?");
        }
        return Purity.values()[ordinal].toComponent();
    }

    @Override
    protected void extractPanel(GuiGraphicsExtractor g, int x, int y) {
        // separator above the encode row
        g.fill(x + 16, y + 116, x + imageWidth - 16, y + 117, 0xFF8B8B8B);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        int mode = menu.mode();
        switch (mode) {
            case EncoderBlockEntity.MODE_UPGRADE -> labelsUpgrade(g);
            case EncoderBlockEntity.MODE_CRUSH -> labelsCrush(g);
            default -> labelsInfusion(g);
        }
        EncoderStatus status = menu.status();
        Component statusText;
        int color;
        if (!status.isOk()) {
            statusText = status.text();
            color = TEXT_RED;
        } else if (!menu.canEncode()) {
            statusText = Component.translatable("gui.ae2apotheotic.status.need_blank");
            color = TEXT_YELLOW;
        } else {
            statusText = status.text();
            color = TEXT_GREEN;
        }
        centeredText(g, statusText, imageWidth / 2, 105, color);
    }

    private void labelsUpgrade(GuiGraphicsExtractor g) {
        int ordinal = menu.get(EncoderMenu.D_PURITY);
        if (ordinal >= 0 && ordinal < Purity.values().length - 1) {
            Purity from = Purity.values()[ordinal];
            MutableComponent text = from.toComponent()
                .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                .append(from.next().toComponent());
            centeredText(g, text, 114, 45, TEXT);
        }
        g.text(font, "+", 60, 71, TEXT, false);
        g.text(font, "+", 96, 71, TEXT, false);
        g.text(font, "=", 140, 71, TEXT, false);
        if (menu.get(EncoderMenu.D_RECIPE_COUNT) > 1) {
            centeredText(g, Component.literal((menu.get(EncoderMenu.D_RECIPE_INDEX) + 1) + "/" + menu.get(EncoderMenu.D_RECIPE_COUNT)), 166, 88, TEXT_GRAY);
        }
    }

    private void labelsCrush(GuiGraphicsExtractor g) {
        int ordinal = menu.get(EncoderMenu.D_PURITY);
        int dustYield;
        if (ordinal >= 0 && ordinal < Purity.values().length) {
            centeredText(g, purityName(), 114, 45, TEXT);
            dustYield = GemCrushEncoding.dustYield(Purity.values()[ordinal]);
        } else {
            centeredText(g, Component.translatable("gui.ae2apotheotic.crush_mode.any"), 114, 45, TEXT_GRAY);
            dustYield = GemCrushEncoding.dustYield(Purity.CRACKED);
        }
        Component ratio = dustYield == 1
            ? Component.translatable("gui.ae2apotheotic.crush.ratio.one")
            : Component.translatable("gui.ae2apotheotic.crush.ratio.many", dustYield);
        centeredText(g, ratio, 105, 56, TEXT_GRAY);
        centeredText(g, Component.translatable("gui.ae2apotheotic.crush.arrow"), 105, 71, TEXT);
    }

    private void labelsInfusion(GuiGraphicsExtractor g) {
        int count = menu.get(EncoderMenu.D_RECIPE_COUNT);
        if (count > 0) {
            centeredText(g, Component.translatable("gui.ae2apotheotic.recipe.counter", menu.get(EncoderMenu.D_RECIPE_INDEX) + 1, count), 114, 45, TEXT);
        }
        if (count <= 0) {
            return;
        }
        int[] values = { menu.get(EncoderMenu.D_ETERNA), menu.get(EncoderMenu.D_QUANTA), menu.get(EncoderMenu.D_ARCANA) };
        int[] mins = { menu.get(EncoderMenu.D_REQ_ETERNA), menu.get(EncoderMenu.D_REQ_QUANTA), menu.get(EncoderMenu.D_REQ_ARCANA) };
        int[] maxs = { menu.get(EncoderMenu.D_MAX_ETERNA), menu.get(EncoderMenu.D_MAX_QUANTA), menu.get(EncoderMenu.D_MAX_ARCANA) };
        for (int stat = 0; stat < 3; stat++) {
            int y = STAT_ROWS[stat];
            g.text(font, Component.translatable("gui.ae2apotheotic." + STAT_KEYS[stat]), 16, y, STAT_COLORS[stat], false);
            String value = String.valueOf(values[stat]);
            g.text(font, value, 84, y, TEXT, false);
            boolean inRange = values[stat] * 10 >= mins[stat] && (maxs[stat] == EncoderLogic.NO_VALUE || values[stat] * 10 <= maxs[stat]);
            g.text(font, rangeText(mins[stat], maxs[stat]), 104, y, inRange ? TEXT_GRAY : TEXT_RED, false);
        }
        int xp = menu.xpCost();
        g.text(font, Component.translatable("gui.ae2apotheotic.xp_cost", xp), 16, 92, TEXT_YELLOW, false);
        int maxEterna = menu.get(EncoderMenu.D_PLAYER_MAX_ETERNA);
        if (values[0] > maxEterna) {
            Component maxText = Component.translatable("gui.ae2apotheotic.max_eterna", maxEterna);
            g.text(font, maxText, 194 - font.width(maxText), 92, TEXT_RED, false);
        }
    }

    private static String fmt(int tenths) {
        return tenths % 10 == 0 ? String.valueOf(tenths / 10) : String.format("%.1f", tenths / 10.0F);
    }

    private static String rangeText(int min10, int max10) {
        String min = min10 == EncoderLogic.NO_VALUE ? "0" : fmt(min10);
        return max10 == EncoderLogic.NO_VALUE ? "≥" + min : min + "–" + fmt(max10);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (isHovering(8, 40, 16, 16, mouseX, mouseY) && menu.getSlot(EncoderMenu.SLOT_SAMPLE).getItem().isEmpty()) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.ae2apotheotic.sample.tooltip"), mouseX, mouseY);
        } else if (isHovering(24, 122, 16, 16, mouseX, mouseY) && menu.getSlot(EncoderMenu.SLOT_BLANK).getItem().isEmpty()) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.ae2apotheotic.blank.tooltip"), mouseX, mouseY);
        }
    }
}
