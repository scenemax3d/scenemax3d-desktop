package com.scenemax.desktop.ai.gemma;

public interface LocalGemmaBridge {

    LocalGemmaBridgeConfig getConfig();

    LocalGemmaBridgeStatus checkStatus();

    LocalGemmaBridgeResponse generate(LocalGemmaBridgeRequest request) throws Exception;
}
