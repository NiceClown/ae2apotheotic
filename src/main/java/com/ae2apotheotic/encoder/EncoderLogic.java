package com.ae2apotheotic.encoder;

import appeng.api.stacks.AEItemKey;
import com.ae2apotheotic.blockentity.EncoderBlockEntity;
import com.ae2apotheotic.blockentity.InfusionProviderBlockEntity;
import com.ae2apotheotic.pattern.GemCrushEncoding;
import com.ae2apotheotic.pattern.GemKeys;
import com.ae2apotheotic.pattern.GemUpgradeEncoding;
import com.ae2apotheotic.pattern.InfusionEncoding;
import com.ae2apotheotic.pattern.MaterialChoice;
import com.ae2apotheotic.registry.ModRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.UnsocketedGem;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingMenu;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingRecipe;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.PurityUpgradeRecipe;
import dev.shadowsoffire.apothic_enchanting.Ench;
import dev.shadowsoffire.apothic_enchanting.table.EnchantingStatRegistry;
import dev.shadowsoffire.apothic_enchanting.table.infusion.InfusionRecipe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jspecify.annotations.Nullable;

/** Server-side brain of the Pattern Encoder: turns the sample + settings into a preview and an encoded pattern. */
public final class EncoderLogic {
    public static final int NO_VALUE = -10;

    private EncoderLogic() {}

    /** Everything the GUI shows. Filled on the server and synced to the client through the menu. */
    public static final class View {
        public EncoderStatus status = EncoderStatus.NO_SAMPLE;
        public int purity = -1;
        public int recipeIndex;
        public int recipeCount;
        public int leftIndex;
        public int leftCount;
        public int rightIndex;
        public int rightCount;
        public int reqEterna10 = NO_VALUE;
        public int reqQuanta10 = NO_VALUE;
        public int reqArcana10 = NO_VALUE;
        public int maxEterna10 = NO_VALUE;
        public int maxQuanta10 = NO_VALUE;
        public int maxArcana10 = NO_VALUE;
        public int xpCost;
        public int playerMaxEterna = 100;
        public ItemStack previewInput = ItemStack.EMPTY;
        public ItemStack previewLeft = ItemStack.EMPTY;
        public ItemStack previewRight = ItemStack.EMPTY;
        public ItemStack previewResult = ItemStack.EMPTY;
        public @Nullable ItemStack pattern;
    }

