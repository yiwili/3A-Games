package com.aedfh;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimLayer;
import com.jme3.anim.Armature;
import com.jme3.anim.SkinningControl;
import com.jme3.anim.tween.action.Action;
import com.jme3.asset.AssetManager;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Matrix4f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * 带骨骼的第三方角色（CC0）：KayKit 人形在 {@code Models/KayKit/Chars/}，
 * Quaternius 四足在 {@code Models/Quaternius/}，许可与出处见各自目录的 LICENSE 文件。
 *
 * <p>与 {@link Gltf} 的区别：道具要合批，角色要每实例独立的骨架与动画；所以这里
 * {@code clone(false)}（共享网格与材质，但 {@code AnimComposer}/{@code Armature} 各自克隆），
 * 并把「语义动作名 → 剪辑名」的播放状态收在这一层，业务侧只关心 Idle/Run/Attack/Death。
 *
 * <p>材质两种来源：带贴图的（KayKit）抽 BaseColorMap 做一张共用 Phong；只有材质色的
 * （Quaternius）逐几何体按 BaseColor 建 Phong，并把线性色转 sRGB，否则会渲成黑块。
 *
 * <p>缩放不用包围盒：glTF 的蒙皮几何顶点在**骨骼局部空间**，{@code getWorldBound()} 量的
 * 不是人高。这里用 {@code Armature.computeSkinningMatrices()} 在 CPU 上蒙皮一次，量出真实
 * 脚底/头顶，按目标身高等比缩放（脚底对齐 y=0）。
 */
final class ActorRig {

    private static final String DIR = "Models/KayKit/Chars/";
    private static final Map<String, float[]> BOUNDS = new HashMap<>();
    private static final Map<String, Material> SHARED = new HashMap<>();

    private final Node anchor = new Node("actorRig");
    private final AnimComposer composer;
    private String current;
    private float currentSpeed = 1f;

    private ActorRig(AnimComposer composer) {
        this.composer = composer;
    }

    /** 加载并克隆一份；失败返回 {@code null}，调用方回退程序化模型。 */
    static ActorRig load(AssetManager am, String name, float targetHeight, String... strip) {
        return load(am, name, targetHeight, null, strip);
    }

    static ActorRig load(AssetManager am, String name, float targetHeight,
                         ColorRGBA tint, String... strip) {
        String path = resolve(name);
        Spatial proto;
        try {
            proto = am.loadModel(path);
        } catch (Throwable t) {
            System.out.println("[ActorRig] load failed " + path + ": " + t);
            return null;
        }
        proto.updateGeometricState();
        if (convert(am, path, proto) == null) {
            return null;
        }
        float[] b = bounds(path, proto);
        float h = Math.max(0.3f, b[1] - b[0]);
        Spatial copy = proto.clone(tint != null);
        AnimComposer ac = find(copy, AnimComposer.class);
        if (ac == null) {
            System.out.println("[ActorRig] no AnimComposer in " + path);
            return null;
        }
        if (tint != null) {
            tint(copy, tint);
        }
        for (String node : strip) {
            Spatial s = findNamed(copy, node);
            if (s != null) {
                s.removeFromParent();
            }
        }
        float k = targetHeight / h;
        copy.setLocalTranslation(0f, -b[0] * k, 0f);
        copy.setLocalScale(k);
        ActorRig rig = new ActorRig(ac);
        rig.anchor.attachChild(copy);
        return rig;
    }

    Node node() {
        return anchor;
    }

    /** 带路径的按原样加载；只写名字的走 KayKit 角色目录。 */
    private static String resolve(String name) {
        return name.indexOf('/') >= 0 ? name : DIR + name + ".glb";
    }

    boolean has(String clip) {
        return clip != null && composer.getAnimClip(clip) != null;
    }

    float clipLength(String clip) {
        return has(clip) ? (float) composer.getAnimClip(clip).getLength() : 0f;
    }

    /** 切换剪辑；同名重复调用只更新速度，不会把动作重头播一遍。 */
    void play(String clip, boolean loop, float speed) {
        if (clip == null || !has(clip)) {
            return;
        }
        if (clip.equals(current)) {
            if (Math.abs(currentSpeed - speed) > 0.01f) {
                composer.setGlobalSpeed(speed);
                currentSpeed = speed;
            }
            return;
        }
        Action action = composer.setCurrentAction(clip);
        if (action == null) {
            return;
        }
        AnimLayer layer = composer.getLayer(AnimComposer.DEFAULT_LAYER);
        if (layer != null) {
            layer.setLooping(loop);
            layer.setTime(0);
        }
        composer.setGlobalSpeed(speed);
        current = clip;
        currentSpeed = speed;
    }

