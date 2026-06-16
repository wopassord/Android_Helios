package com.example.android_helios;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int SHAKE_SLOP_TIME_MS = 500;
    private static final int SHAKE_COUNT_RESET_TIME_MS = 3000;
    private static final int MIN_SHAKE_COUNT = 2;

    private float shakeThreshold = 2.75f;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime;
    private int shakeCount;

    private CameraManager cameraManager;
    private String torchCameraId;
    private boolean torchOn = false;

    private View viewTorchIndicator;
    private TextView tvTorchStatus;
    private TextView tvSensitivityValue;
    private AccelerometerGraphView graphAccelerometer;
    private boolean backgroundEnabled = false;

    private final CameraManager.TorchCallback torchCallback = new CameraManager.TorchCallback() {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (cameraId.equals(torchCameraId)) {
                torchOn = enabled;
                updateTorchUI();
            }
        }
    };

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
            Log.e("MainActivity", "Cet appareil n'a pas d'accéléromètre");
        }

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        torchCameraId = findTorchCameraId();
        cameraManager.registerTorchCallback(torchCallback, new Handler(Looper.getMainLooper()));

        viewTorchIndicator = findViewById(R.id.viewTorchIndicator);
        tvTorchStatus = findViewById(R.id.tvTorchStatus);
        tvSensitivityValue = findViewById(R.id.tvSensitivityValue);
        graphAccelerometer = findViewById(R.id.graphAccelerometer);
        graphAccelerometer.setThreshold(shakeThreshold);
        updateTorchUI();

        SeekBar seekBarSensitivity = findViewById(R.id.seekBarSensitivity);
        seekBarSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                shakeThreshold = 4.0f - (progress * 0.25f);
                tvSensitivityValue.setText(progress + " / 10");
                graphAccelerometer.setThreshold(shakeThreshold);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        SwitchMaterial switchBackground = findViewById(R.id.switchBackground);
        switchBackground.setOnCheckedChangeListener((buttonView, isChecked) ->
                backgroundEnabled = isChecked);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraManager.unregisterTorchCallback(torchCallback);
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
            Log.e("MainActivity", "Impossible de trouver la caméra torch", e);
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        stopService(new Intent(this, TorchService.class));
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
        if (backgroundEnabled) {
            Intent intent = new Intent(this, TorchService.class);
            intent.putExtra(TorchService.EXTRA_THRESHOLD, shakeThreshold);
            intent.putExtra(TorchService.EXTRA_TORCH_STATE, torchOn);
            startForegroundService(intent);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float gX = event.values[0] / SensorManager.GRAVITY_EARTH;
        float gY = event.values[1] / SensorManager.GRAVITY_EARTH;
        float gZ = event.values[2] / SensorManager.GRAVITY_EARTH;
        float gForce = (float) Math.sqrt(gX * gX + gY * gY + gZ * gZ);

        graphAccelerometer.addValue(gForce);

        if (gForce < shakeThreshold) return;

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
        if (torchCameraId == null) return;
        try {
            cameraManager.setTorchMode(torchCameraId, !torchOn);
        } catch (CameraAccessException e) {
            Log.e("MainActivity", "Impossible de basculer la torche", e);
        }
    }

    private void updateTorchUI() {
        GradientDrawable drawable = (GradientDrawable) viewTorchIndicator.getBackground();
        if (torchOn) {
            drawable.setColor(0xFFFFC107);
            tvTorchStatus.setText("ALLUMÉE");
            tvTorchStatus.setTextColor(0xFFFFC107);
        } else {
            drawable.setColor(0xFF9E9E9E);
            tvTorchStatus.setText("ÉTEINTE");
            tvTorchStatus.setTextColor(0xFF9E9E9E);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
