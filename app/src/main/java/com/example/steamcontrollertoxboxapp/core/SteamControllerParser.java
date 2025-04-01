package com.example.steamcontrollertoxboxapp.core;

public class SteamControllerParser {
    private static final String TAG = "SteamControllerParser";
    
    // Steam Controller input report structure constants
    private static final int REPORT_ID_OFFSET = 0;
    private static final int BUTTONS_OFFSET = 1;
    private static final int LEFT_TRIGGER_OFFSET = 3;
    private static final int RIGHT_TRIGGER_OFFSET = 4;
    private static final int LEFT_STICK_X_OFFSET = 5;
    private static final int LEFT_STICK_Y_OFFSET = 7;
    private static final int RIGHT_STICK_X_OFFSET = 9;
    private static final int RIGHT_STICK_Y_OFFSET = 11;
    private static final int TOUCHPAD_OFFSET = 13;
    
    // Steam Controller button bitmasks
    private static final int BUTTON_A = 0x01;
    private static final int BUTTON_B = 0x02;
    private static final int BUTTON_X = 0x04;
    private static final int BUTTON_Y = 0x08;
    private static final int BUTTON_LB = 0x10;
    private static final int BUTTON_RB = 0x20;
    private static final int BUTTON_LT = 0x40;
    private static final int BUTTON_RT = 0x80;
    private static final int BUTTON_BACK = 0x100;
    private static final int BUTTON_START = 0x200;
    private static final int BUTTON_STEAM = 0x400;
    private static final int BUTTON_LSTICK = 0x800;
    private static final int BUTTON_RSTICK = 0x1000;
    
    // Xbox controller output structure
    public static class XboxOutput {
        public float leftStickX;
        public float leftStickY;
        public float rightStickX;
        public float rightStickY;
        public float leftTrigger;
        public float rightTrigger;
        public boolean buttonA;
        public boolean buttonB;
        public boolean buttonX;
        public boolean buttonY;
        public boolean buttonLB;
        public boolean buttonRB;
        public boolean buttonBack;
        public boolean buttonStart;
        public boolean buttonLStick;
        public boolean buttonRStick;
    }
    
    public static XboxOutput parseInput(byte[] data) {
        if (data == null || data.length < 20) {
            return null;
        }
        
        XboxOutput output = new XboxOutput();
        
        // Parse buttons (2 bytes)
        int buttons = ((data[BUTTONS_OFFSET + 1] & 0xFF) << 8 | (data[BUTTONS_OFFSET] & 0xFF));
        
        // Map buttons to Xbox layout
        output.buttonA = (buttons & BUTTON_B) != 0; // Steam B -> Xbox A
        output.buttonB = (buttons & BUTTON_A) != 0; // Steam A -> Xbox B
        output.buttonX = (buttons & BUTTON_Y) != 0;  // Steam Y -> Xbox X
        output.buttonY = (buttons & BUTTON_X) != 0;  // Steam X -> Xbox Y
        output.buttonLB = (buttons & BUTTON_LB) != 0;
        output.buttonRB = (buttons & BUTTON_RB) != 0;
        output.buttonBack = (buttons & BUTTON_BACK) != 0;
        output.buttonStart = (buttons & BUTTON_START) != 0;
        output.buttonLStick = (buttons & BUTTON_LSTICK) != 0;
        output.buttonRStick = (buttons & BUTTON_RSTICK) != 0;
        
        // Parse triggers (0-255)
        output.leftTrigger = (data[LEFT_TRIGGER_OFFSET] & 0xFF) / 255.0f;
        output.rightTrigger = (data[RIGHT_TRIGGER_OFFSET] & 0xFF) / 255.0f;
        
        // Parse sticks (16-bit signed values)
        output.leftStickX = normalizeAxis(readShort(data, LEFT_STICK_X_OFFSET));
        output.leftStickY = normalizeAxis(readShort(data, LEFT_STICK_Y_OFFSET));
        output.rightStickX = normalizeAxis(readShort(data, RIGHT_STICK_X_OFFSET));
        output.rightStickY = normalizeAxis(readShort(data, RIGHT_STICK_Y_OFFSET));
        
        return output;
    }
    
    private static short readShort(byte[] data, int offset) {
        return (short)((data[offset + 1] & 0xFF) << 8 | (data[offset] & 0xFF));
    }
    
    private static float normalizeAxis(short value) {
        // Normalize to -1.0 to 1.0 range
        return value / 32768.0f;
    }
}
