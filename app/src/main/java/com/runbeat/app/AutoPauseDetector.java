package com.runbeat.app;

/**
 * 자동 일시정지 판단기. (순수 자바 → 테스트 쉬움)
 *
 * 멈춤 판단: GPS 속도가 시속 약 2km(0.6m/s) 미만 + 걸음도 없음 → 이 상태가 6초 계속되면 일시정지
 * 재개 판단: GPS 속도가 시속 약 3.6km(1.0m/s) 이상이 2번 연속  또는  걸음이 다시 감지됨
 *
 * 걷기 속도는 보통 시속 4~5km라서 걷기 구간에서도 멈춘 것으로 오해하지 않는다.
 * 출발 직후(30m 이내)에는 출발선에서 GPS를 기다리며 서 있는 경우가 많아서 판단하지 않는다.
 */
public class AutoPauseDetector {

    public static final float STOP_SPEED = 0.6f;    // m/s
    public static final float MOVE_SPEED = 1.0f;    // m/s
    public static final long STOP_HOLD_MS = 6000;
    public static final int MOVE_FIXES = 2;
    public static final double ARM_DISTANCE_M = 30;

    public enum Change { NONE, PAUSE, RESUME }

    private long slowSince = -1;
    private int fastCount;
    private boolean paused;

    /** GPS 위치가 들어올 때마다 호출. speed < 0 이면 속도를 모르는 것 */
    public Change onSpeed(float speed, long nowMs, boolean stepping, double distanceM) {
        if (speed < 0) return Change.NONE;
        if (!paused) {
            if (distanceM < ARM_DISTANCE_M) return Change.NONE;
            if (speed < STOP_SPEED && !stepping) {
                if (slowSince < 0) slowSince = nowMs;
                if (nowMs - slowSince >= STOP_HOLD_MS) {
                    paused = true;
                    slowSince = -1;
                    fastCount = 0;
                    return Change.PAUSE;
                }
            } else {
                slowSince = -1;
            }
        } else {
            fastCount = speed >= MOVE_SPEED ? fastCount + 1 : 0;
            if (fastCount >= MOVE_FIXES || stepping) {
                reset();
                return Change.RESUME;
            }
        }
        return Change.NONE;
    }

    /** 걸음 센서가 걸음을 감지했을 때 호출 (GPS가 늦게 따라와도 바로 재개되도록) */
    public Change onStepping(boolean stepping) {
        if (paused && stepping) {
            reset();
            return Change.RESUME;
        }
        return Change.NONE;
    }

    public boolean isPaused() { return paused; }

    /** 수동 일시정지/재개 등으로 상태를 초기화할 때 */
    public void reset() {
        paused = false;
        slowSince = -1;
        fastCount = 0;
    }
}
