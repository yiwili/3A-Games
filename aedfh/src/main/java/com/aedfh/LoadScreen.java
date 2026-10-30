package com.aedfh;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;

import java.awt.Color;

/**
 * 启动加载动画：只挂在 guiNode 上的一个覆盖层，旋转/呼吸/进度全部在渲染线程做。
 * 烘焙在后台线程跑，这里每帧只读一个 0..1 的进度值，所以窗口一打开就能动。
 */
final class LoadScreen {

    private static final float W = 1280f;
    private static final float H = 720f;

    private static final ColorRGBA GOLD = new ColorRGBA(0.86f, 0.72f, 0.42f, 1f);

    private static final int RING_N = 12;
    private static final float RING_R = 62f;
    private static final float RING_Y = 486f;
    private static final float BAR_W = 432f;
    private static final float BAR_H = 2.2f;
    private static final float BAR_Y = 274f;

    private final AssetManager am;
    private final Node gui;
    private final com.jme3.renderer.Camera camera;
    private final Node root = new Node("loadScreen");
    private final Node design = new Node("loadDesign");
    private final Node ring = new Node("loadRing");
    private final Mesh unit = unitQuad();
    private final Texture2D white;
    private final Texture2D diamond;
    private final Texture2D puff;
    private final Geometry barFill;
    private final Label status;
    private final Label percent;
    private final Geometry emblem;

    private float t;
    private float shown;
    private int shownPct = -1;
    private int hudW;
    private int hudH;
    private boolean closed;

    LoadScreen(AssetManager am, Node guiNode, com.jme3.renderer.Camera camera) {
        this.am = am;
        this.gui = guiNode;
        this.camera = camera;
        white = UiText.solid(0xFFFFFFFF);
        diamond = Tex.diamond();
        puff = Tex.puff();

        gui.attachChild(root);
        root.attachChild(design);

        design.attachChild(quad(white, 0f, 0f, W, H,
                new ColorRGBA(0.008f, 0.009f, 0.013f, 1f), 1f));
        design.attachChild(quad(puff, W * 0.5f - 300f, RING_Y - 300f, 600f, 600f,
                new ColorRGBA(GOLD.r, GOLD.g, GOLD.b, 0.055f), 1.1f));
        design.attachChild(quad(white, W * 0.5f - 220f, 560f, 440f, 0.8f,
                new ColorRGBA(0.80f, 0.66f, 0.38f, 0.20f), 1.2f));
        design.attachChild(quad(white, W * 0.5f - 220f, 120f, 440f, 0.8f,
                new ColorRGBA(0.80f, 0.66f, 0.38f, 0.14f), 1.2f));

        design.attachChild(ring);
        ring.setLocalTranslation(W * 0.5f, RING_Y, 2f);
        for (int i = 0; i < RING_N; i++) {
            float a = FastMath.TWO_PI * i / RING_N;
            float x = FastMath.cos(a) * RING_R - 14f;
            float y = FastMath.sin(a) * RING_R - 14f;
            float alpha = 0.15f + 0.85f * (i / (float) (RING_N - 1));
            quad(ring, diamond, x, y, 28f, 28f, new ColorRGBA(
                    GOLD.r, GOLD.g, GOLD.b, alpha), 2f + i * 0.01f);
        }
        emblem = quad(ring, diamond, -19f, -19f, 38f, 38f, new ColorRGBA(
                GOLD.r, GOLD.g, GOLD.b, 0.95f), 2.5f);

        status = new Label("正在准备宁姆格福…", 14, new Color(206, 194, 168, 235),
                false, W * 0.5f, 306f, 3f, true);
        percent = new Label("0%", 11, new Color(178, 164, 136, 200),
                false, W * 0.5f + BAR_W * 0.5f + 26f, BAR_Y - 7f, 3f, false);
        new Label("艾尔登法猴 · 宁姆格福", 34, new Color(230, 218, 188, 255),
                false, W * 0.5f, 372f, 3f, true);
        new Label("E L D E N   M O N K E Y", 12, new Color(165, 151, 122, 190),
                true, W * 0.5f, 348f, 3f, true);

        design.attachChild(quad(white, W * 0.5f - BAR_W * 0.5f, BAR_Y, BAR_W, BAR_H,
                new ColorRGBA(0.70f, 0.58f, 0.34f, 0.30f), 3f));
        barFill = quad(white, W * 0.5f - BAR_W * 0.5f, BAR_Y, BAR_W, BAR_H,
                new ColorRGBA(GOLD.r, GOLD.g, GOLD.b, 0.92f), 3.1f);
        barFill.setLocalScale(0.001f, BAR_H, 1f);

        new Label("褪色者，雾气正在散开", 11, new Color(140, 132, 114, 170),
                false, W * 0.5f, 238f, 3f, true);
        new Label("程序化场景 · 原创美术 · 1:1 尺度", 10, new Color(122, 115, 100, 140),
                false, 40f, 36f, 3f, false);
        new Label("v1.0 · LIMGRAVE", 10, new Color(122, 115, 100, 140),
                true, W - 150f, 36f, 3f, true);

        resize();
    }

