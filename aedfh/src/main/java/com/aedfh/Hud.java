package com.aedfh;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;

import java.awt.Color;

final class Hud {

    private static final float W = 1280f;
    private static final float H = 720f;

    private static final float BOSS_BAR_W = 680f;
    private static final float HP_BAR_W = 304f;
    private static final float ST_BAR_W = 248f;
    private static final float MP_BAR_W = 248f;
    private static final float CHARGE_BAR_W = 150f;

    private static final ColorRGBA GOLD = new ColorRGBA(0.88f, 0.75f, 0.48f, 1f);
    private static final ColorRGBA PALE = new ColorRGBA(0.8f, 0.78f, 0.71f, 1f);
    private static final ColorRGBA ASH = new ColorRGBA(0.64f, 0.61f, 0.55f, 1f);
    private static final ColorRGBA BLOOD = new ColorRGBA(0.62f, 0.09f, 0.06f, 1f);
    private static final ColorRGBA BOSS_FILL = new ColorRGBA(0.8f, 0.62f, 0.26f, 1f);
    private static final ColorRGBA BOSS_CHIP = new ColorRGBA(0.93f, 0.83f, 0.6f, 1f);
    private static final ColorRGBA HP_FILL = new ColorRGBA(0.66f, 0.11f, 0.07f, 1f);
    private static final ColorRGBA HP_CHIP = new ColorRGBA(0.9f, 0.74f, 0.5f, 1f);
    private static final ColorRGBA ST_FILL = new ColorRGBA(0.6f, 0.74f, 0.34f, 1f);
    private static final ColorRGBA MP_FILL = new ColorRGBA(0.22f, 0.42f, 0.82f, 1f);
    private static final ColorRGBA CHARGE_FILL = new ColorRGBA(0.95f, 0.78f, 0.32f, 0.95f);

    private static final Mesh UNIT = unitQuad();

    private static final class Item {
        final Geometry geo;
        final ColorRGBA base;

        Item(Geometry geo, ColorRGBA base) {
            this.geo = geo;
            this.base = base;
        }

        void alpha(float a) {
            geo.getMaterial().setColor("Color",
                    new ColorRGBA(base.r, base.g, base.b, MathX.clamp(base.a * a, 0f, 1f)));
        }

        void visible(boolean v) {
            geo.setCullHint(v ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        }
    }

    private static final float PAUSE_BTN_X = 470f;
    private static final float PAUSE_BTN_Y = 286f;
    private static final float PAUSE_BTN_W = 340f;
    private static final float PAUSE_BTN_H = 52f;

    private final AssetManager am;
    private final Node gui;
    private final Node design = new Node("hudDesign");
    private final Texture2D white;
    private final Texture2D radial;
    private final Texture2D lockTex;
    private float hudScale = 1f;
    private float hudOffX;
    private float hudOffY;
    private int hudW = (int) W;
    private int hudH = (int) H;

    private final Geometry vignette;
    private final Geometry hurtEdge;
    private final Geometry hurtFlat;

    private Item bossTitle;
    private Item bossSub;
    private Item bossBack;
    private Item bossChip;
    private Item bossFill;

    private Item hpBack;
    private Item hpChip;
    private Item hpFill;
    private Item stBack;
    private Item stFill;
    private Item mpBack;
    private Item mpFill;
    private Item skillLabel;
    private Item chargeBar;
    private Item chargeLabel;
    private Item execFlash;
    private Item controls;

    private Item introTitle;
    private Item introRule;
    private Item introSub;

    private Item deathVeil;
    private Item deathTitle;
    private Item deathSub;

    private Item victoryVeil;
    private Item victoryTitle;
    private Item victorySub;
    private Item retry;

    private Item pausedVeil;
    private Item pausedTitle;
    private Item pausedSub;
    private Item pausedHint;
    private Item pausedControls;
    private Item pausedBtnBg;
    private Item pausedBtnLineA;
    private Item pausedBtnLineB;
    private Item pausedBtnFull;
    private Item pausedBtnWin;

