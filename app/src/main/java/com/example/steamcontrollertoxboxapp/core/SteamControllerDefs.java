package com.example.steamcontrollertoxboxapp.core; // << Note package name change

import java.util.EnumSet;

/**
 * Definitions mirrored from steam_controller.hpp
 */
public class SteamControllerDefs {

    public enum Button {
        RT(1 << 0),                // Right trigger fully pressed.
        LT(1 << 1),                // Left trigger fully pressed.
        RS(1 << 2),                // Right shoulder button pressed.
        LS(1 << 3),                // Left shoulder button pressed.
        Y(1 << 4),                 // Y button.
        B(1 << 5),                 // B button.
        X(1 << 6),                 // X button.
        A(1 << 7),                 // A button.
        DPAD_UP(0x01 << 8),        // Left pad pressed with thumb in the upper quarter.
        DPAD_RIGHT(0x02 << 8),     // Left pad pressed with thumb in the right quarter.
        DPAD_LEFT(0x04 << 8),      // Left pad pressed with thumb in the left quarter.
        DPAD_DOWN(0x08 << 8),      // Left pad pressed with thumb in the bottom quarter.
        PREV(0x10 << 8),           // Left arrow button.
        HOME(0x20 << 8),           // Steam logo button.
        NEXT(0x40 << 8),           // Right arrow button.
        LG(0x80 << 8),             // Left grip button.
        RG(0x01 << 16),            // Right grip button.
        STICK(0x02 << 16),         // Stick or left pad is pressed down.
        RPAD(0x04 << 16),          // Right pad pressed.
        LFINGER(0x08 << 16),       // If set, a finger is touching left touch pad.
        RFINGER(0x10 << 16),       // If set, a finger is touching right touch pad.
        FLAG_PAD_STICK(0x80 << 16); // If set, LFINGER determines whether left_axis is pad- or stick-position.

        private final int value;
        Button(int value) { this.value = value; }
        public int getValue() { return value; }

        public static EnumSet<Button> fromMask(int mask) {
            EnumSet<Button> set = EnumSet.noneOf(Button.class);
            for (Button b : Button.values()) {
                if ((mask & b.value) != 0) {
                    set.add(b);
                }
            }
            return set;
        }
    }

    public enum EventKey {
        UPDATE(1),
        CONNECTION(3),
        BATTERY(4);

        private final int value;
        EventKey(int value) { this.value = value; }
        public int getValue() { return value; }

        public static EventKey fromValue(int value) {
            for (EventKey key : values()) {
                if (key.value == value) {
                    return key;
                }
            }
            return null; // Or throw exception
        }
    }

    public static class Vec2 {
        public short x, y;
        @Override public String toString() { return "(" + x + ", " + y + ")"; }
    }

    public static class Vec3 {
        public short x, y, z;
        @Override public String toString() { return "(" + x + ", " + y + ", " + z + ")"; }
    }

    public static class Quat {
        public short x, y, z, w;
        @Override public String toString() { return "(" + x + ", " + y + ", " + z + ", " + w + ")"; }
    }

    // Represents the primary input data packet
    public static class UpdateEvent {
        public EventKey key = EventKey.UPDATE;
        public int timeStamp; // Consider using long and appropriate time units
        public int buttons;   // Raw button mask
        public byte leftTrigger; // 0-255
        public byte rightTrigger; // 0-255
        public Vec2 leftAxis = new Vec2();
        public Vec2 rightAxis = new Vec2();
        // Optional sensor data (populated based on controller flags)
        public Quat orientation = new Quat();
        public Vec3 acceleration = new Vec3();
        public Vec3 angularVelocity = new Vec3();

        @Override
        public String toString() {
            return "UpdateEvent{" +
                   "timeStamp=" + timeStamp +
                   ", buttons=" + Integer.toBinaryString(buttons) +
                   ", LT=" + (leftTrigger & 0xFF) +
                   ", RT=" + (rightTrigger & 0xFF) +
                   ", leftAxis=" + leftAxis +
                   ", rightAxis=" + rightAxis +
                   // Add sensors if needed
                   '}';
        }
    }

    // Represents connection status changes
    public static class ConnectionEvent {
        // Enum corrected to match C++ enum values
        public enum MessageType { UNKNOWN(0), DISCONNECTED(1), CONNECTED(2), PAIRING_REQUESTED(3);
            private final int value;
            MessageType(int value) { this.value = value;}
            public int getValue() { return value; }

            public static MessageType fromValue(int val) {
                for (MessageType type : values()) {
                    if (type.value == val) return type;
                }
                return UNKNOWN;
            }
        }
        public EventKey key = EventKey.CONNECTION;
        public MessageType message;

        @Override public String toString() { return "ConnectionEvent{" + "message=" + message + '}'; }
    }

    // Represents battery status updates
    public static class BatteryEvent {
        public EventKey key = EventKey.BATTERY;
        public short voltage; // Millivolts
        @Override public String toString() { return "BatteryEvent{" + "voltage=" + voltage + "mV" + '}'; }
    }

    // Union equivalent - use specific event classes
    // public static class Event { ... }
}