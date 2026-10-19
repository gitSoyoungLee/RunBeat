package com.runbeat.app;

import android.os.Bundle;
import android.widget.Toast;

import org.osmdroid.views.MapView;

/** 전체 화면 경로 지도. 여기서는 손가락으로 움직이고 확대할 수 있다. */
public class MapActivity extends BaseActivity {

    public static final String EXTRA_ID = "run_id";

    private MapView map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RouteMap.init(this);
        setTitle("경로 지도");

        String id = getIntent().getStringExtra(EXTRA_ID);
        RunRecord r = id == null ? null : RunStorage.load(this, id);
        if (r == null) {
            Toast.makeText(this, "기록을 불러오지 못했어요", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        map = new MapView(this);
        setContentView(map);
        RouteMap.show(this, map, r.route, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }
}
