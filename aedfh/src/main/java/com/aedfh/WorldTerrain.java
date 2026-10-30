package com.aedfh;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Analytic Limgrave height field plus a single LOD-free ground mesh. All
 * gameplay queries use the same height() function, so nothing ever floats.
 *
 * <p>地形的三层骨架：风暴山丘断崖（北侧 30–40m 直壁）、三条 U 形峡谷、内陆水塘盆地。
 * 所有玩法坐标（赐福/POI/Boss/道路/出生洞窟）都用平台与掩码压平，只改高度不改位置。
 */
final class WorldTerrain {

    static final int SEGS = 360;
    private static final int MAP_RES = 768;
    /** 地表混合权重图分辨率：权重是低频量，512² 覆盖 1400m ≈ 2.7m/px 足够。 */
    private static final int SPLAT_RES = 512;

    /** 风暴山丘台地标高。 */
    private static final float SCARP_TOP = 64f;
    /**
     * 爬崖公路的折线（与 ROADS[0] 从低地到史东薇尔的一段重合）。
     * 缓坡走廊必须按"到折线的距离"放宽：只按到隘口中心的距离算，离中心 60m 的路段
     * 会把走廊收窄到 20m，坡度冲到 1.2，玩家在路中间被 slideDownhill 推回来。
     */
    private static final float[] PASS_PATH = {
            -280f, -230f, -320f, -310f, -345f, -350f, -368f, -390f, -390f, -432f,
            -405f, -452f };
    private static final float CAVE_X = 0f;
    private static final float CAVE_Z = 348f;

    /** 每个塘的水面标高 = 塘心原始地形 - 0.55m。 */
    private static final float[] POND_LEVEL = new float[WorldData.PONDS.length];
    /** 站点平台：{x, z, 核心半径, 外缘半径, 基准高, 倾斜 x, 倾斜 z}，避免起伏把建筑/赐福抬歪。 */
    private static final float[][] PADS;

    static {
        for (int i = 0; i < WorldData.PONDS.length; i++) {
            WorldData.Pond p = WorldData.PONDS[i];
            POND_LEVEL[i] = prePond(p.x(), p.z()) - 0.55f;
        }
        java.util.ArrayList<float[]> pads = new java.util.ArrayList<>();
        for (WorldData.GraceDef g : WorldData.GRACES) {
            if (inCaveZone(g.x(), g.z())) {
                continue;
            }
            pads.add(new float[]{g.x(), g.z(), 8f, 24f, 0f, 0f, 0f});
        }
        for (WorldData.Poi poi : WorldData.POIS) {
            // 断桥（11）要跨峡谷，压平反而毁掉地貌；其余站点（含石阵/陵墓/巨像）
            // 都是落在平基上的整块石构，必须有平台，否则台基会一半悬空。
            if (poi.kind() >= 0 && poi.kind() != 11 && poi.radius() > 0f) {
                // 半径封顶：城堡 POI 的 radius 是 150，按 1.85 倍铺开会把 277m 内的
                // 丘陵、断崖、水塘全压成一块 66m 的平盘。
                float core = Math.min(poi.radius() * 0.95f, 60f);
                float outer = Math.min(poi.radius() * 1.85f, 110f);
                pads.add(new float[]{poi.x(), poi.z(), core, outer, 0f, 0f, 0f});
            }
        }
        for (float[] p : pads) {
            p[4] = rawHeight(p[0], p[1]);
            // 隘口走廊里的站点（风暴关卡、赐福）必须让平台顺着坡走：一块平盘摆在
            // 0.25 的斜坡上，会沿外缘切出一道环形坎，正好横在爬崖公路中间。
            // 其余站点保持水平——教堂/破屋这类整体成型的建筑需要平地。
            if (passDistance(p[0], p[1]) > 70f) {
                continue;
            }
            float dx = (rawHeight(p[0] + 6f, p[1]) - rawHeight(p[0] - 6f, p[1])) / 12f;
            float dz = (rawHeight(p[0], p[1] + 6f) - rawHeight(p[0], p[1] - 6f)) / 12f;
            float g = (float) Math.sqrt(dx * dx + dz * dz);
            float k = g > 0.9f ? 0.9f / g : 1f;
            p[5] = dx * k;
            p[6] = dz * k;
        }
        PADS = pads.toArray(new float[0][]);
    }

    private final Node root = new Node("limgraveTerrain");
    private final BufferedImage mapImage;
    private final Texture2D mapTexture;
    private final Material groundMat;
    private final Material waterMat;
    private Material skyMat;

    /** 纯 CPU 烘焙结果：地图小图与地表权重图，不碰 AssetManager / 场景图。 */
    static final class Bake {
        final BufferedImage map;
        final BufferedImage splat;

        Bake(BufferedImage map, BufferedImage splat) {
            this.map = map;
            this.splat = splat;
        }
    }

    private static volatile int bakeDone;
    private static volatile int bakeTotal;

    /**
     * 生成地形贴图与权重图。1.3 秒左右的重活，可以在后台线程跑——
     * 它只读静态高度场，不创建任何 jME 对象。
     */
    static Bake bake() {
        bakeDone = 0;
        bakeTotal = MAP_RES + SPLAT_RES;
        BufferedImage map = paintMap();
        BufferedImage splat = paintSplat();
        return new Bake(map, splat);
    }

    static float bakeProgress() {
        int total = bakeTotal;
        return total <= 0 ? 0f : MathX.clamp(bakeDone / (float) total, 0f, 1f);
    }

    WorldTerrain(AssetManager am, Bake bake) {
        mapImage = bake.map;
        mapTexture = Tex.image(mapImage, false);
        mapTexture.setAnisotropicFilter(8);

        groundMat = buildGroundMaterial(am, bake.splat);

        waterMat = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        waterMat.setTexture("ColorMap", Tex.water());
        waterMat.setColor("Color", new ColorRGBA(0.075f, 0.19f, 0.22f, 0.90f));
        waterMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        waterMat.getAdditionalRenderState().setDepthWrite(false);

        buildGround(am);
        buildRoads(am);
        buildWater(am);
        buildSky(am);
    }

    Node node() {
        return root;
    }

    BufferedImage mapImage() {
        return mapImage;
    }

