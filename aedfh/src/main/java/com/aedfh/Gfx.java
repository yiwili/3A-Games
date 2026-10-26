package com.aedfh;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
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
import com.jme3.util.TangentBinormalGenerator;

final class Gfx {

    private Gfx() {
    }

    static Material lit(AssetManager am, ColorRGBA diffuse, ColorRGBA ambient) {
        return lit(am, diffuse, ambient, new ColorRGBA(0.045f, 0.042f, 0.038f, 1f), 10f);
    }

    static Material lit(AssetManager am, ColorRGBA diffuse, ColorRGBA ambient,
                        ColorRGBA specular, float shininess) {
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", diffuse);
        m.setColor("Ambient", ambient);
        m.setColor("Specular", specular);
        m.setFloat("Shininess", shininess);
        return m;
    }

    /** Apply a Poly Haven CC0 diffuse + normal map pair to a lighting material. */
    static void mapped(Material m, AssetManager am, String base, float normalScale) {
        var diffuse = Tex.loaded(am, "Textures/PolyHaven/" + base + "_diff.jpg", true);
        if (diffuse != null) {
            m.setTexture("DiffuseMap", diffuse);
        }
        var normal = Tex.loaded(am, "Textures/PolyHaven/" + base + "_nor.jpg", true);
        if (normal != null) {
            m.setTexture("NormalMap", normal);
            m.setFloat("NormalType", 1f);
        }
    }

    /** Generate tangents once so normal maps render correctly. */
    static void tangents(Spatial spatial) {
        if (spatial instanceof Geometry g) {
            if (g.getMaterial() == null || g.getMaterial().getParam("NormalMap") == null
                    || g.getMesh().getBuffer(VertexBuffer.Type.Normal) == null
                    || g.getMesh().getBuffer(VertexBuffer.Type.TexCoord) == null
                    || g.getMesh().getBuffer(VertexBuffer.Type.Tangent) != null) {
                return;
            }
            java.util.logging.Logger.getLogger("com.jme3.util.TangentBinormalGenerator")
                    .setLevel(java.util.logging.Level.SEVERE);
            TangentBinormalGenerator.generate(g.getMesh());
            return;
        }
        if (spatial instanceof Node n) {
            for (Spatial child : n.getChildren()) {
                tangents(child);
            }
        }
    }

    static Material unshaded(AssetManager am, ColorRGBA color) {
        Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        return m;
    }

    static Material translucent(AssetManager am, ColorRGBA color) {
        Material m = unshaded(am, color);
        m.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        m.getAdditionalRenderState().setDepthWrite(false);
        return m;
    }

    static Geometry attach(Geometry g, Node parent, float x, float y, float z) {
        g.setLocalTranslation(x, y, z);
        parent.attachChild(g);
        return g;
    }

    static Geometry box(String name, Mesh mesh, Material mat, Node parent, float x, float y, float z) {
        Geometry g = new Geometry(name, mesh);
        g.setMaterial(mat);
        return attach(g, parent, x, y, z);
    }

    static Geometry box(String name, float hx, float hy, float hz, Material mat, Node parent,
                        float x, float y, float z) {
        return box(name, new Box(hx, hy, hz), mat, parent, x, y, z);
    }

    static Geometry sphere(String name, float radius, int samples, Material mat, Node parent,
                           float x, float y, float z) {
        return box(name, new Sphere(samples, samples, radius), mat, parent, x, y, z);
    }

    static Geometry cylinder(String name, float radius, float height, Material mat, Node parent,
                             float x, float y, float z) {
        Geometry g = box(name, new Cylinder(2, 20, radius, height, true), mat, parent, x, y, z);
        g.rotate(-FastMath.HALF_PI, 0f, 0f);
        return g;
    }

    static Geometry taperedCylinder(String name, float radiusTop, float radiusBottom, float height,
                                    Material mat, Node parent, float x, float y, float z) {
        Geometry g = box(name, new Cylinder(2, 20, radiusTop, radiusBottom, height, true, false),
                mat, parent, x, y, z);
        g.rotate(-FastMath.HALF_PI, 0f, 0f);
        return g;
    }

    static Geometry torus(String name, float radius, float tube, Material mat, Node parent,
                          float x, float y, float z) {
        Geometry g = new Geometry(name, torusMesh(radius, tube, 64, 10));
        g.setMaterial(mat);
        g.setLocalTranslation(x, y, z);
        parent.attachChild(g);
        return g;
    }

    /** Flat torus around the Y axis; jME's built-in Torus winds its ring diagonally. */
    static Mesh torusMesh(float majorRadius, float tubeRadius, int majorSamples, int tubeSamples) {
        int rings = majorSamples + 1;
        int sides = tubeSamples + 1;
        float[] pos = new float[rings * sides * 3];
        float[] nrm = new float[rings * sides * 3];
        float[] uv = new float[rings * sides * 2];
        short[] idx = new short[majorSamples * tubeSamples * 6];
        int p = 0;
        int t = 0;
        for (int i = 0; i < rings; i++) {
            float u = i * FastMath.TWO_PI / majorSamples;
            float cu = FastMath.cos(u);
            float su = FastMath.sin(u);
            for (int j = 0; j < sides; j++) {
                float v = j * FastMath.TWO_PI / tubeSamples;
                float cv = FastMath.cos(v);
                float sv = FastMath.sin(v);
                pos[p] = (majorRadius + tubeRadius * cv) * cu;
                pos[p + 1] = tubeRadius * sv;
                pos[p + 2] = (majorRadius + tubeRadius * cv) * su;
                nrm[p] = cv * cu;
                nrm[p + 1] = sv;
                nrm[p + 2] = cv * su;
                p += 3;
                uv[t++] = (float) i / majorSamples;
                uv[t++] = (float) j / tubeSamples;
            }
        }
        int k = 0;
        for (int i = 0; i < majorSamples; i++) {
            for (int j = 0; j < tubeSamples; j++) {
                int a = i * sides + j;
                int b = a + sides;
                idx[k++] = (short) a;
                idx[k++] = (short) (a + 1);
                idx[k++] = (short) b;
                idx[k++] = (short) (a + 1);
                idx[k++] = (short) (b + 1);
                idx[k++] = (short) b;
            }
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(pos));
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(nrm));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(uv));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createShortBuffer(idx));
        mesh.setStatic();
        mesh.updateBound();
        return mesh;
    }

    static Geometry fxSphere(String name, ColorRGBA color, AssetManager am, Node parent) {
        Geometry g = box(name, new Sphere(6, 6, 1f), translucent(am, color), parent, 0f, 0f, 0f);
        g.setQueueBucket(RenderQueue.Bucket.Transparent);
        g.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.Off);
        return g;
    }

    static Geometry telegraphDisc(String name, AssetManager am, Node parent, ColorRGBA color) {
        Geometry g = new Geometry(name, new Cylinder(2, 48, 1f, 0.02f, true));
        g.rotate(-FastMath.HALF_PI, 0f, 0f);
        Material mat = translucent(am, color);
        g.setMaterial(mat);
        g.setLocalTranslation(0f, 0.03f, 0f);
        g.setQueueBucket(RenderQueue.Bucket.Transparent);
        g.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.Off);
        parent.attachChild(g);
        return g;
    }
}
