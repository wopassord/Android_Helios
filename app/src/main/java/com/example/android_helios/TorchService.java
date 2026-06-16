package com.example.android_helios;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class TorchService extends Service implements SensorEventListener {

    static final String EXTRA_THRESHOLD = "threshold";
    static final String EXTRA_TORCH_STATE = "torch_state";

    private static final String CHANNEL_ID = "helios_bg";
    private static final int NOTIF_ID = 1;
    private static final int SHAKE_SLOP_TIME_MS = 500;
    private static final int SHAKE_COUNT_RESET_TIME_MS = 3000;
    private static final int MIN_SHAKE_COUNT = 2;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private CameraManager cameraManager;
    private String torchCameraId;

    private float shakeThreshold = 2.75f;
    private boolean torchOn = false;
    private long lastShakeTime;
    private int shakeCount;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        torchCameraId = findTorchCameraId();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            shakeThreshold = intent.getFloatExtra(EXTRA_THRESHOLD, 2.75f);
            torchOn = intent.getBooleanExtra(EXTRA_TORCH_STATE, false);
        }
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (accelerometer != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float gX = event.values[0] / SensorManager.GRAVITY_EARTH;
        float gY = event.values[1] / SensorManager.GRAVITY_EARTH;
        float gZ = event.values[2] / SensorManager.GRAVITY_EARTH;
        float gForce = (float) Math.sqrt(gX * gX + gY * gY + gZ * gZ);

        if (gForce < shakeThreshold) return;

        long now = System.currentTimeMillis();
        if (now - lastShakeTime < SHAKE_SLOP_TIME_MS) return;
        if (now - lastShakeTime > SHAKE_COUNT_RESET_TIME_MS) shakeCount = 0;

        lastShakeTime = now;
        shakeCount++;

        if (shakeCount >= MIN_SHAKE_COUNT) {
            shakeCount = 0;
            toggleTorch();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void toggleTorch() {
        if (torchCameraId == null) return;
        try {
            torchOn = !torchOn;
            cameraManager.setTorchMode(torchCameraId, torchOn);
        } catch (CameraAccessException e) {
            Log.e("TorchService", "Impossible de basculer la torche", e);
        }
    }

    private String findTorchCameraId() {
        try {
            for (String id : cameraManager.getCameraIdList()) {
                Boolean hasFlash = cameraManager
                        .getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(hasFlash)) return id;
            }
        } catch (CameraAccessException e) {
            Log.e("TorchService", "Impossible de trouver la caméra torch", e);
        }
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Helios", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Helios actif")
                .setContentText("Secouez pour allumer/éteindre la torche")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .build();
    }
}
