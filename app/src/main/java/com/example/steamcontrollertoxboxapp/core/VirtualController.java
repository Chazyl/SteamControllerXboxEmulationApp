package com.example.steamcontrollertoxboxapp.core; // << Note package name change

import java.io.IOException; // Added for Android context
import java.util.Map;

/**
 * Interface for a virtual gamepad (Xbox 360 / XInput style).
 * Implementations will use platform-specific backends (uinput on Linux/Android Root).
 */
public interface VirtualController extends AutoCloseable {

    // Standard Xbox 360 / XInput button mapping
    enum XboxButton {
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
        START, BACK, LS, RS, // Left Stick Click, Right Stick Click
        LB, RB, // Left Bumper, Right Bumper
        GUIDE, // Xbox logo button
        A, B, X, Y
    }

    // Standard Xbox 360 / XInput axis mapping
    enum XboxAxis {
        LEFT_X, LEFT_Y, // Left Stick X/Y
        RIGHT_X, RIGHT_Y, // Right Stick X/Y
        LT, RT // Left Trigger, Right Trigger (often treated as axes 0-255 or 0-1023)
    }

    // Standard XInput axis range
    short AXIS_MIN = -32768;
    short AXIS_MAX = 32767;
    // Standard XInput trigger range (as reported by ViGEm/uinput)
    short TRIGGER_MIN = 0;
    short TRIGGER_MAX = 255; // uinput usually uses 0-255

    /**
     * Connects/initializes the virtual controller device.
     * @throws Exception If initialization fails (e.g., cannot access uinput, missing root).
     */
    void connect() throws Exception; // Keep Exception for broader errors like SecurityException

    /**
     * Disconnects/releases the virtual controller device.
     * @throws Exception If disconnection fails (less likely, but keep for consistency).
     */
    void disconnect() throws Exception; // Keep Exception

    /**
     * Updates the state of the virtual controller.
     * @param buttonStates A map containing the current pressed state (true/false) for each button.
     *                     Buttons not present in the map are assumed to be released.
     * @param axisStates A map containing the current value (-32768 to 32767 or 0-255 for triggers) for each axis.
     *                   Axes not present are assumed to be centered (0).
     * @throws IOException If writing to the underlying device fails.
     * @throws IllegalStateException If called when not connected.
     */
    void updateState(Map<XboxButton, Boolean> buttonStates, Map<XboxAxis, Short> axisStates) throws IOException, IllegalStateException;

    /**
     * Closes the controller and releases resources. Should call disconnect.
     * @throws Exception If closing fails.
     */
    @Override
    void close() throws Exception; // Keep Exception
}