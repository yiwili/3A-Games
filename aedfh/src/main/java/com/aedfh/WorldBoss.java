package com.aedfh;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import java.util.Random;

/** Data-driven field boss: one brain, nine Limgrave silhouettes. */
final class WorldBoss extends Target {

    enum Archetype {
        SENTINEL, CAVALRY, BEAST, HUNTER, MARGIT, DRAGON, MARINER, AVATAR, DEATHBIRD,
        GODRICK
    }

    enum State { IDLE, CHASE, WINDUP, STRIKE, RECOVER, STAGGER, DEAD }

    enum MoveKind { MELEE, DASH, LEAP, AOE, BREATH, SUMMON, FLURRY, TELEPORT }

    static final class Move {
        final String name;
        final MoveKind kind;
        final float windup;
        final float active;
        final float recover;
        final float range;
        final float arc;
        final float damage;
        final float radius;
        final float dash;
        final float cooldown;
        final int hits;
        final int phase;

        Move(String name, MoveKind kind, float windup, float active, float recover,
             float range, float arc, float damage, float radius, float dash,
             float cooldown, int hits, int phase) {
            this.name = name;
            this.kind = kind;
            this.windup = windup;
            this.active = active;
            this.recover = recover;
            this.range = range;
            this.arc = arc;
            this.damage = damage;
            this.radius = radius;
            this.dash = dash;
            this.cooldown = cooldown;
            this.hits = hits;
            this.phase = phase;
        }
    }

    static final class Spec {
        final String id;
        final String name;
        final String title;
        final String location;
        final Archetype archetype;
        final float x;
        final float z;
        final float hp;
        final float poise;
        final float radius;
        final int runes;
        final boolean nightOnly;
        final boolean gate;

        Spec(String id, String name, String title, String location, Archetype archetype,
             float x, float z, float hp, float poise, float radius, int runes,
             boolean nightOnly, boolean gate) {
            this.id = id;
            this.name = name;
            this.title = title;
            this.location = location;
            this.archetype = archetype;
            this.x = x;
            this.z = z;
            this.hp = hp;
            this.poise = poise;
            this.radius = radius;
            this.runes = runes;
            this.nightOnly = nightOnly;
            this.gate = gate;
        }
    }

    static final Spec[] SPECS = {
            new Spec("tree-sentinel", "大树守卫", "TREE SENTINEL", "引导之始", Archetype.SENTINEL,
                    26f, 248f, 1750f, 95f, 1.5f, 2200, false, false),
            new Spec("agheel", "飞龙亚基尔", "FLYING DRAGON AGHEEL", "亚基尔湖", Archetype.DRAGON,
                    250f, 108f, 3400f, 140f, 4.6f, 5200, false, false),
            new Spec("darriwil", "猎犬骑士达瑞威尔", "BLOODHOUND KNIGHT DARRIWIL", "无主猎犬的封印监牢",
                    Archetype.BEAST, 120f, 420f, 1500f, 80f, 1.15f, 1900, false, false),
            new Spec("tibia", "提比亚的唤声船", "TIBIA MARINER", "召唤水村", Archetype.MARINER,
                    390f, -325f, 1800f, 90f, 2.0f, 2600, false, false),
            new Spec("avatar", "黄金树化身", "ERDTREE AVATAR", "小黄金树", Archetype.AVATAR,
                    516f, 434f, 2800f, 120f, 2.4f, 3800, false, false),
            new Spec("nights-cavalry", "夜之骑兵", "NIGHT'S CAVALRY", "亚基尔湖北的桥", Archetype.CAVALRY,
                    205f, -70f, 1600f, 85f, 1.5f, 2400, true, false),
            new Spec("deathbird", "死之鸟", "DEATHBIRD", "风暴山丘东南", Archetype.DEATHBIRD,
                    270f, -300f, 2000f, 100f, 1.7f, 3000, true, false),
            new Spec("bell-hunter", "铃珠猎人", "BELL BEARING HUNTER", "习战者的破屋", Archetype.HUNTER,
                    201f, -245f, 2200f, 105f, 1.25f, 3200, true, false),
            new Spec("margit", "“恶兆妖鬼”玛尔基特", "MARGIT, THE FELL OMEN", "史东薇尔城门前",
                    Archetype.MARGIT, -386f, -402f, 4400f, 150f, 1.35f, 9000, false, true),
            new Spec("godrick", "“接枝的怪物”葛瑞克", "GODRICK THE GRAFTED", "史东薇尔城·主殿",
                    Archetype.GODRICK, -405f, -588f, 6200f, 240f, 1.9f, 12000, false, false)
    };

