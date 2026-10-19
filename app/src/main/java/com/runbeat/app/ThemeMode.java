package com.runbeat.app;

import android.content.res.Configuration;

/**
 * 테마 설정값("system" / "light" / "dark")을 화면 설정(uiMode)으로 바꿔 주는 계산 담당.
 *
 * [왜 따로 뺐을까?]
 * BaseActivity 안에 섞어 두면 안드로이드 기기(또는 에뮬레이터)가 있어야만 확인할 수 있다.
 * 계산 부분만 순수 자바로 떼어 두면 JUnit으로 컴퓨터에서 바로 테스트할 수 있다.
 *
 * [uiMode가 뭐야?]
 * 안드로이드는 "지금 밤 모드인가?"를 Configuration.uiMode라는 int 하나에 비트로 담아 둔다.
 * 그중 NIGHT 비트(UI_MODE_NIGHT_MASK 자리)가 YES면 values-night/ 리소스를, NO면 values/ 리소스를 고른다.
 * 그래서 이 비트만 바꿔 끼우면 시스템 설정과 상관없이 라이트/다크를 고를 수 있다.
 *
 * (Configuration.UI_MODE_... 는 static final int 상수라 컴파일할 때 숫자로 박힌다.
 *  그래서 JUnit에서 이 클래스를 써도 안드로이드 클래스를 실제로 불러오지 않는다.)
 */
public final class ThemeMode {

    /** SharedPreferences("runbeat") 안에서 쓰는 키 */
    public static final String KEY = "theme";

    public static final String SYSTEM = "system"; // 폰의 다크 모드 설정을 따라감
    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    /**
     * 기본값은 다크.
     * v0.6까지 앱이 다크 화면 하나뿐이었기 때문에, 업데이트하자마자 화면이 하얗게 바뀌어 놀라지 않도록.
     */
    public static final String DEFAULT = DARK;

    private ThemeMode() {
    }

    /** 저장된 값이 없거나(null) 알 수 없는 글자면 기본값으로 바꿔 준다 */
    public static String normalize(String value) {
        if (SYSTEM.equals(value) || LIGHT.equals(value) || DARK.equals(value)) return value;
        return DEFAULT;
    }

    /**
     * 기존 uiMode에서 NIGHT 비트만 설정값에 맞게 바꾼 값을 돌려준다.
     * 다른 비트(예: TYPE_NORMAL, TYPE_CAR 같은 기기 종류)는 그대로 둔다.
     *
     * @param mode   테마 설정값 (이상한 값이면 기본값 취급)
     * @param uiMode 지금 기기의 Configuration.uiMode
     */
    public static int applyToUiMode(String mode, int uiMode) {
        String m = normalize(mode);
        if (SYSTEM.equals(m)) return uiMode; // 시스템 따라가기 → 손대지 않음

        int night = LIGHT.equals(m) ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES;
        // (uiMode & ~MASK) : NIGHT 자리만 0으로 지우고
        // | night          : 그 자리에 원하는 값을 넣는다
        return (uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
    }
}