    /** @param progress 0..1 总进度；@param stage 当前阶段文案（相同文案不会重绘） */
    void update(float tpf, float progress, String stage) {
        if (closed) {
            return;
        }
        resize();
        t += Math.min(tpf, 0.1f);
        shown += (MathX.clamp(progress, 0f, 1f) - shown) * Math.min(1f, tpf * 7f);
        shown = MathX.clamp(shown, 0f, 1f);

        ring.setLocalRotation(new com.jme3.math.Quaternion().fromAngles(0f, 0f, t * 1.15f));
        float pulse = 1f + FastMath.sin(t * 3.1f) * 0.04f;
        ring.setLocalScale(pulse, pulse, 1f);
        float glow = 0.55f + 0.35f * FastMath.sin(t * 2.3f);
        emblem.getMaterial().setColor("Color", new ColorRGBA(GOLD.r, GOLD.g, GOLD.b, glow));

        barFill.setLocalScale(Math.max(0.001f, BAR_W * shown), BAR_H, 1f);
        int pct = Math.round(shown * 100f);
        if (pct != shownPct) {
            shownPct = pct;
            percent.setText(pct + "%");
        }
        status.setText(stage);
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        gui.detachChild(root);
    }

    private void resize() {
        int w = Math.max(1, camera.getWidth());
        int h = Math.max(1, camera.getHeight());
        if (w == hudW && h == hudH) {
            return;
        }
        hudW = w;
        hudH = h;
        float scale = Math.min(w / W, h / H);
        design.setLocalScale(scale);
        design.setLocalTranslation((w - W * scale) * 0.5f, (h - H * scale) * 0.5f, 0f);
    }

    private final class Label {
        final Geometry geo;
        final int size;
        final Color color;
        final boolean serif;
        final float x;
        final float y;
        final boolean centered;
        String text = "";

        Label(String value, int size, Color color, boolean serif,
              float x, float y, float z, boolean centered) {
            this.size = size;
            this.color = color;
            this.serif = serif;
            this.x = x;
            this.y = y;
            this.centered = centered;
            geo = quad(design, white, x, y, 1f, 1f, ColorRGBA.White, z);
            setText(value);
        }

        void setText(String value) {
            if (value.equals(text)) {
                return;
            }
            text = value;
            UiText.Made made = UiText.make(value.isEmpty() ? " " : value, size, color, serif);
            geo.getMaterial().setTexture("ColorMap", made.texture());
            geo.setLocalScale(made.width(), made.height(), 1f);
            geo.setLocalTranslation(centered ? x - made.width() * 0.5f : x, y,
                    geo.getLocalTranslation().z);
        }
    }

    private Geometry quad(Texture2D tex, float x, float y, float w, float h,
                          ColorRGBA color, float z) {
        return quad(design, tex, x, y, w, h, color, z);
    }

    private Geometry quad(Node parent, Texture2D tex, float x, float y, float w, float h,
                          ColorRGBA color, float z) {
        Geometry g = new Geometry("loadQuad", unit);
        Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setTexture("ColorMap", tex);
        m.setColor("Color", color);
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        m.getAdditionalRenderState().setDepthTest(false);
        m.getAdditionalRenderState().setDepthWrite(false);
        g.setMaterial(m);
        g.setQueueBucket(RenderQueue.Bucket.Gui);
        g.setShadowMode(RenderQueue.ShadowMode.Off);
        g.setCullHint(Spatial.CullHint.Never);
        g.setLocalTranslation(x, y, z);
        g.setLocalScale(w, h, 1f);
        parent.attachChild(g);
        return g;
    }

    private static Mesh unitQuad() {
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(
                0f, 0f, 0f,
                1f, 0f, 0f,
                1f, 1f, 0f,
                0f, 1f, 0f));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(
                0f, 0f,
                1f, 0f,
                1f, 1f,
                0f, 1f));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createShortBuffer(
                new short[]{0, 1, 2, 0, 2, 3}));
        mesh.setStatic();
        mesh.updateBound();
        return mesh;
    }
}