    private static final Move[] SENTINEL_MOVES = {
            new Move("halberd sweep", MoveKind.MELEE, 0.78f, 0.20f, 0.92f, 6.1f, 1.55f, 34f, 0f, 0f, 1.15f, 1, 0),
            new Move("overhead cleave", MoveKind.MELEE, 1.02f, 0.20f, 1.05f, 4.9f, 0.72f, 46f, 0f, 0f, 1.6f, 1, 0),
            new Move("lance charge", MoveKind.DASH, 0.82f, 0.55f, 1.10f, 9.0f, 0.55f, 40f, 0f, 17f, 2.4f, 1, 0),
            new Move("hoof stomp", MoveKind.AOE, 0.92f, 0.22f, 0.92f, 0f, 0f, 30f, 6.2f, 0f, 1.8f, 1, 0),
            new Move("golden charge", MoveKind.DASH, 0.66f, 0.62f, 0.86f, 10f, 0.7f, 44f, 0f, 19f, 2.2f, 1, 2)
    };
    private static final Move[] CAVALRY_MOVES = {
            new Move("reaping halberd", MoveKind.MELEE, 0.58f, 0.18f, 0.70f, 5.6f, 1.65f, 28f, 0f, 0f, 0.9f, 1, 0),
            new Move("night charge", MoveKind.DASH, 0.62f, 0.48f, 0.72f, 9.5f, 0.55f, 34f, 0f, 18f, 1.7f, 1, 0),
            new Move("wheel slash", MoveKind.FLURRY, 0.55f, 0.72f, 0.75f, 4.4f, 2.2f, 18f, 0f, 0f, 1.5f, 3, 0),
            new Move("trample", MoveKind.AOE, 0.74f, 0.20f, 0.74f, 0f, 0f, 26f, 4.8f, 0f, 1.6f, 1, 2)
    };
    private static final Move[] BEAST_MOVES = {
            new Move("claw flurry", MoveKind.FLURRY, 0.36f, 0.60f, 0.58f, 3.7f, 1.5f, 14f, 0f, 0f, 1.0f, 4, 0),
            new Move("hound leap", MoveKind.LEAP, 0.52f, 0.32f, 0.70f, 12f, 0f, 30f, 4.2f, 0f, 1.8f, 1, 0),
            new Move("bloodhound dash", MoveKind.DASH, 0.42f, 0.34f, 0.58f, 8f, 0.96f, 24f, 0f, 12f, 1.2f, 1, 0),
            new Move("frenzied rake", MoveKind.FLURRY, 0.28f, 0.55f, 0.45f, 3.4f, 2.0f, 11f, 0f, 0f, 1.3f, 5, 2)
    };
    private static final Move[] HUNTER_MOVES = {
            new Move("greatsword sweep", MoveKind.MELEE, 0.70f, 0.20f, 0.85f, 4.8f, 1.65f, 32f, 0f, 0f, 1.1f, 1, 0),
            new Move("executioner cleave", MoveKind.MELEE, 0.92f, 0.22f, 1.00f, 4.2f, 0.65f, 42f, 0f, 0f, 1.5f, 1, 0),
            new Move("bell toll", MoveKind.AOE, 1.10f, 0.25f, 0.95f, 0f, 0f, 36f, 7.5f, 0f, 2.2f, 1, 0),
            new Move("hunter's step", MoveKind.DASH, 0.38f, 0.34f, 0.55f, 7.0f, 0.9f, 28f, 0f, 10f, 1.4f, 1, 2)
    };
    private static final Move[] MARGIT_MOVES = {
            new Move("staff sweep", MoveKind.MELEE, 0.66f, 0.18f, 0.75f, 5.4f, 1.7f, 30f, 0f, 0f, 0.9f, 1, 0),
            new Move("overhead smash", MoveKind.MELEE, 0.88f, 0.20f, 0.90f, 4.6f, 0.7f, 41f, 0f, 0f, 1.3f, 1, 0),
            new Move("omen leap", MoveKind.LEAP, 0.70f, 0.40f, 0.85f, 15f, 0f, 36f, 4.6f, 0f, 1.9f, 1, 0),
            new Move("dagger flurry", MoveKind.FLURRY, 0.40f, 0.78f, 0.65f, 3.9f, 1.9f, 12f, 0f, 0f, 1.2f, 5, 0),
            new Move("holy hammer", MoveKind.AOE, 1.05f, 0.28f, 1.10f, 0f, 0f, 48f, 7.2f, 0f, 2.6f, 1, 2),
            new Move("crucible charge", MoveKind.DASH, 0.58f, 0.50f, 0.72f, 12f, 0.8f, 34f, 0f, 16f, 2.0f, 1, 2)
    };
    private static final Move[] DRAGON_MOVES = {
            new Move("bite", MoveKind.MELEE, 0.62f, 0.22f, 0.80f, 8.5f, 0.85f, 38f, 0f, 0f, 1.0f, 1, 0),
            new Move("tail sweep", MoveKind.AOE, 0.78f, 0.24f, 0.92f, 0f, 0f, 34f, 9.5f, 0f, 1.7f, 1, 0),
            new Move("dragon stomp", MoveKind.AOE, 0.95f, 0.22f, 1.00f, 0f, 0f, 42f, 7.0f, 0f, 2.0f, 1, 0),
            new Move("agheel fire", MoveKind.BREATH, 1.05f, 1.15f, 1.15f, 19f, 0.62f, 15f, 0f, 0f, 3.0f, 0, 0),
            new Move("wing gust", MoveKind.AOE, 0.70f, 0.30f, 0.85f, 0f, 0f, 26f, 11f, 0f, 2.2f, 1, 2)
    };
    private static final Move[] MARINER_MOVES = {
            new Move("oar sweep", MoveKind.MELEE, 0.60f, 0.20f, 0.72f, 4.6f, 1.8f, 26f, 0f, 0f, 1.0f, 1, 0),
            new Move("spectral horn", MoveKind.AOE, 0.95f, 0.30f, 0.90f, 0f, 0f, 30f, 7.8f, 0f, 2.1f, 1, 0),
            new Move("raise the dead", MoveKind.SUMMON, 1.00f, 0.35f, 0.85f, 0f, 0f, 0f, 0f, 0f, 4.5f, 2, 0),
            new Move("tide step", MoveKind.TELEPORT, 0.55f, 0.25f, 0.65f, 0f, 0f, 0f, 0f, 0f, 2.8f, 1, 0),
            new Move("drowning surge", MoveKind.AOE, 0.85f, 0.28f, 0.95f, 0f, 0f, 36f, 8.5f, 0f, 2.4f, 1, 2)
    };
    private static final Move[] AVATAR_MOVES = {
            new Move("club slam", MoveKind.MELEE, 0.82f, 0.22f, 0.92f, 5.0f, 0.85f, 38f, 0f, 0f, 1.2f, 1, 0),
            new Move("triple slam", MoveKind.FLURRY, 0.62f, 0.90f, 0.90f, 4.8f, 1.2f, 20f, 0f, 0f, 1.8f, 3, 0),
            new Move("erdtree bloom", MoveKind.AOE, 1.10f, 0.30f, 1.05f, 0f, 0f, 42f, 9.0f, 0f, 2.5f, 1, 0),
            new Move("root spike", MoveKind.AOE, 0.85f, 0.25f, 0.95f, 0f, 0f, 34f, 6.5f, 0f, 2.0f, 1, 2)
    };
    private static final Move[] DEATHBIRD_MOVES = {
            new Move("carrion peck", MoveKind.MELEE, 0.48f, 0.18f, 0.62f, 5.0f, 0.85f, 26f, 0f, 0f, 0.9f, 1, 0),
            new Move("grave wing", MoveKind.MELEE, 0.62f, 0.22f, 0.72f, 5.8f, 1.8f, 30f, 0f, 0f, 1.2f, 1, 0),
            new Move("death swoop", MoveKind.DASH, 0.55f, 0.46f, 0.80f, 13f, 0.9f, 34f, 0f, 19f, 1.9f, 1, 0),
            new Move("frost screech", MoveKind.AOE, 0.95f, 0.30f, 0.95f, 0f, 0f, 32f, 8.0f, 0f, 2.3f, 1, 2)
    };
    private static final Move[] GODRICK_MOVES = {
            new Move("grafted cleave", MoveKind.MELEE, 0.72f, 0.22f, 0.86f, 6.4f, 1.6f, 42f, 0f, 0f, 1.1f, 1, 0),
            new Move("twin arm swing", MoveKind.MELEE, 0.56f, 0.28f, 0.74f, 5.6f, 2.2f, 27f, 0f, 0f, 1.5f, 2, 0),
            new Move("shoulder ram", MoveKind.DASH, 0.66f, 0.52f, 0.84f, 11f, 0.7f, 44f, 0f, 15f, 2.0f, 1, 0),
            new Move("ground pound", MoveKind.AOE, 0.90f, 0.24f, 0.96f, 0f, 0f, 46f, 8.4f, 0f, 2.1f, 1, 0),
            new Move("grafted roar", MoveKind.AOE, 1.02f, 0.30f, 1.05f, 0f, 0f, 38f, 9.5f, 0f, 2.6f, 1, 2),
            new Move("beast leap", MoveKind.LEAP, 0.62f, 0.40f, 0.82f, 16f, 0f, 46f, 5.4f, 0f, 2.2f, 1, 2)
    };

