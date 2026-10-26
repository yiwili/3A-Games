package com.aedfh;

import com.jme3.math.Vector3f;

/**
 * Anything the player can lock on to, hit, or execute. The arena boss and all
 * open-world enemies share this so the combat code stays target agnostic.
 */
abstract class Target {

    final Vector3f pos = new Vector3f();
    float yaw;
    boolean alive = true;

    abstract float radius();

    abstract float maxHp();

    abstract float hp();

    boolean enraged() {
        return false;
    }

    abstract boolean canBeExecuted(Vector3f from);

    abstract void lockForExecution(Vector3f from);

    abstract void clearExecutionLock();

    abstract void takeExecution(float dmg, Vector3f impact);

    abstract void takeHit(float dmg, float poiseDmg, Vector3f impact);

    /** Height used by lock-on markers and UI bars. */
    float displayHeight() {
        return 2.4f;
    }

    /** Terrain height under the target; zero inside the arena. */
    float groundY() {
        return 0f;
    }
}