    Texture2D mapTexture() {
        return mapTexture;
    }

    Material waterMaterial() {
        return waterMat;
    }

    Material skyMaterial() {
        return skyMat;
    }

    // ---------------------------------------------------------------- height

    private static float caveBase = Float.NaN;

    float height(float x, float z) {
        return height0(x, z);
    }

    /** 高度场本体：后台烘焙与前台查询共用同一份实现。 */
    static float height0(float x, float z) {
        float h = rawHeight(x, z);
        // 站点平台：POI/赐福周围压平，起伏只出现在野外
        for (float[] p : PADS) {
            float dx = Math.abs(x - p[0]);
            if (dx > p[3]) {
                continue;
            }
            float dz = Math.abs(z - p[1]);
            if (dz > p[3]) {
                continue;
            }
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            float site = p[4] + p[5] * (x - p[0]) + p[6] * (z - p[1]);
            h = lerp(h, site, sstep(p[3], p[2], d));
        }
        return caveFlatten(x, z, h);
    }

    private static float caveFlatten(float x, float z, float h) {
        if (Float.isNaN(caveBase)) {
            caveBase = rawHeight(CAVE_X, CAVE_Z);
        }
        float d = Math.max(Math.abs(x - CAVE_X), Math.abs(z - CAVE_Z)) / 40f;
        if (d < 1f) {
            float k = FastMath.clamp((d - 0.68f) / 0.32f, 0f, 1f);
            k = k * k * (3f - 2f * k);
            h = FastMath.interpolateLinear(k, caveBase, h);
        }
        return h;
    }

    /** 含峡谷/丘陵/断崖/湖海，不含水塘盆地（水塘水位要用它反算）。 */
    private static float prePond(float x, float z) {
        // 基准面 13.5：低地噪声最多能压到 -8，基准太低会让大片草原撞上海面下限，
        // 被一刀切成 1.2m 的平板——水塘也就贴着海平面，看着像海的一部分。
        float h = 13.5f;
        h += 2.3f * FastMath.sin(x * 0.0063f + 1.1f) * FastMath.cos(z * 0.0051f - 0.4f);
        h += 1.35f * FastMath.sin(x * 0.017f + 2.0f) * FastMath.sin(z * 0.014f + 0.7f);
        // 三层丘陵：大尺度起伏给剪影，中尺度给坡面，小尺度只做草地颗粒
        h += (noise(x * 0.0037f + 21f, z * 0.0037f - 9f) - 0.5f) * 18f;
        h += (noise(x * 0.0082f + 3f, z * 0.0076f + 17f) - 0.5f) * 11f;
        h += (noise(x * 0.0165f, z * 0.0145f) - 0.5f) * 5.0f;
        h += (noise(x * 0.040f + 11f, z * 0.044f - 7f) - 0.5f) * 2.2f;
        h += (noise(x * 0.085f, z * 0.090f) - 0.5f) * 0.9f;

        // 隘口公路走廊：走廊内把岩阶噪声和城堡土丘的高频起伏压掉，
        // 否则 22m 宽的公路会横穿一串 0.6 坡度的碎岩坎，抵消缓坡的通行性。
        float corridor = smooth(96f, 40f, passDistance(x, z));
        float rough = 1f - 0.85f * corridor;

        // 风暴山丘：崖线以北抬到 64m，22m 内走完 —— 以前的 123m 缓坡读作"长坡"不是崖
        float up = scarpUp(x, z);
        if (up > 0f) {
            float rel = (noise(x * 0.012f + 4f, z * 0.012f - 2f) - 0.5f) * 7f * rough;
            h = lerp(h, SCARP_TOP + rel, up);
            // 崖面岩阶：只在坡中段生效，让 22m 的坎壁碎成一级级岩棚而不是一块光滑斜面
            float face = 4f * up * (1f - up) * rough;
            if (face > 0.001f) {
                float ledge = (noise(x * 0.026f + 8f, z * 0.026f - 5f) - 0.5f) * 8f;
                float grit = (noise(x * 0.075f + 2f, z * 0.075f + 9f) - 0.5f) * 2.2f;
                h += face * (ledge + grit);
            }
        }

        float castle = dist(x, z, WorldData.STORM_X, WorldData.STORM_Z + 22f);
        h = lerp(h, 66f, smooth(215f, 92f, castle) * (1f - 0.70f * corridor));

        // 北境群峰：风暴山丘以东的高原断崖 + 主峰（亚坛高原方向的远景）。
        // 这片区域没有任何赐福/POI/道路，只在世界边缘加高度，不碰玩法坐标。
        float northWall = smooth(-436f, -478f, z) * smooth(120f, 230f, x);
        if (northWall > 0f) {
            float top = 92f + 12f * noise(x * 0.0075f + 9f, z * 0.0075f - 3f);
            h = lerp(h, Math.max(h, top), northWall);
        }
        h += peak(x, z, 430f, -585f, 265f, 170f);
        h += peak(x, z, 165f, -672f, 205f, 140f);
        h += peak(x, z, 665f, -515f, 170f, 110f);
        // 史东薇尔背后的巨人山：只在远景里露一个剪影。半径 150m 卡在城堡
        // 北墙（最东端 x=-270, z=-524，距峰心 195m）之外，不抬升主殿地坪。
        h += peak(x, z, -150f, -675f, 150f, 140f);

        h += 2.6f * smooth(250f, 460f, x) * smooth(200f, 410f, z);
        float erdtree = dist(x, z, 540f, 410f);
        h += 17f * smooth(180f, 58f, erdtree);

        h = carveGorges(x, z, h);

        // 内陆低地不能低于海面：海水是一整块 1400m 的平板，
        // 洼地只要跌到 0.55 以下，草原中间就会凭空冒出一片"湖"。
        h = Math.max(h, WorldData.WATER_Y + 0.65f);

        float lake = (float) Math.sqrt(sq((x - WorldData.LAKE_X) / WorldData.LAKE_RX)
                + sq((z - WorldData.LAKE_Z) / WorldData.LAKE_RZ));
        h = lerp(h, -2.4f, smooth(1.03f, 0.61f, lake));

        // Small island for Agheel, with a sandbar causeway from the south shore.
        float agheel = dist(x, z, 250f, 108f);
        float isle = smooth(30f, 12f, agheel);
        if (isle > 0f) {
            float isleH = 1.55f + 0.6f * noise(x * 0.08f + 5f, z * 0.08f - 3f);
            h = Math.max(h, lerp(-2.4f, isleH, isle));
        }
        float bar = WorldData.segmentDistance(x, z, 250f, 292f, 250f, 128f);
        float barMask = smooth(11f, 4f, bar);
        if (barMask > 0f) {
            float barH = 1.02f + 0.16f * noise(x * 0.2f, z * 0.2f);
            h = Math.max(h, lerp(-2.4f, barH, barMask));
        }

        h += 30f * smooth(600f, 690f, x);
        h += 20f * smooth(-610f, -695f, z);

        float coastX = -472f + 42f * FastMath.sin(z * 0.0042f)
                + 18f * FastMath.sin(z * 0.011f + 1.7f);
        float coast = smooth(coastX + 42f, coastX - 58f, x);
        if (coast > 0.001f) {
            float cliff = (noise(x * 0.045f, z * 0.045f) - 0.5f) * 7f * coast;
            h = lerp(h, WorldData.SEA_FLOOR, coast) + cliff;
        }
        float coastZ = 562f + 38f * FastMath.sin(x * 0.0038f)
                + 20f * FastMath.sin(x * 0.012f + 0.5f);
        float south = smooth(coastZ - 48f, coastZ + 62f, z);
        if (south > 0.001f) {
            float cliff = (noise(x * 0.04f + 5f, z * 0.04f) - 0.5f) * 6f * south;
            h = lerp(h, WorldData.SEA_FLOOR, south) + cliff;
        }
        return h;
    }

