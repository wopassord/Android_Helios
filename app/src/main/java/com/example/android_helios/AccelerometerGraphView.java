package com.example.android_helios;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class AccelerometerGraphView extends View {

    private static final int BUFFER_SIZE = 150;
    private static final float Y_MAX = 5.0f;

    private final float[] values = new float[BUFFER_SIZE];
    private int head = 0;
    private int count = 0;
    private float threshold = 2.75f;

    private final Paint linePaint;
    private final Paint thresholdPaint;
    private final Paint gridPaint;
    private final Path path = new Path();

    public AccelerometerGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0xFF42A5F5);
        linePaint.setStrokeWidth(3f);
        linePaint.setStyle(Paint.Style.STROKE);

        thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thresholdPaint.setColor(0xFFEF5350);
        thresholdPaint.setStrokeWidth(2f);
        thresholdPaint.setStyle(Paint.Style.STROKE);
        thresholdPaint.setPathEffect(new DashPathEffect(new float[]{16f, 8f}, 0));

        gridPaint = new Paint();
        gridPaint.setColor(0x33FFFFFF);
        gridPaint.setStrokeWidth(1f);
    }

    public void addValue(float gForce) {
        values[head] = gForce;
        head = (head + 1) % BUFFER_SIZE;
        if (count < BUFFER_SIZE) count++;
        postInvalidate();
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        canvas.drawColor(0xFF1E1E1E);

        for (int g = 1; g <= 4; g++) {
            float y = h * (1f - g / Y_MAX);
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        float ty = h * (1f - threshold / Y_MAX);
        canvas.drawLine(0, ty, w, ty, thresholdPaint);

        if (count < 2) return;

        path.reset();
        float xStep = (float) w / (BUFFER_SIZE - 1);
        for (int i = 0; i < count; i++) {
            int idx = (head - count + i + BUFFER_SIZE) % BUFFER_SIZE;
            float x = i * xStep;
            float y = h * (1f - Math.min(values[idx], Y_MAX) / Y_MAX);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, linePaint);
    }
}
