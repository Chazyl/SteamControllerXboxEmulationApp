package com.example.steamcontrollerxbox.core; // << Note package name change

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
    public void processSteamEvent(SteamControllerDefs.UpdateEvent steamEvent) throws IOException, IllegalStateException {
        if (steamEvent == null) {
             // Log.w(TAG, "Received null SteamEvent"); // Can be noisy
             return;
        }

        // --- Button Mapping ---
        int buttons = steamEvent.buttons;
        updateButtonState(VirtualController.XboxButton.A, (buttons & SteamControllerDefs.Button.A.getValue()) != 0);
        updateButtonState(VirtualController.XboxButton.B, (buttons & SteamControllerDefs.Button.B.getValue()) != 0);
        updateButtonState(VirtualController.XboxButton.X, (buttons & SteamControllerDefs.Button.X.getValue()) != 0);
        updateButtonState(VirtualController.XboxButton.Y, (buttons & SteamControllerDefs.Button.Y.getValue()) != 0);

        updateButtonState(VirtualController.XboxButton.LB, (buttons & SteamControllerDefs.Button.LS.getValue()) != 0);
        updateButtonState(VirtualController.XboxButton.RB, (buttons & SteamControllerDefs.Button.RS.getValue()) != 0);

        updateButtonState(VirtualController.XboxButton.BACK, (buttons & SteamControllerDefs.Button.PREV.getValue()) != 0);
        updateButtonState(VirtualController.XboxButton.START, (buttons & SteamControllerDefs.Button.NEXT.getValue()) != 0);
        updateButtonState(VirtualController.XboxButton.GUIDE, (buttons & SteamControllerDefs.Button.HOME.getValue()) != 0);

        // DPad mapping (from Left Pad Quarters)
        updateButtonState(VirtualController.XboxButton.DPAD_UP, (buttons & SteamControllerDefs.Button.DPAD_UP.getValue()) != 0);
        updateButtonState(VirtualController.XboxButton.DPAD_DOWN, (buttons & SteamControllerDefs.Button.DPAD_DOWN.getValue()) != 0);
        updateButtonState(VirtualController.XboxButton.DPAD_LEFT, (buttons & SteamControllerDefs.Button.DPAD_LEFT.getValue()) != 0);
        updateButtonState(VirtualController.XboxButton.DPAD_RIGHT, (buttons & SteamControllerDefs.Button.DPAD_RIGHT.getValue()) != 0);

        // Stick/Pad Click Mapping (Needs refinement based on FLAG_PAD_STICK)
        // Simple example: Map STICK press to Left Stick click, RPAD press to Right Stick click
        // boolean isLeftPadAsStick = (buttons & SteamControllerDefs.Button.FLAG_PAD_STICK.getValue()) != 0;
        updateButtonState(VirtualController.XboxButton.LS, (buttons & SteamControllerDefs.Button.STICK.getValue()) != 0);
        updateButtonState(VirtualController.XboxButton.RS, (buttons & SteamControllerDefs.Button.RPAD.getValue()) != 0);

        // Optional: Map Grip buttons (LG, RG) - e.g., map LG to LS click if not already mapped?
        // if (!currentButtonStates.get(VirtualController.XboxButton.LS)) { // Only map if LS isn't pressed by STICK
        //    updateButtonState(VirtualController.XboxButton.LS, (buttons & SteamControllerDefs.Button.LG.getValue()) != 0);
        // }
        // updateButtonState(VirtualController.XboxButton.RS, (buttons & SteamControllerDefs.Button.RG.getValue()) != 0); // Or map RG to RS?


        // --- Axis Mapping ---
        // Left Stick/Pad
        updateAxisState(VirtualController.XboxAxis.LEFT_X, steamEvent.leftAxis.x);
        updateAxisState(VirtualController.XboxAxis.LEFT_Y, invertAxis(steamEvent.leftAxis.y)); // Invert Y for standard XInput

        // Right Stick/Pad
        updateAxisState(VirtualController.XboxAxis.RIGHT_X, steamEvent.rightAxis.x);
        updateAxisState(VirtualController.XboxAxis.RIGHT_Y, invertAxis(steamEvent.rightAxis.y)); // Invert Y for standard XInput

        // --- Trigger Mapping ---
        // Steam triggers are 0-255, map directly to Xbox trigger axes 0-255
        updateAxisState(VirtualController.XboxAxis.LT, unsignedByteToShort(steamEvent.leftTrigger));
        updateAxisState(VirtualController.XboxAxis.RT, unsignedByteToShort(steamEvent.rightTrigger));

        // --- Send Update ---
        // Optional: Check if state actually changed before sending
        // if (stateHasChanged()) {
            virtualController.updateState(currentButtonStates, currentAxisStates);
            // Update previous state if tracking changes
            // previousButtonStates.putAll(currentButtonStates);
            // previousAxisStates.putAll(currentAxisStates);
        // }
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