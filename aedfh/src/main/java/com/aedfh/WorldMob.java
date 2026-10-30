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

/** Small roaming enemies: Godrick soldiers, demihumans, skeletons and dire wolves. */
final class WorldMob extends Target {

    enum Kind { SOLDIER, DEMIHUMAN, SKELETON, WOLF }

    enum State { IDLE, CHASE, WINDUP, STRIKE, RECOVER, DEAD }

    /** Quaternius 狼（CC0）：51 骨骼、12 段剪辑，剪辑名带 {@code AnimalArmature|} 前缀。 */
    private static final String WOLF_GLB = "Models/Quaternius/Wolf.glb";
    private static final String W_IDLE = "AnimalArmature|Idle";
    private static final String W_WALK = "AnimalArmature|Walk";
    private static final String W_RUN = "AnimalArmature|Gallop";
    private static final String W_ATTACK = "AnimalArmature|Attack";
    private static final String W_DEATH = "AnimalArmature|Death";

    private final WorldTerrain terrain;
    private final Fx fx;
    private final Kind kind;
    private final Node root = new Node("mob");
    private final Node body = new Node("mobBody");
    private final Node legL = new Node("legL");
    private final Node legR = new Node("legR");
    private final Node armL = new Node("armL");
    private final Node armR = new Node("armR");
    private final Node head = new Node("head");
    private final Node weapon = new Node("weapon");
    private final Node wolfTorso = new Node("wolfTorso");
    private ActorRig rig;
    private final Vector3f spawn = new Vector3f();
    private final Vector3f tmp = new Vector3f();

    private final float maxHp;
    private final float damage;
    private final float speed;
    private final int runes;
    private final float modelScale;

    private float hp;
    private State state = State.IDLE;
    private float stateTime;
    private float deadTime;
    private float walk;
    private float flash;
    private float idleWait;
    private float patrolAngle;
    private boolean aggro;
    private boolean pinned;

    boolean evDied;
    boolean evAttack;

    WorldMob(AssetManager am, Node parent, Fx fx, WorldTerrain terrain,
             Kind kind, float x, float z, float yaw) {
        this.fx = fx;
        this.terrain = terrain;
        this.kind = kind;
        switch (kind) {
            case SOLDIER -> {
                maxHp = 210f;
                damage = 24f;
                speed = 2.6f;
                runes = 55;
                modelScale = 1f;
            }
            case DEMIHUMAN -> {
                maxHp = 130f;
                damage = 17f;
                speed = 3.9f;
                runes = 34;
                modelScale = 0.86f;
            }
            case WOLF -> {
                maxHp = 105f;
                damage = 15f;
                speed = 5.4f;
                runes = 26;
                modelScale = 1f;
            }
            default -> {
                maxHp = 165f;
                damage = 21f;
                speed = 2.2f;
                runes = 46;
                modelScale = 0.98f;
            }
        }
        hp = maxHp;
        pos.set(x, terrain.height(x, z), z);
        this.yaw = yaw;
        spawn.set(pos);
        build(am);
        parent.attachChild(root);
        root.setShadowMode(RenderQueue.ShadowMode.Cast);
        root.setLocalTranslation(pos);
        root.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
    }

