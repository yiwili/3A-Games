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
import java.util.Random;

/**
 * Analytic Limgrave height field plus a single LOD-free ground mesh. All
 * gameplay queries use the same height() function, so nothing ever floats.
 */
final class WorldTerrain {

    static final int SEGS = 240;
    private static final int MAP_RES = 768;
    /** 地表混合权重图分辨率：权重是低频量，512² 覆盖 1400m ≈ 2.7m/px 足够。 */
    private static final int SPLAT_RES = 512;

    private final Node root = new Node("limgraveTerrain");
    private final BufferedImage mapImage;
    private final Texture2D mapTexture;
    private final Material groundMat;
    private final Material waterMat;
    private Material skyMat;
    private final Random rnd = new Random(7);

    WorldTerrain(AssetManager am) {
        mapImage = paintMap();
        mapTexture = Tex.image(mapImage, false);
        mapTexture.setAnisotropicFilter(8);

        groundMat = buildGroundMaterial(am);

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
        float h = rawHeight(x, z);
        // 开局试炼洞窟：整片区域压平到洞底标高，丘陵不再刺穿洞壳/地面/穹顶。
        if (Float.isNaN(caveBase)) {
            caveBase = rawHeight(0f, 348f);
        }
        float d = Math.max(Math.abs(x), Math.abs(z - 348f)) / 40f;
        if (d < 1f) {
            float k = FastMath.clamp((d - 0.68f) / 0.32f, 0f, 1f);
            k = k * k * (3f - 2f * k);
            h = FastMath.interpolateLinear(k, caveBase, h);
        }
        return h;
    }

    private float rawHeight(float x, float z) {
        float h = 5.6f;
        h += 2.3f * FastMath.sin(x * 0.0063f + 1.1f) * FastMath.cos(z * 0.0051f - 0.4f);
        h += 1.35f * FastMath.sin(x * 0.017f + 2.0f) * FastMath.sin(z * 0.014f + 0.7f);
        h += (noise(x * 0.021f, z * 0.021f) - 0.5f) * 2.8f;
        h += (noise(x * 0.068f + 11f, z * 0.068f - 7f) - 0.5f) * 0.9f;

        float storm = smooth(-120f, -630f, z);
        float west = smooth(160f, -510f, x);
        h += 56f * storm * (0.58f + 0.42f * west);

        float castle = dist(x, z, WorldData.STORM_X, WorldData.STORM_Z + 22f);
        h = lerp(h, 66f, smooth(215f, 92f, castle));

        h += 2.6f * smooth(250f, 460f, x) * smooth(200f, 410f, z);
        float erdtree = dist(x, z, 540f, 410f);
        h += 17f * smooth(180f, 58f, erdtree);

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

    float waterDepth(float x, float z) {
        return WorldData.WATER_Y - height(x, z);
    }

    boolean dry(float x, float z) {
        return height(x, z) > WorldData.WATER_Y + 0.25f;
    }

    float slope(float x, float z) {
        float d = 2.2f;
        float dx = height(x + d, z) - height(x - d, z);
        float dz = height(x, z + d) - height(x, z - d);
        return (float) Math.sqrt(dx * dx + dz * dz) / (2f * d);
    }

    float normalY(float x, float z) {
        return 1f / (float) Math.sqrt(1f + slope(x, z) * slope(x, z));
    }

    // ---------------------------------------------------------------- ground

    // ---------------------------------------------------------------- splat material

    /**
     * 地形多层 splat：草地（平地）/ 岩石（陡坡·高处·城堡台地）/ 泥土（路网·水岸）/ 林地（雾林）。
     *
     * <p>四层权重烘进一张 {@link #SPLAT_RES}² 的 AlphaMap（RGBA 四通道），细节仍由各层 1k 贴图
     * 按世界坐标平铺提供——权重是低频的，可以粗；细节是高频道，必须靠平铺，不能烘进一张大图。
     * UV 约定：TexCoord = (x+HALF)/SIZE，各层再用 {@code DiffuseMap_N_scale} 拉回米级平铺。
     */
    private Material buildGroundMaterial(AssetManager am) {
        Material m = new Material(am, "Common/MatDefs/Terrain/TerrainLighting.j3md");
        m.setBoolean("useTriPlanarMapping", false);
        // MultiPass 的顶点着色器把 SpecularSum 直接取成 lightColor（不乘反照率），
        // Shininess 一小就等于往地面上糊一层纯白高光。抬到 48 让高光只在正对太阳的坡面出现。
        m.setFloat("Shininess", 48f);

        // 四层：草地 / 岩石 / 泥土 / 林地。原图是枯黄调的写实素材，直接铺会把整张地图染成沙漠，
        // 所以按层给一个染色系数（同时压亮度），把地表拉回"湿绿草地 + 灰岩 + 深褐土 + 林荫"。
        String[] bases = {"aerial_grass_rock", "rock_wall", "dirt", "forest_floor"};
        float[] tiles = {6.5f, 11f, 5.5f, 7f};
        float[][] tints = {
                {0.68f, 0.92f, 0.52f},
                {1.00f, 1.02f, 1.10f},
                {0.72f, 0.64f, 0.50f},
                {0.46f, 0.62f, 0.38f},
        };
        // 岩石层原图是暖褐砌石，先按亮度去饱和再轻微偏冷，才像石灰岩台地
        float[] desat = {0f, 0.62f, 0.12f, 0.10f};
        for (int i = 0; i < bases.length; i++) {
            String suffix = i == 0 ? "" : "_" + i;
            var diffuse = Tex.tinted("Textures/PolyHaven/" + bases[i] + "_diff.jpg",
                    i == 0 ? 512 : 384, tints[i][0], tints[i][1], tints[i][2], desat[i]);
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
            m.setFloat("DiffuseMap_" + i + "_scale", WorldData.SIZE / tiles[i]);
        }
        m.setTexture("AlphaMap", Tex.image(paintSplat(), false));
        return m;
    }

    /** 逐像素算四层权重，写进 AlphaMap（R=草地 G=岩石 B=泥土 A=林地）。 */
    private BufferedImage paintSplat() {
        int res = SPLAT_RES;
        float step = WorldData.SIZE / res;
        float[][] hs = new float[res + 1][res + 1];
        for (int iz = 0; iz <= res; iz++) {
            float z = -WorldData.HALF + iz * step;
            for (int ix = 0; ix <= res; ix++) {
                hs[iz][ix] = height(-WorldData.HALF + ix * step, z);
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

                // 岩石：坡度 → 海拔 → 城堡台地 → 零散裸岩
                float rock = sstep(0.50f, 1.00f, s);
                rock = Math.max(rock, sstep(36f, 60f, h) * 0.9f);
                rock = Math.max(rock, (1f - sstep(96f, 168f,
                        dist(x, z, WorldData.STORM_X, WorldData.STORM_Z))) * 0.85f);
                rock = Math.max(rock, sstep(0.62f, 0.88f,
                        noise(x * 0.02f + 5f, z * 0.02f - 3f)) * 0.3f);

                // 泥土：只做路肩（路带 10m 宽）与窄水岸，别糊满全图
                float road = 1f - sstep(4.5f, 10f, WorldData.roadDistance(x, z));
                float shore = sstep(WorldData.WATER_Y + 0.55f, WorldData.WATER_Y + 0.10f, h);
                float dirt = Math.max(road * 0.85f, shore * 0.55f);

                // 林地：雾林一带的树荫地
                float mist = sstep(250f, 360f, x) * sstep(190f, 320f, z);
                float forest = mist * (0.30f + 0.60f
                        * noise(x * 0.03f + 11f, z * 0.03f + 2f));

                dirt *= 1f - rock * 0.75f;
                forest *= (1f - rock) * (1f - road * 0.85f);
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
        }
        // 调参用：-Daedfh.splatDump=build/splat.png 把权重图导出来看
        String dump = System.getProperty("aedfh.splatDump");
        if (dump != null && !dump.isBlank()) {
            try {
                // 导出成不透明图：R=草地 G=岩石 B=泥土（林地 = 1-三者之和）
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
                        {180f, -28f}, {355f, 315f}, {-165f, 170f}, {0f, 200f}};
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

        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(pos));
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(nrm));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(uv));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(idx));
        mesh.updateBound();
        mesh.setStatic();