    private static final ColorRGBA GOLD = new ColorRGBA(1f, 0.72f, 0.28f, 1f);
    private static final ColorRGBA RED = new ColorRGBA(1f, 0.20f, 0.08f, 1f);
    private static final ColorRGBA SPECTRAL = new ColorRGBA(0.45f, 0.75f, 1f, 1f);
    private static final ColorRGBA FROST = new ColorRGBA(0.62f, 0.86f, 1f, 1f);

    final Spec spec;
    final WorldTerrain terrain;
    final Fx fx;
    final BossModel model;

    float hp;
    float poise;
    float poiseMax;
    State state = State.IDLE;
    float stateTime;
    Move move;
    float cooldown;
    float walk;
    float animTime;
    float hitFlash;
    float deathTime;
    float airLift;
    float breathTimer;
    float flurryTimer;
    int flurryHits;
    boolean engaged;
    boolean available = true;
    boolean phase2;
    boolean evDied;
    boolean evPlayerHit;
    boolean evSummon;
    boolean evEngaged;

    private final Vector3f spawn = new Vector3f();
    private final Vector3f tmp = new Vector3f();
    private final Random rnd = new Random(31);
    private final Move[] moves;
    private float poiseDelay;

    WorldBoss(AssetManager am, Node parent, Fx fx, WorldTerrain terrain, Spec spec) {
        this.spec = spec;
        this.terrain = terrain;
        this.fx = fx;
        this.hp = spec.hp;
        this.poiseMax = spec.poise;
        this.poise = poiseMax;
        this.moves = movesFor(spec.archetype);
        spawn.set(spec.x, terrain.height(spec.x, spec.z), spec.z);
        pos.set(spawn);
        yaw = 3.14f;
        model = new BossModel(am, this, parent);
        model.setVisible(false);
    }

