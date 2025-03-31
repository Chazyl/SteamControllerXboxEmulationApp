package com.example.steamcontrollertoxboxapp.nativeimpl;

import android.util.Log;
import com.example.steamcontrollertoxboxapp.core.UInputConstants;
import com.example.steamcontrollertoxboxapp.core.VirtualController;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Android implementation of VirtualController using JNI to interact with /dev/uinput.
 * Requires root access.
 */
public class UInputController implements VirtualController {
    private static final String TAG = "UInputController";

    // Load the native library
    static {
        try {
            System.loadLibrary("uinput_wrapper");
             Log.i(TAG, "Successfully loaded native library 'uinput_wrapper'");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library 'uinput_wrapper'. Root is likely required, and the app might crash.", e);
            // Consider throwing a custom exception or handling this more gracefully
        }
    }

    // Native methods matching uinput_wrapper.c
    private native int nativeInit();
    private native void nativeClose(int fd);
    private native boolean nativeSendEvent(int fd, int type, int code, int value);

    private int uinputFd = -1;
    private boolean requiresRootChecked = false;
    private boolean hasRoot = false;

    // --- Root Check (Basic) ---
    // A more robust check would use a library like libsu
    private boolean checkRootAccess() {
        if (requiresRootChecked) {
            return hasRoot;
        }
        Process process = null;
        DataOutputStream os = null;
        try {
            Log.d(TAG, "Attempting basic root check...");
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("exit\n");
            os.flush();
            int exitValue = process.waitFor();
             Log.d(TAG, "Root check process finished with exit code: " + exitValue);
            hasRoot = (exitValue == 0);
        } catch (IOException e) {
             Log.w(TAG, "Root check failed (IOException): " + e.getMessage());
            hasRoot = false;
        } catch (InterruptedException e) {
             Log.w(TAG, "Root check interrupted: " + e.getMessage());
            hasRoot = false;
            Thread.currentThread().interrupt();
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) process.destroy();
            } catch (IOException e) { /* Ignore */ }
        }
        requiresRootChecked = true;
         Log.i(TAG, "Root access available: " + hasRoot);
        return hasRoot;
    }


    @Override
    public void connect() throws Exception {
        Log.i(TAG, "connect() called.");
        if (uinputFd >= 0) {
            Log.w(TAG, "UInput: Already connected.");
            return;
        }

        if (!checkRootAccess()) {
            throw new SecurityException("Root access is required to create a virtual input device.");
        }

        Log.i(TAG, "UInput: Initializing virtual device via native call...");
        // The nativeInit function handles opening /dev/uinput and configuring it
        uinputFd = nativeInit();

        if (uinputFd < 0) {
            Log.e(TAG, "UInput: nativeInit failed with error code: " + uinputFd);
            throw new IOException("UInput: Failed to initialize virtual device (nativeInit error " + uinputFd +
                                  "). Ensure root access is granted and /dev/uinput exists and is accessible.");
        }
        Log.i(TAG, "UInput: Virtual device created successfully (fd=" + uinputFd + ").");
    }

    @Override
    public void disconnect() {
        Log.i(TAG, "disconnect() called.");
        if (uinputFd >= 0) {
            Log.i(TAG, "UInput: Closing virtual device (fd=" + uinputFd + ") via native call...");
            try {
                 nativeClose(uinputFd); // Call the native function to close and destroy
                 Log.i(TAG, "UInput: Closed fd " + uinputFd);
            } catch (Exception e) {
                 Log.e(TAG, "Error during nativeClose", e);
            } finally {
                 uinputFd = -1; // Ensure fd is marked as closed even if native call fails
            }

        } else {
            Log.w(TAG, "UInput: Already disconnected.");
        }
    }

    @Override
    public void updateState(Map<XboxButton, Boolean> buttonStates, Map<XboxAxis, Short> axisStates) throws IOException {
       // Log.v(TAG, "updateState() called."); // Can be very verbose
        if (uinputFd < 0) {
            // Log.w(TAG, "UInput: Not connected, cannot update state.");
             throw new IllegalStateException("UInput: Not connected."); // Or handle silently
        }

        // --- Translate and Send Events ---
        // Buttons
        sendButtonEvent(XboxButton.A, buttonStates, UInputConstants.BTN_A);
        sendButtonEvent(XboxButton.B, buttonStates, UInputConstants.BTN_B);
        sendButtonEvent(XboxButton.X, buttonStates, UInputConstants.BTN_X);
        sendButtonEvent(XboxButton.Y, buttonStates, UInputConstants.BTN_Y);
        sendButtonEvent(XboxButton.LB, buttonStates, UInputConstants.BTN_TL);
        sendButtonEvent(XboxButton.RB, buttonStates, UInputConstants.BTN_TR);
        sendButtonEvent(XboxButton.BACK, buttonStates, UInputConstants.BTN_SELECT);
        sendButtonEvent(XboxButton.START, buttonStates, UInputConstants.BTN_START);
        sendButtonEvent(XboxButton.GUIDE, buttonStates, UInputConstants.BTN_MODE);
        sendButtonEvent(XboxButton.LS, buttonStates, UInputConstants.BTN_THUMBL);
        sendButtonEvent(XboxButton.RS, buttonStates, UInputConstants.BTN_THUMBR);

        // DPad Hat
        updateHatState(buttonStates);

        // Axes
        sendAxisEvent(XboxAxis.LEFT_X, axisStates, UInputConstants.ABS_X);
        sendAxisEvent(XboxAxis.LEFT_Y, axisStates, UInputConstants.ABS_Y);
        sendAxisEvent(XboxAxis.RIGHT_X, axisStates, UInputConstants.ABS_RX);
        sendAxisEvent(XboxAxis.RIGHT_Y, axisStates, UInputConstants.ABS_RY);
        sendAxisEvent(XboxAxis.LT, axisStates, UInputConstants.ABS_Z);
        sendAxisEvent(XboxAxis.RT, axisStates, UInputConstants.ABS_RZ);

        // Send Synchronization Event
        if (!nativeSendEvent(uinputFd, UInputConstants.EV_SYN, UInputConstants.SYN_REPORT, 0)) {
            Log.e(TAG, "UInput: Failed to send SYN_REPORT event.");
            throw new IOException("UInput: Failed to send SYN_REPORT event.");
        }
    }

     private void sendButtonEvent(XboxButton button, Map<XboxButton, Boolean> states, int keyCode) throws IOException {
        boolean pressed = states.getOrDefault(button, false);
        if (!nativeSendEvent(uinputFd, UInputConstants.EV_KEY, keyCode, pressed ? 1 : 0)) {
            // Log might be too verbose here
            // Log.e(TAG, "UInput: Failed to send button event for code " + keyCode);
            throw new IOException("UInput: Failed to send button event for code " + keyCode);
        }
    }

    private void sendAxisEvent(XboxAxis axis, Map<XboxAxis, Short> states, int axisCode) throws IOException {
        short value = states.getOrDefault(axis, (short)0);
        if (!nativeSendEvent(uinputFd, UInputConstants.EV_ABS, axisCode, value)) {
             // Log might be too verbose here
             // Log.e(TAG, "UInput: Failed to send axis event for code " + axisCode);
            throw new IOException("UInput: Failed to send axis event for code " + axisCode);
        }
    }

     private void updateHatState(Map<XboxButton, Boolean> states) throws IOException {
         int hatX = 0;
         int hatY = 0;
         if (states.getOrDefault(XboxButton.DPAD_LEFT, false)) hatX = -1;
         else if (states.getOrDefault(XboxButton.DPAD_RIGHT, false)) hatX = 1;

         if (states.getOrDefault(XboxButton.DPAD_UP, false)) hatY = -1;
         else if (states.getOrDefault(XboxButton.DPAD_DOWN, false)) hatY = 1;

         // Only send if changed? uinput might handle this, but check performance.
         sendAxisEventDirect(UInputConstants.ABS_HAT0X, hatX);
         sendAxisEventDirect(UInputConstants.ABS_HAT0Y, hatY);
     }

     // Simplified axis send for hat
     private void sendAxisEventDirect(int axisCode, int value) throws IOException {
         if (!nativeSendEvent(uinputFd, UInputConstants.EV_ABS, axisCode, value)) {
             // Log.e(TAG, "UInput: Failed to send hat axis event for code " + axisCode);
             throw new IOException("UInput: Failed to send hat axis event for code " + axisCode);
         }
     }

    @Override
    public void close() {
        disconnect();
    }
}