package com.huawei.satellite.explorer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.huawei.satellite.explorer.model.GnssSatInfo;

import java.util.ArrayList;
import java.util.List;

public class SkyRadarView extends View {

    public interface AlignmentListener {
        void onAlignmentChanged(boolean isAligned, float offsetDegrees);
    }

    private Paint bgPaint;
    private Paint ringPaint;
    private Paint ringDashedPaint;
    private Paint crossPaint;
    private Paint textPaint;
    private Paint cardinalPaint;
    private Paint satPaintStrong;
    private Paint satPaintMedium;
    private Paint satPaintWeak;
    private Paint satLabelPaint;
    private Paint geoTargetPaint;
    private Paint geoGlowPaint;
    private Paint reticlePaint;
    private Paint reticleLockedPaint;

    private final List<GnssSatInfo> satList = new ArrayList<>();
    private float targetAzimuth = 170.0f; // BeiDou GEO-5 azimuth
    private float targetElevation = 48.0f; // BeiDou GEO-5 elevation
    private float phoneHeading = 0.0f; // 0 = North
    private float phonePitch = 0.0f; // 0 = flat on table, 90 = vertical up pointing
    private boolean isAligned = false;
    private float currentOffset = 999.0f;
    private AlignmentListener alignmentListener;

    public SkyRadarView(Context context) {
        super(context);
        init();
    }

