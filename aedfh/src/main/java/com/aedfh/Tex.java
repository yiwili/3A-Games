package com.aedfh;

import com.jme3.asset.AssetManager;
import com.jme3.math.FastMath;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Random;

import javax.imageio.ImageIO;

final class Tex {

    private Tex() {
    }

    static Texture2D stone(long seed) {
        int size = 512;
        int tiles = 4;
        int cell = size / tiles;
        Random rnd = new Random(seed);

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int ty = 0; ty < tiles; ty++) {
            for (int tx = 0; tx < tiles; tx++) {
                int tone = 62 + rnd.nextInt(26);
                g.setColor(new Color(clamp255(tone + 10), clamp255(tone + 4), clamp255(tone - 9)));
                g.fillRect(tx * cell, ty * cell, cell, cell);

                for (int k = 0; k < 26; k++) {
                    int x = tx * cell + rnd.nextInt(cell);
                    int y = ty * cell + rnd.nextInt(cell);
                    int w = 6 + rnd.nextInt(cell / 2);
                    int h = 4 + rnd.nextInt(cell / 3);
                    int shade = rnd.nextInt(26) - 13;
                    int alpha = 14 + rnd.nextInt(26);
                    g.setColor(new Color(clamp255(tone + shade), clamp255(tone + shade - 4),
                            clamp255(tone + shade - 12), alpha));
                    g.fillOval(x, y, w, h);
                }
            }
        }

        g.setColor(new Color(28, 25, 22, 235));
        g.setStroke(new BasicStroke(5f));
        for (int i = 0; i <= tiles; i++) {
            g.drawLine(i * cell, 0, i * cell, size);
            g.drawLine(0, i * cell, size, i * cell);
        }

        g.setColor(new Color(24, 22, 19, 210));
        g.setStroke(new BasicStroke(1.6f));
        for (int c = 0; c < 9; c++) {
            int x = rnd.nextInt(size);
            int y = rnd.nextInt(size);
            g.drawLine(x, y, x + rnd.nextInt(120) - 60, y + rnd.nextInt(120) - 60);
            g.drawLine(x, y, x + rnd.nextInt(50) - 25, y + rnd.nextInt(50) - 25);
        }

        for (int i = 0; i < size * 18; i++) {
            int v = rnd.nextInt(60) + 180;
            int alpha = 6 + rnd.nextInt(16);
            g.setColor(new Color(v, v - 6, v - 16, alpha));
            g.fillRect(rnd.nextInt(size), rnd.nextInt(size), 1 + rnd.nextInt(2), 1);
        }

