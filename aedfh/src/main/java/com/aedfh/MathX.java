package com.aedfh;

import com.jme3.math.Vector3f;

final class MathX {

    private MathX() {
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp(t, 0f, 1f);
    }

    static float damp(float current, float target, float lambda, float dt) {
        return lerp(current, target, 1f - (float) Math.exp(-lambda * dt));
    }

    static float smooth(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    static float wrap(float angle) {
        return (float) Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    static float lerpAngle(float a, float b, float t) {
        return a + wrap(b - a) * clamp(t, 0f, 1f);
    }

    static float yawTo(Vector3f from, Vector3f to) {
        return (float) Math.atan2(to.x - from.x, to.z - from.z);
    }

    static float distXZ(Vector3f a, Vector3f b) {
        float dx = a.x - b.x;
        float dz = a.z - b.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    static float angleBetween(float yaw, Vector3f from, Vector3f to) {
        return Math.abs(wrap(yawTo(from, to) - yaw));
    }

    static Vector3f forward(float yaw) {
        return new Vector3f((float) Math.sin(yaw), 0f, (float) Math.cos(yaw));
    }

    static Vector3f approach(Vector3f pos, Vector3f target, float step) {
        Vector3f delta = target.subtract(pos);
        float length = delta.length();
        if (length <= step || length < 1e-5f) {
            return target.clone();
        }
        return pos.add(delta.multLocal(step / length));
    }
}
