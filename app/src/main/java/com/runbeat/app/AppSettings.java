package com.runbeat.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 앱 설정값을 한 곳에서 읽고 쓰는 클래스.
 *
 * [왜 필요할까?]
 * 예전에는 화면마다 getSharedPreferences("runbeat")를 직접 열고 "weight", "bpm" 같은 키 문자열을
 * 손으로 적었다. 키 글자 하나만 틀려도(예: "wieght") 컴파일은 되는데 값이 저장되지 않는 버그가 생긴다.
 * 여기 한 군데에만 키와 기본값을 적어 두고, 다른 곳은 메서드(weightKg(), setBpm() …)만 부르게 한다.
 *
 * [이름이 왜 Settings가 아니라 AppSettings?]
 * 안드로이드에 이미 android.provider.Settings(시스템 설정 화면 열기)가 있어서,
 * 같은 이름을 쓰면 MainActivity처럼 둘 다 쓰는 곳에서 어느 Settings인지 헷갈린다.
 *
 * [주의] 키 문자열은 v0.6과 똑같이 유지한다. 바꾸면 업데이트 후 사용자의 기존 설정이 사라진다.
 */
public final class AppSettings {

    /** SharedPreferences 파일 이름. (예전 MainActivity.PREFS와 같은 값) */
    public static final String PREFS = "runbeat";

    // 저장 키 (바꾸지 말 것)
    private static final String KEY_WEIGHT = "weight";
    private static final String KEY_AUTO_PAUSE = "autoPause";
    private static final String KEY_BPM = "bpm";
    private static final String KEY_MODE = "mode";
    private static final String KEY_COURSE_ID = "courseId";

    // 기본값
    public static final float DEFAULT_WEIGHT = 60f;
    public static final float MIN_WEIGHT = 20f;
    public static final float MAX_WEIGHT = 250f;
    public static final int MIN_BPM = 120;
    public static final int MAX_BPM = 220;

    private final SharedPreferences prefs;

    private AppSettings(Context c) {
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 사용법: AppSettings.of(this).weightKg() */
    public static AppSettings of(Context c) {
        return new AppSettings(c);
    }

    // ───────── 체중 (칼로리 계산용) ─────────

    public float weightKg() {
        return prefs.getFloat(KEY_WEIGHT, DEFAULT_WEIGHT);
    }

    /** 범위를 벗어난 값(오타 등)은 저장하지 않고 false를 돌려준다 */
    public boolean setWeightKg(float kg) {
        if (!isValidWeight(kg)) return false;
        prefs.edit().putFloat(KEY_WEIGHT, kg).apply();
        return true;
    }

    // ───────── 자동 일시정지 ─────────

    public boolean autoPause() {
        return prefs.getBoolean(KEY_AUTO_PAUSE, true);
    }

    public void setAutoPause(boolean on) {
        prefs.edit().putBoolean(KEY_AUTO_PAUSE, on).apply();
    }

    // ───────── 메트로놈 ─────────

    /** 0 = 꺼짐 */
    public int bpm() {
        return prefs.getInt(KEY_BPM, 0);
    }

    /** 범위 밖이면 가장 가까운 값으로 맞춰서 저장하고, 실제로 저장한 값을 돌려준다 */
    public int setBpm(int bpm) {
        int v = clampBpm(bpm);
        prefs.edit().putInt(KEY_BPM, v).apply();
        return v;
    }

    // ───────── 홈 화면 모드 / 선택한 코스 ─────────

    /** true = 코스 달리기, false = 자유 달리기 */
    public boolean courseMode() {
        return "course".equals(prefs.getString(KEY_MODE, "free"));
    }

    public void setCourseMode(boolean course) {
        prefs.edit().putString(KEY_MODE, course ? "course" : "free").apply();
    }

    /** 아직 고른 적이 없으면 null */
    public String courseId() {
        return prefs.getString(KEY_COURSE_ID, null);
    }

    public void setCourseId(String id) {
        prefs.edit().putString(KEY_COURSE_ID, id).apply();
    }

    // ───────── 테마 ─────────

    /** "system" / "light" / "dark" 중 하나 (ThemeMode 참고) */
    public String theme() {
        return ThemeMode.normalize(prefs.getString(ThemeMode.KEY, ThemeMode.DEFAULT));
    }

    public void setTheme(String mode) {
        prefs.edit().putString(ThemeMode.KEY, ThemeMode.normalize(mode)).apply();
    }

    // ───────── 값 검사 규칙 (안드로이드 없이 단위 테스트할 수 있게 static으로) ─────────

    public static boolean isValidWeight(float kg) {
        return kg >= MIN_WEIGHT && kg <= MAX_WEIGHT; // NaN은 비교가 항상 false라 자동으로 걸러진다
    }

    /** 0(꺼짐)은 그대로, 나머지는 120~220 사이로 */
    public static int clampBpm(int bpm) {
        if (bpm == 0) return 0;
        return Math.max(MIN_BPM, Math.min(MAX_BPM, bpm));
    }
}
