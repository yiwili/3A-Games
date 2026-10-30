package com.aedfh;

import com.jme3.app.SimpleApplication;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.input.event.InputEvent;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.collision.CollisionResults;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Ray;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FogFilter;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeSystem;
import com.jme3.system.lwjgl.LwjglWindow;
import com.jme3.util.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Open-world Limgrave: title/saves, graces, levelling, field bosses, the
 * somersault cloud and the Stormveil gate duel.
 */
public final class WorldApp extends SimpleApplication {

    private enum Phase { TITLE, NAME, PLAY, PAUSE, GRACE, LEVEL, TRAVEL, MAP, DEAD, ENDING }

    private static final float HOUR_RATE = 1f / 180f;
    private static final float AUTOSAVE = 14f;
    private static final int TRAVEL_PAGE = 8;
    private static final int ACT_PREV = -1;
    private static final int ACT_NEXT = -2;
    private static final int ACT_BACK = -3;

    /** 玩家碰撞圆盘半径（XZ）与"站不住"的坡度阈值（与 standable 共用）。 */
    private static final float PLAYER_RADIUS = 0.45f;
    private static final float SLIDE_SLOPE = 1.15f;
    /** 涉水时脚下一圈涟漪的间隔（秒）。 */
    private static final float RIPPLE_INTERVAL = 0.42f;
    private float rippleT;

    /** 筋斗云专属动作「腾云冲刺」：0.36s 内前冲约 14m，冷却 4.5s。 */
    private static final float CLOUD_DASH_TIME = 0.36f;
    private static final float CLOUD_DASH_SPEED = 40f;
    private static final float CLOUD_DASH_CD = 4.5f;

    private final boolean worldTest;
    private final boolean worldShots;
    private boolean headless;
    /** 输入回调是否放行：离屏 shots 全关，worldtest 只放行注入的合成事件（映射层也要被测到）。 */
    private boolean inputLive;

    private WorldTerrain terrain;
    private WorldProps props;
    private WorldCollision collision;
    private Fx fx;
    private Player player;
    private CloudMount cloud;
    private CameraRig rig;
    private WorldHud hud;

    private final List<WorldBoss> bosses = new ArrayList<>();
    private final List<WorldMob> mobs = new ArrayList<>();
    private final List<Target> targets = new ArrayList<>();
    private final List<SaveData> slots = new ArrayList<>();
    private final DummyTarget dummy = new DummyTarget();
    private final float[] bossCorpse = new float[WorldBoss.SPECS.length];

    private SaveData save;
    private final SaveData previewSave = new SaveData(0);
    private WorldBoss gateBoss;
    private Target lockedTarget;
    private Target execTarget;
    private WorldBoss shownBoss;
    private WorldData.GraceDef currentGrace;

    private Node lostNode;
    private Material lostGlow;
    private Node fogWall;
    private Material fogMat;

    private DirectionalLight sun;
    private DirectionalLight rim;
    private DirectionalLight fill;
    private DirectionalLight shotFill;
    private AmbientLight ambient;
    private PointLight lantern;
    private FogFilter fog;
    private Material skyTint;
    private Material waterTint;

    private Phase phase = Phase.TITLE;
    private int selectedSlot;
    private boolean deleteArmed;
    private String nameBuffer = "";

    private String[] menuOptions = new String[0];
    private int[] travelAction = new int[0];
    private int menuSelected;
    private int travelPage;
    private Phase travelReturn = Phase.GRACE;
    private Phase mapReturn = Phase.PLAY;

    private boolean keyFwd;
    private boolean keyBack;
    private boolean keyLeft;
    private boolean keyRight;
    private boolean keySprint;
    private boolean keySkill;
    private boolean keyChord;
    private boolean charging;
    private float sprintDownAt;
    private float time;

    private float titleTime;
    private float autoSaveTimer = AUTOSAVE;
    private float victoryTimer;
    private float cloudDashCd;
    private float cloudDashT;
    private final Vector3f cloudDashDir = new Vector3f();
    private float hourCheck;
    private boolean lastNight;
    private float locationCheck;
    private float loseRunesFlash;

    private boolean endingActive;
    private float endingTimer;
    private Vector3f endingFocus = new Vector3f();

    private boolean fullscreen;
    private int windowedX;
    private int windowedY;
    private int windowedW = 1280;
    private int windowedH = 720;

    private int frames;
    private String pendingShot;
    private boolean portrait;
    private boolean gateActive;
    private WorldBoss titleBoss;

    // ---- 启动加载：后台烘焙 + 渲染线程分步装配 + GUI 加载动画 ----
    private static final float BAKE_SHARE = 0.42f;
    private static final String[] LOAD_STEPS = {
            "正在铺设宁姆格福的大地…",
            "正在安置崖石与草木…",
            "正在唤醒褪色者…",
            "正在点亮黄金树的天光…",
            "正在编排首领与游魂…",
            "正在开辟洞窟与赐福…",
            "正在展开地图与存档…"
    };
    private LoadScreen loadScreen;
    private WorldTerrain.Bake terrainBake;
    private volatile Throwable bakeError;
    private boolean bakeReady;
    private boolean loading;
    private boolean stageAnnounced;
    private int loadStage = 1;
    private float finishHold;
    private long loadStartMs;

    // 开局试炼洞窟：玩家必须先打赢顽石猿王才能离开出生点
    private static final float CAVE_X = 0f;
    private static final float CAVE_Z = 348f;
    private static final float CAVE_R = 15.2f;
    private static final Vector3f CAVE_SPAWN = new Vector3f(CAVE_X, 0f, CAVE_Z - 7f);
    private static final Vector3f CAVE_BOSS_SPOT = new Vector3f(CAVE_X, 0f, CAVE_Z + 6.2f);
    private Boss caveBoss;
    private boolean caveSealed;
    private boolean caveBossActive;
    private float caveFloorY;
    private Geometry caveBoulder;
    private float caveOpenT;
    private Node caveGraceNode;

    private final Vector3f shotCam = new Vector3f(Float.NaN, 0f, 0f);
    private final Vector3f shotAim = new Vector3f();
    private final Vector3f shotOff = new Vector3f();
    private WorldBoss shotTrack;
    private boolean poseShotMode;
    private static final String[] GALLERY = {"tree-sentinel", "darriwil", "tibia", "avatar",
            "nights-cavalry", "deathbird", "bell-hunter", "godrick"};

    private final List<String> testProblems = new ArrayList<>();
    private final List<String> testNotes = new ArrayList<>();
    private float testWallZ;
    private float testSteepY;
    private float testSteepSlope;
    private float testWadeDepth;
    private float testPondShore;
    private float testStartZ;
    private float testRidePhase;
    private float testDashX;
    private float testDashZ;
    private float testApeHp;
    private float testHpMax;
    private int testLevel;
    private int testStat0;
    private int testRunes;
    private float testLostX;
    private float testLostZ;
    private int testLostRunes;
    private float testSkillHp;
    private int testSkillFx;
    private boolean testBossKilled;

    private double perfTickMs;
    private double perfPeakMs;
    private int perfSamples;
    private int perfWarmup;
    private static final int PERF_WARMUP = 40;
    private static final double PERF_BUDGET_MS = 33.0;

    private WorldApp(boolean worldTest, boolean worldShots) {
        this.worldTest = worldTest;
        this.worldShots = worldShots;
        this.headless = worldTest || worldShots;
        this.inputLive = !headless || worldTest;
    }

    public static void main(String[] args) {
        boolean test = false;
        boolean shots = false;
        for (String arg : args) {
            if ("--worldtest".equals(arg)) {
                test = true;
            } else if ("--worldshots".equals(arg)) {
                shots = true;
            }
        }
        if (test || shots) {
            File dir = new File(System.getProperty("user.dir"), "build/worldtest-saves");
            System.setProperty("eldenmonkey.saves", dir.getAbsolutePath());
        }
        WorldApp app = new WorldApp(test, shots);
        AppSettings settings = new AppSettings(true);
        settings.setTitle("艾尔登法猴 — 宁姆格福");
        settings.setResolution(1280, 720);
        settings.setResizable(false);
        settings.setVSync(true);
        settings.setSamples(test || shots ? 0 : 4);
        // OpenAL context creation throws on hosts without a usable audio endpoint, and jME
        // treats that as fatal: the app dies before the window opens. The game has no audio
        // assets, so the subsystem stays off instead of risking the whole startup.
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        if (test || shots) {
            app.start(com.jme3.system.JmeContext.Type.OffscreenSurface);
        } else {
            app.start();
        }
    }

    @Override
    public void simpleInitApp() {
        setPauseOnLostFocus(false);
        setDisplayStatView(false);
        setDisplayFps(false);
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
        cam.setFrustumPerspective(55f, 1280f / 720f, 0.12f, 4200f);
        viewPort.setBackgroundColor(new ColorRGBA(0.015f, 0.017f, 0.024f, 1f));

        if (headless && System.getProperty("aedfh.staged") == null) {
            // 离屏自测走同步装配：帧号、截图序号必须和没有加载屏时完全一致。
            long t0 = System.currentTimeMillis();
            terrainBake = WorldTerrain.bake();
            System.out.println("[LOAD] bake " + (System.currentTimeMillis() - t0) + " ms");
            for (int step = 1; step <= LOAD_STEPS.length; step++) {
                t0 = System.currentTimeMillis();
                loadStep(step);
                System.out.println("[LOAD] " + LOAD_STEPS[step - 1] + " "
                        + (System.currentTimeMillis() - t0) + " ms");
            }
            loading = false;
            return;
        }

        loadStartMs = System.currentTimeMillis();
        loading = true;
        bakeThread().start();
        loadScreen = new LoadScreen(assetManager, guiNode, cam);
    }

    /** 地形贴图的纯 CPU 烘焙放到后台线程，主线程只负责画加载动画。 */
    private Thread bakeThread() {
        Thread th = new Thread(() -> {
            try {
                terrainBake = WorldTerrain.bake();
            } catch (Throwable ex) {
                bakeError = ex;
            }
        }, "limgrave-bake");
        th.setDaemon(true);
        th.setPriority(Thread.NORM_PRIORITY - 1);
        return th;
    }

    private void tickLoading(float tpf) {
        if (bakeError != null) {
            throw new RuntimeException("terrain bake failed", bakeError);
        }
        if (loadScreen == null) {
            return;
        }
        if (!bakeReady) {
            if (terrainBake == null) {
                loadScreen.update(tpf, WorldTerrain.bakeProgress() * BAKE_SHARE,
                        "正在烘焙地形与地表权重…");
                return;
            }
            bakeReady = true;
        }
        if (finishHold > 0f) {
            finishHold -= tpf;
            loadScreen.update(tpf, 1f, "即将进入宁姆格福…");
            if (finishHold <= 0f) {
                finishLoading();
            }
            return;
        }
        int step = loadStage;
        if (!stageAnnounced) {
            // 先把这一阶段的名字画出来，下一帧再干这段重活：冻结期间用户看到的是正确的阶段。
            float done = (step - 1) / (float) LOAD_STEPS.length;
            loadScreen.update(tpf, BAKE_SHARE + (1f - BAKE_SHARE) * done,
                    LOAD_STEPS[step - 1]);
            stageAnnounced = true;
            return;
        }
        long t0 = System.currentTimeMillis();
        loadStep(step);
        System.out.println("[LOAD] " + LOAD_STEPS[step - 1] + " "
                + (System.currentTimeMillis() - t0) + " ms");
        stageAnnounced = false;
        loadStage = step + 1;
        if (loadStage > LOAD_STEPS.length) {
            finishHold = 0.5f;
        }
    }

    private void finishLoading() {
        loading = false;
        loadScreen.close();
        loadScreen = null;
        System.out.println("[AEDFH] load complete in "
                + (System.currentTimeMillis() - loadStartMs) + " ms");
    }

    /** 分步装配；离屏模式在 simpleInitApp 里一次跑完，交互模式由 tickLoading 每帧推进一步。 */
    private void loadStep(int step) {
        switch (step) {
            case 1 -> {
                terrain = new WorldTerrain(assetManager, terrainBake);
                rootNode.attachChild(terrain.node());
                // 权重图已经上传成纹素，原始 BufferedImage 不再需要
                terrainBake = null;
            }
            case 2 -> {
                props = new WorldProps(assetManager, rootNode, terrain);
                collision = new WorldCollision(props.solids(), props.solidRounds(), PLAYER_RADIUS);
            }
            case 3 -> {
                fx = new Fx(assetManager, rootNode);
                player = new Player(assetManager, rootNode, fx);
                cloud = new CloudMount(assetManager, rootNode, fx);
                rig = new CameraRig(cam);
                rig.setGroundSampler(terrain::height);
                hud = new WorldHud(assetManager, guiNode, terrain.mapTexture());

                player.setArenaBounds(false);
                player.reset(new Vector3f(WorldData.SPAWN.x(), 0f, WorldData.SPAWN.z()), saveYaw());
                player.setGroundY(terrain.height(player.pos.x, player.pos.z));
                player.setVisible(false);
                player.mountMaxHp = 160f;
                player.mountHp = 160f;
            }
            case 4 -> {
                setupLights();
                setupPost();
            }
            case 5 -> buildBosses();
            case 6 -> {
                buildMobs();
                buildLostRunes();
                buildFogWall();
                buildCave();
            }
            case 7 -> finalizeWorld();
            default -> throw new IllegalStateException("unknown load step " + step);
        }
    }

    private void buildCave() {
        caveFloorY = terrain.height(CAVE_X, CAVE_Z);
        caveBoulder = props.buildCave(CAVE_X, CAVE_Z, caveFloorY);
        for (int i = 0; i < 4; i++) {
            float a = FastMath.QUARTER_PI + i * FastMath.HALF_PI;
            PointLight bl = new PointLight();
            bl.setColor(new ColorRGBA(1f, 0.55f, 0.22f, 1f));
            bl.setRadius(19f);
            bl.setPosition(new Vector3f(CAVE_X + FastMath.cos(a) * 14.2f,
                    caveFloorY + 2.2f, CAVE_Z + FastMath.sin(a) * 14.2f));
            rootNode.addLight(bl);
        }
        caveBoss = new Boss(assetManager, rootNode, fx);
        caveBoss.setHome(CAVE_X, CAVE_Z, CAVE_R, caveFloorY);
        caveBoss.setPresent(false);
        caveBoss.alive = false;
        caveGraceNode = props.graceNode("cave-exit");
        if (caveGraceNode != null) {
            caveGraceNode.setCullHint(Spatial.CullHint.Always);
        }
    }

    private void finalizeWorld() {
        targets.addAll(mobs);
        targets.addAll(bosses);
        // Boss.setTargets holds a private COPY of this list; the ape must be in it
        // from the start (alive=false keeps it inert) or later adds never reach resolveHit.
        targets.add(caveBoss);
        player.setTargets(targets);

        slots.clear();
        slots.addAll(SaveData.list());
        hud.setMode(WorldHud.Mode.TITLE);
        hud.setFlaskCounts(4, 2);
        hud.setSelectedItem(0);
        hud.setName("");
        hud.refreshTitle(slots, selectedSlot, false);
        setupInput();
        titleBoss = findBoss("agheel");
        if (titleBoss != null) {
            titleBoss.model.setVisible(true);
        }
        frameTitleCamera(0f);

        if (worldTest) {
            beginTestGame();
        } else if (worldShots) {
            // Title is captured first, then a staged save is loaded.
        }
        System.out.println("[AEDFH] world init ok renderer=" + settings.getRenderer()
                + " saves=" + SaveData.dir().getAbsolutePath()
                + " bosses=" + bosses.size() + " mobs=" + mobs.size()
                + " graces=" + WorldData.GRACES.length
                + " solids=" + collision.count());
    }

    private float saveYaw() {
        return 3.14f;
    }

