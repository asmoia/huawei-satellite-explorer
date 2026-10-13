package com.huawei.satellite.explorer.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

import com.huawei.satellite.explorer.model.GnssSatInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 1:1 Pixel-faithful Clone of Huawei MeeTime Satellite Alignment & Calibration Radar
 * Implements 3D Celestial Dome, Satellite Beam Sector, Guidance Reticle, and Wave Animation.
 */
public class MeeTimeSatelliteRadarView extends View {

    public interface OnAlignmentStatusListener {
        void onStatusChanged(boolean isLocked, float offsetDegrees, String instructionEn);
    }

    // Paints
    private Paint domeBgPaint;
    private Paint domeRingPaint;
    private Paint domeRingDashedPaint;
    private Paint beamSectorPaint;
    private Paint crossAxisPaint;
    private Paint cardinalTextPaint;
    private Paint degreeTextPaint;
    private Paint satDotPaint;
    private Paint satTextPaint;
    private Paint geoSatellitePaint;
    private Paint geoGlowPaint;
    private Paint guidanceReticlePaint;
    private Paint guidanceBallPaint;
    private Paint wavePulsePaint;

    // Geometry & Coordinates
    private final List<GnssSatInfo> trackedSatellites = new ArrayList<>();
    private float targetAzimuth = 169.9f;   // BeiDou-3 GEO-5 Azimuth (Tehran/Iran region)
    private float targetElevation = 47.8f; // BeiDou-3 GEO-5 Elevation
    private float phoneHeading = 0.0f;     // Phone compass heading (0 = North)
    private float phonePitch = 0.0f;       // Phone tilt angle up from horizon (0 to 90)

    private boolean isLocked = false;
    private float currentOffsetDeg = 999.0f;
    private float waveRadiusProgress = 0.0f;
    private ValueAnimator waveAnimator;
    private OnAlignmentStatusListener statusListener;

    public MeeTimeSatelliteRadarView(Context context) {
        super(context);
        init();
    }

    public MeeTimeSatelliteRadarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MeeTimeSatelliteRadarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Deep Space Background
        domeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        domeBgPaint.setColor(Color.parseColor("#0A0F1D"));
        domeBgPaint.setStyle(Paint.Style.FILL);

        // Circular Elevation Rings
        domeRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        domeRingPaint.setColor(Color.parseColor("#1B2A4A"));
        domeRingPaint.setStyle(Paint.Style.STROKE);
        domeRingPaint.setStrokeWidth(3f);

        domeRingDashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        domeRingDashedPaint.setColor(Color.parseColor("#152238"));
        domeRingDashedPaint.setStyle(Paint.Style.STROKE);
        domeRingDashedPaint.setStrokeWidth(2f);
        domeRingDashedPaint.setPathEffect(new DashPathEffect(new float[]{8, 8}, 0));

        // Beam Sector Arc (MeeTime blue satellite reception cone)
        beamSectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        beamSectorPaint.setColor(Color.parseColor("#2200E5FF")); // Translucent Cyan
        beamSectorPaint.setStyle(Paint.Style.FILL);

        // Crosshairs
        crossAxisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crossAxisPaint.setColor(Color.parseColor("#14213D"));
        crossAxisPaint.setStrokeWidth(2f);

        // Cardinal & Degree Labels (Clean Huawei Sans style)
        cardinalTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardinalTextPaint.setColor(Color.parseColor("#E0E6ED"));
        cardinalTextPaint.setTextSize(28f);
        cardinalTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        cardinalTextPaint.setTextAlign(Paint.Align.CENTER);

        degreeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        degreeTextPaint.setColor(Color.parseColor("#4A6572"));
        degreeTextPaint.setTextSize(22f);
        degreeTextPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        // Active BeiDou GNSS Satellites
        satDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        satDotPaint.setStyle(Paint.Style.FILL);

        satTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        satTextPaint.setColor(Color.parseColor("#80FFFFFF"));
        satTextPaint.setTextSize(18f);
        satTextPaint.setTypeface(Typeface.MONOSPACE);

        // Golden Target BeiDou Satellite Icon
        geoSatellitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        geoSatellitePaint.setColor(Color.parseColor("#FFD700")); // Gold
        geoSatellitePaint.setStyle(Paint.Style.STROKE);
        geoSatellitePaint.setStrokeWidth(5f);

        geoGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        geoGlowPaint.setColor(Color.parseColor("#40FFD700"));
        geoGlowPaint.setStyle(Paint.Style.FILL);

        // Mobile Guidance Reticle (MeeTime solid guidance ball & alignment ring)
        guidanceReticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guidanceReticlePaint.setStyle(Paint.Style.STROKE);
        guidanceReticlePaint.setStrokeWidth(4.5f);

        guidanceBallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guidanceBallPaint.setStyle(Paint.Style.FILL);

        // Green Wave Pulse when Locked
        wavePulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePulsePaint.setColor(Color.parseColor("#00E676"));
        wavePulsePaint.setStyle(Paint.Style.STROKE);
        wavePulsePaint.setStrokeWidth(4f);

