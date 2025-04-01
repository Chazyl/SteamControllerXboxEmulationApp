package com.example.steamcontrollertoxboxapp.core;

public interface VirtualController {
    enum XboxButton {
        A, B, X, Y,
        LB, RB,
        BACK, START,
        LSTICK, RSTICK
    }

    enum XboxAxis {
        LEFT_X, LEFT_Y,
        RIGHT_X, RIGHT_Y,
        LT, RT
    }
    
    boolean initialize();
    void update(SteamControllerParser.XboxOutput state);
    void destroy();
    SteamControllerParser.XboxOutput getLastState();
}
