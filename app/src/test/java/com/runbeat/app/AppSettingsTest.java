package com.runbeat.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * AppSettings의 값 검사 규칙 테스트.
 * (SharedPreferences 저장 자체는 안드로이드가 필요해서 여기서는 순수 규칙만 확인한다)
 */
public class AppSettingsTest {

    @Test
    public void 체중은_20부터_250까지만_허용() {
        assertTrue(AppSettings.isValidWeight(20f));
        assertTrue(AppSettings.isValidWeight(60.5f));
        assertTrue(AppSettings.isValidWeight(250f));
        assertFalse(AppSettings.isValidWeight(19.9f));
        assertFalse(AppSettings.isValidWeight(250.1f));
        assertFalse(AppSettings.isValidWeight(0f));      // 칸을 비웠다가 "0"부터 치는 중
        assertFalse(AppSettings.isValidWeight(Float.NaN));
    }

    @Test
    public void 메트로놈_0은_꺼짐이라_그대로() {
        assertEquals(0, AppSettings.clampBpm(0));
    }

    @Test
    public void 메트로놈은_120부터_220_사이로_맞춤() {
        assertEquals(170, AppSettings.clampBpm(170));
        assertEquals(120, AppSettings.clampBpm(119)); // 끈 상태에서 -1 같은 경우
        assertEquals(120, AppSettings.clampBpm(-5));
        assertEquals(220, AppSettings.clampBpm(225));
    }

    @Test
    public void 기본_체중은_허용_범위_안() {
        assertTrue(AppSettings.isValidWeight(AppSettings.DEFAULT_WEIGHT));
    }
}