    private void build(AssetManager am) {
        root.attachChild(body);
        body.setLocalScale(modelScale);
        if (kind == Kind.WOLF) {
            rig = ActorRig.load(am, WOLF_GLB, displayHeight());
            if (rig != null) {
                rig.play(W_IDLE, true, 1f);
                body.setLocalScale(1f);
                body.attachChild(rig.node());
                return;
            }
            buildWolf(am);
            Gfx.tangents(root);
            return;
        }
        if (rig == null) {
            rig = ActorRig.load(am, rigName(), displayHeight(),
                    kind == Kind.DEMIHUMAN
                            ? new String[]{"Mug", "Barbarian_Hat", "Barbarian_Round_Shield"}
                            : new String[0]);
        }
        if (rig != null) {
            rig.play("Idle", true, 1f);
            body.setLocalScale(1f);
            body.attachChild(rig.node());
            return;
        }

        ColorRGBA armor = kind == Kind.SOLDIER
                ? new ColorRGBA(0.34f, 0.33f, 0.30f, 1f)
                : kind == Kind.DEMIHUMAN
                ? new ColorRGBA(0.44f, 0.33f, 0.22f, 1f)
                : new ColorRGBA(0.66f, 0.63f, 0.55f, 1f);
        ColorRGBA cloth = kind == Kind.SOLDIER
                ? new ColorRGBA(0.33f, 0.10f, 0.09f, 1f)
                : kind == Kind.DEMIHUMAN
                ? new ColorRGBA(0.28f, 0.31f, 0.16f, 1f)
                : new ColorRGBA(0.24f, 0.23f, 0.20f, 1f);
        Material metal = Gfx.lit(am, armor, armor.mult(0.35f),
                new ColorRGBA(0.3f, 0.3f, 0.28f, 1f), 24f);
        Gfx.mapped(metal, am, "metal_plate", 0.55f);
        Material fabric = Gfx.lit(am, cloth, cloth.mult(0.4f),
                new ColorRGBA(0.02f, 0.02f, 0.02f, 1f), 2f);
        Gfx.mapped(fabric, am, "rough_linen", 0.8f);
        Material skin = Gfx.lit(am, new ColorRGBA(0.52f, 0.40f, 0.31f, 1f),
                new ColorRGBA(0.15f, 0.11f, 0.08f, 1f), ColorRGBA.Black, 2f);

        boolean thin = kind == Kind.SKELETON;
        float hip = thin ? 0.92f : 0.86f;
        Gfx.box("pelvis", 0.19f, 0.12f, 0.14f, kind == Kind.DEMIHUMAN ? skin : fabric,
                body, 0f, hip, 0f);
        Node torso = new Node("torso");
        torso.setLocalTranslation(0f, hip + 0.16f, 0f);
        body.attachChild(torso);
        float torsoW = thin ? 0.15f : (kind == Kind.DEMIHUMAN ? 0.25f : 0.21f);
        Gfx.box("chest", torsoW, 0.28f, 0.16f,
                kind == Kind.DEMIHUMAN ? skin : metal, torso, 0f, 0.22f, 0f);
        Gfx.box("skirt", torsoW + 0.05f, 0.18f, 0.17f, fabric, torso, 0f, -0.07f, 0.02f);

        head.setLocalTranslation(0f, 0.60f, 0.01f);
        torso.attachChild(head);
        Gfx.sphere("skull", thin ? 0.135f : 0.145f, 8,
                thin ? skin : skin, head, 0f, 0f, 0f);
        if (kind == Kind.SOLDIER) {
            Gfx.box("helmet", 0.17f, 0.10f, 0.17f, metal, head, 0f, 0.075f, 0f);
            Gfx.box("visor", 0.15f, 0.035f, 0.02f, metal, head, 0f, 0.01f, 0.14f);
        } else if (kind == Kind.DEMIHUMAN) {
            Gfx.box("jaw", 0.11f, 0.055f, 0.10f, skin, head, 0f, -0.12f, 0.06f);
            Gfx.sphere("earL", 0.05f, 5, skin, head, -0.14f, 0.03f, 0f);
            Gfx.sphere("earR", 0.05f, 5, skin, head, 0.14f, 0.03f, 0f);
        }

        float shoulder = torsoW + 0.06f;
        armL.setLocalTranslation(-shoulder, 0.44f, 0f);
        armR.setLocalTranslation(shoulder, 0.44f, 0f);
        torso.attachChild(armL);
        torso.attachChild(armR);
        for (Node arm : new Node[]{armL, armR}) {
            Gfx.box("upper", 0.075f, 0.20f, 0.08f, metal, arm, 0f, -0.16f, 0f);
            Gfx.box("fore", 0.065f, 0.18f, 0.07f, thin ? skin : fabric, arm,
                    0f, -0.50f, 0.02f);
        }

        legL.setLocalTranslation(-0.12f, hip - 0.03f, 0f);
        legR.setLocalTranslation(0.12f, hip - 0.03f, 0f);
        body.attachChild(legL);
        body.attachChild(legR);
        for (Node leg : new Node[]{legL, legR}) {
            Gfx.box("thigh", 0.095f, 0.24f, 0.10f, fabric, leg, 0f, -0.20f, 0f);
            Gfx.box("shin", 0.08f, 0.23f, 0.085f, metal, leg, 0f, -0.62f, 0.01f);
            Gfx.box("foot", 0.095f, 0.055f, 0.16f, metal, leg, 0f, -0.86f, 0.06f);
        }

        if (kind == Kind.DEMIHUMAN) {
            Node club = new Node("club");
            club.setLocalTranslation(0f, -0.68f, 0.05f);
            armR.attachChild(club);
            Gfx.box("handle", 0.045f, 0.34f, 0.045f, fabric, club, 0f, -0.12f, 0f);
            Gfx.cylinder("head", 0.14f, 0.36f, metal, club, 0f, -0.46f, 0f);
            weapon.setLocalTranslation(0f, 0f, 0f);
            club.attachChild(weapon);
        } else {
            weapon.setLocalTranslation(0f, -0.72f, 0.05f);
            armR.attachChild(weapon);
            Gfx.box("grip", 0.035f, 0.13f, 0.035f, fabric, weapon, 0f, 0f, 0f);
            Gfx.box("blade", 0.028f, 0.52f, 0.085f, metal, weapon, 0f, -0.55f, 0f);
            Gfx.box("guard", 0.14f, 0.03f, 0.04f, metal, weapon, 0f, -0.12f, 0f);
        }
        if (kind == Kind.SOLDIER) {
            Node shield = new Node("shield");
            shield.setLocalTranslation(0f, -0.46f, 0.10f);
            armL.attachChild(shield);
            Gfx.cylinder("shieldFace", 0.29f, 0.06f, metal, shield, 0f, 0f, 0f);
            Gfx.box("shieldBand", 0.04f, 0.24f, 0.02f, fabric, shield, 0f, 0f, 0.05f);
        }
        Gfx.tangents(root);
    }

