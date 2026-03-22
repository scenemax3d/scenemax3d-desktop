//package com.scenemax.desktop;
//
//import com.scenemaxeng.projector.CanvasRect;
//
//import java.io.*;
//
//public class ScreenRecorder {
//
//    Process process;
//    Reader stdOut;
//
//    public void start(CanvasRect rc)  {
//        try {
//            //process = new ProcessBuilder(".\\Captura\\captura.exe","start","--length", "20").start();
//            String dim = rc.x+","+rc.y+","+rc.width+","+rc.height;
//            String path=System.getProperty("user.dir");
//            File f = new File(".\\out\\recording.mp4");
//            if(f.exists()) {
//                f.delete();
//            }
//
//            process = Runtime.getRuntime().exec(".\\Captura\\captura-cli.exe start --framerate 30 --speaker 0 -y --source "+dim+" --file \""+path+"\\out\\recording.mp4\"",
//                    null, new File(path+"\\Captura\\"));//new File("c:\\program files\\test\\")
//
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    public void stop() {
//        try {
//            if(process!=null) {
//                OutputStream stdIn = process.getOutputStream();
//                stdIn.write("q\n".getBytes("US-ASCII"));
//                stdIn.flush();
//            }
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//}