    public static View compute(ServerLevel level, Player player, EncoderBlockEntity encoder) {
        View view = new View();
        view.playerMaxEterna = (int) Math.floor(player.getAttributeValue(Ench.Attributes.MAX_ETERNA));
        switch (encoder.getMode()) {
            case EncoderBlockEntity.MODE_CRUSH -> crush(encoder, view);
            case EncoderBlockEntity.MODE_INFUSION -> infusion(level, encoder, view);
            default -> upgrade(level, encoder, view);
        }
        return view;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Purity selection

    private static Purity resolvePurity(EncoderBlockEntity encoder, @Nullable Purity fallback, Purity min, Purity max) {
        int ordinal = encoder.getPurity() >= 0 ? encoder.getPurity() : (fallback != null ? fallback.ordinal() : min.ordinal());
        return Purity.values()[Mth.clamp(ordinal, min.ordinal(), max.ordinal())];
    }

    /** Moves the purity selector, keeping it inside the range that makes sense for the current mode and sample. */
    public static void stepPurity(EncoderBlockEntity encoder, int delta) {
        UnsocketedGem gem = UnsocketedGem.of(encoder.getSample());
        boolean useGem = gem.isValid() && !(encoder.getMode() == EncoderBlockEntity.MODE_CRUSH
            && GemCrushEncoding.CrushMode.byId(encoder.getCrushMode()) != GemCrushEncoding.CrushMode.EXACT);
        Purity min = useGem ? gem.gem().get().getMinPurity() : Purity.CRACKED;
        Purity max = encoder.getMode() == EncoderBlockEntity.MODE_UPGRADE ? Purity.FLAWLESS : Purity.PERFECT;
        Purity current = resolvePurity(encoder, gem.isValid() ? gem.purity() : null, min, max);
        int next = Mth.clamp(current.ordinal() + delta, min.ordinal(), Math.max(min.ordinal(), max.ordinal()));
        encoder.setPurity(next);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Gem purity upgrade

    private static void upgrade(ServerLevel level, EncoderBlockEntity encoder, View view) {
        ItemStack sample = encoder.getSample();
        if (sample.isEmpty()) {
            view.status = EncoderStatus.NO_SAMPLE;
            return;
        }
        UnsocketedGem gemInst = UnsocketedGem.of(sample);
        if (!gemInst.isValid()) {
            view.status = EncoderStatus.NOT_A_GEM;
            return;
        }
        Gem gem = gemInst.gem().get();
        if (gem.getMinPurity() == Purity.PERFECT) {
            view.status = EncoderStatus.GEM_ALREADY_PERFECT;
            return;
        }
        Purity from = resolvePurity(encoder, gemInst.purity(), gem.getMinPurity(), Purity.FLAWLESS);
        Purity to = from.next();
        view.purity = from.ordinal();
        AEItemKey input = GemKeys.gem(gem, from);
        AEItemKey output = GemKeys.gem(gem, to);
        view.previewInput = input.toStack(GemUpgradeEncoding.GEMS_PER_UPGRADE);
        view.previewResult = output.toStack();

        List<PurityUpgradeRecipe> recipes = upgradeRecipes(level, from);
        if (recipes.isEmpty()) {
            view.status = EncoderStatus.NO_UPGRADE_RECIPE;
            return;
        }
        view.recipeCount = recipes.size();
        view.recipeIndex = Math.floorMod(encoder.getRecipeIndex(), recipes.size());
        PurityUpgradeRecipe recipe = recipes.get(view.recipeIndex);
        List<SizedIngredient> lefts = usable(recipe.left());
        List<SizedIngredient> rights = usable(recipe.right());
        view.leftCount = lefts.size();
        view.rightCount = rights.size();
        view.leftIndex = Math.floorMod(encoder.getLeftIndex(), lefts.size());
        view.rightIndex = Math.floorMod(encoder.getRightIndex(), rights.size());
        MaterialChoice left = MaterialChoice.of(lefts.get(view.leftIndex));
        MaterialChoice right = MaterialChoice.of(rights.get(view.rightIndex));
        view.previewLeft = left.displayStack();
        view.previewRight = right.displayStack();
        view.pattern = upgradePattern(input, output, left, right);
        view.status = EncoderStatus.OK;
    }

    private static ItemStack upgradePattern(AEItemKey input, AEItemKey output, MaterialChoice left, MaterialChoice right) {
        ItemStack pattern = new ItemStack(ModRegistry.GEM_UPGRADE_PATTERN.get());
        pattern.set(ModRegistry.GEM_UPGRADE.get(), new GemUpgradeEncoding(input, output, left, right));
        return pattern;
    }

    /** Purity upgrade recipes that start at the given purity, in a stable order. */
    private static List<PurityUpgradeRecipe> upgradeRecipes(ServerLevel level, Purity from) {
        List<RecipeHolder<GemCuttingRecipe>> holders = new ArrayList<>();
        for (RecipeHolder<GemCuttingRecipe> holder : GemCuttingMenu.getRecipes(level)) {
            if (holder.value() instanceof PurityUpgradeRecipe r && r.purity() == from && !usable(r.left()).isEmpty() && !usable(r.right()).isEmpty()) {
                holders.add(holder);
            }
        }
        holders.sort(Comparator.comparing(h -> h.id().identifier().toString()));
        return holders.stream().map(h -> (PurityUpgradeRecipe) h.value()).toList();
    }

    /**
     * The pattern currently shown plus one pattern for every following purity step up to Perfect. The following steps
     * use the first recipe and the first material option of each tier. With all of them in a provider the ME system
     * can craft a perfect gem straight from cracked ones.
     */
    public static List<ItemStack> upgradeChain(ServerLevel level, EncoderBlockEntity encoder, View current) {
        List<ItemStack> patterns = new ArrayList<>();
        UnsocketedGem gemInst = UnsocketedGem.of(encoder.getSample());
        if (current.pattern == null || !gemInst.isValid() || current.purity < 0) {
            return patterns;
        }
        patterns.add(current.pattern);
        Gem gem = gemInst.gem().get();
        for (Purity from = Purity.values()[current.purity].next(); from != Purity.PERFECT; from = from.next()) {
            List<PurityUpgradeRecipe> recipes = upgradeRecipes(level, from);
            if (recipes.isEmpty()) {
                break;
            }
            PurityUpgradeRecipe recipe = recipes.getFirst();
            MaterialChoice left = MaterialChoice.of(usable(recipe.left()).getFirst());
            MaterialChoice right = MaterialChoice.of(usable(recipe.right()).getFirst());
            patterns.add(upgradePattern(GemKeys.gem(gem, from), GemKeys.gem(gem, from.next()), left, right));
        }
        return patterns;
    }

    private static List<SizedIngredient> usable(List<SizedIngredient> options) {
        List<SizedIngredient> result = new ArrayList<>();
        for (SizedIngredient option : options) {
            if (option.count() > 0 && !MaterialChoice.of(option).isEmpty()) {
                result.add(option);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Gem crushing

    private static void crush(EncoderBlockEntity encoder, View view) {
        GemCrushEncoding.CrushMode mode = GemCrushEncoding.CrushMode.byId(encoder.getCrushMode());
        UnsocketedGem gemInst = UnsocketedGem.of(encoder.getSample());
        GemCrushEncoding encoding;
        Purity previewPurity = Purity.CRACKED;
        switch (mode) {
            case EXACT -> {
                if (encoder.getSample().isEmpty()) {
                    view.previewResult = GemKeys.dust().toStack(1);
                    view.status = EncoderStatus.NO_SAMPLE;
                    return;
                }
                if (!gemInst.isValid()) {
                    view.previewResult = GemKeys.dust().toStack(1);
                    view.status = EncoderStatus.NOT_A_GEM;
                    return;
                }
                Gem gem = gemInst.gem().get();
                Purity purity = resolvePurity(encoder, gemInst.purity(), gem.getMinPurity(), Purity.PERFECT);
                view.purity = purity.ordinal();
                previewPurity = purity;
                AEItemKey key = GemKeys.gem(gem, purity);
                view.previewInput = key.toStack();
                encoding = new GemCrushEncoding(mode, Optional.of(key), purity);
            }
            case SAME_PURITY -> {
                Purity purity = resolvePurity(encoder, gemInst.isValid() ? gemInst.purity() : null, Purity.CRACKED, Purity.PERFECT);
                view.purity = purity.ordinal();
                previewPurity = purity;
                List<AEItemKey> gems = GemKeys.allGems(purity);
                if (gems.isEmpty()) {
                    view.previewResult = GemKeys.dust().toStack(GemCrushEncoding.dustYield(purity));
                    view.status = EncoderStatus.NO_GEMS;
                    return;
                }
                view.previewInput = gems.getFirst().toStack();
                encoding = new GemCrushEncoding(mode, Optional.empty(), purity);
            }
            default -> {
                List<AEItemKey> gems = GemKeys.allGems(null);
                if (gems.isEmpty()) {
                    view.previewResult = GemKeys.dust().toStack(1);
                    view.status = EncoderStatus.NO_GEMS;
                    return;
                }
                view.previewInput = gems.getFirst().toStack();
                encoding = new GemCrushEncoding(mode, Optional.empty(), Purity.CRACKED);
            }
        }
        int dustCount = GemCrushEncoding.dustYield(previewPurity);
        view.previewResult = GemKeys.dust().toStack(dustCount);
        ItemStack pattern = new ItemStack(ModRegistry.GEM_CRUSH_PATTERN.get());
        pattern.set(ModRegistry.GEM_CRUSH.get(), encoding);
        view.pattern = pattern;
        view.status = EncoderStatus.OK;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Infusion (Enchanting Table of the Raven)

    private static int toTenths(float value) {
        return value < 0 ? NO_VALUE : Math.round(value * 10);
    }

    private static void infusion(ServerLevel level, EncoderBlockEntity encoder, View view) {
        ItemStack sample = encoder.getSample();
        if (sample.isEmpty()) {
            view.status = EncoderStatus.NO_SAMPLE;
            return;
        }
        ItemStack input = sample.copyWithCount(1);
        view.previewInput = input.copy();
        view.previewRight = new ItemStack(Items.LAPIS_LAZULI, InfusionEncoding.LAPIS_COST);

        List<RecipeHolder<InfusionRecipe>> recipes = new ArrayList<>();
        for (RecipeHolder<InfusionRecipe> holder : level.getServer().getRecipeManager().recipeMap().byType(Ench.RecipeTypes.INFUSION)) {
            if (holder.value().getInput().test(input)) {
                recipes.add(holder);
            }
        }
        recipes.sort(Comparator.comparing(h -> h.id().identifier().toString()));
        if (recipes.isEmpty()) {
            view.status = EncoderStatus.NO_INFUSION_RECIPE;
            return;
        }
        view.recipeCount = recipes.size();
        view.recipeIndex = Math.floorMod(encoder.getRecipeIndex(), recipes.size());
        RecipeHolder<InfusionRecipe> holder = recipes.get(view.recipeIndex);
        InfusionRecipe recipe = holder.value();
        EnchantingStatRegistry.Stats req = recipe.getRequirements();
        EnchantingStatRegistry.Stats max = recipe.getMaxRequirements();
        view.reqEterna10 = toTenths(req.eterna());
        view.reqQuanta10 = toTenths(req.quanta());
        view.reqArcana10 = toTenths(req.arcana());
        view.maxEterna10 = toTenths(max.eterna());
        view.maxQuanta10 = toTenths(max.quanta());
        view.maxArcana10 = toTenths(max.arcana());

        if (encoder.statsNeedReset()) {
            encoder.clearStatsReset();
            encoder.setStats(autoStat(req.eterna()), autoStat(req.quanta()), autoStat(req.arcana()));
        }
        int eterna = encoder.getEterna();
        int quanta = encoder.getQuanta();
        int arcana = encoder.getArcana();
        view.xpCost = InfusionProviderBlockEntity.requiredXp(recipe);

        ItemStack expected = recipe.assemble(input.copy(), eterna, quanta, arcana);
        InfusionRecipe matched = InfusionRecipe.findMatch(level, input, eterna, quanta, arcana);
        ItemStack produced = matched == null ? ItemStack.EMPTY : matched.assemble(input.copy(), eterna, quanta, arcana);
        view.previewResult = matched == null ? expected : produced;

        if (matched == null) {
            view.status = EncoderStatus.STATS_OUT_OF_RANGE;
            return;
        }
        if (!ItemStack.isSameItemSameComponents(produced, expected) || produced.getCount() != expected.getCount()) {
            view.status = EncoderStatus.STATS_OTHER_RECIPE;
            return;
        }
        if (eterna > view.playerMaxEterna) {
            view.status = EncoderStatus.ETERNA_ABOVE_MAX;
            return;
        }
        InfusionEncoding encoding = new InfusionEncoding(AEItemKey.of(input), AEItemKey.of(produced), produced.getCount(),
            holder.id().identifier(), eterna, quanta, arcana, view.xpCost);
        ItemStack pattern = new ItemStack(ModRegistry.INFUSION_PATTERN.get());
        pattern.set(ModRegistry.INFUSION.get(), encoding);
        view.pattern = pattern;
        view.status = EncoderStatus.OK;
    }

    /** Smallest whole stat value that satisfies the requirement (the Raven table only takes whole numbers). */
    private static int autoStat(float requirement) {
        return Mth.clamp((int) Math.ceil(Math.max(0, requirement)), 0, 100);
    }

    /** Resets the stats of the current infusion recipe to its minimum requirements. */
    public static void requestAutoStats(EncoderBlockEntity encoder) {
        encoder.requestStatsReset();
    }
}
