package com.aedfh;

import com.jme3.asset.AssetManager;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
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

import java.util.IdentityHashMap;

/**
 * Procedural articulated silhouettes for the Limgrave encounters.
 *
 * <p>Each boss is a real joint tree (spine / neck / jaw, shoulder / elbow / wrist,
 * hip / knee / ankle, plus horse, dragon, wing and root-branch chains). The build
 * pass records every joint's rest transform; {@link #animate} resets to that rest
 * pose every frame and then layers a per-move module on top with {@link #rot}.
 */
final class BossModel {

    private final WorldBoss boss;
    private final Node root = new Node("bossModel");
    private final Node body = new Node("body");
    private final Node torso = new Node("torso");
    private final Node chest = new Node("chest");
    private final Node neck = new Node("neck");
    private final Node head = new Node("head");
    private final Node jaw = new Node("jaw");
    private final Node armL = new Node("armL");
    private final Node armR = new Node("armR");
    private final Node elbowL = new Node("elbowL");
    private final Node elbowR = new Node("elbowR");
    private final Node handL = new Node("handL");
    private final Node handR = new Node("handR");
    private final Node legL = new Node("legL");
    private final Node legR = new Node("legR");
    private final Node kneeL = new Node("kneeL");
    private final Node kneeR = new Node("kneeR");
    private final Node footL = new Node("footL");
    private final Node footR = new Node("footR");
    private final Node weapon = new Node("weapon");
    private final Node offhand = new Node("offhand");
    private final Node tail = new Node("tail");
    private final Node wingL = new Node("wingL");
    private final Node wingR = new Node("wingR");
    private final Node hoodBone = new Node("hoodBone");

    private final Node[] neckChain = chain("neckSeg", 5);
    private final Node[] tailChain = chain("tailSeg", 6);
    private final Node[] wingArmL = chain("wingArmL", 3);
    private final Node[] wingArmR = chain("wingArmR", 3);
    private final Node[] dragonLegs = chain("dragonLeg", 4);
    private final Node[] dragonKnees = chain("dragonKnee", 4);
    private final Node[] horseLegs = chain("horseLeg", 4);
    private final Node[] horseKnees = chain("horseKnee", 4);
    private final Node[] cape = chain("capeSeg", 3);
    private final Node[] rootArmL = chain("rootArmL", 3);
    private final Node[] rootArmR = chain("rootArmR", 3);
    private final Node[] lobe = chain("lobe", 9);

    private final Node horseRoot = new Node("horseRoot");
    private final Node horseBody = new Node("horseBody");
    private final Node horseNeck = new Node("horseNeck");
    private final Node horseNeck2 = new Node("horseNeck2");
    private final Node horseHead = new Node("horseHead");
    private final Node horseTail = new Node("horseTail");
    private final Node horseTail2 = new Node("horseTail2");
    private final Node treeTrunk = new Node("treeTrunk");
    private final Node treeTop = new Node("treeTop");
    private final Node crown = new Node("crown");

    private final IdentityHashMap<Node, Quaternion> restRot = new IdentityHashMap<>();
    private final IdentityHashMap<Node, Vector3f> restPos = new IdentityHashMap<>();
    private int jointTotal;

    private Geometry hitGlow;
    private Geometry aura;
    private final PointLight glowLight = new PointLight();

    private final AssetManager am;
    private final float scale;
    private final Quaternion q = new Quaternion();
    private final Vector3f tmp = new Vector3f();
    private final Vector3f mouthLocal = new Vector3f(0f, 0.02f, 0.86f);
    private final Vector3f weaponTipLocal = new Vector3f();
    private final Vector3f weaponMidLocal = new Vector3f();
    private final float[] angles = new float[3];
    private boolean hasWeaponSpan;
    float dbgWindK;
    float dbgStrikeK;
    String dbgMove = "-";
    private boolean visible;

    BossModel(AssetManager am, WorldBoss boss, Node parent) {
        this.am = am;
        this.boss = boss;
        this.scale = switch (boss.spec.archetype) {
            case MARGIT -> 1.22f;
            case SENTINEL -> 1.24f;
            case CAVALRY -> 1.16f;
            case HUNTER -> 1.10f;
            case BEAST -> 1.05f;
            case DRAGON -> 1.55f;
            case AVATAR -> 1.35f;
            case DEATHBIRD -> 1.30f;
            case MARINER -> 1.0f;
            case GODRICK -> 1.42f;
        };
        parent.attachChild(root);
        root.attachChild(body);
        build();

        hitGlow = Gfx.fxSphere("bossHitGlow", new ColorRGBA(1f, 0.9f, 0.6f, 0f), am, root);
        hitGlow.setLocalTranslation(0f, boss.displayHeight() * 0.5f, 0f);
        hitGlow.setLocalScale(boss.radius() * 1.4f, boss.displayHeight() * 0.7f,
                boss.radius() * 1.4f);

        if (boss.spec.archetype == WorldBoss.Archetype.MARGIT
                || boss.spec.archetype == WorldBoss.Archetype.AVATAR
                || boss.spec.archetype == WorldBoss.Archetype.GODRICK) {
            glowLight.setColor(new ColorRGBA(1f, 0.62f, 0.2f, 1f).multLocal(1.4f));
            glowLight.setRadius(14f);
            glowLight.setPosition(new Vector3f(0f, 2.6f, 0f));
            root.addLight(glowLight);
        }

        root.setLocalScale(scale);
        root.setShadowMode(RenderQueue.ShadowMode.Cast);
        root.setCullHint(Spatial.CullHint.Always);
    }

    boolean visible() {
        return visible;
    }

    void setVisible(boolean value) {
        visible = value;
        root.setCullHint(value ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        if (value) {
            root.setLocalTranslation(boss.pos);
        }
    }

    void resetPose() {
        resetJoints();
        root.setLocalScale(scale);
        root.setLocalRotation(new Quaternion().fromAngleAxis(boss.yaw, Vector3f.UNIT_Y));
        if (aura != null) {
            aura.setCullHint(Spatial.CullHint.Always);
        }
    }

    /**
     * 嘴部世界坐标：顺着骨骼链把局部坐标叠上去。
     * 龙的脖子有五节，用 displayHeight 估嘴高会差好几米，喷火特效会飘在半空。
     */
    Vector3f mouthWorld(Vector3f store) {
        return jointWorld(jaw, mouthLocal, store);
    }

    /**
     * 骨骼链局部点转世界坐标：贴着 weapon 节点记下刀尖/刀身中点，
     * 挥击时就能沿真实弧线撒拖尾，而不是按屏幕方向瞎猜。
     */
    private Vector3f jointWorld(Node node, Vector3f local, Vector3f store) {
        store.set(local);
        for (Node n = node; n != null && n != root; n = n.getParent()) {
            n.getLocalRotation().mult(store, store);
            store.addLocal(n.getLocalTranslation());
        }
        store.multLocal(root.getLocalScale());
        root.getLocalRotation().mult(store, store);
        return store.addLocal(root.getLocalTranslation());
    }

    /** 武器两端在 weapon 局部空间里的位置（建造时登记，攻击时拿来撒拖尾/做判定）。 */
    private void weaponSpan(float tipX, float tipY, float midX, float midY) {
        weaponTipLocal.set(tipX, tipY, 0f);
        weaponMidLocal.set(midX, midY, 0f);
        hasWeaponSpan = true;
    }

    boolean hasWeaponSpan() {
        return hasWeaponSpan;
    }

    Vector3f weaponTipWorld(Vector3f store) {
        return jointWorld(weapon, weaponTipLocal, store);
    }

    Vector3f weaponMidWorld(Vector3f store) {
        return jointWorld(weapon, weaponMidLocal, store);
    }

    Vector3f weaponPivotWorld(Vector3f store) {
        return jointWorld(weapon, Vector3f.ZERO, store);
    }

    void enrage() {
        if (aura == null) {
            aura = Gfx.attach(new Geometry("bossAura", Gfx.torusMesh(1.6f, 0.045f, 64, 8)),
                    root, 0f, 0.08f, 0f);
            Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setColor("Color", new ColorRGBA(1f, 0.55f, 0.12f, 0.7f));
            m.getAdditionalRenderState().setBlendMode(
                    com.jme3.material.RenderState.BlendMode.AlphaAdditive);
            m.getAdditionalRenderState().setDepthWrite(false);
            m.getAdditionalRenderState().setFaceCullMode(com.jme3.material.RenderState.FaceCullMode.Off);
            aura.setMaterial(m);
            aura.setQueueBucket(RenderQueue.Bucket.Transparent);
            aura.setShadowMode(RenderQueue.ShadowMode.Off);
        }
        aura.setCullHint(Spatial.CullHint.Never);
        aura.setLocalScale(boss.radius() * 0.9f, 1f, boss.radius() * 0.9f);
    }

    // ---------------------------------------------------------------- rig

    private static Node[] chain(String prefix, int count) {
        Node[] out = new Node[count];
        for (int i = 0; i < count; i++) {
            out[i] = new Node(prefix + i);
        }
        return out;
    }

    private static Node joint(Node parent, Node child, float x, float y, float z) {
        child.setLocalTranslation(x, y, z);
        parent.attachChild(child);
        return child;
    }

    private void bindJoints(Node... nodes) {
        for (Node n : nodes) {
            if (n == null || n.getParent() == null || restRot.containsKey(n)) {
                continue;
            }
            restRot.put(n, n.getLocalRotation().clone());
            restPos.put(n, n.getLocalTranslation().clone());
            jointTotal++;
        }
    }

    private void bindTree() {
        bindJoints(body, torso, chest, neck, head, jaw, armL, armR, elbowL, elbowR,
                handL, handR, legL, legR, kneeL, kneeR, footL, footR, weapon, offhand,
                tail, wingL, wingR, hoodBone);
        bindJoints(neckChain);
        bindJoints(tailChain);
        bindJoints(wingArmL);
        bindJoints(wingArmR);
        bindJoints(dragonLegs);
        bindJoints(dragonKnees);
        bindJoints(horseLegs);
        bindJoints(horseKnees);
        bindJoints(cape);
        bindJoints(rootArmL);
        bindJoints(rootArmR);
        bindJoints(lobe);
        bindJoints(horseRoot, horseBody, horseNeck, horseNeck2, horseHead, horseTail,
                horseTail2, treeTrunk, treeTop, crown);
    }

    private void resetJoints() {
        for (Node n : restRot.keySet()) {
            n.setLocalRotation(restRot.get(n));
            n.setLocalTranslation(restPos.get(n));
        }
    }

    private void rot(Node n, float x, float y, float z) {
        Quaternion rest = restRot.get(n);
        q.fromAngles(x, y, z);
        n.setLocalRotation(rest == null ? q.clone() : rest.mult(q));
    }

    private void shift(Node n, float x, float y, float z) {
        Vector3f p = restPos.get(n);
        n.setLocalTranslation((p == null ? 0f : p.x) + x, (p == null ? 0f : p.y) + y,
                (p == null ? 0f : p.z) + z);
    }

    /** 关节总数（自测断言用）。 */
    int jointCount() {
        return jointTotal;
    }

    /** 当前姿态的标量指纹：所有关节的欧拉角绝对值 + 位移量（自测断言用）。 */
    float poseSignature() {
        float s = 0f;
        for (Node n : restRot.keySet()) {
            n.getLocalRotation().toAngles(angles);
            s += Math.abs(angles[0]) + Math.abs(angles[1]) + Math.abs(angles[2]);
            Vector3f p = restPos.get(n);
            if (p != null) {
                s += 2f * n.getLocalTranslation().distance(p);
            }
        }
        return s;
    }

    private Material glowMat(ColorRGBA c) {
        Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", c);
        return m;
    }

    private Geometry blob(String name, float sx, float sy, float sz, Material mat,
                          Node parent, float x, float y, float z) {
        Geometry g = Gfx.box(name, new Sphere(10, 12, 1f), mat, parent, x, y, z);
        g.setLocalScale(sx, sy, sz);
        return g;
    }

    private Geometry spike(String name, float radius, float height, Material mat,
                           Node parent, float x, float y, float z, float rx, float rz) {
        Geometry g = Gfx.taperedCylinder(name, 0.006f, radius, height, mat, parent, x, y, z);
        g.rotate(rx, 0f, rz);
        return g;
    }

    /** 竖直锥台：四肢和躯干用它替掉方盒，侧光下才有连续的明暗过渡。 */
    private Geometry tube(String name, float rTop, float rBottom, float height, Material mat,
                          Node parent, float x, float y, float z) {
        return Gfx.taperedCylinder(name, rTop, rBottom, height, mat, parent, x, y, z);
    }

    /**
     * 一束压扁的尖叶，轴向指到 (dx,dy,dz)。
     * 直接用角度拼 rx/rz 时，Euler 顺序一变朝向就偏；这里按世界方向显式求旋转。
     * 叶簇必须是尖的：圆球叠圆球在直射光下只会读成一坨花椰菜。
     */
    private Geometry blade(String name, float radius, float length, Material mat, Node parent,
                           float x, float y, float z, float dx, float dy, float dz) {
        Geometry g = rod(name, 0.015f, radius, length, mat, parent, x, y, z, dx, dy, dz);
        g.setLocalScale(0.45f, 1f, 1f);
        return g;
    }

    /**
     * 任意方向的锥台：rTip 是 +Y 端（也就是 (dx,dy,dz) 指向的那一端）。
     * Gfx.taperedCylinder 自带 -90°X 的姿态，再叠 rotate(rx,ry,rz) 时
     * Euler 的轴向很容易差 90°，板根会横着穿过躯干、鹿角会朝内合拢。
     */
    private Geometry rod(String name, float rTip, float rBase, float length, Material mat,
                         Node parent, float x, float y, float z,
                         float dx, float dy, float dz) {
        Geometry g = Gfx.taperedCylinder(name, rTip, rBase, length, mat, parent, x, y, z);
        Vector3f d = new Vector3f(dx, dy, dz).normalizeLocal();
        Vector3f axis = new Vector3f(Vector3f.UNIT_Y).crossLocal(d);
        float s = axis.length();
        if (s > 1e-4f) {
            axis.multLocal(1f / s);
            float ang = FastMath.acos(FastMath.clamp(d.y, -1f, 1f));
            g.setLocalRotation(new Quaternion().fromAngleAxis(ang, axis)
                    .mult(g.getLocalRotation()));
        }
        return g;
    }

    /**
     * 一片垂布：上段贴着骨、下段垂出去，末端压一条包边。
     * 单块方盒在侧光下就是一块板，两段+包边才读得出布料厚度。
     */
    private Node cloth(Node parent, String name, float x, float y, float z,
                       float halfW, float len, Material mat, Material trim,
                       float rx, float rz) {
        Node n = joint(parent, new Node(name), x, y, z);
        n.setLocalRotation(new Quaternion().fromAngles(rx, 0f, rz));
        float upper = len * 0.55f;
        float lower = len * 0.55f;
        float squash = 0.16f;
        Geometry top = Gfx.taperedCylinder(name + "Cloth", halfW * 0.88f, halfW * 1.02f,
                upper, mat, n, 0f, -upper * 0.5f, 0f);
        top.setLocalScale(1f, 1f, squash);
        Node tail = joint(n, new Node(name + "Drape"), 0f, -upper * 0.9f, 0f);
        tail.setLocalRotation(new Quaternion().fromAngles(rx * 0.4f, 0f, 0f));
        Geometry low = Gfx.taperedCylinder(name + "ClothLow", halfW * 1.0f, halfW * 1.18f,
                lower, mat, tail, 0f, -lower * 0.5f, 0f);
        low.setLocalScale(1f, 1f, squash);
        if (trim != null) {
            Gfx.box(name + "Hem", halfW * 1.18f, 0.015f, halfW * squash * 1.3f, trim, tail,
                    0f, -lower + 0.02f, 0f);
        }
        return tail;
    }

    /** 一排铆钉/甲钉：沿 x 对称铺，两侧各 n 颗。 */
    private void rivets(String name, float radius, Material mat, Node parent,
                        float halfSpan, float y, float z, int n) {
        for (int i = 0; i < n; i++) {
            float t = n == 1 ? 0f : (i / (float) (n - 1)) * 2f - 1f;
            Gfx.sphere(name, radius, 6, mat, parent, t * halfSpan, y, z);
        }
    }

    /** 毛撮：从根部向外斜插，主方向由 rx/rz 控制。 */
    private void tufts(String name, int count, float len, float radius, Material mat,
                       Node parent, float x, float y, float z, float rx, float rz,
                       float spread) {
        for (int i = 0; i < count; i++) {
            float k = count == 1 ? 0f : i / (float) (count - 1) - 0.5f;
            spike(name, radius * (1f - Math.abs(k) * 0.35f), len * (1f - Math.abs(k) * 0.28f),
                    mat, parent, x + k * spread, y - Math.abs(k) * 0.02f, z,
                    rx, rz + k * 0.35f);
        }
    }

    /**
     * 一整片翼膜：前缘贴着肱骨/尺骨/掌骨，后缘在翼指之间做出扇贝缺口。
     * 分段贴膜会在肘/腕处错台，所以整片只挂一个节点，飞龙不做肘部折叠。
     */
    private void wingSheet(Node parent, float side, Material mat, Material bone) {
        float[][] lead = {
                {0.05f, 0.08f, 0.12f},
                {0.80f, 0.20f, 0.04f},
                {1.50f, 0.32f, -0.06f},
                {2.20f, 0.42f, -0.16f},
                {2.90f, 0.52f, -0.32f},
                {3.70f, 0.64f, -0.58f}};
        // 后缘：翼指尖是凸出的低点，指间收回去，膜才不是一块梯形板
        float[][] trail = {
                {0.00f, -0.20f, -0.92f},
                {0.78f, -0.28f, -1.48f},
                {1.48f, -0.12f, -1.22f},
                {2.22f, -0.18f, -1.66f},
                {2.96f, -0.02f, -1.32f},
                {3.62f, 0.18f, -1.54f}};
        // 弦向三排共享顶点，中间按 sin 曲线下垂：膜是一张鼓起来的皮，不是折扇面。
        // 每片单独法线的话，一排排面片会各自反光，整片读成折纸。
        // 后两排是撕裂齿：后缘若是一条光滑直线，侧光下就是一块裁好的硬边皮。
        float[] tear = {0.30f, 0.12f, 0.34f, 0.10f, 0.28f, 0.16f};
        int colsN = lead.length;
        int rowsN = 5;
        Vector3f[][] grid = new Vector3f[rowsN][colsN];
        for (int i = 0; i < colsN; i++) {
            Vector3f l = wingPoint(lead[i], side);
            Vector3f t = wingPoint(trail[i], side);
            Vector3f back = t.clone().subtractLocal(l);
            back.y = 0f;
            float len = back.length();
            back.multLocal(1f / Math.max(len, 0.001f));
            float hang = -0.08f - (i % 3) * 0.045f;
            Vector3f[] pts = {
                    l.clone(),
                    l.clone().interpolateLocal(t, 0.5f),
                    t.clone(),
                    t.clone().addLocal(back.mult(tear[i] * 0.55f)).addLocal(0f, hang * 0.5f, 0f),
                    t.clone().addLocal(back.mult(tear[i])).addLocal(0f, hang, 0f)};
            for (int r = 0; r < rowsN; r++) {
                Vector3f p = pts[r];
                float u = Math.min(1f, r / 2f);
                p.y -= FastMath.sin(u * FastMath.PI) * 0.26f;
                grid[r][i] = p;
            }
        }
        int verts = rowsN * colsN;
        float[] pos = new float[verts * 3];
        float[] nrm = new float[verts * 3];
        float[] uv = new float[verts * 2];
        float[] acc = new float[verts * 3];
        for (int r = 0; r < rowsN; r++) {
            for (int c = 0; c < colsN; c++) {
                int v = r * colsN + c;
                pos[v * 3] = grid[r][c].x;
                pos[v * 3 + 1] = grid[r][c].y;
                pos[v * 3 + 2] = grid[r][c].z;
                uv[v * 2] = c / (float) (colsN - 1) * 2.4f;
                uv[v * 2 + 1] = r / (float) (rowsN - 1);
            }
        }
        Vector3f t0 = new Vector3f();
        Vector3f t1 = new Vector3f();
        Vector3f fn = new Vector3f();
        for (int r = 0; r < rowsN - 1; r++) {
            for (int c = 0; c < colsN - 1; c++) {
                Vector3f a = grid[r][c];
                Vector3f b = grid[r][c + 1];
                Vector3f c2 = grid[r + 1][c + 1];
                Vector3f d = grid[r + 1][c];
                fn.set(b).subtractLocal(a).crossLocal(t1.set(c2).subtractLocal(a))
                        .normalizeLocal();
                if (fn.y < 0f) {
                    fn.negateLocal();
                }
                int[] vs = {r * colsN + c, r * colsN + c + 1,
                        (r + 1) * colsN + c + 1, (r + 1) * colsN + c};
                for (int v : vs) {
                    acc[v * 3] += fn.x;
                    acc[v * 3 + 1] += fn.y;
                    acc[v * 3 + 2] += fn.z;
                }
            }
        }
        for (int v = 0; v < verts; v++) {
            t0.set(acc[v * 3], acc[v * 3 + 1], acc[v * 3 + 2]).normalizeLocal();
            if (t0.y <= 0f) {
                t0.y = 0.02f;
                t0.normalizeLocal();
            }
            nrm[v * 3] = t0.x;
            nrm[v * 3 + 1] = t0.y;
            nrm[v * 3 + 2] = t0.z;
        }
        int[] idx = new int[(rowsN - 1) * (colsN - 1) * 6];
        int k = 0;
        for (int r = 0; r < rowsN - 1; r++) {
            for (int c = 0; c < colsN - 1; c++) {
                int v00 = r * colsN + c;
                int v01 = v00 + 1;
                int v10 = v00 + colsN;
                int v11 = v10 + 1;
                idx[k++] = v00;
                idx[k++] = v01;
                idx[k++] = v11;
                idx[k++] = v00;
                idx[k++] = v11;
                idx[k++] = v10;
            }
        }
        Mesh m = new Mesh();
        m.setMode(Mesh.Mode.Triangles);
        m.setBuffer(VertexBuffer.Type.Position, 3, pos);
        m.setBuffer(VertexBuffer.Type.Normal, 3, nrm);
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, uv);
        m.setBuffer(VertexBuffer.Type.Index, 3, idx);
        m.updateBound();
        Geometry g = new Geometry("membrane", m);
        g.setMaterial(mat);
        parent.attachChild(g);
        // 翼指与翼骨压在膜上，膜才不是一张飘着的牛皮纸
        strut("wingRibB", 0.045f, 1.50f, 0.32f, -0.06f, 0.78f, -0.34f, -1.62f,
                side, bone, parent);
        strut("wingRibC", 0.038f, 2.90f, 0.52f, -0.32f, 2.22f, -0.22f, -1.98f,
                side, bone, parent);
        strut("wingFinger", 0.030f, 2.90f, 0.52f, -0.32f, 3.62f, 0.18f, -1.60f,
                side, bone, parent);
    }

    private Vector3f wingPoint(float[] p, float side) {
        return new Vector3f(side * p[0], p[1], p[2]);
    }

