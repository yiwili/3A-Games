package com.aedfh;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.BatchNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;
import com.jme3.util.BufferUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Static Limgrave set dressing: woods, ruins, landmarks and the gatehouse. */
final class WorldProps {

    static final class GraceGlow {
        final Node node;
        final Geometry flare;
        final float phase;
        final float baseY;

        GraceGlow(Node node, Geometry flare, float phase, float baseY) {
            this.node = node;
            this.flare = flare;
            this.phase = phase;
            this.baseY = baseY;
        }
    }

    /**
     * Analytic stand-in for one batched prop. BatchNode drops the child transforms when it
     * merges, so the resulting meshes carry no collision data; these oriented boxes let
     * camera probes see the set dressing.
     */
    static final class PropBox {
        final float cx, cy, cz;
        final float hx, hy, hz;
        final float yaw;
        final float br;

        PropBox(float cx, float cy, float cz, float hx, float hy, float hz, float yaw) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.hx = hx;
            this.hy = hy;
            this.hz = hz;
            this.yaw = yaw;
            this.br = FastMath.sqrt(hx * hx + hy * hy + hz * hz);
        }
    }

    private final Node root = new Node("limgraveProps");
    private final AssetManager am;
    private final WorldTerrain terrain;
    private final Gltf gltf;
    private final Random rnd = new Random(23);
    private final List<GraceGlow> graces = new ArrayList<>();
    private final List<PropBox> blockers = new ArrayList<>();
    /** 玩家路障（与相机探针 blockers 分开维护，见 WorldCollision）。 */
    private final List<WorldCollision.Box> solids = new ArrayList<>();
    private final List<WorldCollision.Cyl> solidRounds = new ArrayList<>();
    private final Node[] gateLeaves = new Node[2];
    private final float[] gateClosedX = new float[2];
    private final float[] gateOpenX = new float[2];
    private float gateOpenT;
    private boolean gateOpening;
    private float time;

    private final Material bark;
    private final Material leaf;
    private final Material leafMist;
    private final Material stone;
    private final Material stoneDark;
    private final Material rock;
    private final Material wood;
    private final Material iron;
    private final Material cloth;
    private final Material roof;
    private final Material goldGlow;
    private final Material blueGlow;
    private final Material fireGlow;

    WorldProps(AssetManager am, Node parent, WorldTerrain terrain) {
        this.am = am;
        this.terrain = terrain;
        this.gltf = new Gltf(am);
        parent.attachChild(root);

        bark = lit(new ColorRGBA(0.20f, 0.155f, 0.11f, 1f), 3f);
        leaf = lit(new ColorRGBA(0.19f, 0.28f, 0.115f, 1f), 2f);
        leafMist = lit(new ColorRGBA(0.12f, 0.23f, 0.13f, 1f), 2f);
        stone = lit(new ColorRGBA(0.240f, 0.232f, 0.210f, 1f), 4f);
        stoneDark = lit(new ColorRGBA(0.150f, 0.152f, 0.140f, 1f), 3f);
        // mossy_rock 本身已经偏暗，diffuse 再压到 0.3 就成黑石头了，这里只做轻微冷灰偏色
        rock = lit(new ColorRGBA(0.86f, 0.88f, 0.84f, 1f), 3f);
        Gfx.mapped(rock, am, "mossy_rock", 1.1f);
        // 贴图原图偏暗，背光面直接黑成剪影；抬亮一档 + 提环境光，石头才读得出体块
        var rockLit = Tex.tinted("Textures/PolyHaven/mossy_rock_diff.jpg", 512,
                1.32f, 1.34f, 1.26f);
        if (rockLit != null) {
            rock.setTexture("DiffuseMap", rockLit);
        }
        rock.setColor("Ambient", new ColorRGBA(0.32f, 0.33f, 0.30f, 1f));
        var masonry = Tex.masonry();
        stone.setTexture("DiffuseMap", masonry);
        stoneDark.setTexture("DiffuseMap", masonry);
        wood = lit(new ColorRGBA(0.26f, 0.185f, 0.12f, 1f), 3f);
        iron = lit(new ColorRGBA(0.20f, 0.20f, 0.19f, 1f), 22f);
        cloth = lit(new ColorRGBA(0.36f, 0.075f, 0.06f, 1f), 2f);
        roof = lit(new ColorRGBA(0.17f, 0.145f, 0.11f, 1f), 2f);
        goldGlow = glow(new ColorRGBA(1f, 0.66f, 0.24f, 0.34f));
        blueGlow = glow(new ColorRGBA(0.30f, 0.55f, 1f, 0.45f));
        fireGlow = glow(new ColorRGBA(1f, 0.46f, 0.12f, 0.5f));

        buildGraces();
        buildTrees();
        buildDeadTrees();
        buildRocks();
        buildBushes();
        buildPois();
        buildCamps();
        buildLakeRuins();
        buildStormveil();
        buildMinorErdtree();
    }

    List<GraceGlow> graceGlows() {
        return graces;
    }

    int blockerCount() {
        return blockers.size();
    }

    private void blocker(float cx, float cy, float cz, float hx, float hy, float hz, float yaw) {
        blockers.add(new PropBox(cx, cy, cz, hx, hy, hz, yaw));
    }

    // ---------------------------------------------------------------- glTF props

    /**
     * 摆一件 KayKit glTF 道具：底面贴地、按目标高度等比缩放，返回<b>缩放后</b>的包围盒尺寸。
     * 加载失败返回 {@code null}，调用方回退到原程序化几何。
     *
     * <p>模型本身 Y-up、底面 y=0、随机 yaw 由调用方给（不额外消耗 {@link Random}，
     * 否则会改变全局随机序列、把整张地图的 POI 布局洗掉）。
     */
    private Vector3f gltfAt(Node parent, String name, float x, float z, float yaw, float targetH) {
        return gltfAt(parent, name, x, z, yaw, targetH, 0f);
    }

    /**
     * @param xzScale 非 0 时水平方向用这个固定缩放（残柱要"统一粗细、只变高度"），竖直仍按目标高度
     */
    private Vector3f gltfAt(Node parent, String name, float x, float z, float yaw,
                            float targetH, float xzScale) {
        Spatial s = gltf.spawn(name);
        if (s == null) {
            return null;
        }
        Vector3f dim = gltf.size(name);
        float ky = targetH > 0.01f ? gltf.fitHeight(name, targetH) : 1f;
        float kxz = xzScale > 0f ? xzScale : ky;
        s.setLocalScale(kxz, ky, kxz);
        s.setLocalTranslation(x, terrain.height(x, z), z);
        s.rotate(0f, yaw, 0f);
        parent.attachChild(s);
        return new Vector3f(dim.x * kxz, dim.y * ky, dim.z * kxz);
    }

    /** 以底面中心为准，同时登记相机探针与玩家路障。 */
    private void boxColliders(float cx, float cz, float baseY, Vector3f dim, float yaw) {
        float hy = dim.y * 0.5f;
        blocker(cx, baseY + hy, cz, dim.x * 0.5f, hy, dim.z * 0.5f, yaw);
        solid(cx, baseY + hy, cz, dim.x * 0.5f, hy, dim.z * 0.5f, yaw);
    }

    /** 圆柱形（柱子/树干）只登记路障与探针的圆形近似。 */
    private void roundColliders(float cx, float cz, float baseY, Vector3f dim) {
        float hy = dim.y * 0.5f;
        float r = Math.max(dim.x, dim.z) * 0.5f;
        blocker(cx, baseY + hy, cz, r, hy, r, 0f);
        solidRound(cx, baseY + hy, cz, r, hy);
    }

    List<WorldCollision.Box> solids() {
        return solids;
    }

    List<WorldCollision.Cyl> solidRounds() {
        return solidRounds;
    }

    /**
     * 注册玩家路障盒。与 {@link #blocker} 语义不同：这里只登记真正挡路的结构件，
     * 且尺寸贴合视觉（探针那套带取景余量，不能直接当路障）。
     */
    private void solid(float cx, float cy, float cz, float hx, float hy, float hz, float yaw) {
        solids.add(new WorldCollision.Box(cx, cy, cz, hx, hy, hz, yaw));
    }

    private void solidRound(float cx, float cy, float cz, float r, float hy) {
        solidRounds.add(new WorldCollision.Cyl(cx, cy, cz, r, hy));
    }

    /** Swing the castle gate open once the fell omen lies broken at its feet. */
    void openCastleGate() {
        gateOpening = true;
    }

    void closeCastleGate() {
        gateOpening = false;
        gateOpenT = 0f;
        for (int i = 0; i < gateLeaves.length; i++) {
            if (gateLeaves[i] != null) {
                gateLeaves[i].setLocalTranslation(gateClosedX[i],
                        gateLeaves[i].getLocalTranslation().y,
                        gateLeaves[i].getLocalTranslation().z);
            }
        }
    }

    boolean gateOpen() {
        return gateOpenT >= 1f;
    }

    /** True when the segment from origin along dir (length maxDist) runs through a prop. */
    boolean segmentHitsProp(Vector3f origin, Vector3f dir, float maxDist) {
        for (PropBox b : blockers) {
            if (rayBox(b, origin, dir, maxDist) >= 0f) {
                return true;
            }
        }
        return false;
    }

    /** Entry distance of the ray into the box, or -1 when it misses the segment. */
    private static float rayBox(PropBox b, Vector3f o, Vector3f d, float maxDist) {
        float ox = o.x - b.cx;
        float oy = o.y - b.cy;
        float oz = o.z - b.cz;
        float proj = -(ox * d.x + oy * d.y + oz * d.z);
        if (proj < -b.br) {
            return -1f;
        }
        float px = ox + d.x * proj;
        float py = oy + d.y * proj;
        float pz = oz + d.z * proj;
        if (px * px + py * py + pz * pz > b.br * b.br) {
            return -1f;
        }
        float cy = FastMath.cos(b.yaw);
        float sy = FastMath.sin(b.yaw);
        float lox = ox * cy - oz * sy;
        float loz = ox * sy + oz * cy;
        float ldx = d.x * cy - d.z * sy;
        float ldz = d.x * sy + d.z * cy;
        float tmin = -Float.MAX_VALUE;
        float tmax = Float.MAX_VALUE;
        float t0, t1, inv;

        if (Math.abs(ldx) < 1e-7f) {
            if (Math.abs(lox) > b.hx) {
                return -1f;
            }
        } else {
            inv = 1f / ldx;
            t0 = (-b.hx - lox) * inv;
            t1 = (b.hx - lox) * inv;
            if (t0 > t1) {
                float tmp = t0;
                t0 = t1;
                t1 = tmp;
            }
            tmin = Math.max(tmin, t0);
            tmax = Math.min(tmax, t1);
            if (tmin > tmax) {
                return -1f;
            }
        }
        if (Math.abs(d.y) < 1e-7f) {
            if (Math.abs(oy) > b.hy) {
                return -1f;
            }
        } else {
            inv = 1f / d.y;
            t0 = (-b.hy - oy) * inv;
            t1 = (b.hy - oy) * inv;
            if (t0 > t1) {
                float tmp = t0;
                t0 = t1;
                t1 = tmp;
            }
            tmin = Math.max(tmin, t0);
            tmax = Math.min(tmax, t1);
            if (tmin > tmax) {
                return -1f;
            }
        }
        if (Math.abs(ldz) < 1e-7f) {
            if (Math.abs(loz) > b.hz) {
                return -1f;
            }
        } else {
            inv = 1f / ldz;
            t0 = (-b.hz - loz) * inv;
            t1 = (b.hz - loz) * inv;
            if (t0 > t1) {
                float tmp = t0;
                t0 = t1;
                t1 = tmp;
            }
            tmin = Math.max(tmin, t0);
            tmax = Math.min(tmax, t1);
            if (tmin > tmax) {
                return -1f;
            }
        }
        if (tmax < 0f || tmin > maxDist) {
            return -1f;
        }
        return tmin < 0f ? 0f : tmin;
    }

    private Material lit(ColorRGBA color, float shininess) {
        return Gfx.lit(am, color, color.mult(0.30f),
                new ColorRGBA(0.03f, 0.03f, 0.028f, 1f), shininess);
    }

    /** World units covered by one masonry texture repeat. */
    private static final float STONE_TILE = 7.2f;

    private static void scaleUV(Mesh mesh, float su, float sv) {
        VertexBuffer uv = mesh.getBuffer(VertexBuffer.Type.TexCoord);
        if (uv == null) {
            return;
        }
        int n = uv.getNumElements();
        float[] out = new float[n * 2];
        java.nio.FloatBuffer src = (java.nio.FloatBuffer) uv.getDataReadOnly();
        for (int i = 0; i < n; i++) {
            out[i * 2] = src.get(i * 2) * su;
            out[i * 2 + 1] = src.get(i * 2 + 1) * sv;
        }
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(out));
    }

    /** Box with masonry UVs scaled per face so courses keep a constant real-world size. */
    private static Geometry stoneBox(String name, float hx, float hy, float hz, Material mat,
                                     Node parent, float x, float y, float z) {
        Mesh mesh = new com.jme3.scene.shape.Box(hx, hy, hz);
        VertexBuffer uv = mesh.getBuffer(VertexBuffer.Type.TexCoord);
        if (uv != null) {
            float[][] dims = {
                    {2f * hx, 2f * hy}, {2f * hx, 2f * hy},
                    {2f * hz, 2f * hy}, {2f * hz, 2f * hy},
                    {2f * hx, 2f * hz}, {2f * hx, 2f * hz}};
            int n = uv.getNumElements();
            float[] out = new float[n * 2];
            java.nio.FloatBuffer src = (java.nio.FloatBuffer) uv.getDataReadOnly();
            for (int i = 0; i < n; i++) {
                int f = Math.min(i / 4, 5);
                out[i * 2] = src.get(i * 2) * dims[f][0] / STONE_TILE;
                out[i * 2 + 1] = src.get(i * 2 + 1) * dims[f][1] / STONE_TILE;
            }
            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(out));
        }
        return Gfx.box(name, mesh, mat, parent, x, y, z);
    }

    private Geometry stoneCylinder(String name, float radius, float height, Material mat,
                                   Node parent, float x, float y, float z) {
        Mesh mesh = new Cylinder(6, 16, radius, height, true);
        scaleUV(mesh, FastMath.TWO_PI * radius / STONE_TILE, height / STONE_TILE);
        Geometry g = new Geometry(name, mesh);
        g.setMaterial(mat);
        g.rotate(-FastMath.HALF_PI, 0f, 0f);
        g.setLocalTranslation(x, y, z);
        parent.attachChild(g);
        return g;
    }

    private Geometry stoneTaper(String name, float rTop, float rBottom, float height,
                                Material mat, Node parent, float x, float y, float z) {
        Mesh mesh = new Cylinder(2, 16, rTop, rBottom, height, true, true);
        scaleUV(mesh, FastMath.PI * (rTop + rBottom) / STONE_TILE, height / STONE_TILE);
        Geometry g = new Geometry(name, mesh);
        g.setMaterial(mat);
        g.rotate(-FastMath.HALF_PI, 0f, 0f);
        g.setLocalTranslation(x, y, z);
        parent.attachChild(g);
        return g;
    }

    private Material glow(ColorRGBA color) {
        Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
        m.getAdditionalRenderState().setDepthWrite(false);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        return m;
    }

    private BatchNode batch(String name) {
        BatchNode n = new BatchNode(name);
        root.attachChild(n);
        return n;
    }

    private void finish(BatchNode b) {
        b.batch();
        b.setShadowMode(RenderQueue.ShadowMode.Cast);
    }

    private Geometry vertical(String name, float radius, float height, Material mat,
                              Node parent, float x, float y, float z) {
        Geometry g = new Geometry(name, new Cylinder(6, 12, radius, height, true));
        g.setMaterial(mat);
        // jME's Cylinder runs along Z; stand it up on Y.
        g.rotate(-FastMath.HALF_PI, 0f, 0f);
        g.setLocalTranslation(x, y, z);
        parent.attachChild(g);
        return g;
    }

    private Geometry ball(String name, float radius, Material mat, Node parent,
                          float x, float y, float z) {
        Geometry g = new Geometry(name, new Sphere(7, 9, radius));
        g.setMaterial(mat);
        g.setLocalTranslation(x, y, z);
        parent.attachChild(g);
        return g;
    }

    // ---------------------------------------------------------------- graces

    private void buildGraces() {
        for (int i = 0; i < WorldData.GRACES.length; i++) {
            WorldData.GraceDef g = WorldData.GRACES[i];
            float h = terrain.height(g.x(), g.z());
            Node n = new Node("grace-" + g.id());
            n.setLocalTranslation(g.x(), h, g.z());
            root.attachChild(n);

            if (g.id().equals("cave-exit")) {
                // 洞口篝火：石钵 + 火焰 + 保留赐福光轮
                stoneTaper("caveBonfireBowl", 0.78f, 0.5f, 0.42f, stoneDark, n, 0f, 0.24f, 0f);
                stoneCylinder("caveBonfireBase", 0.5f, 0.3f, stoneDark, n, 0f, 0.08f, 0f);
                ball("caveBonfireFlame", 0.3f, goldGlow, n, 0f, 0.55f, 0f)
                        .setLocalScale(0.8f, 1.5f, 0.8f);
                ball("caveBonfireCore", 0.15f, goldGlow, n, 0f, 0.6f, 0f);
            } else {
                vertical("stem", 0.055f, 0.85f, wood, n, 0f, 0.42f, 0f);
                for (int k = 0; k < 6; k++) {
                    float a = FastMath.TWO_PI * k / 6f + i * 0.4f;
                    float lean = 0.22f + 0.05f * (k % 3);
                    Geometry leafG = ball("graceLeaf", 0.10f, goldGlow, n,
                            FastMath.sin(a) * lean, 0.92f + 0.06f * (k % 2),
                            FastMath.cos(a) * lean);
                    leafG.setLocalScale(0.55f, 1.9f, 0.32f);
                }
            }
            Geometry flare = ball("graceFlare", 0.42f, goldGlow, n, 0f, 1.08f, 0f);
            flare.setQueueBucket(RenderQueue.Bucket.Transparent);
            flare.setShadowMode(RenderQueue.ShadowMode.Off);
            Geometry ring = new Geometry("graceRing",
                    Gfx.torusMesh(0.78f, 0.022f, 40, 6));
            ring.setMaterial(goldGlow);
            ring.setLocalTranslation(0f, 0.04f, 0f);
            ring.setQueueBucket(RenderQueue.Bucket.Transparent);
            ring.setShadowMode(RenderQueue.ShadowMode.Off);
            n.attachChild(ring);
            graces.add(new GraceGlow(n, flare, i * 0.9f, 1.08f));
        }
    }

    /** 按赐福 id 取视觉节点（洞口篝火由 WorldApp 控制点亮/隐藏）。 */
    Node graceNode(String id) {
        for (WorldData.GraceDef g : WorldData.GRACES) {
            if (g.id().equals(id)) {
                return (Node) root.getChild("grace-" + id);
            }
        }
        return null;
    }

    void update(float dt) {
        time += dt;
        for (GraceGlow g : graces) {
            float bob = FastMath.sin(time * 2.1f + g.phase) * 0.06f;
            g.flare.setLocalTranslation(0f, g.baseY + bob, 0f);
            float pulse = 0.88f + 0.12f * FastMath.sin(time * 3.7f + g.phase);
            g.flare.setLocalScale(pulse);
        }
        if (gateOpening && gateOpenT < 1f) {
            gateOpenT = Math.min(1f, gateOpenT + dt * 0.45f);
            float k = MathX.smooth(gateOpenT);
            for (int i = 0; i < gateLeaves.length; i++) {
                if (gateLeaves[i] == null) {
                    continue;
                }
                float x = gateClosedX[i] + (gateOpenX[i] - gateClosedX[i]) * k;
                gateLeaves[i].setLocalTranslation(x,
                        gateLeaves[i].getLocalTranslation().y,
                        gateLeaves[i].getLocalTranslation().z);
            }
        }
    }

    // ---------------------------------------------------------------- woods

    private void buildTrees() {
        BatchNode trees = batch("trees");
        int placed = 0;
        for (int attempt = 0; attempt < 1400 && placed < 240; attempt++) {
            float x = -660f + rnd.nextFloat() * 1320f;
            float z = -660f + rnd.nextFloat() * 1320f;
            boolean mist = x > 260f && z > 220f;
            boolean storm = z < -230f && x < 200f;
            float density = mist ? 0.94f : (storm ? 0.36f : 0.5f);
            if (rnd.nextFloat() > density) {
                continue;
            }
            if (!openGround(x, z, 7f, 0.66f)) {
                continue;
            }
            if (WorldData.roadDistance(x, z) < 8.5f) {
                continue;
            }
            float h = terrain.height(x, z);
            float s = 0.75f + rnd.nextFloat() * 0.85f;
            Material foliage = mist ? leafMist : leaf;
            float trunkH = (4.6f + rnd.nextFloat() * 3.4f) * s;
            vertical("trunk", 0.24f * s, trunkH, bark, trees, x, h + trunkH * 0.46f, z);
            blocker(x, h + trunkH * 0.46f, z, 0.24f * s, trunkH * 0.5f, 0.24f * s, 0f);
            solidRound(x, h + trunkH * 0.5f, z, 0.3f * s, trunkH * 0.5f);
            int lobes = 2 + rnd.nextInt(2);
            for (int k = 0; k < lobes; k++) {
                float ly = h + trunkH * (0.72f + k * 0.15f);
                float spread = (0.55f + k * 0.35f) * s;
                Geometry lobe = ball("canopy", (1.35f + rnd.nextFloat() * 0.7f) * s,
                        foliage, trees,
                        x + (rnd.nextFloat() - 0.5f) * spread,
                        ly, z + (rnd.nextFloat() - 0.5f) * spread);
                lobe.setLocalScale(1f, 0.78f + rnd.nextFloat() * 0.3f, 1f);
            }
            placed++;
        }
        finish(trees);
    }

    /** 开局试炼洞窟占位：散布的树石营地不要长进洞里。 */
    private boolean inCaveZone(float x, float z) {
        return Math.abs(x) < 24f && Math.abs(z - 348f) < 26f;
    }

    // ---------------------------------------------------------------- tutorial cave

    /** 洞窟外壳：封闭地下洞窟（石板地面 + 金圈 + 火盆 + 洞顶岩板），南向出口通道。
     *  视觉语言对齐开局竞技场（stone_tiles / rock_wall / 金圈 / 火盆）。返回封住出口的巨石。 */
    Geometry buildCave(float cx, float cz, float floorY) {
        Material floorMat = Gfx.lit(am, new ColorRGBA(0.52f, 0.48f, 0.42f, 1f),
                new ColorRGBA(0.16f, 0.15f, 0.13f, 1f),
                new ColorRGBA(0.08f, 0.075f, 0.07f, 1f), 14f);
        Gfx.mapped(floorMat, am, "stone_tiles", 0.9f);
        Material rock = Gfx.lit(am, new ColorRGBA(0.38f, 0.35f, 0.31f, 1f),
                new ColorRGBA(0.13f, 0.12f, 0.11f, 1f),
                new ColorRGBA(0.05f, 0.05f, 0.045f, 1f), 10f);
        Gfx.mapped(rock, am, "rock_wall", 1.1f);
        Material darkRock = Gfx.lit(am, new ColorRGBA(0.22f, 0.20f, 0.18f, 1f),
                new ColorRGBA(0.07f, 0.065f, 0.06f, 1f),
                new ColorRGBA(0.03f, 0.03f, 0.028f, 1f), 8f);
        Gfx.mapped(darkRock, am, "rock_wall", 1.4f);
        Material gold = Gfx.lit(am, new ColorRGBA(0.72f, 0.55f, 0.22f, 1f),
                new ColorRGBA(0.30f, 0.23f, 0.09f, 1f),
                new ColorRGBA(1f, 0.86f, 0.5f, 1f), 64f);
        Material flameA = glow(new ColorRGBA(1f, 0.46f, 0.1f, 1f));
        Material flameB = glow(new ColorRGBA(1f, 0.86f, 0.4f, 1f));

        BatchNode b = batch("tutorialCave");
        java.util.Random crnd = new java.util.Random(77);

        // 全包暗色穹顶：堵住巨石间与洞顶石板缝漏进的天光/外部地形。
        // 不进合批（避免整体投影），双面无光照纯剪影，从洞外看是一座暗色岩丘。
        Material shellMat = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        shellMat.setColor("Color", new ColorRGBA(0.035f, 0.033f, 0.03f, 1f));
        shellMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        Geometry dome = new Geometry("caveDome", new Sphere(12, 20, 24f));
        dome.setMaterial(shellMat);
        dome.setLocalTranslation(cx, floorY - 6f, cz);
        dome.setShadowMode(RenderQueue.ShadowMode.Off);
        root.attachChild(dome);

        stoneCylinder("caveFloor", 18.9f, 0.6f, floorMat, b, cx, floorY - 0.26f, cz);
        Gfx.torus("caveRing", 13.5f, 0.09f, gold, b, cx, floorY + 0.07f, cz);
        Gfx.torus("caveRingIn", 6.5f, 0.07f, gold, b, cx, floorY + 0.06f, cz);

        float south = -FastMath.HALF_PI;
        int n = 24;
        for (int i = 0; i < n; i++) {
            float a = (i + 0.5f) / n * FastMath.TWO_PI;
            if (Math.abs(MathX.wrap(a - south)) < 0.5f) {
                continue;
            }
            float r = 18.6f + crnd.nextFloat() * 1.2f;
            float w = 3.4f + crnd.nextFloat() * 1.8f;
            float h = 8f + crnd.nextFloat() * 6f;
            float d = 2.2f + crnd.nextFloat() * 1.2f;
            float wy = floorY + h * 0.5f - 1.2f;
            stoneBox("caveWall", w * 0.5f, h * 0.5f, d * 0.5f,
                    crnd.nextBoolean() ? rock : darkRock, b,
                    cx + FastMath.cos(a) * r, wy, cz + FastMath.sin(a) * r)
                    .rotate((crnd.nextFloat() - 0.5f) * 0.12f, -a - FastMath.HALF_PI,
                            (crnd.nextFloat() - 0.5f) * 0.12f);
            blocker(cx + FastMath.cos(a) * r, wy, cz + FastMath.sin(a) * r,
                    w * 0.5f + 0.8f, h * 0.5f, d * 0.5f + 0.8f, -a - FastMath.HALF_PI);
        }

        stoneBox("caveRoofCap", 12.5f, 0.7f, 12.5f, darkRock, b, cx, floorY + 12.7f, cz);
        // 岩壁内圈补隙巨石，堵住外圈之间的缝
        for (int i = 0; i < 18; i++) {
            float a = i / 18f * FastMath.TWO_PI + 0.09f;
            if (Math.abs(MathX.wrap(a - south)) < 0.36f) {
                continue;
            }
            float r = 17.4f + crnd.nextFloat() * 0.7f;
            float w = 5.5f + crnd.nextFloat() * 2f;
            float h = 10f + crnd.nextFloat() * 4f;
            float wy = floorY + h * 0.5f - 1.4f;
            stoneBox("caveWallIn", w * 0.5f, h * 0.5f, 1.6f,
                    crnd.nextBoolean() ? rock : darkRock, b,
                    cx + FastMath.cos(a) * r, wy, cz + FastMath.sin(a) * r)
                    .rotate((crnd.nextFloat() - 0.5f) * 0.1f, -a - FastMath.HALF_PI,
                            (crnd.nextFloat() - 0.5f) * 0.1f);
            blocker(cx + FastMath.cos(a) * r, wy, cz + FastMath.sin(a) * r,
                    w * 0.5f + 0.6f, h * 0.5f, 1.8f, -a - FastMath.HALF_PI);
        }
        int rn = 12;
        for (int i = 0; i < rn; i++) {
            float a = (i + 0.5f) / rn * FastMath.TWO_PI;
            if (Math.abs(MathX.wrap(a - south)) < 0.42f) {
                continue;
            }
            float r = 15.6f;
            float ry = floorY + 12.4f + (crnd.nextFloat() - 0.5f) * 0.7f;
            stoneBox("caveRoof", 6.4f, 0.5f, 3.4f,
                    crnd.nextBoolean() ? rock : darkRock, b,
                    cx + FastMath.cos(a) * r, ry, cz + FastMath.sin(a) * r)
                    .rotate((crnd.nextFloat() - 0.5f) * 0.14f, -a - FastMath.HALF_PI,
                            (crnd.nextFloat() - 0.5f) * 0.14f);
        }
        for (int i = 0; i < 8; i++) {
            float a = crnd.nextFloat() * FastMath.TWO_PI;
            float r = 4f + crnd.nextFloat() * 10f;
            float len = 1.4f + crnd.nextFloat() * 2.2f;
            stoneTaper("caveStalactite", 1.0f + crnd.nextFloat() * 0.5f, 0.25f, len, darkRock, b,
                    cx + FastMath.cos(a) * r, floorY + 11.9f - len * 0.5f,
                    cz + FastMath.sin(a) * r);
        }

        for (int i = 0; i < 4; i++) {
            float a = FastMath.QUARTER_PI + i * FastMath.HALF_PI;
            float x = cx + FastMath.cos(a) * 14.2f;
            float z = cz + FastMath.sin(a) * 14.2f;
            stoneCylinder("caveBrazier", 0.3f, 1.45f, darkRock, b, x, floorY + 0.73f, z);
            stoneTaper("caveBowl", 0.62f, 0.28f, 0.4f, darkRock, b, x, floorY + 1.6f, z);
            ball("caveFlame", 0.34f, flameA, b, x, floorY + 1.98f, z);
            ball("caveFlameCore", 0.17f, flameB, b, x, floorY + 2.0f, z);
            blocker(x, floorY + 0.9f, z, 0.45f, 0.9f, 0.45f, 0f);
        }

        for (int i = 0; i < 8; i++) {
            float a = crnd.nextFloat() * FastMath.TWO_PI;
            float rr = 10.5f + crnd.nextFloat() * 6f;
            float s = 0.5f + crnd.nextFloat() * 0.9f;
            stoneBox("caveDebris", s, s * 0.6f, s, crnd.nextBoolean() ? rock : darkRock, b,
                    cx + FastMath.cos(a) * rr, floorY + s * 0.4f,
                    cz + FastMath.sin(a) * rr)
                    .rotate(0f, crnd.nextFloat() * FastMath.TWO_PI, 0f);
        }
        for (int s = -1; s <= 1; s += 2) {
            float px = cx + s * 9.5f;
            float pz = cz + s * 2.5f;
            stoneBox("cavePillarBase", 0.92f, 0.22f, 0.92f, darkRock, b, px, floorY + 0.22f, pz);
            stoneCylinder("cavePillar", 0.54f, 4.6f, floorMat, b, px, floorY + 2.7f, pz);
            stoneBox("cavePillarFall", 0.6f, 0.35f, 0.6f, rock, b,
                    px + s * 0.9f, floorY + 0.4f, pz + 0.7f);
            blocker(px, floorY + 2.4f, pz, 0.7f, 2.4f, 0.7f, 0f);
        }

        for (int s = -1; s <= 1; s += 2) {
            stoneTaper("caveJaw", 1.8f, 3.8f, 13.5f, rock, b,
                    cx + s * 3.6f, floorY + 5.2f, cz - 17.6f);
            blocker(cx + s * 3.6f, floorY + 5.2f, cz - 17.6f, 3.2f, 6.6f, 3.2f, 0f);
        }
        stoneBox("caveCorridor", 2.6f, 4.2f, 2.6f, rock, b,
                cx - 3.5f, floorY + 3f, cz - 21.5f);
        stoneBox("caveCorridor", 2.4f, 3.6f, 2.4f, rock, b,
                cx + 3.3f, floorY + 2.4f, cz - 23f);
        stoneBox("caveLintel", 4.4f, 1.3f, 2.6f, rock, b,
                cx, floorY + 7.4f, cz - 21f);
        blocker(cx - 3.5f, floorY + 3f, cz - 21.5f, 2.8f, 4.2f, 2.8f, 0f);
        blocker(cx + 3.3f, floorY + 2.4f, cz - 23f, 2.6f, 3.6f, 2.6f, 0f);
        blocker(cx, floorY + 7.4f, cz - 21f, 4.6f, 1.4f, 2.8f, 0f);

        finish(b);
        Geometry seal = stoneTaper("caveSeal", 1.7f, 2.7f, 5.2f, rock, root,
                cx, floorY + 1.8f, cz - 14.8f);
        seal.rotate(0f, 0.5f, 0f);
        return seal;
    }

    private void buildRocks() {
        BatchNode rocks = batch("rocks");
        int placed = 0;
        for (int attempt = 0; attempt < 2600 && placed < 430; attempt++) {
            float x = -660f + rnd.nextFloat() * 1320f;
            float z = -660f + rnd.nextFloat() * 1320f;
            if (!openGround(x, z, 4f, 1.25f) || WorldData.roadDistance(x, z) < 4f) {
                continue;
            }
            // 平方分布：小碎石多、大孤石少
            float s = 0.30f + rnd.nextFloat() * rnd.nextFloat() * 2.1f;
            tumble(rocks, x, z, s);
            placed++;
        }

        // 崖脚塌积带：崖线南侧 6~24m 一排大块崩石，崖壁才有"掉下来的石头"的体量
        for (int i = 0; i < 54; i++) {
            float x = -560f + rnd.nextFloat() * 690f;
            float z = WorldTerrain.scarpZ(x) + 5f + rnd.nextFloat() * 20f;
            if (!openGround(x, z, 2f, 1.7f)) {
                continue;
            }
            tumble(rocks, x, z, 1.3f + rnd.nextFloat() * 2.6f);
        }

        // 峡谷两缘的碎岩
        for (WorldData.Gorge g : WorldData.GORGES) {
            for (int i = 0; i < 16; i++) {
                float t = rnd.nextFloat();
                float px = g.x1() + (g.x2() - g.x1()) * t;
                float pz = g.z1() + (g.z2() - g.z1()) * t;
                float len = MathX.distXZ(new Vector3f(g.x1(), 0f, g.z1()),
                        new Vector3f(g.x2(), 0f, g.z2()));
                float nx = -(g.z2() - g.z1()) / len;
                float nz = (g.x2() - g.x1()) / len;
                float side = rnd.nextBoolean() ? 1f : -1f;
                float off = g.halfWidth() * (0.85f + rnd.nextFloat() * 0.5f) * side;
                float x = px + nx * off;
                float z = pz + nz * off;
                if (!openGround(x, z, 2f, 1.8f)) {
                    continue;
                }
                tumble(rocks, x, z, 0.9f + rnd.nextFloat() * 2.2f);
            }
        }

        // 塘岸石：水边零散几块半埋的石头，破掉"蓝色圆盘"的边
        for (WorldData.Pond p : WorldData.PONDS) {
            for (int i = 0; i < 7; i++) {
                float a = rnd.nextFloat() * FastMath.TWO_PI;
                float k = 0.92f + rnd.nextFloat() * 0.34f;
                float x = p.x() + FastMath.sin(a) * p.rx() * k;
                float z = p.z() + FastMath.cos(a) * p.rz() * k;
                if (!openGround(x, z, 2f, 1.6f)) {
                    continue;
                }
                tumble(rocks, x, z, 0.5f + rnd.nextFloat() * 1.1f);
            }
        }
        finish(rocks);
    }

    /** 一块半埋在地里的石头：压扁 + 随机翻滚，大块登记路障与碰撞。 */
    private void tumble(BatchNode batch, float x, float z, float s) {
        float h = terrain.height(x, z);
        float sx = 1.10f + rnd.nextFloat() * 0.45f;
        float sz = 0.82f + rnd.nextFloat() * 0.36f;
        float sy = 0.66f + rnd.nextFloat() * 0.26f;
        float cy = h + s * sy * 0.52f;
        Geometry g = ball("rock", s, rock, batch, x, cy, z);
        g.setLocalScale(sx, sy, sz);
        g.setLocalRotation(new Quaternion().fromAngles(
                rnd.nextFloat() * 0.5f, rnd.nextFloat() * FastMath.TWO_PI,
                rnd.nextFloat() * 0.5f));
        blocker(x, cy, z, s * sx, s * sy, s * sz, 0f);
        if (s > 0.85f) {
            solid(x, cy, z, s * sx * 0.86f, s * sy, s * sz * 0.86f, 0f);
        }
    }

    /** 灌木丛：三到五个压扁的低矮叶球，只做视觉、不挡路。 */
    private void buildBushes() {
        BatchNode bushes = batch("bushes");
        Material bush = lit(new ColorRGBA(0.115f, 0.175f, 0.070f, 1f), 1f);
        Material bushMist = lit(new ColorRGBA(0.085f, 0.145f, 0.075f, 1f), 1f);
        Material bushDry = lit(new ColorRGBA(0.185f, 0.155f, 0.075f, 1f), 1f);
        int placed = 0;
        for (int attempt = 0; attempt < 4200 && placed < 880; attempt++) {
            float x = -665f + rnd.nextFloat() * 1330f;
            float z = -665f + rnd.nextFloat() * 1330f;
            if (!openGround(x, z, 2f, 1.05f)) {
                continue;
            }
            if (WorldData.roadDistance(x, z) < 3.2f) {
                continue;
            }
            boolean mist = x > 250f && z > 210f;
            boolean dry = z < -300f || x < -430f;
            // 平地上灌木成簇，坡上稀疏
            float dens = 0.35f + (terrain.slope(x, z) < 0.45f ? 0.55f : 0f)
                    + (mist ? 0.45f : 0f);
            if (rnd.nextFloat() > dens) {
                continue;
            }
            float h = terrain.height(x, z);
            Material mat = mist ? bushMist : (dry ? bushDry : bush);
            float s = 0.34f + rnd.nextFloat() * 0.42f;
            int lobes = 2 + rnd.nextInt(3);
            for (int k = 0; k < lobes; k++) {
                float off = s * (0.55f + rnd.nextFloat() * 0.7f);
                float a = rnd.nextFloat() * FastMath.TWO_PI;
                Geometry lobe = ball("bush", s * (0.7f + rnd.nextFloat() * 0.5f), mat,
                        bushes, x + FastMath.sin(a) * off, h + s * 0.42f,
                        z + FastMath.cos(a) * off);
                lobe.setLocalScale(1f, 0.52f + rnd.nextFloat() * 0.2f, 1f);
            }
            placed++;
        }
        finish(bushes);
    }

    /** 枯树：风丘与雾林里的裸干枯枝，给崖线与林地加竖向剪影。 */
    private void buildDeadTrees() {
        BatchNode dead = batch("deadTrees");
        Material deadBark = lit(new ColorRGBA(0.125f, 0.108f, 0.088f, 1f), 2f);
        int placed = 0;
        for (int attempt = 0; attempt < 1400 && placed < 46; attempt++) {
            float x = -660f + rnd.nextFloat() * 1320f;
            float z = -660f + rnd.nextFloat() * 1320f;
            boolean mist = x > 240f && z > 200f;
            boolean storm = z < WorldTerrain.scarpZ(x) + 40f;
            if (!mist && !storm) {
                continue;
            }
            if (!openGround(x, z, 9f, 0.85f) || WorldData.roadDistance(x, z) < 9f) {
                continue;
            }
            float h = terrain.height(x, z);
            float s = 0.8f + rnd.nextFloat() * 0.9f;
            float th = (5.5f + rnd.nextFloat() * 4f) * s;
            vertical("deadTrunk", 0.19f * s, th, deadBark, dead,
                    x, h + th * 0.44f, z);
            blocker(x, h + th * 0.45f, z, 0.28f * s, th * 0.5f, 0.28f * s, 0f);
            solidRound(x, h + th * 0.5f, z, 0.26f * s, th * 0.5f);
            int branches = 3 + rnd.nextInt(3);
            for (int k = 0; k < branches; k++) {
                float a = rnd.nextFloat() * FastMath.TWO_PI;
                float up = 0.35f + rnd.nextFloat() * 0.75f;
                float by = h + th * (0.35f + rnd.nextFloat() * 0.55f);
                float blen = (1.1f + rnd.nextFloat() * 1.5f) * s;
                Geometry br = new Geometry("deadBranch",
                        new Cylinder(4, 6, 0.045f * s, 0.10f * s, blen, true, true));
                br.setMaterial(deadBark);
                Vector3f dir = new Vector3f(FastMath.sin(a), up, FastMath.cos(a))
                        .normalizeLocal();
                br.setLocalRotation(new Quaternion().lookAt(dir, Vector3f.UNIT_Y));
                br.setLocalTranslation(x + dir.x * blen * 0.45f, by + dir.y * blen * 0.45f,
                        z + dir.z * blen * 0.45f);
                dead.attachChild(br);
            }
            placed++;
        }
        finish(dead);
    }


    private boolean openGround(float x, float z, float roadGap, float maxSlope) {
        if (inCaveZone(x, z)) {
            return false;
        }
        if (terrain.depth(x, z) > -0.8f || terrain.slope(x, z) > maxSlope) {
            return false;
        }
        for (WorldData.GraceDef g : WorldData.GRACES) {
            if (MathX.distXZ(new Vector3f(x, 0f, z), new Vector3f(g.x(), 0f, g.z())) < 9f) {
                return false;
            }
        }
        for (WorldData.Poi poi : WorldData.POIS) {
            if (poi.kind() < 0 || poi.radius() <= 0f) {
                continue;
            }
            if (MathX.distXZ(new Vector3f(x, 0f, z),
                    new Vector3f(poi.x(), 0f, poi.z())) < poi.radius() + 5f) {
                return false;
            }
        }
        for (WorldBoss.Spec spec : WorldBoss.SPECS) {
            if (MathX.distXZ(new Vector3f(x, 0f, z),
                    new Vector3f(spec.x, 0f, spec.z)) < 20f) {
                return false;
            }
        }
        return MathX.distXZ(new Vector3f(x, 0f, z),
                new Vector3f(WorldData.SPAWN.x(), 0f, WorldData.SPAWN.z())) > 12f;
    }

    // ---------------------------------------------------------------- ruins & sites

    private void buildPois() {
        BatchNode statics = batch("sites");
        for (WorldData.Poi poi : WorldData.POIS) {
            if (poi.kind() == 0) {
                ruin(statics, poi.x(), poi.z(), poi.radius(), rnd.nextLong());
            } else if (poi.kind() == 2) {
                shack(statics, poi.x(), poi.z(), poi.radius());
            } else if (poi.kind() == 3) {
                church(statics, poi.x(), poi.z(), poi.radius());
            } else if (poi.kind() == 4) {
                evergaol(statics, poi.x(), poi.z());
            } else if (poi.kind() == 5) {
                well(statics, poi.x(), poi.z());
            } else if (poi.kind() == 8) {
                stoneCircle(statics, poi.x(), poi.z(), poi.radius());
            } else if (poi.kind() == 9) {
                mausoleum(statics, poi.x(), poi.z());
            } else if (poi.kind() == 10) {
                colossus(statics, poi.x(), poi.z());
            } else if (poi.kind() == 11) {
                brokenBridge(statics, poi.x(), poi.z());
            }
        }
        finish(statics);
    }

    private boolean nearBoss(float x, float z, float gap) {
        for (WorldBoss.Spec spec : WorldBoss.SPECS) {
            if (MathX.distXZ(new Vector3f(x, 0f, z),
                    new Vector3f(spec.x, 0f, spec.z)) < gap) {
                return true;
            }
        }
        return false;
    }

    private void ruin(BatchNode p, float cx, float cz, float radius, long seed) {
        Random r = new Random(seed);
        float h = terrain.height(cx, cz);
        int walls = 3 + r.nextInt(3) + (radius > 26f ? 3 : 0);
        for (int i = 0; i < walls; i++) {
            float a = FastMath.TWO_PI * i / walls + r.nextFloat() * 0.4f;
            float w = Math.min(radius * (0.55f + r.nextFloat() * 0.5f), 8f);
            float hgt = 1.7f + r.nextFloat() * 2.6f;
            float px = cx + FastMath.sin(a) * radius * 0.55f;
            float pz = cz + FastMath.cos(a) * radius * 0.55f;
            if (nearBoss(px, pz, 17f)) {
                continue;
            }
            float gh = terrain.height(px, pz);
            float wallYaw = a + FastMath.HALF_PI;
            // 墙段等比缩放到目标高度后宽度只有 hgt 那么宽，单段撑不出原设计的 w，
            // 所以按 w 拆成 1~3 段沿墙方向并排（不额外消耗随机数）
            float k = gltf.fitHeight("wall", hgt);
            float segW = 4f * k;
            int segs = Math.max(1, Math.min(3, Math.round(w / segW)));
            float cosY = FastMath.cos(wallYaw);
            float sinY = -FastMath.sin(wallYaw);
            Vector3f seg = null;
            for (int j = 0; j < segs; j++) {
                float off = (j - (segs - 1) * 0.5f) * segW;
                float sx = px + cosY * off;
                float sz = pz + sinY * off;
                Vector3f d = gltfAt(p, "wall", sx, sz, wallYaw, hgt);
                if (d == null) {
                    break;
                }
                boxColliders(sx, sz, terrain.height(sx, sz), d, wallYaw);
                seg = d;
            }
            if (seg == null) {
                Geometry wall = Gfx.box("ruinWall", w, hgt, 0.75f, stone, p, px, gh + hgt * 0.5f, pz);
                wall.rotate(0f, wallYaw, 0f);
                blocker(px, gh + hgt * 0.5f, pz, w, hgt, 0.75f, wallYaw);
                solid(px, gh + hgt * 0.5f, pz, w, hgt, 0.75f, wallYaw);
            }
            if (r.nextBoolean()) {
                Gfx.box("ruinBreak", w * 0.3f, 0.4f, 0.82f, stone, p,
                        px + FastMath.sin(a + 1.57f) * w * 0.4f, gh + hgt + 0.1f,
                        pz + FastMath.cos(a + 1.57f) * w * 0.4f);
            }
        }
        int columns = 2 + r.nextInt(4);
        for (int i = 0; i < columns; i++) {
            float a = r.nextFloat() * FastMath.TWO_PI;
            float d = radius * (0.25f + r.nextFloat() * 0.5f);
            float px = cx + FastMath.sin(a) * d;
            float pz = cz + FastMath.cos(a) * d;
            if (nearBoss(px, pz, 15f)) {
                continue;
            }
            float gh = terrain.height(px, pz);
            float ch = 2.6f + r.nextFloat() * 4.8f;
            Vector3f col = gltfAt(p, "pillar", px, pz, 0f, ch, 0.80f);
            if (col != null) {
                roundColliders(px, pz, gh, col);
            } else {
                vertical("ruinColumn", 0.55f, ch, stone, p, px, gh + ch * 0.5f, pz);
                blocker(px, gh + ch * 0.5f, pz, 0.55f, ch * 0.5f, 0.55f, 0f);
                solidRound(px, gh + ch * 0.5f, pz, 0.55f, ch * 0.5f);
            }
        }
        // a surviving arch
        if (r.nextFloat() < 0.7f) {
            float px = cx + radius * 0.4f;
            float pz = cz - radius * 0.3f;
            if (!nearBoss(px, pz, 18f)) {
                float gh = terrain.height(px, pz);
                Gfx.box("archL", 0.6f, 2.8f, 0.6f, stone, p, px - 2.2f, gh + 2.8f, pz);
                Gfx.box("archR", 0.6f, 2.8f, 0.6f, stone, p, px + 2.2f, gh + 2.8f, pz);
                Gfx.box("archTop", 2.9f, 0.55f, 0.7f, stone, p, px, gh + 5.7f, pz);
                blocker(px - 2.2f, gh + 2.8f, pz, 0.6f, 2.8f, 0.6f, 0f);
                blocker(px + 2.2f, gh + 2.8f, pz, 0.6f, 2.8f, 0.6f, 0f);
                blocker(px, gh + 5.7f, pz, 2.9f, 0.55f, 0.7f, 0f);
                solid(px - 2.2f, gh + 2.8f, pz, 0.6f, 2.8f, 0.6f, 0f);
                solid(px + 2.2f, gh + 2.8f, pz, 0.6f, 2.8f, 0.6f, 0f);
            }
        }
    }

    // ---------------------------------------------------------------- 法环式大地标

    /** 风蚀石阵：九根巨石围环（两根已倒伏），中心一座祭坛。 */
    private void stoneCircle(BatchNode p, float cx, float cz, float radius) {
        Random r = new Random(0x51C1EL);
        float base = terrain.height(cx, cz);
        int count = 9;
        for (int i = 0; i < count; i++) {
            float a = FastMath.TWO_PI * i / count + 0.14f;
            float d = radius * (0.58f + 0.07f * r.nextFloat());
            float px = cx + FastMath.sin(a) * d;
            float pz = cz + FastMath.cos(a) * d;
            float gh = terrain.height(px, pz);
            float hgt = 3.6f + r.nextFloat() * 3.2f;
            boolean fallen = i == 2 || i == 6;
            float tilt = fallen ? 1.42f : (r.nextFloat() - 0.5f) * 0.14f;
            float y = gh + (fallen ? 0.8f : hgt * 0.5f);
            stoneBox("menhir", 0.9f, hgt * 0.5f, 0.6f, stone, p, px, y, pz)
                    .rotate(0f, a, tilt);
            blocker(px, y, pz, 0.9f, fallen ? 0.8f : hgt * 0.5f, 0.6f, a);
            solid(px, y, pz, 0.9f, fallen ? 0.8f : hgt * 0.5f, 0.6f, a);
        }
        stoneBox("altarBase", 2.6f, 0.5f, 1.7f, stoneDark, p, cx, base + 0.5f, cz);
        stoneBox("altarTop", 2.2f, 0.24f, 1.4f, stone, p, cx, base + 1.24f, cz);
        blocker(cx, base + 0.85f, cz, 2.6f, 0.85f, 1.7f, 0f);
        solid(cx, base + 0.85f, cz, 2.6f, 0.85f, 1.7f, 0f);
        Geometry flame = ball("altarFlame", 0.34f, goldGlow, p, cx, base + 1.75f, cz);
        flame.setLocalScale(0.8f, 1.6f, 0.8f);
        flame.setQueueBucket(RenderQueue.Bucket.Transparent);
        flame.setShadowMode(RenderQueue.ShadowMode.Off);
        for (int i = 0; i < 7; i++) {
            float a = r.nextFloat() * FastMath.TWO_PI;
            float d = radius * (0.25f + 0.5f * r.nextFloat());
            float px = cx + FastMath.sin(a) * d;
            float pz = cz + FastMath.cos(a) * d;
            Gfx.box("menhirRubble", 0.5f + r.nextFloat() * 0.5f, 0.24f,
                    0.4f + r.nextFloat() * 0.4f, stone, p, px,
                    terrain.height(px, pz) + 0.2f, pz);
        }
    }

    /** 英雄陵墓：三级台基上的柱廊主殿，屋脊塌了一半，门前两尊残像。 */
    private void mausoleum(BatchNode p, float cx, float cz) {
        float base = terrain.height(cx, cz);
        stoneBox("mausPlinth0", 19f, 0.6f, 14f, stoneDark, p, cx, base + 0.6f, cz);
        stoneBox("mausPlinth1", 16.6f, 0.55f, 12.2f, stone, p, cx, base + 1.75f, cz);
        stoneBox("mausPlinth2", 14.2f, 0.5f, 10.4f, stone, p, cx, base + 2.8f, cz);
        blocker(cx, base + 1.4f, cz, 19f, 1.4f, 14f, 0f);
        solid(cx, base + 1.4f, cz, 19f, 1.4f, 14f, 0f);

        float floorY = base + 3.3f;
        float hallH = 8.2f;
        float hallZ = cz - 1.2f;
        stoneBox("mausHall", 9.6f, hallH, 6.4f, stone, p, cx, floorY + hallH, hallZ);
        blocker(cx, floorY + hallH, hallZ, 9.6f, hallH, 6.4f, 0f);
        solid(cx, floorY + hallH, hallZ, 9.6f, hallH, 6.4f, 0f);
        // 门洞：贴在殿前的深色内凹
        stoneBox("mausDoor", 2.2f, 3.6f, 0.5f, stoneDark, p, cx,
                floorY + 3.6f, hallZ + 6.6f);
        // 正面柱廊
        for (int i = -3; i <= 3; i++) {
            if (i == 0) {
                continue;
            }
            float px = cx + i * 4.6f;
            float pz = hallZ + 8.2f;
            stoneCylinder("mausColumn", 0.85f, 7.4f, stone, p, px,
                    floorY + 3.7f, pz);
            blocker(px, floorY + 3.7f, pz, 0.85f, 3.7f, 0.85f, 0f);
            solidRound(px, floorY + 3.7f, pz, 0.85f, 3.7f);
        }
        stoneBox("mausLintel", 14.4f, 0.6f, 1.1f, stone, p, cx,
                floorY + 7.9f, hallZ + 8.2f);
        blocker(cx, floorY + 7.9f, hallZ + 8.2f, 14.4f, 0.6f, 1.1f, 0f);
        // 塌掉的屋脊：只留半边屋顶与几块悬着的石板
        stoneBox("mausRoofL", 9.6f, 0.7f, 3.4f, stone, p, cx - 3.1f,
                floorY + hallH * 2f + 0.9f, hallZ - 2.6f);
        stoneBox("mausRoofR", 6.2f, 0.6f, 2.2f, stone, p, cx + 5.4f,
                floorY + hallH * 2f + 0.6f, hallZ + 2.2f);
        // 塌落的檐板：落在台基上，不是悬在半空
        Geometry slab = stoneBox("mausRoofFall", 3.2f, 0.35f, 2.4f, stone, p,
                cx - 4.2f, floorY + 0.35f, cz + 11.2f);
        slab.rotate(0f, 0.5f, 0.1f);
        // 门前残像 + 长明灯：站在台基外的神道上，不从台面里长出来
        for (int i = -1; i <= 1; i += 2) {
            float px = cx + i * 6.2f;
            float pz = cz + 16.5f;
            float gh = terrain.height(px, pz);
            stoneBox("mausStatueBody", 1.6f, 2.9f, 1.2f, stone, p, px,
                    gh + 2.9f, pz);
            Geometry headG = ball("mausStatueHead", 0.85f, stone, p, px,
                    gh + 6.7f, pz);
            headG.setLocalScale(1f, 1.1f, 1f);
            headG.rotate(0f, i * 0.4f, i * 0.18f);
            blocker(px, gh + 2.9f, pz, 1.6f, 2.9f, 1.2f, 0f);
            solid(px, gh + 2.9f, pz, 1.6f, 2.9f, 1.2f, 0f);
            Geometry lamp = ball("mausLamp", 0.32f, blueGlow, p, px,
                    gh + 8.0f, pz);
            lamp.setQueueBucket(RenderQueue.Bucket.Transparent);
            lamp.setShadowMode(RenderQueue.ShadowMode.Off);
        }
    }

    /** 坠落巨像：半埋进土里的无头石像，断臂与巨剑散落四周。 */
    private void colossus(BatchNode p, float cx, float cz) {
        float base = terrain.height(cx, cz);
        stoneBox("colossusPlinth", 12.5f, 1.4f, 9f, stoneDark, p, cx, base + 1.1f, cz);
        blocker(cx, base + 1.1f, cz, 12.5f, 1.4f, 9f, 0f);
        solid(cx, base + 1.1f, cz, 12.5f, 1.4f, 9f, 0f);
        // 躯干：斜躺着，胸口以上已经没了
        Geometry torso = stoneBox("colossusTorso", 4.6f, 3.1f, 8.2f, stone, p,
                cx, base + 4.6f, cz - 2.4f);
        torso.rotate(-0.22f, 0.38f, 0.20f);
        blocker(cx, base + 4.6f, cz - 2.4f, 5.2f, 3.4f, 8.6f, 0.38f);
        solid(cx, base + 4.6f, cz - 2.4f, 5.2f, 3.4f, 8.6f, 0.38f);
        // 腰甲与肩甲碎块
        stoneBox("colossusHip", 5.4f, 1.1f, 3.4f, stoneDark, p, cx + 0.4f,
                base + 2.2f, cz + 4.6f);
        // 滚落的头颅：落在台基东侧的地面上，不与台基体相交
        float headX = cx + 14.8f;
        float headZ = cz + 7.2f;
        float headGh = terrain.height(headX, headZ);
        Geometry head = ball("colossusHead", 3.1f, stone, p, headX,
                headGh + 1.2f, headZ);
        head.setLocalScale(1.1f, 0.85f, 1f);
        head.rotate(0.3f, 0.7f, 0.2f);
        solidRound(headX, headGh + 1.2f, headZ, 3.1f, 1.2f);
        // 断臂：搭在西侧台基边缘
        float armX = cx - 14.6f;
        float armY = terrain.height(armX, cz + 2f) + 1.1f;
        Geometry arm = stoneBox("colossusArm", 1.5f, 1.4f, 5.6f, stone, p,
                armX, armY, cz + 2f);
        arm.rotate(0.35f, 0.9f, 0.15f);
        solid(armX, armY, cz + 2f, 3.4f, 1.6f, 3.4f, 0.9f);
        // 插进台基的巨剑，护手卡在剑身上端
        Geometry blade = stoneBox("colossusBlade", 0.9f, 0.3f, 11f, stoneDark, p,
                cx + 5.6f, base + 5.6f, cz - 12f);
        blade.rotate(0.42f, 0.25f, -0.12f);
        stoneBox("colossusGuard", 2.6f, 0.5f, 0.6f, stone, p, cx + 4.1f,
                base + 8.9f, cz - 19.5f).rotate(0.42f, 0.25f, -0.12f);
    }

    /** 断桥：横跨峡谷的高架石桥，桥心塌了一整段，桥墩直插谷底。 */
    private void brokenBridge(BatchNode p, float cx, float cz) {
        // 峡谷 1 的走向 (60,-120)→(140,-30)，桥沿垂直方向跨过去
        float gx = 80f;
        float gz = 90f;
        float glen = (float) Math.sqrt(gx * gx + gz * gz);
        float ux = gx / glen;
        float uz = gz / glen;
        float nx = uz;
        float nz = -ux;
        float yaw = (float) Math.atan2(nx, nz);
        float half = 54f;
        float rimA = terrain.height(cx + nx * half, cz + nz * half);
        float rimB = terrain.height(cx - nx * half, cz - nz * half);
        float deck = Math.max(rimA, rimB) + 6.5f;
        float seg = 11.5f;
        for (int i = -4; i <= 4; i++) {
            if (i == 0 || i == -1) {
                // 桥心两段整段塌掉
                continue;
            }
            float px = cx + nx * i * seg;
            float pz = cz + nz * i * seg;
            stoneBox("bridgeDeck", seg * 0.5f, 0.85f, 4.6f, stone, p, px, deck, pz)
                    .rotate(0f, yaw + FastMath.HALF_PI, 0f);
            blocker(px, deck, pz, seg * 0.5f, 0.85f, 4.6f, yaw + FastMath.HALF_PI);
            for (int k = -1; k <= 1; k += 2) {
                float wx = px + ux * 4.1f * k;
                float wz = pz + uz * 4.1f * k;
                stoneBox("bridgeParapet", seg * 0.5f, 0.75f, 0.45f, stone, p,
                        wx, deck + 1.5f, wz).rotate(0f, yaw + FastMath.HALF_PI, 0f);
            }
        }
        // 桥墩：从谷底撑到桥面
        for (int k = -1; k <= 1; k += 2) {
            float px = cx + nx * 2.5f * seg * k;
            float pz = cz + nz * 2.5f * seg * k;
            float floorY = terrain.height(px, pz);
            float hgt = Math.max(2f, deck - 0.9f - floorY);
            stoneCylinder("bridgePier", 2.4f, hgt, stoneDark, p, px,
                    floorY + hgt * 0.5f, pz);
            blocker(px, floorY + hgt * 0.5f, pz, 2.4f, hgt * 0.5f, 2.4f, 0f);
            solidRound(px, floorY + hgt * 0.5f, pz, 2.4f, hgt * 0.5f);
            // 桥墩肩部的托座
            stoneBox("bridgeCorbel", 4.2f, 1.1f, 4.2f, stone, p, px,
                    deck - 1.8f, pz).rotate(0f, yaw, 0f);
        }
        // 两端桥台
        for (int k = -1; k <= 1; k += 2) {
            float px = cx + nx * half * k;
            float pz = cz + nz * half * k;
            float gh = terrain.height(px, pz);
            float hgt = Math.max(1.2f, deck - gh + 1.4f);
            stoneBox("bridgeAbutment", 6.4f, hgt * 0.5f, 6.4f, stone, p, px,
                    gh + hgt * 0.5f, pz).rotate(0f, yaw, 0f);
            blocker(px, gh + hgt * 0.5f, pz, 6.4f, hgt * 0.5f, 6.4f, yaw);
            solid(px, gh + hgt * 0.5f, pz, 6.4f, hgt * 0.5f, 6.4f, yaw);
        }
        // 塌到谷底的桥面残块
        for (int i = 0; i < 4; i++) {
            float t = (i - 1.5f) * 6.5f;
            float px = cx + nx * t;
            float pz = cz + nz * t;
            float gh = terrain.height(px, pz);
            stoneBox("bridgeRubble", 3.4f, 0.7f, 2.6f, stone, p, px, gh + 0.6f, pz)
                    .rotate(0.16f * (i - 1.5f), yaw + i, 0.1f * i);
            blocker(px, gh + 0.6f, pz, 3.4f, 0.7f, 2.6f, yaw + i);
        }
    }

    private void shack(BatchNode p, float cx, float cz, float radius) {
        float h = terrain.height(cx, cz);
        float s = Math.max(4.5f, radius * 0.45f);
        Gfx.box("shackBase", s, 1.9f, s * 0.8f, wood, p, cx, h + 1.9f, cz);
        blocker(cx, h + 1.9f, cz, s, 1.9f, s * 0.8f, 0f);
        solid(cx, h + 1.9f, cz, s, 1.9f, s * 0.8f, 0f);
        Geometry roof = new Geometry("shackRoof", new Cylinder(4, 4, 0.1f, s * 1.2f, 1.5f, true, false));
        roof.setMaterial(roofMat());
        roof.rotate(-FastMath.HALF_PI, 0f, 0f);
        roof.setLocalTranslation(cx, h + 4.1f, cz);
        roof.rotate(0f, 0f, FastMath.QUARTER_PI);
        p.attachChild(roof);
        blocker(cx, h + 4.1f, cz, s * 1.2f, 0.75f, s * 1.2f, 0f);
        Gfx.box("door", 0.7f, 1.25f, 0.08f, iron, p, cx, h + 0.9f, cz + s * 0.82f);
        Gfx.box("step", 1.1f, 0.12f, 0.55f, stone, p, cx, h + 0.08f, cz + s * 0.9f);
        // fence
        for (int i = 0; i < 8; i++) {
            float a = i / 8f * FastMath.TWO_PI;
            Gfx.box("fence", 0.08f, 0.62f, 0.08f, wood, p,
                    cx + FastMath.sin(a) * (s + 1.2f), h + 0.55f,
                    cz + FastMath.cos(a) * (s + 1.2f));
        }
        campfire(p, cx + s * 1.4f, cz + s * 0.6f);
    }

    private Material roofMat() {
        return roof;
    }

    private void church(BatchNode p, float cx, float cz, float radius) {
        float h = terrain.height(cx, cz);
        float w = radius * 0.8f;
        float d = radius * 1.1f;
        Gfx.box("churchFloor", w, 0.35f, d, stone, p, cx, h + 0.2f, cz);
        // broken nave walls
        Gfx.box("wallL", 0.75f, 3.6f, d * 0.85f, stone, p, cx - w, h + 1.9f, cz);
        Gfx.box("wallR", 0.75f, 2.6f, d * 0.85f, stone, p, cx + w, h + 1.4f, cz);
        Gfx.box("wallBack", w, 3.0f, 0.75f, stone, p, cx, h + 1.6f, cz - d);
        blocker(cx - w, h + 1.9f, cz, 0.75f, 3.6f, d * 0.85f, 0f);
        blocker(cx + w, h + 1.4f, cz, 0.75f, 2.6f, d * 0.85f, 0f);
        blocker(cx, h + 1.6f, cz - d, w, 3.0f, 0.75f, 0f);
        solid(cx - w, h + 1.9f, cz, 0.75f, 3.6f, d * 0.85f, 0f);
        solid(cx + w, h + 1.4f, cz, 0.75f, 2.6f, d * 0.85f, 0f);
        solid(cx, h + 1.6f, cz - d, w, 3.0f, 0.75f, 0f);
        // facade with a rose window gap
        Gfx.box("facadeL", w * 0.34f, 4.1f, 0.8f, stone, p, cx - w * 0.6f, h + 2.2f, cz + d * 0.9f);
        Gfx.box("facadeR", w * 0.34f, 4.1f, 0.8f, stone, p, cx + w * 0.6f, h + 2.2f, cz + d * 0.9f);
        Gfx.box("facadeTop", w, 0.8f, 0.8f, stone, p, cx, h + 5.3f, cz + d * 0.9f);
        blocker(cx - w * 0.6f, h + 2.2f, cz + d * 0.9f, w * 0.34f, 4.1f, 0.8f, 0f);
        blocker(cx + w * 0.6f, h + 2.2f, cz + d * 0.9f, w * 0.34f, 4.1f, 0.8f, 0f);
        blocker(cx, h + 5.3f, cz + d * 0.9f, w, 0.8f, 0.8f, 0f);
        solid(cx - w * 0.6f, h + 2.2f, cz + d * 0.9f, w * 0.34f, 4.1f, 0.8f, 0f);
        solid(cx + w * 0.6f, h + 2.2f, cz + d * 0.9f, w * 0.34f, 4.1f, 0.8f, 0f);
        // buttresses
        for (int i = -1; i <= 1; i++) {
            Gfx.box("buttress", 0.55f, 2.6f, 0.9f, stone, p, cx - w - 1.1f, h + 1.4f,
                    cz + i * d * 0.55f);
            Gfx.box("buttress", 0.55f, 2.6f, 0.9f, stone, p, cx + w + 1.1f, h + 1.4f,
                    cz + i * d * 0.55f);
            blocker(cx - w - 1.1f, h + 1.4f, cz + i * d * 0.55f, 0.55f, 2.6f, 0.9f, 0f);
            blocker(cx + w + 1.1f, h + 1.4f, cz + i * d * 0.55f, 0.55f, 2.6f, 0.9f, 0f);
            solid(cx - w - 1.1f, h + 1.4f, cz + i * d * 0.55f, 0.55f, 2.6f, 0.9f, 0f);
            solid(cx + w + 1.1f, h + 1.4f, cz + i * d * 0.55f, 0.55f, 2.6f, 0.9f, 0f);
        }
        // altar
        Gfx.box("altar", 1.6f, 0.65f, 0.9f, stoneDark, p, cx, h + 0.9f, cz - d * 0.45f);
    }

    private void evergaol(BatchNode p, float cx, float cz) {
        float h = terrain.height(cx, cz);
        for (int i = 0; i < 10; i++) {
            float a = FastMath.TWO_PI * i / 10f;
            float px = cx + FastMath.sin(a) * 13f;
            float pz = cz + FastMath.cos(a) * 13f;
            float gh = terrain.height(px, pz);
            vertical("gaolPillar", 0.75f, 5.2f, stone, p, px, gh + 2.6f, pz);
            blocker(px, gh + 2.6f, pz, 0.75f, 2.6f, 0.75f, 0f);
            solidRound(px, gh + 2.6f, pz, 0.75f, 2.6f);
            ball("gaolCap", 0.85f, stone, p, px, gh + 5.3f, pz);
        }
        Geometry disc = new Geometry("gaolFloor", new Cylinder(2, 40, 12f, 0.18f, true));
        disc.rotate(-FastMath.HALF_PI, 0f, 0f);
        disc.setMaterial(stone);
        disc.setLocalTranslation(cx, h + 0.1f, cz);
        p.attachChild(disc);
        Geometry glow = ball("gaolGlow", 1.2f, blueGlow, root, cx, h + 0.2f, cz);
        glow.setLocalScale(2.6f, 0.1f, 2.6f);
        glow.setQueueBucket(RenderQueue.Bucket.Transparent);
    }

    private void well(BatchNode p, float cx, float cz) {
        float h = terrain.height(cx, cz);
        Geometry ring = new Geometry("wellRing", new Cylinder(2, 28, 4.4f, 1.15f, true));
        ring.rotate(-FastMath.HALF_PI, 0f, 0f);
        ring.setMaterial(stone);
        ring.setLocalTranslation(cx, h + 0.57f, cz);
        p.attachChild(ring);
        // 井口整圈当实心（井壁 4.4m 半径，里面是深洞，不该走得进去）
        solidRound(cx, h + 0.58f, cz, 4.3f, 0.58f);
        Geometry inner = new Geometry("wellDark", new Cylinder(2, 28, 3.5f, 0.12f, true));
        inner.rotate(-FastMath.HALF_PI, 0f, 0f);
        inner.setMaterial(stoneDark);
        inner.setLocalTranslation(cx, h + 0.08f, cz);
        p.attachChild(inner);
        vertical("wellPostL", 0.18f, 3.4f, wood, p, cx - 3.3f, h + 1.7f, cz);
        vertical("wellPostR", 0.18f, 3.4f, wood, p, cx + 3.3f, h + 1.7f, cz);
        Gfx.box("wellBeam", 3.5f, 0.2f, 0.2f, wood, p, cx, h + 3.45f, cz);
        blocker(cx - 3.3f, h + 1.7f, cz, 0.18f, 1.7f, 0.18f, 0f);
        blocker(cx + 3.3f, h + 1.7f, cz, 0.18f, 1.7f, 0.18f, 0f);
        blocker(cx, h + 3.45f, cz, 3.5f, 0.2f, 0.2f, 0f);
        Geometry beam = ball("wellGlow", 0.55f, blueGlow, root, cx, h + 1.7f, cz);
        beam.setLocalScale(0.55f, 3.4f, 0.55f);
        beam.setQueueBucket(RenderQueue.Bucket.Transparent);
    }

    private void campfire(Node p, float cx, float cz) {
        float h = terrain.height(cx, cz);
        for (int i = 0; i < 5; i++) {
            float a = i / 5f * FastMath.TWO_PI;
            Gfx.box("fireLog", 0.55f, 0.08f, 0.09f, wood, p,
                    cx + FastMath.sin(a) * 0.26f, h + 0.14f,
                    cz + FastMath.cos(a) * 0.26f).rotate(0f, a, 0f);
        }
        for (int i = 0; i < 4; i++) {
            Gfx.box("fireStone", 0.2f, 0.12f, 0.16f, stoneDark, p,
                    cx + FastMath.sin(i * 1.57f) * 0.7f, h + 0.1f,
                    cz + FastMath.cos(i * 1.57f) * 0.7f);
        }
        Geometry f = ball("campFire", 0.34f, fireGlow, root, cx, h + 0.42f, cz);
        f.setQueueBucket(RenderQueue.Bucket.Transparent);
        f.setLocalScale(0.8f, 1.7f, 0.8f);
    }

    // ---------------------------------------------------------------- camps

    private void buildCamps() {
        BatchNode camp = batch("camps");
        float[][] spots = {
                {-95f, 82f}, {175f, -35f}, {352f, 300f}, {392f, -338f},
                {512f, 190f}, {268f, -290f}, {-160f, 195f}, {198f, -262f},
                {210f, 278f}, {-438f, 258f}, {110f, 400f}
        };
        for (int i = 0; i < spots.length; i++) {
            camp(spots[i][0], spots[i][1], i);
        }
        finish(camp);
    }

    private void camp(float cx, float cz, int seed) {
        Random r = new Random(seed * 17L + 3);
        // palisade arc
        for (int i = 0; i < 12; i++) {
            float a = FastMath.PI * (0.15f + 0.7f * i / 11f);
            float px = cx + FastMath.sin(a) * 9f;
            float pz = cz + FastMath.cos(a) * 9f;
            float gh = terrain.height(px, pz);
            Geometry post = Gfx.box("palisade", 0.18f, 1.15f, 0.18f, wood, root,
                    px, gh + 1.0f, pz);
            post.rotate(0f, -a, 0f);
            blocker(px, gh + 1.0f, pz, 0.18f, 1.15f, 0.18f, 0f);
        }
        // tents
        for (int t = 0; t < 3; t++) {
            float a = t * FastMath.TWO_PI / 3f + 0.6f;
            float px = cx + FastMath.sin(a) * (4.2f + r.nextFloat() * 2f);
            float pz = cz + FastMath.cos(a) * (4.2f + r.nextFloat() * 2f);
            float gh = terrain.height(px, pz);
            Geometry tent = new Geometry("tent", new Cylinder(4, 4, 0.12f, 1.9f, 2.1f, true, false));
            tent.setMaterial(cloth);
            tent.rotate(-FastMath.HALF_PI, 0f, 0f);
            tent.setLocalTranslation(px, gh + 1.05f, pz);
            tent.rotate(0f, 0f, r.nextFloat() * FastMath.TWO_PI);
            root.attachChild(tent);
            blocker(px, gh + 1.05f, pz, 1.9f, 1.05f, 1.9f, 0f);
            solid(px, gh + 1.05f, pz, 1.55f, 1.05f, 1.55f, 0f);
            Gfx.box("tentPole", 0.06f, 1.1f, 0.06f, wood, root, px, gh + 2.05f, pz);
        }
        campfire(root, cx, cz);
        // supply crates
        float kx = cx + 2f;
        float kz = cz - 1f;
        float kh = terrain.height(kx, kz);
        Vector3f box = gltfAt(root, "box_large", kx, kz, r.nextFloat() * FastMath.TWO_PI, 0.95f);
        if (box != null) {
            boxColliders(kx, kz, kh, box, 0f);
        } else {
            Gfx.box("crate", 0.55f, 0.45f, 0.5f, wood, root, kx, kh + 0.45f, kz);
            blocker(kx, kh + 0.45f, kz, 0.55f, 0.45f, 0.5f, 0f);
        }
        // 营地杂物：酒桶/木桶摆在帐篷缝里（半径 5.5，避开半径 4.2~6.2 的三顶帐篷），
        // 货堆挨着补给箱。半径不能太大，否则会挡到营地附近 Boss 的取景探针
        float[] kitA = {2.15f, 4.24f, 6.34f};
        String[] kitN = {"barrel_large", "keg", "barrel_small"};
        for (int i = 0; i < kitA.length; i++) {
            float px = cx + FastMath.sin(kitA[i]) * 5.5f;
            float pz = cz + FastMath.cos(kitA[i]) * 5.5f;
            float gh = terrain.height(px, pz);
            Vector3f d = gltfAt(root, kitN[i], px, pz, r.nextFloat() * FastMath.TWO_PI, 1.9f);
            if (d != null) {
                boxColliders(px, pz, gh, d, 0f);
            }
        }
        float sx = cx + 4.3f;
        float sz = cz - 2.2f;
        float sh = terrain.height(sx, sz);
        Vector3f stack = gltfAt(root, "crates_stacked", sx, sz,
                r.nextFloat() * FastMath.TWO_PI, 1.7f);
        if (stack != null) {
            boxColliders(sx, sz, sh, stack, 0f);
        }
    }

    // ---------------------------------------------------------------- lake & landmarks

    private void buildLakeRuins() {
        BatchNode lake = batch("lakeRuins");
        for (int i = 0; i < 7; i++) {
            float a = i / 7f * FastMath.TWO_PI;
            float px = WorldData.LAKE_X + FastMath.sin(a) * 70f;
            float pz = WorldData.LAKE_Z + FastMath.cos(a) * 52f;
            float gh = terrain.height(px, pz);
            float hgt = 2.4f + (i % 3) * 1.7f;
            Vector3f col = gltfAt(lake, "column", px, pz, i * 0.7f, hgt, 2.0f);
            if (col != null) {
                roundColliders(px, pz, gh, col);
            } else {
                vertical("drownedColumn", 0.7f, hgt, stone, lake, px,
                        gh + hgt * 0.5f, pz);
                blocker(px, gh + hgt * 0.5f, pz, 0.7f, hgt * 0.5f, 0.7f, 0f);
                solidRound(px, gh + hgt * 0.5f, pz, 0.7f, hgt * 0.5f);
            }
        }
        Gfx.box("drownedWall", 22f, 2.1f, 1.2f, stone, lake,
                WorldData.LAKE_X - 30f, terrain.height(WorldData.LAKE_X - 30f,
                        WorldData.LAKE_Z + 30f) + 2.6f, WorldData.LAKE_Z + 30f);
        blocker(WorldData.LAKE_X - 30f, terrain.height(WorldData.LAKE_X - 30f,
                WorldData.LAKE_Z + 30f) + 2.6f, WorldData.LAKE_Z + 30f, 22f, 2.1f, 1.2f, 0f);
        solid(WorldData.LAKE_X - 30f, terrain.height(WorldData.LAKE_X - 30f,
                WorldData.LAKE_Z + 30f) + 2.6f, WorldData.LAKE_Z + 30f, 22f, 2.1f, 1.2f, 0f);
        finish(lake);
    }

    private void buildMinorErdtree() {
        float cx = 540f;
        float cz = 410f;
        float h = terrain.height(cx, cz);
        Node tree = new Node("minorErdtree");
        tree.setLocalTranslation(cx, h, cz);
        root.attachChild(tree);
        vertical("erdtreeTrunk", 6.2f, 34f, lit(new ColorRGBA(0.55f, 0.42f, 0.22f, 1f), 3f),
                tree, 0f, 17f, 0f);
        blocker(cx, h + 17f, cz, 6.2f, 17f, 6.2f, 0f);
        solidRound(cx, h + 17f, cz, 6.2f, 17f);
        for (int i = 0; i < 7; i++) {
            float a = i / 7f * FastMath.TWO_PI;
            Geometry rootFlare = ball("erdtreeRoot", 2.6f, bark, tree,
                    FastMath.sin(a) * 5.6f, 1.2f, FastMath.cos(a) * 5.6f);
            rootFlare.setLocalScale(1.6f, 0.55f, 1.1f);
        }
        Material goldLeaf = Gfx.lit(am, new ColorRGBA(0.72f, 0.60f, 0.24f, 1f),
                new ColorRGBA(0.35f, 0.27f, 0.09f, 1f),
                new ColorRGBA(0.12f, 0.1f, 0.04f, 1f), 8f);
        for (int i = 0; i < 14; i++) {
            float a = i / 14f * FastMath.TWO_PI;
            float d = 7f + (i % 4) * 3.2f;
            float y = 27f + (i % 5) * 3.1f;
            Geometry crown = ball("erdtreeCrown", 6.5f, goldLeaf, tree,
                    FastMath.sin(a) * d, y, FastMath.cos(a) * d);
            crown.setLocalScale(1.1f, 0.75f, 1.05f);
        }
        Geometry core = ball("erdtreeCore", 11f, goldGlow, tree, 0f, 34f, 0f);
        core.setQueueBucket(RenderQueue.Bucket.Transparent);
        core.setShadowMode(RenderQueue.ShadowMode.Off);
        // golden ground halo
        Geometry halo = new Geometry("erdtreeHalo", Gfx.torusMesh(24f, 0.12f, 72, 6));
        halo.setMaterial(goldGlow);
        halo.setLocalTranslation(0f, 0.12f, 0f);
        halo.setQueueBucket(RenderQueue.Bucket.Transparent);
        tree.attachChild(halo);
    }

    // ---------------------------------------------------------------- Stormveil

    private void buildStormveil() {
        BatchNode castle = batch("stormveil");
        float cx = WorldData.STORM_X;
        float cz = WorldData.STORM_Z;
        float base = terrain.height(cx, cz + 20f);
        float gateZ = -474f;

        // curtain walls: the west run stops at the cliff, the east run at the slope
        wall(castle, cx - 50f, gateZ, 35f, 24f, 4.5f, base);
        wall(castle, cx + 65f, gateZ, 70f, 24f, 4.5f, base);
        wall(castle, cx + 131f, gateZ - 50f, 4.5f, 24f, 50f, base, FastMath.HALF_PI);
        groundPlinth(castle, cx - 50f, gateZ, 35f, 4.9f, base);
        groundPlinth(castle, cx + 65f, gateZ, 70f, 4.9f, base);
        groundPlinth(castle, cx + 131f, gateZ - 50f, 4.9f, 50f, base);

        // inner bailey wall, split by the gate that opens onto the keep terrace
        float innerZ = gateZ - 66f;
        wall(castle, cx - 33f, innerZ, 27f, 26f, 5f, base);
        wall(castle, cx + 73.5f, innerZ, 61.5f, 26f, 5f, base);
        groundPlinth(castle, cx - 33f, innerZ, 27f, 5f, base);
        groundPlinth(castle, cx + 73.5f, innerZ, 61.5f, 5f, base);
        tower(castle, cx - 14f, innerZ, 8f, 30f, base);
        tower(castle, cx + 14f, innerZ, 8f, 30f, base);
        towerBase(castle, cx - 14f, innerZ, 8f, base);
        towerBase(castle, cx + 14f, innerZ, 8f, base);
        stoneBox("innerGateLintel", 7f, 4f, 5f, stone, castle, cx, base + 13f, innerZ);
        blocker(cx, base + 13f, innerZ, 7f, 4f, 5f, 0f);
        stoneBox("innerGateFloor", 6f, 0.2f, 5f, stone, castle, cx, base - 2.6f, innerZ);

        // gatehouse
        tower(castle, cx - 24f, gateZ, 10f, 38f, base);
        tower(castle, cx + 24f, gateZ, 10f, 38f, base);
        stoneBox("gateLintel", 25f, 4.5f, 6.2f, stone, castle, cx, base + 25f, gateZ);
        blocker(cx, base + 25f, gateZ, 25f, 4.5f, 6.2f, 0f);
        // 门楣正面贴一排砌石（与幕墙外皮同一套模块）；比石盒凸出 0.1m，避免共面闪烁
        cladRow(castle, cx, gateZ, 25f, 8.4f, 6.3f, base + 20.5f, 0f);
        // gate arch stones
        for (int i = 0; i < 7; i++) {
            float a = FastMath.PI * i / 6f;
            stoneBox("archStone", 1.6f, 0.9f, 1.5f, stone, castle,
                    cx + FastMath.cos(a) * 8.6f, base + 21f + FastMath.sin(a) * 2.0f, gateZ);
        }
        // approach walls
        wall(castle, cx - 20f, gateZ + 10f, 4.5f, 8f, 12f, base, FastMath.HALF_PI);
        wall(castle, cx + 20f, gateZ + 10f, 4.5f, 8f, 12f, base, -FastMath.HALF_PI);

        // main keep silhouette, raised on a plinth that meets the hillside
        float keepZ = cz - 190f;
        stoneBox("keep", 44f, 30f, 41.88f, stone, castle, cx, base + 30f, keepZ);
        stoneBox("keepUpper", 34f, 14f, 30f, stoneDark, castle, cx, base + 74f, keepZ);
        blocker(cx, base + 30f, keepZ, 44f, 30f, 42f, 0f);
        solid(cx, base + 30f, keepZ, 44f, 30f, 42f, 0f);
        blocker(cx, base + 74f, keepZ, 34f, 14f, 30f, 0f);
        stoneBox("keepPlinth", 44f, 13f, 42f, stoneDark, castle, cx, base - 13f, keepZ);
        blocker(cx, base - 13f, keepZ, 44f, 13f, 42f, 0f);
        tower(castle, cx - 38f, keepZ, 12f, 62f, base);
        tower(castle, cx + 52f, keepZ, 12f, 62f, base);
        towerBase(castle, cx - 38f, keepZ, 12f, base);
        towerBase(castle, cx + 52f, keepZ, 12f, base);

        // keep facade: pilasters, arrow-slit windows, the hall door and a parapet
        float faceZ = keepZ + 42f;
        // 正立面同样铺砌石（11 列 × 8 排 = 88 块，8m 模块），立面构件整体前移到模块之外
        cladRow(castle, cx, keepZ, 88f, 60f, 42f, base, 0f);
        for (int i = -2; i <= 2; i++) {
            float bx = cx + i * 12f;
            stoneBox("keepPilaster", 1.3f, 28f, 1.1f, stoneDark, castle, bx, base + 28f, faceZ + 0.9f);
            blocker(bx, base + 28f, faceZ + 0.9f, 1.3f, 28f, 1.1f, 0f);
        }
        for (int row = 0; row < 2; row++) {
            float wy = base + 21f + row * 18f;
            for (int i = -3; i <= 3; i++) {
                if (i == 0 && row == 0) {
                    continue;
                }
                stoneBox("keepWindow", 0.75f, 2.6f, 0.35f, iron, castle,
                        cx + i * 12f, wy, faceZ + 0.05f);
            }
        }
        // great door of the hall, right behind the throne
        stoneBox("hallDoor", 4.2f, 6.4f, 0.4f, iron, castle, cx, base + 6.4f, faceZ + 0.45f);
        stoneBox("hallDoorLintel", 5.4f, 0.9f, 0.7f, stone, castle, cx, base + 13.2f, faceZ + 0.85f);
        for (int i = -6; i <= 6; i++) {
            stoneBox("keepMerlon", 1.6f, 1.6f, 1.2f, stone, castle,
                    cx + i * 7f, base + 61.4f, faceZ - 1.1f);
        }
        tower(castle, cx - 72f, gateZ + 10f, 12f, 46f, base);
        tower(castle, cx + 120f, gateZ + 10f, 12f, 46f, base);
        towerBase(castle, cx - 72f, gateZ + 10f, 12f, base);
        towerBase(castle, cx + 120f, gateZ + 10f, 12f, base);

        // battlements along the curtain walls, skipping the gate opening
        crenels(castle, cx - 50f, gateZ + 1.6f, 35f, base + 25.8f, 1.2f, 0f, 0f);
        crenels(castle, cx + 65f, gateZ + 1.6f, 70f, base + 25.8f, 1.2f, cx, 20f);
        crenels(castle, cx - 33f, innerZ, 27f, base + 27.8f, 1.2f, cx, 20f);
        crenels(castle, cx + 73.5f, innerZ, 61.5f, base + 27.8f, 1.2f, cx, 20f);

        // buttresses and arrow slits on the gatehouse
        for (float side : new float[]{-1f, 1f}) {
            for (int i = 0; i < 3; i++) {
                stoneBox("buttress", 0.8f, 11f, 1.0f, stoneDark, castle,
                        cx + side * 33f, base + 8f + i * 9f, gateZ + 5.2f);
                blocker(cx + side * 33f, base + 8f + i * 9f, gateZ + 5.2f,
                        0.8f, 11f, 1.0f, 0f);
            }
        }
        // banners
        for (float side : new float[]{-1f, 1f}) {
            Gfx.box("banner", 2.4f, 9f, 0.12f, cloth, castle,
                    cx + side * 11f, base + 18f, gateZ + 6.6f);
            Gfx.box("bannerTrim", 2.6f, 0.5f, 0.16f, iron, castle,
                    cx + side * 11f, base + 22.8f, gateZ + 6.6f);
        }

        // approach causeway stones
        for (int i = 0; i < 12; i++) {
            float z = gateZ + 12f + i * 5.6f;
            float gy = terrain.height(cx, z);
            stoneBox("causeway", 8.5f - i * 0.12f, 0.35f, 2.6f, stone, castle,
                    cx, gy + 0.18f, z);
        }
        // ruined outer wall stubs on the slope
        stoneBox("outerWall", 24f, 6.5f, 3.0f, stoneDark, castle,
                cx - 90f, terrain.height(cx - 90f, gateZ + 55f) + 3.2f, gateZ + 55f);
        stoneBox("outerWall", 18f, 4.5f, 3.0f, stoneDark, castle,
                cx + 86f, terrain.height(cx + 86f, gateZ + 70f) + 2.2f, gateZ + 70f);
        blocker(cx - 90f, terrain.height(cx - 90f, gateZ + 55f) + 3.2f, gateZ + 55f,
                24f, 6.5f, 3.0f, 0f);
        blocker(cx + 86f, terrain.height(cx + 86f, gateZ + 70f) + 2.2f, gateZ + 70f,
                18f, 4.5f, 3.0f, 0f);
        solid(cx - 90f, terrain.height(cx - 90f, gateZ + 55f) + 3.2f, gateZ + 55f,
                24f, 6.5f, 3.0f, 0f);
        solid(cx + 86f, terrain.height(cx + 86f, gateZ + 70f) + 2.2f, gateZ + 70f,
                18f, 4.5f, 3.0f, 0f);
        // throne terrace in front of the keep, where the grafted king holds court
        float hallY = terrain.height(cx, -592f);
        stoneBox("hallFloor", 19f, 0.14f, 11f, stone, castle, cx, hallY + 0.14f, -592f);
        stoneBox("hallStep", 17f, 0.16f, 2.2f, stoneDark, castle, cx, hallY + 0.16f, -581.5f);
        stoneBox("hallStep", 15f, 0.16f, 2.2f, stoneDark, castle, cx, hallY + 0.32f, -579.5f);
        blocker(cx, hallY + 0.32f, -592f, 19f, 0.32f, 11f, 0f);
        // golden throne on the terrace
        stoneBox("throneSeat", 2.4f, 0.35f, 1.9f, stone, castle, cx, hallY + 1.6f, -597f);
        stoneBox("throneBack", 2.4f, 3.1f, 0.35f, stoneDark, castle, cx, hallY + 3.0f, -598.4f);
        Gfx.box("throneGold", 2.1f, 0.16f, 1.6f,
                Gfx.lit(am, new ColorRGBA(0.72f, 0.56f, 0.20f, 1f),
                        new ColorRGBA(0.24f, 0.18f, 0.06f, 1f),
                        new ColorRGBA(0.92f, 0.80f, 0.45f, 1f), 48f),
                castle, cx, hallY + 2.02f, -597f);
        blocker(cx, hallY + 2.6f, -597.6f, 2.4f, 3.1f, 1.2f, 0f);
        for (float side : new float[]{-1f, 1f}) {
            Gfx.box("hallBanner", 2.6f, 6.5f, 0.14f, cloth, castle,
                    cx + side * 12f, hallY + 6.5f, -601f);
            Gfx.box("hallBannerTrim", 2.8f, 0.5f, 0.18f, iron, castle,
                    cx + side * 12f, hallY + 10.1f, -601f);
        }

        finish(castle);

        // gate banners and braziers are additive/transparent
        for (float side : new float[]{-1f, 1f}) {
            Geometry flame = ball("gateFlame", 0.5f, fireGlow, root,
                    cx + side * 9.8f, base + 19f, gateZ + 7.2f);
            flame.setQueueBucket(RenderQueue.Bucket.Transparent);
            flame.setLocalScale(0.7f, 1.5f, 0.7f);
        }

        buildGateDoors(cx, base, gateZ);
    }

    /** Two iron-bound leaves that slide into the gatehouse once Margit falls. */
    private void buildGateDoors(float cx, float base, float gateZ) {
        Node doors = new Node("castleDoors");
        root.attachChild(doors);
        for (int i = 0; i < 2; i++) {
            float side = i == 0 ? -1f : 1f;
            float closed = cx + side * 6.25f;
            Node leaf = new Node("gateLeaf" + i);
            leaf.setLocalTranslation(closed, base + 10.5f, gateZ);
            doors.attachChild(leaf);
            stoneBox("gateLeaf", 6.25f, 10.5f, 1.1f, stoneDark, leaf, 0f, 0f, 0f);
            Gfx.box("gateBand", 6.3f, 0.45f, 1.2f, iron, leaf, 0f, -6f, 0f);
            Gfx.box("gateBand", 6.3f, 0.45f, 1.2f, iron, leaf, 0f, 0f, 0f);
            Gfx.box("gateBand", 6.3f, 0.45f, 1.2f, iron, leaf, 0f, 6f, 0f);
            gateLeaves[i] = leaf;
            gateClosedX[i] = closed;
            gateOpenX[i] = closed + side * 13.5f;
        }
        blocker(cx, base + 10.5f, gateZ, 12.5f, 10.5f, 1.2f, 0f);
    }

    /** `wall.glb` 原始 4×4×1m，砌石外皮统一放大这个倍数（8×8×2m，24m 墙高正好三排）。 */
    private static final float CLAD_K = 2f;

    private void wall(BatchNode p, float cx, float cz, float halfW, float height,
                      float halfD, float base) {
        wall(p, cx, cz, halfW, height, halfD, base, 0f);
    }

    /**
     * 幕墙。
     *
     * @param outerYaw 外侧法线的 yaw（0 = 朝 +Z，{@code HALF_PI} = 朝 +X），决定砌石外皮贴哪一面。
     *                 碰撞 footprint 始终等于原始半长，不随外皮变化。
     */
    private void wall(BatchNode p, float cx, float cz, float halfW, float height,
                      float halfD, float base, float outerYaw) {
        boolean clad = cladWall(p, cx, cz, halfW, halfD, base, height, outerYaw);
        // 有砌石外皮时把实体盒朝观众一侧收 0.12m：模块外表面正好落在原墙面，
        // 既不会共面 z-fight，浮雕又读得出来；未贴外皮的朝向保持原尺寸。
        float shave = clad ? 0.12f : 0f;
        float bx = halfW - (FastMath.abs(FastMath.sin(outerYaw)) > 0.5f ? shave : 0f);
        float bz = halfD - (FastMath.abs(FastMath.cos(outerYaw)) > 0.5f ? shave : 0f);
        stoneBox("curtainWall", bx, height * 0.5f, bz, stoneDark, p,
                cx, base + height * 0.5f, cz);
        blocker(cx, base + height * 0.5f, cz, halfW, height * 0.5f, halfD, 0f);
        solid(cx, base + height * 0.5f, cz, halfW, height * 0.5f, halfD, 0f);
        if (!clad) {
            // 程序化腰线只在没有砌石外皮时出现，否则两道石带会横穿模块
            stoneBox("wallCourse", halfW + 0.15f, 0.28f, halfD + 0.15f, stone, p,
                    cx, base + height * 0.72f, cz);
            stoneBox("wallCourse", halfW + 0.15f, 0.28f, halfD + 0.15f, stone, p,
                    cx, base + height * 0.36f, cz);
        }
    }

    /**
     * 在幕墙外侧平铺 KayKit {@code wall.glb} 砌石层。返回 {@code false} 表示模型不可用
     * （调用方回退程序化腰线）。
     *
     * <p><b>只做视觉，不登记 blocker/solid</b>：碰撞仍由 {@link #wall} 的原始 footprint 负责，
     * 所以 A 项的 `wall blocks` 断言（停在墙面外一个碰撞半径）不需要改。
     */
    private boolean cladWall(BatchNode p, float cx, float cz, float halfW, float halfD,
                             float base, float height, float outerYaw) {
        boolean alongX = FastMath.abs(FastMath.cos(outerYaw)) > 0.5f;
        return cladRow(p, cx, cz, 2f * (alongX ? halfW : halfD), height,
                alongX ? halfD : halfW, base, outerYaw);
    }

    /**
     * 在一条长 {@code runLen}、高 {@code height} 的墙面上平铺砌石模块；墙面位于中心沿法线
     * {@code faceOff} 处，模块外表面与墙面齐平（实体盒已内缩 0.12m，浮雕读得出来）。
     */
    private boolean cladRow(BatchNode p, float cx, float cz, float runLen, float height,
                            float faceOff, float base, float outerYaw) {
        Vector3f dim = gltf.size("wall");
        if (dim == null) {
            return false;
        }
        float nx = FastMath.sin(outerYaw);
        float nz = FastMath.cos(outerYaw);
        float dx = FastMath.cos(outerYaw);
        float dz = -FastMath.sin(outerYaw);
        float modD = dim.z * CLAD_K;
        int cols = Math.max(1, Math.round(runLen / (dim.x * CLAD_K)));
        int rows = Math.max(1, Math.round(height / (dim.y * CLAD_K)));
        float colW = runLen / cols;
        float rowH = height / rows;
        float out = faceOff - modD * 0.5f;      // 模块中心：外表面正好落在原墙面
        for (int r = 0; r < rows; r++) {
            float y = base + r * rowH;
            for (int c = 0; c < cols; c++) {
                Spatial s = gltf.spawn("wall");
                if (s == null) {
                    return false;
                }
                // 每块在长宽上多铺 6cm：相邻模块的上下面/侧面共面会 z-fight，
                // 表现成一排带锯齿的暗带（横缝）与竖缝
                s.setLocalScale((colW + 0.06f) / dim.x, (rowH + 0.06f) / dim.y, CLAD_K);
                float t = (c + 0.5f) * colW - runLen * 0.5f;
                s.setLocalTranslation(cx + dx * t + nx * out, y, cz + dz * t + nz * out);
                s.rotate(0f, outerYaw, 0f);
                p.attachChild(s);
            }
        }
        return true;
    }

    /** Crenellated parapet along a wall run, leaving a gap around a gate. */
    private void crenels(BatchNode p, float cx, float cz, float halfW, float y, float halfD,
                         float skipCx, float skipHalfW) {
        int n = Math.max(2, (int) (halfW * 2f / 3.6f));
        for (int i = 0; i <= n; i++) {
            float x = cx - halfW + (halfW * 2f) * i / n;
            if (skipHalfW > 0f && Math.abs(x - skipCx) < skipHalfW) {
                continue;
            }
            stoneBox("merlon", 1.2f, 1.8f, halfD, stone, p, x, y, cz);
        }
    }

    /** Foundation that reaches down to the ground under a footprint. */
    private void groundPlinth(BatchNode p, float cx, float cz, float halfW, float halfD,
                              float base) {
        float lo = Float.MAX_VALUE;
        for (int i = 0; i <= 6; i++) {
            float fx = cx + (i / 3f - 1f) * halfW;
            for (int j = 0; j <= 2; j++) {
                lo = Math.min(lo, terrain.height(fx, cz + (j - 1f) * halfD));
            }
        }
        float bottom = lo - 6f;
        float drop = base + 0.2f - bottom;
        if (drop < 1f) {
            return;
        }
        stoneBox("plinth", halfW, drop * 0.5f, halfD, stoneDark, p,
                cx, base + 0.2f - drop * 0.5f, cz);
        blocker(cx, base + 0.2f - drop * 0.5f, cz, halfW, drop * 0.5f, halfD, 0f);
    }

    /** Battered base so a tower on a slope meets the ground instead of hovering. */
    private void towerBase(BatchNode p, float cx, float cz, float radius, float base) {
        float outer = radius * 1.5f;
        float lo = Float.MAX_VALUE;
        for (int i = 0; i < 12; i++) {
            float a = i / 12f * FastMath.TWO_PI;
            lo = Math.min(lo, terrain.height(cx + FastMath.sin(a) * outer,
                    cz + FastMath.cos(a) * outer));
        }
        float bottom = lo - 3f;
        float drop = base - bottom;
        if (drop < 1.5f) {
            return;
        }
        stoneTaper("towerBase", radius, outer, drop, stoneDark, p,
                cx, bottom + drop * 0.5f, cz);
        blocker(cx, bottom + drop * 0.5f, cz, radius, drop * 0.5f, radius, 0f);
    }

    private void tower(BatchNode p, float cx, float cz, float radius, float height, float base) {
        stoneCylinder("tower", radius, height, stoneDark, p, cx, base + height * 0.5f, cz);
        blocker(cx, base + height * 0.5f, cz, radius, height * 0.5f, radius, 0f);
        solidRound(cx, base + height * 0.5f, cz, radius, height * 0.5f);
        Mesh topMesh = new Cylinder(4, 18, radius * 1.12f, 2.2f, true);
        scaleUV(topMesh, FastMath.TWO_PI * radius * 1.12f / STONE_TILE, 2.2f / STONE_TILE);
        Geometry top = new Geometry("towerTop", topMesh);
        top.setMaterial(stone);
        top.rotate(-FastMath.HALF_PI, 0f, 0f);
        top.setLocalTranslation(cx, base + height + 0.6f, cz);
        p.attachChild(top);
        blocker(cx, base + height + 0.6f, cz, radius * 1.12f, 1.1f, radius * 1.12f, 0f);
        // 塔冠雉堞。Dungeon 包里没有圆柱塔楼件，试过两种 glTF 替代都更差：
        // `pillar.glb` 等比缩小后像蛋糕上插的酒瓶，`barrier.glb` 是地牢铁栏杆、绕一圈像阳台护栏。
        // 所以塔身/塔冠保持程序化，只把雉堞从 6 个加密到 12 个并贴合塔冠半径。
        for (int i = 0; i < 12; i++) {
            float a = i / 12f * FastMath.TWO_PI;
            Geometry m = stoneBox("towerMerlon", radius * 0.16f, 1.3f, 0.5f, stone, p,
                    cx + FastMath.sin(a) * radius * 1.05f, base + height + 3.0f,
                    cz + FastMath.cos(a) * radius * 1.05f);
            m.rotate(0f, a, 0f);
        }
    }
}
