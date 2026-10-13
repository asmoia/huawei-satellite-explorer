package com.huawei.satellite.explorer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.location.GnssStatus;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.huawei.satellite.explorer.model.GnssSatInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Sky dome of all live GNSS satellites tracked by the Kirin chipset (BeiDou, GPS, GLONASS, Galileo)
 */
public class GnssSkyView extends View {

    private Paint bgPaint;
    private Paint ringPaint;
    private Paint dashedRingPaint;
    private Paint axisPaint;
    private Paint cardinalPaint;
    private Paint textPaint;
    private Paint bdsPaint;
    private Paint gpsPaint;
    private Paint glonassPaint;
    private Paint galileoPaint;

    private final List<GnssSatInfo> satellites = new ArrayList<>();
    private float heading = 0f;

    public GnssSkyView(Context context) {
        super(context);
        init();
    }

    public GnssSkyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GnssSkyView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#060A14"));
        bgPaint.setStyle(Paint.Style.FILL);

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setColor(Color.parseColor("#1B2A4A"));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);

        dashedRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dashedRingPaint.setColor(Color.parseColor("#152238"));
        dashedRingPaint.setStyle(Paint.Style.STROKE);
        dashedRingPaint.setStrokeWidth(2f);
        dashedRingPaint.setPathEffect(new DashPathEffect(new float[]{8, 8}, 0));

        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(Color.parseColor("#14213D"));
        axisPaint.setStrokeWidth(2f);

        cardinalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardinalPaint.setColor(Color.parseColor("#00E5FF"));
        cardinalPaint.setTextSize(26f);
        cardinalPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        cardinalPaint.setTextAlign(Paint.Align.CENTER);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(18f);
        textPaint.setTypeface(Typeface.MONOSPACE);

        // Constellation paints
        bdsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bdsPaint.setColor(Color.parseColor("#00E676")); // BeiDou = Green
        bdsPaint.setStyle(Paint.Style.FILL);

        gpsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gpsPaint.setColor(Color.parseColor("#29B6F6")); // GPS = Blue
        gpsPaint.setStyle(Paint.Style.FILL);

        glonassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glonassPaint.setColor(Color.parseColor("#FF7043")); // GLONASS = Orange
        glonassPaint.setStyle(Paint.Style.FILL);

        galileoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        galileoPaint.setColor(Color.parseColor("#AB47BC")); // Galileo = Purple
        galileoPaint.setStyle(Paint.Style.FILL);
    }

    public void setSatellites(List<GnssSatInfo> list, float phoneHeading) {
        satellites.clear();
        if (list != null) {
            satellites.addAll(list);
        }
        this.heading = phoneHeading;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float cx = w / 2.0f;
        float cy = h / 2.0f;
        float radius = Math.min(cx, cy) - 30f;
        if (radius <= 0) return;

        canvas.drawCircle(cx, cy, radius, bgPaint);
        canvas.drawCircle(cx, cy, radius, ringPaint);
        canvas.drawCircle(cx, cy, radius * (60f / 90f), dashedRingPaint);
        canvas.drawCircle(cx, cy, radius * (30f / 90f), dashedRingPaint);

        canvas.drawLine(cx - radius, cy, cx + radius, cy, axisPaint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, axisPaint);

        drawCardinal(canvas, cx, cy, radius, 0f, "N");
        drawCardinal(canvas, cx, cy, radius, 90f, "E");
        drawCardinal(canvas, cx, cy, radius, 180f, "S");
        drawCardinal(canvas, cx, cy, radius, 270f, "W");

        for (GnssSatInfo sat : satellites) {
            float r = radius * ((90f - sat.elevation) / 90f);
            double rad = Math.toRadians(sat.azimuth - heading - 90.0);
            float sx = (float) (cx + r * Math.cos(rad));
            float sy = (float) (cy + r * Math.sin(rad));

            Paint p = bdsPaint;
            String prefix = "B";
            if (sat.constellationType == GnssStatus.CONSTELLATION_GPS) {
                p = gpsPaint;
                prefix = "G";
            } else if (sat.constellationType == GnssStatus.CONSTELLATION_GLONASS) {
                p = glonassPaint;
                prefix = "R";
            } else if (sat.constellationType == GnssStatus.CONSTELLATION_GALILEO) {
                p = galileoPaint;
                prefix = "E";
            }

            canvas.drawCircle(sx, sy, 9f, p);
            canvas.drawText(prefix + sat.svid, sx + 11, sy + 6, textPaint);
        }
    }

    private void drawCardinal(Canvas canvas, float cx, float cy, float radius, float angleDeg, String label) {
        double rad = Math.toRadians(angleDeg - heading - 90.0);
        float x = (float) (cx + (radius - 20) * Math.cos(rad));
        float y = (float) (cy + (radius - 20) * Math.sin(rad)) + 10f;
        canvas.drawText(label, x, y, cardinalPaint);
    }
}
