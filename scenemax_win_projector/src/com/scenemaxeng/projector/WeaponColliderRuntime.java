package com.scenemaxeng.projector;

import com.jme3.bullet.control.GhostControl;
import com.jme3.scene.Node;

class WeaponColliderRuntime {
    final String runtimeName;
    final String colliderName;
    final Node node;
    final GhostControl ghostControl;

    WeaponColliderRuntime(String runtimeName, String colliderName, Node node, GhostControl ghostControl) {
        this.runtimeName = runtimeName;
        this.colliderName = colliderName;
        this.node = node;
        this.ghostControl = ghostControl;
    }
}