    void stop() {
        composer.removeCurrentAction();
        current = null;
    }

    /** 从骨骼挂点取一个节点（手/头/胸）；没有该骨骼时返回 {@code null}。 */
    Node attachment(String bone) {
        SkinningControl sc = find(anchor, SkinningControl.class);
        return sc == null ? null : sc.getAttachmentsNode(bone);
    }

    // ------------------------------------------------------------- material

    private static Material convert(AssetManager am, String name, Spatial proto) {
        Material shared = SHARED.get(name);
        if (shared != null) {
            apply(proto, shared);
            return shared;
        }
        Texture atlas = null;
        for (Geometry g : geos(proto)) {
            Material old = g.getMaterial();
            if (old == null || isPhong(old)) {
                continue;
            }
            Texture base = baseColor(old);
            if (base != null) {
                atlas = base;
                break;
            }
        }
        if (atlas != null) {
            shared = phong(am, atlas);
            SHARED.put(name, shared);
            apply(proto, shared);
            return shared;
        }
        // 没有贴图的包（Quaternius 的狼靠每个几何体的 BaseColor 分色）：逐个换成纯色 Phong
        Material first = null;
        for (Geometry g : geos(proto)) {
            Material old = g.getMaterial();
            if (old == null || isPhong(old)) {
                first = first == null ? old : first;
                continue;
            }
            ColorRGBA c = colorOf(old, "BaseColor");
            if (c == null) {
                c = colorOf(old, "Diffuse");
            }
            Material m = phongColor(am, c == null ? new ColorRGBA(0.55f, 0.53f, 0.5f, 1f) : c);
            g.setMaterial(m);
            first = first == null ? m : first;
        }
        return first;
    }

    private static boolean isPhong(Material m) {
        return m != null && m.getMaterialDef().getName().contains("Lighting.j3md");
    }

    private static ColorRGBA colorOf(Material m, String key) {
        MatParam p = m.getParam(key);
        return p != null && p.getValue() instanceof ColorRGBA c ? c : null;
    }

    private static Material phongColor(AssetManager am, ColorRGBA diffuse) {
        // glTF 的 BaseColor 是线性值，Phong 的 Diffuse 按 sRGB 解释，不转会把狼调成黑狗
        ColorRGBA d = new ColorRGBA(srgb(diffuse.r), srgb(diffuse.g), srgb(diffuse.b), 1f);
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", d);
        m.setColor("Ambient", new ColorRGBA(d.r * 0.12f, d.g * 0.12f, d.b * 0.12f, 1f));
        m.setColor("Specular", new ColorRGBA(0.04f, 0.04f, 0.04f, 1f));
        m.setFloat("Shininess", 5f);
        return m;
    }

    private static float srgb(float linear) {
        return (float) Math.pow(Math.max(0f, linear), 1.0 / 2.2);
    }

    private static void apply(Spatial s, Material m) {
        if (s instanceof Geometry g) {
            g.setMaterial(m);
        } else if (s instanceof Node n) {
            for (Spatial c : n.getChildren()) {
                apply(c, m);
            }
        }
    }

    private static Texture baseColor(Material m) {
        for (String key : new String[]{"BaseColorMap", "BaseColorTexture", "DiffuseMap"}) {
            MatParam p = m.getParam(key);
            if (p != null && p.getValue() instanceof Texture t) {
                return t;
            }
        }
        return null;
    }

    private static Material phong(AssetManager am, Texture atlas) {
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setTexture("DiffuseMap", atlas);
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Diffuse", new ColorRGBA(0.92f, 0.91f, 0.88f, 1f));
        m.setColor("Ambient", new ColorRGBA(0.10f, 0.10f, 0.105f, 1f));
        m.setColor("Specular", new ColorRGBA(0.05f, 0.05f, 0.05f, 1f));
        m.setFloat("Shininess", 6f);
        return m;
    }

    private static void tint(Spatial s, ColorRGBA c) {
        if (s instanceof Geometry g) {
            Material m = g.getMaterial();
            if (m != null && m.getMaterialDef().getName().contains("Lighting.j3md")) {
                m.setColor("Diffuse", c);
            }
        } else if (s instanceof Node n) {
            for (Spatial child : n.getChildren()) {
                tint(child, c);
            }
        }
    }

