package com.runbeat.app;

/**
 * 러닝 중 "지금 상태"를 담아두는 공용 칠판.
 * RunService(서비스)가 값을 쓰고, MainActivity(화면)가 0.5초마다 읽어서 보여준다.
 * volatile: 여러 스레드가 동시에 읽고 써도 항상 최신 값을 보게 해주는 키워드.
 */
public final class RunState {
    private RunState() {}

    public static volatile boolean running;
    public static volatile long elapsedMs;     // 일시정지 시간을 뺀 실제 운동 시간
    public static volatile double distanceM;
    public static volatile double currentPace; // 초/km, 0이면 알 수 없음
    public static volatile double avgPace;     // 초/km
    public static volatile double kcal;
    public static volatile int cadence;        // 실시간 걸음수/분(spm)
    public static volatile int steps;
    public static volatile int gpsAccuracy = -1; // 미터, -1이면 아직 신호 없음

    // 일시정지
    public static volatile boolean paused;      // 내가 누른 일시정지
    public static volatile boolean autoPaused;  // 자동 일시정지

    // 코스 달리기
    public static volatile boolean courseMode;
    public static volatile boolean courseDone;
    public static volatile String courseName = "";
    public static volatile String segLabel = "";   // "달리기 2/6"
    public static volatile int segType;
    public static volatile long segRemainingMs;
    public static volatile String nextLabel = "";  // "다음: 걷기 1:00"
    public static volatile long courseElapsedMs;
    public static volatile long courseTotalMs;

    // 설정값 (화면에서 바꾸면 서비스가 바로 반영)
    public static volatile int targetBpm;      // 0이면 메트로놈 끔
    public static volatile double weightKg = 60;
    public static volatile boolean autoPauseEnabled = true;

    public static volatile String lastSavedId;

    public static boolean isPaused() {
        return paused || autoPaused;
    }

    static void resetLive() {
        elapsedMs = 0;
        distanceM = 0;
        currentPace = 0;
        avgPace = 0;
        kcal = 0;
        cadence = 0;
        steps = 0;
        gpsAccuracy = -1;
        paused = false;
        autoPaused = false;
        courseMode = false;
        courseDone = false;
        courseName = "";
        segLabel = "";
        segRemainingMs = 0;
        nextLabel = "";
        courseElapsedMs = 0;
        courseTotalMs = 0;
    }
}
