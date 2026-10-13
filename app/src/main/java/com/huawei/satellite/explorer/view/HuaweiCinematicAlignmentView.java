package com.huawei.satellite.explorer.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

/**
 * 1:1 Cinematic Clone of Huawei's Iconic Satellite Alignment Screen
 * Features:
 * - Orbiting Satellite at the top radiating continuous radio waves
 * - Fixed Center Target Circle with alignment crosshairs
 * - Gyro-driven Guidance Ball that moves with phone attitude
 * - Emerald Green Light Wave Explosion on Alignment Lock
 */
public class HuaweiCinematicAlignmentView extends View {

    public interface OnAlignmentEventListener {
        void onAlignmentStateChanged(boolean isLocked, float offsetDegrees);
    }

    // Paints
    private Paint bgPaint;
    private Paint celestialRingPaint;
    private Paint targetCirclePaint;
    private Paint targetCircleFillPaint;
    private Paint targetCrosshairPaint;
    private Paint guidanceBallPaint;
    private Paint guidanceBallGlowPaint;
    private Paint satellitePaint;
    private Paint waveRadiatePaint;
    private Paint greenLockWavePaint;
    private Paint textPaint;

    // Animations
    private ValueAnimator radioWaveAnimator;
    private float radioWavePhase = 0.0f;

    private ValueAnimator greenWaveAnimator;
    private float greenWavePhase = 0.0f;

    // Alignment Physics & Coordinates
    private float targetAzimuth = 162.4f;   // Calculated for target (e.g. Yahsat 52.5°E)
    private float targetElevation = 49.1f; // Calculated for target
    private float phoneHeading = 0.0f;     // Compass heading (0-360)
    private float phonePitch = 0.0f;       // Phone elevation/tilt angle (0-90)
    private float smoothedBallX = 0f;
    private float smoothedBallY = 0f;

    private boolean isLocked = false;
    private float offsetDegrees = 999.0f;
    private String satelliteName = "Yahsat 1A (52.5°E)";
    private OnAlignmentEventListener alignmentListener;

    public HuaweiCinematicAlignmentView(Context context) {
        super(context);
        init();
    }

    public HuaweiCinematicAlignmentView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HuaweiCinematicAlignmentView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Deep Space Canvas Background
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#060A14"));
        bgPaint.setStyle(Paint.Style.FILL);

        // Subtle Outer Celestial Sphere Ring
        celestialRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        celestialRingPaint.setColor(Color.parseColor("#152036"));
        celestialRingPaint.setStyle(Paint.Style.STROKE);
        celestialRingPaint.setStrokeWidth(2.5f);
        celestialRingPaint.setPathEffect(new DashPathEffect(new float[]{6, 6}, 0));

        // Fixed Center Target Circle
        targetCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetCirclePaint.setColor(Color.parseColor("#00E5FF")); // Cyan initially
        targetCirclePaint.setStyle(Paint.Style.STROKE);
        targetCirclePaint.setStrokeWidth(4.5f);

        targetCircleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetCircleFillPaint.setColor(Color.parseColor("#1000E5FF"));
        targetCircleFillPaint.setStyle(Paint.Style.FILL);

        targetCrosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetCrosshairPaint.setColor(Color.parseColor("#3000E5FF"));
        targetCrosshairPaint.setStrokeWidth(2f);

        // Movable Guidance Ball
        guidanceBallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guidanceBallPaint.setColor(Color.parseColor("#FFD600")); // Gold / Yellow
        guidanceBallPaint.setStyle(Paint.Style.FILL);

        guidanceBallGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guidanceBallGlowPaint.setColor(Color.parseColor("#40FFD600"));
        guidanceBallGlowPaint.setStyle(Paint.Style.FILL);

        // Satellite & Radiating Radio Waves (Top)
        satellitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        satellitePaint.setColor(Color.parseColor("#00E5FF"));
        satellitePaint.setStyle(Paint.Style.FILL);

        waveRadiatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        waveRadiatePaint.setColor(Color.parseColor("#00E5FF"));
        waveRadiatePaint.setStyle(Paint.Style.STROKE);
        waveRadiatePaint.setStrokeWidth(2.5f);

        // Green Wave Explosion when Locked
        greenLockWavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        greenLockWavePaint.setColor(Color.parseColor("#00E676"));
        greenLockWavePaint.setStyle(Paint.Style.STROKE);
        greenLockWavePaint.setStrokeWidth(5f);

