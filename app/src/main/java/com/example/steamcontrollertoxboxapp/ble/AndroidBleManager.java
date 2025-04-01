package com.example.steamcontrollertoxboxapp.ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;


import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class AndroidBleManager implements BleDeviceManager {
    // Error codes
    public static final int ERROR_INVALID_STATE = 1;
    public static final int ERROR_SCAN_FAILED = 2;
    public static final int ERROR_CONNECTION_FAILED = 3;
    public static final int ERROR_DISCONNECTED = 4;
    // Error codes
    public static final int ERROR_PERMISSION_DENIED = 1;
    public static final int ERROR_BLUETOOTH_DISABLED = 2;
    // Interface implementation
    @Override
    public void onDeviceConnected(String deviceAddress) {
        if (connectionStateCallback != null) {
            connectionStateCallback.onConnectionStateChanged(BluetoothProfile.STATE_CONNECTED, deviceAddress);
        }
    }

    @Override
    public void onDeviceDisconnected(String deviceAddress) {
        if (connectionStateCallback != null) {
            connectionStateCallback.onConnectionStateChanged(BluetoothProfile.STATE_DISCONNECTED, deviceAddress);
        }
    }

    @Override
    public void onDataReceived(String deviceAddress, byte[] data) {
        if (dataConsumer != null) {
            dataConsumer.accept(data);
        }
    }

    @Override
    public void onError(String deviceAddress, String errorMessage) {
        Log.e(TAG, "BLE Error [" + deviceAddress + "]: " + errorMessage);
        if (connectionStateCallback != null) {
            connectionStateCallback.onConnectionFailed(deviceAddress, -1); // -1 for generic error
        }
    }

    private static final String TAG = "AndroidBleManager";

    public interface ConnectionStateCallback {
        void onConnectionStateChanged(int state, String deviceAddress);
        void onConnectionFailed(String deviceAddress, int errorCode);
    }

    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private final ConnectionStateCallback connectionStateCallback;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothGatt;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;
    private boolean isConnected = false;
    private int connectionRetries = 0;
    private static final int MAX_CONNECTION_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500; // Delay between retries

    private final Map<String, BluetoothDevice> discoveredDevices = new HashMap<>();
    private Consumer<byte[]> dataConsumer = null;
    private ScanCallback leScanCallback;

    // --- UUIDs (VERIFY THESE!) ---
    // Using the placeholders from the interface for consistency
    private static final UUID SERVICE_UUID = BleDeviceManager.STEAM_CONTROLLER_SERVICE_UUID;
    private static final UUID INPUT_CHAR_UUID = BleDeviceManager.INPUT_CHARACTERISTIC_UUID;
    private static final UUID CCCD_UUID = BleDeviceManager.CCCD_UUID;


    public AndroidBleManager(Context context, ConnectionStateCallback callback) {
        this.context = context.getApplicationContext();
        this.connectionStateCallback = callback;
        bluetoothManager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is not enabled or not available.");
            // Consider throwing an exception or signaling an error state
        } else {
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    @Override
    public void scanForDevices() {
        scanForDevices(10000, new ScanListener() {
            public void onDeviceDiscovered(String address, String name) {}
            public void onScanFinished() {}
            public void onScanFailed(int errorCode) {}
        });
    }

    public interface ScanListener {
        void onDeviceDiscovered(String address, String name);
        void onScanFinished();
        void onScanFailed(int errorCode);
    }

    public void scanForDevices(long scanDurationMillis, ScanListener callback) throws SecurityException {
        if (!hasScanPermission()) {
            Log.e(TAG, "Missing Bluetooth Scan Permission!");
            callback.onScanFailed(ERROR_PERMISSION_DENIED);
            throw new SecurityException("Missing Bluetooth Scan Permission");
        }
        if (bleScanner == null) {
            Log.e(TAG, "BLE Scanner not initialized (BT disabled?).");
            callback.onScanFailed(ERROR_BLUETOOTH_DISABLED);
            return;
        }

        discoveredDevices.clear(); // Clear previous results
        leScanCallback = new ScanCallback() {
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice device = result.getDevice();
                String deviceAddress = device.getAddress();
                String deviceName = device.getName() != null ? device.getName() : "Unknown";
                if (!discoveredDevices.containsKey(deviceAddress)) {
                    Log.d(TAG, "Device found: " + deviceName + " [" + deviceAddress + "]");
                    discoveredDevices.put(deviceAddress, device);
                    callback.onDeviceDiscovered(deviceAddress, deviceName);
                }
            }

            public void onScanFinished() {
                callback.onScanFinished();
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
                for (ScanResult result : results) {
                    BluetoothDevice device = result.getDevice();
                    String deviceAddress = device.getAddress();
                    String deviceName = device.getName() != null ? device.getName() : "Unknown";
                    if (!discoveredDevices.containsKey(deviceAddress)) {
                        Log.d(TAG, "Batch Device found: " + deviceName + " [" + deviceAddress + "]");
                        discoveredDevices.put(deviceAddress, device);
                        callback.onDeviceDiscovered(deviceAddress, deviceName);
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e(TAG, "BLE Scan Failed with error code: " + errorCode);
                isScanning = false;
                callback.onScanFailed(errorCode);
            }
        };

        // Steam Controller specific scan filters
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder()
            .setServiceUuid(new ParcelUuid(SERVICE_UUID))
            .build());
            
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        Log.i(TAG, "Starting BLE scan...");
        isScanning = true;
        bleScanner.startScan(filters, settings, leScanCallback);

        handler.postDelayed(() -> {
            stopScan();
            callback.onScanFinished();
        }, scanDurationMillis);
    }

    public void stopScan() {
        if (!hasScanPermission()) {
            Log.w(TAG, "Attempted to stop scan without permission.");
            // Don't throw, just log, as it might be called automatically.
            return;
        }
        if (bleScanner == null) return; // BT might have been disabled

        if (isScanning && leScanCallback != null) {
            Log.i(TAG, "Stopping BLE scan.");
            bleScanner.stopScan(leScanCallback);
            isScanning = false;
            leScanCallback = null; // Release callback
        }
    }

    @Override
    public void connect(String deviceAddress, Consumer<byte[]> dataConsumer) throws SecurityException, IllegalArgumentException {
        if (!hasConnectPermission()) {
            Log.e(TAG, "Missing Bluetooth Connect Permission!");
            throw new SecurityException("Missing Bluetooth Connect Permission");
        }

        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        if (device == null) {
            Log.w(TAG, "Device not found. Unable to connect.");
            throw new IllegalArgumentException("Device with address " + deviceAddress + " not found");
        }

        // Disconnect previous connection if any
        if (bluetoothGatt != null) {
            Log.w(TAG, "Disconnecting previous GATT connection before connecting to new device.");
            try {
                close(); // Use the close method to ensure proper cleanup
            } catch (Exception e) {
                Log.e(TAG, "Error closing previous GATT connection", e);
            }
        }


        this.dataConsumer = dataConsumer;
        this.connectionRetries = 0; // Reset retries on new connect attempt
        Log.i(TAG, "Attempting to connect to " + device.getName() + " [" + deviceAddress + "] (Attempt " + (connectionRetries + 1) + ")");

        // Connect on the main thread (callbacks will arrive on binder threads)
        // Use handler to ensure GATT operations are on the main thread if needed, though connectGatt is okay off-main
        handler.post(() -> {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            if (bluetoothGatt == null) {
                Log.e(TAG, "device.connectGatt returned null!");
                // Handle this failure case, perhaps retry or notify UI
            }
        });
        // Note: autoConnect=false is generally preferred for active connections
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public void writeCharacteristic(UUID serviceUuid, UUID characteristicUuid, byte[] data) throws Exception {
        if (bluetoothGatt == null || !isConnected) {
            throw new IllegalStateException("Not connected to a device.");
        }
        if (!hasConnectPermission()) {
            throw new SecurityException("Missing Bluetooth Connect Permission");
        }

        BluetoothGattService service = bluetoothGatt.getService(serviceUuid);
        if (service == null) {
            throw new IllegalArgumentException("Service not found: " + serviceUuid);
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
        if (characteristic == null) {
            throw new IllegalArgumentException("Characteristic not found: " + characteristicUuid);
        }

        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0 &&
                (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
            throw new UnsupportedOperationException("Characteristic does not support writing.");
        }

        characteristic.setValue(data);
        // Prefer WRITE over WRITE_NO_RESPONSE if available for confirmation, but NO_RESPONSE might be needed
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); // Or WRITE_TYPE_NO_RESPONSE

        boolean success = bluetoothGatt.writeCharacteristic(characteristic);
        if (!success) {
            throw new IOException("Failed to initiate characteristic write.");
        }
        Log.d(TAG, "Initiated write to characteristic: " + characteristicUuid);
    }

    @Override
    public void destroy() throws Exception {
        if (bluetoothGatt != null) {
            if (hasConnectPermission()) {
                Log.i(TAG, "Closing GATT connection for " + bluetoothGatt.getDevice().getAddress());
                bluetoothGatt.close(); // Release resources
            } else {
                Log.w(TAG, "Cannot close GATT without Connect permission, resources might leak.");
            }
            bluetoothGatt = null; // Nullify the reference
            isConnected = false;
            dataConsumer = null;
        }
        stopScan(); // Ensure scanning is stopped
    }

    // --- GATT Callback ---
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String deviceAddress = gatt.getDevice().getAddress();
            if (!hasConnectPermission()) {
                Log.e(TAG, "GATT callback received without Connect permission!");
                // Attempt to close gracefully anyway, though it might fail
                try { gatt.close(); } catch (SecurityException se) { /* ignore */ }
                bluetoothGatt = null;
                isConnected = false;
                return;
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to GATT server: " + deviceAddress);
                    isConnected = true;
                    connectionRetries = 0; // Reset retries on successful connection
                    if (connectionStateCallback != null) {
                        connectionStateCallback.onConnectionStateChanged(newState, deviceAddress);
                    }
                    // Request higher MTU for better throughput (Android default is often 23 bytes)
                    // 512 is a common value, but the peripheral might negotiate lower.
                    Log.i(TAG, "Requesting MTU change to 512");
                    if (!gatt.requestMtu(512)) {
                        Log.w(TAG, "Failed to initiate MTU request.");
                        // Proceed with service discovery even if MTU request fails initially
                        Log.i(TAG, "Attempting to start service discovery: " + gatt.discoverServices());
                    }
                    // Service discovery will now be initiated in onMtuChanged or if requestMtu fails
                    // TODO: Notify UI/Service about connection attempt progress
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from GATT server: " + deviceAddress);
                    isConnected = false;
                    if (connectionStateCallback != null) {
                        connectionStateCallback.onConnectionStateChanged(newState, deviceAddress);
                    }
                    try {
                        close(); // Ensure resources are released on disconnect
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing GATT after disconnect", e);
                    }
                }
            } else {
                Log.w(TAG, "GATT Error onConnectionStateChange: " + deviceAddress + " Status: " + status + " newState: " + newState);
                isConnected = false;

                // --- Retry Logic ---
                if (connectionRetries < MAX_CONNECTION_RETRIES) {
                    connectionRetries++;
                    Log.w(TAG, "Connection attempt failed. Retrying (" + connectionRetries + "/" + MAX_CONNECTION_RETRIES + ") after delay...");
                    final BluetoothDevice device = gatt.getDevice(); // Get device for retry
                    try {
                        // Close the failed connection before retrying
                        if (hasConnectPermission()) gatt.close();
                        bluetoothGatt = null; // Nullify GATT object
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing GATT before retry", e);
                    }
                    // Schedule retry after a delay
                    handler.postDelayed(() -> {
                        Log.i(TAG, "Retrying connection to " + device.getAddress() + " (Attempt " + (connectionRetries + 1) + ")");
                        if (hasConnectPermission()) {
                            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
                            if (bluetoothGatt == null) {
                                Log.e(TAG, "device.connectGatt returned null on retry!");
                                // Handle final failure if retry also fails immediately
                            }
                        } else {
                            Log.e(TAG, "Cannot retry connection without permission.");
                        }
                    }, RETRY_DELAY_MS);

                } else {
                    Log.e(TAG, "GATT connection failed after " + MAX_CONNECTION_RETRIES + " retries. Status: " + status);
                    if (connectionStateCallback != null) {
                        connectionStateCallback.onConnectionFailed(deviceAddress, status);
                    }
                    try {
                        close(); // Clean up definitively on final failure
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing GATT after final connection failure", e);
                    }
                }
                // --- End Retry Logic ---
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (!hasConnectPermission()) { Log.e(TAG, "GATT callback received without Connect permission!"); return; }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered for: " + gatt.getDevice().getAddress());
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service == null) {
                    Log.e(TAG, "Steam Controller Service NOT found ("+SERVICE_UUID+"). Check UUID!");
                    // TODO: Notify UI/Service
                    return;
                }
                BluetoothGattCharacteristic inputCharacteristic = service.getCharacteristic(INPUT_CHAR_UUID);
                if (inputCharacteristic == null) {
                    Log.e(TAG, "Steam Controller Input Characteristic NOT found ("+INPUT_CHAR_UUID+"). Check UUID!");
                    // TODO: Notify UI/Service
                    return;
                }

                // Enable notifications for the input characteristic
                boolean notificationSet = false;
                if ((inputCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    Log.i(TAG, "Setting up notifications for input characteristic");
                    if (bluetoothGatt.setCharacteristicNotification(inputCharacteristic, true)) {
                        BluetoothGattDescriptor descriptor = inputCharacteristic.getDescriptor(CCCD_UUID);
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            if (bluetoothGatt.writeDescriptor(descriptor)) {
                                notificationSet = true;
                                Log.i(TAG, "Successfully enabled notifications");
                            } else {
                                Log.e(TAG, "Failed to write descriptor for notifications");
                            }
                        } else {
                            Log.e(TAG, "CCCD descriptor not found");
                        }
                    } else {
                        Log.e(TAG, "Failed to set characteristic notification");
                    }
                } else {
                    Log.e(TAG, "Characteristic does not support notifications");
                }

                if (!notificationSet) {
                    Log.e(TAG, "Failed to setup notifications - input data won't be received");
                    if (connectionStateCallback != null) {
                        connectionStateCallback.onConnectionFailed(gatt.getDevice().getAddress(), -1);
                    }
                }
            } else {
                Log.e(TAG, "Service discovery failed: " + status);
                if (connectionStateCallback != null) {
                    connectionStateCallback.onConnectionFailed(gatt.getDevice().getAddress(), status);
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (!hasConnectPermission()) { Log.e(TAG, "GATT callback received without Connect permission!"); return; }
            
            if (characteristic.getUuid().equals(INPUT_CHAR_UUID)) {
                byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    if (dataConsumer != null) {
                        dataConsumer.accept(data);
                    }
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (!hasConnectPermission()) { Log.e(TAG, "GATT callback received without Connect permission!"); return; }
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write successful: " + descriptor.getUuid());
            } else {
                Log.e(TAG, "Descriptor write failed: " + status);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (!hasConnectPermission()) { Log.e(TAG, "GATT callback received without Connect permission!"); return; }
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "MTU changed to: " + mtu);
                // Now that MTU is set, discover services if we haven't already
                if (!gatt.discoverServices()) {
                    Log.e(TAG, "Failed to start service discovery after MTU change");
                }
            } else {
                Log.w(TAG, "MTU change failed: " + status);
                // Still attempt service discovery even if MTU change failed
                if (!gatt.discoverServices()) {
                    Log.e(TAG, "Failed to start service discovery after MTU change failure");
                }
            }
        }
    };

    private boolean hasScanPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasConnectPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }
}
