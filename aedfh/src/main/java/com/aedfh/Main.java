package com.aedfh;

import com.jme3.app.SimpleApplication;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeSystem;
import com.jme3.system.lwjgl.LwjglWindow;
import com.jme3.util.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

public final class Main extends SimpleApplication {

    private enum Phase {
        PLAY, DEAD, VICTORY
    }

    private static final float FIXED_DT = 1f / 60f;
    private static final int SELFTEST_FRAMES = 700;

    private boolean selfTest;
    private boolean shotMode;
    private boolean poseMode;
    private boolean bossShots;

    private Arena arena;
    private Player player;
    private Boss boss;
    private Fx fx;
    private CameraRig rig;
    private Hud hud;
    private DirectionalLight cameraFill;

    private Phase phase = Phase.PLAY;
    private boolean paused;
    private boolean locked;
    private float hitStop;
    private float slowMo;
    private float time;
    private float emberTimer = 1.2f;
    private int frames;
    private boolean selftestHit;
    private boolean selftestSkill;
    private boolean selftestMana;
    private boolean selftestNoMana;
    private boolean selftestRoll;
    private boolean selftestPitchSign;
    private boolean selftestYawSign;
    private float selftestViewYBefore;
    private final Vector3f selftestRight = new Vector3f();
    private float lockHeadY = -1f;
    private float lockFeetY = -1f;
    private float lockBossY = -1f;
    private boolean selftestJump;
    private boolean selftestLand;
    private boolean selftestAir;
    private boolean selftestPlunge;
    private boolean selftestChargeDmg;
    private boolean selftestExec;
    private boolean selftestExecHit;
    private boolean selftestExecDmg;
    private boolean selftestKnock;
    private boolean selftestGetUp;
    private boolean execStaggered;
    private boolean execRequested;
    private float chargeHpBefore;
    private float selftestMaxY;
    private float execHpBefore = -1f;

    private boolean keyFwd;
    private boolean keyBack;
    private boolean keyLeft;
    private boolean keyRight;
    private boolean keySprint;
    private float sprintDownAt;
    private boolean keySkill;
    private boolean keyChord;
    private boolean charging;

    private boolean fullscreen;
    private int windowedX;
    private int windowedY;
    private int windowedW = 1280;
    private int windowedH = 720;

    private final Random rnd = new Random(17);
    private String pendingShot;

    private Main(boolean selfTest, boolean shotMode, boolean poseMode, boolean bossShots) {
        this.selfTest = selfTest;
        this.shotMode = shotMode;
        this.poseMode = poseMode;
        this.bossShots = bossShots;
    }

    public static void main(String[] args) {
        boolean arenaMode = false;
        for (String arg : args) {
            if ("--arena".equals(arg) || "--selftest".equals(arg) || "--shot".equals(arg)
                    || "--poses".equals(arg) || "--bossshots".equals(arg)) {
                arenaMode = true;
            }
        }
        if (!arenaMode) {
            WorldApp.main(args);
            return;
        }
        boolean selfTest = false;
        boolean shot = false;
        boolean poses = false;
        boolean bossPreview = false;
        for (String arg : args) {
            if ("--selftest".equals(arg)) {
                selfTest = true;
            } else if ("--shot".equals(arg)) {
                shot = true;
            } else if ("--poses".equals(arg)) {
                poses = true;
            } else if ("--bossshots".equals(arg)) {
                bossPreview = true;
            }
        }
        Main app = new Main(selfTest, shot, poses, bossPreview);
        boolean offscreen = selfTest || shot || poses || bossPreview;

        AppSettings settings = new AppSettings(true);
        settings.setTitle("艾尔登法猴 — 天命人");
        settings.setResolution(1280, 720);
        settings.setResizable(false);
        settings.setVSync(true);
        settings.setSamples(offscreen ? 0 : 4);
        // Same reason as WorldApp: OpenAL init failure is fatal for jME startup.
        settings.setAudioRenderer(null);
        app.setSettings(settings);
        app.setShowSettings(false);
        if (offscreen) {
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
        inputManager.setCursorVisible(false);

        cam.setFrustumPerspective(55f, 1280f / 720f, 0.1f, 1400f);
        viewPort.setBackgroundColor(new ColorRGBA(0.01f, 0.011f, 0.014f, 1f));

        arena = new Arena(assetManager, rootNode);
        fx = new Fx(assetManager, rootNode);
        player = new Player(assetManager, rootNode, fx);
        boss = new Boss(assetManager, rootNode, fx);
        rig = new CameraRig(cam);
        hud = new Hud(assetManager, guiNode);

        player.reset(Arena.PLAYER_SPAWN, 0f);
        boss.reset(Arena.BOSS_SPAWN, FastMath.PI);
        rig.reset(Arena.PLAYER_SPAWN.x, Arena.PLAYER_SPAWN.z, 0f);
        rig.snap(player, boss, false);

        if (selfTest) {
            player.reset(new Vector3f(0f, 0f, 4.2f), 0f);
            boss.reset(new Vector3f(0f, 0f, 7.0f), FastMath.PI);
            rig.reset(0f, 4.2f, 0f);
            rig.snap(player, boss, false);
        }
        if (poseMode) {
            player.reset(new Vector3f(0f, 0f, 0f), 0f);
            boss.reset(new Vector3f(30f, 0f, 30f), 0f);
            rig.reset(0f, 0f, 0f);
            rig.setYaw(2.6f);
            rig.snap(player, boss, false);
        }
        if (bossShots) {
            player.reset(new Vector3f(0f, -60f, 11f), 0f);
            boss.reset(new Vector3f(0f, 0f, 6.2f), 0f);
        }

        setupLights();
        setupPostProcessing();
        setupInput();

        if (poseMode || bossShots) {
            guiNode.setCullHint(com.jme3.scene.Node.CullHint.Always);
        }

        System.out.println("[AEDFH] init ok renderer=" + settings.getRenderer()
                + " thirdPerson=true camAtInit=" + cam.getWidth() + "x" + cam.getHeight()
                + " settings=" + settings.getWidth() + "x" + settings.getHeight());
    }

    private void setupLights() {
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.58f, -1.05f, -0.52f).normalizeLocal());
        sun.setColor(new ColorRGBA(1f, 0.78f, 0.56f, 1f).multLocal(1.5f));
        rootNode.addLight(sun);

