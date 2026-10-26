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
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** All open-world UI: title/saves, HUD, graces, leveling, map and overlays. */
final class WorldHud {

    enum Mode { TITLE, NAME, PLAY, MENU, MAP, DEAD, VICTORY, ENDING }

    static final float W = 1280f;
    static final float H = 720f;

    private static final ColorRGBA GOLD = new ColorRGBA(0.86f, 0.72f, 0.42f, 1f);
    private static final ColorRGBA PALE = new ColorRGBA(0.82f, 0.79f, 0.70f, 1f);
    private static final ColorRGBA ASH = new ColorRGBA(0.60f, 0.57f, 0.50f, 1f);

    /** 道具快栏三格：红露滴 / 蓝露滴 / 筋斗云铃。索引即 SaveData.quickSlot。 */
    static final String[] QUICK_NAMES = {"红露滴圣杯瓶", "蓝露滴圣杯瓶", "筋斗云铃"};
    static final String[] QUICK_SHORT = {"红露滴", "蓝露滴", "筋斗云"};

    static String quickName(int index) {
        return QUICK_NAMES[Math.floorMod(index, QUICK_NAMES.length)];
    }

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

    private final AssetManager am;
    private Node gui;
    private Node design = new Node("worldHudDesign");
    private Texture2D white;
    private Texture2D radial;
    private Texture2D diamond;
    private Texture2D mapTex;
    private final Mesh unit = unitQuad();

    private float hudScale = 1f;
    private float hudOffX;
    private float hudOffY;
    private int hudW = (int) W;
    private int hudH = (int) H;

    private Node titleRoot = new Node("titleRoot");
    private Node nameRoot = new Node("nameRoot");
    private Node playRoot = new Node("playRoot");
    private Node menuRoot = new Node("menuRoot");
    private Node mapRoot = new Node("mapRoot");
    private Node deathRoot = new Node("deathRoot");
    private Node victoryRoot = new Node("victoryRoot");
    private Node endingRoot = new Node("endingRoot");
    /** Toast layer: must sit above menuRoot, otherwise 升级/休息 的反馈会被菜单面板盖住。 */
    private Node toastRoot = new Node("toastRoot");
    private Node fadeRoot = new Node("fadeRoot");

    private Node slotRoot = new Node("titleSlots");
    private Item[] slotBg = new Item[3];
    private Item[] slotLine = new Item[3];
    private Label[] slotHead = new Label[3];
    private Label[] slotInfo = new Label[3];
    private Label[] slotAction = new Label[3];
    private Label titleWarning;
    private Label titleName;

    private Label nameValue;

    private Item hpBack;
    private Item hpFill;
    private Item hpChip;
    private Item fpBack;
    private Item fpFill;
    private Item stBack;
    private Item stFill;
    private Item mountBack;
    private Item mountFill;
    private Label hpValue;
    private Label fpValue;
    private Label stValue;
    private Label mountLabel;
    private Label runeValue;
    private Label levelValue;
    private Label clockValue;
    private Label regionValue;
    private Label quickLabel;
    private Label itemName;
    private Item promptBack;
    private Label promptLabel;
    private Label locationTitle;
    private Label locationSub;
    private Item toastBack;
    private Label toastLabel;
    private Label controls;
    private Item hurtEdge;
    private Item hurtFlat;

    private Item[] flaskBg = new Item[QUICK_NAMES.length];
    private Item[] flaskLine = new Item[QUICK_NAMES.length];
    private Label[] flaskCount = new Label[QUICK_NAMES.length];
    private Label[] flaskName = new Label[QUICK_NAMES.length];

    private Item bossBack;
    private Item bossFill;
    private Item bossChip;
    private Label bossName;
    private Label bossTitle;
    private Item lockMark;

    private Label menuTitle;
    private Label menuSub;
    private Label menuHint;
    private Item menuPanel;
    private Item menuHighlight;
    private Label[] menuRows = new Label[12];
    private Item menuFoot;

    private Node mapBoard = new Node("mapBoard");
    private Item mapHoverBack;
    private Label mapHover;
    private Label mapCounts;
    private Label mapPlayerInfo;
    private Label mapScale;
    private List<MapMark> mapMarks = new ArrayList<>();
    private Item mapPlayer;

    private Label deathTitle;
    private Label deathSub;
    private Label deathTip;
    private Label victoryTitle;
    private Label victoryName;
    private Label victoryRunes;
    private Label victoryTip;

    private Label endingTitle;
    private Label endingSub;
    private final Label[] endingLines = new Label[4];
    private Label endingStats;
    private Label endingTip;
    private Item fade;

    private static final class MapMark {
        final float x;
        final float z;
        final int kind;
        final int index;
        final String name;
        final Item item;

        MapMark(float x, float z, int kind, int index, String name, Item item) {
            this.x = x;
            this.z = z;
            this.kind = kind;
            this.index = index;
            this.name = name;
            this.item = item;
        }
    }

    private static final class Hit {
        final String action;
        final float x;
        final float y;
        final float w;
        final float h;

