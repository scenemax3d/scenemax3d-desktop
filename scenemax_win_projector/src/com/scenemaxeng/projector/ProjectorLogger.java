package com.scenemaxeng.projector;

import java.io.IOException;
import java.util.Calendar;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ProjectorLogger {

    public static Logger run () {

        Calendar rightNow = Calendar.getInstance();
        int hours = rightNow.get(Calendar.HOUR_OF_DAY);
        int minutes = rightNow.get(Calendar.MINUTE);
        int seconds = rightNow.get(Calendar.SECOND);
        String hhmmss = String.format("%02d%02d%02d", hours, minutes, seconds);

        FileHandler handler = null;
        try {
            handler = new FileHandler("projector_logger_"+hhmmss + ".txt");
        } catch (IOException e) {
        }
        SimpleFormatter formatter = new SimpleFormatter();
        handler.setFormatter(formatter);


        Logger psLogger = Logger.getLogger("com.jme3.bullet.PhysicsSpace");//com.jme3.bullet.PhysicsSpace
        psLogger.addHandler(handler);
        psLogger.setLevel(Level.FINE);

        return psLogger;

    }
}