    /** Quadruped dire wolf: armL/armR carry the front legs, legL/legR the hind legs. */
    private void buildWolf(AssetManager am) {
        ColorRGBA fur = new ColorRGBA(0.42f, 0.38f, 0.32f, 1f);
        ColorRGBA belly = new ColorRGBA(0.56f, 0.52f, 0.45f, 1f);
        Material coat = Gfx.lit(am, fur, fur.mult(0.38f),
                new ColorRGBA(0.10f, 0.09f, 0.08f, 1f), 3f);
        Gfx.mapped(coat, am, "faux_fur", 0.9f);
        Material pale = Gfx.lit(am, belly, belly.mult(0.4f),
                new ColorRGBA(0.10f, 0.09f, 0.08f, 1f), 3f);
        Material eye = Gfx.lit(am, new ColorRGBA(0.95f, 0.72f, 0.20f, 1f),
                new ColorRGBA(0.25f, 0.16f, 0.03f, 1f), ColorRGBA.Black, 48f);

        wolfTorso.setLocalTranslation(0f, 0.62f, 0f);
        body.attachChild(wolfTorso);
        Gfx.box("chest", 0.185f, 0.185f, 0.22f, coat, wolfTorso, 0f, 0.03f, 0.20f);
        Gfx.box("ribs", 0.165f, 0.155f, 0.24f, coat, wolfTorso, 0f, 0.02f, -0.12f);
        Gfx.box("back", 0.185f, 0.085f, 0.36f, coat, wolfTorso, 0f, 0.16f, 0.02f);
        Gfx.box("belly", 0.145f, 0.075f, 0.34f, pale, wolfTorso, 0f, -0.10f, 0.00f);

        Node neck = new Node("wolfNeck");
        neck.setLocalTranslation(0f, 0.13f, 0.40f);
        neck.setLocalRotation(new Quaternion().fromAngleAxis(-0.42f, Vector3f.UNIT_X));
        wolfTorso.attachChild(neck);
        Gfx.box("neckMesh", 0.115f, 0.115f, 0.14f, coat, neck, 0f, 0f, 0f);

        head.setLocalTranslation(0f, 0.08f, 0.16f);
        neck.attachChild(head);
        Gfx.box("skull", 0.105f, 0.10f, 0.13f, coat, head, 0f, 0f, 0f);
        Gfx.box("muzzle", 0.062f, 0.052f, 0.12f, coat, head, 0f, -0.035f, 0.16f);
        Gfx.box("nose", 0.042f, 0.032f, 0.03f, pale, head, 0f, -0.025f, 0.28f);
        for (int side = -1; side <= 1; side += 2) {
            Gfx.taperedCylinder("ear", 0.006f, 0.045f, 0.12f, coat, head,
                    side * 0.07f, 0.13f, -0.02f);
            Gfx.box("eye", 0.022f, 0.018f, 0.02f, eye, head, side * 0.055f, 0.015f, 0.10f);
        }
        Gfx.box("teeth", 0.05f, 0.018f, 0.06f, pale, head, 0f, -0.075f, 0.22f);

        Node tail = new Node("wolfTail");
        tail.setLocalTranslation(0f, 0.10f, -0.34f);
        tail.setLocalRotation(new Quaternion().fromAngleAxis(0.95f, Vector3f.UNIT_X));
        wolfTorso.attachChild(tail);
        Gfx.taperedCylinder("tailMesh", 0.025f, 0.075f, 0.46f, coat, tail, 0f, -0.19f, 0f);

        armL.setLocalTranslation(-0.135f, -0.02f, 0.24f);
        armR.setLocalTranslation(0.135f, -0.02f, 0.24f);
        wolfTorso.attachChild(armL);
        wolfTorso.attachChild(armR);
        legL.setLocalTranslation(-0.135f, -0.02f, -0.20f);
        legR.setLocalTranslation(0.135f, -0.02f, -0.20f);
        wolfTorso.attachChild(legL);
        wolfTorso.attachChild(legR);
        for (Node leg : new Node[]{armL, armR, legL, legR}) {
            Gfx.box("upper", 0.052f, 0.15f, 0.06f, coat, leg, 0f, -0.14f, 0f);
            Gfx.box("lower", 0.042f, 0.15f, 0.048f, coat, leg, 0f, -0.42f, 0.01f);
            Gfx.box("paw", 0.055f, 0.032f, 0.085f, coat, leg, 0f, -0.58f, 0.035f);
        }
    }

