package com.ae2apotheotic.registry;

import appeng.api.AECapabilities;
import appeng.api.crafting.PatternDetailsHelper;
import com.ae2apotheotic.AE2Apotheotic;
import com.ae2apotheotic.block.EncoderBlock;
import com.ae2apotheotic.block.ProviderBlock;
import com.ae2apotheotic.blockentity.EncoderBlockEntity;
import com.ae2apotheotic.blockentity.GemProviderBlockEntity;
import com.ae2apotheotic.blockentity.InfusionProviderBlockEntity;
import com.ae2apotheotic.item.InfoBlockItem;
import com.ae2apotheotic.menu.EncoderMenu;
import com.ae2apotheotic.menu.GemProviderMenu;
import com.ae2apotheotic.menu.ProviderMenu;
import com.ae2apotheotic.pattern.GemCrushEncoding;
import com.ae2apotheotic.pattern.GemCrushPattern;
import com.ae2apotheotic.pattern.GemUpgradeEncoding;
import com.ae2apotheotic.pattern.GemUpgradePattern;
import com.ae2apotheotic.pattern.InfusionEncoding;
import com.ae2apotheotic.pattern.InfusionPattern;
import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AE2Apotheotic.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AE2Apotheotic.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AE2Apotheotic.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AE2Apotheotic.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AE2Apotheotic.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AE2Apotheotic.MODID);

    // ---- Data components carried by the encoded patterns ----
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GemUpgradeEncoding>> GEM_UPGRADE = COMPONENTS.registerComponentType(
        "gem_upgrade", b -> b.persistent(GemUpgradeEncoding.CODEC).networkSynchronized(GemUpgradeEncoding.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GemCrushEncoding>> GEM_CRUSH = COMPONENTS.registerComponentType(
        "gem_crush", b -> b.persistent(GemCrushEncoding.CODEC).networkSynchronized(GemCrushEncoding.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<InfusionEncoding>> INFUSION = COMPONENTS.registerComponentType(
        "infusion", b -> b.persistent(InfusionEncoding.CODEC).networkSynchronized(InfusionEncoding.STREAM_CODEC));

    // ---- Blocks ----
    public static final DeferredBlock<EncoderBlock> PATTERN_ENCODER = BLOCKS.registerBlock(
        "pattern_encoder", EncoderBlock::new, p -> p.mapColor(MapColor.COLOR_PURPLE).strength(2.2F, 11.0F).sound(SoundType.METAL));
    public static final DeferredBlock<ProviderBlock> GEM_PROVIDER = BLOCKS.registerBlock(
        "gem_pattern_provider", p -> new ProviderBlock(p, () -> ModRegistry.GEM_PROVIDER_BE.get()),
        p -> p.mapColor(MapColor.COLOR_CYAN).strength(2.2F, 11.0F).sound(SoundType.METAL));
    public static final DeferredBlock<ProviderBlock> INFUSION_PROVIDER = BLOCKS.registerBlock(
        "infusion_pattern_provider", p -> new ProviderBlock(p, () -> ModRegistry.INFUSION_PROVIDER_BE.get()),
        p -> p.mapColor(MapColor.COLOR_RED).strength(2.2F, 11.0F).sound(SoundType.METAL));

    // ---- Items ----
    public static final DeferredItem<BlockItem> PATTERN_ENCODER_ITEM = ITEMS.registerSimpleBlockItem(PATTERN_ENCODER);
    public static final DeferredItem<BlockItem> GEM_PROVIDER_ITEM = ITEMS.registerItem("gem_pattern_provider",
        p -> new InfoBlockItem(GEM_PROVIDER.get(), p.useBlockDescriptionPrefix(), "tooltip.ae2apotheotic.gem_provider.info"));
    public static final DeferredItem<BlockItem> INFUSION_PROVIDER_ITEM = ITEMS.registerSimpleBlockItem(INFUSION_PROVIDER);

    public static final DeferredItem<Item> GEM_UPGRADE_PATTERN = ITEMS.registerItem(
        "gem_upgrade_pattern", p -> PatternDetailsHelper.encodedPatternItemBuilder(GemUpgradePattern::new).build(p));
    public static final DeferredItem<Item> GEM_CRUSH_PATTERN = ITEMS.registerItem(
        "gem_crush_pattern", p -> PatternDetailsHelper.encodedPatternItemBuilder(GemCrushPattern::new).build(p));
    public static final DeferredItem<Item> INFUSION_PATTERN = ITEMS.registerItem(
        "infusion_pattern", p -> PatternDetailsHelper.encodedPatternItemBuilder(InfusionPattern::new).build(p));

    // ---- Block entities ----
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EncoderBlockEntity>> PATTERN_ENCODER_BE = BLOCK_ENTITIES.register(
        "pattern_encoder", () -> new BlockEntityType<>(EncoderBlockEntity::new, Set.of(PATTERN_ENCODER.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GemProviderBlockEntity>> GEM_PROVIDER_BE = BLOCK_ENTITIES.register(
        "gem_pattern_provider", () -> new BlockEntityType<>(GemProviderBlockEntity::new, Set.of(GEM_PROVIDER.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InfusionProviderBlockEntity>> INFUSION_PROVIDER_BE = BLOCK_ENTITIES.register(
        "infusion_pattern_provider", () -> new BlockEntityType<>(InfusionProviderBlockEntity::new, Set.of(INFUSION_PROVIDER.get())));

    // ---- Menus ----
    public static final DeferredHolder<MenuType<?>, MenuType<EncoderMenu>> ENCODER_MENU = MENUS.register(
        "pattern_encoder", () -> IMenuTypeExtension.create(EncoderMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<ProviderMenu>> PROVIDER_MENU = MENUS.register(
        "pattern_provider", () -> IMenuTypeExtension.create(ProviderMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<GemProviderMenu>> GEM_PROVIDER_MENU = MENUS.register(
        "gem_pattern_provider", () -> IMenuTypeExtension.create(GemProviderMenu::fromNetwork));

    // ---- Creative tab ----
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.ae2apotheotic"))
        .icon(() -> new ItemStack(PATTERN_ENCODER_ITEM.get()))
        .displayItems((params, output) -> {
            output.accept(PATTERN_ENCODER_ITEM.get());
            output.accept(GEM_PROVIDER_ITEM.get());
            output.accept(INFUSION_PROVIDER_ITEM.get());
        })
        .build());

    private ModRegistry() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(ModRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, GEM_PROVIDER_BE.get(), (be, ctx) -> be);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, INFUSION_PROVIDER_BE.get(), (be, ctx) -> be);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, INFUSION_PROVIDER_BE.get(), (be, side) -> be.getXpSink());
    }
}
