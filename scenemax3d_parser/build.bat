del *.java
del *.tokens

"C:\Program Files\Java\jdk-11.0.12\bin\java.exe" -Xmx500M -cp antlr-4.5.3-complete.jar org.antlr.v4.Tool -no-listener -visitor SceneMax.g4

SET CLASSPATH=.;antlr-4.5.3-complete.jar;%CLASSPATH%
"C:\Program Files\Java\jdk-11.0.12\bin\javac.exe" -source 1.8 -target 1.8 -d build SceneMax*.java
del *.java
del *.tokens
cd build
rmdir com
"C:\Program Files\Java\jdk-11.0.12\bin\jar.exe" cvf scenemax_parser.jar *

xcopy scenemax_parser.jar ..\..\scenemax_win_projector\libs
xcopy scenemax_parser.jar ..\..\scenemax3d_compiler\libs
xcopy scenemax_parser.jar ..\..\..\SceneMax3DGameHub\app\libs
xcopy scenemax_parser.jar ..\..\..\scenemax3d-mobile-engine\libs

pause
