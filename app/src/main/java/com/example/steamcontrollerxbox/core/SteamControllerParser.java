package com.example.steamcontrollerxbox.core; // << Note package name change

import android.util.Log; // Use Android logging

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Parses raw byte arrays from Steam Controller BLE characteristics.
 * WARNING: The offsets and interpretation logic here are EDUCATED GUESSES based on
 * the header file and typical BLE packet structures. They MUST be verified against
 * the actual C++ implementation in ltjax/steam_controller or by sniffing BLE data
 * for correctness.
 */
public class SteamControllerParser {
    private static final String TAG = "SteamControllerParser"; // Added for logging

    // === PLACEHOLDER OFFSETS - **NEEDS VERIFICATION** ===
    // These offsets likely depend on the specific characteristic and report ID (if any)
    private static final int EVENT_TYPE_BYTE_OFFSET = 0; // Guess
    private static final int TIMESTAMP_OFFSET = 1;       // Guess (e.g., 3 bytes?)
    private static final int BUTTONS_OFFSET = 4;         // Guess (e.g., 3 bytes?)
    private static final int LEFT_TRIGGER_OFFSET = 7;    // Guess
    private static final int RIGHT_TRIGGER_OFFSET = 8;   // Guess
    private static final int LEFT_AXIS_X_OFFSET = 9;     // Guess (2 bytes)
    private static final int LEFT_AXIS_Y_OFFSET = 11;    // Guess (2 bytes)
    private static final int RIGHT_AXIS_X_OFFSET = 13;   // Guess (2 bytes)
    private static final int RIGHT_AXIS_Y_OFFSET = 15;   // Guess (2 bytes)
    // Offsets for sensor data (quat, accel, gyro) would follow, likely ~17 onwards
    private static final int ACCEL_X_OFFSET = 17;        // Highly speculative
    // ... add other sensor offsets if needed ...
    private static final int BATTERY_VOLTAGE_OFFSET = 1; // Guess for Battery Event
    private static final int CONNECTION_MSG_OFFSET = 1;  // Guess for Connection Event


    // Parse general event - might return UpdateEvent, ConnectionEvent, BatteryEvent, or null
    public static Object parse(byte[] data) {
        if (data == null || data.length == 0) {
            Log.w(TAG, "Received null or empty data array.");
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // --- Determine Event Type ---
        // ASSUMPTION: First byte indicates event type (from EventKey enum value)
        SteamControllerDefs.EventKey eventKey = SteamControllerDefs.EventKey.fromValue(buffer.get(EVENT_TYPE_BYTE_OFFSET) & 0xFF);

        if (eventKey == null) {
            // Maybe it's an implicit update event if no key matches?
            // Or maybe the first byte isn't the key. Re-evaluate this logic based on C++ code or sniffing.
            // Log.d(TAG, "No explicit event key found, assuming UPDATE if length is sufficient.");
            // Fallback: Try parsing as UpdateEvent if length is plausible
            if (data.length >= 20) { // Arbitrary minimum length for an update event
                 try {
                     return parseUpdateEventData(buffer, data.length);
                 } catch (IndexOutOfBoundsException e) {
                     Log.e(TAG,"Fallback parsing as UpdateEvent failed (Buffer underflow). Data length: " + data.length, e);
                     return null;
                 }
            } else {
                 Log.w(TAG, "Unknown event type or insufficient data length for implicit update: " + data.length);
                 return null; // Cannot determine type
            }
        }

        // --- Parse Based on Event Type ---
        try {
            switch (eventKey) {
                case UPDATE:
                    return parseUpdateEventData(buffer, data.length);
                case CONNECTION:
                    return parseConnectionEventData(buffer, data.length);
                case BATTERY:
                    return parseBatteryEventData(buffer, data.length);
                default:
                    Log.w(TAG, "Unhandled known event key: " + eventKey);
                    return null;
            }
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "Parser Error: Buffer underflow while parsing event type " + eventKey + ". Data length: " + data.length, e);
            return null;
        }
    }

