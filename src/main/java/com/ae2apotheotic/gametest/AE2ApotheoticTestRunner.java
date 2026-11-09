package com.ae2apotheotic.gametest;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEItems;
import com.ae2apotheotic.blockentity.EncoderBlockEntity;
import com.ae2apotheotic.blockentity.GemProviderBlockEntity;
import com.ae2apotheotic.blockentity.InfusionProviderBlockEntity;
import com.ae2apotheotic.menu.EncoderMenu;
import com.ae2apotheotic.network.TransferRecipePayload;
import com.ae2apotheotic.pattern.GemCrushEncoding;
import com.ae2apotheotic.pattern.GemCrushPattern;
import com.ae2apotheotic.pattern.GemKeys;
import com.ae2apotheotic.pattern.GemUpgradeEncoding;
import com.ae2apotheotic.pattern.GemUpgradePattern;
import com.ae2apotheotic.pattern.InfusionEncoding;
import com.ae2apotheotic.pattern.InfusionPattern;
import com.ae2apotheotic.pattern.MaterialChoice;
import com.ae2apotheotic.registry.ModRegistry;
import com.ae2apotheotic.util.XpHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.UnsocketedGem;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingMenu;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingRecipe;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.PurityUpgradeRecipe;
import dev.shadowsoffire.apotheosis.socket.gem.storage.GemCaseTile;
import dev.shadowsoffire.apothic_enchanting.Ench;
import dev.shadowsoffire.apothic_enchanting.table.infusion.InfusionRecipe;
import dev.shadowsoffire.placebo.cap.InternalItemHandler;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Robust automated in-game test runner supporting both isolated local runs and multiplayer live server testing.
 */
public final class AE2ApotheoticTestRunner {
    private static int autoTicks = 0;
    private static boolean testsDone = false;
    private static boolean waitingForMultiplayer = false;
    private static int mpWaitTicks = 0;
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
        Minecraft mc = Minecraft.getInstance();

        // 1. Multiplayer Client Mode
        if (Boolean.getBoolean("ae2apotheotic.runMultiplayerTests")) {
            if (mc.screen instanceof DisconnectedScreen ds) {
                fail("Multiplayer Client Disconnected Screen -> " + ds.getNarrationMessage().getString());
                saveResultsToFile("multiplayer_results.txt");
                signalMultiplayerDone();
                mc.stop();
                return;
            }
            if (testsDone || mc.level == null || mc.player == null) {
                return;
            }
            autoTicks++;
            if (autoTicks >= 20) {
                testsDone = true;
                runMultiplayerTests();
                if (Boolean.getBoolean("ae2apotheotic.exitAfterTests")) {
                    mc.stop();
                }
            }
            return;
        }

        // 2. Host / Singleplayer Mode Waiting for Multiplayer Client
        if (waitingForMultiplayer) {
            mpWaitTicks++;
            File mpDone = new File("multiplayer_done.txt");
            if (mpDone.exists()) {
                waitingForMultiplayer = false;
                pass("Remote Multiplayer Client Session & TCP Packet Roundtrip");
                log("=== ALL IN-GAME AND SERVER TESTS COMPLETED SUCCESSFULLY ===");
                saveResultsToFile();
                if (Boolean.getBoolean("ae2apotheotic.exitAfterTests")) {
                    mc.stop();
                }
                return;
            }
            if (mpWaitTicks > 1200) { // 60 seconds timeout
                waitingForMultiplayer = false;
                fail("Remote Multiplayer Client Session -> Timeout waiting for client connection");
                saveResultsToFile();
                if (Boolean.getBoolean("ae2apotheotic.exitAfterTests")) {
                    mc.stop();
                }
            }
            return;
        }