    private static Move[] movesFor(Archetype archetype) {
        return switch (archetype) {
            case SENTINEL -> SENTINEL_MOVES;
            case CAVALRY -> CAVALRY_MOVES;
            case BEAST -> BEAST_MOVES;
            case HUNTER -> HUNTER_MOVES;
            case MARGIT -> MARGIT_MOVES;
            case DRAGON -> DRAGON_MOVES;
            case MARINER -> MARINER_MOVES;
            case AVATAR -> AVATAR_MOVES;
            case DEATHBIRD -> DEATHBIRD_MOVES;
            case GODRICK -> GODRICK_MOVES;
        };
    }

    @Override
    float radius() {
        return spec.radius;
    }

    @Override
    float maxHp() {
        return spec.hp;
    }

    @Override
    float hp() {
        return hp;
    }

    @Override
    float displayHeight() {
        return switch (spec.archetype) {
            case DRAGON -> 7.2f;
            case AVATAR -> 8.4f;
            case DEATHBIRD -> 6.0f;
            case MARINER -> 3.4f;
            case MARGIT -> 4.0f;
            case GODRICK -> 5.4f;
            case SENTINEL, CAVALRY -> 4.0f;
            default -> 2.8f;
        };
    }

    @Override
    float groundY() {
        return pos.y;
    }

