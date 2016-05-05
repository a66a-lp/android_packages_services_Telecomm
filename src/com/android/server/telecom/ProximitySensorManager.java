/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.server.telecom;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemProperties;

import cyanogenmod.hardware.CMHardwareManager;

/**
 * This class manages the proximity sensor and allows callers to turn it on and off.
 */
public class ProximitySensorManager extends CallsManagerListenerBase {
    private static final String TAG = ProximitySensorManager.class.getSimpleName();

    private final PowerManager.WakeLock mProximityWakeLock;
    private boolean mWasTapToWakeEnabled = false;
    private final CMHardwareManager mHardware;

    // MTK/Meizu workaround
    private static final boolean mIsMTKHardware = !(SystemProperties.get("ro.mediatek.platform", "").equals(""));

    public ProximitySensorManager(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mHardware = CMHardwareManager.getInstance(context);
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            mProximityWakeLock = pm.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG);
        } else {
            mProximityWakeLock = null;
        }
        Log.d(this, "onCreate: mProximityWakeLock: ", mProximityWakeLock);
    }

    @Override
    public void onCallRemoved(Call call) {
        if (CallsManager.getInstance().getCalls().isEmpty()) {
            Log.i(this, "All calls removed, resetting proximity sensor to default state");
            turnOff(true);

            // MTK has screenOnImmediately set to false, at least on Meizu MX4
            // passing true would result in tap-to-wake or proximity sensor
            // stopping working if remote hung up.
            // However it seems only calling turnOff with false can't eliminate
            // all cases of malfunctioning... so we'd rather keep the original
            // sources and re-calibrate instead.
            if (mIsMTKHardware) {
                // call into calibration service if one exists
                // hopefully none will run into namespace collision with me...
                SystemProperties.set("ctl.start", "ps_calibrate");
            }
        }
        super.onCallRemoved(call);
    }

    /**
     * Turn the proximity sensor on.
     */
    void turnOn() {
        if (CallsManager.getInstance().getCalls().isEmpty()) {
            Log.w(this, "Asking to turn on prox sensor without a call? I don't think so.");
            return;
        }

        if (mProximityWakeLock == null) {
            return;
        }
        if (!mProximityWakeLock.isHeld()) {
            Log.i(this, "Acquiring proximity wake lock");
            mProximityWakeLock.acquire();
            if (mHardware.isSupported(CMHardwareManager.FEATURE_TAP_TO_WAKE)) {
                mWasTapToWakeEnabled =
                        mHardware.get(CMHardwareManager.FEATURE_TAP_TO_WAKE);
                mHardware.set(CMHardwareManager.FEATURE_TAP_TO_WAKE, false);
            }
        } else {
            Log.i(this, "Proximity wake lock already acquired");
        }
    }

    /**
     * Turn the proximity sensor off.
     * @param screenOnImmediately
     */
    void turnOff(boolean screenOnImmediately) {
        if (mProximityWakeLock == null) {
            return;
        }
        if (mProximityWakeLock.isHeld()) {
            if (mHardware.isSupported(CMHardwareManager.FEATURE_TAP_TO_WAKE)
                    && mWasTapToWakeEnabled) {
                mHardware.set(CMHardwareManager.FEATURE_TAP_TO_WAKE, true);
            }
            Log.i(this, "Releasing proximity wake lock");
            int flags =
                (screenOnImmediately ? 0 : PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
            mProximityWakeLock.release(flags);
        } else {
            Log.i(this, "Proximity wake lock already released");
        }
    }
}