    @Override
    float radius() {
        if (kind == Kind.WOLF) {
            return 0.42f;
        }
        return kind == Kind.DEMIHUMAN ? 0.42f : 0.36f;
    }

    @Override
    float maxHp() {
        return maxHp;
    }

    @Override
    float hp() {
        return hp;
    }

    @Override
    float displayHeight() {
        if (kind == Kind.WOLF) {
            return 1.05f;
        }
        return kind == Kind.DEMIHUMAN ? 1.85f : 2.1f;
    }

    @Override
    float groundY() {
        return pos.y;
    }

    @Override
    boolean canBeExecuted(Vector3f from) {
        return false;
    }

    @Override
    void lockForExecution(Vector3f from) {
    }

    @Override
    void clearExecutionLock() {
    }

    @Override
    void takeExecution(float dmg, Vector3f impact) {
        takeHit(dmg, 100f, impact);
    }

    @Override
    void takeHit(float dmg, float poiseDmg, Vector3f impact) {
        if (!alive) {
            return;
        }
        hp -= dmg;
        flash = 1f;
        tmp.set(pos.x - impact.x, 0f, pos.z - impact.z);
        if (tmp.lengthSquared() > 1e-4f) {
            tmp.normalizeLocal().multLocal(kind == Kind.DEMIHUMAN ? 0.9f
                    : (kind == Kind.WOLF ? 0.55f : 0.35f));
            pos.x += tmp.x;
            pos.z += tmp.z;
        }
        aggro = true;
        if (state == State.IDLE) {
            state = State.CHASE;
        }
        if (hp <= 0f) {
            alive = false;
            state = State.DEAD;
            deadTime = 0f;
            evDied = true;
            fx.dust(new Vector3f(pos.x, pos.y + 0.3f, pos.z), 14);
        }
    }