    private final Vector3f markerWorld = new Vector3f();
    private final Vector3f markerDelta = new Vector3f();
    private final Vector3f markerScreen = new Vector3f();

    private float time;
    private float hurt;
    private float tired;
    private float manaFlash;
    private float execFlashT;
    private float introT;
    private float deathT = -1f;
    private float victoryT = -1f;
    private boolean paused;
    private boolean fullscreen;

    private float bossBarA = 1f;
    private float bossChipFrac = 1f;
    private float bossChipDelay;
    private float lastBossHp = Boss.HP_MAX;
    private float hpChipFrac = 1f;
    private float hpChipDelay;
    private float lastHp = Player.HP_MAX;

    Hud(AssetManager am, Node guiNode) {
        this.am = am;
        this.gui = guiNode;
        white = UiText.solid(0xFFFFFFFF);
        radial = Tex.radial();
        lockTex = Tex.diamond();
        gui.attachChild(design);

        vignette = quad(radial, 0f, 0f, W, H, new ColorRGBA(0.015f, 0.015f, 0.02f, 0.62f), 0f);
        vignette.setCullHint(Spatial.CullHint.Never);

        for (int i = 0; i < 10; i++) {
            quad(white, 0f, 720f - (i + 1) * 10f, W, 10f,
                    new ColorRGBA(0f, 0f, 0f, 0.32f * (1f - i / 10f)), 3f + i * 0.01f);
        }
        for (int i = 0; i < 9; i++) {
            quad(white, 0f, i * 10f, W, 10f,
                    new ColorRGBA(0f, 0f, 0f, 0.26f * (1f - i / 9f)), 3f + i * 0.01f);
        }

        hurtEdge = quad(radial, 0f, 0f, W, H, new ColorRGBA(0.55f, 0.03f, 0.015f, 0f), 1f);
        hurtFlat = quad(white, 0f, 0f, W, H, new ColorRGBA(0.45f, 0.02f, 0.01f, 0f), 2f);

        buildBossBar();
        buildPlayerBars();
        buildIntro();
        buildDeathAndVictory();
        buildPause();
        buildLockMarker();

        controls = text("WASD 移动 · 轻点 Shift 翻滚 / 按住冲刺 · 空格 跳跃 · 左键 轻击连段 · 右键 重击 · 冲刺+左键 跑攻 · 翻滚后左键 回旋斩 · Ctrl+左键 蓄力/跳劈 · F 战技（长按） · Q/中键 锁定 · P/E 暂停 · F11 全屏",
                13, new Color(196, 190, 176, 150), false, 0f, 32f, 5f);
        controls.geo.setLocalTranslation(W - 44f - controls.geo.getLocalScale().x, 30f, 5f);
    }

    private void buildBossBar() {
        float x = (W - BOSS_BAR_W) * 0.5f;

        bossTitle = textCentered("THE HOLLOW APE", 19, new Color(214, 190, 146, 235), true, 640f, 682f, 4f);
        bossSub = textCentered("顽石猿王 · 石心不坏", 13, new Color(166, 158, 141, 200), false, 640f, 652f, 4f);

        bossBack = new Item(quad(white, x - 4f, 634f, BOSS_BAR_W + 8f, 13f,
                new ColorRGBA(0.015f, 0.012f, 0.012f, 0.72f), 4f), new ColorRGBA(0.015f, 0.012f, 0.012f, 0.72f));
        bossChip = new Item(quad(white, x, 637f, BOSS_BAR_W, 7f, BOSS_CHIP, 4.1f), BOSS_CHIP);
        bossFill = new Item(quad(white, x, 637f, BOSS_BAR_W, 7f, BOSS_FILL, 4.2f), BOSS_FILL);

        Geometry topEdge = quad(white, x, 646f, BOSS_BAR_W, 1f,
                new ColorRGBA(0.55f, 0.48f, 0.34f, 0.5f), 4.3f);
        topEdge.setCullHint(Spatial.CullHint.Never);

        execFlash = textCentered("处  决", 54, new Color(248, 216, 132, 255), true,
                640f, 300f, 9.5f);
        execFlash.visible(false);
    }

