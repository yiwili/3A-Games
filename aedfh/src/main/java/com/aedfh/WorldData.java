package com.aedfh;

/**
 * Shared layout for the 1:1 Limgrave recreation. X grows east, Z grows south,
 * the sea sits west and south, Stormveil closes the north-west road.
 */
final class WorldData {

    static final float SIZE = 1400f;
    static final float HALF = SIZE * 0.5f;
    static final float WATER_Y = 0.55f;
    static final float SEA_FLOOR = -16f;
    /** 涉水深度：水没过这个值才拦人；塘底最深只挖到 1.2m，所以池塘永远进得去。 */
    static final float WADE_DEPTH = 1.35f;

    static final float LAKE_X = 250f;
    static final float LAKE_Z = 115f;
    static final float LAKE_RX = 205f;
    static final float LAKE_RZ = 160f;

    static final float STORM_X = -405f;
    static final float STORM_Z = -455f;

    /** 内陆水塘：椭圆盆地 + 独立水位（水面 = 塘心地形 - 0.55m），depth 是塘心最深水深。 */
    record Pond(String name, float x, float z, float rx, float rz, float depth) {
    }

    static final Pond[] PONDS = {
            new Pond("艾雷水塘", -260f, 150f, 32f, 25f, 1.05f),
            new Pond("关卡沼泽", 20f, 175f, 27f, 22f, 1.15f),
            new Pond("雾林深潭", 320f, 390f, 34f, 27f, 1.10f),
            new Pond("南岸水塘", 60f, 470f, 30f, 23f, 1.00f),
            new Pond("湖东水塘", 450f, 60f, 28f, 22f, 1.10f),
            new Pond("海德南塘", 610f, 120f, 30f, 24f, 1.05f),
            new Pond("山丘水塘", -220f, -470f, 28f, 21f, 1.00f),
            new Pond("龙息水塘", 150f, -140f, 25f, 20f, 1.10f),
    };

    /** 峡谷：到折线的距离做 U 形下切，depth 为最大下切深度，halfWidth 为半宽。 */
    record Gorge(float x1, float z1, float x2, float z2, float depth, float halfWidth) {
    }

    static final Gorge[] GORGES = {
            new Gorge(60f, -120f, 140f, -30f, 22f, 34f),
            new Gorge(330f, 180f, 420f, 260f, 20f, 30f),
            new Gorge(500f, 300f, 590f, 370f, 24f, 32f),
    };

    /** 该点属于哪个塘（椭圆归一化距离 < 1.3 视为水域），没有就返回 null。 */
    static Pond pondAt(float x, float z) {
        for (Pond p : PONDS) {
            float dx = (x - p.x()) / p.rx();
            float dz = (z - p.z()) / p.rz();
            if (dx * dx + dz * dz < 1.69f) {
                return p;
            }
        }
        return null;
    }

    static final Vector3f2 SPAWN = new Vector3f2(0f, 348f);
    static final Vector3f2 MARGIT_GATE = new Vector3f2(-382f, -404f);

    record Vector3f2(float x, float z) {
    }

    record GraceDef(String id, String name, float x, float z, float facing) {
    }

    record Poi(String name, float x, float z, float radius, int kind) {
    }

    static final GraceDef[] GRACES = {
            new GraceDef("first-step", "引导之始", 0f, 330f, 3.14f),
            new GraceDef("church-elleh", "艾雷教堂", -165f, 170f, 2.4f),
            new GraceDef("gatefront", "关卡前方", -95f, 75f, 3.4f),
            new GraceDef("agheel-north", "亚基尔湖北", 180f, -28f, 3.0f),
            new GraceDef("agheel-south", "亚基尔湖南", 205f, 270f, 3.2f),
            new GraceDef("mistwood", "雾林边缘", 355f, 315f, 2.3f),
            new GraceDef("third-church", "玛莉卡第三教堂", 420f, -155f, 3.1f),
            new GraceDef("fort-haight", "海德要塞西方", 520f, 185f, 1.6f),
            new GraceDef("warmaster", "习战者的破屋", 190f, -255f, 3.0f),
            new GraceDef("artist", "画家的破屋", -370f, -110f, 1.4f),
            new GraceDef("seaside", "海边遗迹", -445f, 250f, 2.0f),
            new GraceDef("stormhill-shack", "风暴山丘的破屋", -220f, -285f, 3.1f),
            new GraceDef("stormgate", "风暴关卡", -345f, -350f, 3.9f),
            new GraceDef("siofra", "希芙拉河井", 500f, -30f, 3.0f),
            new GraceDef("stormveil-hall", "史东薇尔城·主殿", -405f, -556f, 3.14f),
            // 开局洞窟出口的篝火：打死顽石猿王后点亮（见 WorldApp.onCaveCleared）
            new GraceDef("cave-exit", "求学洞窟", 0f, 335f, 3.14f)
    };