    @Override
    boolean enraged() {
        return phase2;
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
        takeHit(dmg, 60f, impact);
    }

    @Override
    void takeHit(float dmg, float poiseDmg, Vector3f impact) {
        if (!alive || state == State.DEAD) {
            return;
        }
        engaged = true;
        hp = Math.max(0f, hp - dmg);
        poise -= poiseDmg;
        hitFlash = 1f;
        poiseDelay = 0.55f;
        evPlayerHit = true;
        tmp.set(pos.x, pos.y + displayHeight() * 0.55f, pos.z);
        fx.spark(tmp.clone(), new ColorRGBA(1f, 0.82f, 0.45f, 1f), 10);
        if (!phase2 && hp < spec.hp * 0.5f) {
            phase2 = true;
            model.enrage();
            fx.shockwave(new Vector3f(pos.x, pos.y + 0.1f, pos.z), radius() + 3f, 0.6f,
                    new ColorRGBA(1f, 0.5f, 0.15f, 0.55f));
        }
        if (poise <= 0f && state != State.STAGGER && state != State.DEAD) {
            poise = poiseMax;
            state = State.STAGGER;
            stateTime = 0f;
            move = null;
            airLift = 0f;
            fx.spark(new Vector3f(pos.x, pos.y + 1.4f, pos.z), GOLD, 24);
        }
        if (hp <= 0f) {
            alive = false;
            state = State.DEAD;
            stateTime = 0f;
            deathTime = 0f;
            evDied = true;
            move = null;
            airLift = 0f;
            fx.shockwave(new Vector3f(pos.x, pos.y + 0.1f, pos.z), radius() + 5f, 0.8f,
                    new ColorRGBA(1f, 0.72f, 0.3f, 0.5f));
            fx.spark(new Vector3f(pos.x, pos.y + 1.6f, pos.z), GOLD, 46);
        }
    }

