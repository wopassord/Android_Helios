package com.example.android_helios;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final float SHAKE_THRESHOLD_GRAVITY = 2.7f;
    private static final int SHAKE_SLOP_TIME_MS = 500;
    private static final int SHAKE_COUNT_RESET_TIME_MS = 3000;
    private static final int MIN_SHAKE_COUNT = 2;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime;
    private int shakeCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        if (accelerometer == null) {
            Log.e("MainActivity", "The device does not have an accelerometer");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (accelerometer != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float gX = event.values[0] / SensorManager.GRAVITY_EARTH;
        float gY = event.values[1] / SensorManager.GRAVITY_EARTH;
        float gZ = event.values[2] / SensorManager.GRAVITY_EARTH;
        float gForce = (float) Math.sqrt(gX * gX + gY * gY + gZ * gZ);

        if (gForce < SHAKE_THRESHOLD_GRAVITY) return;

        long now = System.currentTimeMillis();
        if (now - lastShakeTime < SHAKE_SLOP_TIME_MS) return;

        if (now - lastShakeTime > SHAKE_COUNT_RESET_TIME_MS) {
            shakeCount = 0;
        }

        lastShakeTime = now;
        shakeCount++;

        if (shakeCount >= MIN_SHAKE_COUNT) {
            shakeCount = 0;
            onShakeDetected();
        }
    }

    private void onShakeDetected() {
        Log.d("MainActivity", "Shake detected");
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