    /**
     * 沿 Z 轴放样的闭合椭圆柱。每环 {z, 半宽x, 半高y, 圆心y偏移}。
     * 躯干这种大块体量用一整张连续网格，比一串球体更像生物——
     * 球体无论怎么叠，轮廓上都留着球与球的接缝。
     */
    private Geometry loft(String name, float[][] rings, int radial, Material mat,
                          Node parent, float x, float y, float z) {
        int rows = rings.length;
        int cols = radial + 1;
        float[] pos = new float[rows * cols * 3];
        float[] nrm = new float[rows * cols * 3];
        float[] uv = new float[rows * cols * 2];
        int[] idx = new int[(rows - 1) * radial * 6];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                float a = FastMath.TWO_PI * j / radial;
                int v = i * cols + j;
                pos[v * 3] = FastMath.cos(a) * rings[i][1];
                pos[v * 3 + 1] = rings[i][3] + FastMath.sin(a) * rings[i][2];
                pos[v * 3 + 2] = rings[i][0];
                uv[v * 2] = j / (float) radial;
                uv[v * 2 + 1] = i / (float) (rows - 1);
            }
        }
        Vector3f circ = new Vector3f();
        Vector3f axial = new Vector3f();
        Vector3f n = new Vector3f();
        for (int i = 0; i < rows; i++) {
            int ip = Math.max(0, i - 1);
            int in = Math.min(rows - 1, i + 1);
            for (int j = 0; j < cols; j++) {
                int jp = (j + radial - 1) % radial;
                int jn = (j + 1) % radial;
                circ.set(pos[(i * cols + jn) * 3] - pos[(i * cols + jp) * 3],
                        pos[(i * cols + jn) * 3 + 1] - pos[(i * cols + jp) * 3 + 1],
                        pos[(i * cols + jn) * 3 + 2] - pos[(i * cols + jp) * 3 + 2]);
                axial.set(pos[(in * cols + j) * 3] - pos[(ip * cols + j) * 3],
                        pos[(in * cols + j) * 3 + 1] - pos[(ip * cols + j) * 3 + 1],
                        pos[(in * cols + j) * 3 + 2] - pos[(ip * cols + j) * 3 + 2]);
                n.set(circ).crossLocal(axial).normalizeLocal();
                int v = i * cols + j;
                nrm[v * 3] = n.x;
                nrm[v * 3 + 1] = n.y;
                nrm[v * 3 + 2] = n.z;
            }
        }
        int k = 0;
        for (int i = 0; i < rows - 1; i++) {
            for (int j = 0; j < radial; j++) {
                int v00 = i * cols + j;
                int v01 = i * cols + j + 1;
                int v10 = (i + 1) * cols + j;
                int v11 = (i + 1) * cols + j + 1;
                idx[k++] = v00;
                idx[k++] = v01;
                idx[k++] = v11;
                idx[k++] = v00;
                idx[k++] = v11;
                idx[k++] = v10;
            }
        }
        Mesh m = new Mesh();
        m.setMode(Mesh.Mode.Triangles);
        m.setBuffer(VertexBuffer.Type.Position, 3, pos);
        m.setBuffer(VertexBuffer.Type.Normal, 3, nrm);
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, uv);
        m.setBuffer(VertexBuffer.Type.Index, 3, idx);
        m.updateBound();
        Geometry g = new Geometry(name, m);
        g.setMaterial(mat);
        g.setLocalTranslation(x, y, z);
        parent.attachChild(g);
        return g;
    }

    /** 从 (ax,ay,az) 到 (bx,by,bz) 的骨杆；x 按 side 镜像。 */
    private void strut(String name, float radius, float ax, float ay, float az,
                       float bx, float by, float bz, float side, Material mat,
                       Node parent) {
        Vector3f a = new Vector3f(side * ax, ay, az);
        Vector3f b = new Vector3f(side * bx, by, bz);
        Vector3f d = b.subtract(a);
        float len = d.length();
        Geometry g = Gfx.box(name, new Box(radius, len * 0.5f, radius), mat, parent,
                (a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f, (a.z + b.z) * 0.5f);
        g.setLocalRotation(yTo(d.divide(len)));
    }

    private void build() {
        switch (boss.spec.archetype) {
            case SENTINEL, CAVALRY -> buildMounted();
            case MARGIT -> buildMargit();
            case HUNTER -> buildHunter();
            case BEAST -> buildBeast();
            case DRAGON -> buildDragon();
            case MARINER -> buildMariner();
            case AVATAR -> buildAvatar();
            case DEATHBIRD -> buildDeathbird();
            case GODRICK -> buildGodrick();
        }
        bindTree();
        if (boss.spec.archetype == WorldBoss.Archetype.DRAGON) {
            buildDragonTubes();
            detailDragon();
        }
        if (boss.spec.archetype == WorldBoss.Archetype.AVATAR) {
            detailAvatar();
        }
        Gfx.tangents(root);
    }

    /**
     * 颈/尾的锥台骨筒：两端半径与关节球对齐，弯折时接缝被球盖住。
     * 只在 bindTree() 之后建——锥台要按关节的 rest 变换把父节点方向反算到子节点局部。
     */
    private void buildDragonTubes() {
        Material skin = Gfx.lit(am, new ColorRGBA(0.84f, 0.56f, 0.40f, 1f),
                new ColorRGBA(0.28f, 0.18f, 0.12f, 1f), ColorRGBA.Black, 9f);
        Gfx.mapped(skin, am, "brown_leather", 1.0f);
        tubeChain(neckChain, 0.055f, 0.515f, skin);
        tubeChain(tailChain, 0.13f, 0.525f, skin);
    }

    private void tubeChain(Node[] chain, float taper, float radius, Material mat) {
        for (int i = 1; i < chain.length; i++) {
            Node child = chain[i];
            Vector3f off = restPos.get(child);
            Quaternion rot = restRot.get(child);
            if (off == null || rot == null) {
                continue;
            }
            Vector3f dir = rot.inverse().mult(off.negate());
            float len = dir.length();
            // 半径要比关节球略大：球只负责弯折时补缝，一旦宽过骨筒就变成一串念珠
            Cylinder mesh = new Cylinder(6, 14, radius * (1f - i * taper),
                    radius * (1f - (i - 1) * taper), len, true, false);
            Geometry g = new Geometry("boneTube", mesh);
            g.setMaterial(mat);
            g.setLocalTranslation(dir.mult(0.5f));
            // Cylinder 的轴是 +Z，yTo 对齐的是 +Y：先绕 X 转 -90° 把轴立到 Y，再对到骨段方向。
            // 少了这一步骨筒会横躺在骨段上，整条颈/尾变成一串横放的鼓。
            g.setLocalRotation(yTo(dir.negate()).mult(
                    new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X)));
            child.attachChild(g);
        }
    }

    /** 把 Y 轴对齐到 dir；圆柱/方杆都靠它落到骨段方向上。 */
    private static Quaternion yTo(Vector3f dir) {
        Vector3f d = dir.normalize();
        float dot = Vector3f.UNIT_Y.dot(d);
        if (dot > 0.9999f) {
            return new Quaternion();
        }
        if (dot < -0.9999f) {
            return new Quaternion().fromAngleAxis(FastMath.PI, Vector3f.UNIT_X);
        }
        return new Quaternion().fromAngleAxis(FastMath.acos(dot),
                Vector3f.UNIT_Y.cross(d).normalizeLocal());
    }

    // ---------------------------------------------------------------- humanoid

    private void buildHumanoid(float limbScale, ColorRGBA armorColor, ColorRGBA clothColor,
                               boolean horned, boolean plate) {
        Material armor = Gfx.lit(am, armorColor, plate ? armorColor.mult(0.85f)
                        : armorColor.mult(1.1f),
                new ColorRGBA(0.36f, 0.34f, 0.28f, 1f), plate ? 28f : 44f);
        if (plate && boss.spec.archetype != WorldBoss.Archetype.CAVALRY) {
            Gfx.mapped(armor, am, "metal_plate", 0.7f);
        }
        // 暗甲做甲片底衬：整身一个材质再多的分件也读成一整块
        Material armorDark = Gfx.lit(am, armorColor.mult(0.52f), armorColor.mult(0.26f),
                new ColorRGBA(0.28f, 0.27f, 0.23f, 1f), 24f);
        Gfx.mapped(armorDark, am, "metal_plate", 0.7f);
        Material trim = Gfx.lit(am, horned ? new ColorRGBA(0.72f, 0.55f, 0.20f, 1f)
                        : new ColorRGBA(0.56f, 0.58f, 0.63f, 1f),
                horned ? new ColorRGBA(0.17f, 0.11f, 0.03f, 1f)
                        : new ColorRGBA(0.11f, 0.12f, 0.15f, 1f),
                new ColorRGBA(0.95f, 0.88f, 0.62f, 1f), 72f);
        Gfx.mapped(trim, am, "metal_plate", 0.6f);
        Material cloth = Gfx.lit(am, clothColor, clothColor.mult(0.75f),
                ColorRGBA.Black, 2f);
        Gfx.mapped(cloth, am, "rough_linen", 1.0f);
        Material clothDark = Gfx.lit(am, clothColor.mult(0.55f), clothColor.mult(0.34f),
                ColorRGBA.Black, 2f);
        Gfx.mapped(clothDark, am, "rough_linen", 1.0f);
        Material leather = Gfx.lit(am, new ColorRGBA(0.24f, 0.16f, 0.095f, 1f),
                new ColorRGBA(0.07f, 0.045f, 0.028f, 1f), ColorRGBA.Black, 6f);
        Gfx.mapped(leather, am, "brown_leather", 0.7f);
        Material skin = Gfx.lit(am, new ColorRGBA(0.54f, 0.43f, 0.34f, 1f),
                new ColorRGBA(0.14f, 0.1f, 0.08f, 1f), ColorRGBA.Black, 2f);

        joint(root, body, 0f, 1.02f * limbScale, 0f);
        joint(body, torso, 0f, 0.10f, 0f);
        joint(torso, chest, 0f, 0.30f, 0f);
        joint(chest, neck, 0f, 0.56f, 0.02f);
        joint(neck, head, 0f, 0.17f, 0.01f);
        joint(head, jaw, 0f, -0.08f, 0.06f);

        // 腰胯：布裙拆成四片垂下来，前片压后片，动起来才有层次
        blob("hip", 0.21f, 0.15f, 0.16f, clothDark, body, 0f, -0.04f, 0f);
        for (int side = -1; side <= 1; side += 2) {
            Geometry tasset = Gfx.box("tasset", 0.085f, 0.15f, 0.022f, armor,
                    body, side * 0.155f, -0.13f, 0.145f);
            tasset.rotate(-0.10f, side * -0.12f, side * 0.10f);
            Gfx.box("tassetTrim", 0.092f, 0.016f, 0.026f, trim, body,
                    side * 0.155f, -0.272f, 0.155f);
            Gfx.sphere("tassetStud", 0.014f, 6, trim, body,
                    side * 0.155f, -0.085f, 0.17f);
        }
        cloth(body, "skirtF", 0f, 0.02f, 0.155f, 0.185f, 0.48f, cloth, trim, -0.10f, 0f);
        cloth(body, "skirtB", 0f, 0.02f, -0.155f, 0.195f, 0.52f, clothDark, trim, 0.12f, 0f);
        cloth(body, "skirtL", -0.195f, 0.02f, 0f, 0.095f, 0.44f, clothDark, null, 0.03f, 0.07f);
        cloth(body, "skirtR", 0.195f, 0.02f, 0f, 0.095f, 0.44f, clothDark, null, 0.03f, -0.07f);
        Gfx.torus("belt", 0.208f, 0.028f, leather, torso, 0f, 0.06f, 0.01f);
        Gfx.torus("beltTrim", 0.211f, 0.009f, trim, torso, 0f, 0.088f, 0.01f);
        Gfx.box("buckle", 0.055f, 0.05f, 0.022f, trim, torso, 0f, 0.06f, 0.195f);
        Gfx.sphere("buckleGem", 0.021f, 8, glowMat(new ColorRGBA(1f, 0.72f, 0.28f, 1f)),
                torso, 0f, 0.06f, 0.218f).setLocalScale(1f, 1.2f, 0.55f);
        Gfx.sphere("pouch", 0.06f, 8, leather, torso, 0.20f, -0.02f, -0.02f)
                .setLocalScale(0.8f, 1.15f, 0.7f);

        // 胸甲：底衬 + 主甲 + 上下包边 + 六颗铆钉 + 中央纹章
        Gfx.box("chestUnder", 0.245f, 0.31f, 0.15f, clothDark, chest, 0f, 0.02f, 0.02f);
        Geometry chestPlate = Gfx.box("chestPlate", 0.252f, 0.235f, 0.175f, armor,
                chest, 0f, 0.10f, 0.01f);
        chestPlate.rotate(-0.03f, 0f, 0f);
        Gfx.box("chestTrimTop", 0.258f, 0.022f, 0.181f, trim, chest, 0f, 0.322f, 0.01f);
        Gfx.box("chestTrimLow", 0.250f, 0.018f, 0.176f, trim, chest, 0f, -0.112f, 0.01f);
        Gfx.box("sternum", 0.030f, 0.235f, 0.012f, trim, chest, 0f, 0.10f, 0.182f);
        for (int i = 0; i < 3; i++) {
            for (int side = -1; side <= 1; side += 2) {
                Gfx.sphere("rivet", 0.013f, 6, trim, chest,
                        side * 0.19f, 0.24f - i * 0.135f, 0.172f);
            }
        }
        Gfx.sphere("emblem", 0.05f, 10, trim, chest, 0f, 0.20f, 0.186f);
        Gfx.sphere("emblemCore", 0.022f, 8,
                glowMat(new ColorRGBA(1f, 0.74f, 0.3f, 1f)), chest, 0f, 0.20f, 0.21f)
                .setLocalScale(1f, 1.25f, 0.5f);
        // 腹部两片叠甲，比一整块方盒多一道横向断点
        Gfx.box("abdomen", 0.208f, 0.075f, 0.16f, armorDark, chest, 0f, -0.15f, 0.012f);
        Gfx.box("abdomenLow", 0.196f, 0.055f, 0.152f, armorDark, chest, 0f, -0.255f, 0.014f);
        Gfx.box("backPlate", 0.232f, 0.225f, 0.045f, armorDark, chest, 0f, 0.10f, -0.185f);
        Gfx.box("spineRidge", 0.030f, 0.30f, 0.055f, trim, chest, 0f, 0.10f, -0.215f);
        Gfx.torus("gorget", 0.132f, 0.030f, trim, chest, 0f, 0.335f, 0.01f);
        for (int side = -1; side <= 1; side += 2) {
            Geometry col = Gfx.box("gorgetPlate", 0.07f, 0.06f, 0.05f, armor,
                    chest, side * 0.115f, 0.335f, 0.10f);
            col.rotate(0f, side * 0.35f, 0f);
            // 背带收窄到 4 厘米并压一枚铆钉：两根 6 厘米的深色宽带会读成背心吊带
            Gfx.box("shoulderStrap", 0.022f, 0.155f, 0.020f, leather, chest,
                    side * 0.15f, 0.23f, 0.17f).rotate(0.1f, 0f, -side * 0.2f);
            Gfx.sphere("strapStud", 0.015f, 6, trim, chest,
                    side * 0.155f, 0.145f, 0.188f);
        }

        // 头：盔壳 + 面甲 + 呼吸孔 + 颊甲；只有一颗球的脸在近景撑不住
        blob("skull", 0.152f, 0.165f, 0.150f, skin, head, 0f, 0f, 0f);
        Gfx.box("lowerJaw", 0.098f, 0.045f, 0.09f, skin, jaw, 0f, 0f, 0.03f);
        Geometry helm = Gfx.sphere("helm", 0.175f, 12, armor, head, 0f, 0.045f, -0.01f);
        helm.setLocalScale(1f, 0.92f, 1.02f);
        Gfx.torus("helmRim", 0.166f, 0.016f, trim, head, 0f, -0.018f, -0.01f);
        Gfx.box("facePlate", 0.145f, 0.115f, 0.030f, armor, head, 0f, 0.010f, 0.145f);
        Gfx.box("faceTrim", 0.150f, 0.014f, 0.034f, trim, head, 0f, 0.115f, 0.146f);
        for (int side = -1; side <= 1; side += 2) {
            Gfx.box("breather", 0.012f, 0.030f, 0.012f, trim, head,
                    side * 0.030f, -0.060f, 0.174f);
            Gfx.sphere("helmStud", 0.014f, 6, trim, head, side * 0.125f, 0.090f, 0.100f);
            Geometry cheek = Gfx.box("cheekGuard", 0.032f, 0.085f, 0.075f, armorDark,
                    head, side * 0.145f, -0.050f, 0.050f);
            cheek.rotate(0f, side * 0.20f, 0f);
        }
        Gfx.box("visorSlit", 0.105f, 0.013f, 0.012f,
                glowMat(horned ? new ColorRGBA(1f, 0.72f, 0.3f, 1f)
                        : new ColorRGBA(1f, 0.5f, 0.16f, 1f)), head, 0f, 0.035f, 0.178f);
        Gfx.box("crestRidge", 0.022f, 0.10f, 0.20f, trim, head, 0f, 0.200f, -0.010f);
        for (int i = 0; i < 3; i++) {
            spike("plume", 0.030f, 0.34f - i * 0.05f, cloth, head,
                    0f, 0.22f, -0.10f - i * 0.03f, -0.9f - i * 0.15f, (i - 1) * 0.35f);
        }
        if (horned) {
            Material hornMat = Gfx.lit(am, new ColorRGBA(0.78f, 0.62f, 0.28f, 1f),
                    new ColorRGBA(0.22f, 0.16f, 0.06f, 1f),
                    new ColorRGBA(0.8f, 0.7f, 0.4f, 1f), 48f);
            for (int side = -1; side <= 1; side += 2) {
                Gfx.taperedCylinder("hornBase", 0.042f, 0.058f, 0.30f, hornMat,
                        head, side * 0.13f, 0.16f, 0f).rotate(0f, 0f, -side * 0.55f);
                spike("hornTip", 0.042f, 0.34f, hornMat, head,
                        side * 0.20f, 0.40f, 0f, 0f, -side * 0.95f);
                Gfx.sphere("hornBoss", 0.055f, 8, trim, head, side * 0.125f, 0.16f, 0.02f);
            }
        }

        joint(chest, armL, -0.32f, 0.26f, 0f);
        joint(chest, armR, 0.32f, 0.26f, 0f);
        joint(armL, elbowL, 0f, -0.40f, 0f);
        joint(armR, elbowR, 0f, -0.40f, 0f);
        joint(elbowL, handL, 0f, -0.38f, 0.02f);
        joint(elbowR, handR, 0f, -0.38f, 0.02f);
        for (Node arm : new Node[]{armL, armR}) {
            int side = arm == armL ? -1 : 1;
            // 肩甲三片叠压：主盖 + 两片垂叶 + 铆钉；一整块球壳在侧光下没有厚度
            Geometry cap = Gfx.sphere("pauldron", 0.160f, 10, armor, arm,
                    side * 0.01f, 0.05f, 0f);
            cap.setLocalScale(1.08f, 0.80f, 1.14f);
            Gfx.torus("pauldronRim", 0.145f, 0.014f, trim, arm, side * 0.01f, -0.02f, 0f);
            for (int i = 0; i < 2; i++) {
                Geometry lame = Gfx.box("lame", 0.075f, 0.028f, 0.115f, armorDark, arm,
                        side * (0.055f + i * 0.045f), -0.045f - i * 0.05f, 0f);
                lame.rotate(0f, 0f, side * (0.28f + i * 0.22f));
                Gfx.sphere("lameStud", 0.011f, 6, trim, arm,
                        side * (0.10f + i * 0.05f), -0.055f - i * 0.05f, 0.080f);
            }
            Gfx.sphere("shoulderStud", 0.022f, 6, trim, arm, side * 0.06f, 0.11f, 0.06f);
            tube("upper", 0.078f, 0.092f, 0.40f, cloth, arm, 0f, -0.21f, 0f);
            Gfx.torus("armBand", 0.086f, 0.014f, leather, arm, 0f, -0.33f, 0f);
        }
        for (Node elbow : new Node[]{elbowL, elbowR}) {
            Gfx.sphere("elbowCap", 0.082f, 8, armor, elbow, 0f, -0.01f, 0f)
                    .setLocalScale(1f, 1.05f, 1f);
            Gfx.sphere("elbowStud", 0.016f, 6, trim, elbow, 0f, -0.01f, 0.075f);
            tube("fore", 0.062f, 0.078f, 0.36f, cloth, elbow, 0f, -0.19f, 0.005f);
            tube("bracer", 0.072f, 0.082f, 0.20f, armor, elbow, 0f, -0.26f, 0.010f);
            Gfx.torus("bracerTrim", 0.080f, 0.012f, trim, elbow, 0f, -0.36f, 0.010f);
        }
        for (Node hand : new Node[]{handL, handR}) {
            Gfx.box("palm", 0.070f, 0.070f, 0.045f, armorDark, hand, 0f, -0.02f, 0.01f);
            for (int f = -1; f <= 1; f++) {
                Gfx.box("finger", 0.020f, 0.045f, 0.022f, armor, hand,
                        f * 0.042f, -0.085f, 0.035f).rotate(0.25f, 0f, 0f);
            }
            Gfx.sphere("thumb", 0.025f, 6, armor, hand, -0.062f, -0.035f, 0.040f);
        }

        joint(body, legL, -0.145f, 0f, 0f);
        joint(body, legR, 0.145f, 0f, 0f);
        joint(legL, kneeL, 0f, -0.50f, 0f);
        joint(legR, kneeR, 0f, -0.50f, 0f);
        joint(kneeL, footL, 0f, -0.52f, 0.02f);
        joint(kneeR, footR, 0f, -0.52f, 0.02f);
        for (Node leg : new Node[]{legL, legR}) {
            int side = leg == legL ? -1 : 1;
            blob("hipJoint", 0.115f, 0.125f, 0.115f, armorDark, leg, 0f, -0.01f, 0f);
            tube("thigh", 0.098f, 0.118f, 0.46f, cloth, leg, 0f, -0.24f, 0f);
            Gfx.torus("thighStrap", 0.106f, 0.013f, leather, leg, 0f, -0.16f, 0f);
            Gfx.box("thighPlate", 0.055f, 0.11f, 0.028f, armor, leg,
                    side * 0.090f, -0.20f, 0.050f).rotate(0f, side * 0.30f, 0f);
            Gfx.sphere("kneePlate", 0.098f, 8, armor, leg, 0f, -0.47f, 0.015f)
                    .setLocalScale(1f, 0.85f, 0.95f);
            Gfx.torus("kneeRim", 0.092f, 0.012f, trim, leg, 0f, -0.47f, 0.015f);
            spike("kneeFin", 0.022f, 0.10f, trim, leg, 0f, -0.47f, 0.110f, 1.2f, 0f);
        }
        for (Node knee : new Node[]{kneeL, kneeR}) {
            tube("shin", 0.078f, 0.092f, 0.48f, armor, knee, 0f, -0.25f, 0.010f);
            Gfx.box("greave", 0.062f, 0.19f, 0.026f, armorDark, knee, 0f, -0.24f, 0.095f);
            Gfx.torus("ankleRing", 0.084f, 0.012f, trim, knee, 0f, -0.46f, 0.010f);
        }
        for (Node foot : new Node[]{footL, footR}) {
            Gfx.box("heel", 0.088f, 0.055f, 0.110f, armorDark, foot, 0f, 0.005f, 0.020f);
            Gfx.box("sabaton", 0.092f, 0.045f, 0.160f, armor, foot, 0f, -0.010f, 0.130f);
            Gfx.box("toeCap", 0.078f, 0.035f, 0.070f, trim, foot, 0f, -0.030f, 0.245f);
            Gfx.box("sole", 0.094f, 0.014f, 0.200f, leather, foot, 0f, -0.045f, 0.110f);
        }

        joint(chest, cape[0], 0f, 0.16f, -0.21f);
        joint(cape[0], cape[1], 0f, -0.34f, -0.01f);
        joint(cape[1], cape[2], 0f, -0.34f, -0.02f);
        // 披风三段各自压扁成布面，最下面撕成三缕；方盒在背面会读成一块门板
        Gfx.taperedCylinder("capeA", 0.30f, 0.34f, 0.38f, cloth, cape[0], 0f, 0.04f, 0f)
                .setLocalScale(1f, 1f, 0.09f);
        Gfx.taperedCylinder("capeB", 0.34f, 0.38f, 0.38f, cloth, cape[1], 0f, 0.04f, 0f)
                .setLocalScale(1f, 1f, 0.085f);
        Gfx.taperedCylinder("capeC", 0.38f, 0.41f, 0.36f, clothDark, cape[2], 0f, 0.02f, 0f)
                .setLocalScale(1f, 1f, 0.08f);
        Gfx.box("capeHem", 0.40f, 0.012f, 0.014f, trim, cape[2], 0f, -0.155f, 0f);
        for (int i = -1; i <= 1; i++) {
            spike("capeRag", 0.045f, 0.28f - Math.abs(i) * 0.05f, clothDark, cape[2],
                    i * 0.17f, -0.15f, 0f, 0.10f, i * 0.12f);
        }

        joint(handR, weapon, 0f, -0.02f, 0.07f);
    }

    // ---------------------------------------------------------------- mounted

    private void buildMounted() {
        boolean gold = boss.spec.archetype == WorldBoss.Archetype.SENTINEL;
        buildHumanoid(1.16f,
                gold ? new ColorRGBA(0.58f, 0.34f, 0.06f, 1f)
                        : new ColorRGBA(0.20f, 0.21f, 0.29f, 1f),
                gold ? new ColorRGBA(0.16f, 0.23f, 0.12f, 1f)
                        : new ColorRGBA(0.10f, 0.10f, 0.16f, 1f),
                gold, !gold);

        Material goldSteel = Gfx.lit(am, new ColorRGBA(0.50f, 0.36f, 0.12f, 1f),
                new ColorRGBA(0.10f, 0.07f, 0.03f, 1f),
                new ColorRGBA(1f, 0.9f, 0.55f, 1f), 64f);
        Gfx.mapped(goldSteel, am, "metal_plate", 0.7f);
        Material darkSteel = Gfx.lit(am, new ColorRGBA(0.30f, 0.31f, 0.38f, 1f),
                new ColorRGBA(0.07f, 0.07f, 0.10f, 1f),
                new ColorRGBA(0.75f, 0.78f, 0.9f, 1f), 64f);
        Gfx.mapped(darkSteel, am, "metal_plate", 0.7f);

        // Rider sits the saddle: re-parent the humanoid onto the horse barrel.
        joint(root, horseRoot, 0f, 0f, 0f);
        joint(horseRoot, horseBody, 0f, 1.30f, 0.02f);
        horseBody.attachChild(body);
        body.setLocalTranslation(0f, 0.62f, -0.10f);
        body.setLocalScale(1.12f);

        legL.setLocalRotation(new Quaternion().fromAngles(-0.58f, 0f, -0.42f));
        legR.setLocalRotation(new Quaternion().fromAngles(-0.58f, 0f, 0.42f));
        kneeL.setLocalRotation(new Quaternion().fromAngleAxis(1.25f, Vector3f.UNIT_X));
        kneeR.setLocalRotation(new Quaternion().fromAngleAxis(1.25f, Vector3f.UNIT_X));
        footL.setLocalRotation(new Quaternion().fromAngleAxis(-0.6f, Vector3f.UNIT_X));
        footR.setLocalRotation(new Quaternion().fromAngleAxis(-0.6f, Vector3f.UNIT_X));
        armL.setLocalRotation(new Quaternion().fromAngleAxis(-0.55f, Vector3f.UNIT_X));
        elbowL.setLocalRotation(new Quaternion().fromAngleAxis(-0.55f, Vector3f.UNIT_X));

        // 马皮不能是深棕再乘深棕贴图：反光只剩轮廓，整匹马会读成一条黑香肠
        // 马皮压到栗棕色：0.26 的漫反射配宽高光，正对太阳那半身会直接过曝成奶油色
        Material hide = Gfx.lit(am, gold ? new ColorRGBA(0.185f, 0.098f, 0.052f, 1f)
                        : new ColorRGBA(0.22f, 0.235f, 0.31f, 1f),
                gold ? new ColorRGBA(0.24f, 0.15f, 0.095f, 1f)
                        : new ColorRGBA(0.15f, 0.16f, 0.21f, 1f),
                new ColorRGBA(0.13f, 0.085f, 0.055f, 1f), 26f);
        // 没有马皮毛贴图：上贴图只会把整匹马压成深棕，靠形体和高光读质感
        Gfx.mapped(hide, am, "brown_leather", 0.8f);
        hide.clearParam("DiffuseMap");
        Material caparison = Gfx.lit(am, gold ? new ColorRGBA(0.16f, 0.22f, 0.09f, 1f)
                        : new ColorRGBA(0.13f, 0.14f, 0.21f, 1f),
                gold ? new ColorRGBA(0.05f, 0.07f, 0.03f, 1f)
                        : new ColorRGBA(0.06f, 0.07f, 0.11f, 1f),
                ColorRGBA.Black, 2f);
        Material maneMat = Gfx.lit(am, new ColorRGBA(0.26f, 0.21f, 0.16f, 1f),
                new ColorRGBA(0.14f, 0.11f, 0.08f, 1f), ColorRGBA.Black, 2f);
        Material horn = Gfx.lit(am, new ColorRGBA(0.24f, 0.22f, 0.20f, 1f),
                new ColorRGBA(0.10f, 0.09f, 0.08f, 1f),
                new ColorRGBA(0.55f, 0.52f, 0.48f, 1f), 30f);
        Material leather = Gfx.lit(am, new ColorRGBA(0.24f, 0.16f, 0.095f, 1f),
                new ColorRGBA(0.07f, 0.045f, 0.028f, 1f), ColorRGBA.Black, 6f);
        Gfx.mapped(leather, am, "brown_leather", 0.7f);
        Material trim = gold ? goldSteel : darkSteel;

        // ———— 马体：三块主形体 + 肩胛/髋部垫块，体侧挂马衣
        blob("barrel", 0.54f, 0.56f, 1.14f, hide, horseBody, 0f, 0.02f, 0.02f);
        blob("chest", 0.50f, 0.52f, 0.52f, hide, horseBody, 0f, 0.00f, 0.74f);
        blob("rump", 0.50f, 0.54f, 0.50f, hide, horseBody, 0f, 0.04f, -0.76f);
        blob("bellyLine", 0.46f, 0.30f, 1.00f, hide, horseBody, 0f, -0.32f, 0f);
        // 肩隆与腰：一具直筒躯干在侧光下读不出马的体块
        blob("withers", 0.30f, 0.26f, 0.34f, hide, horseBody, 0f, 0.44f, 0.60f);
        blob("loin", 0.34f, 0.24f, 0.40f, hide, horseBody, 0f, 0.38f, -0.34f);
        for (int side = -1; side <= 1; side += 2) {
            blob("scapula", 0.18f, 0.36f, 0.36f, hide, horseBody,
                    side * 0.34f, -0.04f, 0.64f);
            blob("haunch", 0.22f, 0.38f, 0.40f, hide, horseBody,
                    side * 0.34f, 0.02f, -0.64f);
            // 马衣垂在体侧：压扁的锥台布面比一块方盒多出下摆的弧
            Gfx.taperedCylinder("caparison", 0.30f, 0.34f, 0.64f, caparison,
                    horseBody, side * 0.55f, -0.18f, -0.02f).setLocalScale(0.22f, 1f, 1f);
            Gfx.box("caparisonHem", 0.026f, 0.014f, 0.30f, trim, horseBody,
                    side * 0.60f, -0.48f, -0.02f);
            Gfx.sphere("caparisonStud", 0.020f, 6, trim, horseBody,
                    side * 0.62f, -0.12f, 0.18f);
            Gfx.box("girth", 0.028f, 0.28f, 0.09f, leather, horseBody,
                    side * 0.56f, 0.16f, 0.02f);
        }
        // ———— 鞍具：鞍座 + 前后桥 + 鞍褥 + 马镫
        blob("saddle", 0.40f, 0.10f, 0.44f, leather, horseBody, 0f, 0.52f, -0.02f);
        Gfx.box("saddleSeat", 0.33f, 0.055f, 0.34f, leather, horseBody, 0f, 0.585f, -0.02f);
        Gfx.sphere("pommel", 0.070f, 8, trim, horseBody, 0f, 0.645f, 0.26f);
        Gfx.sphere("cantle", 0.080f, 8, trim, horseBody, 0f, 0.645f, -0.30f);
        Gfx.box("saddleBlanket", 0.46f, 0.020f, 0.52f, caparison, horseBody,
                0f, 0.505f, -0.02f);
        for (int side = -1; side <= 1; side += 2) {
            Gfx.box("saddleFlap", 0.020f, 0.15f, 0.26f, leather, horseBody,
                    side * 0.36f, 0.44f, -0.02f);
            Gfx.box("stirrupLeather", 0.020f, 0.22f, 0.045f, leather, horseBody,
                    side * 0.55f, 0.34f, 0.04f);
            Gfx.box("stirrupFoot", 0.085f, 0.016f, 0.075f, trim, horseBody,
                    side * 0.55f, 0.155f, 0.04f);
        }
        if (gold) {
            for (int side = -1; side <= 1; side += 2) {
                Gfx.box("flanchard", 0.026f, 0.20f, 0.34f, goldSteel, horseBody,
                        side * 0.66f, 0.02f, -0.04f);
                Gfx.box("flanchardTrim", 0.030f, 0.026f, 0.36f, trim, horseBody,
                        side * 0.665f, -0.17f, -0.04f);
            }
            // 胸甲五片叠压，铆钉压在最下一片
            for (int i = 0; i < 5; i++) {
                float t = (i - 2) / 2f;
                Geometry pey = Gfx.box("peytral", 0.155f, 0.16f, 0.030f, goldSteel,
                        horseBody, t * 0.34f, -0.10f, 1.02f - Math.abs(t) * 0.04f);
                pey.rotate(0f, t * 0.30f, 0f);
                Gfx.sphere("peytralStud", 0.018f, 6, trim, horseBody,
                        t * 0.34f, -0.02f, 1.06f);
            }
        }

        joint(horseBody, horseNeck, 0f, 0.36f, 0.86f);
        horseNeck.setLocalRotation(new Quaternion().fromAngleAxis(0.55f, Vector3f.UNIT_X));
        blob("neckBase", 0.26f, 0.26f, 0.30f, hide, horseNeck, 0f, 0.06f, 0.02f);
        tube("neckMesh", 0.21f, 0.34f, 0.68f, hide, horseNeck, 0f, 0.31f, 0f);
        // 鬃毛一撮撮铺过颈脊；一整块方盒在任何光下都读成一块深色板
        Gfx.box("crest", 0.035f, 0.30f, 0.09f, maneMat, horseNeck, 0f, 0.32f, -0.16f);
        for (int i = 0; i < 7; i++) {
            spike("mane", 0.045f + (i % 3) * 0.008f, 0.30f + (i % 2) * 0.06f, maneMat,
                    horseNeck, (i % 2 == 0 ? 0.03f : -0.03f), -0.02f + i * 0.10f,
                    -0.20f, -2.1f, (i % 2 == 0 ? 0.18f : -0.16f));
        }
        joint(horseNeck, horseNeck2, 0f, 0.66f, 0f);
        horseNeck2.setLocalRotation(new Quaternion().fromAngleAxis(0.12f, Vector3f.UNIT_X));
        tube("neckMesh2", 0.15f, 0.19f, 0.50f, hide, horseNeck2, 0f, 0.23f, 0f);
        Gfx.box("crest2", 0.030f, 0.22f, 0.08f, maneMat, horseNeck2, 0f, 0.24f, -0.13f);
        for (int i = 0; i < 5; i++) {
            spike("mane2", 0.040f, 0.24f + (i % 2) * 0.05f, maneMat, horseNeck2,
                    (i % 2 == 0 ? 0.02f : -0.02f), 0.04f + i * 0.09f, -0.16f,
                    -2.1f, (i % 2 == 0 ? 0.16f : -0.14f));
        }

        joint(horseNeck2, horseHead, 0f, 0.50f, 0.02f);
        horseHead.setLocalRotation(new Quaternion().fromAngleAxis(0.55f, Vector3f.UNIT_X));
        // 马头要够大：小脑袋 + 长吻管会读成狼
        blob("skull", 0.225f, 0.235f, 0.30f, hide, horseHead, 0f, 0.02f, 0.12f);
        blob("cheek", 0.185f, 0.20f, 0.23f, hide, horseHead, 0f, -0.07f, 0.22f);
        // 吻部沿 +Z 甩出去，横向压窄
        Gfx.taperedCylinder("muzzle", 0.115f, 0.165f, 0.38f, hide, horseHead,
                0f, -0.05f, 0.44f).rotate(FastMath.HALF_PI, 0f, 0f)
                .setLocalScale(1f, 1f, 0.88f);
        Gfx.sphere("nose", 0.118f, 10, hide, horseHead, 0f, -0.055f, 0.615f)
                .setLocalScale(1.15f, 0.88f, 0.82f);
        Gfx.box("lipLine", 0.095f, 0.008f, 0.05f, maneMat, horseHead, 0f, -0.10f, 0.685f);
        for (int side = -1; side <= 1; side += 2) {
            Gfx.box("nostril", 0.038f, 0.026f, 0.020f, maneMat, horseHead,
                    side * 0.055f, -0.005f, 0.715f);
            Gfx.taperedCylinder("ear", 0.022f, 0.075f, 0.24f, hide, horseHead,
                    side * 0.115f, 0.255f, -0.04f).rotate(-0.25f, 0f, side * 0.22f);
            Gfx.sphere("eyeSocket", 0.055f, 8, maneMat, horseHead,
                    side * 0.165f, 0.055f, 0.30f).setLocalScale(0.6f, 1f, 1f);
            Gfx.sphere("eye", 0.022f, 8, glowMat(new ColorRGBA(1f, 0.55f, 0.18f, 1f)),
                    horseHead, side * 0.180f, 0.055f, 0.315f);
            Gfx.box("bridle", 0.015f, 0.015f, 0.34f, leather, horseHead,
                    side * 0.15f, -0.10f, 0.10f);
            Gfx.torus("bitRing", 0.035f, 0.010f, trim, horseHead,
                    side * 0.13f, -0.11f, 0.60f).rotate(0f, FastMath.HALF_PI, 0f);
        }
        Gfx.box("browBand", 0.155f, 0.018f, 0.030f, leather, horseHead, 0f, 0.130f, 0.24f);
        for (int i = 0; i < 3; i++) {
            spike("forelock", 0.035f, 0.22f, maneMat, horseHead,
                    0f, 0.20f, 0.255f, -1.35f, (i - 1) * 0.30f);
        }
        if (gold) {
            Gfx.box("chanfron", 0.16f, 0.15f, 0.045f, goldSteel, horseHead, 0f, 0.06f, 0.30f);
            Gfx.box("chanfronRidge", 0.030f, 0.17f, 0.020f, trim, horseHead, 0f, 0.10f, 0.335f);
            spike("chanfronSpike", 0.030f, 0.26f, goldSteel, horseHead, 0f, 0.20f, 0.28f,
                    -0.5f, 0f);
            Gfx.box("crinet", 0.030f, 0.16f, 0.14f, goldSteel, horseNeck2, 0f, 0.30f, -0.06f);
        }

        float[] lx = {-0.44f, 0.44f, -0.44f, 0.44f};
        float[] lz = {0.74f, 0.74f, -0.70f, -0.70f};
        for (int i = 0; i < 4; i++) {
            joint(horseBody, horseLegs[i], lx[i], -0.02f, lz[i]);
            joint(horseLegs[i], horseKnees[i], 0f, -0.62f, 0f);
            Gfx.taperedCylinder("upper", 0.125f, 0.175f, 0.62f, hide, horseLegs[i],
                    0f, -0.30f, 0f);
            Gfx.sphere("stifle", 0.135f, 8, hide, horseLegs[i], 0f, -0.58f, 0f);
            Gfx.taperedCylinder("lower", 0.062f, 0.090f, 0.56f, hide, horseKnees[i],
                    0f, -0.30f, 0f);
            Gfx.torus("fetlockBand", 0.078f, 0.013f, maneMat, horseKnees[i], 0f, -0.53f, 0f);
            Gfx.box("hoofCrown", 0.098f, 0.020f, 0.140f, hide, horseKnees[i], 0f, -0.585f, 0.040f);
            Gfx.box("hoof", 0.108f, 0.058f, 0.155f, horn, horseKnees[i], 0f, -0.632f, 0.045f);
            if (i < 2) {
                Gfx.box("kneeGuard", 0.105f, 0.11f, 0.105f, trim, horseLegs[i],
                        0f, -0.56f, 0.05f);
            }
        }

        joint(horseBody, horseTail, 0f, 0.44f, -1.05f);
        horseTail.setLocalRotation(new Quaternion().fromAngleAxis(-0.55f, Vector3f.UNIT_X));
        tube("dock", 0.10f, 0.16f, 0.55f, hide, horseTail, 0f, 0.24f, 0f);
        joint(horseTail, horseTail2, 0f, 0.52f, 0f);
        horseTail2.setLocalRotation(new Quaternion().fromAngleAxis(-0.35f, Vector3f.UNIT_X));
        Gfx.sphere("tailKnot", 0.09f, 8, maneMat, horseTail2, 0f, 0.02f, 0f);
        for (int i = 0; i < 7; i++) {
            float k = (i - 3) / 3f;
            spike("tailHair", 0.035f, 0.55f - Math.abs(k) * 0.10f, maneMat, horseTail2,
                    k * 0.055f, 0.04f, k * 0.04f, -2.7f, k * 0.25f);
        }

        // 骑手在马上是俯视构图，头顶细节最吃镜头：盔缨加长、颈甲垂片
        for (int i = 0; i < 4; i++) {
            spike("helmPlume", 0.035f, 0.32f, gold ? caparison : maneMat, head,
                    0f, 0.30f, -0.10f - i * 0.04f, -0.5f - i * 0.28f, (i - 1.5f) * 0.22f);
        }
        for (int side = -1; side <= 1; side += 2) {
            Geometry aventail = Gfx.box("aventail", 0.045f, 0.10f, 0.030f,
                    gold ? goldSteel : darkSteel, chest, side * 0.12f, 0.26f, 0.13f);
            aventail.rotate(0.15f, side * 0.25f, 0f);
        }

        Material haft = Gfx.lit(am, new ColorRGBA(0.30f, 0.21f, 0.11f, 1f),
                new ColorRGBA(0.08f, 0.055f, 0.025f, 1f), ColorRGBA.Black, 8f);
        Gfx.mapped(haft, am, "wood_planks", 0.9f);
        tube("halberdShaft", 0.038f, 0.050f, 3.10f, haft, weapon, 0f, 0.55f, 0f);
        for (int i = 0; i < 4; i++) {
            Gfx.torus("gripRing", 0.048f, 0.010f, maneMat, weapon, 0f, -0.35f + i * 0.17f, 0f);
        }
        Gfx.box("buttCap", 0.050f, 0.050f, 0.050f, trim, weapon, 0f, -1.02f, 0f);
        if (gold) {
            // 斧刃三片：主体 + 刃口 + 上压条；一整块平板的侧面没有刃线
            Geometry axe = Gfx.box("axe", 0.30f, 0.26f, 0.030f, goldSteel, weapon,
                    0.24f, 1.98f, 0f);
            axe.rotate(0f, 0f, 0.10f);
            Gfx.box("axeEdge", 0.035f, 0.30f, 0.040f, trim, weapon, 0.545f, 1.98f, 0f);
            Gfx.box("axeTrimTop", 0.30f, 0.022f, 0.042f, trim, weapon, 0.24f, 2.225f, 0f);
            Gfx.sphere("axeStud", 0.026f, 6, trim, weapon, 0.16f, 1.98f, 0.035f);
            Gfx.taperedCylinder("spike", 0.004f, 0.05f, 0.62f, goldSteel, weapon, 0f, 2.42f, 0f);
            Gfx.torus("shaftRing", 0.062f, 0.016f, trim, weapon, 0f, 1.62f, 0f);
            Gfx.torus("shaftRing2", 0.056f, 0.014f, trim, weapon, 0f, 1.30f, 0f);
            Gfx.box("langet", 0.026f, 0.26f, 0.050f, goldSteel, weapon, 0f, 1.58f, 0.03f)
                    .rotate(0.06f, 0f, 0f);
            cloth(weapon, "pennant", 0f, 1.94f, -0.03f, 0.13f, 0.52f, caparison, trim,
                    0.35f, 0f);
        } else {
            Gfx.box("blade", 0.27f, 0.08f, 0.030f,
                    Gfx.lit(am, new ColorRGBA(0.55f, 0.57f, 0.61f, 1f),
                            new ColorRGBA(0.1f, 0.1f, 0.12f, 1f),
                            new ColorRGBA(0.8f, 0.8f, 0.9f, 1f), 64f),
                    weapon, 0.20f, 1.34f, 0f);
            Gfx.box("guard", 0.11f, 0.03f, 0.06f, trim, weapon, 0f, 1.22f, 0f);
            Gfx.torus("shaftRing", 0.058f, 0.014f, trim, weapon, 0f, 1.72f, 0f);
        }
        weapon.setLocalRotation(new Quaternion().fromAngleAxis(-0.10f, Vector3f.UNIT_Z));
        weaponSpan(gold ? 0.1f : 0.47f, gold ? 2.42f : 1.42f, 0f, 1.35f);
    }

    // ---------------------------------------------------------------- Margit

    private void buildMargit() {
        Material cloak = Gfx.lit(am, new ColorRGBA(0.055f, 0.052f, 0.048f, 1f),
                new ColorRGBA(0.055f, 0.052f, 0.050f, 1f), ColorRGBA.Black, 3f);
        Gfx.mapped(cloak, am, "rough_linen", 1.0f);
        Material robe = Gfx.lit(am, new ColorRGBA(0.115f, 0.085f, 0.050f, 1f),
                new ColorRGBA(0.12f, 0.095f, 0.062f, 1f), ColorRGBA.Black, 4f);
        Gfx.mapped(robe, am, "rough_linen", 1.0f);
        Material trim = Gfx.lit(am, new ColorRGBA(0.58f, 0.44f, 0.17f, 1f),
                new ColorRGBA(0.11f, 0.075f, 0.02f, 1f),
                new ColorRGBA(0.95f, 0.80f, 0.42f, 1f), 48f);
        Material skin = Gfx.lit(am, new ColorRGBA(0.50f, 0.41f, 0.28f, 1f),
                new ColorRGBA(0.19f, 0.15f, 0.09f, 1f), ColorRGBA.Black, 2f);
        Material hornMat = Gfx.lit(am, new ColorRGBA(0.60f, 0.53f, 0.41f, 1f),
                new ColorRGBA(0.24f, 0.21f, 0.16f, 1f),
                new ColorRGBA(0.52f, 0.45f, 0.32f, 1f), 16f);
        Material hairMat = Gfx.lit(am, new ColorRGBA(0.34f, 0.32f, 0.28f, 1f),
                new ColorRGBA(0.16f, 0.15f, 0.13f, 1f), ColorRGBA.Black, 2f);
        Material strap = Gfx.lit(am, new ColorRGBA(0.15f, 0.105f, 0.065f, 1f),
                new ColorRGBA(0.045f, 0.032f, 0.020f, 1f), ColorRGBA.Black, 5f);
        Gfx.mapped(strap, am, "brown_leather", 0.8f);
        // 脚背不用皮肤色：浅米色方块在草地上会读成一双白球鞋
        Material footMat = Gfx.lit(am, new ColorRGBA(0.24f, 0.21f, 0.18f, 1f),
                new ColorRGBA(0.09f, 0.08f, 0.07f, 1f), ColorRGBA.Black, 3f);

        joint(root, body, 0f, 1.56f, 0f);
        body.setLocalRotation(new Quaternion().fromAngleAxis(0.08f, Vector3f.UNIT_X));
        joint(body, torso, 0f, 0.12f, 0f);
        joint(torso, chest, 0f, 0.34f, 0f);
        joint(chest, neck, 0f, 0.70f, 0.02f);
        joint(neck, head, 0f, 0.20f, 0.02f);
        joint(head, jaw, 0f, -0.12f, 0.06f);
        // 头缩一圈、腿加长：否则 3 米的身高配 0.6 米的头，怎么细化都是 Q 版
        head.setLocalScale(0.90f);

        joint(body, legL, -0.235f, 0f, 0f);
        joint(body, legR, 0.235f, 0f, 0f);
        joint(legL, kneeL, 0f, -0.76f, 0f);
        joint(legR, kneeR, 0f, -0.76f, 0f);
        joint(kneeL, footL, 0f, -0.76f, 0.04f);
        joint(kneeR, footR, 0f, -0.76f, 0.04f);
        for (Node leg : new Node[]{legL, legR}) {
            int side = leg == legL ? -1 : 1;
            blob("hipJoint", 0.150f, 0.160f, 0.150f, robe, leg, 0f, -0.01f, 0f);
            tube("thigh", 0.112f, 0.142f, 0.70f, robe, leg, 0f, -0.36f, 0f);
            Gfx.torus("thighStrap", 0.128f, 0.014f, strap, leg, 0f, -0.24f, 0f);
            Gfx.torus("thighStrap2", 0.120f, 0.012f, strap, leg, 0f, -0.46f, 0f);
            Gfx.box("thighPlate", 0.060f, 0.16f, 0.026f, trim, leg,
                    side * 0.110f, -0.30f, 0.070f).rotate(0f, side * 0.30f, 0f);
            for (int i = 0; i < 3; i++) {
                spike("legFur", 0.040f, 0.24f, cloak, leg,
                        side * (0.115f + i * 0.012f), -0.54f - i * 0.02f, -0.06f,
                        -2.3f, -side * (0.45f + i * 0.08f));
            }
        }
        for (Node knee : new Node[]{kneeL, kneeR}) {
            int side = knee == kneeL ? -1 : 1;
            blob("kneeJoint", 0.115f, 0.115f, 0.115f, skin, knee, 0f, 0f, 0f);
            tube("shin", 0.092f, 0.114f, 0.70f, cloak, knee, 0f, -0.36f, 0.02f);
            Gfx.box("greave", 0.072f, 0.22f, 0.030f, trim, knee, 0f, -0.20f, 0.110f);
            Gfx.torus("shinBand", 0.100f, 0.013f, strap, knee, 0f, -0.50f, 0.02f);
            for (int i = 0; i < 2; i++) {
                spike("shinFur", 0.032f, 0.18f, cloak, knee,
                        side * 0.080f, -0.54f + i * 0.10f, 0.060f,
                        -2.4f, -side * 0.35f);
            }
        }
        for (Node foot : new Node[]{footL, footR}) {
            blob("ankle", 0.105f, 0.075f, 0.110f, footMat, foot, 0f, 0f, 0.02f);
            Gfx.box("foot", 0.112f, 0.050f, 0.200f, footMat, foot, 0f, -0.025f, 0.120f);
            for (int t = -1; t <= 1; t++) {
                Gfx.box("toe", 0.036f, 0.038f, 0.075f, footMat, foot,
                        t * 0.062f, -0.050f, 0.270f);
                Gfx.taperedCylinder("toeClaw", 0.006f, 0.020f, 0.075f, hornMat, foot,
                        t * 0.062f, -0.075f, 0.320f).rotate(1.9f, 0f, 0f);
            }
        }

        blob("pelvis", 0.290f, 0.210f, 0.215f, robe, body, 0f, 0.02f, 0f);
        // 腰带做成贴身的筒，别用两圈圆环：圆环比腰身细一圈就整圈悬空
        Gfx.cylinder("belt", 0.243f, 0.085f, strap, body, 0f, 0.200f, 0.010f)
                .setLocalScale(1f, 1f, 0.80f);
        Gfx.cylinder("beltTrim", 0.246f, 0.020f, trim, body, 0f, 0.233f, 0.010f)
                .setLocalScale(1f, 1f, 0.80f);
        Gfx.box("buckle", 0.070f, 0.065f, 0.026f, trim, body, 0f, 0.200f, 0.198f);
        Gfx.sphere("beltGem", 0.026f, 8, glowMat(new ColorRGBA(1f, 0.72f, 0.25f, 1f)),
                body, 0f, 0.200f, 0.220f).setLocalScale(1f, 1.25f, 0.55f);
        // 长袍拆成内外两层 + 四片下摆；一整块方盒在跑动时就是一块砖
        cloth(body, "robeF", 0f, 0.10f, 0.195f, 0.135f, 1.15f, robe, trim, -0.05f, 0f);
        cloth(body, "robeB", 0f, 0.10f, -0.195f, 0.155f, 1.20f, robe, null, 0.09f, 0f);
        cloth(body, "robeL", -0.230f, 0.10f, 0f, 0.095f, 1.05f, robe, null, 0.03f, 0.07f);
        cloth(body, "robeR", 0.230f, 0.10f, 0f, 0.095f, 1.05f, robe, null, 0.03f, -0.07f);
        cloth(body, "underRobe", 0f, 0.06f, 0.02f, 0.205f, 1.02f, cloak, null, 0f, 0f);
        blob("chestBody", 0.330f, 0.320f, 0.230f, robe, chest, 0f, 0.12f, 0.01f);
        Gfx.box("plate", 0.250f, 0.200f, 0.045f, cloak, chest, 0f, 0.18f, 0.235f);
        Gfx.torus("plateRim", 0.130f, 0.014f, trim, chest, 0f, 0.18f, 0.250f)
                .rotate(FastMath.HALF_PI, 0f, 0f);
        Gfx.box("trimL", 0.028f, 0.280f, 0.018f, trim, chest, -0.190f, 0.14f, 0.245f);
        Gfx.box("trimR", 0.028f, 0.280f, 0.018f, trim, chest, 0.190f, 0.14f, 0.245f);
        // 胸前锁链：七节同朝一面，逐节略微错开
        for (int i = 0; i < 7; i++) {
            float a = -0.62f + i * 0.207f;
            Gfx.torus("chain", 0.036f, 0.010f, trim, chest,
                    FastMath.sin(a) * 0.20f, 0.30f - FastMath.cos(a) * 0.10f, 0.235f)
                    .rotate(FastMath.HALF_PI, 0f, i % 2 == 0 ? 0f : 0.5f);
        }
        Gfx.box("collar", 0.300f, 0.090f, 0.220f, hairMat, chest, 0f, 0.500f, 0f);
        Gfx.box("mantle", 0.420f, 0.080f, 0.270f, robe, chest, 0f, 0.420f, 0.02f);
        for (int i = 0; i < 9; i++) {
            float a = -1.5f + i * 0.375f;
            spike("mantleFur", 0.045f, 0.26f, hairMat, chest,
                    FastMath.sin(a) * 0.27f, 0.44f, FastMath.cos(a) * 0.19f,
                    -2.0f, -FastMath.sin(a) * 0.55f);
        }

        // Shoulder horns: the omen silhouette lives or dies on these.
        for (int side = -1; side <= 1; side += 2) {
            var pauldron = Gfx.box("shoulderHorn", 0.13f, 0.13f, 0.16f, hornMat, chest,
                    side * 0.30f, 0.34f, -0.02f);
            pauldron.rotate(0.15f, 0f, side * 0.2f);
            spike("shoulderSpike", 0.075f, 0.52f, hornMat, chest,
                    side * 0.38f, 0.48f, -0.04f, 0.35f, -side * 0.75f);
        }

        joint(chest, cape[0], 0f, 0.30f, -0.26f);
        joint(cape[0], cape[1], 0f, -0.62f, -0.04f);
        joint(cape[1], cape[2], 0f, -0.60f, -0.02f);
        // 斗篷三段压扁成布面，下摆撕成五缕
        Gfx.taperedCylinder("capeA", 0.42f, 0.46f, 0.66f, cloak, cape[0], 0f, -0.16f, 0f)
                .setLocalScale(1f, 1f, 0.075f);
        Gfx.taperedCylinder("capeB", 0.46f, 0.50f, 0.64f, cloak, cape[1], 0f, -0.06f, 0f)
                .setLocalScale(1f, 1f, 0.070f);
        Gfx.taperedCylinder("capeC", 0.48f, 0.52f, 0.60f, cloak, cape[2], 0f, -0.02f, 0f)
                .setLocalScale(1f, 1f, 0.065f);
        Gfx.box("capeHem", 0.50f, 0.012f, 0.030f, trim, cape[2], 0f, -0.31f, 0f);
        for (int i = 0; i < 5; i++) {
            spike("rag", 0.055f, 0.30f + (i % 3) * 0.10f, cloak, cape[2],
                    (i - 2) * 0.17f, -0.30f, -0.01f, 0.06f, (i - 2) * 0.10f);
        }

        // 头：颅骨 + 眉脊 + 颧骨 + 眼窝/眼珠 + 鼻梁 + 上下獠牙 + 后发
        blob("skull", 0.215f, 0.235f, 0.215f, skin, head, 0f, 0f, 0f);
        Gfx.box("hairBack", 0.235f, 0.280f, 0.125f, hairMat, head, 0f, -0.04f, -0.13f);
        for (int i = 0; i < 8; i++) {
            float k = (i - 3.5f) / 3.5f;
            spike("hair", 0.042f, 0.30f, hairMat, head, k * 0.19f, -0.10f, -0.155f,
                    -2.4f, k * 0.35f);
        }
        Material eyeMat = glowMat(new ColorRGBA(1f, 0.72f, 0.25f, 1f));
        for (int side = -1; side <= 1; side += 2) {
            Geometry brow = Gfx.box("browRidge", 0.100f, 0.030f, 0.055f, skin, head,
                    side * 0.085f, 0.075f, 0.175f);
            brow.rotate(0.10f, 0f, -side * 0.22f);
            blob("cheekBone", 0.055f, 0.045f, 0.050f, skin, head,
                    side * 0.145f, -0.035f, 0.155f);
            Gfx.sphere("socket", 0.050f, 8, cloak, head, side * 0.078f, 0.010f, 0.178f)
                    .setLocalScale(1f, 0.80f, 0.40f);
            Gfx.sphere("eye", 0.023f, 8, eyeMat, head, side * 0.078f, 0.012f, 0.196f);
            Gfx.sphere("pupil", 0.010f, 6, cloak, head, side * 0.078f, 0.012f, 0.212f);
            Gfx.box("nostril", 0.014f, 0.010f, 0.014f, cloak, head,
                    side * 0.026f, -0.062f, 0.242f);
            Gfx.box("fangUpper", 0.014f, 0.050f, 0.014f, hornMat, head,
                    side * 0.048f, -0.088f, 0.215f);
        }
        Gfx.box("noseBridge", 0.028f, 0.075f, 0.055f, skin, head, 0f, -0.020f, 0.222f);
        Gfx.box("mouth", 0.075f, 0.010f, 0.030f, cloak, head, 0f, -0.090f, 0.212f);
        Gfx.box("lowerJaw", 0.105f, 0.048f, 0.090f, skin, jaw, 0f, -0.02f, 0.04f);
        for (int side = -1; side <= 1; side += 2) {
            Gfx.box("fangLow", 0.012f, 0.045f, 0.012f, hornMat, jaw,
                    side * 0.042f, 0.030f, 0.118f);
            Gfx.box("fangLow2", 0.010f, 0.032f, 0.010f, hornMat, jaw,
                    side * 0.072f, 0.026f, 0.080f);
        }

        var beard = Gfx.taperedCylinder("beard", 0.11f, 0.03f, 0.56f, hairMat,
                head, 0f, -0.42f, 0.13f);
        beard.rotate(0.10f, 0f, 0f);
        for (int side = -1; side <= 1; side += 2) {
            var strand = Gfx.taperedCylinder("beardSide", 0.035f, 0.012f, 0.36f, hairMat,
                    head, side * 0.13f, -0.32f, 0.10f);
            strand.rotate(0.10f, 0f, side * 0.14f);
        }

        float[] hornAng = {1.13f, 0.68f, 0.21f};
        float[] hornLen = {0.34f, 0.36f, 0.38f};
        float[] hornRad = {0.082f, 0.062f, 0.040f, 0.004f};
        for (int side = -1; side <= 1; side += 2) {
            float px = side * 0.13f;
            float py = 0.06f;
            for (int i = 0; i < 3; i++) {
                float dx = FastMath.sin(hornAng[i]) * side;
                float dy = FastMath.cos(hornAng[i]);
                float cx = px + dx * hornLen[i] * 0.5f;
                float cy = py + dy * hornLen[i] * 0.5f;
                var seg = Gfx.taperedCylinder("horn" + i, hornRad[i + 1], hornRad[i],
                        hornLen[i], hornMat, head, cx, cy, 0f);
                seg.setLocalRotation(new Quaternion()
                        .fromAngleAxis(side * -hornAng[i], Vector3f.UNIT_Z)
                        .mult(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X)));
                px += dx * hornLen[i];
                py += dy * hornLen[i];
            }
        }

        joint(chest, armL, -0.42f, 0.30f, 0f);
        joint(chest, armR, 0.42f, 0.30f, 0f);
        joint(armL, elbowL, 0f, -0.48f, 0f);
        joint(armR, elbowR, 0f, -0.48f, 0f);
        joint(elbowL, handL, 0f, -0.46f, 0.02f);
        joint(elbowR, handR, 0f, -0.46f, 0.02f);
        for (Node arm : new Node[]{armL, armR}) {
            int side = arm == armL ? -1 : 1;
            Geometry pauldron = Gfx.sphere("pauldron", 0.175f, 10, cloak, arm,
                    side * 0.02f, 0.06f, 0f);
            pauldron.setLocalScale(1.05f, 0.85f, 1.10f);
            Gfx.sphere("pauldronStud", 0.022f, 6, trim, arm, side * 0.09f, 0.12f, 0.04f);
            for (int i = 0; i < 3; i++) {
                spike("shoulderSpike", 0.045f, 0.30f + i * 0.04f, hornMat, arm,
                        side * (0.10f + i * 0.03f), 0.10f - i * 0.04f, -0.02f,
                        -0.2f, -side * (0.75f + i * 0.18f));
            }
            for (int i = 0; i < 2; i++) {
                spike("armFur", 0.040f, 0.22f, robe, arm,
                        side * 0.09f, -0.24f - i * 0.10f, -0.06f, -2.4f, -side * 0.5f);
            }
            tube("upper", 0.088f, 0.100f, 0.58f, robe, arm, 0f, -0.28f, 0f);
            Gfx.torus("armBand", 0.098f, 0.014f, strap, arm, 0f, -0.20f, 0f);
        }
        for (Node elbow : new Node[]{elbowL, elbowR}) {
            blob("elbow", 0.090f, 0.090f, 0.090f, skin, elbow, 0f, -0.01f, 0f);
            tube("fore", 0.076f, 0.092f, 0.50f, skin, elbow, 0f, -0.24f, 0.01f);
            tube("bracer", 0.086f, 0.096f, 0.20f, hornMat, elbow, 0f, -0.15f, 0.01f);
            for (int i = 0; i < 2; i++) {
                Gfx.torus("bracerRing", 0.092f, 0.010f, strap, elbow,
                        0f, -0.08f - i * 0.12f, 0.01f);
            }
            Gfx.box("bracerFin", 0.024f, 0.10f, 0.040f, hornMat, elbow, 0f, -0.20f, 0.10f)
                    .rotate(0.30f, 0f, 0f);
        }
        for (Node hand : new Node[]{handL, handR}) {
            Gfx.box("palm", 0.095f, 0.070f, 0.075f, skin, hand, 0f, 0.01f, 0.02f);
            for (int f = -1; f <= 1; f++) {
                Gfx.box("finger", 0.026f, 0.075f, 0.028f, skin, hand,
                        f * 0.055f, -0.085f, 0.085f).rotate(0.30f, 0f, f * 0.06f);
                Gfx.taperedCylinder("claw", 0.005f, 0.018f, 0.070f, hornMat, hand,
                        f * 0.055f, -0.135f, 0.105f).rotate(2.0f, 0f, 0f);
            }
            Gfx.sphere("thumb", 0.030f, 6, skin, hand, -0.085f, -0.02f, 0.05f);
            Gfx.taperedCylinder("thumbClaw", 0.005f, 0.016f, 0.060f, hornMat, hand,
                    -0.105f, -0.055f, 0.075f).rotate(1.7f, 0f, -0.4f);
        }

        joint(handR, weapon, 0f, 0f, 0.05f);
        Material wood = Gfx.lit(am, new ColorRGBA(0.20f, 0.14f, 0.085f, 1f),
                new ColorRGBA(0.04f, 0.03f, 0.02f, 1f), ColorRGBA.Black, 6f);
        Gfx.mapped(wood, am, "wood_planks", 1.0f);
        Material stone = Gfx.lit(am, new ColorRGBA(0.17f, 0.165f, 0.155f, 1f),
                new ColorRGBA(0.035f, 0.034f, 0.032f, 1f), ColorRGBA.Black, 8f);
        Gfx.mapped(stone, am, "rock_wall", 1.2f);
        // 灾厄之锤：粗木柄 + 双头方石锤（重击招式的分量全在这块石头上）
        Gfx.cylinder("haft", 0.058f, 2.0f, wood, weapon, 0f, 0.30f, 0f);
        Gfx.cylinder("ferrule", 0.072f, 0.10f, trim, weapon, 0f, 1.14f, 0f);
        Gfx.cylinder("butt", 0.064f, 0.16f, trim, weapon, 0f, -0.68f, 0f);
        Gfx.box("hammerCore", 0.30f, 0.30f, 0.28f, stone, weapon, 0f, 1.55f, 0f);
        for (int side = -1; side <= 1; side += 2) {
            // 两端外扩的锤面 + 金箍：一整块平板从侧面看就是一块木牌
            Gfx.box("hammerEnd", 0.20f, 0.44f, 0.40f, stone, weapon,
                    side * 0.50f, 1.55f, 0f);
            Gfx.box("hammerFaceRim", 0.05f, 0.34f, 0.30f, stone, weapon,
                    side * 0.74f, 1.55f, 0f);
            Gfx.box("hammerBand", 0.04f, 0.34f, 0.32f, trim, weapon,
                    side * 0.295f, 1.55f, 0f);
            for (int k = -1; k <= 1; k += 2) {
                Gfx.sphere("hammerStud", 0.028f, 6, trim, weapon,
                        side * 0.755f, 1.55f, k * 0.24f);
                Gfx.box("hammerChip", 0.050f, 0.085f, 0.045f, stone, weapon,
                        side * 0.60f, 1.55f + k * 0.38f, 0.19f);
            }
        }
        // 握把缠皮：近景里锤柄不能是一根光棍
        for (int i = 0; i < 5; i++) {
            Gfx.torus("gripWrap", 0.064f, 0.011f, strap, weapon, 0f, -0.20f + i * 0.13f, 0f);
        }
        // 静止姿势把锤子扛在肩上；不转的话锤头正好骑在头顶上，看着像戴了顶木箱帽子
        weapon.setLocalRotation(new Quaternion().fromAngles(-0.55f, 0f, -0.22f));
        weaponSpan(0f, 1.55f, 0f, 0.55f);

        joint(handL, offhand, 0f, -0.05f, 0.06f);
        // 反手握：刀身沿 -Y 顺着手掌往下、略向前外。原来刀身朝 +Y 指回肘窝，
        // 0.72 米的长刀正好横穿手指，近景看是从手背里长出来的一根铁条。
        offhand.setLocalRotation(new Quaternion().fromAngles(-0.18f, 0f, -0.34f));
        Gfx.box("daggerGrip", 0.028f, 0.085f, 0.028f, hairMat, offhand, 0f, 0.010f, 0f);
        Gfx.sphere("daggerPommel", 0.030f, 6, trim, offhand, 0f, 0.100f, 0f);
        Gfx.box("daggerGuard", 0.105f, 0.018f, 0.028f, trim, offhand, 0f, -0.075f, 0f);
        Gfx.box("daggerBlade", 0.034f, 0.175f, 0.011f, trim, offhand, 0f, -0.265f, 0f);
        Gfx.box("daggerFuller", 0.010f, 0.150f, 0.015f, hairMat, offhand, 0f, -0.255f, 0f);
        Gfx.box("daggerTip", 0.022f, 0.045f, 0.009f, trim, offhand, 0f, -0.465f, 0f);

        joint(body, tailChain[0], 0f, -0.30f, -0.28f);
        tailChain[0].setLocalRotation(new Quaternion().fromAngleAxis(-0.35f, Vector3f.UNIT_X));
        for (int i = 1; i < tailChain.length; i++) {
            joint(tailChain[i - 1], tailChain[i], 0f, -0.06f, -0.36f);
            tailChain[i].setLocalRotation(new Quaternion().fromAngleAxis(-0.10f, Vector3f.UNIT_X));
        }
        for (int i = 0; i < tailChain.length; i++) {
            float k = 1f - i * 0.11f;
            blob("tailSeg", 0.115f * k, 0.105f * k, 0.21f * k, skin, tailChain[i], 0f, 0f, 0f);
            if (i % 2 == 0) {
                spike("tailSpike", 0.035f * k, 0.20f * k, hornMat, tailChain[i],
                        0f, 0.11f * k, 0f, 0f, 0f);
            }
        }
        spike("tailTip", 0.05f, 0.34f, hornMat, tailChain[tailChain.length - 1],
                0f, 0f, -0.28f, -1.55f, 0f);
    }

    // ---------------------------------------------------------------- Godrick

    private void buildGodrick() {
        Material flesh = Gfx.lit(am, new ColorRGBA(0.52f, 0.42f, 0.37f, 1f),
                new ColorRGBA(0.28f, 0.20f, 0.17f, 1f), ColorRGBA.Black, 3f);
        Material graft = Gfx.lit(am, new ColorRGBA(0.80f, 0.62f, 0.22f, 1f),
                new ColorRGBA(0.26f, 0.18f, 0.05f, 1f),
                new ColorRGBA(1f, 0.88f, 0.50f, 1f), 56f);
        Material iron = Gfx.lit(am, new ColorRGBA(0.22f, 0.21f, 0.20f, 1f),
                new ColorRGBA(0.07f, 0.07f, 0.06f, 1f), ColorRGBA.Black, 22f);
        Material cloth = Gfx.lit(am, new ColorRGBA(0.30f, 0.09f, 0.07f, 1f),
                new ColorRGBA(0.15f, 0.04f, 0.03f, 1f), ColorRGBA.Black, 2f);
        Material stone = Gfx.lit(am, new ColorRGBA(0.34f, 0.33f, 0.30f, 1f),
                new ColorRGBA(0.10f, 0.10f, 0.09f, 1f), ColorRGBA.Black, 10f);

        joint(root, body, 0f, 1.34f, 0f);
        body.setLocalRotation(new Quaternion().fromAngleAxis(0.10f, Vector3f.UNIT_X));
        joint(body, torso, 0f, 0.14f, 0f);
        joint(torso, chest, 0f, 0.32f, 0f);
        joint(chest, neck, 0f, 0.48f, 0.06f);
        joint(neck, head, 0f, 0.18f, 0.08f);
        joint(head, jaw, 0f, -0.12f, 0.08f);

        joint(body, legL, -0.30f, -0.14f, 0f);
        joint(body, legR, 0.30f, -0.14f, 0f);
        joint(legL, kneeL, 0f, -0.66f, 0f);
        joint(legR, kneeR, 0f, -0.66f, 0f);
        joint(kneeL, footL, 0f, -0.62f, 0.02f);
        joint(kneeR, footR, 0f, -0.62f, 0.02f);
        for (Node leg : new Node[]{legL, legR}) {
            Gfx.box("thigh", 0.21f, 0.34f, 0.23f, flesh, leg, 0f, -0.32f, 0f);
        }
        for (Node knee : new Node[]{kneeL, kneeR}) {
            Gfx.box("shin", 0.17f, 0.30f, 0.19f, flesh, knee, 0f, -0.30f, 0.02f);
        }
        for (Node foot : new Node[]{footL, footR}) {
            Gfx.box("foot", 0.20f, 0.09f, 0.30f, iron, foot, 0f, 0.02f, 0.09f);
        }

        Gfx.box("pelvis", 0.34f, 0.19f, 0.26f, cloth, body, 0f, -0.12f, 0f);
        Gfx.box("belt", 0.36f, 0.07f, 0.27f, graft, body, 0f, 0.04f, 0f);
        Gfx.box("chestBody", 0.46f, 0.34f, 0.32f, flesh, chest, 0f, 0.04f, 0.02f);
        Gfx.box("hump", 0.50f, 0.30f, 0.26f, flesh, chest, 0f, 0.22f, -0.30f);
        Gfx.box("collar", 0.34f, 0.10f, 0.24f, graft, chest, 0f, 0.32f, -0.02f);

        for (int i = 0; i < 4; i++) {
            float side = i < 2 ? -1f : 1f;
            float row = i % 2;
            Node graftArm = new Node("graftArm" + i);
            graftArm.setLocalTranslation(side * (0.22f + row * 0.14f),
                    0.08f + row * 0.26f, -0.34f);
            graftArm.setLocalRotation(new Quaternion().fromAngleAxis(
                    side * (0.35f + row * 0.2f), Vector3f.UNIT_Z));
            chest.attachChild(graftArm);
            Gfx.box("graftUpper", 0.07f, 0.20f, 0.08f, flesh, graftArm, 0f, -0.20f, 0f);
            Gfx.box("graftFore", 0.055f, 0.18f, 0.06f, graft, graftArm, 0f, -0.56f, 0.02f);
            Gfx.sphere("graftHand", 0.065f, 6, graft, graftArm, 0f, -0.74f, 0.03f);
        }

        Gfx.sphere("head", 0.17f, 12, flesh, head, 0f, 0f, 0f)
                .setLocalScale(1f, 1.05f, 1.05f);
        Gfx.box("brow", 0.15f, 0.03f, 0.04f, flesh, head, 0f, 0.06f, 0.16f);
        Gfx.box("lowerJaw", 0.11f, 0.07f, 0.12f, flesh, jaw, 0f, 0f, 0.05f);
        Material eyeMat = glowMat(new ColorRGBA(1f, 0.55f, 0.18f, 1f));
        Gfx.sphere("eyeL", 0.024f, 8, eyeMat, head, -0.06f, 0.01f, 0.17f);
        Gfx.sphere("eyeR", 0.024f, 8, eyeMat, head, 0.06f, 0.01f, 0.17f);
        Gfx.box("crownBand", 0.15f, 0.045f, 0.15f, graft, head, 0f, 0.14f, 0f);
        for (int i = 0; i < 5; i++) {
            float a = i / 5f * FastMath.TWO_PI;
            Gfx.taperedCylinder("crownSpike", 0.004f, 0.028f, 0.20f + (i % 2) * 0.07f,
                    graft, head, FastMath.cos(a) * 0.12f, 0.25f, FastMath.sin(a) * 0.12f);
        }

        joint(chest, armL, -0.52f, 0.24f, 0f);
        joint(chest, armR, 0.52f, 0.24f, 0f);
        joint(armL, elbowL, 0f, -0.56f, 0f);
        joint(armR, elbowR, 0f, -0.56f, 0f);
        joint(elbowL, handL, 0f, -0.52f, 0.02f);
        joint(elbowR, handR, 0f, -0.52f, 0.02f);
        for (Node arm : new Node[]{armL, armR}) {
            Gfx.box("pauldron", 0.19f, 0.14f, 0.20f, graft, arm, 0f, 0.04f, 0f);
            Gfx.box("upper", 0.15f, 0.30f, 0.16f, flesh, arm, 0f, -0.28f, 0f);
        }
        for (Node elbow : new Node[]{elbowL, elbowR}) {
            Gfx.box("fore", 0.14f, 0.28f, 0.15f, flesh, elbow, 0f, -0.26f, 0.02f);
        }
        for (Node hand : new Node[]{handL, handR}) {
            Gfx.sphere("hand", 0.15f, 8, flesh, hand, 0f, 0f, 0.03f);
        }

        joint(handR, weapon, 0f, -0.02f, 0.06f);
        Gfx.box("haft", 0.055f, 0.78f, 0.055f, iron, weapon, 0f, 0.35f, 0f);
        Gfx.box("axeHead", 0.42f, 0.26f, 0.075f, stone, weapon, 0.30f, 1.12f, 0f);
        Gfx.box("axeEdge", 0.10f, 0.27f, 0.08f, graft, weapon, 0.62f, 1.12f, 0f);
        Gfx.box("axeCollar", 0.10f, 0.10f, 0.10f, iron, weapon, 0.06f, 1.12f, 0f);
        weapon.setLocalRotation(new Quaternion().fromAngleAxis(-0.12f, Vector3f.UNIT_Z));
        weaponSpan(0.72f, 1.12f, 0.20f, 0.55f);
    }

    // ---------------------------------------------------------------- hunter / beast

    private void buildHunter() {
        buildHumanoid(1.0f, new ColorRGBA(0.38f, 0.35f, 0.33f, 1f),
                new ColorRGBA(0.42f, 0.12f, 0.10f, 1f), false, true);
        Material blade = Gfx.lit(am, new ColorRGBA(0.62f, 0.60f, 0.56f, 1f),
                new ColorRGBA(0.12f, 0.11f, 0.10f, 1f),
                new ColorRGBA(0.9f, 0.85f, 0.7f, 1f), 64f);
        Gfx.box("grip", 0.045f, 0.24f, 0.045f, Gfx.lit(am,
                        new ColorRGBA(0.14f, 0.09f, 0.07f, 1f),
                        new ColorRGBA(0.04f, 0.03f, 0.02f, 1f), ColorRGBA.Black, 4f),
                weapon, 0f, 0.02f, 0f);
        Gfx.box("greatsword", 0.135f, 0.64f, 0.04f, blade, weapon, 0f, 0.80f, 0f);
        Gfx.box("fuller", 0.02f, 0.52f, 0.045f,
                Gfx.lit(am, new ColorRGBA(0.30f, 0.29f, 0.28f, 1f),
                        new ColorRGBA(0.06f, 0.06f, 0.06f, 1f), ColorRGBA.Black, 32f),
                weapon, 0f, 0.82f, 0f);
        Gfx.box("guard", 0.30f, 0.045f, 0.055f, blade, weapon, 0f, 0.14f, 0f);
        weapon.setLocalRotation(new Quaternion().fromAngleAxis(-0.26f, Vector3f.UNIT_Z));
        weaponSpan(0f, 1.44f, 0f, 0.65f);

        Material brass = Gfx.lit(am, new ColorRGBA(0.78f, 0.60f, 0.24f, 1f),
                new ColorRGBA(0.30f, 0.21f, 0.07f, 1f),
                new ColorRGBA(1f, 0.88f, 0.5f, 1f), 56f);
        Node bell = new Node("hunterBell");
        bell.setLocalTranslation(0.36f, 0.30f, 0.10f);
        chest.attachChild(bell);
        Gfx.taperedCylinder("bellBody", 0.19f, 0.30f, 0.40f, brass, bell, 0f, -0.18f, 0f);
        Gfx.sphere("bellTop", 0.09f, 8, brass, bell, 0f, 0.04f, 0f);
        Gfx.box("bellRope", 0.045f, 0.20f, 0.045f, Gfx.lit(am,
                        new ColorRGBA(0.30f, 0.22f, 0.16f, 1f),
                        new ColorRGBA(0.10f, 0.07f, 0.05f, 1f), ColorRGBA.Black, 3f),
                bell, 0f, 0.24f, 0f);
    }

    private void buildBeast() {
        buildHumanoid(0.98f, new ColorRGBA(0.30f, 0.25f, 0.22f, 1f),
                new ColorRGBA(0.12f, 0.10f, 0.09f, 1f), false, true);
        body.setLocalTranslation(0f, 0.94f, 0f);
        chest.setLocalRotation(new Quaternion().fromAngleAxis(0.35f, Vector3f.UNIT_X));
        neck.setLocalRotation(new Quaternion().fromAngleAxis(-0.25f, Vector3f.UNIT_X));
        Material fur = Gfx.lit(am, new ColorRGBA(0.36f, 0.29f, 0.24f, 1f),
                new ColorRGBA(0.16f, 0.12f, 0.09f, 1f), ColorRGBA.Black, 2f);
        Gfx.mapped(fur, am, "faux_fur", 0.9f);
        Gfx.box("mane", 0.30f, 0.28f, 0.14f, fur, chest, 0f, 0.36f, -0.14f);
        for (int side = -1; side <= 1; side += 2) {
            Node arm = side < 0 ? armL : armR;
            Node elbow = side < 0 ? elbowL : elbowR;
            arm.setLocalTranslation(side * 0.34f, 0.26f, 0.08f);
            elbow.setLocalRotation(new Quaternion().fromAngleAxis(-0.55f, Vector3f.UNIT_X));
            Gfx.box("clawUpper", 0.10f, 0.28f, 0.11f, fur, arm, 0f, -0.22f, 0.06f);
        }
        Gfx.box("clawForeL", 0.09f, 0.26f, 0.10f, fur, elbowL, 0f, -0.22f, 0.06f);
        Gfx.box("clawForeR", 0.09f, 0.26f, 0.10f, fur, elbowR, 0f, -0.22f, 0.06f);
        Material claw = Gfx.lit(am, new ColorRGBA(0.72f, 0.68f, 0.58f, 1f),
                new ColorRGBA(0.12f, 0.1f, 0.08f, 1f), ColorRGBA.Black, 16f);
        for (Node hand : new Node[]{handL, handR}) {
            for (int c = -1; c <= 1; c++) {
                Gfx.box("claw", 0.025f, 0.16f, 0.025f, claw, hand,
                        c * 0.055f, -0.10f, 0.10f);
            }
        }
        legL.setLocalRotation(new Quaternion().fromAngleAxis(-0.22f, Vector3f.UNIT_X));
        legR.setLocalRotation(new Quaternion().fromAngleAxis(0.22f, Vector3f.UNIT_X));
    }

    // ---------------------------------------------------------------- dragon

    private void buildDragon() {
        Material hide = Gfx.lit(am, new ColorRGBA(1f, 0.97f, 0.88f, 1f),
                new ColorRGBA(0.34f, 0.26f, 0.18f, 1f),
                new ColorRGBA(0.20f, 0.14f, 0.09f, 1f), 9f);
        Gfx.mapped(hide, am, "brown_leather", 1.0f);
        Material belly = Gfx.lit(am, new ColorRGBA(0.96f, 0.88f, 0.78f, 1f),
                new ColorRGBA(0.30f, 0.22f, 0.15f, 1f), ColorRGBA.Black, 4f);
        Gfx.mapped(belly, am, "faux_fur", 0.7f);
        Material horn = Gfx.lit(am, new ColorRGBA(0.80f, 0.74f, 0.62f, 1f),
                new ColorRGBA(0.22f, 0.20f, 0.16f, 1f), ColorRGBA.Black, 6f);
        Material claw = Gfx.lit(am, new ColorRGBA(0.34f, 0.30f, 0.24f, 1f),
                new ColorRGBA(0.10f, 0.08f, 0.06f, 1f), ColorRGBA.Black, 3f);
        Material scale = Gfx.lit(am, new ColorRGBA(0.92f, 0.66f, 0.46f, 1f),
                new ColorRGBA(0.28f, 0.19f, 0.13f, 1f), ColorRGBA.Black, 3f);
        Gfx.mapped(scale, am, "brown_leather", 1.6f);
        // 翼膜有明暗才会读成皮子：Unshaded 平铺一张图在任何光下都是一块色板
        Material membrane = Gfx.lit(am, new ColorRGBA(0.90f, 0.60f, 0.42f, 1f),
                new ColorRGBA(0.34f, 0.22f, 0.15f, 1f), ColorRGBA.Black, 3f);
        Gfx.mapped(membrane, am, "brown_leather", 1.0f);
        membrane.getAdditionalRenderState().setFaceCullMode(
                com.jme3.material.RenderState.FaceCullMode.Off);
        Material eyeMat = glowMat(new ColorRGBA(1f, 0.62f, 0.15f, 1f));
        Material maw = Gfx.lit(am, new ColorRGBA(0.32f, 0.07f, 0.06f, 1f),
                new ColorRGBA(0.10f, 0.02f, 0.02f, 1f), ColorRGBA.Black, 30f);

        joint(root, body, 0f, 2.55f, 0f);
        // 躯干一整张放样网格：胸廓宽、腰身收、胯部再放，轮廓是连续的曲线
        loft("torsoLoft", new float[][]{
                {2.45f, 0.30f, 0.30f, 0.10f},
                {2.20f, 0.82f, 0.74f, 0.06f},
                {1.90f, 1.10f, 0.98f, 0.02f},
                {1.45f, 1.28f, 1.10f, 0.00f},
                {0.95f, 1.36f, 1.18f, 0.04f},
                {0.20f, 1.34f, 1.24f, 0.02f},
                {-0.55f, 1.26f, 1.14f, -0.02f},
                {-1.20f, 1.12f, 1.00f, -0.02f},
                {-1.85f, 0.92f, 0.82f, 0.00f},
                {-2.30f, 0.62f, 0.58f, 0.04f},
                {-2.45f, 0.40f, 0.40f, 0.10f}}, 18, hide, body, 0f, 0f, 0f);
        // 腹皮：另一半放样体，只有下缘露在躯干外面，读成一条连续的浅色腹带
        loft("bellyLoft", new float[][]{
                {2.05f, 0.40f, 0.35f, -0.56f},
                {1.50f, 0.72f, 0.63f, -0.56f},
                {0.80f, 0.80f, 0.70f, -0.56f},
                {0.00f, 0.80f, 0.70f, -0.56f},
                {-0.80f, 0.78f, 0.68f, -0.56f},
                {-1.50f, 0.70f, 0.61f, -0.56f},
                {-2.05f, 0.46f, 0.40f, -0.56f}}, 16, belly, body, 0f, 0f, 0f);
        // 背脊：先垫一条脊梁，棘刺才像"长在背上"而不是插在球上
        Gfx.box("spineRidge", 0.11f, 0.10f, 2.35f, scale, body, 0f, 1.12f, -0.10f);
        for (int i = 0; i < 7; i++) {
            float z = 1.75f - i * 0.60f;
            float tall = 0.82f - Math.abs(i - 3f) * 0.09f;
            spike("spine", 0.075f, tall, horn, body, 0f, 1.16f + tall * 0.30f, z,
                    -0.32f, 0f);
        }
        for (int side = -1; side <= 1; side += 2) {
            // 胁腹鳞片贴在体表、顺着体轴排，薄板离体太远会读成贴纸
            for (int i = 0; i < 6; i++) {
                float z = 1.55f - i * 0.62f;
                float k = 1f - Math.abs(z) * 0.05f;
                Gfx.box("flankScale", 0.045f, 0.13f, 0.26f, scale, body,
                        side * 1.30f * k, 0.18f - i * 0.02f, z)
                        .rotate(0f, side * 0.18f, side * 0.30f);
            }
        }

        joint(body, neckChain[0], 0f, 0.62f, 2.05f);
        neckChain[0].setLocalRotation(new Quaternion().fromAngleAxis(-0.10f, Vector3f.UNIT_X));
        for (int i = 1; i < neckChain.length; i++) {
            joint(neckChain[i - 1], neckChain[i], 0f, 0.40f, 0.52f);
            neckChain[i].setLocalRotation(new Quaternion().fromAngleAxis(-0.06f, Vector3f.UNIT_X));
        }
        for (int i = 0; i < neckChain.length; i++) {
            float k = 1f - i * 0.055f;
            // 关节球 + buildDragonTubes() 里的锥台骨筒：只有球会读成串珠
            Gfx.sphere("vertebra", 0.50f * k, 10, hide, neckChain[i], 0f, 0f, 0f);
            blob("throat", 0.30f * k, 0.15f, 0.50f * k, belly, neckChain[i],
                    0f, -0.31f, 0.06f);
            Gfx.box("napeRidge", 0.06f * k, 0.06f, 0.44f * k, scale, neckChain[i],
                    0f, 0.36f * k, 0f);
            // 颈刺一簇比一根粗刺更像脊椎：三根小刺前后错开
            spike("neckSpike", 0.055f * k, 0.52f, horn, neckChain[i],
                    0f, 0.42f * k, 0.08f, -0.55f, 0f);
            spike("neckSpike2", 0.040f * k, 0.34f, horn, neckChain[i],
                    0f, 0.40f * k, -0.20f, -0.75f, 0f);
            if (i % 2 == 0) {
                for (int side = -1; side <= 1; side += 2) {
                    spike("neckHorn", 0.038f, 0.28f, horn, neckChain[i],
                            side * 0.36f * k, -0.02f, 0f, -0.5f, -side * 0.9f);
                }
            }
        }

        joint(neckChain[neckChain.length - 1], head, 0f, 0.46f, 0.52f);
        head.setLocalRotation(new Quaternion().fromAngleAxis(0.10f, Vector3f.UNIT_X));
        blob("skull", 0.68f, 0.56f, 1.00f, hide, head, 0f, 0.02f, 0.06f);
        blob("cheekL", 0.24f, 0.26f, 0.36f, hide, head, -0.52f, -0.06f, 0.34f);
        blob("cheekR", 0.24f, 0.26f, 0.36f, hide, head, 0.52f, -0.06f, 0.34f);
        Gfx.box("brow", 0.52f, 0.11f, 0.12f, horn, head, 0f, 0.30f, 0.50f);
        // 吻部跟头同色，只有下颌与喉部用浅色腹皮；浅色吻部在阳光下会读成一整块白板
        Gfx.box("snout", 0.37f, 0.25f, 0.60f, hide, head, 0f, -0.05f, 0.86f);
        Gfx.box("noseRidge", 0.24f, 0.08f, 0.58f, scale, head, 0f, 0.19f, 0.88f);
        Gfx.box("nostrilBand", 0.33f, 0.045f, 0.09f, horn, head, 0f, 0.12f, 1.10f);
        Gfx.box("maw", 0.31f, 0.05f, 0.44f, maw, head, 0f, -0.17f, 0.80f);
        Gfx.box("crest", 0.10f, 0.16f, 0.34f, scale, head, 0f, 0.34f, -0.28f);
        for (int side = -1; side <= 1; side += 2) {
            Gfx.box("nostril", 0.05f, 0.03f, 0.04f, maw, head,
                    side * 0.11f, 0.17f, 1.30f);
            Gfx.sphere("eye", 0.105f, 8, eyeMat, head,
                    side * 0.34f, 0.15f, 0.60f);
            Gfx.box("socket", 0.13f, 0.09f, 0.07f, horn, head,
                    side * 0.37f, 0.24f, 0.57f);
            // 后掠的双角冠：主刺粗、副刺长，第三根贴颊
            spike("hornMain", 0.17f, 1.45f, horn, head,
                    side * 0.36f, 0.34f, -0.18f, -1.12f, -side * 0.38f);
            spike("hornSecond", 0.11f, 0.90f, horn, head,
                    side * 0.46f, 0.14f, -0.06f, -1.32f, -side * 0.60f);
            spike("cheekSpike", 0.085f, 0.56f, horn, head,
                    side * 0.50f, -0.14f, 0.22f, -1.55f, -side * 0.35f);
            for (int t = 0; t < 4; t++) {
                Gfx.box("tooth", 0.034f, 0.11f, 0.034f, horn, head,
                        side * (0.19f + t * 0.06f), -0.22f, 0.70f + t * 0.11f);
            }
        }

        joint(head, jaw, 0f, -0.24f, 0.26f);
        Gfx.box("jawBone", 0.36f, 0.12f, 0.60f, belly, jaw, 0f, 0f, 0.32f);
        Gfx.box("tongue", 0.17f, 0.03f, 0.34f, maw, jaw, 0f, 0.10f, 0.36f);
        Gfx.box("chin", 0.22f, 0.08f, 0.14f, hide, jaw, 0f, -0.09f, 0.70f);
        for (int side = -1; side <= 1; side += 2) {
            for (int t = 0; t < 4; t++) {
                Gfx.box("toothLow", 0.032f, 0.10f, 0.032f, horn, jaw,
                        side * (0.17f + t * 0.06f), 0.10f, 0.22f + t * 0.12f);
            }
            spike("jawSpike", 0.05f, 0.28f, horn, jaw,
                    side * 0.28f, -0.04f, -0.20f, -1.4f, -side * 0.4f);
        }

        for (int side = -1; side <= 1; side += 2) {
            Node[] arm = side < 0 ? wingArmL : wingArmR;
            Node shoulder = side < 0 ? wingL : wingR;
            joint(body, shoulder, side * 1.05f, 0.86f, 0.42f);
            // 翼尖朝上（左翼 -Z 为抬），后掠角收小；膜是整片刚体，不再单独转肘腕
            shoulder.setLocalRotation(new Quaternion().fromAngles(
                    0f, side * 0.30f, side * 0.28f));
            joint(shoulder, arm[0], side * 0.10f, 0.06f, 0f);
            Gfx.box("humerus", 0.78f, 0.15f, 0.17f, hide, arm[0], side * 0.72f, 0f, 0f)
                    .rotate(0f, 0f, side * 0.04f);
            Gfx.sphere("wingShoulder", 0.28f, 8, hide, arm[0], side * 0.05f, 0f, 0f);
            joint(arm[0], arm[1], side * 1.42f, 0.10f, -0.06f);
            Gfx.box("radius", 0.70f, 0.11f, 0.13f, hide, arm[1], side * 0.66f, 0f, 0f);
            joint(arm[1], arm[2], side * 1.34f, 0f, 0f);
            Gfx.box("handBone", 0.62f, 0.08f, 0.10f, hide, arm[2], side * 0.58f, 0f, 0f);
            // 翼面只挂一个节点：三段盒子拼膜必然在肘/腕处错台，看起来像撕开的纸
            wingSheet(arm[0], side, membrane, hide);
            spike("wingClaw", 0.06f, 0.40f, claw, arm[2], side * 1.18f, 0.06f, 0f,
                    1.25f, side * 0.5f);
        }

        joint(body, tailChain[0], 0f, -0.12f, -2.30f);
        tailChain[0].setLocalRotation(new Quaternion().fromAngleAxis(0.10f, Vector3f.UNIT_X));
        for (int i = 1; i < tailChain.length; i++) {
            joint(tailChain[i - 1], tailChain[i], 0f, -0.04f, -0.88f);
            tailChain[i].setLocalRotation(new Quaternion().fromAngleAxis(0.04f, Vector3f.UNIT_X));
        }
        for (int i = 0; i < tailChain.length; i++) {
            float k = 1f - i * 0.13f;
            // 尾部同样靠锥台骨筒连成一条尾，球只做关节包
            Gfx.sphere("tailSeg", 0.50f * k, 10, hide, tailChain[i], 0f, 0f, 0f);
            spike("tailSpike", 0.09f * k, 0.56f * k, horn, tailChain[i],
                    0f, 0.44f * k, 0f, -0.35f, 0f);
        }
        spike("tailBlade", 0.10f, 0.85f, horn, tailChain[tailChain.length - 1],
                0f, 0f, -0.62f, -1.45f, 0f);

        float[] lx = {-1.14f, 1.14f, -1.24f, 1.24f};
        float[] ly = {-0.25f, -0.25f, -0.10f, -0.10f};
        float[] lz = {1.34f, 1.34f, -1.22f, -1.22f};
        for (int i = 0; i < 4; i++) {
            joint(body, dragonLegs[i], lx[i], ly[i], lz[i]);
            joint(dragonLegs[i], dragonKnees[i], 0f, -1.00f, 0.02f);
            // 大腿用上粗下细的锥台接进躯干，球体做腿根会读成挂在体侧的蛋
            tube("haunch", 0.30f, 0.48f, 0.66f, hide, dragonLegs[i], 0f, -0.24f, -0.02f);
            tube("thigh", 0.25f, 0.32f, 0.52f, hide, dragonLegs[i], 0f, -0.66f, 0.02f);
            Gfx.sphere("hipBall", 0.30f, 10, hide, dragonLegs[i], 0f, -0.02f, 0.0f);
            Gfx.box("shin", new Cylinder(6, 14, 0.26f, 0.19f, 1.16f, true, false),
                    hide, dragonKnees[i], 0f, -0.58f, 0.06f);
            Gfx.sphere("hock", 0.19f, 8, hide, dragonKnees[i], 0f, -1.04f, 0.06f);
            Gfx.box("kneePlate", 0.20f, 0.10f, 0.16f, scale, dragonKnees[i],
                    0f, -0.16f, 0.24f).rotate(0.35f, 0f, 0f);
            Gfx.box("ankle", 0.20f, 0.16f, 0.22f, claw, dragonKnees[i], 0f, -1.10f, 0.06f);
            Gfx.box("foot", 0.31f, 0.13f, 0.44f, hide, dragonKnees[i], 0f, -1.20f, 0.28f);
            for (int c = -1; c <= 1; c++) {
                Gfx.taperedCylinder("talon", 0.015f, 0.06f, 0.44f, claw, dragonKnees[i],
                        c * 0.16f, -1.23f, 0.66f).rotate(1.15f, 0f, c * 0.10f);
            }
            spike("dewClaw", 0.035f, 0.22f, claw, dragonKnees[i],
                    0f, -1.18f, -0.02f, -2.0f, 0f);
        }
    }

    // ---------------------------------------------------------------- mariner

    private void buildMariner() {
        Material hull = Gfx.lit(am, new ColorRGBA(0.26f, 0.27f, 0.26f, 1f),
                new ColorRGBA(0.14f, 0.15f, 0.15f, 1f), ColorRGBA.Black, 5f);
        Gfx.mapped(hull, am, "wood_planks", 0.9f);
        Material ghost = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        ghost.setColor("Color", new ColorRGBA(0.35f, 0.72f, 1f, 0.55f));
        ghost.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.AlphaAdditive);
        ghost.getAdditionalRenderState().setDepthWrite(false);

        Node boat = new Node("boat");
        root.attachChild(boat);
        blob("hull", 1.25f, 0.40f, 3.1f, hull, boat, 0f, 0.62f, 0f);
        Gfx.box("rim", new Box(1.15f, 0.10f, 2.7f), hull, boat, 0f, 1.0f, 0f);
        for (int side = -1; side <= 1; side += 2) {
            Gfx.box("gunwale", 0.06f, 0.09f, 2.6f, hull, boat, side * 1.12f, 1.06f, 0f);
        }
        Gfx.taperedCylinder("prow", 0.10f, 0.22f, 1.3f, hull, boat, 0f, 1.05f, 2.95f)
                .rotate(-2.35f, 0f, 0f);
        Gfx.box("lanternPole", 0.05f, 1.35f, 0.05f, hull, boat, 0.92f, 1.85f, -2.35f);
        Gfx.box("lanternArm", 0.30f, 0.04f, 0.04f, hull, boat, 0.66f, 3.15f, -2.35f);
        Gfx.box("lanternPole2", 0.05f, 1.05f, 0.05f, hull, boat, -0.82f, 1.85f, -2.1f);
        Gfx.box("lanternArm2", 0.28f, 0.04f, 0.04f, hull, boat, -0.57f, 2.85f, -2.1f);
        Gfx.sphere("lantern", 0.22f, 8, ghost, boat, 0.42f, 2.82f, -2.35f);
        Gfx.sphere("lantern2", 0.18f, 8, ghost, boat, -0.42f, 2.52f, -2.1f);

        buildHumanoid(0.8f, new ColorRGBA(0.42f, 0.40f, 0.35f, 1f),
                new ColorRGBA(0.10f, 0.16f, 0.22f, 1f), false, true);
        body.setLocalTranslation(0f, 1.30f, -0.85f);
        Gfx.box("hood", 0.17f, 0.17f, 0.17f, hull, head, 0f, 0.06f, -0.03f);
        Material bone = Gfx.lit(am, new ColorRGBA(0.76f, 0.74f, 0.66f, 1f),
                new ColorRGBA(0.2f, 0.19f, 0.16f, 1f), ColorRGBA.Black, 4f);
        Material boneDark = Gfx.lit(am, new ColorRGBA(0.08f, 0.08f, 0.07f, 1f),
                new ColorRGBA(0.02f, 0.02f, 0.02f, 1f), ColorRGBA.Black, 2f);
        Gfx.sphere("skull", 0.38f, 10, bone, head, 0f, 0.10f, 0.14f)
                .setLocalScale(1f, 1.12f, 1f);
        Gfx.sphere("eyeSocketL", 0.11f, 8, boneDark, head, -0.15f, 0.15f, 0.43f);
        Gfx.sphere("eyeSocketR", 0.11f, 8, boneDark, head, 0.15f, 0.15f, 0.43f);
        Gfx.sphere("eyeGlowL", 0.05f, 6, glowMat(new ColorRGBA(0.45f, 0.8f, 1f, 1f)),
                head, -0.15f, 0.15f, 0.47f);
        Gfx.sphere("eyeGlowR", 0.05f, 6, glowMat(new ColorRGBA(0.45f, 0.8f, 1f, 1f)),
                head, 0.15f, 0.15f, 0.47f);
        Gfx.box("noseHole", 0.05f, 0.07f, 0.05f, boneDark, head, 0f, 0.01f, 0.49f);
        Gfx.box("boneJaw", 0.24f, 0.09f, 0.26f, bone, jaw, 0f, 0f, 0.14f);
        for (int i = 0; i < 4; i++) {
            Gfx.box("rib", 0.30f - i * 0.03f, 0.02f, 0.06f, boneDark, chest,
                    0f, 0.24f - i * 0.10f, 0.16f);
        }
        Gfx.box("oar", 0.05f, 2.1f, 0.05f, hull, weapon, 0.5f, -0.8f, 0f)
                .rotate(0f, 0f, 0.4f);
        weaponSpan(0.92f, 1.35f, 0.5f, 0.10f);
    }

    // ---------------------------------------------------------------- avatar

    private void buildAvatar() {
        // 树冠会把树干整段遮进自阴影里，环境色压太低就只剩黑剪影；
        // bark 走 brown_leather 贴图（本身就偏暗），底色必须给亮
        // brown_leather 本身的均值只有 (81,45,16)，乘完 diffuse 就是一根黑炭；
        // 灰白树皮用 mossy_rock：亮度够，自带的裂纹正好像干裂的树皮沟
        Material bark = Gfx.lit(am, new ColorRGBA(0.96f, 0.92f, 0.84f, 1f),
                new ColorRGBA(0.62f, 0.58f, 0.50f, 1f), ColorRGBA.Black, 3f);
        Gfx.mapped(bark, am, "mossy_rock", 1.1f);
        Material barkDark = Gfx.lit(am, new ColorRGBA(0.46f, 0.40f, 0.31f, 1f),
                new ColorRGBA(0.28f, 0.24f, 0.19f, 1f), ColorRGBA.Black, 2f);
        Gfx.mapped(barkDark, am, "mossy_rock", 1.4f);
        // 叶色压深降饱和：0.54 的亮黄绿在太阳下会整冠泛白，读成仙人掌尖
        Material leaf = Gfx.lit(am, new ColorRGBA(0.30f, 0.39f, 0.15f, 1f),
                new ColorRGBA(0.22f, 0.29f, 0.14f, 1f), ColorRGBA.Black, 4f);
        Material leafLight = Gfx.lit(am, new ColorRGBA(0.40f, 0.47f, 0.19f, 1f),
                new ColorRGBA(0.27f, 0.33f, 0.16f, 1f), ColorRGBA.Black, 4f);
        Material leafDark = Gfx.lit(am, new ColorRGBA(0.19f, 0.26f, 0.095f, 1f),
                new ColorRGBA(0.14f, 0.20f, 0.10f, 1f), ColorRGBA.Black, 3f);
        Material gold = Gfx.lit(am, new ColorRGBA(1f, 0.76f, 0.28f, 1f),
                new ColorRGBA(0.42f, 0.26f, 0.04f, 1f),
                new ColorRGBA(1f, 0.9f, 0.6f, 1f), 56f);
        Material goldGlow = glowMat(new ColorRGBA(1f, 0.76f, 0.26f, 1f));

        joint(root, treeTrunk, 0f, 0f, 0f);
        Gfx.taperedCylinder("trunk", 0.88f, 1.06f, 3.30f, bark, treeTrunk, 0f, 1.65f, 0f);
        // 根部外扩成喇叭裙：直筒落地像立在草地上的木桶
        Gfx.taperedCylinder("trunkFoot", 1.02f, 1.38f, 1.00f, bark, treeTrunk, 0f, 0.50f, 0f);
        // 板根：从树干里长出来，向外下方扎进土里；方向必须显式给，不能靠 Euler 角度猜
        for (int i = 0; i < 8; i++) {
            float a = i * FastMath.TWO_PI / 8f + 0.25f;
            float r = 0.86f + (i % 3) * 0.08f;
            float spread = 0.82f + (i % 3) * 0.08f;
            rod("root", 0.08f, 0.38f, 1.95f, bark, treeTrunk,
                    FastMath.cos(a) * r, 0.62f, FastMath.sin(a) * r,
                    FastMath.cos(a) * spread, -1f, FastMath.sin(a) * spread);
        }
        // 五道厚重板根：只在底部铺满一圈细红根，腰部以下还是一段裙摆
        for (int i = 0; i < 5; i++) {
            float a = i * FastMath.TWO_PI / 5f + 0.35f;
            rod("buttress", 0.13f, 0.46f, 2.10f, bark, treeTrunk,
                    FastMath.cos(a) * 0.98f, 1.15f, FastMath.sin(a) * 0.98f,
                    FastMath.cos(a) * 0.62f, -1f, FastMath.sin(a) * 0.62f);
        }
        // 木纹裂缝只留几道短的：长条金线会读成顺着树干淌下来的蜡烛油
        for (int i = 0; i < 4; i++) {
            float a = i * 1.55f + 0.55f;
            Geometry crack = Gfx.box("goldCrack", 0.026f, 0.13f + (i % 2) * 0.10f, 0.02f,
                    goldGlow, treeTrunk, FastMath.cos(a) * 0.98f, 1.60f + (i % 3) * 0.55f,
                    FastMath.sin(a) * 0.98f);
            crack.rotate(0.12f, -a, 0.06f);
        }

        joint(treeTrunk, treeTop, 0f, 3.30f, 0f);
        Gfx.taperedCylinder("trunk2", 0.74f, 0.92f, 1.70f, bark, treeTop, 0f, 0.85f, 0f);
        // 脸先垫出一块凸出的体量。树干在 y=0.86 处半径约 0.83，
        // 五官直接放在 z=0.65 会整段埋进树干里——这是之前"没有脸"的真正原因。
        blob("faceMass", 0.64f, 0.72f, 0.34f, bark, treeTop, 0f, 0.86f, 0.74f);
        // 五官全部要凸出这张脸板（正面 z=1.08）才看得到；
        // 之前整套五官都贴在 z≈1.03 的树干弧面上，只剩两点金线露在外面。
        Gfx.box("carvedFace", 0.46f, 0.46f, 0.08f, bark, treeTop, 0f, 0.88f, 1.00f);
        Material carve = Gfx.lit(am, new ColorRGBA(0.16f, 0.11f, 0.06f, 1f),
                new ColorRGBA(0.05f, 0.04f, 0.02f, 1f), ColorRGBA.Black, 2f);
        // 眉骨整条压过眼窝，眼窝再挖暗，金瞳只留一道细缝：
        // 等大的方眼 + 金方块会读成机器人脸。
        Gfx.box("browRidge", 0.56f, 0.105f, 0.15f, bark, treeTop, 0f, 1.18f, 1.06f)
                .rotate(-0.12f, 0f, 0f);
        for (int side = -1; side <= 1; side += 2) {
            Gfx.box("browWing", 0.18f, 0.075f, 0.12f, bark, treeTop,
                    side * 0.30f, 1.10f, 1.07f).rotate(-0.10f, 0f, -side * 0.30f);
            Gfx.box("socket", 0.17f, 0.12f, 0.06f, carve, treeTop,
                    side * 0.25f, 0.97f, 1.10f).rotate(0f, 0f, -side * 0.10f);
            Gfx.box("eyeSlit", 0.09f, 0.026f, 0.03f, goldGlow, treeTop,
                    side * 0.25f, 0.96f, 1.155f).rotate(0f, 0f, -side * 0.18f);
            blob("cheek", 0.18f, 0.20f, 0.12f, bark, treeTop,
                    side * 0.40f, 0.76f, 1.00f);
        }
        // 鼻子要长过眉骨：只有它凸出来，这张脸才不是一块平板
        Gfx.box("noseRidge", 0.075f, 0.26f, 0.12f, bark, treeTop, 0f, 0.80f, 1.10f);
        // 嘴是一道深槽，里面竖着几颗木牙——金牙会读成咧嘴笑的骷髅
        Gfx.box("mouth", 0.30f, 0.09f, 0.05f, carve, treeTop, 0f, 0.44f, 1.10f);
        // 牙做成长短不一的木楔：等宽等高的方牙会排成一排琴键
        for (int i = 0; i < 4; i++) {
            float tw = i == 1 ? 0.028f : 0.045f;
            Gfx.box("tooth", tw, 0.055f + (i % 2) * 0.045f, 0.03f, bark, treeTop,
                    -0.165f + i * 0.11f, 0.445f, 1.135f)
                    .rotate(0f, 0f, (i % 2 == 0 ? 0.09f : -0.09f));
        }
        blob("chin", 0.24f, 0.12f, 0.11f, bark, treeTop, 0f, 0.32f, 0.99f);

        joint(treeTop, crown, 0f, 1.70f, 0f);
        // 三层压扁的叶盘叠成伞形；单颗大球 + 一圈小球就是花椰菜
        // 冠心是一摞往上收的叶团：底盘宽、顶上收，才不会盖成一顶平伞
        blob("canopyBase", 0.95f, 0.55f, 0.95f, leafDark, crown, 0f, 0.50f, 0f);
        blob("canopyMid", 0.84f, 0.54f, 0.84f, leaf, crown, 0f, 0.92f, 0f);
        blob("canopyTop", 0.68f, 0.48f, 0.68f, leafLight, crown, 0f, 1.30f, 0f);
        for (int i = 0; i < lobe.length; i++) {
            float a = i * FastMath.TWO_PI / lobe.length;
            // 瓣要留缝：九颗球互相压满就合成一顶蘑菇伞，中间露出天光才读成树叶
            float r = 1.02f + (i % 3) * 0.13f;
            float ly = 0.64f + (i % 4) * 0.15f;
            // 树冠九瓣各自是一节骨：绽放时向外翻开，不是整体缩放
            joint(crown, lobe[i], FastMath.cos(a) * r, ly, FastMath.sin(a) * r);
            lobe[i].setLocalRotation(new Quaternion().fromAngles(0.05f, 0f, 0.10f));
            // 每瓣是三四颗小团压出来的：一颗大球就是一块光板奶酪
            blob("leafLobe", 0.46f, 0.34f, 0.46f, i % 2 == 0 ? leaf : leafLight,
                    lobe[i], 0f, 0f, 0f);
            blob("leafPuff", 0.34f, 0.28f, 0.34f, i % 3 == 0 ? leafLight : leaf,
                    lobe[i], FastMath.cos(a) * 0.26f, 0.16f, FastMath.sin(a) * 0.26f);
            blob("leafPuff", 0.28f, 0.24f, 0.28f, leafDark,
                    lobe[i], FastMath.cos(a + 1.5f) * 0.24f, -0.12f,
                    FastMath.sin(a + 1.5f) * 0.24f);
            // 冠沿垂叶：叶尖朝下往外挂，树冠才有"披下来"的一圈，而不是向四周放花瓣
            for (int f = -1; f <= 1; f += 2) {
                float fa = a + f * 0.44f + (i % 2) * 0.17f;
                float tilt = 2.34f - (i % 3) * 0.06f;
                blade("leafFan", 0.30f, 0.86f, f > 0 ? leaf : leafDark, lobe[i],
                        FastMath.cos(fa) * 0.52f, -0.04f, FastMath.sin(fa) * 0.52f,
                        FastMath.cos(fa) * FastMath.sin(tilt), FastMath.cos(tilt),
                        FastMath.sin(fa) * FastMath.sin(tilt));
            }
        }
        // 冠顶一撮朝上的宽叶，正面剪影才不是个圆蘑菇
        for (int i = 0; i < 5; i++) {
            float a = i * FastMath.TWO_PI / 5f + 0.42f;
            blade("leafCrest", 0.34f, 0.80f, i % 2 == 0 ? leaf : leafDark, crown,
                    FastMath.cos(a) * 0.32f, 1.92f, FastMath.sin(a) * 0.32f,
                    FastMath.cos(a) * 0.42f, 1f, FastMath.sin(a) * 0.42f);
        }

        for (int side = -1; side <= 1; side += 2) {
            Node[] arm = side < 0 ? rootArmL : rootArmR;
            // 右手握锤：小臂折到胸前、手腕再折回来，锤头才落得到地面
            boolean grip = side > 0;
            // 肩接在颈根（treeTop y≈0）+ 树干半径之外；接在脸那一侧会读成"手臂长在脸上"
            blob("shoulder", 0.44f, 0.42f, 0.44f, bark, treeTop, side * 0.82f, 0.10f, 0.02f);
            joint(treeTop, arm[0], side * 0.95f, 0.04f, 0.02f);
            arm[0].setLocalRotation(new Quaternion().fromAngleAxis(side * 0.34f, Vector3f.UNIT_Z));
            tube("branch", 0.28f, 0.23f, 1.15f, bark, arm[0], 0f, -0.56f, 0f);
            blob("elbowKnot", 0.27f, 0.24f, 0.25f, bark, arm[0], 0f, -1.12f, 0f);
            joint(arm[0], arm[1], 0f, -1.12f, 0f);
            arm[1].setLocalRotation(grip
                    ? new Quaternion().fromAngleAxis(-2.0f, Vector3f.UNIT_X)
                    : new Quaternion().fromAngleAxis(-side * 0.12f, Vector3f.UNIT_Z));
            tube("branch2", 0.23f, 0.18f, 0.95f, bark, arm[1], 0f, -0.46f, 0.03f);
            joint(arm[1], arm[2], 0f, -0.94f, 0f);
            arm[2].setLocalRotation(grip
                    ? new Quaternion().fromAngleAxis(2.0f, Vector3f.UNIT_X)
                    : new Quaternion().fromAngleAxis(side * 0.16f, Vector3f.UNIT_Z));
            blob("knuckle", 0.25f, 0.23f, 0.26f, bark, arm[2], 0f, -0.14f, 0f);
            for (int f = -1; f <= 1; f++) {
                blade("claw", 0.060f, 0.46f, bark, arm[2],
                        f * 0.12f, -0.32f, 0.10f, f * 0.30f, -1f, 0.30f);
            }
        }

        joint(rootArmR[2], weapon, 0f, -0.20f, 0.06f);
        weapon.setLocalRotation(new Quaternion().fromAngleAxis(0.20f, Vector3f.UNIT_Z));
        Gfx.cylinder("clubShaft", 0.17f, 1.65f, bark, weapon, 0f, -0.82f, 0f);
        // 锤头做成略扁的木瘤：正球 + 两道金环会读成挂着两个呼啦圈
        blob("clubHead", 0.52f, 0.58f, 0.52f, bark, weapon, 0f, -1.90f, 0f);
        Gfx.torus("clubBand", 0.31f, 0.055f, gold, weapon, 0f, -1.42f, 0f);
        // 锤头是长满木瘤的树桩：细金刺会读成一圈铁丝
        for (int i = 0; i < 5; i++) {
            float a = i * FastMath.TWO_PI / 5f + 0.3f;
            blob("clubKnob", 0.20f, 0.20f, 0.20f, barkDark, weapon,
                    FastMath.cos(a) * 0.44f, -1.90f + FastMath.sin(a) * 0.42f, 0.06f);
        }
        weaponSpan(0f, -2.44f, 0f, -1.05f);
    }

    /**
     * 飞龙第二遍：鳞板、腹甲、翼骨关节、腿部甲片。
     * buildDragon() 只给骨架与大块体量，细节在这里补；全部挂在已绑定的关节下，
     * 所以姿态指纹和关节数不受影响。
     */
    private void detailDragon() {
        Material hide = Gfx.lit(am, new ColorRGBA(1f, 0.97f, 0.88f, 1f),
                new ColorRGBA(0.34f, 0.26f, 0.18f, 1f),
                new ColorRGBA(0.20f, 0.14f, 0.09f, 1f), 9f);
        Gfx.mapped(hide, am, "brown_leather", 1.0f);
        Material scale = Gfx.lit(am, new ColorRGBA(0.92f, 0.66f, 0.46f, 1f),
                new ColorRGBA(0.28f, 0.19f, 0.13f, 1f), ColorRGBA.Black, 3f);
        Gfx.mapped(scale, am, "brown_leather", 1.6f);
        Material belly = Gfx.lit(am, new ColorRGBA(0.96f, 0.88f, 0.78f, 1f),
                new ColorRGBA(0.30f, 0.22f, 0.15f, 1f), ColorRGBA.Black, 4f);
        Gfx.mapped(belly, am, "faux_fur", 0.7f);
        Material horn = Gfx.lit(am, new ColorRGBA(0.80f, 0.74f, 0.62f, 1f),
                new ColorRGBA(0.22f, 0.20f, 0.16f, 1f), ColorRGBA.Black, 6f);
        Material claw = Gfx.lit(am, new ColorRGBA(0.34f, 0.30f, 0.24f, 1f),
                new ColorRGBA(0.10f, 0.08f, 0.06f, 1f), ColorRGBA.Black, 3f);

        // 脊梁两侧再压一排小鳞刺：单排背刺从侧后方看只剩一条黑线
        for (int i = 0; i < 8; i++) {
            float z = 1.85f - i * 0.52f;
            float tall = 0.36f - Math.abs(i - 3.5f) * 0.03f;
            for (int side = -1; side <= 1; side += 2) {
                spike("dorsalScale", 0.052f, tall, horn, body,
                        side * 0.33f, 1.10f + tall * 0.40f, z, -0.42f, side * 0.42f);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            Node[] arm = side < 0 ? wingArmL : wingArmR;
            Gfx.sphere("wingElbow", 0.13f, 8, hide, arm[1], 0f, 0f, 0f);
            Gfx.sphere("wingWrist", 0.10f, 8, hide, arm[2], 0f, 0f, 0f);
            spike("wingSpur", 0.045f, 0.34f, claw, arm[2],
                    side * 1.22f, 0.14f, 0f, -0.45f, -side * 0.55f);
        }
        for (int i = 0; i < neckChain.length; i++) {
            float k = 1f - i * 0.055f;
            for (int j = -1; j <= 1; j++) {
                Gfx.box("neckScale", 0.19f * k, 0.035f, 0.12f * k, scale, neckChain[i],
                        j * 0.20f * k, -0.42f * k, j * 0.02f)
                        .rotate(0f, 0f, -j * 0.28f);
            }
            for (int side = -1; side <= 1; side += 2) {
                spike("neckFin", 0.045f * k, 0.38f * k, horn, neckChain[i],
                        side * 0.42f * k, 0.02f, -0.12f, -0.85f, -side * 0.85f);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            Gfx.box("lipRidge", 0.045f, 0.055f, 0.42f, scale, head,
                    side * 0.35f, -0.20f, 0.84f).rotate(0f, side * 0.06f, 0f);
            Gfx.box("eyeRidge", 0.16f, 0.05f, 0.10f, horn, head,
                    side * 0.36f, 0.29f, 0.62f).rotate(-0.30f, 0f, 0f);
            spike("browQuill", 0.038f, 0.30f, horn, head,
                    side * 0.24f, 0.38f, 0.38f, -0.90f, -side * 0.40f);
            spike("cheekQuill", 0.045f, 0.40f, horn, head,
                    side * 0.56f, 0.02f, 0.05f, -1.20f, -side * 0.75f);
            Gfx.box("jawLip", 0.040f, 0.045f, 0.36f, scale, jaw,
                    side * 0.30f, 0.02f, 0.32f);
        }
        spike("chinBarbel", 0.05f, 0.42f, claw, jaw, 0f, -0.14f, 0.52f, 2.90f, 0f);
        for (int i = 0; i < tailChain.length; i++) {
            float k = 1f - i * 0.13f;
            Gfx.box("tailScute", 0.34f * k, 0.05f, 0.16f * k, scale, tailChain[i],
                    0f, -0.40f * k, 0f);
            for (int side = -1; side <= 1; side += 2) {
                spike("tailFin", 0.036f * k, 0.26f * k, horn, tailChain[i],
                        side * 0.34f * k, 0.10f, -0.18f, -0.90f, -side * 0.80f);
            }
        }
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                Gfx.box("shinScale", 0.20f, 0.055f, 0.14f, scale, dragonKnees[i],
                        0f, -0.30f - j * 0.26f, 0.19f + j * 0.01f)
                        .rotate(0.22f, 0f, 0f);
            }
            for (int side = -1; side <= 1; side += 2) {
                spike("ankleSpur", 0.04f, 0.24f, claw, dragonKnees[i],
                        side * 0.18f, -0.86f, 0.02f, -0.60f, -side * 1.0f);
            }
        }
    }

    /**
     * 黄金树化身第二遍：树皮沟壑、树瘤、面孔、鹿角枝、叶簇、藤蔓与金穗。
     */
    private void detailAvatar() {
        // 与 buildAvatar 的树干同一组颜色，否则补上去的沟壑会和树干分成两种木头
        Material bark = Gfx.lit(am, new ColorRGBA(0.96f, 0.92f, 0.84f, 1f),
                new ColorRGBA(0.62f, 0.58f, 0.50f, 1f), ColorRGBA.Black, 3f);
        Gfx.mapped(bark, am, "mossy_rock", 1.1f);
        Material barkDark = Gfx.lit(am, new ColorRGBA(0.46f, 0.40f, 0.31f, 1f),
                new ColorRGBA(0.28f, 0.24f, 0.19f, 1f), ColorRGBA.Black, 2f);
        Gfx.mapped(barkDark, am, "mossy_rock", 1.4f);
        Material leaf = Gfx.lit(am, new ColorRGBA(0.42f, 0.50f, 0.18f, 1f),
                new ColorRGBA(0.34f, 0.40f, 0.20f, 1f), ColorRGBA.Black, 4f);
        Material leafDark = Gfx.lit(am, new ColorRGBA(0.24f, 0.30f, 0.10f, 1f),
                new ColorRGBA(0.12f, 0.16f, 0.06f, 1f), ColorRGBA.Black, 3f);
        Material moss = Gfx.lit(am, new ColorRGBA(0.30f, 0.38f, 0.16f, 1f),
                new ColorRGBA(0.12f, 0.15f, 0.07f, 1f), ColorRGBA.Black, 2f);
        Material gold = Gfx.lit(am, new ColorRGBA(1f, 0.76f, 0.28f, 1f),
                new ColorRGBA(0.42f, 0.26f, 0.04f, 1f),
                new ColorRGBA(1f, 0.9f, 0.6f, 1f), 56f);
        Material goldGlow = glowMat(new ColorRGBA(1f, 0.76f, 0.26f, 1f));

        // 树皮沟壑：粗细高低都错开，等宽等高的竖条会读成木桶的桶板
        for (int i = 0; i < 14; i++) {
            float a = i * FastMath.TWO_PI / 14f + 0.12f;
            float h = 1.45f + (i % 4) * 0.38f;
            float r = 0.040f + (i % 3) * 0.016f;
            float rad = 0.93f + (i % 3) * 0.028f;
            float ym = 0.62f + h * 0.5f + (i % 3) * 0.22f;
            rod("barkRidgeFine", r * 0.45f, r, h, bark, treeTrunk,
                    FastMath.cos(a) * rad, ym, FastMath.sin(a) * rad,
                    FastMath.cos(a) * 0.07f + FastMath.sin(i * 1.7f) * 0.05f, 1f,
                    FastMath.sin(a) * 0.07f + FastMath.cos(i * 2.1f) * 0.05f);
        }
        // 深色干裂凹槽：竖着贴住树皮，太长会跨出锥度、悬在树干外面
        for (int i = 0; i < 5; i++) {
            float a = i * 1.32f + 0.45f;
            float h = 0.32f + (i % 3) * 0.22f;
            Gfx.box("barkGroove", 0.030f, h, 0.030f, barkDark, treeTrunk,
                    FastMath.cos(a) * 0.97f, 0.75f + (i % 3) * 0.62f, FastMath.sin(a) * 0.97f)
                    .rotate(0f, -a, FastMath.sin(i * 1.9f) * 0.06f);
        }
        for (int i = 0; i < 6; i++) {
            float a = i * 1.05f + 0.30f;
            float y = 0.70f + (i % 3) * 0.85f;
            blob("barkKnot", 0.18f, 0.13f, 0.18f, barkDark, treeTrunk,
                    FastMath.cos(a) * 0.94f, y, FastMath.sin(a) * 0.94f);
        }

        // 鹿角：主枝粗到 30 米外还看得见，叉子朝外张在树冠轮廓之外
        for (int side = -1; side <= 1; side += 2) {
            rod("antler", 0.13f, 0.42f, 2.35f, bark, crown,
                    side * 0.50f, 3.30f, -0.30f, side * 0.55f, 1f, -0.18f);
            rod("antlerTine", 0.03f, 0.15f, 1.55f, bark, crown,
                    side * 0.95f, 3.85f, -0.42f, side * 1.0f, 0.85f, -0.10f);
            rod("antlerTine2", 0.03f, 0.13f, 1.25f, bark, crown,
                    side * 0.78f, 4.18f, -0.34f, side * 0.45f, 1f, 0.15f);
            rod("antlerSpur", 0.02f, 0.10f, 0.95f, bark, crown,
                    side * 0.62f, 3.62f, 0.10f, side * 0.55f, 0.8f, 0.65f);
        }
        for (int i = 0; i < 6; i++) {
            float a = i * 1.05f + 0.2f;
            blob("branchKnotTop", 0.15f, 0.11f, 0.15f, barkDark, crown,
                    FastMath.cos(a) * 0.55f, 1.75f + (i % 2) * 0.18f, FastMath.sin(a) * 0.55f);
        }

        for (int side = -1; side <= 1; side += 2) {
            Node[] arm = side < 0 ? rootArmL : rootArmR;
            blob("branchKnot", 0.16f, 0.22f, 0.16f, barkDark, arm[0], 0f, -0.82f, 0.16f);
            blob("branchKnot2", 0.14f, 0.18f, 0.14f, barkDark, arm[1], 0f, -0.72f, 0.14f);
            Gfx.torus("branchRing", 0.26f, 0.04f, gold, arm[0], 0f, -0.30f, 0f);
            blob("moss", 0.26f, 0.10f, 0.22f, moss, arm[1],
                    side * 0.12f, -0.30f, 0.10f);
        }

        Gfx.sphere("clubCap", 0.20f, 8, gold, weapon, 0f, 0.06f, 0f);
        for (int i = 0; i < 3; i++) {
            Gfx.torus("clubCrack", 0.52f - i * 0.05f, 0.03f, barkDark, weapon, 0f,
                    -2.20f - i * 0.16f, 0f).rotate(0.12f * i, 0f, 0.12f * i);
        }
    }

    // ---------------------------------------------------------------- deathbird

    private void buildDeathbird() {
        Material feather = Gfx.lit(am, new ColorRGBA(0.17f, 0.165f, 0.18f, 1f),
                new ColorRGBA(0.05f, 0.05f, 0.06f, 1f),
                new ColorRGBA(0.30f, 0.32f, 0.40f, 1f), 12f);
        Material featherDark = Gfx.lit(am, new ColorRGBA(0.095f, 0.09f, 0.105f, 1f),
                new ColorRGBA(0.03f, 0.03f, 0.035f, 1f), ColorRGBA.Black, 6f);
        Material bone = Gfx.lit(am, new ColorRGBA(0.72f, 0.70f, 0.62f, 1f),
                new ColorRGBA(0.18f, 0.17f, 0.14f, 1f), ColorRGBA.Black, 6f);
        Material frost = glowMat(new ColorRGBA(0.62f, 0.86f, 1f, 1f));

        joint(root, body, 0f, 0f, 0f);
        blob("body", 0.66f, 0.92f, 1.06f, feather, body, 0f, 2.50f, 0f);
        blob("breast", 0.55f, 0.72f, 0.55f, featherDark, body, 0f, 2.30f, 0.70f);
        blob("ribs", 0.30f, 0.52f, 0.14f, bone, body, 0f, 2.45f, 1.02f);
        for (int i = -1; i <= 1; i++) {
            Gfx.box("scapular", 0.22f, 0.045f, 0.42f, featherDark, body,
                    i * 0.30f, 3.15f, -0.10f).rotate(0f, i * 0.25f, 0f);
        }

        joint(body, tailChain[0], 0f, 2.35f, -1.02f);
        for (int i = 1; i < 4; i++) {
            joint(tailChain[i - 1], tailChain[i], 0f, -0.02f, -0.55f);
        }
        for (int i = 0; i < 4; i++) {
            for (int k = -1; k <= 1; k++) {
                Gfx.box("tailPlume", 0.15f - i * 0.02f, 0.022f, 0.55f,
                        k == 0 ? featherDark : feather, tailChain[i],
                        k * 0.18f, 0f, -0.30f).rotate(0f, k * 0.24f, 0f);
            }
        }

        joint(body, neck, 0f, 3.22f, 0.40f);
        neck.setLocalRotation(new Quaternion().fromAngleAxis(0.46f, Vector3f.UNIT_X));
        Gfx.cylinder("neckMesh", 0.17f, 0.40f, feather, neck, 0f, 0.20f, 0f);
        Gfx.cylinder("neckMesh2", 0.14f, 0.38f, feather, neck, 0f, 0.58f, 0.06f);
        Gfx.cylinder("neckMesh3", 0.11f, 0.34f, bone, neck, 0f, 0.94f, 0.12f);
        joint(neck, head, 0f, 1.16f, 0.18f);
        Gfx.sphere("skull", 0.32f, 10, bone, head, 0f, 0f, 0f);
        Gfx.box("beakTop", 0.12f, 0.11f, 0.34f, bone, head, 0f, -0.03f, 0.44f);
        Gfx.box("beakHook", 0.08f, 0.18f, 0.11f, bone, head, 0f, -0.17f, 0.70f);
        Gfx.sphere("eyeL", 0.075f, 6, frost, head, -0.20f, 0.09f, 0.22f);
        Gfx.sphere("eyeR", 0.075f, 6, frost, head, 0.20f, 0.09f, 0.22f);
        joint(head, jaw, 0f, -0.13f, 0.16f);
        Gfx.box("beakLower", 0.09f, 0.06f, 0.30f, featherDark, jaw, 0f, 0f, 0.24f);

        for (int side = -1; side <= 1; side += 2) {
            Node shoulder = side < 0 ? wingL : wingR;
            Node[] arm = side < 0 ? wingArmL : wingArmR;
            joint(body, shoulder, side * 0.60f, 3.00f, 0.05f);
            Gfx.sphere("wingRoot", 0.34f, 8, featherDark, shoulder, side * 0.10f, 0.02f, 0.02f)
                    .setLocalScale(1.1f, 0.8f, 1.0f);
            joint(shoulder, arm[0], side * 0.16f, 0f, 0f);
            Gfx.box("wingArm", 0.95f, 0.075f, 0.16f, feather, arm[0], side * 0.92f, 0f, 0f);
            joint(arm[0], arm[1], side * 1.80f, 0f, -0.04f);
            Gfx.box("wingArm2", 0.85f, 0.06f, 0.13f, feather, arm[1], side * 0.82f, 0f, 0f);
            Gfx.box("wingMembrane", 1.35f, 0.03f, 0.66f, featherDark, arm[1],
                    side * 0.55f, -0.16f, -0.52f).rotate(0f, side * 0.14f, side * 0.10f);
            joint(arm[1], arm[2], side * 1.64f, 0f, -0.08f);
            Gfx.box("wingMembrane2", 1.20f, 0.03f, 0.60f, featherDark, arm[2],
                    side * 0.30f, -0.10f, -0.50f).rotate(0f, side * 0.10f, 0f);
            for (int k = 0; k < 9; k++) {
                float len = 1.95f - k * 0.11f;
                Gfx.box("feather", len * 0.5f, 0.035f, 0.30f,
                        (k % 2 == 0) ? feather : featherDark, arm[2],
                        side * (0.24f + len * 0.5f), -0.06f - k * 0.085f,
                        -0.08f - k * 0.17f).rotate(0f, side * (0.10f + k * 0.075f), 0f);
            }
        }

        joint(body, legL, -0.28f, 1.70f, 0.02f);
        joint(body, legR, 0.28f, 1.70f, 0.02f);
        joint(legL, kneeL, 0f, -0.50f, 0f);
        joint(legR, kneeR, 0f, -0.50f, 0f);
        joint(kneeL, footL, 0f, -0.48f, 0.02f);
        joint(kneeR, footR, 0f, -0.48f, 0.02f);
        for (Node leg : new Node[]{legL, legR}) {
            Gfx.cylinder("legMesh", 0.065f, 0.50f, bone, leg, 0f, -0.25f, 0f);
        }
        for (Node knee : new Node[]{kneeL, kneeR}) {
            Gfx.cylinder("tarsus", 0.05f, 0.48f, bone, knee, 0f, -0.24f, 0f);
        }
        for (Node foot : new Node[]{footL, footR}) {
            Gfx.box("foot", 0.14f, 0.05f, 0.20f, bone, foot, 0f, -0.02f, 0.06f);
            for (int t = -1; t <= 1; t++) {
                Gfx.box("toe", 0.035f, 0.03f, 0.20f, bone, foot, t * 0.10f, -0.02f, 0.24f);
            }
        }
    }

    // ---------------------------------------------------------------- animation

    void animate(float dt) {
        boolean windup = boss.state == WorldBoss.State.WINDUP;
        boolean strike = boss.state == WorldBoss.State.STRIKE;
        boolean recover = boss.state == WorldBoss.State.RECOVER;
        boolean dead = !boss.alive;
        WorldBoss.Move move = boss.move;
        float windK = 0f;
        float strikeK = 0f;
        if (move != null) {
            if (windup) {
                // 起手：前 75% 抬到位，最后 25% 的保持段才是玩家读招的窗口。
                float u = MathX.clamp(boss.stateTime / Math.max(0.2f, move.windup), 0f, 1f);
                windK = MathX.smooth(Math.min(1f, u / 0.75f));
                float hold = MathX.clamp((u - 0.72f) / 0.28f, 0f, 1f);
                windK += hold * FastMath.sin(boss.animTime * 42f) * 0.014f;
            } else if (strike) {
                // 蓄力姿势必须留住：原来 strike 段把 windK 直接归零，
                // 起手姿势在一帧内弹回静止再挥出去，看起来就是“动作不明显”。
                windK = 1f;
                float dur = MathX.clamp(move.active * 0.55f, 0.10f, 0.26f);
                strikeK = MathX.smooth(MathX.clamp(boss.stateTime / dur, 0f, 1f));
            } else if (recover) {
                // 收招：挥击姿势平滑放回，不再在 STRIKE→RECOVER 边界瞬切。
                float u = MathX.clamp(boss.stateTime / Math.max(0.25f, move.recover), 0f, 1f);
                float k = 1f - MathX.smooth(Math.min(1f, u / 0.70f));
                windK = k;
                strikeK = k;
            }
        }
        float deathK = dead ? MathX.smooth(MathX.clamp(boss.deathTime / 2.4f, 0f, 1f)) : 0f;
        float lift = boss.airLift * 8f - deathK * 0.30f;
        dbgWindK = windK;
        dbgStrikeK = strikeK;
        dbgMove = move == null ? "-" : move.name;

        root.setLocalTranslation(boss.pos.x, boss.pos.y + lift, boss.pos.z);
        q.fromAngleAxis(boss.yaw, Vector3f.UNIT_Y);
        if (deathK > 0f) {
            q.multLocal(new Quaternion().fromAngleAxis(
                    deathK * (boss.spec.archetype == WorldBoss.Archetype.DRAGON ? 1.1f : 1.4f),
                    Vector3f.UNIT_X));
        }
        root.setLocalRotation(q);
        root.setLocalScale(scale);

        float flash = boss.hitFlash;
        if (hitGlow != null) {
            hitGlow.getMaterial().setColor("Color",
                    new ColorRGBA(1f, 0.9f, 0.6f, 0.34f * flash));
            hitGlow.setCullHint(flash > 0.02f ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        }
        if (aura != null) {
            aura.rotate(0f, dt * (boss.phase2 ? 2.4f : 0.7f), 0f);
        }
        if (glowLight != null) {
            glowLight.setColor(new ColorRGBA(1f, 0.62f, 0.2f,
                    boss.phase2 ? 1f : 0.55f).multLocal(1.2f + (boss.phase2 ? 0.8f : 0f)));
        }

        resetJoints();
        if (dead) {
            poseDeath(deathK);
        } else {
            switch (boss.spec.archetype) {
                case SENTINEL, CAVALRY -> animateMounted(windK, strikeK, move);
                case MARGIT -> animateMargit(windK, strikeK, move);
                case DRAGON -> animateDragon(windK, strikeK, move);
                case AVATAR -> animateAvatar(windK, strikeK, move);
                case DEATHBIRD -> animateDeathbird(windK, strikeK, move);
                case MARINER -> animateMariner(windK, strikeK, move);
                case BEAST -> animateBeast(windK, strikeK, move);
                default -> animateHumanoid(windK, strikeK, move);
            }
            if (boss.state == WorldBoss.State.STAGGER) {
                slump(boss.stateTime, 1f);
            } else if (boss.state == WorldBoss.State.KNOCKDOWN) {
                slump(boss.stateTime, 1.35f);
            }
            if (boss.spec.archetype == WorldBoss.Archetype.MARINER
                    && boss.state == WorldBoss.State.WINDUP && move != null
                    && move.kind == WorldBoss.MoveKind.TELEPORT) {
                float flick = FastMath.sin(boss.animTime * 34f);
                root.setLocalScale(scale * (0.92f + 0.08f * flick));
            }
        }
    }

    private boolean named(WorldBoss.Move move, String a, String b) {
        return move != null && (move.name.equals(a) || move.name.equals(b));
    }

    private boolean named(WorldBoss.Move move, String a) {
        return move != null && move.name.equals(a);
    }

    // ---------------------------------------------------------------- humanoid

    private void animateHumanoid(float windK, float strikeK, WorldBoss.Move move) {
        float t = boss.animTime;
        float breathe = FastMath.sin(t * 1.7f);
        shift(body, 0f, breathe * 0.012f, 0f);
        rot(chest, breathe * 0.02f, 0f, 0f);
        rot(neck, -breathe * 0.03f, 0f, 0f);
        rot(armL, breathe * 0.04f, 0f, 0f);
        rot(armR, -breathe * 0.04f, 0f, 0f);
        for (int i = 0; i < cape.length; i++) {
            rot(cape[i], FastMath.sin(t * 1.3f - i * 0.45f) * 0.05f, 0f, 0f);
        }
        if (boss.state == WorldBoss.State.CHASE) {
            float s = FastMath.sin(boss.walk * 1.15f);
            rot(legL, s * 0.52f, 0f, 0f);
            rot(legR, -s * 0.52f, 0f, 0f);
            rot(kneeL, Math.max(0f, -s) * 0.60f, 0f, 0f);
            rot(kneeR, Math.max(0f, s) * 0.60f, 0f, 0f);
            rot(footL, -Math.max(0f, -s) * 0.30f, 0f, 0f);
            rot(footR, -Math.max(0f, s) * 0.30f, 0f, 0f);
            rot(armL, -s * 0.35f, 0f, 0f);
            rot(armR, s * 0.30f, 0f, 0f);
            rot(elbowL, 0.20f + Math.max(0f, s) * 0.25f, 0f, 0f);
            rot(elbowR, 0.20f + Math.max(0f, -s) * 0.25f, 0f, 0f);
            shift(body, 0f, Math.abs(s) * 0.04f, 0f);
        }
        if (move == null) {
            return;
        }
        switch (move.kind) {
            case MELEE -> {
                // 起手把重心压到后脚、刀举到肩后，挥出去时整个人前压半步
                rot(torso, 0f, 0.80f * windK - 1.15f * strikeK, 0.10f * windK);
                rot(armR, -2.35f * windK + 1.50f * strikeK, 0f, -0.18f * windK);
                rot(elbowR, -0.70f * windK + 0.35f * strikeK, 0f, 0f);
                rot(weapon, 1.15f * windK - 2.30f * strikeK, 0f, 0f);
                rot(armL, -0.55f * windK + 0.25f * strikeK, 0f, 0.28f * windK);
                rot(legL, -0.18f * windK + 0.22f * strikeK, 0f, 0f);
                rot(legR, 0.14f * windK - 0.10f * strikeK, 0f, 0f);
                shift(body, 0f, -0.07f * windK + 0.04f * strikeK,
                        0.08f * windK - 0.26f * strikeK);
            }
            case FLURRY -> {
                float chop = FastMath.sin(boss.stateTime * 26f) * 0.55f * strikeK;
                rot(torso, 0f, -0.35f * windK + chop * 0.6f, 0f);
                rot(armR, -1.10f * windK + 0.55f * strikeK + chop, 0f, 0f);
                rot(armL, -0.95f * windK + 0.50f * strikeK - chop, 0f, 0f);
                rot(elbowR, -0.70f * windK, 0f, 0f);
                rot(elbowL, -0.70f * windK, 0f, 0f);
                rot(weapon, 0.60f * windK + chop, 0f, 0f);
            }
            case AOE -> {
                rot(armL, -2.50f * windK + 1.55f * strikeK, 0f, 0.55f * windK);
                rot(armR, -2.50f * windK + 1.55f * strikeK, 0f, -0.55f * windK);
                rot(elbowL, -0.55f * windK + 0.20f * strikeK, 0f, 0f);
                rot(elbowR, -0.55f * windK + 0.20f * strikeK, 0f, 0f);
                rot(weapon, 0.40f * windK - 1.30f * strikeK, 0f, 0f);
                rot(chest, -0.34f * windK + 0.42f * strikeK, 0f, 0f);
                shift(body, 0f, 0.10f * windK - 0.30f * strikeK, 0f);
                rot(kneeL, 0.62f * windK + 0.25f * strikeK, 0f, 0f);
                rot(kneeR, 0.62f * windK + 0.25f * strikeK, 0f, 0f);
                rot(legL, 0.20f * strikeK, 0f, 0f);
                rot(legR, 0.20f * strikeK, 0f, 0f);
            }
            case DASH, LEAP -> {
                rot(torso, -0.35f * windK + 0.25f * strikeK, 0f, 0f);
                rot(legL, -0.55f * windK + 0.45f * strikeK, 0f, 0f);
                rot(legR, -0.30f * windK - 0.35f * strikeK, 0f, 0f);
                rot(kneeL, 0.95f * windK, 0f, 0f);
                rot(kneeR, 0.45f * windK, 0f, 0f);
                rot(armR, -0.80f * windK + 0.35f * strikeK, 0f, 0f);
                rot(armL, 0.60f * windK - 0.45f * strikeK, 0f, 0f);
                shift(body, 0f, -0.20f * windK, 0.10f * windK);
            }
            case SUMMON, TELEPORT -> {
                rot(armL, -1.15f * windK, 0f, 0.55f * windK);
                rot(armR, -1.15f * windK, 0f, -0.55f * windK);
                rot(chest, -0.20f * windK, 0f, 0f);
                rot(neck, -0.25f * windK, 0f, 0f);
            }
            default -> {
                rot(armL, -0.55f * windK, 0f, 0.30f);
                rot(armR, -0.55f * windK, 0f, -0.30f);
                rot(chest, -0.18f * windK, 0f, 0f);
            }
        }
    }

    // ---------------------------------------------------------------- mounted

    private void animateMounted(float windK, float strikeK, WorldBoss.Move move) {
        float t = boss.animTime;
        float w = boss.walk * 1.55f;
        boolean moving = boss.state == WorldBoss.State.CHASE;
        if (moving) {
            for (int i = 0; i < 4; i++) {
                float phase = w + ((i == 0 || i == 3) ? 0f : FastMath.PI);
                float sw = FastMath.sin(phase);
                rot(horseLegs[i], sw * 0.62f, 0f, 0f);
                rot(horseKnees[i], Math.max(0f, sw) * 1.05f, 0f, 0f);
            }
        } else {
            float idle = FastMath.sin(t * 1.1f) * 0.05f;
            rot(horseLegs[0], idle, 0f, 0f);
            rot(horseLegs[3], -idle, 0f, 0f);
        }
        if (move != null && (move.kind == WorldBoss.MoveKind.DASH
                || move.kind == WorldBoss.MoveKind.LEAP)) {
            float g = boss.animTime * 20f;
            for (int i = 0; i < 4; i++) {
                float phase = g + ((i == 0 || i == 3) ? 0f : FastMath.PI);
                rot(horseLegs[i], FastMath.sin(phase) * 0.85f, 0f, 0f);
                rot(horseKnees[i], Math.max(0f, FastMath.sin(phase)) * 1.25f, 0f, 0f);
            }
        }
        rot(horseNeck, FastMath.sin(t * 1.15f) * 0.05f - 0.06f * windK, 0f, 0f);
        rot(horseNeck2, FastMath.sin(t * 1.15f + 0.5f) * 0.04f, 0f, 0f);
        rot(horseHead, FastMath.sin(t * 1.15f + 1.0f) * 0.05f, 0f, 0f);
        rot(horseTail, FastMath.sin(t * 1.4f) * 0.10f, FastMath.sin(t * 0.9f) * 0.18f, 0f);
        rot(horseTail2, FastMath.sin(t * 1.4f - 0.6f) * 0.14f, 0f, 0f);

        float breathe = FastMath.sin(t * 1.6f);
        rot(torso, breathe * 0.02f, 0f, 0f);
        rot(armL, -0.10f, 0f, 0f);
        rot(elbowL, -0.20f, 0f, 0f);
        for (int i = 0; i < cape.length; i++) {
            rot(cape[i], FastMath.sin(t * 1.5f - i * 0.5f) * 0.06f - 0.14f * windK, 0f, 0f);
        }
        if (move == null) {
            return;
        }
        if (named(move, "halberd sweep", "reaping halberd")) {
            rot(torso, 0f, 1.10f * windK - 1.45f * strikeK, 0.10f * windK);
            rot(armR, -1.50f * windK + 0.55f * strikeK, 0f, 0.32f * windK);
            rot(elbowR, -0.55f * windK + 0.25f * strikeK, 0f, 0f);
            rot(weapon, 1.20f * windK - 2.05f * strikeK, 0f, 0f);
            rot(horseBody, 0f, 0.16f * windK - 0.24f * strikeK, -0.12f * windK);
        } else if (named(move, "overhead cleave", "")) {
            rot(armR, -2.60f * windK + 0.20f * strikeK, 0f, 0f);
            rot(armL, -0.55f * windK, 0f, 0f);
            rot(elbowR, -0.65f * windK + 0.18f * strikeK, 0f, 0f);
            rot(weapon, 0.80f * windK + 2.05f * strikeK, 0f, 0f);
            rot(torso, -0.26f * windK + 0.32f * strikeK, 0f, 0f);
            shift(body, 0f, 0.10f * windK - 0.22f * strikeK, 0f);
            rot(horseNeck, 0.16f * windK, 0f, 0f);
        } else if (named(move, "lance charge", "golden charge")
                || named(move, "night charge", "")) {
            rot(armR, -1.10f * windK + 0.75f * strikeK, 0f, 0f);
            rot(elbowR, -0.28f * windK, 0f, 0f);
            rot(weapon, 1.70f * windK - 0.25f * strikeK, 0f, 0f);
            rot(torso, 0.38f * windK - 0.12f * strikeK, 0f, 0f);
            rot(horseBody, -0.16f * windK + 0.10f * strikeK, 0f, 0f);
            rot(horseNeck, -0.22f * windK, 0f, 0f);
        } else if (named(move, "wheel slash", "")) {
            rot(torso, 0f, -5.5f * strikeK, 0f);
            rot(armR, -0.40f * windK, 0f, 1.35f * strikeK);
            rot(armL, -0.40f * windK, 0f, -1.20f * strikeK);
            rot(weapon, 0f, 0f, 1.40f * strikeK);
            rot(horseBody, 0f, -2.5f * strikeK, 0f);
        } else if (named(move, "hoof stomp", "trample")) {
            rot(horseBody, -0.62f * windK + 0.32f * strikeK, 0f, 0f);
            for (int i = 0; i < 2; i++) {
                rot(horseLegs[i], -1.30f * windK + 0.45f * strikeK, 0f, 0f);
                rot(horseKnees[i], 1.45f * windK, 0f, 0f);
            }
            rot(armR, -0.80f * windK + 0.55f * strikeK, 0f, 0f);
            rot(weapon, 0.60f * windK + 1.15f * strikeK, 0f, 0f);
            shift(horseBody, 0f, 0.18f * windK, 0f);
        }
    }

    // ---------------------------------------------------------------- Margit

    private void animateMargit(float windK, float strikeK, WorldBoss.Move move) {
        float t = boss.animTime;
        float breathe = FastMath.sin(t * 1.5f);
        rot(chest, breathe * 0.02f, 0f, 0f);
        rot(neck, -breathe * 0.03f, 0f, 0f);
        rot(armL, -0.12f + breathe * 0.03f, 0f, 0.05f);
        rot(elbowL, -0.38f, 0f, 0f);
        for (int i = 0; i < cape.length; i++) {
            rot(cape[i], FastMath.sin(t * 1.2f - i * 0.5f) * 0.05f, 0f, 0f);
        }
        for (int i = 0; i < tailChain.length; i++) {
            rot(tailChain[i], 0f,
                    FastMath.sin(t * 1.1f - i * 0.45f) * 0.10f, 0f);
        }
        if (boss.state == WorldBoss.State.CHASE) {
            float s = FastMath.sin(boss.walk * 1.10f);
            rot(legL, s * 0.48f, 0f, 0f);
            rot(legR, -s * 0.48f, 0f, 0f);
            rot(kneeL, Math.max(0f, -s) * 0.55f, 0f, 0f);
            rot(kneeR, Math.max(0f, s) * 0.55f, 0f, 0f);
            rot(armR, s * 0.22f, 0f, 0f);
            shift(body, 0f, Math.abs(s) * 0.045f, 0f);
        }
        if (move == null) {
            return;
        }
        if (named(move, "staff sweep", "")) {
            rot(torso, 0f, 1.15f * windK - 1.55f * strikeK, 0.10f * windK);
            rot(armR, -1.55f * windK + 0.60f * strikeK, 0f, 0.38f * windK);
            rot(elbowR, -0.60f * windK + 0.20f * strikeK, 0f, 0f);
            rot(weapon, 1.05f * windK - 1.85f * strikeK, 0f, 0f);
            rot(legL, -0.16f * windK + 0.20f * strikeK, 0f, 0f);
            shift(body, 0f, -0.05f * windK, 0.10f * windK - 0.22f * strikeK);
        } else if (named(move, "overhead smash", "")) {
            rot(armR, -2.75f * windK + 0.55f * strikeK, 0f, 0f);
            rot(armL, -1.35f * windK + 0.20f * strikeK, 0f, 0f);
            rot(elbowR, -0.65f * windK, 0f, 0f);
            rot(weapon, 0.85f * windK + 2.00f * strikeK, 0f, 0f);
            rot(chest, -0.30f * windK + 0.40f * strikeK, 0f, 0f);
            shift(body, 0f, 0.10f * windK - 0.30f * strikeK, 0f);
            rot(kneeL, 0.35f * windK, 0f, 0f);
            rot(kneeR, 0.35f * windK, 0f, 0f);
        } else if (named(move, "omen leap", "")) {
            shift(body, 0f, -0.34f * windK + 0.55f * strikeK, 0.10f * windK);
            rot(legL, -0.70f * windK + 0.30f * strikeK, 0f, 0f);
            rot(legR, -0.70f * windK + 0.30f * strikeK, 0f, 0f);
            rot(kneeL, 1.30f * windK, 0f, 0f);
            rot(kneeR, 1.30f * windK, 0f, 0f);
            rot(armR, -1.60f * windK + 0.90f * strikeK, 0f, 0f);
            rot(weapon, 0.40f * windK - 0.60f * strikeK, 0f, 0f);
            rot(chest, 0.20f * windK, 0f, 0f);
        } else if (named(move, "dagger flurry", "")) {
            float flurry = FastMath.sin(boss.stateTime * 36f) * 0.72f * strikeK;
            rot(torso, 0f, -0.45f * windK + flurry * 0.9f, 0f);
            rot(armL, -1.30f * windK + 0.95f * strikeK + flurry, 0f, 0.28f);
            rot(elbowL, -0.75f * windK - flurry * 0.7f, 0f, 0f);
            rot(armR, -0.50f * windK - flurry * 0.5f, 0f, -0.20f);
            rot(weapon, 0.40f * windK, 0f, 0f);
            shift(body, 0f, -0.08f * windK, 0.06f * windK - 0.16f * strikeK);
        } else if (named(move, "holy hammer", "")) {
            rot(armL, -2.75f * windK + 1.65f * strikeK, 0f, 0.30f * windK);
            rot(armR, -2.75f * windK + 1.65f * strikeK, 0f, -0.30f * windK);
            rot(elbowL, -0.45f * windK, 0f, 0f);
            rot(elbowR, -0.45f * windK, 0f, 0f);
            // 手骨抬起后 +Y 朝下后方，锤柄要再转近 180° 才把锤头举到头顶
            rot(weapon, 3.10f * windK - 3.05f * strikeK, 0f, 0f);
            rot(chest, -0.30f * windK + 0.34f * strikeK, 0f, 0f);
            shift(body, 0f, 0.10f * windK - 0.22f * strikeK, 0f);
            rot(kneeL, 0.45f * windK, 0f, 0f);
            rot(kneeR, 0.45f * windK, 0f, 0f);
        } else if (named(move, "crucible charge", "")) {
            rot(chest, 0.42f * windK - 0.10f * strikeK, 0f, 0f);
            rot(armR, -1.15f * windK + 0.75f * strikeK, 0f, 0f);
            rot(armL, 0.65f * windK - 0.35f * strikeK, 0f, 0f);
            rot(weapon, 1.55f * windK - 0.35f * strikeK, 0f, 0f);
            for (int i = 0; i < tailChain.length; i++) {
                rot(tailChain[i], -0.40f * windK + 0.16f * strikeK, 0f, 0f);
            }
            rot(legL, -0.60f * windK + 0.30f * strikeK, 0f, 0f);
            rot(legR, 0.42f * windK - 0.25f * strikeK, 0f, 0f);
            rot(kneeL, 0.45f * windK, 0f, 0f);
            shift(body, 0f, -0.10f * windK, 0.14f * windK);
        }
    }

    // ---------------------------------------------------------------- dragon

    private void animateDragon(float windK, float strikeK, WorldBoss.Move move) {
        float t = boss.animTime;
        shift(body, 0f, FastMath.sin(t * 1.1f) * 0.10f, 0f);
        rot(body, FastMath.sin(t * 1.1f) * 0.02f, 0f, 0f);
        for (int i = 0; i < neckChain.length; i++) {
            rot(neckChain[i], FastMath.sin(t * 0.9f - i * 0.35f) * 0.035f,
                    FastMath.sin(t * 0.5f - i * 0.30f) * 0.030f, 0f);
        }
        rot(head, FastMath.sin(t * 0.7f) * 0.04f, FastMath.sin(t * 0.45f) * 0.06f, 0f);
        for (int i = 0; i < tailChain.length; i++) {
            rot(tailChain[i], FastMath.sin(t * 0.8f - i * 0.40f) * 0.045f,
                    FastMath.sin(t * 0.6f - i * 0.35f) * 0.085f, 0f);
        }
        float flap = 0.10f + FastMath.sin(t * 1.2f) * 0.06f;
        rot(wingL, 0f, 0f, flap);
        rot(wingR, 0f, 0f, -flap);
        rot(wingArmL[0], 0f, 0f, flap * 0.6f);
        rot(wingArmR[0], 0f, 0f, -flap * 0.6f);
        for (int i = 0; i < 4; i++) {
            rot(dragonLegs[i], FastMath.sin(t * 1.05f + i * 1.6f) * 0.03f, 0f, 0f);
        }
        if (move == null) {
            return;
        }
        if (named(move, "bite", "")) {
            // 咬：脖子后坐蓄力 → 整条颈链甩出去，头低、颌张到最大
            float k = -0.20f * windK + 0.26f * strikeK;
            for (int i = 0; i < neckChain.length; i++) {
                rot(neckChain[i], k, 0f, 0f);
            }
            rot(head, -0.60f * windK + 0.90f * strikeK, 0f, 0f);
            rot(jaw, 0.25f * windK + 1.05f * strikeK, 0f, 0f);
            rot(body, -0.10f * windK + 0.16f * strikeK, 0f, 0f);
            shift(body, 0f, -0.10f * windK, 0.12f * windK + 0.55f * strikeK);
        } else if (named(move, "tail sweep", "")) {
            float sweep = 0.55f * windK - 1.15f * strikeK;
            for (int i = 0; i < tailChain.length; i++) {
                rot(tailChain[i], 0f, sweep * (0.55f + i * 0.14f), 0f);
            }
            rot(body, 0f, 0.24f * windK - 0.40f * strikeK, 0f);
            shift(body, 0f, 0f, 0.10f * windK - 0.14f * strikeK);
        } else if (named(move, "dragon stomp", "")) {
            rot(body, -0.42f * windK + 0.30f * strikeK, 0f, 0f);
            shift(body, 0f, 0.16f * windK - 0.08f * strikeK, -0.10f * windK);
            for (int i = 0; i < 2; i++) {
                rot(dragonLegs[i], -1.15f * windK + 0.50f * strikeK, 0f, 0f);
                rot(dragonKnees[i], 1.30f * windK, 0f, 0f);
            }
            for (int i = 2; i < 4; i++) {
                rot(dragonLegs[i], 0.25f * windK, 0f, 0f);
            }
            rot(wingL, 0f, 0f, -0.45f * windK + 0.30f * strikeK);
            rot(wingR, 0f, 0f, 0.45f * windK - 0.30f * strikeK);
        } else if (named(move, "agheel fire", "")) {
            // 蓄力时脖子后缩、翅膀半张；喷火时整条脖子前探（头不低，火舌才不会被身体挡住）
            for (int i = 0; i < neckChain.length; i++) {
                rot(neckChain[i], -0.08f * windK + 0.16f * strikeK, 0f, 0f);
            }
            rot(head, -0.55f * windK + 0.42f * strikeK, 0f, 0f);
            rot(jaw, 0.25f * windK + 1.25f * strikeK, 0f, 0f);
            rot(body, -0.12f * windK + 0.12f * strikeK, 0f, 0f);
            float spread = 0.20f * windK + 0.45f * strikeK;
            float raise = 0.34f * windK + 0.60f * strikeK;
            rot(wingL, 0f, spread, -raise);
            rot(wingR, 0f, -spread, raise);
            shift(body, 0f, 0.12f * windK - 0.06f * strikeK,
                    -0.10f * windK + 0.45f * strikeK);
        } else if (named(move, "wing gust", "")) {
            float raise = 1.65f * windK - 0.85f * strikeK;
            rot(wingL, 0f, 0f, -raise);
            rot(wingR, 0f, 0f, raise);
            rot(wingArmL[0], 0f, 0f, -raise * 0.28f);
            rot(wingArmR[0], 0f, 0f, raise * 0.28f);
            shift(body, 0f, 0.24f * windK - 0.10f * strikeK, 0f);
            rot(body, -0.18f * windK + 0.12f * strikeK, 0f, 0f);
            rot(head, -0.25f * windK, 0f, 0f);
        }
    }

    // ---------------------------------------------------------------- avatar

    private void animateAvatar(float windK, float strikeK, WorldBoss.Move move) {
        float t = boss.animTime;
        float sway = FastMath.sin(t * 0.8f) * 0.022f;
        rot(treeTrunk, sway * 0.7f, FastMath.sin(t * 0.5f) * 0.05f, sway);
        rot(treeTop, -sway * 0.9f, FastMath.sin(t * 0.5f + 1.0f) * 0.045f, sway * 0.8f);
        rot(crown, 0f, FastMath.sin(t * 0.6f) * 0.09f, sway * 1.3f);
        for (int i = 0; i < lobe.length; i++) {
            rot(lobe[i], FastMath.sin(t * 0.9f + i * 0.7f) * 0.05f, 0f,
                    FastMath.sin(t * 0.8f + i * 0.5f) * 0.05f);
        }
        for (int side = 0; side < 2; side++) {
            Node[] arm = side == 0 ? rootArmL : rootArmR;
            float s = side == 0 ? -1f : 1f;
            for (int i = 0; i < arm.length; i++) {
                rot(arm[i], FastMath.sin(t * 0.7f - i * 0.5f) * 0.025f, 0f,
                        s * FastMath.sin(t * 0.6f - i * 0.4f) * 0.018f);
            }
        }
        if (move == null) {
            return;
        }
        if (named(move, "club slam", "triple slam")) {
            float chop = named(move, "triple slam")
                    ? FastMath.sin(boss.stateTime * 18f) * 0.50f * strikeK : 0f;
            rot(rootArmR[0], -2.10f * windK + 1.40f * strikeK + chop, 0f, -0.45f * windK);
            rot(rootArmR[1], -0.60f * windK + 0.42f * strikeK, 0f, 0f);
            rot(rootArmR[2], -0.25f * windK + 0.20f * strikeK, 0f, 0f);
            rot(weapon, 0.45f * windK - 1.50f * strikeK, 0f, 0f);
            rot(treeTop, -0.30f * windK + 0.42f * strikeK, 0f, 0f);
            rot(rootArmL[0], 0.60f * windK - 0.20f * strikeK, 0f, -0.38f * windK);
            shift(treeTrunk, 0f, -0.26f * windK - 0.14f * strikeK, 0.10f * windK);
            rot(treeTrunk, 0.10f * windK - 0.06f * strikeK, 0f, 0f);
        } else if (named(move, "erdtree bloom", "")) {
            // 蓄力：树冠向上翻开、整棵树立起来；挥出：枝叶往前压、树冠向下炸开。
            // 光有蓄力没有释放，玩家读到的是一棵一直在发光的树，看不出招在哪一帧落下来。
            rot(treeTop, -0.22f * windK + 0.46f * strikeK, 0f, 0f);
            rot(crown, 0f, FastMath.sin(t * 3.2f) * 0.20f * windK,
                    0.16f * windK + 0.40f * strikeK);
            // 九瓣树冠向外翻开：外扩 + 上抬，读作黄金树在身前炸开
            for (int i = 0; i < lobe.length; i++) {
                Vector3f p = restPos.get(lobe[i]);
                if (p == null) {
                    continue;
                }
                shift(lobe[i], p.x * (0.14f * windK + 0.10f * strikeK),
                        0.26f * windK - 0.22f * strikeK,
                        p.z * (0.14f * windK + 0.10f * strikeK));
                rot(lobe[i], -0.16f * windK + 0.75f * strikeK, 0f,
                        -0.10f * windK - 0.25f * strikeK);
            }
            rot(rootArmL[0], -0.30f * windK + 0.42f * strikeK, 0f,
                    -0.95f * windK - 0.20f * strikeK);
            rot(rootArmR[0], -0.30f * windK + 0.42f * strikeK, 0f,
                    0.95f * windK + 0.20f * strikeK);
            rot(rootArmL[1], 0f, 0f, -0.30f * windK);
            rot(rootArmR[1], 0f, 0f, 0.30f * windK);
            shift(treeTrunk, 0f, 0.20f * windK - 0.34f * strikeK,
                    0.06f * windK - 0.16f * strikeK);
        } else if (named(move, "root spike", "")) {
            rot(treeTrunk, 0.30f * windK - 0.22f * strikeK, 0f, 0f);
            rot(treeTop, -0.24f * windK + 0.20f * strikeK, 0f, 0f);
            rot(rootArmL[0], 0.80f * windK - 0.30f * strikeK, 0f, -0.75f * windK);
            rot(rootArmR[0], 0.80f * windK - 0.30f * strikeK, 0f, 0.75f * windK);
            rot(rootArmL[1], 0.50f * windK, 0f, 0f);
            rot(rootArmR[1], 0.50f * windK, 0f, 0f);
            rot(crown, 0f, 0f, sway * 2f);
            shift(treeTrunk, 0f, 0.10f * windK - 0.24f * strikeK, 0f);
        }
    }

    // ---------------------------------------------------------------- deathbird

    private void animateDeathbird(float windK, float strikeK, WorldBoss.Move move) {
        float t = boss.animTime;
        shift(body, 0f, FastMath.sin(t * 1.6f) * 0.10f, 0f);
        float flap = 0.18f + FastMath.sin(t * 3.2f) * 0.16f
                + 0.70f * windK - 0.40f * strikeK;
        rot(wingL, 0f, 0f, -flap);
        rot(wingR, 0f, 0f, flap);
        rot(wingArmL[0], 0f, 0f, -flap * 0.35f);
        rot(wingArmR[0], 0f, 0f, flap * 0.35f);
        rot(wingArmL[1], 0f, 0f, flap * 0.20f);
        rot(wingArmR[1], 0f, 0f, -flap * 0.20f);
        rot(neck, FastMath.sin(t * 1.4f) * 0.05f - 0.25f * windK + 0.35f * strikeK, 0f, 0f);
        rot(head, -0.15f * windK + 0.30f * strikeK, FastMath.sin(t * 0.9f) * 0.10f, 0f);
        rot(legL, 0.45f + FastMath.sin(t * 1.3f) * 0.06f, 0f, 0f);
        rot(legR, 0.45f - FastMath.sin(t * 1.3f) * 0.06f, 0f, 0f);
        rot(kneeL, -0.85f, 0f, 0f);
        rot(kneeR, -0.85f, 0f, 0f);
        for (int i = 0; i < 4; i++) {
            rot(tailChain[i], FastMath.sin(t * 1.2f - i * 0.4f) * 0.06f, 0f, 0f);
        }
        if (move == null) {
            return;
        }
        if (named(move, "carrion peck", "")) {
            rot(neck, -0.45f * windK + 1.05f * strikeK, 0f, 0f);
            rot(head, -0.55f * windK + 0.85f * strikeK, 0f, 0f);
            rot(jaw, 0.15f * windK + 0.70f * strikeK, 0f, 0f);
            shift(body, 0f, 0.10f * windK, 0.10f * windK + 0.45f * strikeK);
        } else if (named(move, "grave wing", "")) {
            rot(wingL, 0f, 0f, 0.65f * windK - 1.25f * strikeK);
            rot(wingR, 0f, 0f, -0.65f * windK + 1.25f * strikeK);
            rot(body, 0f, 0.45f * windK - 0.65f * strikeK, 0f);
        } else if (named(move, "death swoop", "")) {
            rot(body, 0.32f * windK - 0.40f * strikeK, 0f, 0f);
            shift(body, 0f, 0.30f * windK - 0.10f * strikeK, 0.20f * strikeK);
            rot(wingL, 0f, 0f, -0.55f * windK);
            rot(wingR, 0f, 0f, 0.55f * windK);
            rot(neck, 0.25f * windK, 0f, 0f);
        } else if (named(move, "frost screech", "")) {
            rot(neck, -0.60f * windK, 0f, 0f);
            rot(head, -0.55f * windK + 0.30f * strikeK, 0f, 0f);
            rot(jaw, 0.30f * windK + 0.65f * strikeK, 0f, 0f);
            rot(wingL, 0f, 0f, -0.90f * windK);
            rot(wingR, 0f, 0f, 0.90f * windK);
            shift(body, 0f, 0.16f * windK, 0f);
        }
    }

    // ---------------------------------------------------------------- mariner

    private void animateMariner(float windK, float strikeK, WorldBoss.Move move) {
        float t = boss.animTime;
        shift(body, 0f, FastMath.sin(t * 1.7f) * 0.07f, 0f);
        rot(chest, FastMath.sin(t * 1.7f + 0.4f) * 0.04f, 0f, 0f);
        rot(head, FastMath.sin(t * 0.8f) * 0.04f, FastMath.sin(t * 0.55f) * 0.14f, 0f);
        rot(armL, -0.25f, 0f, 0f);
        rot(elbowL, -0.40f, 0f, 0f);
        rot(jaw, FastMath.sin(t * 0.6f) * 0.05f, 0f, 0f);
        // windK 在 STRIKE 段保持为 1，划桨必须用两者的和，不能再用二选一
        float row = (0.10f + FastMath.sin(t * 1.1f) * 0.08f) * (1f - windK)
                - 1.15f * windK + 1.05f * strikeK;
        rot(armR, row, 0f, 0f);
        rot(elbowR, -0.62f * windK + 0.20f * strikeK, 0f, 0f);
        rot(weapon, 0.70f * windK - 0.90f * strikeK, 0f, 0f);
        if (move != null && named(move, "raise the dead", "spectral horn")) {
            rot(armL, -1.60f * windK + 0.30f * strikeK, 0f, 0.65f * windK);
            rot(head, -0.35f * windK, 0f, 0f);
        }
        if (move != null && named(move, "tide step", "")) {
            float flick = FastMath.sin(boss.animTime * 22f);
            rot(chest, 0f, flick * 0.10f, 0f);
        }
    }

    // ---------------------------------------------------------------- beast

    private void animateBeast(float windK, float strikeK, WorldBoss.Move move) {
        animateHumanoid(windK, strikeK, move);
        shift(body, 0f, -0.08f, 0f);
        rot(neck, -0.12f, 0f, 0f);
        if (move != null && move.kind == WorldBoss.MoveKind.FLURRY) {
            float chop = FastMath.sin(boss.stateTime * 34f) * 0.85f * strikeK;
            rot(armL, -1.25f * windK + chop, 0f, 0.42f);
            rot(armR, -1.25f * windK - chop, 0f, -0.42f);
            rot(elbowL, -0.75f * windK, 0f, 0f);
            rot(elbowR, -0.75f * windK, 0f, 0f);
            shift(body, 0f, -0.10f * windK, 0.18f * strikeK);
        }
    }

    // ---------------------------------------------------------------- utility poses

    /** 失衡跪地：depth 1 = 单膝跪地，>1 = 处决后的倒地。 */
    private void slump(float t, float depth) {
        float s = FastMath.sin(t * 14f) * Math.max(0f, 1f - t * 0.6f);
        shift(body, 0f, -0.30f * depth, 0.10f * depth);
        rot(chest, 0.55f * depth, 0f, s * 0.10f);
        rot(neck, 0.62f * depth, s * 0.12f, 0f);
        rot(head, 0.42f * depth, s * 0.10f, 0f);
        rot(jaw, 0.20f * depth, 0f, 0f);
        rot(armL, 0.50f * depth, 0f, 0.45f * depth);
        rot(armR, 0.50f * depth, 0f, -0.45f * depth);
        rot(elbowL, 0.75f * depth, 0f, 0f);
        rot(elbowR, 0.75f * depth, 0f, 0f);
        rot(legL, -0.55f * depth, 0f, 0f);
        rot(legR, 0.15f * depth, 0f, 0f);
        rot(kneeL, 1.05f * depth, 0f, 0f);
        rot(kneeR, 0.40f * depth, 0f, 0f);
        rot(weapon, 0.55f * depth, 0f, 0f);
        for (int i = 0; i < tailChain.length; i++) {
            rot(tailChain[i], 0.18f * depth, 0f, 0f);
        }
        for (int i = 0; i < neckChain.length; i++) {
            rot(neckChain[i], 0.20f * depth, 0f, 0f);
        }
        rot(treeTrunk, 0.30f * depth, 0f, 0.10f * depth);
        rot(treeTop, 0.25f * depth, 0f, 0f);
        rot(horseBody, 0.20f * depth, 0f, 0f);
    }

    private void poseDeath(float k) {
        if (k <= 0f) {
            return;
        }
        shift(body, 0f, -0.35f * k, 0.20f * k);
        rot(chest, 0.55f * k, 0f, 0.25f * k);
        rot(neck, 0.55f * k, 0f, 0f);
        rot(head, 0.45f * k, 0.25f * k, 0f);
        rot(jaw, 0.35f * k, 0f, 0f);
        rot(armL, 0.90f * k, 0f, 0.45f * k);
        rot(armR, 1.05f * k, 0f, -0.35f * k);
        rot(elbowL, 0.60f * k, 0f, 0f);
        rot(elbowR, 0.75f * k, 0f, 0f);
        rot(legL, -0.55f * k, 0f, 0.15f * k);
        rot(legR, -0.45f * k, 0f, -0.15f * k);
        rot(kneeL, 0.90f * k, 0f, 0f);
        rot(kneeR, 0.80f * k, 0f, 0f);
        for (int i = 0; i < horseLegs.length; i++) {
            rot(horseLegs[i], (i < 2 ? -0.90f : 0.35f) * k, 0f, 0f);
            rot(horseKnees[i], (i < 2 ? 1.40f : -0.90f) * k, 0f, 0f);
        }
        rot(horseNeck, 0.55f * k, 0f, 0f);
        rot(horseBody, 0.35f * k, 0f, 0.20f * k);
        for (int i = 0; i < 4; i++) {
            rot(dragonLegs[i], (i < 2 ? -0.70f : 0.55f) * k, 0f, 0f);
            rot(dragonKnees[i], (i < 2 ? 1.30f : -0.95f) * k, 0f, 0f);
        }
        rot(wingL, 0f, 0f, 0.90f * k);
        rot(wingR, 0f, 0f, -0.90f * k);
        for (int i = 0; i < wingArmL.length; i++) {
            rot(wingArmL[i], 0f, 0f, 0.30f * k);
            rot(wingArmR[i], 0f, 0f, -0.30f * k);
        }
        for (int i = 0; i < tailChain.length; i++) {
            rot(tailChain[i], 0.25f * k, 0f, 0f);
        }
        for (int i = 0; i < neckChain.length; i++) {
            rot(neckChain[i], 0.30f * k, 0f, 0f);
        }
        rot(treeTrunk, 0.45f * k, 0f, 0.18f * k);
        rot(treeTop, 0.30f * k, 0f, 0f);
        rot(crown, 0.20f * k, 0f, 0.25f * k);
        for (int i = 0; i < rootArmL.length; i++) {
            rot(rootArmL[i], 0.20f * k, 0f, 0.30f * k);
            rot(rootArmR[i], 0.20f * k, 0f, -0.30f * k);
        }
        for (int i = 0; i < cape.length; i++) {
            rot(cape[i], 0.35f * k, 0f, 0f);
        }
    }
}