    void update(float dt, Player player) {
        if (!available) {
            model.setVisible(false);
            return;
        }
        boolean near = engaged || MathX.distXZ(pos, player.pos) < 240f;
        if (near != model.visible()) {
            model.setVisible(near);
        }
        if (!near) {
            return;
        }
        animTime += dt;
        hitFlash = Math.max(0f, hitFlash - dt * 4f);
        poiseDelay = Math.max(0f, poiseDelay - dt);
        if (!alive) {
            deathTime += dt;
            stateTime += dt;
            model.animate(dt);
            return;
        }
        cooldown = Math.max(0f, cooldown - dt);
        float dist = MathX.distXZ(pos, player.pos);
        if (!engaged) {
            if (player.alive && dist < (spec.gate ? 18f : 27f)) {
                engaged = true;
                evEngaged = true;
            } else {
                state = State.IDLE;
                stateTime += dt;
                pos.y = terrain.height(pos.x, pos.z);
                model.animate(dt);
                return;
            }
        }
        if (!player.alive) {
            engaged = false;
            resetField();
            return;
        }
        if (!spec.gate && dist > 85f) {
            engaged = false;
            resetField();
            return;
        }
        stateTime += dt;
        yaw = MathX.lerpAngle(yaw, MathX.yawTo(pos, player.pos),
                1f - (float) Math.exp(-(phase2 ? 8f : 6f) * dt));

        switch (state) {
            case IDLE -> {
                state = State.CHASE;
                stateTime = 0f;
            }
            case CHASE -> {
                pos.y = terrain.height(pos.x, pos.z);
                float desired = 3.3f + radius();
                if (dist > desired) {
                    float speed = (spec.archetype == Archetype.DRAGON ? 4.2f
                            : spec.archetype == Archetype.BEAST ? 6.2f
                            : spec.archetype == Archetype.CAVALRY ? 6.4f : 4.4f)
                            * (phase2 ? 1.18f : 1f);
                    tmp.set(player.pos.x - pos.x, 0f, player.pos.z - pos.z).normalizeLocal();
                    float nx = pos.x + tmp.x * speed * dt;
                    float nz = pos.z + tmp.z * speed * dt;
                    if (canStand(nx, nz)) {
                        pos.x = nx;
                        pos.z = nz;
                    }
                    walk += dt * speed * 1.2f;
                }
                airLift = MathX.damp(airLift, 0f, 5f, dt);
                if (cooldown <= 0f && dist < 22f) {
                    chooseMove(dist);
                }
            }
            case WINDUP -> {
                airLift = MathX.damp(airLift, 0f, 6f, dt);
                if (stateTime >= move.windup) {
                    beginStrike(player);
                }
            }
            case STRIKE -> {
                updateStrike(dt, player);
            }
            case RECOVER -> {
                if (stateTime >= move.recover) {
                    state = State.CHASE;
                    stateTime = 0f;
                    cooldown = move.cooldown;
                    move = null;
                    airLift = 0f;
                }
            }
            case STAGGER -> {
                airLift = 0f;
                if (stateTime >= 1.75f) {
                    state = State.CHASE;
                    stateTime = 0f;
                    cooldown = 0.45f;
                }
            }
            default -> {
            }
        }
        if (state != State.DEAD) {
            pos.y = terrain.height(pos.x, pos.z);
        }
        model.animate(dt);
    }

    private void chooseMove(float dist) {
        Move best = null;
        float bestScore = -1f;
        for (Move m : moves) {
            if (m.phase == 2 && !phase2) {
                continue;
            }
            float min = switch (m.kind) {
                case AOE -> 0f;
                case BREATH -> 6f;
                case DASH, LEAP -> 5.8f;
                case SUMMON, TELEPORT -> 0f;
                default -> 1.8f;
            };
            float max = switch (m.kind) {
                case BREATH -> 22f;
                case DASH, LEAP -> 18f;
                case AOE -> 8f;
                case SUMMON, TELEPORT -> 30f;
                default -> m.range + radius();
            };
            if (dist < min || dist > max) {
                continue;
            }
            float score = rnd.nextFloat() + (m.phase == 2 ? 0.35f : 0f)
                    + (m.kind == MoveKind.MELEE ? 0.15f : 0f);
            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
        }
        if (best == null) {
            best = moves[0];
        }
        move = best;
        state = State.WINDUP;
        stateTime = 0f;
        airLift = 0f;
        tellFx(best);
    }

    private void tellFx(Move m) {
        ColorRGBA color = switch (spec.archetype) {
            case DRAGON -> RED;
            case MARINER -> SPECTRAL;
            case DEATHBIRD -> FROST;
            case AVATAR -> GOLD;
            default -> spec.archetype == Archetype.MARGIT ? GOLD : new ColorRGBA(1f, 0.55f, 0.2f, 1f);
        };
        Vector3f top = new Vector3f(pos.x, pos.y + displayHeight() * 0.7f, pos.z);
        switch (m.kind) {
            case AOE, SUMMON, TELEPORT -> fx.shockwave(
                    new Vector3f(pos.x, pos.y + 0.08f, pos.z), m.radius + 1.2f, m.windup,
                    new ColorRGBA(color.r, color.g, color.b, 0.28f));
            case BREATH -> fx.flame(new Vector3f(pos.x, pos.y + 2.6f, pos.z), 2.0f, 6);
            case DASH, LEAP -> fx.dust(new Vector3f(pos.x, pos.y + 0.2f, pos.z), 12);
            default -> fx.spark(top, color, 6);
        }
    }

