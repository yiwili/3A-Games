package com.aedfh;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** Persistent Tarnished bookkeeping. Three slots, plain properties files. */
final class SaveData {

    static final int STAT_COUNT = 8;
    static final String[] STAT_NAMES = {"生命力", "集中力", "耐力", "力气", "灵巧", "智力", "信仰", "感应"};
    static final String[] STAT_KEYS = {"vigor", "mind", "endurance", "strength",
            "dexterity", "intelligence", "faith", "arcane"};

    int slot;
    String name = "褪色者";
    int[] stats = {12, 10, 11, 12, 12, 9, 8, 8};
    int level;
    int runes;
    int lostRunes;
    float lostX;
    float lostZ;
    boolean lostAlive;
    int flaskCrimson = 4;
    int flaskCerulean = 2;
    int flaskCrimsonMax = 4;
    int flaskCeruleanMax = 2;
    /** 道具快栏当前选中格：0 红露滴 / 1 蓝露滴 / 2 筋斗云铃。 */
    int quickSlot;
    float x = WorldData.SPAWN.x();
    float z = WorldData.SPAWN.z();
    float yaw = 3.14f;
    float hour = 9f;
    float playSeconds;
    String lastGrace = "first-step";
    boolean mountUnlocked = true;
    boolean tutorialCleared;
    boolean margitCleared;
    boolean godrickCleared;
    boolean endingReached;
    final Set<String> graces = new LinkedHashSet<>();
    final Set<String> bosses = new LinkedHashSet<>();
    final Set<String> landmarks = new LinkedHashSet<>();

    SaveData(int slot) {
        this.slot = slot;
        recalcLevel();
    }

    int maxHp() {
        return Math.round(100f + Math.max(0, stats[0] - 10) * 15.5f);
    }

    int maxFp() {
        return Math.round(78f + Math.max(0, stats[1] - 10) * 7.5f);
    }

    int maxStamina() {
        return Math.round(92f + Math.max(0, stats[2] - 10) * 5.4f);
    }

    float damageMul() {
        return 1f + Math.max(0, stats[3] - 10) * 0.038f
                + Math.max(0, stats[4] - 10) * 0.027f;
    }

    int nextLevelCost() {
        return 120 + level * level * 8 + level * 30;
    }

    void recalcLevel() {
        int sum = 0;
        for (int s : stats) {
            sum += s;
        }
        level = sum - 70;
    }

    boolean levelUp() {
        int cost = nextLevelCost();
        if (runes < cost) {
            return false;
        }
        runes -= cost;
        stats[0]++;
        recalcLevel();
        return true;
    }

    void applyTo(Player player) {
        player.applyStats(maxHp(), maxStamina(), maxFp(), damageMul());
        player.hp = player.hpMax;
        player.stamina = player.stMax;
        player.mana = player.mpMax;
    }

    // ---------------------------------------------------------------- files