    private static java.util.List<Geometry> geos(Spatial proto) {
        java.util.List<Geometry> out = new java.util.ArrayList<>();
        proto.depthFirstTraversal(sp -> {
            if (sp instanceof Geometry g) {
                out.add(g);
            }
        });
        return out;
    }

    private static float[] bounds(String name, Spatial proto) {
        float[] cached = BOUNDS.get(name);
        if (cached != null) {
            return cached;
        }
        float[] b = measure(proto);
        BOUNDS.put(name, b);
        return b;
    }

    /** CPU 蒙皮后的 y 包围（[minY, maxY]，模型单位），失败回退 1.8m 站立。 */
    private static float[] measure(Spatial proto) {
        float min = 0f;
        float max = 1.8f;
        try {
            SkinningControl sc = find(proto, SkinningControl.class);
            if (sc == null) {
                return new float[]{min, max};
            }
            Armature arm = sc.getArmature();
            arm.update();
            Matrix4f[] mats = arm.computeSkinningMatrices();
            float[] acc = {1e9f, -1e9f};
            for (Geometry g : geos(proto)) {
                Mesh mesh = g.getMesh();
                VertexBuffer pb = mesh.getBuffer(VertexBuffer.Type.Position);
                VertexBuffer ib = mesh.getBuffer(VertexBuffer.Type.BoneIndex);
                VertexBuffer wb = mesh.getBuffer(VertexBuffer.Type.BoneWeight);
                if (pb == null || ib == null || wb == null) {
                    continue;
                }
                Vector3f[] pos = BufferUtils.getVector3Array((FloatBuffer) pb.getDataReadOnly());
                Object idx = ib.getDataReadOnly();
                FloatBuffer wts = (FloatBuffer) wb.getDataReadOnly();
                int comps = ib.getNumComponents();
                g.updateGeometricState();
                Matrix4f gw = g.getWorldMatrix();
                Vector3f tmp = new Vector3f();
                for (int v = 0; v < pos.length; v++) {
                    tmp.set(0f, 0f, 0f);
                    boolean any = false;
                    for (int k = 0; k < 4; k++) {
                        int bi = boneIndex(idx, comps, v, k);
                        float w = wts.get(v * 4 + k);
                        if (w == 0f || bi >= mats.length) {
                            continue;
                        }
                        Vector3f tv = mats[bi].mult(pos[v], new Vector3f());
                        tmp.addLocal(tv.multLocal(w));
                        any = true;
                    }
                    if (!any) {
                        tmp.set(pos[v]);
                    }
                    gw.mult(tmp, tmp);
                    acc[0] = Math.min(acc[0], tmp.y);
                    acc[1] = Math.max(acc[1], tmp.y);
                }
            }
            if (acc[1] > acc[0]) {
                min = acc[0];
                max = acc[1];
            }
        } catch (Throwable t) {
            System.out.println("[ActorRig] bounds failed: " + t);
        }
        return new float[]{min, max};
    }

    /**
     * 骨骼索引在不同 glTF 里可能是 ubyte (ByteBuffer) 或 ushort (ShortBuffer)，
     * 直接强转 ByteBuffer 会 ClassCastException（Quaternius 的狼就是 short）。
     */
    private static int boneIndex(Object data, int comps, int v, int k) {
        int at = v * comps + k;
        if (data instanceof ByteBuffer bb) {
            return bb.get(at) & 0xFF;
        }
        if (data instanceof ShortBuffer sb) {
            return sb.get(at) & 0xFFFF;
        }
        if (data instanceof IntBuffer ib) {
            return ib.get(at);
        }
        return 0;
    }

    private static <T> T find(Spatial s, Class<T> type) {
        final Object[] out = {null};
        s.depthFirstTraversal(sp -> {
            for (int i = 0; i < sp.getNumControls(); i++) {
                if (type.isInstance(sp.getControl(i))) {
                    out[0] = sp.getControl(i);
                }
            }
        });
        return type.cast(out[0]);
    }

    private static Spatial findNamed(Spatial s, String name) {
        if (s instanceof Node n) {
            Spatial direct = n.getChild(name);
            if (direct != null) {
                return direct;
            }
            for (Spatial child : n.getChildren()) {
                Spatial hit = findNamed(child, name);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }
}