        // Typography
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#A0B2C6"));
        textPaint.setTextSize(24f);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Continuous Satellite Radio Wave Animator
        radioWaveAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        radioWaveAnimator.setDuration(2200);
        radioWaveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        radioWaveAnimator.setInterpolator(new LinearInterpolator());
        radioWaveAnimator.addUpdateListener(anim -> {
            radioWavePhase = (float) anim.getAnimatedValue();
            invalidate();
        });
        radioWaveAnimator.start();

        // Green Explosion Wave Animator
        greenWaveAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        greenWaveAnimator.setDuration(1200);
        greenWaveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        greenWaveAnimator.setInterpolator(new LinearInterpolator());
        greenWaveAnimator.addUpdateListener(anim -> {
            greenWavePhase = (float) anim.getAnimatedValue();
            if (isLocked) {
                invalidate();
            }
        });
    }

    public void setAlignmentListener(OnAlignmentEventListener listener) {
        this.alignmentListener = listener;
    }

    public void setTargetSatellite(String name, float azimuth, float elevation) {
        this.satelliteName = name;
        this.targetAzimuth = azimuth;
        this.targetElevation = elevation;
        updatePhysics();
        invalidate();
    }

    public void setPhoneAttitude(float heading, float pitch) {
        this.phoneHeading = heading;
        this.phonePitch = Math.max(0f, Math.min(90f, pitch));
        updatePhysics();
        invalidate();
    }

    private void updatePhysics() {
        // Compute delta Azimuth and delta Elevation
        float dAz = phoneHeading - targetAzimuth;
        if (dAz > 180f) dAz -= 360f;
        if (dAz < -180f) dAz += 360f;

        float dEl = phonePitch - targetElevation;

        // Spherical separation angle
        offsetDegrees = (float) Math.sqrt(dAz * dAz + dEl * dEl);

        boolean nowLocked = offsetDegrees <= 6.5f; // Within 6.5 degrees: ball is inside center circle
        if (nowLocked != isLocked) {
            isLocked = nowLocked;
            if (isLocked) {
                if (!greenWaveAnimator.isRunning()) greenWaveAnimator.start();
            } else {
                if (greenWaveAnimator.isRunning()) greenWaveAnimator.cancel();
            }
            if (alignmentListener != null) {
                alignmentListener.onAlignmentStateChanged(isLocked, offsetDegrees);
            }
        }
    }

    public boolean isSignalLocked() {
        return isLocked;
    }

    public float getOffsetDegrees() {
        return offsetDegrees;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float cx = w / 2.0f;
        float cy = h / 2.0f;
        float arenaRadius = Math.min(cx, cy) - 30f;
        if (arenaRadius <= 0) return;

        // 1. Draw Deep Space Celestial Canvas
        canvas.drawCircle(cx, cy, arenaRadius, bgPaint);
        canvas.drawCircle(cx, cy, arenaRadius, celestialRingPaint);
        canvas.drawCircle(cx, cy, arenaRadius * 0.65f, celestialRingPaint);

        // 2. Draw Satellite Icon at the Top (Radiating Waves)
        float satX = cx;
        float satY = cy - arenaRadius + 45f;

        // Draw Continuous Radio Wave Rings from Satellite
        for (int i = 0; i < 4; i++) {
            float phase = (radioWavePhase + (i * 0.25f)) % 1.0f;
            float waveR = 25f + phase * (arenaRadius * 0.75f);
            int alpha = (int) (180 * (1.0f - phase));
            waveRadiatePaint.setAlpha(alpha);

            // Draw wave arc directed downward toward center
            RectF waveBounds = new RectF(satX - waveR, satY - waveR, satX + waveR, satY + waveR);
            canvas.drawArc(waveBounds, 45f, 90f, false, waveRadiatePaint);
        }

        // Draw Satellite Graphic (Golden Core + Cyan Wings)
        drawSatelliteIcon(canvas, satX, satY);

        // 3. Draw Center Target Circle (Where the user needs to put the ball)
        float targetRadius = 38.0f;

        if (isLocked) {
            // GREEN LOCKED EXPLOSION WAVES
            targetCirclePaint.setColor(Color.parseColor("#00E676"));
            targetCircleFillPaint.setColor(Color.parseColor("#2500E676"));

            // Expanding concentric green wave bursts
            for (int j = 0; j < 3; j++) {
                float gPhase = (greenWavePhase + (j * 0.33f)) % 1.0f;
                float gRadius = targetRadius + gPhase * 90f;
                int gAlpha = (int) (220 * (1.0f - gPhase));
                greenLockWavePaint.setAlpha(gAlpha);
                canvas.drawCircle(cx, cy, gRadius, greenLockWavePaint);
            }
        } else {
            targetCirclePaint.setColor(Color.parseColor("#00E5FF"));
            targetCircleFillPaint.setColor(Color.parseColor("#1200E5FF"));
        }

        canvas.drawCircle(cx, cy, targetRadius, targetCircleFillPaint);
        canvas.drawCircle(cx, cy, targetRadius, targetCirclePaint);

        // Center crosshair lines
        canvas.drawLine(cx - targetRadius - 16, cy, cx + targetRadius + 16, cy, targetCrosshairPaint);
        canvas.drawLine(cx, cy - targetRadius - 16, cx, cy + targetRadius + 16, targetCrosshairPaint);

        // 4. Calculate and Smooth Guidance Ball Position (Phone's aiming direction)
        float dAz = phoneHeading - targetAzimuth;
        if (dAz > 180f) dAz -= 360f;
        if (dAz < -180f) dAz += 360f;

        float dEl = targetElevation - phonePitch;

        // Scale factors: map 30 degrees of error to the arena edge
        float scale = (arenaRadius * 0.7f) / 30.0f;
        float targetBallX = cx + (dAz * scale);
        float targetBallY = cy + (dEl * scale);

        // Clamp inside arena
        float distFromCenter = (float) Math.hypot(targetBallX - cx, targetBallY - cy);
        float maxBallDist = arenaRadius - 20f;
        if (distFromCenter > maxBallDist) {
            targetBallX = cx + (targetBallX - cx) * (maxBallDist / distFromCenter);
            targetBallY = cy + (targetBallY - cy) * (maxBallDist / distFromCenter);
        }

        // Low-pass smooth filter (inertia)
        smoothedBallX += (targetBallX - smoothedBallX) * 0.25f;
        smoothedBallY += (targetBallY - smoothedBallY) * 0.25f;

        // 5. Draw Guidance Ball
        float ballRadius = 14f;
        if (isLocked) {
            guidanceBallPaint.setColor(Color.parseColor("#00E676")); // Emerald green
            guidanceBallGlowPaint.setColor(Color.parseColor("#6000E676"));
        } else {
            guidanceBallPaint.setColor(Color.parseColor("#FFD600")); // Glowing Yellow
            guidanceBallGlowPaint.setColor(Color.parseColor("#40FFD600"));
        }

        canvas.drawCircle(smoothedBallX, smoothedBallY, ballRadius * 2.2f, guidanceBallGlowPaint);
        canvas.drawCircle(smoothedBallX, smoothedBallY, ballRadius, guidanceBallPaint);

        // 6. Draw Instruction Labels
        if (isLocked) {
            Paint lockText = new Paint(textPaint);
            lockText.setColor(Color.parseColor("#00E676"));
            lockText.setTextSize(26f);
            canvas.drawText("ALIGNMENT LOCKED (100%)", cx, cy + targetRadius + 45f, lockText);
        } else {
            canvas.drawText("Center ball inside the circle", cx, cy + targetRadius + 45f, textPaint);
        }
    }

    private void drawSatelliteIcon(Canvas canvas, float x, float y) {
        // Satellite Main Core
        Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        corePaint.setColor(Color.parseColor("#FFD700")); // Gold core
        canvas.drawCircle(x, y, 12f, corePaint);

        // Satellite Solar Panels (Wings)
        Paint wingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wingPaint.setColor(Color.parseColor("#00E5FF")); // Cyan solar arrays
        wingPaint.setStyle(Paint.Style.FILL);

        // Left wing
        canvas.drawRect(x - 34, y - 6, x - 16, y + 6, wingPaint);
        // Right wing
        canvas.drawRect(x + 16, y - 6, x + 34, y + 6, wingPaint);

        // Satellite Label
        Paint satLabel = new Paint(textPaint);
        satLabel.setColor(Color.parseColor("#E0E6ED"));
        satLabel.setTextSize(20f);
        canvas.drawText(satelliteName, x, y - 22f, satLabel);
    }
}
