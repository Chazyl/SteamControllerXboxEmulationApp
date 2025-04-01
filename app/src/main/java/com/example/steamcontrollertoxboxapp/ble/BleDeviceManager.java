package com.example.steamcontrollertoxboxapp.ble;

import java.util.UUID;
import java.util.function.Consumer;

public interface BleDeviceManager {
    // UUIDs for Steam Controller BLE service and characteristics
    UUID STEAM_CONTROLLER_SERVICE_UUID = UUID.fromString("00010000-0001-1000-8000-00805F9B34FB");
    UUID INPUT_CHARACTERISTIC_UUID = UUID.fromString("00010001-0001-1000-8000-00805F9B34FB");
    UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); // Standard CCCD UUID

    void scanForDevices();
    void onDeviceConnected(String deviceAddress);
    void onDeviceDisconnected(String deviceAddress);
    void onDataReceived(String deviceAddress, byte[] data);
    void onError(String deviceAddress, String errorMessage);

    void connect(String deviceAddress, Consumer<byte[]> dataConsumer) throws SecurityException, IllegalArgumentException;

    boolean isConnected();

    void writeCharacteristic(UUID serviceUuid, UUID characteristicUuid, byte[] data) throws Exception;

    void destroy() throws Exception;
}
