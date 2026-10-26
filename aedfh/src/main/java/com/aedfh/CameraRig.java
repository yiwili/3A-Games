package com.aedfh;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

/**
 * Over-the-shoulder orbit camera. Mouse deltas drive yaw/pitch; the rig eases
 * toward the boss while locked on and takes over framing on death/victory.
 */
final class CameraRig {

    private static final float SENS = 0.0023f;
    private static final float PITCH_MIN = -0.2f;
    private static final float PITCH_MAX = 1.02f;
    private static final float LOCK_PITCH_MIN = 0.06f;
    private static final float LOCK_PITCH_MAX = 0.36f;
    private static final float DIST = 4.55f;
    private static final float LOCK_DIST = 5.65f;
    private static final float LOOK_AHEAD = 2.65f;
    private static final float LOCK_LOOK_AHEAD = 1.95f;
    private static final float EYE_MIN_Y = 0.6f;

    private final Camera cam;

    private float yaw;
    private float pitch = 0.18f;
    private float distance = DIST;
    private float shake;
    private float shakeTime;
    private float deathBlend;
    private float victoryBlend;

    private final Vector3f focus = new Vector3f(0f, 1.55f, 0f);
    private final Vector3f eye = new Vector3f();
    private final Vector3f look = new Vector3f();
    private final Vector3f fwd = new Vector3f();
    private final Vector3f right = new Vector3f();
    private final Vector3f target = new Vector3f();
    private final Vector3f tmp = new Vector3f();
    private java.util.function.BiFunction<Float, Float, Float> ground;

    CameraRig(Camera cam) {
        this.cam = cam;
    }

    void setGroundSampler(java.util.function.BiFunction<Float, Float, Float> sampler) {
        ground = sampler;
    }

    float yaw() {
        return yaw;
    }

    float pitch() {
        return pitch;
    }

    /** jME reports mouse Y positive upward, so raising the mouse drops the camera. */
    void mouseMoved(float dx, float dy) {
        yaw -= dx * SENS;
        pitch = MathX.clamp(pitch - dy * SENS, PITCH_MIN, PITCH_MAX);
    }

    void addShake(float amount) {
        shake = Math.min(1.5f, shake + amount);
    }

    void setYaw(float value) {
        yaw = MathX.wrap(value);
    }

    void reset(float playerX, float playerZ, float facing) {
        yaw = facing;
        pitch = 0.18f;
        distance = DIST;
        shake = 0f;
        deathBlend = 0f;
        victoryBlend = 0f;
        focus.set(playerX, 1.55f, playerZ);
        updateEye(0.5f, LOOK_AHEAD, 0f);
    }

    private void updateEye(float shoulder, float lookAhead, float aimUp) {
        fwd.set(FastMath.sin(yaw), 0f, FastMath.cos(yaw));
        right.set(-FastMath.cos(yaw), 0f, FastMath.sin(yaw));
        float horiz = distance * FastMath.cos(pitch);
        float vertical = distance * FastMath.sin(pitch);

        eye.set(focus);
        eye.x += -fwd.x * horiz + right.x * shoulder;
        eye.z += -fwd.z * horiz + right.z * shoulder;
        eye.y += vertical;
        float floor = ground != null
                ? ground.apply(eye.x, eye.z) + 0.55f : EYE_MIN_Y;
        if (eye.y < floor) {
            eye.y = floor;
        }

        look.set(focus);
        look.x += fwd.x * lookAhead + right.x * shoulder * 0.35f;
        look.z += fwd.z * lookAhead + right.z * shoulder * 0.35f;
        look.y += aimUp;
    }