    private void beginStrike(Player player) {
        state = State.STRIKE;
        stateTime = 0f;
        breathTimer = 0f;
        flurryTimer = 0f;
        flurryHits = 0;
        float dmg = move.damage * (phase2 ? 1.22f : 1f);
        switch (move.kind) {
            case MELEE -> {
                if (inMelee(player, move.range, move.arc)) {
                    hitPlayer(player, dmg, 0.55f);
                }
            }
            case DASH -> {
                tmp.set(player.pos.x - pos.x, 0f, player.pos.z - pos.z);
                float d = tmp.length();
                if (d > 0.01f) {
                    tmp.multLocal(Math.min(move.dash, Math.max(0f, d - 2.2f)) / d);
                    float nx = pos.x + tmp.x;
                    float nz = pos.z + tmp.z;
                    if (canStand(nx, nz)) {
                        pos.x = nx;
                        pos.z = nz;
                    }
                }
                if (inMelee(player, 3.1f + radius(), 1.15f)) {
                    hitPlayer(player, dmg, 0.75f);
                }
                fx.dust(new Vector3f(pos.x, pos.y + 0.15f, pos.z), 20);
            }
            case LEAP -> {
                if (spec.gate) {
                    pos.x = MathX.clamp(player.pos.x, -440f, -315f);
                    pos.z = MathX.clamp(player.pos.z, -452f, -360f);
                } else {
                    float d = MathX.distXZ(pos, player.pos);
                    if (d < move.range) {
                        tmp.set(player.pos.x - pos.x, 0f, player.pos.z - pos.z)
                                .normalizeLocal().multLocal(Math.max(0f, d - 2.4f));
                        pos.x += tmp.x;
                        pos.z += tmp.z;
                    }
                }
                pos.y = terrain.height(pos.x, pos.z);
                airLift = 0.2f;
                fx.shockwave(new Vector3f(pos.x, pos.y + 0.08f, pos.z), move.radius, 0.55f,
                        new ColorRGBA(1f, 0.62f, 0.24f, 0.5f));
                fx.dust(new Vector3f(pos.x, pos.y + 0.15f, pos.z), 24);
                if (MathX.distXZ(pos, player.pos) < move.radius + 0.4f) {
                    hitPlayer(player, dmg, 0.85f);
                }
            }
            case AOE -> {
                fract(player, dmg);
            }
            case BREATH -> {
                fx.flame(new Vector3f(pos.x, pos.y + 2.7f, pos.z), 2.6f, 12);
            }
            case SUMMON -> {
                evSummon = true;
                fx.flame(new Vector3f(pos.x, pos.y + 0.3f, pos.z), 5.0f, 18);
                fx.shockwave(new Vector3f(pos.x, pos.y + 0.08f, pos.z), 6f, 0.8f,
                        new ColorRGBA(0.4f, 0.7f, 1f, 0.4f));
            }
            case TELEPORT -> {
                Vector3f behind = new Vector3f(
                        player.pos.x - MathX.forward(player.yaw).x * 6f, 0f,
                        player.pos.z - MathX.forward(player.yaw).z * 6f);
                if (!canStand(behind.x, behind.z)) {
                    behind.set(pos);
                }
                fx.flash(new Vector3f(pos.x, pos.y + 1.4f, pos.z), SPECTRAL, 1.2f);
                pos.set(behind);
                pos.y = terrain.height(pos.x, pos.z);
                fx.flash(new Vector3f(pos.x, pos.y + 1.4f, pos.z), SPECTRAL, 1.2f);
                fx.flame(new Vector3f(pos.x, pos.y + 0.2f, pos.z), 3.2f, 12);
            }
            case FLURRY -> {
                flurryTick(player, dmg);
            }
        }
    }

    private void fract(Player player, float dmg) {
        fx.shockwave(new Vector3f(pos.x, pos.y + 0.08f, pos.z), move.radius, 0.6f,
                new ColorRGBA(1f, 0.6f, 0.2f, 0.45f));
        fx.flame(new Vector3f(pos.x, pos.y + 0.2f, pos.z), move.radius * 0.6f, 14);
        if (MathX.distXZ(pos, player.pos) < move.radius + player.radius()) {
            hitPlayer(player, dmg, 0.9f);
        }
    }