    static File dir() {
        String override = System.getProperty("eldenmonkey.saves");
        if (override != null && !override.isBlank()) {
            File dir = new File(override);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            return dir;
        }
        String appData = System.getenv("APPDATA");
        File base = appData != null && !appData.isBlank()
                ? new File(appData, "EldenMonkey") : new File(System.getProperty("user.home"), ".eldenmonkey");
        File dir = new File(base, "saves");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    static File file(int slot) {
        return new File(dir(), "slot" + slot + ".properties");
    }

    static boolean exists(int slot) {
        return file(slot).isFile();
    }

    static SaveData load(int slot) {
        File f = file(slot);
        if (!f.isFile()) {
            return null;
        }
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (IOException ex) {
            System.out.println("[Save] load failed: " + ex.getMessage());
            return null;
        }
        SaveData d = new SaveData(slot);
        d.name = p.getProperty("name", d.name);
        d.level = Integer.parseInt(p.getProperty("level", "" + d.level));
        d.runes = Integer.parseInt(p.getProperty("runes", "0"));
        d.lostRunes = Integer.parseInt(p.getProperty("lostRunes", "0"));
        d.lostX = Float.parseFloat(p.getProperty("lostX", "0"));
        d.lostZ = Float.parseFloat(p.getProperty("lostZ", "0"));
        d.lostAlive = Boolean.parseBoolean(p.getProperty("lostAlive", "false"));
        d.flaskCrimson = Integer.parseInt(p.getProperty("flaskCrimson", "4"));
        d.flaskCerulean = Integer.parseInt(p.getProperty("flaskCerulean", "2"));
        d.flaskCrimsonMax = Integer.parseInt(p.getProperty("flaskCrimsonMax", "4"));
        d.flaskCeruleanMax = Integer.parseInt(p.getProperty("flaskCeruleanMax", "2"));
        d.quickSlot = Integer.parseInt(p.getProperty("quickSlot", "0"));
        d.x = Float.parseFloat(p.getProperty("x", "" + WorldData.SPAWN.x()));
        d.z = Float.parseFloat(p.getProperty("z", "" + WorldData.SPAWN.z()));
        d.yaw = Float.parseFloat(p.getProperty("yaw", "3.14"));
        d.hour = Float.parseFloat(p.getProperty("hour", "9"));
        d.playSeconds = Float.parseFloat(p.getProperty("playSeconds", "0"));
        d.lastGrace = p.getProperty("lastGrace", d.lastGrace);
        d.mountUnlocked = Boolean.parseBoolean(p.getProperty("mountUnlocked", "true"));
        d.tutorialCleared = Boolean.parseBoolean(p.getProperty("tutorialCleared", "false"));
        d.margitCleared = Boolean.parseBoolean(p.getProperty("margitCleared", "false"));
        d.godrickCleared = Boolean.parseBoolean(p.getProperty("godrickCleared", "false"));
        d.endingReached = Boolean.parseBoolean(p.getProperty("endingReached", "false"));
        for (String key : STAT_KEYS) {
            int index = indexOf(STAT_KEYS, key);
            d.stats[index] = Integer.parseInt(p.getProperty(key, "" + d.stats[index]));
        }
        splitInto(p.getProperty("graces", ""), d.graces);
        splitInto(p.getProperty("bosses", ""), d.bosses);
        splitInto(p.getProperty("landmarks", ""), d.landmarks);
        d.recalcLevel();
        return d;
    }

    void save() {
        Properties p = new Properties();
        p.setProperty("version", "1");
        p.setProperty("name", name);
        p.setProperty("level", "" + level);
        p.setProperty("runes", "" + runes);
        p.setProperty("lostRunes", "" + lostRunes);
        p.setProperty("lostX", "" + lostX);
        p.setProperty("lostZ", "" + lostZ);
        p.setProperty("lostAlive", "" + lostAlive);
        p.setProperty("flaskCrimson", "" + flaskCrimson);
        p.setProperty("flaskCerulean", "" + flaskCerulean);
        p.setProperty("flaskCrimsonMax", "" + flaskCrimsonMax);
        p.setProperty("flaskCeruleanMax", "" + flaskCeruleanMax);
        p.setProperty("quickSlot", "" + quickSlot);
        p.setProperty("x", "" + x);
        p.setProperty("z", "" + z);
        p.setProperty("yaw", "" + yaw);
        p.setProperty("hour", "" + hour);
        p.setProperty("playSeconds", "" + playSeconds);
        p.setProperty("lastGrace", lastGrace);
        p.setProperty("mountUnlocked", "" + mountUnlocked);
        p.setProperty("tutorialCleared", "" + tutorialCleared);
        p.setProperty("margitCleared", "" + margitCleared);
        p.setProperty("godrickCleared", "" + godrickCleared);
        p.setProperty("endingReached", "" + endingReached);
        for (int i = 0; i < STAT_COUNT; i++) {
            p.setProperty(STAT_KEYS[i], "" + stats[i]);
        }
        p.setProperty("graces", join(graces));
        p.setProperty("bosses", join(bosses));
        p.setProperty("landmarks", join(landmarks));
        try (FileOutputStream out = new FileOutputStream(file(slot))) {
            p.store(out, "Elden Monkey - Limgrave save");
        } catch (IOException ex) {
            System.out.println("[Save] write failed: " + ex.getMessage());
        }
    }

    static void delete(int slot) {
        File f = file(slot);
        if (f.isFile()) {
            f.delete();
        }
    }

    static List<SaveData> list() {
        List<SaveData> out = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            out.add(exists(i) ? load(i) : null);
        }
        return out;
    }

    private static String join(Set<String> set) {
        return String.join(",", set);
    }

    private static void splitInto(String value, Set<String> set) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String part : value.split(",")) {
            if (!part.isBlank()) {
                set.add(part.trim());
            }
        }
    }

    private static int indexOf(String[] arr, String key) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(key)) {
                return i;
            }
        }
        return 0;
    }
}