    private void buildPlayerBars() {
        float x = 50f;

        hpBack = new Item(quad(white, x, 52f, HP_BAR_W + 6f, 16f,
                new ColorRGBA(0.01f, 0.01f, 0.01f, 0.62f), 4f), new ColorRGBA(0.01f, 0.01f, 0.01f, 0.62f));
        hpChip = new Item(quad(white, x + 3f, 55f, HP_BAR_W, 10f, HP_CHIP, 4.1f), HP_CHIP);
        hpFill = new Item(quad(white, x + 3f, 55f, HP_BAR_W, 10f, HP_FILL, 4.2f), HP_FILL);
        quad(white, x - 3f, 51f, 2.5f, 18f, new ColorRGBA(0.72f, 0.56f, 0.25f, 0.8f), 4.3f)
                .setCullHint(Spatial.CullHint.Never);
        quad(white, x + 3f, 66f, HP_BAR_W, 1f, new ColorRGBA(0.9f, 0.75f, 0.45f, 0.28f), 4.3f)
                .setCullHint(Spatial.CullHint.Never);

        stBack = new Item(quad(white, x, 36f, ST_BAR_W + 6f, 10f,
                new ColorRGBA(0.01f, 0.01f, 0.01f, 0.58f), 4f), new ColorRGBA(0.01f, 0.01f, 0.01f, 0.58f));
        stFill = new Item(quad(white, x + 3f, 38f, ST_BAR_W, 6f, ST_FILL, 4.2f), ST_FILL);
        quad(white, x - 3f, 35f, 2.5f, 12f, new ColorRGBA(0.72f, 0.62f, 0.32f, 0.7f), 4.3f)
                .setCullHint(Spatial.CullHint.Never);

        mpBack = new Item(quad(white, x, 23f, MP_BAR_W + 6f, 10f,
                new ColorRGBA(0.01f, 0.01f, 0.015f, 0.58f), 4f), new ColorRGBA(0.01f, 0.01f, 0.015f, 0.58f));
        mpFill = new Item(quad(white, x + 3f, 25f, MP_BAR_W, 6f, MP_FILL, 4.2f), MP_FILL);
        quad(white, x - 3f, 22f, 2.5f, 12f, new ColorRGBA(0.4f, 0.5f, 0.78f, 0.7f), 4.3f)
                .setCullHint(Spatial.CullHint.Never);
        skillLabel = text("F  烈焰风暴 · 长按持续", 11, new Color(150, 174, 214, 185),
                false, 0f, 24f, 4.3f);
        skillLabel.geo.setLocalTranslation(x + MP_BAR_W + 14f, 24f, 4.3f);

        chargeBar = new Item(quad(white, x, 48f, CHARGE_BAR_W, 4f, CHARGE_FILL, 4.4f), CHARGE_FILL);
        chargeBar.visible(false);
        chargeLabel = text("蓄力 · 松手释放重击", 11, new Color(226, 198, 122, 200),
                false, 0f, 46f, 4.4f);
        chargeLabel.geo.setLocalTranslation(x + CHARGE_BAR_W + 12f, 46f, 4.4f);
        chargeLabel.visible(false);
    }

    private void buildIntro() {
        introTitle = textCentered("艾尔登法猴", 64, new Color(226, 213, 184, 255), false, 640f, 442f, 5f);
        introRule = new Item(quad(white, 640f - 140f, 430f, 280f, 1.5f,
                new ColorRGBA(0.8f, 0.66f, 0.4f, 0.55f), 5f), new ColorRGBA(0.8f, 0.66f, 0.4f, 0.55f));
        introSub = textCentered("ELDEN APE  ·  天命人  ·  顽石猿王", 15,
                new Color(182, 172, 152, 210), true, 640f, 396f, 5f);
    }

