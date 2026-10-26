package com.aedfh;

import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class UiText {

    record Made(Texture2D texture, int width, int height) {
    }

    private UiText() {
    }

    static Made make(String text, int size, Color color, boolean serif) {
        Font font = pickFont(serif, size, text);

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(font);
        FontMetrics fm = pg.getFontMetrics();
        int textWidth = Math.max(1, fm.stringWidth(text));
        int textHeight = Math.max(1, fm.getHeight());
        pg.dispose();

        int pad = 6;
        BufferedImage img = new BufferedImage(textWidth + pad * 2, textHeight + pad * 2,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, pad, pad + fm.getAscent());
        g.dispose();

        Texture2D tex = new Texture2D(new AWTLoader().load(img, true));
        tex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        tex.setMagFilter(Texture.MagFilter.Bilinear);
        return new Made(tex, img.getWidth(), img.getHeight());
    }

    static Texture2D solid(int argb) {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(argb, true));
        g.fillRect(0, 0, 2, 2);
        g.dispose();
        Texture2D tex = new Texture2D(new AWTLoader().load(img, true));
        tex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        tex.setMagFilter(Texture.MagFilter.Nearest);
        return tex;
    }

    private static Font pickFont(boolean serif, int size, String text) {
        String family = serif ? "Georgia" : "Microsoft YaHei";
        if (hasCjk(text)) {
            family = serif ? "SimSun" : "Microsoft YaHei";
        }
        Font font = new Font(family, Font.BOLD, size);
        if ("Dialog".equalsIgnoreCase(font.getFamily())) {
            font = new Font(serif ? Font.SERIF : Font.SANS_SERIF, Font.BOLD, size);
        }
        return font;
    }

    private static boolean hasCjk(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= 0x2E80) {
                return true;
            }
        }
        return false;
    }
}
