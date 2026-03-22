//
//
//public class Main implements IAppObserver{
//
//    //NINJA Animations:
//    //    Attack1
//    //    Attack2
//    //    Attack3
//    //    Backflip
//    //    Block
//    //    Climb
//    //    Crouch
//    //    Death1
//    //    Death2
//    //    HighJump
//    //    Idle1
//    //    Idle2
//    //    Idle3
//    //    Jump
//    //    JumpNoHeight
//    //    Kick
//    //    SideKick
//    //    Spin
//    //    Stealth
//    //    Walk
//
//    private static final String code =
//            "ninja is a model from xxxx; " +
//            "adi is a ninja;" +
//            "adi.rotate(y+180) in 8 seconds;"+
//            "adi.rotate(y-100) in 5 seconds;"+
//            "adi.animate(Kick at speed of 0.8 then Backflip at speed of 1 then SideKick at speed of 0.5) ;";
//
//    private static SceneMaxApp app = new SceneMaxApp(new Main());
//
//    public static void main(String[] args) {
//        app.start();
//    }
//
//    @Override
//    public void init() {
//        app.run(code);
//    }
//}