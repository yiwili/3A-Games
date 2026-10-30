package com.aedfh;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.control.BillboardControl;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class Fx {

    private static final class Particle {
        Geometry geometry;
        Material material;
        Vector3f velocity;
        ColorRGBA color;
        float life;
        float maxLife;
        float gravity;
        float growth;
        float baseScale;
    }

    /** 火柱：一层旋转的锥形壳，加色混合，半径呼吸 + 高度先冲高后回缩。 */
    private static final class Column {
        Geometry geo;
        Material material;
        ColorRGBA color;
        float life;
        float maxLife;
        float radius;
        float height;
        float spin;
    }

    private final AssetManager am;
    private final Node root;
    private final List<Particle> particles = new ArrayList<>();
    private final List<Column> columns = new ArrayList<>();
    private final Texture2D puffTex;
    private static final float TRAIL_LIFE = 0.07f;
    private static final int MAX_TRAIL = 160;

    private final List<TrailSample> trail = new ArrayList<>();
    private final Random rnd = new Random(7);
    private Geometry trailGeo;
    private float trailTime;
    private float lastDt = 1f / 60f;
    private final Vector3f lastTip = new Vector3f();
    private final Vector3f lastMid = new Vector3f();
    private final Vector3f lastPivot = new Vector3f();
    private boolean hasLastTip;

    private static final class TrailSample {
        final Vector3f tip;
        final Vector3f mid;
        final float birth;

        TrailSample(Vector3f tip, Vector3f mid, float birth) {
            this.tip = tip;
            this.mid = mid;
            this.birth = birth;
        }
    }

    Fx(AssetManager am, Node root) {
        this.am = am;
        this.root = root;
        this.puffTex = Tex.puff();
    }

    void spark(Vector3f pos, ColorRGBA color, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = particle(pos.clone(), color, 0.05f);
            p.velocity.set((rnd.nextFloat() - 0.5f) * 7f,
                    1.5f + rnd.nextFloat() * 5.5f,
                    (rnd.nextFloat() - 0.5f) * 7f);
            p.life = 0.22f + rnd.nextFloat() * 0.22f;
            p.maxLife = p.life;
            p.gravity = -14f;
            p.growth = -0.6f;
        }
    }

    void dust(Vector3f pos, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = particle(pos, new ColorRGBA(0.52f, 0.44f, 0.33f, 0.22f), 0.09f);
            p.velocity.set((rnd.nextFloat() - 0.5f) * 2.4f,
                    0.3f + rnd.nextFloat() * 1.2f,
                    (rnd.nextFloat() - 0.5f) * 2.4f);
            p.life = 0.45f + rnd.nextFloat() * 0.4f;
            p.maxLife = p.life;
            p.gravity = -1.4f;
            p.growth = 1.8f;
        }
    }

    /** Low swirling flames plus small rising embers around {@code center}. */
    void flame(Vector3f center, float radius, int count) {
        for (int i = 0; i < count; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            float r = radius * (0.42f + rnd.nextFloat() * 0.6f);
            boolean ember = i % 3 == 0;
            Vector3f p;
            Particle q;
            if (ember) {
                p = new Vector3f(center.x + cos * r,
                        center.y + 0.35f + rnd.nextFloat() * 0.85f,
                        center.z + sin * r);
                ColorRGBA c = new ColorRGBA(1f, 0.74f + rnd.nextFloat() * 0.2f,
                        0.26f + rnd.nextFloat() * 0.16f, 0.85f);
                q = particle(p, c, 0.032f + rnd.nextFloat() * 0.028f, 2.6f);
                float swirl = 1.2f + rnd.nextFloat() * 1.2f;
                q.velocity.set(sin * swirl + (rnd.nextFloat() - 0.5f) * 0.6f,
                        2.2f + rnd.nextFloat() * 1.8f,
                        -cos * swirl + (rnd.nextFloat() - 0.5f) * 0.6f);
                q.life = 0.4f + rnd.nextFloat() * 0.3f;
                q.gravity = 2.4f;
                q.growth = -0.25f;
            } else {
                float h = rnd.nextFloat();
                h *= h;
                p = new Vector3f(center.x + cos * r,
                        center.y + 0.05f + h * 1.05f,
                        center.z + sin * r);
                ColorRGBA c = new ColorRGBA(1f, 0.2f + rnd.nextFloat() * 0.3f,
                        0.02f + rnd.nextFloat() * 0.06f, 0.58f);
                q = particle(p, c, 0.11f + rnd.nextFloat() * 0.09f, 2.2f);
                float swirl = 2.2f + rnd.nextFloat() * 1.3f;
                q.velocity.set(sin * swirl + (rnd.nextFloat() - 0.5f) * 0.7f,
                        1.8f + rnd.nextFloat() * 1.3f,
                        -cos * swirl + (rnd.nextFloat() - 0.5f) * 0.7f);
                q.life = 0.3f + rnd.nextFloat() * 0.24f;
                q.gravity = 1.4f;
                q.growth = 0.3f;
            }
            q.maxLife = q.life;
            q.material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
        }
    }

    /**
     * 旋转火柱：世界坐标 {@code base} 起、高 {@code height} 的锥形壳。开放两端、加色混合，
     * 每帧绕 Y 自转并按 (半径, 高度) 呼吸；烈焰风暴与 Boss 光柱都走这一套。
     */
    void firePillar(Vector3f base, float radius, float height, float duration, ColorRGBA color) {
        // 3 段轴采样的三角柱在近景会读成金板子，柱面要足够圆
        Mesh mesh = new com.jme3.scene.shape.Cylinder(6, 24, 0.45f, 1f, 1f, false, false);
        Geometry geo = new Geometry("firePillar", mesh);
        Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setTexture("ColorMap", puffTex);
        m.setColor("Color", color);
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
        m.getAdditionalRenderState().setDepthWrite(false);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        geo.setMaterial(m);
        geo.setLocalTranslation(base.x, base.y + height * 0.5f, base.z);
        geo.setLocalScale(radius, height, radius);
        geo.setQueueBucket(RenderQueue.Bucket.Transparent);
        geo.setShadowMode(RenderQueue.ShadowMode.Off);
        root.attachChild(geo);

        Column c = new Column();
        c.geo = geo;
        c.material = m;
        c.color = color.clone();
        c.life = duration;
        c.maxLife = duration;
        c.radius = radius;
        c.height = height;
        c.spin = 2.2f + rnd.nextFloat() * 1.6f;
        columns.add(c);
    }

    void firePillar(Vector3f base, float radius, float height, float duration) {
        firePillar(base, radius, height, duration, new ColorRGBA(1f, 0.42f, 0.10f, 0.62f));
    }


    /** 存活的特效对象数（断言用：柱子 + 粒子）。 */
    int activeFx() {
        return columns.size() + particles.size();
    }

    void shockwave(Vector3f pos, float radius, float duration, ColorRGBA color) {
        Geometry geo = new Geometry("shockwave", new com.jme3.scene.shape.Cylinder(2, 48, 1f, 0.02f, true));
        geo.rotate(-com.jme3.math.FastMath.HALF_PI, 0f, 0f);
        Material mat = Gfx.translucent(am, color);
        geo.setMaterial(mat);
        geo.setLocalTranslation(pos.x, 0.04f, pos.z);
        geo.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Transparent);
        geo.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.Off);
        root.attachChild(geo);

        Particle p = new Particle();
        p.geometry = geo;
        p.material = mat;
        p.color = color.clone();
        p.velocity = new Vector3f();
        p.life = duration;
        p.maxLife = duration;
        p.gravity = 0f;
        p.growth = radius;
        p.baseScale = 0.2f;
        particles.add(p);
    }

    /** 水面涟漪：与 shockwave 同一套粒子，但用传入的 y（shockwave 固定在 y=0.04，只能铺平地）。 */
    void ripple(Vector3f pos, float radius, float duration, ColorRGBA color) {
        Geometry geo = new Geometry("ripple", new com.jme3.scene.shape.Cylinder(2, 40, 1f, 0.015f, true));
        geo.rotate(-com.jme3.math.FastMath.HALF_PI, 0f, 0f);
        Material mat = Gfx.translucent(am, color);
        geo.setMaterial(mat);
        geo.setLocalTranslation(pos.x, pos.y, pos.z);
        geo.setQueueBucket(RenderQueue.Bucket.Transparent);
        geo.setShadowMode(RenderQueue.ShadowMode.Off);
        root.attachChild(geo);

        Particle p = new Particle();
        p.geometry = geo;
        p.material = mat;
        p.color = color.clone();
        p.velocity = new Vector3f();
        p.life = duration;
        p.maxLife = duration;
        p.gravity = 0f;
        p.growth = radius;
        p.baseScale = 0.2f;
        particles.add(p);
    }

    void flash(Vector3f pos, ColorRGBA color, float size) {
        Particle p = particle(pos, color, size);
        p.life = 0.18f;
        p.maxLife = p.life;
        p.gravity = 0f;
        p.growth = 2.4f;
    }

    void trail(Vector3f pos, ColorRGBA color, float size) {
        Particle p = particle(pos, color, size);
        p.life = 0.26f;
        p.maxLife = p.life;
        p.gravity = 0f;
        p.growth = -0.9f;
    }

    /** Ribbon-shaped weapon arc; call once per frame while the weapon is swinging. */
    void swingTrail(Vector3f tip, Vector3f mid, Vector3f pivot) {
        if (trailGeo == null) {
            Mesh mesh = new Mesh();
            mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(new float[16 * 3]));
            mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(new float[16 * 4]));
            trailGeo = new Geometry("weaponTrail", mesh);
            Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setBoolean("VertexColor", true);
            m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            m.getAdditionalRenderState().setDepthWrite(false);
            m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            trailGeo.setMaterial(m);
            trailGeo.setQueueBucket(RenderQueue.Bucket.Transparent);
            trailGeo.setShadowMode(RenderQueue.ShadowMode.Off);
            trailGeo.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
            root.attachChild(trailGeo);
        }
        if (!hasLastTip) {
            trail.add(new TrailSample(tip.clone(), mid.clone(), trailTime));
            hasLastTip = true;
            lastTip.set(tip);
            lastMid.set(mid);
            lastPivot.set(pivot);
            return;
        }
        if (lastTip.distanceSquared(tip) < 0.0025f) {
            return;
        }
        Vector3f t0 = lastTip.subtract(lastPivot);
        Vector3f t1 = tip.subtract(pivot);
        Vector3f m0 = lastMid.subtract(lastPivot);
        Vector3f m1 = mid.subtract(pivot);
        float r0 = t0.length();
        float r1 = t1.length();
        float s0 = m0.length();
        float s1 = m1.length();
        float angle = r0 > 1e-4f && r1 > 1e-4f
                ? (float) Math.acos(MathX.clamp(t0.dot(t1) / (r0 * r1), -1f, 1f)) : 0f;
        if (angle > 2.6f) {
            trail.clear();
            trail.add(new TrailSample(tip.clone(), mid.clone(), trailTime));
            lastTip.set(tip);
            lastMid.set(mid);
            lastPivot.set(pivot);
            return;
        }
        int steps = Math.max(1, Math.min(24, (int) Math.ceil(angle / 0.10f)));
        for (int k = 1; k <= steps; k++) {
            float u = k / (float) steps;
            float birth = trailTime - lastDt * (1f - u);
            Vector3f pv = lastPivot.clone().interpolateLocal(pivot, u);
            Vector3f dirT = t0.clone().multLocal(1f - u).addLocal(t1.mult(u)).normalizeLocal();
            Vector3f dirM = m0.clone().multLocal(1f - u).addLocal(m1.mult(u)).normalizeLocal();
            Vector3f tipPt = pv.clone().addLocal(dirT.multLocal(MathX.lerp(r0, r1, u)));
            Vector3f midPt = pv.addLocal(dirM.multLocal(MathX.lerp(s0, s1, u)));
            trail.add(new TrailSample(tipPt, midPt, birth));
        }
        lastTip.set(tip);
        lastMid.set(mid);
        lastPivot.set(pivot);
        Vector3f newestDir = tip.subtract(pivot);
        if (newestDir.lengthSquared() > 1e-6f) {
            newestDir.normalizeLocal();
            while (!trail.isEmpty()
                    && trail.get(0).tip.subtract(pivot).normalize().dot(newestDir) < 0.70f) {
                trail.remove(0);
            }
        }
        while (trail.size() > MAX_TRAIL) {
            trail.remove(0);
        }
    }

    private void updateTrail(float dt) {
        trailTime += dt;
        lastDt = Math.max(dt, 1e-4f);
        for (int i = trail.size() - 1; i >= 0; i--) {
            if (trailTime - trail.get(i).birth > TRAIL_LIFE) {
                trail.remove(i);
            }
        }
        if (trail.isEmpty()) {
            hasLastTip = false;
        }
        if (trailGeo == null) {
            return;
        }
        int n = trail.size();
        if (n < 2) {
            trailGeo.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
            return;
        }
        float[] pos = new float[n * 2 * 3];
        float[] col = new float[n * 2 * 4];
        short[] idx = new short[(n - 1) * 6];
        for (int i = 0; i < n; i++) {
            TrailSample s = trail.get(i);
            float age = MathX.clamp((trailTime - s.birth) / TRAIL_LIFE, 0f, 1f);
            float fade = 1f - age;
            float innerK = 0.24f * (1f - 0.75f * age);
            float innerX = s.tip.x + (s.mid.x - s.tip.x) * innerK;
            float innerY = s.tip.y + (s.mid.y - s.tip.y) * innerK;
            float innerZ = s.tip.z + (s.mid.z - s.tip.z) * innerK;
            int p = i * 6;
            pos[p] = s.tip.x;
            pos[p + 1] = s.tip.y;
            pos[p + 2] = s.tip.z;
            pos[p + 3] = innerX;
            pos[p + 4] = innerY;
            pos[p + 5] = innerZ;
            int c = i * 8;
            float bright = fade * fade;
            col[c] = 1f;
            col[c + 1] = 0.36f;
            col[c + 2] = 0.03f;
            col[c + 3] = 0.90f * bright;
            col[c + 4] = 1f;
            col[c + 5] = 0.14f;
            col[c + 6] = 0f;
            col[c + 7] = 0.14f * bright;
        }
        for (int i = 0; i < n - 1; i++) {
            short a0 = (short) (i * 2);
            short a1 = (short) (i * 2 + 1);
            short b0 = (short) (i * 2 + 2);
            short b1 = (short) (i * 2 + 3);
            int k = i * 6;
            idx[k] = a0;
            idx[k + 1] = b0;
            idx[k + 2] = a1;
            idx[k + 3] = a1;
            idx[k + 4] = b0;
            idx[k + 5] = b1;
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(pos));
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(col));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createShortBuffer(idx));
        mesh.updateBound();
        trailGeo.setMesh(mesh);
        trailGeo.setCullHint(com.jme3.scene.Spatial.CullHint.Never);
    }

    void ember(Vector3f pos, ColorRGBA color) {
        Particle p = particle(pos, color, 0.035f + rnd.nextFloat() * 0.025f);
        p.velocity.set((rnd.nextFloat() - 0.5f) * 0.4f,
                0.35f + rnd.nextFloat() * 0.5f,
                (rnd.nextFloat() - 0.5f) * 0.4f);
        p.life = 1.8f + rnd.nextFloat() * 1.8f;
        p.maxLife = p.life;
        p.gravity = 0.12f;
        p.growth = 0.2f;
    }

    private Particle particle(Vector3f pos, ColorRGBA color, float radius) {
        return particle(pos, color, radius, 1f);
    }

    private Particle particle(Vector3f pos, ColorRGBA color, float radius, float aspect) {
        Geometry geo = new Geometry("fx", new Quad(radius * 2f, radius * 2f * aspect));
        Material mat = Gfx.translucent(am, color);
        mat.setTexture("ColorMap", puffTex);
        mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        geo.setMaterial(mat);
        geo.setLocalTranslation(pos.x, pos.y, pos.z);
        geo.addControl(new BillboardControl());
        geo.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Transparent);
        geo.setShadowMode(com.jme3.renderer.queue.RenderQueue.ShadowMode.Off);
        geo.setCullHint(com.jme3.scene.Spatial.CullHint.Never);
        root.attachChild(geo);

        Particle p = new Particle();
        p.geometry = geo;
        p.material = mat;
        p.color = color.clone();
        p.velocity = new Vector3f();
        p.life = 0.3f;
        p.maxLife = 0.3f;
        p.baseScale = 1f;
        particles.add(p);
        return p;
    }

    /** 清掉遗留特效（换区/取景时用），避免上一场战斗的火柱串进镜头。 */
    void clear() {
        for (Particle p : particles) {
            p.geometry.removeFromParent();
        }
        for (Column c : columns) {
            c.geo.removeFromParent();
        }
        particles.clear();
        columns.clear();
    }

    void update(float dt) {
        for (int i = columns.size() - 1; i >= 0; i--) {
            Column c = columns.get(i);
            c.life -= dt;
            if (c.life <= 0f) {
                c.geo.removeFromParent();
                columns.remove(i);
                continue;
            }
            float t = 1f - c.life / c.maxLife;
            float grow = MathX.smooth(Math.min(1f, t * 5.5f));
            float fade = 1f - MathX.smooth(Math.max(0f, (t - 0.45f) / 0.55f));
            float pulse = 0.86f + 0.14f * FastMath.sin(t * 22f + c.spin);
            c.geo.rotate(0f, c.spin * dt, 0f);
            float sx = c.radius * grow * fade * pulse;
            float sy = c.height * (0.30f + 0.70f * grow) * (0.55f + 0.45f * fade);
            c.geo.setLocalScale(Math.max(0.02f, sx), Math.max(0.05f, sy), Math.max(0.02f, sx));
            float a = c.color.a * fade * 0.85f;
            c.material.setColor("Color", new ColorRGBA(c.color.r, c.color.g, c.color.b, a));
        }
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0f) {
                p.geometry.removeFromParent();
                particles.remove(i);
                continue;
            }

            float t = 1f - p.life / p.maxLife;
            p.velocity.y += p.gravity * dt;
            Vector3f loc = p.geometry.getLocalTranslation();
            p.geometry.setLocalTranslation(
                    loc.x + p.velocity.x * dt,
                    Math.max(0.02f, loc.y + p.velocity.y * dt),
                    loc.z + p.velocity.z * dt);

            float scale = p.baseScale * (1f + p.growth * t);
            if (p.growth < 0f) {
                scale = Math.max(0.02f, scale);
            } else if (p.baseScale <= 1f && p.geometry.getMesh().getVertexCount() > 0
                    && p.geometry.getMesh() instanceof com.jme3.scene.shape.Cylinder) {
                scale = Math.max(0.2f, p.growth * MathX.smooth(t));
            }
            p.geometry.setLocalScale(Math.max(0.02f, scale));

            float alpha = p.color.a * (1f - t);
            p.material.setColor("Color", new ColorRGBA(p.color.r, p.color.g, p.color.b, alpha));
        }
        updateTrail(dt);
    }
}
