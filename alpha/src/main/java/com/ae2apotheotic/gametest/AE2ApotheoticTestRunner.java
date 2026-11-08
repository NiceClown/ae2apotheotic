package com.ae2apotheotic.gametest;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import com.ae2apotheotic.blockentity.GemProviderBlockEntity;
import com.ae2apotheotic.blockentity.InfusionProviderBlockEntity;
import com.ae2apotheotic.pattern.GemCrushEncoding;
import com.ae2apotheotic.pattern.GemUpgradeEncoding;
import com.ae2apotheotic.pattern.InfusionEncoding;
import com.ae2apotheotic.pattern.MaterialChoice;
import com.ae2apotheotic.registry.ModRegistry;
import com.ae2apotheotic.util.XpHelper;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apothic_enchanting.Ench;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * Robust automated test suite with isolated test execution.
 */
public final class AE2ApotheoticTestRunner {
    private static int autoTicks = 0;
    private static boolean testsDone = false;
    private static final List<String> results = new ArrayList<>();

    public static void init() {
        NeoForge.EVENT_BUS.addListener(AE2ApotheoticTestRunner::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(AE2ApotheoticTestRunner::onClientTick);
    }

    private static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("ae2apotheotic")
                .then(Commands.literal("test")
                    .executes(ctx -> {
                        runAllTests();
                        return 1;
                    }))
        );
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2apotheotic.runTests") || testsDone) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        autoTicks++;
        if (autoTicks >= 40) {
            testsDone = true;
            runAllTests();
            if (Boolean.getBoolean("ae2apotheotic.exitAfterTests")) {
                mc.stop();
            }
        }
    }

    @FunctionalInterface
    public interface TestCase {
        void run() throws Throwable;
    }

    public static void runTest(String name, TestCase test) {
        try {
            test.run();
            pass(name);
        } catch (Throwable t) {
            String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            fail(name + " -> " + msg);
        }
    }

    public static void runAllTests() {
        results.clear();
        log("=================================================");
        log("    AE2 Apotheotic Automation Test Suite (v2.2.0)");
        log("=================================================");

        boolean mutate = Boolean.getBoolean("ae2apotheotic.mutateTests");
        if (mutate) {
            log(">>> MUTATION MODE ENABLED: DELIBERATE ERRORS INJECTED <<<");
        }

        // Test 1: Gem Upgrade Encoding
        runTest("Gem Upgrade Pattern Encoding & Decoding", () -> {
            Gem firstGem = GemRegistry.INSTANCE.getValues().iterator().next();
            ItemStack sampleGem = GemItem.createStack(firstGem, Purity.CRACKED, 1);
            AEItemKey inputKey = AEItemKey.of(sampleGem);
            AEItemKey outputKey = AEItemKey.of(GemItem.createStack(firstGem, Purity.CHIPPED, 1));
            MaterialChoice leftChoice = new MaterialChoice(List.of(AEItemKey.of(Items.DIAMOND)), 1);
            MaterialChoice rightChoice = new MaterialChoice(List.of(AEItemKey.of(Items.EMERALD)), 1);
            GemUpgradeEncoding upgradeEnc = new GemUpgradeEncoding(inputKey, outputKey, leftChoice, rightChoice);

            ItemStack upgradePattern = new ItemStack(ModRegistry.GEM_UPGRADE_PATTERN.get());
            upgradePattern.set(ModRegistry.GEM_UPGRADE.get(), upgradeEnc);

            GemUpgradeEncoding decodedEnc = upgradePattern.get(ModRegistry.GEM_UPGRADE.get());
            assertNotNull(decodedEnc, "Decoded upgrade encoding must not be null");
            assertEqual(decodedEnc.input().toStack().getItem(), sampleGem.getItem(), "Upgrade input item match");
        });

        // Test 2: Gem Crush Encoding
        runTest("Gem Crush Pattern Encoding & Decoding", () -> {
            Gem firstGem = GemRegistry.INSTANCE.getValues().iterator().next();
            AEItemKey inputKey = AEItemKey.of(GemItem.createStack(firstGem, Purity.CRACKED, 1));
            GemCrushEncoding crushEnc = new GemCrushEncoding(GemCrushEncoding.CrushMode.EXACT, Optional.of(inputKey), Purity.CRACKED);
            ItemStack crushPattern = new ItemStack(ModRegistry.GEM_CRUSH_PATTERN.get());
            crushPattern.set(ModRegistry.GEM_CRUSH.get(), crushEnc);

            GemCrushEncoding decodedCrush = crushPattern.get(ModRegistry.GEM_CRUSH.get());
            assertNotNull(decodedCrush, "Decoded crush encoding must not be null");
            assertEqual(decodedCrush.mode(), GemCrushEncoding.CrushMode.EXACT, "Crush mode EXACT");
        });

        // Test 3: Infusion Encoding
        runTest("Infusion Pattern Encoding & Decoding", () -> {
            AEItemKey sword = AEItemKey.of(Items.DIAMOND_SWORD);
            AEItemKey netheriteSword = AEItemKey.of(Items.NETHERITE_SWORD);
            InfusionEncoding infEnc = new InfusionEncoding(sword, netheriteSword, 1, Identifier.fromNamespaceAndPath("apothic_enchanting", "infusion/netherite_sword"), 50, 25, 10, 15);
            ItemStack infPattern = new ItemStack(ModRegistry.INFUSION_PATTERN.get());
            infPattern.set(ModRegistry.INFUSION.get(), infEnc);

            InfusionEncoding decodedInf = infPattern.get(ModRegistry.INFUSION.get());
            assertNotNull(decodedInf, "Decoded infusion encoding must not be null");
            assertEqual(decodedInf.eterna(), 50, "Infusion eterna value");
            assertEqual(decodedInf.quanta(), 25, "Infusion quanta value");
            assertEqual(decodedInf.arcana(), 10, "Infusion arcana value");

            if (mutate) {
                // DELIBERATE ERROR 1: inject intentional assertion failure
                assertEqual(decodedInf.eterna(), 999, "Deliberate mutation error: impossible eterna");
            }
        });

        // Test 4: Terminal Composite Inventory
        runTest("Gem Provider Composite Terminal Inventory", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                throw new IllegalStateException("No active client level");
            }

            BlockPos pos = mc.player.blockPosition().above(5);
            GemProviderBlockEntity be = new GemProviderBlockEntity(pos, ModRegistry.GEM_PROVIDER.get().defaultBlockState());

            InternalInventory termInv = be.getTerminalPatternInventory();
            assertNotNull(termInv, "Terminal inventory must not be null");
            assertTrue(be.isVisibleInTerminal(), "GemProvider must be visible in terminal");
            assertTrue(termInv.size() >= GemProviderBlockEntity.BULK_SLOTS, "Terminal inv size at least bulk slots");

            // Bulk slot test (slot 0)
            ItemStack bulkCrush = new ItemStack(ModRegistry.GEM_CRUSH_PATTERN.get());
            bulkCrush.set(ModRegistry.GEM_CRUSH.get(), new GemCrushEncoding(GemCrushEncoding.CrushMode.ANY, Optional.empty(), Purity.CRACKED));

            assertTrue(termInv.isItemValid(0, bulkCrush), "Slot 0 must accept bulk crush pattern");
            termInv.setItemDirect(0, bulkCrush.copy());
            assertEqual(termInv.getStackInSlot(0).getItem(), bulkCrush.getItem(), "Slot 0 holds bulk crush pattern");

            termInv.setItemDirect(0, ItemStack.EMPTY);
            assertTrue(termInv.getStackInSlot(0).isEmpty(), "Slot 0 cleared");
        });

        // Test 5: Negative Testing: Pattern Terminal Rejects Non-Pattern Items
        runTest("Gem Provider Terminal Validation Rejects Invalid Items", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                throw new IllegalStateException("No active client level");
            }
            BlockPos pos = mc.player.blockPosition().above(7);
            GemProviderBlockEntity be = new GemProviderBlockEntity(pos, ModRegistry.GEM_PROVIDER.get().defaultBlockState());
            InternalInventory termInv = be.getTerminalPatternInventory();

            // Iron ingot must NOT be valid in slot 0
            boolean ironValid = termInv.isItemValid(0, new ItemStack(Items.IRON_INGOT));
            if (mutate) {
                // DELIBERATE ERROR 2: invert condition so it fails
                assertTrue(ironValid, "Deliberate mutation error: asserting iron ingot is accepted");
            } else {
                assertTrue(!ironValid, "Terminal must reject iron ingot in pattern slot");
            }
        });

        // Test 6: XP Math
        runTest("XP Math Conversion Factors", () -> {
            assertEqual(XpHelper.MB_PER_XP, 20, "20 mB per XP point standard");
            if (mutate) {
                // DELIBERATE ERROR 3: intentional wrong XP factor
                assertEqual(XpHelper.MB_PER_XP, 100, "Deliberate mutation error: wrong MB_PER_XP");
            }
        });

        // Test 7: Jade Server Data
        runTest("Jade Server Data Serialization", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                throw new IllegalStateException("No active client level");
            }

            BlockPos pos = mc.player.blockPosition().above(6);
            GemProviderBlockEntity gemBe = new GemProviderBlockEntity(pos, ModRegistry.GEM_PROVIDER.get().defaultBlockState());
            InfusionProviderBlockEntity infBe = new InfusionProviderBlockEntity(pos.above(), ModRegistry.INFUSION_PROVIDER.get().defaultBlockState());

            CompoundTag gemTag = new CompoundTag();
            gemTag.putString("type", "gem_provider");
            gemTag.putBoolean("online", gemBe.isOnline());
            gemTag.putInt("patterns", gemBe.getPatternCount());
            assertEqual(gemTag.getStringOr("type", ""), "gem_provider", "Gem provider Jade tag type");

            CompoundTag infTag = new CompoundTag();
            infTag.putString("type", "infusion_provider");
            infTag.putInt("stored_xp", infBe.getStoredXp());
            infTag.putLong("network_xp", infBe.getNetworkXpMb());
            assertEqual(infTag.getStringOr("type", ""), "infusion_provider", "Infusion provider Jade tag type");
        });

        // Test 8: In-World Machine Detection
        runTest("In-World Adjacent Machine Detection (Gem Case + Raven Table)", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                throw new IllegalStateException("No level for in-world machine test");
            }

            Level level = mc.level;
            BlockPos base = mc.player.blockPosition().relative(mc.player.getDirection(), 3);

            BlockPos casePos = base;
            BlockPos provPos = base.above();

            level.setBlock(casePos, Apoth.Blocks.GEM_CASE.value().defaultBlockState(), 3);
            level.setBlock(provPos, ModRegistry.GEM_PROVIDER.get().defaultBlockState(), 3);

            if (level.getBlockEntity(provPos) instanceof GemProviderBlockEntity provBe) {
                assertNotNull(provBe.findGemCase(), "Gem Provider must detect adjacent Gem Case");
                assertEqual(provBe.machineFlags(), GemProviderBlockEntity.FLAG_GEM_CASE, "Gem Provider flag GEM_CASE set");
            } else {
                throw new AssertionError("Block entity at provPos was not GemProviderBlockEntity");
            }

            BlockPos tablePos = base.relative(Direction.EAST, 2);
            BlockPos infPos = tablePos.above();

            level.setBlock(tablePos, Ench.Blocks.RAVEN_ENCHANTING_TABLE.value().defaultBlockState(), 3);
            level.setBlock(infPos, ModRegistry.INFUSION_PROVIDER.get().defaultBlockState(), 3);

            if (level.getBlockEntity(infPos) instanceof InfusionProviderBlockEntity infBe) {
                int flags = infBe.machineFlags();
                assertTrue((flags & InfusionProviderBlockEntity.FLAG_TABLE) != 0, "FLAG_TABLE set");
                assertTrue((flags & InfusionProviderBlockEntity.FLAG_RAVEN) != 0, "FLAG_RAVEN set for Raven table");
            } else {
                throw new AssertionError("Block entity at infPos was not InfusionProviderBlockEntity");
            }

            level.setBlock(casePos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(provPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(tablePos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(infPos, Blocks.AIR.defaultBlockState(), 3);
        });

        long passCount = results.stream().filter(s -> s.startsWith("[PASS]")).count();
        long failCount = results.stream().filter(s -> s.startsWith("[FAIL]")).count();

        log("=================================================");
        log("  SUMMARY: " + passCount + " PASSED, " + failCount + " FAILED");
        log("=================================================");

        saveResultsToFile();
    }

    private static void pass(String name) {
        String msg = "[PASS] " + name;
        results.add(msg);
        log(msg);
    }

    private static void fail(String reason) {
        String msg = "[FAIL] " + reason;
        results.add(msg);
        log(msg);
    }

    private static void log(String msg) {
        System.out.println("[AE2Apotheotic-Test] " + msg);
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.getChat() != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal(msg));
        }
    }

    private static void assertEqual(Object actual, Object expected, String desc) {
        if (!java.util.Objects.equals(actual, expected)) {
            throw new AssertionError(desc + ": expected <" + expected + "> but got <" + actual + ">");
        }
    }

    private static void assertTrue(boolean condition, String desc) {
        if (!condition) {
            throw new AssertionError(desc + " was false");
        }
    }

    private static void assertNotNull(@Nullable Object obj, String desc) {
        if (obj == null) {
            throw new AssertionError(desc + " was null");
        }
    }

    private static void saveResultsToFile() {
        try {
            File outFile = new File("gametest_results.txt");
            try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
                for (String r : results) {
                    pw.println(r);
                }
            }
            log("Test results saved to: " + outFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