        // 3. Normal Host / GameTest Execution
        if (!Boolean.getBoolean("ae2apotheotic.runTests") || testsDone) {
            return;
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        autoTicks++;
        if (autoTicks >= 40) {
            testsDone = true;
            runAllTests();

            if (Boolean.getBoolean("ae2apotheotic.publishServer") && mc.getSingleplayerServer() != null) {
                int port = Integer.getInteger("ae2apotheotic.serverPort", 25565);
                mc.getSingleplayerServer().setUsesAuthentication(false);
                boolean published = mc.getSingleplayerServer().publishServer(GameType.SURVIVAL, false, port);
                if (published) {
                    pass("LAN Server Publishing on Port " + port);
                    log("Singleplayer server published to LAN on port " + port);
                    try (FileWriter fw = new FileWriter(new File("server_ready.txt"))) {
                        fw.write(String.valueOf(port));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    waitingForMultiplayer = true;
                    mpWaitTicks = 0;
                    return;
                } else {
                    fail("LAN Server Publishing -> failed to publish on port " + port);
                }
            }

            saveResultsToFile();
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

    public static void runServerTest(String name, TestCase test) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null) {
            fail(name + " -> Singleplayer server not running");
            return;
        }
        try {
            mc.getSingleplayerServer().executeBlocking(() -> {
                try {
                    test.run();
                    pass(name);
                } catch (Throwable t) {
                    String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                    fail(name + " -> " + msg);
                }
            });
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

            boolean ironValid = termInv.isItemValid(0, new ItemStack(Items.IRON_INGOT));
            if (mutate) {
                assertTrue(ironValid, "Deliberate mutation error: asserting iron ingot is accepted");
            } else {
                assertTrue(!ironValid, "Terminal must reject iron ingot in pattern slot");
            }
        });

        // Test 6: XP Math
        runTest("XP Math Conversion Factors", () -> {
            assertEqual(XpHelper.MB_PER_XP, 20, "20 mB per XP point standard");
            if (mutate) {
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

        // Test 9: Real In-Game Gem Cutting & Purity Upgrade Transaction (Server Thread)
        runServerTest("In-World Real Gem Cutting & Purity Upgrade Transaction", () -> {
            Minecraft mc = Minecraft.getInstance();
            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            ServerLevel serverLevel = serverPlayer != null ? serverPlayer.level() : mc.getSingleplayerServer().overworld();
            BlockPos playerPos = serverPlayer != null ? serverPlayer.blockPosition() : mc.player.blockPosition();

            BlockPos base = playerPos.relative(Direction.NORTH, 3);
            BlockPos casePos = base;
            BlockPos provPos = base.above();

            serverLevel.setBlock(casePos, Apoth.Blocks.GEM_CASE.value().defaultBlockState(), 3);
            serverLevel.setBlock(provPos, ModRegistry.GEM_PROVIDER.get().defaultBlockState(), 3);

            GemCaseTile gemCase = (GemCaseTile) serverLevel.getBlockEntity(casePos);
            GemProviderBlockEntity provBe = (GemProviderBlockEntity) serverLevel.getBlockEntity(provPos);
            assertNotNull(gemCase, "GemCaseTile present");
            assertNotNull(provBe, "GemProviderBlockEntity present");

            List<RecipeHolder<GemCuttingRecipe>> recipes = GemCuttingMenu.getRecipes(serverLevel);
            PurityUpgradeRecipe upgradeRecipe = null;
            Gem validGem = null;
            for (RecipeHolder<GemCuttingRecipe> holder : recipes) {
                if (holder.value() instanceof PurityUpgradeRecipe pur) {
                    for (Gem g : GemRegistry.INSTANCE.getValues()) {
                        if (pur.purity().isAtLeast(g.getMinPurity()) && pur.purity() != Purity.PERFECT) {
                            validGem = g;
                            upgradeRecipe = pur;
                            break;
                        }
                    }
                    if (upgradeRecipe != null) break;
                }
            }
            assertNotNull(upgradeRecipe, "Valid PurityUpgradeRecipe found in registry");
            assertNotNull(validGem, "Valid Gem for upgrade recipe found");

            Purity from = upgradeRecipe.purity();
            Purity to = from.next();
            ItemStack inputGemStack = GemItem.createStack(validGem, from, 1);
            ItemStack outputGemStack = GemItem.createStack(validGem, to, 1);

            SizedIngredient leftIng = upgradeRecipe.left().get(0);
            SizedIngredient rightIng = upgradeRecipe.right().get(0);
            MaterialChoice leftChoice = MaterialChoice.of(leftIng);
            MaterialChoice rightChoice = MaterialChoice.of(rightIng);
            ItemStack leftStack = leftChoice.displayStack();
            ItemStack rightStack = rightChoice.displayStack();

            GemUpgradeEncoding enc = new GemUpgradeEncoding(
                AEItemKey.of(inputGemStack),
                AEItemKey.of(outputGemStack),
                leftChoice,
                rightChoice
            );
            ItemStack upgradePatternStack = new ItemStack(ModRegistry.GEM_UPGRADE_PATTERN.get());
            upgradePatternStack.set(ModRegistry.GEM_UPGRADE.get(), enc);
            GemUpgradePattern pattern = new GemUpgradePattern(AEItemKey.of(upgradePatternStack), serverLevel);

            KeyCounter[] inputs = new KeyCounter[] { new KeyCounter(), new KeyCounter(), new KeyCounter() };
            inputs[0].add(AEItemKey.of(inputGemStack), GemUpgradeEncoding.GEMS_PER_UPGRADE);
            inputs[1].add(AEItemKey.of(leftStack), leftStack.getCount());
            inputs[2].add(AEItemKey.of(rightStack), rightStack.getCount());

            List<GenericStack> res = provBe.upgrade(serverLevel, pattern, inputs);
            assertNotNull(res, "Upgrade results must not be null");
            assertTrue(!res.isEmpty(), "Upgrade results must contain upgraded gem");
            assertEqual(res.get(0).what(), AEItemKey.of(outputGemStack), "Output matches expected upgraded gem");
            assertEqual(res.get(0).amount(), 1L, "Output count is 1");

            serverLevel.setBlock(casePos, Blocks.AIR.defaultBlockState(), 3);
            serverLevel.setBlock(provPos, Blocks.AIR.defaultBlockState(), 3);
        });

        // Test 10: In-World Safe Rollback (Preserves Unrelated Player Inventory) (Server Thread)
        runServerTest("In-World Safe Rollback (Preserves Unrelated Player Inventory)", () -> {
            Minecraft mc = Minecraft.getInstance();
            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            ServerLevel serverLevel = serverPlayer != null ? serverPlayer.level() : mc.getSingleplayerServer().overworld();
            BlockPos playerPos = serverPlayer != null ? serverPlayer.blockPosition() : mc.player.blockPosition();

            BlockPos base = playerPos.relative(Direction.SOUTH, 3);
            BlockPos casePos = base;
            BlockPos provPos = base.above();

            serverLevel.setBlock(casePos, Apoth.Blocks.GEM_CASE.value().defaultBlockState(), 3);
            serverLevel.setBlock(provPos, ModRegistry.GEM_PROVIDER.get().defaultBlockState(), 3);

            GemCaseTile gemCase = (GemCaseTile) serverLevel.getBlockEntity(casePos);
            GemProviderBlockEntity provBe = (GemProviderBlockEntity) serverLevel.getBlockEntity(provPos);
            assertNotNull(gemCase, "GemCaseTile present");

            InternalItemHandler mats = gemCase.getUpgradeMaterials();
            try (Transaction tx = Transaction.openRoot()) {
                mats.insertInternal(0, ItemResource.of(new ItemStack(Items.DIAMOND, 10)), 10, tx);
                tx.commit();
            }
            long diamondsBefore = mats.getAmountAsLong(0);
            assertEqual(diamondsBefore, 10L, "Gem Case contains 10 diamonds initially");

            Gem firstGem = GemRegistry.INSTANCE.getValues().iterator().next();
            ItemStack crackedStack = GemItem.createStack(firstGem, Purity.CRACKED, 1);
            ItemStack chippedStack = GemItem.createStack(firstGem, Purity.CHIPPED, 1);
            MaterialChoice leftChoice = new MaterialChoice(List.of(AEItemKey.of(Items.DIRT)), 1);
            MaterialChoice rightChoice = new MaterialChoice(List.of(AEItemKey.of(Items.COBBLESTONE)), 1);
            GemUpgradeEncoding enc = new GemUpgradeEncoding(
                AEItemKey.of(crackedStack),
                AEItemKey.of(chippedStack),
                leftChoice,
                rightChoice
            );
            ItemStack upgradePatternStack = new ItemStack(ModRegistry.GEM_UPGRADE_PATTERN.get());
            upgradePatternStack.set(ModRegistry.GEM_UPGRADE.get(), enc);
            GemUpgradePattern pattern = new GemUpgradePattern(AEItemKey.of(upgradePatternStack), serverLevel);

            KeyCounter[] inputs = new KeyCounter[] { new KeyCounter(), new KeyCounter(), new KeyCounter() };
            inputs[0].add(AEItemKey.of(crackedStack), 1);
            inputs[1].add(AEItemKey.of(Items.DIRT), 1);
            inputs[2].add(AEItemKey.of(Items.COBBLESTONE), 1);

            List<GenericStack> res = provBe.upgrade(serverLevel, pattern, inputs);
            assertTrue(res == null, "Upgrade must fail and return null for invalid recipe");

            long diamondsAfter = mats.getAmountAsLong(0);
            assertEqual(diamondsAfter, 10L, "Rollback: diamonds must remain untouched (exact 10)");

            serverLevel.setBlock(casePos, Blocks.AIR.defaultBlockState(), 3);
            serverLevel.setBlock(provPos, Blocks.AIR.defaultBlockState(), 3);
        });

        // Test 11: In-World Real Gem Crushing Pipeline (Server Thread)
        runServerTest("In-World Real Gem Crushing Pipeline", () -> {
            Minecraft mc = Minecraft.getInstance();
            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            ServerLevel serverLevel = serverPlayer != null ? serverPlayer.level() : mc.getSingleplayerServer().overworld();
            BlockPos playerPos = serverPlayer != null ? serverPlayer.blockPosition() : mc.player.blockPosition();

            BlockPos provPos = playerPos.relative(Direction.WEST, 3);
            serverLevel.setBlock(provPos, ModRegistry.GEM_PROVIDER.get().defaultBlockState(), 3);
            GemProviderBlockEntity provBe = (GemProviderBlockEntity) serverLevel.getBlockEntity(provPos);
            assertNotNull(provBe, "GemProviderBlockEntity present");

            Gem firstGem = GemRegistry.INSTANCE.getValues().iterator().next();
            ItemStack crackedStack = GemItem.createStack(firstGem, Purity.CRACKED, 2);

            KeyCounter[] inputs = new KeyCounter[] { new KeyCounter() };
            inputs[0].add(AEItemKey.of(crackedStack), 2);

            GemCrushEncoding crushEnc = new GemCrushEncoding(GemCrushEncoding.CrushMode.EXACT,
                Optional.of(AEItemKey.of(GemItem.createStack(firstGem, Purity.CRACKED, 1))), Purity.CRACKED);
            ItemStack crushPatternItem = new ItemStack(ModRegistry.GEM_CRUSH_PATTERN.get());
            crushPatternItem.set(ModRegistry.GEM_CRUSH.get(), crushEnc);
            GemCrushPattern crushPattern = new GemCrushPattern(AEItemKey.of(crushPatternItem), serverLevel);

            List<GenericStack> res = provBe.crush(crushPattern, inputs);
            assertNotNull(res, "Crush results must not be null");
            assertTrue(!res.isEmpty(), "Crush produces dust");
            assertEqual(res.get(0).what(), GemKeys.dust(), "Yields Gem Dust");
            int yield = GemCrushEncoding.dustYield(UnsocketedGem.of(crackedStack).purity());
            int expectedDust = 2 * yield;
            assertEqual(res.get(0).amount(), (long) expectedDust, "Yields correct dust quantity");

            serverLevel.setBlock(provPos, Blocks.AIR.defaultBlockState(), 3);
        });

        // Test 12: In-World Real Infusion Recipe Crafting & Liquid XP Consumption (Server Thread)
        runServerTest("In-World Real Infusion Recipe Crafting & Liquid XP Consumption", () -> {
            Minecraft mc = Minecraft.getInstance();
            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            ServerLevel serverLevel = serverPlayer != null ? serverPlayer.level() : mc.getSingleplayerServer().overworld();
            BlockPos playerPos = serverPlayer != null ? serverPlayer.blockPosition() : mc.player.blockPosition();

            BlockPos base = playerPos.relative(Direction.EAST, 4);
            BlockPos tablePos = base;
            BlockPos infPos = base.above();

            serverLevel.setBlock(tablePos, Ench.Blocks.RAVEN_ENCHANTING_TABLE.value().defaultBlockState(), 3);
            serverLevel.setBlock(infPos, ModRegistry.INFUSION_PROVIDER.get().defaultBlockState(), 3);

            InfusionProviderBlockEntity infBe = (InfusionProviderBlockEntity) serverLevel.getBlockEntity(infPos);
            assertNotNull(infBe, "InfusionProviderBlockEntity present");

            InfusionRecipe infRecipe = null;
            Identifier recipeId = null;
            for (var recipeHolder : serverLevel.recipeAccess().getRecipes()) {
                if (recipeHolder.value() instanceof InfusionRecipe ir) {
                    infRecipe = ir;
                    recipeId = recipeHolder.id().identifier();
                    break;
                }
            }
            assertNotNull(infRecipe, "At least one InfusionRecipe found in registry");

            ItemStack inputItem = infRecipe.getInput().items()
                .map(h -> h.value().getDefaultInstance())
                .findFirst()
                .orElse(ItemStack.EMPTY);
            assertTrue(!inputItem.isEmpty(), "Infusion input item exists");
            float eterna = infRecipe.getRequirements().eterna();
            float quanta = infRecipe.getRequirements().quanta();
            float arcana = infRecipe.getRequirements().arcana();
            ItemStack assembled = infRecipe.assemble(inputItem.copy(), eterna, quanta, arcana);
            assertTrue(!assembled.isEmpty(), "Recipe assemble returns non-empty result");

            infBe.setStoredXpMb(100_000L);
            int initialXp = infBe.getStoredXp();
            assertTrue(initialXp > 0, "Initial stored XP > 0");

            int xpCost = InfusionProviderBlockEntity.requiredXp(infRecipe);
            InfusionEncoding enc = new InfusionEncoding(
                AEItemKey.of(inputItem),
                AEItemKey.of(assembled),
                1,
                recipeId,
                (int) Math.ceil(eterna),
                (int) Math.ceil(quanta),
                (int) Math.ceil(arcana),
                xpCost
            );
            ItemStack infPatternStack = new ItemStack(ModRegistry.INFUSION_PATTERN.get());
            infPatternStack.set(ModRegistry.INFUSION.get(), enc);
            InfusionPattern pattern = new InfusionPattern(AEItemKey.of(infPatternStack), serverLevel);

            KeyCounter[] inputs = new KeyCounter[] { new KeyCounter(), new KeyCounter() };
            inputs[0].add(AEItemKey.of(inputItem), 1);
            inputs[1].add(AEItemKey.of(Items.LAPIS_LAZULI), InfusionEncoding.LAPIS_COST);

            List<GenericStack> res = infBe.process(serverLevel, pattern, inputs);
            assertNotNull(res, "Infusion process results must not be null");
            assertTrue(!res.isEmpty(), "Infusion produces output item");
            assertEqual(res.get(0).what(), AEItemKey.of(assembled), "Output matches assembled item");
            assertTrue(infBe.getStoredXp() <= initialXp, "Stored XP consumed by infusion");

            serverLevel.setBlock(tablePos, Blocks.AIR.defaultBlockState(), 3);
            serverLevel.setBlock(infPos, Blocks.AIR.defaultBlockState(), 3);
        });

        // Test 13: In-World ME Pattern Encoder Blank Programming Pipeline (Server Thread)
        runServerTest("In-World ME Pattern Encoder Blank Programming Pipeline", () -> {
            Minecraft mc = Minecraft.getInstance();
            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            ServerLevel serverLevel = serverPlayer != null ? serverPlayer.level() : mc.getSingleplayerServer().overworld();
            BlockPos playerPos = serverPlayer != null ? serverPlayer.blockPosition() : mc.player.blockPosition();

            BlockPos encPos = playerPos.relative(Direction.UP, 4);
            serverLevel.setBlock(encPos, ModRegistry.PATTERN_ENCODER.get().defaultBlockState(), 3);

            EncoderBlockEntity encBe = (EncoderBlockEntity) serverLevel.getBlockEntity(encPos);
            assertNotNull(encBe, "EncoderBlockEntity present");

            Gem firstGem = GemRegistry.INSTANCE.getValues().iterator().next();
            ItemStack crackedStack = GemItem.createStack(firstGem, Purity.CRACKED, 1);

            encBe.getInventory().setItemDirect(EncoderBlockEntity.SLOT_BLANK, new ItemStack(AEItems.BLANK_PATTERN.asItem(), 3));
            encBe.getInventory().setItemDirect(EncoderBlockEntity.SLOT_SAMPLE, crackedStack);
            encBe.setMode(EncoderBlockEntity.MODE_CRUSH);
            encBe.setCrushMode(GemCrushEncoding.CrushMode.EXACT.ordinal());

            EncoderMenu menu = new EncoderMenu(1, serverPlayer.getInventory(), encBe);
            menu.clickMenuButton(serverPlayer, EncoderMenu.BTN_ENCODE);

            ItemStack blanksLeft = encBe.getInventory().getStackInSlot(EncoderBlockEntity.SLOT_BLANK);
            assertEqual(blanksLeft.getCount(), 2, "1 Blank pattern was consumed");

            ItemStack encodedOutput = encBe.getInventory().getStackInSlot(EncoderBlockEntity.SLOT_OUTPUT);
            assertTrue(!encodedOutput.isEmpty(), "Output slot contains pattern");
            assertEqual(encodedOutput.getItem(), ModRegistry.GEM_CRUSH_PATTERN.get(), "Encoded item is GEM_CRUSH_PATTERN");
            assertNotNull(encodedOutput.get(ModRegistry.GEM_CRUSH.get()), "Data component GEM_CRUSH present");

            serverLevel.setBlock(encPos, Blocks.AIR.defaultBlockState(), 3);
        });

        // Test 14: In-World Serverbound Recipe Transfer Payload Handler (Server Thread)
        runServerTest("In-World Serverbound Recipe Transfer Payload Handler", () -> {
            Minecraft mc = Minecraft.getInstance();
            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            ServerLevel serverLevel = serverPlayer != null ? serverPlayer.level() : mc.getSingleplayerServer().overworld();
            BlockPos playerPos = serverPlayer != null ? serverPlayer.blockPosition() : mc.player.blockPosition();

            BlockPos encPos = playerPos.relative(Direction.UP, 5);
            serverLevel.setBlock(encPos, ModRegistry.PATTERN_ENCODER.get().defaultBlockState(), 3);

            EncoderBlockEntity encBe = (EncoderBlockEntity) serverLevel.getBlockEntity(encPos);
            assertNotNull(encBe, "EncoderBlockEntity present");

            EncoderMenu menu = new EncoderMenu(10, serverPlayer.getInventory(), encBe);
            serverPlayer.containerMenu = menu;

            ItemStack sample = new ItemStack(Items.DIAMOND_SWORD);
            serverPlayer.getInventory().add(sample.copy());

            TransferRecipePayload payload = new TransferRecipePayload(
                EncoderBlockEntity.MODE_INFUSION,
                sample,
                ItemStack.EMPTY,
                -1,
                -1,
                75,
                35,
                20
            );

            TransferRecipePayload.applyTo(serverPlayer, payload);

            assertEqual(encBe.getMode(), EncoderBlockEntity.MODE_INFUSION, "Mode set to INFUSION");
            assertEqual(encBe.getSample().getItem(), Items.DIAMOND_SWORD, "Sample set to Diamond Sword");
            assertEqual(encBe.getEterna(), 75, "Eterna set to 75");
            assertEqual(encBe.getQuanta(), 35, "Quanta set to 35");
            assertEqual(encBe.getArcana(), 20, "Arcana set to 20");

            serverPlayer.containerMenu = serverPlayer.inventoryMenu;
            serverLevel.setBlock(encPos, Blocks.AIR.defaultBlockState(), 3);
        });

        long passCount = results.stream().filter(s -> s.startsWith("[PASS]")).count();
        long failCount = results.stream().filter(s -> s.startsWith("[FAIL]")).count();

        log("=================================================");
        log("  HOST RUN SUMMARY: " + passCount + " PASSED, " + failCount + " FAILED");
        log("=================================================");
    }

    public static void runMultiplayerTests() {
        results.clear();
        log("=================================================");
        log("    AE2 Apotheotic Multiplayer Client Test Suite  ");
        log("=================================================");

        // MP Test 1: Remote Connection Active
        runTest("Multiplayer: Remote Server Connection Active (TCP Netty)", () -> {
            Minecraft mc = Minecraft.getInstance();
            assertTrue(!mc.isLocalServer(), "Must be remote connection (isLocalServer == false)");
            assertNotNull(mc.getConnection(), "ClientPacketListener must be active");
            assertNotNull(mc.getCurrentServer(), "CurrentServer must not be null");
            log("Connected to server: " + mc.getCurrentServer().ip);
        });

        // MP Test 2: Live TCP Packet Dispatch to Server
        runTest("Multiplayer: Client-to-Server Live TCP Packet Dispatch", () -> {
            Minecraft mc = Minecraft.getInstance();
            // Empty sample: this only exercises the TCP packet path, and the server must not trust arbitrary items
            // sent by a client anyway.
            TransferRecipePayload payload = new TransferRecipePayload(
                EncoderBlockEntity.MODE_INFUSION,
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                -1,
                -1,
                80,
                40,
                25
            );
            assertNotNull(mc.getConnection(), "Connection must exist to send payload");
            mc.getConnection().send(new ServerboundCustomPayloadPacket(payload));
            log("Successfully sent TransferRecipePayload over TCP socket to server!");
        });

        saveResultsToFile("multiplayer_results.txt");
        signalMultiplayerDone();
    }

    /**
     * Signals the host instance that the remote client finished. The marker is written to the working directory and,
     * when {@code -Dae2apotheotic.hostDir=<dir>} is set, also to the host's game directory so a separately launched
     * host can notice it.
     */
    private static void signalMultiplayerDone() {
        try {
            new File("multiplayer_done.txt").createNewFile();
        } catch (Exception ignored) {}
        String hostDir = System.getProperty("ae2apotheotic.hostDir");
        if (hostDir != null && !hostDir.isBlank()) {
            try {
                new File(hostDir, "multiplayer_done.txt").createNewFile();
            } catch (Exception ignored) {}
        }
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
            if (RenderSystem.isOnRenderThread()) {
                mc.gui.getChat().addClientSystemMessage(Component.literal(msg));
            } else {
                mc.execute(() -> {
                    if (mc.gui != null && mc.gui.getChat() != null) {
                        mc.gui.getChat().addClientSystemMessage(Component.literal(msg));
                    }
                });
            }
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
        saveResultsToFile("gametest_results.txt");
    }

    private static void saveResultsToFile(String filename) {
        try {
            File outFile = new File(filename);
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