        Hit(String action, float x, float y, float w, float h) {
            this.action = action;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private List<Hit> hits = new ArrayList<>();

    private float hpChipFrac = 1f;
    private float hpChipDelay;
    private float lastHp = -1f;
    private float bossChipFrac = 1f;
    private float bossChipDelay;
    private float lastBossHp = -1f;
    private float hurt;
    private float hurtPulse;
    private float toastT;
    private String toastText = "";
    private float locT;
    private float time;
    private float promptPulse;
    private String prompt = "";
    private int selectedItem = 0;
    private float mapMarkerHover = -1f;
    private float mouseX = -1f;
    private float mouseY = -1f;
    private Mode mode = Mode.TITLE;
    private float bossAlpha;

    private static final float MAP_X = 112f;
    private static final float MAP_Y = 52f;
    private static final float MAP_W = 660f;
    private static final float MAP_H = 660f;
    private static final float MAP_SCALE = 0.4714f;

    WorldHud(AssetManager am, Node guiNode, Texture2D mapTexture) {
        this.am = am;
        this.gui = guiNode;
        this.mapTex = mapTexture;
        white = UiText.solid(0xFFFFFFFF);
        radial = Tex.radial();
        diamond = Tex.diamond();
        gui.attachChild(design);

        design.attachChild(titleRoot);
        design.attachChild(nameRoot);
        design.attachChild(playRoot);
        design.attachChild(menuRoot);
        design.attachChild(mapRoot);
        design.attachChild(deathRoot);
        design.attachChild(victoryRoot);
        design.attachChild(endingRoot);
        design.attachChild(toastRoot);
        design.attachChild(fadeRoot);
        titleRoot.attachChild(slotRoot);

        titleRoot.attachChild(quad(white, 0f, 0f, W, H,
                new ColorRGBA(0.008f, 0.008f, 0.011f, 0.40f), 10f));
        titleRoot.attachChild(quad(radial, 0f, 200f, W, 520f,
                new ColorRGBA(0.02f, 0.015f, 0.01f, 0.26f), 10.1f));
        Label title = label(titleRoot, "艾尔登法猴", 58, new Color(230, 218, 188, 255),
                false, W * 0.5f, 576f, 12f, true);
        titleRoot.attachChild(quad(white, W * 0.5f - 150f, 562f, 300f, 1.4f,
                new ColorRGBA(0.82f, 0.68f, 0.40f, 0.5f), 11f));
        label(titleRoot, "E L D E N   M O N K E Y", 13, new Color(165, 151, 122, 210),
                true, W * 0.5f, 532f, 12f, true);
        label(titleRoot, "宁姆格福 · LIMGRAVE", 17, new Color(196, 180, 146, 220),
                false, W * 0.5f, 500f, 12f, true);
        label(titleRoot, "褪色者，踏入黄金树的阴影", 14, new Color(146, 138, 120, 180),
                false, W * 0.5f, 466f, 12f, true);

        for (int i = 0; i < 3; i++) {
            float y = 382f - i * 74f;
            slotBg[i] = item(quad(slotRoot, white, 366f, y, 548f, 62f,
                    new ColorRGBA(0.035f, 0.032f, 0.026f, 0.58f), 13f));
            slotLine[i] = item(quad(slotRoot, white, 366f, y + 61f, 548f, 1f,
                    new ColorRGBA(0.70f, 0.58f, 0.34f, 0.35f), 13.2f));
            slotHead[i] = label(slotRoot, "存档 " + (i + 1), 13,
                    new Color(156, 146, 124, 220), false, 386f, y + 38f, 14f, false);
            slotInfo[i] = label(slotRoot, "", 16, new Color(222, 210, 184, 245),
                    false, 520f, y + 33f, 14f, false);
            slotAction[i] = label(slotRoot, "", 12, new Color(170, 160, 140, 200),
                    false, 850f, y + 36f, 14f, true);
        }
        titleName = label(titleRoot, "", 12, new Color(150, 140, 120, 170), false,
                W * 0.5f, 164f, 12f, true);
        titleWarning = label(titleRoot, "", 12, new Color(206, 120, 92, 230), false,
                W * 0.5f, 142f, 12f, true);
        label(titleRoot, "↑↓ 选择   Enter 进入/创建   Delete 删除存档   F11 全屏",
                12, new Color(150, 142, 124, 190), false, W * 0.5f, 100f, 12f, true);
        label(titleRoot, "程序化场景 · 原创美术 · 1:1 尺度", 11,
                new Color(116, 108, 94, 150), false, W - 40f, 26f, 12f, false).geo
                .setLocalTranslation(W - 286f, 26f, 12f);

        nameRoot.attachChild(quad(white, 0f, 0f, W, H,
                new ColorRGBA(0.004f, 0.004f, 0.006f, 0.86f), 20f));
        nameRoot.attachChild(quad(white, 390f, 238f, 500f, 252f,
                new ColorRGBA(0.05f, 0.045f, 0.036f, 0.94f), 21f));
        nameRoot.attachChild(quad(white, 390f, 488f, 500f, 1f,
                new ColorRGBA(0.82f, 0.68f, 0.4f, 0.6f), 22f));
        label(nameRoot, "创造新的褪色者", 26, new Color(226, 214, 186, 250),
                false, W * 0.5f, 438f, 22f, true);
        label(nameRoot, "你的名字将被宁姆格福记住", 12, new Color(148, 138, 118, 200),
                false, W * 0.5f, 406f, 22f, true);
        label(nameRoot, "名字", 12, new Color(160, 150, 128, 200), false,
                430f, 350f, 22f, false);
        nameRoot.attachChild(quad(white, 430f, 306f, 420f, 40f,
                new ColorRGBA(0.015f, 0.014f, 0.012f, 0.86f), 22f));
        nameRoot.attachChild(quad(white, 430f, 306f, 420f, 1f,
                new ColorRGBA(0.82f, 0.68f, 0.4f, 0.55f), 22.1f));
        nameValue = label(nameRoot, "褪色者", 19, new Color(228, 214, 182, 250),
                false, 444f, 318f, 22.2f, false);
        label(nameRoot, "Enter 开始旅程    E 返回    退格 删除", 12,
                new Color(146, 136, 116, 190), false, W * 0.5f, 268f, 22f, true);

        buildPlay();
        buildMenu();
        buildMap();
        buildDeathVictory();
        setMode(Mode.TITLE);
    }

    // ---------------------------------------------------------------- build

    private void buildPlay() {
        for (int i = 0; i < 8; i++) {
            quad(playRoot, white, 0f, 720f - (i + 1) * 12f, W, 12f,
                    new ColorRGBA(0f, 0f, 0f, 0.28f * (1f - i / 8f)), 2f + i * 0.01f);
        }

        hpBack = item(quad(playRoot, white, 46f, 88f, 324f, 18f,
                new ColorRGBA(0.01f, 0.008f, 0.008f, 0.72f), 4f));
        hpChip = item(quad(playRoot, white, 49f, 91f, 318f, 12f,
                new ColorRGBA(0.88f, 0.72f, 0.46f, 0.85f), 4.1f));
        hpFill = item(quad(playRoot, white, 49f, 91f, 318f, 12f,
                new ColorRGBA(0.64f, 0.10f, 0.065f, 1f), 4.2f));
        label(playRoot, "生命", 11, new Color(190, 164, 138, 190), false, 46f, 112f, 4.3f, false);
        hpValue = label(playRoot, "", 11, new Color(214, 198, 166, 210), false,
                96f, 108f, 4.3f, false);

        fpBack = item(quad(playRoot, white, 46f, 70f, 246f, 10f,
                new ColorRGBA(0.008f, 0.008f, 0.012f, 0.68f), 4f));
        fpFill = item(quad(playRoot, white, 48f, 72f, 242f, 6f,
                new ColorRGBA(0.20f, 0.38f, 0.78f, 1f), 4.2f));
        label(playRoot, "专注", 10, new Color(150, 172, 208, 180), false, 46f, 57f, 4.3f, false);
        fpValue = label(playRoot, "", 10, new Color(180, 196, 222, 200), false,
                150f, 57f, 4.3f, false);

        stBack = item(quad(playRoot, white, 46f, 52f, 246f, 10f,
                new ColorRGBA(0.008f, 0.010f, 0.008f, 0.68f), 4f));
        stFill = item(quad(playRoot, white, 48f, 54f, 242f, 6f,
                new ColorRGBA(0.55f, 0.70f, 0.32f, 1f), 4.2f));
        label(playRoot, "耐力", 10, new Color(164, 182, 136, 180), false, 46f, 39f, 4.3f, false);
        stValue = label(playRoot, "", 10, new Color(190, 204, 164, 200), false,
                150f, 39f, 4.3f, false);

        mountBack = item(quad(playRoot, white, 46f, 133f, 222f, 8f,
                new ColorRGBA(0.006f, 0.008f, 0.012f, 0.66f), 4f));
        mountFill = item(quad(playRoot, white, 48f, 135f, 218f, 4f,
                new ColorRGBA(0.66f, 0.82f, 1f, 0.95f), 4.2f));
        mountLabel = label(playRoot, "筋斗云", 10, new Color(174, 196, 226, 210),
                false, 46f, 144f, 4.3f, false);

        runeValue = label(playRoot, "卢恩 0", 15, new Color(218, 199, 156, 230),
                false, 0f, 102f, 4.3f, false);
        levelValue = label(playRoot, "等级 12", 12, new Color(160, 150, 128, 200),
                false, 0f, 80f, 4.3f, false);
        clockValue = label(playRoot, "", 12, new Color(182, 176, 158, 210),
                false, W - 44f, 688f, 4.3f, false);
        regionValue = label(playRoot, "宁姆格福", 13, new Color(198, 184, 152, 220),
                false, 46f, 688f, 4.3f, false);

        for (int i = 0; i < QUICK_NAMES.length; i++) {
            float x = 566f + i * 62f;
            flaskBg[i] = item(quad(playRoot, white, x, 46f, 56f, 66f,
                    new ColorRGBA(0.012f, 0.011f, 0.009f, 0.76f), 4f));
            flaskLine[i] = item(quad(playRoot, white, x, 111f, 56f, 1.4f,
                    new ColorRGBA(0.7f, 0.58f, 0.34f, 0.55f), 4.4f));
            flaskCount[i] = label(playRoot, "0", 17, i == 0
                            ? new Color(238, 154, 128, 245)
                            : i == 1 ? new Color(138, 176, 246, 245)
                            : new Color(226, 200, 132, 245),
                    false, x + 28f, 74f, 4.5f, true);
            flaskName[i] = label(playRoot, QUICK_SHORT[i], 8,
                    new Color(160, 148, 126, 210), false, x + 28f, 54f, 4.5f, true);
        }
        itemName = label(playRoot, "↓ 道具：" + QUICK_NAMES[0] + " · R 使用", 12,
                new Color(214, 196, 158, 225), false, W - 170f, 176f, 4.5f, true);
        quickLabel = label(playRoot, "↑ 魔法：空栏   ← 左手：空   → 右手：如意金箍棒",
                10, new Color(156, 148, 130, 180), false, W - 170f, 152f, 4.5f, true);

        promptBack = item(quad(playRoot, white, W * 0.5f - 170f, 186f, 340f, 34f,
                new ColorRGBA(0.015f, 0.013f, 0.010f, 0.72f), 5f));
        promptBack.visible(false);
        promptLabel = label(playRoot, "", 15, new Color(228, 213, 178, 245),
                false, W * 0.5f, 197f, 5.1f, true);
        promptBack.geo.setLocalTranslation(W * 0.5f - 170f, 186f, 5f);

        locationTitle = label(playRoot, "", 38, new Color(226, 212, 178, 250),
                true, W * 0.5f, 520f, 7f, true);
        locationSub = label(playRoot, "", 13, new Color(184, 170, 140, 220),
                false, W * 0.5f, 494f, 7f, true);

        toastBack = item(quad(toastRoot, white, W * 0.5f - 200f, 404f, 400f, 32f,
                new ColorRGBA(0.012f, 0.011f, 0.009f, 0.62f), 17f));
        toastLabel = label(toastRoot, "", 14, new Color(224, 210, 178, 240),
                false, W * 0.5f, 415f, 17.1f, true);
        toastBack.visible(false);

        controls = label(playRoot,
                "E 互动/返回 · G 地图 · F 战技 · R 使用道具 · ↓ 切快栏 · Q/中键 锁定 · ←→ 武器 · P 菜单",
                11, new Color(140, 134, 120, 175), false, W * 0.5f, 20f, 5f, true);

        hurtEdge = item(quad(playRoot, radial, 0f, 0f, W, H,
                new ColorRGBA(0.55f, 0.03f, 0.015f, 0f), 3f));
        hurtFlat = item(quad(playRoot, white, 0f, 0f, W, H,
                new ColorRGBA(0.45f, 0.02f, 0.01f, 0f), 3.1f));

        bossName = label(playRoot, "", 18, new Color(218, 199, 158, 240),
                true, W * 0.5f, 672f, 6f, true);
        bossTitle = label(playRoot, "", 11, new Color(158, 148, 126, 200),
                true, W * 0.5f, 654f, 6f, true);
        bossBack = item(quad(playRoot, white, 300f, 632f, 680f, 13f,
                new ColorRGBA(0.01f, 0.009f, 0.008f, 0.72f), 6f));
        bossChip = item(quad(playRoot, white, 303f, 635f, 674f, 7f,
                new ColorRGBA(0.9f, 0.8f, 0.58f, 0.9f), 6.1f));
        bossFill = item(quad(playRoot, white, 303f, 635f, 674f, 7f,
                new ColorRGBA(0.78f, 0.58f, 0.24f, 1f), 6.2f));
        lockMark = item(quad(playRoot, diamond, 0f, 0f, 36f, 36f, GOLD, 6.5f));
        lockMark.visible(false);
    }

    private void buildMenu() {
        menuRoot.attachChild(quad(white, 0f, 0f, W, H,
                new ColorRGBA(0.006f, 0.006f, 0.008f, 0.72f), 14f));
        menuPanel = item(quad(menuRoot, white, 300f, 80f, 680f, 560f,
                new ColorRGBA(0.035f, 0.031f, 0.025f, 0.94f), 15f));
        menuRoot.attachChild(quad(white, 300f, 639f, 680f, 1f,
                new ColorRGBA(0.78f, 0.64f, 0.38f, 0.55f), 15.2f));
        menuRoot.attachChild(quad(white, 300f, 80f, 680f, 1f,
                new ColorRGBA(0.78f, 0.64f, 0.38f, 0.3f), 15.2f));
        menuTitle = label(menuRoot, "", 26, new Color(226, 214, 184, 250),
                false, 340f, 574f, 16f, false);
        menuSub = label(menuRoot, "", 12, new Color(158, 148, 126, 210),
                false, 340f, 542f, 16f, false);
        menuHighlight = item(quad(menuRoot, white, 332f, 0f, 616f, 36f,
                new ColorRGBA(0.16f, 0.13f, 0.075f, 0.55f), 15.5f));
        for (int i = 0; i < menuRows.length; i++) {
            menuRows[i] = label(menuRoot, "", 16, new Color(212, 199, 170, 235),
                    false, 354f, 0f, 16f, false);
        }
        menuHint = label(menuRoot, "", 11, new Color(146, 138, 120, 190),
                false, 340f, 104f, 16f, false);
        menuFoot = item(quad(menuRoot, white, 332f, 126f, 616f, 1f,
                new ColorRGBA(0.65f, 0.54f, 0.32f, 0.28f), 15.4f));
    }

    private void buildMap() {
        mapRoot.attachChild(quad(white, 0f, 0f, W, H,
                new ColorRGBA(0.006f, 0.005f, 0.006f, 0.82f), 14f));
        mapRoot.attachChild(quad(white, MAP_X - 5f, MAP_Y - 5f, MAP_W + 10f, MAP_H + 10f,
                new ColorRGBA(0.07f, 0.06f, 0.045f, 0.95f), 15f));
        Geometry map = quad(mapRoot, mapTex, MAP_X, MAP_Y, MAP_W, MAP_H,
                new ColorRGBA(1f, 0.94f, 0.82f, 0.96f), 15.2f);
        map.setQueueBucket(RenderQueue.Bucket.Gui);
        label(mapRoot, "宁姆格福", 22, new Color(226, 212, 178, 245),
                false, MAP_X + 18f, 688f, 16f, false);
        mapScale = label(mapRoot, "1:1 尺度 · 1400 × 1400 m", 11,
                new Color(164, 152, 126, 200), false, MAP_X + 18f, 668f, 16f, false);
        label(mapRoot, "G / E 关闭", 11, new Color(150, 140, 120, 180),
                false, MAP_X + MAP_W - 100f, 668f, 16f, false);

        mapRoot.attachChild(mapBoard);
        mapBoard.setLocalTranslation(MAP_X, MAP_Y, 15.4f);
        for (WorldData.GraceDef g : WorldData.GRACES) {
            addMark(g.x(), g.z(), 0, 0, "赐福 · " + g.name(), radial, 9f);
        }
        for (int i = 0; i < WorldBoss.SPECS.length; i++) {
            WorldBoss.Spec s = WorldBoss.SPECS[i];
            addMark(s.x, s.z, 1, i, s.name, diamond, 12f);
        }
        for (WorldData.Poi poi : WorldData.POIS) {
            if (poi.kind() < 0) {
                continue;
            }
            addMark(poi.x(), poi.z(), 2, 0, poi.name(), white, 4f);
        }
        mapPlayer = item(quad(mapBoard, diamond, 0f, 0f, 11f, 11f,
                new ColorRGBA(1f, 0.96f, 0.82f, 1f), 15.6f));

        float px = 800f;
        mapRoot.attachChild(quad(white, px, MAP_Y, 440f, MAP_H,
                new ColorRGBA(0.03f, 0.027f, 0.022f, 0.94f), 15.1f));
        label(mapRoot, "地图", 24, new Color(222, 208, 176, 245),
                false, px + 26f, 664f, 16f, false);
        label(mapRoot, "LIMGRAVE", 11, new Color(146, 136, 116, 190),
                true, px + 26f, 642f, 16f, false);
        mapRoot.attachChild(quad(white, px + 26f, 622f, 388f, 1f,
                new ColorRGBA(0.7f, 0.58f, 0.34f, 0.4f), 16f));

        mapHoverBack = item(quad(mapRoot, white, px + 22f, 452f, 396f, 132f,
                new ColorRGBA(0.008f, 0.007f, 0.006f, 0.66f), 16f));
        mapHover = label(mapRoot, "将鼠标移到标记上", 13, new Color(192, 180, 154, 220),
                false, px + 40f, 520f, 16.2f, false);
        label(mapRoot, "金色 · 赐福\n红色 · 强敌\n蓝色 · 夜之强敌\n白色 · 地点",
                11, new Color(160, 150, 128, 200), false, px + 40f, 486f, 16.2f, false);
        mapCounts = label(mapRoot, "", 12, new Color(196, 184, 156, 220),
                false, px + 26f, 410f, 16.2f, false);
        mapPlayerInfo = label(mapRoot, "", 12, new Color(170, 162, 142, 210),
                false, px + 26f, 356f, 16.2f, false);
        label(mapRoot, "赐福可休息、升级与传送\n夜色降临时，三位夜之强敌会现身",
                11, new Color(140, 132, 116, 190), false, px + 26f, 290f, 16.2f, false);
        label(mapRoot, "↑↓ 选择赐福  ·  Enter 传送", 11,
                new Color(140, 132, 116, 170), false, px + 26f, 176f, 16.2f, false);
        label(mapRoot, "G / E 关闭地图", 12,
                new Color(200, 186, 156, 220), false, px + 26f, 112f, 16.2f, false);
    }

    private void addMark(float x, float z, int kind, int index, String name,
                         Texture2D tex, float size) {
        float mx = (x + WorldData.HALF) / WorldData.SIZE * MAP_W;
        float my = (WorldData.HALF - z) / WorldData.SIZE * MAP_H;
        ColorRGBA base = kind == 0 ? GOLD
                : kind == 1 && WorldBoss.SPECS[index].nightOnly
                ? new ColorRGBA(0.42f, 0.64f, 1f, 1f)
                : kind == 1 ? new ColorRGBA(0.88f, 0.30f, 0.20f, 1f)
                : new ColorRGBA(0.82f, 0.78f, 0.68f, 1f);
        Geometry g = quad(mapBoard, tex, mx - size * 0.5f, my - size * 0.5f,
                size, size, base, 15.5f);
        g.setQueueBucket(RenderQueue.Bucket.Gui);
        mapMarks.add(new MapMark(x, z, kind, index, name, new Item(g, base)));
    }

    private void buildDeathVictory() {
        deathRoot.attachChild(quad(white, 0f, 0f, W, H,
                new ColorRGBA(0f, 0f, 0f, 0.5f), 30f));
        deathTitle = label(deathRoot, "YOU DIED", 58, new Color(152, 24, 17, 255),
                true, W * 0.5f, 420f, 31f, true);
        deathSub = label(deathRoot, "你死了", 20, new Color(150, 138, 126, 220),
                false, W * 0.5f, 380f, 31f, true);
        deathTip = label(deathRoot, "", 13, new Color(166, 154, 134, 200),
                false, W * 0.5f, 336f, 31f, true);

        victoryRoot.attachChild(quad(white, 0f, 0f, W, H,
                new ColorRGBA(0.012f, 0.009f, 0.005f, 0.36f), 30f));
        victoryTitle = label(victoryRoot, "GREAT ENEMY FELLED", 36,
                new Color(218, 190, 128, 255), true, W * 0.5f, 432f, 31f, true);
        victoryName = label(victoryRoot, "", 19, new Color(206, 190, 154, 240),
                false, W * 0.5f, 392f, 31f, true);
        victoryRunes = label(victoryRoot, "", 15, new Color(190, 174, 140, 220),
                false, W * 0.5f, 360f, 31f, true);
        victoryTip = label(victoryRoot, "强敌已灭", 12, new Color(150, 140, 122, 190),
                false, W * 0.5f, 330f, 31f, true);

        endingRoot.attachChild(quad(white, 0f, 0f, W, H,
                new ColorRGBA(0.006f, 0.006f, 0.009f, 0.94f), 33f));
        endingTitle = label(endingRoot, "宁姆格福 · 落幕", 46,
                new Color(230, 210, 160, 255), true, W * 0.5f, 578f, 34f, true);
        endingSub = label(endingRoot, "LIMGRAVE  ·  THE CHAPTER ENDS", 13,
                new Color(170, 156, 126, 215), false, W * 0.5f, 538f, 34f, true);
        endingRoot.attachChild(quad(white, W * 0.5f - 170f, 516f, 340f, 1.2f,
                new ColorRGBA(0.80f, 0.66f, 0.40f, 0.45f), 34f));
        for (int i = 0; i < endingLines.length; i++) {
            endingLines[i] = label(endingRoot, "", 15,
                    new Color(212, 200, 174, 235), false, W * 0.5f, 452f - i * 32f, 34f, true);
        }
        endingStats = label(endingRoot, "", 14, new Color(186, 170, 136, 220),
                false, W * 0.5f, 250f, 34f, true);
        endingTip = label(endingRoot, "按 E 返回标题", 12,
                new Color(146, 136, 116, 190), false, W * 0.5f, 84f, 34f, true);

        fade = item(quad(fadeRoot, white, 0f, 0f, W, H,
                new ColorRGBA(0f, 0f, 0f, 1f), 32f));
        fade.visible(false);
    }

    // ---------------------------------------------------------------- state

    void setMode(Mode m) {
        mode = m;
        titleRoot.setCullHint(m == Mode.TITLE ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        nameRoot.setCullHint(m == Mode.NAME ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        playRoot.setCullHint(m == Mode.TITLE || m == Mode.NAME || m == Mode.MAP
                || m == Mode.ENDING ? Spatial.CullHint.Always : Spatial.CullHint.Never);
        menuRoot.setCullHint(m == Mode.MENU ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        mapRoot.setCullHint(m == Mode.MAP ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        deathRoot.setCullHint(m == Mode.DEAD ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        victoryRoot.setCullHint(m == Mode.VICTORY ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        endingRoot.setCullHint(m == Mode.ENDING ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        // 提示层在 update() 早退的那些界面里不会被刷掉可见性，这里整层收起防止留残影
        toastRoot.setCullHint(m == Mode.TITLE || m == Mode.NAME || m == Mode.MAP
                || m == Mode.ENDING ? Spatial.CullHint.Always : Spatial.CullHint.Never);
        hits.clear();
        if (m == Mode.TITLE) {
            for (int i = 0; i < 3; i++) {
                hits.add(new Hit("slot" + i, 366f, 382f - i * 74f, 548f, 62f));
            }
        }
    }

    void refreshTitle(List<SaveData> slots, int selected, boolean deleteArmed) {
        for (int i = 0; i < 3; i++) {
            SaveData d = slots.get(i);
            boolean sel = i == selected;
            slotBg[i].geo.getMaterial().setColor("Color", sel
                    ? new ColorRGBA(0.11f, 0.09f, 0.055f, 0.82f)
                    : new ColorRGBA(0.035f, 0.032f, 0.026f, 0.72f));
            slotLine[i].geo.getMaterial().setColor("Color", sel
                    ? new ColorRGBA(0.9f, 0.74f, 0.44f, 0.7f)
                    : new ColorRGBA(0.7f, 0.58f, 0.34f, 0.25f));
            slotHead[i].setText("存档 " + (i + 1)
                    + (d == null ? "   空" : "   等级 " + d.level));
            if (d == null) {
                slotInfo[i].setText("空存档");
                slotAction[i].setText(sel ? "ENTER 创建褪色者" : "");
            } else {
                slotInfo[i].setText(d.name);
                String where = nearestGrace(d.x, d.z);
                slotAction[i].setText(where + "  ·  " + formatPlay(d.playSeconds));
            }
        }
        titleName.setText("存档目录：" + SaveData.dir().getAbsolutePath());
        titleWarning.setText(deleteArmed
                ? "再按一次 Delete 永久删除该存档" : "");
    }

    void setName(String value) {
        nameValue.setText(value.isEmpty() ? "褪色者" : value);
    }

    void setSelectedItem(int index) {
        selectedItem = Math.floorMod(index, QUICK_NAMES.length);
        itemName.setText("↓ 道具：" + QUICK_NAMES[selectedItem] + " · R 使用");
    }

    void showMenu(String title, String subtitle, String[] options, int selected, String hint) {
        menuTitle.setText(title);
        menuSub.setText(subtitle);
        for (int i = 0; i < menuRows.length; i++) {
            if (i < options.length) {
                menuRows[i].setText(options[i]);
                menuRows[i].visible(true);
            } else {
                menuRows[i].setText("");
                menuRows[i].visible(false);
            }
        }
        menuHint.setText(hint);
        setMode(Mode.MENU);
        for (int i = 0; i < options.length; i++) {
            hits.add(new Hit("menu" + i, 332f, 476f - i * 42f, 616f, 36f));
        }
        setMenuSelection(selected);
    }

    void setMenuSelection(int selected) {
        float y = 476f - selected * 42f;
        menuHighlight.geo.setLocalTranslation(332f, y, 15.5f);
        for (int i = 0; i < menuRows.length; i++) {
            menuRows[i].geo.getMaterial().setColor("Color", i == selected
                    ? new ColorRGBA(1f, 0.86f, 0.52f, 1f)
                    : new ColorRGBA(0.83f, 0.78f, 0.67f, 0.92f));
            menuRows[i].geo.setLocalTranslation(354f + (i == selected ? 10f : 0f),
                    491f - i * 42f, 16f);
        }
    }

    void setMenuSubtitle(String text) {
        menuSub.setText(text);
    }

    String hitTest(float x, float y) {
        float dx = (x - hudOffX) / hudScale;
        float dy = (y - hudOffY) / hudScale;
        for (Hit h : hits) {
            if (dx >= h.x && dx <= h.x + h.w && dy >= h.y && dy <= h.y + h.h) {
                return h.action;
            }
        }
        return null;
    }

    void mouseMoved(float x, float y) {
        mouseX = (x - hudOffX) / hudScale;
        mouseY = (y - hudOffY) / hudScale;
    }

    void addHurt(float amount) {
        hurt = MathX.clamp(hurt + amount, 0f, 1f);
    }

    void toast(String text) {
        toastText = text;
        toastT = 2.8f;
    }

    void clearToast() {
        toastT = 0f;
    }

    /** 测试钩子：还挂在屏幕上的提示文字，没有则空串。 */
    String toastProbe() {
        return toastT > 0f ? toastText : "";
    }

    void setPrompt(String text) {
        prompt = text == null ? "" : text;
    }

    void showLocation(String title, String sub) {
        locationTitle.setText(title);
        locationSub.setText(sub);
        locT = 4.6f;
    }

    void clearLocation() {
        locT = 0f;
    }

    void showDeath(String tip) {
        deathTip.setText(tip);
        setMode(Mode.DEAD);
    }

    void showVictory(String name, int runes) {
        victoryName.setText(name);
        victoryRunes.setText("获得 " + String.format("%,d", runes) + " 卢恩");
        setMode(Mode.VICTORY);
    }

    /** Black curtain used by the ending cinematic. */
    void setFade(float alpha) {
        float a = MathX.clamp(alpha, 0f, 1f);
        fade.alpha(a);
        fade.visible(a > 0.005f);
    }

    void showEnding(String title, String sub, String[] lines, String stats, String tip) {
        endingTitle.setText(title);
        endingSub.setText(sub);
        for (int i = 0; i < endingLines.length; i++) {
            endingLines[i].setText(i < lines.length ? lines[i] : "");
        }
        endingStats.setText(stats);
        endingTip.setText(tip);
        setMode(Mode.ENDING);
    }

    // ---------------------------------------------------------------- update

    void update(float dt, Player player, SaveData save, Target locked,
                WorldBoss boss, Boss caveApe, Camera cam, boolean mounted, float hour) {
        if (cam.getWidth() != hudW || cam.getHeight() != hudH) {
            resize(cam.getWidth(), cam.getHeight());
        }
        time += dt;
        hurt = MathX.damp(hurt, 0f, 3.2f, dt);
        hurtPulse += dt;
        promptPulse += dt;
        locT = Math.max(0f, locT - dt);
        toastT = Math.max(0f, toastT - dt);
        if (mode == Mode.TITLE || mode == Mode.NAME || mode == Mode.MAP) {
            return;
        }
        updateBars(dt, player, save, mounted);
        updateFlaskFrame(player);
        updateClock(save, hour);
        updateBoss(dt, boss, caveApe, player);
        updateLock(cam, locked, player);
        updateOverlays(player);
        if (mode == Mode.PLAY || mode == Mode.MENU) {
            controls.visible(mode == Mode.PLAY);
        }
    }

    private void updateBars(float dt, Player player, SaveData save, boolean mounted) {
        float hf = MathX.clamp(player.hp / Math.max(1f, player.hpMax), 0f, 1f);
        if (player.hp < lastHp - 0.01f) {
            hpChipDelay = 0.5f;
        }
        lastHp = player.hp;
        hpChipDelay = Math.max(0f, hpChipDelay - dt);
        if (hpChipDelay <= 0f) {
            hpChipFrac = MathX.damp(hpChipFrac, hf, 2.6f, dt);
        }
        if (hpChipFrac < hf) {
            hpChipFrac = hf;
        }
        hpFill.geo.setLocalScale(Math.max(0.01f, 318f * hf), 12f, 1f);
        hpChip.geo.setLocalScale(Math.max(0.01f, 318f * hpChipFrac), 12f, 1f);
        hpValue.setText((int) Math.ceil(player.hp) + " / " + (int) player.hpMax);

        float ff = MathX.clamp(player.mana / Math.max(1f, player.mpMax), 0f, 1f);
        fpFill.geo.setLocalScale(Math.max(0.01f, 242f * ff), 6f, 1f);
        fpValue.setText((int) player.mana + " / " + (int) player.mpMax);
        float sf = MathX.clamp(player.stamina / Math.max(1f, player.stMax), 0f, 1f);
        stFill.geo.setLocalScale(Math.max(0.01f, 242f * sf), 6f, 1f);
        stValue.setText((int) player.stamina + " / " + (int) player.stMax);

        mountBack.visible(mounted);
        mountFill.visible(mounted);
        mountLabel.visible(mounted);
        if (mounted) {
            float mf = MathX.clamp(player.mountHp / Math.max(1f, player.mountMaxHp), 0f, 1f);
            mountFill.geo.setLocalScale(Math.max(0.01f, 218f * mf), 4f, 1f);
            mountLabel.setText("筋斗云  " + (int) Math.ceil(player.mountHp));
        }

        runeValue.setText("卢恩 " + String.format("%,d", save.runes));
        runeValue.geo.setLocalTranslation(W - 44f - textWidth(runeValue), 102f, 4.3f);
        levelValue.setText("等级 " + save.level);
        levelValue.geo.setLocalTranslation(W - 44f - textWidth(levelValue), 80f, 4.3f);
        regionValue.setText("宁姆格福 · " + nearestGrace(player.pos.x, player.pos.z));
    }

    private void updateFlaskFrame(Player player) {
        for (int i = 0; i < QUICK_NAMES.length; i++) {
            boolean sel = i == selectedItem;
            flaskBg[i].geo.getMaterial().setColor("Color", sel
                    ? new ColorRGBA(0.15f, 0.115f, 0.06f, 0.9f)
                    : new ColorRGBA(0.012f, 0.011f, 0.009f, 0.76f));
            flaskLine[i].geo.getMaterial().setColor("Color", sel
                    ? new ColorRGBA(0.95f, 0.78f, 0.42f, 0.85f)
                    : new ColorRGBA(0.7f, 0.58f, 0.34f, 0.3f));
        }
    }

    private int saveFlasksCrimson;
    private int saveFlasksCerulean;

    void setFlaskCounts(int crimson, int cerulean) {
        saveFlasksCrimson = crimson;
        saveFlasksCerulean = cerulean;
        flaskCount[0].setText("" + crimson);
        flaskCount[1].setText("" + cerulean);
        // 筋斗云铃是常驻道具，没有次数
        flaskCount[2].setText("∞");
    }

    private void updateClock(SaveData save, float hour) {
        int hh = (int) hour;
        int mm = (int) ((hour - hh) * 60f);
        boolean night = hour >= 20f || hour < 5f;
        int day = (int) (save.playSeconds / 90f / 24f) + 1;
        clockValue.setText((night ? "夜" : "昼") + "  " + String.format("%02d:%02d", hh, mm)
                + "  ·  第 " + day + " 天");
        clockValue.geo.setLocalTranslation(W - 44f - textWidth(clockValue), 688f, 4.3f);
    }

    private void updateBoss(float dt, WorldBoss boss, Boss ape, Player player) {
        if (ape != null) {
            updateBossCore(dt, player, "顽石猿王", "洞窟 · 新手试炼",
                    ape.alive, ape.hp, ape.maxHp(), ape.enraged(), 0f);
            return;
        }
        updateBossCore(dt, player,
                boss == null ? "" : boss.title(),
                boss == null ? "" : boss.displayName(),
                boss != null && boss.alive,
                boss == null ? 0f : boss.hp,
                boss == null ? 1f : boss.maxHp(),
                boss != null && boss.phase2,
                boss != null ? MathX.distXZ(player.pos, boss.pos) : 999f);
    }

    private void updateBossCore(float dt, Player player, String name, String title,
            boolean alive, float hp, float maxHp, boolean enraged, float dist) {
        boolean show = alive && dist <= 55f;
        if (show) {
            bossAlpha = MathX.damp(bossAlpha, 1f, 5f, dt);
        } else {
            bossAlpha = MathX.damp(bossAlpha, 0f, 3.2f, dt);
            if (bossAlpha < 0.02f) {
                bossName.visible(false);
                bossTitle.visible(false);
                bossBack.visible(false);
                bossChip.visible(false);
                bossFill.visible(false);
                return;
            }
        }
        float alpha = bossAlpha;
        bossName.visible(true);
        bossTitle.visible(true);
        bossBack.visible(true);
        bossChip.visible(true);
        bossFill.visible(true);
        bossName.setText(name);
        bossName.geo.getMaterial().setColor("Color", new ColorRGBA(0.85f, 0.78f, 0.62f, alpha));
        bossTitle.setText(title);
        bossTitle.geo.getMaterial().setColor("Color", new ColorRGBA(0.62f, 0.58f, 0.49f, alpha * 0.8f));
        float frac = MathX.clamp(hp / maxHp, 0f, 1f);
        if (hp < lastBossHp - 0.01f) {
            bossChipDelay = 0.5f;
        }
        lastBossHp = hp;
        bossChipDelay = Math.max(0f, bossChipDelay - dt);
        if (bossChipDelay <= 0f) {
            bossChipFrac = MathX.damp(bossChipFrac, frac, 2.2f, dt);
        }
        if (bossChipFrac < frac) {
            bossChipFrac = frac;
        }
        bossFill.geo.setLocalScale(Math.max(0.02f, 674f * frac), 7f, 1f);
        bossChip.geo.setLocalScale(Math.max(0.02f, 674f * bossChipFrac), 7f, 1f);
        bossFill.geo.getMaterial().setColor("Color", (enraged
                ? new ColorRGBA(0.86f, 0.40f, 0.16f, 1f)
                : new ColorRGBA(0.78f, 0.58f, 0.24f, 1f)));
        bossChip.alpha(alpha * 0.85f);
        bossBack.alpha(alpha * 0.9f);
    }

    private void updateLock(Camera cam, Target locked, Player player) {
        boolean show = locked != null && locked.alive;
        if (show) {
            Vector3f world = new Vector3f(locked.pos.x,
                    locked.groundY() + locked.displayHeight() + 0.35f, locked.pos.z);
            Vector3f delta = world.subtract(cam.getLocation());
            if (cam.getDirection().dot(delta) <= 0f) {
                show = false;
            } else {
                Vector3f screen = cam.getScreenCoordinates(world);
                float sx = (screen.x - hudOffX) / hudScale;
                float sy = (screen.y - hudOffY) / hudScale;
                if (sx < -40f || sx > W + 40f || sy < -40f || sy > H + 40f) {
                    show = false;
                } else {
                    lockMark.geo.setLocalTranslation(sx - 18f, sy - 18f, 6.5f);
                }
            }
        }
        lockMark.visible(show);
        if (show) {
            lockMark.alpha(0.55f + 0.3f * FastMath.sin(time * 4.2f));
        }
    }

    private void updateOverlays(Player player) {
        promptBack.visible(!prompt.isEmpty() && mode == Mode.PLAY);
        promptLabel.visible(!prompt.isEmpty() && mode == Mode.PLAY);
        if (!prompt.isEmpty()) {
            promptLabel.setText(prompt);
            float alpha = 0.7f + 0.18f * FastMath.sin(promptPulse * 3.2f);
            promptBack.geo.getMaterial().setColor("Color",
                    new ColorRGBA(0.015f, 0.013f, 0.010f, 0.72f * alpha));
            promptLabel.geo.getMaterial().setColor("Color",
                    new ColorRGBA(0.90f, 0.84f, 0.70f, 0.95f * alpha));
        }
        toastBack.visible(toastT > 0f);
        toastLabel.visible(toastT > 0f);
        if (toastT > 0f) {
            toastLabel.setText(toastText);
            // 菜单界面里挪到面板顶部的空档，别压在选项行上
            float ty = mode == Mode.MENU ? 598f : 404f;
            toastBack.geo.setLocalTranslation(W * 0.5f - 200f, ty, 17f);
            toastLabel.geo.setLocalTranslation(W * 0.5f - textWidth(toastLabel) * 0.5f,
                    ty + 11f, 17.1f);
            float a = MathX.clamp(toastT / 0.5f, 0f, 1f)
                    * MathX.clamp((2.8f - toastT) / 0.25f, 0f, 1f);
            toastBack.alpha(a * 0.9f);
            toastLabel.geo.getMaterial().setColor("Color", new ColorRGBA(0.88f, 0.82f, 0.68f, a));
        }
        float locA = MathX.clamp(locT / 0.8f, 0f, 1f)
                * MathX.clamp((4.6f - locT) / 0.4f, 0f, 1f);
        locationTitle.geo.getMaterial().setColor("Color", new ColorRGBA(0.88f, 0.82f, 0.68f, locA));
        locationSub.geo.getMaterial().setColor("Color", new ColorRGBA(0.70f, 0.65f, 0.55f, locA * 0.85f));

        float lowHp = player.alive ? MathX.clamp(1f - player.hp / Math.max(1f, player.hpMax), 0f, 1f) : 1f;
        float edge = MathX.clamp(hurt * 0.9f + lowHp * lowHp
                * (0.16f + 0.08f * FastMath.sin(time * 4.2f)), 0f, 1f);
        hurtEdge.alpha(edge * 0.75f);
        hurtFlat.alpha(hurt * 0.14f);
        hurtEdge.visible(edge > 0.004f);
        hurtFlat.visible(hurt > 0.004f);
    }

    void updateMap(SaveData save, Player player, boolean showMap) {
        if (!showMap) {
            return;
        }
        for (MapMark m : mapMarks) {
            boolean known;
            if (m.kind == 0) {
                known = save.graces.contains(WorldData.GRACES[m.index].id());
            } else if (m.kind == 1) {
                known = true;
            } else {
                known = save.landmarks.contains(m.name);
            }
            m.item.alpha(known ? 1f : 0.22f);
            if (m.kind == 1) {
                WorldBoss.Spec spec = WorldBoss.SPECS[m.index];
                boolean dead = save.bosses.contains(spec.id);
                m.item.alpha(dead ? 0.22f : 1f);
            }
        }
        float mx = (player.pos.x + WorldData.HALF) / WorldData.SIZE * MAP_W;
        float my = (WorldData.HALF - player.pos.z) / WorldData.SIZE * MAP_H;
        mapPlayer.geo.setLocalTranslation(mx - 5.5f, my - 5.5f, 15.6f);
        mapPlayer.alpha(0.65f + 0.35f * FastMath.sin(time * 3.4f));

        int graceCount = save.graces.size();
        int bossCount = save.bosses.size();
        mapCounts.setText("赐福  " + graceCount + " / " + WorldData.GRACES.length
                + "      强敌  " + bossCount + " / " + WorldBoss.SPECS.length);
        mapPlayerInfo.setText("当前位置  " + nearestGrace(player.pos.x, player.pos.z)
                + "\n等级 " + save.level + "  ·  卢恩 " + String.format("%,d", save.runes));

        int hover = -1;
        float best = 16f;
        for (int i = 0; i < mapMarks.size(); i++) {
            MapMark m = mapMarks.get(i);
            float cx = MAP_X + (m.x + WorldData.HALF) / WorldData.SIZE * MAP_W;
            float cy = MAP_Y + (WorldData.HALF - m.z) / WorldData.SIZE * MAP_H;
            float d = (float) Math.hypot(mouseX - cx, mouseY - cy);
            if (d < best) {
                best = d;
                hover = i;
            }
        }
        if (hover != mapMarkerHover) {
            mapMarkerHover = hover;
            if (hover < 0) {
                mapHover.setText("将鼠标移到标记上");
                mapHoverBack.visible(false);
            } else {
                MapMark m = mapMarks.get(hover);
                String status = "";
                if (m.kind == 0) {
                    status = save.graces.contains(WorldData.GRACES[m.index].id())
                            ? "已发现" : "未发现";
                } else if (m.kind == 1) {
                    WorldBoss.Spec spec = WorldBoss.SPECS[m.index];
                    status = save.bosses.contains(spec.id) ? "已击败"
                            : (spec.nightOnly ? "夜间出没" : "尚未击败");
                } else {
                    status = save.landmarks.contains(m.name) ? "已探明" : "未探明";
                }
                mapHover.setText(m.name + "\n" + status);
                mapHoverBack.visible(true);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private float textWidth(Label label) {
        return label.geo.getLocalScale().x;
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

    private static String nearestGrace(float x, float z) {
        float best = 1e9f;
        String name = "宁姆格福";
        for (WorldData.GraceDef g : WorldData.GRACES) {
            float d = (x - g.x()) * (x - g.x()) + (z - g.z()) * (z - g.z());
            if (d < best) {
                best = d;
                name = g.name();
            }
        }
        return name;
    }

    private static String formatPlay(float seconds) {
        int total = (int) seconds;
        int h = total / 3600;
        int m = (total % 3600) / 60;
        return String.format("%d:%02d", h, m);
    }

    private Item item(Geometry g) {
        return new Item(g, colorOf(g));
    }

    private static ColorRGBA colorOf(Geometry g) {
        var param = g.getMaterial().getParam("Color");
        if (param != null && param.getValue() instanceof ColorRGBA color) {
            return color;
        }
        return ColorRGBA.White;
    }

    private Label label(Node parent, String value, int size, Color color, boolean serif,
                        float x, float y, float z, boolean centered) {
        Label l = new Label(parent, size, color, serif, x, y, z, centered);
        l.setText(value);
        return l;
    }

    private final class Label {
        final Geometry geo;
        final int size;
        final Color color;
        final boolean serif;
        final float x;
        final float y;
        final float z;
        final boolean centered;
        String text = "";

        Label(Node parent, int size, Color color, boolean serif,
              float x, float y, float z, boolean centered) {
            this.size = size;
            this.color = color;
            this.serif = serif;
            this.x = x;
            this.y = y;
            this.z = z;
            this.centered = centered;
            geo = quad(parent, white, x, y, 1f, 1f, ColorRGBA.White, z);
        }

        void setText(String value) {
            if (value.equals(text)) {
                return;
            }
            text = value;
            UiText.Made made = UiText.make(value.isEmpty() ? " " : value, size, color, serif);
            geo.getMaterial().setTexture("ColorMap", made.texture());
            geo.setLocalScale(made.width(), made.height(), 1f);
            geo.setLocalTranslation(centered ? x - made.width() * 0.5f : x, y, z);
        }

        void visible(boolean v) {
            geo.setCullHint(v ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        }
    }

    private Geometry quad(Texture2D tex, float x, float y, float w, float h,
                          ColorRGBA color, float z) {
        Geometry g = quad(design, tex, x, y, w, h, color, z);
        design.detachChild(g);
        return g;
    }

    private Geometry quad(Node parent, Texture2D tex, float x, float y, float w, float h,
                          ColorRGBA color, float z) {
        Geometry g = new Geometry("hudQuad", unit);
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
        parent.attachChild(g);
        return g;
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
