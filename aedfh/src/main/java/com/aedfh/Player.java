package com.aedfh;

import com.jme3.asset.AssetManager;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;

import java.util.ArrayList;
import java.util.List;

final class Player {

    static final float HP_MAX = 100f;
    static final float ST_MAX = 100f;
    static final float MP_MAX = 100f;

    private static final float WALK_SPEED = 4.4f;
    private static final float SPRINT_SPEED = 6.7f;
    private static final float ROLL_TIME = 0.58f;
    private static final float ROLL_COST = 22f;

    private static final float SKILL_MP_COST = 18f;
    private static final float SKILL_ST_COST = 14f;
    private static final float SKILL_MP_DRAIN = 14f;
    private static final float SKILL_ST_DRAIN = 9f;
    private static final float SKILL_CYCLE = 0.90f;
    private static final float SKILL_TICK = 0.30f;
    private static final float SKILL_DMG = 22f;
    private static final float SKILL_POISE = 9f;
    private static final float SKILL_REACH = 3.6f;

    private static final float[] LIGHT_DUR = {0.44f, 0.47f, 0.64f, 0.56f};
    private static final float[] LIGHT_WINDUP = {0.15f, 0.16f, 0.25f, 0.19f};
    private static final float[] LIGHT_HIT = {0.21f, 0.22f, 0.32f, 0.27f};
    private static final float[] LIGHT_DMG = {34f, 36f, 52f, 45f};
    private static final float[] LIGHT_POISE = {15f, 16f, 24f, 20f};
    private static final float[] LIGHT_COST = {10f, 10f, 14f, 12f};

    private static final float HEAVY_DUR = 1.02f;
    private static final float HEAVY_WINDUP = 0.44f;
    private static final float HEAVY_HIT = 0.58f;
    private static final float HEAVY_DMG = 110f;
    private static final float HEAVY_POISE = 50f;
    private static final float HEAVY_COST = 25f;

    private static final float RUN_DUR = 0.66f;
    private static final float RUN_WINDUP = 0.20f;
    private static final float RUN_HIT = 0.29f;
    private static final float RUN_DMG = 62f;
    private static final float RUN_POISE = 30f;
    private static final float RUN_COST = 18f;

    private static final float ROLL_ATK_DUR = 0.52f;
    private static final float ROLL_ATK_WINDUP = 0.13f;
    private static final float ROLL_ATK_HIT = 0.19f;
    private static final float ROLL_ATK_DMG = 44f;
    private static final float ROLL_ATK_POISE = 20f;
    private static final float ROLL_ATK_COST = 12f;

    private static final float JUMP_V = 5.7f;
    private static final float GRAVITY = 15.5f;
    private static final float AIR_COST = 9f;
    private static final float AIR_DMG = 46f;
    private static final float AIR_POISE = 20f;
    private static final float AIR_HIT = 0.17f;
    private static final float AIR_DUR = 0.44f;
    private static final float PLUNGE_COST = 12f;
    private static final float PLUNGE_DMG = 128f;
    private static final float PLUNGE_POISE = 58f;
    private static final float PLUNGE_LAG = 0.42f;
    private static final float PLUNGE_RAISE_TIME = 0.12f;
    private static final float PLUNGE_CLEAVE_TIME = 0.30f;
    private static final float CHARGE_MAX = 1.15f;
    private static final float CHARGE_COST = 20f;
    private static final float CHARGE_DRAIN = 8f;
    private static final float EXEC_RANGE = 3.2f;
    private static final float EXEC_DMG = 240f;
    private static final float EXEC_HIT = 0.74f;
    private static final float EXEC_DUR = 2.30f;

    private static final Pose REST = new Pose()
            .legs(-0.18f, 0.055f, 0.28f, -0.05f, 0.10f, -0.05f, 0.22f, 0.02f)
            .torso(0.03f, 0.12f, -0.02f)
            .head(-0.03f, -0.16f, 0.02f)
            .armR(-0.16f, 0.05f, 0.20f, -0.48f, 0.05f)
            .armL(-0.06f, 0.0f, -0.18f, -0.30f, 0.0f)
            .staff(-0.34f, 0.0f, -0.24f);

    private static final Pose L1_WINDUP = new Pose()
            .legs(-0.38f, 0.05f, 0.55f, -0.05f, 0.30f, -0.05f, 0.34f, 0.06f)
            .torso(-0.10f, 0.66f, 0.06f)
            .head(-0.02f, -0.42f, 0.04f)
            .armR(0.42f, 0.15f, 1.02f, -0.52f, -0.08f)
            .armL(-0.30f, -0.12f, -0.62f, -0.85f, 0.0f)
            .staff(-1.42f, 0.0f, -0.62f)
            .crouch(0.05f);
    private static final Pose L1_STRIKE = new Pose()
            .legs(-0.62f, 0.06f, 0.50f, -0.12f, 0.42f, -0.06f, 0.28f, 0.08f)
            .torso(0.38f, -0.62f, 0.06f)
            .head(0.10f, 0.36f, -0.03f)
            .armR(-1.18f, -0.15f, -0.50f, -0.22f, -0.05f)
            .armL(-0.18f, 0.12f, -0.50f, -0.60f, 0.0f)
            .staff(1.18f, 0.0f, 0.78f)
            .crouch(0.09f);
    private static final Pose L1_FOLLOW = new Pose()
            .legs(-0.68f, 0.07f, 0.58f, -0.14f, 0.46f, -0.06f, 0.32f, 0.09f)
            .torso(0.46f, -0.82f, 0.07f)
            .head(0.13f, 0.44f, -0.04f)
            .armR(-1.32f, -0.18f, -0.72f, -0.16f, -0.02f)
            .armL(-0.12f, 0.15f, -0.42f, -0.55f, 0.0f)
            .staff(1.42f, 0.0f, 1.05f)
            .crouch(0.11f);

    private static final Pose L2_WINDUP = new Pose()
            .legs(0.16f, 0.05f, 0.30f, -0.02f, -0.34f, -0.05f, 0.55f, 0.05f)
            .torso(-0.08f, -0.64f, -0.06f)
            .head(0.0f, 0.42f, -0.04f)
            .armR(-0.92f, 0.22f, -0.85f, -1.20f, 0.08f)
            .armL(-0.30f, -0.15f, 0.40f, -0.70f, 0.0f)
            .staff(-0.55f, 0.0f, 1.05f)
            .crouch(0.06f);
    private static final Pose L2_STRIKE = new Pose()
            .legs(0.40f, 0.06f, 0.30f, -0.06f, -0.42f, -0.06f, 0.42f, 0.05f)
            .torso(0.32f, 0.68f, 0.06f)
            .head(0.10f, -0.38f, 0.03f)
            .armR(-1.00f, -0.10f, 1.22f, -0.24f, 0.0f)
            .armL(-0.15f, 0.10f, -0.60f, -0.55f, 0.0f)
            .staff(0.98f, 0.0f, -1.12f)
            .crouch(0.09f);
    private static final Pose L2_FOLLOW = new Pose()
            .legs(0.44f, 0.06f, 0.34f, -0.07f, -0.48f, -0.06f, 0.46f, 0.06f)
            .torso(0.40f, 0.82f, 0.07f)
            .head(0.12f, -0.46f, 0.04f)
            .armR(-0.85f, -0.12f, 1.36f, -0.18f, 0.0f)
            .armL(-0.10f, 0.12f, -0.55f, -0.50f, 0.0f)
            .staff(1.22f, 0.0f, -1.42f)
            .crouch(0.11f);

    private static final Pose L3_WINDUP = new Pose()
            .legs(0.18f, 0.04f, 0.34f, -0.02f, -0.24f, -0.04f, 0.40f, 0.02f)
            .torso(-0.24f, 0.08f, 0.0f)
            .head(-0.14f, -0.05f, 0.0f)
            .armR(-2.55f, 0.05f, 0.18f, -0.42f, -0.08f)
            .armL(-2.30f, 0.05f, -0.30f, -0.55f, 0.0f)
            .staff(-0.62f, 0.0f, -0.08f)
            .crouch(0.01f);
    private static final Pose L3_STRIKE = new Pose()
            .legs(-0.60f, 0.08f, 0.80f, -0.16f, 0.44f, -0.08f, 0.40f, 0.09f)
            .torso(0.50f, -0.05f, 0.0f)
            .head(0.12f, 0.04f, 0.0f)
            .armR(-0.92f, 0.02f, 0.08f, -0.18f, 0.0f)
            .armL(-0.78f, 0.0f, -0.32f, -0.38f, 0.0f)
            .staff(2.42f, 0.0f, 0.06f)
            .crouch(0.11f);
    private static final Pose L3_FOLLOW = new Pose()
            .legs(-0.64f, 0.08f, 0.88f, -0.18f, 0.48f, -0.08f, 0.44f, 0.10f)
            .torso(0.58f, -0.12f, 0.0f)
            .head(0.16f, 0.10f, 0.0f)
            .armR(-0.75f, 0.0f, -0.02f, -0.24f, 0.0f)
            .armL(-0.65f, 0.0f, -0.40f, -0.42f, 0.0f)
            .staff(2.68f, 0.0f, 0.12f)
            .crouch(0.13f);

    private static final Pose L4_WINDUP = new Pose()
            .legs(-0.44f, 0.06f, 0.62f, -0.10f, 0.40f, -0.06f, 0.34f, 0.07f)
            .torso(-0.16f, 0.46f, 0.02f)
            .head(-0.04f, -0.30f, 0.02f)
            .armR(0.30f, 0.16f, 0.52f, -1.26f, -0.04f)
            .armL(-0.24f, -0.10f, -0.34f, -0.70f, 0.0f)
            .staff(-1.34f, 0.10f, 0.18f)
            .crouch(0.07f);
    private static final Pose L4_STRIKE = new Pose()
            .legs(-0.72f, 0.09f, 0.78f, -0.18f, 0.52f, -0.09f, 0.32f, 0.09f)
            .torso(0.40f, -0.30f, 0.04f)
            .head(0.14f, 0.20f, -0.02f)
            .armR(-1.52f, -0.04f, 0.06f, -0.10f, 0.0f)
            .armL(-0.36f, 0.10f, -0.44f, -0.62f, 0.0f)
            .staff(1.56f, -0.14f, 0.02f)
            .crouch(0.10f);
    private static final Pose L4_FOLLOW = new Pose()
            .legs(-0.80f, 0.10f, 0.86f, -0.20f, 0.56f, -0.10f, 0.34f, 0.10f)
            .torso(0.48f, -0.34f, 0.05f)
            .head(0.16f, 0.24f, -0.02f)
            .armR(-1.62f, -0.04f, 0.02f, -0.06f, 0.0f)
            .armL(-0.32f, 0.10f, -0.50f, -0.66f, 0.0f)
            .staff(1.62f, -0.16f, 0.0f)
            .crouch(0.12f);

    private static final Pose RUN_WU = new Pose()
            .legs(-0.52f, 0.08f, 0.88f, -0.22f, 0.46f, -0.07f, 0.30f, 0.12f)
            .torso(-0.24f, 0.86f, 0.10f)
            .head(-0.12f, -0.52f, -0.05f)
            .armR(0.34f, 0.20f, 0.92f, -1.34f, -0.06f)
            .armL(-0.52f, -0.16f, -0.72f, -0.92f, 0.0f)
            .staff(-1.12f, 0.24f, -0.52f)
            .crouch(0.06f);
    private static final Pose RUN_ST = new Pose()
            .legs(-0.84f, 0.10f, 1.02f, -0.26f, 0.56f, -0.08f, 0.30f, 0.14f)
            .torso(0.46f, -0.92f, 0.14f)
            .head(0.16f, 0.54f, -0.08f)
            .armR(-1.46f, -0.10f, -0.86f, -0.14f, 0.0f)
            .armL(-0.18f, 0.14f, -0.44f, -0.60f, 0.0f)
            .staff(1.34f, -0.42f, 1.02f)
            .crouch(0.12f);
    private static final Pose RUN_FOL = new Pose()
            .legs(-0.92f, 0.11f, 1.10f, -0.28f, 0.60f, -0.09f, 0.34f, 0.15f)
            .torso(0.54f, -1.04f, 0.16f)
            .head(0.20f, 0.60f, -0.09f)
            .armR(-1.30f, -0.12f, -1.04f, -0.10f, 0.0f)
            .armL(-0.12f, 0.16f, -0.40f, -0.56f, 0.0f)
            .staff(1.48f, -0.50f, 1.24f)
            .crouch(0.14f);

    private static final Pose ROLL_WU = new Pose()
            .legs(-0.72f, 0.08f, 1.20f, -0.30f, 0.68f, -0.08f, 0.62f, 0.14f)
            .torso(-0.18f, 0.64f, 0.10f)
            .head(-0.06f, -0.40f, -0.04f)
            .armR(0.46f, 0.16f, 0.82f, -1.10f, -0.05f)
            .armL(-0.36f, -0.12f, -0.58f, -0.78f, 0.0f)
            .staff(-0.84f, 0.30f, -0.38f)
            .crouch(0.14f);
    private static final Pose ROLL_ST = new Pose()
            .legs(-0.88f, 0.10f, 1.12f, -0.24f, 0.62f, -0.08f, 0.40f, 0.14f)
            .torso(0.42f, -0.78f, 0.12f)
            .head(0.14f, 0.46f, -0.06f)
            .armR(-1.10f, -0.08f, -0.62f, -0.22f, 0.0f)
            .armL(-0.24f, 0.12f, -0.46f, -0.58f, 0.0f)
            .staff(1.18f, -0.36f, 1.16f)
            .crouch(0.15f);

    private static final Pose HEAVY_WU = new Pose()
            .legs(0.28f, 0.05f, 0.40f, 0.00f, -0.34f, -0.05f, 0.45f, 0.02f)
            .torso(-0.38f, 0.50f, -0.05f)
            .head(-0.18f, -0.30f, 0.03f)
            .armR(-2.60f, 0.12f, 0.45f, -0.62f, -0.06f)
            .armL(-2.20f, 0.10f, -0.65f, -1.00f, 0.0f)
            .staff(-1.15f, 0.0f, -0.30f)
            .crouch(0.03f);
    private static final Pose HEAVY_ST = new Pose()
            .legs(-0.66f, 0.10f, 0.84f, -0.18f, 0.48f, -0.10f, 0.46f, 0.10f)
            .torso(0.46f, -0.18f, 0.04f)
            .head(0.06f, 0.12f, -0.03f)
            .armR(-0.95f, 0.0f, 0.0f, -0.12f, 0.0f)
            .armL(-1.00f, 0.0f, -0.42f, -0.35f, 0.0f)
            .staff(2.35f, 0.0f, 0.18f)
            .crouch(0.10f);
    private static final Pose HEAVY_FOL = new Pose()
            .legs(-0.70f, 0.10f, 0.94f, -0.20f, 0.52f, -0.10f, 0.50f, 0.11f)
            .torso(0.56f, -0.24f, 0.05f)
            .head(0.10f, 0.16f, -0.04f)
            .armR(-0.70f, 0.0f, -0.08f, -0.20f, 0.0f)
            .armL(-0.85f, 0.0f, -0.50f, -0.40f, 0.0f)
            .staff(2.62f, 0.0f, 0.26f)
            .crouch(0.13f);

    private static final Pose SPIN_POSE = new Pose()
            .legs(-0.46f, 0.16f, 0.62f, -0.10f, 0.40f, -0.16f, 0.58f, 0.08f)
            .torso(0.16f, 0.26f, 0.14f)
            .head(-0.06f, -0.24f, -0.08f)
            .armR(0.10f, 0.0f, 1.32f, -0.28f, 0.0f)
            .armL(-0.12f, 0.0f, -1.18f, -0.42f, 0.0f)
            .staff(0.18f, 0.0f, -0.10f)
            .crouch(0.12f);

