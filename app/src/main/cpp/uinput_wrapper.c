#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <linux/input.h>
#include <linux/uinput.h> // Requires kernel headers available during build
#include <android/log.h> // For Android logging

// Define Log Tag for Android Logging
#define LOG_TAG "uinput_wrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// --- JNI Function Declarations (matching UInputController.java) ---
// Package: com.example.steamcontrollertoxboxapp.nativeimpl
// Class:   UInputController
// Methods: nativeInit, nativeClose, nativeSendEvent

JNIEXPORT jint JNICALL Java_com_example_steamcontrollertoxboxapp_nativeimpl_UInputController_nativeInit
  (JNIEnv *, jobject);

JNIEXPORT void JNICALL Java_com_example_steamcontrollertoxboxapp_nativeimpl_UInputController_nativeClose
  (JNIEnv *, jobject, jint);

JNIEXPORT jboolean JNICALL Java_com_example_steamcontrollertoxboxapp_nativeimpl_UInputController_nativeSendEvent
  (JNIEnv *, jobject, jint, jint, jint, jint);


// --- Helper Function to send uinput events ---
static int send_event(int fd, int type, int code, int value) {
   struct input_event ev;

   // Check for valid fd first
   if (fd < 0) {
       LOGE("send_event: Invalid file descriptor %d", fd);
       return -1; // Or another specific error code like -EBADF
   }

   memset(&ev, 0, sizeof(ev));
   ev.type = type;
   ev.code = code;
   ev.value = value;
   // Kernel usually fills in ev.time

   if (write(fd, &ev, sizeof(ev)) < 0) {
      // Use strerror to get a meaningful error message
      LOGE("send_event: Error writing event (type=%d, code=%d, value=%d): %s (errno %d)",
           type, code, value, strerror(errno), errno);
      return -errno; // Return negative errno
   }
   // LOGD("send_event: Sent type=%d, code=%d, value=%d", type, code, value); // Verbose
   return 0; // Success
}