    private void buildDeathAndVictory() {
        deathVeil = new Item(quad(white, 0f, 0f, W, H, new ColorRGBA(0f, 0f, 0f, 0.42f), 8f),
                new ColorRGBA(0f, 0f, 0f, 0.42f));
        deathTitle = textCentered("YOU  DIED", 56, new Color(158, 27, 20, 255), true, 640f, 430f, 9f);
        deathSub = textCentered("你死了", 20, new Color(151, 139, 127, 220), false, 640f, 392f, 9f);

        victoryVeil = new Item(quad(white, 0f, 0f, W, H, new ColorRGBA(0.01f, 0.008f, 0.005f, 0.3f), 8f),
                new ColorRGBA(0.01f, 0.008f, 0.005f, 0.3f));
        victoryTitle = textCentered("GREAT  ENEMY  FELLED", 38, new Color(216, 188, 126, 255), true, 640f, 442f, 9f);
        victorySub = textCentered("强敌已灭", 20, new Color(183, 168, 137, 220), false, 640f, 402f, 9f);

        retry = textCentered("按 R 重新挑战", 17, new Color(210, 199, 175, 230), false, 640f, 330f, 9f);
    }

    private void buildPause() {
        pausedVeil = new Item(quad(white, 0f, 0f, W, H, new ColorRGBA(0.008f, 0.008f, 0.01f, 0.7f), 14f),
                new ColorRGBA(0.008f, 0.008f, 0.01f, 0.7f));
        pausedTitle = textCentered("已暂停", 42, new Color(219, 210, 193, 255), false, 640f, 446f, 15f);
        pausedSub = textCentered("PAUSED", 15, new Color(150, 143, 129, 230), true, 640f, 416f, 15f);
        pausedHint = textCentered("按 E 继续游戏", 18, new Color(196, 185, 163, 220), false, 640f, 382f, 15f);

        pausedBtnBg = new Item(quad(white, PAUSE_BTN_X, PAUSE_BTN_Y, PAUSE_BTN_W, PAUSE_BTN_H,
                new ColorRGBA(0.09f, 0.078f, 0.055f, 0.62f), 15.1f),
                new ColorRGBA(0.09f, 0.078f, 0.055f, 0.62f));
        pausedBtnLineA = new Item(quad(white, PAUSE_BTN_X, PAUSE_BTN_Y + PAUSE_BTN_H - 1f,
                PAUSE_BTN_W, 1f, new ColorRGBA(0.82f, 0.68f, 0.4f, 0.62f), 15.2f),
                new ColorRGBA(0.82f, 0.68f, 0.4f, 0.62f));
        pausedBtnLineB = new Item(quad(white, PAUSE_BTN_X, PAUSE_BTN_Y,
                PAUSE_BTN_W, 1f, new ColorRGBA(0.82f, 0.68f, 0.4f, 0.4f), 15.2f),
                new ColorRGBA(0.82f, 0.68f, 0.4f, 0.4f));
        pausedBtnFull = textCentered("全屏显示    F11", 18, new Color(225, 211, 178, 245), false,
                640f, PAUSE_BTN_Y + 17f, 15.3f);
        pausedBtnWin = textCentered("窗口显示    F11", 18, new Color(225, 211, 178, 245), false,
                640f, PAUSE_BTN_Y + 17f, 15.3f);
        pausedBtnWin.visible(false);

        pausedControls = textCentered(
                "WASD 移动 · 轻点 Shift 翻滚 / 按住冲刺 · 空格 跳跃 · 空中左键 跳打 · 冲刺+左键 跑攻 · 翻滚后左键 回旋斩 · Ctrl+左键 蓄力/跳劈 · 右键 重击 · F 战技 · Q/中键 锁定 · R 重新挑战",
                13, new Color(139, 133, 120, 200), false, 640f, 226f, 15f);
    }

    private void buildLockMarker() {
        Item mark = new Item(quad(lockTex, 0f, 0f, 40f, 40f, GOLD, 6f), GOLD);
        mark.visible(false);
        lockMark = mark;
    }

