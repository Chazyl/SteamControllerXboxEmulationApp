package com.example.steamcontrollertoxboxapp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.bluetooth.BluetoothProfile;

import androidx.core.app.NotificationCompat;

import com.example.steamcontrollertoxboxapp.R; // Your R file
import com.example.steamcontrollertoxboxapp.ble.AndroidBleManager;
import com.example.steamcontrollertoxboxapp.core.ControllerMapper;
import com.example.steamcontrollertoxboxapp.core.SteamControllerDefs;
import com.example.steamcontrollertoxboxapp.core.SteamControllerParser;
import com.example.steamcontrollertoxboxapp.core.VirtualController;
import com.example.steamcontrollertoxboxapp.nativeimpl.UInputController;
import com.example.steamcontrollertoxboxapp.ui.MainActivity;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

public class EmulationService extends Service implements AndroidBleManager.ConnectionStateCallback {
    private static final String TAG = "EmulationService";
    private static final String CHANNEL_ID = "EmulationServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private final IBinder binder = new LocalBinder();
    private Handler serviceHandler;
    private HandlerThread handlerThread;

    private AndroidBleManager bleManager;
    private VirtualController virtualController;
    private ControllerMapper controllerMapper;

    private final BlockingQueue<byte[]> bleDataQueue = new LinkedBlockingQueue<>(128);
    private volatile boolean isProcessingRunning = false;
    private Thread processingThread;

    public enum ServiceState { IDLE, SCANNING, CONNECTING, CONNECTED, FAILED, NO_ROOT }
    private final AtomicReference<ServiceState> currentState = new AtomicReference<>(ServiceState.IDLE);
    private String connectedDeviceAddress = null;

