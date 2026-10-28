import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Generates the encoded pattern sprites (AE2 shape, recolored) and the mod logo (our blocks in isometry + vanilla-style title).
 * Uses only this mod's own textures: Apotheosis assets are All Rights Reserved, so gems are drawn by hand.
 * Run: java art/GenArt.java <AE2 blank_pattern.png> src/main/resources/assets/ae2apotheotic/textures/item src/main/resources/logo.png <preview dir>
 * (blank_pattern.png comes from the AE2 jar: assets/ae2/textures/item/blank_pattern.png)
 */
public class GenArt {
    public static void main(String[] args) throws Exception {
        BufferedImage blank = ImageIO.read(new File(args[0]));
        String itemDir = args[1];
        // sapphire sheet, gold glass, gold up arrow
        pattern(blank, itemDir + "/gem_upgrade_pattern.png", 0x3F7BFF, 0xFFD447, new String[] {
            "..#..",
            ".###.",
            "#####",
            "..#..",
            "..#.." }, 0xFFFFE680, 0xFF9A6A00);
        // amber sheet, pale dust glass, shattered sparkle
        pattern(blank, itemDir + "/gem_crush_pattern.png", 0xE88A2A, 0xFFF3B0, new String[] {
            "#...#",
            ".#.#.",
            "..#..",
            ".#.#.",
            "#...#" }, 0xFFFFF6D8, 0xFF5A2A08);
        // magenta sheet, liquid-xp glass, enchanting ring with an xp core
        pattern(blank, itemDir + "/infusion_pattern.png", 0xC83CD8, 0x7CFF4F, new String[] {
            ".###.",
            "#...#",
            "#.@.#",
            "#...#",
            ".###." }, 0xFFFFC2F8, 0xFF8CFF4A);
        logoScene(args[2], new File(itemDir).getParent() + "/block", itemDir);
        preview(args[3], itemDir);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Patterns

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static float lum(int c) {
        return (((c >> 16) & 0xFF) * 0.3F + ((c >> 8) & 0xFF) * 0.59F + (c & 0xFF) * 0.11F) / 255F;
    }

    /** Base color scaled by brightness, very bright values drift towards white. */
    private static int shade(int base, float l) {
        int r = (base >> 16) & 0xFF;
        int g = (base >> 8) & 0xFF;
        int b = base & 0xFF;
        float k = 0.25F + 1.05F * l;
        int nr = (int) (r * k);
        int ng = (int) (g * k);
        int nb = (int) (b * k);
        if (l > 0.85F) {
            float w = (l - 0.85F) / 0.15F * 0.55F;
            nr = (int) (nr + (255 - nr) * w);
            ng = (int) (ng + (255 - ng) * w);
            nb = (int) (nb + (255 - nb) * w);
        }
        return rgb(nr, ng, nb);
    }

    private static boolean isGlass(int c) {
        int r = (c >> 16) & 0xFF;
        int b = c & 0xFF;
        return b - r > 40; // the light blue "screen" of the AE2 pattern
    }

    private static void pattern(BufferedImage blank, String out, int body, int glass, String[] glyph, int glyphColor, int glyphAccent)
        throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = blank.getRGB(x, y);
                if ((c >>> 24) == 0) {
                    continue;
                }
                float l = lum(c);
                int color;
                if (isGlass(c)) {
                    color = shade(glass, 0.45F + l * 0.6F);
                } else if (l < 0.33F) {
                    color = shade(body, 0.12F); // outline
                } else {
                    color = shade(body, l);
                }
                img.setRGB(x, y, color);
            }
        }
        // glyph in the 5x5 area on the right of the sheet, with a dark drop shadow
        for (int gy = 0; gy < 5; gy++) {
            for (int gx = 0; gx < 5; gx++) {
                char ch = glyph[gy].charAt(gx);
                if (ch == '.') {
                    continue;
                }
                int x = 9 + gx;
                int y = 7 + gy;
                if (y + 1 < 16 && glyph.length > gy + 1 && glyph[gy + 1].charAt(gx) == '.' && (img.getRGB(x, y + 1) >>> 24) != 0) {
                    img.setRGB(x, y + 1, shade(body, 0.2F));
                }
                img.setRGB(x, y, ch == '@' ? glyphAccent : glyphColor);
            }
        }
        // second pass so shadows never cover glyph pixels
        for (int gy = 0; gy < 5; gy++) {
            for (int gx = 0; gx < 5; gx++) {
                char ch = glyph[gy].charAt(gx);
                if (ch != '.') {
                    img.setRGB(9 + gx, 7 + gy, ch == '@' ? glyphAccent : glyphColor);
                }
            }
        }
        ImageIO.write(img, "png", new File(out));
        System.out.println("wrote " + out);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return rgb(r, g, bl);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Logo

    private static final Map<Character, String[]> FONT = new HashMap<>();

    static {
        FONT.put('A', new String[] { ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#" });
        FONT.put('P', new String[] { "####.", "#...#", "#...#", "####.", "#....", "#....", "#...." });
        FONT.put('O', new String[] { ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###." });
        FONT.put('T', new String[] { "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.." });
        FONT.put('H', new String[] { "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#" });
        FONT.put('E', new String[] { "#####", "#....", "#....", "####.", "#....", "#....", "#####" });
        FONT.put('I', new String[] { "###", ".#.", ".#.", ".#.", ".#.", ".#.", "###" });
        FONT.put('C', new String[] { ".####", "#....", "#....", "#....", "#....", "#....", ".####" });
        FONT.put('U', new String[] { "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###." });
        FONT.put('M', new String[] { "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#" });
        FONT.put('N', new String[] { "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#" });
        FONT.put('2', new String[] { ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####" });
        FONT.put(' ', new String[] { "..", "..", "..", "..", "..", "..", ".." });
    }

    private static int textWidth(String text, int gap) {
        int w = 0;
        for (char ch : text.toCharArray()) {
            w += FONT.get(ch)[0].length() + gap;
        }
        return w - gap;
    }

    /** Text mask in font pixels. */
    private static boolean[][] textMask(String text, int gap) {
        int w = textWidth(text, gap);
        boolean[][] mask = new boolean[7][w];
        int cx = 0;
        for (char ch : text.toCharArray()) {
            String[] g = FONT.get(ch);
            for (int y = 0; y < 7; y++) {
                for (int x = 0; x < g[y].length(); x++) {
                    if (g[y].charAt(x) == '#') {
                        mask[y][cx + x] = true;
                    }
                }
            }
            cx += g[0].length() + gap;
        }
        return mask;
    }

    private static void logo(String out) throws Exception {
        Random rnd = new Random(26);
        int W = 230;
        int H = 54;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);

        // --- main title: stone block letters, 3 px per font pixel, extruded down-right like the vanilla logo
        String title = "APOTHEOTIC";
        boolean[][] mask = textMask(title, 2);
        int s = 3;
        int tw = mask[0].length * s;
        int ox = (W - tw) / 2 - 1;
        int oy = 4;
        boolean[][] face = new boolean[H][W];
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < mask[0].length; x++) {
                if (mask[y][x]) {
                    for (int dy = 0; dy < s; dy++) {
                        for (int dx = 0; dx < s; dx++) {
                            face[oy + y * s + dy][ox + x * s + dx] = true;
                        }
                    }
                }
            }
        }
        int depth = 4;
        boolean[][] solid = new boolean[H][W];
        // extrusion
        for (int d = depth; d >= 1; d--) {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    if (face[y][x] && y + d < H && x + d / 2 < W) {
                        int yy = y + d;
                        int xx = x + d / 2;
                        if (!face[yy][xx]) {
                            int g = 0x3A - d * 4 + rnd.nextInt(8);
                            img.setRGB(xx, yy, rgb(g, g, g + 4));
                        }
                        solid[yy][xx] = true;
                    }
                }
            }
        }
        // face with stone noise, lighter top edges
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (!face[y][x]) {
                    continue;
                }
                solid[y][x] = true;
                int g = 0x86 + rnd.nextInt(5) * 8 - 16;
                if (rnd.nextInt(9) == 0) {
                    g -= 28;
                }
                boolean topEdge = y == 0 || !face[y - 1][x];
                boolean leftEdge = x == 0 || !face[y][x - 1];
                boolean bottomEdge = y + 1 >= H || !face[y + 1][x];
                if (topEdge || leftEdge) {
                    g += 40;
                } else if (bottomEdge) {
                    g -= 26;
                }
                img.setRGB(x, y, rgb(g, g, g + 3));
            }
        }
        outline(img, solid, 0xFF000000);

        // --- subtitle: "AE2 AUTOMATION" in yellow with a dark shadow, between two gems
        String sub = "AE2 AUTOMATION";
        boolean[][] subMask = textMask(sub, 1);
        int sw = subMask[0].length * 2;
        int sx = (W - sw) / 2;
        int sy = oy + 7 * s + depth + 8;
        boolean[][] subSolid = new boolean[H][W];
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < subMask[0].length; x++) {
                if (subMask[y][x]) {
                    for (int dy = 0; dy < 2; dy++) {
                        for (int dx = 0; dx < 2; dx++) {
                            int px = sx + x * 2 + dx;
                            int py = sy + y * 2 + dy;
                            img.setRGB(px + 1, py + 1, 0xFF3F3F00);
                            subSolid[py][px] = true;
                            subSolid[py + 1][px + 1] = true;
                        }
                    }
                }
            }
        }
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < subMask[0].length; x++) {
                if (subMask[y][x]) {
                    int c = y < 3 ? 0xFFFFFF7A : 0xFFFFE040;
                    for (int dy = 0; dy < 2; dy++) {
                        for (int dx = 0; dx < 2; dx++) {
                            img.setRGB(sx + x * 2 + dx, sy + y * 2 + dy, c);
                        }
                    }
                }
            }
        }
        gem(img, sx - 14, sy + 1, 0xFF3FA9FF);
        gem(img, sx + sw + 4, sy + 1, 0xFFD23FE0);

        // upscale x4 with nearest neighbour
        int k = 4;
        BufferedImage big = new BufferedImage(W * k, H * k, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H * k; y++) {
            for (int x = 0; x < W * k; x++) {
                big.setRGB(x, y, img.getRGB(x / k, y / k));
            }
        }
        ImageIO.write(big, "png", new File(out));
        System.out.println("wrote " + out);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Logo with a scene: our three blocks in inventory-style isometry, cables, gems and patterns, title below.

    private static void logoScene(String out, String blockDir, String itemDir) throws Exception {
        Random rnd = new Random(26);
        int W = 160;
        int H = 160;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);

        // background: dark rounded panel with a purple / cyan glow and a few sparkles
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (corner(x, y, W, H, 6)) {
                    continue;
                }
                float dx = (x - 80) / 80F;
                float dy = (y - 52) / 70F;
                float glow = Math.max(0, 1 - (dx * dx + dy * dy));
                int r = (int) (0x16 + 0x30 * glow * glow);
                int g = (int) (0x13 + 0x10 * glow);
                int b = (int) (0x24 + 0x40 * glow * glow);
                // dither bands like a pixel-art sky
                if (((x + y) & 1) == 0 && glow > 0.2F && glow < 0.8F) {
                    r += 4;
                    b += 6;
                }
                img.setRGB(x, y, rgb(r, g, b));
            }
        }
        frame(img, W, H, 6);
        for (int i = 0; i < 40; i++) {
            int x = 6 + rnd.nextInt(W - 12);
            int y = 6 + rnd.nextInt(92);
            int c = rnd.nextBoolean() ? 0xFF7FE6FF : 0xFFD9A6FF;
            img.setRGB(x, y, mix(img.getRGB(x, y), c, 0.5F + rnd.nextFloat() * 0.5F));
        }

        // side blocks (encoder left, infusion provider right) and the gem provider in front
        shadow(img, 30, 86, 18, 3);
        shadow(img, 130, 86, 18, 3);
        shadow(img, 80, 90, 34, 5);
        isoCube(img, blockDir, "pattern_encoder", 30, 54, 1);
        isoCube(img, blockDir, "infusion_pattern_provider", 130, 54, 1);
        isoCube(img, blockDir, "gem_pattern_provider", 80, 24, 2);

        // floating patterns and gems
        sprite(img, itemDir + "/gem_upgrade_pattern.png", 14, 22, 1);
        sprite(img, itemDir + "/infusion_pattern.png", 130, 22, 1);
        sprite(img, itemDir + "/gem_crush_pattern.png", 72, 4, 1);
        gem(img, 44, 12, 0xFF3FA9FF);
        gem(img, 108, 10, 0xFFD23FE0);
        gem(img, 12, 46, 0xFF3FE07A);
        gem(img, 140, 44, 0xFFFF6A3F);
        sparkle(img, 36, 30);
        sparkle(img, 124, 32);
        sparkle(img, 96, 6);
        sparkle(img, 60, 10);

        // title
        drawStoneText(img, "APOTHEOTIC", W / 2, 104, 2, 3, rnd);
        drawYellowText(img, "AE2 AUTOMATION", W / 2, 129);

        int k = 4;
        BufferedImage big = new BufferedImage(W * k, H * k, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H * k; y++) {
            for (int x = 0; x < W * k; x++) {
                big.setRGB(x, y, img.getRGB(x / k, y / k));
            }
        }
        ImageIO.write(big, "png", new File(out));
        System.out.println("wrote " + out);
    }

    private static boolean corner(int x, int y, int w, int h, int r) {
        int cx = x < r ? r : x >= w - r ? w - r - 1 : x;
        int cy = y < r ? r : y >= h - r ? h - r - 1 : y;
        int dx = x - cx;
        int dy = y - cy;
        return dx * dx + dy * dy > r * r;
    }

    private static void frame(BufferedImage img, int w, int h, int r) {
        boolean[][] solid = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                solid[y][x] = (img.getRGB(x, y) >>> 24) != 0;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!solid[y][x]) {
                    continue;
                }
                boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1 || !solid[y - 1][x] || !solid[y + 1][x] || !solid[y][x - 1]
                    || !solid[y][x + 1];
                boolean inner = !edge && (x <= 1 || y <= 1 || x >= w - 2 || y >= h - 2 || !solid[y - 2][x] || !solid[y + 2][x]
                    || !solid[y][x - 2] || !solid[y][x + 2]);
                if (edge) {
                    img.setRGB(x, y, 0xFF0A0812);
                } else if (inner) {
                    img.setRGB(x, y, y < h / 2 ? 0xFF8A5CD8 : 0xFF4FB8D8);
                }
            }
        }
    }

    /** Minecraft inventory-style block: top face lit, left face 86 %, right face 68 %. cx/ty = top vertex, a = pixels per texel. */
    private static void isoCube(BufferedImage img, String dir, String name, int cx, int ty, int a) throws Exception {
        BufferedImage top = ImageIO.read(new File(dir + "/" + name + "_top.png"));
        BufferedImage left = ImageIO.read(new File(dir + "/" + name + "_front.png"));
        BufferedImage right = ImageIO.read(new File(dir + "/" + name + "_side.png"));
        int n = 16;
        boolean[][] solid = new boolean[img.getHeight()][img.getWidth()];
        for (int sy = ty - 1; sy < ty + 2 * n * a + 1; sy++) {
            for (int sx = cx - n * a - 1; sx < cx + n * a + 1; sx++) {
                if (sx < 0 || sy < 0 || sx >= img.getWidth() || sy >= img.getHeight()) {
                    continue;
                }
                double px = sx + 0.5;
                double py = sy + 0.5;
                double X = (px - cx) / a;
                double Y = (py - ty) * 2 / a;
                double u = (X + Y) / 2;
                double v = (Y - X) / 2;
                int color = 0;
                if (u >= 0 && v >= 0 && u < n && v < n) {
                    color = top.getRGB((int) u, (int) v);
                } else if (px < cx) {
                    double s = (px - cx + n * a) / a;
                    double t = (py - ty - n * a / 2.0 - s * a / 2) / a;
                    if (s >= 0 && s < n && t >= 0 && t < n) {
                        color = darken(left.getRGB((int) s, (int) t), 0.86F);
                    }
                } else {
                    double s = (px - cx) / a;
                    double t = (py - ty - n * a + s * a / 2) / a;
                    if (s >= 0 && s < n && t >= 0 && t < n) {
                        color = darken(right.getRGB((int) s, (int) t), 0.68F);
                    }
                }
                if ((color >>> 24) != 0) {
                    img.setRGB(sx, sy, color | 0xFF000000);
                    solid[sy][sx] = true;
                }
            }
        }
        outline(img, solid, 0xFF06060C);
    }

    private static int darken(int c, float k) {
        if ((c >>> 24) == 0) {
            return 0;
        }
        return rgb((int) (((c >> 16) & 0xFF) * k), (int) (((c >> 8) & 0xFF) * k), (int) ((c & 0xFF) * k));
    }

    private static void shadow(BufferedImage img, int cx, int cy, int rx, int ry) {
        for (int y = cy - ry; y <= cy + ry; y++) {
            for (int x = cx - rx; x <= cx + rx; x++) {
                float dx = (x - cx) / (float) rx;
                float dy = (y - cy) / (float) ry;
                if (dx * dx + dy * dy <= 1) {
                    img.setRGB(x, y, mix(img.getRGB(x, y), 0xFF000000, 0.45F));
                }
            }
        }
    }

    private static void sprite(BufferedImage img, String file, int x0, int y0, int scale) throws Exception {
        BufferedImage s = ImageIO.read(new File(file));
        boolean[][] solid = new boolean[img.getHeight()][img.getWidth()];
        for (int y = 0; y < 16 * scale; y++) {
            for (int x = 0; x < 16 * scale; x++) {
                int c = s.getRGB(x / scale, y / scale);
                if ((c >>> 24) != 0) {
                    img.setRGB(x0 + x, y0 + y, c);
                    solid[y0 + y][x0 + x] = true;
                }
            }
        }
        outline(img, solid, 0xFF06060C);
    }

    private static void sparkle(BufferedImage img, int x, int y) {
        img.setRGB(x, y, 0xFFFFFFFF);
        img.setRGB(x - 1, y, 0xFFB8F0FF);
        img.setRGB(x + 1, y, 0xFFB8F0FF);
        img.setRGB(x, y - 1, 0xFFB8F0FF);
        img.setRGB(x, y + 1, 0xFFB8F0FF);
    }

    private static void drawStoneText(BufferedImage img, String text, int centerX, int oy, int s, int depth, Random rnd) {
        int W = img.getWidth();
        int H = img.getHeight();
        boolean[][] mask = textMask(text, 2);
        int ox = centerX - mask[0].length * s / 2 - 1;
        boolean[][] face = new boolean[H][W];
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < mask[0].length; x++) {
                if (mask[y][x]) {
                    for (int dy = 0; dy < s; dy++) {
                        for (int dx = 0; dx < s; dx++) {
                            face[oy + y * s + dy][ox + x * s + dx] = true;
                        }
                    }
                }
            }
        }
        boolean[][] solid = new boolean[H][W];
        for (int d = depth; d >= 1; d--) {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    if (face[y][x] && y + d < H && x + d / 2 < W) {
                        int yy = y + d;
                        int xx = x + d / 2;
                        if (!face[yy][xx]) {
                            int g = 0x3A - d * 4 + rnd.nextInt(8);
                            img.setRGB(xx, yy, rgb(g, g, g + 4));
                        }
                        solid[yy][xx] = true;
                    }
                }
            }
        }
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (!face[y][x]) {
                    continue;
                }
                solid[y][x] = true;
                int g = 0x86 + rnd.nextInt(5) * 8 - 16;
                if (rnd.nextInt(9) == 0) {
                    g -= 28;
                }
                boolean topEdge = !face[y - 1][x];
                boolean leftEdge = !face[y][x - 1];
                boolean bottomEdge = !face[y + 1][x];
                if (topEdge || leftEdge) {
                    g += 40;
                } else if (bottomEdge) {
                    g -= 26;
                }
                img.setRGB(x, y, rgb(g, g, g + 3));
            }
        }
        outline(img, solid, 0xFF000000);
    }

    private static void drawYellowText(BufferedImage img, String text, int centerX, int y0) {
        boolean[][] mask = textMask(text, 1);
        int w = mask[0].length;
        int x0 = centerX - w / 2;
        boolean[][] solid = new boolean[img.getHeight()][img.getWidth()];
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < w; x++) {
                if (mask[y][x]) {
                    img.setRGB(x0 + x + 1, y0 + y + 1, 0xFF3F3F00);
                    solid[y0 + y][x0 + x] = true;
                    solid[y0 + y + 1][x0 + x + 1] = true;
                }
            }
        }
        outline(img, solid, 0xFF0A0812);
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < w; x++) {
                if (mask[y][x]) {
                    img.setRGB(x0 + x, y0 + y, y < 3 ? 0xFFFFFF7A : 0xFFFFE040);
                }
            }
        }
        gem(img, x0 - 13, y0 - 1, 0xFF3FA9FF);
        gem(img, x0 + w + 4, y0 - 1, 0xFFD23FE0);
    }

    private static void outline(BufferedImage img, boolean[][] solid, int color) {
        int h = solid.length;
        int w = solid[0].length;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (solid[y][x]) {
                    continue;
                }
                boolean near = false;
                for (int dy = -1; dy <= 1 && !near; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int yy = y + dy;
                        int xx = x + dx;
                        if (yy >= 0 && yy < h && xx >= 0 && xx < w && solid[yy][xx]) {
                            near = true;
                            break;
                        }
                    }
                }
                if (near) {
                    img.setRGB(x, y, color);
                }
            }
        }
    }

    /** 9x11 cut gem in the Apotheosis style. */
    private static void gem(BufferedImage img, int x0, int y0, int color) {
        String[] shape = {
            "..#####..",
            ".#LLLLM#.",
            "#LLWLMMD#",
            "#LWLMMMD#",
            "#LLMMMDD#",
            ".#MMMDD#.",
            "..#MMD#..",
            "...#D#...",
            "....#...." };
        for (int y = 0; y < shape.length; y++) {
            for (int x = 0; x < shape[y].length(); x++) {
                char ch = shape[y].charAt(x);
                int c = switch (ch) {
                    case '#' -> 0xFF101018;
                    case 'W' -> 0xFFFFFFFF;
                    case 'L' -> mix(color, 0xFFFFFFFF, 0.45F);
                    case 'M' -> color;
                    case 'D' -> mix(color, 0xFF000000, 0.45F);
                    default -> 0;
                };
                if (c != 0) {
                    img.setRGB(x0 + x, y0 + y, c);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    private static void preview(String dir, String itemDir) throws Exception {
        String[] names = { "gem_upgrade_pattern", "gem_crush_pattern", "infusion_pattern" };
        int z = 12;
        BufferedImage out = new BufferedImage(names.length * (16 * z + 8), 16 * z, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < names.length; i++) {
            BufferedImage im = ImageIO.read(new File(itemDir + "/" + names[i] + ".png"));
            for (int y = 0; y < 16 * z; y++) {
                for (int x = 0; x < 16 * z; x++) {
                    int c = im.getRGB(x / z, y / z);
                    if ((c >>> 24) == 0) {
                        c = ((x / z + y / z) % 2 == 0) ? 0xFF8B8B8B : 0xFF7B7B7B;
                    }
                    out.setRGB(i * (16 * z + 8) + x, y, c);
                }
            }
        }
        ImageIO.write(out, "png", new File(dir + "/patterns_preview.png"));
    }
}