        g.dispose();
        return texture(img, true);
    }

    static Texture2D fur(long seed) {
        int size = 256;
        Random rnd = new Random(seed);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(206, 204, 199));
        g.fillRect(0, 0, size, size);

        for (int i = 0; i < 150; i++) {
            int x = rnd.nextInt(size);
            int y = rnd.nextInt(size);
            int w = 30 + rnd.nextInt(70);
            int h = 20 + rnd.nextInt(50);
            int tone = 130 + rnd.nextInt(100);
            g.setColor(new Color(tone, tone, tone - 4, 44 + rnd.nextInt(50)));
            g.fillOval(x, y, w, h);
        }
        for (int i = 0; i < 260; i++) {
            int x = rnd.nextInt(size);
            int y = rnd.nextInt(size);
            int w = 10 + rnd.nextInt(30);
            int h = 8 + rnd.nextInt(24);
            int tone = 140 + rnd.nextInt(90);
            g.setColor(new Color(tone, tone, tone - 3, 40 + rnd.nextInt(50)));
            g.fillOval(x, y, w, h);
        }
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 2600; i++) {
            int x = rnd.nextInt(size);
            int y = rnd.nextInt(size);
            float a = (float) (Math.PI * 0.5 + (rnd.nextFloat() - 0.5f) * 0.9f);
            int len = 5 + rnd.nextInt(18);
            int x2 = x + (int) (Math.cos(a) * len);
            int y2 = y + (int) (Math.sin(a) * len);
            int tone = 105 + rnd.nextInt(145);
            g.setColor(new Color(tone, tone, tone, 55 + rnd.nextInt(85)));
            g.drawLine(x, y, x2, y2);
        }
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < 260; i++) {
            int x = rnd.nextInt(size);
            int y = rnd.nextInt(size);
            g.setColor(new Color(238, 238, 232, 40 + rnd.nextInt(40)));
            g.drawLine(x, y, x + rnd.nextInt(3) - 1, y + 4 + rnd.nextInt(10));
        }
        g.dispose();
        return texture(img, true);
    }

    static Texture2D hide(long seed) {
        int size = 256;
        Random rnd = new Random(seed);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int n = 196 + rnd.nextInt(46);
                img.setRGB(x, y, (n << 16) | (n << 8) | (n - 4));
            }
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < 520; i++) {
            int x = rnd.nextInt(size);
            int y = rnd.nextInt(size);
            int len = 3 + rnd.nextInt(14);
            g.setColor(new Color(120 + rnd.nextInt(50), 115 + rnd.nextInt(45),
                    108 + rnd.nextInt(40), 45 + rnd.nextInt(55)));
            g.drawLine(x, y, x + (rnd.nextInt(7) - 3), y + len);
        }
        for (int i = 0; i < 500; i++) {
            int x = rnd.nextInt(size);
            int y = rnd.nextInt(size);
            g.setColor(new Color(240, 236, 228, 22 + rnd.nextInt(28)));
            g.fillOval(x, y, 1 + rnd.nextInt(3), 1 + rnd.nextInt(3));
        }
        g.dispose();
        return texture(img, true);
    }

    static Texture2D sky() {
        int w = 16;
        int h = 1024;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[][] stops = {
                {0, 20, 36, 68},
                {180, 44, 72, 114},
                {330, 78, 112, 156},
                {420, 132, 152, 178},
                {470, 196, 176, 142},
                {512, 226, 188, 138},
                {554, 198, 174, 140},
                {598, 118, 148, 184},
                {690, 62, 98, 146},
                {844, 34, 60, 104},
                {1024, 20, 36, 68}
        };
        Graphics2D g = img.createGraphics();
        for (int y = 0; y < h; y++) {
            int[] c = sample(stops, y);
            g.setColor(new Color(c[0], c[1], c[2]));
            g.drawLine(0, y, w, y);
        }
        g.dispose();
        return texture(img, false);
    }

    /** Seamless ground detail: fine grass grain, used tiled under vertex colour. */
    static Texture2D grass() {
        int size = 256;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float u = x / (float) size;
                float v = y / (float) size;
                float n = wrapNoise(u * 8f, v * 8f, 8) * 0.42f
                        + wrapNoise(u * 16f + 3.1f, v * 16f + 7.7f, 16) * 0.33f
                        + wrapNoise(u * 32f + 9.4f, v * 32f + 1.2f, 32) * 0.25f;
                float blades = wrapNoise(u * 96f, v * 22f, 96) - 0.5f;
                float lum = 0.74f + (n - 0.5f) * 0.34f + blades * 0.16f;
                int r = clamp255((int) (lum * 0.94f * 255f));
                int g = clamp255((int) (lum * 1.02f * 255f));
                int b = clamp255((int) (lum * 0.80f * 255f));
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        Graphics2D g = img.createGraphics();
        Random rnd = new Random(91);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < 520; i++) {
            int x = rnd.nextInt(size);
            int y = rnd.nextInt(size);
            int h = 3 + rnd.nextInt(7);
            int tone = 140 + rnd.nextInt(90);
            g.setColor(new Color(tone, tone + 12, tone - 30, 40 + rnd.nextInt(60)));
            g.drawLine(x, y, x + rnd.nextInt(3) - 1, y + h);
            if (x < 3) {
                g.drawLine(x + size, y, x + size + rnd.nextInt(3) - 1, y + h);
            }
        }
        g.dispose();
        Texture2D tex = texture(img, true);
        tex.setMinFilter(Texture.MinFilter.Trilinear);
        tex.setAnisotropicFilter(8);
        return tex;
    }

    /** Seamless staggered ashlar courses for Stormveil masonry (bright, tinted by material). */
    static Texture2D masonry() {
        int size = 512;
        int rows = 8;
        int rowH = size / rows;
        int cols = 4;
        int colW = size / cols;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(91);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float u = x / (float) size;
                float v = y / (float) size;
                int row = y / rowH;
                int shifted = (x + (row % 2) * colW / 2) % size;
                int col = shifted / colW;
                Random block = new Random(row * 977L + col * 131L + 7);
                float tone = 0.76f + block.nextFloat() * 0.16f;
                float n = wrapNoise(u * 10f + row, v * 10f, 10) * 0.5f
                        + wrapNoise(u * 44f, v * 44f + 3f, 44) * 0.5f;
                float lum = tone + (n - 0.5f) * 0.16f;
                // chamfered edges: lift the top edge of each block, sink the bottom
                int ly = y % rowH;
                if (ly < 2) {
                    lum += 0.05f;
                } else if (ly > rowH - 3) {
                    lum -= 0.07f;
                }
                lum += (block.nextFloat() - 0.5f) * 0.03f;
                img.setRGB(x, y, (clamp255((int) (lum * 214f)) << 16)
                        | (clamp255((int) (lum * 206f)) << 8)
                        | clamp255((int) (lum * 186f)));
            }
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // mortar joints, drawn after the blocks so they read as recessed
        g.setStroke(new BasicStroke(3.4f));
        for (int r = 0; r <= rows; r++) {
            g.setColor(new Color(96, 92, 82, 205));
            g.drawLine(0, r * rowH, size, r * rowH);
        }
        g.setStroke(new BasicStroke(2.6f));
        for (int r = 0; r < rows; r++) {
            int off = (r % 2) * colW / 2;
            for (int c = 0; c <= cols; c++) {
                int x = (c * colW - off + size) % size;
                g.setColor(new Color(96, 92, 82, 190));
                g.drawLine(x, r * rowH + 3, x, (r + 1) * rowH - 3);
            }
        }
        // weather streaks and moss
        for (int i = 0; i < 70; i++) {
            int x = rnd.nextInt(size);
            int y = rnd.nextInt(size);
            int h = 12 + rnd.nextInt(70);
            g.setColor(new Color(58, 62, 50, 10 + rnd.nextInt(26)));
            g.fillRect(x, y, 1 + rnd.nextInt(5), h);
        }
        for (int i = 0; i < 26; i++) {
            int x = rnd.nextInt(size);
            int y = rnd.nextInt(size);
            g.setColor(new Color(74, 88, 58, 16 + rnd.nextInt(22)));
            g.fillOval(x, y, 8 + rnd.nextInt(30), 6 + rnd.nextInt(18));
        }
        g.dispose();
        Texture2D tex = texture(img, true);
        tex.setMinFilter(Texture.MinFilter.Trilinear);
        tex.setAnisotropicFilter(8);
        return tex;
    }

    /** Seamless dirt for the road ribbons. */
    static Texture2D dirt() {
        int size = 256;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(37);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float u = x / (float) size;
                float v = y / (float) size;
                float n = wrapNoise(u * 6f, v * 6f, 6) * 0.55f
                        + wrapNoise(u * 24f + 4f, v * 24f + 2f, 24) * 0.45f;
                float lum = 0.68f + (n - 0.5f) * 0.30f;
                img.setRGB(x, y, (clamp255((int) (lum * 235f)) << 16)
                        | (clamp255((int) (lum * 214f)) << 8)
                        | clamp255((int) (lum * 168f)));
            }
        }
        Graphics2D g = img.createGraphics();
        for (int i = 0; i < 900; i++) {
            int x = rnd.nextInt(size);
            int y = rnd.nextInt(size);
            int w = 1 + rnd.nextInt(3);
            int tone = 90 + rnd.nextInt(100);
            g.setColor(new Color(tone + 18, tone + 6, tone - 24, 120 + rnd.nextInt(90)));
            g.fillOval(x, y, w, 1 + rnd.nextInt(2));
        }
        g.dispose();
        Texture2D tex = texture(img, true);
        tex.setMinFilter(Texture.MinFilter.Trilinear);
        tex.setAnisotropicFilter(8);
        return tex;
    }

    /** Seamless lake surface ripples; tinted per hour by the water material. */
    static Texture2D water() {
        int size = 256;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float u = x / (float) size;
                float v = y / (float) size;
                float warp = wrapNoise(u * 5f, v * 5f, 5) * 2.4f;
                float wave = FastMath.sin((v * 9f + warp) * FastMath.TWO_PI) * 0.5f + 0.5f;
                float fine = wrapNoise(u * 40f + 2f, v * 28f + 5f, 40) - 0.5f;
                float lum = 0.80f + wave * 0.10f + fine * 0.13f;
                img.setRGB(x, y, (clamp255((int) (lum * 236f)) << 16)
                        | (clamp255((int) (lum * 248f)) << 8)
                        | clamp255((int) (lum * 255f)));
            }
        }
        Graphics2D g = img.createGraphics();
        Random rnd = new Random(64);
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 160; i++) {
            int x = rnd.nextInt(size);
            int y = rnd.nextInt(size);
            int w = 6 + rnd.nextInt(22);
            g.setColor(new Color(240, 248, 255, 40 + rnd.nextInt(70)));
            g.drawLine(x, y, x + w, y + rnd.nextInt(3) - 1);
        }
        g.dispose();
        Texture2D tex = texture(img, true);
        tex.setMinFilter(Texture.MinFilter.Trilinear);
        tex.setAnisotropicFilter(8);
        return tex;
    }

    private static float wrapNoise(float x, float y, int period) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float xf = x - xi;
        float yf = y - yi;
        float u = xf * xf * (3f - 2f * xf);
        float v = yf * yf * (3f - 2f * yf);
        int x0 = Math.floorMod(xi, period);
        int y0 = Math.floorMod(yi, period);
        int x1 = Math.floorMod(xi + 1, period);
        int y1 = Math.floorMod(yi + 1, period);
        float a = hash2(x0, y0);
        float b = hash2(x1, y0);
        float c = hash2(x0, y1);
        float d = hash2(x1, y1);
        return MathX.lerp(MathX.lerp(a, b, u), MathX.lerp(c, d, u), v);
    }

    private static float hash2(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= h >> 16;
        return (h & 0x7fffffff) / (float) 0x7fffffff;
    }

    static Texture2D radial() {
        int size = 256;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        float half = size * 0.5f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x - half) / half;
                float dy = (y - half) / half;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float t = MathX.smooth(MathX.clamp((d - 0.42f) / 0.62f, 0f, 1f));
                int alpha = (int) (t * 235f);
                img.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        return texture(img, false);
    }

    static Texture2D puff() {
        int size = 128;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        float half = size * 0.5f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f - half) / half;
                float dy = (y + 0.5f - half) / half;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float t = MathX.smooth(MathX.clamp(1f - d, 0f, 1f));
                int alpha = (int) (t * 255f);
                img.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        return texture(img, false);
    }

    static Texture2D diamond() {
        int size = 40;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(255, 255, 255, 235));
        g.setStroke(new BasicStroke(2.4f));
        int c = size / 2;
        int r = 13;
        g.drawPolygon(new int[]{c, c + r, c, c - r}, new int[]{c - r, c, c + r, c}, 4);
        g.setColor(new Color(255, 232, 180, 235));
        g.fillOval(c - 2, c - 2, 5, 5);
        g.dispose();
        return texture(img, false);
    }

    private static int[] sample(int[][] stops, int y) {
        for (int i = 0; i < stops.length - 1; i++) {
            if (y <= stops[i + 1][0]) {
                int[] a = stops[i];
                int[] b = stops[i + 1];
                float t = (y - a[0]) / (float) (b[0] - a[0]);
                return new int[]{
                        (int) MathX.lerp(a[1], b[1], t),
                        (int) MathX.lerp(a[2], b[2], t),
                        (int) MathX.lerp(a[3], b[3], t)
                };
            }
        }
        int[] last = stops[stops.length - 1];
        return new int[]{last[1], last[2], last[3]};
    }

    static Texture2D image(BufferedImage img, boolean repeat) {
        return texture(img, repeat);
    }

    static Texture2D loaded(AssetManager am, String path, boolean repeat) {
        try {
            Texture2D tex = (Texture2D) am.loadTexture(path);
            tex.setWrap(repeat ? Texture.WrapMode.Repeat : Texture.WrapMode.EdgeClamp);
            tex.setMinFilter(Texture.MinFilter.Trilinear);
            tex.setMagFilter(Texture.MagFilter.Bilinear);
            tex.setAnisotropicFilter(8);
            return tex;
        } catch (Exception ex) {
            System.out.println("[Tex] missing " + path + " -> procedural fallback");
            return null;
        }
    }

    /**
     * 把 PolyHaven 原图缩放 + 按通道染色后做成带 mipmap 的地表贴图。
     * 地形四层共用一个 AlphaMap 混合，没法在材质里调色，只能在下发贴图时把色调掰回来：
     * 原图偏枯黄，直接铺出来整张地图像沙漠。
     */
    static Texture2D tinted(String path, int size, float tr, float tg, float tb) {
        return tinted(path, size, tr, tg, tb, 0f);
    }

    /**
     * @param desat 0..1，先按亮度去饱和再做染色；岩石层靠它把暖褐的砌石压成灰石。
     */
    static Texture2D tinted(String path, int size, float tr, float tg, float tb, float desat) {
        try (java.io.InputStream in = Tex.class.getResourceAsStream("/" + path)) {
            if (in == null) {
                System.out.println("[Tex] missing " + path + " -> procedural fallback");
                return null;
            }
            BufferedImage src = ImageIO.read(in);
            BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, size, size, null);
            g.dispose();
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int c = out.getRGB(x, y);
                    float r = (c >> 16) & 0xFF;
                    float gg = (c >> 8) & 0xFF;
                    float b = c & 0xFF;
                    if (desat > 0f) {
                        float lum = 0.30f * r + 0.59f * gg + 0.11f * b;
                        r += (lum - r) * desat;
                        gg += (lum - gg) * desat;
                        b += (lum - b) * desat;
                    }
                    int rr = clamp255(Math.round(r * tr));
                    int g2 = clamp255(Math.round(gg * tg));
                    int b2 = clamp255(Math.round(b * tb));
                    out.setRGB(x, y, (rr << 16) | (g2 << 8) | b2);
                }
            }
            com.jme3.texture.Image img = new AWTLoader().load(out, true);
            // 默认帧缓冲是 sRGB，jME 只对标记成 sRGB 的贴图做线性化；AWTLoader 出来的是 Linear，
            // 不标就会少一次解码，画面整体发白发灰。
            img.setColorSpace(com.jme3.texture.image.ColorSpace.sRGB);
            com.jme3.util.MipMapGenerator.generateMipMaps(img);
            Texture2D tex = new Texture2D(img);
            tex.setWrap(Texture.WrapMode.Repeat);
            tex.setMinFilter(Texture.MinFilter.Trilinear);
            tex.setMagFilter(Texture.MagFilter.Bilinear);
            tex.setAnisotropicFilter(8);
            return tex;
        } catch (Exception ex) {
            System.out.println("[Tex] tinted " + path + " failed: " + ex.getMessage());
            return null;
        }
    }

    private static Texture2D texture(BufferedImage img, boolean repeat) {
        Texture2D tex = new Texture2D(new AWTLoader().load(img, true));
        tex.setWrap(repeat ? Texture.WrapMode.Repeat : Texture.WrapMode.EdgeClamp);
        tex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        tex.setMagFilter(Texture.MagFilter.Bilinear);
        return tex;
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
