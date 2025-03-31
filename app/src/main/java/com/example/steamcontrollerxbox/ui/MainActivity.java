package com.example.steamcontrollerxbox.ui;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.steamcontrollerxbox.R; // Your R file
import com.example.steamcontrollerxbox.service.EmulationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final long SCAN_PERIOD = 10000; // Scan for 10 seconds

    private EmulationService emulationService;
    private boolean isServiceBound = false;

    private Button btnScan, btnDisconnect;
    private TextView tvStatus;
    private RecyclerView rvDevices;
    private DeviceScanAdapter deviceAdapter;
    private final List<String> discoveredDeviceAddresses = new ArrayList<>();

    // --- Permission Handling ---
    private final ActivityResultLauncher<String[]> requestMultiplePermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = true;
                for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                    if (!entry.getValue()) {
                        allGranted = false;
                        Log.w(TAG, "Permission Denied: " + entry.getKey());
                    }
                }
                if (allGranted) {
                    Log.i(TAG, "All required permissions granted.");
                    startScan(); // Proceed with scan after getting permissions
                } else {
                    Log.e(TAG, "One or more permissions were denied. App cannot function.");
                    Toast.makeText(this, "Required permissions denied. Cannot scan or connect.", Toast.LENGTH_LONG).show();
                    updateUiState(EmulationService.ServiceState.FAILED); // Indicate failure due to permissions
                }
            });

    private final ActivityResultLauncher<Intent> requestBluetoothEnableLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Log.i(TAG, "Bluetooth has been enabled.");
                    // Now check permissions and proceed
                    checkAndRequestPermissions();
                } else {
                    Log.e(TAG, "Bluetooth was not enabled. App cannot function.");
                    Toast.makeText(this, "Bluetooth is required.", Toast.LENGTH_LONG).show();
                     updateUiState(EmulationService.ServiceState.FAILED);
                }
            });

    // --- Service Connection ---
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            EmulationService.LocalBinder binder = (EmulationService.LocalBinder) service;
            emulationService = binder.getService();
            isServiceBound = true;
            Log.i(TAG, "EmulationService connected.");
            // Update UI based on current service state
            updateUiState(emulationService.getCurrentState());
            // Check initial root status
             if(emulationService.getCurrentState() == EmulationService.ServiceState.NO_ROOT) {
                 Toast.makeText(MainActivity.this, "ROOT ACCESS REQUIRED!", Toast.LENGTH_LONG).show();
             }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isServiceBound = false;
            emulationService = null;
            Log.w(TAG, "EmulationService disconnected unexpectedly.");
             updateUiState(EmulationService.ServiceState.FAILED); // Or IDLE?
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnScan = findViewById(R.id.btn_scan);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        tvStatus = findViewById(R.id.tv_status);
        rvDevices = findViewById(R.id.rv_devices);

        setupRecyclerView();

        btnScan.setOnClickListener(v -> checkAndRequestPermissions()); // Start permission check flow first
        btnDisconnect.setOnClickListener(v -> disconnect());

         // Start and bind to the service
         Intent serviceIntent = new Intent(this, EmulationService.class);
         ContextCompat.startForegroundService(this, serviceIntent); // Start as foreground
         bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

         updateUiState(EmulationService.ServiceState.IDLE); // Initial UI state
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
             Log.d(TAG, "Service unbound.");
            // Note: Service might still run in background if started as foreground
        }
    }

    private void setupRecyclerView() {
        deviceAdapter = new DeviceScanAdapter(discoveredDeviceAddresses, this::connectToDevice);
        rvDevices.setLayoutManager(new LinearLayoutManager(this));
        rvDevices.setAdapter(deviceAdapter);
    }

    private void checkAndRequestPermissions() {
        // 1. Check Bluetooth Enabled
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device.", Toast.LENGTH_LONG).show();
             updateUiState(EmulationService.ServiceState.FAILED);
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            requestBluetoothEnableLauncher.launch(enableBtIntent);
            return; // Wait for result
        }

        // 2. Check Permissions
        List<String> permissionsToRequest = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
             // Optional: Notification permission for foreground service display
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                       permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
                  }
             }
        } else {
            // Before Android 12
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
             Log.i(TAG, "Requesting permissions: " + permissionsToRequest);
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toArray(new String[0]));
        } else {
             Log.i(TAG, "All permissions already granted.");
            startScan(); // All permissions granted, proceed with scan
        }
    }

    private void startScan() {
        if (!isServiceBound || emulationService == null) {
            Toast.makeText(this, "Service not ready.", Toast.LENGTH_SHORT).show();
            return;
        }
         if (emulationService.getCurrentState() == EmulationService.ServiceState.NO_ROOT) {
             Toast.makeText(this, "Cannot Scan: Root Required!", Toast.LENGTH_LONG).show();
              updateUiState(EmulationService.ServiceState.NO_ROOT);
             return;
         }
         if (emulationService.getCurrentState() == EmulationService.ServiceState.FAILED) {
              Toast.makeText(this, "Cannot Scan: Service in failed state.", Toast.LENGTH_LONG).show();
              updateUiState(EmulationService.ServiceState.FAILED);
              return;
         }


        Log.d(TAG, "Scan button clicked.");
        discoveredDeviceAddresses.clear();
        deviceAdapter.notifyDataSetChanged();
         updateUiState(EmulationService.ServiceState.SCANNING);

        List<String> initialDevices = emulationService.startScan(SCAN_PERIOD);
        if (initialDevices == null) {
            // Error handled by service, UI state updated
            Log.e(TAG,"Scan initiation failed (likely permissions)");
        } else {
             // Populate initial list (though a callback from service is better)
             discoveredDeviceAddresses.addAll(initialDevices);
             deviceAdapter.notifyDataSetChanged();
             // Service will update state back to IDLE after scan period, or UI can poll
             // For simplicity, we'll rely on the service state updates for now.
        }
         // TODO: Implement a callback from Service to update the list dynamically during scan
    }

    private void connectToDevice(String deviceAddress) {
        if (!isServiceBound || emulationService == null) {
            Toast.makeText(this, "Service not ready.", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.i(TAG, "Attempting to connect to: " + deviceAddress);
         updateUiState(EmulationService.ServiceState.CONNECTING); // Show immediate feedback
        emulationService.connectToDevice(deviceAddress);
        // Service will update its state, UI will reflect it via polling or callback
    }

    private void disconnect() {
        if (!isServiceBound || emulationService == null) {
            Toast.makeText(this, "Service not ready.", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.i(TAG, "Disconnect button clicked.");
        emulationService.disconnectDevice();
         updateUiState(EmulationService.ServiceState.IDLE); // Reflect disconnect immediately
    }

    // Update UI elements based on the service's state
    private void updateUiState(EmulationService.ServiceState state) {
        runOnUiThread(() -> {
            Log.d(TAG, "Updating UI for state: " + state);
            switch (state) {
                case IDLE:
                    tvStatus.setText(R.string.status_idle);
                    btnScan.setEnabled(true);
                    btnDisconnect.setEnabled(false);
                    rvDevices.setVisibility(View.VISIBLE);
                    break;
                case SCANNING:
                    tvStatus.setText(R.string.status_scanning);
                    btnScan.setEnabled(false); // Disable scan while scanning
                    btnDisconnect.setEnabled(false);
                    rvDevices.setVisibility(View.VISIBLE);
                    break;
                case CONNECTING:
                    tvStatus.setText(R.string.status_connecting);
                    btnScan.setEnabled(false);
                    btnDisconnect.setEnabled(false); // Allow disconnect during connection? Maybe.
                    rvDevices.setVisibility(View.GONE); // Hide list while connecting
                    break;
                case CONNECTED:
                    String addr = isServiceBound && emulationService != null ? emulationService.getConnectedDeviceAddress() : "device";
                    tvStatus.setText(getString(R.string.status_connected, addr != null ? addr : "Unknown"));
                    btnScan.setEnabled(false);
                    btnDisconnect.setEnabled(true);
                    rvDevices.setVisibility(View.GONE);
                    break;
                case FAILED:
                    tvStatus.setText(R.string.status_failed);
                    btnScan.setEnabled(true); // Allow retry?
                    btnDisconnect.setEnabled(false);
                    rvDevices.setVisibility(View.VISIBLE);
                    break;
                case NO_ROOT:
                    tvStatus.setText(R.string.status_no_root);
                    btnScan.setEnabled(false); // Cannot function without root
                    btnDisconnect.setEnabled(false);
                    rvDevices.setVisibility(View.GONE);
                    break;
            }
        });
    }

     // TODO: Implement a mechanism (e.g., BroadcastReceiver, LiveData with ViewModel, Handler)
     // for the Service to push state updates and discovered devices to the Activity
     // instead of relying solely on the initial state check in onServiceConnected.
     // For now, the UI state might lag slightly behind the actual service state.
}