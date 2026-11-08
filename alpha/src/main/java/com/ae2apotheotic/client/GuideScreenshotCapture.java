package com.ae2apotheotic.client;

import com.ae2apotheotic.registry.ModRegistry;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class GuideScreenshotCapture {
    private static int captureState = -1;
    private static int waitTicks = 0;
    private static int autoTicks = 0;
    private static boolean autoDone = false;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(GuideScreenshotCapture::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(GuideScreenshotCapture::onClientTick);
    }

    private static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("ae2apotheotic")
                .then(Commands.literal("capture")
                    .executes(ctx -> {
                        startCapture();
                        return 1;
                    }))
        );
    }

    public static void startCapture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        captureState = 0;
        waitTicks = 20;
        mc.options.hideGui = true;
        mc.player.getInventory().clearContent();
        if (mc.getSingleplayerServer() != null) {
            var s = mc.getSingleplayerServer();
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "time set day");
        }
        if (mc.gui != null && mc.gui.getChat() != null) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§d[AE2Apotheotic] Starting in-game screenshots capture..."));
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Auto-run if launched with -Dae2apotheotic.autoCapture=true
        if (!autoDone && Boolean.getBoolean("ae2apotheotic.autoCapture")) {
            autoTicks++;
            if (autoTicks == 100) { // 5 seconds after world join
                startCapture();
            }
        }

        if (captureState < 0) return;

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        Level level = mc.level;
        LocalPlayer player = mc.player;

        switch (captureState) {
            case 0 -> {
                // Setup Scene 1: Pattern Encoder
                buildEncoderScene(level);
                positionCamera(player, 98.2, 100.6, 98.0, -45.0f, 26.0f);
                captureState = 1;
                waitTicks = 30; // wait for chunks & light to render
            }
            case 1 -> {
                takeAndSave("encoder_setup.png", () -> {
                    // Setup Scene 2: Gem Pattern Provider
                    buildGemProviderScene(level);
                    positionCamera(player, 118.2, 100.6, 98.0, -45.0f, 26.0f);
                    captureState = 2;
                    waitTicks = 30;
                });
            }
            case 2 -> {
                takeAndSave("gem_provider_setup.png", () -> {
                    // Setup Scene 3: Infusion Pattern Provider
                    buildInfusionProviderScene(level);
                    positionCamera(player, 138.2, 100.6, 98.0, -45.0f, 26.0f);
                    captureState = 3;
                    waitTicks = 30;
                });
            }
            case 3 -> {
                takeAndSave("infusion_provider_setup.png", () -> {
                    captureState = -1;
                    mc.options.hideGui = false;
                    autoDone = true;
                    if (mc.gui != null && mc.gui.getChat() != null) {
                        mc.gui.getChat().addClientSystemMessage(Component.literal("§a[AE2Apotheotic] All 3 guide screenshots successfully captured and saved!"));
                    }
                    if (Boolean.getBoolean("ae2apotheotic.autoCapture")) {
                        mc.stop();
                    }
                });
            }
        }
    }

    private static void positionCamera(LocalPlayer player, double x, double y, double z, float yaw, float pitch) {
        player.setPos(x, y, z);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
    }

    private static void buildEncoderScene(Level level) {
        clearArea(level, 96, 99, 96, 105, 104, 105);
        // Floor
        for (int x = 97; x <= 103; x++) {
            for (int z = 97; z <= 103; z++) {
                level.setBlock(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 3);
            }
        }
        // Wall behind
        for (int x = 97; x <= 103; x++) {
            for (int y = 100; y <= 102; y++) {
                level.setBlock(new BlockPos(x, y, 103), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
        }
        // Center block
        level.setBlock(new BlockPos(100, 100, 100), ModRegistry.PATTERN_ENCODER.get().defaultBlockState(), 3);
        // AE2 Drive
        level.setBlock(new BlockPos(99, 100, 100), getBlockOr("ae2:drive", Blocks.CHISELED_STONE_BRICKS), 3);
        // AE2 Energy Cell
        level.setBlock(new BlockPos(101, 100, 100), getBlockOr("ae2:energy_cell", Blocks.CHISELED_QUARTZ_BLOCK), 3);
        // Lighting
        level.setBlock(new BlockPos(100, 103, 100), Blocks.GLOWSTONE.defaultBlockState(), 3);
    }

    private static void buildGemProviderScene(Level level) {
        clearArea(level, 116, 99, 96, 125, 104, 105);
        // Floor
        for (int x = 117; x <= 123; x++) {
            for (int z = 97; z <= 103; z++) {
                level.setBlock(new BlockPos(x, 99, z), Blocks.POLISHED_ANDESITE.defaultBlockState(), 3);
            }
        }
        // Wall behind
        for (int x = 117; x <= 123; x++) {
            for (int y = 100; y <= 102; y++) {
                level.setBlock(new BlockPos(x, y, 103), Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
        }
        // Provider
        level.setBlock(new BlockPos(120, 100, 100), ModRegistry.GEM_PROVIDER.get().defaultBlockState(), 3);
        // Gem Case directly adjacent
        level.setBlock(new BlockPos(121, 100, 100), getBlockOr("apotheosis:gem_case", Blocks.CHEST), 3);
        // AE2 Drive
        level.setBlock(new BlockPos(119, 100, 100), getBlockOr("ae2:drive", Blocks.CHISELED_STONE_BRICKS), 3);
        // Lighting
        level.setBlock(new BlockPos(120, 103, 100), Blocks.GLOWSTONE.defaultBlockState(), 3);
    }

    private static void buildInfusionProviderScene(Level level) {
        clearArea(level, 136, 99, 96, 145, 104, 105);
        // Floor
        for (int x = 137; x <= 143; x++) {
            for (int z = 97; z <= 103; z++) {
                level.setBlock(new BlockPos(x, 99, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
            }
        }
        // Bookshelves behind
        for (int x = 137; x <= 143; x++) {
            for (int y = 100; y <= 102; y++) {
                level.setBlock(new BlockPos(x, y, 103), getBlockOr("apothic_enchanting:infused_hellshelf", Blocks.BOOKSHELF), 3);
            }
        }
        // Provider
        level.setBlock(new BlockPos(140, 100, 100), ModRegistry.INFUSION_PROVIDER.get().defaultBlockState(), 3);
        // Raven's Enchanting Table directly adjacent
        level.setBlock(new BlockPos(141, 100, 100), getBlockOr("apothic_enchanting:raven_enchanting_table", Blocks.ENCHANTING_TABLE), 3);
        // AE2 Drive
        level.setBlock(new BlockPos(139, 100, 100), getBlockOr("ae2:drive", Blocks.CHISELED_STONE_BRICKS), 3);
        // Liquid XP tank
        level.setBlock(new BlockPos(140, 100, 101), getBlockOr("ae2:sky_stone_tank", Blocks.LIME_STAINED_GLASS), 3);
        // Lighting
        level.setBlock(new BlockPos(140, 103, 100), Blocks.GLOWSTONE.defaultBlockState(), 3);
    }

    private static void clearArea(Level level, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static BlockState getBlockOr(String id, net.minecraft.world.level.block.Block fallback) {
        var opt = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id));
        return opt.map(net.minecraft.world.level.block.Block::defaultBlockState).orElseGet(fallback::defaultBlockState);
    }

    private static void takeAndSave(String filename, Runnable onDone) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget rt = mc.getMainRenderTarget();
        Screenshot.takeScreenshot(rt, nativeImage -> {
            try {
                Path temp = Files.createTempFile("ae2apoth_ss_", ".png");
                nativeImage.writeToFile(temp);
                nativeImage.close();

                // Load and optimize to 560px width
                BufferedImage raw = ImageIO.read(temp.toFile());
                Files.deleteIfExists(temp);

                int targetW = 560;
                int targetH = (int) Math.round(raw.getHeight() * ((double) targetW / raw.getWidth()));

                BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(raw, 0, 0, targetW, targetH, null);
                g.dispose();

                // Save to guide paths
                saveToGuide(scaled, filename);
            } catch (Exception e) {
                e.printStackTrace();
            }
            onDone.run();
        });
    }

    private static void saveToGuide(BufferedImage img, String filename) throws IOException {
        String base = System.getProperty("ae2apotheotic.outputDir", "src/main/resources/assets/ae2/ae2guide/");
        File f1 = new File(base, "ae2apotheotic/assets/" + filename);
        File f2 = new File(base, "_ru_ru/ae2apotheotic/assets/" + filename);
        f1.getParentFile().mkdirs();
        f2.getParentFile().mkdirs();
        ImageIO.write(img, "png", f1);
        ImageIO.write(img, "png", f2);
        System.out.println("[AE2Apotheotic] Saved in-game screenshot: " + f1.getAbsolutePath() + " (" + (f1.length() / 1024) + " KB)");
    }
}