    public SkyRadarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SkyRadarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#0B132B"));
        bgPaint.setStyle(Paint.Style.FILL);

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setColor(Color.parseColor("#1C3A5E"));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);

        ringDashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringDashedPaint.setColor(Color.parseColor("#16324F"));
        ringDashedPaint.setStyle(Paint.Style.STROKE);
        ringDashedPaint.setStrokeWidth(2f);
        ringDashedPaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));

        crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crossPaint.setColor(Color.parseColor("#1E4268"));
        crossPaint.setStrokeWidth(2f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#64B5F6"));
        textPaint.setTextSize(26f);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        cardinalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardinalPaint.setColor(Color.parseColor("#00E5FF"));
        cardinalPaint.setTextSize(32f);
        cardinalPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        cardinalPaint.setTextAlign(Paint.Align.CENTER);

        satPaintStrong = new Paint(Paint.ANTI_ALIAS_FLAG);
        satPaintStrong.setColor(Color.parseColor("#00E676")); // Neon Green
        satPaintStrong.setStyle(Paint.Style.FILL);

        satPaintMedium = new Paint(Paint.ANTI_ALIAS_FLAG);
        satPaintMedium.setColor(Color.parseColor("#FFD600")); // Yellow
        satPaintMedium.setStyle(Paint.Style.FILL);

        satPaintWeak = new Paint(Paint.ANTI_ALIAS_FLAG);
        satPaintWeak.setColor(Color.parseColor("#FF5252")); // Red
        satPaintWeak.setStyle(Paint.Style.FILL);

        satLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        satLabelPaint.setColor(Color.WHITE);
        satLabelPaint.setTextSize(20f);
        satLabelPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        geoTargetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        geoTargetPaint.setColor(Color.parseColor("#FFB300")); // Gold
        geoTargetPaint.setStyle(Paint.Style.STROKE);
        geoTargetPaint.setStrokeWidth(6f);

        geoGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        geoGlowPaint.setColor(Color.parseColor("#55FFB300"));
        geoGlowPaint.setStyle(Paint.Style.FILL);

        reticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        reticlePaint.setColor(Color.parseColor("#FF9100")); // Orange reticle
        reticlePaint.setStyle(Paint.Style.STROKE);
        reticlePaint.setStrokeWidth(4f);

        reticleLockedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        reticleLockedPaint.setColor(Color.parseColor("#00E676")); // Green locked
        reticleLockedPaint.setStyle(Paint.Style.STROKE);
        reticleLockedPaint.setStrokeWidth(6f);
    }

    public void setAlignmentListener(AlignmentListener listener) {
        this.alignmentListener = listener;
    }

    public void updateSatellites(List<GnssSatInfo> list) {
        satList.clear();
        if (list != null) {
            satList.addAll(list);
        }
        postInvalidate();
    }

    public void setTargetPosition(float az, float el) {
        this.targetAzimuth = az;
        this.targetElevation = el;
        checkAlignment();
        postInvalidate();
    }

    public void setOrientation(float heading, float pitch) {
        this.phoneHeading = heading;
        this.phonePitch = pitch;
        checkAlignment();
        postInvalidate();
    }

    private void checkAlignment() {
        // Calculate angular distance between phone pointing vector and GEO satellite vector
        // Phone pointing direction: Azimuth = phoneHeading, Elevation = phonePitch
        float deltaAz = Math.abs(phoneHeading - targetAzimuth);
        if (deltaAz > 180f) deltaAz = 360f - deltaAz;
        float deltaEl = Math.abs(phonePitch - targetElevation);

        currentOffset = (float) Math.sqrt(deltaAz * deltaAz + deltaEl * deltaEl);
        boolean alignedNow = currentOffset < 10.0f; // Within 10 degrees is aligned

        if (alignedNow != isAligned) {
            isAligned = alignedNow;
            if (alignmentListener != null) {
                alignmentListener.onAlignmentChanged(isAligned, currentOffset);
            }
        }
    }

    public boolean isAligned() {
        return isAligned;
    }

    public float getCurrentOffset() {
        return currentOffset;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        float cx = width / 2.0f;
        float cy = height / 2.0f;
        float radius = Math.min(cx, cy) - 40.0f;
        if (radius <= 0) return;

        // 1. Background radar circle
        canvas.drawCircle(cx, cy, radius, bgPaint);

        // 2. Rings (Horizon = outer, 30 deg, 60 deg, Zenith = center)
        canvas.drawCircle(cx, cy, radius, ringPaint);
        canvas.drawCircle(cx, cy, radius * (60.0f / 90.0f), ringDashedPaint); // 30 deg el
        canvas.drawCircle(cx, cy, radius * (30.0f / 90.0f), ringDashedPaint); // 60 deg el
        canvas.drawCircle(cx, cy, 6f, ringPaint); // Zenith dot

        // 3. Elevation text labels
        canvas.drawText("0°", cx + radius - 30, cy - 8, textPaint);
        canvas.drawText("30°", cx + radius * (60.0f / 90.0f) - 40, cy - 8, textPaint);
        canvas.drawText("60°", cx + radius * (30.0f / 90.0f) - 40, cy - 8, textPaint);
        canvas.drawText("90° (سرسو)", cx - 45, cy + 30, textPaint);

        // 4. Crosshair axes
        canvas.drawLine(cx - radius, cy, cx + radius, cy, crossPaint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, crossPaint);

        // 5. Cardinal Direction Labels (Rotate relative to phone heading)
        drawCardinal(canvas, cx, cy, radius, 0f, "شمال (N)");
        drawCardinal(canvas, cx, cy, radius, 90f, "شرق (E)");
        drawCardinal(canvas, cx, cy, radius, 180f, "جنوب (S)");
        drawCardinal(canvas, cx, cy, radius, 270f, "غرب (W)");

        // 6. Draw Tracked BeiDou Satellites
        for (GnssSatInfo sat : satList) {
            float r = radius * ((90.0f - sat.elevation) / 90.0f);
            double angleRad = Math.toRadians(sat.azimuth - phoneHeading - 90.0);
            float sx = (float) (cx + r * Math.cos(angleRad));
            float sy = (float) (cy + r * Math.sin(angleRad));

            Paint p = sat.cn0DbHz >= 30 ? satPaintStrong : (sat.cn0DbHz >= 20 ? satPaintMedium : satPaintWeak);
            canvas.drawCircle(sx, sy, 12f, p);
            canvas.drawText(sat.getDisplayName(), sx + 14, sy + 6, satLabelPaint);
        }

        // 7. Draw BeiDou GEO-5 Target (Clarke Belt)
        float geoR = radius * ((90.0f - targetElevation) / 90.0f);
        double geoAngleRad = Math.toRadians(targetAzimuth - phoneHeading - 90.0);
        float gx = (float) (cx + geoR * Math.cos(geoAngleRad));
        float gy = (float) (cy + geoR * Math.sin(geoAngleRad));

        canvas.drawCircle(gx, gy, 28f, geoGlowPaint);
        canvas.drawCircle(gx, gy, 22f, geoTargetPaint);
        canvas.drawText("🛰️ بیدو GEO-5", gx - 55, gy - 30, cardinalPaint);

        // 8. Draw Phone Aim Reticle (Crosshair of where phone is currently pointed)
        float aimR = radius * ((90.0f - phonePitch) / 90.0f);
        // Phone heading relative to itself is always at angle 0 (top of the phone)
        double aimAngleRad = Math.toRadians(-90.0); // top of display
        float ax = (float) (cx + aimR * Math.cos(aimAngleRad));
        float ay = (float) (cy + aimR * Math.sin(aimAngleRad));

        Paint rPaint = isAligned ? reticleLockedPaint : reticlePaint;
        canvas.drawCircle(ax, ay, isAligned ? 34f : 24f, rPaint);
        canvas.drawLine(ax - 18, ay, ax + 18, ay, rPaint);
        canvas.drawLine(ax, ay - 18, ax, ay + 18, rPaint);

        if (isAligned) {
            canvas.drawCircle(ax, ay, 46f, geoGlowPaint);
            Paint alignedTextPaint = new Paint(cardinalPaint);
            alignedTextPaint.setColor(Color.parseColor("#00E676"));
            alignedTextPaint.setTextSize(26f);
            canvas.drawText("🎯 تراز کامل با ماهواره!", ax, ay + 60, alignedTextPaint);
        }
    }

    private void drawCardinal(Canvas canvas, float cx, float cy, float radius, float cardinalAzimuth, String label) {
        double rad = Math.toRadians(cardinalAzimuth - phoneHeading - 90.0);
        float x = (float) (cx + (radius - 24) * Math.cos(rad));
        float y = (float) (cy + (radius - 24) * Math.sin(rad)) + 10f;
        canvas.drawText(label, x, y, cardinalPaint);
    }
}
