package com.aedfh;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class Boss extends Target {

    static final float HP_MAX = 1500f;
    static final float POISE_MAX = 90f;

    enum State {
        IDLE, CHASE, WINDUP, STRIKE, RECOVER, STAGGER, KNOCKDOWN, ROAR, DEAD
    }

    float hp = HP_MAX;
    float poise = POISE_MAX;
    boolean phase2;
    State state = State.IDLE;
    float stateTime;
    float deadTime;

    boolean evPlayerHit;
    boolean evDied;
    boolean evPhaseChange;
    float cameraShake;

    private static final float[] IDLE = {-0.10f, 0.28f, -0.24f, 0.06f, -0.26f, -0.30f,
            0.40f, 0.03f, 0.10f, 0.02f, 1.62f, 0f, 1.65f};
    private static final float[] SMASH_WU = {-2.35f, 0.10f, -0.55f, -1.95f, -0.55f, -0.90f,
            -0.16f, 0.12f, 0.12f, -0.08f, -0.70f, -0.04f, 1.65f};
    private static final float[] SMASH_ST = {0.55f, 0.10f, -0.22f, 0.35f, -0.42f, -0.40f,
            0.72f, -0.05f, 0.22f, 0.04f, 2.05f, 0.08f, 1.58f};
    private static final float[] SWEEP_WU = {-0.75f, 1.25f, -0.65f, -0.40f, -0.50f, -0.80f,
            0.15f, -0.70f, 0.02f, 0.45f, -0.25f, -0.05f, 1.63f};
    private static final float[] SWEEP_ST = {-0.35f, -0.95f, -0.35f, 0.20f, 0.50f, -0.50f,
            0.40f, 0.85f, 0.08f, -0.45f, 0.90f, 0.05f, 1.60f};
    private static final float[] QUAKE_WU = {-2.55f, 0.05f, -0.35f, -2.55f, -0.05f, -0.35f,
            -0.22f, 0f, 0.15f, 0f, -0.55f, -0.06f, 1.68f};
    private static final float[] QUAKE_ST = {0.65f, 0.12f, -0.15f, 0.65f, -0.12f, -0.15f,
            0.85f, 0f, 0.30f, 0f, 2.20f, 0.10f, 1.50f};
    private static final float[] ROAR = {-1.35f, 0.95f, -0.70f, -1.35f, -0.95f, -0.70f,
            -0.25f, 0f, -0.55f, 0f, -0.90f, -0.12f, 1.70f};
    private static final float[] STAGGER = {0.25f, 0.35f, -0.50f, 0.15f, -0.40f, -0.70f,
            0.55f, 0.15f, 0.40f, 0.20f, 1.10f, 0.18f, 1.50f};
    private static final float[] KNOCK = {0.70f, 0.86f, -0.90f, 0.64f, -0.96f, -0.92f,
            0.34f, 0.26f, 0.56f, 0.16f, 1.52f, -1.30f, 0.72f};
    private static final float[] DEAD = {0.30f, 0.60f, -0.30f, 0.20f, -0.70f, -0.40f,
            0.40f, 0.10f, 0.35f, 0.10f, 1.80f, 1.32f, 0.62f};

    private static final float[] SMASH_FOL = {0.78f, 0.10f, -0.28f, 0.56f, -0.45f, -0.46f,
            0.66f, -0.08f, 0.18f, 0.05f, 2.18f, 0.10f, 1.55f};
    private static final float[] SWEEP_FOL = {-0.18f, -1.15f, -0.30f, 0.30f, 0.62f, -0.48f,
            0.48f, 1.02f, 0.10f, -0.55f, 1.10f, 0.04f, 1.58f};
    private static final float[] QUAKE_FOL = {0.82f, 0.14f, -0.12f, 0.82f, -0.14f, -0.12f,
            0.72f, 0f, 0.20f, 0f, 2.32f, 0.12f, 1.48f};
    private static final float[] LEAP_WU = {-2.62f, 0.12f, -0.52f, -2.30f, -0.12f, -0.52f,
            -0.24f, 0.05f, 0.16f, 0f, -0.86f, -0.10f, 1.36f};
    private static final float[] LEAP_ST = {0.52f, 0.14f, -0.18f, 0.40f, -0.14f, -0.18f,
            0.46f, 0.02f, 0.14f, 0.02f, 2.18f, 0.10f, 1.52f};
    private static final float[] LEAP_FOL = {0.58f, 0.12f, -0.10f, 0.46f, -0.12f, -0.10f,
            0.54f, 0f, 0.16f, 0f, 2.32f, 0.12f, 1.48f};

    private static final ColorRGBA EYE_CALM = new ColorRGBA(1f, 0.46f, 0.07f, 1f);
    private static final ColorRGBA EYE_RAGE = new ColorRGBA(1f, 0.12f, 0.05f, 1f);

    private final AssetManager am;
    private final Fx fx;
    private final Random rnd = new Random(91);

    private final Node root = new Node("hollowApe");
    private final Node fallPivot = new Node("fallPivot");
    private final Node body = new Node("apeBody");
    private Node torso;
    private Node head;
    private Node jaw;
    private Node maneRoot;
    private Node clothRoot;
    private Node legL;
    private Node legR;
    private Node kneeL;
    private Node kneeR;
    private Node ankleL;
    private Node ankleR;
    private Node armL;
    private Node armR;
    private Node elbowL;
    private Node elbowR;
    private Node weapon;
    private final Node[] clothStrips = new Node[5];
    private final List<Geometry> cracks = new ArrayList<>();
    private Geometry auraRing;
    private Geometry execGlow;
    private Geometry execHalo;
    private Material execMat;
    private Material execHaloMat;

    private final Material rockMat;
    private final Material eyeMat;
    private final Material auraMat;
    private final Material crackMat;
    private final ColorRGBA rockBase;
    private float homeX;
    private float homeZ;
    private float homeRadius = Arena.PLAY_RADIUS;
    private float floorY;
    private final float[] pose = new float[13];
    private final Quaternion tmpQ = new Quaternion();
    private final Vector3f tmp = new Vector3f();

    private int attack;
    private float windupDur;
    private float strikeDur;
    private float recoverDur;
    private float staggerDur;
    private float knockDur;
    private boolean executionLocked;
    private float roarDur;
    private boolean hitApplied;
    private boolean enragePending;
    private float attackCooldown;
    private float poiseDelay;
    private float flashTime;
    private float idleTime;
    private float walkPhase;
    private float animTime;
    private float emberTimer;
    private float staggerFxTimer;
    private float footTimer;
    private boolean moving;
    private boolean hasPlayer;
    private final Vector3f playerPos = new Vector3f();
    private int comboLeft;
    private boolean chained;
    private boolean aftershockPending;
    private float aftershockAt;
    private float leapTime;

    Boss(AssetManager am, Node parent, Fx fx) {
        this.am = am;
        this.fx = fx;

        rockBase = new ColorRGBA(0.62f, 0.58f, 0.52f, 1f);
        rockMat = Gfx.lit(am, rockBase, new ColorRGBA(0.078f, 0.073f, 0.065f, 1f),
                new ColorRGBA(0.085f, 0.078f, 0.066f, 1f), 14f);
        eyeMat = Gfx.unshaded(am, EYE_CALM.clone());
        auraMat = Gfx.translucent(am, new ColorRGBA(1f, 0.18f, 0.06f, 0f));
        crackMat = Gfx.unshaded(am, new ColorRGBA(1f, 0.3f, 0.06f, 1f));

        buildModel();
        Gfx.tangents(root);
        parent.attachChild(root);
        root.setShadowMode(RenderQueue.ShadowMode.Cast);
    }

    private void buildModel() {
        fallPivot.setLocalTranslation(0f, 1.65f, 0f);
        root.attachChild(fallPivot);
        body.setLocalTranslation(0f, -1.65f, 0f);
        fallPivot.attachChild(body);

        Material fur = Gfx.lit(am, new ColorRGBA(0.34f, 0.27f, 0.20f, 1f),
                new ColorRGBA(0.12f, 0.095f, 0.07f, 1f),
                new ColorRGBA(0.05f, 0.04f, 0.03f, 1f), 3f);
        Material furDark = Gfx.lit(am, new ColorRGBA(0.17f, 0.13f, 0.095f, 1f),
                new ColorRGBA(0.065f, 0.05f, 0.036f, 1f),
                new ColorRGBA(0.02f, 0.017f, 0.014f, 1f), 2f);
        Material furLight = Gfx.lit(am, new ColorRGBA(0.48f, 0.37f, 0.26f, 1f),
                new ColorRGBA(0.17f, 0.13f, 0.09f, 1f),
                new ColorRGBA(0.05f, 0.04f, 0.032f, 1f), 3f);
        Material darkRock = Gfx.lit(am, new ColorRGBA(0.40f, 0.38f, 0.34f, 1f),
                new ColorRGBA(0.15f, 0.14f, 0.12f, 1f));
        Material stonePale = Gfx.lit(am, new ColorRGBA(0.58f, 0.54f, 0.47f, 1f),
                new ColorRGBA(0.22f, 0.20f, 0.17f, 1f),
                new ColorRGBA(0.10f, 0.09f, 0.08f, 1f), 12f);
        Material moss = Gfx.lit(am, new ColorRGBA(0.30f, 0.40f, 0.20f, 1f),
                new ColorRGBA(0.10f, 0.14f, 0.07f, 1f));
        Material bone = Gfx.lit(am, new ColorRGBA(0.62f, 0.55f, 0.43f, 1f),
                new ColorRGBA(0.23f, 0.20f, 0.15f, 1f));
        Material iron = Gfx.lit(am, new ColorRGBA(0.38f, 0.36f, 0.33f, 1f),
                new ColorRGBA(0.14f, 0.13f, 0.12f, 1f),
                new ColorRGBA(0.30f, 0.28f, 0.25f, 1f), 34f);
        Material cloth = Gfx.lit(am, new ColorRGBA(0.42f, 0.085f, 0.06f, 1f),
                new ColorRGBA(0.14f, 0.03f, 0.022f, 1f),
                new ColorRGBA(0.05f, 0.022f, 0.016f, 1f), 6f);
        Material skin = Gfx.lit(am, new ColorRGBA(0.50f, 0.40f, 0.30f, 1f),
                new ColorRGBA(0.17f, 0.13f, 0.10f, 1f));
        Material skinLight = Gfx.lit(am, new ColorRGBA(0.60f, 0.48f, 0.36f, 1f),
                new ColorRGBA(0.20f, 0.16f, 0.12f, 1f));

        Gfx.mapped(fur, am, "faux_fur", 1.0f);
        Gfx.mapped(furDark, am, "faux_fur", 1.0f);
        Gfx.mapped(furLight, am, "faux_fur", 1.0f);
        Gfx.mapped(darkRock, am, "rock_wall", 1.0f);
        Gfx.mapped(stonePale, am, "rock_wall", 1.0f);
        Gfx.mapped(moss, am, "rough_linen", 0.9f);
        Gfx.mapped(bone, am, "rock_wall", 0.5f);
        Gfx.mapped(iron, am, "metal_plate", 0.8f);
        Gfx.mapped(cloth, am, "rough_linen", 1.1f);
        Gfx.mapped(skin, am, "brown_leather", 0.7f);
        Gfx.mapped(skinLight, am, "brown_leather", 0.7f);
        Gfx.mapped(rockMat, am, "rock_wall", 1.0f);
        rockMat.setColor("Diffuse", new ColorRGBA(0.62f, 0.58f, 0.52f, 1f));

        legL = pivot(body, "legL", -0.54f, 1.38f, 0f);
        legR = pivot(body, "legR", 0.54f, 1.38f, 0f);
        for (Node leg : new Node[]{legL, legR}) {
            float side = leg == legL ? -1f : 1f;
            Geometry hip = Gfx.sphere("hipFur", 0.32f, 10, fur, leg, 0f, 0.04f, 0f);
            hip.setLocalScale(1.05f, 0.95f, 1.05f);
            Gfx.taperedCylinder("thigh", 0.28f, 0.235f, 0.70f, fur, leg, 0f, -0.35f, 0f);
            Geometry thighPlate = Gfx.box("thighPlate", 0.15f, 0.24f, 0.09f, rockMat,
                    leg, side * 0.16f, -0.32f, 0.19f);
            thighPlate.rotate(0.08f, side * 0.25f, side * 0.08f);
            for (int i = 0; i < 3; i++) {
                Node legTuft = pivot(leg, "thighTuft", side * (0.20f + i * 0.02f),
                        -0.10f - i * 0.15f, -0.06f);
                legTuft.setLocalRotation(new Quaternion().fromAngles(0.15f, 0f,
                        -side * (1.35f + i * 0.15f)));
                Gfx.taperedCylinder("thighFur", 0.006f, 0.085f, 0.26f - i * 0.03f,
                        i == 1 ? furLight : furDark, legTuft, 0f, 0.11f, 0f);
            }

            Node knee = pivot(leg, "knee", 0f, -0.70f, 0f);
            Geometry kneeCap = Gfx.sphere("kneeCap", 0.18f, 10, darkRock,
                    knee, 0f, 0.01f, 0.10f);
            kneeCap.setLocalScale(0.95f, 0.78f, 0.8f);
            Gfx.taperedCylinder("shin", 0.195f, 0.155f, 0.60f, darkRock, knee, 0f, -0.30f, 0f);
            Gfx.torus("ankleRing", 0.185f, 0.032f, iron, knee, 0f, -0.55f, 0f);
            for (int i = 0; i < 2; i++) {
                Node shinTuft = pivot(knee, "shinTuft", side * 0.16f,
                        -0.10f - i * 0.17f, -0.03f);
                shinTuft.setLocalRotation(new Quaternion().fromAngles(0.2f, 0f,
                        -side * 1.45f));
                Gfx.taperedCylinder("shinFur", 0.005f, 0.06f, 0.24f,
                        i == 0 ? fur : furDark, shinTuft, 0f, 0.10f, 0f);
            }

            Node ankle = pivot(knee, "ankle", 0f, -0.60f, 0f);
            Geometry foot = Gfx.sphere("foot", 0.26f, 10, darkRock,
                    ankle, 0f, -0.035f, 0.15f);
            foot.setLocalScale(1.22f, 0.52f, 1.55f);
            for (int i = -1; i <= 1; i++) {
                Geometry claw = Gfx.taperedCylinder("toeClaw", 0.008f, 0.05f, 0.24f, bone,
                        ankle, i * 0.115f, -0.07f, 0.42f);
                claw.rotate(FastMath.HALF_PI + 0.15f, 0f, 0f);
            }
            if (leg == legL) {
                kneeL = knee;
                ankleL = ankle;
            } else {
                kneeR = knee;
                ankleR = ankle;
            }
        }

        torso = pivot(body, "torso", 0f, 1.34f, 0f);
        Geometry pelvis = Gfx.sphere("pelvis", 0.40f, 12, fur, torso, 0f, 0.02f, 0f);
        pelvis.setLocalScale(1.14f, 0.88f, 1.02f);
        Gfx.taperedCylinder("belly", 0.48f, 0.60f, 0.98f, fur, torso, 0f, 0.49f, 0.04f);
        Geometry chestMass = Gfx.sphere("chestMass", 0.64f, 14, fur, torso, 0f, 1.00f, 0.06f);
        chestMass.setLocalScale(1.06f, 0.90f, 0.95f);
        for (int side = -1; side <= 1; side += 2) {
            Geometry pec = Gfx.box("pecPlate", 0.24f, 0.20f, 0.08f, darkRock,
                    torso, side * 0.27f, 1.05f, 0.52f);
            pec.rotate(0.16f, side * -0.20f, side * 0.16f);
            Geometry pecRidge = Gfx.box("pecRidge", 0.25f, 0.035f, 0.09f, stonePale,
                    torso, side * 0.27f, 1.235f, 0.52f);
            pecRidge.rotate(0.16f, side * -0.20f, side * 0.16f);
            Geometry chip = Gfx.box("pecChip", 0.12f, 0.085f, 0.06f, stonePale,
                    torso, side * 0.40f, 0.90f, 0.50f);
            chip.rotate(0.2f, side * -0.35f, side * 0.3f);
        }
        Gfx.box("sternum", 0.06f, 0.40f, 0.09f, darkRock, torso, 0f, 0.98f, 0.57f);
        Gfx.box("bellyPlate", 0.20f, 0.085f, 0.06f, darkRock, torso, 0f, 0.52f, 0.56f);

        Geometry hump = Gfx.sphere("backHump", 0.60f, 12, rockMat, torso, 0f, 1.06f, -0.30f);
        hump.setLocalScale(1.10f, 1.00f, 1.0f);
        for (int i = 0; i < 5; i++) {
            Geometry plate = Gfx.box("backPlate", 0.30f, 0.12f, 0.19f, darkRock, torso,
                    (i - 2) * 0.24f, 0.62f + i * 0.17f, -0.74f - (i % 2) * 0.04f);
            plate.rotate(-0.28f, i * 0.4f, 0f);
        }
        for (int i = 0; i < 5; i++) {
            Geometry spine = Gfx.taperedCylinder("spineSpike", 0.008f, 0.095f, 0.42f,
                    i % 2 == 0 ? bone : stonePale, torso, 0f, 0.60f + i * 0.19f, -0.80f);
            spine.rotate(-0.95f - (i % 2) * 0.08f, 0f, 0f);
        }
        for (int side = -1; side <= 1; side += 2) {
            Geometry boulder = Gfx.sphere("shoulderBoulder", 0.40f, 10, fur,
                    torso, side * 0.82f, 1.26f, 0.03f);
            boulder.setLocalScale(1.12f, 0.95f, 1.02f);
            Geometry mossCap = Gfx.sphere("shoulderMoss", 0.23f, 8, moss,
                    torso, side * 0.84f, 1.44f, 0.0f);
            mossCap.setLocalScale(1.2f, 0.20f, 1.0f);
            for (int i = 0; i < 3; i++) {
                Geometry slab = Gfx.box("pauldronSlab", 0.19f, 0.07f, 0.17f,
                        i == 1 ? stonePale : darkRock,
                        torso, side * (0.86f - i * 0.06f), 1.34f + i * 0.09f,
                        -0.16f + i * 0.20f);
                slab.rotate(0.14f + i * 0.08f, side * (0.18f + i * 0.2f), -side * 0.30f);
            }
            for (int j = 0; j < 3; j++) {
                Node tuft = pivot(torso, "shoulderFur",
                        side * (0.68f + j * 0.06f), 1.42f, 0.12f - j * 0.16f);
                tuft.setLocalRotation(new Quaternion().fromAngles(
                        0.20f + j * 0.08f, 0f, -side * (1.55f + j * 0.14f)));
                Gfx.taperedCylinder("shoulderFurTip", 0.006f, 0.11f,
                        0.40f + j * 0.06f, j == 1 ? furLight : fur,
                        tuft, 0f, 0.18f, 0f);
            }
            Geometry spike = Gfx.taperedCylinder("shoulderSpike", 0.008f, 0.13f, 0.44f,
                    stonePale, torso, side * 0.92f, 1.48f, -0.04f);
            spike.rotate(-0.12f, 0f, -side * 0.62f);
        }

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 4; i++) {
                Node tuft = pivot(torso, "flankFur",
                        side * (0.50f + (i % 2) * 0.07f), 0.26f + i * 0.24f, -0.16f);
                tuft.setLocalRotation(new Quaternion().fromAngles(0.2f,
                        side * -0.6f, -side * 1.35f));
                Gfx.taperedCylinder("flankFurGeo", 0.006f, 0.09f, 0.32f,
                        i % 2 == 0 ? furDark : fur, tuft, 0f, 0.12f, 0f);
            }
        }

        maneRoot = pivot(torso, "mane", 0f, 1.44f, 0.06f);
        Geometry collar = Gfx.sphere("furCollar", 0.50f, 12, furDark, maneRoot, 0f, 0f, 0f);
        collar.setLocalScale(1.30f, 0.52f, 1.05f);
        for (int i = 0; i < 18; i++) {
            float a = -3.1f + i * (6.2f / 17f);
            Node yawNode = pivot(maneRoot, "maneYaw",
                    FastMath.sin(a) * 0.46f, -0.01f, FastMath.cos(a) * 0.46f);
            yawNode.setLocalRotation(new Quaternion().fromAngles(0f, a, 0f));
            Node tilt = pivot(yawNode, "maneTilt", 0f, 0f, 0f);
            tilt.setLocalRotation(new Quaternion().fromAngles(2.05f, 0f, 0f));
            float len = 0.52f + (i % 4) * 0.10f;
            Gfx.taperedCylinder("maneTip", 0.006f, 0.105f, len,
                    i % 5 == 0 ? furLight : (i % 2 == 0 ? fur : furDark),
                    tilt, 0f, len * 0.5f, 0f);
        }

        Gfx.torus("waistBelt", 0.48f, 0.055f, iron, torso, 0f, 0.04f, 0f);
        clothRoot = pivot(torso, "clothRoot", 0f, 0f, 0.30f);
        for (int i = 0; i < 5; i++) {
            Node strip = pivot(clothRoot, "clothStrip", (i - 2) * 0.15f, 0f, 0f);
            Gfx.taperedCylinder("clothStripGeo", 0.075f, 0.095f, 0.84f, cloth,
                    strip, 0f, -0.42f, 0f);
            Geometry hem = Gfx.box("clothHem", 0.095f, 0.015f, 0.03f, bone,
                    strip, 0f, -0.83f, 0f);
            hem.rotate(0.1f, 0f, 0f);
            clothStrips[i] = strip;
        }

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 6; i++) {
                float a = side * (0.62f + i * 0.45f);
                Node yawNode = pivot(torso, "hipFurYaw",
                        FastMath.sin(a) * 0.44f, 0.02f, FastMath.cos(a) * 0.44f);
                yawNode.setLocalRotation(new Quaternion().fromAngles(0f, a, 0f));
                Node tilt = pivot(yawNode, "hipFurTilt", 0f, 0f, 0f);
                tilt.setLocalRotation(new Quaternion().fromAngles(2.45f, 0f, 0f));
                float len = 0.34f + (i % 3) * 0.08f;
                Gfx.taperedCylinder("hipFur", 0.005f, 0.085f, len,
                        i % 4 == 0 ? furLight : furDark, tilt, 0f, len * 0.5f, 0f);
            }
        }

        head = pivot(torso, "head", 0f, 1.74f, 0.38f);
        Geometry ruff = Gfx.sphere("neckRuff", 0.28f, 10, furDark, head, 0f, -0.06f, 0f);
        ruff.setLocalScale(1.1f, 0.85f, 1.0f);
        Geometry neckMass = Gfx.sphere("neckMass", 0.30f, 10, furDark,
                torso, 0f, 1.58f, 0.24f);
        neckMass.setLocalScale(1.2f, 0.8f, 1.0f);
        Geometry skull = Gfx.sphere("skull", 0.35f, 16, fur, head, 0f, 0.13f, 0.02f);
        skull.setLocalScale(1.06f, 1.06f, 1.0f);
        Geometry face = Gfx.sphere("face", 0.27f, 14, skin, head, 0f, 0.08f, 0.18f);
        face.setLocalScale(0.84f, 0.92f, 0.56f);
        for (int side = -1; side <= 1; side += 2) {
            Geometry socket = Gfx.sphere("eyeSocket", 0.105f, 8, furDark,
                    head, side * 0.135f, 0.155f, 0.318f);
            socket.setLocalScale(1.15f, 0.86f, 0.42f);
            socket.rotate(0f, 0f, -side * 0.22f);
            Geometry browSlab = Gfx.box("brow", 0.15f, 0.058f, 0.15f, furDark,
                    head, side * 0.17f, 0.262f, 0.30f);
            browSlab.rotate(-0.14f, 0f, -side * 0.52f);
            Geometry cheekBone = Gfx.box("cheekBone", 0.075f, 0.10f, 0.11f, darkRock,
                    head, side * 0.245f, 0.075f, 0.30f);
            cheekBone.rotate(0.1f, 0f, side * 0.25f);
            Geometry eye = Gfx.sphere("eye", 0.052f, 8, eyeMat,
                    head, side * 0.145f, 0.158f, 0.372f);
            eye.setLocalScale(1.45f, 0.62f, 0.55f);
            eye.rotate(0f, 0f, -side * 0.20f);
            Geometry ear = Gfx.sphere("ear", 0.15f, 8, furDark, head,
                    side * 0.345f, 0.10f, -0.05f);
            ear.setLocalScale(0.34f, 0.72f, 0.58f);
            ear.rotate(0.0f, 0f, side * 0.22f);
            Geometry innerEar = Gfx.sphere("innerEar", 0.08f, 8, skin,
                    head, side * 0.358f, 0.10f, -0.02f);
            innerEar.setLocalScale(0.24f, 0.56f, 0.46f);
            for (int i = 0; i < 4; i++) {
                Node tuft = pivot(head, "cheekTuft",
                        side * (0.225f + i * 0.015f), 0.10f - i * 0.05f, 0.19f);
                tuft.setLocalRotation(new Quaternion().fromAngles(0.15f, 0f,
                        side * (1.45f + i * 0.18f)));
                Gfx.taperedCylinder("cheekFur", 0.005f, 0.055f, 0.20f - i * 0.02f,
                        i == 1 ? furLight : furDark, tuft, 0f, 0.08f, 0f);
            }
        }
        Geometry muzzle = Gfx.sphere("muzzle", 0.22f, 12, skin, head, 0f, -0.075f, 0.38f);
        muzzle.setLocalScale(1.32f, 0.62f, 1.0f);
        Geometry snout = Gfx.sphere("snoutBridge", 0.15f, 10, skin,
                head, 0f, 0.055f, 0.415f);
        snout.setLocalScale(0.82f, 1.05f, 0.9f);
        Geometry nose = Gfx.box("nose", 0.085f, 0.045f, 0.055f, furDark,
                head, 0f, 0.012f, 0.545f);
        nose.rotate(0.22f, 0f, 0f);
        for (int side = -1; side <= 1; side += 2) {
            Gfx.box("nostril", 0.025f, 0.014f, 0.02f, furDark,
                    head, side * 0.048f, -0.012f, 0.590f);
        }
        Gfx.box("browCrease", 0.022f, 0.06f, 0.035f, furDark, head, 0f, 0.235f, 0.36f);
        Gfx.box("mouthCavity", 0.165f, 0.06f, 0.05f, furDark, head, 0f, -0.145f, 0.525f);
        Gfx.box("upperLip", 0.195f, 0.03f, 0.05f, skin, head, 0f, -0.09f, 0.545f);
        for (int side = -1; side <= 1; side += 2) {
            Geometry fang = Gfx.taperedCylinder("fang", 0.006f, 0.05f, 0.22f, bone,
                    head, side * 0.11f, -0.20f, 0.55f);
            fang.rotate(FastMath.PI, 0f, 0f);
            fang.rotate(0.08f, 0f, side * 0.06f);
        }

        jaw = pivot(head, "jaw", 0f, -0.13f, 0.29f);
        Geometry jawMass = Gfx.sphere("jawMass", 0.20f, 10, skin, jaw, 0f, -0.035f, 0.07f);
        jawMass.setLocalScale(1.22f, 0.58f, 1.02f);
        Gfx.box("jawLip", 0.16f, 0.022f, 0.04f, skinLight, jaw, 0f, 0.015f, 0.16f);
        for (int side = -1; side <= 1; side += 2) {
            Geometry tusk = Gfx.taperedCylinder("tusk", 0.007f, 0.055f, 0.34f, bone,
                    jaw, side * 0.185f, 0.10f, 0.17f);
            tusk.rotate(0.10f, 0f, side * -0.20f);
            Geometry lowerTooth = Gfx.taperedCylinder("lowerTooth", 0.006f, 0.025f, 0.10f,
                    bone, jaw, side * 0.06f, 0.05f, 0.20f);
            lowerTooth.rotate(0.04f, 0f, 0f);
        }
        for (int i = -1; i <= 1; i++) {
            Node chin = pivot(jaw, "chinFur", i * 0.10f, -0.10f, 0.12f);
            chin.setLocalRotation(new Quaternion().fromAngles(2.75f, 0f, i * 0.22f));
            Gfx.taperedCylinder("chinFurGeo", 0.005f, 0.06f, 0.20f - Math.abs(i) * 0.04f,
                    i == 0 ? furDark : fur, chin, 0f, 0.08f, 0f);
        }

        for (int i = 0; i < 4; i++) {
            Node crest = pivot(head, "crest", 0f, 0.33f - i * 0.05f,
                    -0.10f - i * 0.07f);
            crest.setLocalRotation(new Quaternion().fromAngles(-0.65f - i * 0.10f, 0f, 0f));
            Gfx.taperedCylinder("crestFur", 0.005f, 0.10f, 0.30f + i * 0.03f,
                    i % 2 == 0 ? furDark : fur, crest, 0f, 0.13f, 0f);
        }
        for (int side = -1; side <= 1; side += 2) {
            float len = side < 0 ? 0.50f : 0.36f;
            Geometry horn = Gfx.taperedCylinder("horn", 0.014f, 0.11f, len, stonePale,
                    head, side * 0.29f, 0.44f, -0.08f);
            horn.rotate(-0.52f, 0f, side * -0.55f);
            Geometry hornNub = Gfx.sphere("hornNub", 0.09f, 8, darkRock,
                    head, side * 0.35f, 0.53f, -0.20f);
            hornNub.setLocalScale(0.9f, 1.3f, 0.9f);
        }

        armL = pivot(torso, "armL", -0.80f, 1.18f, 0.02f);
        armR = pivot(torso, "armR", 0.80f, 1.18f, 0.02f);
        for (Node arm : new Node[]{armL, armR}) {
            float side = arm == armL ? -1f : 1f;
            Geometry deltoid = Gfx.sphere("deltoidFur", 0.36f, 10, fur, arm, 0f, 0.01f, 0f);
            deltoid.setLocalScale(1.05f, 1.05f, 1.05f);
            Gfx.taperedCylinder("upperArm", 0.235f, 0.185f, 0.92f, fur, arm, 0f, -0.46f, 0f);
            for (int i = 0; i < 3; i++) {
                Node tuft = pivot(arm, "armTuft", side * 0.17f, -0.14f - i * 0.19f, -0.04f);
                tuft.setLocalRotation(new Quaternion().fromAngles(0.1f, 0f,
                        -side * (1.45f + i * 0.10f)));
                Gfx.taperedCylinder("armFurTip", 0.005f, 0.06f, 0.26f - i * 0.02f,
                        i == 0 ? furLight : fur, tuft, 0f, 0.11f, 0f);
            }
            Gfx.torus("armBand", 0.20f, 0.035f, iron, arm, 0f, -0.70f, 0f);

            Node elbow = pivot(arm, "elbow", 0f, -0.92f, 0f);
            Geometry elbowRock = Gfx.sphere("elbowRock", 0.17f, 8, darkRock,
                    elbow, 0f, 0f, 0.02f);
            elbowRock.setLocalScale(1.0f, 0.85f, 0.95f);
            Gfx.taperedCylinder("forearm", 0.195f, 0.155f, 0.90f, darkRock, elbow, 0f, -0.45f, 0f);
            Geometry bracer = Gfx.box("bracer", 0.15f, 0.17f, 0.05f, stonePale,
                    elbow, 0f, -0.30f, 0.14f);
            bracer.rotate(0.12f, 0f, 0f);
            Gfx.torus("wristBand", 0.165f, 0.028f, iron, elbow, 0f, -0.56f, 0f);
            for (int i = 0; i < 3; i++) {
                Node ft = pivot(elbow, "forearmTuft", side * 0.16f,
                        -0.14f - i * 0.20f, -0.05f);
                ft.setLocalRotation(new Quaternion().fromAngles(0.15f, 0f,
                        -side * (1.35f + i * 0.10f)));
                Gfx.taperedCylinder("forearmFur", 0.005f, 0.055f, 0.26f - i * 0.02f,
                        i == 0 ? furLight : furDark, ft, 0f, 0.11f, 0f);
            }
            Geometry fist = Gfx.sphere("fist", 0.265f, 10, darkRock, elbow, 0f, -0.92f, 0.03f);
            fist.setLocalScale(1.05f, 0.92f, 1.1f);
            for (int i = -1; i <= 1; i++) {
                Geometry knuckle = Gfx.box("knucklePlate", 0.07f, 0.095f, 0.055f,
                        stonePale, elbow, i * 0.10f, -1.00f, 0.17f);
                knuckle.rotate(0.15f, 0f, 0f);
                Geometry claw = Gfx.taperedCylinder("claw", 0.006f, 0.038f, 0.18f, bone,
                        elbow, i * 0.09f, -1.03f, 0.25f);
                claw.rotate(2.6f, 0f, 0f);
            }
            if (arm == armL) {
                elbowL = elbow;
            } else {
                elbowR = elbow;
            }
        }

        weapon = new Node("club");
        weapon.setLocalTranslation(0f, -0.96f, 0.06f);
        elbowR.attachChild(weapon);
        Gfx.taperedCylinder("clubShaft", 0.175f, 0.095f, 1.82f, darkRock,
                weapon, 0f, 0.91f, 0f);
        Geometry clubButt = Gfx.sphere("clubButt", 0.16f, 8, iron, weapon, 0f, -0.02f, 0f);
        clubButt.setLocalScale(1.1f, 0.75f, 1.1f);
        for (int i = 0; i < 4; i++) {
            Gfx.torus("gripWrap", 0.12f + i * 0.006f, 0.022f, i % 2 == 0 ? iron : furDark,
                    weapon, 0f, 0.12f + i * 0.14f, 0f);
        }

        float headY = 2.00f;
        Geometry clubHead = Gfx.sphere("clubHead", 0.46f, 10, rockMat, weapon,
                0f, headY, 0f);
        clubHead.setLocalScale(1.10f, 1.04f, 1.02f);
        for (int i = 0; i < 8; i++) {
            float a = i * FastMath.TWO_PI / 8f + 0.28f;
            float band = ((i % 3) - 1) * 0.20f;
            float r = 0.36f + (i % 2) * 0.08f;
            Geometry chunk = Gfx.sphere("clubRock", 0.16f + (i % 3) * 0.035f, 7,
                    i % 4 == 0 ? stonePale : darkRock, weapon,
                    FastMath.sin(a) * r, headY + band,
                    FastMath.cos(a) * r);
            chunk.setLocalScale(1.2f, 0.9f, 1.2f);
            chunk.rotate(0.3f * i, 0.7f * i, 0.2f * i);
        }
        Vector3f dir = new Vector3f();
        for (int i = 0; i < 6; i++) {
            float a = i * FastMath.TWO_PI / 6f + 0.15f;
            float elev = i == 0 ? 1.0f : (i < 4 ? 0.28f : -0.22f);
            float flat = FastMath.sqrt(Math.max(0f, 1f - elev * elev));
            dir.set(FastMath.sin(a) * flat, elev, FastMath.cos(a) * flat).normalizeLocal();
            Node spikeN = pivot(weapon, "clubSpike",
                    dir.x * 0.46f, headY + dir.y * 0.46f, dir.z * 0.46f);
            orientY(spikeN, dir);
            float len = i == 0 ? 0.52f : 0.38f + (i % 2) * 0.08f;
            Gfx.taperedCylinder("clubSpikeGeo", 0.006f, 0.10f, len, bone,
                    spikeN, 0f, len * 0.5f, 0f);
        }
        Gfx.torus("clubBand", 0.175f, 0.05f, iron, weapon, 0f, 1.58f, 0f);
        Gfx.torus("clubBand2", 0.165f, 0.042f, iron, weapon, 0f, 1.20f, 0f);

        auraRing = Gfx.torus("auraRing", 1.90f, 0.05f, auraMat, body, 0f, 0.08f, 0f);
        auraRing.setShadowMode(RenderQueue.ShadowMode.Off);
        auraRing.setCullHint(Spatial.CullHint.Always);

        execMat = Gfx.translucent(am, new ColorRGBA(1f, 0.82f, 0.28f, 0f));
        execMat.getAdditionalRenderState()
                .setBlendMode(com.jme3.material.RenderState.BlendMode.AlphaAdditive);
        execGlow = Gfx.sphere("execGlow", 0.40f, 14, execMat, torso, 0f, 1.02f, 0.58f);
        execGlow.setLocalScale(1f, 1.15f, 0.7f);
        execGlow.setQueueBucket(RenderQueue.Bucket.Transparent);
        execGlow.setShadowMode(RenderQueue.ShadowMode.Off);
        execGlow.setCullHint(Spatial.CullHint.Always);

        execHaloMat = Gfx.translucent(am, new ColorRGBA(1f, 0.76f, 0.22f, 0f));
        execHaloMat.getAdditionalRenderState()
                .setBlendMode(com.jme3.material.RenderState.BlendMode.AlphaAdditive);
        execHalo = Gfx.sphere("execHalo", 0.58f, 16, execHaloMat, torso, 0f, 1.02f, 0.36f);
        execHalo.setLocalScale(1f, 1.15f, 0.72f);
        execHalo.setQueueBucket(RenderQueue.Bucket.Transparent);
        execHalo.setShadowMode(RenderQueue.ShadowMode.Off);
        execHalo.setCullHint(Spatial.CullHint.Always);

        addCrack(torso, 0.31f, 1.03f, 0.67f, 0.035f, 0.20f, 0.015f, 0f, 0f, 0.45f);
        addCrack(torso, -0.31f, 1.02f, 0.67f, 0.03f, 0.17f, 0.015f, 0f, 0f, -0.5f);
        addCrack(torso, 0f, 0.86f, 0.665f, 0.025f, 0.24f, 0.015f, 0f, 0f, -0.16f);
        addCrack(torso, 0.30f, 1.08f, -0.82f, 0.03f, 0.21f, 0.015f, 0.25f, 0f, 0.35f);
        addCrack(torso, -0.85f, 1.24f, 0.36f, 0.02f, 0.16f, 0.015f, 0f, 0.2f, 0.6f);
        addCrack(head, 0f, 0.255f, 0.39f, 0.11f, 0.012f, 0.012f, 0f, 0f, 0.12f);
        addCrack(elbowR, 0f, -0.20f, 0.16f, 0.015f, 0.15f, 0.012f, 0.1f, 0f, 0.2f);
        addCrack(elbowL, 0f, -0.24f, 0.16f, 0.015f, 0.13f, 0.012f, 0.1f, 0f, -0.25f);

        head.setLocalScale(1.24f);
    }

    private void addCrack(Node parent, float x, float y, float z,
                          float hx, float hy, float hz, float rx, float ry, float rz) {
        Geometry g = Gfx.box("crack", hx, hy, hz, crackMat, parent, x, y, z);
        g.setLocalRotation(new Quaternion().fromAngles(rx, ry, rz));
        g.setShadowMode(RenderQueue.ShadowMode.Off);
        g.setCullHint(Spatial.CullHint.Always);
        cracks.add(g);
    }

    private void setCracksVisible(boolean visible) {
        for (Geometry g : cracks) {
            g.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        }
    }

    private Node pivot(Node parent, String name, float x, float y, float z) {
        Node n = new Node(name);
        n.setLocalTranslation(x, y, z);
        parent.attachChild(n);
        return n;
    }

    /** Points the node's local +Y along dir; jME 3.9's angleBetween is unreliable. */
    private void orientY(Node n, Vector3f dir) {
        Vector3f d = dir.normalize();
        float dot = MathX.clamp(d.y, -1f, 1f);
        if (dot > 0.9999f) {
            return;
        }
        if (dot < -0.9999f) {
            n.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.PI, Vector3f.UNIT_X));
            return;
        }
        Vector3f axis = new Vector3f(Vector3f.UNIT_Y).crossLocal(d).normalizeLocal();
        n.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.acos(dot), axis));
    }

    /** Relocates the fight: chase clamp, model height and all FX offsets. */
    void setHome(float x, float z, float radius, float floor) {
        homeX = x;
        homeZ = z;
        homeRadius = radius;
        floorY = floor;
    }

    private float fy(float y) {
        return y + floorY;
    }

    /** Hides/shows the whole model (tutorial cave removes the ape once beaten). */
    void setPresent(boolean on) {
        root.setCullHint(on ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }

    void reset(Vector3f spawn, float facing) {
        pos.set(spawn.x, floorY, spawn.z);
        yaw = facing;
        hp = HP_MAX;
        poise = POISE_MAX;
        alive = true;
        phase2 = false;
        state = State.IDLE;
        stateTime = 0f;
        deadTime = 0f;
        attackCooldown = 2.0f;
        poiseDelay = 0f;
        idleTime = 2.1f;
        hitApplied = true;
        enragePending = false;
        flashTime = 0f;
        animTime = 0f;
        walkPhase = 0f;
        emberTimer = 0f;
        staggerFxTimer = 0f;
        hasPlayer = false;
        executionLocked = false;
        evPlayerHit = false;
        evDied = false;
        evPhaseChange = false;
        cameraShake = 0f;
        comboLeft = 0;
        chained = false;
        aftershockPending = false;
        aftershockAt = 0f;
        leapTime = 0f;
        rockMat.setColor("Diffuse", rockBase);
        eyeMat.setColor("Color", EYE_CALM.clone());
        crackMat.setColor("Color", new ColorRGBA(1f, 0.3f, 0.06f, 1f));
        setCracksVisible(false);
        auraRing.setCullHint(Spatial.CullHint.Always);
        updateVisuals(0f);
    }

    @Override
    float maxHp() {
        return HP_MAX;
    }

    @Override
    float hp() {
        return hp;
    }

    @Override
    boolean enraged() {
        return phase2;
    }

    @Override
    float displayHeight() {
        return 2.55f;
    }

    @Override
    float radius() {
        return 1.25f;
    }

    @Override
    float groundY() {
        return floorY;
    }

    boolean isDead() {
        return !alive;
    }

    /** Poise-broken and close enough for the player to land a finisher. */
    boolean canBeExecuted(Vector3f from) {
        return alive && state == State.STAGGER
                && MathX.distXZ(pos, from) < 3.2f + radius();
    }

    /** Freeze the stagger window while the player commits to the finisher. */
    void lockForExecution(Vector3f from) {
        executionLocked = true;
        yaw = MathX.yawTo(pos, from);
    }

    void clearExecutionLock() {
        executionLocked = false;
    }

    void takeExecution(float dmg, Vector3f impact) {
        executionLocked = false;
        if (!alive) {
            return;
        }
        hp = Math.max(0f, hp - dmg);
        flashTime = 0.34f;
        poise = POISE_MAX;
        poiseDelay = 4f;
        if (hp <= 0f) {
            die(impact);
            return;
        }
        if (!phase2 && hp <= HP_MAX * 0.5f) {
            enragePending = true;
        }
        knockDur = 2.3f;
        state = State.KNOCKDOWN;
        stateTime = 0f;
        hitApplied = true;
        cameraShake = Math.max(cameraShake, 1.2f);
    }

    void takeHit(float dmg, float poiseDmg, Vector3f impact) {
        if (!alive) {
            return;
        }
        hp = Math.max(0f, hp - dmg);
        flashTime = 0.14f;
        poiseDelay = 4f;

        if (hp <= 0f) {
            die(impact);
            return;
        }

        if (!phase2 && hp <= HP_MAX * 0.5f) {
            enragePending = true;
        }

        if (state != State.STAGGER && state != State.KNOCKDOWN && state != State.ROAR) {
            poise -= poiseDmg;
            if (poise <= 0f) {
                poise = POISE_MAX;
                staggerDur = phase2 ? 1.6f : 1.9f;
                state = State.STAGGER;
                stateTime = 0f;
                hitApplied = true;
                cameraShake = Math.max(cameraShake, 0.55f);
                fx.shockwave(new Vector3f(pos.x, fy(0.05f), pos.z), 4.4f, 0.55f,
                        new ColorRGBA(1f, 0.72f, 0.3f, 0.6f));
                tmp.set(pos.x, fy(1.35f), pos.z);
                fx.flash(tmp, new ColorRGBA(1f, 0.84f, 0.36f, 0.7f), 1.0f);
                tmp.set(pos.x, fy(2.4f), pos.z);
                fx.spark(tmp, new ColorRGBA(1f, 0.8f, 0.4f, 1f), 38);
                tmp.set(pos.x, fy(1.25f), pos.z);
                fx.spark(tmp, new ColorRGBA(1f, 0.88f, 0.46f, 1f), 18);
            }
        }
    }

    private void die(Vector3f impact) {
        alive = false;
        state = State.DEAD;
        stateTime = 0f;
        deadTime = 0f;
        hitApplied = true;
        auraRing.setCullHint(Spatial.CullHint.Always);
        eyeMat.setColor("Color", new ColorRGBA(0.25f, 0.12f, 0.05f, 1f));
        evDied = true;
        cameraShake = 1.1f;

        tmp.set(pos.x, fy(2.2f), pos.z);
        fx.flash(tmp, new ColorRGBA(1f, 0.72f, 0.34f, 0.9f), 1.4f);
        fx.spark(tmp, new ColorRGBA(1f, 0.8f, 0.4f, 1f), 46);
        fx.dust(new Vector3f(pos.x, fy(0.15f), pos.z), 30);
        fx.shockwave(new Vector3f(pos.x, fy(0.05f), pos.z), 6.5f, 0.9f,
                new ColorRGBA(1f, 0.68f, 0.28f, 0.5f));
    }

    void update(float dt, Player player) {
        evPlayerHit = false;
        evDied = false;
        evPhaseChange = false;
        cameraShake = 0f;
        animTime += dt;
        playerPos.set(player.pos);
        hasPlayer = true;

        if (flashTime > 0f) {
            flashTime = Math.max(0f, flashTime - dt);
            float f = flashTime / 0.14f;
            rockMat.setColor("Diffuse", new ColorRGBA(
                    MathX.lerp(rockBase.r, 1f, f),
                    MathX.lerp(rockBase.g, 0.9f, f),
                    MathX.lerp(rockBase.b, 0.72f, f), 1f));
        } else {
            rockMat.setColor("Diffuse", rockBase);
        }

        if (!alive) {
            deadTime += dt;
            stateTime += dt;
            updateVisuals(dt);
            return;
        }

        stateTime += dt;
        attackCooldown = Math.max(0f, attackCooldown - dt);
        poiseDelay = Math.max(0f, poiseDelay - dt);
        if (state != State.STAGGER && state != State.KNOCKDOWN && poiseDelay <= 0f) {
            poise = Math.min(POISE_MAX, poise + 8f * dt);
        }

        if (phase2) {
            emberTimer -= dt;
            if (emberTimer <= 0f) {
                emberTimer = 0.09f;
                tmp.set(pos.x + (rnd.nextFloat() - 0.5f) * 2.2f,
                        fy(1.2f + rnd.nextFloat() * 2.4f),
                        pos.z + (rnd.nextFloat() - 0.5f) * 2.2f);
                fx.ember(tmp, new ColorRGBA(1f, 0.3f, 0.1f, 0.75f));
            }
        }

        if (!player.alive) {
            if (state != State.IDLE && state != State.DEAD) {
                state = State.IDLE;
                stateTime = 0f;
            }
            turnToward(player.pos, 1.2f, dt);
            updateVisuals(dt);
            return;
        }

        if (enragePending && (state == State.CHASE || state == State.IDLE || state == State.RECOVER)) {
            enragePending = false;
            startRoar();
        }

        moving = false;
        float d = MathX.distXZ(pos, player.pos);

        switch (state) {
            case IDLE -> {
                turnToward(player.pos, 2.6f, dt);
                if (stateTime >= idleTime) {
                    state = State.CHASE;
                    stateTime = 0f;
                }
            }
            case CHASE -> {
                turnToward(player.pos, 2.8f, dt);
                if (attackCooldown <= 0f && d < 8.4f) {
                    chooseAttack(d);
                } else if (d > 3.9f) {
                    float speed = phase2 ? 3.0f : 2.45f;
                    pos.x += FastMath.sin(yaw) * speed * dt;
                    pos.z += FastMath.cos(yaw) * speed * dt;
                    walkPhase += dt * (phase2 ? 7.4f : 5.6f);
                    moving = true;
                    footTimer -= dt;
                    if (footTimer <= 0f) {
                        footTimer = 0.34f;
                        fx.dust(new Vector3f(pos.x + FastMath.sin(yaw) * 0.5f, fy(0.08f),
                                pos.z + FastMath.cos(yaw) * 0.5f), 3);
                    }
                }
            }
            case WINDUP -> {
                if (stateTime < windupDur * 0.62f) {
                    turnToward(player.pos, 1.5f, dt);
                }
                if (stateTime >= windupDur) {
                    state = State.STRIKE;
                    stateTime = 0f;
                    hitApplied = false;
                    if (attack == 3) {
                        cameraShake = Math.max(cameraShake, 0.3f);
                        fx.dust(new Vector3f(pos.x, fy(0.12f), pos.z), 12);
                    }
                }
            }
            case STRIKE -> {
                float hitAt = attackHitTime();
                if (attack == 0) {
                    pos.x += FastMath.sin(yaw) * 2.2f * dt;
                    pos.z += FastMath.cos(yaw) * 2.2f * dt;
                } else if (attack == 3) {
                    if (stateTime < leapTime) {
                        pos.x += FastMath.sin(yaw) * 15f * dt;
                        pos.z += FastMath.cos(yaw) * 15f * dt;
                    }
                    if (stateTime >= leapTime && !hitApplied) {
                        hitApplied = true;
                        applyAttackHit(player);
                    }
                }
                if (!hitApplied && stateTime >= hitAt) {
                    hitApplied = true;
                    applyAttackHit(player);
                }
                if (aftershockPending && stateTime >= aftershockAt) {
                    aftershockPending = false;
                    applyAftershock(player);
                }
                if (stateTime >= strikeDur) {
                    if (comboLeft > 0) {
                        beginChain();
                    } else {
                        state = State.RECOVER;
                        stateTime = 0f;
                    }
                }
            }
            case RECOVER -> {
                if (stateTime >= recoverDur) {
                    state = State.CHASE;
                    stateTime = 0f;
                    chained = false;
                    attackCooldown = phase2 ? 0.55f + rnd.nextFloat() * 0.45f
                            : 0.9f + rnd.nextFloat() * 0.7f;
                }
            }
            case STAGGER -> {
                if (!executionLocked && stateTime >= staggerDur) {
                    state = State.CHASE;
                    stateTime = 0f;
                    attackCooldown = 0.65f;
                }
            }
            case KNOCKDOWN -> {
                turnToward(player.pos, 0.6f, dt);
                if (stateTime >= knockDur) {
                    state = State.CHASE;
                    stateTime = 0f;
                    attackCooldown = 0.8f;
                }
            }
            case ROAR -> {
                if (stateTime > roarDur * 0.35f && stateTime < roarDur * 0.9f && emberTimer <= 0f) {
                    emberTimer = 0.05f;
                    fx.ember(new Vector3f(pos.x + (rnd.nextFloat() - 0.5f) * 2.4f,
                            fy(1.5f + rnd.nextFloat() * 2.5f),
                            pos.z + (rnd.nextFloat() - 0.5f) * 2.4f),
                            new ColorRGBA(1f, 0.35f, 0.1f, 0.8f));
                }
                if (stateTime >= roarDur) {
                    state = State.CHASE;
                    stateTime = 0f;
                    attackCooldown = 0.4f;
                    emberTimer = 0f;
                }
            }
            default -> {
            }
        }

        float limit = homeRadius - 2.1f;
        float dx = pos.x - homeX;
        float dz = pos.z - homeZ;
        float r = (float) Math.sqrt(dx * dx + dz * dz);
        if (r > limit) {
            pos.x = homeX + dx / r * limit;
            pos.z = homeZ + dz / r * limit;
        }

        boolean exposed = state == State.STAGGER
                || (state == State.KNOCKDOWN && stateTime < knockDur - 0.35f);
        if (exposed) {
            staggerFxTimer -= dt;
            if (staggerFxTimer <= 0f) {
                staggerFxTimer = 0.06f;
                float a = rnd.nextFloat() * FastMath.TWO_PI;
                float reach = 0.30f + rnd.nextFloat() * 0.34f;
                tmp.set(pos.x + FastMath.sin(yaw) * 0.5f + FastMath.cos(a) * reach,
                        fy(0.85f + rnd.nextFloat() * 0.85f),
                        pos.z + FastMath.cos(yaw) * 0.5f + FastMath.sin(a) * reach);
                fx.ember(tmp, new ColorRGBA(1f, 0.80f + rnd.nextFloat() * 0.16f,
                        0.24f + rnd.nextFloat() * 0.22f, 0.95f));
                if (rnd.nextFloat() < 0.25f) {
                    fx.spark(tmp, new ColorRGBA(1f, 0.86f, 0.44f, 1f), 3);
                }
            }
        } else {
            staggerFxTimer = 0f;
        }

        updateVisuals(dt);
    }

    private void startRoar() {
        state = State.ROAR;
        stateTime = 0f;
        roarDur = 1.7f;
        phase2 = true;
        hitApplied = true;
        eyeMat.setColor("Color", EYE_RAGE.clone());
        auraRing.setCullHint(Spatial.CullHint.Inherit);
        setCracksVisible(true);
        evPhaseChange = true;
        cameraShake = 1.0f;

        tmp.set(pos.x, fy(1.8f), pos.z);
        fx.shockwave(new Vector3f(pos.x, fy(0.05f), pos.z), 7f, 0.8f,
                new ColorRGBA(1f, 0.2f, 0.08f, 0.5f));
        fx.spark(tmp, new ColorRGBA(1f, 0.35f, 0.12f, 1f), 40);
        fx.flash(tmp, new ColorRGBA(1f, 0.3f, 0.12f, 0.5f), 1.2f);
    }

    int attackIndex() {
        return attack;
    }

    void debugAttack(int index) {
        attack = index;
        state = State.WINDUP;
        stateTime = 0f;
        hitApplied = false;
        comboLeft = 0;
        chained = false;
        aftershockPending = index == 2 && phase2;
        aftershockAt = 0.34f;
        attackCooldown = 0f;
        float speedScale = phase2 ? 0.88f : 1f;
        switch (index) {
            case 0 -> {
                windupDur = 0.96f * speedScale;
                strikeDur = 0.36f;
                recoverDur = 1.02f;
            }
            case 1 -> {
                windupDur = 0.68f * speedScale;
                strikeDur = 0.40f;
                recoverDur = 0.90f;
            }
            case 3 -> {
                windupDur = 0.88f * speedScale;
                strikeDur = 0.66f;
                recoverDur = 1.15f;
                leapTime = 0.30f;
            }
            default -> {
                windupDur = 1.20f * speedScale;
                strikeDur = 0.48f;
                recoverDur = 1.25f;
            }
        }
    }

    private void chooseAttack(float d) {
        float roll = rnd.nextFloat();
        if (d < 4.9f) {
            attack = roll < 0.55f ? 0 : 1;
        } else if (d < 6.8f) {
            attack = roll < (phase2 ? 0.50f : 0.32f) ? 3 : 1;
        } else {
            attack = roll < (phase2 ? 0.55f : 0.40f) ? 3 : 2;
        }

        float speedScale = phase2 ? 0.88f : 1f;
        float jitter = 0.92f + rnd.nextFloat() * 0.18f;
        switch (attack) {
            case 0 -> {
                windupDur = 0.96f * speedScale * jitter;
                strikeDur = 0.36f;
                recoverDur = 1.02f;
            }
            case 1 -> {
                windupDur = 0.68f * speedScale * jitter;
                strikeDur = 0.40f;
                recoverDur = 0.90f;
            }
            case 3 -> {
                windupDur = 0.88f * speedScale * jitter;
                strikeDur = 0.66f;
                recoverDur = 1.15f;
                leapTime = 0.30f;
            }
            default -> {
                windupDur = 1.20f * speedScale * jitter;
                strikeDur = 0.48f;
                recoverDur = 1.25f;
            }
        }

        comboLeft = 0;
        chained = false;
        if ((attack == 0 || attack == 1) && d < 6.4f
                && rnd.nextFloat() < (phase2 ? 0.72f : 0.48f)) {
            comboLeft = phase2 && rnd.nextFloat() < 0.35f ? 2 : 1;
        } else if (attack == 3 && phase2 && rnd.nextFloat() < 0.40f) {
            comboLeft = 1;
        }
        aftershockPending = attack == 2 && phase2;
        aftershockAt = 0.34f;

        state = State.WINDUP;
        stateTime = 0f;
        hitApplied = false;
    }

    private float attackHitTime() {
        return switch (attack) {
            case 0 -> 0.10f;
            case 1 -> 0.13f;
            case 3 -> leapTime;
            default -> 0.17f;
        };
    }

    private void beginChain() {
        comboLeft--;
        chained = true;
        attack = attack == 0 ? 1 : 0;
        float speedScale = phase2 ? 0.88f : 1f;
        if (attack == 0) {
            windupDur = 0.48f * speedScale;
            strikeDur = 0.34f;
            recoverDur = 1.12f;
        } else {
            windupDur = 0.40f * speedScale;
            strikeDur = 0.36f;
            recoverDur = 0.98f;
        }
        state = State.WINDUP;
        stateTime = 0f;
        hitApplied = false;
        aftershockPending = false;
    }

    private void applyAftershock(Player player) {
        fx.shockwave(new Vector3f(pos.x, fy(0.05f), pos.z), 9.4f, 0.62f,
                new ColorRGBA(0.9f, 0.38f, 0.13f, 0.4f));
        fx.dust(new Vector3f(pos.x, fy(0.1f), pos.z), 20);
        cameraShake = Math.max(cameraShake, 0.45f);
        if (MathX.distXZ(pos, player.pos) <= 9.4f + player.radius()
                && player.takeHit(14f, pos)) {
            evPlayerHit = true;
            cameraShake = Math.max(cameraShake, 0.8f);
        }
    }

    private void applyAttackHit(Player player) {
        float d = MathX.distXZ(pos, player.pos);
        boolean hit = false;
        float dmg = 0f;
        Vector3f impact = new Vector3f(pos.x, fy(0.1f), pos.z);

        if (attack == 0) {
            dmg = 26f;
            hit = d <= 5.4f + player.radius() && MathX.angleBetween(yaw, pos, player.pos) <= 1.05f;
            impact.set(pos.x + FastMath.sin(yaw) * 3.0f, fy(0.1f), pos.z + FastMath.cos(yaw) * 3.0f);
            fx.shockwave(impact, 3.4f, 0.55f, new ColorRGBA(0.65f, 0.58f, 0.45f, 0.35f));
            fx.dust(impact, 18);
            cameraShake = Math.max(cameraShake, 0.75f);
        } else if (attack == 1) {
            dmg = 20f;
            hit = d <= 6.3f + player.radius() && MathX.angleBetween(yaw, pos, player.pos) <= 1.78f;
            impact.set(pos.x + FastMath.sin(yaw) * 3.2f, fy(0.1f), pos.z + FastMath.cos(yaw) * 3.2f);
            fx.shockwave(impact, 4.4f, 0.5f, new ColorRGBA(0.65f, 0.58f, 0.45f, 0.3f));
            fx.dust(impact, 14);
            cameraShake = Math.max(cameraShake, 0.5f);
        } else if (attack == 2) {
            dmg = 30f;
            hit = d <= 8.3f + player.radius();
            impact.set(pos.x, fy(0.1f), pos.z);
            fx.shockwave(new Vector3f(pos.x, fy(0.05f), pos.z), 8.3f, 0.75f,
                    new ColorRGBA(0.75f, 0.55f, 0.35f, 0.4f));
            fx.dust(new Vector3f(pos.x, fy(0.1f), pos.z), 26);
            cameraShake = Math.max(cameraShake, 1.0f);
        } else {
            dmg = 28f;
            hit = d <= 5.6f + player.radius();
            impact.set(pos.x + FastMath.sin(yaw) * 1.4f, fy(0.1f), pos.z + FastMath.cos(yaw) * 1.4f);
            fx.shockwave(new Vector3f(pos.x, fy(0.05f), pos.z), 5.6f, 0.62f,
                    new ColorRGBA(0.72f, 0.55f, 0.36f, 0.42f));
            fx.dust(new Vector3f(pos.x, fy(0.1f), pos.z), 22);
            cameraShake = Math.max(cameraShake, 1.1f);
        }

        if (hit) {
            if (player.takeHit(dmg, pos)) {
                evPlayerHit = true;
                cameraShake = Math.max(cameraShake, 1.0f);
            }
        }
    }

    private void turnToward(Vector3f target, float rate, float dt) {
        float want = MathX.yawTo(pos, target);
        float diff = MathX.wrap(want - yaw);
        yaw += MathX.clamp(diff, -rate * dt, rate * dt);
    }

    private void updateVisuals(float dt) {
        float breath = FastMath.sin(animTime * 1.15f);
        float legRX = -0.08f;
        float legLX = -0.08f;
        float kneeRX = 0.18f;
        float kneeLX = 0.18f;
        float ankleRX = -0.03f;
        float ankleLX = -0.03f;
        float bob = -0.02f;
        float pace = 0f;
        float torsoRoll = 0f;
        float bodySway = 0f;
        float leapHop = 0f;

        System.arraycopy(IDLE, 0, pose, 0, 13);
        pose[6] += breath * 0.022f;
        pose[8] += breath * 0.012f;
        pose[10] += breath * 0.025f;
        pose[12] += breath * 0.012f;

        if (state == State.CHASE && moving) {
            pace = 1f;
            float s = FastMath.sin(walkPhase);
            float c = FastMath.cos(walkPhase);
            legRX = s * 0.52f - 0.05f;
            legLX = -s * 0.52f - 0.05f;
            kneeRX = 0.26f + Math.max(0f, -s) * 0.62f;
            kneeLX = 0.26f + Math.max(0f, s) * 0.62f;
            ankleRX = -legRX * 0.4f - kneeRX * 0.32f + 0.06f;
            ankleLX = -legLX * 0.4f - kneeLX * 0.32f + 0.06f;
            bob = -0.09f + Math.abs(c) * 0.11f;
            pose[6] = 0.42f + Math.abs(s) * 0.05f;
            pose[7] = -s * 0.14f;
            pose[8] = 0.02f + c * 0.05f;
            pose[9] = s * 0.08f;
            pose[3] = 0.06f - s * 0.44f;
            pose[4] = -0.30f;
            pose[5] = -0.38f - Math.max(0f, s) * 0.26f;
            pose[0] = -0.10f + s * 0.14f;
            pose[2] = -0.24f + s * 0.10f;
            pose[10] = 1.62f + Math.abs(s) * 0.08f;
            torsoRoll = c * 0.035f;
            bodySway = s * 0.05f;
        }

        switch (state) {
            case WINDUP -> {
                float p = MathX.clamp(stateTime / windupDur, 0f, 1f);
                float e = attack == 1 ? MathX.smooth(p) : easeOutCubic(p);
                blendInto(attackWu(), e);
                switch (attack) {
                    case 0 -> {
                        legRX = 0.20f;
                        legLX = -0.30f;
                        kneeRX = 0.30f;
                        kneeLX = 0.48f;
                        ankleRX = -0.15f;
                        ankleLX = -0.04f;
                        bob = -0.05f;
                        torsoRoll = 0.07f * e;
                    }
                    case 1 -> {
                        legRX = 0.10f;
                        legLX = -0.34f;
                        kneeRX = 0.38f;
                        kneeLX = 0.52f;
                        ankleRX = -0.10f;
                        ankleLX = 0.0f;
                        bob = -0.08f;
                        torsoRoll = -0.10f * e;
                    }
                    case 2 -> {
                        legRX = -0.12f;
                        legLX = 0.12f;
                        kneeRX = 0.56f;
                        kneeLX = 0.56f;
                        ankleRX = -0.20f;
                        ankleLX = -0.20f;
                        bob = -0.16f;
                    }
                    default -> {
                        legRX = 0.34f;
                        legLX = -0.38f;
                        kneeRX = 0.64f;
                        kneeLX = 0.70f;
                        ankleRX = -0.24f;
                        ankleLX = 0.06f;
                        bob = -0.12f;
                        bodySway = -0.06f * e;
                    }
                }
                float hold = MathX.smooth(MathX.clamp((p - 0.60f) / 0.40f, 0f, 1f));
                float tremble = FastMath.sin(animTime * 19f) * 0.016f * hold;
                pose[6] += tremble;
                pose[1] += tremble * 0.8f;
                pose[10] += tremble * 0.7f;
            }
            case STRIKE -> {
                float[] wu = attackWu();
                float[] st = attackSt();
                float[] fol = attackFol();
                float q = MathX.smooth(MathX.clamp(stateTime / 0.16f, 0f, 1f));
                float f = MathX.smooth(MathX.clamp(
                        (stateTime - attackHitTime()) / Math.max(0.08f, strikeDur - attackHitTime()),
                        0f, 1f));
                for (int i = 0; i < 13; i++) {
                    pose[i] = MathX.lerp(MathX.lerp(wu[i], st[i], q), fol[i], f);
                }
                switch (attack) {
                    case 0 -> {
                        legRX = -0.38f;
                        legLX = 0.28f;
                        kneeRX = 0.46f;
                        kneeLX = 0.34f;
                        ankleRX = -0.10f;
                        ankleLX = -0.22f;
                        bob = -0.14f;
                    }
                    case 1 -> {
                        legRX = -0.18f;
                        legLX = 0.34f;
                        kneeRX = 0.42f;
                        kneeLX = 0.52f;
                        ankleRX = -0.18f;
                        ankleLX = -0.12f;
                        bob = -0.12f;
                        torsoRoll = 0.12f;
                    }
                    case 2 -> {
                        legRX = -0.24f;
                        legLX = 0.24f;
                        kneeRX = 0.62f;
                        kneeLX = 0.62f;
                        ankleRX = -0.22f;
                        ankleLX = -0.22f;
                        bob = -0.20f;
                    }
                    default -> {
                        legRX = -0.42f;
                        legLX = 0.34f;
                        kneeRX = 0.72f;
                        kneeLX = 0.62f;
                        ankleRX = -0.22f;
                        ankleLX = -0.10f;
                        bob = -0.12f;
                        float t = MathX.clamp(stateTime / Math.max(0.01f, leapTime), 0f, 1f);
                        leapHop = FastMath.sin(t * FastMath.PI) * 1.15f;
                    }
                }
            }
            case RECOVER -> {
                float[] fol = attackFol();
                float p = MathX.smooth(MathX.clamp(stateTime / recoverDur, 0f, 1f));
                for (int i = 0; i < 13; i++) {
                    pose[i] = MathX.lerp(fol[i], IDLE[i], p);
                }
                pose[6] += breath * 0.06f;
                pose[12] += breath * 0.02f;
                legRX = 0.08f;
                legLX = -0.16f;
                kneeRX = 0.30f;
                kneeLX = 0.34f;
                ankleRX = -0.10f;
                ankleLX = -0.06f;
                bob = -0.08f;
            }
            case STAGGER -> {
                float in = MathX.smooth(MathX.clamp(stateTime / 0.22f, 0f, 1f));
                float out = MathX.smooth(MathX.clamp((stateTime - (staggerDur - 0.35f)) / 0.35f, 0f, 1f));
                for (int i = 0; i < 13; i++) {
                    pose[i] = MathX.lerp(IDLE[i], STAGGER[i], in * (1f - out));
                }
                float wobble = FastMath.sin(stateTime * 11f) * 0.06f * (1f - out);
                pose[7] += wobble;
                pose[8] += wobble * 0.7f;
                kneeRX = 0.52f;
                kneeLX = 0.52f;
                ankleRX = -0.16f;
                ankleLX = -0.16f;
                bob = -0.17f + Math.abs(wobble) * 0.3f;
                legRX = 0.12f;
                legLX = -0.12f;
            }
            case KNOCKDOWN -> {
                float in = MathX.smooth(MathX.clamp(stateTime / 0.17f, 0f, 1f));
                float out = MathX.smooth(MathX.clamp(
                        (stateTime - (knockDur - 0.62f)) / 0.62f, 0f, 1f));
                float k = in * (1f - out);
                for (int i = 0; i < 13; i++) {
                    pose[i] = MathX.lerp(IDLE[i], KNOCK[i], k);
                }
                float impact = stateTime < 0.42f
                        ? FastMath.sin(MathX.clamp(stateTime / 0.42f, 0f, 1f) * FastMath.PI) * 0.05f
                        : 0f;
                pose[11] -= impact;
                legRX = -0.62f * in;
                legLX = 0.48f * in;
                kneeRX = 0.20f + 0.72f * in;
                kneeLX = 0.18f + 0.58f * in;
                ankleRX = -0.12f * in;
                ankleLX = 0.08f * in;
                bob = -0.02f * in;
            }
            case ROAR -> {
                float in = MathX.smooth(MathX.clamp(stateTime / 0.28f, 0f, 1f));
                float out = MathX.smooth(MathX.clamp((stateTime - (roarDur - 0.3f)) / 0.3f, 0f, 1f));
                for (int i = 0; i < 13; i++) {
                    pose[i] = MathX.lerp(IDLE[i], ROAR[i], in * (1f - out));
                }
                pose[6] += FastMath.sin(animTime * 22f) * 0.02f;
                pose[8] += FastMath.sin(animTime * 22f) * 0.02f;
                pose[12] += 0.03f * in * (1f - out);
                legRX = 0.10f;
                legLX = -0.10f;
                kneeRX = 0.22f;
                kneeLX = 0.22f;
                ankleRX = -0.08f;
                ankleLX = -0.08f;
                bob = 0.02f;
            }
            case DEAD -> {
                float f = MathX.smooth(MathX.clamp(deadTime / 1.15f, 0f, 1f));
                for (int i = 0; i < 13; i++) {
                    pose[i] = MathX.lerp(IDLE[i], DEAD[i], f);
                }
                legRX = -0.45f * f;
                legLX = 0.35f * f;
                kneeRX = 0.25f + 0.35f * f;
                kneeLX = 0.20f + 0.30f * f;
                ankleRX = -0.10f * f;
                ankleLX = 0.05f * f;
                bob = -0.1f * f;
                if (deadTime < 0.5f) {
                    pose[7] += FastMath.sin(deadTime * 30f) * 0.05f * (1f - deadTime / 0.5f);
                }
            }
            default -> {
            }
        }

        if (attack == 2 && state == State.WINDUP) {
            pose[6] += FastMath.sin(animTime * 15f) * 0.014f;
        }

        if (state == State.IDLE || state == State.CHASE) {
            bodySway += FastMath.sin(animTime * 0.62f) * 0.018f;
            torsoRoll += FastMath.sin(animTime * 0.47f + 0.6f) * 0.014f;
            pose[1] += FastMath.sin(animTime * 0.53f) * 0.025f;
            pose[4] += FastMath.sin(animTime * 0.47f + 1.2f) * 0.025f;
        }
        if (hasPlayer && state != State.DEAD) {
            float look = MathX.wrap(MathX.yawTo(pos, playerPos) - yaw);
            pose[9] += MathX.clamp(look, -0.8f, 0.8f) * 0.5f;
        }

        root.setLocalTranslation(pos);
        root.setLocalRotation(tmpQ.fromAngles(0f, yaw, 0f));
        body.setLocalTranslation(bodySway, -1.65f, 0f);

        fallPivot.setLocalTranslation(0f, pose[12] + bob + leapHop, 0f);
        fallPivot.setLocalRotation(tmpQ.fromAngles(pose[11], 0f, 0f));

        setRot(legR, legRX, 0f, 0f);
        setRot(legL, legLX, 0f, 0f);
        setRot(kneeR, kneeRX, 0f, 0f);
        setRot(kneeL, kneeLX, 0f, 0f);
        setRot(ankleR, ankleRX, 0f, 0f);
        setRot(ankleL, ankleLX, 0f, 0f);

        setRot(torso, pose[6], pose[7], torsoRoll);
        setRot(head, pose[8], pose[9], 0f);
        setRot(armR, pose[0], 0f, pose[1]);
        setRot(elbowR, pose[2], 0f, 0f);
        setRot(armL, pose[3], 0f, pose[4]);
        setRot(elbowL, pose[5], 0f, 0f);
        float clubLocalX = pose[10] - (pose[6] + pose[0] + pose[2]);
        setRot(weapon, clubLocalX, 0f, -pose[1]);

        float jawOpen = 0.03f + 0.02f * breath;
        if (state == State.WINDUP) {
            jawOpen = 0.18f * MathX.clamp(stateTime / Math.max(0.01f, windupDur), 0f, 1f);
        } else if (state == State.STRIKE) {
            jawOpen = 0.22f;
        } else if (state == State.ROAR) {
            float in = MathX.smooth(MathX.clamp(stateTime / 0.28f, 0f, 1f));
            float out = MathX.smooth(MathX.clamp((stateTime - (roarDur - 0.3f)) / 0.3f, 0f, 1f));
            jawOpen = 0.45f * in * (1f - 0.7f * out);
        } else if (state == State.STAGGER) {
            jawOpen = 0.08f;
        } else if (state == State.KNOCKDOWN) {
            jawOpen = 0.34f;
        }
        setRot(jaw, -jawOpen, 0f, 0f);

        float maneGust = 0.03f + pace * 0.05f + (state == State.ROAR ? 0.10f : 0f)
                + (state == State.STRIKE ? 0.06f : 0f);
        setRot(maneRoot, breath * 0.03f + maneGust, 0f, 0f);

        for (int i = 0; i < clothStrips.length; i++) {
            float sway = FastMath.sin(animTime * 2.3f - i * 0.55f) * 0.05f;
            setRot(clothStrips[i], 0.08f + pace * 0.32f + sway
                    + (state == State.STRIKE ? 0.18f : 0f), 0f,
                    FastMath.sin(animTime * 1.9f - i * 0.4f) * 0.04f);
        }

        if (!alive) {
            crackMat.setColor("Color", new ColorRGBA(0.55f, 0.13f, 0.04f, 1f));
        } else if (phase2) {
            float pulse = 0.5f + 0.5f * FastMath.sin(animTime * 7f);
            crackMat.setColor("Color", new ColorRGBA(1f, 0.20f + 0.24f * pulse, 0.05f, 1f));
            if (auraRing.getCullHint() != Spatial.CullHint.Always) {
                float ap = 0.5f + 0.5f * FastMath.sin(animTime * 6f);
                auraMat.setColor("Color", new ColorRGBA(1f, 0.18f, 0.06f, 0.05f + 0.13f * ap));
                auraRing.setLocalScale(1f + 0.05f * ap, 1f, 1f + 0.05f * ap);
            }
        }

        boolean exposed = alive && (state == State.STAGGER
                || (state == State.KNOCKDOWN && stateTime < knockDur - 0.35f));
        if (exposed) {
            float p = 0.5f + 0.5f * FastMath.sin(animTime * 9f);
            float kick = MathX.clamp(stateTime / 0.18f, 0f, 1f);
            execMat.setColor("Color", new ColorRGBA(1f, 0.84f + 0.12f * p,
                    0.30f + 0.18f * p, (0.50f + 0.42f * p) * kick));
            float s = 1.12f + 0.24f * p;
            execGlow.setLocalScale(s, 1.18f * s, 0.74f * s);
            execGlow.setCullHint(Spatial.CullHint.Inherit);
            execHaloMat.setColor("Color", new ColorRGBA(1f, 0.72f + 0.12f * p, 0.20f,
                    (0.10f + 0.10f * p) * kick));
            float hs = 0.96f + 0.16f * p;
            execHalo.setLocalScale(hs, 1.12f * hs, 0.72f * hs);
            execHalo.setCullHint(Spatial.CullHint.Inherit);
        } else {
            execGlow.setCullHint(Spatial.CullHint.Always);
            execHalo.setCullHint(Spatial.CullHint.Always);
        }
    }

    private float[] attackWu() {
        return switch (attack) {
            case 0 -> SMASH_WU;
            case 1 -> SWEEP_WU;
            case 3 -> LEAP_WU;
            default -> QUAKE_WU;
        };
    }

    private float[] attackSt() {
        return switch (attack) {
            case 0 -> SMASH_ST;
            case 1 -> SWEEP_ST;
            case 3 -> LEAP_ST;
            default -> QUAKE_ST;
        };
    }

    private float[] attackFol() {
        return switch (attack) {
            case 0 -> SMASH_FOL;
            case 1 -> SWEEP_FOL;
            case 3 -> LEAP_FOL;
            default -> QUAKE_FOL;
        };
    }

    private static float easeOutCubic(float t) {
        t = MathX.clamp(t, 0f, 1f);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private void blendInto(float[] target, float t) {
        for (int i = 0; i < 13; i++) {
            pose[i] = MathX.lerp(pose[i], target[i], t);
        }
    }

    private void setRot(Node node, float x, float y, float z) {
        node.setLocalRotation(tmpQ.fromAngles(x, y, z));
    }
}