    private void updateStrike(float dt, Player player) {
        float dmg = move.damage * (phase2 ? 1.22f : 1f);
        switch (move.kind) {
            case BREATH -> {
                breathTimer -= dt;
                if (breathTimer <= 0f) {
                    breathTimer = 0.16f;
                    if (inMelee(player, move.range, move.arc)) {
                        hitPlayer(player, dmg, 0.32f);
                    }
                    Vector3f fwd = MathX.forward(yaw);
                    for (int i = 0; i < 3; i++) {
                        float d = 3f + i * 5.2f + (float) Math.random() * 3f;
                        fx.flame(new Vector3f(pos.x + fwd.x * d, pos.y + 0.4f,
                                pos.z + fwd.z * d), 1.6f, 3);
                    }
                }
            }
            case FLURRY -> {
                flurryTimer -= dt;
                if (flurryTimer <= 0f && flurryHits < Math.max(1, move.hits)) {
                    flurryTimer = move.active / Math.max(1, move.hits);
                    flurryHits++;
                    flurryTick(player, dmg);
                }
            }
            default -> {
            }
        }
        if (stateTime >= move.active) {
            state = State.RECOVER;
            stateTime = 0f;
        }
    }

    private void flurryTick(Player player, float dmg) {
        if (inMelee(player, move.range, move.arc)) {
            hitPlayer(player, dmg, 0.25f);
        }
        Vector3f fwd = MathX.forward(yaw);
        fx.spark(new Vector3f(pos.x + fwd.x * 1.8f, pos.y + 1.3f,
                pos.z + fwd.z * 1.8f), new ColorRGBA(1f, 0.72f, 0.4f, 1f), 6);
    }

    private boolean inMelee(Player player, float range, float arc) {
        float d = MathX.distXZ(pos, player.pos);
        return d < range + player.radius() && d < range + radius() + 2f
                && MathX.angleBetween(yaw, pos, player.pos) < arc;
    }

    private void hitPlayer(Player player, float dmg, float shake) {
        if (player.takeHit(dmg, pos)) {
            cameraShake = Math.max(cameraShake, shake);
        }
    }

    float cameraShake;

    private boolean canStand(float x, float z) {
        if (Math.abs(x) > WorldData.HALF - 20f || Math.abs(z) > WorldData.HALF - 20f) {
            return false;
        }
        return terrain.height(x, z) > WorldData.WATER_Y + 0.4f
                && terrain.slope(x, z) < 1.05f;
    }

    private void resetField() {
        if (spec.gate) {
            return;
        }
        pos.set(spawn);
        hp = spec.hp;
        poise = poiseMax;
        phase2 = false;
        state = State.IDLE;
        stateTime = 0f;
        move = null;
        cooldown = 0f;
        airLift = 0f;
        deathTime = 0f;
        yaw = 3.14f;
        model.resetPose();
    }

    void setAvailable(boolean value) {
        if (available == value) {
            return;
        }
        available = value;
        model.setVisible(value && alive);
        if (!value) {
            resetField();
            engaged = false;
            model.setVisible(false);
        }
    }

    void forceEngage() {
        if (alive && available) {
            engaged = true;
        }
    }

    void setGatePhase(boolean active) {
        if (!spec.gate) {
            return;
        }
        engaged = active;
    }

    boolean gate() {
        return spec.gate;
    }

    boolean nightOnly() {
        return spec.nightOnly;
    }

    float spawnX() {
        return spec.x;
    }

    float spawnZ() {
        return spec.z;
    }

    String id() {
        return spec.id;
    }

    String displayName() {
        return spec.name;
    }

    String title() {
        return spec.title;
    }

    int runeReward() {
        return spec.runes;
    }

    void respawnBoss() {
        hp = spec.hp;
        poisedReset();
        alive = true;
        state = State.IDLE;
        engaged = false;
        pos.set(spawn);
        phase2 = false;
        deathTime = 0f;
        model.resetPose();
    }

    private void poisedReset() {
        poise = poiseMax;
        stateTime = 0f;
        move = null;
        cooldown = 0f;
        airLift = 0f;
    }
}
