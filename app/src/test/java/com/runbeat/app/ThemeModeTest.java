package com.runbeat.app;

import static org.junit.Assert.assertEquals;

import android.content.res.Configuration;

import org.junit.Test;

/**
 * ThemeMode 계산이 맞는지 확인하는 단위 테스트.
 * 기기 없이 컴퓨터에서 실행된다: ./gradlew testDebugUnitTest
 */
public class ThemeModeTest {

    // 일반 폰의 uiMode = 기기 종류(TYPE_NORMAL) + 밤 모드 비트
    private static final int PHONE_DAY = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_NO;
    private static final int PHONE_NIGHT = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES;

    @Test
    public void 기본값은_다크() {
        assertEquals(ThemeMode.DARK, ThemeMode.DEFAULT);
    }

    @Test
    public void 저장값이_없거나_이상하면_기본값() {
        assertEquals(ThemeMode.DARK, ThemeMode.normalize(null));
        assertEquals(ThemeMode.DARK, ThemeMode.normalize(""));
        assertEquals(ThemeMode.DARK, ThemeMode.normalize("Dark")); // 대소문자도 정확히 맞아야 함
        assertEquals(ThemeMode.DARK, ThemeMode.normalize("blue"));
    }

    @Test
    public void 올바른_값은_그대로() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.normalize("system"));
        assertEquals(ThemeMode.LIGHT, ThemeMode.normalize("light"));
        assertEquals(ThemeMode.DARK, ThemeMode.normalize("dark"));
    }

    @Test
    public void 시스템_따라가기는_uiMode를_건드리지_않음() {
        assertEquals(PHONE_DAY, ThemeMode.applyToUiMode(ThemeMode.SYSTEM, PHONE_DAY));
        assertEquals(PHONE_NIGHT, ThemeMode.applyToUiMode(ThemeMode.SYSTEM, PHONE_NIGHT));
    }

    @Test
    public void 라이트는_폰이_다크여도_낮모드() {
        assertEquals(PHONE_DAY, ThemeMode.applyToUiMode(ThemeMode.LIGHT, PHONE_NIGHT));
        assertEquals(PHONE_DAY, ThemeMode.applyToUiMode(ThemeMode.LIGHT, PHONE_DAY));
    }

    @Test
    public void 다크는_폰이_라이트여도_밤모드() {
        assertEquals(PHONE_NIGHT, ThemeMode.applyToUiMode(ThemeMode.DARK, PHONE_DAY));
        assertEquals(PHONE_NIGHT, ThemeMode.applyToUiMode(ThemeMode.DARK, PHONE_NIGHT));
    }

    @Test
    public void 이상한_설정값은_다크로_적용() {
        assertEquals(PHONE_NIGHT, ThemeMode.applyToUiMode(null, PHONE_DAY));
    }

    @Test
    public void 밤모드_비트_외의_다른_비트는_유지() {
        // 차량 모드(TYPE_CAR)에서 테마를 바꿔도 기기 종류 정보는 남아 있어야 한다
        int carDay = Configuration.UI_MODE_TYPE_CAR | Configuration.UI_MODE_NIGHT_NO;
        int result = ThemeMode.applyToUiMode(ThemeMode.DARK, carDay);
        assertEquals(Configuration.UI_MODE_TYPE_CAR, result & Configuration.UI_MODE_TYPE_MASK);
        assertEquals(Configuration.UI_MODE_NIGHT_YES, result & Configuration.UI_MODE_NIGHT_MASK);
    }

    @Test
    public void 밤모드_미정_상태에서도_정해진_값으로() {
        // 일부 기기는 UNDEFINED(0)로 들어올 수 있다
        int undefined = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_UNDEFINED;
        assertEquals(PHONE_DAY, ThemeMode.applyToUiMode(ThemeMode.LIGHT, undefined));
        assertEquals(PHONE_NIGHT, ThemeMode.applyToUiMode(ThemeMode.DARK, undefined));
    }
}
