package com.aedfh;

import com.jme3.math.Vector3f;

import java.util.List;

/**
 * 玩家解析碰撞。
 *
 * <p>与 {@link WorldProps} 的相机探针（{@code blocker()} / {@code PropBox}）是<b>两套独立数据</b>：
 * 探针只服务机位（允许贴墙取景、含装饰件与放大余量），这里才是真正的路障。
 * 只做水平（XZ）推出，竖直方向仍由 {@link WorldTerrain#height} 决定——所以上墙、爬坡
 * 依旧由地形高度场说话，碰撞体只负责"别穿进去"。
 */
final class WorldCollision {

    /** 轴对齐盒 + yaw（绕 Y 轴旋转），与 Gfx.box 的半长参数一一对应。 */
    static final class Box {
        final float cx;
        final float cy;
        final float cz;
        final float hx;
        final float hy;
        final float hz;
        final float yaw;
        /** 水平包围半径，用于廉价粗筛。 */
        final float br;

        Box(float cx, float cy, float cz, float hx, float hy, float hz, float yaw) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.hx = hx;
            this.hy = hy;
            this.hz = hz;
            this.yaw = yaw;
            this.br = (float) Math.sqrt(hx * hx + hz * hz);
        }
    }

    /** 竖圆柱：塔楼、树干、井口。 */
    static final class Cyl {
        final float cx;
        final float cy;
        final float cz;
        final float r;
        final float hy;

        Cyl(float cx, float cy, float cz, float r, float hy) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.r = r;
            this.hy = hy;
        }
    }

    /** 低于这个高度的实心体可以跨过去（台阶、碎石、路缘）。 */
    private static final float STEP = 0.35f;
    private static final int PASSES = 3;

    private final List<Box> boxes;
    private final List<Cyl> cyls;
    private final float radius;

    WorldCollision(List<Box> boxes, List<Cyl> cyls, float radius) {
        this.boxes = boxes;
        this.cyls = cyls;
        this.radius = radius;
    }

    int count() {
        return boxes.size() + cyls.size();
    }

    int boxCount() {
        return boxes.size();
    }

    int cylCount() {
        return cyls.size();
    }

    /** 把玩家圆盘推出所有实心体，返回本次是否发生过推出。 */
    boolean resolve(Vector3f pos, float feetY, float bodyH) {
        boolean any = false;
        for (int pass = 0; pass < PASSES; pass++) {
            boolean moved = false;
            for (int i = 0; i < boxes.size(); i++) {
                if (pushBox(boxes.get(i), pos, feetY, bodyH)) {
                    moved = true;
                }
            }
            for (int i = 0; i < cyls.size(); i++) {
                if (pushCyl(cyls.get(i), pos, feetY, bodyH)) {
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
            any = true;
        }
        return any;
    }

    private boolean pushBox(Box b, Vector3f pos, float feetY, float bodyH) {
        float dx = pos.x - b.cx;
        float dz = pos.z - b.cz;
        float reach = b.br + radius;
        if (dx * dx + dz * dz > reach * reach) {
            return false;
        }
        if (!overlapsY(feetY, bodyH, b.cy, b.hy)) {
            return false;
        }
        float c = (float) Math.cos(b.yaw);
        float s = (float) Math.sin(b.yaw);
        float lx = dx * c - dz * s;
        float lz = dx * s + dz * c;
        float qx = Math.max(-b.hx, Math.min(b.hx, lx));
        float qz = Math.max(-b.hz, Math.min(b.hz, lz));
        float ox = lx - qx;
        float oz = lz - qz;
        float d2 = ox * ox + oz * oz;
        if (d2 >= radius * radius) {
            return false;
        }
        float px;
        float pz;
        if (d2 > 1e-6f) {
            float d = (float) Math.sqrt(d2);
            float push = radius - d;
            px = ox / d * push;
            pz = oz / d * push;
        } else {
            // 圆心已在盒内：沿穿透最浅的一侧顶出去
            float penX = b.hx + radius - Math.abs(lx);
            float penZ = b.hz + radius - Math.abs(lz);
            if (penX <= penZ) {
                px = (lx >= 0f ? penX : -penX);
                pz = 0f;
            } else {
                px = 0f;
                pz = (lz >= 0f ? penZ : -penZ);
            }
        }
        // 局部位移转回世界坐标（与上面 +yaw 旋转互逆）
        pos.x += px * c + pz * s;
        pos.z += -px * s + pz * c;
        return true;
    }

    private boolean pushCyl(Cyl c, Vector3f pos, float feetY, float bodyH) {
        float dx = pos.x - c.cx;
        float dz = pos.z - c.cz;
        float reach = c.r + radius;
        float d2 = dx * dx + dz * dz;
        if (d2 >= reach * reach) {
            return false;
        }
        if (!overlapsY(feetY, bodyH, c.cy, c.hy)) {
            return false;
        }
        float d = (float) Math.sqrt(d2);
        if (d < 1e-4f) {
            pos.x = c.cx + reach;
            return true;
        }
        pos.x = c.cx + dx / d * reach;
        pos.z = c.cz + dz / d * reach;
        return true;
    }

    /** 竖直区间是否相交；低于 {@link #STEP} 的可跨越体不算障碍。 */
    private static boolean overlapsY(float feetY, float bodyH, float cy, float hy) {
        return cy + hy > feetY + STEP && cy - hy < feetY + bodyH;
    }
}