        DirectionalLight rim = new DirectionalLight();
        rim.setDirection(new Vector3f(0.7f, -0.35f, 0.55f).normalizeLocal());
        rim.setColor(new ColorRGBA(0.3f, 0.4f, 0.62f, 1f).multLocal(0.85f));
        rootNode.addLight(rim);

        cameraFill = new DirectionalLight();
        cameraFill.setColor(new ColorRGBA(1f, 0.86f, 0.7f, 1f).multLocal(0.72f));
        cameraFill.setDirection(new Vector3f(0f, -0.3f, -1f));
        rootNode.addLight(cameraFill);

        rootNode.addLight(new AmbientLight(new ColorRGBA(0.205f, 0.215f, 0.25f, 1f)));

        float[] braziers = {0.6f, 1.42f, 2.4f, 3.85f, 4.86f, 5.72f};
        for (float a : braziers) {
            PointLight fire = new PointLight();
            fire.setPosition(new Vector3f(FastMath.sin(a) * 17.15f, 2.35f,
                    FastMath.cos(a) * 17.15f));
            fire.setColor(new ColorRGBA(1f, 0.43f, 0.13f, 1f).multLocal(1.15f));
            fire.setRadius(8f);
            rootNode.addLight(fire);
        }

        DirectionalLightShadowRenderer shadows =
                new DirectionalLightShadowRenderer(assetManager, 2048, 3);
        shadows.setLight(sun);
        shadows.setShadowIntensity(0.62f);
        shadows.setEdgeFilteringMode(EdgeFilteringMode.PCF4);
        viewPort.addProcessor(shadows);
    }

    private void setupPostProcessing() {
        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
        BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Scene);
        bloom.setBloomIntensity(0.78f);
        bloom.setBlurScale(1.25f);
        bloom.setExposureCutOff(0.78f);
        bloom.setExposurePower(2.25f);
        bloom.setDownSamplingFactor(2f);
        fpp.addFilter(bloom);
        viewPort.addProcessor(fpp);
    }

    private void setupInput() {
        inputManager.addMapping("fwd", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("back", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("left", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("right", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("sprint", new KeyTrigger(KeyInput.KEY_LSHIFT), new KeyTrigger(KeyInput.KEY_RSHIFT));
        inputManager.addMapping("skill", new KeyTrigger(KeyInput.KEY_F));
        inputManager.addMapping("jump", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("chord", new KeyTrigger(KeyInput.KEY_LCONTROL),
                new KeyTrigger(KeyInput.KEY_RCONTROL));
        inputManager.addMapping("lock", new KeyTrigger(KeyInput.KEY_Q), new MouseButtonTrigger(2));
        // Esc 全流程解绑（全屏下会被系统当成退出路径）：P 开暂停，E 继续/返回
        inputManager.addMapping("pause", new KeyTrigger(KeyInput.KEY_P),
                new KeyTrigger(KeyInput.KEY_E));
        inputManager.addMapping("restart", new KeyTrigger(KeyInput.KEY_R));
        inputManager.addMapping("fullscreen", new KeyTrigger(KeyInput.KEY_F11));
        inputManager.addMapping("light", new MouseButtonTrigger(0));
        inputManager.addMapping("heavy", new MouseButtonTrigger(1));

        inputManager.addListener(actionListener,
                "fwd", "back", "left", "right", "sprint", "skill",
                "jump", "chord", "light", "heavy", "lock", "pause", "restart", "fullscreen");
        inputManager.addRawInputListener(rawListener);
    }

    private final ActionListener actionListener = (name, isPressed, tpf) -> {
        if (selfTest || shotMode) {
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
                if (isPressed) {
                    sprintDownAt = time;
                } else if (canControl() && time - sprintDownAt < 0.2f) {
                    player.requestRoll();
                }
                return;
            }
            case "skill" -> {
                keySkill = isPressed;
                if (isPressed && canControl()) {
                    player.requestSkill();
                }
                return;
            }
            case "jump" -> {
                if (isPressed && canControl()) {
                    player.requestJump();
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
            case "light" -> {
                if (isPressed) {
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
                } else if (charging) {
                    player.releaseCharge();
                    charging = false;
                }
                return;
            }
            default -> {
            }
        }
        if (!isPressed) {
            return;
        }
        switch (name) {
            case "heavy" -> {
                if (canControl()) {
                    player.requestHeavy();
                }
            }
            case "lock" -> {
                if (canControl()) {
                    toggleLock();
                }
            }
            case "pause" -> {
                if (phase == Phase.PLAY) {
                    setPaused(!paused);
                }
            }
            case "restart" -> {
                if (phase != Phase.PLAY || paused) {
                    restartRun();
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
            if (!selfTest && !shotMode && phase == Phase.PLAY && !paused) {
                rig.mouseMoved(evt.getDX(), evt.getDY());
            }
        }

        @Override
        public void onMouseButtonEvent(MouseButtonEvent evt) {
            if (paused && phase == Phase.PLAY && evt.getButtonIndex() == 0
                    && evt.isPressed() && hud.pauseFullscreenHit(evt.getX(), evt.getY())) {
                toggleFullscreen();
            }
        }

        @Override
        public void onKeyEvent(KeyInputEvent evt) {
        }

        @Override
        public void onTouchEvent(TouchEvent evt) {
        }
    };

    private boolean canControl() {
        return phase == Phase.PLAY && !paused && player.alive;
    }

    private void toggleLock() {
        if (locked) {
            locked = false;
            return;
        }
        if (boss.alive && MathX.distXZ(player.pos, boss.pos) < 27f) {
            locked = true;
        }
    }

    private void toggleFullscreen() {
        if (selfTest || shotMode || poseMode || bossShots) {
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
        hud.setFullscreen(fullscreen);
        inputManager.setCursorVisible(paused);
    }

    private void setPaused(boolean value) {
        if (phase != Phase.PLAY) {
            return;
        }
        paused = value;
        if (value) {
            clearMoveKeys();
        }
        inputManager.setCursorVisible(value);
        hud.setPaused(value);
    }

    @Override
    public void loseFocus() {
        super.loseFocus();
        if (player != null && phase == Phase.PLAY && !paused) {
            setPaused(true);
        }
    }

    @Override
    public void simpleUpdate(float tpf) {
        float raw = Math.min(tpf, 0.05f);
        cameraFill.setDirection(cam.getDirection());

        if (selfTest) {
            tickSelfTest(raw);
            return;
        }

        if (shotMode) {
            tickShotMode(raw);
            return;
        }

        if (poseMode) {
            tickPoseMode();
            return;
        }

        if (bossShots) {
            tickBossShots();
            return;
        }

        if (paused) {
            hud.update(0f, player, boss, locked, true, cam);
            return;
        }

        float dt = raw;
        if (hitStop > 0f) {
            hitStop -= raw;
            dt = Math.min(dt, raw * 0.12f);
        }
        if (slowMo > 0f) {
            slowMo -= raw;
            dt = Math.min(dt, raw * 0.42f);
        }

        time += dt;
        arena.update(time);
        spawnAtmosphere(dt);

        float mf = 0f;
        float mr = 0f;
        boolean sprint = false;
        if (phase == Phase.PLAY) {
            mf = (keyFwd ? 1f : 0f) - (keyBack ? 1f : 0f);
            mr = (keyRight ? 1f : 0f) - (keyLeft ? 1f : 0f);
            sprint = keySprint;
        }

        player.update(dt, mf, mr, sprint, keySkill, rig.yaw(), locked, boss);

        if (player.evHit) {
            hitStop = Math.max(hitStop, player.evHeavyHit ? 0.09f : 0.055f);
            if (!player.evPlunge) {
                rig.addShake(player.evHeavyHit ? 0.6f : 0.33f);
            }
        }
        if (player.evSkillHit) {
            rig.addShake(0.22f);
        }
        if (player.evPlunge) {
            hitStop = Math.max(hitStop, 0.11f);
            rig.addShake(0.34f);
        }
        if (player.evExecute) {
            hitStop = Math.max(hitStop, 0.20f);
            slowMo = Math.max(slowMo, 0.55f);
            rig.addShake(1.25f);
            hud.addExecuteFlash();
        }
        if (player.evHurt) {
            hud.addHurt(0.9f);
            rig.addShake(0.55f);
        }
        if (player.evTired) {
            hud.addTired(0.1f);
        }
        if (player.evNoMana) {
            hud.addManaAlert();
        }

        boss.update(dt, player);
        if (boss.cameraShake > 0f) {
            rig.addShake(boss.cameraShake);
        }

        if (player.evDied) {
            phase = Phase.DEAD;
            locked = false;
            slowMo = 1.4f;
            clearMoveKeys();
            hud.showDeath();
            rig.addShake(0.9f);
        }
        if (boss.evDied) {
            phase = Phase.VICTORY;
            locked = false;
            slowMo = 1f;
            hud.showVictory();
        }

        if (locked && (!boss.alive || !player.alive
                || MathX.distXZ(player.pos, boss.pos) > 27f)) {
            locked = false;
        }

        fx.update(dt);
        rig.update(dt, player, boss, locked, false, phase == Phase.VICTORY);
        hud.update(dt, player, boss, locked, false, cam);
    }

    private void clearMoveKeys() {
        keyFwd = false;
        keyBack = false;
        keyLeft = false;
        keyRight = false;
        keySprint = false;
        keySkill = false;
        keyChord = false;
        charging = false;
    }

    private void spawnAtmosphere(float dt) {
        emberTimer -= dt;
        if (emberTimer > 0f) {
            return;
        }
        emberTimer = 0.32f + rnd.nextFloat() * 0.3f;
        float a = rnd.nextFloat() * FastMath.TWO_PI;
        float r = 3f + rnd.nextFloat() * 13f;
        fx.ember(new Vector3f(FastMath.sin(a) * r, 0.4f + rnd.nextFloat() * 2.4f,
                FastMath.cos(a) * r), new ColorRGBA(1f, 0.52f, 0.18f, 0.5f));
    }

    private void restartRun() {
        phase = Phase.PLAY;
        paused = false;
        locked = false;
        hitStop = 0f;
        slowMo = 0f;
        clearMoveKeys();
        player.reset(Arena.PLAYER_SPAWN, 0f);
        boss.reset(Arena.BOSS_SPAWN, FastMath.PI);
        rig.reset(Arena.PLAYER_SPAWN.x, Arena.PLAYER_SPAWN.z, 0f);
        rig.snap(player, boss, false);
        hud.resetRun();
        inputManager.setCursorVisible(false);
    }

    private void standInFrontOfBoss(float dist) {
        Vector3f dir = MathX.forward(boss.yaw);
        player.forceGrounded();
        player.pos.set(boss.pos.x + dir.x * dist, 0f, boss.pos.z + dir.z * dist);
    }

    private boolean bossExecWindow() {
        return boss.state == Boss.State.CHASE || boss.state == Boss.State.IDLE
                || boss.state == Boss.State.RECOVER || boss.state == Boss.State.STAGGER;
    }

    private void tickSelfTest(float raw) {
        frames++;
        if (frames == 1) {
            player.invuln = 99999f;
        }
        if (frames == 20) {
            player.requestLight();
        }
        if (frames == 60) {
            player.requestSkill();
        }
        if (frames == 120) {
            player.mana = 0f;
            player.requestSkill();
        }
        if (frames == 140) {
            player.requestRoll();
        }
        if (frames == 150) {
            selftestViewYBefore = cam.getDirection().y;
            rig.mouseMoved(0f, 150f);
        }
        if (frames == 151) {
            selftestPitchSign = cam.getDirection().y > selftestViewYBefore + 0.002f;
            float y0 = rig.yaw();
            selftestRight.set(-FastMath.cos(y0), 0f, FastMath.sin(y0));
            rig.mouseMoved(150f, 0f);
        }
        if (frames == 152) {
            selftestYawSign = MathX.forward(rig.yaw()).dot(selftestRight) > 0.004f;
            locked = true;
        }
        if (frames == 200) {
            Vector3f head = cam.getScreenCoordinates(
                    new Vector3f(player.pos.x, player.pos.y + 1.62f, player.pos.z));
            Vector3f feet = cam.getScreenCoordinates(
                    new Vector3f(player.pos.x, player.pos.y + 0.12f, player.pos.z));
            Vector3f chest = cam.getScreenCoordinates(new Vector3f(boss.pos.x, 2.2f, boss.pos.z));
            lockHeadY = head.y;
            lockFeetY = feet.y;
            lockBossY = chest.y;
        }
        if (frames == 218 || frames == 258 || frames == 330) {
            standInFrontOfBoss(2.4f);
        }
        if (frames == 220 || frames == 260) {
            player.stamina = Player.ST_MAX;
            player.requestJump();
        }
        if (frames == 232) {
            player.requestLight();
        }
        if (frames == 270) {
            player.requestPlunge();
        }
        if (frames == 330) {
            player.stamina = Player.ST_MAX;
            player.requestCharge();
        }
        if (frames == 385) {
            chargeHpBefore = boss.hp;
            player.releaseCharge();
        }
        if (frames == 450) {
            selftestChargeDmg = boss.hp <= chargeHpBefore - 150f;
        }
        if (frames > 460 && !execStaggered && boss.alive && bossExecWindow()) {
            standInFrontOfBoss(2.1f);
            if (boss.state != Boss.State.STAGGER) {
                boss.takeHit(0f, 999f, new Vector3f(boss.pos.x, 1.5f, boss.pos.z));
            }
            execStaggered = true;
        }
        if (execStaggered && !execRequested && boss.state == Boss.State.STAGGER
                && player.state == Player.State.FREE) {
            player.stamina = Player.ST_MAX;
            player.requestLight();
            execRequested = true;
        }

        float dt = FIXED_DT;
        time += dt;
        arena.update(time);
        float staminaBefore = player.stamina;
        player.update(dt, 0f, 0f, false, false, rig.yaw(), locked, boss);
        if (player.evHit) {
            selftestHit = true;
        }
        if (player.evSkillHit) {
            selftestSkill = true;
        }
        if (player.mana < Player.MP_MAX - 5f) {
            selftestMana = true;
        }
        if (player.evNoMana) {
            selftestNoMana = true;
        }
        if (frames == 140 && player.stamina <= staminaBefore - 20f) {
            selftestRoll = true;
        }
        if (player.pos.y > 0.7f) {
            selftestJump = true;
        }
        if (player.pos.y > selftestMaxY) {
            selftestMaxY = player.pos.y;
        }
        if (player.evLand) {
            selftestLand = true;
        }
        if (player.isAirborne() && player.evHit) {
            selftestAir = true;
        }
        if (player.evPlunge) {
            selftestPlunge = true;
        }
        if (player.isExecuting()) {
            selftestExec = true;
            if (execHpBefore <= 0f) {
                execHpBefore = boss.hp;
            }
        }
        if (player.evExecute) {
            selftestExecHit = true;
        }
        boss.update(dt, player);
        if (boss.state == Boss.State.KNOCKDOWN) {
            selftestKnock = true;
        }
        if (selftestKnock && !selftestGetUp
                && (boss.state == Boss.State.CHASE || boss.state == Boss.State.IDLE)) {
            selftestGetUp = true;
        }
        if (execHpBefore > 0f && boss.hp <= execHpBefore - 200f) {
            selftestExecDmg = true;
        }
        fx.update(dt);
        rig.update(dt, player, boss, locked, false, false);
        hud.update(dt, player, boss, locked, false, cam);

        if (frames >= SELFTEST_FRAMES) {
            StringBuilder problems = new StringBuilder();
            if (!selftestHit) {
                problems.append("attack never landed; ");
            }
            if (!selftestSkill || !selftestMana) {
                problems.append("skill never landed/drank mana; ");
            }
            if (!selftestNoMana) {
                problems.append("skill fired without mana; ");
            }
            if (!selftestRoll) {
                problems.append("roll did not spend stamina; ");
            }
            if (!selftestJump || !selftestLand) {
                problems.append("jump did not leave/return to ground; ");
            }
            if (!selftestAir) {
                problems.append("air attack never landed; ");
            }
            if (!selftestPlunge) {
                problems.append("plunge never resolved; ");
            }
            if (!selftestChargeDmg) {
                problems.append("charged heavy under-damaged; ");
            }
            if (!selftestExec || !selftestExecHit) {
                problems.append("execution never triggered; ");
            }
            if (!selftestExecDmg) {
                problems.append("execution damage too low; ");
            }
            if (!selftestKnock) {
                problems.append("boss never knocked down; ");
            }
            if (!selftestGetUp) {
                problems.append("boss never got up; ");
            }
            if (!selftestPitchSign) {
                problems.append("mouse up did not look up; ");
            }
            if (!selftestYawSign) {
                problems.append("mouse right did not turn right; ");
            }
            float h = cam.getHeight();
            if (lockHeadY < h * 0.28f || lockHeadY > h * 0.82f) {
                problems.append("locked: head y=").append(lockHeadY).append("; ");
            }
            if (lockFeetY > h * 0.995f) {
                problems.append("locked: feet below frame y=").append(lockFeetY).append("; ");
            }
            if (lockBossY < h * 0.06f || lockBossY > h * 0.94f) {
                problems.append("locked: boss off frame y=").append(lockBossY).append("; ");
            }
            if (boss.hp > Boss.HP_MAX - 90f) {
                problems.append("boss hp=").append(boss.hp).append("; ");
            }
            if (player.hp < Player.HP_MAX) {
                problems.append("player was hit; ");
            }
            Vector3f fwd = MathX.forward(player.yaw);
            float camDot = cam.getDirection().dot(fwd);
            if (camDot < 0.5f) {
                problems.append("camera not behind player dot=").append(camDot).append("; ");
            }
            if (cam.getLocation().y < 0.5f) {
                problems.append("camera below ground y=").append(cam.getLocation().y).append("; ");
            }
            float camDist = cam.getLocation().distance(player.pos);
            if (camDist < 3.2f || camDist > 9.5f) {
                problems.append("camera distance=").append(camDist).append("; ");
            }

            if (problems.length() == 0) {
                System.out.println("[AEDFH] SELFTEST OK frames=" + frames
                        + " bossHp=" + boss.hp
                        + " camDist=" + String.format(java.util.Locale.ROOT, "%.2f", camDist)
                        + " camDot=" + String.format(java.util.Locale.ROOT, "%.2f", camDot)
                        + " camY=" + String.format(java.util.Locale.ROOT, "%.2f", cam.getLocation().y)
                        + " lockHeadY=" + String.format(java.util.Locale.ROOT, "%.0f", lockHeadY)
                        + " lockFeetY=" + String.format(java.util.Locale.ROOT, "%.0f", lockFeetY)
                        + " lockBossY=" + String.format(java.util.Locale.ROOT, "%.0f", lockBossY)
                        + " jumpY=" + String.format(java.util.Locale.ROOT, "%.2f", selftestMaxY)
                        + " execDmg=" + String.format(java.util.Locale.ROOT, "%.0f",
                        execHpBefore > 0f ? execHpBefore - boss.hp : -1f));
            } else {
                System.out.println("[AEDFH] SELFTEST FAIL " + problems);
            }
            stop();
        }
    }

    private void tickPoseMode() {
        frames++;
        float dt = FIXED_DT;
        time += dt;
        arena.update(time);

        if (frames == 30) {
            rig.setYaw(2.6f);
        }
        if (frames == 70) {
            rig.setYaw(1.57f);
        }
        if (frames == 82) {
            rig.setYaw(2.6f);
        }
        if (frames == 94) {
            rig.setYaw(0.12f);
        }
        if (frames == 106) {
            rig.setYaw(2.42f);
        }

        boolean sprintPhase = frames >= 388 && frames <= 418;
        float mf = (frames >= 30 && frames <= 78) || sprintPhase ? 1f : 0f;
        if (frames == 112) {
            player.requestRoll();
        }
        if (frames == 150) {
            player.requestLight();
        }
        if (frames == 210) {
            player.requestHeavy();
        }
        if (frames == 280) {
            player.requestSkill();
        }
        boolean skillHeld = frames >= 280 && frames <= 360;
        if (frames == 388) {
            player.stamina = Player.ST_MAX;
        }
        if (frames == 412) {
            player.requestLight();
        }
        if (frames == 458) {
            player.stamina = Player.ST_MAX;
            player.requestRoll();
        }
        if (frames == 476) {
            player.requestLight();
        }

        if (frames == 536) {
            player.forceGrounded();
            player.pos.set(0f, 0f, 0.5f);
            player.velocity.set(0f, 0f, 0f);
        }
        if (frames == 538) {
            rig.setYaw(1.57f);
        }
        if (frames == 540) {
            player.requestJump();
        }
        if (frames == 566) {
            player.requestLight();
        }
        if (frames == 600) {
            player.requestJump();
        }
        if (frames == 620) {
            player.requestPlunge();
        }
        if (frames == 660) {
            player.stamina = Player.ST_MAX;
            player.requestCharge();
        }
        if (frames == 816) {
            player.forceGrounded();
            player.pos.set(0f, 0f, 1.1f);
            boss.pos.set(0f, 0f, 3.2f);
            boss.yaw = FastMath.PI;
            boss.takeHit(0f, 999f, new Vector3f(0f, 1.5f, 3.2f));
            rig.setYaw(-1.45f);
        }
        if (frames > 816 && player.state == Player.State.FREE
                && boss.state == Boss.State.STAGGER && !execRequested) {
            player.stamina = Player.ST_MAX;
            player.requestLight();
            execRequested = true;
        }
        if (frames > 816) {
            boss.update(dt, player);
            boss.pos.set(0f, 0f, 3.2f);
        }

        player.update(dt, mf, 0f, sprintPhase, skillHeld, 0f, false, boss);

        if (frames == 60) {
            pendingShot = "pose-walk-front";
        } else if (frames == 76) {
            pendingShot = "pose-walk-side";
        } else if (frames == 90) {
            pendingShot = "pose-idle-front";
        } else if (frames == 100) {
            pendingShot = "pose-idle-back";
        } else if (frames == 124) {
            pendingShot = "pose-roll";
        } else if (frames == 130) {
            pendingShot = "pose-roll-mid";
        } else if (frames == 160) {
            pendingShot = "pose-l1-windup";
        } else if (frames == 164) {
            pendingShot = "pose-l1-hit";
        } else if (frames == 176) {
            pendingShot = "pose-l1-follow";
        } else if (frames == 238) {
            pendingShot = "pose-heavy-windup";
        } else if (frames == 248) {
            pendingShot = "pose-heavy-hit";
        } else if (frames == 258) {
            pendingShot = "pose-heavy-follow";
        } else if (frames == 286) {
            pendingShot = "pose-skill-start";
        } else if (frames == 306) {
            pendingShot = "pose-skill-spin";
        } else if (frames == 336) {
            pendingShot = "pose-skill-burn";
        } else if (frames == 420) {
            pendingShot = "pose-run-windup";
        } else if (frames == 426) {
            pendingShot = "pose-run-hit";
        } else if (frames == 438) {
            pendingShot = "pose-run-follow";
        } else if (frames == 496) {
            pendingShot = "pose-rollatk-windup";
        } else if (frames == 502) {
            pendingShot = "pose-rollatk-hit";
        } else if (frames == 508) {
            pendingShot = "pose-rollatk-follow";
        } else if (frames == 552) {
            pendingShot = "pose-jump-rise";
        } else if (frames == 560) {
            pendingShot = "pose-jump-fall";
        } else if (frames == 574) {
            pendingShot = "pose-air-slash";
        } else if (frames == 625) {
            pendingShot = "pose-plunge-raise";
        } else if (frames == 636) {
            pendingShot = "pose-plunge-swing";
        } else if (frames == 650) {
            pendingShot = "pose-plunge-land";
        } else if (frames == 682) {
            pendingShot = "pose-charge-mid";
        } else if (frames == 728) {
            pendingShot = "pose-charge-full";
        } else if (frames == 742) {
            pendingShot = "pose-charged-windup";
        } else if (frames == 772) {
            pendingShot = "pose-charged-hit";
        } else if (frames == 834) {
            pendingShot = "pose-exec-raise";
        } else if (frames == 852) {
            pendingShot = "pose-exec-windup";
        } else if (frames == 862) {
            pendingShot = "pose-exec-strike";
        } else if (frames == 896) {
            pendingShot = "pose-exec-knock";
        } else if (frames >= 980) {
            System.out.println("[AEDFH] POSES OK frames=" + frames);
            stop();
        }
        if (pendingShot != null) {
            System.out.println("[AEDFH] pose shot " + pendingShot
                    + " state=" + player.state);
        }

        fx.update(dt);
        rig.update(dt, player, boss, false, false, false);
        if (frames >= 605 && frames <= 658) {
            cam.setLocation(new Vector3f(player.pos.x + 1.6f, 1.8f, player.pos.z + 4.0f));
            cam.lookAt(new Vector3f(player.pos.x, 1.15f, player.pos.z + 0.3f), Vector3f.UNIT_Y);
        }
        hud.update(dt, player, boss, false, false, cam);
    }

    private void tickBossShots() {
        frames++;
        float dt = FIXED_DT;
        time += dt;
        arena.update(time);
        player.hp = 1e9f;
        player.pos.set(0f, -60f, 10.4f);

        if (frames == 40) {
            boss.takeHit(800f, 0f, new Vector3f(0f, 1.8f, 6.2f));
        }
        if (frames == 60) {
            beginBossMove(0);
        }
        if (frames == 200) {
            beginBossMove(1);
        }
        if (frames == 300) {
            beginBossMove(2);
        }
        if (frames == 440) {
            beginBossMove(3);
        }
        if (frames == 535) {
            boss.pos.set(0f, 0f, 6.2f);
        }
        if (frames == 540) {
            boss.takeHit(0f, 999f, new Vector3f(0f, 1.5f, 6.2f));
        }
        boss.update(dt, player);
        fx.update(dt);

        cam.setLocation(new Vector3f(5.2f, 2.9f, 16.2f));
        cam.lookAt(new Vector3f(0f, 1.55f, 7.6f), Vector3f.UNIT_Y);

        pendingShot = bossShotName(frames);
        if (pendingShot != null) {
            System.out.println("[AEDFH] boss shot " + pendingShot
                    + " attack=" + boss.attackIndex() + " state=" + boss.state
                    + " phase2=" + boss.phase2 + " posZ=" + boss.pos.z);
        }
        if (frames >= 575) {
            System.out.println("[AEDFH] BOSSSHOTS OK frames=" + frames);
            stop();
        }
    }

    private void beginBossMove(int index) {
        boss.pos.set(0f, 0f, 6.2f);
        boss.yaw = 0f;
        boss.debugAttack(index);
    }

    private static String bossShotName(int frame) {
        return switch (frame) {
            case 95 -> "boss-smash-windup";
            case 116 -> "boss-smash-hit";
            case 128 -> "boss-smash-follow";
            case 225 -> "boss-sweep-windup";
            case 240 -> "boss-sweep-hit";
            case 252 -> "boss-sweep-follow";
            case 350 -> "boss-quake-windup";
            case 368 -> "boss-quake-hit";
            case 386 -> "boss-quake-aftershock";
            case 478 -> "boss-leap-windup";
            case 496 -> "boss-leap-air";
            case 508 -> "boss-leap-hit";
            case 518 -> "boss-leap-follow";
            case 542 -> "boss-stagger-burst";
            case 552 -> "boss-stagger-glow";
            case 566 -> "boss-stagger-glow2";
            default -> null;
        };
    }

    private void tickShotMode(float raw) {
        frames++;
        float dt = FIXED_DT;
        time += dt;
        if (frames == 200) {
            player.requestSkill();
        }
        if (frames == 380) {
            locked = true;
        }
        if (frames == 470) {
            standInFrontOfBoss(2.2f);
            boss.takeHit(0f, 999f, new Vector3f(boss.pos.x, 1.5f, boss.pos.z));
        }
        if (frames == 478 && player.state == Player.State.FREE
                && boss.state == Boss.State.STAGGER) {
            player.stamina = Player.ST_MAX;
            player.requestLight();
        }
        boolean skillHeld = frames >= 200 && frames <= 300;
        arena.update(time);
        player.update(dt, 0f, 0f, false, skillHeld, rig.yaw(), false, boss);
        boss.update(dt, player);
        if (player.evDied) {
            phase = Phase.DEAD;
            locked = false;
            hud.showDeath();
        }
        if (boss.evDied) {
            phase = Phase.VICTORY;
            locked = false;
            hud.showVictory();
        }
        fx.update(dt);
        rig.update(dt, player, boss, locked, false, phase == Phase.VICTORY);
        hud.update(dt, player, boss, locked, false, cam);

        if (shotMode) {
            tickShot();
        }
    }

    private void tickShot() {
        if (frames == 90) {
            pendingShot = "aedfh-intro";
        } else if (frames == 262) {
            pendingShot = "aedfh-skill";
        } else if (frames == 340) {
            pendingShot = "aedfh-fight";
        } else if (frames == 430) {
            pendingShot = "aedfh-locked";
            Vector3f hd = cam.getScreenCoordinates(
                    new Vector3f(player.pos.x, player.pos.y + 1.62f, player.pos.z));
            Vector3f ft = cam.getScreenCoordinates(
                    new Vector3f(player.pos.x, player.pos.y + 0.12f, player.pos.z));
            System.out.println("[AEDFH] locked framing dist="
                    + String.format(java.util.Locale.ROOT, "%.2f", MathX.distXZ(player.pos, boss.pos))
                    + " headY=" + String.format(java.util.Locale.ROOT, "%.0f", hd.y)
                    + " feetY=" + String.format(java.util.Locale.ROOT, "%.0f", ft.y)
                    + " headX=" + String.format(java.util.Locale.ROOT, "%.0f", hd.x)
                    + " h=" + cam.getHeight());
        } else if (frames == 505) {
            pendingShot = "aedfh-exec";
        } else if (frames == 545) {
            pendingShot = "aedfh-exec2";
        } else if (frames >= 560) {
            System.out.println("[AEDFH] SHOT OK frames=" + frames);
            stop();
        }
    }

    private static File shotDir() {
        File dir = new File(System.getProperty("user.dir"), "build/areshots");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            dir = new File(System.getProperty("user.dir"));
        }
        return dir;
    }

    @Override
    public void update() {
        super.update();
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
}
