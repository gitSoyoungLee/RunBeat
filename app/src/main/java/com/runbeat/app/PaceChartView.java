package com.runbeat.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 1km 구간 페이스 막대 차트. (외부 라이브러리 없이 Canvas로 직접 그림)
 * 막대가 높을수록 빠른 구간, 가장 빠른 구간은 주황색으로 강조, 점선은 평균 페이스.
 */
public class PaceChartView extends View {

    private float[] paces = new float[0];   // 초/km
    private String[] labels = new String[0];
    private float avgPace;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public PaceChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        textPaint.setColor(Color.parseColor("#8A8F98"));
        textPaint.setTextSize(11 * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
        avgPaint.setColor(Color.parseColor("#66F5F5F7"));
        avgPaint.setStrokeWidth(1.5f * density);
        avgPaint.setStyle(Paint.Style.STROKE);
        avgPaint.setPathEffect(new DashPathEffect(new float[]{8 * density, 6 * density}, 0));
    }

    public void setData(float[] paces, String[] labels, float avgPace) {
        this.paces = paces;
        this.labels = labels;
        this.avgPace = avgPace;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (paces.length == 0) {
            canvas.drawText("구간 기록이 없어요 (1km 이상 달리면 표시)", w / 2, h / 2, textPaint);
            return;
        }

        float top = 20 * density, bottom = h - 22 * density;
        float chartH = bottom - top;

        // 속도(=1/페이스)로 바꿔서 빠를수록 높은 막대
        float maxSpeed = 0, minSpeed = Float.MAX_VALUE;
        int fastest = 0;
        for (int i = 0; i < paces.length; i++) {
            float sp = paces[i] > 0 ? 1000f / paces[i] : 0;
            if (sp > maxSpeed) { maxSpeed = sp; fastest = i; }
            if (sp > 0 && sp < minSpeed) minSpeed = sp;
        }
        if (minSpeed == Float.MAX_VALUE) minSpeed = 0;
        float base = minSpeed * 0.8f;           // 차이가 잘 보이도록 바닥을 살짝 올림
        float range = Math.max(maxSpeed - base, 0.0001f);

        int n = paces.length;
        float slot = w / n;
        float barW = Math.max(slot * 0.7f, 2 * density);
        int labelEvery = (int) Math.ceil(n / 10.0); // 라벨은 최대 10개 정도만
        boolean showPaceText = n <= 12;

        for (int i = 0; i < n; i++) {
            float sp = paces[i] > 0 ? 1000f / paces[i] : 0;
            float barH = sp > 0 ? Math.max((sp - base) / range * chartH, 3 * density) : 0;
            float cx = slot * i + slot / 2;
            barPaint.setColor(i == fastest ? Color.parseColor("#FF6B2C") : Color.parseColor("#5B8DEF"));
            float rad = Math.min(barW / 2, 6 * density);
            canvas.drawRoundRect(cx - barW / 2, bottom - barH, cx + barW / 2, bottom, rad, rad, barPaint);

            if (showPaceText) {
                canvas.drawText(Fmt.pace(paces[i]), cx, bottom - barH - 4 * density, textPaint);
            }
            if (i % labelEvery == 0 || i == n - 1) {
                canvas.drawText(labels[i], cx, h - 6 * density, textPaint);
            }
        }

        // 평균 페이스 점선
        if (avgPace > 0) {
            float sp = 1000f / avgPace;
            float y = bottom - (sp - base) / range * chartH;
            if (y >= top && y <= bottom) canvas.drawLine(0, y, w, y, avgPaint);
        }
    }
}
