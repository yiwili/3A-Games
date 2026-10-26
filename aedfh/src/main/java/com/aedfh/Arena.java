package com.aedfh;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import com.jme3.util.BufferUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class Arena {

    static final float PLATFORM_RADIUS = 19f;
    static final float PLAY_RADIUS = 16.2f;
    static final Vector3f PLAYER_SPAWN = new Vector3f(0f, 0f, -6.8f);
    static final Vector3f BOSS_SPAWN = new Vector3f(0f, 0f, 6.2f);

    private static final class Flame {
        Geometry outer;
        Geometry inner;
        float baseY;
        float phase;
    }

    private final Node root = new Node("arena");
    private final List<Flame> flames = new ArrayList<>();
    private final Random rnd = new Random(23);

    private final Material floorMat;
    private final Material stoneMat;
    private final Material darkStoneMat;
    private final Material goldMat;
    private final Material glowMat;

    Arena(AssetManager am, Node parent) {
        parent.attachChild(root);

        floorMat = Gfx.lit(am, new ColorRGBA(0.88f, 0.84f, 0.76f, 1f),
                new ColorRGBA(0.30f, 0.28f, 0.25f, 1f),
                new ColorRGBA(0.16f, 0.14f, 0.12f, 1f), 20f);
        Gfx.mapped(floorMat, am, "stone_tiles", 1.0f);

        stoneMat = Gfx.lit(am, new ColorRGBA(0.66f, 0.62f, 0.55f, 1f),
                new ColorRGBA(0.26f, 0.25f, 0.23f, 1f),
                new ColorRGBA(0.12f, 0.11f, 0.1f, 1f), 16f);
        Gfx.mapped(stoneMat, am, "stone_tiles", 0.9f);

        darkStoneMat = Gfx.lit(am, new ColorRGBA(0.40f, 0.37f, 0.33f, 1f),
                new ColorRGBA(0.19f, 0.18f, 0.17f, 1f),
                new ColorRGBA(0.08f, 0.08f, 0.07f, 1f), 12f);
        Gfx.mapped(darkStoneMat, am, "rock_wall", 0.9f);

        goldMat = Gfx.lit(am, new ColorRGBA(0.72f, 0.55f, 0.22f, 1f),
                new ColorRGBA(0.3f, 0.23f, 0.09f, 1f),
                new ColorRGBA(1f, 0.86f, 0.5f, 1f), 64f);

        glowMat = Gfx.unshaded(am, new ColorRGBA(0.95f, 0.62f, 0.2f, 1f));

        buildSky(am);
        buildPlatform(am);
        buildRingsAndSigil();
        buildArch();
        buildPillars();
        buildBraziers(am);
        buildRubble();
        buildDistantWorld(am);
        Gfx.tangents(root);
    }

    private void buildSky(AssetManager am) {
        Geometry sky = new Geometry("sky", new Sphere(24, 48, 900f, true, true));
        Material mat = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", Tex.sky());
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Off);
        sky.setMaterial(mat);
        sky.setQueueBucket(RenderQueue.Bucket.Sky);
        sky.setShadowMode(RenderQueue.ShadowMode.Off);
        sky.setCullHint(Spatial.CullHint.Never);
        root.attachChild(sky);
    }

    private void buildPlatform(AssetManager am) {
        Geometry floor = new Geometry("floor", new Cylinder(2, 96, PLATFORM_RADIUS, 1.4f, true));
        floor.rotate(-FastMath.HALF_PI, 0f, 0f);
        floor.setMaterial(floorMat);
        floor.setLocalTranslation(0f, -0.7f, 0f);
        floor.setShadowMode(RenderQueue.ShadowMode.Receive);
        floor.getMesh().scaleTextureCoordinates(new Vector2f(7f, 7f));
        root.attachChild(floor);

        Geometry lip = new Geometry("lip", new Cylinder(2, 96, PLATFORM_RADIUS + 0.55f, 0.5f, true));
        lip.rotate(-FastMath.HALF_PI, 0f, 0f);
        lip.setMaterial(darkStoneMat);
        lip.setLocalTranslation(0f, -1.35f, 0f);
        lip.setShadowMode(RenderQueue.ShadowMode.Receive);
        root.attachChild(lip);

        Material cliffMat = Gfx.lit(am, new ColorRGBA(0.42f, 0.39f, 0.35f, 1f),
                new ColorRGBA(0.14f, 0.14f, 0.13f, 1f), new ColorRGBA(0.05f, 0.05f, 0.05f, 1f), 8f);
        Gfx.mapped(cliffMat, am, "rock_wall", 1.1f);
        Geometry cliff = Gfx.taperedCylinder("cliff", PLATFORM_RADIUS + 0.6f, PLATFORM_RADIUS + 5f,
                16f, cliffMat, root, 0f, -9.6f, 0f);
        cliff.setShadowMode(RenderQueue.ShadowMode.Off);

        Geometry abyss = new Geometry("abyss", new Box(900f, 0.5f, 900f));
        abyss.setMaterial(Gfx.unshaded(am, new ColorRGBA(0.045f, 0.05f, 0.06f, 1f)));
        abyss.setLocalTranslation(0f, -23f, 0f);
        abyss.setShadowMode(RenderQueue.ShadowMode.Off);
        root.attachChild(abyss);
    }

    private void buildRingsAndSigil() {
        ring(PLATFORM_RADIUS - 0.45f, 0.075f, 0.055f, goldMat);
        ring(12.1f, 0.05f, 0.05f, darkStoneMat);
        ring(6.2f, 0.06f, 0.05f, goldMat);
        ring(2.7f, 0.07f, 0.06f, goldMat);

        for (int i = 0; i < 8; i++) {
            float a = i * FastMath.TWO_PI / 8f;
            Geometry spoke = new Geometry("spoke", new Box(0.05f, 0.012f, 4.7f));
            spoke.setMaterial(goldMat);
            spoke.setLocalTranslation(FastMath.sin(a) * 9.15f, 0.045f, FastMath.cos(a) * 9.15f);
            spoke.rotate(0f, a, 0f);
            spoke.setShadowMode(RenderQueue.ShadowMode.Off);
            root.attachChild(spoke);
        }

        for (int i = 0; i < 6; i++) {
            float a = 0.7f + i * FastMath.TWO_PI / 6f;
            Geometry seam = new Geometry("seam", new Box(0.028f, 0.012f, 1.4f + rnd.nextFloat() * 1.8f));
            seam.setMaterial(glowMat);
            seam.setLocalTranslation(FastMath.sin(a) * (7.5f + rnd.nextFloat() * 6f), 0.035f,
                    FastMath.cos(a) * (7.5f + rnd.nextFloat() * 6f));
            seam.rotate(0f, a + (rnd.nextFloat() - 0.5f) * 0.7f, 0f);
            seam.setShadowMode(RenderQueue.ShadowMode.Off);
            root.attachChild(seam);
        }
    }

    private void ring(float radius, float tube, float y, Material mat) {
        Geometry g = Gfx.torus("ring", radius, tube, mat, root, 0f, y, 0f);
        g.setShadowMode(RenderQueue.ShadowMode.Receive);
    }

    private void buildPillars() {
        float[] angles = {0.42f, 1.15f, 1.92f, 2.62f, 3.68f, 4.38f, 5.08f, 5.82f};
        for (int i = 0; i < angles.length; i++) {
            float a = angles[i];
            float x = FastMath.sin(a) * 17.7f;
            float z = FastMath.cos(a) * 17.7f;
            float h = 2.2f + rnd.nextFloat() * 4.2f;
            boolean broken = i % 3 != 1;
            pillar(x, z, h, broken, a);
        }
    }

    private void pillar(float x, float z, float height, boolean broken, float yaw) {
        Node n = new Node("pillar");
        n.setLocalTranslation(x, 0f, z);
        n.rotate(0f, yaw + (rnd.nextFloat() - 0.5f) * 0.3f, 0f);

        Gfx.box("base", 0.92f, 0.22f, 0.92f, darkStoneMat, n, 0f, 0.22f, 0f);
        Gfx.box("plinth", 0.74f, 0.26f, 0.74f, stoneMat, n, 0f, 0.68f, 0f);
        Gfx.cylinder("shaft", 0.54f, height, stoneMat, n, 0f, 1.02f + height * 0.5f, 0f);

        if (broken) {
            Geometry chunk = Gfx.box("broken", 0.56f, 0.18f, 0.56f, stoneMat, n,
                    0.05f, 1.02f + height + 0.1f, 0.02f);
            chunk.rotate(0.12f, 0.4f, 0.1f);
        } else {
            Gfx.box("capital", 0.78f, 0.2f, 0.78f, darkStoneMat, n,
                    0f, 1.02f + height + 0.18f, 0f);
            Gfx.box("abacus", 0.86f, 0.08f, 0.86f, stoneMat, n,
                    0f, 1.02f + height + 0.42f, 0f);
        }
        n.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        root.attachChild(n);
    }

    private void buildArch() {
        Node arch = new Node("arch");
        for (int side = -1; side <= 1; side += 2) {
            float x = side * 6.3f;
            Gfx.box("towerBase", 1.15f, 0.8f, 1.15f, darkStoneMat, arch, x, 0.8f, 17.8f);
            Gfx.box("tower", 1.0f, 4.1f, 1.0f, stoneMat, arch, x, 5.4f, 17.8f);
            Gfx.box("towerBand", 1.1f, 0.22f, 1.1f, darkStoneMat, arch, x, 7.3f, 17.8f);
            Gfx.box("capital", 1.25f, 0.35f, 1.25f, stoneMat, arch, x, 9.6f, 17.8f);
            for (int k = 0; k < 3; k++) {
                Gfx.box("flute", 0.12f, 3.7f, 0.05f, darkStoneMat, arch,
                        x - 0.55f + k * 0.55f, 5.4f, 16.79f);
            }
        }
        Gfx.box("lintel", 7.3f, 0.62f, 1.2f, stoneMat, arch, 0f, 10.6f, 17.8f);
        Gfx.box("lintelTrim", 7.5f, 0.14f, 1.3f, darkStoneMat, arch, 0f, 11.3f, 17.8f);
        Gfx.box("keystone", 0.55f, 1.15f, 1.05f, darkStoneMat, arch, 0f, 9.15f, 17.7f);
        Gfx.box("damaged", 0.9f, 0.4f, 1.0f, stoneMat, arch, 3.9f, 11.55f, 17.8f);
        arch.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        root.attachChild(arch);

        for (int i = 0; i < 4; i++) {
            Gfx.box("step", 8.4f - i * 0.5f, 0.14f, 1.1f, darkStoneMat, root,
                    0f, 0.06f + i * 0.02f, 17.1f + i * 0.5f);
        }
    }

    private void buildBraziers(AssetManager am) {
        float[] angles = {0.6f, 1.42f, 2.4f, 3.85f, 4.86f, 5.72f};
        Material flameOuter = Gfx.unshaded(am, new ColorRGBA(1f, 0.46f, 0.1f, 1f));
        Material flameInner = Gfx.unshaded(am, new ColorRGBA(1f, 0.86f, 0.4f, 1f));
        for (int i = 0; i < angles.length; i++) {
            float a = angles[i];
            float x = FastMath.sin(a) * 17.15f;
            float z = FastMath.cos(a) * 17.15f;

            Gfx.cylinder("brazierColumn", 0.3f, 1.45f, darkStoneMat, root, x, 0.73f, z);
            Gfx.taperedCylinder("brazierBowl", 0.62f, 0.28f, 0.4f, darkStoneMat, root, x, 1.6f, z);

            Flame f = new Flame();
            f.outer = Gfx.sphere("flame", 0.34f, 10, flameOuter, root, x, 1.98f, z);
            f.inner = Gfx.sphere("flameCore", 0.17f, 8, flameInner, root, x, 2.0f, z);
            f.outer.setShadowMode(RenderQueue.ShadowMode.Off);
            f.inner.setShadowMode(RenderQueue.ShadowMode.Off);
            f.baseY = 1.98f;
            f.phase = i * 1.7f;
            flames.add(f);
        }
    }

    private void buildRubble() {
        for (int i = 0; i < 30; i++) {
            float a = rnd.nextFloat() * FastMath.TWO_PI;
            float r = 16.8f + rnd.nextFloat() * 2f;
            float sx = 0.18f + rnd.nextFloat() * 0.5f;
            float sy = 0.1f + rnd.nextFloat() * 0.28f;
            float sz = 0.18f + rnd.nextFloat() * 0.6f;
            Geometry rock = Gfx.box("rubble", sx, sy, sz, rnd.nextBoolean() ? stoneMat : darkStoneMat,
                    root, FastMath.sin(a) * r, sy * 0.8f, FastMath.cos(a) * r);
            rock.rotate((rnd.nextFloat() - 0.5f) * 0.4f, rnd.nextFloat() * FastMath.TWO_PI,
                    (rnd.nextFloat() - 0.5f) * 0.4f);
        }
        for (int i = 0; i < 7; i++) {
            float a = 0.35f + i * FastMath.TWO_PI / 7f;
            float r = 13.5f + rnd.nextFloat() * 2.2f;
            Geometry slab = Gfx.box("slab", 0.7f + rnd.nextFloat() * 0.6f, 0.03f, 0.45f + rnd.nextFloat(),
                    stoneMat, root, FastMath.sin(a) * r, 0.02f, FastMath.cos(a) * r);
            slab.rotate(0f, rnd.nextFloat() * FastMath.TWO_PI, 0f);
        }
    }

    private void buildDistantWorld(AssetManager am) {
        Material ruinMat = Gfx.lit(am, new ColorRGBA(0.2f, 0.19f, 0.18f, 1f),
                new ColorRGBA(0.07f, 0.07f, 0.07f, 1f), ColorRGBA.Black, 8f);

        buildRidge(am, "ridgeFar", 640f, 90f, 24f, 85f, 0.6f,
                new ColorRGBA(0.075f, 0.078f, 0.098f, 1f));
        buildRidge(am, "ridgeNear", 330f, 60f, 16f, 48f, 2.1f,
                new ColorRGBA(0.032f, 0.034f, 0.045f, 1f));

        for (int i = 0; i < 7; i++) {
            float x = (i - 3) * 17f + rnd.nextFloat() * 10f;
            float z = 42f + rnd.nextFloat() * 45f;
            float h = 7f + rnd.nextFloat() * 15f;
            Geometry col = Gfx.taperedCylinder("farColumn", 0.7f, 1.6f, h, ruinMat, root,
                    x, -14f + h * 0.5f, z);
            col.setShadowMode(RenderQueue.ShadowMode.Off);
        }

        Geometry farArchL = Gfx.box("farArchL", 1.5f, 14f, 1.5f, ruinMat, root, -32f, 0f, 92f);
        Geometry farArchR = Gfx.box("farArchR", 1.5f, 11f, 1.5f, ruinMat, root, -18f, -3f, 92f);
        Geometry farArchTop = Gfx.box("farArchTop", 9f, 1.2f, 1.7f, ruinMat, root, -25f, 13f, 92f);
        farArchL.setShadowMode(RenderQueue.ShadowMode.Off);
        farArchR.setShadowMode(RenderQueue.ShadowMode.Off);
        farArchTop.setShadowMode(RenderQueue.ShadowMode.Off);

        buildErdtree(am);
        buildMoon(am);
    }

    private void buildRidge(AssetManager am, String name, float radius, float jitter,
                            float minH, float maxH, float phase, ColorRGBA color) {
        int segments = 144;
        float[] pos = new float[(segments + 1) * 6];
        short[] idx = new short[segments * 6];
        Random shape = new Random(91L + (long) (phase * 1000f));
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float a = t * FastMath.TWO_PI;
            float rr = radius + (shape.nextFloat() - 0.5f) * jitter;
            float h = minH + (maxH - minH)
                    * (0.5f + 0.5f * FastMath.sin(a * 6f + phase))
                    * (0.55f + 0.45f * FastMath.sin(a * 13f + phase * 2.3f + 1.7f));
            int p = i * 6;
            pos[p] = FastMath.sin(a) * rr;
            pos[p + 1] = -140f;
            pos[p + 2] = FastMath.cos(a) * rr;
            pos[p + 3] = pos[p];
            pos[p + 4] = h;
            pos[p + 5] = pos[p + 2];
        }
        int k = 0;
        for (int i = 0; i < segments; i++) {
            short b0 = (short) (i * 2);
            short b1 = (short) (i * 2 + 1);
            short b2 = (short) (i * 2 + 2);
            short b3 = (short) (i * 2 + 3);
            idx[k++] = b0;
            idx[k++] = b2;
            idx[k++] = b1;
            idx[k++] = b1;
            idx[k++] = b2;
            idx[k++] = b3;
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(pos));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createShortBuffer(idx));
        mesh.updateBound();
        Geometry g = new Geometry(name, mesh);
        Material mat = Gfx.unshaded(am, color);
        mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        g.setMaterial(mat);
        g.setShadowMode(RenderQueue.ShadowMode.Off);
        root.attachChild(g);
    }

    private void buildErdtree(AssetManager am) {
        Material trunk = Gfx.unshaded(am, new ColorRGBA(0.055f, 0.042f, 0.022f, 1f));
        Material branch = Gfx.unshaded(am, new ColorRGBA(0.075f, 0.055f, 0.028f, 1f));
        Material leafA = Gfx.unshaded(am, new ColorRGBA(0.36f, 0.17f, 0.035f, 1f));
        Material leafB = Gfx.unshaded(am, new ColorRGBA(0.58f, 0.3f, 0.07f, 1f));
        Material halo = Gfx.translucent(am, new ColorRGBA(1f, 0.72f, 0.3f, 0.04f));

        float tx = -34f;
        float tz = 560f;
        Geometry body = Gfx.taperedCylinder("erdtreeTrunk", 9f, 26f, 170f, trunk, root,
                tx, 30f, tz);
        body.setShadowMode(RenderQueue.ShadowMode.Off);

        Vector3f top = new Vector3f(tx, 115f, tz);
        for (int i = 0; i < 6; i++) {
            float a = i * FastMath.TWO_PI / 6f + 0.4f;
            Geometry b = new Geometry("branch", new Cylinder(2, 8, 2.6f, 2.6f, 70f, true, false));
            b.rotate(-FastMath.HALF_PI, 0f, 0f);
            b.setMaterial(branch);
            b.setLocalTranslation(top.x + FastMath.sin(a) * 18f, 120f, top.z + FastMath.cos(a) * 18f);
            b.rotate(FastMath.cos(a) * 0.85f, 0f, FastMath.sin(a) * -0.85f);
            b.setShadowMode(RenderQueue.ShadowMode.Off);
            root.attachChild(b);
        }

        for (int i = 0; i < 13; i++) {
            float a = rnd.nextFloat() * FastMath.TWO_PI;
            float r = 18f + rnd.nextFloat() * 62f;
            float y = 128f + rnd.nextFloat() * 95f;
            float size = 28f + rnd.nextFloat() * 34f;
            Geometry blob = Gfx.sphere("erdCanopy", size, 10, i % 3 == 0 ? leafB : leafA, root,
                    tx + FastMath.sin(a) * r, y, tz + FastMath.cos(a) * r);
            blob.setShadowMode(RenderQueue.ShadowMode.Off);
        }

        for (int i = 0; i < 3; i++) {
            Geometry glow = Gfx.sphere("erdHalo", 95f + i * 30f, 8, halo, root, tx, 150f + i * 8f, tz);
            glow.setShadowMode(RenderQueue.ShadowMode.Off);
        }

        Geometry glowBase = Gfx.sphere("erdBaseGlow", 60f, 8,
                Gfx.translucent(am, new ColorRGBA(1f, 0.62f, 0.2f, 0.07f)), root, tx, 0f, tz);
        glowBase.setShadowMode(RenderQueue.ShadowMode.Off);
    }

    private void buildMoon(AssetManager am) {
        Material moon = Gfx.unshaded(am, new ColorRGBA(0.86f, 0.84f, 0.76f, 1f));
        Material halo = Gfx.translucent(am, new ColorRGBA(0.9f, 0.88f, 0.8f, 0.09f));
        Geometry moonGeo = Gfx.sphere("moon", 30f, 16, moon, root, 235f, 190f, 380f);
        moonGeo.setShadowMode(RenderQueue.ShadowMode.Off);
        Geometry haloGeo = Gfx.sphere("moonHalo", 50f, 12, halo, root, 235f, 190f, 380f);
        haloGeo.setShadowMode(RenderQueue.ShadowMode.Off);
    }

    void update(float time) {
        for (Flame f : flames) {
            float flick = 1f + 0.18f * FastMath.sin(time * 9f + f.phase)
                    + 0.1f * FastMath.sin(time * 23f + f.phase * 3f);
            f.outer.setLocalScale(flick, 1.35f * flick, flick);
            f.outer.setLocalTranslation(f.outer.getLocalTranslation().x,
                    f.baseY + 0.1f * flick, f.outer.getLocalTranslation().z);
            f.inner.setLocalScale(flick * 0.95f, 1.15f * flick, flick * 0.95f);
        }
    }
}
