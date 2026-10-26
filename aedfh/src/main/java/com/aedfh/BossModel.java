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
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Sphere;

/** Procedural silhouettes for the nine Limgrave encounters. */
final class BossModel {

    private final WorldBoss boss;
    private final Node root = new Node("bossModel");
    private final Node body = new Node("body");
    private final Node torso = new Node("torso");
    private final Node head = new Node("head");
    private final Node armL = new Node("armL");
    private final Node armR = new Node("armR");
    private final Node elbowL = new Node("elbowL");
    private final Node elbowR = new Node("elbowR");
    private final Node legL = new Node("legL");
    private final Node legR = new Node("legR");
    private final Node kneeL = new Node("kneeL");
    private final Node kneeR = new Node("kneeR");
    private final Node weapon = new Node("weapon");
    private final Node tail = new Node("tail");
    private final Node wingL = new Node("wingL");
    private final Node wingR = new Node("wingR");
    private final Node extra = new Node("extra");
    private final Vector3f bodyBase = new Vector3f(0f, 1.02f, 0f);
    private final Node[] horseLegs = new Node[4];
    private final Geometry[] glow = new Geometry[1];
    private final ColorRGBA tellColor = new ColorRGBA(1f, 0.6f, 0.2f, 1f);
    private Geometry hitGlow;
    private Geometry aura;
    private final PointLight glowLight = new PointLight();

