package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandMove;
import com.scenemaxeng.compiler.BoxVariableDef;
import com.jme3.scene.Node;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RuntimeEntityResolutionTest {

    @Test
    public void resolvesRuntimeOnlyEntityFromParentScopeWhenCommandHasNoVarDef() {
        SceneMaxScope parentScope = new SceneMaxScope();
        BoxVariableDef weaponColliderDef = new BoxVariableDef();
        weaponColliderDef.varName = "weapon_player_weapon";
        parentScope.entities.put("weapon_player_weapon", new BoxInst(weaponColliderDef, parentScope));

        SceneMaxScope eventScope = new SceneMaxScope();
        eventScope.parent = parentScope;

        ActionCommandMove cmd = new ActionCommandMove();
        cmd.targetVar = "weapon_player_weapon";

        TargetResolverProbe probe = new TargetResolverProbe(eventScope, cmd);

        assertEquals(0, probe.resolve());
        assertEquals("weapon_player_weapon@" + parentScope.scopeId, probe.resolvedTargetVar());
        assertEquals(weaponColliderDef.varType, probe.resolvedTargetVarDef().varType);
    }

    @Test
    public void weaponDefinitionIdDoesNotRegisterGlobalWeaponEntity() {
        SceneMaxApp app = new SceneMaxApp();
        SceneMaxScope ownerScope = new SceneMaxScope();

        app.registerWeaponModel("weapon_player_weapon@" + ownerScope.scopeId,
                new Node("weapon_player_weapon@" + ownerScope.scopeId));

        assertNull(ownerScope.getEntityInst("weapon_player_weapon"));
    }

    private static class TargetResolverProbe extends SceneMaxBaseController {
        TargetResolverProbe(SceneMaxScope scope, ActionCommandMove cmd) {
            this.scope = scope;
            this.cmd = cmd;
        }

        int resolve() {
            return findTargetVar();
        }

        String resolvedTargetVar() {
            return targetVar;
        }

        com.scenemaxeng.compiler.VariableDef resolvedTargetVarDef() {
            return targetVarDef;
        }
    }
}