    private static float rawHeight(float x, float z) {
        return applyPonds(x, z, prePond(x, z));
    }

    /** 崖线 z 坐标（随 x 变化）：线北是 64m 台地，线南是低地。 */
    static float scarpZ(float x) {
        if (x <= -520f) {
            return lerp(-205f, -215f, smooth(-700f, -520f, x));
        }
        if (x <= -400f) {
            return lerp(-215f, -275f, (x + 520f) / 120f);
        }
        if (x <= -300f) {
            return lerp(-275f, -330f, (x + 400f) / 100f);
        }
        if (x <= -190f) {
            return lerp(-330f, -385f, (x + 300f) / 110f);
        }
        return lerp(-385f, -440f, MathX.clamp((x + 190f) / 130f, 0f, 1f));
    }

    /** 0..1：越往北越接近台地。隘口处把 22m 陡坎拉成 ~170m 缓坡，让公路能爬上去。 */
    private static float scarpUp(float x, float z) {
        float d = z - scarpZ(x);
        float pass = smooth(96f, 40f, passDistance(x, z));
        float tw = 11f + 104f * pass;
        float up = sstep(tw, -tw, d);
        return up * sstep(185f, 85f, x);
    }

    /** 到隘口公路折线的距离（米）。 */
    static float passDistance(float x, float z) {
        float best = 1e9f;
        for (int i = 0; i + 3 < PASS_PATH.length; i += 2) {
            best = Math.min(best, WorldData.segmentDistance(x, z, PASS_PATH[i],
                    PASS_PATH[i + 1], PASS_PATH[i + 2], PASS_PATH[i + 3]));
        }
        return best;
    }

    /** 独立山峰：中心最高、半径处归零；指数让山腰比圆顶更陡，读得出"高山"轮廓。 */
    private static float peak(float x, float z, float px, float pz, float radius, float height) {
        float d = dist(x, z, px, pz) / radius;
        if (d >= 1f) {
            return 0f;
        }
        float k = 1f - d;
        // 两个八度：低频让山体左右不对称，高频把轮廓打碎，避免出现完美圆锥
        float ridge = 0.66f + 0.34f * noise(x * 0.013f + px * 0.01f, z * 0.013f + pz * 0.01f)
                + 0.18f * noise(x * 0.045f + px * 0.02f, z * 0.045f + pz * 0.02f);
        return height * k * k * (1.35f - 0.35f * k) * ridge;
    }

    private static float carveGorges(float x, float z, float h) {
        for (WorldData.Gorge g : WorldData.GORGES) {
            float d = WorldData.segmentDistance(x, z, g.x1(), g.z1(), g.x2(), g.z2());
            float base = h;
            // 先把两岸垫成一道高原肩：宁姆格福的低地只有 5~10m 高，
            // 直接下切 22m 会切穿海平面，只能把谷底卡在 +2.75，最后只剩 5m 浅沟。
            float shoulder = sstep(g.halfWidth() * 2.05f, g.halfWidth() * 1.02f, d);
            if (shoulder > 0f) {
                h += shoulder * 11f * (0.70f + 0.60f
                        * noise(x * 0.02f + 6f, z * 0.02f - 4f));
            }
            if (d > g.halfWidth()) {
                continue;
            }
            float n = sstep(g.halfWidth(), g.halfWidth() * 0.42f, d);
            if (n <= 0f) {
                continue;
            }
            // 谷底按垫肩前的原地面算：否则谷心跟着肩一起抬高，下切量一点没变
            float floor = Math.max(base - g.depth(), WorldData.WATER_Y + 2.2f
                    + (noise(x * 0.05f + 9f, z * 0.05f + 1f) - 0.5f) * 2f);
            h = lerp(h, floor, n);
        }
        return h;
    }

