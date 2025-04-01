package com.example.steamcontrollertoxboxapp.nativeimpl;

import android.util.Log;
import com.example.steamcontrollertoxboxapp.core.UInputConstants;
import com.example.steamcontrollertoxboxapp.core.VirtualController;
import com.example.steamcontrollertoxboxapp.core.SteamControllerParser;
import java.io.DataOutputStream;
import java.io.IOException;

public class UInputController implements VirtualController {
    private static final String TAG = "UInputController";

    static {
        try {
            System.loadLibrary("uinput_wrapper");
            Log.i(TAG, "Successfully loaded native library 'uinput_wrapper'");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library 'uinput_wrapper'. Root is likely required.", e);
        }
    }

    private native int nativeInit();
    private native void nativeClose(int fd);
    private native boolean nativeSendEvent(int fd, int type, int code, int value);

    private int uinputFd = -1;
    private SteamControllerParser.XboxOutput lastState;

    @Override
    public boolean initialize() {
        try {
            if (uinputFd >= 0) {
                Log.w(TAG, "Already initialized");
                return true;
            }

            uinputFd = nativeInit();
            if (uinputFd < 0) {
                Log.e(TAG, "nativeInit failed with error code: " + uinputFd);
                return false;
            }
            Log.i(TAG, "Virtual device created (fd=" + uinputFd + ")");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Initialization failed", e);
            return false;
        }
    }

    @Override
    public void destroy() {
        if (uinputFd >= 0) {
            try {
                nativeClose(uinputFd);
                Log.i(TAG, "Closed fd " + uinputFd);
            } catch (Exception e) {
                Log.e(TAG, "Error during nativeClose", e);
            } finally {
                uinputFd = -1;
            }
        }
    }

    @Override
    public void update(SteamControllerParser.XboxOutput state) throws IOException {
        if (uinputFd < 0) {
            throw new IllegalStateException("Not initialized");
        }

        lastState = state;

        // Buttons
        nativeSendEvent(uinputFd, UInputConstants.EV_KEY, UInputConstants.BTN_A, state.buttonA ? 1 : 0);
        nativeSendEvent(uinputFd, UInputConstants.EV_KEY, UInputConstants.BTN_B, state.buttonB ? 1 : 0);
        nativeSendEvent(uinputFd, UInputConstants.EV_KEY, UInputConstants.BTN_X, state.buttonX ? 1 : 0);
        nativeSendEvent(uinputFd, UInputConstants.EV_KEY, UInputConstants.BTN_Y, state.buttonY ? 1 : 0);
        nativeSendEvent(uinputFd, UInputConstants.EV_KEY, UInputConstants.BTN_TL, state.buttonLB ? 1 : 0);
        nativeSendEvent(uinputFd, UInputConstants.EV_KEY, UInputConstants.BTN_TR, state.buttonRB ? 1 : 0);
        nativeSendEvent(uinputFd, UInputConstants.EV_KEY, UInputConstants.BTN_SELECT, state.buttonBack ? 1 : 0);
        nativeSendEvent(uinputFd, UInputConstants.EV_KEY, UInputConstants.BTN_START, state.buttonStart ? 1 : 0);
        nativeSendEvent(uinputFd, UInputConstants.EV_KEY, UInputConstants.BTN_THUMBL, state.buttonLStick ? 1 : 0);
        nativeSendEvent(uinputFd, UInputConstants.EV_KEY, UInputConstants.BTN_THUMBR, state.buttonRStick ? 1 : 0);

        // Axes
        nativeSendEvent(uinputFd, UInputConstants.EV_ABS, UInputConstants.ABS_X, (int)(state.leftStickX * 32767));
        nativeSendEvent(uinputFd, UInputConstants.EV_ABS, UInputConstants.ABS_Y, (int)(state.leftStickY * 32767));
        nativeSendEvent(uinputFd, UInputConstants.EV_ABS, UInputConstants.ABS_RX, (int)(state.rightStickX * 32767));
        nativeSendEvent(uinputFd, UInputConstants.EV_ABS, UInputConstants.ABS_RY, (int)(state.rightStickY * 32767));
        nativeSendEvent(uinputFd, UInputConstants.EV_ABS, UInputConstants.ABS_Z, (int)(state.leftTrigger * 255));
        nativeSendEvent(uinputFd, UInputConstants.EV_ABS, UInputConstants.ABS_RZ, (int)(state.rightTrigger * 255));

        if (!nativeSendEvent(uinputFd, UInputConstants.EV_SYN, UInputConstants.SYN_REPORT, 0)) {
            throw new IOException("Failed to send SYN_REPORT");
        }
    }

    @Override
    public SteamControllerParser.XboxOutput getLastState() {
        return lastState;
    }
}