    void update(float dt, Player player) {
        if (!alive) {
            deadTime += dt;
            if (rig != null) {
                rig.play(deathClip(), false, 1f);
            } else {
                float t = MathX.clamp(deadTime / 1.1f, 0f, 1f);
                body.setLocalRotation(new Quaternion().fromAngleAxis(t * 1.5f, Vector3f.UNIT_X));
                body.setLocalTranslation(0f, -t * 0.55f, 0f);
            }
            root.setLocalTranslation(pos.x, pos.y, pos.z);
            if (deadTime > 2.4f) {
                root.setCullHint(Spatial.CullHint.Always);
            }
            return;
        }
        flash = Math.max(0f, flash - dt * 5f);
        stateTime += dt;
        float dist = MathX.distXZ(pos, player.pos);
        if (!player.alive || pinned) {
            aggro = false;
        } else if (dist < 21f) {
            aggro = true;
        } else if (dist > 65f) {
            aggro = false;
        }

        float face = yaw;
        boolean moving = false;
        switch (state) {
            case IDLE -> {
                idleWait -= dt;
                if (idleWait <= 0f) {
                    idleWait = 2.5f + (float) Math.random() * 3f;
                    patrolAngle = (float) (Math.random() * FastMath.TWO_PI);
                }
                if (aggro) {
                    state = State.CHASE;
                    stateTime = 0f;
                } else if (dist > 3f) {
                    tmp.set(FastMath.sin(patrolAngle), 0f, FastMath.cos(patrolAngle));
                    tryMove(tmp, speed * 0.32f, dt, player);
                    face = (float) Math.atan2(tmp.x, tmp.z);
                    moving = true;
                }
            }
            case CHASE -> {
                if (!aggro) {
                    state = State.IDLE;
                    break;
                }
                face = MathX.yawTo(pos, player.pos);
                if (dist > 2.05f + player.radius() + radius()) {
                    tmp.set(player.pos.x - pos.x, 0f, player.pos.z - pos.z).normalizeLocal();
                    tryMove(tmp, speed, dt, player);
                    moving = true;
                } else {
                    state = State.WINDUP;
                    stateTime = 0f;
                }
            }
            case WINDUP -> {
                face = MathX.yawTo(pos, player.pos);
                if (stateTime > (kind == Kind.WOLF ? 0.30f
                        : (kind == Kind.DEMIHUMAN ? 0.42f : 0.55f))) {
                    state = State.STRIKE;
                    stateTime = 0f;
                    if (dist < 2.5f + player.radius()
                            && MathX.angleBetween(yaw, pos, player.pos) < 1.25f) {
                        player.takeHit(damage, pos);
                        evAttack = true;
                        fx.spark(new Vector3f(player.pos.x, player.worldY() + 1.1f,
                                player.pos.z), new ColorRGBA(0.9f, 0.25f, 0.12f, 1f), 8);
                    }
                }
            }
            case STRIKE -> {
                if (stateTime > 0.18f) {
                    state = State.RECOVER;
                    stateTime = 0f;
                }
            }
            case RECOVER -> {
                if (stateTime > 0.55f) {
                    state = aggro ? State.CHASE : State.IDLE;
                    stateTime = 0f;
                }
            }
            default -> {
            }
        }
        yaw = MathX.lerpAngle(yaw, face, 1f - (float) Math.exp(-10f * dt));
        if (dist > 90f && !pinned) {
            pos.set(spawn);
            aggro = false;
            state = State.IDLE;
            hp = maxHp;
        }
        pos.y = terrain.height(pos.x, pos.z);
        root.setLocalTranslation(pos.x, pos.y, pos.z);
        root.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
        animate(dt, moving);
    }

