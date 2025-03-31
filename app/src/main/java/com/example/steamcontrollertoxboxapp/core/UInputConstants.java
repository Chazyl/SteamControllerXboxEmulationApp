package com.example.steamcontrollertoxboxapp.core; // << Note package name change

/**
 * Constants from linux/input-event-codes.h needed for uinput.
 * These are generally standard across Linux systems, including Android's kernel.
 * Check against relevant kernel sources if issues arise.
 */
public class UInputConstants {

    // Event types (from <linux/input.h>)
    public static final int EV_SYN = 0x00;
    public static final int EV_KEY = 0x01;
    public static final int EV_REL = 0x02;
    public static final int EV_ABS = 0x03;
    public static final int EV_MSC = 0x04;
    public static final int EV_SW = 0x05;
    public static final int EV_LED = 0x11;
    public static final int EV_SND = 0x12;
    public static final int EV_REP = 0x14;
    public static final int EV_FF = 0x15;
    public static final int EV_PWR = 0x16;
    public static final int EV_FF_STATUS = 0x17;

    // Synchronization events (from <linux/input.h>)
    public static final int SYN_REPORT = 0;
    public static final int SYN_CONFIG = 1;
    public static final int SYN_MT_REPORT = 2;
    public static final int SYN_DROPPED = 3;

    // Key codes (BTN_*) matching standard gamepad layout (from <linux/input-event-codes.h>)
    public static final int BTN_A = 0x130; // 304 - Often Gamepad South
    public static final int BTN_B = 0x131; // 305 - Often Gamepad East
    public static final int BTN_X = 0x133; // 307 - Often Gamepad West
    public static final int BTN_Y = 0x134; // 308 - Often Gamepad North
    public static final int BTN_TL = 0x136; // 310 (Trigger Left/Top Left Bumper)
    public static final int BTN_TR = 0x137; // 311 (Trigger Right/Top Right Bumper)
    public static final int BTN_TL2 = 0x138; // 312 (Bottom Left Trigger/Bumper 2) - Use ABS_Z instead?
    public static final int BTN_TR2 = 0x139; // 313 (Bottom Right Trigger/Bumper 2) - Use ABS_RZ instead?
    public static final int BTN_SELECT = 0x13a; // 314 (Select/Back)
    public static final int BTN_START = 0x13b; // 315 (Start)
    public static final int BTN_MODE = 0x13c; // 316 (Mode/Home/Guide)
    public static final int BTN_THUMBL = 0x13d; // 317 (Left Thumb stick click)
    public static final int BTN_THUMBR = 0x13e; // 318 (Right Thumb stick click)

    // DPad Buttons (BTN_DPAD_*) - Often mapped to HAT axes instead
    public static final int BTN_DPAD_UP = 0x220;
    public static final int BTN_DPAD_DOWN = 0x221;
    public static final int BTN_DPAD_LEFT = 0x222;
    public static final int BTN_DPAD_RIGHT = 0x223;

    // Absolute axes codes (ABS_*) matching standard gamepad (from <linux/input-event-codes.h>)
    public static final int ABS_X = 0x00; // Left Stick X
    public static final int ABS_Y = 0x01; // Left Stick Y
    public static final int ABS_Z = 0x02; // Often Left Trigger analog
    public static final int ABS_RX = 0x03; // Right Stick X
    public static final int ABS_RY = 0x04; // Right Stick Y
    public static final int ABS_RZ = 0x05; // Often Right Trigger analog
    public static final int ABS_HAT0X = 0x10; // D-Pad X (-1, 0, 1)
    public static final int ABS_HAT0Y = 0x11; // D-Pad Y (-1, 0, 1)

    // Bus types (from <linux/input.h>)
    public static final int BUS_USB = 0x03;
    public static final int BUS_BLUETOOTH = 0x05;
    public static final int BUS_VIRTUAL = 0x06; // Maybe more appropriate for uinput?
}