    private Item lockMark;

    void update(float dt, Player player, Target boss, boolean locked, boolean paused, Camera cam) {
        if (cam.getWidth() != hudW || cam.getHeight() != hudH) {
            resize(cam.getWidth(), cam.getHeight());
        }
        this.paused = paused;
        if (!paused) {
            time += dt;
        }
        hurt = MathX.damp(hurt, 0f, 3.4f, dt);

        updateBossBar(dt, boss);
        updatePlayerBars(dt, player);
        updateIntro(dt);
        updateDeath(dt);
        updateVictory(dt);
        updatePause();
        updateLockMarker(dt, player, boss, locked, paused, cam);
        controls.visible(!paused && deathT < 0f && victoryT < 0f);

        execFlashT = Math.max(0f, execFlashT - dt);
        execFlash.visible(execFlashT > 0f);
        if (execFlashT > 0f) {
            execFlash.alpha(MathX.clamp(execFlashT / 0.7f, 0f, 1f));
        }

        float lowHp = player.alive ? MathX.clamp(1f - player.hp / Player.HP_MAX, 0f, 1f) : 1f;
        float edge = MathX.clamp(hurt * 0.9f + lowHp * lowHp * (0.18f + 0.1f * FastMath.sin(time * 4.2f)), 0f, 1f);
        setColor(hurtEdge, new ColorRGBA(0.55f, 0.03f, 0.015f, edge * 0.8f));
        setColor(hurtFlat, new ColorRGBA(0.45f, 0.02f, 0.01f, hurt * 0.13f));
        hurtEdge.setCullHint(edge > 0.004f ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        hurtFlat.setCullHint(hurt > 0.004f ? Spatial.CullHint.Never : Spatial.CullHint.Always);
    }

    private void updateBossBar(float dt, Target boss) {
        float frac = MathX.clamp(boss.hp() / boss.maxHp(), 0f, 1f);

        if (boss.hp() > lastBossHp + 0.01f) {
            bossChipFrac = frac;
            bossChipDelay = 0f;
        } else if (boss.hp() < lastBossHp - 0.01f) {
            bossChipDelay = 0.45f;
        }
        lastBossHp = boss.hp();
        bossChipDelay = Math.max(0f, bossChipDelay - dt);
        if (bossChipDelay <= 0f) {
            bossChipFrac = MathX.damp(bossChipFrac, frac, 2.3f, dt);
        }
        if (bossChipFrac < frac) {
            bossChipFrac = frac;
        }

        bossBarA = MathX.damp(bossBarA, boss.alive ? 1f : 0f, boss.alive ? 8f : 1.1f, dt);
        float x = (W - BOSS_BAR_W) * 0.5f;
        bossFill.geo.setLocalScale(Math.max(0.02f, BOSS_BAR_W * frac), 7f, 1f);
        bossChip.geo.setLocalScale(Math.max(0.02f, BOSS_BAR_W * bossChipFrac), 7f, 1f);
        bossChip.geo.setLocalTranslation(x, 637f, 4.1f);

        setColor(bossFill.geo, boss.enraged()
                ? new ColorRGBA(0.84f, 0.42f, 0.17f, bossBarA)
                : new ColorRGBA(BOSS_FILL.r, BOSS_FILL.g, BOSS_FILL.b, bossBarA));
        bossChip.alpha(bossBarA * 0.8f);
        bossBack.alpha(bossBarA * 0.9f);
        bossTitle.alpha(bossBarA * 0.95f);
        bossSub.alpha(bossBarA * 0.8f);
    }

    private void updatePlayerBars(float dt, Player player) {
        float hpFrac = MathX.clamp(player.hp / Player.HP_MAX, 0f, 1f);
        if (player.hp > lastHp + 0.01f) {
            hpChipFrac = hpFrac;
            hpChipDelay = 0f;
        } else if (player.hp < lastHp - 0.01f) {
            hpChipDelay = 0.38f;
        }
        lastHp = player.hp;
        hpChipDelay = Math.max(0f, hpChipDelay - dt);
        if (hpChipDelay <= 0f) {
            hpChipFrac = MathX.damp(hpChipFrac, hpFrac, 2.6f, dt);
        }
        if (hpChipFrac < hpFrac) {
            hpChipFrac = hpFrac;
        }

        float stFrac = MathX.clamp(player.stamina / Player.ST_MAX, 0f, 1f);
        float mpFrac = MathX.clamp(player.mana / Player.MP_MAX, 0f, 1f);
        hpFill.geo.setLocalScale(Math.max(0.02f, HP_BAR_W * hpFrac), 10f, 1f);
        hpChip.geo.setLocalScale(Math.max(0.02f, HP_BAR_W * hpChipFrac), 10f, 1f);
        stFill.geo.setLocalScale(Math.max(0.02f, ST_BAR_W * stFrac), 6f, 1f);
        mpFill.geo.setLocalScale(Math.max(0.02f, MP_BAR_W * mpFrac), 6f, 1f);

        float pulse = 0.82f + 0.18f * FastMath.sin(time * (hpFrac < 0.3f ? 7.5f : 2.2f));
        float hpAlpha = hpFrac < 0.3f ? pulse : 1f;
        setColor(hpFill.geo, new ColorRGBA(HP_FILL.r, HP_FILL.g, HP_FILL.b, hpAlpha));
        setColor(stFill.geo, stFrac < 0.2f || tired > 0f
                ? new ColorRGBA(0.76f, 0.3f, 0.12f, 0.95f)
                : new ColorRGBA(ST_FILL.r, ST_FILL.g, ST_FILL.b, 0.95f));
        boolean denied = manaFlash > 0f;
        setColor(mpFill.geo, denied
                ? new ColorRGBA(0.85f, 0.22f, 0.18f, 0.95f)
                : new ColorRGBA(MP_FILL.r, MP_FILL.g, MP_FILL.b, 0.95f));
        tired = Math.max(0f, tired - dt);
        manaFlash = Math.max(0f, manaFlash - dt);

        boolean channel = player.state == Player.State.SKILL;
        skillLabel.alpha(channel
                ? 0.7f + 0.3f * FastMath.sin(time * 12f)
                : (player.mana < 18f ? 0.35f : 0.85f));
        boolean charging = player.state == Player.State.CHARGE;
        chargeBar.visible(charging);
        chargeLabel.visible(charging);
        if (charging) {
            float k = MathX.clamp(player.chargeFraction(), 0f, 1f);
            chargeBar.geo.setLocalScale(Math.max(0.02f, CHARGE_BAR_W * k), 4f, 1f);
            float ready = k >= 1f ? 0.55f + 0.45f * FastMath.sin(time * 24f) : 1f;
            setColor(chargeBar.geo, new ColorRGBA(0.95f, 0.62f + 0.26f * ready,
                    0.22f + 0.3f * k, 0.95f));
        }
        hpChip.alpha(1f);
        hpBack.alpha(1f);
        stBack.alpha(1f);
        mpBack.alpha(1f);
    }

    private void updateIntro(float dt) {
        introT += dt;
        boolean canShow = deathT < 0f && victoryT < 0f;
        float a = 0f;
        if (canShow) {
            if (introT < 0.7f) {
                a = MathX.smooth(introT / 0.7f);
            } else if (introT < 3.2f) {
                a = 1f;
            } else if (introT < 4.4f) {
                a = 1f - MathX.smooth((introT - 3.2f) / 1.2f);
            }
        }
        introTitle.alpha(a);
        introRule.alpha(a * 0.9f);
        introSub.alpha(a * 0.85f);
        introTitle.visible(a > 0.01f);
        introRule.visible(a > 0.01f);
        introSub.visible(a > 0.01f);
    }

    private void updateDeath(float dt) {
        if (deathT < 0f) {
            deathVeil.visible(false);
            deathTitle.visible(false);
            deathSub.visible(false);
            return;
        }
        deathT += dt;
        float e = MathX.smooth(MathX.clamp(deathT / 1.25f, 0f, 1f));
        deathVeil.alpha(e * 0.8f);
        deathTitle.alpha(e);
        deathSub.alpha(e * 0.9f);
        deathVeil.visible(true);
        deathTitle.visible(true);
        deathSub.visible(true);
        updateRetry();
    }

    private void updateVictory(float dt) {
        if (victoryT < 0f) {
            victoryVeil.visible(false);
            victoryTitle.visible(false);
            victorySub.visible(false);
            if (deathT < 0f) {
                retry.visible(false);
            }
            return;
        }
        victoryT += dt;
        float e = MathX.smooth(MathX.clamp(victoryT / 1.4f, 0f, 1f));
        victoryVeil.alpha(e * 0.8f);
        victoryTitle.alpha(e);
        victorySub.alpha(e * 0.9f);
        victoryVeil.visible(true);
        victoryTitle.visible(true);
        victorySub.visible(true);
        updateRetry();
    }

    private void updateRetry() {
        float t = deathT >= 0f ? deathT : victoryT;
        float a = MathX.smooth(MathX.clamp((t - 1.9f) / 0.9f, 0f, 1f));
        retry.alpha(a * (0.62f + 0.28f * FastMath.sin(time * 2.4f)));
        retry.visible(a > 0.01f);
    }

    private void updatePause() {
        pausedVeil.visible(paused);
        pausedTitle.visible(paused);
        pausedSub.visible(paused);
        pausedHint.visible(paused);
        pausedControls.visible(paused);
        pausedBtnBg.visible(paused);
        pausedBtnLineA.visible(paused);
        pausedBtnLineB.visible(paused);
        pausedBtnFull.visible(paused && !fullscreen);
        pausedBtnWin.visible(paused && fullscreen);
        pausedVeil.alpha(1f);
        pausedTitle.alpha(1f);
        pausedSub.alpha(1f);
        pausedHint.alpha(0.9f);
        pausedControls.alpha(0.9f);
        pausedBtnBg.alpha(1f);
        pausedBtnLineA.alpha(1f);
        pausedBtnLineB.alpha(1f);
        pausedBtnFull.alpha(1f);
        pausedBtnWin.alpha(1f);
    }

    void setFullscreen(boolean value) {
        fullscreen = value;
    }

    boolean pauseFullscreenHit(float screenX, float screenY) {
        float x = (screenX - hudOffX) / hudScale;
        float y = (screenY - hudOffY) / hudScale;
        return x >= PAUSE_BTN_X && x <= PAUSE_BTN_X + PAUSE_BTN_W
                && y >= PAUSE_BTN_Y && y <= PAUSE_BTN_Y + PAUSE_BTN_H;
    }

    private void resize(int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        hudW = w;
        hudH = h;
        hudScale = Math.min(w / W, h / H);
        hudOffX = (w - W * hudScale) * 0.5f;
        hudOffY = (h - H * hudScale) * 0.5f;
        design.setLocalScale(hudScale);
        design.setLocalTranslation(hudOffX, hudOffY, 0f);
    }

    private void updateLockMarker(float dt, Player player, Target boss, boolean locked,
                                  boolean paused, Camera cam) {
        boolean show = locked && boss.alive && player.alive && !paused;
        if (show) {
            markerWorld.set(boss.pos.x, boss.groundY() + boss.displayHeight(), boss.pos.z);
            markerDelta.set(markerWorld).subtractLocal(cam.getLocation());
            if (cam.getDirection().dot(markerDelta) <= 0f) {
                show = false;
            } else {
                markerScreen.set(cam.getScreenCoordinates(markerWorld));
                markerScreen.x = (markerScreen.x - hudOffX) / hudScale;
                markerScreen.y = (markerScreen.y - hudOffY) / hudScale;
                if (markerScreen.x < -30f || markerScreen.x > W + 30f
                        || markerScreen.y < -30f || markerScreen.y > H + 30f) {
                    show = false;
                } else {
                    lockMark.geo.setLocalTranslation(markerScreen.x - 20f, markerScreen.y - 20f, 6f);
                }
            }
        }
        lockMark.visible(show);
        if (show) {
            lockMark.alpha(0.55f + 0.28f * FastMath.sin(time * 4.4f));
        }
    }

    void showDeath() {
        deathT = 0.0001f;
        victoryT = -1f;
        introT = 99f;
    }

    void showVictory() {
        victoryT = 0.0001f;
        deathT = -1f;
        introT = 99f;
    }

    void resetRun() {
        deathT = -1f;
        victoryT = -1f;
        introT = 99f;
        hurt = 0f;
        tired = 0f;
        manaFlash = 0f;
        execFlashT = 0f;
        lastBossHp = Boss.HP_MAX;
        bossChipFrac = 1f;
        bossChipDelay = 0f;
        lastHp = Player.HP_MAX;
        hpChipFrac = 1f;
        hpChipDelay = 0f;
        bossBarA = 1f;
        setPaused(false);
        deathVeil.visible(false);
        deathTitle.visible(false);
        deathSub.visible(false);
        victoryVeil.visible(false);
        victoryTitle.visible(false);
        victorySub.visible(false);
        retry.visible(false);
    }

    void setPaused(boolean value) {
        paused = value;
        updatePause();
    }

    void addHurt(float amount) {
        hurt = MathX.clamp(hurt + amount, 0f, 1f);
    }

    void addTired(float seconds) {
        tired = Math.max(tired, seconds);
    }

    void addManaAlert() {
        manaFlash = 0.55f;
    }

    void addExecuteFlash() {
        execFlashT = 1.15f;
    }

    private void setColor(Geometry g, ColorRGBA color) {
        g.getMaterial().setColor("Color", color);
    }

    private Geometry quad(Texture2D tex, float x, float y, float w, float h,
                          ColorRGBA color, float z) {
        Geometry g = new Geometry("hudQuad", UNIT);
        Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setTexture("ColorMap", tex);
        m.setColor("Color", color);
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        m.getAdditionalRenderState().setDepthTest(false);
        m.getAdditionalRenderState().setDepthWrite(false);
        g.setMaterial(m);
        g.setQueueBucket(RenderQueue.Bucket.Gui);
        g.setShadowMode(RenderQueue.ShadowMode.Off);
        g.setCullHint(Spatial.CullHint.Never);
        g.setLocalTranslation(x, y, z);
        g.setLocalScale(w, h, 1f);
        design.attachChild(g);
        return g;
    }

    private Item text(String value, int size, Color color, boolean serif,
                      float x, float y, float z) {
        UiText.Made made = UiText.make(value, size, color, serif);
        Geometry g = quad(made.texture(), x, y, made.width(), made.height(),
                new ColorRGBA(1f, 1f, 1f, 1f), z);
        return new Item(g, new ColorRGBA(1f, 1f, 1f, 1f));
    }

    private Item textCentered(String value, int size, Color color, boolean serif,
                              float centerX, float bottomY, float z) {
        UiText.Made made = UiText.make(value, size, color, serif);
        Geometry g = quad(made.texture(), centerX - made.width() * 0.5f, bottomY,
                made.width(), made.height(), new ColorRGBA(1f, 1f, 1f, 1f), z);
        return new Item(g, new ColorRGBA(1f, 1f, 1f, 1f));
    }

    private static Mesh unitQuad() {
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(
                0f, 0f, 0f,
                1f, 0f, 0f,
                1f, 1f, 0f,
                0f, 1f, 0f));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(
                0f, 0f,
                1f, 0f,
                1f, 1f,
                0f, 1f));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createShortBuffer(
                new short[]{0, 1, 2, 0, 2, 3}));
        mesh.setStatic();
        mesh.updateBound();
        return mesh;
    }
}