        // Setup wave animator
        waveAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        waveAnimator.setDuration(1400);
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.setInterpolator(new LinearInterpolator());
        waveAnimator.addUpdateListener(animation -> {
            waveRadiusProgress = (float) animation.getAnimatedValue();
            if (isLocked) {
                invalidate();
            }
        });
    }

    public void setStatusListener(OnAlignmentStatusListener listener) {
        this.statusListener = listener;
    }

    public void updateSatellites(List<GnssSatInfo> sats) {
        trackedSatellites.clear();
        if (sats != null) {
            trackedSatellites.addAll(sats);
        }
        postInvalidate();
    }

    public void setSatelliteTarget(float azimuth, float elevation) {
        this.targetAzimuth = azimuth;
        this.targetElevation = elevation;
        evaluateAlignment();
        postInvalidate();
    }

    public void setPhoneOrientation(float heading, float pitch) {
        this.phoneHeading = heading;
        this.phonePitch = Math.max(0f, Math.min(90f, pitch));
        evaluateAlignment();
        postInvalidate();
    }

    private void evaluateAlignment() {
        // MeeTime Angular Separation Algorithm (Spherical law of cosines)
        double pRad = Math.toRadians(phonePitch);
        double sRad = Math.toRadians(targetElevation);
        double dAzRad = Math.toRadians(phoneHeading - targetAzimuth);

        double cosSep = Math.sin(pRad) * Math.sin(sRad) + Math.cos(pRad) * Math.cos(sRad) * Math.cos(dAzRad);
        cosSep = Math.max(-1.0, Math.min(1.0, cosSep));
        currentOffsetDeg = (float) Math.toDegrees(Math.acos(cosSep));

        boolean nowLocked = currentOffsetDeg <= 8.0f; // Within 8 degrees is locked
        if (nowLocked != isLocked) {
            isLocked = nowLocked;
            if (isLocked) {
                if (!waveAnimator.isRunning()) waveAnimator.start();
            } else {
                if (waveAnimator.isRunning()) waveAnimator.cancel();
            }
        }

        // Generate MeeTime guidance instructions
        String instruction;
        if (isLocked) {
            instruction = "Satellite Connected. Keep phone steady.";
        } else {
            float dAz = targetAzimuth - phoneHeading;
            if (dAz > 180) dAz -= 360;
            if (dAz < -180) dAz += 360;

            float dEl = targetElevation - phonePitch;

            if (Math.abs(dAz) > 12) {
                instruction = dAz > 0 ? "Turn body to the RIGHT (" + (int) Math.abs(dAz) + "°)"
                                      : "Turn body to the LEFT (" + (int) Math.abs(dAz) + "°)";
            } else if (Math.abs(dEl) > 8) {
                instruction = dEl > 0 ? "Tilt phone UPWARDS (" + (int) Math.abs(dEl) + "°)"
                                      : "Lower phone angle (" + (int) Math.abs(dEl) + "°)";
            } else {
                instruction = "Almost aligned... center the ball in target";
            }
        }

        if (statusListener != null) {
            statusListener.onStatusChanged(isLocked, currentOffsetDeg, instruction);
        }
    }

    public boolean isSatelliteLocked() {
        return isLocked;
    }

    public float getOffsetDegrees() {
        return currentOffsetDeg;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float cx = w / 2.0f;
        float cy = h / 2.0f;
        float radius = Math.min(cx, cy) - 36.0f;
        if (radius <= 10) return;

        // 1. Draw Celestial Dome Circle
        canvas.drawCircle(cx, cy, radius, domeBgPaint);

        // 2. Draw Concentric Elevation Rings (Horizon 0°, 30°, 60°, Zenith 90°)
        canvas.drawCircle(cx, cy, radius, domeRingPaint);
        canvas.drawCircle(cx, cy, radius * (60.0f / 90.0f), domeRingDashedPaint); // 30°
        canvas.drawCircle(cx, cy, radius * (30.0f / 90.0f), domeRingDashedPaint); // 60°
        canvas.drawCircle(cx, cy, 6f, domeRingPaint); // Zenith center

        // Ring degree labels
        canvas.drawText("0°", cx + radius - 28, cy - 8, degreeTextPaint);
        canvas.drawText("30°", cx + radius * (60f / 90f) - 36, cy - 8, degreeTextPaint);
        canvas.drawText("60°", cx + radius * (30f / 90f) - 36, cy - 8, degreeTextPaint);
        canvas.drawText("90°", cx - 18, cy + 32, degreeTextPaint);

        // 3. Draw Axis Lines
        canvas.drawLine(cx - radius, cy, cx + radius, cy, crossAxisPaint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, crossAxisPaint);

        // 4. Draw MeeTime Blue Reception Fan/Sector (Beam towards GEO satellite)
        float relativeTargetAz = targetAzimuth - phoneHeading;
        float sectorStart = relativeTargetAz - 30.0f - 90.0f; // -90 for canvas orientation
        RectF sectorRect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(sectorRect, sectorStart, 60.0f, true, beamSectorPaint);

        // 5. Draw Cardinal Direction Markers (N, E, S, W) rotated with heading
        drawCardinal(canvas, cx, cy, radius, 0.0f, "N");
        drawCardinal(canvas, cx, cy, radius, 90.0f, "E");
        drawCardinal(canvas, cx, cy, radius, 180.0f, "S");
        drawCardinal(canvas, cx, cy, radius, 270.0f, "W");

        // 6. Draw Tracked BeiDou Satellites (Dots with SVID)
        for (GnssSatInfo sat : trackedSatellites) {
            float satR = radius * ((90.0f - sat.elevation) / 90.0f);
            double satRad = Math.toRadians(sat.azimuth - phoneHeading - 90.0);
            float sx = (float) (cx + satR * Math.cos(satRad));
            float sy = (float) (cy + satR * Math.sin(satRad));

            if (sat.cn0DbHz >= 35) {
                satDotPaint.setColor(Color.parseColor("#00E676")); // Strong green
            } else if (sat.cn0DbHz >= 25) {
                satDotPaint.setColor(Color.parseColor("#FFD600")); // Medium yellow
            } else {
                satDotPaint.setColor(Color.parseColor("#FF5252")); // Weak red
            }
            canvas.drawCircle(sx, sy, 8f, satDotPaint);
            canvas.drawText("B" + sat.svid, sx + 10, sy + 6, satTextPaint);
        }

        // 7. Draw Target Satellite (BeiDou-3 GEO-5)
        float geoR = radius * ((90.0f - targetElevation) / 90.0f);
        double geoRad = Math.toRadians(targetAzimuth - phoneHeading - 90.0);
        float gx = (float) (cx + geoR * Math.cos(geoRad));
        float gy = (float) (cy + geoR * Math.sin(geoRad));

        // Satellite Glow & Outer Halo
        canvas.drawCircle(gx, gy, 32f, geoGlowPaint);
        canvas.drawCircle(gx, gy, 22f, geoSatellitePaint);

        // Draw Solar Panel Wings of Satellite
        canvas.drawLine(gx - 32, gy, gx - 22, gy, geoSatellitePaint);
        canvas.drawLine(gx + 22, gy, gx + 32, gy, geoSatellitePaint);
        canvas.drawRect(gx - 36, gy - 6, gx - 32, gy + 6, geoSatellitePaint);
        canvas.drawRect(gx + 32, gy - 6, gx + 36, gy + 6, geoSatellitePaint);

        Paint labelPaint = new Paint(degreeTextPaint);
        labelPaint.setColor(Color.parseColor("#FFD700"));
        labelPaint.setTextSize(22f);
        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        canvas.drawText("BDS GEO-5", gx - 48, gy - 36, labelPaint);

        // 8. Draw Phone Guidance Ball / Alignment Crosshair Reticle (MeeTime Guidance Mechanism)
        // Phone aiming position relative to screen center:
        float aimR = radius * ((90.0f - phonePitch) / 90.0f);
        double aimRad = Math.toRadians(-90.0); // Top of phone is -90 degrees
        float ax = (float) (cx + aimR * Math.cos(aimRad));
        float ay = (float) (cy + aimR * Math.sin(aimRad));

        if (isLocked) {
            // GREEN LOCKED WAVE ANIMATION
            float maxWaveR = 64f;
            float currentWaveR = 24f + (maxWaveR - 24f) * waveRadiusProgress;
            int alpha = (int) (255 * (1.0f - waveRadiusProgress));
            wavePulsePaint.setAlpha(alpha);
            canvas.drawCircle(gx, gy, currentWaveR, wavePulsePaint);

            // Locked Reticle
            guidanceReticlePaint.setColor(Color.parseColor("#00E676"));
            guidanceBallPaint.setColor(Color.parseColor("#00E676"));
            canvas.drawCircle(ax, ay, 26f, guidanceReticlePaint);
            canvas.drawCircle(ax, ay, 12f, guidanceBallPaint);

            // Crosshair lines
            canvas.drawLine(ax - 20, ay, ax + 20, ay, guidanceReticlePaint);
            canvas.drawLine(ax, ay - 20, ax, ay + 20, guidanceReticlePaint);
        } else {
            // SEARCHING RETICLE (Amber / Cyan)
            guidanceReticlePaint.setColor(Color.parseColor("#00E5FF"));
            guidanceBallPaint.setColor(Color.parseColor("#FF9100"));
            canvas.drawCircle(ax, ay, 24f, guidanceReticlePaint);
            canvas.drawCircle(ax, ay, 10f, guidanceBallPaint);

            // Crosshair lines
            canvas.drawLine(ax - 16, ay, ax + 16, ay, guidanceReticlePaint);
            canvas.drawLine(ax, ay - 16, ax, ay + 16, guidanceReticlePaint);
        }
    }

    private void drawCardinal(Canvas canvas, float cx, float cy, float radius, float angleDeg, String label) {
        double rad = Math.toRadians(angleDeg - phoneHeading - 90.0);
        float x = (float) (cx + (radius - 22) * Math.cos(rad));
        float y = (float) (cy + (radius - 22) * Math.sin(rad)) + 10f;
        canvas.drawText(label, x, y, cardinalTextPaint);
    }
}