    // --- Binder for Activity Communication ---
    public class LocalBinder extends Binder {
        public EmulationService getService() {
            return EmulationService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service onCreate");

        // Setup background thread for handling service tasks
        handlerThread = new HandlerThread("EmulationServiceThread", Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        Looper serviceLooper = handlerThread.getLooper();
        serviceHandler = new Handler(serviceLooper);

        // Initialize components on the background thread
        serviceHandler.post(() -> {
            bleManager = new AndroidBleManager(this, this);
            try {
                virtualController = new UInputController(); // This checks root internally
                controllerMapper = new ControllerMapper(virtualController);
                 Log.i(TAG, "Core components initialized.");
                 // Initial state check after controller init
                 if (virtualController instanceof UInputController) {
                    // Attempt to connect to uinput immediately to verify root early
                    try {
                        ((UInputController) virtualController).connect();
                        Log.i(TAG, "Virtual controller connected successfully (root verified).");
                        // Don't keep it connected yet, just verified access
                         ((UInputController) virtualController).disconnect();
                    } catch (SecurityException se) {
                         Log.e(TAG, "Root required but not available!");
                         updateState(ServiceState.NO_ROOT);
                    } catch (Exception e) {
                         Log.e(TAG, "Failed to initially connect virtual controller", e);
                         updateState(ServiceState.FAILED); // Indicate failure
                    }
                 }
            } catch (Exception | UnsatisfiedLinkError e) {
                Log.e(TAG, "Failed to initialize VirtualController (likely missing root or native lib issue)", e);
                 updateState(ServiceState.FAILED); // Or NO_ROOT specifically if identifiable
                 // Stop the service? Or let UI handle the FAILED state.
            }
        });

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service onStartCommand");
        // Keep the service running until it's explicitly stopped
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "Service onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
         Log.i(TAG, "Service onUnbind");
        // Allow re-binding
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Service onDestroy");
        isProcessingRunning = false; // Signal processing thread to stop
        if (processingThread != null) {
            processingThread.interrupt(); // Interrupt if blocked on queue
        }
        serviceHandler.post(() -> {
             Log.d(TAG, "Cleaning up resources on background thread...");
            if (bleManager != null) {
                try {
                    bleManager.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing BLE Manager", e);
                }
                bleManager = null;
            }
            if (virtualController != null) {
                try {
                    virtualController.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing Virtual Controller", e);
                }
                virtualController = null;
            }
            controllerMapper = null;
             Log.d(TAG, "Resource cleanup finished.");
             // Quit the handler thread's looper safely
             if (handlerThread != null) {
                 handlerThread.quitSafely();
                 handlerThread = null;
             }
        });

    }

    // --- Public Methods for Activity Interaction ---

    public ServiceState getCurrentState() {
        return currentState.get();
    }

     public String getConnectedDeviceAddress() {
         return connectedDeviceAddress;
     }

    public List<String> startScan(long duration) {
         if (bleManager == null || currentState.get() == ServiceState.NO_ROOT || currentState.get() == ServiceState.FAILED) {
             Log.w(TAG, "Cannot scan, BLE manager not ready or in error state.");
             return null; // Indicate error
         }
         updateState(ServiceState.SCANNING);
         updateNotification("Scanning for devices...");
         try {
             // Run scan on service thread, return results (might be empty initially)
             // A callback mechanism to update UI progressively would be better.
             return bleManager.scanForDevices(duration);
         } catch (SecurityException e) {
              Log.e(TAG, "Scan failed: Missing permissions.", e);
              updateState(ServiceState.FAILED);
              updateNotification("Scan failed (Permissions)");
              return null;
         } catch (Exception e) {
              Log.e(TAG, "Scan failed", e);
              updateState(ServiceState.FAILED);
              updateNotification("Scan failed");
              return null;
         }
    }

    public void connectToDevice(String address) {
         if (bleManager == null || virtualController == null || currentState.get() == ServiceState.NO_ROOT || currentState.get() == ServiceState.FAILED) {
             Log.w(TAG, "Cannot connect, core components not ready or in error state.");
             if (currentState.get() == ServiceState.NO_ROOT) updateNotification("Cannot connect: Root required");
             else updateNotification("Cannot connect: Service error");
             return;
         }

        serviceHandler.post(() -> {
            if (currentState.get() == ServiceState.CONNECTED && address.equals(connectedDeviceAddress)) {
                 Log.w(TAG, "Already connected to " + address);
                 return;
            }

            // Disconnect previous if any (ensure clean state)
            if (currentState.get() == ServiceState.CONNECTED || currentState.get() == ServiceState.CONNECTING) {
                 disconnectDeviceInternal();
            }

             updateState(ServiceState.CONNECTING);
             updateNotification("Connecting to " + address + "...");
             Log.i(TAG, "Initiating connection to " + address);
             try {
                 // Connect virtual controller first (requires root)
                 virtualController.connect();
                 Log.i(TAG, "Virtual controller connected for emulation.");

                 // Connect BLE device
                 bleManager.connect(address, bleDataQueue::offer); // Offer data to the queue
                 connectedDeviceAddress = address;

                  // Start the processing thread if not already running
                 startProcessingThread();

                 // State will be updated to CONNECTED via BLE callback
                 // updateState(ServiceState.CONNECTED); // Don't set here, wait for callback
                 // updateNotification("Connected to " + address);

             } catch (SecurityException e) {
                 Log.e(TAG, "Connection failed: Missing permissions or Root required.", e);
                 updateState(ServiceState.FAILED); // Or NO_ROOT?
                 updateNotification("Connection failed (Permissions/Root)");
                 disconnectDeviceInternal(); // Clean up partial connection
             } catch (Exception e) {
                 Log.e(TAG, "Connection failed", e);
                 updateState(ServiceState.FAILED);
                 updateNotification("Connection failed");
                 disconnectDeviceInternal(); // Clean up partial connection
             }
        });
    }

    public void disconnectDevice() {
         Log.i(TAG, "Disconnect requested by UI.");
        serviceHandler.post(this::disconnectDeviceInternal);
    }


    // --- Internal Methods ---

    private void disconnectDeviceInternal() {
         Log.i(TAG, "disconnectDeviceInternal called.");
        isProcessingRunning = false; // Signal processing thread to stop
         if (processingThread != null) {
             processingThread.interrupt(); // Interrupt if blocked
             processingThread = null; // Clear reference
         }
         bleDataQueue.clear(); // Clear any pending data

         if (bleManager != null && bleManager.isConnected()) {
             try {
                 bleManager.disconnect(); // Disconnect BLE
             } catch (Exception e) {
                  Log.e(TAG, "Error during BLE disconnect", e);
             }
         }
         if (virtualController != null) {
              try {
                  virtualController.disconnect(); // Disconnect virtual device
              } catch (Exception e) {
                   Log.e(TAG, "Error during virtual controller disconnect", e);
              }
         }
         updateState(ServiceState.IDLE);
         updateNotification("Disconnected");
         connectedDeviceAddress = null;
    }

     private void startProcessingThread() {
         if (isProcessingRunning) return; // Already running

         isProcessingRunning = true;
         processingThread = new Thread(() -> {
             Log.i(TAG, "Data processing thread started.");
             while (isProcessingRunning) {
                 try {
                     byte[] rawData = bleDataQueue.take(); // Blocks until data is available
                     if (!isProcessingRunning) break; // Check again after waking up

                     Object parsedEvent = SteamControllerParser.parse(rawData);

                     if (parsedEvent instanceof SteamControllerDefs.UpdateEvent) {
                         if (controllerMapper != null && virtualController != null) {
                             try {
                                 controllerMapper.processSteamEvent((SteamControllerDefs.UpdateEvent) parsedEvent);
                             } catch (IllegalStateException ise) {
                                 // Virtual controller likely disconnected
                                 Log.w(TAG, "Failed to update state, controller likely not connected: " + ise.getMessage());
                                 // Consider attempting to reconnect or stopping?
                             } catch (Exception e) {
                                  Log.e(TAG, "Error processing update event", e);
                             }
                         }
                     } else if (parsedEvent instanceof SteamControllerDefs.BatteryEvent) {
                          Log.i(TAG, "Controller Battery: " + ((SteamControllerDefs.BatteryEvent) parsedEvent).voltage + "mV");
                         // TODO: Optionally update notification or broadcast status
                     }
                     // Ignore ConnectionEvents here, handled by BLE manager callbacks setting service state

                 } catch (InterruptedException e) {
                     Log.i(TAG, "Processing thread interrupted.");
                     isProcessingRunning = false; // Ensure loop terminates
                     Thread.currentThread().interrupt();
                 } catch (Exception e) {
                     // Catch unexpected errors during parsing or processing
                     Log.e(TAG, "Unexpected error in processing thread", e);
                 }
             }
              Log.i(TAG, "Data processing thread finished.");
         }, "BleDataProcessor");
         processingThread.start();
     }

    private void updateState(ServiceState newState) {
         ServiceState oldState = currentState.getAndSet(newState);
         Log.i(TAG, "Service state changed: " + oldState + " -> " + newState);
         // TODO: Broadcast state change to Activity if needed, or Activity can poll getState()
    }

    // --- Notification Handling ---
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Emulation Service Channel",
                    NotificationManager.IMPORTANCE_LOW // Low importance for background task
            );
            serviceChannel.setDescription("Notification channel for the controller emulation service");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                 Log.d(TAG, "Notification channel created.");
            } else {
                 Log.e(TAG, "Failed to get NotificationManager.");
            }
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