    private final AssetManager am;
    private final float scale;
    private final Quaternion q = new Quaternion();
    private final Vector3f tmp = new Vector3f();
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
        body.setLocalTranslation(bodyBase);
        body.setLocalRotation(Quaternion.IDENTITY);
        torso.setLocalRotation(Quaternion.IDENTITY);
        head.setLocalRotation(Quaternion.IDENTITY);
        armL.setLocalRotation(Quaternion.IDENTITY);
        armR.setLocalRotation(Quaternion.IDENTITY);
        legL.setLocalRotation(Quaternion.IDENTITY);
        legR.setLocalRotation(Quaternion.IDENTITY);
        if (aura != null) {
            aura.setCullHint(Spatial.CullHint.Always);
        }
        root.setLocalRotation(new Quaternion().fromAngleAxis(boss.yaw, Vector3f.UNIT_Y));
    }

    void enrage() {
        if (aura == null) {
            aura = Gfx.attach(new Geometry("bossAura", Gfx.torusMesh(1.6f, 0.045f, 64, 8)),
                    root, 0f, 0.08f, 0f);
            Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
            m.setColor("Color", new ColorRGBA(1f, 0.55f, 0.12f, 0.7f));
            m.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.AlphaAdditive);
            m.getAdditionalRenderState().setDepthWrite(false);
            m.getAdditionalRenderState().setFaceCullMode(com.jme3.material.RenderState.FaceCullMode.Off);
            aura.setMaterial(m);
            aura.setQueueBucket(RenderQueue.Bucket.Transparent);
            aura.setShadowMode(RenderQueue.ShadowMode.Off);
        }
        aura.setCullHint(Spatial.CullHint.Never);
        aura.setLocalScale(boss.radius() * 0.9f, 1f, boss.radius() * 0.9f);
    }

    // ---------------------------------------------------------------- build

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
        Gfx.tangents(root);
        bodyBase.set(body.getLocalTranslation());
        buildGlow();
    }

    private void buildHumanoid(float limbScale, ColorRGBA armorColor, ColorRGBA clothColor,
                               boolean horned, boolean plate) {
        Material armor = Gfx.lit(am, armorColor, plate ? armorColor.mult(0.85f)
                        : armorColor.mult(1.1f),
                new ColorRGBA(0.36f, 0.34f, 0.28f, 1f), plate ? 28f : 44f);
        if (plate && boss.spec.archetype != WorldBoss.Archetype.CAVALRY) {
            Gfx.mapped(armor, am, "metal_plate", 0.7f);
        }
        Material cloth = Gfx.lit(am, clothColor, clothColor.mult(0.75f),
                ColorRGBA.Black, 2f);
        Material skin = Gfx.lit(am, new ColorRGBA(0.54f, 0.43f, 0.34f, 1f),
                new ColorRGBA(0.14f, 0.1f, 0.08f, 1f), ColorRGBA.Black, 2f);

        body.setLocalTranslation(0f, 1.02f * limbScale, 0f);
        torso.setLocalTranslation(0f, 0.10f, 0f);
        body.attachChild(torso);
        Gfx.box("hip", 0.20f, 0.14f, 0.15f, cloth, body, 0f, -0.03f, 0f);
        Gfx.box("chest", 0.26f, 0.31f, 0.18f, armor, torso, 0f, 0.30f, 0f);
        Gfx.box("skirt", 0.29f, 0.22f, 0.19f, cloth, torso, 0f, -0.06f, 0.02f);
        if (horned) {
            Gfx.box("cape", 0.34f, 0.42f, 0.03f, cloth, torso, 0f, 0.12f, -0.19f);
        }

        head.setLocalTranslation(0f, 0.72f, 0.02f);
        torso.attachChild(head);
        Gfx.sphere("head", 0.155f, 10, skin, head, 0f, 0f, 0f);
        if (horned) {
            Material goldMat = Gfx.lit(am, new ColorRGBA(0.78f, 0.62f, 0.28f, 1f),
                    new ColorRGBA(0.22f, 0.16f, 0.06f, 1f),
                    new ColorRGBA(0.8f, 0.7f, 0.4f, 1f), 48f);
            Gfx.cylinder("hornL", 0.045f, 0.40f, goldMat, head, -0.13f, 0.16f, 0f)
                    .rotate(0f, 0f, 0.55f);
            Gfx.cylinder("hornR", 0.045f, 0.40f, goldMat, head, 0.13f, 0.16f, 0f)
                    .rotate(0f, 0f, -0.55f);
            Gfx.box("visor", 0.13f, 0.035f, 0.02f, goldMat, head, 0f, 0.01f, 0.15f);
        } else {
            Gfx.box("helm", 0.18f, 0.10f, 0.18f, armor, head, 0f, 0.08f, 0f);
            Gfx.box("crest", 0.035f, 0.16f, 0.20f, cloth, head, 0f, 0.22f, -0.02f);
        }

        armL.setLocalTranslation(-0.32f, 0.56f, 0f);
        armR.setLocalTranslation(0.32f, 0.56f, 0f);
        torso.attachChild(armL);
        torso.attachChild(armR);
        for (Node arm : new Node[]{armL, armR}) {
            Gfx.box("pauldron", 0.13f, 0.11f, 0.14f, armor, arm, 0f, 0.03f, 0f);
            Gfx.box("upper", 0.085f, 0.21f, 0.09f, armor, arm, 0f, -0.20f, 0f);
            Gfx.box("fore", 0.075f, 0.20f, 0.08f, cloth, arm, 0f, -0.58f, 0.02f);
            Gfx.sphere("hand", 0.085f, 6, skin, arm, 0f, -0.80f, 0.02f);
        }

        legL.setLocalTranslation(-0.145f, 0f, 0f);
        legR.setLocalTranslation(0.145f, 0f, 0f);
        body.attachChild(legL);
        body.attachChild(legR);
        for (Node leg : new Node[]{legL, legR}) {
            Gfx.box("thigh", 0.11f, 0.28f, 0.12f, cloth, leg, 0f, -0.30f, 0f);
            Gfx.box("shin", 0.095f, 0.27f, 0.10f, armor, leg, 0f, -0.78f, 0.01f);
            Gfx.box("boot", 0.11f, 0.07f, 0.18f, armor, leg, 0f, -1.03f, 0.06f);
        }

        weapon.setLocalTranslation(0f, -0.78f, 0.07f);
        armR.attachChild(weapon);
    }

    private void buildMounted() {
        boolean gold = boss.spec.archetype == WorldBoss.Archetype.SENTINEL;
        buildHumanoid(1.16f,
                gold ? new ColorRGBA(0.60f, 0.44f, 0.18f, 1f)
                        : new ColorRGBA(0.20f, 0.21f, 0.29f, 1f),
                gold ? new ColorRGBA(0.22f, 0.30f, 0.17f, 1f)
                        : new ColorRGBA(0.10f, 0.10f, 0.16f, 1f),
                gold, !gold);
        body.setLocalTranslation(0f, 2.06f, 0.15f);
        body.setLocalScale(1.06f);
        legL.setLocalTranslation(-0.18f, 0.12f, 0.06f);
        legR.setLocalTranslation(0.18f, 0.12f, 0.06f);
        legL.setLocalScale(0.32f);
        legR.setLocalScale(0.32f);
        legL.setCullHint(Spatial.CullHint.Always);
        legR.setCullHint(Spatial.CullHint.Always);
        if (gold) {
            Material goldSteel = Gfx.lit(am, new ColorRGBA(0.74f, 0.57f, 0.22f, 1f),
                    new ColorRGBA(0.26f, 0.18f, 0.06f, 1f),
                    new ColorRGBA(1f, 0.9f, 0.55f, 1f), 56f);
            Gfx.box("greatHelm", 0.19f, 0.17f, 0.19f, goldSteel, head, 0f, 0.07f, 0f);
            Gfx.box("facePlate", 0.14f, 0.11f, 0.04f, goldSteel, head, 0f, -0.05f, 0.17f);
            Gfx.box("helmRidge", 0.035f, 0.13f, 0.20f, goldSteel, head, 0f, 0.27f, -0.02f);
        }

        Material hide = Gfx.lit(am, gold ? new ColorRGBA(0.60f, 0.47f, 0.32f, 1f)
                        : new ColorRGBA(0.23f, 0.24f, 0.30f, 1f),
                gold ? new ColorRGBA(0.28f, 0.22f, 0.15f, 1f)
                        : new ColorRGBA(0.11f, 0.11f, 0.15f, 1f), ColorRGBA.Black, 3f);
        if (gold) {
            Gfx.mapped(hide, am, "brown_leather", 0.8f);
        }
        Material caparison = Gfx.lit(am, gold ? new ColorRGBA(0.20f, 0.28f, 0.13f, 1f)
                        : new ColorRGBA(0.13f, 0.14f, 0.21f, 1f),
                gold ? new ColorRGBA(0.09f, 0.13f, 0.06f, 1f)
                        : new ColorRGBA(0.06f, 0.07f, 0.11f, 1f),
                ColorRGBA.Black, 2f);

        Node horse = new Node("horse");
        root.attachChild(horse);
        Geometry barrel = Gfx.box("barrel", new Sphere(12, 14, 1f), hide, horse, 0f, 1.28f, 0.05f);
        barrel.setLocalScale(0.66f, 0.62f, 1.25f);
        Gfx.box("saddle", 0.42f, 0.12f, 0.52f, caparison, horse, 0f, 1.78f, -0.05f);
        Gfx.box("cloth", 0.55f, 0.45f, 0.62f, caparison, horse, 0f, 1.42f, 0.04f);

        Material maneMat = Gfx.lit(am, new ColorRGBA(0.18f, 0.15f, 0.12f, 1f),
                new ColorRGBA(0.10f, 0.08f, 0.05f, 1f), ColorRGBA.Black, 2f);
        Node neck = new Node("neck");
        neck.setLocalTranslation(0f, 1.62f, 0.92f);
        horse.attachChild(neck);
        neck.setLocalRotation(new Quaternion().fromAngleAxis(0.42f, Vector3f.UNIT_X));
        Gfx.taperedCylinder("neckMesh", 0.14f, 0.21f, 0.86f, hide, neck, 0f, 0.34f, 0f);
        Node hhead = new Node("horseHead");
        hhead.setLocalTranslation(0f, 0.84f, 0f);
        neck.attachChild(hhead);
        hhead.setLocalRotation(new Quaternion().fromAngleAxis(-0.26f, Vector3f.UNIT_X));
        Gfx.box("skull", 0.22f, 0.23f, 0.34f, hide, hhead, 0f, 0.02f, 0.13f);
        Gfx.box("muzzle", 0.145f, 0.13f, 0.24f, hide, hhead, 0f, -0.07f, 0.42f);
        Gfx.box("nostril", 0.07f, 0.05f, 0.03f, maneMat, hhead, 0f, -0.08f, 0.63f);
        for (int side = -1; side <= 1; side += 2) {
            Gfx.taperedCylinder("ear", 0.025f, 0.11f, 0.28f, hide, hhead,
                    side * 0.13f, 0.28f, -0.07f).rotate(-0.35f, 0f, side * 0.28f);
        }
        Gfx.box("forelock", 0.09f, 0.14f, 0.16f, maneMat, hhead, 0f, 0.20f, 0.20f);
        Gfx.box("mane", 0.075f, 0.34f, 0.46f, maneMat, neck, 0f, 0.44f, -0.20f);

        float[] lx = {-0.36f, 0.36f, -0.36f, 0.36f};
        float[] lz = {0.72f, 0.72f, -0.66f, -0.66f};
        for (int i = 0; i < 4; i++) {
            Node leg = new Node("horseLeg" + i);
            leg.setLocalTranslation(lx[i], 1.12f, lz[i]);
            horse.attachChild(leg);
            Gfx.box("upper", 0.11f, 0.34f, 0.12f, hide, leg, 0f, -0.30f, 0f);
            Gfx.box("lower", 0.075f, 0.32f, 0.085f, hide, leg, 0f, -0.86f, 0f);
            Gfx.box("hoof", 0.10f, 0.07f, 0.15f, maneMat, leg, 0f, -1.16f, 0.03f);
            horseLegs[i] = leg;
        }
        Node htail = new Node("horseTail");
        htail.setLocalTranslation(0f, 1.62f, -1.16f);
        horse.attachChild(htail);
        Gfx.box("tail", 0.13f, 0.46f, 0.12f, maneMat, htail, 0f, -0.42f, -0.10f);
        Gfx.box("tailTip", 0.09f, 0.26f, 0.08f, maneMat, htail, 0f, -0.96f, -0.16f);
        htail.setLocalRotation(new Quaternion().fromAngleAxis(-0.18f, Vector3f.UNIT_X));

        if (gold) {
            Gfx.box("halberdShaft", 0.045f, 1.55f, 0.045f,
                    Gfx.lit(am, new ColorRGBA(0.35f, 0.25f, 0.13f, 1f),
                            new ColorRGBA(0.1f, 0.07f, 0.03f, 1f), ColorRGBA.Black, 8f),
                    weapon, 0f, 0.55f, 0f);
            Material goldMat = Gfx.lit(am, new ColorRGBA(0.84f, 0.66f, 0.25f, 1f),
                    new ColorRGBA(0.25f, 0.17f, 0.05f, 1f),
                    new ColorRGBA(1f, 0.9f, 0.5f, 1f), 64f);
            Gfx.box("axe", 0.34f, 0.20f, 0.035f, goldMat, weapon, 0.20f, 1.95f, 0f);
            Gfx.cylinder("spike", 0.05f, 0.55f, goldMat, weapon, 0f, 2.26f, 0f);
        } else {
            Gfx.box("shaft", 0.04f, 1.30f, 0.04f,
                    Gfx.lit(am, new ColorRGBA(0.14f, 0.14f, 0.16f, 1f),
                            new ColorRGBA(0.05f, 0.05f, 0.06f, 1f), ColorRGBA.Black, 8f),
                    weapon, 0f, 0.15f, 0f);
            Gfx.box("blade", 0.26f, 0.09f, 0.03f,
                    Gfx.lit(am, new ColorRGBA(0.55f, 0.57f, 0.61f, 1f),
                            new ColorRGBA(0.1f, 0.1f, 0.12f, 1f),
                            new ColorRGBA(0.8f, 0.8f, 0.9f, 1f), 64f),
                    weapon, 0.20f, 1.30f, 0f);
            Gfx.box("butt", 0.055f, 0.08f, 0.055f,
                    Gfx.lit(am, new ColorRGBA(0.34f, 0.28f, 0.16f, 1f),
                            new ColorRGBA(0.08f, 0.06f, 0.03f, 1f), ColorRGBA.Black, 16f),
                    weapon, 0f, -1.13f, 0f);
        }
        weapon.setLocalRotation(new Quaternion().fromAngleAxis(-0.10f, Vector3f.UNIT_Z));
    }

    private void buildMargit() {
        Material cloak = Gfx.lit(am, new ColorRGBA(0.13f, 0.125f, 0.11f, 1f),
                new ColorRGBA(0.30f, 0.27f, 0.22f, 1f), ColorRGBA.Black, 3f);
        Material robe = Gfx.lit(am, new ColorRGBA(0.24f, 0.19f, 0.13f, 1f),
                new ColorRGBA(0.32f, 0.29f, 0.24f, 1f), ColorRGBA.Black, 4f);
        Material trim = Gfx.lit(am, new ColorRGBA(0.58f, 0.44f, 0.17f, 1f),
                new ColorRGBA(0.11f, 0.075f, 0.02f, 1f),
                new ColorRGBA(0.95f, 0.80f, 0.42f, 1f), 48f);
        Material skin = Gfx.lit(am, new ColorRGBA(0.42f, 0.36f, 0.30f, 1f),
                new ColorRGBA(0.38f, 0.34f, 0.28f, 1f), ColorRGBA.Black, 2f);
        Material hornMat = Gfx.lit(am, new ColorRGBA(0.60f, 0.53f, 0.41f, 1f),
                new ColorRGBA(0.42f, 0.38f, 0.32f, 1f),
                new ColorRGBA(0.52f, 0.45f, 0.32f, 1f), 16f);
        Material hairMat = Gfx.lit(am, new ColorRGBA(0.46f, 0.43f, 0.38f, 1f),
                new ColorRGBA(0.40f, 0.36f, 0.31f, 1f), ColorRGBA.Black, 2f);

        body.setLocalTranslation(0f, 1.50f, 0f);
        body.setLocalRotation(new Quaternion().fromAngleAxis(0.08f, Vector3f.UNIT_X));
        torso.setLocalTranslation(0f, 0.12f, 0f);
        body.attachChild(torso);

        legL.setLocalTranslation(-0.235f, 0f, 0f);
        legR.setLocalTranslation(0.235f, 0f, 0f);
        body.attachChild(legL);
        body.attachChild(legR);
        for (Node leg : new Node[]{legL, legR}) {
            Gfx.box("thigh", 0.13f, 0.36f, 0.15f, robe, leg, 0f, -0.38f, 0f);
            Gfx.box("shin", 0.11f, 0.36f, 0.125f, cloak, leg, 0f, -1.04f, 0.03f);
            Gfx.box("foot", 0.145f, 0.08f, 0.27f, hairMat, leg, 0f, -1.42f, 0.10f);
            Gfx.box("toe", 0.12f, 0.05f, 0.08f, skin, leg, 0f, -1.42f, 0.34f);
        }

        Gfx.box("pelvis", 0.28f, 0.20f, 0.21f, robe, body, 0f, 0.02f, 0f);
        Gfx.box("belt", 0.29f, 0.06f, 0.22f, trim, body, 0f, 0.20f, 0f);
        Gfx.box("chest", 0.34f, 0.34f, 0.235f, robe, torso, 0f, 0.44f, 0.01f);
        Gfx.box("plate", 0.26f, 0.20f, 0.05f, robe, torso, 0f, 0.50f, 0.24f);
        Gfx.box("trimL", 0.03f, 0.30f, 0.02f, trim, torso, -0.19f, 0.46f, 0.245f);
        Gfx.box("trimR", 0.03f, 0.30f, 0.02f, trim, torso, 0.19f, 0.46f, 0.245f);

        var cape = Gfx.box("cape", 0.40f, 0.62f, 0.035f, cloak, torso, 0f, 0.02f, -0.26f);
        cape.rotate(-0.06f, 0f, 0f);
        var capeLow = Gfx.box("capeLow", 0.46f, 0.60f, 0.03f, cloak, torso, 0f, -0.86f, -0.34f);
        capeLow.rotate(0.10f, 0f, 0f);
        for (int i = 0; i < 5; i++) {
            float len = 0.14f + (i % 3) * 0.07f;
            Gfx.box("rag", 0.075f, len, 0.02f, cloak, torso,
                    (i - 2) * 0.18f, -1.48f - len * 0.4f, -0.38f);
        }
        Gfx.box("collar", 0.30f, 0.10f, 0.22f, hairMat, torso, 0f, 0.78f, 0f);
        Gfx.box("mantle", 0.40f, 0.09f, 0.26f, robe, torso, 0f, 0.68f, 0.02f);

        head.setLocalTranslation(0f, 0.96f, 0.06f);
        torso.attachChild(head);
        var skull = Gfx.sphere("head", 0.22f, 12, skin, head, 0f, 0f, 0f);
        skull.setLocalScale(1f, 1.10f, 1.02f);
        Gfx.box("brow", 0.18f, 0.035f, 0.04f, skin, head, 0f, 0.07f, 0.19f);
        Gfx.box("hairBack", 0.24f, 0.28f, 0.13f, hairMat, head, 0f, -0.04f, -0.13f);
        Material eyeMat = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        eyeMat.setColor("Color", new ColorRGBA(1f, 0.72f, 0.25f, 1f));
        Gfx.sphere("eyeL", 0.024f, 8, eyeMat, head, -0.075f, 0.02f, 0.205f);
        Gfx.sphere("eyeR", 0.024f, 8, eyeMat, head, 0.075f, 0.02f, 0.205f);

        var beard = Gfx.taperedCylinder("beard", 0.10f, 0.03f, 0.52f, hairMat,
                head, 0f, -0.36f, 0.13f);
        beard.rotate(0.10f, 0f, 0f);
        for (int side = -1; side <= 1; side += 2) {
            var strand = Gfx.taperedCylinder("beardSide", 0.035f, 0.012f, 0.34f, hairMat,
                    head, side * 0.13f, -0.28f, 0.10f);
            strand.rotate(0.10f, 0f, side * 0.14f);
        }

        // Horns are a three-segment chain laid along an outward-then-up arc.
        float[] hornAng = {1.13f, 0.68f, 0.21f};
        float[] hornLen = {0.32f, 0.34f, 0.36f};
        float[] hornRad = {0.078f, 0.060f, 0.040f, 0.004f};
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
                // The cylinder is already laid on Y; compose the arc tilt in parent space.
                seg.setLocalRotation(new Quaternion()
                        .fromAngleAxis(side * -hornAng[i], Vector3f.UNIT_Z)
                        .mult(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X)));
                px += dx * hornLen[i];
                py += dy * hornLen[i];
            }
        }

        armL.setLocalTranslation(-0.42f, 0.60f, 0f);
        armR.setLocalTranslation(0.42f, 0.60f, 0f);
        torso.attachChild(armL);
        torso.attachChild(armR);
        for (Node arm : new Node[]{armL, armR}) {
            int side = arm == armL ? -1 : 1;
            Gfx.box("pauldron", 0.17f, 0.12f, 0.19f, cloak, arm, side * 0.02f, 0.06f, 0f);
            Gfx.box("upper", 0.10f, 0.30f, 0.11f, robe, arm, 0f, -0.28f, 0f);
            Gfx.box("fore", 0.09f, 0.30f, 0.10f, skin, arm, 0f, -0.74f, 0.01f);
            Gfx.sphere("hand", 0.10f, 8, skin, arm, 0f, -0.98f, 0.02f);
            for (int f = -1; f <= 1; f++) {
                Gfx.box("claw", 0.018f, 0.10f, 0.018f, hornMat, arm, f * 0.05f, -1.07f, 0.09f);
            }
        }

        weapon.setLocalTranslation(0f, -0.96f, 0.05f);
        armR.attachChild(weapon);
        Material wood = Gfx.lit(am, new ColorRGBA(0.22f, 0.16f, 0.10f, 1f),
                new ColorRGBA(0.04f, 0.03f, 0.02f, 1f), ColorRGBA.Black, 6f);
        Gfx.cylinder("staff", 0.045f, 2.5f, wood, weapon, 0f, 0.25f, 0f);
        Gfx.cylinder("ferrule", 0.06f, 0.10f, trim, weapon, 0f, 1.30f, 0f);
        Gfx.cylinder("butt", 0.055f, 0.14f, trim, weapon, 0f, -0.92f, 0f);
        var ring = Gfx.torus("staffRing", 0.15f, 0.026f, trim, weapon, 0f, 1.62f, 0f);
        ring.rotate(FastMath.HALF_PI, 0f, 0f);
        Gfx.box("staffBlade", 0.032f, 0.26f, 0.012f, trim, weapon, 0f, 1.94f, 0f);

        Gfx.box("grip", 0.03f, 0.14f, 0.03f, hairMat, armL, 0f, -1.00f, 0.08f);
        Gfx.box("guard", 0.12f, 0.02f, 0.03f, trim, armL, 0f, -0.87f, 0.08f);
        Gfx.box("blade", 0.035f, 0.36f, 0.012f, trim, armL, 0f, -1.22f, 0.08f);

        var tail1 = Gfx.taperedCylinder("tail1", 0.10f, 0.13f, 0.55f, skin, body, 0f, -0.42f, -0.28f);
        tail1.rotate(-1.05f, 0f, 0f);
        var tail2 = Gfx.taperedCylinder("tail2", 0.035f, 0.10f, 0.55f, skin, body, 0f, -0.56f, -0.72f);
        tail2.rotate(-1.28f, 0f, 0f);
        var tail3 = Gfx.taperedCylinder("tail3", 0.004f, 0.035f, 0.36f, hornMat, body, 0f, -0.68f, -1.12f);
        tail3.rotate(-1.45f, 0f, 0f);
    }

    /** Godrick: a hulking grafted king, crown and cleaver of gold on raw flesh. */
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

        body.setLocalTranslation(0f, 1.34f, 0f);
        body.setLocalRotation(new Quaternion().fromAngleAxis(0.10f, Vector3f.UNIT_X));
        torso.setLocalTranslation(0f, 0.14f, 0f);
        body.attachChild(torso);

        legL.setLocalTranslation(-0.30f, -0.14f, 0f);
        legR.setLocalTranslation(0.30f, -0.14f, 0f);
        body.attachChild(legL);
        body.attachChild(legR);
        for (Node leg : new Node[]{legL, legR}) {
            Gfx.box("thigh", 0.21f, 0.34f, 0.23f, flesh, leg, 0f, -0.34f, 0f);
            Gfx.box("shin", 0.17f, 0.30f, 0.19f, flesh, leg, 0f, -1.00f, 0.02f);
            Gfx.box("foot", 0.20f, 0.09f, 0.30f, iron, leg, 0f, -1.20f, 0.09f);
        }

        Gfx.box("pelvis", 0.34f, 0.19f, 0.26f, cloth, body, 0f, -0.12f, 0f);
        Gfx.box("belt", 0.36f, 0.07f, 0.27f, graft, body, 0f, 0.04f, 0f);
        Gfx.box("chest", 0.46f, 0.34f, 0.32f, flesh, torso, 0f, 0.36f, 0.02f);
        Gfx.box("hump", 0.50f, 0.30f, 0.26f, flesh, torso, 0f, 0.54f, -0.30f);
        Gfx.box("collar", 0.34f, 0.10f, 0.24f, graft, torso, 0f, 0.64f, -0.02f);

        // The grafted arms sprouting from the hump.
        for (int i = 0; i < 4; i++) {
            float side = i < 2 ? -1f : 1f;
            float row = i % 2;
            Node graftArm = new Node("graftArm" + i);
            graftArm.setLocalTranslation(side * (0.22f + row * 0.14f),
                    0.40f + row * 0.26f, -0.34f);
            graftArm.setLocalRotation(new Quaternion().fromAngleAxis(
                    side * (0.35f + row * 0.2f), Vector3f.UNIT_Z));
            torso.attachChild(graftArm);
            Gfx.box("graftUpper", 0.07f, 0.20f, 0.08f, flesh, graftArm, 0f, -0.20f, 0f);
            Gfx.box("graftFore", 0.055f, 0.18f, 0.06f, graft, graftArm, 0f, -0.56f, 0.02f);
            Gfx.sphere("graftHand", 0.065f, 6, graft, graftArm, 0f, -0.74f, 0.03f);
        }

        head.setLocalTranslation(0f, 0.60f, 0.17f);
        torso.attachChild(head);
        var skull = Gfx.sphere("head", 0.17f, 12, flesh, head, 0f, 0f, 0f);
        skull.setLocalScale(1f, 1.05f, 1.05f);
        Gfx.box("brow", 0.15f, 0.03f, 0.04f, flesh, head, 0f, 0.06f, 0.16f);
        Gfx.box("jaw", 0.11f, 0.07f, 0.12f, flesh, head, 0f, -0.13f, 0.08f);
        Material eyeMat = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        eyeMat.setColor("Color", new ColorRGBA(1f, 0.55f, 0.18f, 1f));
        Gfx.sphere("eyeL", 0.022f, 8, eyeMat, head, -0.06f, 0.01f, 0.17f);
        Gfx.sphere("eyeR", 0.022f, 8, eyeMat, head, 0.06f, 0.01f, 0.17f);
        // The grafted golden crown.
        Gfx.box("crownBand", 0.15f, 0.045f, 0.15f, graft, head, 0f, 0.14f, 0f);
        for (int i = 0; i < 5; i++) {
            float a = i / 5f * FastMath.TWO_PI;
            Gfx.taperedCylinder("crownSpike", 0.004f, 0.028f, 0.20f + (i % 2) * 0.07f,
                    graft, head, FastMath.cos(a) * 0.12f, 0.25f, FastMath.sin(a) * 0.12f);
        }

        armL.setLocalTranslation(-0.52f, 0.44f, 0f);
        armR.setLocalTranslation(0.52f, 0.44f, 0f);
        torso.attachChild(armL);
        torso.attachChild(armR);
        for (Node arm : new Node[]{armL, armR}) {
            Gfx.box("pauldron", 0.19f, 0.14f, 0.20f, graft, arm, 0f, 0.04f, 0f);
            Gfx.box("upper", 0.15f, 0.30f, 0.16f, flesh, arm, 0f, -0.28f, 0f);
            Gfx.box("fore", 0.14f, 0.28f, 0.15f, flesh, arm, 0f, -0.80f, 0.02f);
            Gfx.sphere("hand", 0.15f, 8, flesh, arm, 0f, -1.10f, 0.03f);
        }

        weapon.setLocalTranslation(0f, -1.05f, 0.06f);
        armR.attachChild(weapon);
        Gfx.box("haft", 0.055f, 0.78f, 0.055f, iron, weapon, 0f, 0.35f, 0f);
        Gfx.box("axeHead", 0.42f, 0.26f, 0.075f, stone, weapon, 0.30f, 1.12f, 0f);
        Gfx.box("axeEdge", 0.10f, 0.27f, 0.08f, graft, weapon, 0.62f, 1.12f, 0f);
        Gfx.box("axeCollar", 0.10f, 0.10f, 0.10f, iron, weapon, 0.06f, 1.12f, 0f);
        weapon.setLocalRotation(new Quaternion().fromAngleAxis(-0.12f, Vector3f.UNIT_Z));
    }

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
        Gfx.box("guard", 0.30f, 0.045f, 0.055f, blade, weapon, 0f, 0.14f, 0f);
        weapon.setLocalRotation(new Quaternion().fromAngleAxis(-0.26f, Vector3f.UNIT_Z));

        Material brass = Gfx.lit(am, new ColorRGBA(0.78f, 0.60f, 0.24f, 1f),
                new ColorRGBA(0.30f, 0.21f, 0.07f, 1f),
                new ColorRGBA(1f, 0.88f, 0.5f, 1f), 56f);
        Node bell = new Node("hunterBell");
        bell.setLocalTranslation(0.36f, 0.26f, 0.10f);
        torso.attachChild(bell);
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
        torso.setLocalRotation(new Quaternion().fromAngleAxis(0.45f, Vector3f.UNIT_X));
        Material fur = Gfx.lit(am, new ColorRGBA(0.36f, 0.29f, 0.24f, 1f),
                new ColorRGBA(0.16f, 0.12f, 0.09f, 1f), ColorRGBA.Black, 2f);
        Gfx.mapped(fur, am, "faux_fur", 0.9f);
        Gfx.box("mane", 0.30f, 0.28f, 0.14f, fur, torso, 0f, 0.52f, -0.12f);
        for (int side = -1; side <= 1; side += 2) {
            Node arm = side < 0 ? armL : armR;
            arm.setLocalTranslation(side * 0.34f, 0.58f, 0.08f);
            Gfx.box("clawUpper", 0.10f, 0.30f, 0.11f, fur, arm, 0f, -0.28f, 0.06f);
            Gfx.box("clawFore", 0.09f, 0.28f, 0.10f, fur, arm, 0f, -0.78f, 0.16f);
            Material claw = Gfx.lit(am, new ColorRGBA(0.72f, 0.68f, 0.58f, 1f),
                    new ColorRGBA(0.12f, 0.1f, 0.08f, 1f), ColorRGBA.Black, 16f);
            for (int c = -1; c <= 1; c++) {
                Gfx.box("claw", 0.025f, 0.16f, 0.025f, claw, arm,
                        c * 0.055f, -0.99f, 0.28f);
            }
        }
        legL.setLocalRotation(new Quaternion().fromAngleAxis(-0.22f, Vector3f.UNIT_X));
        legR.setLocalRotation(new Quaternion().fromAngleAxis(0.22f, Vector3f.UNIT_X));
    }

    private void buildDragon() {
        Material hide = Gfx.lit(am, new ColorRGBA(0.45f, 0.36f, 0.28f, 1f),
                new ColorRGBA(0.20f, 0.16f, 0.12f, 1f),
                new ColorRGBA(0.2f, 0.18f, 0.14f, 1f), 18f);
        Gfx.mapped(hide, am, "rock_wall", 0.8f);
        Material belly = Gfx.lit(am, new ColorRGBA(0.58f, 0.46f, 0.32f, 1f),
                new ColorRGBA(0.22f, 0.17f, 0.11f, 1f), ColorRGBA.Black, 4f);
        Gfx.mapped(belly, am, "faux_fur", 0.7f);
        Material horn = Gfx.lit(am, new ColorRGBA(0.68f, 0.60f, 0.48f, 1f),
                new ColorRGBA(0.15f, 0.13f, 0.10f, 1f), ColorRGBA.Black, 12f);

        Node dragonBody = new Node("dragonBody");
        root.attachChild(dragonBody);
        dragonBody.setLocalTranslation(0f, 3.0f, 0f);
        Gfx.box("barrel", new Sphere(14, 16, 1f), hide, dragonBody, 0f, 0f, 0f)
                .setLocalScale(1.45f, 1.30f, 2.65f);
        Gfx.box("belly", new Sphere(12, 14, 1f), belly, dragonBody, 0f, -0.55f, 0.1f)
                .setLocalScale(1.18f, 0.72f, 2.30f);
        for (int i = 0; i < 6; i++) {
            float z = -1.7f + i * 0.62f;
            float tall = 0.62f - Math.abs(i - 2.5f) * 0.09f;
            Gfx.taperedCylinder("spine", 0.01f, 0.18f, tall, horn, dragonBody,
                    0f, 1.16f + tall * 0.45f, z);
        }

        Node neck = new Node("dragonNeck");
        neck.setLocalTranslation(0f, 0.55f, 2.2f);
        dragonBody.attachChild(neck);
        for (int i = 0; i < 4; i++) {
            Node seg = new Node("neckSeg" + i);
            seg.setLocalTranslation(0f, 0.5f * i + 0.15f, 0.46f * i);
            neck.attachChild(seg);
            Gfx.box("seg", new Sphere(10, 12, 1f), hide, seg, 0f, 0f, 0f)
                    .setLocalScale(0.62f - i * 0.08f, 0.58f - i * 0.07f,
                            0.75f - i * 0.08f);
        }
        head.setLocalTranslation(0f, 1.75f, 1.6f);
        neck.attachChild(head);
        Gfx.box("skull", new Sphere(10, 12, 1f), hide, head, 0f, 0f, 0f)
                .setLocalScale(0.58f, 0.52f, 0.95f);
        Gfx.box("snout", 0.34f, 0.26f, 0.44f, belly, head, 0f, -0.08f, 0.66f);
        Gfx.box("jaw", 0.30f, 0.11f, 0.46f, belly, head, 0f, -0.30f, 0.62f);
        Gfx.cylinder("hornL", 0.075f, 0.62f, horn, head, -0.34f, 0.35f, -0.16f)
                .rotate(0.5f, 0f, 0.45f);
        Gfx.cylinder("hornR", 0.075f, 0.62f, horn, head, 0.34f, 0.35f, -0.16f)
                .rotate(0.5f, 0f, -0.45f);

        wingL.setLocalTranslation(-1.15f, 0.75f, 0.2f);
        wingR.setLocalTranslation(1.15f, 0.75f, 0.2f);
        dragonBody.attachChild(wingL);
        dragonBody.attachChild(wingR);
        for (int side = -1; side <= 1; side += 2) {
            Node wing = side < 0 ? wingL : wingR;
            Gfx.box("wingArm", 1.55f, 0.10f, 0.12f, hide, wing,
                    side * 1.45f, 0.08f, 0f).rotate(0f, 0f, side * 0.18f);
            Gfx.box("membrane", 1.55f, 0.045f, 1.15f, belly, wing,
                    side * 1.55f, -0.10f, -0.85f);
            Gfx.box("membrane2", 1.0f, 0.035f, 1.35f, belly, wing,
                    side * 1.05f, -0.24f, -1.25f);
        }

        Node dtail = new Node("dragonTail");
        dtail.setLocalTranslation(0f, -0.15f, -2.2f);
        dragonBody.attachChild(dtail);
        tail.setLocalTranslation(0f, 0f, 0f);
        dtail.attachChild(tail);
        for (int i = 0; i < 5; i++) {
            Node seg = new Node("tailSeg" + i);
            seg.setLocalTranslation(0f, -0.1f * i, -0.9f * i);
            tail.attachChild(seg);
            Gfx.box("seg", new Sphere(8, 10, 1f), hide, seg, 0f, 0f, 0f)
                    .setLocalScale(0.55f - i * 0.09f, 0.50f - i * 0.08f,
                            0.95f - i * 0.13f);
        }

        for (int side = -1; side <= 1; side += 2) {
            for (int front = 0; front < 2; front++) {
                Node leg = new Node("dragonLeg" + side + front);
                leg.setLocalTranslation(side * (front == 0 ? 1.05f : 1.18f),
                        front == 0 ? -0.55f : -0.35f, front == 0 ? 1.4f : -1.25f);
                dragonBody.attachChild(leg);
                Gfx.box("thigh", 0.30f, 0.62f, 0.34f, hide, leg, 0f, -0.35f, 0f);
                Gfx.box("shin", 0.22f, 0.58f, 0.25f, hide, leg, 0f, -0.96f, 0.1f);
                Gfx.box("foot", 0.30f, 0.14f, 0.48f, horn, leg, 0f, -1.30f, 0.2f);
            }
        }
        legL.setCullHint(Spatial.CullHint.Always);
        legR.setCullHint(Spatial.CullHint.Always);
        armL.setCullHint(Spatial.CullHint.Always);
        armR.setCullHint(Spatial.CullHint.Always);
        torso.setCullHint(Spatial.CullHint.Always);
        head.setCullHint(Spatial.CullHint.Never);
    }

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
        Gfx.box("hull", new Sphere(12, 14, 1f), hull, boat, 0f, 0.62f, 0f)
                .setLocalScale(1.25f, 0.40f, 3.1f);
        Gfx.box("rim", new Box(1.15f, 0.10f, 2.7f), hull, boat, 0f, 1.0f, 0f);
        Gfx.box("lanternPole", 0.05f, 1.35f, 0.05f, hull, boat, 0.92f, 1.85f, -2.35f);
        Gfx.box("lanternArm", 0.30f, 0.04f, 0.04f, hull, boat, 0.66f, 3.15f, -2.35f);
        Gfx.box("lanternPole", 0.05f, 1.05f, 0.05f, hull, boat, 0.8f, 1.85f, -2.1f);
        Gfx.box("lanternArm", 0.28f, 0.04f, 0.04f, hull, boat, 0.55f, 2.85f, -2.1f);

        body.setLocalTranslation(0f, 1.15f, 0.35f);
        body.setLocalScale(0.86f);
        buildHumanoid(0.8f, new ColorRGBA(0.42f, 0.40f, 0.35f, 1f),
                new ColorRGBA(0.10f, 0.16f, 0.22f, 1f), false, true);
        body.setLocalTranslation(0f, 1.30f, -0.85f);
        Gfx.box("hood", 0.17f, 0.17f, 0.17f, hull, head, 0f, 0.06f, -0.03f);
        Material bone = Gfx.lit(am, new ColorRGBA(0.76f, 0.74f, 0.66f, 1f),
                new ColorRGBA(0.2f, 0.19f, 0.16f, 1f), ColorRGBA.Black, 4f);
        Material boneDark = Gfx.lit(am, new ColorRGBA(0.08f, 0.08f, 0.07f, 1f),
                new ColorRGBA(0.02f, 0.02f, 0.02f, 1f), ColorRGBA.Black, 2f);
        Geometry skull = Gfx.sphere("skull", 0.38f, 10, bone, head, 0f, 0.10f, 0.14f);
        skull.setLocalScale(1f, 1.12f, 1f);
        Gfx.sphere("eyeSocketL", 0.11f, 8, boneDark, head, -0.15f, 0.15f, 0.43f);
        Gfx.sphere("eyeSocketR", 0.11f, 8, boneDark, head, 0.15f, 0.15f, 0.43f);
        Gfx.box("noseHole", 0.05f, 0.07f, 0.05f, boneDark, head, 0f, 0.01f, 0.49f);
        Gfx.box("jaw", 0.24f, 0.09f, 0.26f, bone, head, 0f, -0.16f, 0.20f);
        Gfx.box("oar", 0.05f, 2.1f, 0.05f, hull, weapon, 0.5f, -0.8f, 0f)
                .rotate(0f, 0f, 0.4f);
        Gfx.box("lantern", new Sphere(8, 8, 0.22f), ghost, boat, 0.42f, 2.82f, -2.35f);
    }

    private void buildAvatar() {
        Material bark = Gfx.lit(am, new ColorRGBA(0.44f, 0.38f, 0.29f, 1f),
                new ColorRGBA(0.18f, 0.15f, 0.11f, 1f), ColorRGBA.Black, 3f);
        Gfx.mapped(bark, am, "brown_leather", 0.8f);
        Material leaf = Gfx.lit(am, new ColorRGBA(0.38f, 0.44f, 0.16f, 1f),
                new ColorRGBA(0.12f, 0.14f, 0.05f, 1f), ColorRGBA.Black, 4f);
        Material gold = Gfx.unshaded(am, new ColorRGBA(1f, 0.72f, 0.24f, 1f));

        Node tree = new Node("tree");
        root.attachChild(tree);
        Gfx.cylinder("trunk", 0.95f, 3.1f, bark, tree, 0f, 1.6f, 0f);
        Gfx.cylinder("trunk2", 0.68f, 1.8f, bark, tree, 0f, 3.6f, 0f);
        for (int i = 0; i < 7; i++) {
            float a = i * FastMath.TWO_PI / 7f;
            Node leafNode = new Node("crown" + i);
            leafNode.setLocalTranslation(FastMath.cos(a) * 1.5f, 4.4f + (i % 3) * 0.5f,
                    FastMath.sin(a) * 1.5f);
            tree.attachChild(leafNode);
            Geometry lobe = Gfx.sphere("leaves", 1.15f, 8, leaf, leafNode, 0f, 0f, 0f);
            lobe.setLocalScale(1.2f, 0.8f, 1.2f);
        }
        Gfx.sphere("face", 0.55f, 10, gold, tree, 0f, 3.15f, 0.72f);
        Gfx.box("eyeL", 0.11f, 0.06f, 0.04f, gold, tree, -0.25f, 3.28f, 1.08f);
        Gfx.box("eyeR", 0.11f, 0.06f, 0.04f, gold, tree, 0.25f, 3.28f, 1.08f);

        for (int side = -1; side <= 1; side += 2) {
            Node arm = side < 0 ? armL : armR;
            tree.attachChild(arm);
            arm.setLocalTranslation(side * 0.9f, 3.4f, 0f);
            Gfx.box("branch", 0.22f, 1.15f, 0.22f, bark, arm, side * 0.35f, -0.5f, 0f)
                    .rotate(0f, 0f, -side * 0.45f);
            Gfx.box("branch2", 0.16f, 1.05f, 0.16f, bark, arm, side * 0.75f, -1.5f, 0.1f)
                    .rotate(0f, 0f, -side * 0.2f);
        }
        legL.setCullHint(Spatial.CullHint.Always);
        legR.setCullHint(Spatial.CullHint.Always);
        torso.setCullHint(Spatial.CullHint.Always);
        head.setCullHint(Spatial.CullHint.Always);
        Gfx.cylinder("clubShaft", 0.18f, 2.5f, bark, weapon, 0f, -0.3f, 0f);
        Gfx.sphere("clubHead", 0.52f, 8, bark, weapon, 0f, -1.6f, 0f);
        Gfx.sphere("clubGold", 0.26f, 6, gold, weapon, 0f, -1.6f, 0.4f);
    }

    private void buildDeathbird() {
        Material feather = Gfx.lit(am, new ColorRGBA(0.17f, 0.165f, 0.18f, 1f),
                new ColorRGBA(0.05f, 0.05f, 0.06f, 1f),
                new ColorRGBA(0.30f, 0.32f, 0.40f, 1f), 12f);
        Material featherDark = Gfx.lit(am, new ColorRGBA(0.095f, 0.09f, 0.105f, 1f),
                new ColorRGBA(0.03f, 0.03f, 0.035f, 1f), ColorRGBA.Black, 6f);
        Material bone = Gfx.lit(am, new ColorRGBA(0.72f, 0.70f, 0.62f, 1f),
                new ColorRGBA(0.18f, 0.17f, 0.14f, 1f), ColorRGBA.Black, 6f);
        Material frost = Gfx.unshaded(am, new ColorRGBA(0.62f, 0.86f, 1f, 1f));

        Node bird = body;
        Gfx.box("body", new Sphere(12, 14, 1f), feather, bird, 0f, 2.5f, 0f)
                .setLocalScale(0.66f, 0.92f, 1.06f);
        Gfx.box("breast", new Sphere(10, 12, 1f), featherDark, bird, 0f, 2.30f, 0.70f)
                .setLocalScale(0.55f, 0.72f, 0.55f);
        Gfx.box("ribs", new Sphere(8, 10, 1f), bone, bird, 0f, 2.45f, 1.02f)
                .setLocalScale(0.30f, 0.52f, 0.14f);
        for (int i = -1; i <= 1; i++) {
            Geometry scap = Gfx.box("scapular", 0.22f, 0.045f, 0.42f, featherDark, bird,
                    i * 0.30f, 3.15f, -0.10f);
            scap.rotate(0f, i * 0.25f, 0f);
        }

        tail.setLocalTranslation(0f, 2.35f, -1.05f);
        treeTail();
        for (int k = -1; k <= 1; k++) {
            Geometry plume = Gfx.box("tailPlume", 0.16f, 0.025f, 0.80f,
                    k == 0 ? featherDark : feather, tail, k * 0.20f, 0f, -0.55f);
            plume.rotate(0f, k * 0.24f, 0f);
        }

        Node neck = new Node("birdNeck");
        neck.setLocalTranslation(0f, 3.25f, 0.42f);
        bird.attachChild(neck);
        neck.setLocalRotation(new Quaternion().fromAngleAxis(0.46f, Vector3f.UNIT_X));
        for (int i = 0; i < 3; i++) {
            Gfx.cylinder("neckMesh", 0.17f - i * 0.028f, 0.40f,
                    i == 2 ? bone : feather, neck, 0f, 0.32f + i * 0.34f, i * 0.05f);
        }
        head.setLocalTranslation(0f, 1.16f, 0.22f);
        neck.attachChild(head);
        Gfx.sphere("skull", 0.34f, 10, bone, head, 0f, 0f, 0f);
        Gfx.box("beakTop", 0.12f, 0.11f, 0.34f, bone, head, 0f, -0.03f, 0.46f);
        Gfx.box("beakHook", 0.08f, 0.18f, 0.11f, bone, head, 0f, -0.17f, 0.74f);
        Gfx.box("jaw", 0.09f, 0.06f, 0.28f, featherDark, head, 0f, -0.16f, 0.40f);
        Gfx.sphere("eyeL", 0.07f, 6, frost, head, -0.20f, 0.09f, 0.22f);
        Gfx.sphere("eyeR", 0.07f, 6, frost, head, 0.20f, 0.09f, 0.22f);

        for (int side = -1; side <= 1; side += 2) {
            Node wing = side < 0 ? wingL : wingR;
            wing.setLocalTranslation(side * 0.60f, 3.0f, 0.05f);
            bird.attachChild(wing);
            Gfx.box("wingArm", 0.95f, 0.075f, 0.16f, feather, wing,
                    side * 0.92f, 0.02f, 0.02f).rotate(0f, 0f, side * 0.18f);
            Gfx.sphere("shoulder", 0.34f, 8, featherDark, wing,
                    side * 0.18f, 0.02f, 0.02f).setLocalScale(1.1f, 0.8f, 1.0f);
            Gfx.box("wingMembrane", 0.78f, 0.035f, 0.62f, featherDark, wing,
                    side * 0.94f, -0.20f, -0.50f).rotate(0f, side * 0.14f, side * 0.10f);
            for (int k = 0; k < 9; k++) {
                float len = 1.95f - k * 0.11f;
                Geometry fe = Gfx.box("feather", len * 0.5f, 0.035f, 0.30f,
                        (k % 2 == 0) ? feather : featherDark, wing,
                        side * (0.34f + len * 0.5f), -0.06f - k * 0.085f,
                        -0.08f - k * 0.17f);
                fe.rotate(0f, side * (0.10f + k * 0.075f), 0f);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            Node leg = new Node("birdLeg" + side);
            leg.setLocalTranslation(side * 0.30f, 1.66f, 0.02f);
            bird.attachChild(leg);
            Gfx.cylinder("legMesh", 0.065f, 0.95f, bone, leg, 0f, -0.42f, 0f);
            Gfx.box("foot", 0.14f, 0.05f, 0.20f, bone, leg, 0f, -0.93f, 0.06f);
            for (int t = -1; t <= 1; t++) {
                Gfx.box("toe", 0.035f, 0.03f, 0.20f, bone, leg,
                        t * 0.10f, -0.93f, 0.24f);
            }
        }
        torso.setCullHint(Spatial.CullHint.Always);
        armL.setCullHint(Spatial.CullHint.Always);
        armR.setCullHint(Spatial.CullHint.Always);
        body.setLocalTranslation(0f, 0f, 0f);
    }

    private void treeTail() {
        Node birdTail = body.getParent() == null ? root : root;
        birdTail.attachChild(tail);
        Gfx.box("tailFan", 0.55f, 0.04f, 0.95f, Gfx.lit(am,
                new ColorRGBA(0.09f, 0.09f, 0.1f, 1f),
                new ColorRGBA(0.02f, 0.02f, 0.03f, 1f), ColorRGBA.Black, 4f), tail, 0f, 0f, -0.5f);
    }

    private void buildGlow() {
        ColorRGBA c = switch (boss.spec.archetype) {
            case DRAGON -> new ColorRGBA(1f, 0.32f, 0.08f, 1f);
            case MARINER -> new ColorRGBA(0.4f, 0.75f, 1f, 1f);
            case DEATHBIRD -> new ColorRGBA(0.6f, 0.85f, 1f, 1f);
            case AVATAR, MARGIT -> new ColorRGBA(1f, 0.72f, 0.24f, 1f);
            default -> new ColorRGBA(1f, 0.5f, 0.16f, 1f);
        };
        tellColor.set(c);
        Geometry g = Gfx.fxSphere("tellGlow", new ColorRGBA(c.r, c.g, c.b, 0f), am, root);
        g.setLocalTranslation(0f, boss.displayHeight() * 0.6f, 0.9f);
        glow[0] = g;
    }

    // ---------------------------------------------------------------- animation

    void animate(float dt) {
        float t = boss.stateTime;
        float lift = 0f;
        float deathFall = 0f;
        boolean windup = boss.state == WorldBoss.State.WINDUP;
        boolean strike = boss.state == WorldBoss.State.STRIKE;
        WorldBoss.Move move = boss.move;

        if (!boss.alive) {
            float k = MathX.smooth(MathX.clamp(boss.deathTime / 2.4f, 0f, 1f));
            deathFall = k;
            lift = -k * 0.55f;
        }
        root.setLocalTranslation(boss.pos.x, boss.pos.y + lift, boss.pos.z);
        q.fromAngleAxis(boss.yaw, Vector3f.UNIT_Y);
        if (deathFall > 0f) {
            Quaternion fall = new Quaternion().fromAngleAxis(
                    deathFall * (boss.spec.archetype == WorldBoss.Archetype.DRAGON ? 1.2f : 1.45f),
                    Vector3f.UNIT_X);
            q.multLocal(fall);
        }
        root.setLocalRotation(q);

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

        switch (boss.spec.archetype) {
            case DRAGON -> animateDragon(dt, windup, strike, move, deathFall);
            case MARINER -> animateMariner(dt, windup, strike, move);
            case AVATAR -> animateAvatar(dt, windup, strike, move, deathFall);
            case DEATHBIRD -> animateDeathbird(dt, windup, strike, move, deathFall);
            default -> animateHumanoid(dt, windup, strike, move, deathFall);
        }
        animateGlow(windup, strike, move);
    }

    private void animateHumanoid(float dt, boolean windup, boolean strike,
                                 WorldBoss.Move move, float deathFall) {
        float walk = boss.walk;
        if (boss.state == WorldBoss.State.CHASE) {
            float s = FastMath.sin(walk * 1.2f);
            legL.setLocalRotation(new Quaternion().fromAngleAxis(s * 0.55f, Vector3f.UNIT_X));
            legR.setLocalRotation(new Quaternion().fromAngleAxis(-s * 0.55f, Vector3f.UNIT_X));
            armL.setLocalRotation(new Quaternion().fromAngleAxis(-s * 0.3f, Vector3f.UNIT_X));
            body.setLocalTranslation(0f, bodyBase.y + Math.abs(s) * 0.035f, bodyBase.z);
            for (int i = 0; i < 4; i++) {
                if (horseLegs[i] != null) {
                    float p = walk * 1.45f + (i % 2 == 0 ? 0f : FastMath.PI);
                    horseLegs[i].setLocalRotation(new Quaternion().fromAngleAxis(
                            FastMath.sin(p) * 0.55f, Vector3f.UNIT_X));
                }
            }
        } else {
            legL.setLocalRotation(Quaternion.IDENTITY);
            legR.setLocalRotation(Quaternion.IDENTITY);
            armL.setLocalRotation(Quaternion.IDENTITY);
            body.setLocalTranslation(bodyBase);
            for (int i = 0; i < 4; i++) {
                if (horseLegs[i] != null) {
                    horseLegs[i].setLocalRotation(Quaternion.IDENTITY);
                }
            }
        }

        boolean mounted = boss.spec.archetype == WorldBoss.Archetype.CAVALRY
                || boss.spec.archetype == WorldBoss.Archetype.SENTINEL;
        float arm = 0f;
        float twist = 0f;
        float crouch = 0f;
        float windK = 0f;
        if (windup && move != null) {
            windK = MathX.smooth(MathX.clamp(boss.stateTime / Math.max(0.2f, move.windup), 0f, 1f));
            arm = (mounted ? -0.30f : -1.9f) * windK;
            twist = 0.55f * windK;
            crouch = -0.08f * windK;
        } else if (strike) {
            arm = mounted ? 0.30f : 1.15f;
            twist = -0.65f;
            crouch = 0.08f;
        } else if (boss.state == WorldBoss.State.STAGGER) {
            twist = FastMath.sin(boss.stateTime * 14f) * 0.12f;
            crouch = 0.12f;
        }
        armR.setLocalRotation(new Quaternion().fromAngleAxis(arm, Vector3f.UNIT_X));
        if (mounted) {
            float swing = strike ? 1.35f : -0.75f * windK;
            weapon.setLocalRotation(new Quaternion().fromAngleAxis(swing, Vector3f.UNIT_X)
                    .mult(new Quaternion().fromAngleAxis(-0.10f, Vector3f.UNIT_Z)));
        }
        torso.setLocalRotation(new Quaternion().fromAngleAxis(twist, Vector3f.UNIT_Y));
        body.setLocalTranslation(0f, bodyBase.y + crouch, bodyBase.z);
        if (deathFall > 0f) {
            torso.setLocalRotation(new Quaternion().fromAngleAxis(-0.3f * deathFall, Vector3f.UNIT_X));
        }
        if (boss.spec.archetype == WorldBoss.Archetype.BEAST) {
            armL.setLocalRotation(new Quaternion().fromAngleAxis(
                    strike ? 0.9f : (windup ? -1.4f : 0f), Vector3f.UNIT_X));
        }
        if (neckNoise(boss)) {
            head.setLocalRotation(new Quaternion().fromAngleAxis(
                    FastMath.sin(boss.animTime * 1.3f) * 0.08f, Vector3f.UNIT_Y));
        }
    }

    private boolean neckNoise(WorldBoss boss) {
        return boss.spec.archetype == WorldBoss.Archetype.MARGIT;
    }

    private void animateDragon(float dt, boolean windup, boolean strike,
                               WorldBoss.Move move, float deathFall) {
        body.setLocalTranslation(0f, FastMath.sin(boss.animTime * 1.1f) * 0.12f, 0f);
        float wing = 0.85f + FastMath.sin(boss.animTime * 1.4f) * 0.1f;
        if (windup && move != null) {
            float k = MathX.clamp(boss.stateTime / Math.max(0.2f, move.windup), 0f, 1f);
            if (move.kind == WorldBoss.MoveKind.BREATH) {
                body.setLocalRotation(new Quaternion().fromAngleAxis(0.12f * k, Vector3f.UNIT_X));
                head.setLocalRotation(new Quaternion().fromAngleAxis(-0.55f * k, Vector3f.UNIT_X));
            } else if (move.kind == WorldBoss.MoveKind.AOE) {
                body.setLocalRotation(new Quaternion().fromAngleAxis(0.16f * k, Vector3f.UNIT_X));
            } else {
                body.setLocalRotation(new Quaternion().fromAngleAxis(-0.18f * k, Vector3f.UNIT_X));
            }
            wing += 0.45f * k;
        } else if (strike) {
            body.setLocalRotation(new Quaternion().fromAngleAxis(0.18f, Vector3f.UNIT_X));
            head.setLocalRotation(new Quaternion().fromAngleAxis(
                    move != null && move.kind == WorldBoss.MoveKind.BREATH ? -0.8f : 0.32f,
                    Vector3f.UNIT_X));
            wing -= 0.12f;
        } else {
            body.setLocalRotation(Quaternion.IDENTITY);
            head.setLocalRotation(Quaternion.IDENTITY);
        }
        wingL.setLocalRotation(new Quaternion().fromAngleAxis(-wing, Vector3f.UNIT_Z));
        wingR.setLocalRotation(new Quaternion().fromAngleAxis(wing, Vector3f.UNIT_Z));
        if (deathFall > 0f) {
            body.setLocalRotation(new Quaternion().fromAngleAxis(0.6f * deathFall, Vector3f.UNIT_Z));
        }
    }

    private void animateMariner(float dt, boolean windup, boolean strike,
                                WorldBoss.Move move) {
        body.setLocalTranslation(0f, 1.30f + FastMath.sin(boss.animTime * 1.7f) * 0.07f, -0.85f);
        float row = windup ? -0.9f : (strike ? 0.8f : 0.1f);
        armR.setLocalRotation(new Quaternion().fromAngleAxis(row, Vector3f.UNIT_X));
        armL.setLocalRotation(new Quaternion().fromAngleAxis(-0.2f, Vector3f.UNIT_X));
        head.setLocalRotation(new Quaternion().fromAngleAxis(
                FastMath.sin(boss.animTime * 0.8f) * 0.16f, Vector3f.UNIT_Y));
        if (boss.state == WorldBoss.State.WINDUP && move != null
                && move.kind == WorldBoss.MoveKind.TELEPORT) {
            float flick = FastMath.sin(boss.animTime * 34f);
            root.setLocalScale(scale * (0.92f + 0.08f * flick));
        } else {
            root.setLocalScale(scale);
        }
    }

    private void animateAvatar(float dt, boolean windup, boolean strike,
                               WorldBoss.Move move, float deathFall) {
        float sway = FastMath.sin(boss.animTime * 0.8f) * 0.035f;
        body.setLocalRotation(new Quaternion().fromAngleAxis(sway, Vector3f.UNIT_Z));
        float arm = 0f;
        if (windup && move != null) {
            float k = MathX.clamp(boss.stateTime / Math.max(0.2f, move.windup), 0f, 1f);
            arm = -1.5f * k;
            body.setLocalRotation(new Quaternion().fromAngleAxis(0.10f * k, Vector3f.UNIT_X));
        } else if (strike) {
            arm = 1.0f;
            body.setLocalRotation(new Quaternion().fromAngleAxis(-0.12f, Vector3f.UNIT_X));
        }
        armR.setLocalRotation(new Quaternion().fromAngleAxis(arm, Vector3f.UNIT_X));
        armL.setLocalRotation(new Quaternion().fromAngleAxis(-arm * 0.4f, Vector3f.UNIT_X));
        body.setLocalTranslation(0f, Math.abs(sway) * 0.12f, 0f);
        if (deathFall > 0f) {
            body.setLocalRotation(new Quaternion().fromAngleAxis(0.5f * deathFall, Vector3f.UNIT_X));
        }
    }

    private void animateDeathbird(float dt, boolean windup, boolean strike,
                                  WorldBoss.Move move, float deathFall) {
        float bob = FastMath.sin(boss.animTime * 1.6f) * 0.08f;
        body.setLocalTranslation(0f, bob, 0f);
        float flap = 0.25f + FastMath.sin(boss.animTime * 3.2f) * 0.14f;
        if (windup) {
            flap += 0.55f;
        } else if (strike) {
            flap -= 0.15f;
        }
        wingL.setLocalRotation(new Quaternion().fromAngleAxis(-flap, Vector3f.UNIT_Z));
        wingR.setLocalRotation(new Quaternion().fromAngleAxis(flap, Vector3f.UNIT_Z));
        float neck = 0f;
        if (windup && move != null) {
            neck = -0.45f;
        } else if (strike) {
            neck = 0.55f;
        }
        head.setLocalRotation(new Quaternion().fromAngleAxis(neck, Vector3f.UNIT_X));
        if (deathFall > 0f) {
            body.setLocalRotation(new Quaternion().fromAngleAxis(0.7f * deathFall, Vector3f.UNIT_Z));
        }
    }

    private void animateGlow(boolean windup, boolean strike, WorldBoss.Move move) {
        float alpha = 0f;
        float size = 0.12f;
        if (windup && move != null) {
            float k = MathX.clamp(boss.stateTime / Math.max(0.2f, move.windup), 0f, 1f);
            alpha = 0.32f * k;
            size = 0.16f + 0.2f * k;
        } else if (strike) {
            alpha = 0.26f;
            size = 0.34f;
        }
        for (Geometry g : glow) {
            if (g == null) {
                continue;
            }
            g.setLocalScale(size);
            g.getMaterial().setColor("Color",
                    new ColorRGBA(tellColor.r, tellColor.g, tellColor.b, alpha));
            g.setCullHint(alpha > 0.01f ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        }
    }
}
