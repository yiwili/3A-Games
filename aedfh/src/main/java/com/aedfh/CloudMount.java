package com.aedfh;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Sphere;

/** 筋斗云: a summoned somersault cloud with Torrent's movement rules. */
final class CloudMount {

    private static final float DASH_TIME = 0.45f;

    private final Node root = new Node("somersaultCloud");
    private final Node puffs = new Node("puffs");
    private final Geometry[] lobes = new Geometry[9];
    private final float[] phase = new float[9];
    private final float[] baseY = new float[9];
    private final Vector3f tmp = new Vector3f();
    private final Fx fx;
    private float time;
    private float summon = 1f;
    private float trailTimer;
    private float dashT;
    private boolean visible;

    CloudMount(AssetManager am, Node parent, Fx fx) {
        this.fx = fx;
        parent.attachChild(root);
        Material cloud = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        cloud.setColor("Color", new ColorRGBA(0.86f, 0.92f, 1f, 0.55f));
        cloud.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        cloud.getAdditionalRenderState().setDepthWrite(false);
        cloud.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);

        for (int i = 0; i < lobes.length; i++) {
            float a = FastMath.TWO_PI * i / lobes.length;
            float ring = i == 0 ? 0f : 0.78f + 0.15f * FastMath.sin(i * 2.1f);
            Geometry g = new Geometry("cloudLobe" + i, new Sphere(10, 12, 1f));
            g.setMaterial(cloud.clone());
            g.setLocalTranslation(FastMath.cos(a) * ring, 0.08f, FastMath.sin(a) * ring * 1.35f);
            g.setLocalScale(0.62f + 0.18f * FastMath.sin(i * 1.7f), 0.34f,
                    0.72f + 0.15f * FastMath.cos(i * 1.3f));
            g.setQueueBucket(RenderQueue.Bucket.Transparent);
            g.setShadowMode(RenderQueue.ShadowMode.Off);
            puffs.attachChild(g);
            lobes[i] = g;
            phase[i] = i * 1.71f;
            baseY[i] = 0.08f;
        }
        root.attachChild(puffs);
        root.setCullHint(Spatial.CullHint.Always);
        root.setQueueBucket(RenderQueue.Bucket.Transparent);
    }

    boolean visible() {
        return visible;
    }

    void show(boolean value) {
        visible = value;
        summon = 0f;
        if (!value) {
            root.setCullHint(Spatial.CullHint.Always);
        }
    }

    void burst() {
        if (!visible) {
            return;
        }
        fx.spark(tmp.set(root.getWorldTranslation()), new ColorRGBA(0.8f, 0.9f, 1f, 1f), 22);
        fx.shockwave(root.getWorldTranslation().clone(), 2.6f, 0.35f,
                new ColorRGBA(0.72f, 0.85f, 1f, 0.4f));
    }

    /** 腾云冲刺：云体前冲拉长 + 尾迹，同样只在可见时有效。 */
    void dash() {
        if (!visible) {
            return;
        }
        dashT = DASH_TIME;
        trailTimer = 0f;
        Vector3f p = root.getWorldTranslation();
        fx.spark(p.clone(), new ColorRGBA(0.86f, 0.94f, 1f, 1f), 26);
        fx.shockwave(p.clone(), 3.2f, 0.42f, new ColorRGBA(0.78f, 0.9f, 1f, 0.45f));
    }

    boolean dashing() {
        return dashT > 0f;
    }

    void update(float dt, Player player, boolean moving, boolean sprinting, Fx fx) {
        time += dt;
        if (!visible) {
            return;
        }
        root.setCullHint(Spatial.CullHint.Never);
        summon = Math.min(1f, summon + dt * 3.6f);
        dashT = Math.max(0f, dashT - dt);
        float surge = MathX.smooth(dashT / DASH_TIME);
        float pop = 1f + 0.22f * FastMath.sin(summon * FastMath.PI) * (1f - summon);
        float ground = player.worldY();
        float bob = FastMath.sin(time * 2.6f) * 0.045f;
        float hover = player.airborne ? 0.03f : 0.52f + bob;
        tmp.set(player.pos.x, ground - (player.mounted ? 0.20f : 0.02f) + hover,
                player.pos.z);
        root.setLocalTranslation(tmp);
        root.setLocalScale(pop * 0.68f * (1f + 0.28f * surge),
                0.92f + 0.08f * FastMath.sin(time * 3.2f) - 0.12f * surge,
                pop * 0.68f * (1f + 0.28f * surge));
        root.rotate(0f, dt * (moving ? (sprinting ? 1.5f : 0.8f) : 0.25f), 0f);
        for (int i = 0; i < lobes.length; i++) {
            float wobble = FastMath.sin(time * (2.2f + i * 0.13f) + phase[i]);
            lobes[i].setLocalTranslation(lobes[i].getLocalTranslation().x,
                    baseY[i] + wobble * 0.06f, lobes[i].getLocalTranslation().z);
            lobes[i].setLocalScale(0.6f + 0.1f * wobble, 0.32f + 0.03f * wobble,
                    0.68f + 0.08f * wobble);
        }
        trailTimer -= dt;
        if (surge > 0f) {
            trailTimer = Math.min(trailTimer, 0.02f);
        }
        if ((moving || surge > 0f) && trailTimer <= 0f) {
            trailTimer = sprinting || surge > 0f ? 0.06f : 0.13f;
            fx.dust(new Vector3f(player.pos.x, player.worldY() + 0.12f, player.pos.z), 2);
        }
    }

    Vector3f position() {
        return root.getWorldTranslation();
    }
}
