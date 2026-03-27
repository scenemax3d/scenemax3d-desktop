package com.scenemaxeng.projector;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.scenemaxeng.compiler.DirectionVerb;
import com.scenemaxeng.compiler.PositionStatement;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class Util {

    public static void calcPositionStatementVerbs(SceneMaxScope thread, PositionStatement posStatement, Quaternion locRot, Vector3f initLocation) {

        if(posStatement.directionVerbs!=null) {

            for(DirectionVerb dv:posStatement.directionVerbs) {
                Double val = (Double) new ActionLogicalExpressionVm(dv.valExp,thread).evaluate();

                if(dv.verb==DirectionVerb.FORWARD) {
                    Vector3f forward = locRot.mult(Vector3f.UNIT_Z);
                    initLocation.addLocal(forward.mult(val.floatValue()));
                } else if(dv.verb==DirectionVerb.BACKWARD) {
                    Vector3f forward = locRot.mult(Vector3f.UNIT_Z);
                    initLocation.addLocal(forward.mult(val.floatValue()).negate());
                } else if(dv.verb==DirectionVerb.LEFT) {
                    Vector3f left = locRot.mult(Vector3f.UNIT_X);
                    initLocation.addLocal(left.mult(val.floatValue()));
                } else if(dv.verb==DirectionVerb.RIGHT) {
                    Vector3f left = locRot.mult(Vector3f.UNIT_X);
                    initLocation.addLocal(left.mult(val.floatValue()).negate());
                } else if(dv.verb==DirectionVerb.UP) {
                    Vector3f up = locRot.mult(Vector3f.UNIT_Y);
                    initLocation.addLocal(up.mult(val.floatValue()));
                } else if(dv.verb==DirectionVerb.DOWN) {
                    Vector3f up = locRot.mult(Vector3f.UNIT_Y);
                    initLocation.addLocal(up.mult(val.floatValue()).negate());
                }
            }
        }

    }

    public static boolean writeFile(String path, String text) {

        BufferedWriter writer = null;
        try {
            File target = new File(path);
            writer = new BufferedWriter(new FileWriter(target));
            writer.write(text);
            writer.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;

    }

    public static String readFile(File f) {

        String text = "";
        try {
            text = FileUtils.readFileToString(f,StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return text;

    }

    public static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[0x1000];
        while (true) {
            int r = in.read(buf);
            if (r == -1) {
                break;
            }
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }
}