    void update(float dt, Player player, Target boss, boolean locked,
                boolean paused, boolean victory) {
        if (paused) {
            return;
        }
        shakeTime += dt;
        float lookAhead = LOOK_AHEAD;
        float aimUp = 0f;

        boolean dead = !player.alive;
        boolean exec = player.state == Player.State.EXECUTE;
        deathBlend = MathX.damp(deathBlend, dead ? 1f : 0f, 3.4f, dt);
        victoryBlend = MathX.damp(victoryBlend, victory ? 1f : 0f, 2.2f, dt);

        if (victory) {
            target.set(boss.pos.x, boss.groundY() + 2.1f + 1.1f * victoryBlend, boss.pos.z);
            focus.x = MathX.damp(focus.x, target.x, 2.2f, dt);
            focus.y = MathX.damp(focus.y, target.y, 2.2f, dt);
            focus.z = MathX.damp(focus.z, target.z, 2.2f, dt);
            yaw += 0.16f * dt * victoryBlend;
            pitch = MathX.damp(pitch, 0.16f, 1.6f, dt);
        } else if (dead) {
            target.set(player.pos.x, player.worldY() + 1.0f, player.pos.z);
            focus.x = MathX.damp(focus.x, target.x, 2.6f, dt);
            focus.y = MathX.damp(focus.y, target.y, 2.6f, dt);
            focus.z = MathX.damp(focus.z, target.z, 2.6f, dt);
            pitch = MathX.damp(pitch, 0.5f, 1.8f, dt);
        } else {
            target.set(player.pos.x, player.worldY() + 1.52f, player.pos.z);
            if (locked && boss.alive) {
                float d = MathX.distXZ(player.pos, boss.pos);
                lookAhead = LOCK_LOOK_AHEAD;
                if (d > 0.6f) {
                    aimUp = MathX.clamp((2.25f - 1.52f) * lookAhead / d, 0f, 0.42f);
                }
                yaw = MathX.lerpAngle(yaw, MathX.yawTo(player.pos, boss.pos),
                        1f - FastMath.exp(-7.5f * dt));
                float band = MathX.clamp(pitch, LOCK_PITCH_MIN, LOCK_PITCH_MAX);
                pitch = MathX.damp(pitch, band, 5.5f, dt);
            }
            focus.x = MathX.damp(focus.x, target.x, 13f, dt);
            focus.y = MathX.damp(focus.y, target.y, 9f, dt);
            focus.z = MathX.damp(focus.z, target.z, 13f, dt);
        }

        float wantDist = victory ? 8.6f : (dead ? 7.1f
                : (exec ? 4.9f : (locked ? LOCK_DIST : DIST)));
        if (exec) {
            lookAhead = 1.7f;
            aimUp = 0.62f;
        }
        distance = MathX.damp(distance, wantDist, victory || dead ? 1.8f : 4.5f, dt);

        float shoulder = exec ? 0.10f : (locked && !dead ? 0.28f : 0.42f);
        updateEye(shoulder, lookAhead, aimUp);

        shake = MathX.damp(shake, 0f, 4.6f, dt);
        if (shake > 0.002f) {
            float amp = shake * shake * 0.55f;
            tmp.set(right).multLocal(
                    (FastMath.sin(shakeTime * 61f) + 0.55f * FastMath.sin(shakeTime * 97f)) * amp);
            tmp.y += (FastMath.sin(shakeTime * 79f) + 0.5f * FastMath.sin(shakeTime * 113f))
                    * amp * 0.7f;
            look.addLocal(tmp);
        }

        cam.setLocation(eye);
        cam.lookAt(look, Vector3f.UNIT_Y);
    }

    void snap(Player player, Target boss, boolean locked) {
        focus.set(player.pos.x, player.worldY() + 1.52f, player.pos.z);
        if (locked && boss.alive) {
            yaw = MathX.yawTo(player.pos, boss.pos);
        }
        distance = locked ? LOCK_DIST : DIST;
        updateEye(locked ? 0.28f : 0.42f, locked ? LOCK_LOOK_AHEAD : LOOK_AHEAD, 0f);
        cam.setLocation(eye);
        cam.lookAt(look, Vector3f.UNIT_Y);
    }
}