    private void tryMove(Vector3f dir, float speed, float dt, Player player) {
        float nx = pos.x + dir.x * speed * dt;
        float nz = pos.z + dir.z * speed * dt;
        if (Math.abs(nx) > WorldData.HALF - 30f || Math.abs(nz) > WorldData.HALF - 30f) {
            return;
        }
        if (terrain.depth(nx, nz) > WorldData.WADE_DEPTH * 0.8f) {
            return;
        }
        if (terrain.slope(nx, nz) > 0.95f) {
            return;
        }
        float d = MathX.distXZ(new Vector3f(nx, 0f, nz), player.pos);
        if (d < 0.8f) {
            return;
        }
        pos.x = nx;
        pos.z = nz;
    }

    private void animate(float dt, boolean moving) {
        if (rig != null) {
            animateRig(moving);
            return;
        }
        if (kind == Kind.WOLF) {
            animateWolf(dt, moving);
            return;
        }
        if (moving) {
            walk += dt * (kind == Kind.DEMIHUMAN ? 11f : 7.5f);
            float s = FastMath.sin(walk);
            legL.setLocalRotation(new Quaternion().fromAngleAxis(s * 0.55f, Vector3f.UNIT_X));
            legR.setLocalRotation(new Quaternion().fromAngleAxis(-s * 0.55f, Vector3f.UNIT_X));
            armL.setLocalRotation(new Quaternion().fromAngleAxis(-s * 0.35f, Vector3f.UNIT_X));
            armR.setLocalRotation(new Quaternion().fromAngleAxis(s * 0.32f, Vector3f.UNIT_X));
            body.setLocalTranslation(0f, Math.abs(s) * 0.035f, 0f);
        } else {
            float s = FastMath.sin(walk);
            legL.setLocalRotation(Quaternion.IDENTITY);
            legR.setLocalRotation(Quaternion.IDENTITY);
            armL.setLocalRotation(Quaternion.IDENTITY);
            if (state == State.WINDUP) {
                float t = MathX.clamp(stateTime / 0.5f, 0f, 1f);
                armR.setLocalRotation(new Quaternion().fromAngleAxis(-1.8f * t, Vector3f.UNIT_X));
            } else if (state == State.STRIKE) {
                armR.setLocalRotation(new Quaternion().fromAngleAxis(1.15f, Vector3f.UNIT_X));
            } else {
                armR.setLocalRotation(new Quaternion().fromAngleAxis(0.05f * s, Vector3f.UNIT_X));
            }
            body.setLocalTranslation(0f, 0f, 0f);
        }
        if (flash > 0f) {
            body.setLocalScale(modelScale * (1f + 0.06f * flash));
        } else {
            body.setLocalScale(modelScale);
        }
    }

    /** 骨骼动画驱动：状态机只挑剪辑，迈步/挥砍的节奏全交给剪辑本身。 */
    private void animateRig(boolean moving) {
        if (state == State.WINDUP || state == State.STRIKE || state == State.RECOVER) {
            String clip = attackClip();
            float len = rig.clipLength(clip);
            float total = windupTime() + 0.18f + 0.55f;
            rig.play(clip, false, len > 0.01f ? len / total : 1f);
        } else if (state == State.CHASE) {
            rig.play(runClip(), true, 1f);
        } else if (moving) {
            rig.play(walkClip(), true, 1f);
        } else {
            rig.play(idleClip(), true, 1f);
        }
        body.setLocalScale(flash > 0f ? 1f + 0.06f * flash : 1f);
    }

    private String idleClip() {
        return kind == Kind.WOLF ? W_IDLE : "Idle";
    }

    private String walkClip() {
        return kind == Kind.WOLF ? W_WALK : "Walking_A";
    }

    private String runClip() {
        return kind == Kind.WOLF ? W_RUN : "Running_A";
    }

    private String deathClip() {
        return kind == Kind.WOLF ? W_DEATH : "Death_A";
    }

    private String rigName() {
        return switch (kind) {
            case SOLDIER -> "Knight";
            case DEMIHUMAN -> "Barbarian";
            default -> "Skeleton_Warrior";
        };
    }

    private String attackClip() {
        return switch (kind) {
            case SOLDIER -> "2H_Melee_Attack_Slice";
            case DEMIHUMAN -> "Unarmed_Melee_Attack_Punch_A";
            case WOLF -> W_ATTACK;
            default -> "Unarmed_Melee_Attack_Punch_B";
        };
    }

