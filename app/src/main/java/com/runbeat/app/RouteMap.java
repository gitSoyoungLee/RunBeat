package com.runbeat.app;

import android.content.Context;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

/**
 * 지도에 러닝 경로를 그리는 공통 코드.
 * 상세 화면(작은 고정 지도)과 전체 화면 지도가 같은 방식으로 경로를 그리도록 한 곳에 모았다.
 */
public final class RouteMap {
    private RouteMap() {}

    /** osmdroid(OpenStreetMap)는 지도를 쓰기 전에 앱 식별값을 설정해야 한다 */
    public static void init(Context c) {
        Configuration.getInstance().setUserAgentValue(c.getPackageName());
    }

    /**
     * @param interactive true면 드래그·확대 가능(전체 화면), false면 고정 지도(상세 화면)
     */
    public static void show(Context c, MapView map, double[] route, boolean interactive) {
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(interactive);
        map.getZoomController().setVisibility(interactive
                ? CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT
                : CustomZoomButtonsController.Visibility.NEVER);

        // OpenStreetMap 지도는 이용 조건상 출처 표시가 필수: 지도 구석에 "© OpenStreetMap contributors"
        CopyrightOverlay copyright = new CopyrightOverlay(c);
        copyright.setTextSize(10);
        map.getOverlays().add(copyright);

        List<GeoPoint> pts = new ArrayList<>();
        for (int i = 0; i + 1 < route.length; i += 2) {
            pts.add(new GeoPoint(route[i], route[i + 1]));
        }
        if (pts.isEmpty()) { // GPS 기록이 없으면 서울시청 근처를 보여줌
            map.getController().setZoom(15.0);
            map.getController().setCenter(new GeoPoint(37.5665, 126.9780));
            return;
        }

        Polyline line = new Polyline(map);
        line.setPoints(pts);
        line.getOutlinePaint().setColor(c.getColor(R.color.brand));
        line.getOutlinePaint().setStrokeWidth(Ui.dp(c, 5));
        map.getOverlays().add(line);

        if (pts.size() < 2) {
            map.getController().setZoom(17.0);
            map.getController().setCenter(pts.get(0));
        } else {
            BoundingBox box = BoundingBox.fromGeoPoints(pts);
            // 지도 크기가 정해진 뒤에야 "경로가 꽉 차게" 맞출 수 있어서 첫 레이아웃 이후에 실행
            map.addOnFirstLayoutListener((v, left, top, right, bottom) ->
                    map.zoomToBoundingBox(box, false, Ui.dp(c, 32)));
        }
    }
}