    /**
     * kind: 0 ruin, 1 camp, 2 shack, 3 church, 4 evergaol, 5 well,
     * 6/7 城堡, 8 stone circle, 9 mausoleum, 10 colossus, 11 broken bridge.
     * 0..7 会把周围地形压平；8 以后自带地貌关系，不压平台。
     */
    static final Poi[] POIS = {
            new Poi("关卡前方废墟", -95f, 70f, 34f, 0),
            new Poi("风暴关卡", -345f, -350f, 26f, 0),
            new Poi("亚基尔湖", 250f, 115f, 0f, -1),
            new Poi("龙息废墟", 255f, -60f, 34f, 0),
            new Poi("召唤水村", 390f, -330f, 38f, 0),
            new Poi("习战者的破屋", 190f, -255f, 14f, 2),
            new Poi("雾林", 400f, 350f, 150f, -1),
            new Poi("小黄金树", 540f, 410f, 60f, -1),
            new Poi("海德要塞", 560f, 200f, 42f, 0),
            new Poi("艾雷教堂", -165f, 170f, 20f, 3),
            new Poi("画家的破屋", -370f, -110f, 13f, 2),
            new Poi("海边遗迹", -445f, 250f, 30f, 0),
            new Poi("无主猎犬的封印监牢", 120f, 420f, 26f, 4),
            new Poi("希芙拉河井", 500f, -30f, 22f, 5),
            new Poi("玛莉卡第三教堂", 420f, -155f, 22f, 3),
            new Poi("风暴山丘的破屋", -220f, -285f, 14f, 2),
            new Poi("史东薇尔城", -405f, -455f, 150f, 6),
            new Poi("史东薇尔城主殿", -405f, -588f, 34f, 7),
            new Poi("风蚀石阵", -110f, -500f, 30f, 8),
            new Poi("英雄陵墓", 585f, -265f, 26f, 9),
            new Poi("坠落巨像", -60f, -335f, 34f, 10),
            new Poi("断桥遗迹", 100f, -75f, 44f, 11)
    };

    static final float[][] ROADS = {
            {0f, 430f, 0f, 330f, 10f, 280f, 0f, 220f, -30f, 165f, -75f, 110f, -95f, 70f,
                    -135f, 25f, -185f, -60f, -240f, -145f, -280f, -230f, -320f, -310f,
                    -345f, -350f, -368f, -390f, -390f, -432f},
            {-95f, 70f, -20f, 55f, 80f, 40f, 160f, 30f, 220f, 5f, 255f, -30f, 260f, -90f,
                    230f, -150f, 230f, -210f, 190f, -255f},
            {-20f, 55f, 80f, 95f, 180f, 130f, 280f, 150f, 380f, 170f, 470f, 180f,
                    540f, 200f},
            {180f, 130f, 240f, 200f, 300f, 260f, 355f, 315f, 400f, 360f, 450f, 395f,
                    530f, 410f},
            {-135f, 25f, -220f, 55f, -300f, 90f, -360f, 130f, -410f, 180f, -445f, 250f,
                    -460f, 300f},
            {0f, 330f, 50f, 380f, 90f, 420f, 120f, 440f},
            {-220f, 55f, -280f, 0f, -340f, -60f, -370f, -110f},
            {-240f, -145f, -220f, -220f, -220f, -285f},
            {470f, 180f, 530f, 200f}
    };

    private WorldData() {
    }

    static float roadDistance(float x, float z) {
        float best = 1e9f;
        for (float[] road : ROADS) {
            for (int i = 0; i + 3 < road.length; i += 2) {
                best = Math.min(best, segmentDistance(x, z, road[i], road[i + 1],
                        road[i + 2], road[i + 3]));
            }
        }
        return best;
    }

    static float segmentDistance(float px, float pz, float ax, float az, float bx, float bz) {
        float vx = bx - ax;
        float vz = bz - az;
        float wx = px - ax;
        float wz = pz - az;
        float len2 = vx * vx + vz * vz;
        float t = len2 < 1e-6f ? 0f : MathX.clamp((wx * vx + wz * vz) / len2, 0f, 1f);
        float dx = px - (ax + vx * t);
        float dz = pz - (az + vz * t);
        return (float) Math.sqrt(dx * dx + dz * dz);
    }
}
