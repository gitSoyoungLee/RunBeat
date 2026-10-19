package com.runbeat.app;

import android.os.Bundle;

/**
 * 설정 화면. 홈 오른쪽 위 톱니바퀴 버튼으로 연다.
 *
 * 러닝 전에 한 번 정해 두면 되는 값(체중, 자동 일시정지, 테마 등)을 여기로 모아서
 * 홈 화면은 "달리기"에 필요한 것만 보이게 한다.
 * 값은 모두 AppSettings로 읽고 쓴다.
 */
public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
    }
}