    private static final Pose JUMP_RISE = new Pose()
            .legs(-0.62f, 0.06f, 0.95f, -0.34f, 0.34f, -0.05f, 0.28f, 0.12f)
            .torso(-0.06f, 0.10f, 0.0f)
            .head(-0.14f, -0.05f, 0.0f)
            .armR(-0.72f, 0.06f, 0.34f, -0.86f, -0.04f)
            .armL(-0.46f, -0.06f, -0.52f, -0.74f, 0f)
            .staff(-0.72f, 0f, -0.30f)
            .crouch(0.0f);

    private static final Pose JUMP_FALL = new Pose()
            .legs(-0.30f, 0.08f, 0.55f, -0.22f, 0.30f, -0.06f, 0.42f, 0.10f)
            .torso(0.10f, 0.06f, 0.0f)
            .head(-0.06f, -0.10f, 0f)
            .armR(-0.95f, 0.08f, 0.42f, -0.70f, 0f)
            .armL(-0.60f, -0.08f, -0.62f, -0.60f, 0f)
            .staff(-0.95f, 0f, -0.22f)
            .crouch(0.0f);

    private static final Pose LAND_POSE = new Pose()
            .legs(-0.34f, 0.05f, 0.72f, -0.18f, 0.30f, -0.05f, 0.66f, -0.16f)
            .torso(0.42f, 0.08f, 0.0f)
            .head(0.30f, 0f, 0f)
            .armR(-0.35f, 0.05f, 0.40f, -0.95f, 0f)
            .armL(-0.22f, -0.05f, -0.48f, -0.80f, 0f)
            .staff(-0.42f, 0f, -0.28f)
            .crouch(0.16f);

    private static final Pose AIR_SLASH_WU = new Pose()
            .legs(-0.50f, 0.06f, 0.80f, -0.28f, 0.40f, -0.05f, 0.40f, 0.10f)
            .torso(-0.20f, 0.55f, 0.0f)
            .head(-0.10f, -0.30f, 0f)
            .armR(-1.85f, 0.12f, 0.50f, -0.95f, 0f)
            .armL(-1.45f, -0.10f, -0.55f, -0.95f, 0f)
            .staff(-1.20f, 0f, -0.35f)
            .crouch(0.0f);

    private static final Pose AIR_SLASH_ST = new Pose()
            .legs(-0.30f, 0.08f, 0.62f, -0.20f, 0.55f, -0.06f, 0.36f, 0.14f)
            .torso(0.40f, -0.55f, 0.04f)
            .head(0.12f, 0.30f, 0f)
            .armR(-0.85f, -0.05f, 0.95f, -0.30f, 0f)
            .armL(-0.35f, 0.08f, -0.70f, -0.55f, 0f)
            .staff(1.15f, 0f, -0.95f)
            .crouch(0.04f);

    private static final Pose PLUNGE_WU = new Pose()
            .legs(-0.72f, 0.10f, 1.16f, -0.38f, 0.40f, -0.06f, 0.58f, 0.02f)
            .torso(-0.40f, 0.18f, 0.03f)
            .head(-0.26f, -0.12f, 0.0f)
            .armR(-2.86f, 0.12f, 0.46f, -0.40f, -0.10f)
            .armL(-2.60f, -0.12f, -0.52f, -0.50f, 0f)
            .staff(-1.05f, 0f, -0.20f)
            .crouch(0.0f);

    private static final Pose PLUNGE_ST = new Pose()
            .legs(-0.58f, 0.08f, 0.98f, -0.30f, 0.46f, -0.06f, 0.78f, 0.16f)
            .torso(0.40f, -0.03f, 0.0f)
            .head(0.12f, 0.04f, 0.0f)
            .armR(-1.32f, 0.02f, 0.08f, -0.18f, 0.0f)
            .armL(-1.08f, -0.02f, -0.18f, -0.30f, 0f)
            .staff(1.98f, 0f, 0.04f)
            .crouch(0.02f);

    private static final Pose PLUNGE_IMPACT = new Pose()
            .legs(-0.92f, 0.12f, 1.52f, -0.36f, 0.58f, -0.10f, 1.04f, 0.18f)
            .torso(0.54f, 0.05f, 0.02f)
            .head(0.20f, -0.04f, 0.0f)
            .armR(-0.78f, 0.02f, 0.04f, -0.22f, 0.0f)
            .armL(-0.62f, -0.02f, -0.10f, -0.32f, 0f)
            .staff(2.16f, 0f, 0.02f)
            .crouch(0.17f);

    private static final Pose CHARGE_POSE = new Pose()
            .legs(0.30f, 0.05f, 0.42f, -0.05f, -0.28f, -0.05f, 0.40f, 0.04f)
            .torso(-0.30f, 0.66f, -0.05f)
            .head(-0.14f, -0.36f, 0.03f)
            .armR(-2.62f, 0.14f, 0.50f, -0.78f, -0.06f)
            .armL(-2.28f, -0.10f, -0.62f, -1.02f, 0f)
            .staff(-1.48f, 0f, -0.36f)
            .crouch(0.05f);

    private static final Pose EXEC_WIND = new Pose()
            .legs(-0.62f, 0.06f, 0.96f, -0.26f, 0.50f, -0.06f, 0.38f, 0.12f)
            .torso(-0.14f, 0.44f, 0.02f)
            .head(-0.02f, -0.30f, 0.0f)
            .armR(0.06f, 0.16f, 0.54f, -1.18f, -0.05f)
            .armL(-0.28f, -0.12f, -0.40f, -0.72f, 0f)
            .staff(-1.36f, 0.14f, 0.12f)
            .crouch(0.08f);

    private static final Pose EXEC_RAISE = new Pose()
            .legs(-0.58f, 0.08f, 1.08f, -0.22f, 0.48f, -0.08f, 0.34f, 0.10f)
            .torso(-0.34f, 0.28f, -0.04f)
            .head(-0.22f, -0.16f, 0.02f)
            .armR(-2.72f, 0.14f, 0.60f, -0.42f, -0.06f)
            .armL(-2.48f, -0.14f, -0.52f, -0.56f, 0f)
            .staff(-0.28f, 0.02f, -0.46f)
            .crouch(0.02f);

    private static final Pose EXEC_STAB = new Pose()
            .legs(-0.92f, 0.10f, 1.28f, -0.28f, 0.58f, -0.08f, 0.32f, 0.16f)
            .torso(0.58f, -0.14f, 0.02f)
            .head(0.22f, 0.10f, 0f)
            .armR(-1.58f, -0.02f, 0.02f, -0.08f, 0f)
            .armL(-1.42f, 0.02f, -0.16f, -0.14f, 0f)
            .staff(1.56f, -0.12f, 0.0f)
            .crouch(0.10f);

    private static final Pose EXEC_HOLD = new Pose()
            .legs(-0.98f, 0.11f, 1.36f, -0.30f, 0.64f, -0.09f, 0.30f, 0.17f)
            .torso(0.66f, -0.20f, 0.04f)
            .head(0.26f, 0.14f, 0f)
            .armR(-1.66f, -0.02f, -0.04f, -0.04f, 0f)
            .armL(-1.50f, 0.02f, -0.20f, -0.12f, 0f)
            .staff(1.60f, -0.16f, -0.02f)
            .crouch(0.12f);

    private static final Pose EXEC_PULL = new Pose()
            .legs(-0.82f, 0.09f, 1.10f, -0.24f, 0.52f, -0.08f, 0.40f, 0.13f)
            .torso(-0.16f, -0.34f, -0.02f)
            .head(0.04f, 0.24f, 0f)
            .armR(-0.92f, 0.04f, 0.26f, -0.52f, 0f)
            .armL(-0.78f, -0.02f, -0.30f, -0.58f, 0f)
            .staff(-0.92f, -0.20f, 0.12f)
            .crouch(0.04f);

    private static final Pose EXEC_FOL = new Pose()
            .legs(-0.70f, 0.08f, 0.94f, -0.18f, 0.46f, -0.07f, 0.34f, 0.10f)
            .torso(0.18f, -0.18f, 0.0f)
            .head(0.08f, 0.10f, 0f)
            .armR(-0.44f, 0.0f, 0.14f, -0.36f, 0f)
            .armL(-0.32f, 0f, -0.22f, -0.42f, 0f)
            .staff(0.96f, -0.08f, 0.06f)
            .crouch(0.08f);

    private static final Pose ROLL_TUCK = new Pose()
            .legs(-2.00f, 0.12f, 2.45f, -0.35f, -2.15f, -0.12f, 2.55f, -0.35f)
            .torso(1.15f, 0.0f, 0.0f)
            .head(0.50f, 0.0f, 0.0f)
            .armR(-1.30f, 0.10f, 0.32f, -2.10f, 0.0f)
            .armL(-1.30f, -0.10f, -0.32f, -2.10f, 0.0f)
            .staff(0.55f, 0.0f, 0.8f)
            .crouch(0.0f);

    private static final Pose HURT_POSE = new Pose()
            .legs(0.30f, 0.04f, 0.42f, 0.05f, -0.30f, -0.04f, 0.38f, -0.02f)
            .torso(-0.50f, -0.22f, -0.12f)
            .head(-0.30f, 0.30f, 0.10f)
            .armR(0.30f, 0.0f, 0.65f, -0.70f, 0.0f)
            .armL(-0.70f, 0.0f, -0.80f, -1.00f, 0.0f)
            .staff(-0.90f, 0.0f, -0.55f)
            .crouch(0.06f);

    private static final Pose DEAD_POSE = new Pose()
            .legs(-0.70f, 0.08f, 1.30f, -0.10f, 0.45f, -0.10f, 0.90f, 0.10f)
            .torso(0.45f, -0.20f, 0.15f)
            .head(0.35f, 0.20f, 0.10f)
            .armR(0.35f, 0.0f, 0.80f, -0.35f, 0.0f)
            .armL(0.15f, 0.0f, -0.90f, -0.50f, 0.0f)
            .staff(-1.40f, 0.0f, 0.60f)
            .crouch(0.12f);

    enum State {
        FREE, ROLL, ATTACK, SKILL, CHARGE, EXECUTE, HURT, DEAD, PLUNGE_LAND
    }

    enum AttackKind {
        LIGHT, HEAVY, RUN, ROLL, AIR, PLUNGE
    }

    final Vector3f pos = new Vector3f();
    final Vector3f velocity = new Vector3f();
    float yaw;
    float hpMax = HP_MAX;
    float stMax = ST_MAX;
    float mpMax = MP_MAX;
    float hp = HP_MAX;
    float stamina = ST_MAX;
    float mana = MP_MAX;
    boolean alive = true;
    boolean mounted;
    boolean evMountDown;
    float mountHp = 100f;
    float mountMaxHp = 100f;
    int mountJumps;
    boolean doubleJumped;

    float damageMul = 1f;
    private float groundY;
    private boolean arenaBounds = true;
    private final List<Target> targets = new ArrayList<>();
    State state = State.FREE;
    float stateTime;

    boolean evHit;
    boolean evHeavyHit;
    boolean evSkillHit;
    boolean evHurt;
    boolean evDied;
    boolean evTired;
    boolean evNoMana;
    boolean evJump;
    boolean evLand;
    boolean evPlunge;
    boolean evExecute;
    boolean evChargeReady;
    float evDamage;

    private final AssetManager am;
    private final Fx fx;
    private final Node root = new Node("destinedOne");
    private final Node rollPivot = new Node("rollPivot");
    private final Node body = new Node("body");
    private Node legL;
    private Node legR;
    private Node kneeL;
    private Node kneeR;
    private Node ankleL;
    private Node ankleR;
    private Node torso;
    private Node head;
    private Node armL;
    private Node armR;
    private Node elbowL;
    private Node elbowR;
    private Node wristL;
    private Node wristR;
    private Node weapon;
    private Node staffTip;
    private Node staffMid;
    private Material goldMat;
    private Geometry fireRingOuter;
    private Geometry fireRingInner;
    private Material fireRingOuterMat;
    private Material fireRingInnerMat;
    private final PointLight fireLight = new PointLight();
    private float fireRingBlend;

    private final Node[] tail = new Node[5];
    private final Node[] skirt = new Node[5];
    private Node capeL1;
    private Node capeL2;
    private Node capeR1;
    private Node capeR2;
    private Node scarfL;
    private Node scarfR;
    private Node ribbonL;
    private Node ribbonR;
    private Node plume;

    private final Vector3f moveDir = new Vector3f();
    private final Vector3f rollDir = new Vector3f();
    private final Vector3f attackForward = new Vector3f();
    private final Vector3f tmp = new Vector3f();
    private final Quaternion tmpQ = new Quaternion();
    private final Quaternion staffQ = new Quaternion();
    private final Pose pose = new Pose();

    private boolean wantLight;
    private boolean wantHeavy;
    private boolean wantRoll;
    private boolean wantSkill;
    private boolean wantJump;
    private boolean wantCharge;
    private boolean wantChargeRelease;
    private boolean wantPlunge;
    float velY;
    boolean airborne;
    private float chargeTime;
    private float chargeLevel;
    private float chargeFxTimer;
    private float landTimer;
    private boolean isAirAttack;
    private boolean isPlunge;
    private float chargedLevel;
    private boolean execHitDone;
    private float manaDelay;
    private float skillCycleEnd;
    private float skillTickTimer;
    private float fireTimer;
    private float spinAngle;
    private float spinBlend;
    private boolean bufferedLight;
    private boolean bufferedHeavy;
    private boolean isHeavy;
    AttackKind attackKind = AttackKind.LIGHT;
    private boolean hitDone;
    private int comboIndex;
    private float comboGrace;
    private final Pose attackEntry = new Pose();
    private boolean sprintingNow;
    private final Quaternion staffSmooth = new Quaternion();
    private boolean staffReady;
    private float attackDuration;
    private float attackWindup;
    private float attackHitTime;
    float invuln;
    private float staminaDelay;
    private float walkPhase;
    private float animTime;
    private float rollTuck;

    Player(AssetManager am, Node parent, Fx fx) {
        this.am = am;
        this.fx = fx;
        buildModel();
        Gfx.tangents(root);
        buildFireRing();
        parent.attachChild(root);
        root.setShadowMode(RenderQueue.ShadowMode.Cast);
    }

    private void buildFireRing() {
        fireRingOuterMat = fireRingMaterial();
        fireRingOuter = new Geometry("fireRingOuter", Gfx.torusMesh(1.72f, 0.05f, 72, 8));
        fireRingOuter.setMaterial(fireRingOuterMat);
        fireRingOuter.setLocalTranslation(0f, 0.055f, 0f);
        fireRingOuter.setQueueBucket(RenderQueue.Bucket.Transparent);
        fireRingOuter.setShadowMode(RenderQueue.ShadowMode.Off);
        fireRingOuter.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
        root.attachChild(fireRingOuter);

        fireRingInnerMat = fireRingMaterial();
        fireRingInner = new Geometry("fireRingInner", Gfx.torusMesh(1.2f, 0.032f, 64, 8));
        fireRingInner.setMaterial(fireRingInnerMat);
        fireRingInner.setLocalTranslation(0f, 0.05f, 0f);
        fireRingInner.setQueueBucket(RenderQueue.Bucket.Transparent);
        fireRingInner.setShadowMode(RenderQueue.ShadowMode.Off);
        fireRingInner.setCullHint(com.jme3.scene.Spatial.CullHint.Always);
        root.attachChild(fireRingInner);

        fireLight.setPosition(new Vector3f(0f, 1.2f, 0f));
        fireLight.setRadius(9f);
        fireLight.setColor(ColorRGBA.Black);
        root.addLight(fireLight);
    }

    private Material fireRingMaterial() {
        Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", new ColorRGBA(1f, 0.4f, 0.06f, 0f));
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
        m.getAdditionalRenderState().setDepthWrite(false);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        return m;
    }

