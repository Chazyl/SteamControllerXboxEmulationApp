package com.example.steamcontrollertoxboxapp.core; // << Note package name change

import android.util.Log; // Use Android logging

import java.io.IOException; // Added for Android context
import java.util.EnumMap;
import java.util.Map;

/**
 * Maps input events from a parsed SteamControllerDefs.UpdateEvent
 * to the state expected by the VirtualController (Xbox layout).
 */
public class ControllerMapper {
    private static final String TAG = "ControllerMapper"; // Added for logging

    private final VirtualController virtualController;
    private final Map<VirtualController.XboxButton, Boolean> currentButtonStates = new EnumMap<>(VirtualController.XboxButton.class);
    private final Map<VirtualController.XboxAxis, Short> currentAxisStates = new EnumMap<>(VirtualController.XboxAxis.class);
    // Optional: Keep track of previous state to only send changes (might add overhead vs benefit)
    // private final Map<VirtualController.XboxButton, Boolean> previousButtonStates = new EnumMap<>(VirtualController.XboxButton.class);
    // private final Map<VirtualController.XboxAxis, Short> previousAxisStates = new EnumMap<>(VirtualController.XboxAxis.class);

    public ControllerMapper(VirtualController controller) {
        this.virtualController = controller;
        // Initialize maps with default values (all buttons released, axes centered)
        resetState();
    }

    private void resetState() {
        for (VirtualController.XboxButton btn : VirtualController.XboxButton.values()) {
            currentButtonStates.put(btn, false);
            // previousButtonStates.put(btn, false);
        }
        for (VirtualController.XboxAxis axis : VirtualController.XboxAxis.values()) {
            // Triggers default to 0, sticks default to 0
            currentAxisStates.put(axis, (short)0);
             // previousAxisStates.put(axis, (short)0);
        }
        Log.d(TAG, "Mapper state reset.");
    }

    /**
     * Processes a Steam Controller update event and sends the mapped state
     * to the virtual Xbox controller.
     * @param steamEvent The parsed event from the Steam Controller.
     * @throws IOException If the underlying virtual controller write fails.
     * @throws IllegalStateException If the virtual controller is not connected.
     */
    public void processSteamEvent(SteamControllerParser.XboxOutput xboxOutput) throws IllegalStateException {
        if (xboxOutput == null) {
            return;
        }

        // Update button states
        updateButtonState(VirtualController.XboxButton.A, xboxOutput.buttonA);
        updateButtonState(VirtualController.XboxButton.B, xboxOutput.buttonB);
        updateButtonState(VirtualController.XboxButton.X, xboxOutput.buttonX);
        updateButtonState(VirtualController.XboxButton.Y, xboxOutput.buttonY);
        updateButtonState(VirtualController.XboxButton.LB, xboxOutput.buttonLB);
        updateButtonState(VirtualController.XboxButton.RB, xboxOutput.buttonRB);
        updateButtonState(VirtualController.XboxButton.BACK, xboxOutput.buttonBack);
        updateButtonState(VirtualController.XboxButton.START, xboxOutput.buttonStart);
        updateButtonState(VirtualController.XboxButton.LSTICK, xboxOutput.buttonLStick);
        updateButtonState(VirtualController.XboxButton.RSTICK, xboxOutput.buttonRStick);

        // Update axis states
        updateAxisState(VirtualController.XboxAxis.LEFT_X, (short)(xboxOutput.leftStickX * 32767));
        updateAxisState(VirtualController.XboxAxis.LEFT_Y, (short)(-xboxOutput.leftStickY * 32767)); // Invert Y
        updateAxisState(VirtualController.XboxAxis.RIGHT_X, (short)(xboxOutput.rightStickX * 32767));
        updateAxisState(VirtualController.XboxAxis.RIGHT_Y, (short)(-xboxOutput.rightStickY * 32767)); // Invert Y
        updateAxisState(VirtualController.XboxAxis.LT, (short)(xboxOutput.leftTrigger * 255));
        updateAxisState(VirtualController.XboxAxis.RT, (short)(xboxOutput.rightTrigger * 255));

        // Update the virtual controller
        virtualController.update(xboxOutput);
    }

    // Helper to update button state (no change tracking here)
    private void updateButtonState(VirtualController.XboxButton button, boolean isPressed) {
        currentButtonStates.put(button, isPressed);
    }

    // Helper to update axis state (no change tracking here)
    private void updateAxisState(VirtualController.XboxAxis axis, short value) {
         // Optional: Add deadzone logic here if needed before putting value
         currentAxisStates.put(axis, value);
    }

    // Optional: Check if current state differs from previous state
    /*
    private boolean stateHasChanged() {
        for (VirtualController.XboxButton btn : VirtualController.XboxButton.values()) {
            if (currentButtonStates.get(btn) != previousButtonStates.get(btn)) {
                return true;
            }
        }
        for (VirtualController.XboxAxis axis : VirtualController.XboxAxis.values()) {
            if (currentAxisStates.get(axis) != previousAxisStates.get(axis)) {
                // Add tolerance for analog axes if needed
                // if (Math.abs(currentAxisStates.get(axis) - previousAxisStates.get(axis)) > AXIS_TOLERANCE)
                return true;
            }
        }
        return false;
    }
    */

    // Helper to invert axis value
    private short invertAxis(short value) {
        // Handle potential overflow for Short.MIN_VALUE
        if (value == Short.MIN_VALUE) return Short.MAX_VALUE;
        return (short) (-value);
    }

    // Helper to convert unsigned byte (0-255) to short
    private short unsignedByteToShort(byte b) {
        return (short) (b & 0xFF);
    }
}