    private static SteamControllerDefs.UpdateEvent parseUpdateEventData(ByteBuffer buffer, int dataLength) {
        // Example check, adjust based on real data structure
        // Need at least up to right Y axis (speculative offset 15 + 2 bytes = 17)
        if (dataLength < 17) {
            Log.w(TAG, "Parser(Update): Insufficient data length: " + dataLength);
            return null;
        }

        SteamControllerDefs.UpdateEvent event = new SteamControllerDefs.UpdateEvent();
        buffer.position(0); // Ensure buffer is at the start for this specific type

        // Read timestamp (Example: 3 bytes) - **VERIFY OFFSET & SIZE**
        // event.timeStamp = (buffer.get(TIMESTAMP_OFFSET) & 0xFF) |
        //                  ((buffer.get(TIMESTAMP_OFFSET + 1) & 0xFF) << 8) |
        //                  ((buffer.get(TIMESTAMP_OFFSET + 2) & 0xFF) << 16);

        // Read button mask (Example: 3 bytes) - **VERIFY OFFSET & SIZE**
        event.buttons = (buffer.get(BUTTONS_OFFSET) & 0xFF) |
                       ((buffer.get(BUTTONS_OFFSET + 1) & 0xFF) << 8) |
                       ((buffer.get(BUTTONS_OFFSET + 2) & 0xFF) << 16);

        event.leftTrigger = buffer.get(LEFT_TRIGGER_OFFSET);
        event.rightTrigger = buffer.get(RIGHT_TRIGGER_OFFSET);

        event.leftAxis.x = buffer.getShort(LEFT_AXIS_X_OFFSET);
        event.leftAxis.y = buffer.getShort(LEFT_AXIS_Y_OFFSET);
        event.rightAxis.x = buffer.getShort(RIGHT_AXIS_X_OFFSET);
        event.rightAxis.y = buffer.getShort(RIGHT_AXIS_Y_OFFSET);

        // TODO: Conditionally parse sensor data based on controller flags/data length
        // if (dataLength >= ACCEL_X_OFFSET + 6) { // Example check - **VERIFY OFFSETS**
        //     event.acceleration.x = buffer.getShort(ACCEL_X_OFFSET);
        //     event.acceleration.y = buffer.getShort(ACCEL_X_OFFSET + 2);
        //     event.acceleration.z = buffer.getShort(ACCEL_X_OFFSET + 4);
        // }
        // ... parse orientation (quat) and angular_velocity (gyro) similarly

        return event;
    }

     private static SteamControllerDefs.ConnectionEvent parseConnectionEventData(ByteBuffer buffer, int dataLength) {
         // ASSUMPTION: Connection message type is in the byte after the event key - **VERIFY OFFSET**
         if (dataLength < CONNECTION_MSG_OFFSET + 1) {
             Log.w(TAG, "Parser(Connection): Insufficient data length: " + dataLength);
             return null;
         }
         SteamControllerDefs.ConnectionEvent event = new SteamControllerDefs.ConnectionEvent();
         event.message = SteamControllerDefs.ConnectionEvent.MessageType.fromValue(buffer.get(CONNECTION_MSG_OFFSET) & 0xFF);
         return event;
     }

     private static SteamControllerDefs.BatteryEvent parseBatteryEventData(ByteBuffer buffer, int dataLength) {
         // ASSUMPTION: Battery voltage (uint16) is in the two bytes after the event key - **VERIFY OFFSET**
         if (dataLength < BATTERY_VOLTAGE_OFFSET + 2) {
              Log.w(TAG, "Parser(Battery): Insufficient data length: " + dataLength);
             return null;
         }
         SteamControllerDefs.BatteryEvent event = new SteamControllerDefs.BatteryEvent();
         event.voltage = buffer.getShort(BATTERY_VOLTAGE_OFFSET);
         return event;
     }
}