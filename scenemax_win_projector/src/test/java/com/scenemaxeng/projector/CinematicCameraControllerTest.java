package com.scenemaxeng.projector;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.scenemaxeng.compiler.CinematicCameraPlayCommand;
import com.scenemaxeng.compiler.PositionStatement;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class CinematicCameraControllerTest {

    @Test
    public void explicitPositionStatementTargetWinsOverRigTargetFallback() {
        Vector3f explicitStatementPoint = new Vector3f(1f, 2f, 3f);
        Vector3f explicitTargetPoint = new Vector3f(4f, 5f, 6f);
        Vector3f rigTargetPoint = new Vector3f(7f, 8f, 9f);
        Vector3f fallbackPoint = new Vector3f(10f, 11f, 12f);

        Vector3f chosen = CinematicCameraController.selectLookAtPoint(
                explicitStatementPoint,
                explicitTargetPoint,
                rigTargetPoint,
                fallbackPoint
        );

        assertSame(explicitStatementPoint, chosen);
    }

    @Test
    public void targetPointUsesSpatialOriginInsteadOfBoundingCenter() {
        Node node = new Node("target");
        node.setLocalTranslation(10f, 20f, 30f);
        node.setModelBound(new BoundingBox(new Vector3f(100f, 200f, 300f), 1f, 1f, 1f));
        node.updateModelBound();
        node.updateGeometricState();

        Vector3f point = CinematicCameraController.spatialWorldTranslationWithOffset(
                node,
                new Vector3f(0f, 5f, 0f)
        );

        assertEquals(new Vector3f(10f, 25f, 30f), point);
    }

    @Test
    public void simpleTargetsKeepInitialPlacementRotation() {
        CinematicCameraPlayCommand cmd = new CinematicCameraPlayCommand();
        CinematicCameraController controller = new CinematicCameraController(null, null, null, cmd);
        Quaternion initial = new Quaternion().fromAngleAxis(0.25f * FastMath.PI, Vector3f.UNIT_Y);
        Quaternion spinning = new Quaternion().fromAngleAxis(0.75f * FastMath.PI, Vector3f.UNIT_Y);

        Quaternion first = controller.resolveRigPlacementRotation(initial);
        Quaternion second = controller.resolveRigPlacementRotation(spinning);

        assertEquals(initial, first);
        assertEquals(initial, second);
    }

    @Test
    public void positionStatementTargetsFollowLivePlacementRotation() {
        CinematicCameraPlayCommand cmd = new CinematicCameraPlayCommand();
        cmd.lookAtPosStatement = new PositionStatement();
        CinematicCameraController controller = new CinematicCameraController(null, null, null, cmd);
        Quaternion initial = new Quaternion().fromAngleAxis(0.25f * FastMath.PI, Vector3f.UNIT_Y);
        Quaternion changed = new Quaternion().fromAngleAxis(0.75f * FastMath.PI, Vector3f.UNIT_Y);

        assertEquals(initial, controller.resolveRigPlacementRotation(initial));
        assertEquals(changed, controller.resolveRigPlacementRotation(changed));
    }
}
