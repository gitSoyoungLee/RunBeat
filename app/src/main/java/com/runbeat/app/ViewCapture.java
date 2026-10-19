package com.runbeat.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

/**
 * 화면(View)을 한 장의 이미지(Bitmap)로 그리는 도우미.
 *
 * [어떻게 화면 밖 부분까지 찍을까?]
 * ScrollView 안에 들어 있는 내용 레이아웃은 화면에 보이는 부분만이 아니라
 * "전체 길이"만큼 이미 배치(layout)되어 있다. 그래서 그 레이아웃을 Canvas에 그대로 그리면
 * 스크롤하지 않아도 맨 아래 구간표까지 한 장에 담긴다. (스크롤 캡처를 앱이 직접 하는 셈)
 */
public final class ViewCapture {
    private ViewCapture() {}

    /** 이미지가 너무 커서 메모리가 부족해지지 않도록 픽셀 수 상한 (약 2,400만 픽셀 ≈ 96MB) */
    private static final long MAX_PIXELS = 24_000_000L;

    /**
     * @param content  그릴 레이아웃 (ScrollView의 자식)
     * @param height   위에서부터 몇 px까지 그릴지 (아래쪽 버튼 영역을 빼고 싶을 때 사용)
     * @param bgColor  배경색 (레이아웃이 투명하면 배경이 검게 찍히므로 직접 칠함)
     */
    public static Bitmap capture(View content, int height, int bgColor) {
        int w = content.getWidth();
        int h = Math.min(height, content.getHeight());
        if (w <= 0 || h <= 0) return null;

        // 아주 긴 기록(예: 풀코스 42개 구간)은 해상도를 조금 낮춰서라도 저장되게
        float scale = 1f;
        if ((long) w * h > MAX_PIXELS) scale = (float) Math.sqrt(MAX_PIXELS / ((double) w * h));

        Bitmap bmp = Bitmap.createBitmap(Math.round(w * scale), Math.round(h * scale), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(bgColor);
        canvas.scale(scale, scale);
        canvas.clipRect(0, 0, w, h);
        content.draw(canvas);
        return bmp;
    }
}