        Geometry ground = new Geometry("limgraveGround", mesh);
        ground.setMaterial(groundMat);
        groundMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        ground.setShadowMode(RenderQueue.ShadowMode.Receive);
        root.attachChild(ground);
        // 四层各带法线贴图，需要切线基
        Gfx.tangents(ground);
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
                valid[i] = gy > WorldData.WATER_Y + 0.95f;
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

    private BufferedImage paintMap() {
        BufferedImage img = new BufferedImage(MAP_RES, MAP_RES, BufferedImage.TYPE_INT_RGB);
        float scale = WorldData.SIZE / MAP_RES;
        for (int py = 0; py < MAP_RES; py++) {
            float z = -WorldData.HALF + (py + 0.5f) * scale;
            for (int px = 0; px < MAP_RES; px++) {
                float x = -WorldData.HALF + (px + 0.5f) * scale;
                img.setRGB(px, py, terrainColor(x, z));
            }
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

    private void drawRoad(Graphics2D g, float[] road, float scale) {
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
                boolean dry = height(x, z) > WorldData.WATER_Y + 0.95f;
                if (dry && has) {
                    g.drawLine(px, py, ix, iy);
                }
                px = ix;
                py = iy;
                has = dry;
            }
        }
    }

    private int terrainColor(float x, float z) {
        float h = height(x, z);
        float s = slope(x, z);
        int seed = (int) (x * 12.9898f + z * 78.233f);
        float n = noise(x * 0.12f, z * 0.12f);
        float n2 = noise(x * 0.031f + 3.7f, z * 0.031f - 1.3f);

        float r;
        float gg;
        float b;
        float mist = smooth(270f, 390f, x) * smooth(210f, 340f, z);
        float storm = smooth(-170f, -430f, z);

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
        float beach = 1f - MathX.clamp((h - WorldData.WATER_Y) / 0.85f, 0f, 1f);
        float barD = WorldData.segmentDistance(x, z, 250f, 292f, 250f, 128f);
        float isleD = dist(x, z, 250f, 108f);
        float sand = Math.max(beach * 0.70f,
                Math.max(MathX.clamp((12f - barD) / 5f, 0f, 1f),
                        MathX.clamp((30f - isleD) / 7f, 0f, 1f)));
        if (h < WorldData.WATER_Y + 0.4f) {
            float depth = MathX.clamp(WorldData.WATER_Y - h, 0f, 3.5f) / 3.5f;
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