         Log.d(TAG, "Creating notification with text: " + text);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Steam Controller Emulation")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_controller_notif) // Replace with your icon
                .setContentIntent(pendingIntent)
                .setOngoing(true) // Make it non-dismissable while service is running
                .setOnlyAlertOnce(true) // Don't sound/vibrate for updates
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        Notification notification = createNotification(text);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            try {
                manager.notify(NOTIFICATION_ID, notification);
                 Log.d(TAG, "Notification updated: " + text);
            } catch (SecurityException e) {
                 Log.e(TAG,"Failed to update notification, missing POST_NOTIFICATIONS permission?", e);
            }
        } else {
             Log.e(TAG, "Failed to get NotificationManager for update.");
        }
    }

    // --- ConnectionStateCallback Implementation ---
    @Override
    public void onConnectionStateChanged(int state, String deviceAddress) {
        serviceHandler.post(() -> {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                updateState(ServiceState.CONNECTED);
                updateNotification("Connected to " + deviceAddress);
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                // If we were connecting or connected, and now disconnected
                if (currentState.get() == ServiceState.CONNECTING || currentState.get() == ServiceState.CONNECTED) {
                    Log.w(TAG, "BLE connection lost unexpectedly.");
                    disconnectDeviceInternal(); // Clean up fully
                }
            }
        });
    }

    @Override
    public void onConnectionFailed(String deviceAddress, int errorCode) {
        serviceHandler.post(() -> {
            Log.e(TAG, "Connection failed to " + deviceAddress + " with error: " + errorCode);
            updateState(ServiceState.FAILED);
            updateNotification("Connection failed to " + deviceAddress);
            disconnectDeviceInternal(); // Clean up any partial connection
        });
    }

}