    private float windupTime() {
        return kind == Kind.WOLF ? 0.30f : (kind == Kind.DEMIHUMAN ? 0.42f : 0.55f);
    }

    /** Diagonal quadruped gait: front pair and hind pair swing in opposition. */
    private void animateWolf(float dt, boolean moving) {
        if (moving) {
            walk += dt * 13f;
            float s = FastMath.sin(walk);
            armL.setLocalRotation(new Quaternion().fromAngleAxis(s * 0.62f, Vector3f.UNIT_X));
            armR.setLocalRotation(new Quaternion().fromAngleAxis(-s * 0.62f, Vector3f.UNIT_X));
            legL.setLocalRotation(new Quaternion().fromAngleAxis(-s * 0.62f, Vector3f.UNIT_X));
            legR.setLocalRotation(new Quaternion().fromAngleAxis(s * 0.62f, Vector3f.UNIT_X));
            body.setLocalTranslation(0f, Math.abs(s) * 0.05f, 0f);
            wolfTorso.setLocalRotation(new Quaternion().fromAngleAxis(s * 0.05f, Vector3f.UNIT_X));
        } else {
            float s = FastMath.sin(walk * 0.6f);
            armL.setLocalRotation(Quaternion.IDENTITY);
            armR.setLocalRotation(Quaternion.IDENTITY);
            legL.setLocalRotation(Quaternion.IDENTITY);
            legR.setLocalRotation(Quaternion.IDENTITY);
            body.setLocalTranslation(0f, 0f, 0f);
            wolfTorso.setLocalRotation(Quaternion.IDENTITY);
            if (state == State.WINDUP) {
                float t = MathX.clamp(stateTime / 0.30f, 0f, 1f);
                wolfTorso.setLocalRotation(new Quaternion().fromAngleAxis(
                        -0.34f * t, Vector3f.UNIT_X));
            } else if (state == State.STRIKE) {
                wolfTorso.setLocalRotation(new Quaternion().fromAngleAxis(0.30f, Vector3f.UNIT_X));
            } else {
                wolfTorso.setLocalRotation(new Quaternion().fromAngleAxis(
                        0.02f * s, Vector3f.UNIT_X));
            }
        }
        if (flash > 0f) {
            body.setLocalScale(modelScale * (1f + 0.06f * flash));
        } else {
            body.setLocalScale(modelScale);
        }
    }

    void respawn() {
        pos.set(spawn);
        hp = maxHp;
        alive = true;
        state = State.IDLE;
        aggro = false;
        evDied = false;
        deadTime = 0f;
        root.setCullHint(Spatial.CullHint.Never);
        body.setLocalRotation(Quaternion.IDENTITY);
        body.setLocalTranslation(0f, 0f, 0f);
        wolfTorso.setLocalRotation(Quaternion.IDENTITY);
        if (rig != null) {
            body.setLocalScale(1f);
            rig.stop();
            rig.play(idleClip(), true, 1f);
        }
    }

    Vector3f spawn() {
        return spawn;
    }

    /** 离屏画廊专用：把杂兵钉在指定位置，免得巡逻/仇恨把取景搅乱。 */
    void pinForShot(float x, float z, float faceYaw) {
        // 钉住后不能再吃 90m 脱战回巢，否则刚挪走就被 update 拉回出生点
        pinned = true;
        pos.set(x, terrain.height(x, z), z);
        yaw = faceYaw;
        state = State.IDLE;
        aggro = false;
        idleWait = 60f;
        root.setCullHint(Spatial.CullHint.Never);
        root.setLocalTranslation(pos.x, pos.y, pos.z);
        root.setLocalRotation(new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y));
    }

    /** 同样用于截图：直接藏起来，免得杂兵走进特写。 */
    void hideForShot() {
        pinned = true;
        root.setCullHint(Spatial.CullHint.Always);
    }

    int runeReward() {
        return runes;
    }

    Kind kind() {
        return kind;
    }
}