    private void buildModel() {
        rollPivot.setLocalTranslation(0f, 1.02f, 0f);
        root.attachChild(rollPivot);
        body.setLocalTranslation(0f, -1.02f, 0f);
        rollPivot.attachChild(body);

        Material fur = Gfx.lit(am, new ColorRGBA(0.46f, 0.35f, 0.25f, 1f),
                new ColorRGBA(0.17f, 0.12f, 0.08f, 1f),
                new ColorRGBA(0.08f, 0.06f, 0.045f, 1f), 8f);
        Material furDark = Gfx.lit(am, new ColorRGBA(0.25f, 0.19f, 0.14f, 1f),
                new ColorRGBA(0.09f, 0.065f, 0.045f, 1f),
                new ColorRGBA(0.04f, 0.03f, 0.024f, 1f), 6f);
        Material furLight = Gfx.lit(am, new ColorRGBA(0.62f, 0.49f, 0.34f, 1f),
                new ColorRGBA(0.23f, 0.17f, 0.11f, 1f),
                new ColorRGBA(0.08f, 0.065f, 0.045f, 1f), 8f);
        Material furCream = Gfx.lit(am, new ColorRGBA(0.70f, 0.58f, 0.43f, 1f),
                new ColorRGBA(0.26f, 0.20f, 0.14f, 1f),
                new ColorRGBA(0.07f, 0.06f, 0.05f, 1f), 8f);
        Material skin = Gfx.lit(am, new ColorRGBA(0.72f, 0.55f, 0.41f, 1f),
                new ColorRGBA(0.24f, 0.17f, 0.12f, 1f),
                new ColorRGBA(0.07f, 0.055f, 0.04f, 1f), 10f);
        Material cloth = Gfx.lit(am, new ColorRGBA(0.55f, 0.10f, 0.065f, 1f),
                new ColorRGBA(0.18f, 0.035f, 0.024f, 1f),
                new ColorRGBA(0.10f, 0.04f, 0.03f, 1f), 12f);
        Material clothDark = Gfx.lit(am, new ColorRGBA(0.26f, 0.075f, 0.055f, 1f),
                new ColorRGBA(0.085f, 0.028f, 0.02f, 1f),
                new ColorRGBA(0.05f, 0.03f, 0.024f, 1f), 8f);
        Material armor = Gfx.lit(am, new ColorRGBA(0.46f, 0.39f, 0.30f, 1f),
                new ColorRGBA(0.15f, 0.13f, 0.10f, 1f),
                new ColorRGBA(0.55f, 0.42f, 0.26f, 1f), 52f);
        Material gold = Gfx.lit(am, new ColorRGBA(0.78f, 0.56f, 0.2f, 1f),
                new ColorRGBA(0.24f, 0.165f, 0.06f, 1f),
                new ColorRGBA(0.75f, 0.68f, 0.44f, 1f), 82f);
        goldMat = gold;
        Material leather = Gfx.lit(am, new ColorRGBA(0.48f, 0.33f, 0.22f, 1f),
                new ColorRGBA(0.16f, 0.11f, 0.07f, 1f));
        Material wrap = Gfx.lit(am, new ColorRGBA(0.62f, 0.51f, 0.37f, 1f),
                new ColorRGBA(0.22f, 0.17f, 0.11f, 1f));
        Material eye = Gfx.unshaded(am, new ColorRGBA(1f, 0.66f, 0.13f, 1f));
        Material feather = Gfx.lit(am, new ColorRGBA(0.72f, 0.09f, 0.045f, 1f),
                new ColorRGBA(0.22f, 0.03f, 0.018f, 1f),
                new ColorRGBA(0.18f, 0.05f, 0.02f, 1f), 16f);
        Material staffShaft = Gfx.lit(am, new ColorRGBA(0.58f, 0.41f, 0.25f, 1f),
                new ColorRGBA(0.19f, 0.12f, 0.07f, 1f),
                new ColorRGBA(0.3f, 0.22f, 0.13f, 1f), 40f);

        Gfx.mapped(fur, am, "faux_fur", 0.9f);
        Gfx.mapped(furDark, am, "faux_fur", 0.9f);
        Gfx.mapped(furLight, am, "faux_fur", 0.9f);
        Gfx.mapped(furCream, am, "faux_fur", 0.9f);
        Gfx.mapped(skin, am, "brown_leather", 0.6f);
        Gfx.mapped(cloth, am, "rough_linen", 1.1f);
        Gfx.mapped(clothDark, am, "rough_linen", 1.1f);
        Gfx.mapped(armor, am, "metal_plate", 0.7f);
        Gfx.mapped(leather, am, "brown_leather", 0.7f);
        Gfx.mapped(wrap, am, "rough_linen", 0.8f);
        Gfx.mapped(feather, am, "rough_linen", 1.0f);
        Gfx.mapped(staffShaft, am, "wood_planks", 0.7f);

        legL = pivot(body, "legL", -0.185f, 0.98f, 0f);
        legR = pivot(body, "legR", 0.185f, 0.98f, 0f);
        for (Node leg : new Node[]{legL, legR}) {
            float side = leg == legL ? -1f : 1f;
            Gfx.taperedCylinder("thigh", 0.15f, 0.115f, 0.5f, cloth, leg, 0f, -0.25f, 0f);
            Geometry pant = Gfx.taperedCylinder("pant", 0.16f, 0.12f, 0.3f, clothDark,
                    leg, 0f, -0.32f, 0f);
            pant.setLocalScale(1f, 1f, 1.06f);
            Gfx.torus("thighCord", 0.132f, 0.012f, leather, leg, 0f, -0.17f, 0f);
            Gfx.box("thighStripe", 0.012f, 0.12f, 0.018f, gold, leg,
                    side * 0.145f, -0.28f, 0.02f);

            Node knee = pivot(leg, "knee", 0f, -0.48f, 0f);
            Gfx.taperedCylinder("shin", 0.09f, 0.072f, 0.4f, wrap, knee, 0f, -0.2f, 0f);
            Geometry kneeCap = Gfx.sphere("kneeCap", 0.105f, 10, armor,
                    knee, 0f, -0.02f, 0.055f);
            kneeCap.setLocalScale(1f, 0.8f, 0.72f);
            Gfx.torus("kneeRing", 0.104f, 0.012f, gold, knee, 0f, 0.005f, 0f);
            for (int i = 0; i < 3; i++) {
                Gfx.torus("shinWrap", 0.087f - i * 0.004f, 0.011f, leather,
                        knee, 0f, -0.09f - i * 0.09f, 0f);
            }
            Gfx.box("shinGuard", 0.07f, 0.115f, 0.025f, armor, knee, 0f, -0.18f, 0.073f);

            Node ankle = pivot(knee, "ankle", 0f, -0.365f, 0f);
            Gfx.box("boot", 0.105f, 0.065f, 0.17f, furDark, ankle, 0f, -0.055f, 0.05f);
            Gfx.box("bootCuff", 0.115f, 0.045f, 0.11f, cloth, ankle, 0f, -0.005f, 0.01f);
            Gfx.box("toeCap", 0.094f, 0.05f, 0.07f, armor, ankle, 0f, -0.072f, 0.2f);
            Gfx.box("sole", 0.108f, 0.016f, 0.19f, leather, ankle, 0f, -0.112f, 0.055f);
            Gfx.torus("anklet", 0.086f, 0.009f, gold, ankle, 0f, -0.03f, 0.01f);
            if (leg == legL) {
                kneeL = knee;
                ankleL = ankle;
            } else {
                kneeR = knee;
                ankleR = ankle;
            }
        }

        skirt[0] = clothPanel(body, "skirtFL", -0.10f, 0.90f, 0.19f,
                0.155f, 0.45f, 0.012f, cloth, -0.08f, 0.06f);
        skirt[1] = clothPanel(body, "skirtFR", 0.10f, 0.90f, 0.19f,
                0.155f, 0.45f, 0.012f, cloth, -0.08f, -0.06f);
        skirt[2] = clothPanel(body, "skirtBL", -0.10f, 0.90f, -0.19f,
                0.16f, 0.50f, 0.012f, cloth, 0.15f, 0.05f);
        skirt[3] = clothPanel(body, "skirtBR", 0.10f, 0.90f, -0.19f,
                0.16f, 0.50f, 0.012f, cloth, 0.15f, -0.05f);
        skirt[4] = clothPanel(body, "skirtSideL", -0.245f, 0.885f, 0.01f,
                0.07f, 0.42f, 0.012f, clothDark, 0.06f, 0.18f);
        clothPanel(body, "skirtSideR", 0.245f, 0.885f, 0.01f,
                0.07f, 0.42f, 0.012f, clothDark, 0.06f, -0.18f);
        clothPanel(body, "tabard", 0f, 0.9f, 0.205f,
                0.085f, 0.56f, 0.009f, clothDark, -0.05f, 0f);

        capeL1 = clothPanel(body, "capeL1", -0.09f, 0.90f, -0.215f,
                0.125f, 0.34f, 0.01f, clothDark, 0.16f, 0.04f);
        capeL2 = clothPanel(capeL1, "capeL2", 0f, -0.34f, -0.004f,
                0.115f, 0.32f, 0.01f, clothDark, 0.08f, 0.03f);
        capeR1 = clothPanel(body, "capeR1", 0.09f, 0.90f, -0.215f,
                0.125f, 0.34f, 0.01f, clothDark, 0.16f, -0.04f);
        capeR2 = clothPanel(capeR1, "capeR2", 0f, -0.34f, -0.004f,
                0.115f, 0.32f, 0.01f, clothDark, 0.08f, -0.03f);

        Gfx.taperedCylinder("hipRobe", 0.245f, 0.262f, 0.3f, clothDark,
                body, 0f, 0.83f, 0f);
        Gfx.torus("belt", 0.252f, 0.028f, leather, body, 0f, 0.925f, 0f);
        Gfx.torus("beltGold", 0.255f, 0.008f, gold, body, 0f, 0.945f, 0f);
        Gfx.box("buckle", 0.065f, 0.055f, 0.028f, gold, body, 0f, 0.925f, 0.247f);
        Geometry buckleGem = Gfx.sphere("buckleGem", 0.021f, 8, cloth,
                body, 0f, 0.925f, 0.273f);
        buckleGem.setLocalScale(1f, 1.3f, 0.5f);
        Gfx.sphere("sashKnot", 0.055f, 10, cloth, body, 0.255f, 0.90f, 0.10f);
        clothPanel(body, "sashTailA", 0.26f, 0.87f, 0.105f,
                0.05f, 0.3f, 0.008f, cloth, 0.04f, 0.08f);
        clothPanel(body, "sashTailB", 0.24f, 0.86f, 0.115f,
                0.04f, 0.24f, 0.008f, cloth, 0.1f, 0.12f);

        for (int side = -1; side <= 1; side += 2) {
            Node tasset = pivot(body, "tasset", side * 0.135f, 0.9f, 0.225f);
            tasset.setLocalRotation(tmpQ.fromAngles(-0.05f, side * -0.15f, side * 0.08f));
            Gfx.box("tassetPlate", 0.105f, 0.115f, 0.016f, armor, tasset, 0f, -0.105f, 0f);
            Gfx.box("tassetTrim", 0.112f, 0.013f, 0.02f, gold, tasset, 0f, -0.222f, 0f);
            Gfx.sphere("tassetStud", 0.021f, 8, gold, tasset, 0f, -0.04f, 0.02f);

            Node sidePlate = pivot(body, "sideTasset", side * 0.272f, 0.9f, 0.01f);
            sidePlate.setLocalRotation(tmpQ.fromAngles(0.03f, side * FastMath.HALF_PI, 0f));
            Gfx.box("sideTassetPlate", 0.085f, 0.095f, 0.016f, armor,
                    sidePlate, 0f, -0.095f, 0f);
            Gfx.box("sideTassetTrim", 0.09f, 0.012f, 0.02f, gold,
                    sidePlate, 0f, -0.192f, 0f);
        }

        torso = pivot(body, "torso", 0f, 0.98f, 0f);
        Gfx.taperedCylinder("waistRobe", 0.235f, 0.27f, 0.34f, clothDark,
                torso, 0f, 0.1f, 0f);
        Gfx.taperedCylinder("chestRobe", 0.27f, 0.235f, 0.34f, cloth,
                torso, 0f, 0.4f, 0f);
        Gfx.box("breastplate", 0.27f, 0.17f, 0.105f, armor, torso, 0f, 0.44f, 0.075f);
        Gfx.box("breastplateTrim", 0.278f, 0.018f, 0.115f, gold, torso, 0f, 0.575f, 0.07f);
        Gfx.box("breastplateTrimLow", 0.272f, 0.014f, 0.112f, gold, torso, 0f, 0.302f, 0.07f);
        Gfx.sphere("emblem", 0.062f, 12, gold, torso, 0f, 0.455f, 0.155f);
        Geometry emblemCore = Gfx.sphere("emblemCore", 0.026f, 8, cloth,
                torso, 0f, 0.455f, 0.19f);
        emblemCore.setLocalScale(1f, 1.2f, 0.45f);

        Gfx.box("backPlate", 0.245f, 0.21f, 0.055f, armor, torso, 0f, 0.43f, -0.145f);
        Gfx.box("backTrim", 0.25f, 0.016f, 0.062f, gold, torso, 0f, 0.56f, -0.145f);
        Gfx.sphere("spineStud", 0.037f, 8, gold, torso, 0f, 0.42f, -0.19f);

        for (int side = -1; side <= 1; side += 2) {
            Geometry strap = Gfx.box("shoulderStrap", 0.035f, 0.22f, 0.024f, leather,
                    torso, side * 0.155f, 0.555f, 0.105f);
            strap.rotate(0f, 0f, -side * 0.18f);
        }
        for (int i = 0; i < 6; i++) {
            float a = -0.62f + i * 0.25f;
            Gfx.sphere("prayerBead", 0.021f, 8, i % 2 == 0 ? gold : leather,
                    torso, FastMath.sin(a) * 0.19f, 0.59f - FastMath.cos(a) * 0.045f,
                    0.135f + FastMath.cos(a) * 0.055f);
        }
        Geometry collar = Gfx.sphere("collar", 0.215f, 14, furDark,
                torso, 0f, 0.675f, -0.015f);
        collar.setLocalScale(1.28f, 0.4f, 1.08f);
        Gfx.torus("collarWrap", 0.19f, 0.021f, cloth, torso, 0f, 0.7f, 0f);
        for (int i = 0; i < 11; i++) {
            float a = -2.55f + i * (5.1f / 10f);
            float len = 0.135f + (i % 3) * 0.024f + (Math.abs(a) > 1.7f ? 0.045f : 0f);
            Node yawNode = pivot(torso, "mane" + i,
                    FastMath.sin(a) * 0.168f, 0.685f, FastMath.cos(a) * 0.168f);
            yawNode.setLocalRotation(new Quaternion().fromAngles(0f, a, 0f));
            Node tilt = pivot(yawNode, "maneTilt", 0f, 0f, 0f);
            tilt.setLocalRotation(new Quaternion().fromAngles(0.62f, 0f, 0f));
            Gfx.taperedCylinder("maneTip", 0.002f, 0.042f, len,
                    i % 2 == 0 ? furLight : fur, tilt, 0f, len * 0.5f, 0f);
        }

        scarfL = clothPanel(torso, "scarfL", -0.13f, 0.6f, -0.14f,
                0.055f, 0.42f, 0.01f, cloth, 0.32f, 0.06f);
        scarfR = clothPanel(torso, "scarfR", 0.12f, 0.62f, -0.15f,
                0.045f, 0.36f, 0.01f, cloth, 0.38f, -0.05f);

        head = pivot(torso, "head", 0f, 0.81f, 0.015f);
        Gfx.cylinder("neck", 0.08f, 0.15f, furDark, head, 0f, 0.02f, 0f);
        Geometry skull = Gfx.sphere("skull", 0.19f, 18, fur, head, 0f, 0.19f, 0f);
        skull.setLocalScale(1.03f, 1.04f, 0.97f);

        Geometry muzzle = Gfx.sphere("muzzle", 0.095f, 14, skin,
                head, 0f, 0.14f, 0.172f);
        muzzle.setLocalScale(1.2f, 0.85f, 1.02f);
        Geometry jaw = Gfx.sphere("jaw", 0.078f, 12, skin,
                head, 0f, 0.088f, 0.168f);
        jaw.setLocalScale(1.12f, 0.72f, 0.92f);

        for (int side = -1; side <= 1; side += 2) {
            Geometry eyePatch = Gfx.sphere("eyePatch", 0.062f, 10, furLight,
                    head, side * 0.068f, 0.212f, 0.158f);
            eyePatch.setLocalScale(1f, 0.78f, 0.3f);
            for (int i = 0; i < 3; i++) {
                Node tuft = pivot(head, "cheekTuft",
                        side * (0.112f + i * 0.011f), 0.152f - i * 0.043f,
                        0.078f - i * 0.007f);
                tuft.setLocalRotation(new Quaternion().fromAngles(0.16f, 0f,
                        side * (1.12f + i * 0.2f)));
                Gfx.taperedCylinder("cheekFur", 0.003f, 0.032f,
                        0.15f - i * 0.02f, i == 1 ? furLight : fur,
                        tuft, 0f, 0.062f, 0f);
            }
            Geometry ear = Gfx.sphere("ear", 0.083f, 10, furLight,
                    head, side * 0.198f, 0.19f, 0f);
            ear.setLocalScale(0.5f, 1f, 0.9f);
            Geometry innerEar = Gfx.sphere("innerEar", 0.048f, 8, skin,
                    head, side * 0.202f, 0.19f, 0.028f);
            innerEar.setLocalScale(0.36f, 0.92f, 0.7f);
            Geometry socket = Gfx.sphere("eyeSocket", 0.045f, 10, furDark,
                    head, side * 0.073f, 0.218f, 0.155f);
            socket.setLocalScale(1.05f, 0.95f, 0.34f);
            Gfx.sphere("eye", 0.022f, 8, eye, head, side * 0.073f, 0.218f, 0.18f);
            Gfx.sphere("pupil", 0.0095f, 6, furDark, head, side * 0.073f, 0.218f, 0.197f);
            Geometry brow = Gfx.box("brow", 0.077f, 0.018f, 0.03f, furDark,
                    head, side * 0.07f, 0.252f, 0.155f);
            brow.rotate(0f, 0f, side * 0.16f);
        }
        Geometry nose = Gfx.sphere("nose", 0.03f, 8, furDark,
                head, 0f, 0.174f, 0.267f);
        nose.setLocalScale(1.25f, 0.7f, 0.7f);
        Gfx.box("mouth", 0.055f, 0.0075f, 0.011f, furDark,
                head, 0f, 0.112f, 0.271f);
        Geometry chin = Gfx.sphere("chinTuft", 0.055f, 10, furCream,
                head, 0f, 0.058f, 0.242f);
        chin.setLocalScale(1.05f, 0.8f, 0.7f);

        Gfx.torus("headband", 0.174f, 0.021f, gold, head, 0f, 0.284f, 0f);
        Gfx.box("bandPlate", 0.078f, 0.025f, 0.02f, gold, head, 0f, 0.292f, 0.168f);
        Gfx.sphere("bandGem", 0.029f, 8, eye, head, 0f, 0.292f, 0.19f);
        Gfx.sphere("bandKnot", 0.036f, 8, gold, head, 0f, 0.282f, -0.174f);
        ribbonL = clothPanel(head, "ribbonL", -0.034f, 0.278f, -0.178f,
                0.026f, 0.36f, 0.008f, cloth, 0.5f, 0.09f);
        ribbonR = clothPanel(head, "ribbonR", 0.034f, 0.278f, -0.178f,
                0.023f, 0.32f, 0.008f, cloth, 0.58f, -0.07f);

        for (int i = 0; i < 4; i++) {
            Node tuft = pivot(head, "hairTuft", -0.075f + i * 0.05f,
                    0.345f, -0.055f - Math.abs(i - 1.5f) * 0.02f);
            tuft.setLocalRotation(new Quaternion().fromAngles(-0.55f, 0f,
                    (i - 1.5f) * 0.26f));
            Gfx.taperedCylinder("hairTuftCone", 0.003f, 0.037f, 0.14f,
                    fur, tuft, 0f, 0.055f, 0f);
        }

        plume = pivot(head, "plume", 0f, 0.3f, -0.11f);
        Gfx.torus("plumeCrown", 0.048f, 0.012f, gold, plume, 0f, 0.005f, 0f);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 3; i++) {
                float spread = 0.16f + i * 0.2f;
                Node f1 = pivot(plume, "featherBase", side * (0.018f + i * 0.018f),
                        0.012f + i * 0.008f, -0.004f - i * 0.014f);
                f1.setLocalRotation(tmpQ.fromAngles(-0.36f - i * 0.1f, 0f, -side * spread));
                float len1 = 0.30f - i * 0.031f;
                Geometry blade = Gfx.taperedCylinder("phoenixFeather",
                        0.004f, 0.03f, len1,
                        feather, f1, 0f, len1 * 0.5f, 0f);
                blade.setLocalScale(1.6f, 1f, 0.26f);
                Gfx.torus("featherBand", 0.026f, 0.006f, gold, f1, 0f, 0.035f, 0f);
                Node f2 = pivot(f1, "featherTip", 0f, len1, 0f);
                f2.setLocalRotation(tmpQ.fromAngles(0.34f, 0f, -side * 0.12f));
                float len2 = 0.195f - i * 0.018f;
                Geometry tipBlade = Gfx.taperedCylinder("featherTip",
                        0.002f, 0.019f, len2,
                        feather, f2, 0f, len2 * 0.5f, 0f);
                tipBlade.setLocalScale(1.8f, 1f, 0.22f);
                Gfx.torus("featherTipBand", 0.017f, 0.005f, gold, f2, 0f, 0.008f, 0f);
            }
        }

        armL = pivot(torso, "armL", -0.325f, 0.55f, 0f);
        armR = pivot(torso, "armR", 0.325f, 0.55f, 0f);
        for (Node arm : new Node[]{armL, armR}) {
            boolean left = arm == armL;
            float side = left ? -1f : 1f;
            if (left) {
                Geometry underFur = Gfx.sphere("pauldronFur", 0.118f, 10, furDark,
                        arm, side * 0.012f, 0.0f, -0.01f);
                underFur.setLocalScale(1.12f, 0.9f, 1.04f);
                for (int i = 0; i < 3; i++) {
                    Geometry plate = Gfx.box("pauldronPlate", 0.142f - i * 0.016f,
                            0.026f, 0.142f - i * 0.013f, armor,
                            arm, side * (0.02f + i * 0.006f), 0.015f - i * 0.046f, 0f);
                    plate.rotate(0f, 0f, side * (0.16f + i * 0.07f));
                }
                Gfx.torus("pauldronGold", 0.138f, 0.011f, gold, arm,
                        side * 0.018f, 0.03f, 0f);
                Gfx.sphere("pauldronStud", 0.026f, 8, gold, arm,
                        side * 0.15f, 0.06f, 0.03f);
            } else {
                Geometry shoulderFur = Gfx.sphere("shoulderFur", 0.12f, 10, furLight,
                        arm, side * 0.018f, -0.015f, 0f);
                shoulderFur.setLocalScale(1.08f, 0.82f, 1f);
                for (int i = 0; i < 3; i++) {
                    Node tuft = pivot(arm, "shoulderTuft", side * 0.085f,
                            0.005f - i * 0.035f, -0.035f + i * 0.035f);
                    tuft.setLocalRotation(new Quaternion().fromAngles(
                            0f, 0f, -side * (1.75f + i * 0.22f)));
                    Gfx.taperedCylinder("shoulderFurTip", 0.003f, 0.031f, 0.14f,
                            fur, tuft, 0f, 0.055f, 0f);
                }
                Gfx.torus("armBand", 0.108f, 0.015f, gold, arm, 0f, -0.135f, 0f);
            }
            Gfx.taperedCylinder("upperArm", 0.085f, 0.072f, 0.36f,
                    fur, arm, 0f, -0.2f, 0f);
            Gfx.torus("upperArmWrap", 0.082f, 0.011f, leather, arm, 0f, -0.34f, 0f);

            Node elbow = pivot(arm, "elbow", 0f, -0.4f, 0f);
            Geometry elbowCap = Gfx.sphere("elbowCap", 0.076f, 10, armor, elbow, 0f, 0f, 0f);
            elbowCap.setLocalScale(1f, 0.82f, 0.9f);
            Gfx.taperedCylinder("forearm", 0.072f, 0.058f, 0.32f, furDark,
                    elbow, 0f, -0.17f, 0f);
            Gfx.taperedCylinder("bracer", 0.085f, 0.071f, 0.2f, armor, elbow, 0f, -0.205f, 0f);
            Gfx.torus("bracerGold", 0.079f, 0.012f, gold, elbow, 0f, -0.108f, 0f);
            Gfx.torus("wristWrap", 0.069f, 0.012f, leather, elbow, 0f, -0.32f, 0f);

            Node wrist = pivot(elbow, "wrist", 0f, -0.38f, 0f);
            Gfx.sphere("hand", 0.07f, 10, skin, wrist, 0f, -0.02f, 0.012f);
            for (int i = -1; i <= 1; i++) {
                Gfx.box("finger", 0.018f, 0.05f, 0.021f, skin,
                        wrist, i * 0.027f, -0.086f, 0.022f);
            }
            Gfx.box("thumb", 0.022f, 0.045f, 0.022f, skin,
                    wrist, side * 0.05f, -0.042f, 0.036f);
            if (left) {
                elbowL = elbow;
                wristL = wrist;
            } else {
                elbowR = elbow;
                wristR = wrist;
            }
        }

        weapon = new Node("weapon");
        weapon.setLocalTranslation(0f, -0.025f, 0.022f);
        wristR.attachChild(weapon);

        Gfx.cylinder("staff", 0.031f, 2.35f, staffShaft, weapon, 0f, 0.34f, 0f);
        for (int side = -1; side <= 1; side += 2) {
            Gfx.cylinder("staffCap", 0.044f, 0.16f, gold, weapon, 0f, 0.34f + side * 1.17f, 0f);
            Gfx.torus("staffRing", 0.047f, 0.011f, gold, weapon, 0f, 0.34f + side * 0.78f, 0f);
            Gfx.torus("staffRing2", 0.041f, 0.008f, gold, weapon, 0f, 0.34f + side * 0.56f, 0f);
        }
        Gfx.sphere("staffCrown", 0.055f, 10, gold, weapon, 0f, 1.535f, 0f);
        Node tasselNode = pivot(weapon, "staffTassel", 0.052f, 1.44f, 0.015f);
        Gfx.taperedCylinder("tassel", 0.036f, 0.006f, 0.34f, cloth,
                tasselNode, 0f, -0.17f, 0f);
        Gfx.torus("tasselBand", 0.03f, 0.008f, gold, tasselNode, 0f, -0.015f, 0f);

        staffTip = new Node("staffTip");
        staffTip.setLocalTranslation(0f, 1.57f, 0f);
        weapon.attachChild(staffTip);
        staffMid = new Node("staffMid");
        staffMid.setLocalTranslation(0f, 0.9f, 0f);
        weapon.attachChild(staffMid);

        Node tailParent = body;
        for (int i = 0; i < 5; i++) {
            float y = i == 0 ? 0.86f : 0.275f;
            float z = i == 0 ? -0.25f : 0f;
            Node seg = pivot(tailParent, "tail" + i, 0f, y, z);
            seg.setLocalRotation(tmpQ.fromAngles(i == 0 ? -2.05f : 0.30f, 0f,
                    i == 0 ? 0.22f : 0f));
            float r = 0.098f - i * 0.014f;
            Geometry segGeo = Gfx.taperedCylinder("tailSeg", r * 0.8f, r, 0.275f,
                    i >= 3 ? furLight : fur, seg, 0f, 0.138f, 0f);
            segGeo.setLocalScale(1.12f, 1f, 1.08f);
            if (i == 0) {
                Gfx.torus("tailRing", 0.088f, 0.012f, gold, seg, 0f, 0.035f, 0f);
            }
            tail[i] = seg;
            tailParent = seg;
        }
        Geometry tailTuft = Gfx.sphere("tailTuft", 0.092f, 10, furLight,
                tail[4], 0f, 0.3f, 0f);
        tailTuft.setLocalScale(0.85f, 1.55f, 0.85f);
        for (int i = -1; i <= 1; i += 2) {
            Node tuft = pivot(tail[4], "tailTuftSide", i * 0.045f, 0.27f, 0f);
            tuft.setLocalRotation(new Quaternion().fromAngles(0f, 0f, i * 2.4f));
            Gfx.taperedCylinder("tailTuftTip", 0.003f, 0.042f, 0.16f,
                    furLight, tuft, 0f, 0.055f, 0f);
        }

        head.setLocalScale(0.82f);
    }

    private Node clothPanel(Node parent, String name, float x, float y, float z,
                            float halfWidth, float length, float halfThick,
                            Material mat, float rx, float rz) {
        Node n = pivot(parent, name, x, y, z);
        n.setLocalRotation(new Quaternion().fromAngles(rx, 0f, rz));
        float upper = length * 0.56f;
        float lower = length * 0.5f;
        Geometry top = Gfx.taperedCylinder(name + "Cloth", halfWidth * 0.86f,
                halfWidth * 1.02f, upper, mat, n, 0f, -upper * 0.5f, 0f);
        top.setLocalScale(1f, 1f, halfThick / Math.max(0.02f, halfWidth));
        Node tail = pivot(n, name + "Drape", 0f, -upper * 0.92f, 0f);
        tail.setLocalRotation(new Quaternion().fromAngles(rx * 0.35f, 0f, 0f));
        Geometry low = Gfx.taperedCylinder(name + "ClothLow", halfWidth * 0.98f,
                halfWidth * 1.2f, lower, mat, tail, 0f, -lower * 0.5f, 0f);
        low.setLocalScale(1f, 1f, halfThick / Math.max(0.02f, halfWidth) * 0.82f);
        Gfx.box(name + "Hem", halfWidth * 1.16f, 0.012f, halfThick * 1.25f,
                goldMat, tail, 0f, -lower + 0.015f, 0f);
        return n;
    }

    private Node pivot(Node parent, String name, float x, float y, float z) {
        Node n = new Node(name);
        n.setLocalTranslation(x, y, z);
        parent.attachChild(n);
        return n;
    }

    void setVisible(boolean value) {
        root.setCullHint(value ? com.jme3.scene.Spatial.CullHint.Never
                : com.jme3.scene.Spatial.CullHint.Always);
    }

    void reset(Vector3f spawn, float facing) {        pos.set(spawn);
        yaw = facing;
        velocity.set(0f, 0f, 0f);
        hp = hpMax;
        stamina = stMax;
        mana = mpMax;
        alive = true;
        mounted = false;
        evMountDown = false;
        mountHp = mountMaxHp;
        mountJumps = 0;
        doubleJumped = false;
        state = State.FREE;
        stateTime = 0f;
        invuln = 0f;
        staminaDelay = 0f;
        manaDelay = 0f;
        comboIndex = 0;
        comboGrace = 0f;
        bufferedLight = false;
        bufferedHeavy = false;
        wantLight = false;
        wantHeavy = false;
        wantRoll = false;
        wantSkill = false;
        wantJump = false;
        wantCharge = false;
        wantChargeRelease = false;
        wantPlunge = false;
        velY = 0f;
        airborne = false;
        chargeTime = 0f;
        chargeLevel = 0f;
        landTimer = 0f;
        isAirAttack = false;
        isPlunge = false;
        attackKind = AttackKind.LIGHT;
        staffSmooth.set(new Quaternion());
        staffReady = false;
        execHitDone = false;
        evHit = false;
        evHeavyHit = false;
        evSkillHit = false;
        evHurt = false;
        evDied = false;
        evTired = false;
        evNoMana = false;
        evJump = false;
        evLand = false;
        evPlunge = false;
        evExecute = false;
        evChargeReady = false;
        skillCycleEnd = 0f;
        skillTickTimer = 0f;
        fireTimer = 0f;
        spinAngle = 0f;
        spinBlend = 0f;
        animTime = 0f;
        updateVisuals(0f, 0f);
    }

    void requestLight() {
        wantLight = true;
    }

    void requestHeavy() {
        wantHeavy = true;
    }

    void requestRoll() {
        wantRoll = true;
    }

    void requestSkill() {
        wantSkill = true;
    }

    void requestJump() {
        wantJump = true;
    }

    void requestCharge() {
        wantCharge = true;
    }

    void releaseCharge() {
        wantChargeRelease = true;
    }

    void requestPlunge() {
        wantPlunge = true;
    }

    /** Airborne while falling or recovering from a landing. */
    boolean isAirborne() {
        return airborne;
    }

    boolean isExecuting() {
        return state == State.EXECUTE;
    }

    float chargeFraction() {
        return chargeTime / CHARGE_MAX;
    }

    boolean isInvulnerable() {
        return invuln > 0f || (state == State.ROLL && stateTime > 0.05f && stateTime < 0.37f);
    }

    boolean isRolling() {
        return state == State.ROLL;
    }

    float radius() {
        return 0.42f;
    }

    void setGroundY(float y) {
        groundY = y;
    }

    void setArenaBounds(boolean value) {
        arenaBounds = value;
    }

    float groundY() {
        return groundY;
    }

    /** Absolute world Y of the feet (terrain plus jump arc). */
    float worldY() {
        return groundY + pos.y + (mounted ? 0.62f : 0f);
    }

    void setTargets(List<Target> list) {
        targets.clear();
        targets.addAll(list);
    }

    void applyStats(float hpMax, float stMax, float mpMax, float damageMul) {
        float hpFrac = this.hpMax > 0f ? hp / this.hpMax : 1f;
        this.hpMax = hpMax;
        this.stMax = stMax;
        this.mpMax = mpMax;
        this.damageMul = damageMul;
        hp = hpMax * hpFrac;
        stamina = stMax;
        mana = mpMax;
    }

    void healHp(float amount) {
        hp = Math.min(hpMax, hp + amount);
    }

    void refill() {
        hp = hpMax;
        stamina = stMax;
        mana = mpMax;
    }

    void setMounted(boolean value) {
        if (value && !mounted) {
            mountJumps = 1;
            doubleJumped = false;
        }
        if (!value) {
            mountJumps = 0;
            doubleJumped = false;
        }
        mounted = value;
    }

    void update(float dt, float moveF, float moveR, boolean sprint, boolean skillHeld,
                float camYaw, boolean locked, Target boss) {
        evHit = false;
        evHeavyHit = false;
        evSkillHit = false;
        evHurt = false;
        evDied = false;
        evTired = false;
        evNoMana = false;
        evJump = false;
        evLand = false;
        evPlunge = false;
        evExecute = false;
        evChargeReady = false;
        evMountDown = false;
        evDamage = 0f;

        boolean light = wantLight;
        boolean heavy = wantHeavy;
        boolean roll = wantRoll;
        boolean skill = wantSkill;
        boolean jump = wantJump;
        boolean charge = wantCharge;
        boolean chargeRelease = wantChargeRelease;
        boolean plunge = wantPlunge;
        wantLight = false;
        wantHeavy = false;
        wantRoll = false;
        wantSkill = false;
        wantJump = false;
        wantCharge = false;
        wantChargeRelease = false;
        wantPlunge = false;

        animTime += dt;
        invuln = Math.max(0f, invuln - dt);
        staminaDelay = Math.max(0f, staminaDelay - dt);
        manaDelay = Math.max(0f, manaDelay - dt);
        comboGrace = Math.max(0f, comboGrace - dt);
        landTimer = Math.max(0f, landTimer - dt);

        if (!alive) {
            stateTime += dt;
            state = State.DEAD;
            pos.y = 0f;
            velY = 0f;
            airborne = false;
            updateVisuals(dt, 0f);
            return;
        }

        float len = (float) Math.sqrt(moveF * moveF + moveR * moveR);
        if (len > 1f) {
            moveF /= len;
            moveR /= len;
        }
        moveDir.set(FastMath.sin(camYaw) * moveF - FastMath.cos(camYaw) * moveR, 0f,
                FastMath.cos(camYaw) * moveF + FastMath.sin(camYaw) * moveR);
        boolean moving = moveDir.lengthSquared() > 0.004f;

        boolean sprinting = false;
        float speed = 0f;

        switch (state) {
            case FREE -> {
                boolean canAct = landTimer <= 0f;
                boolean wantsSprint = mounted ? (sprint && moving)
                        : (sprint && moving && !airborne && stamina > 1f);
                sprintingNow = wantsSprint;
                if (canAct && mounted && jump && airborne && !doubleJumped) {
                    doubleJumped = true;
                    velY = JUMP_V * 1.05f;
                    fx.dust(new Vector3f(pos.x, groundY + 0.2f, pos.z), 12);
                    fx.shockwave(new Vector3f(pos.x, groundY + 0.05f, pos.z), 1.6f, 0.3f,
                            new ColorRGBA(0.85f, 0.92f, 1f, 0.35f));
                } else if (canAct && skill && !airborne) {
                    startSkill(locked, boss);
                } else if (canAct && light && boss.canBeExecuted(pos)) {
                    startExecute(boss);
                } else if (canAct && roll && stamina >= ROLL_COST && !airborne) {
                    startRoll(locked, boss);
                } else if (canAct && jump && !airborne) {
                    startJump();
                } else if (canAct && charge && !airborne) {
                    startCharge(locked, boss);
                } else if (canAct && plunge && airborne) {
                    startAirAttack(true, camYaw, locked, boss);
                } else if (canAct && light) {
                    if (airborne) {
                        startAirAttack(false, camYaw, locked, boss);
                    } else if (wantsSprint) {
                        startRunAttack(camYaw, locked, boss);
                    } else {
                        startAttack(false, camYaw, locked, boss);
                    }
                } else if (canAct && heavy) {
                    startAttack(true, camYaw, locked, boss);
                } else {
                    sprinting = wantsSprint;
                    speed = mounted ? (sprinting ? 16.5f : 9.6f)
                            : (sprinting ? SPRINT_SPEED : WALK_SPEED);
                    if (sprinting && !mounted) {
                        stamina = Math.max(0f, stamina - 15f * dt);
                        staminaDelay = 0.7f;
                        if (stamina <= 0f) {
                            sprinting = false;
                            speed = WALK_SPEED;
                        }
                    }
                    tmp.set(moveDir).multLocal(moving ? speed : 0f);
                    float ctrl = airborne ? 2.6f : 14f;
                    velocity.x = MathX.damp(velocity.x, tmp.x, ctrl, dt);
                    velocity.z = MathX.damp(velocity.z, tmp.z, ctrl, dt);
                    pos.x += velocity.x * dt;
                    pos.z += velocity.z * dt;

                    if (locked && boss.alive) {
                        yaw = MathX.lerpAngle(yaw, MathX.yawTo(pos, boss.pos), 1f - (float) Math.exp(-14f * dt));
                    } else if (moving) {
                        yaw = MathX.lerpAngle(yaw, (float) Math.atan2(moveDir.x, moveDir.z),
                                1f - (float) Math.exp(-12f * dt));
                    }
                    if (moving && !airborne) {
                        walkPhase += dt * (sprinting ? 11.5f : 8.2f);
                    }
                }
            }
            case ROLL -> updateRoll(dt, light, heavy, boss);
            case ATTACK -> updateAttack(dt, boss, light, heavy, roll, camYaw, locked);
            case SKILL -> updateSkill(dt, skillHeld, boss);
            case CHARGE -> updateCharge(dt, chargeRelease, boss);
            case EXECUTE -> updateExecute(dt, boss);
            case PLUNGE_LAND -> {
                stateTime += dt;
                velocity.set(0f, 0f, 0f);
                if (stateTime >= PLUNGE_LAG) {
                    state = State.FREE;
                    stateTime = 0f;
                    landTimer = 0.08f;
                    comboGrace = 0.25f;
                }
            }
            case HURT -> {
                stateTime += dt;
                pos.x += velocity.x * dt;
                pos.z += velocity.z * dt;
                velocity.x = MathX.damp(velocity.x, 0f, 9f, dt);
                velocity.z = MathX.damp(velocity.z, 0f, 9f, dt);
                if (stateTime >= 0.42f) {
                    state = State.FREE;
                    stateTime = 0f;
                    velocity.set(0f, 0f, 0f);
                    comboGrace = 0f;
                }
            }
            default -> {
            }
        }

        if (airborne || pos.y > 0f) {
            float gravity = GRAVITY;
            if (isPlunge && state == State.ATTACK) {
                gravity = stateTime <= PLUNGE_RAISE_TIME ? GRAVITY * 0.22f : GRAVITY * 2.55f;
            }
            velY -= gravity * dt;
            pos.y += velY * dt;
            if (pos.y <= 0f) {
                pos.y = 0f;
                velY = 0f;
                airborne = false;
                doubleJumped = false;
                land(boss);
            }
        }

        if (!sprinting && staminaDelay <= 0f) {
            stamina = Math.min(stMax, stamina + 30f * dt);
        }
        if (state != State.SKILL && manaDelay <= 0f) {
            mana = Math.min(mpMax, mana + 2.4f * dt);
        }

        if (boss.alive && !isRolling() && state != State.EXECUTE) {
            float minD = boss.radius() + radius();
            float d = MathX.distXZ(pos, boss.pos);
            if (d < minD && d > 1e-4f) {
                pos.x = boss.pos.x + (pos.x - boss.pos.x) * minD / d;
                pos.z = boss.pos.z + (pos.z - boss.pos.z) * minD / d;
            } else if (d <= 1e-4f) {
                pos.x += minD;
            }
        }

        if (arenaBounds) {
            float limit = Arena.PLAY_RADIUS - 0.45f;
            float r = (float) Math.sqrt(pos.x * pos.x + pos.z * pos.z);
            if (r > limit) {
                pos.x = pos.x / r * limit;
                pos.z = pos.z / r * limit;
            }
        }

        updateVisuals(dt, moving ? (sprinting ? 1f : 0.6f) : 0f);
    }

    private void startRoll(boolean locked, Target boss) {
        stamina = Math.max(0f, stamina - ROLL_COST);
        staminaDelay = 0.7f;
        if (moveDir.lengthSquared() > 0.01f) {
            rollDir.set(moveDir).normalizeLocal();
        } else if (locked && boss.alive) {
            rollDir.set(pos.x - boss.pos.x, 0f, pos.z - boss.pos.z).normalizeLocal();
        } else {
            rollDir.set(MathX.forward(yaw));
        }
        yaw = (float) Math.atan2(rollDir.x, rollDir.z);
        velocity.set(rollDir).multLocal(7.4f);
        state = State.ROLL;
        stateTime = 0f;
        fx.dust(new Vector3f(pos.x, groundY + 0.12f, pos.z), 7);
        bufferedLight = false;
        bufferedHeavy = false;
    }

    private void updateRoll(float dt, boolean light, boolean heavy, Target boss) {
        stateTime += dt;
        float t = MathX.clamp(stateTime / ROLL_TIME, 0f, 1f);
        float velocityCurve = 7.4f * (1f - 0.55f * t);
        pos.x += rollDir.x * velocityCurve * dt;
        pos.z += rollDir.z * velocityCurve * dt;
        if (stateTime > ROLL_TIME * 0.45f) {
            if (light) {
                bufferedLight = true;
            }
            if (heavy) {
                bufferedHeavy = true;
            }
        }
        if (stateTime >= ROLL_TIME) {
            state = State.FREE;
            stateTime = 0f;
            velocity.set(0f, 0f, 0f);
            fx.dust(new Vector3f(pos.x, groundY + 0.1f, pos.z), 4);
            comboGrace = 0.35f;
            if (bufferedLight) {
                bufferedLight = false;
                startRollAttack(yaw, boss);
            } else if (bufferedHeavy) {
                bufferedHeavy = false;
                startAttack(true, yaw, false, boss);
            }
        }
    }

    /** Test hook: drop to the ground and clear any committed aerial attack. */
    void forceGrounded() {
        pos.y = 0f;
        velY = 0f;
        airborne = false;
        landTimer = 0f;
        if (state == State.ATTACK && isAirAttack) {
            isAirAttack = false;
            isPlunge = false;
            state = State.FREE;
            stateTime = 0f;
        }
        if (state == State.PLUNGE_LAND) {
            state = State.FREE;
            stateTime = 0f;
        }
    }

    private void startJump() {
        airborne = true;
        velY = mounted ? JUMP_V * 1.18f : JUMP_V;
        doubleJumped = false;
        pos.y = 0.02f;
        landTimer = 0f;
        fx.dust(new Vector3f(pos.x, groundY + 0.08f, pos.z), 7);
        evJump = true;
        bufferedLight = false;
        bufferedHeavy = false;
    }

    private void startAirAttack(boolean plunge, float camYaw, boolean locked, Target boss) {
        float cost = plunge ? PLUNGE_COST : AIR_COST;
        if (stamina < cost) {
            evTired = true;
            return;
        }
        stamina = Math.max(0f, stamina - cost);
        staminaDelay = 0.6f;
        isAirAttack = true;
        isPlunge = plunge;
        attackKind = plunge ? AttackKind.PLUNGE : AttackKind.AIR;
        isHeavy = false;
        comboIndex = 0;
        chargeLevel = 0f;
        state = State.ATTACK;
        stateTime = 0f;
        hitDone = false;
        bufferedLight = false;
        bufferedHeavy = false;
        comboGrace = 0f;
        if (plunge) {
            attackDuration = 4.0f;
            attackWindup = 0f;
            attackHitTime = 99f;
            velY = Math.min(velY, 0.9f);
            fx.flash(new Vector3f(pos.x, groundY + pos.y + 1.5f, pos.z),
                    new ColorRGBA(1f, 0.84f, 0.42f, 0.5f), 0.55f);
            tmp.set(staffTip.getWorldTranslation());
            fx.spark(tmp, new ColorRGBA(1f, 0.86f, 0.48f, 1f), 8);
        } else {
            attackDuration = AIR_DUR;
            attackWindup = 0.07f;
            attackHitTime = AIR_HIT;
        }
        if (locked && boss.alive) {
            yaw = MathX.yawTo(pos, boss.pos);
        } else if (moveDir.lengthSquared() > 0.01f) {
            yaw = (float) Math.atan2(moveDir.x, moveDir.z);
        } else {
            yaw = camYaw;
        }
        attackForward.set(MathX.forward(yaw));
    }

    private void land(Target boss) {
        evLand = true;
        if (isPlunge) {
            isPlunge = false;
            isAirAttack = false;
            state = State.PLUNGE_LAND;
            stateTime = 0f;
            velocity.set(0f, 0f, 0f);
            landTimer = 0f;
            resolvePlunge(boss);
        } else if (state == State.ATTACK && isAirAttack) {
            isAirAttack = false;
            state = State.FREE;
            stateTime = 0f;
            landTimer = 0.14f;
            fx.dust(new Vector3f(pos.x, groundY + 0.1f, pos.z), 6);
        } else {
            landTimer = 0.13f;
            fx.dust(new Vector3f(pos.x, groundY + 0.1f, pos.z), 6);
        }
    }

    private void resolvePlunge(Target boss) {
        evPlunge = true;
        fx.dust(new Vector3f(pos.x, groundY + 0.12f, pos.z), 20);
        fx.spark(new Vector3f(pos.x, groundY + 0.45f, pos.z), new ColorRGBA(1f, 0.82f, 0.42f, 1f), 22);
        fx.shockwave(new Vector3f(pos.x, groundY + 0.06f, pos.z), 4.6f, 0.55f,
                new ColorRGBA(1f, 0.7f, 0.3f, 0.5f));
        int count = targets.isEmpty() ? 1 : targets.size();
        for (int i = 0; i < count; i++) {
            Target t = targets.isEmpty() ? boss : targets.get(i);
            if (t == null || !t.alive || MathX.distXZ(pos, t.pos) > 3.4f + t.radius()) {
                continue;
            }
            tmp.set(t.pos).addLocal(pos).multLocal(0.5f);
            tmp.y = t.groundY() + 0.95f;
            t.takeHit(PLUNGE_DMG * damageMul, PLUNGE_POISE, tmp.clone());
            evHit = true;
            evHeavyHit = true;
            fx.spark(tmp.clone(), new ColorRGBA(1f, 0.88f, 0.52f, 1f), 26);
        }
    }

    private void startCharge(boolean locked, Target boss) {
        if (stamina < CHARGE_COST) {
            evTired = true;
            return;
        }
        stamina = Math.max(0f, stamina - CHARGE_COST);
        staminaDelay = 0.7f;
        state = State.CHARGE;
        stateTime = 0f;
        chargeTime = 0f;
        chargeLevel = 0f;
        chargeFxTimer = 0f;
        velocity.set(0f, 0f, 0f);
        bufferedLight = false;
        bufferedHeavy = false;
        comboGrace = 0f;
        if (locked && boss.alive) {
            yaw = MathX.yawTo(pos, boss.pos);
        }
    }

    private void updateCharge(float dt, boolean release, Target boss) {
        stateTime += dt;
        chargeTime = Math.min(CHARGE_MAX, chargeTime + dt);
        float k = chargeTime / CHARGE_MAX;
        chargeFxTimer -= dt;
        if (chargeFxTimer <= 0f) {
            chargeFxTimer = 0.11f - 0.05f * k;
            tmp.set(staffTip.getWorldTranslation());
            fx.spark(tmp, new ColorRGBA(1f, 0.76f + 0.2f * k, 0.28f + 0.2f * k, 1f),
                    2 + (int) (5 * k));
        }
        if (k >= 1f) {
            evChargeReady = true;
        }
        if (release || k >= 1f) {
            chargeLevel = k;
            state = State.FREE;
            stateTime = 0f;
            startAttack(true, yaw, false, boss, 0f);
        }
    }

    private void startExecute(Target boss) {
        attackEntry.set(pose);
        state = State.EXECUTE;
        stateTime = 0f;
        execHitDone = false;
        invuln = Math.max(invuln, EXEC_DUR);
        airborne = false;
        velY = 0f;
        pos.y = 0f;
        velocity.set(0f, 0f, 0f);
        landTimer = 0f;
        yaw = MathX.yawTo(pos, boss.pos);
        boss.lockForExecution(pos);
        fx.flash(new Vector3f(pos.x, groundY + 1.2f, pos.z),
                new ColorRGBA(1f, 0.88f, 0.45f, 0.7f), 0.7f);
    }

    private void updateExecute(float dt, Target boss) {
        stateTime += dt;
        if (!boss.alive) {
            finishExecute();
            return;
        }
        yaw = MathX.lerpAngle(yaw, MathX.yawTo(pos, boss.pos), 1f - (float) Math.exp(-16f * dt));
        float want = boss.radius() + 0.62f;
        float d = MathX.distXZ(pos, boss.pos);
        if (stateTime < 0.36f && d > want && d > 1e-4f) {
            float step = Math.min(d - want, 12f * dt);
            tmp.set(boss.pos.x - pos.x, 0f, boss.pos.z - pos.z).normalizeLocal();
            pos.x += tmp.x * step;
            pos.z += tmp.z * step;
        }
        if (!execHitDone && stateTime >= EXEC_HIT) {
            execHitDone = true;
            float chest = boss.groundY() + Math.min(1.95f, boss.displayHeight() * 0.72f);
            tmp.set(pos.x + (boss.pos.x - pos.x) * 0.72f, chest,
                    pos.z + (boss.pos.z - pos.z) * 0.72f);
            boss.takeExecution(EXEC_DMG * damageMul, tmp.clone());
            evExecute = true;
            evHit = true;
            evHeavyHit = true;
            fx.spark(tmp, new ColorRGBA(1f, 0.9f, 0.55f, 1f), 40);
            fx.flash(tmp, new ColorRGBA(1f, 0.82f, 0.36f, 0.85f), 1.1f);
            fx.shockwave(new Vector3f(boss.pos.x, boss.groundY() + 0.06f, boss.pos.z), 4.4f, 0.62f,
                    new ColorRGBA(1f, 0.62f, 0.16f, 0.55f));
            fx.dust(new Vector3f(boss.pos.x, boss.groundY() + 0.15f, boss.pos.z), 16);
        }
        if (stateTime >= EXEC_DUR) {
            boss.clearExecutionLock();
            finishExecute();
        }
    }

    private void finishExecute() {
        state = State.FREE;
        stateTime = 0f;
        comboGrace = 0f;
        landTimer = 0.12f;
        velocity.set(0f, 0f, 0f);
    }

    private void startSkill(boolean locked, Target boss) {
        if (mana < SKILL_MP_COST) {
            evNoMana = true;
            return;
        }
        if (stamina < SKILL_ST_COST) {
            evTired = true;
            return;
        }
        mana = Math.max(0f, mana - SKILL_MP_COST);
        stamina = Math.max(0f, stamina - SKILL_ST_COST);
        manaDelay = 1.6f;
        staminaDelay = 0.7f;
        if (locked && boss.alive) {
            yaw = MathX.yawTo(pos, boss.pos);
        } else if (moveDir.lengthSquared() > 0.01f) {
            yaw = (float) Math.atan2(moveDir.x, moveDir.z);
        }
        state = State.SKILL;
        stateTime = 0f;
        skillCycleEnd = SKILL_CYCLE;
        skillTickTimer = 0.12f;
        fireTimer = 0f;
        spinAngle = 0f;
        velocity.set(0f, 0f, 0f);
        comboGrace = 0f;
        bufferedLight = false;
        bufferedHeavy = false;
        Vector3f base = new Vector3f(pos.x, groundY + 0.06f, pos.z);
        fx.shockwave(base, 2.6f, 0.42f, new ColorRGBA(1f, 0.38f, 0.1f, 0.45f));
        fx.flame(new Vector3f(pos.x, groundY + 0.12f, pos.z), 1.5f, 24);
    }

    private void updateSkill(float dt, boolean held, Target boss) {
        stateTime += dt;
        if (held) {
            if (mana > 0f && stamina > 0f) {
                mana = Math.max(0f, mana - SKILL_MP_DRAIN * dt);
                stamina = Math.max(0f, stamina - SKILL_ST_DRAIN * dt);
                manaDelay = 1.3f;
                staminaDelay = 0.5f;
                while (stateTime >= skillCycleEnd) {
                    skillCycleEnd += SKILL_CYCLE;
                }
            } else {
                evNoMana = mana <= 0f;
                evTired = stamina <= 0f;
                finishSkill();
                return;
            }
        }

        float drift = 1.0f * dt;
        pos.x += MathX.forward(yaw).x * drift;
        pos.z += MathX.forward(yaw).z * drift;

        skillTickTimer -= dt;
        if (skillTickTimer <= 0f) {
            skillTickTimer += SKILL_TICK;
            skillHit(boss);
        }

        fireTimer -= dt;
        if (fireTimer <= 0f) {
            fireTimer = 0.03f;
            float t = (stateTime % SKILL_CYCLE) / SKILL_CYCLE;
            fx.flame(new Vector3f(pos.x, groundY + 0.06f, pos.z), 1.35f + t * 0.5f,
                    t < 0.12f ? 22 : 14);
            if (t < 0.1f) {
                fx.shockwave(new Vector3f(pos.x, groundY + 0.05f, pos.z), 2.7f, 0.42f,
                        new ColorRGBA(1f, 0.32f, 0.06f, 0.42f));
            }
        }

        if (!held && stateTime >= skillCycleEnd) {
            finishSkill();
        }
    }

    private void skillHit(Target boss) {
        int count = targets.isEmpty() ? 1 : targets.size();
        boolean any = false;
        for (int i = 0; i < count; i++) {
            Target t = targets.isEmpty() ? boss : targets.get(i);
            if (t == null || !t.alive
                    || MathX.distXZ(pos, t.pos) > SKILL_REACH + t.radius()) {
                continue;
            }
            tmp.set(t.pos).addLocal(pos).multLocal(0.5f);
            tmp.y = t.groundY() + 1.0f;
            t.takeHit(SKILL_DMG * damageMul, SKILL_POISE, tmp.clone());
            fx.spark(tmp.clone(), new ColorRGBA(1f, 0.58f, 0.18f, 1f), 8);
            fx.flame(new Vector3f(t.pos.x, t.groundY() + 0.12f, t.pos.z), 2.4f, 8);
            fx.shockwave(new Vector3f(t.pos.x, t.groundY() + 0.05f, t.pos.z), 2.6f, 0.32f,
                    new ColorRGBA(1f, 0.4f, 0.1f, 0.5f));
            any = true;
        }
        evSkillHit = any;
    }

    private void finishSkill() {
        state = State.FREE;
        stateTime = 0f;
        velocity.set(0f, 0f, 0f);
        comboGrace = 0.25f;
        fx.flash(new Vector3f(pos.x, groundY + 0.9f, pos.z), new ColorRGBA(1f, 0.5f, 0.16f, 0.7f), 0.8f);
    }

    private void startAttack(boolean heavy, float camYaw, boolean locked, Target boss) {
        startAttack(heavy, camYaw, locked, boss, -1f);
    }

    private void startAttack(boolean heavy, float camYaw, boolean locked, Target boss,
                             float costOverride) {
        int nextCombo = heavy ? 0 : (comboGrace > 0f ? (comboIndex + 1) % LIGHT_DUR.length : 0);
        float cl = heavy ? MathX.clamp(chargeLevel, 0f, 1f) : 0f;
        chargeLevel = 0f;
        float cost = costOverride >= 0f ? costOverride
                : (heavy ? HEAVY_COST + 14f * cl : LIGHT_COST[nextCombo]);
        if (stamina < cost) {
            evTired = true;
            return;
        }
        comboIndex = nextCombo;
        chargedLevel = cl;
        attackKind = heavy ? AttackKind.HEAVY : AttackKind.LIGHT;
        if (heavy) {
            attackDuration = HEAVY_DUR + 0.30f * cl;
            attackWindup = HEAVY_WINDUP + 0.14f * cl;
            attackHitTime = HEAVY_HIT + 0.10f * cl;
        } else {
            attackDuration = LIGHT_DUR[comboIndex];
            attackWindup = LIGHT_WINDUP[comboIndex];
            attackHitTime = LIGHT_HIT[comboIndex];
        }
        isHeavy = heavy;
        isAirAttack = false;
        isPlunge = false;
        beginAttack(cost, camYaw, locked, boss);
    }

    private void startRunAttack(float camYaw, boolean locked, Target boss) {
        if (stamina < RUN_COST) {
            evTired = true;
            return;
        }
        attackKind = AttackKind.RUN;
        comboIndex = 0;
        chargedLevel = 0f;
        chargeLevel = 0f;
        isHeavy = false;
        isAirAttack = false;
        isPlunge = false;
        attackDuration = RUN_DUR;
        attackWindup = RUN_WINDUP;
        attackHitTime = RUN_HIT;
        beginAttack(RUN_COST, camYaw, locked, boss);
    }

    private void startRollAttack(float camYaw, Target boss) {
        if (stamina < ROLL_ATK_COST) {
            evTired = true;
            return;
        }
        attackKind = AttackKind.ROLL;
        comboIndex = 0;
        chargedLevel = 0f;
        chargeLevel = 0f;
        isHeavy = false;
        isAirAttack = false;
        isPlunge = false;
        attackDuration = ROLL_ATK_DUR;
        attackWindup = ROLL_ATK_WINDUP;
        attackHitTime = ROLL_ATK_HIT;
        beginAttack(ROLL_ATK_COST, camYaw, false, boss);
    }

    private void beginAttack(float cost, float camYaw, boolean locked, Target boss) {
        stamina = Math.max(0f, stamina - cost);
        staminaDelay = 0.7f;
        attackEntry.set(pose);
        state = State.ATTACK;
        stateTime = 0f;
        hitDone = false;
        bufferedLight = false;
        bufferedHeavy = false;
        comboGrace = 0f;
        velocity.set(0f, 0f, 0f);

        if (locked && boss.alive) {
            yaw = MathX.yawTo(pos, boss.pos);
        } else if (moveDir.lengthSquared() > 0.01f) {
            yaw = (float) Math.atan2(moveDir.x, moveDir.z);
        } else {
            yaw = camYaw;
        }
        attackForward.set(MathX.forward(yaw));
    }

    private void updateAttack(float dt, Target boss, boolean light, boolean heavy, boolean roll,
                              float camYaw, boolean locked) {
        stateTime += dt;
        if (!isAirAttack && roll && stateTime > attackHitTime
                && stamina >= ROLL_COST && stateTime < attackDuration - 0.06f) {
            startRoll(locked, boss);
            return;
        }
        if (isAirAttack) {
            if (isPlunge) {
                float lunge = stateTime < PLUNGE_CLEAVE_TIME ? 2.6f : 1.3f;
                pos.x += attackForward.x * lunge * dt;
                pos.z += attackForward.z * lunge * dt;
                if (locked && boss.alive && stateTime < PLUNGE_CLEAVE_TIME) {
                    yaw = MathX.lerpAngle(yaw, MathX.yawTo(pos, boss.pos),
                            1f - (float) Math.exp(-9f * dt));
                    attackForward.set(MathX.forward(yaw));
                }
            } else if (!hitDone && stateTime >= attackHitTime) {
                hitDone = true;
                resolveHit(boss);
            }
            return;
        }
        float lungeTime = attackKind == AttackKind.RUN
                ? attackHitTime + 0.14f
                : (attackKind == AttackKind.ROLL ? attackHitTime + 0.05f : attackWindup + 0.06f);
        if (stateTime < lungeTime) {
            float lunge = switch (attackKind) {
                case HEAVY -> 2.9f;
                case RUN -> 5.4f;
                case ROLL -> 2.1f;
                case LIGHT -> comboIndex == 3 ? 3.4f : 2.5f;
                default -> 2.4f;
            };
            float ease = attackKind == AttackKind.RUN
                    ? 1f - 0.45f * MathX.clamp(stateTime / Math.max(0.01f, lungeTime), 0f, 1f)
                    : 1f;
            pos.x += attackForward.x * lunge * ease * dt;
            pos.z += attackForward.z * lunge * ease * dt;
        }
        if (!hitDone && stateTime >= attackHitTime) {
            hitDone = true;
            resolveHit(boss);
        }
        if (stateTime > attackDuration * 0.45f) {
            if (light) {
                bufferedLight = true;
            }
            if (heavy) {
                bufferedHeavy = true;
            }
        }
        if (stateTime >= attackDuration) {
            state = State.FREE;
            stateTime = 0f;
            if (bufferedLight) {
                bufferedLight = false;
                comboGrace = 0.9f;
                startAttack(false, camYaw, locked, boss);
            } else if (bufferedHeavy) {
                bufferedHeavy = false;
                startAttack(true, camYaw, locked, boss);
            } else {
                comboGrace = attackKind == AttackKind.LIGHT ? 0.9f : 0f;
            }
        }
    }

    private void resolveHit(Target boss) {
        boolean air = isAirAttack;
        float baseReach = switch (attackKind) {
            case HEAVY -> 3.3f;
            case RUN -> 3.5f;
            case ROLL -> 3.0f;
            case AIR, PLUNGE -> 3.4f;
            default -> 3.0f;
        };
        float arc = switch (attackKind) {
            case HEAVY -> 1.18f;
            case RUN -> 1.42f;
            case ROLL -> 1.34f;
            default -> 1.30f;
        };
        float dmg;
        float poise;
        if (air) {
            dmg = AIR_DMG;
            poise = AIR_POISE;
        } else if (attackKind == AttackKind.HEAVY) {
            dmg = HEAVY_DMG;
            poise = HEAVY_POISE;
        } else if (attackKind == AttackKind.RUN) {
            dmg = RUN_DMG;
            poise = RUN_POISE;
        } else if (attackKind == AttackKind.ROLL) {
            dmg = ROLL_ATK_DMG;
            poise = ROLL_ATK_POISE;
        } else {
            dmg = LIGHT_DMG[comboIndex];
            poise = LIGHT_POISE[comboIndex];
        }
        dmg *= (1f + 1.35f * chargedLevel) * damageMul;
        poise *= 1f + 1.7f * chargedLevel;

        int count = targets.isEmpty() ? 1 : targets.size();
        boolean any = false;
        boolean heavyFx = false;
        for (int i = 0; i < count; i++) {
            Target t = targets.isEmpty() ? boss : targets.get(i);
            if (t == null || !t.alive) {
                continue;
            }
            if (MathX.distXZ(pos, t.pos) > baseReach + t.radius()
                    || MathX.angleBetween(yaw, pos, t.pos) > arc) {
                continue;
            }
            tmp.set(t.pos).addLocal(pos).multLocal(0.5f);
            tmp.y = t.groundY() + 1.45f;
            if (t.groundY() == 0f) {
                tmp.y = 1.45f;
            }
            t.takeHit(dmg, poise, tmp.clone());
            any = true;
            heavyFx |= isHeavy || attackKind == AttackKind.RUN;
            int sparks = switch (attackKind) {
                case HEAVY -> 20 + (int) (18 * chargedLevel);
                case RUN -> 20;
                case ROLL -> 14;
                default -> 12;
            };
            fx.spark(tmp.clone(), new ColorRGBA(1f, 0.78f, 0.35f, 1f), sparks);
            fx.flash(tmp, new ColorRGBA(1f, 0.84f, 0.5f, 0.65f),
                    isHeavy ? 0.45f : (attackKind == AttackKind.RUN ? 0.4f : 0.3f));
        }
        if (!any) {
            return;
        }
        evHit = true;
        evHeavyHit = heavyFx || air;
        if (heavyFx) {
            tmp.set(pos.x, 0.05f + groundY, pos.z);
            fx.shockwave(tmp.clone(),
                    2.2f + 2.0f * chargedLevel + (attackKind == AttackKind.RUN ? 0.8f : 0f), 0.4f,
                    new ColorRGBA(1f, 0.7f, 0.3f, 0.4f + 0.2f * chargedLevel));
        }
    }

    boolean takeHit(float dmg, Vector3f from) {
        if (!alive || isInvulnerable()) {
            return false;
        }
        if (mounted && mountHp > 0f) {
            mountHp = Math.max(0f, mountHp - dmg);
            invuln = 0.55f;
            evHurt = true;
            evDamage = 0f;
            fx.spark(new Vector3f(pos.x, worldY() + 1.2f, pos.z),
                    new ColorRGBA(0.72f, 0.86f, 1f, 1f), 10);
            if (mountHp <= 0f) {
                mounted = false;
                mountJumps = 0;
                doubleJumped = false;
                evMountDown = true;
                fx.dust(new Vector3f(pos.x, groundY + 0.3f, pos.z), 22);
                fx.flash(new Vector3f(pos.x, worldY() + 1.1f, pos.z),
                        new ColorRGBA(0.75f, 0.9f, 1f, 0.65f), 0.8f);
            }
            return true;
        }
        hp = Math.max(0f, hp - dmg);
        evDamage = dmg;
        invuln = 0.75f;
        evHurt = true;
        state = State.HURT;
        stateTime = 0f;
        comboGrace = 0f;
        bufferedLight = false;
        bufferedHeavy = false;
        tmp.set(pos.x - from.x, 0f, pos.z - from.z);
        if (tmp.lengthSquared() < 1e-4f) {
            tmp.set(MathX.forward(yaw)).multLocal(-1f);
        }
        tmp.normalizeLocal().multLocal(6.2f);
        velocity.set(tmp);
        fx.dust(new Vector3f(pos.x, worldY() + 0.15f, pos.z), 8);
        fx.spark(new Vector3f(pos.x, worldY() + 1.2f, pos.z), new ColorRGBA(0.9f, 0.2f, 0.12f, 1f), 10);
        if (hp <= 0f) {
            alive = false;
            state = State.DEAD;
            stateTime = 0f;
            velocity.set(0f, 0f, 0f);
            evDied = true;
        }
        return true;
    }

    private void updateVisuals(float dt, float motion) {
        float bob = 0f;
        float rollAngle = 0f;
        float deathTilt = 0f;
        float pivotY = 1.02f;
        float bodyScale = 1f;
        float bodySpin = 0f;
        rollTuck = 0f;

        switch (state) {
            case FREE -> {
                if (airborne) {
                    bob = airbornePose();
                } else if (landTimer > 0f) {
                    bob = landingPose();
                } else {
                    bob = locomotionPose(motion);
                }
            }
            case ROLL -> {
                float t = MathX.clamp(stateTime / ROLL_TIME, 0f, 1f);
                rollTuck = MathX.smooth(MathX.clamp(t / 0.16f, 0f, 1f))
                        * (1f - MathX.smooth(MathX.clamp((t - 0.78f) / 0.22f, 0f, 1f)));
                Pose.interpolate(REST, ROLL_TUCK, rollTuck, pose);
                rollAngle = FastMath.TWO_PI * MathX.smooth(t);
                pivotY = MathX.lerp(1.02f, 0.64f, rollTuck)
                        + 0.04f * FastMath.sin(FastMath.PI * t) * rollTuck;
                bodyScale = 1f - 0.2f * rollTuck;
            }
            case ATTACK -> attackPose();
            case SKILL -> {
            float in = MathX.smooth(MathX.clamp(stateTime / 0.10f, 0f, 1f));
                Pose.interpolate(REST, SPIN_POSE, in, pose);
                bob = -0.025f * in;
                spinAngle += dt * 11.5f;
                spinBlend = MathX.damp(spinBlend, 1f, 14f, dt);
            }
            case CHARGE -> {
                float in = MathX.smooth(MathX.clamp(stateTime / 0.20f, 0f, 1f));
                float k = MathX.clamp(chargeTime / CHARGE_MAX, 0f, 1f);
                Pose.interpolate(REST, CHARGE_POSE, in, pose);
                pose.ty += k * 0.20f;
                pose.arx -= k * 0.22f;
                pose.hx -= k * 0.06f;
                pose.crouch += k * 0.035f;
                bob = -0.02f * in - k * 0.015f;
            }
            case EXECUTE -> bob = executePose();
            case PLUNGE_LAND -> bob = plungeLandingPose();
            case HURT -> {
                float in = MathX.smooth(MathX.clamp(stateTime / 0.1f, 0f, 1f));
                float out = MathX.smooth(MathX.clamp((stateTime - 0.15f) / 0.27f, 0f, 1f));
                Pose.interpolate(REST, HURT_POSE, in * (1f - 0.7f * out), pose);
                bob = -0.03f * in;
            }
            case DEAD -> {
                float f = MathX.smooth(MathX.clamp(stateTime / 1.0f, 0f, 1f));
                Pose.interpolate(REST, DEAD_POSE, f, pose);
                rollAngle = -1.35f * f;
                deathTilt = 0.22f * f;
                pivotY = MathX.lerp(1.02f, 0.45f, f);
            }
            default -> {
            }
        }
        if (state != State.SKILL) {
            spinBlend = MathX.damp(spinBlend, 0f, 9f, dt);
        }
        bodySpin = spinAngle * spinBlend;

        root.setLocalTranslation(pos.x, pos.y + groundY + (mounted ? 0.62f : 0f), pos.z);
        root.setLocalRotation(tmpQ.fromAngles(0f, yaw, 0f));

        rollPivot.setLocalTranslation(0f, pivotY, 0f);
        rollPivot.setLocalRotation(tmpQ.fromAngles(rollAngle, 0f, deathTilt));
        float swayX = state == State.FREE ? FastMath.sin(animTime * 0.55f) * 0.012f : 0f;
        float swayZ = state == State.FREE ? FastMath.cos(animTime * 0.43f) * 0.01f : 0f;
        float stanceY = -1.02f + bob - pose.crouch;
        body.setLocalScale(bodyScale);
        body.setLocalTranslation(swayX,
                MathX.lerp(stanceY, -1.05f * bodyScale, rollTuck), swayZ);
        body.setLocalRotation(tmpQ.fromAngles(0f, bodySpin, 0f));

        updateFireRing(dt);

        setRot(legR, pose.lrx, 0f, pose.lrz);
        setRot(legL, pose.llx, 0f, pose.llz);
        setRot(kneeR, pose.lrk, 0f, 0f);
        setRot(kneeL, pose.llk, 0f, 0f);
        setRot(ankleR, pose.lra, 0f, 0f);
        setRot(ankleL, pose.lla, 0f, 0f);

        setRot(torso, pose.tx, pose.ty, pose.tz);
        setRot(head, pose.hx, pose.hy, pose.hz);
        setRot(armR, pose.arx, pose.ary, pose.arz);
        setRot(elbowR, pose.erx, 0f, 0f);
        setRot(wristR, pose.wrx, 0f, 0f);
        setRot(armL, pose.alx, pose.aly, pose.alz);
        setRot(elbowL, pose.elx, 0f, 0f);
        setRot(wristL, pose.wlx, 0f, 0f);

        staffQ.fromAngles(pose.sx, pose.sy, pose.sz);
        staffQ.set(body.getWorldRotation().mult(staffQ));
        if (!staffReady || dt <= 0f) {
            staffSmooth.set(staffQ);
            staffReady = true;
        } else {
            float lag = state == State.ATTACK ? 26f : 18f;
            staffSmooth.slerp(staffQ, 1f - (float) Math.exp(-lag * dt));
        }
        weapon.setLocalRotation(wristR.getWorldRotation().inverse().mult(staffSmooth));

        updateSecondary(dt, motion);

        boolean swinging = state == State.SKILL
                || (state == State.ATTACK
                && stateTime > attackWindup - 0.05f
                && stateTime < attackHitTime + (isHeavy ? 0.22f : 0.15f));
        if (swinging) {
            fx.swingTrail(staffTip.getWorldTranslation(), staffMid.getWorldTranslation(),
                    weapon.getWorldTranslation());
        }
    }

    private void updateFireRing(float dt) {
        boolean active = state == State.SKILL;
        fireRingBlend = MathX.damp(fireRingBlend, active ? 1f : 0f, active ? 9f : 16f, dt);
        boolean on = fireRingBlend > 0.02f;
        var hint = on ? com.jme3.scene.Spatial.CullHint.Never
                : com.jme3.scene.Spatial.CullHint.Always;
        fireRingOuter.setCullHint(hint);
        fireRingInner.setCullHint(hint);
        if (!on) {
            fireLight.setColor(ColorRGBA.Black);
            return;
        }
        float flicker = 0.82f + 0.16f * FastMath.sin(animTime * 21.3f)
                + 0.05f * FastMath.sin(animTime * 47f);
        float a = fireRingBlend * flicker;
        fireRingOuter.setLocalScale(1f + 0.04f * FastMath.sin(animTime * 12f));
        fireRingInner.setLocalScale(1f + 0.06f * FastMath.sin(animTime * 18f + 1.1f));
        fireRingOuterMat.setColor("Color", new ColorRGBA(1f, 0.38f, 0.05f, 0.5f * a));
        fireRingInnerMat.setColor("Color", new ColorRGBA(1f, 0.62f, 0.16f, 0.32f * a));
        fireLight.setColor(new ColorRGBA(0.95f * a, 0.34f * a, 0.07f * a, 1f));
    }

    private float locomotionPose(float motion) {
        pose.set(REST);
        float s = FastMath.sin(walkPhase);
        float c = FastMath.cos(walkPhase);
        if (motion <= 0.01f) {
            float breath = FastMath.sin(animTime * 1.7f);
            float shift = FastMath.sin(animTime * 0.55f);
            pose.tx += breath * 0.016f;
            pose.ty += shift * 0.035f;
            pose.tz += shift * 0.018f;
            pose.hx += breath * 0.01f - shift * 0.012f;
            pose.hy += shift * 0.05f + FastMath.sin(animTime * 0.37f) * 0.04f;
            pose.arx += breath * 0.02f;
            pose.alx -= breath * 0.012f;
            pose.sx += breath * 0.02f;
            pose.crouch = 0.006f + 0.004f * breath;
            return breath * 0.008f;
        }

        boolean run = motion > 0.85f;
        float amp = run ? 0.86f : 0.62f;
        pose.lrx = s * amp - 0.05f;
        pose.llx = -s * amp - 0.05f;
        pose.lrk = 0.10f + Math.max(0f, -s) * (run ? 1.24f : 0.92f) + (run ? 0.14f : 0f);
        pose.llk = 0.10f + Math.max(0f, s) * (run ? 1.24f : 0.92f) + (run ? 0.14f : 0f);
        pose.lra = -pose.lrx * 0.36f - pose.lrk * 0.18f + 0.03f;
        pose.lla = -pose.llx * 0.36f - pose.llk * 0.18f + 0.03f;
        pose.lrz = 0.045f;
        pose.llz = -0.045f;

        pose.tx = 0.07f + 0.11f * motion + (run ? 0.12f : 0f);
        pose.ty = -s * 0.17f * motion;
        pose.tz = c * 0.04f * motion;
        pose.hx = -0.04f - 0.06f * motion - (run ? 0.04f : 0f);
        pose.hy = s * 0.13f * motion - pose.ty * 0.6f;
        pose.hz = -c * 0.032f * motion;

        pose.arx = -0.10f + s * (run ? 0.82f : 0.54f) * motion;
        pose.arz = 0.20f + 0.04f * motion;
        pose.erx = -0.55f - Math.max(0f, s) * (run ? 0.3f : 0.2f) * motion;
        pose.alx = -0.08f - s * (run ? 0.72f : 0.48f) * motion;
        pose.alz = -0.15f - 0.04f * motion;
        pose.elx = -0.45f - Math.max(0f, -s) * 0.38f * motion;

        pose.sx = -0.22f - 0.2f * motion - (run ? 0.12f : 0f) + s * 0.05f * motion;
        pose.sz = -0.12f - 0.05f * motion - s * 0.04f * motion;
        pose.crouch = 0.012f + Math.abs(c) * 0.024f * motion + (run ? 0.028f : 0f);
        return Math.abs(c) * 0.045f * motion + (run ? 0.02f : 0f);
    }

    private float airbornePose() {
        float rise = MathX.clamp(velY * 0.26f + 0.5f, 0f, 1f);
        Pose.interpolate(JUMP_FALL, JUMP_RISE, rise, pose);
        pose.ty += FastMath.sin(animTime * 2.2f) * 0.03f;
        return 0f;
    }

    private float landingPose() {
        float k = MathX.smooth(MathX.clamp(landTimer / 0.16f, 0f, 1f));
        Pose.interpolate(REST, LAND_POSE, k, pose);
        return -0.02f * k;
    }

    private float plungeLandingPose() {
        if (stateTime < 0.18f) {
            float k = MathX.smooth(MathX.clamp(stateTime / 0.10f, 0f, 1f));
            Pose.interpolate(PLUNGE_ST, PLUNGE_IMPACT, k, pose);
        } else {
            float k = MathX.smooth(MathX.clamp(
                    (stateTime - 0.18f) / Math.max(0.01f, PLUNGE_LAG - 0.18f), 0f, 1f));
            Pose.interpolate(PLUNGE_IMPACT, REST, k, pose);
        }
        float settle = FastMath.sin(MathX.clamp(stateTime / PLUNGE_LAG, 0f, 1f) * FastMath.PI);
        pose.tx += settle * 0.035f;
        return -0.015f * settle;
    }

    private float executePose() {
        float raiseAt = 0.26f;
        float setAt = 0.56f;
        float hitAt = EXEC_HIT;
        float holdAt = hitAt + 0.24f;
        float pushAt = holdAt + 0.34f;
        float pullAt = pushAt + 0.34f;
        float settleAt = pullAt + 0.26f;
        if (stateTime < raiseAt) {
            Pose.interpolate(attackEntry, EXEC_WIND, MathX.smooth(stateTime / raiseAt), pose);
        } else if (stateTime < setAt) {
            float k = MathX.smooth((stateTime - raiseAt) / (setAt - raiseAt));
            Pose.interpolate(EXEC_WIND, EXEC_RAISE, k, pose);
            pose.hx -= 0.04f * k;
        } else if (stateTime < hitAt) {
            float k = (stateTime - setAt) / Math.max(0.01f, hitAt - setAt);
            Pose.interpolate(EXEC_RAISE, EXEC_STAB, k * k, pose);
            pose.arx -= 0.05f * MathX.smooth(k);
            pose.hx += 0.08f * k;
        } else if (stateTime < holdAt) {
            float k = easeOutCubic((stateTime - hitAt) / (holdAt - hitAt));
            Pose.interpolate(EXEC_STAB, EXEC_HOLD, k, pose);
        } else if (stateTime < pushAt) {
            float k = MathX.smooth((stateTime - holdAt) / (pushAt - holdAt));
            Pose.interpolate(EXEC_HOLD, EXEC_STAB, 0.10f + 0.06f * k, pose);
            float tremble = FastMath.sin(stateTime * 42f) * 0.012f;
            pose.sx += tremble;
            pose.tx += tremble * 0.6f;
        } else if (stateTime < pullAt) {
            float k = MathX.smooth((stateTime - pushAt) / (pullAt - pushAt));
            Pose.interpolate(EXEC_STAB, EXEC_PULL, k, pose);
        } else if (stateTime < settleAt) {
            float k = MathX.smooth((stateTime - pullAt) / (settleAt - pullAt));
            Pose.interpolate(EXEC_PULL, EXEC_FOL, k, pose);
        } else {
            float k = MathX.smooth(MathX.clamp(
                    (stateTime - settleAt) / Math.max(0.01f, EXEC_DUR - settleAt), 0f, 1f));
            Pose.interpolate(EXEC_FOL, REST, k, pose);
        }
        if (stateTime < setAt) {
            return -0.04f * MathX.smooth(stateTime / Math.max(0.01f, setAt));
        }
        if (stateTime < hitAt) {
            return -0.05f - 0.05f * MathX.smooth((stateTime - setAt) / Math.max(0.01f, hitAt - setAt));
        }
        return stateTime < pullAt ? -0.10f : -0.07f;
    }

    private void attackPose() {
        if (isAirAttack) {
            airAttackPose();
            return;
        }
        Pose windup;
        Pose strike;
        Pose follow;
        switch (attackKind) {
            case HEAVY -> {
                windup = HEAVY_WU;
                strike = HEAVY_ST;
                follow = HEAVY_FOL;
            }
            case RUN -> {
                windup = RUN_WU;
                strike = RUN_ST;
                follow = RUN_FOL;
            }
            case ROLL -> {
                windup = ROLL_WU;
                strike = ROLL_ST;
                follow = ROLL_ST;
            }
            case LIGHT -> {
                switch (comboIndex) {
                    case 0 -> {
                        windup = L1_WINDUP;
                        strike = L1_STRIKE;
                        follow = L1_FOLLOW;
                    }
                    case 1 -> {
                        windup = L2_WINDUP;
                        strike = L2_STRIKE;
                        follow = L2_FOLLOW;
                    }
                    case 2 -> {
                        windup = L3_WINDUP;
                        strike = L3_STRIKE;
                        follow = L3_FOLLOW;
                    }
                    default -> {
                        windup = L4_WINDUP;
                        strike = L4_STRIKE;
                        follow = L4_FOLLOW;
                    }
                }
            }
            default -> {
                windup = L1_WINDUP;
                strike = L1_STRIKE;
                follow = L1_FOLLOW;
            }
        }

        float followAt = attackHitTime + (attackKind == AttackKind.HEAVY ? 0.20f : 0.10f);
        if (stateTime <= attackWindup) {
            float k = easeOutCubic(stateTime / Math.max(0.01f, attackWindup));
            Pose.interpolate(attackEntry, windup, k, pose);
        } else if (stateTime <= attackHitTime) {
            float t = MathX.clamp((stateTime - attackWindup)
                    / Math.max(0.01f, attackHitTime - attackWindup), 0f, 1f);
            Pose.interpolate(windup, strike, t * t * (3f - 2f * t), pose);
        } else if (stateTime <= followAt) {
            float k = easeOutCubic((stateTime - attackHitTime)
                    / Math.max(0.01f, followAt - attackHitTime));
            Pose.interpolate(strike, follow, k, pose);
        } else {
            float k = MathX.smooth(MathX.clamp(
                    (stateTime - followAt) / Math.max(0.01f, attackDuration - followAt), 0f, 1f));
            Pose.interpolate(follow, REST, k, pose);
        }
    }

    private void airAttackPose() {
        if (isPlunge) {
            if (stateTime <= PLUNGE_RAISE_TIME) {
                float k = MathX.smooth(MathX.clamp(stateTime / PLUNGE_RAISE_TIME, 0f, 1f));
                Pose.interpolate(JUMP_FALL, PLUNGE_WU, k, pose);
            } else if (stateTime <= PLUNGE_CLEAVE_TIME) {
                float t = MathX.clamp((stateTime - PLUNGE_RAISE_TIME)
                        / (PLUNGE_CLEAVE_TIME - PLUNGE_RAISE_TIME), 0f, 1f);
                Pose.interpolate(PLUNGE_WU, PLUNGE_ST, t * t * (3f - 2f * t), pose);
                pose.sx += FastMath.sin(stateTime * 64f) * 0.018f * (1f - t);
            } else {
                float k = MathX.smooth(MathX.clamp(
                        (stateTime - PLUNGE_CLEAVE_TIME) / 0.24f, 0f, 1f));
                Pose.interpolate(PLUNGE_ST, PLUNGE_IMPACT, k * 0.34f, pose);
                pose.tz += FastMath.sin(animTime * 1.6f) * 0.012f;
            }
            return;
        }
        if (stateTime <= attackWindup) {
            float k = easeOutCubic(stateTime / Math.max(0.01f, attackWindup));
            Pose.interpolate(JUMP_RISE, AIR_SLASH_WU, k, pose);
        } else if (stateTime <= attackHitTime + 0.05f) {
            float t = MathX.clamp((stateTime - attackWindup)
                    / Math.max(0.01f, attackHitTime - attackWindup), 0f, 1f);
            Pose.interpolate(AIR_SLASH_WU, AIR_SLASH_ST, t * t, pose);
        } else {
            float k = MathX.smooth(MathX.clamp(
                    (stateTime - attackHitTime - 0.05f)
                            / Math.max(0.01f, attackDuration - attackHitTime - 0.05f), 0f, 1f));
            Pose.interpolate(AIR_SLASH_ST, JUMP_FALL, k, pose);
        }
    }

    private void updateSecondary(float dt, float motion) {
        float t = animTime;
        float pace = state == State.SKILL ? 1.45f : (state == State.ROLL ? 1f : motion);
        for (int i = 0; i < tail.length; i++) {
            float baseCurl = i == 0 ? -2.05f : 0.30f;
            float sway = FastMath.sin(t * (2.1f + pace * 1.6f) - i * 0.65f)
                    * (0.045f + 0.075f * pace);
            float yaw = FastMath.sin(t * (1.6f + pace) - i * 0.5f)
                    * (0.09f + 0.2f * pace) + 0.08f;
            float roll = FastMath.sin(t * (1.3f + pace * 0.8f) - i * 0.4f)
                    * (0.02f + 0.04f * pace) + (i == 0 ? 0.22f : 0f);
            setRot(tail[i], baseCurl + sway + (state == State.DEAD ? 0.3f : 0f), yaw, roll);
        }

        float gust = 0.12f + pace * 0.5f + (state == State.ATTACK ? 0.3f : 0f)
                + (state == State.ROLL ? 0.55f : 0f) + (airborne ? 0.62f : 0f);
        setRot(capeL1, gust + FastMath.sin(t * 5.1f) * 0.06f, 0f, 0.05f);
        setRot(capeL2, 0.1f + gust * 0.45f + FastMath.sin(t * 5.1f + 0.8f) * 0.09f, 0f, 0.03f);
        setRot(capeR1, gust + FastMath.sin(t * 5.1f + 0.35f) * 0.06f, 0f, -0.05f);
        setRot(capeR2, 0.1f + gust * 0.45f + FastMath.sin(t * 5.1f + 1.15f) * 0.09f, 0f, -0.03f);

        float stride = FastMath.sin(walkPhase) * motion;
        setRot(skirt[0], -0.08f - stride * 0.1f, 0f, 0.06f + stride * 0.03f);
        setRot(skirt[1], -0.08f + stride * 0.1f, 0f, -0.06f + stride * 0.03f);
        setRot(skirt[2], 0.15f + pace * 0.3f + FastMath.sin(t * 4.2f) * 0.04f, 0f, 0.05f);
        setRot(skirt[3], 0.15f + pace * 0.3f + FastMath.sin(t * 4.2f + 0.6f) * 0.04f, 0f, -0.05f);
        setRot(skirt[4], 0.06f + pace * 0.28f + FastMath.sin(t * 4.2f + 0.3f) * 0.05f,
                0f, 0.2f + FastMath.sin(t * 3.3f) * 0.04f);

        float scarfBase = 0.32f + pace * 0.45f;
        setRot(scarfL, scarfBase + FastMath.sin(t * 4.6f) * 0.08f, 0f,
                0.06f + FastMath.sin(t * 3.1f) * 0.04f);
        setRot(scarfR, scarfBase + 0.05f + FastMath.sin(t * 4.6f + 0.7f) * 0.07f, 0f, -0.05f);

        float ribbonBase = 0.55f + pace * 0.4f;
        setRot(ribbonL, ribbonBase + FastMath.sin(t * 6f) * 0.08f, 0f,
                0.08f + FastMath.sin(t * 4f) * 0.05f);
        setRot(ribbonR, ribbonBase + 0.06f + FastMath.sin(t * 6f + 0.8f) * 0.08f, 0f, -0.06f);

        setRot(plume, -0.02f + FastMath.sin(t * 2.8f) * 0.035f,
                FastMath.sin(t * 1.9f) * 0.03f, 0f);

        for (Node part : skirt) {
            part.setLocalScale(1f, 1f - 0.5f * rollTuck, 1f);
        }
        capeL1.setLocalScale(1f, 1f - 0.4f * rollTuck, 1f);
        capeR1.setLocalScale(1f, 1f - 0.4f * rollTuck, 1f);
        capeL2.setLocalScale(1f, 1f - 0.25f * rollTuck, 1f);
        capeR2.setLocalScale(1f, 1f - 0.25f * rollTuck, 1f);
        scarfL.setLocalScale(1f, 1f - 0.45f * rollTuck, 1f);
        scarfR.setLocalScale(1f, 1f - 0.45f * rollTuck, 1f);
        ribbonL.setLocalScale(1f, 1f - 0.4f * rollTuck, 1f);
        ribbonR.setLocalScale(1f, 1f - 0.4f * rollTuck, 1f);
        weapon.setLocalScale(MathX.lerp(1f, 0.5f, rollTuck));
    }

    private static float easeOutCubic(float t) {
        t = MathX.clamp(t, 0f, 1f);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static float easeInCubic(float t) {
        t = MathX.clamp(t, 0f, 1f);
        return t * t * t;
    }

    private void setRot(Node node, float x, float y, float z) {
        node.setLocalRotation(tmpQ.fromAngles(x, y, z));
    }

    private static final class Pose {
        float lrx, lrz, lrk, lra;
        float llx, llz, llk, lla;
        float tx, ty, tz;
        float hx, hy, hz;
        float arx, ary, arz, erx, wrx;
        float alx, aly, alz, elx, wlx;
        float sx, sy, sz;
        float crouch;

        Pose legs(float legRx, float legRz, float kneeR, float ankleR,
                  float legLx, float legLz, float kneeL, float ankleL) {
            lrx = legRx;
            lrz = legRz;
            lrk = kneeR;
            lra = ankleR;
            llx = legLx;
            llz = legLz;
            llk = kneeL;
            lla = ankleL;
            return this;
        }

        Pose torso(float x, float y, float z) {
            tx = x;
            ty = y;
            tz = z;
            return this;
        }

        Pose head(float x, float y, float z) {
            hx = x;
            hy = y;
            hz = z;
            return this;
        }

        Pose armR(float x, float y, float z, float elbow, float wrist) {
            arx = x;
            ary = y;
            arz = z;
            erx = elbow;
            wrx = wrist;
            return this;
        }

        Pose armL(float x, float y, float z, float elbow, float wrist) {
            alx = x;
            aly = y;
            alz = z;
            elx = elbow;
            wlx = wrist;
            return this;
        }

        Pose staff(float x, float y, float z) {
            sx = x;
            sy = y;
            sz = z;
            return this;
        }

        Pose crouch(float value) {
            crouch = value;
            return this;
        }

        Pose set(Pose other) {
            lrx = other.lrx;
            lrz = other.lrz;
            lrk = other.lrk;
            lra = other.lra;
            llx = other.llx;
            llz = other.llz;
            llk = other.llk;
            lla = other.lla;
            tx = other.tx;
            ty = other.ty;
            tz = other.tz;
            hx = other.hx;
            hy = other.hy;
            hz = other.hz;
            arx = other.arx;
            ary = other.ary;
            arz = other.arz;
            erx = other.erx;
            wrx = other.wrx;
            alx = other.alx;
            aly = other.aly;
            alz = other.alz;
            elx = other.elx;
            wlx = other.wlx;
            sx = other.sx;
            sy = other.sy;
            sz = other.sz;
            crouch = other.crouch;
            return this;
        }

        static void interpolate(Pose a, Pose b, float t, Pose out) {
            t = MathX.clamp(t, 0f, 1f);
            out.lrx = MathX.lerp(a.lrx, b.lrx, t);
            out.lrz = MathX.lerp(a.lrz, b.lrz, t);
            out.lrk = MathX.lerp(a.lrk, b.lrk, t);
            out.lra = MathX.lerp(a.lra, b.lra, t);
            out.llx = MathX.lerp(a.llx, b.llx, t);
            out.llz = MathX.lerp(a.llz, b.llz, t);
            out.llk = MathX.lerp(a.llk, b.llk, t);
            out.lla = MathX.lerp(a.lla, b.lla, t);
            out.tx = MathX.lerp(a.tx, b.tx, t);
            out.ty = MathX.lerp(a.ty, b.ty, t);
            out.tz = MathX.lerp(a.tz, b.tz, t);
            out.hx = MathX.lerp(a.hx, b.hx, t);
            out.hy = MathX.lerp(a.hy, b.hy, t);
            out.hz = MathX.lerp(a.hz, b.hz, t);
            out.arx = MathX.lerp(a.arx, b.arx, t);
            out.ary = MathX.lerp(a.ary, b.ary, t);
            out.arz = MathX.lerp(a.arz, b.arz, t);
            out.erx = MathX.lerp(a.erx, b.erx, t);
            out.wrx = MathX.lerp(a.wrx, b.wrx, t);
            out.alx = MathX.lerp(a.alx, b.alx, t);
            out.aly = MathX.lerp(a.aly, b.aly, t);
            out.alz = MathX.lerp(a.alz, b.alz, t);
            out.elx = MathX.lerp(a.elx, b.elx, t);
            out.wlx = MathX.lerp(a.wlx, b.wlx, t);
            out.sx = MathX.lerp(a.sx, b.sx, t);
            out.sy = MathX.lerp(a.sy, b.sy, t);
            out.sz = MathX.lerp(a.sz, b.sz, t);
            out.crouch = MathX.lerp(a.crouch, b.crouch, t);
        }
    }
}
