package com.aedfh;

import com.jme3.asset.AssetManager;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;

import java.util.HashMap;
import java.util.Map;

/**
 * KayKit 低模 glTF 道具库（CC0，许可见 {@code Models/KayKit/LICENSE.txt}）。
 *
 * <p>三件事：
 * <ol>
 *   <li>加载：{@code jme3-plugins} 自带 {@code GltfLoader}，但只在 {@code General.cfg} 里注册，
 *       这里再显式注册一次兜底；模型是 Y-up、底面 y=0、单张内嵌 atlas，直接落地摆放。</li>
 *   <li>换材质：glb 自带 {@code PBRLighting}，本项目跑的是 Phong 传统管线且没有 LightProbe，
 *       PBR 材质会发暗发死。这里把内嵌的 BaseColorTexture 抽出来，套到项目统一的
 *       {@code Lighting.j3md} 上，并把颜色乘暗一档对齐场景调子。</li>
 *   <li>复用：同一 {@code AssetManager} 对同一路径返回同一实例，所以原型只加载一次，
 *       放置时 {@code clone(false)} 共享网格与<b>同一个</b>材质实例——BatchNode 才合得动批。</li>
 * </ol>
 */
final class Gltf {

    private static final String DIR = "Models/KayKit/";

    private final AssetManager am;
    private final Map<String, Spatial> protos = new HashMap<>();
    private final Map<String, Vector3f> sizes = new HashMap<>();
    private Material shared;

    Gltf(AssetManager am) {
        this.am = am;
        // 注意：不要再 registerLoader(GltfLoader.class, "glb") —— jme3-core 的 General.cfg 里
        // 已经是 GlbLoader:glb（二进制容器）+ GltfLoader:gltf（JSON），覆盖成 GltfLoader 会把
        // glb 的 12 字节文件头当 JSON 解析，报 "Not a JSON Object: glTF..."。
    }

    /** 所有 glTF 道具共用的材质；BatchNode 靠它把不同 glb 合到一批。 */
    Material material() {
        return shared;
    }

    /** 克隆一份用于摆放；加载失败返回 {@code null}，调用方回退程序化几何。 */
    Spatial spawn(String name) {
        Spatial proto = proto(name);
        if (proto == null) {
            return null;
        }
        Spatial copy = proto.clone(false);
        copy.updateGeometricState();
        return copy;
    }

    /** 原始尺寸（米，未缩放）。 */
    Vector3f size(String name) {
        proto(name);
        Vector3f v = sizes.get(name);
        return v == null ? null : v.clone();
    }

    /** 目标高度 → 等比缩放系数（模型底面在 y=0，缩完仍然贴地）。 */
    float fitHeight(String name, float targetH) {
        Vector3f s = size(name);
        if (s == null || s.y <= 0.01f) {
            return 1f;
        }
        return targetH / s.y;
    }

    private Spatial proto(String name) {
        Spatial p = protos.get(name);
        if (p != null) {
            return p;
        }
        try {
            p = am.loadModel(DIR + name + ".glb");
        } catch (Throwable t) {
            System.out.println("[Gltf] load failed " + name + ": " + t);
            Throwable c = t.getCause();
            while (c != null) {
                System.out.println("   cause: " + c.getClass().getName() + ": " + c.getMessage());
                c = c.getCause();
            }
            return null;
        }
        retarget(p);
        p.updateGeometricState();
        var bb = (com.jme3.bounding.BoundingBox) p.getWorldBound();
        sizes.put(name, bb.getMax(new Vector3f()).subtract(bb.getMin(new Vector3f())));
        protos.put(name, p);
        return p;
    }

    private void retarget(Spatial s) {
        if (s instanceof Geometry g) {
            Material old = g.getMaterial();
            if (old != null) {
                Texture base = baseColor(old);
                if (base != null && shared == null) {
                    shared = phong(base);
                }
                if (System.getProperty("aedfh.gltfdebug") != null) {
                    StringBuilder sb = new StringBuilder();
                    for (MatParam mp : old.getParams()) {
                        sb.append(mp.getName()).append('=').append(mp.getVarType()).append(' ');
                    }
                    System.out.println("[Gltf] geo=" + g.getName() + " def="
                            + old.getMaterialDef().getName() + " base=" + (base != null)
                            + " shared=" + (shared != null) + " params: " + sb);
                }
            }
            if (shared != null) {
                g.setMaterial(shared);
            }
            return;
        }
        if (s instanceof Node n) {
            for (Spatial c : n.getChildren()) {
                retarget(c);
            }
        }
    }

    private static Texture baseColor(Material m) {
        // jME 的 PBRLighting 用 BaseColorMap；不同版本/加载器也可能是 BaseColorTexture 或 DiffuseMap
        for (String key : new String[]{"BaseColorMap", "BaseColorTexture", "DiffuseMap"}) {
            MatParam p = m.getParam(key);
            if (p != null && p.getValue() instanceof Texture t) {
                return t;
            }
        }
        return null;
    }

    private Material phong(Texture atlas) {
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setTexture("DiffuseMap", atlas);
        m.setBoolean("UseMaterialColors", true);
        // KayKit 地牢包自带深灰石调，但直接铺比本项目 stone 亮一档，乘 0.85 拉齐
        m.setColor("Diffuse", new ColorRGBA(0.85f, 0.84f, 0.82f, 1f));
        m.setColor("Ambient", new ColorRGBA(0.085f, 0.085f, 0.09f, 1f));
        m.setColor("Specular", new ColorRGBA(0.03f, 0.03f, 0.03f, 1f));
        m.setFloat("Shininess", 4f);
        return m;
    }
}
