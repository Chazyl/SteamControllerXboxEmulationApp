package com.example.steamcontrollertoxboxapp.ui;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.steamcontrollertoxboxapp.R;
import com.example.steamcontrollertoxboxapp.ble.AndroidBleManager;
import com.example.steamcontrollertoxboxapp.service.EmulationService;

import java.util.ArrayList;
import java.util.List;
import com.example.steamcontrollertoxboxapp.ble.BleDevice;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private EmulationService emulationService;
    private boolean bound = false;
    private RecyclerView deviceList;
    private Button scanButton;
    private List<BleDevice> discoveredDevices = new ArrayList<>();
    private DeviceScanAdapter adapter;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            EmulationService.LocalBinder binder = (EmulationService.LocalBinder) service;
            emulationService = binder.getService();
            bound = true;
            emulationService.setStateListener(new EmulationService.StateListener() {
                @Override
                public void onStateChanged(EmulationService.ServiceState newState) {
                    updateScanButtonState();
                }
            });
            updateScanButtonState();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
            if (emulationService != null) {
                emulationService.setStateListener(null);
            }
            updateScanButtonState();
        }
    };

    private void updateScanButtonState() {
        runOnUiThread(() -> {
            boolean enabled = bound && emulationService != null && 
                emulationService.getCurrentState() != EmulationService.ServiceState.NO_ROOT &&
                emulationService.getCurrentState() != EmulationService.ServiceState.FAILED;
            scanButton.setEnabled(enabled);
        });
    }

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        boolean allGranted = true;
                        for (Boolean granted : permissions.values()) {
                            if (!granted) {
                                allGranted = false;
                                break;
                            }
                        }
                        if (allGranted) {
                            startScan();
                        } else {
                            Toast.makeText(this, "Permissions requises non accordées",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceList = findViewById(R.id.device_list);
        scanButton = findViewById(R.id.scan_button);

        adapter = new DeviceScanAdapter(discoveredDevices, this::connectToDevice);
        deviceList.setLayoutManager(new LinearLayoutManager(this));
        deviceList.setAdapter(adapter);

        scanButton.setOnClickListener(v -> checkPermissionsAndScan());

        // Démarrer et lier le service
        Intent intent = new Intent(this, EmulationService.class);
        startForegroundService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void checkPermissionsAndScan() {
        Log.d(TAG, "Scan button clicked");
        if (!bound || emulationService == null) {
            Log.w(TAG, "Cannot scan - service not bound");
            Toast.makeText(this, "Service not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] permissions = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
        };

        // Check if all permissions are already granted
        boolean allGranted = true;
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            Log.d(TAG, "All permissions already granted, starting scan");
            startScan();
        } else {
            Log.d(TAG, "Requesting missing permissions: " + String.join(", ", permissions));
            requestPermissionLauncher.launch(permissions);
        }
    }

    private void startScan() {
        if (bound && emulationService != null) {
            discoveredDevices.clear();
            adapter.notifyDataSetChanged();
            
            emulationService.startScan(5000, new AndroidBleManager.ScanListener() {
                @Override
                public void onDeviceDiscovered(String deviceAddress, String name) {
                    runOnUiThread(() -> {
                        discoveredDevices.add(new BleDevice(deviceAddress, name));
                        adapter.notifyItemInserted(discoveredDevices.size() - 1);
                    });
                }

                @Override
                public void onScanFinished() {
                    runOnUiThread(() -> 
                        Toast.makeText(MainActivity.this, 
                            "Scan completed", 
                            Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onScanFailed(int errorCode) {
                    runOnUiThread(() -> {
                        String errorMsg = "Scan failed";
                        if (errorCode == AndroidBleManager.ERROR_PERMISSION_DENIED) {
                            errorMsg = "Bluetooth permissions denied";
                        } else if (errorCode == AndroidBleManager.ERROR_BLUETOOTH_DISABLED) {
                            errorMsg = "Bluetooth is disabled";
                        }
                        Toast.makeText(MainActivity.this, 
                            errorMsg, 
                            Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }

    private void connectToDevice(String address) {
        if (bound && emulationService != null) {
            emulationService.connectToDevice(address);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            unbindService(connection);
            bound = false;
        }
    }
}
