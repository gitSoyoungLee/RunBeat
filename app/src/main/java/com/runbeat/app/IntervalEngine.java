package com.runbeat.app;

import java.util.List;

/**
 * 인터벌 타이머의 "두뇌". 안드로이드 코드가 하나도 없는 순수 자바 클래스라서 테스트하기 쉽다.
 *
 * 시간은 전부 "운동 시간(act)" 기준이다.
 * 운동 시간은 일시정지 동안 멈춰 있으므로, 일시정지하면 인터벌 타이머도 자동으로 멈춘다.
 */
public class IntervalEngine {

    public interface Listener {
        void onSegmentStart(Course.Planned seg, int index, int count);
        void onTenSecondsLeft(Course.Planned seg);
        void onHalfway();
        void onFinished();
    }

    private final List<Course.Planned> plan;
    private final Listener listener;
    private final long[] offsets; // 각 구간이 코스 시작부터 몇 ms 지점에서 시작하는지
    private final long totalMs;

    private int idx;
    private long segStartAct;
    private boolean warned;
    private boolean halfDone;
    private boolean done;

    public IntervalEngine(List<Course.Planned> plan, Listener listener) {
        this.plan = plan;
        this.listener = listener;
        offsets = new long[plan.size()];
        long t = 0;
        for (int i = 0; i < plan.size(); i++) {
            offsets[i] = t;
            t += plan.get(i).sec * 1000L;
        }
        totalMs = t;
    }

    public void start(long act) {
        idx = 0;
        segStartAct = act;
        if (plan.isEmpty()) {
            done = true;
            listener.onFinished();
            return;
        }
        listener.onSegmentStart(plan.get(0), 0, plan.size());
    }

    /** 1초마다 호출 (일시정지 중에는 호출하지 않아도 되고, 해도 act가 멈춰 있어서 안전) */
    public void update(long act) {
        while (!done) {
            Course.Planned p = plan.get(idx);
            long rem = p.sec * 1000L - (act - segStartAct);

            if (!warned && p.sec >= 30 && rem <= 10_000 && rem > 0) {
                warned = true;
                listener.onTenSecondsLeft(p);
            }
            if (!halfDone && totalMs >= 10 * 60_000 && courseElapsed(act) >= totalMs / 2) {
                halfDone = true;
                listener.onHalfway();
            }
            if (rem > 0) break;
            // 구간이 끝남 → 다음 구간은 "원래 끝났어야 할 시각"부터 시작 (오차 누적 방지)
            advance(segStartAct + p.sec * 1000L);
        }
    }

    /** 지금 구간 건너뛰기 */
    public void skip(long act) {
        if (!done) advance(act);
    }

    private void advance(long newStart) {
        warned = false;
        if (idx + 1 >= plan.size()) {
            done = true;
            listener.onFinished();
            return;
        }
        idx++;
        segStartAct = newStart;
        listener.onSegmentStart(plan.get(idx), idx, plan.size());
    }

    // ───────────────────────── 조회 ─────────────────────────

    public boolean isDone() { return done; }

    public long totalMs() { return totalMs; }

    public Course.Planned current() {
        return plan.isEmpty() ? null : plan.get(idx);
    }

    public Course.Planned next() {
        return (done || idx + 1 >= plan.size()) ? null : plan.get(idx + 1);
    }

    public long remainingMs(long act) {
        if (done || plan.isEmpty()) return 0;
        return Math.max(0, plan.get(idx).sec * 1000L - (act - segStartAct));
    }

    /** 코스 진행 시간 (건너뛰기를 하면 그만큼 앞으로 점프) */
    public long courseElapsed(long act) {
        if (done) return totalMs;
        long inSeg = Math.min(act - segStartAct, plan.get(idx).sec * 1000L);
        return Math.min(totalMs, offsets[idx] + Math.max(0, inSeg));
    }
}