    /** 水塘盆地：中间挖到水面下 depth，外圈抬 0.42m 岸唇，水才不会从边上漏出去。 */
    private static float applyPonds(float x, float z, float h) {
        for (int i = 0; i < WorldData.PONDS.length; i++) {
            WorldData.Pond p = WorldData.PONDS[i];
            float dx = (x - p.x()) / p.rx();
            float dz = (z - p.z()) / p.rz();
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d > 1.55f) {
                continue;
            }
            float lvl = POND_LEVEL[i];
            // 岸坡压到 5m 宽：坡太长的话水面和草地几乎同高，远看就是一个蓝色圆盘。
            float bowl = sstep(1.00f, 0.84f, d);
            h = lerp(h, lvl - p.depth() + 0.15f * noise(x * 0.06f, z * 0.06f), bowl);
            float lip = sstep(1.22f, 1.00f, d) * (1f - bowl);
            h = Math.max(h, lerp(h, lvl + 0.55f, lip));
        }
        return h;
    }

    /** 该点的水面标高（塘内用塘的水位，其余是海平面）。 */
    static float waterLevelAt(float x, float z) {
        for (int i = 0; i < WorldData.PONDS.length; i++) {
            WorldData.Pond p = WorldData.PONDS[i];
            float dx = (x - p.x()) / p.rx();
            float dz = (z - p.z()) / p.rz();
            if (dx * dx + dz * dz < 1.69f) {
                return POND_LEVEL[i];
            }
        }
        return WorldData.WATER_Y;
    }

    static float pondLevel(int index) {
        return POND_LEVEL[index];
    }

    /** 水深：负值代表在水面之上。 */
    float depth(float x, float z) {
        return waterLevelAt(x, z) - height(x, z);
    }

    float waterDepth(float x, float z) {
        return Math.max(0f, depth(x, z));
    }

    boolean dry(float x, float z) {
        return height(x, z) > waterLevelAt(x, z) + 0.25f;
    }

    float slope(float x, float z) {
        return slope0(x, z);
    }

    static float slope0(float x, float z) {
        float d = 2.2f;
        float dx = height0(x + d, z) - height0(x - d, z);
        float dz = height0(x, z + d) - height0(x, z - d);
        return (float) Math.sqrt(dx * dx + dz * dz) / (2f * d);
    }

    float normalY(float x, float z) {
        return 1f / (float) Math.sqrt(1f + slope(x, z) * slope(x, z));
    }

    /** 崖线离该点的垂直距离（正数=线南低地），用来判断"崖脚"位置。 */
    float scarpGap(float x, float z) {
        return z - scarpZ(x);
    }

    private static boolean inCaveZone(float x, float z) {
        return Math.abs(x) < 24f && Math.abs(z - CAVE_Z) < 26f;
    }

    // ---------------------------------------------------------------- ground

    // ---------------------------------------------------------------- splat material

    /**
     * 地形多层 splat：草地（平地）/ 崖壁岩层（陡坡·高处·城堡台地）/ 泥土（路网·水岸）/ 林地（雾林）。
     *
     * <p>四层权重烘进一张 {@link #SPLAT_RES}² 的 AlphaMap（RGBA 四通道），细节仍由各层 1k 贴图
     * 按世界坐标平铺提供——权重是低频的，可以粗；细节是高频道，必须靠平铺，不能烘进一张大图。
     * 崖壁用三平面投影：高度场的垂直面 UV 会被拉成竖条，按世界坐标三个轴投影才正常。
     */
    private Material buildGroundMaterial(AssetManager am, BufferedImage splat) {
        Material m = new Material(am, "Common/MatDefs/Terrain/TerrainLighting.j3md");
        m.setBoolean("useTriPlanarMapping", true);
        // MultiPass 的顶点着色器把 SpecularSum 直接取成 lightColor（不乘反照率），
        // Shininess 一小就等于往地面上糊一层纯白高光。抬到 48 让高光只在正对太阳的坡面出现。
        m.setFloat("Shininess", 48f);
        // 地表是粗糙的草/岩，不该有镜面反射；不显式压黑的话整面崖壁会被反光洗白。
        m.setColor("Specular", new ColorRGBA(0.02f, 0.02f, 0.02f, 1f));

        String[] bases = {"grass_ground", "cliff_side", "brown_mud_rocks_01", "forest_floor"};
        float[] tiles = {7f, 13f, 5f, 7f};
        float[][] tints = {
                {0.42f, 0.58f, 0.30f},
                {0.30f, 0.315f, 0.34f},
                {0.46f, 0.40f, 0.33f},
                {0.32f, 0.44f, 0.24f},
        };
        // 崖壁原图是暖褐砂岩，压掉饱和度再轻微偏冷，才是宁姆格福的灰岩层理
        float[] desat = {0f, 0.58f, 0.10f, 0.10f};
        for (int i = 0; i < bases.length; i++) {
            String suffix = i == 0 ? "" : "_" + i;
            var diffuse = Tex.tinted("Textures/PolyHaven/" + bases[i] + "_diff.jpg",
                    i == 0 ? 512 : 512, tints[i][0], tints[i][1], tints[i][2], desat[i]);
            if (diffuse == null) {
                diffuse = Tex.loaded(am, "Textures/PolyHaven/" + bases[i] + "_diff.jpg", true);
            }
            if (diffuse != null) {
                m.setTexture("DiffuseMap" + suffix, diffuse);
            }
            var normal = Tex.loaded(am, "Textures/PolyHaven/" + bases[i] + "_nor.jpg", true);
            if (normal != null) {
                m.setTexture("NormalMap" + suffix, normal);
            }
            // 三平面模式采样坐标是世界米，scale 是"每米几个平铺"，不是全图铺几次；
            // 传 SIZE/tiles 会让贴图每 5mm 重复一次，直接糊成一块单色。
            m.setFloat("DiffuseMap_" + i + "_scale", 1f / tiles[i]);
        }
        m.setTexture("AlphaMap", Tex.image(splat, false));
        return m;
    }

    /** 逐像素算四层权重，写进 AlphaMap（R=草地 G=崖壁/岩石 B=泥土 A=林地）。 */
    private static BufferedImage paintSplat() {
        int res = SPLAT_RES;
        float step = WorldData.SIZE / res;
        float[][] hs = new float[res + 1][res + 1];
        for (int iz = 0; iz <= res; iz++) {
            float z = -WorldData.HALF + iz * step;
            for (int ix = 0; ix <= res; ix++) {
                hs[iz][ix] = height0(-WorldData.HALF + ix * step, z);
            }
        }
        BufferedImage img = new BufferedImage(res, res, BufferedImage.TYPE_INT_ARGB);
        for (int iz = 0; iz < res; iz++) {
            float z = -WorldData.HALF + (iz + 0.5f) * step;
            for (int ix = 0; ix < res; ix++) {
                float x = -WorldData.HALF + (ix + 0.5f) * step;
                float h = hs[iz][ix];
                float dx = hs[iz][ix + 1] - hs[iz][ix];
                float dz = hs[iz + 1][ix] - hs[iz][ix];
                float s = (float) Math.sqrt(dx * dx + dz * dz) / step;
                float lvl = waterLevelAt(x, z);

                // 崖壁：坡度 → 海拔 → 城堡台地 → 零散裸岩
                float rock = sstep(0.55f, 1.02f, s);
                rock = Math.max(rock, sstep(54f, 76f, h) * 0.42f);
                rock = Math.max(rock, (1f - sstep(96f, 168f,
                        dist(x, z, WorldData.STORM_X, WorldData.STORM_Z))) * 0.85f);
                rock = Math.max(rock, sstep(0.62f, 0.88f,
                        noise(x * 0.02f + 5f, z * 0.02f - 3f)) * 0.22f);
                rock *= 0.78f + 0.22f * noise(x * 0.09f + 2f, z * 0.09f + 6f);

                // 泥土：只做路肩（路带 10m 宽）、水岸与峡谷底，别糊满全图
                float road = 1f - sstep(4.5f, 10f, WorldData.roadDistance(x, z));
                float shore = sstep(lvl + 0.90f, lvl + 0.05f, h);
                float gorge = 0f;
                for (WorldData.Gorge g : WorldData.GORGES) {
                    float gd = WorldData.segmentDistance(x, z, g.x1(), g.z1(), g.x2(), g.z2());
                    gorge = Math.max(gorge, sstep(g.halfWidth() * 0.95f,
                            g.halfWidth() * 0.45f, gd));
                }
                // 谷底也是碎石河床，不该是平整草地
                rock = Math.max(rock, gorge * 0.55f);
                // 峡谷的泥土只做窄窄的谷肩过渡：铺到 0.55 会让整圈谷肩变成一条白沙带
                float dirt = Math.max(road * 0.85f, Math.max(shore * 0.62f, gorge * 0.30f));

                // 林地：雾林一带的树荫地 + 雾林里的峡谷底
                float mist = sstep(230f, 320f, x) * sstep(180f, 280f, z);
                float forest = mist * (0.30f + 0.60f
                        * noise(x * 0.03f + 11f, z * 0.03f + 2f));
                forest = Math.max(forest, gorge * mist * 0.6f);

                dirt *= 1f - rock * 0.75f;
                forest *= (1f - rock) * (1f - road * 0.85f);
                // 崖面苔带与土流：整面墙糊一种砂岩会读成"白色水坝"，
                // 绿苔和挂土把大面打碎成一块块岩棚
                float moss = rock * sstep(0.40f, 0.70f,
                        noise(x * 0.055f + 13f, z * 0.055f + 21f)) * 0.55f;
                moss = Math.max(moss, rock * sstep(0.44f, 0.74f,
                        noise(x * 0.020f - 31f, z * 0.020f + 17f)) * 0.50f);
                float soil = rock * sstep(0.55f, 0.80f,
                        noise(x * 0.055f - 7f, z * 0.055f + 3f)) * 0.28f;
                dirt = Math.max(dirt, soil);
                forest = Math.max(forest, moss);
                float grass = Math.max(0f, 1f - rock - dirt - forest);
                float sum = grass + rock + dirt + forest;
                if (sum < 1e-4f) {
                    grass = 1f;
                    sum = 1f;
                }
                int r = ch(grass, sum);
                int g = ch(rock, sum);
                int b = ch(dirt, sum);
                int a = ch(forest, sum);
                // 图片行 0 是纹理顶部（v=1），世界 z 从 -HALF 起，所以要上下翻一次
                img.setRGB(ix, res - 1 - iz, (a << 24) | (r << 16) | (g << 8) | b);
            }
            bakeDone++;
        }
        // 调参用：-Daedfh.splatDump=build/splat.png 把权重图导出来看
        String dump = System.getProperty("aedfh.splatDump");
        if (dump != null && !dump.isBlank()) {
            try {
                // 导出成不透明图：R=草地 G=崖壁 B=泥土（林地 = 1-三者之和）
                BufferedImage flat = new BufferedImage(res, res, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < res; y++) {
                    for (int x = 0; x < res; x++) {
                        flat.setRGB(x, y, img.getRGB(x, y) & 0x00FFFFFF);
                    }
                }
                javax.imageio.ImageIO.write(flat, "png", new java.io.File(dump));
                System.out.println("[Terrain] splat dump -> " + dump);
                float[][] probes = {{0f, 330f}, {250f, 115f}, {-405f, -455f}, {400f, 350f},
                        {650f, 0f}, {-95f, 70f}, {540f, 410f}, {8f, 336f},
                        {180f, -28f}, {355f, 315f}, {-165f, 170f}, {0f, 200f},
                        {-330f, -318f}, {320f, 390f}, {100f, -75f},
                        {-160f, -378f}, {-160f, -390f}, {-160f, -402f},
                        {95f, -80f}, {95f, -70f}};
                for (float[] q : probes) {
                    int px = (int) ((q[0] + WorldData.HALF) / step);
                    int pz = (int) ((q[1] + WorldData.HALF) / step);
                    int argb = img.getRGB(Math.min(res - 1, px), res - 1 - Math.min(res - 1, pz));
                    System.out.println("[Terrain] splat @" + q[0] + "," + q[1]
                            + " grass=" + ((argb >> 16) & 0xFF)
                            + " rock=" + ((argb >> 8) & 0xFF)
                            + " dirt=" + (argb & 0xFF)
                            + " forest=" + ((argb >>> 24) & 0xFF));
                }
                int[] dom = new int[4];
                for (int y = 0; y < res; y++) {
                    for (int x = 0; x < res; x++) {
                        int c = img.getRGB(x, y);
                        int g0 = (c >> 16) & 0xFF;
                        int r0 = (c >> 8) & 0xFF;
                        int d0 = c & 0xFF;
                        int f0 = (c >>> 24) & 0xFF;
                        int best = 0;
                        int bv = g0;
                        if (r0 > bv) {
                            best = 1;
                            bv = r0;
                        }
                        if (d0 > bv) {
                            best = 2;
                            bv = d0;
                        }
                        if (f0 > bv) {
                            best = 3;
                        }
                        dom[best]++;
                    }
                }
                int total = res * res;
                System.out.println("[Terrain] dominant grass=" + (100 * dom[0] / total)
                        + "% rock=" + (100 * dom[1] / total)
                        + "% dirt=" + (100 * dom[2] / total)
                        + "% forest=" + (100 * dom[3] / total) + "%");
            } catch (Exception ex) {
                System.out.println("[Terrain] splat dump failed: " + ex.getMessage());
            }
        }
        return img;
    }

    /** 平滑阶跃；edge0 > edge1 时自动反向（用于"越靠近水越泥"这类）。 */
    private static float sstep(float edge0, float edge1, float v) {
        float t = MathX.clamp((v - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static int ch(float weight, float sum) {
        int v = Math.round(255f * weight / sum);
        return v < 0 ? 0 : Math.min(v, 255);
    }

    private void buildGround(AssetManager am) {
        int side = SEGS + 1;
        float step = WorldData.SIZE / SEGS;
        float[] pos = new float[side * side * 3];
        float[] nrm = new float[side * side * 3];
        float[] uv = new float[side * side * 2];
        int[] idx = new int[SEGS * SEGS * 6];
        float[] heights = new float[side * side];

        int p = 0;
        int t = 0;
        for (int iz = 0; iz < side; iz++) {
            float z = -WorldData.HALF + iz * step;
            for (int ix = 0; ix < side; ix++) {
                float x = -WorldData.HALF + ix * step;
                float h = height(x, z);
                heights[iz * side + ix] = h;
                pos[p] = x;
                pos[p + 1] = h;
                pos[p + 2] = z;
                p += 3;
                // 归一化世界 UV：AlphaMap 用它采样整张权重图，各层贴图再乘自己的 scale 平铺
                uv[t++] = (x + WorldData.HALF) / WorldData.SIZE;
                uv[t++] = (z + WorldData.HALF) / WorldData.SIZE;
            }
        }
        for (int iz = 0; iz < side; iz++) {
            for (int ix = 0; ix < side; ix++) {
                int i = iz * side + ix;
                float hl = heights[iz * side + Math.max(0, ix - 1)];
                float hr = heights[iz * side + Math.min(side - 1, ix + 1)];
                float hu = heights[Math.max(0, iz - 1) * side + ix];
                float hd = heights[Math.min(side - 1, iz + 1) * side + ix];
                float nx = (hl - hr) / (2f * step);
                float nz = (hu - hd) / (2f * step);
                float len = (float) Math.sqrt(nx * nx + 1f + nz * nz);
                nrm[i * 3] = nx / len;
                nrm[i * 3 + 1] = 1f / len;
                nrm[i * 3 + 2] = nz / len;
            }
        }
        int k = 0;
        for (int iz = 0; iz < SEGS; iz++) {
            for (int ix = 0; ix < SEGS; ix++) {
                int a = iz * side + ix;
                int b = a + 1;
                int c = a + side;
                int d = c + 1;
                idx[k++] = a;
                idx[k++] = b;
                idx[k++] = c;
                idx[k++] = b;
                idx[k++] = d;
                idx[k++] = c;
            }
        }

        // 切线基自己算：高度场的切线就是沿 +X 的坡面方向，一次遍历即可。
        // jME 的 TangentBinormalGenerator 在这张 13 万顶点的网格上要跑 34 秒，
        // 而且它的逐顶点搜索对规则高度场没有任何收益。
        float[] tan = new float[side * side * 4];
        for (int iz = 0; iz < side; iz++) {
            for (int ix = 0; ix < side; ix++) {
                int i = iz * side + ix;
                float hl = heights[iz * side + Math.max(0, ix - 1)];
                float hr = heights[iz * side + Math.min(side - 1, ix + 1)];
                float dx = (hr - hl) / (ix == 0 || ix == side - 1 ? step : 2f * step);
                float inv = 1f / (float) Math.sqrt(1f + dx * dx);
                tan[i * 4] = inv;
                tan[i * 4 + 1] = dx * inv;
                tan[i * 4 + 2] = 0f;
                // UV 的 v 随 +z 增长，cross(N,T) 指向 -z，所以手性取 -1
                tan[i * 4 + 3] = -1f;
            }
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(pos));
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(nrm));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(uv));
        mesh.setBuffer(VertexBuffer.Type.Tangent, 4, BufferUtils.createFloatBuffer(tan));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(idx));
        mesh.updateBound();
        mesh.setStatic();

        Geometry ground = new Geometry("limgraveGround", mesh);
        ground.setMaterial(groundMat);
        groundMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        ground.setShadowMode(RenderQueue.ShadowMode.Receive);
        root.attachChild(ground);
    }

    private void buildRoads(AssetManager am) {
        Geometry shoulder = new Geometry("roadShoulder", roadMesh(10.2f, 0.10f));
        shoulder.setMaterial(roadMaterial(am, new ColorRGBA(0.255f, 0.228f, 0.180f, 1f)));
        shoulder.setQueueBucket(RenderQueue.Bucket.Opaque);
        shoulder.setShadowMode(RenderQueue.ShadowMode.Receive);
        root.attachChild(shoulder);

        Geometry top = new Geometry("roadTop", roadMesh(7.4f, 0.16f));
        top.setMaterial(roadMaterial(am, new ColorRGBA(0.330f, 0.300f, 0.245f, 1f)));
        top.setQueueBucket(RenderQueue.Bucket.Opaque);
        top.setShadowMode(RenderQueue.ShadowMode.Receive);
        root.attachChild(top);
    }

    private Material roadMaterial(AssetManager am, ColorRGBA tint) {
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", tint);
        m.setColor("Ambient", tint.mult(0.28f));
        m.setColor("Specular", new ColorRGBA(0.02f, 0.02f, 0.018f, 1f));
        m.setFloat("Shininess", 2f);
        m.setTexture("DiffuseMap", Tex.dirt());
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        return m;
    }

    private Mesh roadMesh(float width, float lift) {
        java.util.ArrayList<Float> pos = new java.util.ArrayList<>();
        java.util.ArrayList<Float> uvs = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> idx = new java.util.ArrayList<>();
        float half = width * 0.5f;
        for (float[] road : WorldData.ROADS) {
            java.util.ArrayList<float[]> pts = new java.util.ArrayList<>();
            float dist = 0f;
            for (int i = 0; i + 3 < road.length; i += 2) {
                float ax = road[i];
                float az = road[i + 1];
                float bx = road[i + 2];
                float bz = road[i + 3];
                float len = (float) Math.hypot(bx - ax, bz - az);
                int steps = Math.max(1, (int) Math.ceil(len / 5f));
                for (int s = (i == 0 ? 0 : 1); s <= steps; s++) {
                    float t = s / (float) steps;
                    pts.add(new float[]{ax + (bx - ax) * t, az + (bz - az) * t, dist + len * t});
                }
                dist += len;
            }
            int n = pts.size();
            if (n < 2) {
                continue;
            }
            float[][] left = new float[n][];
            float[][] right = new float[n][];
            boolean[] valid = new boolean[n];
            for (int i = 0; i < n; i++) {
                float[] prev = pts.get(Math.max(0, i - 1));
                float[] next = pts.get(Math.min(n - 1, i + 1));
                float dx = next[0] - prev[0];
                float dz = next[1] - prev[1];
                float l = (float) Math.hypot(dx, dz);
                if (l < 1e-4f) {
                    dx = 0f;
                    dz = 1f;
                    l = 1f;
                }
                float nx = -dz / l;
                float nz = dx / l;
                float[] pt = pts.get(i);
                float gy = height(pt[0], pt[1]);
                valid[i] = gy > waterLevelAt(pt[0], pt[1]) + 0.95f;
                left[i] = new float[]{pt[0] + nx * half, gy + lift, pt[1] + nz * half,
                        pt[2] / 5.5f};
                right[i] = new float[]{pt[0] - nx * half, gy + lift, pt[1] - nz * half,
                        pt[2] / 5.5f};
            }
            for (int i = 0; i < n - 1; i++) {
                if (!valid[i] || !valid[i + 1]) {
                    continue;
                }
                int a = pos.size() / 3;
                for (float[] v : new float[][]{left[i], right[i], left[i + 1], right[i + 1]}) {
                    pos.add(v[0]);
                    pos.add(v[1]);
                    pos.add(v[2]);
                }
                float uEnd = width / 5.5f;
                uvs.add(0f);
                uvs.add(left[i][3]);
                uvs.add(uEnd);
                uvs.add(right[i][3]);
                uvs.add(0f);
                uvs.add(left[i + 1][3]);
                uvs.add(uEnd);
                uvs.add(right[i + 1][3]);
                idx.add(a);
                idx.add(a + 2);
                idx.add(a + 1);
                idx.add(a + 2);
                idx.add(a + 3);
                idx.add(a + 1);
            }
        }
        float[] p = new float[pos.size()];
        for (int i = 0; i < p.length; i++) {
            p[i] = pos.get(i);
        }
        float[] u = new float[uvs.size()];
        for (int i = 0; i < u.length; i++) {
            u[i] = uvs.get(i);
        }
        float[] nrm = new float[p.length];
        for (int i = 1; i < nrm.length; i += 3) {
            nrm[i] = 1f;
        }
        int[] ind = new int[idx.size()];
        for (int i = 0; i < ind.length; i++) {
            ind[i] = idx.get(i);
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(p));
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(nrm));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(u));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(ind));
        mesh.updateBound();
        mesh.setStatic();
        return mesh;
    }

    private void buildWater(AssetManager am) {
        float size = WorldData.SIZE * 4.2f;
        com.jme3.scene.shape.Quad quad = new com.jme3.scene.shape.Quad(size, size);
        quad.scaleTextureCoordinates(new Vector2f(220f, 220f));
        Geometry water = new Geometry("sea", quad);
        water.rotate(-FastMath.HALF_PI, 0f, 0f);
        water.setMaterial(waterMat);
        // Quad is corner-anchored; after the -90deg X rotation it spans x:[0,size], z:[-size,0].
        water.setLocalTranslation(-size * 0.5f, WorldData.WATER_Y, size * 0.5f);
        water.setQueueBucket(RenderQueue.Bucket.Transparent);
        water.setShadowMode(RenderQueue.ShadowMode.Off);
        root.attachChild(water);

        // 内陆水塘各自一张水面；用椭圆盘而不是矩形——矩形的角会伸出岸唇，
        // 在地形低处露出蓝色的尖角。盘半径留在岸唇环内，多余部分被抬高地形盖住。
        for (int i = 0; i < WorldData.PONDS.length; i++) {
            WorldData.Pond p = WorldData.PONDS[i];
            Geometry pond = new Geometry("pond-" + p.name(),
                    discMesh(p.rx() * 1.14f, p.rz() * 1.14f, 48, 26.7f));
            pond.setMaterial(waterMat);
            pond.setLocalTranslation(p.x(), POND_LEVEL[i], p.z());
            pond.setQueueBucket(RenderQueue.Bucket.Transparent);
            pond.setShadowMode(RenderQueue.ShadowMode.Off);
            root.attachChild(pond);
        }
    }

    /** 水平椭圆盘（三角扇），UV 按世界米平铺，给水塘当水面。 */
    private static Mesh discMesh(float rx, float rz, int seg, float tile) {
        int n = seg + 2;
        float[] pos = new float[n * 3];
        float[] uv = new float[n * 2];
        float[] nrm = new float[n * 3];
        int[] idx = new int[seg * 3];
        for (int i = 0; i < n; i++) {
            float a = i == 0 ? 0f : FastMath.TWO_PI * (i - 1) / seg;
            float x = i == 0 ? 0f : FastMath.sin(a) * rx;
            float z = i == 0 ? 0f : FastMath.cos(a) * rz;
            pos[i * 3] = x;
            pos[i * 3 + 1] = 0f;
            pos[i * 3 + 2] = z;
            uv[i * 2] = x / tile;
            uv[i * 2 + 1] = z / tile;
            nrm[i * 3 + 1] = 1f;
        }
        for (int i = 0; i < seg; i++) {
            idx[i * 3] = 0;
            idx[i * 3 + 1] = 1 + i;
            idx[i * 3 + 2] = 1 + (i + 1) % seg;
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(pos));
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(nrm));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(uv));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(idx));
        mesh.updateBound();
        mesh.setStatic();
        return mesh;
    }

    private void buildSky(AssetManager am) {
        Geometry sky = new Geometry("sky", new Sphere(24, 48, 2900f, true, true));
        Material mat = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", Tex.sky());
        mat.setColor("Color", new ColorRGBA(0.9f, 0.92f, 1f, 1f));
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Off);
        sky.setMaterial(mat);
        skyMat = mat;
        sky.setQueueBucket(RenderQueue.Bucket.Sky);
        sky.setShadowMode(RenderQueue.ShadowMode.Off);
        sky.setCullHint(Spatial.CullHint.Never);
        root.attachChild(sky);
    }

    // ---------------------------------------------------------------- map art

    private static BufferedImage paintMap() {
        BufferedImage img = new BufferedImage(MAP_RES, MAP_RES, BufferedImage.TYPE_INT_RGB);
        float scale = WorldData.SIZE / MAP_RES;
        for (int py = 0; py < MAP_RES; py++) {
            float z = -WorldData.HALF + (py + 0.5f) * scale;
            for (int px = 0; px < MAP_RES; px++) {
                float x = -WorldData.HALF + (px + 0.5f) * scale;
                img.setRGB(px, py, terrainColor(x, z));
            }
            bakeDone++;
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(7.2f * MAP_RES / WorldData.SIZE, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.setColor(new Color(66, 55, 40));
        for (float[] road : WorldData.ROADS) {
            drawRoad(g, road, scale);
        }
        g.setStroke(new BasicStroke(4.2f * MAP_RES / WorldData.SIZE, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g.setColor(new Color(109, 94, 69));
        for (float[] road : WorldData.ROADS) {
            drawRoad(g, road, scale);
        }
        g.dispose();
        return img;
    }

    private static void drawRoad(Graphics2D g, float[] road, float scale) {
        int n = road.length / 2;
        for (int i = 0; i + 1 < n; i++) {
            float ax = road[i * 2];
            float az = road[i * 2 + 1];
            float bx = road[i * 2 + 2];
            float bz = road[i * 2 + 3];
            int steps = Math.max(1, (int) Math.ceil(Math.hypot(bx - ax, bz - az) / 6f));
            int px = 0;
            int py = 0;
            boolean has = false;
            for (int s = 0; s <= steps; s++) {
                float t = s / (float) steps;
                float x = ax + (bx - ax) * t;
                float z = az + (bz - az) * t;
                int ix = Math.round((x + WorldData.HALF) / scale);
                int iy = Math.round((z + WorldData.HALF) / scale);
                boolean dry = height0(x, z) > waterLevelAt(x, z) + 0.95f;
                if (dry && has) {
                    g.drawLine(px, py, ix, iy);
                }
                px = ix;
                py = iy;
                has = dry;
            }
        }
    }

    private static int terrainColor(float x, float z) {
        float h = height0(x, z);
        float s = slope0(x, z);
        float lvl = waterLevelAt(x, z);
        float n = noise(x * 0.12f, z * 0.12f);
        float n2 = noise(x * 0.031f + 3.7f, z * 0.031f - 1.3f);

        float r;
        float gg;
        float b;
        float mist = smooth(270f, 390f, x) * smooth(210f, 340f, z);
        float storm = smooth(-120f, -400f, z);

        r = 0.22f + 0.10f * n + 0.035f * n2;
        gg = 0.287f + 0.085f * n + 0.03f * n2;
        b = 0.118f + 0.045f * n;
        r += 0.045f * storm;
        gg += 0.012f * storm;
        r *= 1f - 0.14f * mist;
        gg *= 1f - 0.10f * mist;
        b *= 1f - 0.02f * mist;

        if (h > 34f || s > 0.46f) {
            float rock = MathX.clamp((Math.max(h - 34f, 0f) / 26f) + (s - 0.46f) * 1.8f, 0f, 1f);
            r = lerp(r, 0.35f + 0.09f * n, rock);
            gg = lerp(gg, 0.33f + 0.07f * n, rock);
            b = lerp(b, 0.285f + 0.05f * n, rock);
        }
        float beach = 1f - MathX.clamp((h - lvl) / 0.85f, 0f, 1f);
        float barD = WorldData.segmentDistance(x, z, 250f, 292f, 250f, 128f);
        float isleD = dist(x, z, 250f, 108f);
        float sand = Math.max(beach * 0.70f,
                Math.max(MathX.clamp((12f - barD) / 5f, 0f, 1f),
                        MathX.clamp((30f - isleD) / 7f, 0f, 1f)));
        if (h < lvl + 0.4f) {
            float depth = MathX.clamp(lvl - h, 0f, 3.5f) / 3.5f;
            r = lerp(0.44f, 0.12f, depth) + 0.03f * n;
            gg = lerp(0.41f, 0.19f, depth) + 0.03f * n;
            b = lerp(0.27f, 0.20f, depth) + 0.02f * n;
        } else if (sand > 0.01f) {
            r = lerp(r, 0.50f, sand * 0.80f);
            gg = lerp(gg, 0.45f, sand * 0.74f);
            b = lerp(b, 0.31f, sand * 0.62f);
        }
        int ri = (int) (MathX.clamp(r, 0f, 1f) * 255f);
        int gi = (int) (MathX.clamp(gg, 0f, 1f) * 255f);
        int bi = (int) (MathX.clamp(b, 0f, 1f) * 255f);
        return (ri << 16) | (gi << 8) | bi;
    }

    // ---------------------------------------------------------------- noise

    private static float noise(float x, float z) {
        int xi = (int) FastMath.floor(x);
        int zi = (int) FastMath.floor(z);
        float xf = x - xi;
        float zf = z - zi;
        float u = xf * xf * (3f - 2f * xf);
        float v = zf * zf * (3f - 2f * zf);
        float a = hash(xi, zi);
        float b = hash(xi + 1, zi);
        float c = hash(xi, zi + 1);
        float d = hash(xi + 1, zi + 1);
        return lerp(lerp(a, b, u), lerp(c, d, u), v);
    }

    private static float hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= h >> 16;
        return (h & 0x7fffffff) / (float) 0x7fffffff;
    }

    private static float smooth(float a, float b, float x) {
        float t = MathX.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float sq(float v) {
        return v * v;
    }

    private static float dist(float ax, float az, float bx, float bz) {
        float dx = ax - bx;
        float dz = az - bz;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }
}