// --- JNI Implementation: nativeInit ---
JNIEXPORT jint JNICALL Java_com_example_steamcontrollertoxboxapp_nativeimpl_UInputController_nativeInit
  (JNIEnv *env, jobject thisObject) {

    LOGI("nativeInit: Attempting to open /dev/uinput...");
    // Try to open uinput device (try multiple paths for compatibility)
    const char *uinput_paths[] = {
        "/dev/uinput",
        "/dev/input/uinput",
        "/dev/misc/uinput"
    };
    
    int fd = -1;
    for (int i = 0; i < sizeof(uinput_paths)/sizeof(uinput_paths[0]); i++) {
        fd = open(uinput_paths[i], O_WRONLY | O_NONBLOCK);
        if (fd >= 0) {
            LOGI("nativeInit: Opened %s successfully (fd=%d)", uinput_paths[i], fd);
            break;
        }
        LOGW("nativeInit: Failed to open %s: %s", uinput_paths[i], strerror(errno));
    }
    if (fd < 0) {
        LOGE("nativeInit: Failed to open any uinput device. Check ROOT permissions.");
        return -ENODEV; // Return "No such device" error
    }

    // --- Configure the virtual device (Simulating Xbox 360 Controller) ---
    // Use uinput_user_dev for broader kernel compatibility than uinput_setup
    struct uinput_user_dev uidev;
    memset(&uidev, 0, sizeof(uidev));

    // Device Name
    snprintf(uidev.name, UINPUT_MAX_NAME_SIZE, "Xbox 360 Controller (Virtual)");

    // IDs - Use Microsoft's Vendor ID and a common Xbox 360 Product ID
    uidev.id.bustype = BUS_VIRTUAL; // Or BUS_USB / BUS_BLUETOOTH if needed by specific games
    uidev.id.vendor  = 0x045e;     // Microsoft Corp.
    uidev.id.product = 0x028e;     // Xbox 360 Controller
    uidev.id.version = 0x0110;     // Version 1.10 (example)

    // --- Enable required event types ---
    LOGD("nativeInit: Setting event bits...");
    if (ioctl(fd, UI_SET_EVBIT, EV_SYN) < 0 ||
        ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0 ||
        ioctl(fd, UI_SET_EVBIT, EV_ABS) < 0) {
        LOGE("nativeInit: Error setting EV bits: %s", strerror(errno));
        close(fd);
        return -errno;
    }

    // --- Enable buttons (KEY_* codes matching UInputConstants.java) ---
    LOGD("nativeInit: Setting key bits...");
    ioctl(fd, UI_SET_KEYBIT, BTN_A);
    ioctl(fd, UI_SET_KEYBIT, BTN_B);
    ioctl(fd, UI_SET_KEYBIT, BTN_X);
    ioctl(fd, UI_SET_KEYBIT, BTN_Y);
    ioctl(fd, UI_SET_KEYBIT, BTN_TL);      // LB
    ioctl(fd, UI_SET_KEYBIT, BTN_TR);      // RB
    ioctl(fd, UI_SET_KEYBIT, BTN_SELECT);  // Back
    ioctl(fd, UI_SET_KEYBIT, BTN_START);   // Start
    ioctl(fd, UI_SET_KEYBIT, BTN_MODE);    // Guide
    ioctl(fd, UI_SET_KEYBIT, BTN_THUMBL);  // LS Click
    ioctl(fd, UI_SET_KEYBIT, BTN_THUMBR);  // RS Click
    // DPad will be handled via ABS_HAT0X/Y axes

    // --- Enable axes (ABS_* codes matching UInputConstants.java) ---
    LOGD("nativeInit: Setting abs bits and ranges...");
    // Left Stick X/Y (-32768 to 32767 is standard for joysticks)
    ioctl(fd, UI_SET_ABSBIT, ABS_X);
    uidev.absmin[ABS_X] = -32768;
    uidev.absmax[ABS_X] = 32767;
    uidev.absfuzz[ABS_X] = 16; // Optional deadzone suggestion
    uidev.absflat[ABS_X] = 128;// Optional deadzone suggestion
    ioctl(fd, UI_SET_ABSBIT, ABS_Y);
    uidev.absmin[ABS_Y] = -32768;
    uidev.absmax[ABS_Y] = 32767;
    uidev.absfuzz[ABS_Y] = 16;
    uidev.absflat[ABS_Y] = 128;

    // Right Stick X/Y
    ioctl(fd, UI_SET_ABSBIT, ABS_RX);
    uidev.absmin[ABS_RX] = -32768;
    uidev.absmax[ABS_RX] = 32767;
    uidev.absfuzz[ABS_RX] = 16;
    uidev.absflat[ABS_RX] = 128;
    ioctl(fd, UI_SET_ABSBIT, ABS_RY);
    uidev.absmin[ABS_RY] = -32768;
    uidev.absmax[ABS_RY] = 32767;
    uidev.absfuzz[ABS_RY] = 16;
    uidev.absflat[ABS_RY] = 128;

    // Triggers (ABS_Z/ABS_RZ, range 0 to 255 is common for triggers)
    ioctl(fd, UI_SET_ABSBIT, ABS_Z); // LT
    uidev.absmin[ABS_Z] = 0;
    uidev.absmax[ABS_Z] = 255;
    ioctl(fd, UI_SET_ABSBIT, ABS_RZ); // RT
    uidev.absmin[ABS_RZ] = 0;
    uidev.absmax[ABS_RZ] = 255;

    // DPad Hat (ABS_HAT0X/Y, range -1, 0, 1)
    ioctl(fd, UI_SET_ABSBIT, ABS_HAT0X);
    uidev.absmin[ABS_HAT0X] = -1;
    uidev.absmax[ABS_HAT0X] = 1;
    ioctl(fd, UI_SET_ABSBIT, ABS_HAT0Y);
    uidev.absmin[ABS_HAT0Y] = -1;
    uidev.absmax[ABS_HAT0Y] = 1;

    // --- Write device info ---
    LOGD("nativeInit: Writing device info...");
    if (write(fd, &uidev, sizeof(uidev)) < 0) {
        LOGE("nativeInit: Error writing device info: %s", strerror(errno));
        close(fd);
        return -errno;
    }

    // --- Create the virtual device ---
    LOGD("nativeInit: Creating device node...");
    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        LOGE("nativeInit: Error creating virtual device node: %s", strerror(errno));
        close(fd);
        return -errno;
    }

    LOGI("nativeInit: Virtual input device '%s' created successfully (fd=%d)", uidev.name, fd);
    return fd; // Return the file descriptor on success
}

// --- JNI Implementation: nativeClose ---
JNIEXPORT void JNICALL Java_com_example_steamcontrollertoxboxapp_nativeimpl_UInputController_nativeClose
  (JNIEnv *env, jobject thisObject, jint fd) {

    if (fd < 0) {
        LOGW("nativeClose: Attempted to close invalid fd %d", fd);
        return;
    }

    LOGI("nativeClose: Destroying virtual device (fd=%d)...", fd);
    // Destroy the uinput device first
    if (ioctl(fd, UI_DEV_DESTROY) < 0) {
        // Log error but continue to close fd
        LOGE("nativeClose: Error destroying virtual device node: %s (errno %d)", strerror(errno), errno);
    } else {
         LOGD("nativeClose: UI_DEV_DESTROY successful.");
    }

    // Close the file descriptor
    if (close(fd) < 0) {
         LOGE("nativeClose: Error closing file descriptor %d: %s (errno %d)", fd, strerror(errno), errno);
    } else {
         LOGI("nativeClose: Closed file descriptor %d.", fd);
    }
}

// --- JNI Implementation: nativeSendEvent ---
JNIEXPORT jboolean JNICALL Java_com_example_steamcontrollertoxboxapp_nativeimpl_UInputController_nativeSendEvent
  (JNIEnv *env, jobject thisObject, jint fd, jint type, jint code, jint value) {

    if (send_event(fd, type, code, value) < 0) {
        // Error already logged by send_event helper
        return JNI_FALSE; // Indicate failure
    }
    return JNI_TRUE; // Indicate success
}