    private void setupLights() {
        sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.58f, -1.05f, -0.52f).normalizeLocal());
        sun.setColor(new ColorRGBA(1f, 0.86f, 0.68f, 1f).multLocal(0.82f));
        rootNode.addLight(sun);

        rim = new DirectionalLight();
        rim.setDirection(new Vector3f(0.7f, -0.35f, 0.55f).normalizeLocal());
        rim.setColor(new ColorRGBA(0.32f, 0.44f, 0.66f, 1f).multLocal(0.24f));
        rootNode.addLight(rim);

        fill = new DirectionalLight();
        fill.setColor(new ColorRGBA(0.92f, 0.86f, 0.74f, 1f).multLocal(0.18f));
        fill.setDirection(new Vector3f(0f, -0.3f, -1f));
        rootNode.addLight(fill);

        ambient = new AmbientLight(new ColorRGBA(0.17f, 0.18f, 0.20f, 1f));
        rootNode.addLight(ambient);

        if (worldShots) {
            // 画廊太阳方向固定，背光机位下盔甲/斧刃会整块发黑。
            // 这盏补光跟着机位走（方向恒为 机位→被摄物），只在截图模式存在。
            shotFill = new DirectionalLight();
            shotFill.setColor(new ColorRGBA(0.96f, 0.91f, 0.82f, 1f).multLocal(0.34f));
            shotFill.setDirection(new Vector3f(0f, -0.6f, -0.8f).normalizeLocal());
            rootNode.addLight(shotFill);
        }

        lantern = new PointLight();
        lantern.setColor(new ColorRGBA(1f, 0.68f, 0.36f, 1f).multLocal(0.6f));
        lantern.setRadius(18f);
        lantern.setPosition(new Vector3f(0f, 4f, 348f));
        rootNode.addLight(lantern);

        skyTint = terrain.skyMaterial();
        waterTint = terrain.waterMaterial();
    }

    private void setupPost() {
        if (System.getProperty("aedfh.nopost") != null) {
            System.out.println("[AEDFH] post filters disabled");
            return;
        }
        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
        BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Scene);
        bloom.setBloomIntensity(0.32f);
        bloom.setBlurScale(1.0f);
        bloom.setExposureCutOff(0.90f);
        bloom.setExposurePower(2.6f);
        bloom.setDownSamplingFactor(2f);
        fpp.addFilter(bloom);
        fog = new FogFilter(new ColorRGBA(0.58f, 0.65f, 0.74f, 1f), 0.24f, 1600f);
        fpp.addFilter(fog);
        viewPort.addProcessor(fpp);
    }

    private void buildBosses() {
        for (WorldBoss.Spec spec : WorldBoss.SPECS) {
            WorldBoss boss = new WorldBoss(assetManager, rootNode, fx, terrain, spec);
            bosses.add(boss);
            if (spec.gate) {
                gateBoss = boss;
            }
        }
    }

    private void buildMobs() {
        Object[][] spawns = {
                {-104f, 90f, 0f, WorldMob.Kind.SOLDIER},
                {-88f, 76f, 2.1f, WorldMob.Kind.WOLF},
                {-112f, 70f, 1.2f, WorldMob.Kind.WOLF},
                {178f, -30f, 1.6f, WorldMob.Kind.DEMIHUMAN},
                {168f, -42f, 2.4f, WorldMob.Kind.DEMIHUMAN},
                {180f, -44f, 0.4f, WorldMob.Kind.DEMIHUMAN},
                {356f, 302f, 1.1f, WorldMob.Kind.DEMIHUMAN},
                {344f, 310f, 2.3f, WorldMob.Kind.DEMIHUMAN},
                {362f, 316f, 0.2f, WorldMob.Kind.DEMIHUMAN},
                {400f, 346f, 1.8f, WorldMob.Kind.DEMIHUMAN},
                {386f, -330f, 1.3f, WorldMob.Kind.SKELETON},
                {396f, -344f, 2.9f, WorldMob.Kind.SKELETON},
                {404f, -322f, 0.4f, WorldMob.Kind.SKELETON},
                {378f, -316f, 1.9f, WorldMob.Kind.SKELETON},
                {516f, 192f, 0.9f, WorldMob.Kind.SOLDIER},
                {506f, 204f, 2.4f, WorldMob.Kind.SOLDIER},
                {520f, 206f, 1.5f, WorldMob.Kind.SOLDIER},
                {272f, -284f, 0.8f, WorldMob.Kind.WOLF},
                {260f, -296f, 2.2f, WorldMob.Kind.WOLF},
                {276f, -300f, 1.4f, WorldMob.Kind.SOLDIER},
                {-152f, 192f, 1.7f, WorldMob.Kind.WOLF},
                {-166f, 184f, 0.5f, WorldMob.Kind.WOLF},
                {202f, -256f, 2.7f, WorldMob.Kind.SOLDIER},
                {188f, -250f, 1.0f, WorldMob.Kind.SOLDIER},
                {214f, 282f, 1.9f, WorldMob.Kind.DEMIHUMAN},
                {204f, 272f, 0.7f, WorldMob.Kind.DEMIHUMAN},
                {-430f, 254f, 2.1f, WorldMob.Kind.SKELETON},
                {-446f, 262f, 0.3f, WorldMob.Kind.SKELETON},
                {-442f, 244f, 1.5f, WorldMob.Kind.SKELETON},
                {106f, 406f, 1.2f, WorldMob.Kind.SKELETON},
                {116f, 418f, 2.6f, WorldMob.Kind.SKELETON},
                {8f, 300f, 3.0f, WorldMob.Kind.SOLDIER},
                {-24f, 160f, 0.2f, WorldMob.Kind.SOLDIER},
                {226f, 22f, 2.5f, WorldMob.Kind.DEMIHUMAN},
                {246f, -146f, 1.1f, WorldMob.Kind.SKELETON},
                {-222f, -158f, 1.7f, WorldMob.Kind.SOLDIER},
                {-306f, -286f, 2.2f, WorldMob.Kind.SOLDIER},
                {-96f, 104f, 0.4f, WorldMob.Kind.WOLF},
                {-118f, 62f, 2.6f, WorldMob.Kind.WOLF},
                {-84f, 62f, 1.5f, WorldMob.Kind.WOLF},
                {-140f, 206f, 0.9f, WorldMob.Kind.WOLF},
                {228f, 296f, 1.2f, WorldMob.Kind.WOLF},
                {196f, 296f, 2.7f, WorldMob.Kind.WOLF},
                {254f, -288f, 0.6f, WorldMob.Kind.WOLF},
                {-430f, 236f, 1.8f, WorldMob.Kind.WOLF},
                {20f, 172f, 2.2f, WorldMob.Kind.WOLF}
        };
        for (Object[] row : spawns) {
            float x = (Float) row[0];
            float z = (Float) row[1];
            if (terrain.depth(x, z) > 0.3f || terrain.slope(x, z) > 0.9f) {
                continue;
            }
            mobs.add(new WorldMob(assetManager, rootNode, fx, terrain,
                    (WorldMob.Kind) row[3], x, z, (Float) row[2]));
        }
    }

    private void buildLostRunes() {
        lostNode = new Node("lostRunes");
        Material glow = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        glow.setColor("Color", new ColorRGBA(1f, 0.76f, 0.32f, 0.75f));
        glow.getAdditionalRenderState().setBlendMode(
                com.jme3.material.RenderState.BlendMode.AlphaAdditive);
        glow.getAdditionalRenderState().setDepthWrite(false);
        lostGlow = glow;
        Geometry orb = Gfx.sphere("lostOrb", 0.42f, 10, glow, lostNode, 0f, 1.0f, 0f);
        orb.setQueueBucket(RenderQueue.Bucket.Transparent);
        Geometry beam = new Geometry("lostBeam",
                new com.jme3.scene.shape.Cylinder(6, 12, 0.16f, 5.2f, true));
        beam.setMaterial(glow);
        beam.setLocalTranslation(0f, 2.6f, 0f);
        lostNode.attachChild(beam);
        beam.setQueueBucket(RenderQueue.Bucket.Transparent);
        Geometry ring = Gfx.torus("lostRing", 1.15f, 0.05f, glow, lostNode, 0f, 0.08f, 0f);
        ring.setQueueBucket(RenderQueue.Bucket.Transparent);
        lostNode.setCullHint(Spatial.CullHint.Always);
        rootNode.attachChild(lostNode);
    }

    private void buildFogWall() {
        fogWall = new Node("gateFog");
        fogMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        fogMat.setColor("Color", new ColorRGBA(0.95f, 0.72f, 0.34f, 0.20f));
        fogMat.getAdditionalRenderState().setBlendMode(
                com.jme3.material.RenderState.BlendMode.AlphaAdditive);
        fogMat.getAdditionalRenderState().setDepthWrite(false);
        fogMat.getAdditionalRenderState().setFaceCullMode(
                com.jme3.material.RenderState.FaceCullMode.Off);
        float base = terrain.height(-382f, -360f);
        Geometry g = new Geometry("fogSheet", new com.jme3.scene.shape.Quad(128f, 9f));
        g.setMaterial(fogMat);
        g.setLocalTranslation(-382f - 64f, base + 0.15f, -359.4f);
        g.setQueueBucket(RenderQueue.Bucket.Transparent);
        fogWall.attachChild(g);
        for (int i = 0; i < 9; i++) {
            Geometry wisp = Gfx.sphere("fogWisp", 0.75f, 7, fogMat,
                    fogWall, -440f + i * 15f, base + 1.4f + (i % 3) * 1.5f, -359.2f);
            wisp.setLocalScale(1.1f, 2.4f, 0.5f);
            wisp.setQueueBucket(RenderQueue.Bucket.Transparent);
        }
        fogWall.setCullHint(Spatial.CullHint.Always);
        rootNode.attachChild(fogWall);
    }

    // ---------------------------------------------------------------- input

    private void setupInput() {
        inputManager.addMapping("fwd", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("back", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("left", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("right", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("sprint", new KeyTrigger(KeyInput.KEY_LSHIFT),
                new KeyTrigger(KeyInput.KEY_RSHIFT));
        inputManager.addMapping("jump", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("skill", new KeyTrigger(KeyInput.KEY_F));
        // R = 使用快栏道具（对齐原作）；T 保留为过渡期兼容键，后续版本删除。
        inputManager.addMapping("item", new KeyTrigger(KeyInput.KEY_R),
                new KeyTrigger(KeyInput.KEY_T));
        // Z 曾是独立召唤键，现已并入快栏（第 3 格筋斗云铃），保留为过渡期快捷方式。
        inputManager.addMapping("mount", new KeyTrigger(KeyInput.KEY_Z));
        inputManager.addMapping("interact", new KeyTrigger(KeyInput.KEY_E));
        inputManager.addMapping("map", new KeyTrigger(KeyInput.KEY_G));
        inputManager.addMapping("lock", new KeyTrigger(KeyInput.KEY_Q),
                new MouseButtonTrigger(2));
        // Esc 全流程解绑：全屏下它会被系统当成退出路径，按一下就没了。
        // 暂停/菜单入口改到 P，界面内的返回与取消一律走 E（场景内 E 仍是交互，按阶段区分）。
        inputManager.addMapping("pause", new KeyTrigger(KeyInput.KEY_P));
        inputManager.addMapping("fullscreen", new KeyTrigger(KeyInput.KEY_F11));
        inputManager.addMapping("up", new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping("down", new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping("leftswitch", new KeyTrigger(KeyInput.KEY_LEFT));
        inputManager.addMapping("rightswitch", new KeyTrigger(KeyInput.KEY_RIGHT));
        inputManager.addMapping("confirm", new KeyTrigger(KeyInput.KEY_RETURN),
                new KeyTrigger(KeyInput.KEY_NUMPADENTER));
        inputManager.addMapping("delete", new KeyTrigger(KeyInput.KEY_DELETE));
        // 不再给 "back"（= WASD 的后退 S）追加 Backspace 触发器：addMapping 同名是累加，
        // 两个语义挤在一个名字里，动作回调只会走先注册的那条，退格永远传不进去。
        // 起名界面的退格删除由 rawListener.onKeyEvent 直接处理。
        inputManager.addMapping("chord", new KeyTrigger(KeyInput.KEY_LCONTROL),
                new KeyTrigger(KeyInput.KEY_RCONTROL));
        inputManager.addMapping("light", new MouseButtonTrigger(0));
        inputManager.addMapping("heavy", new MouseButtonTrigger(1));

        inputManager.addListener(actionListener,
                "fwd", "back", "left", "right", "sprint", "jump", "skill", "item",
                "mount", "interact", "map", "lock", "pause", "fullscreen", "up", "down",
                "leftswitch", "rightswitch", "confirm", "delete", "chord", "light", "heavy");
        inputManager.addRawInputListener(rawListener);
    }

    private final ActionListener actionListener = (name, isPressed, tpf) -> {
        // worldtest 注入的是合成按键/鼠标事件（离屏上下文没有真实输入），必须放行才测得到映射层
        if (!inputLive) {
            return;
        }
        switch (name) {
            case "fwd" -> {
                keyFwd = isPressed;
                return;
            }
            case "back" -> {
                keyBack = isPressed;
                return;
            }
            case "left" -> {
                keyLeft = isPressed;
                return;
            }
            case "right" -> {
                keyRight = isPressed;
                return;
            }
            case "sprint" -> {
                keySprint = isPressed;
                if (phase != Phase.PLAY) {
                    return;
                }
                if (isPressed) {
                    sprintDownAt = time;
                } else if (canControl() && time - sprintDownAt < 0.2f) {
                    player.requestRoll();
                }
                return;
            }
            case "chord" -> {
                keyChord = isPressed;
                if (!isPressed && charging) {
                    player.releaseCharge();
                    charging = false;
                }
                return;
            }
            default -> {
            }
        }
        if (!isPressed) {
            if ("skill".equals(name)) {
                keySkill = false;
            } else if ("light".equals(name) && charging) {
                player.releaseCharge();
                charging = false;
            }
            return;
        }
        switch (name) {
            case "up" -> onUp();
            case "down" -> onDown();
            case "leftswitch" -> onLeftSwitch();
            case "rightswitch" -> onRightSwitch();
            case "confirm" -> onConfirm();
            case "delete" -> onDelete();
            case "jump" -> {
                if (canControl()) {
                    player.requestJump();
                }
            }
            case "skill" -> {
                keySkill = true;
                if (canControl()) {
                    // 骑筋斗云时 F 改吃专属动作：地面上那套棍法战技在云上用不出来
                    if (player.mounted) {
                        cloudDash();
                    } else {
                        player.requestSkill();
                    }
                }
            }
            case "item" -> {
                if (canControl()) {
                    useItem();
                }
            }
            case "mount" -> {
                if (canControl()) {
                    toggleMount();
                }
            }
            case "interact" -> {
                if (canControl()) {
                    interact();
                } else {
                    backOut();
                }
            }
            case "map" -> {
                if (phase == Phase.PLAY) {
                    openMap(Phase.PLAY);
                } else if (phase == Phase.MAP) {
                    closeMap();
                }
            }
            case "lock" -> {
                if (canControl()) {
                    toggleLock();
                }
            }
            case "pause" -> {
                if (phase == Phase.PLAY) {
                    openPause();
                } else {
                    backOut();
                }
            }
            case "light" -> {
                if (canControl()) {
                    if (keyChord && !player.isExecuting()) {
                        if (player.isAirborne()) {
                            player.requestPlunge();
                        } else {
                            player.requestCharge();
                            charging = true;
                        }
                    } else {
                        player.requestLight();
                    }
                }
            }
            case "heavy" -> {
                if (canControl()) {
                    player.requestHeavy();
                }
            }
            case "fullscreen" -> toggleFullscreen();
            default -> {
            }
        }
    };

    private final RawInputListener rawListener = new RawInputListener() {
        @Override
        public void beginInput() {
        }

        @Override
        public void endInput() {
        }

        @Override
        public void onJoyAxisEvent(JoyAxisEvent evt) {
        }

        @Override
        public void onJoyButtonEvent(JoyButtonEvent evt) {
        }

        @Override
        public void onMouseMotionEvent(MouseMotionEvent evt) {
            if (headless) {
                return;
            }
            if (phase == Phase.PLAY) {
                rig.mouseMoved(evt.getDX(), evt.getDY());
                return;
            }
            hud.mouseMoved(evt.getX(), evt.getY());
            if (phase == Phase.TITLE) {
                hoverTitle(evt.getX(), evt.getY());
            } else if (phase == Phase.PAUSE || phase == Phase.GRACE
                    || phase == Phase.LEVEL || phase == Phase.TRAVEL) {
                hoverMenu(evt.getX(), evt.getY());
            }
        }

        @Override
        public void onMouseButtonEvent(MouseButtonEvent evt) {
            // 与 actionListener 同理：worldtest 需要放行注入的合成点击才能测到菜单点选
            if (!inputLive || !evt.isPressed() || evt.getButtonIndex() != 0) {
                return;
            }
            String hit = hud.hitTest(evt.getX(), evt.getY());
            if (hit == null) {
                return;
            }
            if (phase == Phase.TITLE && hit.startsWith("slot")) {
                selectedSlot = Integer.parseInt(hit.substring(4));
                deleteArmed = false;
                hud.refreshTitle(slots, selectedSlot, false);
                activateSlot();
            } else if ((phase == Phase.PAUSE || phase == Phase.GRACE
                    || phase == Phase.LEVEL || phase == Phase.TRAVEL)
                    && hit.startsWith("menu")) {
                menuSelected = Integer.parseInt(hit.substring(4));
                hud.setMenuSelection(menuSelected);
                chooseMenu(menuSelected);
            }
        }

        @Override
        public void onKeyEvent(KeyInputEvent evt) {
            if (!evt.isPressed() || phase != Phase.NAME) {
                return;
            }
            int key = evt.getKeyCode();
            if (key == KeyInput.KEY_RETURN || key == KeyInput.KEY_NUMPADENTER) {
                createCharacter();
            } else if (key == KeyInput.KEY_BACK) {
                nameBackspace();
            } else {
                char c = evt.getKeyChar();
                // E 让给"返回标题"，起名界面不再把 e/E 当字符吃进去
                if (c == 'e' || c == 'E') {
                    return;
                }
                if (c >= 0x20 && c != 0x7f && nameBuffer.codePointCount(0, nameBuffer.length()) < 12) {
                    nameBuffer += c;
                    hud.setName(nameBuffer);
                }
            }
        }

        @Override
        public void onTouchEvent(TouchEvent evt) {
        }
    };

    // ---------------------------------------------------------------- ui verbs

    private void onUp() {
        if (phase == Phase.TITLE) {
            moveTitle(-1);
        } else if (isMenuPhase()) {
            moveMenu(-1);
        } else if (phase == Phase.PLAY && canControl()) {
            hud.toast("魔法：空栏（尚未习得魔法）");
        }
    }

    private void onDown() {
        if (phase == Phase.TITLE) {
            moveTitle(1);
        } else if (isMenuPhase()) {
            moveMenu(1);
        } else if (phase == Phase.PLAY && canControl()) {
            selectQuickSlot(selectedItem + 1);
        }
    }

    /** 循环切换道具快栏（红露滴 → 蓝露滴 → 筋斗云铃 → 红露滴）。 */
    private void selectQuickSlot(int index) {
        selectedItem = Math.floorMod(index, WorldHud.QUICK_NAMES.length);
        save.quickSlot = selectedItem;
        hud.setSelectedItem(selectedItem);
        hud.toast("快栏 · " + WorldHud.quickName(selectedItem));
        saveNow();
    }

    private int hudSelected() {
        return selectedItem;
    }

    private int selectedItem;

    private void onLeftSwitch() {
        if (phase == Phase.PLAY && canControl()) {
            hud.toast("左手武器：空");
        }
    }

    private void onRightSwitch() {
        if (phase == Phase.PLAY && canControl()) {
            hud.toast("右手武器：如意金箍棒");
        }
    }

    private void onConfirm() {
        if (phase == Phase.TITLE) {
            activateSlot();
        } else if (phase == Phase.NAME) {
            createCharacter();
        } else if (isMenuPhase()) {
            chooseMenu(menuSelected);
        } else if (phase == Phase.DEAD) {
            respawnPlayer();
        } else if (phase == Phase.PLAY && victoryTimer > 0f) {
            victoryTimer = 0f;
            hud.setMode(WorldHud.Mode.PLAY);
        } else if (phase == Phase.ENDING) {
            backToTitle();
        }
    }

    private void onDelete() {
        if (phase != Phase.TITLE) {
            return;
        }
        if (slots.get(selectedSlot) == null) {
            return;
        }
        if (!deleteArmed) {
            deleteArmed = true;
            hud.refreshTitle(slots, selectedSlot, true);
            return;
        }
        SaveData.delete(selectedSlot + 1);
        deleteArmed = false;
        slots.clear();
        slots.addAll(SaveData.list());
        hud.refreshTitle(slots, selectedSlot, false);
    }

    private boolean isMenuPhase() {
        return phase == Phase.PAUSE || phase == Phase.GRACE
                || phase == Phase.LEVEL || phase == Phase.TRAVEL;
    }

    private void moveTitle(int dir) {
        selectedSlot = Math.floorMod(selectedSlot + dir, 3);
        deleteArmed = false;
        hud.refreshTitle(slots, selectedSlot, false);
    }

    private void hoverTitle(float x, float y) {
        String hit = hud.hitTest(x, y);
        if (hit != null && hit.startsWith("slot")) {
            int idx = Integer.parseInt(hit.substring(4));
            if (idx != selectedSlot) {
                selectedSlot = idx;
                deleteArmed = false;
                hud.refreshTitle(slots, selectedSlot, false);
            }
        }
    }

    private void hoverMenu(float x, float y) {
        String hit = hud.hitTest(x, y);
        if (hit != null && hit.startsWith("menu")) {
            int idx = Integer.parseInt(hit.substring(4));
            if (idx < menuOptions.length && idx != menuSelected) {
                menuSelected = idx;
                hud.setMenuSelection(idx);
            }
        }
    }

    private void moveMenu(int dir) {
        if (menuOptions.length == 0) {
            return;
        }
        menuSelected = Math.floorMod(menuSelected + dir, menuOptions.length);
        hud.setMenuSelection(menuSelected);
    }

    private void activateSlot() {
        SaveData d = slots.get(selectedSlot);
        if (d != null) {
            loadGame(d);
        } else {
            enterName();
        }
    }

    private void enterName() {
        phase = Phase.NAME;
        nameBuffer = "";
        deleteArmed = false;
        hud.setName("");
        hud.setMode(WorldHud.Mode.NAME);
        inputManager.setCursorVisible(true);
    }

    private void nameBackspace() {
        if (nameBuffer.isEmpty()) {
            return;
        }
        int cp = nameBuffer.codePointBefore(nameBuffer.length());
        nameBuffer = nameBuffer.substring(0, nameBuffer.length() - Character.charCount(cp));
        hud.setName(nameBuffer);
    }

    private void createCharacter() {
        SaveData d = new SaveData(selectedSlot + 1);
        d.name = nameBuffer.isBlank() ? "褪色者" : nameBuffer.strip();
        d.graces.add("first-step");
        d.lastGrace = "first-step";
        d.save();
        slots.clear();
        slots.addAll(SaveData.list());
        SaveData loaded = slots.get(selectedSlot);
        loadGame(loaded != null ? loaded : d);
    }

    private void backToTitle() {
        if (save != null && player.alive && phase == Phase.NAME) {
            saveNow();
        }
        save = null;
        phase = Phase.TITLE;
        deleteArmed = false;
        endingActive = false;
        endingTimer = 0f;
        hud.setFade(0f);
        player.setVisible(false);
        player.setMounted(false);
        cloud.show(false);
        hud.setMode(WorldHud.Mode.TITLE);
        slots.clear();
        slots.addAll(SaveData.list());
        hud.refreshTitle(slots, selectedSlot, false);
        inputManager.setCursorVisible(true);
        titleTime = 0f;
        frameTitleCamera(0f);
    }

    // ---------------------------------------------------------------- game start

    private void beginTestGame() {
        SaveData d = new SaveData(1);
        d.name = "测试者";
        d.graces.add("first-step");
        d.lastGrace = "first-step";
        d.save();
        loadGame(d);
    }

    /**
     * 洞窟试炼只属于新档或还没打完石猴的档：老档（位置在洞外、已有进度）不回炉。
     */
    private boolean needsTutorial() {
        if (save == null || save.tutorialCleared) {
            return false;
        }
        boolean inCavePos = MathX.distXZ(new Vector3f(save.x, 0f, save.z),
                new Vector3f(CAVE_X, 0f, CAVE_Z)) < CAVE_R + 4f;
        return inCavePos
                || (save.level <= 1 && save.playSeconds < 30f && save.graces.size() <= 1);
    }

    private void loadGame(SaveData d) {
        save = d;
        if (save.graces.isEmpty()) {
            save.graces.add("first-step");
        }
        if (graceById(save.lastGrace) == null) {
            save.lastGrace = "first-step";
        }
        save.applyTo(player);
        player.mountMaxHp = 160f;
        player.mountHp = 160f;

        float sx = save.x;
        float sz = save.z;
        boolean cave = needsTutorial();
        if (cave) {
            sx = CAVE_SPAWN.x;
            sz = CAVE_SPAWN.z;
        } else if (!standable(sx, sz)) {
            WorldData.GraceDef g0 = graceById(save.lastGrace);
            sx = g0.x();
            sz = g0.z();
        }
        player.reset(new Vector3f(sx, 0f, sz),
                cave ? MathX.yawTo(CAVE_SPAWN, CAVE_BOSS_SPOT) : save.yaw);
        player.setGroundY(cave ? caveFloorY : terrain.height(sx, sz));
        player.setVisible(true);
        player.invuln = 1.2f;
        player.setMounted(false);
        cloud.show(false);

        syncBossesFromSave();
        if (cave) {
            caveSealed = true;
            caveBossActive = true;
            caveBoss.setPresent(true);
            caveBoss.reset(new Vector3f(CAVE_BOSS_SPOT), 3.14f);
            if (caveBoulder != null) {
                caveBoulder.setCullHint(Spatial.CullHint.Inherit);
                caveBoulder.setLocalTranslation(CAVE_X, caveFloorY + 1.8f, CAVE_Z - 14.8f);
            }
            save.x = sx;
            save.z = sz;
        } else {
            caveSealed = false;
            caveBossActive = false;
            caveOpenT = 0f;
            caveBoss.setPresent(false);
            caveBoss.alive = false;
            if (caveBoulder != null) {
                caveBoulder.setCullHint(Spatial.CullHint.Always);
            }
            if (caveGraceNode != null) {
                caveGraceNode.setCullHint(save.graces.contains("cave-exit")
                        ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            }
        }
        for (WorldMob m : mobs) {
            m.respawn();
        }
        spawnLostRunes();
        lastNight = isNight(save.hour);
        refreshNightBosses(false);
        updateLighting(0f);
        autoSaveTimer = AUTOSAVE;
        victoryTimer = 0f;
        gateActive = false;
        fogWall.setCullHint(Spatial.CullHint.Always);
        if (gateBoss != null && !save.bosses.contains(gateBoss.id())) {
            gateBoss.respawnBoss();
        }
        lockedTarget = null;
        selectedItem = Math.floorMod(save.quickSlot, WorldHud.QUICK_NAMES.length);
        keyFwd = false;
        keyBack = false;
        keyLeft = false;
        keyRight = false;
        keySprint = false;
        keySkill = false;
        keyChord = false;
        charging = false;

        rig.reset(sx, sz, save.yaw);
        rig.snap(player, dummy, false);
        hud.setMode(WorldHud.Mode.PLAY);
        endingActive = false;
        endingTimer = 0f;
        hud.setFade(0f);
        if (save.margitCleared) {
            props.openCastleGate();
        } else {
            props.closeCastleGate();
        }
        hud.setSelectedItem(selectedItem);
        hud.setFlaskCounts(save.flaskCrimson, save.flaskCerulean);
        WorldData.GraceDef g = graceById(save.lastGrace);
        if (g != null) {
            hud.showLocation("宁姆格福", cave ? "试炼洞窟" : g.name());
        }
        if (cave) {
            hud.toast("击败洞中的顽石猿王，洞口的巨石才会移开");
        }
        inputManager.setCursorVisible(false);
        phase = Phase.PLAY;
        saveNow();
    }

    private void syncBossesFromSave() {
        for (int i = 0; i < bosses.size(); i++) {
            WorldBoss b = bosses.get(i);
            bossCorpse[i] = 0f;
            if (save.bosses.contains(b.id())) {
                b.alive = false;
                b.hp = 0f;
                b.state = WorldBoss.State.DEAD;
                b.available = false;
                b.engaged = false;
                b.model.setVisible(false);
            } else {
                b.alive = true;
                b.respawnBoss();
                b.available = true;
                if (b.nightOnly() && !isNight(save.hour)) {
                    b.setAvailable(false);
                }
                if (!bossUnlocked(b)) {
                    b.setAvailable(false);
                }
            }
        }
    }

    private void spawnLostRunes() {
        if (save.lostAlive && save.lostRunes > 0
                && standable(save.lostX, save.lostZ)) {
            lostNode.setLocalTranslation(save.lostX,
                    terrain.height(save.lostX, save.lostZ), save.lostZ);
            lostNode.setCullHint(Spatial.CullHint.Never);
        } else {
            lostNode.setCullHint(Spatial.CullHint.Always);
        }
    }

    private void openPause() {
        if (phase != Phase.PLAY) {
            return;
        }
        saveNow();
        phase = Phase.PAUSE;
        clearMoveInput();
        hud.setPrompt("");
        hud.setMode(WorldHud.Mode.MENU);
        showMenuScreen("暂停", "宁姆格福 · " + graceName(save.lastGrace),
                new String[]{"继续旅程", "赐福传送", "打开地图", "返回标题", "退出游戏"},
                0, "↑↓ 选择 · Enter 确认 · E 继续");
        inputManager.setCursorVisible(true);
    }

    private void closeToPlay() {
        phase = Phase.PLAY;
        hud.setMode(WorldHud.Mode.PLAY);
        hud.setPrompt("");
        inputManager.setCursorVisible(false);
        player.invuln = Math.max(player.invuln, 0.4f);
        autoSaveTimer = AUTOSAVE;
    }

    private void openGraceMenu(WorldData.GraceDef g) {
        currentGrace = g;
        phase = Phase.GRACE;
        clearMoveInput();
        hud.setMode(WorldHud.Mode.MENU);
        showMenuScreen("赐福 · " + g.name(),
                "已发现 " + save.graces.size() + " / " + WorldData.GRACES.length
                        + " 处赐福   ·   红瓶 " + save.flaskCrimson + " / " + save.flaskCrimsonMax
                        + "   蓝瓶 " + save.flaskCerulean + " / " + save.flaskCeruleanMax,
                new String[]{"在此休息", "升级", "赐福传送", "消磨时间", "离开赐福"},
                0, "↑↓ 选择 · Enter 确认 · E 离开");
        inputManager.setCursorVisible(true);
    }

    private void openLevelMenu() {
        phase = Phase.LEVEL;
        buildLevelMenu(0);
    }

    private void buildLevelMenu(int selected) {
        String[] opts = new String[SaveData.STAT_COUNT + 1];
        for (int i = 0; i < SaveData.STAT_COUNT; i++) {
            opts[i] = SaveData.STAT_NAMES[i] + "   " + save.stats[i]
                    + "  →  " + (save.stats[i] + 1);
        }
        opts[SaveData.STAT_COUNT] = "返回赐福";
        int cost = save.nextLevelCost();
        // 卢恩不够时把原因写在菜单里：飘字只有 2.8 秒，玩家会当成"按了没反应"
        String hint = save.runes < cost
                ? "卢恩不足，还差 " + (cost - save.runes) + " · 击杀强敌或拾取卢恩后再来"
                : "↑↓ 选择属性 · Enter 消耗卢恩 · E 返回";
        showMenuScreen("升级", "等级 " + save.level + "   ·   持有 " + save.runes
                        + " 卢恩   ·   需要 " + cost + " 卢恩",
                opts, Math.min(selected, opts.length - 1), hint);
        inputManager.setCursorVisible(true);
    }

    private void openTravel(Phase from) {
        travelReturn = from;
        travelPage = 0;
        phase = Phase.TRAVEL;
        buildTravelMenu(0);
        inputManager.setCursorVisible(true);
    }

    private void buildTravelMenu(int selected) {
        List<WorldData.GraceDef> known = new ArrayList<>();
        for (WorldData.GraceDef g : WorldData.GRACES) {
            if (save.graces.contains(g.id())) {
                known.add(g);
            }
        }
        int pages = Math.max(1, (known.size() + TRAVEL_PAGE - 1) / TRAVEL_PAGE);
        travelPage = Math.max(0, Math.min(pages - 1, travelPage));
        int start = travelPage * TRAVEL_PAGE;
        int end = Math.min(known.size(), start + TRAVEL_PAGE);
        List<String> opts = new ArrayList<>();
        List<Integer> acts = new ArrayList<>();
        for (int i = start; i < end; i++) {
            opts.add(known.get(i).name());
            acts.add(i);
        }
        if (travelPage > 0) {
            opts.add("上一页");
            acts.add(ACT_PREV);
        }
        if (travelPage < pages - 1) {
            opts.add("下一页");
            acts.add(ACT_NEXT);
        }
        opts.add("返回");
        acts.add(ACT_BACK);
        menuOptions = opts.toArray(new String[0]);
        travelAction = new int[acts.size()];
        for (int i = 0; i < acts.size(); i++) {
            travelAction[i] = acts.get(i);
        }
        menuSelected = Math.max(0, Math.min(menuOptions.length - 1, selected));
        hud.showMenu("赐福传送",
                "已发现 " + known.size() + " / " + WorldData.GRACES.length
                        + " 处赐福   ·   第 " + (travelPage + 1) + " / " + pages + " 页",
                menuOptions, menuSelected, "↑↓ 选择 · Enter 传送 · E 返回");
    }

    private void showMenuScreen(String title, String sub, String[] options,
                                int selected, String hint) {
        menuOptions = options;
        menuSelected = Math.max(0, Math.min(Math.max(0, options.length - 1), selected));
        hud.showMenu(title, sub, options, menuSelected, hint);
    }

    private void chooseMenu(int index) {
        if (index < 0 || index >= menuOptions.length) {
            return;
        }
        switch (phase) {
            case PAUSE -> {
                switch (index) {
                    case 0 -> closeToPlay();
                    case 1 -> {
                        if (gateActive) {
                            hud.toast("强敌当前，无法进行赐福传送");
                        } else {
                            openTravel(Phase.PAUSE);
                        }
                    }
                    case 2 -> openMap(Phase.PAUSE);
                    case 3 -> {
                        saveNow();
                        backToTitle();
                    }
                    case 4 -> {
                        saveNow();
                        stop();
                    }
                    default -> {
                    }
                }
            }
            case GRACE -> {
                switch (index) {
                    case 0 -> restAtGrace();
                    case 1 -> openLevelMenu();
                    case 2 -> openTravel(Phase.GRACE);
                    case 3 -> passTime();
                    case 4 -> closeToPlay();
                    default -> {
                    }
                }
            }
            case LEVEL -> {
                if (index == SaveData.STAT_COUNT) {
                    backToGrace();
                } else {
                    levelStat(index);
                }
            }
            case TRAVEL -> {
                int act = travelAction[index];
                if (act >= 0) {
                    travelTo(act);
                } else if (act == ACT_PREV) {
                    travelPage--;
                    buildTravelMenu(0);
                } else if (act == ACT_NEXT) {
                    travelPage++;
                    buildTravelMenu(0);
                } else {
                    travelBack();
                }
            }
            default -> {
            }
        }
    }

    private void backToGrace() {
        phase = Phase.GRACE;
        if (currentGrace == null) {
            currentGrace = graceById(save.lastGrace);
        }
        if (currentGrace == null) {
            currentGrace = WorldData.GRACES[0];
        }
        openGraceMenu(currentGrace);
    }

    private void travelBack() {
        if (travelReturn == Phase.PAUSE) {
            phase = Phase.PAUSE;
            hud.setMode(WorldHud.Mode.MENU);
            showMenuScreen("暂停", "宁姆格福 · " + graceName(save.lastGrace),
                    new String[]{"继续旅程", "赐福传送", "打开地图", "返回标题", "退出游戏"},
                    0, "↑↓ 选择 · Enter 确认 · E 继续");
        } else {
            backToGrace();
        }
    }

    private void levelStat(int stat) {
        int cost = save.nextLevelCost();
        if (save.runes < cost) {
            hud.toast("卢恩不足，还需要 " + (cost - save.runes));
            buildLevelMenu(menuSelected);
            return;
        }
        save.runes -= cost;
        save.stats[stat]++;
        save.recalcLevel();
        save.applyTo(player);
        player.refill();
        hud.setFlaskCounts(save.flaskCrimson, save.flaskCerulean);
        hud.toast(SaveData.STAT_NAMES[stat] + " 提升至 " + save.stats[stat]);
        saveNow();
        buildLevelMenu(menuSelected);
    }

    private void restAtGrace() {
        player.refill();
        save.flaskCrimson = save.flaskCrimsonMax;
        save.flaskCerulean = save.flaskCeruleanMax;
        save.flaskCrimsonMax = Math.max(save.flaskCrimsonMax, save.flaskCrimson);
        player.setMounted(false);
        cloud.show(false);
        player.invuln = 1.4f;
        for (WorldMob m : mobs) {
            m.respawn();
        }
        for (WorldBoss b : bosses) {
            if (!save.bosses.contains(b.id()) && (!b.nightOnly() || isNight(save.hour))) {
                b.respawnBoss();
                b.available = true;
                if (!bossUnlocked(b)) {
                    b.setAvailable(false);
                }
            }
        }
        gateActive = false;
        fogWall.setCullHint(Spatial.CullHint.Always);
        if (gateBoss != null && !save.bosses.contains(gateBoss.id())) {
            gateBoss.respawnBoss();
        }
        WorldData.GraceDef g = graceById(save.lastGrace);
        if (g != null) {
            player.pos.set(g.x(), 0f, g.z());
            player.setGroundY(terrain.height(g.x(), g.z()));
            player.yaw = g.facing();
            rig.snap(player, dummy, false);
        }
        save.x = player.pos.x;
        save.z = player.pos.z;
        save.yaw = player.yaw;
        saveNow();
        hud.setFlaskCounts(save.flaskCrimson, save.flaskCerulean);
        hud.toast("休息完毕 · 圣杯瓶已注满");
    }

    private void passTime() {
        boolean night = isNight(save.hour);
        save.hour = night ? 7f : 22f;
        lastNight = isNight(save.hour);
        refreshNightBosses(true);
        updateLighting(0f);
        for (WorldMob m : mobs) {
            m.respawn();
        }
        saveNow();
        hud.toast(isNight(save.hour) ? "你消磨时光，夜幕降临" : "你消磨时光，黎明到来");
        if (currentGrace != null) {
            openGraceMenu(currentGrace);
        }
    }

    private void travelTo(int index) {
        WorldData.GraceDef g = null;
        int seen = -1;
        for (WorldData.GraceDef def : WorldData.GRACES) {
            if (!save.graces.contains(def.id())) {
                continue;
            }
            seen++;
            if (seen == index) {
                g = def;
                break;
            }
        }
        if (g == null) {
            return;
        }
        player.setMounted(false);
        cloud.show(false);
        player.pos.set(g.x(), 0f, g.z());
        player.setGroundY(terrain.height(g.x(), g.z()));
        player.yaw = g.facing();
        player.velocity.set(0f, 0f, 0f);
        player.pos.y = 0f;
        player.forceGrounded();
        player.invuln = 1.2f;
        save.lastGrace = g.id();
        save.x = g.x();
        save.z = g.z();
        save.yaw = g.facing();
        for (WorldMob m : mobs) {
            m.respawn();
        }
        for (WorldBoss b : bosses) {
            if (!save.bosses.contains(b.id()) && (!b.nightOnly() || isNight(save.hour))) {
                b.respawnBoss();
                b.available = true;
                if (!bossUnlocked(b)) {
                    b.setAvailable(false);
                }
            }
        }
        gateActive = false;
        fogWall.setCullHint(Spatial.CullHint.Always);
        if (gateBoss != null && !save.bosses.contains(gateBoss.id())) {
            gateBoss.respawnBoss();
        }
        lockedTarget = null;
        rig.reset(g.x(), g.z(), g.facing());
        rig.snap(player, dummy, false);
        saveNow();
        closeToPlay();
        hud.showLocation("赐福", g.name());
        hud.toast("已抵达 " + g.name());
    }

    private void openMap(Phase from) {
        mapReturn = from;
        phase = Phase.MAP;
        clearMoveInput();
        hud.setMode(WorldHud.Mode.MAP);
        hud.setPrompt("");
        inputManager.setCursorVisible(true);
        hud.updateMap(save, player, true);
    }

    private void closeMap() {
        if (mapReturn == Phase.PAUSE) {
            phase = Phase.PAUSE;
            hud.setMode(WorldHud.Mode.MENU);
            showMenuScreen("暂停", "宁姆格福 · " + graceName(save.lastGrace),
                    new String[]{"继续旅程", "赐福传送", "打开地图", "返回标题", "退出游戏"},
                    0, "↑↓ 选择 · Enter 确认 · E 继续");
            inputManager.setCursorVisible(true);
        } else {
            closeToPlay();
        }
    }

    // ---------------------------------------------------------------- play verbs

    private boolean canControl() {
        if (endingActive) {
            return false;
        }
        return phase == Phase.PLAY && !headless ? player.alive
                : phase == Phase.PLAY && player.alive;
    }

    /** 界面里的"返回/取消"：E 与 P 共用，一次退一层（Esc 已全流程解绑，不再有退出路径）。 */
    private void backOut() {
        switch (phase) {
            case MAP -> closeMap();
            case NAME -> backToTitle();
            case PAUSE, GRACE -> closeToPlay();
            case LEVEL -> backToGrace();
            case TRAVEL -> travelBack();
            case ENDING -> backToTitle();
            default -> {
            }
        }
    }

    private void clearMoveInput() {
        keyFwd = false;
        keyBack = false;
        keyLeft = false;
        keyRight = false;
        keySprint = false;
        keySkill = false;
        keyChord = false;
        charging = false;
    }

    private void toggleMount() {
        if (gateActive) {
            hud.toast("史东薇尔城门内无法唤出筋斗云");
            return;
        }
        if (!player.alive) {
            return;
        }
        if (player.mounted) {
            player.setMounted(false);
            cloudDashT = 0f;
            cloud.burst();
            cloud.show(false);
            hud.toast("筋斗云已散去");
            return;
        }
        if (!save.mountUnlocked) {
            hud.toast("尚未获得筋斗云");
            return;
        }
        player.setMounted(true);
        player.mountHp = Math.max(player.mountHp, player.mountMaxHp * 0.35f);
        cloud.show(true);
        cloud.update(0f, player, false, false, fx);
        cloud.burst();
        hud.toast("筋斗云 · 唤出");
    }

    /** 筋斗云专属动作：按当前输入方向（无输入则朝角色面向）向前猛冲一段。 */
    private void cloudDash() {
        if (!player.mounted || !player.alive || cloudDashCd > 0f) {
            return;
        }
        float moveF = (keyFwd ? 1f : 0f) - (keyBack ? 1f : 0f);
        float moveR = (keyRight ? 1f : 0f) - (keyLeft ? 1f : 0f);
        float yaw = rig.yaw();
        cloudDashDir.set(FastMath.sin(yaw) * moveF - FastMath.cos(yaw) * moveR, 0f,
                FastMath.cos(yaw) * moveF + FastMath.sin(yaw) * moveR);
        if (cloudDashDir.lengthSquared() < 0.01f) {
            cloudDashDir.set(MathX.forward(player.yaw));
        } else {
            cloudDashDir.normalizeLocal();
        }
        player.yaw = (float) Math.atan2(cloudDashDir.x, cloudDashDir.z);
        cloudDashT = CLOUD_DASH_TIME;
        cloudDashCd = CLOUD_DASH_CD;
        cloud.dash();
        rig.addShake(0.18f);
        hud.toast("腾云冲刺");
    }

    /** 冲刺位移逐帧推进：按 0.6m 切片，避免一帧跨过峡谷边缘。 */
    private void tickCloudDash(float dt) {
        if (cloudDashT <= 0f) {
            return;
        }
        if (!player.mounted || !player.alive || !canControl()) {
            cloudDashT = 0f;
            return;
        }
        cloudDashT -= dt;
        float step = CLOUD_DASH_SPEED * dt;
        int slices = Math.max(1, (int) Math.ceil(step / 0.6f));
        float px = player.pos.x;
        float pz = player.pos.z;
        for (int i = 0; i < slices; i++) {
            float nx = player.pos.x + cloudDashDir.x * step / slices;
            float nz = player.pos.z + cloudDashDir.z * step / slices;
            if (!standable(nx, nz)) {
                break;
            }
            player.pos.x = nx;
            player.pos.z = nz;
        }
        if (player.pos.x == px && player.pos.z == pz) {
            cloudDashT = 0f;
        }
    }

    private void toggleLock() {
        if (lockedTarget != null && lockedTarget.alive
                && MathX.distXZ(player.pos, lockedTarget.pos) < 45f) {
            lockedTarget = null;
            return;
        }
        Vector3f fwd = cam.getDirection();
        Target best = null;
        float bestScore = -1e9f;
        for (Target t : targets) {
            if (!t.alive) {
                continue;
            }
            float d = MathX.distXZ(player.pos, t.pos);
            if (d > 34f) {
                continue;
            }
            Vector3f dir = new Vector3f(t.pos.x - player.pos.x, 0f, t.pos.z - player.pos.z);
            if (dir.lengthSquared() < 1e-4f) {
                continue;
            }
            dir.normalizeLocal();
            float dot = dir.dot(new Vector3f(fwd.x, 0f, fwd.z).normalizeLocal());
            if (dot < -0.05f) {
                continue;
            }
            float score = dot * 10f - d * 0.35f + (t instanceof WorldBoss ? 2.5f : 0f);
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        lockedTarget = best;
        if (best == null) {
            hud.toast("附近没有可锁定的敌人");
        }
    }

    /** 使用快栏当前格的道具：0 红露滴 / 1 蓝露滴 / 2 筋斗云铃。 */
    private void useItem() {
        if (selectedItem == 2) {
            toggleMount();
            return;
        }
        if (selectedItem == 1) {
            useCeruleanFlask();
        } else {
            useCrimsonFlask();
        }
        hud.setFlaskCounts(save.flaskCrimson, save.flaskCerulean);
        saveNow();
    }

    private void useCrimsonFlask() {
        if (save.flaskCrimson <= 0) {
            hud.toast("红露滴圣杯瓶已经空了");
            return;
        }
        save.flaskCrimson--;
        float heal = player.hpMax * 0.45f + 30f;
        player.healHp(heal);
        hud.addHurt(-0.25f);
        fx.spark(new Vector3f(player.pos.x, player.worldY() + 1.2f, player.pos.z),
                new ColorRGBA(1f, 0.36f, 0.24f, 1f), 16);
        fx.flash(new Vector3f(player.pos.x, player.worldY() + 1f, player.pos.z),
                new ColorRGBA(1f, 0.3f, 0.18f, 0.55f), 1.1f);
        hud.toast("红露滴圣杯瓶 · 回复生命");
    }

    private void useCeruleanFlask() {
        if (save.flaskCerulean <= 0) {
            hud.toast("蓝露滴圣杯瓶已经空了");
            return;
        }
        save.flaskCerulean--;
        player.mana = Math.min(player.mpMax, player.mana + player.mpMax * 0.6f + 15f);
        fx.spark(new Vector3f(player.pos.x, player.worldY() + 1.2f, player.pos.z),
                new ColorRGBA(0.45f, 0.65f, 1f, 1f), 16);
        fx.flash(new Vector3f(player.pos.x, player.worldY() + 1f, player.pos.z),
                new ColorRGBA(0.3f, 0.5f, 1f, 0.5f), 1.1f);
        hud.toast("蓝露滴圣杯瓶 · 回复专注");
    }

    private void interact() {
        if (nearGrace() != null) {
            discoverGrace(nearGrace());
            openGraceMenu(nearGrace());
            return;
        }
        if (nearGate()) {
            hud.toast(save.margitCleared ? "城门已开，但城内的道路尚未完成" : "雾门挡住了去路");
        }
    }

    private WorldData.GraceDef nearGrace;
    private boolean nearGate;

    private WorldData.GraceDef nearGrace() {
        return nearGrace;
    }

    private boolean nearGate() {
        return nearGate;
    }

    private void discoverGrace(WorldData.GraceDef g) {
        if (save.graces.add(g.id())) {
            hud.showLocation(g.name(), "赐福已发现");
            hud.toast("赐福已发现 · " + g.name());
            save.lastGrace = g.id();
            save.x = g.x();
            save.z = g.z();
            save.yaw = g.facing();
            saveNow();
        }
    }

    private void findInteractables() {
        nearGrace = null;
        float best = 3.8f;
        for (WorldData.GraceDef g : WorldData.GRACES) {
            if (g.id().equals("cave-exit") && !save.tutorialCleared) {
                continue;
            }
            float d = (float) Math.hypot(player.pos.x - g.x(), player.pos.z - g.z());
            if (d < best) {
                best = d;
                nearGrace = g;
            }
        }
        nearGate = MathX.distXZ(player.pos,
                new Vector3f(WorldData.MARGIT_GATE.x(), 0f, WorldData.MARGIT_GATE.z())) < 38f;
    }

    // ---------------------------------------------------------------- world update

    @Override
    public void simpleUpdate(float tpf) {
        if (loading) {
            tickLoading(tpf);
            return;
        }
        float raw = Math.min(tpf, 0.05f);
        if (phase == Phase.TITLE || phase == Phase.NAME) {
            titleTime += raw;
            props.update(raw);
            fx.update(raw);
            if (titleBoss != null) {
                titleBoss.model.animate(raw);
            }
            frameTitleCamera(raw);
            hud.update(0f, player, previewSave, null, null, null, cam, false, 10f);
            if (phase == Phase.TITLE) {
                hud.refreshTitle(slots, selectedSlot, deleteArmed);
            }
            runHeadlessTicks();
            return;
        }
        if (phase == Phase.MAP) {
            props.update(raw);
            fx.update(raw);
            hud.update(0f, player, save, null, shownBoss, null, cam, player.mounted, save.hour);
            hud.updateMap(save, player, true);
            runHeadlessTicks();
            return;
        }
        if (isMenuPhase()) {
            fx.update(raw);
            props.update(raw);
            // dt 不能传 0：提示条的淡入是按 toastT 从 2.8 往下算的，冻住就永远全透明
            hud.update(raw, player, save, null, shownBoss, null, cam, player.mounted, save.hour);
            runHeadlessTicks();
            return;
        }
        if (phase == Phase.DEAD) {
            fx.update(raw);
            props.update(raw);
            rig.update(raw, player, dummy, false, false, false);
            hud.update(raw, player, save, null, shownBoss, null, cam, false, save.hour);
            runHeadlessTicks();
            return;
        }

        if (phase == Phase.ENDING) {
            fx.update(raw);
            props.update(raw);
            hud.update(raw, player, save, null, null, null, cam, false, save.hour);
            runHeadlessTicks();
            return;
        }

        float dt = raw;
        if (headless) {
            dt = 1f / 60f;
        }
        long t0 = System.nanoTime();
        tickPlay(dt);
        if (worldTest) {
            perfWarmup++;
            if (perfWarmup > PERF_WARMUP) {
                double ms = (System.nanoTime() - t0) / 1_000_000.0;
                perfTickMs += ms;
                perfSamples++;
                perfPeakMs = Math.max(perfPeakMs, ms);
            }
        }
        runHeadlessTicks();
        maybeShot();
    }

    private void runHeadlessTicks() {
        if (worldTest) {
            tickWorldTest();
        }
        if (worldShots) {
            tickWorldShots();
        }
    }

    // ---------------------------------------------------------------- ending

    private static final float ENDING_CINEMATIC = 5.4f;

    /** The grafted king falls: the world holds its breath, then the chapter closes. */
    private void startEnding(WorldBoss b) {
        endingActive = true;
        endingTimer = 0f;
        endingFocus.set(b.pos.x, b.pos.y + 2.6f, b.pos.z);
        lockedTarget = null;
        player.setMounted(false);
        cloud.show(false);
        clearMoveInput();
        hud.setPrompt("");
        hud.setMode(WorldHud.Mode.PLAY);
        save.godrickCleared = true;
        save.endingReached = true;
        saveNow();
        fx.spark(new Vector3f(b.pos.x, b.pos.y + 2.2f, b.pos.z),
                new ColorRGBA(1f, 0.78f, 0.36f, 1f), 64);
        fx.shockwave(new Vector3f(b.pos.x, b.pos.y + 0.1f, b.pos.z), 10f, 1.3f,
                new ColorRGBA(1f, 0.72f, 0.30f, 0.55f));
    }

    private void tickEnding(float dt) {
        endingTimer += dt;
        float k = MathX.clamp(endingTimer / ENDING_CINEMATIC, 0f, 1f);
        float ang = -0.62f + k * 1.18f;
        float dist = 9.5f + k * 15.5f;
        float high = 2.4f + k * 11.5f;
        float s = FastMath.sin(ang);
        float c = FastMath.cos(ang);
        Vector3f eye = new Vector3f(endingFocus.x + s * dist,
                endingFocus.y + high, endingFocus.z + c * dist);
        float floor = terrain.height(eye.x, eye.z) + 1.8f;
        if (eye.y < floor) {
            eye.y = floor;
        }
        cam.setLocation(eye);
        cam.lookAt(endingFocus, Vector3f.UNIT_Y);

        updateHour(dt);
        updateLighting(dt);
        fx.update(dt);
        props.update(dt);
        hud.update(dt, player, save, null, null, null, cam, false, save.hour);

        float fade = MathX.clamp((endingTimer - 3.4f) / 1.9f, 0f, 1f);
        hud.setFade(fade);
        if (endingTimer >= ENDING_CINEMATIC && phase != Phase.ENDING) {
            phase = Phase.ENDING;
            hud.setFade(0f);
            showEndingCard();
        }
    }

    private void showEndingCard() {
        String[] lines = {
            "葛瑞克的王冠在黄金树下碎裂，接枝的手臂终于垂落。",
            "史东薇尔城的钟声重新响起，宁姆格福的律法归于完整。",
            "黄金树的荫庇再度覆盖这片土地，而褪色者仍要向更远之处走去。",
            "宁姆格福 · 序章  完"
        };
        String stats = "等级 " + save.level
                + "　·　讨伐强敌 " + save.bosses.size() + " / 10"
                + "　·　发现赐福 " + save.graces.size() + " / " + WorldData.GRACES.length
                + "　·　卢恩 " + String.format("%,d", save.runes)
                + "　·　旅时 " + playTime(save.playSeconds);
        hud.showEnding("宁姆格福 · 落幕", "LIMGRAVE  ·  THE CHAPTER ENDS",
                lines, stats, "按 E 返回标题");
    }

    private static String playTime(float seconds) {
        int total = (int) seconds;
        int h = total / 3600;
        int m = (total % 3600) / 60;
        return h > 0 ? h + " 小时 " + m + " 分" : m + " 分";
    }

    private void tickPlay(float dt) {
        if (endingActive) {
            tickEnding(dt);
            return;
        }
        time += dt;
        save.playSeconds += dt;
        cloudDashCd = Math.max(0f, cloudDashCd - dt);

        float moveF = (keyFwd ? 1f : 0f) - (keyBack ? 1f : 0f);
        float moveR = (keyRight ? 1f : 0f) - (keyLeft ? 1f : 0f);
        boolean moving = Math.abs(moveF) + Math.abs(moveR) > 0.01f;
        boolean sprint = keySprint;
        if (player.mounted && (gateActive || caveSealed)) {
            player.setMounted(false);
            cloud.show(false);
        }
        player.setMounted(player.mounted);

        float px = player.pos.x;
        float pz = player.pos.z;
        player.setGroundY(terrain.height(px, pz));
        // 涉水：水面 0.15m 起减速，0.75m 起明显拖慢；塘最深 1.2m，所以永远走得到对岸
        float wade = terrain.depth(px, pz);
        player.waterDrag = wade > 0.75f ? 0.55f : (wade > 0.15f ? 0.72f : 1f);
        if (wade > 0.15f && moving && player.pos.y < 0.35f && player.alive) {
            rippleT -= dt;
            if (rippleT <= 0f) {
                rippleT = RIPPLE_INTERVAL;
                fx.ripple(new Vector3f(px, terrain.waterLevelAt(px, pz) + 0.03f, pz),
                        1.5f, 0.75f, new ColorRGBA(0.72f, 0.86f, 0.92f, 0.30f));
            }
        } else {
            rippleT = 0f;
        }
        Target combat = lockedTarget != null && lockedTarget.alive ? lockedTarget : dummy;
        if (combat == dummy) {
            Target exec = executableTarget();
            if (exec != null) {
                combat = exec;
            }
        }
        execTarget = combat != dummy && combat.canBeExecuted(player.pos) ? combat : null;
        // 骑云时 F 归腾云冲刺，不能再被玩家当作"按住战技"的下压状态
        player.update(dt, moveF, moveR, sprint, keySkill && !player.mounted, rig.yaw(),
                combat != dummy, combat);
        tickCloudDash(dt);

        if (!standable(player.pos.x, player.pos.z) && player.alive) {
            player.pos.x = px;
            player.pos.z = pz;
            slideDownhill(dt);
        }
        if (player.alive) {
            // 解析碰撞：把玩家圆盘推出城墙/废墟/树干等实心体（与相机探针无关）
            collision.resolve(player.pos, player.groundY() + player.pos.y, 1.5f);
        }
        if (gateActive) {
            player.pos.x = MathX.clamp(player.pos.x, -440f, -315f);
            player.pos.z = MathX.clamp(player.pos.z, -452f, -360f);
        } else if (caveSealed && player.alive) {
            float cdx = player.pos.x - CAVE_X;
            float cdz = player.pos.z - CAVE_Z;
            float cr = FastMath.sqrt(cdx * cdx + cdz * cdz);
            if (cr > CAVE_R) {
                player.pos.x = CAVE_X + cdx / cr * CAVE_R;
                player.pos.z = CAVE_Z + cdz / cr * CAVE_R;
            }
        } else {
            player.pos.x = MathX.clamp(player.pos.x, -WorldData.HALF + 22f,
                    WorldData.HALF - 22f);
            player.pos.z = MathX.clamp(player.pos.z, -WorldData.HALF + 22f,
                    WorldData.HALF - 22f);
        }
        player.setGroundY(caveSealed && player.alive
                ? caveFloorY : terrain.height(player.pos.x, player.pos.z));

        cloud.update(dt, player, moving, sprint, fx);
        if (!player.mounted && cloud.visible()) {
            cloud.show(false);
        }

        if (player.evHurt) {
            hud.addHurt(player.mounted ? 0.25f : 0.7f);
            rig.addShake(player.mounted ? 0.22f : 0.55f);
        }
        if (player.evMountDown) {
            cloudDashT = 0f;
            cloud.burst();
            cloud.show(false);
            hud.toast("筋斗云被击散了");
        }
        if (!player.alive || player.evDied) {
            handleDeath();
            return;
        }

        if (lockedTarget != null && (!lockedTarget.alive
                || MathX.distXZ(player.pos, lockedTarget.pos) > 45f)) {
            lockedTarget = null;
        }
        if (player.evHit) {
            rig.addShake(player.evHeavyHit ? 0.5f : 0.28f);
        }

        updateBosses(dt);
        updateMobs(dt);
        updateGate(dt);
        updateCave(dt);
        updateLostRunes();
        findInteractables();
        if (victoryTimer > 0f) {
            victoryTimer -= dt;
            if (victoryTimer <= 0f) {
                hud.setMode(WorldHud.Mode.PLAY);
            }
        }
        updateHour(dt);
        updateLighting(dt);
        updatePrompt();
        updateDiscovery(dt);

        if (autoSaveTimer > 0f) {
            autoSaveTimer -= dt;
            if (autoSaveTimer <= 0f) {
                saveNow();
                autoSaveTimer = AUTOSAVE;
            }
        }
        shownBoss = chooseShownBoss();
        hud.setFlaskCounts(save.flaskCrimson, save.flaskCerulean);
        hud.setCloudCooldown(cloudDashCd / CLOUD_DASH_CD);
        hud.update(dt, player, save, lockedTarget, shownBoss,
                caveBossActive ? caveBoss : null, cam,
                player.mounted, save.hour);
        rig.update(dt, player, dummy, false, false, false);
        fx.update(dt);
        props.update(dt);
        if (player.alive && !gateActive && player.pos.z > -352f) {
            save.x = player.pos.x;
            save.z = player.pos.z;
        }
        save.yaw = player.yaw;
    }

    /** 站在过陡的坡上时沿下坡方向滑落，而不是原地卡住（贴崖攀爬的兜底）。 */
    private void slideDownhill(float dt) {
        float x = player.pos.x;
        float z = player.pos.z;
        float s = terrain.slope(x, z);
        if (s < SLIDE_SLOPE) {
            return;
        }
        float d = 1.4f;
        float gx = terrain.height(x + d, z) - terrain.height(x - d, z);
        float gz = terrain.height(x, z + d) - terrain.height(x, z - d);
        float len = (float) Math.sqrt(gx * gx + gz * gz);
        if (len < 1e-4f) {
            return;
        }
        float speed = Math.min(7f, (s - SLIDE_SLOPE) * 6f + 1.6f);
        float nx = x - gx / len * speed * dt;
        float nz = z - gz / len * speed * dt;
        if (terrain.depth(nx, nz) <= WorldData.WADE_DEPTH
                && Math.abs(nx) < WorldData.HALF - 18f
                && Math.abs(nz) < WorldData.HALF - 18f) {
            player.pos.x = nx;
            player.pos.z = nz;
        }
    }

    private boolean standable(float x, float z) {
        if (Math.abs(x) > WorldData.HALF - 18f || Math.abs(z) > WorldData.HALF - 18f) {
            return false;
        }
        // 水边不再有空气墙：只要没到 WADE_DEPTH 就踩着塘底走进去（水面在 0.55m 以上都算浅水）
        if (terrain.depth(x, z) > WorldData.WADE_DEPTH) {
            return false;
        }
        return terrain.slope(x, z) < 1.15f;
    }

    private boolean isNight(float hour) {
        return hour >= 20f || hour < 5f;
    }

    private void updateHour(float dt) {
        float oldNight = isNight(save.hour) ? 1f : 0f;
        save.hour = (save.hour + dt * HOUR_RATE) % 24f;
        float nowNight = isNight(save.hour) ? 1f : 0f;
        if (oldNight != nowNight) {
            refreshNightBosses(true);
        }
        hourCheck -= dt;
    }

    private void refreshNightBosses(boolean announce) {
        boolean night = isNight(save.hour);
        if (announce && night != lastNight) {
            hud.toast(night ? "夜幕降临 · 夜之强敌现身" : "黎明驱散了夜色");
        }
        lastNight = night;
        for (WorldBoss b : bosses) {
            if (!b.nightOnly() || save.bosses.contains(b.id())) {
                continue;
            }
            b.setAvailable(night);
        }
    }

    private void updateBosses(float dt) {
        for (int i = 0; i < bosses.size(); i++) {
            WorldBoss b = bosses.get(i);
            if (!b.available) {
                continue;
            }
            if (bossCorpse[i] > 0f) {
                bossCorpse[i] -= dt;
                if (bossCorpse[i] <= 0f) {
                    b.setAvailable(false);
                }
            }
            b.update(dt, player);
            if (b.evEngaged) {
                b.evEngaged = false;
                if (!b.gate()) {
                    hud.showLocation(b.displayName(), b.title());
                }
            }
            if (b.evDied) {
                b.evDied = false;
                onBossKilled(b, i);
            }
            if (b.cameraShake > 0f) {
                rig.addShake(b.cameraShake);
                b.cameraShake = 0f;
            }
        }
    }

    private void onBossKilled(WorldBoss b, int index) {
        if (!save.bosses.add(b.id())) {
            return;
        }
        save.runes += b.runeReward();
        bossCorpse[index] = 6f;
        if (b.gate()) {
            save.margitCleared = true;
            gateActive = false;
            fogWall.setCullHint(Spatial.CullHint.Always);
            props.openCastleGate();
            hud.showLocation("史东薇尔城", "城门前的恶兆已被击败");
            hud.toast("史东薇尔城门已开 · 主殿里有人在等着你");
            unlockKeepBoss();
        }
        lockedTarget = null;
        victoryTimer = 3.6f;
        hud.showVictory(b.displayName(), b.runeReward());
        saveNow();
        if (b.spec.archetype == WorldBoss.Archetype.GODRICK) {
            startEnding(b);
        }
    }

    /** The grafted king only holds court once the fell omen is dead. */
    private void unlockKeepBoss() {
        for (WorldBoss b : bosses) {
            if (b.spec.archetype == WorldBoss.Archetype.GODRICK && b.alive) {
                b.setAvailable(true);
            }
        }
    }

    private boolean bossUnlocked(WorldBoss b) {
        return b.spec.archetype != WorldBoss.Archetype.GODRICK || save.margitCleared;
    }

    private void updateMobs(float dt) {
        for (WorldMob m : mobs) {
            m.update(dt, player);
            if (m.evDied) {
                m.evDied = false;
                save.runes += m.runeReward();
                loseRunesFlash = 0.3f;
            }
        }
        if (loseRunesFlash > 0f) {
            loseRunesFlash -= dt;
        }
    }

    private void updateGate(float dt) {
        if (gateBoss == null) {
            return;
        }
        if (!gateActive && !save.margitCleared && gateBoss.alive && player.alive) {
            float d = MathX.distXZ(player.pos, gateBoss.pos);
            if (d < 27f && player.pos.z < -350f) {
                gateActive = true;
                gateBoss.forceEngage();
                fogWall.setCullHint(Spatial.CullHint.Never);
                hud.showLocation("史东薇尔城", "“恶兆妖鬼”玛尔基特");
                hud.toast("雾门已封锁 · 击败恶兆妖鬼");
                saveNow();
            }
        }
        if (gateActive) {
            float pulse = 0.16f + 0.06f * FastMath.sin(time * 3.2f);
            fogMat.setColor("Color", new ColorRGBA(0.95f, 0.72f, 0.34f, pulse));
            if (!gateBoss.alive || save.margitCleared) {
                gateActive = false;
                fogWall.setCullHint(Spatial.CullHint.Always);
            }
        }
    }

    private void updateCave(float dt) {
        if (!caveSealed && caveOpenT > 0f && caveOpenT < 1f) {
            caveOpenT = Math.min(1f, caveOpenT + dt / 1.8f);
            if (caveBoulder != null) {
                caveBoulder.setLocalTranslation(CAVE_X,
                        caveFloorY + 1.8f + caveOpenT * 7.5f,
                        CAVE_Z - 14.8f + caveOpenT * 5f);
                if (caveOpenT >= 1f) {
                    caveBoulder.setCullHint(Spatial.CullHint.Always);
                }
            }
        }
        if (!caveBossActive || caveBoss == null) {
            return;
        }
        caveBoss.update(dt, player);
        if (caveBoss.evPlayerHit) {
            rig.addShake(0.6f);
        }
        if (caveBoss.evPhaseChange) {
            rig.addShake(0.8f);
            hud.toast("顽石猿王 目露凶光");
        }
        if (!caveBoss.alive && caveSealed) {
            caveBoss.evDied = false;
            onCaveCleared();
        }
    }

    private void onCaveCleared() {
        save.tutorialCleared = true;
        save.runes += 800;
        caveSealed = false;
        caveOpenT = 0.001f;
        lockedTarget = null;
        victoryTimer = 3.6f;
        hud.showVictory("顽石猿王", 800);
        rig.addShake(1f);
        if (caveBoulder != null) {
            fx.dust(caveBoulder.getLocalTranslation().clone(), 26);
        }
        if (caveGraceNode != null) {
            caveGraceNode.setCullHint(Spatial.CullHint.Inherit);
        }
        if (save.graces.add("cave-exit")) {
            save.lastGrace = "cave-exit";
            hud.toast("篝火在洞口燃起 · 赐福已发现：求学洞窟");
        }
        hud.toast("洞口的巨石移开了 · 通道通向外界");
        saveNow();
    }

    private void updateLostRunes() {
        if (!save.lostAlive || save.lostRunes <= 0) {
            lostNode.setCullHint(Spatial.CullHint.Always);
            return;
        }
        lostNode.setCullHint(Spatial.CullHint.Never);
        lostNode.rotate(0f, 0.9f * (1f / 60f), 0f);
        float target = 1.0f + FastMath.sin(time * 2.4f) * 0.14f;
        lostNode.setLocalTranslation(lostNode.getLocalTranslation().x,
                terrain.height(lostNode.getLocalTranslation().x,
                        lostNode.getLocalTranslation().z) + 0.6f,
                lostNode.getLocalTranslation().z);
        if (lostGlow != null) {
            lostGlow.setColor("Color", new ColorRGBA(1f, 0.76f, 0.32f,
                    0.45f + 0.3f * FastMath.sin(time * 3.1f)));
        }
        float d = MathX.distXZ(player.pos, lostNode.getLocalTranslation());
        if (d < 2.4f) {
            int taken = save.lostRunes;
            save.runes += taken;
            save.lostRunes = 0;
            save.lostAlive = false;
            lostNode.setCullHint(Spatial.CullHint.Always);
            hud.toast("已取回 " + String.format("%,d", taken) + " 卢恩");
            fx.spark(new Vector3f(player.pos.x, player.worldY() + 1f, player.pos.z),
                    new ColorRGBA(1f, 0.82f, 0.4f, 1f), 22);
            saveNow();
        }
        if (target < 0f) {
            target = 0f;
        }
    }

    private void updateLighting(float dt) {
        if (save == null || sun == null) {
            return;
        }
        float dawn = MathX.smooth((save.hour - 4.6f) / 2.8f);
        float dusk = MathX.smooth((save.hour - 18.2f) / 3f);
        float day = MathX.clamp(dawn * (1f - dusk), 0f, 1f);
        float night = 1f - day;

        float sr = 0.98f - 0.55f * night;
        float sg = 0.82f - 0.28f * night;
        float sb = 0.62f + 0.18f * night;
        sun.setColor(new ColorRGBA(sr, sg, sb, 1f).multLocal(0.26f + 0.56f * day));
        rim.setColor(new ColorRGBA(0.32f - 0.12f * night, 0.42f - 0.18f * night,
                0.64f, 1f).multLocal(0.21f + 0.14f * day));
        fill.setColor(new ColorRGBA(1f, 0.86f, 0.7f, 1f).multLocal(0.15f + 0.14f * day));
        ambient.setColor(new ColorRGBA(0.150f + 0.025f * day, 0.165f + 0.020f * day,
                0.225f - 0.020f * day, 1f));
        if (worldShots) {
            // 截图只用来验收模型：环境光抬到能看清每一个零件
            ambient.setColor(new ColorRGBA(0.30f, 0.30f, 0.34f, 1f));
        }
        lantern.setColor(new ColorRGBA(1f, 0.68f, 0.36f, 1f)
                .multLocal(0.15f + 0.85f * night));
        lantern.setRadius(16f + 8f * night);
        lantern.setPosition(new Vector3f(player.pos.x, player.worldY() + 2.2f, player.pos.z));

        skyTint.setColor("Color", new ColorRGBA(
                1.0f - 0.82f * night,
                1.0f - 0.80f * night,
                1.0f - 0.66f * night, 1f));
        waterTint.setColor("Color", new ColorRGBA(
                0.055f - 0.036f * night,
                0.155f - 0.105f * night,
                0.195f - 0.110f * night,
                0.78f + 0.10f * night));
        if (fog != null) {
            fog.setFogColor(new ColorRGBA(
                    0.58f - 0.53f * night,
                    0.65f - 0.59f * night,
                    0.74f - 0.65f * night, 1f));
        }
    }

    /**
     * 最近一个处于失衡硬直、且在处决距离内的敌人。
     * 不要求先按 Q 锁定——魂游里失衡处决是可以直接贴上去按的，
     * 锁定只是补一个更稳的取景。
     */
    private Target executableTarget() {
        Target best = null;
        float bestD = 6f;
        for (WorldBoss b : bosses) {
            if (!b.alive || !b.available || !b.canBeExecuted(player.pos)) {
                continue;
            }
            float d = MathX.distXZ(player.pos, b.pos);
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        if (gateBoss != null && gateBoss.alive && gateBoss.canBeExecuted(player.pos)) {
            float d = MathX.distXZ(player.pos, gateBoss.pos);
            if (d < bestD) {
                bestD = d;
                best = gateBoss;
            }
        }
        if (caveBoss != null && caveBoss.alive && caveBoss.canBeExecuted(player.pos)) {
            float d = MathX.distXZ(player.pos, caveBoss.pos);
            if (d < bestD) {
                best = caveBoss;
            }
        }
        return best;
    }

    private void updatePrompt() {
        if (poseShotMode) {
            hud.setPrompt("");
            return;
        }
        if (!player.alive) {
            hud.setPrompt("");
            return;
        }
        if (execTarget != null) {
            hud.setPrompt("左键  处决");
            return;
        }
        if (gateActive) {
            hud.setPrompt("");
            return;
        }
        if (nearGrace != null) {
            hud.setPrompt("E  触摸赐福");
        } else if (nearGate) {
            hud.setPrompt(save.margitCleared ? "E  查看城门" : "E  靠近雾门");
        } else if (player.mounted) {
            hud.setPrompt("Z  收起筋斗云   ·   T  使用道具   ·   Q  锁定");
        } else {
            hud.setPrompt("");
        }
    }

    private void updateDiscovery(float dt) {
        locationCheck -= dt;
        if (locationCheck > 0f) {
            return;
        }
        locationCheck = 0.8f;
        for (WorldData.Poi poi : WorldData.POIS) {
            if (poi.radius() <= 0f || save.landmarks.contains(poi.name())) {
                continue;
            }
            float d = (float) Math.hypot(player.pos.x - poi.x(), player.pos.z - poi.z());
            if (d < poi.radius()) {
                save.landmarks.add(poi.name());
                hud.showLocation(poi.name(), "宁姆格福");
                saveNow();
                break;
            }
        }
    }

    private WorldBoss chooseShownBoss() {
        if (gateActive && gateBoss != null && gateBoss.alive) {
            return gateBoss;
        }
        WorldBoss best = null;
        float bestD = 55f;
        for (WorldBoss b : bosses) {
            if (!b.alive || !b.available) {
                continue;
            }
            float d = MathX.distXZ(player.pos, b.pos);
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- death

    private void handleDeath() {
        phase = Phase.DEAD;
        lockedTarget = null;
        execTarget = null;
        clearMoveInput();
        player.setMounted(false);
        cloud.show(false);
        if (gateActive) {
            gateActive = false;
            fogWall.setCullHint(Spatial.CullHint.Always);
            if (gateBoss != null && !save.bosses.contains(gateBoss.id())) {
                gateBoss.respawnBoss();
            }
        }
        if (!save.tutorialCleared && caveBoss != null && !caveBoss.alive) {
            caveBoss.reset(new Vector3f(CAVE_BOSS_SPOT), 3.14f);
        }
        if (save.runes > 0) {
            save.lostRunes = save.runes;
            save.lostX = player.pos.x;
            save.lostZ = player.pos.z;
            save.lostAlive = true;
            save.runes = 0;
            lostNode.setLocalTranslation(save.lostX,
                    terrain.height(save.lostX, save.lostZ), save.lostZ);
            lostNode.setCullHint(Spatial.CullHint.Never);
        }
        hud.showDeath(save.lostAlive
                ? "卢恩已散落在死亡之地   ·   按 Enter 返回最后的赐福"
                : "安眠吧，褪色者   ·   按 Enter 返回最后的赐福");
        inputManager.setCursorVisible(true);
        saveNow();
    }

    private void respawnPlayer() {
        if (phase != Phase.DEAD || save == null) {
            return;
        }
        WorldData.GraceDef g = graceById(save.lastGrace);
        if (g == null) {
            g = WorldData.GRACES[0];
        }
        float gx = g.x();
        float gz = g.z();
        float gyaw = g.facing();
        boolean cave = needsTutorial();
        if (cave) {
            gx = CAVE_SPAWN.x;
            gz = CAVE_SPAWN.z;
            gyaw = MathX.yawTo(CAVE_SPAWN, CAVE_BOSS_SPOT);
            caveSealed = true;
            caveBossActive = true;
            caveBoss.setPresent(true);
            caveBoss.reset(new Vector3f(CAVE_BOSS_SPOT), 3.14f);
        }
        save.applyTo(player);
        player.reset(new Vector3f(gx, 0f, gz), gyaw);
        player.setGroundY(cave ? caveFloorY : terrain.height(gx, gz));
        player.invuln = 1.6f;
        player.setVisible(true);
        player.setMounted(false);
        cloud.show(false);
        save.flaskCrimson = save.flaskCrimsonMax;
        save.flaskCerulean = save.flaskCeruleanMax;
        for (WorldMob m : mobs) {
            m.respawn();
        }
        for (WorldBoss b : bosses) {
            if (!save.bosses.contains(b.id()) && (!b.nightOnly() || isNight(save.hour))) {
                b.respawnBoss();
                b.available = true;
                if (!bossUnlocked(b)) {
                    b.setAvailable(false);
                }
            }
        }
        if (gateBoss != null && !save.bosses.contains(gateBoss.id())) {
            gateBoss.respawnBoss();
        }
        gateActive = false;
        fogWall.setCullHint(Spatial.CullHint.Always);
        save.x = gx;
        save.z = gz;
        save.yaw = gyaw;
        rig.reset(gx, gz, gyaw);
        rig.snap(player, dummy, false);
        hud.setFlaskCounts(save.flaskCrimson, save.flaskCerulean);
        hud.setMode(WorldHud.Mode.PLAY);
        hud.showLocation(cave ? "试炼洞窟" : "赐福", cave ? "石猴仍在洞中等你" : g.name());
        inputManager.setCursorVisible(false);
        phase = Phase.PLAY;
        saveNow();
    }

    // ---------------------------------------------------------------- persistence

    private void saveNow() {
        if (save == null || phase == Phase.TITLE || phase == Phase.NAME) {
            return;
        }
        save.yaw = player.yaw;
        save.hour = save.hour;
        save.quickSlot = selectedItem;
        if (player.alive && !gateActive) {
            save.x = player.pos.x;
            save.z = player.pos.z;
        } else if (!player.alive) {
            save.x = player.pos.x;
            save.z = player.pos.z;
        }
        save.save();
    }

    private void spawnLostAt(float x, float z) {
        save.lostX = x;
        save.lostZ = z;
        save.lostAlive = true;
        lostNode.setLocalTranslation(x, terrain.height(x, z), z);
        lostNode.setCullHint(Spatial.CullHint.Never);
    }

    // ---------------------------------------------------------------- titles

    private void frameTitleCamera(float dt) {
        float a = 0.30f + FastMath.sin(titleTime * 0.045f) * 0.55f;
        float cx = 246f + FastMath.sin(a) * 14f;
        float cz = 336f;
        float cy = terrain.height(cx, cz) + 74f;
        cam.setLocation(new Vector3f(cx, cy, cz));
        Vector3f look = new Vector3f(252f, 6f, 20f);
        cam.lookAt(look, Vector3f.UNIT_Y);
    }

    private void toggleFullscreen() {
        if (headless) {
            return;
        }
        if (!(getContext() instanceof LwjglWindow window)) {
            return;
        }
        long handle = window.getWindowHandle();
        if (handle == 0L) {
            return;
        }
        if (!fullscreen) {
            int[] px = new int[1];
            int[] py = new int[1];
            int[] pw = new int[1];
            int[] ph = new int[1];
            GLFW.glfwGetWindowPos(handle, px, py);
            GLFW.glfwGetWindowSize(handle, pw, ph);
            if (pw[0] > 0 && ph[0] > 0) {
                windowedX = px[0];
                windowedY = py[0];
                windowedW = pw[0];
                windowedH = ph[0];
            }
            long monitor = GLFW.glfwGetPrimaryMonitor();
            GLFWVidMode mode = monitor == 0L ? null : GLFW.glfwGetVideoMode(monitor);
            if (mode == null) {
                return;
            }
            GLFW.glfwSetWindowMonitor(handle, monitor, 0, 0,
                    mode.width(), mode.height(), mode.refreshRate());
            fullscreen = true;
        } else {
            GLFW.glfwSetWindowMonitor(handle, 0L, windowedX, windowedY,
                    windowedW, windowedH, 0);
            fullscreen = false;
        }
        inputManager.setCursorVisible(phase != Phase.PLAY);
    }

    @Override
    public void loseFocus() {
        super.loseFocus();
        if (!headless && phase == Phase.PLAY && player != null && player.alive) {
            openPause();
        }
    }

    @Override
    public void stop() {
        if (save != null) {
            try {
                saveNow();
            } catch (Exception ignored) {
            }
        }
        super.stop();
    }

    // ---------------------------------------------------------------- test hooks

    private WorldBoss findBoss(String id) {
        for (WorldBoss b : bosses) {
            if (b.id().equals(id)) {
                return b;
            }
        }
        return null;
    }

    private WorldMob nearestMob(WorldTerrain t) {
        WorldMob best = null;
        float bestD = 1e9f;
        for (WorldMob m : mobs) {
            float d = MathX.distXZ(player.pos, m.pos);
            if (m.alive && d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    private void teleport(float x, float z) {
        player.pos.set(x, 0f, z);
        player.pos.y = 0f;
        player.velocity.set(0f, 0f, 0f);
        player.forceGrounded();
        player.setGroundY(terrain.height(x, z));
        rig.snap(player, dummy, false);
    }

    private void beginCombatDummy() {
    }

    /**
     * 注入一次真实按键。不能直接调 {@code inputManager.onKeyEvent}——它带 eventsPermitted
     * 时序断言，只在每帧的输入阶段合法；这里把事件压进 InputManager 自己的队列，
     * 下一帧由 jME 的 processQueue 走真正的 trigger→mapping→ActionListener 派发。
     */
    private void tapKey(int code) {
        ArrayList<InputEvent> queue = inputQueue();
        queue.add(new KeyInputEvent(code, (char) 0, true, false));
        queue.add(new KeyInputEvent(code, (char) 0, false, false));
    }

    /** 注入一次左键点击。离屏上下文固定 1280×720，HUD 设计坐标即屏幕坐标。 */
    private void tapMouse(float x, float y) {
        ArrayList<InputEvent> queue = inputQueue();
        int ix = (int) x;
        int iy = (int) y;
        queue.add(new MouseButtonEvent(0, true, ix, iy));
        queue.add(new MouseButtonEvent(0, false, ix, iy));
    }

    @SuppressWarnings("unchecked")
    private ArrayList<InputEvent> inputQueue() {
        if (testQueue == null) {
            try {
                Field f = InputManager.class.getDeclaredField("inputQueue");
                f.setAccessible(true);
                testQueue = (ArrayList<InputEvent>) f.get(inputManager);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("无法取到 InputManager.inputQueue", e);
            }
        }
        return testQueue;
    }

    private ArrayList<InputEvent> testQueue;

    private void tickWorldTest() {
        frames++;
        switch (frames) {
            case 1 -> {
                player.invuln = 99999f;
                check("cave start", caveSealed && caveBossActive && caveBoss.alive
                                && Math.abs(player.pos.x - CAVE_SPAWN.x) < 1f
                                && Math.abs(player.pos.z - CAVE_SPAWN.z) < 1f,
                        "sealed=" + caveSealed + " boss=" + (caveBoss != null && caveBoss.alive)
                                + " pos=" + player.pos.x + "," + player.pos.z);
                teleport(CAVE_BOSS_SPOT.x, CAVE_BOSS_SPOT.z - 3f);
                player.yaw = MathX.yawTo(player.pos, caveBoss.pos);
                lockedTarget = caveBoss;
                testApeHp = caveBoss.hp;
                player.stamina = player.stMax;
                player.requestLight();
            }
            case 20 -> {
                check("ape takes damage", caveBoss.hp < testApeHp,
                        "hp " + testApeHp + " -> " + caveBoss.hp);
                teleport(CAVE_X + 30f, CAVE_Z);
            }
            case 24 -> {
                check("cave clamp", Math.abs(player.pos.x - (CAVE_X + CAVE_R)) < 0.4f
                                && Math.abs(player.pos.z - CAVE_Z) < 0.4f,
                        "pos=" + player.pos.x + "," + player.pos.z);
                teleport(CAVE_SPAWN.x, CAVE_SPAWN.z);
                testStartZ = player.pos.z;
                keyFwd = true;
            }
            case 70 -> {
                keyFwd = false;
                check("walking", Math.abs(player.pos.z - testStartZ) > 2f,
                        "z " + testStartZ + " -> " + player.pos.z);
                player.requestJump();
            }
            case 78 -> {
                check("jump", player.airborne, "airborne=" + player.airborne);
                player.stamina = player.stMax;
                keyChord = true;
                player.requestPlunge();
            }
            case 84 -> {
                check("jump plunge", player.attackKind == Player.AttackKind.PLUNGE
                                || player.state == Player.State.PLUNGE_LAND,
                        "kind=" + player.attackKind + " state=" + player.state);
                keyChord = false;
            }
            case 96 -> caveBoss.takeHit(99999f, 0f, caveBoss.pos);
            case 102 -> check("cave cleared", save.tutorialCleared && !caveSealed
                            && save.graces.contains("cave-exit"),
                    "cleared=" + save.tutorialCleared + " sealed=" + caveSealed
                            + " grace=" + save.graces.contains("cave-exit"));
            case 106 -> {
                toggleMount();
                check("mount summon", player.mounted && cloud.visible(), "mounted="
                        + player.mounted);
                keyFwd = true;
                keySprint = true;
                sprintDownAt = time - 1f;
            }
            case 112 -> {
                // 骑云跑动时腿必须锁死：记下迈步相位，冲刺后再核对
                testRidePhase = player.walkPhaseValue();
                testDashX = player.pos.x;
                testDashZ = player.pos.z;
                keyFwd = false;
                keySprint = false;
                tapKey(KeyInput.KEY_F);
            }
            case 140 -> {
                float dashDist = (float) Math.hypot(player.pos.x - testDashX,
                        player.pos.z - testDashZ);
                check("mount legs still", player.walkPhaseValue() == testRidePhase,
                        "phase " + testRidePhase + " -> " + player.walkPhaseValue());
                check("cloud dash", dashDist > 8f && cloudDashCd > 0f,
                        "moved=" + fmt(dashDist) + "m cd=" + fmt(cloudDashCd) + "s");
                toggleMount();
                check("mount dismiss", !player.mounted, "mounted=" + player.mounted);
                WorldMob m = nearestMob(terrain);
                if (m != null) {
                    teleport(m.pos.x, m.pos.z + 2.2f);
                    lockedTarget = m;
                    player.yaw = MathX.yawTo(player.pos, m.pos);
                    testNotes.add("mob target " + m.kind() + " spawned");
                }
            }
            case 150, 160, 170 -> {
                player.stamina = player.stMax;
                player.requestLight();
            }
            case 215 -> {
                WorldMob m = lockedTarget instanceof WorldMob ? (WorldMob) lockedTarget : null;
                check("mob combat", m == null || !m.alive || m.hp() < m.maxHp(),
                        m == null ? "no mob" : "hp=" + m.hp() + "/" + m.maxHp());
            }
            case 225 -> {
                WorldBoss b = findBoss("tree-sentinel");
                if (b != null) {
                    b.hp = 5f;
                    teleport(b.pos.x + 0.4f, b.pos.z + 2.6f);
                    lockedTarget = b;
                    save.runes = 0;
                    player.yaw = MathX.yawTo(player.pos, b.pos);
                    testRunes = save.runes;
                }
            }
            case 230, 238, 246, 254 -> {
                player.stamina = player.stMax;
                player.requestLight();
            }
            case 300 -> {
                WorldBoss b = findBoss("tree-sentinel");
                testBossKilled = b != null && !b.alive;
                check("boss killed", testBossKilled, b == null ? "null"
                        : "alive=" + b.alive + " hp=" + b.hp);
                check("boss runes", save.runes >= 2200, "runes=" + save.runes);
                save.runes = Math.max(save.runes, 5000);
                teleport(8f, 336f);
                player.invuln = 0f;
                player.takeHit(99999f, new Vector3f(player.pos.x + 1f, player.pos.y,
                        player.pos.z));
            }
            case 320 -> {
                check("death state", phase == Phase.DEAD, "phase=" + phase);
                testLostX = save.lostX;
                testLostZ = save.lostZ;
                testLostRunes = save.lostRunes;
                check("lost runes dropped", save.lostAlive && save.lostRunes > 0
                        && save.runes == 0, "lost=" + save.lostRunes + " runes=" + save.runes);
                respawnPlayer();
                check("respawn", player.alive && phase == Phase.PLAY,
                        "alive=" + player.alive + " phase=" + phase);
            }
            case 326 -> {
                // 陡坡攀爬：找一块最陡的干燥地形，朝上坡方向硬走，看能不能贴崖爬上去
                float bestS = 0f;
                float bx = 0f;
                float bz = 0f;
                for (float x = -600f; x <= 600f; x += 8f) {
                    for (float z = -600f; z <= 600f; z += 8f) {
                        if (terrain.height(x, z) <= WorldData.WATER_Y + 1f) {
                            continue;
                        }
                        float s = terrain.slope(x, z);
                        if (s > bestS) {
                            bestS = s;
                            bx = x;
                            bz = z;
                        }
                    }
                }
                testSteepSlope = bestS;
                teleport(bx, bz);
                float gx = terrain.height(bx + 2f, bz) - terrain.height(bx - 2f, bz);
                float gz = terrain.height(bx, bz + 2f) - terrain.height(bx, bz - 2f);
                player.yaw = (float) Math.atan2(gx, gz);
                rig.snap(player, dummy, false);
                testSteepY = player.groundY();
                keyFwd = true;
                testNotes.add("steep probe " + fmt(bx) + "," + fmt(bz)
                        + " slope=" + fmt(bestS));
            }
            case 338 -> {
                keyFwd = false;
                float gain = player.groundY() - testSteepY;
                check("steep climb blocked", testSteepSlope < SLIDE_SLOPE || gain < 2f,
                        "slope=" + fmt(testSteepSlope) + " gain=" + fmt(gain) + "m");
            }
            case 340 -> {
                teleport(testLostX, testLostZ);
                player.invuln = 99999f;
            }
            case 341 -> {
                // 断崖：崖线两侧 60m 内必须有 >=40m 落差与 >=1.6 的坡面
                float scarpMax = 0f;
                for (float sx = -520f; sx <= -60f; sx += 20f) {
                    float line = WorldTerrain.scarpZ(sx);
                    float drop = terrain.height(sx, line - 30f) - terrain.height(sx, line + 30f);
                    for (float z = line - 24f; z <= line + 24f; z += 1.5f) {
                        scarpMax = Math.max(scarpMax, terrain.slope(sx, z));
                    }
                    if (sx == -160f || sx == -140f) {
                        testNotes.add("scarp @" + fmt(sx) + " drop=" + fmt(drop) + "m");
                    }
                }
                check("stormhill scarp", scarpMax >= 1.6f,
                        "max slope on scarp=" + fmt(scarpMax));
                // 爬崖公路必须是缓坡：整条路按 2m 采样都不能出现 slideDownhill 的坡度，
                // 而且真的爬到台地顶（隘口曾残留一道 1.2 坡度的岩坎，玩家被推回崖脚）
                float roadMax = 0f;
                float roadTop = 0f;
                int roadBlocked = 0;
                float[] road0 = WorldData.ROADS[0];
                for (int i = 0; i + 3 < road0.length; i += 2) {
                    float ax = road0[i];
                    float az = road0[i + 1];
                    float bx = road0[i + 2];
                    float bz = road0[i + 3];
                    int n = Math.max(1, (int) (Math.hypot(bx - ax, bz - az) / 2f));
                    for (int k = 0; k <= n; k++) {
                        float t = k / (float) n;
                        float x = ax + (bx - ax) * t;
                        float z = az + (bz - az) * t;
                        float sl = terrain.slope(x, z);
                        roadMax = Math.max(roadMax, sl);
                        if (sl >= SLIDE_SLOPE) {
                            roadBlocked++;
                        }
                        roadTop = Math.max(roadTop, terrain.height(x, z));
                    }
                }
                check("stormhill pass walkable", roadBlocked == 0 && roadMax < 0.95f,
                        "blocked samples=" + roadBlocked + " max slope=" + fmt(roadMax));
                check("stormhill pass reaches plateau", roadTop >= 60f,
                        "highest road point=" + fmt(roadTop) + "m");
                // 大地标：8 以后的新遗迹（石阵/陵墓/巨像）必须落在能站人的平地上；
                // 断桥（11）是跨峡谷的，只看桥心没有意义
                int steepSites = 0;
                for (WorldData.Poi poi : WorldData.POIS) {
                    if (poi.kind() >= 8 && poi.kind() != 11
                            && terrain.slope(poi.x(), poi.z()) >= SLIDE_SLOPE) {
                        steepSites++;
                    }
                }
                check("landmarks standable", steepSites == 0, "steep sites=" + steepSites);
                check("northern peaks", terrain.height(430f, -585f) >= 180f,
                        "peak h=" + fmt(terrain.height(430f, -585f)) + "m");
                // 内陆不能被海面淹没：海水是一整块平板，低地掉到 0.55 以下就成"湖"
                int lowSamples = 0;
                int samples = 0;
                for (float x = -360f; x <= 560f; x += 40f) {
                    for (float z = -360f; z <= 480f; z += 40f) {
                        float lx = (x - WorldData.LAKE_X) / WorldData.LAKE_RX;
                        float lz = (z - WorldData.LAKE_Z) / WorldData.LAKE_RZ;
                        if (lx * lx + lz * lz < 1.4f) {
                            continue;
                        }
                        samples++;
                        if (terrain.height(x, z) < WorldData.WATER_Y + 0.8f) {
                            lowSamples++;
                        }
                    }
                }
                check("inland not flooded", lowSamples * 100 <= samples * 3,
                        "low samples=" + lowSamples + "/" + samples);
                // 峡谷：谷底到两岸中最高谷肩的落差要够，否则只是草地上的一道浅沟
                for (int i = 0; i < WorldData.GORGES.length; i++) {
                    WorldData.Gorge g = WorldData.GORGES[i];
                    float mx = (g.x1() + g.x2()) * 0.5f;
                    float mz = (g.z1() + g.z2()) * 0.5f;
                    float len = MathX.distXZ(new Vector3f(g.x1(), 0f, g.z1()),
                            new Vector3f(g.x2(), 0f, g.z2()));
                    float nx = -(g.z2() - g.z1()) / len;
                    float nz = (g.x2() - g.x1()) / len;
                    float floor = terrain.height(mx, mz);
                    float rim = floor;
                    for (float off = g.halfWidth(); off <= g.halfWidth() * 2.4f; off += 2f) {
                        rim = Math.max(rim, terrain.height(mx + nx * off, mz + nz * off));
                        rim = Math.max(rim, terrain.height(mx - nx * off, mz - nz * off));
                    }
                    check("gorge " + (i + 1) + " depth", rim - floor >= 14f,
                            "floor=" + fmt(floor) + " rim=" + fmt(rim)
                                    + " cut=" + fmt(rim - floor) + "m");
                }
                // 水塘：盆底够浅、岸唇高过水面、涉水进塘不被挡
                for (int i = 0; i < WorldData.PONDS.length; i++) {
                    WorldData.Pond p = WorldData.PONDS[i];
                    float lvl = WorldTerrain.pondLevel(i);
                    float basin = lvl - terrain.height(p.x(), p.z());
                    float rim = terrain.height(p.x() + p.rx() * 1.08f, p.z());
                    check("pond " + p.name() + " depth", basin > 0.6f
                                    && basin <= WorldData.WADE_DEPTH,
                            "basin=" + fmt(basin) + "m rim=" + fmt(rim - lvl) + "m");
                }
                WorldData.Pond p0 = WorldData.PONDS[1];
                float lvl0 = WorldTerrain.pondLevel(1);
                float shore = p0.z();
                for (float dz = 0f; dz < p0.rz() * 1.4f; dz += 0.2f) {
                    if (terrain.height(p0.x(), p0.z() + dz) > lvl0 + 0.02f) {
                        shore = p0.z() + dz;
                        break;
                    }
                }
                check("pond center wadeable",
                        standable(p0.x(), p0.z())
                                && terrain.depth(p0.x(), p0.z()) <= WorldData.WADE_DEPTH,
                        "depth=" + fmt(terrain.depth(p0.x(), p0.z()))
                                + " standable=" + standable(p0.x(), p0.z()));
                testPondShore = shore - 0.3f;
            }
            case 342 -> {
                // Boss 骨骼 + 招式动作：四个重点 Boss 必须是真关节树，并且按招式名能摆出
                // 明显不同的大风车/下劈/踏地姿势（旧版整个模型只是一个刚体组）
                String[][] rigs = {
                        {"tree-sentinel", "overhead cleave"},
                        {"margit", "holy hammer"},
                        {"agheel", "agheel fire"},
                        {"avatar", "erdtree bloom"},
                };
                for (String[] row : rigs) {
                    WorldBoss b = findBoss(row[0]);
                    if (b == null) {
                        check("rig " + row[0], false, "missing boss");
                        continue;
                    }
                    boolean wasAlive = b.alive;
                    // 大树守卫在早前的主线自测里已被打死，姿势模块只在存活分支跑
                    byAlive(b, true);
                    int joints = b.model.jointCount();
                    b.releasePose();
                    b.holdPose(row[1], WorldBoss.State.WINDUP, 0f, false);
                    float rest = b.model.poseSignature();
                    b.holdPose(row[1], WorldBoss.State.WINDUP, b.move.windup * 0.95f, false);
                    float wind = b.model.poseSignature();
                    b.holdPose(row[1], WorldBoss.State.STRIKE, 0.12f, true);
                    float hit = b.model.poseSignature();
                    b.releasePose();
                    check("rig " + row[0], joints >= 18
                                    && Math.abs(wind - rest) > 0.35f
                                    && Math.abs(hit - wind) > 0.35f,
                            "joints=" + joints + " windup+" + fmt(wind - rest)
                                    + " strike+" + fmt(hit - wind) + " alive=" + b.alive
                                    + " move=" + (b.move == null ? "-" : b.move.name)
                                    + " state=" + b.state
                                    + " k=" + fmt(b.model.dbgWindK) + "/"
                                    + fmt(b.model.dbgStrikeK) + " mv=" + b.model.dbgMove
                                    + " sig=" + fmt(rest) + "/" + fmt(wind) + "/" + fmt(hit));
                    testNotes.add("rig " + row[0] + " joints=" + joints);
                    byAlive(b, wasAlive);
                }
            }
            case 344 -> {
                // 烈焰风暴：起手要铺火柱，且施法期间被击中只掉血、不打断、不击退
                player.state = Player.State.FREE;
                player.stateTime = 0f;
                player.airborne = false;
                player.velocity.set(0f, 0f, 0f);
                player.hp = player.hpMax;
                player.mana = player.mpMax;
                player.stamina = player.stMax;
                player.invuln = 0f;
                testSkillHp = player.hp;
                testSkillFx = fx.activeFx();
                player.requestSkill();
            }
            case 345 -> {
                check("skill starts", player.hyperArmor(), "state=" + player.state);
                check("skill fx layers", fx.activeFx() >= testSkillFx + 2,
                        "fx=" + fx.activeFx() + " before=" + testSkillFx);
                player.invuln = 0f;
                boolean landed = player.takeHit(10f,
                        new Vector3f(player.pos.x + 1f, player.pos.y, player.pos.z));
                check("skill hyper armor", landed && player.state == Player.State.SKILL
                                && player.hp <= testSkillHp - 10f + 0.01f
                                && player.velocity.length() < 1.2f,
                        "landed=" + landed + " state=" + player.state + " hp="
                                + fmt(player.hp) + "/" + fmt(testSkillHp)
                                + " vel=" + fmt(player.velocity.length()));
                player.finishSkill();
            }
            case 346 -> {
                // 普通状态下同一击必须打断（HURT），证明霸体只挂在施法上
                player.invuln = 0f;
                player.takeHit(1f, new Vector3f(player.pos.x + 1f, player.pos.y, player.pos.z));
                check("hit interrupts", player.state == Player.State.HURT,
                        "state=" + player.state);
                player.hp = player.hpMax;
                player.state = Player.State.FREE;
                player.stateTime = 0f;
                player.velocity.set(0f, 0f, 0f);
            }
            case 347 -> {
                // 动作连贯性：蓄力末帧 → 挥击首帧、挥击末帧 → 收招首帧必须是连续的，
                // 中途不能有一帧弹回静止（旧版 STRIKE 把 windK 归零，抬手动作整个消失）。
                WorldBoss b = findBoss("margit");
                byAlive(b, true);
                b.releasePose();
                WorldBoss.Move smash = b.moveNamed("overhead smash");
                b.holdPose("overhead smash", WorldBoss.State.WINDUP, smash.windup * 0.95f, false);
                float windEnd = b.model.poseSignature();
                b.holdPose("overhead smash", WorldBoss.State.STRIKE, 0.001f, false);
                float strikeStart = b.model.poseSignature();
                b.holdPose("overhead smash", WorldBoss.State.STRIKE, smash.active * 0.6f, false);
                float strikeEnd = b.model.poseSignature();
                b.holdPose("overhead smash", WorldBoss.State.RECOVER, 0.001f, false);
                float recoverStart = b.model.poseSignature();
                b.releasePose();
                check("boss pose continuity",
                        Math.abs(strikeStart - windEnd) < 0.12f
                                && Math.abs(strikeEnd - windEnd) > 0.35f
                                && Math.abs(recoverStart - strikeEnd) < 0.12f,
                        "wind=" + fmt(windEnd) + " strike0=" + fmt(strikeStart)
                                + " strikeEnd=" + fmt(strikeEnd)
                                + " rec0=" + fmt(recoverStart));

                // 冲锋连续性：整段冲锋的位移必须摊到多帧上，任何一帧都不许瞬移贴脸
                WorldBoss dash = findBoss("tree-sentinel");
                byAlive(dash, true);
                dash.releasePose();
                dash.engaged = true;
                dash.cooldown = 0f;
                player.invuln = 99999f;
                float bestA = 0f;
                float bestLen = 0f;
                for (int i = 0; i < 16; i++) {
                    float a = i * FastMath.TWO_PI / 16f;
                    float len = 0f;
                    for (float d = 2f; d <= 14f; d += 1f) {
                        if (!dash.canStand(dash.pos.x + FastMath.sin(a) * d,
                                dash.pos.z + FastMath.cos(a) * d)) {
                            break;
                        }
                        len = d;
                    }
                    if (len > bestLen) {
                        bestLen = len;
                        bestA = a;
                    }
                }
                teleport(dash.pos.x + FastMath.sin(bestA) * 13f,
                        dash.pos.z + FastMath.cos(bestA) * 13f);
                player.yaw = MathX.yawTo(player.pos, dash.pos);
                dash.yaw = MathX.yawTo(dash.pos, player.pos);
                dash.state = WorldBoss.State.WINDUP;
                dash.move = dash.moveNamed("lance charge");
                dash.stateTime = dash.move.windup;
                testDashX = dash.pos.x;
                testDashZ = dash.pos.z;
                for (int i = 0; i < 90; i++) {
                    dash.update(1f / 60f, player);
                }
                float travel = (float) Math.hypot(dash.pos.x - testDashX,
                        dash.pos.z - testDashZ);
                check("boss dash continuous", dash.testMaxStep < 1.2f && travel > 4f,
                        "maxStep=" + fmt(dash.testMaxStep) + " travel=" + fmt(travel)
                                + " free=" + fmt(bestLen) + " state=" + dash.state);

                // 失衡窗口 + 处决提示：把守卫按住，下一帧 HUD 必须提示"处决"
                dash.releasePose();
                dash.state = WorldBoss.State.STAGGER;
                dash.stateTime = 0f;
                dash.executionLocked = false;
                dash.available = true;
                dash.pos.y = terrain.height(dash.pos.x, dash.pos.z);
                teleport(dash.pos.x + 2.2f, dash.pos.z);
            }
            case 348 -> {
                WorldBoss b = findBoss("tree-sentinel");
                boolean wasAlive = b.alive;
                b.hp = b.maxHp();
                check("execution prompt", execTarget == b
                                && hud.promptProbe().contains("处决"),
                        "target=" + (execTarget == null ? "none" : execTarget.hashCode())
                                + " prompt=" + hud.promptProbe());
                boolean can = b.canBeExecuted(player.pos);
                b.lockForExecution(player.pos);
                float before = b.hp;
                b.takeExecution(240f, new Vector3f(b.pos.x, b.pos.y + 1.2f, b.pos.z));
                float dealt = before - b.hp;
                check("execution knockdown", can && b.state == WorldBoss.State.KNOCKDOWN
                                && dealt >= 240f - 0.01f,
                        "can=" + can + " state=" + b.state + " dealt=" + fmt(dealt));
                b.hp = b.maxHp();
                b.state = WorldBoss.State.CHASE;
                b.stateTime = 0f;
                b.executionLocked = false;
                b.releasePose();
                byAlive(b, wasAlive);
                player.invuln = 99999f;
            }
            case 456 -> {
                // 从岸线往塘心走：旧版在岸边就被"深水"判定推回，这里必须真的踩进水里
                WorldData.Pond p0 = WorldData.PONDS[1];
                teleport(p0.x(), testPondShore);
                player.yaw = MathX.yawTo(player.pos, new Vector3f(p0.x(), 0f, p0.z()));
                rig.snap(player, dummy, false);
                testWadeDepth = terrain.depth(player.pos.x, player.pos.z);
                System.out.println("[WADE] start pos=" + player.pos + " shore=" + testPondShore
                        + " depth=" + testWadeDepth + " phase=" + phase);
                keyFwd = true;
            }
            case 469 -> {
                keyFwd = false;
                float d = terrain.depth(player.pos.x, player.pos.z);
                System.out.println("[WADE] end pos=" + player.pos + " depth=" + d
                        + " ground=" + terrain.height(player.pos.x, player.pos.z));
                check("wade into pond", d > Math.max(0.12f, testWadeDepth + 0.08f),
                        "depth " + fmt(testWadeDepth) + " -> " + fmt(d)
                                + " pos=" + fmt(player.pos.x) + "," + fmt(player.pos.z));
                keyFwd = false;
            }
            case 350 -> check("lost runes recovered", save.runes >= testLostRunes,
                    "runes=" + save.runes + " expected>=" + testLostRunes);
            case 352 -> {
                // 城墙碰撞：贴着史东薇尔幕墙南面向北走，应被挡在墙外（墙面 z=-469.5）
                teleport(-455f, -468.6f);
                player.yaw = 3.14f;
                rig.snap(player, dummy, false);
                testWallZ = player.pos.z;
                keyFwd = true;
                keySprint = true;
            }
            case 368 -> {
                keyFwd = false;
                keySprint = false;
                float face = -469.5f;
                boolean inside = Math.abs(player.pos.x + 455f) < 35f
                        && Math.abs(player.pos.z + 474f) < 4.5f;
                check("wall blocks", !inside && player.pos.z >= face - 0.05f
                                && testWallZ - player.pos.z > 0.2f,
                        "z=" + fmt(player.pos.z) + " face=" + fmt(face)
                                + " from=" + fmt(testWallZ) + " inside=" + inside);
            }
            case 370 -> {
                save.runes = 5000;
                testLevel = save.level;
                testHpMax = player.hpMax;
                currentGrace = graceById("first-step");
                openGraceMenu(currentGrace);
                openLevelMenu();
                chooseMenu(0);
            }
            case 380 -> {
                check("level up", save.level == testLevel + 1,
                        "level=" + save.level + " expected=" + (testLevel + 1));
                check("level hp", player.hpMax > testHpMax,
                        "hpMax=" + player.hpMax + " was=" + testHpMax);
                closeToPlay();
                save.flaskCrimson = 2;
                player.hp = 1f;
                selectQuickSlot(0);
                useItem();
            }
            case 390 -> {
                check("crimson flask", save.flaskCrimson == 1 && player.hp > 1f,
                        "flasks=" + save.flaskCrimson + " hp=" + player.hp);
                selectQuickSlot(1);
                boolean one = selectedItem == 1 && save.quickSlot == 1;
                selectQuickSlot(2);
                boolean two = selectedItem == 2 && save.quickSlot == 2;
                selectQuickSlot(3);
                boolean wrap = selectedItem == 0 && save.quickSlot == 0;
                check("quick slot cycle", one && two && wrap,
                        "one=" + one + " two=" + two + " wrap=" + wrap);
                // 第 3 格筋斗云铃：R 使用应等同于召唤坐骑
                selectQuickSlot(2);
            }
            case 394 -> {
                useItem();
                check("quick slot mount", player.mounted && cloud.visible(),
                        "mounted=" + player.mounted + " cloud=" + cloud.visible());
                toggleMount();
                // 蓝瓶走快栏同样生效
                selectQuickSlot(1);
                save.flaskCerulean = 2;
                player.mana = 0f;
                useItem();
            }
            case 400 -> {
                check("quick slot cerulean", save.flaskCerulean == 1 && player.mana > 0f,
                        "flasks=" + save.flaskCerulean + " mana=" + player.mana);
                setHour(22f);
                WorldBoss night = findBoss("nights-cavalry");
                check("night boss", night != null && night.available,
                        night == null ? "null" : "available=" + night.available);
                teleport(-382f, -380f);
                player.invuln = 99999f;
            }
            case 415 -> {
                check("gate trigger", gateActive, "gateActive=" + gateActive);
                WorldBoss keep = findBoss("godrick");
                check("keep boss locked", keep != null && !keep.available,
                        keep == null ? "null" : "available=" + keep.available);
                if (gateBoss != null) {
                    gateBoss.hp = 5f;
                    teleport(gateBoss.pos.x, gateBoss.pos.z + 2.8f);
                    lockedTarget = gateBoss;
                    player.yaw = MathX.yawTo(player.pos, gateBoss.pos);
                }
            }
            case 422 -> {
                player.stamina = player.stMax;
                player.requestLight();
            }
            case 450 -> {
                check("margit felled", gateBoss != null && !gateBoss.alive
                                && save.margitCleared && !gateActive,
                        gateBoss == null ? "null" : "alive=" + gateBoss.alive
                                + " cleared=" + save.margitCleared + " gate=" + gateActive);
                check("margit runes", save.runes >= 9000, "runes=" + save.runes);
                WorldBoss keep = findBoss("godrick");
                check("keep boss unlocked", keep != null && keep.available,
                        keep == null ? "null" : "available=" + keep.available);
                saveNow();
                SaveData back = SaveData.load(1);
                check("save persistence", back != null && back.level == save.level
                                && back.name.equals(save.name)
                                && back.bosses.contains("margit"),
                        back == null ? "no file" : "level=" + back.level
                                + " name=" + back.name + " bosses=" + back.bosses.size());
                check("save graces", back != null && back.graces.contains("first-step"),
                        back == null ? "no file" : "graces=" + back.graces.size());
            }
            case 465 -> {
                double avg = perfSamples > 0 ? perfTickMs / perfSamples : -1;
                int soldiers = 0;
                int demis = 0;
                int skeletons = 0;
                int wolves = 0;
                for (WorldMob m : mobs) {
                    switch (m.kind()) {
                        case SOLDIER -> soldiers++;
                        case DEMIHUMAN -> demis++;
                        case SKELETON -> skeletons++;
                        default -> wolves++;
                    }
                }
                System.out.println("[WORLDTEST] perf avg=" + fmt(avg) + "ms peak="
                        + fmt(perfPeakMs) + "ms samples=" + perfSamples
                        + " terrainTris=" + (WorldTerrain.SEGS * WorldTerrain.SEGS * 2)
                        + " propColliders=" + props.blockerCount()
                        + " mobs=" + mobs.size() + " (soldier=" + soldiers
                        + " demi=" + demis + " skeleton=" + skeletons + " wolf=" + wolves
                        + ") bosses=" + bosses.size());
                check("perf budget", avg > 0 && avg < PERF_BUDGET_MS,
                        "avg=" + fmt(avg) + "ms budget=" + PERF_BUDGET_MS + "ms"
                                + " peak=" + fmt(perfPeakMs) + "ms");
                check("wolf roster", wolves >= 8,
                        "wolves=" + wolves + " expected>=8");
                System.out.println("[WORLDTEST] solids boxes=" + collision.boxCount()
                        + " cyls=" + collision.cylCount());
                check("solid roster", collision.count() >= 400,
                        "solids=" + collision.count() + " expected>=400");
                float maxSlope = 0f;
                float msx = 0f;
                float msz = 0f;
                for (float sx = -660f; sx <= 660f; sx += 10f) {
                    for (float sz = -660f; sz <= 660f; sz += 10f) {
                        float sl = terrain.slope(sx, sz);
                        if (sl > maxSlope) {
                            maxSlope = sl;
                            msx = sx;
                            msz = sz;
                        }
                    }
                }
                testNotes.add("terrain max slope " + fmt(maxSlope)
                        + " @ " + fmt(msx) + "," + fmt(msz)
                        + " (slide threshold " + SLIDE_SLOPE + ")");
            }
            case 470 -> {
                WorldBoss keep = findBoss("godrick");
                if (keep != null) {
                    keep.hp = 5f;
                    teleport(keep.pos.x, keep.pos.z + 3.2f);
                    lockedTarget = keep;
                    player.yaw = MathX.yawTo(player.pos, keep.pos);
                    player.invuln = 99999f;
                }
            }
            case 472, 478 -> {
                player.stamina = player.stMax;
                player.requestLight();
            }
            case 500 -> {
                WorldBoss keep = findBoss("godrick");
                check("godrick felled", keep != null && !keep.alive
                                && save.godrickCleared && endingActive,
                        keep == null ? "null" : "alive=" + keep.alive
                                + " cleared=" + save.godrickCleared
                                + " ending=" + endingActive);
            }
            case 510 -> check("ending cinematic", phase == Phase.PLAY && endingActive
                    && endingTimer > 0f,
                    "phase=" + phase + " t=" + fmt(endingTimer));
            case 580 -> check("castle gate open", props.gateOpen(), "open=" + props.gateOpen());
            case 830 -> check("ending card", phase == Phase.ENDING,
                    "phase=" + phase);
            // ---- 真输入层回归：以下全部注入合成按键/鼠标事件，和玩家敲键盘走同一条
            // trigger→mapping→ActionListener 路径。注意事件是在下一帧的输入阶段派发的，
            // 而 nearGrace 这类世界状态要等 tickPlay 刷新，所以"改状态→按键→断言"之间都要留帧。
            case 840 -> tapKey(KeyInput.KEY_E);
            case 846 -> {
                check("ending returns via E", phase == Phase.TITLE, "phase=" + phase);
                tapKey(KeyInput.KEY_RETURN);
            }
            case 852 -> {
                check("title starts via Enter", phase == Phase.PLAY, "phase=" + phase);
                WorldData.GraceDef g = graceById("first-step");
                save.runes = 6000;
                teleport(g.x(), g.z() + 1.4f);
            }
            case 856 -> tapKey(KeyInput.KEY_E);
            case 862 -> {
                check("grace opens via E", phase == Phase.GRACE && menuOptions.length == 5,
                        "phase=" + phase + " opts=" + menuOptions.length
                                + " near=" + (nearGrace == null ? "none" : nearGrace.id()));
                tapKey(KeyInput.KEY_DOWN);
            }
            case 867 -> {
                check("grace selection moves", menuSelected == 1
                                && menuOptions[1].equals("升级"), "sel=" + menuSelected);
                testLevel = save.level;
                testStat0 = save.stats[0];
                tapKey(KeyInput.KEY_RETURN);
            }
            case 872 -> {
                check("level menu opens", phase == Phase.LEVEL, "phase=" + phase);
                tapKey(KeyInput.KEY_RETURN);
            }
            case 878 -> {
                check("level up via keys", save.level == testLevel + 1
                                && save.stats[0] == testStat0 + 1,
                        "level=" + save.level + " vigor=" + save.stats[0]
                                + " was=" + testLevel + "/" + testStat0);
                tapKey(KeyInput.KEY_E);
            }
            case 883 -> {
                check("level backs to grace via E", phase == Phase.GRACE, "phase=" + phase);
                tapKey(KeyInput.KEY_E);
            }
            case 888 -> {
                check("grace closes via E", phase == Phase.PLAY, "phase=" + phase);
                tapKey(KeyInput.KEY_P);
            }
            case 893 -> {
                check("P opens pause", phase == Phase.PAUSE, "phase=" + phase);
                tapKey(KeyInput.KEY_E);
            }
            case 898 -> {
                check("pause closes via E", phase == Phase.PLAY, "phase=" + phase);
                tapKey(KeyInput.KEY_ESCAPE);
            }
            case 903 -> {
                check("escape does nothing", phase == Phase.PLAY && player.alive,
                        "phase=" + phase + " alive=" + player.alive);
                save.runes = 0;
                tapKey(KeyInput.KEY_E);
            }
            case 908 -> {
                check("grace reopens via E", phase == Phase.GRACE, "phase=" + phase);
                tapMouse(640f, 452f);
            }
            case 913 -> {
                check("menu row opens via mouse", phase == Phase.LEVEL,
                        "phase=" + phase + " hit=" + hud.hitTest(640f, 452f));
                tapMouse(640f, 494f);
            }
            case 918 -> {
                check("rune shortage gives visible feedback", phase == Phase.LEVEL
                                && hud.toastProbe().contains("卢恩不足"),
                        "phase=" + phase + " toast=[" + hud.toastProbe() + "]");
                tapKey(KeyInput.KEY_E);
            }
            case 923 -> {
                check("click path backs to grace via E", phase == Phase.GRACE,
                        "phase=" + phase);
                tapKey(KeyInput.KEY_E);
            }
            case 928 -> {
                check("every layer backs via E", phase == Phase.PLAY, "phase=" + phase);
                saveNow();
                SaveData end = SaveData.load(1);
                check("ending persisted", end != null && end.godrickCleared
                                && end.endingReached && end.bosses.contains("godrick"),
                        end == null ? "no file" : "godrick=" + end.godrickCleared
                                + " ending=" + end.endingReached);
                if (testProblems.isEmpty()) {
                    System.out.println("[WORLDTEST] PASS frames=" + frames);
                } else {
                    System.out.println("[WORLDTEST] FAIL " + testProblems.size()
                            + " problems: " + String.join("; ", testProblems));
                }
                for (String note : testNotes) {
                    System.out.println("[WORLDTEST] note: " + note);
                }
                stop();
            }
            default -> {
            }
        }
    }

    /** 自测临时改生死：调用方必须还原，否则后段剧情判定会跟着变。 */
    private void byAlive(WorldBoss b, boolean alive) {
        b.alive = alive;
        if (alive) {
            b.deathTime = 0f;
        }
        b.model.resetPose();
    }

    private void check(String name, boolean ok, String detail) {        System.out.println("[WORLDTEST] " + (ok ? "PASS " : "FAIL ") + name
                + " (" + detail + ")");
        if (!ok) {
            testProblems.add(name);
        }
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }

    private void setHour(float hour) {
        save.hour = hour;
        boolean night = isNight(hour);
        if (night != lastNight) {
            refreshNightBosses(true);
        } else {
            refreshNightBosses(false);
        }
        updateLighting(0f);
    }

    private void tickWorldShots() {
        frames++;
        if (frames == 20 && System.getProperty("aedfh.matprobe") != null) {
            Spatial g = terrain.node().getChild("limgraveGround");
            if (g instanceof Geometry geo) {
                var t = geo.getMaterial().getActiveTechnique();
                System.out.println("[PROBE] ground technique=" + (t == null ? "null" : t.getDef().getLightMode())
                        + " shininess=" + geo.getMaterial().getParamValue("Shininess"));
            }
        }
        if (frames == 50) {
            pendingShot = "world-title";
            System.out.println("[WORLDSHOTS] title");
        } else if (frames == 64) {
            SaveData d = new SaveData(1);
            d.name = "观景者";
            d.graces.add("first-step");
            d.lastGrace = "first-step";
            d.save();
            loadGame(d);
            player.invuln = 99999f;
            teleport(CAVE_SPAWN.x, CAVE_SPAWN.z);
            rig.setYaw(MathX.yawTo(CAVE_SPAWN, CAVE_BOSS_SPOT));
            rig.snap(player, dummy, false);
        } else if (frames == 90) {
            pendingShot = "world-cave";
            System.out.println("[WORLDSHOTS] cave");
        } else if (frames == 104) {
            // Gallery continues in the open world: retire the trial after its shot.
            save.tutorialCleared = true;
            caveSealed = false;
            caveBossActive = false;
            caveBoss.setPresent(false);
            caveBoss.alive = false;
            if (caveBoulder != null) {
                caveBoulder.setCullHint(Spatial.CullHint.Always);
            }
            teleport(0f, 344f);
            rig.setYaw(3.14f);
            rig.snap(player, dummy, false);
        } else if (frames == 126) {
            pendingShot = "world-first-step";
        } else if (frames == 146) {
            teleport(250f, 236f);
            rig.setYaw(3.14f);
            rig.snap(player, dummy, false);
        } else if (frames == 176) {
            pendingShot = "world-lake";
        } else if (frames == 196) {
            teleport(-382f, -338f);
            rig.setYaw(3.14f);
            rig.snap(player, dummy, false);
        } else if (frames == 236) {
            pendingShot = "world-stormveil";
        } else if (frames == 241) {
            teleport(-396f, -378f);
            rig.setYaw(3.14f);
            rig.snap(player, dummy, false);
        } else if (frames == 260) {
            portrait = true;
            guiNode.setCullHint(Spatial.CullHint.Always);
        } else if (frames == 264) {
            pendingShot = "world-margit";
        } else if (frames == 276) {
            portrait = false;
            guiNode.setCullHint(Spatial.CullHint.Never);
            gateActive = false;
            fogWall.setCullHint(Spatial.CullHint.Always);
            lockedTarget = null;
            setHour(22f);
            teleport(0f, 336f);
            rig.setYaw(3.14f);
            rig.snap(player, dummy, false);
        } else if (frames == 306) {
            pendingShot = "world-night";
        } else if (frames == 321) {
            openMap(Phase.PLAY);
        } else if (frames == 341) {
            pendingShot = "world-map";
        } else if (frames == 361) {
            closeMap();
            setHour(10f);
            teleport(250f, 300f);
            rig.setYaw(3.14f);
            rig.snap(player, dummy, false);
            toggleMount();
        } else if (frames == 374) {
            pendingShot = "world-cloud";
        } else if (frames == 386) {
            if (player.mounted) {
                toggleMount();
            }
            teleport(26f, 268f);
            rig.setYaw(3.14f);
            rig.snap(player, dummy, false);
        } else if (frames == 392) {
            shotTrack = null;
            shotCam.set(Float.NaN, 0f, 0f);
            hud.clearToast();
        } else if (frames == 432) {
            WorldBoss ts = findBoss("tree-sentinel");
            if (ts != null) {
                System.out.println("[WORLDSHOTS] sentinel pos=" + ts.pos + " yaw=" + ts.yaw
                        + " state=" + ts.state + " flash=" + ts.hitFlash);
            }
        } else if (frames == 440) {
            shotCam.set(Float.NaN, 0f, 0f);
            teleport(250f, 132f);
            rig.setYaw(3.14f);
            rig.snap(player, dummy, false);
            hud.clearToast();
            WorldBoss ag = findBoss("agheel");
            if (ag != null) {
                hud.showLocation(ag.displayName(), ag.title());
            }
        } else if (frames == 446) {
            shotTrack = null;
            shotCam.set(282f, 20f, 154f);
            shotAim.set(250f, 7f, 108f);
        } else if (frames == 486) {
            pendingShot = "world-agheel";
        } else if (frames == 488) {
            shotTrack = null;
            setHour(12f);
            teleport(28f, 178f);
            rig.setYaw(3.14f);
            rig.snap(player, dummy, false);
            hud.clearToast();
            hud.clearLocation();
            WorldMob near = null;
            Vector3f picked = null;
            Vector3f aim = null;
            List<WorldMob> wolves = new ArrayList<>();
            for (WorldMob m : mobs) {
                if (m.kind() == WorldMob.Kind.WOLF) {
                    wolves.add(m);
                }
            }
            wolves.sort((p, q) -> Float.compare(
                    MathX.distXZ(p.pos, new Vector3f(20f, 0f, 172f)),
                    MathX.distXZ(q.pos, new Vector3f(20f, 0f, 172f))));
            for (WorldMob m : wolves) {
                Vector3f a = new Vector3f(m.pos.x, m.pos.y + 0.55f, m.pos.z);
                Vector3f p = pickShotCam(a, new float[]{6.5f, 8.5f, 11f},
                        new float[]{1.15f, 1.75f, 0.75f});
                if (p != null) {
                    near = m;
                    picked = p;
                    aim = a;
                    break;
                }
            }
            if (near != null) {
                shotCam.set(picked);
                shotAim.set(aim);
            }
        } else if (frames == 500) {
            pendingShot = "world-wolves";
        } else if (frames >= 506 && frames < 586) {
            int step = (frames - 506) / 10;
            if ((frames - 506) % 10 == 0) {
                frameBossShot(step);
            } else if ((frames - 506) % 10 == 5) {
                WorldBoss gb = findBoss(GALLERY[step]);
                if (gb != null) {
                    pendingShot = "world-" + GALLERY[step];
                }
            }
        } else if (frames == 586) {
            // Stormveil keep from the courtyard, then the cliff flank
            shotTrack = null;
            hud.clearLocation();
            hud.clearToast();
            shotCam.set(-322f, 128f, -468f);
            shotAim.set(-402f, 84f, -598f);
        } else if (frames == 591) {
            pendingShot = "world-keep";
        } else if (frames == 592) {
            shotCam.set(-405f, 116f, -484f);
            shotAim.set(-405f, 90f, -636f);
        } else if (frames == 597) {
            pendingShot = "world-keep-gate";
        } else if (frames == 598) {
            shotCam.set(-487f, 94f, -520f);
            shotAim.set(-424f, 76f, -616f);
        } else if (frames == 603) {
            pendingShot = "world-keep-cliff";
        } else if (frames == 610) {
            // 道具特写：关卡前方废墟（glTF 墙段 + 石柱的验收机位）
            shotTrack = null;
            hud.clearLocation();
            hud.clearToast();
            shotCam.set(-95f, terrain.height(-95f, 112f) + 13f, 112f);
            shotAim.set(-95f, terrain.height(-95f, 70f) + 2.5f, 70f);
        } else if (frames == 616) {
            pendingShot = "world-ruins";
        } else if (frames == 618) {
            // 杂兵骨骼模型验收：四种角色并排站定，玩家在后方做身高参照
            shotTrack = null;
            hud.clearLocation();
            hud.clearToast();
            setHour(11f);
            float gx = -95f;
            float gz = 100f;
            teleport(gx + 1.5f, gz + 24f);
            rig.setYaw(3.3f);
            rig.snap(player, dummy, false);
            WorldMob[] line = new WorldMob[4];
            for (WorldMob m : mobs) {
                int slot = switch (m.kind()) {
                    case SOLDIER -> 0;
                    case DEMIHUMAN -> 1;
                    case SKELETON -> 2;
                    case WOLF -> 3;
                    default -> -1;
                };
                if (slot >= 0 && line[slot] == null) {
                    line[slot] = m;
                }
            }
            for (int i = 0; i < 4; i++) {
                if (line[i] == null) {
                    continue;
                }
                float x = gx + (i - 1.5f) * 1.8f;
                // 狼给个四分之三侧脸，正对镜头看不出体型和朝向
                line[i].pinForShot(x, gz, i == 3 ? (float) Math.PI * 0.68f : (float) Math.PI);
            }
            // 别的狼原本趴在附近，取景时会从主角背后冒出来；先全部挪到远处
            for (WorldMob m : mobs) {
                if (m.kind() == WorldMob.Kind.WOLF && m != line[3]) {
                    m.pinForShot(gx + 60f, gz + 60f, 0f);
                }
            }
            float gy = terrain.height(gx, gz);
            shotCam.set(gx + 0.2f, gy + 2.8f, gz - 7.6f);
            shotAim.set(gx + 0.2f, gy + 1.1f, gz);
        } else if (frames == 620) {
            pendingShot = "world-mobs";
        } else if (frames == 624) {
            // 赐福 → 升级 → 卢恩为 0 点生命力：菜单提示行与飘字都必须看得见
            save.runes = 0;
            currentGrace = graceById("first-step");
            openGraceMenu(currentGrace);
            openLevelMenu();
            chooseMenu(0);
        } else if (frames == 632) {
            pendingShot = "world-level";
        } else if (frames == 636) {
            // 地貌验收：断崖、水塘、峡谷、雾林
            closeToPlay();
            shotTrack = null;
            hud.clearLocation();
            hud.clearToast();
            setHour(9f);
            // 离崖面 90m：贴着崖根只能拍到一面石墙，退开才看得到 60m 的完整崖壁与崖顶
            shotCam.set(-140f, terrain.height(-140f, -318f) + 10f, -318f);
            shotAim.set(-140f, 34f, -412f);
        } else if (frames == 642) {
            pendingShot = "world-cliff";
        } else if (frames == 648) {
            // 东北岸斜俯视整口塘：水面、岸唇、塘边石头与后方断崖要一起入画
            float lvl = terrain.pondLevel(0);
            shotCam.set(-178f, lvl + 46f, 244f);
            shotAim.set(-262f, lvl + 1f, 148f);
        } else if (frames == 654) {
            pendingShot = "world-pond";
        } else if (frames == 660) {
            // 从西北侧 50m 高处俯看整条峡谷：近岸只留一条边，谷底和两侧谷壁都露出来
            shotCam.set(14f, terrain.height(14f, -4f) + 48f, -4f);
            shotAim.set(100f, terrain.height(100f, -75f) + 4f, -75f);
        } else if (frames == 666) {
            pendingShot = "world-gorge";
        } else if (frames == 672) {
            shotCam.set(250f, terrain.height(250f, 330f) + 12f, 330f);
            shotAim.set(340f, terrain.height(340f, 400f) + 6f, 400f);
        } else if (frames == 678) {
            pendingShot = "world-mistwood";
        } else if (frames == 680) {
            // 风蚀石阵：从东南侧看向台地上的巨石环与祭坛
            shotCam.set(-84f, terrain.height(-84f, -470f) + 12f, -470f);
            shotAim.set(-110f, terrain.height(-110f, -500f) + 3.5f, -500f);
        } else if (frames == 686) {
            pendingShot = "world-stone-circle";
        } else if (frames == 688) {
            // 英雄陵墓：柱廊主殿正面
            shotCam.set(615f, terrain.height(615f, -219f) + 24f, -219f);
            shotAim.set(585f, terrain.height(585f, -265f) + 12f, -265f);
        } else if (frames == 694) {
            pendingShot = "world-mausoleum";
        } else if (frames == 696) {
            // 坠落巨像：斜侧 3/4，台基、断臂、巨剑都要入画
            shotCam.set(-30f, terrain.height(-30f, -300f) + 12f, -300f);
            shotAim.set(-60f, terrain.height(-60f, -335f) + 6f, -335f);
        } else if (frames == 702) {
            pendingShot = "world-colossus";
        } else if (frames == 704) {
            // 断桥：沿峡谷轴线侧看，桥面断层与桥墩一起验
            shotCam.set(160f, 44f, -8f);
            shotAim.set(100f, 22f, -75f);
        } else if (frames == 710) {
            pendingShot = "world-broken-bridge";
        } else if (frames == 712) {
            // 北境群峰：从低地北望，崖台、城墙与三座主峰的剪影
            shotCam.set(60f, 150f, -330f);
            shotAim.set(160f, 150f, -650f);
        } else if (frames == 718) {
            pendingShot = "world-mountains";
        } else if (frames == 724) {
            // 招式动作验收：飞龙吐火（张口 + 口中火柱/余烬）
            WorldBoss gb = findBoss("agheel");
            if (gb != null) {
                setHour(10f);
                hud.clearLocation();
                hud.clearToast();
                fx.clear();
                // 侧前上方 3/4：翼面、颈长和火舌都能同时读到。
                // 先定朝向再摆姿势——吐息火团是按 pose 时的 yaw 铺出去的
                poseShotCam(gb, 7f, 14f, 9.5f, gb.displayHeight() * 0.62f);
                gb.holdPose("agheel fire", WorldBoss.State.STRIKE, 0.55f, true);
                shotAim.addLocal(new Vector3f(
                        FastMath.sin(gb.yaw) * 4f, 0f, FastMath.cos(gb.yaw) * 4f));
            }
        } else if (frames == 730) {
            pendingShot = "world-agheel-fire";
        } else if (frames == 732) {
            // 玛尔基特：圣锤蓄力（举杖 + 金柱预警圈）
            WorldBoss gb = findBoss("margit");
            if (gb != null) {
                setHour(10f);
                hud.clearLocation();
                hud.clearToast();
                fx.clear();
                poseShotCam(gb, 9.5f, 5.2f, 3.4f, 3.9f);
                gb.holdPose("holy hammer", WorldBoss.State.WINDUP, 1.0f, true);
            }
        } else if (frames == 738) {
            pendingShot = "world-margit-hammer";
        } else if (frames == 740) {
            // 大树守卫：过顶下劈（人马 + 戟同帧）
            WorldBoss gb = findBoss("tree-sentinel");
            if (gb != null) {
                setHour(10f);
                hud.clearLocation();
                hud.clearToast();
                fx.clear();
                poseShotCam(gb, 6.2f, 4.4f, 2.9f, 2.7f);
                gb.holdPose("overhead cleave", WorldBoss.State.STRIKE, 0.20f, true);
            }
        } else if (frames == 746) {
            pendingShot = "world-sentinel-cleave";
        } else if (frames == 748) {
            // 黄金树化身：黄金树绽放（树冠张开 + 八道金柱）
            WorldBoss gb = findBoss("avatar");
            if (gb != null) {
                setHour(10f);
                hud.clearLocation();
                hud.clearToast();
                fx.clear();
                poseShotCam(gb, 20f, 14f, 9f, gb.displayHeight() * 0.66f);
                gb.holdPose("erdtree bloom", WorldBoss.State.WINDUP, 1.0f, true);
            }
        } else if (frames == 754) {
            pendingShot = "world-avatar-bloom";
        } else if (frames == 756) {
            // 主角 F 烈焰风暴：火柱 + 余烬，同时松掉 Boss 的冻结姿势
            for (String id : new String[]{"agheel", "margit", "tree-sentinel", "avatar"}) {
                WorldBoss gb = findBoss(id);
                if (gb != null) {
                    gb.releasePose();
                }
            }
            shotTrack = null;
            poseShotMode = false;
            hud.clearLocation();
            hud.clearToast();
            player.alive = true;
            player.hp = player.hpMax;
            player.mana = player.mpMax;
            player.stamina = player.stMax;
            player.invuln = 99999f;
            player.mounted = false;
            teleport(-95f, 100f);
            player.state = Player.State.FREE;
            player.stateTime = 0f;
            player.velocity.set(0f, 0f, 0f);
            // 把近处杂兵藏起来：技能特写里不能有狼从火柱后面走过
            for (WorldMob m : mobs) {
                if (MathX.distXZ(m.pos, player.pos) < 64f) {
                    m.hideForShot();
                }
            }
            fx.clear();
            player.requestSkill();
            float gy = terrain.height(player.pos.x, player.pos.z);
            shotCam.set(player.pos.x + 6.8f, gy + 4.0f, player.pos.z - 8.4f);
            shotAim.set(player.pos.x, gy + 2.8f, player.pos.z);
        } else if (frames == 760) {
            pendingShot = "world-skill";
        } else if (frames == 764) {
            // 挥击帧：玛尔基特杖扫的中段，抬手 → 挥出的落差要一眼能读出来
            WorldBoss gb = findBoss("margit");
            if (gb != null) {
                setHour(10f);
                hud.clearLocation();
                hud.clearToast();
                fx.clear();
                // 机位比圣锤那张更正面：横扫是横向甩杖，侧光背光会把手臂压成黑影
                poseShotCam(gb, 9.5f, 3.2f, 3.4f, 3.4f);
                gb.holdPose("staff sweep", WorldBoss.State.STRIKE, 0.16f, true);
            }
        } else if (frames == 770) {
            pendingShot = "world-margit-sweep";
        } else if (frames == 772) {
            // 黄金树绽放的“释放”帧：树冠向下炸开，与蓄力帧对照
            WorldBoss gb = findBoss("avatar");
            if (gb != null) {
                setHour(10f);
                hud.clearLocation();
                hud.clearToast();
                fx.clear();
                poseShotCam(gb, 18f, 13f, 8f, gb.displayHeight() * 0.58f);
                gb.holdPose("erdtree bloom", WorldBoss.State.STRIKE, 0.16f, true);
            }
        } else if (frames == 778) {
            pendingShot = "world-avatar-release";
        } else if (frames == 780) {
            // 失衡：跪伏姿态 + 处决窗口，验证被处决前的可读状态
            WorldBoss gb = findBoss("tree-sentinel");
            if (gb != null) {
                setHour(10f);
                hud.clearLocation();
                hud.clearToast();
                fx.clear();
                // 骑手趴在马颈上：视点要抬到骑士高度并偏正面，否则只能看到马屁股
                poseShotCam(gb, 7.4f, 3.0f, 3.8f, 3.1f);
                gb.holdPose("overhead cleave", WorldBoss.State.STAGGER, 0.9f, false);
            }
        } else if (frames == 786) {
            pendingShot = "world-sentinel-stagger";
        } else if (frames == 788) {
            // 飞龙咬击的中段：脖子伸出去、下颚全开
            WorldBoss gb = findBoss("agheel");
            if (gb != null) {
                setHour(10f);
                hud.clearLocation();
                hud.clearToast();
                fx.clear();
                // 咬击的脖子前探 8.5m：用近乎正侧的长焦机位，再把视点顺朝向推出去，
                // 头和脖颈的弧线才不会被画面左边缘切掉
                poseShotCam(gb, 6.5f, 15f, 9f, gb.displayHeight() * 0.5f);
                gb.holdPose("bite", WorldBoss.State.STRIKE, 0.14f, true);
                shotAim.addLocal(new Vector3f(
                        FastMath.sin(gb.yaw) * 4f, 0f, FastMath.cos(gb.yaw) * 4f));
            }
        } else if (frames == 794) {
            pendingShot = "world-agheel-bite";
        } else if (frames >= 812) {
            System.out.println("[WORLDSHOTS] DONE frames=" + frames);
            stop();
        }
        props.update(1f / 60f);
        fx.update(1f / 60f);
        if (frames >= 242 && frames <= 260) {
            updateBosses(1f / 60f);
        }
        if (portrait) {
            cam.setLocation(new Vector3f(-387f, 70.5f, -390f));
            cam.lookAt(new Vector3f(-387f, 67.6f, -399.5f), Vector3f.UNIT_Y);
        }
        if (shotTrack != null) {
            shotCam.set(shotTrack.pos).addLocal(shotOff);
            shotAim.set(shotTrack.pos.x,
                    shotTrack.pos.y + shotTrack.displayHeight() * 0.45f, shotTrack.pos.z);
        }
        if (!Float.isNaN(shotCam.x)) {
            cam.setLocation(shotCam);
            cam.lookAt(shotAim, Vector3f.UNIT_Y);
        }
        maybeShot();
    }

    /** Nearest camera spot around a subject that has clear line of sight and open space. */
    private Vector3f pickShotCam(Vector3f target, float[] dists, float[] heights) {
        for (float d : dists) {
            for (float hgt : heights) {
                for (int i = 0; i < 16; i++) {
                    float a = FastMath.TWO_PI * i / 16f + 0.4f;
                    float cx = target.x + FastMath.sin(a) * d;
                    float cz = target.z + FastMath.cos(a) * d;
                    float y = terrain.height(cx, cz) + hgt;
                    if (y < target.y + 0.2f) {
                        continue;
                    }
                    Vector3f p = new Vector3f(cx, y, cz);
                    if (!shotClear(target, p) || !shotRoom(target, p)) {
                        continue;
                    }
                    return p;
                }
            }
        }
        return null;
    }

    private void frameBossShot(int index) {
        String id = GALLERY[index];
        setHour("nights-cavalry".equals(id) || "deathbird".equals(id)
                || "bell-hunter".equals(id) ? 22f : 10f);
        WorldBoss gb = findBoss(id);
        if (gb == null) {
            return;
        }
        if (gb.spec.archetype == WorldBoss.Archetype.GODRICK) {
            // the keep is sealed until Margit falls; open it and face him south, toward the hall
            save.margitCleared = true;
            unlockKeepBoss();
            gb.yaw = 0f;
        } else {
            // 朝主光方位偏 35°，正面取景才是受光面；否则整只 Boss 拍成剪影
            gb.yaw = 0.84f + 0.62f;
        }
        frameBossCam(gb);
        if ("avatar".equals(id)) {
            // 黄金树化身连鹿角接近 9.5 米，通用机位只能框到胸口以上
            float yaw = gb.yaw;
            float fx = FastMath.sin(yaw);
            float fz = FastMath.cos(yaw);
            shotCam.set(gb.pos.x + fx * 18f, gb.pos.y + 4.2f, gb.pos.z + fz * 18f);
            shotAim.set(gb.pos.x, gb.pos.y + 4.6f, gb.pos.z);
            shotOff.set(shotCam).subtractLocal(gb.pos);
            shotTrack = gb;
        }
        aimShotFill();
    }

    /** 截图补光沿「机位→被摄物」打，背光机位下也看得清零件（仅 worldShots 存在）。 */
    private void aimShotFill() {
        if (shotFill == null) {
            return;
        }
        shotFill.setDirection(new Vector3f(shotAim).subtractLocal(shotCam).normalizeLocal());
    }

    /** 围着 Boss 找机位（标题卡与招式截图共用）。 */
    private void frameBossCam(WorldBoss gb) {
        float yaw = gb.yaw;
        float dh = gb.displayHeight();
        float air = Math.max(0f, gb.pos.y - terrain.height(gb.pos.x, gb.pos.z));
        Vector3f aim = new Vector3f(gb.pos.x, gb.pos.y + dh * 0.5f, gb.pos.z);
        Vector3f best = null;
        float bestScore = -1e9f;
        float near = 5.0f + Math.max(0f, dh - 2.4f) * 1.5f;
        float airLift = Math.min(air, 9f) * 0.4f;
        float[] dists = {near, near + 4f, near + 9f};
        int[] rejected = new int[3];
        for (int i = 0; i < 24; i++) {
            float a = yaw + FastMath.TWO_PI * i / 24f;
            for (float d : dists) {
                for (int h = 0; h < 3; h++) {
                    float y = gb.pos.y + dh * 0.5f + 1.1f + h * 1.8f + airLift;
                    float cx = gb.pos.x + FastMath.sin(a) * d;
                    float cz = gb.pos.z + FastMath.cos(a) * d;
                    if (y < terrain.height(cx, cz) + 1.2f) {
                        rejected[0]++;
                        continue;
                    }
                    Vector3f p = new Vector3f(cx, y, cz);
                    if (!shotClear(aim, p)) {
                        rejected[1]++;
                        continue;
                    }
                    if (!shotRoom(aim, p)) {
                        rejected[2]++;
                        continue;
                    }
                    float facing = Math.max(FastMath.cos(a - yaw - 0.6f),
                            FastMath.cos(a - yaw + 0.6f));
                    // 主光从 +x/+z 来；机位不压在受光面这一侧，整只 Boss 会拍成剪影
                    float lit = Math.max(0f, FastMath.sin(a) * 0.44f + FastMath.cos(a) * 0.40f)
                            / 0.594f;
                    float score = facing * 3f + lit * 2.2f
                            - Math.abs(d - (near + 2f)) * 0.14f - h * 0.4f;
                    if (score > bestScore) {
                        bestScore = score;
                        best = p;
                    }
                }
            }
        }
        if (best == null) {
            float fx = FastMath.sin(yaw);
            float fz = FastMath.cos(yaw);
            best = new Vector3f(gb.pos.x + fx * 26f, gb.pos.y + 14f,
                    gb.pos.z + fz * 26f);
        }
        shotCam.set(best);
        shotAim.set(aim);
        shotOff.set(best).subtractLocal(gb.pos);
        shotTrack = gb;
        float fx = FastMath.sin(yaw);
        float fz = FastMath.cos(yaw);
        teleport(gb.pos.x + fx * 24f, gb.pos.z + fz * 24f);
        hud.showLocation(gb.displayName(), gb.title());
        hud.clearToast();
        System.out.println("[WORLDSHOTS] cam " + gb.id() + " boss=" + gb.pos
                + " cam=" + best + " yaw=" + yaw + " dh=" + dh
                + " dist=" + best.distance(gb.pos)
                + " rej terrain/prop/room=" + rejected[0] + "/" + rejected[1] + "/" + rejected[2]);
    }

    /** 招式特写机位：固定 3/4 前侧视角，Boss 已被 poseLock 冻住，不需要每帧跟踪。 */
    private void poseShotCam(WorldBoss gb, float fwdDist, float sideDist, float camH,
                             float aimH) {
        // 主光方位角（+x/+z 来光）：先把 Boss 转向光源偏 35°，再正面取景，
        // 否则 yaw 恰好在背光面时整只怪会拍成黑色剪影
        gb.yaw = 0.84f + 0.62f;
        Vector3f fwd = new Vector3f(FastMath.sin(gb.yaw), 0f, FastMath.cos(gb.yaw));
        Vector3f right = new Vector3f(FastMath.cos(gb.yaw), 0f, -FastMath.sin(gb.yaw));
        shotTrack = null;
        poseShotMode = true;
        shotCam.set(gb.pos).addLocal(fwd.mult(fwdDist)).addLocal(right.mult(sideDist));
        shotCam.y = Math.max(shotCam.y, terrain.height(shotCam.x, shotCam.z) + 1.4f) + camH;
        shotAim.set(gb.pos.x, gb.pos.y + aimH, gb.pos.z);
        aimShotFill();
        // 玩家站到镜头外侧，别挡画面，也让 Boss 朝向保持在冻结时的方向
        teleport(shotCam.x + fwd.x * 4f, shotCam.z + fwd.z * 4f);
        hud.showLocation(gb.displayName(), gb.title());
        hud.clearToast();
    }

    private boolean shotClear(Vector3f aim, Vector3f camPos) {
        Vector3f dir = camPos.subtract(aim);
        float len = dir.length();
        dir.normalizeLocal();
        if (props.segmentHitsProp(aim, dir, len - 0.5f)) {
            return false;
        }
        Ray ray = new Ray(new Vector3f(aim), dir);
        CollisionResults res = new CollisionResults();
        terrain.node().collideWith(ray, res);
        for (int i = 0; i < res.size(); i++) {
            float hit = res.getCollision(i).getDistance();
            if (hit > 2.4f && hit < len - 0.5f) {
                return false;
            }
        }
        return true;
    }

    /** Reject camera spots tucked against a wall that would fill the frame edge. */
    private boolean shotRoom(Vector3f aim, Vector3f camPos) {
        Vector3f fwd = aim.subtract(camPos);
        fwd.y = 0f;
        if (fwd.lengthSquared() < 1e-4f) {
            return true;
        }
        fwd.normalizeLocal();
        float[] angles = {-2.2f, -1.6f, -1.2f, -0.7f, 0.7f, 1.2f, 1.6f, 2.2f};
        for (float ang : angles) {
            float ca = FastMath.cos(ang);
            float sa = FastMath.sin(ang);
            Vector3f dir = new Vector3f(fwd.x * ca - fwd.z * sa, 0f,
                    fwd.x * sa + fwd.z * ca);
            if (props.segmentHitsProp(camPos, dir, 6.5f)) {
                return false;
            }
            Ray ray = new Ray(new Vector3f(camPos), dir);
            CollisionResults res = new CollisionResults();
            terrain.node().collideWith(ray, res);
            if (res.size() > 0 && res.getCollision(0).getDistance() < 13f) {
                return false;
            }
        }
        return true;
    }

    private static File shotDir() {
        File dir = new File(System.getProperty("user.dir"), "build/worldshots");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            dir = new File(System.getProperty("user.dir"));
        }
        return dir;
    }

    private void maybeShot() {
        if (pendingShot == null) {
            return;
        }
        String name = pendingShot;
        pendingShot = null;
        int w = cam.getWidth();
        int h = cam.getHeight();
        if (w <= 0 || h <= 0) {
            System.out.println("[AEDFH] shot skipped, zero viewport");
            return;
        }
        java.nio.ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        renderer.readFrameBuffer(null, buf);
        File file = new File(shotDir(), name + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            JmeSystem.writeImageFile(out, "png", buf, w, h);
            System.out.println("[AEDFH] shot saved " + file.getAbsolutePath());
        } catch (IOException e) {
            System.out.println("[AEDFH] shot failed: " + e);
        }
    }

    @Override
    public void update() {
        super.update();
        maybeShot();
    }

    private static final class DummyTarget extends Target {
        DummyTarget() {
            pos.set(0f, -5000f, 0f);
            alive = false;
        }

        @Override
        float radius() {
            return 0.01f;
        }

        @Override
        float maxHp() {
            return 1f;
        }

        @Override
        float hp() {
            return 0f;
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
        }

        @Override
        void takeHit(float dmg, float poiseDmg, Vector3f impact) {
        }
    }

    private WorldData.GraceDef graceById(String id) {
        for (WorldData.GraceDef g : WorldData.GRACES) {
            if (g.id().equals(id)) {
                return g;
            }
        }
        return null;
    }

    private String graceName(String id) {
        WorldData.GraceDef g = graceById(id);
        return g == null ? "宁姆格福" : g.name();
    }
}
