package com.runbeat.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

/**
 * 러닝을 기록하는 Foreground Service.
 * 화면이 꺼지거나 다른 앱(음악 앱)을 써도 계속 동작한다.
 *
 * 시간 개념이 두 가지 있다.
 *  - 실제 시간 (SystemClock.elapsedRealtime)  : 시계처럼 계속 흐름
 *  - 운동 시간 (elapsed())                    : 일시정지 동안은 멈춤 → 기록·페이스·인터벌 타이머 모두 이걸 사용
 *
 * 모든 콜백(GPS, 걸음 센서, 1초 타이머, 알림 버튼)은 메인 스레드에서 실행되도록 맞춰서
 * 필드를 동시에 건드리는 문제(동기화)를 피했다.
 */
public class RunService extends Service implements LocationListener, SensorEventListener {

    public static final String ACTION_START = "com.runbeat.app.START";
    public static final String ACTION_STOP = "com.runbeat.app.STOP";
    public static final String ACTION_PAUSE = "com.runbeat.app.PAUSE";
    public static final String ACTION_RESUME = "com.runbeat.app.RESUME";
    public static final String ACTION_SKIP = "com.runbeat.app.SKIP";
    public static final String EXTRA_COURSE_ID = "course_id";

    private static final String CHANNEL_ID = "run";
    private static final int NOTIF_ID = 1;

    // GPS 노이즈 필터 기준
    private static final float MAX_ACCURACY_M = 25f;   // 정확도가 이보다 나쁘면 버림
    private static final float MIN_MOVE_M = 3f;        // 이보다 작게 움직이면 제자리 흔들림으로 봄
    private static final double MAX_SPEED_MPS = 12.0;  // 이보다 빠르면 GPS 튐으로 봄

    private final Handler handler = new Handler(Looper.getMainLooper());

    private LocationManager locationManager;
    private SensorManager sensorManager;
    private PowerManager.WakeLock wakeLock;
    private Metronome metronome;
    private VoiceCoach voice;

    private boolean active;
    private String runId;
    private long startWallMs;
    private long startElapsed;

    // 일시정지
    private boolean manualPaused;
    private boolean autoPaused;
    private long pauseStartElapsed;
    private long pausedTotalMs;
    private boolean autoPauseEnabled;
    private final AutoPauseDetector autoPause = new AutoPauseDetector();

    // GPS
    private Location lastLoc;          // 거리 계산 기준점 (일시정지 후에는 새로 잡음)
    private long lastLocElapsed;        // 실제 시간
    private long lastLocAct;            // 운동 시간
    private Location prevRawLoc;        // 속도 계산용 (필터 전 마지막 위치)
    private long prevRawElapsed;
    private int rejectStreak;
    private double distanceM;
    private final ArrayList<Double> route = new ArrayList<>();
    private final ArrayDeque<double[]> paceWindow = new ArrayDeque<>(); // {운동시간ms, 누적거리m}

    // 1km 구간
    private final ArrayList<RunRecord.Split> splits = new ArrayList<>();
    private int nextKm = 1;
    private long lastSplitCumMs;
    private int lastSplitSteps;

    // 걸음
    private int totalSteps;
    private final ArrayDeque<Long> stepTimes = new ArrayDeque<>();

    // 코스
    private Course course;
    private IntervalEngine engine;

    private int tickCount;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            tick();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_START.equals(action)) {
            if (!active) startRun(intent.getStringExtra(EXTRA_COURSE_ID));
            return START_NOT_STICKY;
        }
        if (!active) {
            // 러닝 중이 아닌데 알림 버튼 등이 눌린 경우: 할 일 없음
            if (!ACTION_STOP.equals(action)) stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action)) {
            stopRun();
        } else if (ACTION_PAUSE.equals(action)) {
            setPause(true, false);
            voice.speak("일시정지");
        } else if (ACTION_RESUME.equals(action)) {
            setPause(false, false);
            voice.speak("다시 시작합니다");
        } else if (ACTION_SKIP.equals(action)) {
            if (engine != null && !engine.isDone()) {
                engine.skip(elapsed());
                publishCourse();
                notifyNow();
            }
        }
        return START_NOT_STICKY;
    }

    // ───────────────────────── 시작 / 종료 ─────────────────────────

    private void startRun(String courseId) {
        startWallMs = System.currentTimeMillis();
        startElapsed = SystemClock.elapsedRealtime();
        runId = String.valueOf(startWallMs);
        RunState.resetLive();
        RunState.lastSavedId = null;
        autoPauseEnabled = RunState.autoPauseEnabled;

        course = courseId != null ? CourseStore.find(this, courseId) : null;
        if (course != null) {
            engine = new IntervalEngine(course.flatten(), engineListener);
            RunState.courseMode = true;
            RunState.courseName = course.name;
            RunState.courseTotalMs = engine.totalMs();
        }

        active = true;
        RunState.running = true;

        createChannel();
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIF_ID, n);
        }

        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RunBeat:run");
        wakeLock.acquire(12L * 60 * 60 * 1000); // 최대 12시간

        locationManager = getSystemService(LocationManager.class);
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f,
                    this, Looper.getMainLooper());
        } catch (SecurityException e) {
            // 위치 권한이 없으면 MainActivity에서 시작 자체를 막는다.
        }

        sensorManager = getSystemService(SensorManager.class);
        Sensor stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        if (stepSensor != null && hasActivityPermission()) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }

        metronome = new Metronome(this);
        metronome.start();

        voice = new VoiceCoach(this);
        if (course != null) {
            voice.speak(course.name + " 코스를 시작합니다. 총 " + Fmt.shortDuration(course.totalSec()) + ".");
            engine.start(0); // 첫 구간 안내는 리스너에서
        } else {
            voice.speak("러닝을 시작합니다. 화이팅!");
        }
        publishCourse();
        applyMetronome();

        handler.post(ticker);
    }

    private void stopRun() {
        // 1) 저장을 가장 먼저! 아래 정리 작업에서 문제가 생겨도 기록은 남게 한다.
        long el = elapsed();
        RunState.elapsedMs = el;
        save(true);
        RunState.lastSavedId = runId;

        // 2) 그다음 센서·GPS·메트로놈 정리. 하나가 실패해도 나머지는 계속 진행.
        active = false;
        handler.removeCallbacks(ticker);
        try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        try { sensorManager.unregisterListener(this); } catch (Exception ignored) { }
        try { metronome.stop(); } catch (Exception ignored) { }
        RunState.running = false;
        RunState.paused = false;
        RunState.autoPaused = false;

        voice.speak("러닝을 종료합니다. 총 거리 " + Fmt.speakKm(distanceM)
                + ". 시간 " + Fmt.speakDuration(el) + ". 수고하셨어요!");
        getSystemService(NotificationManager.class).notify(NOTIF_ID,
                baseBuilder().setContentTitle("러닝 종료").setContentText("기록 저장 완료").build());

        // 마지막 안내 음성이 끝날 때까지 기다렸다가 정리
        handler.postDelayed(() -> {
            voice.shutdown();
            releaseWakeLock();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }, 9000);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (active) {
            // 시스템이 강제로 종료한 경우: 지금까지 기록이라도 저장
            save(false);
            active = false;
            RunState.running = false;
            if (locationManager != null) locationManager.removeUpdates(this);
            if (sensorManager != null) sensorManager.unregisterListener(this);
            if (metronome != null) metronome.stop();
        }
        if (voice != null) voice.shutdown();
        releaseWakeLock();
        super.onDestroy();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private boolean hasActivityPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ───────────────────────── 일시정지 ─────────────────────────

    private boolean isPaused() {
        return manualPaused || autoPaused;
    }

    /** 운동 시간 = 실제 흐른 시간 - 일시정지했던 시간 */
    private long elapsed() {
        long now = SystemClock.elapsedRealtime();
        long paused = pausedTotalMs + (isPaused() ? now - pauseStartElapsed : 0);
        return now - startElapsed - paused;
    }

    private void setPause(boolean manual, boolean auto) {
        boolean was = isPaused();
        long now = SystemClock.elapsedRealtime();
        manualPaused = manual;
        autoPaused = auto;
        boolean is = isPaused();

        if (!was && is) {
            pauseStartElapsed = now;
        } else if (was && !is) {
            pausedTotalMs += now - pauseStartElapsed;
        }
        if (was != is) {
            // 멈춘 동안 이동한 거리는 세지 않도록, 재개 후 첫 GPS 위치를 새 기준점으로
            lastLoc = null;
            paceWindow.clear();
            RunState.currentPace = 0;
        }
        if (manual || !auto) autoPause.reset(); // 직접 누른 일시정지/재개는 자동 판단을 초기화

        RunState.paused = manualPaused;
        RunState.autoPaused = autoPaused;
        RunState.elapsedMs = elapsed();
        applyMetronome();
        publishCourse();
        notifyNow();
    }

    private void handleAutoPause(AutoPauseDetector.Change c) {
        if (c == AutoPauseDetector.Change.PAUSE) {
            setPause(false, true);
            voice.speak("자동 일시정지");
        } else if (c == AutoPauseDetector.Change.RESUME) {
            setPause(false, false);
            voice.speak("다시 시작합니다");
        }
    }

    /** 최근 3초 동안 걸음이 4번 이상이면 "걷거나 뛰는 중" */
    private boolean isStepping(long now) {
        int n = 0;
        Iterator<Long> it = stepTimes.descendingIterator();
        while (it.hasNext()) {
            if (now - it.next() > 3000) break;
            n++;
        }
        return n >= 4;
    }

    // ───────────────────────── 코스(인터벌) ─────────────────────────

    private final IntervalEngine.Listener engineListener = new IntervalEngine.Listener() {
        @Override
        public void onSegmentStart(Course.Planned seg, int index, int count) {
            voice.speak(segmentAnnouncement(seg));
            applyMetronome();
        }

        @Override
        public void onTenSecondsLeft(Course.Planned seg) {
            voice.speak("10초 남았어요");
        }

        @Override
        public void onHalfway() {
            voice.speak("절반 지났어요. 잘하고 있어요!");
        }

        @Override
        public void onFinished() {
            voice.speak("코스를 모두 마쳤어요! 수고하셨어요. 종료 버튼을 누르면 기록이 저장돼요.");
            applyMetronome();
        }
    };

    private String segmentAnnouncement(Course.Planned s) {
        String time = Fmt.speakDuration(s.sec * 1000L);
        String rep = s.repTotal > 1 ? " " + s.repTotal + "회 중 " + s.rep + "회차." : "";
        switch (s.type) {
            case Course.WARMUP: return "워밍업 걷기 " + time + ". 가볍게 걸으며 몸을 풀어요.";
            case Course.RUN: return "달리기 시작!" + rep + " " + time + ".";
            case Course.WALK: return "걷기." + rep + " " + time + ". 숨을 고르세요.";
            case Course.COOLDOWN: return "쿨다운 걷기 " + time + ". 천천히 걸으며 마무리해요.";
            default: return time;
        }
    }

    /** 화면·알림에 보여줄 코스 상태를 RunState에 기록 */
    private void publishCourse() {
        if (engine == null) return;
        long act = elapsed();
        RunState.courseDone = engine.isDone();
        RunState.courseElapsedMs = engine.courseElapsed(act);
        if (engine.isDone()) {
            RunState.segLabel = "코스 완료";
            RunState.segRemainingMs = 0;
            RunState.nextLabel = "자유롭게 달리거나 종료하세요";
            return;
        }
        Course.Planned cur = engine.current();
        RunState.segLabel = cur.label();
        RunState.segType = cur.type;
        RunState.segRemainingMs = engine.remainingMs(act);
        Course.Planned nx = engine.next();
        RunState.nextLabel = nx == null ? "마지막 구간"
                : "다음: " + nx.label() + " " + Fmt.duration(nx.sec * 1000L);
    }

    /** 메트로놈은 달리는 중일 때만: 일시정지 중이거나, 코스의 달리기 구간이 아니면 끔 */
    private void applyMetronome() {
        if (metronome == null) return;
        int bpm = RunState.targetBpm;
        if (isPaused()) bpm = 0;
        if (engine != null && (engine.isDone() || engine.current().type != Course.RUN)) bpm = 0;
        metronome.setBpm(bpm);
    }

    // ───────────────────────── 1초마다 실행 ─────────────────────────

    private void tick() {
        long now = SystemClock.elapsedRealtime();
        long act = elapsed();
        RunState.elapsedMs = act;
        RunState.kcal = calories();
        RunState.avgPace = distanceM > 50 ? (act / 1000.0) / (distanceM / 1000.0) : 0;

        // 실시간 케이던스: 최근 15초 동안의 걸음 수 × 4
        while (!stepTimes.isEmpty() && now - stepTimes.peekFirst() > 15000) stepTimes.pollFirst();
        long sinceStart = now - startElapsed;
        if (isPaused()) {
            RunState.cadence = 0;
        } else if (sinceStart >= 15000) {
            RunState.cadence = stepTimes.size() * 4;
        } else if (sinceStart > 3000) {
            RunState.cadence = (int) (stepTimes.size() * 60000L / sinceStart);
        }

        // 10초 이상 새 위치가 없으면(멈춤/터널) 현재 페이스는 "알 수 없음"
        if (lastLocElapsed > 0 && now - lastLocElapsed > 10000) RunState.currentPace = 0;

        if (engine != null && !isPaused()) engine.update(act);
        publishCourse();
        applyMetronome(); // 화면에서 bpm을 바꿨을 수도 있으니 매초 반영

        notifyNow();
        tickCount++;
        if (tickCount % 30 == 0) save(false); // 30초마다 중간 저장 (앱이 죽어도 기록 보존)
    }

    /** 러닝 칼로리 근사식: 체중(kg) × 거리(km) × 1.036 */
    private double calories() {
        return RunState.weightKg * (distanceM / 1000.0) * 1.036;
    }

    // ───────────────────────── GPS ─────────────────────────

    @Override
    public void onLocationChanged(Location loc) {
        if (!active) return;
        RunState.gpsAccuracy = loc.hasAccuracy() ? Math.round(loc.getAccuracy()) : -1;
        if (loc.hasAccuracy() && loc.getAccuracy() > MAX_ACCURACY_M) return;

        long now = SystemClock.elapsedRealtime();

        // 속도: GPS가 알려주는 값(도플러)을 우선 사용, 없으면 직전 위치로 계산
        float speed = -1;
        if (loc.hasSpeed()) {
            speed = loc.getSpeed();
        } else if (prevRawLoc != null && now > prevRawElapsed) {
            speed = prevRawLoc.distanceTo(loc) / ((now - prevRawElapsed) / 1000f);
        }
        prevRawLoc = loc;
        prevRawElapsed = now;

        if (autoPauseEnabled && !manualPaused) {
            handleAutoPause(autoPause.onSpeed(speed, now, isStepping(now), distanceM));
        }
        if (isPaused()) return; // 멈춘 동안은 거리·경로를 쌓지 않음

        long act = elapsed();
        if (lastLoc == null) { // 첫 위치 또는 재개 직후
            acceptPoint(loc, now, act);
            return;
        }

        float d = lastLoc.distanceTo(loc);
        if (d < MIN_MOVE_M) return;

        double dt = (now - lastLocElapsed) / 1000.0;
        if (dt > 0 && d / dt > MAX_SPEED_MPS) {
            // GPS 튐. 단, 3번 연속이면 이전 점이 틀렸던 것으로 보고 기준점만 새로 잡는다.
            if (++rejectStreak >= 3) {
                rejectStreak = 0;
                lastLoc = loc;
                lastLocElapsed = now;
                lastLocAct = act;
            }
            return;
        }
        rejectStreak = 0;

        double prevDist = distanceM;
        long prevAct = lastLocAct;
        distanceM += d;
        RunState.distanceM = distanceM;
        acceptPoint(loc, now, act);

        // 현재 페이스: 최근 30초 구간의 거리/시간
        while (paceWindow.size() > 2 && act - paceWindow.peekFirst()[0] > 30000) paceWindow.pollFirst();
        double[] first = paceWindow.peekFirst();
        double dd = distanceM - first[1];
        double tt = (act - first[0]) / 1000.0;
        RunState.currentPace = dd > 15 ? tt / (dd / 1000.0) : 0;

        checkSplits(prevDist, prevAct, act);
    }

    private void acceptPoint(Location loc, long now, long act) {
        lastLoc = loc;
        lastLocElapsed = now;
        lastLocAct = act;
        route.add(loc.getLatitude());
        route.add(loc.getLongitude());
        paceWindow.addLast(new double[]{act, distanceM});
    }

    /** 1km를 넘는 순간을 찾아서 구간 기록 + 음성 안내 */
    private void checkSplits(double prevDist, long prevAct, long act) {
        while (distanceM >= nextKm * 1000.0) {
            double target = nextKm * 1000.0;
            // 두 GPS 점 사이에서 정확히 1km가 되는 시각을 비율로 계산(선형 보간)
            double frac = (target - prevDist) / (distanceM - prevDist);
            long crossMs = prevAct + Math.round(frac * (act - prevAct));
            long splitMs = crossMs - lastSplitCumMs;
            int stepsIn = totalSteps - lastSplitSteps;
            int cad = splitMs > 0 ? (int) Math.round(stepsIn * 60000.0 / splitMs) : 0;

            splits.add(new RunRecord.Split(nextKm, crossMs, splitMs, cad));
            voice.speak(nextKm + "킬로미터. 페이스 " + Fmt.speakPace(splitMs / 1000.0)
                    + ". 경과 시간 " + Fmt.speakDuration(crossMs) + ".");

            lastSplitCumMs = crossMs;
            lastSplitSteps = totalSteps;
            nextKm++;
        }
    }

    // 구형 안드로이드(8~9)에서는 아래 메서드가 없으면 앱이 죽을 수 있어서 직접 구현해둔다.
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }

    // ───────────────────────── 걸음 센서 ─────────────────────────

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!active || event.sensor.getType() != Sensor.TYPE_STEP_DETECTOR) return;
        long now = SystemClock.elapsedRealtime();
        stepTimes.addLast(now);
        if (autoPaused) {
            // 자동 일시정지 중 걸음이 다시 감지되면 GPS를 기다리지 않고 바로 재개
            handleAutoPause(autoPause.onStepping(isStepping(now)));
        }
        if (!isPaused()) {
            totalSteps++;
            RunState.steps = totalSteps;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    // ───────────────────────── 저장 ─────────────────────────

    private void save(boolean finished) {
        long el = elapsed();
        RunRecord r = new RunRecord();
        r.id = runId;
        r.startTime = startWallMs;
        r.durationMs = el;
        r.distanceM = distanceM;
        r.kcal = calories();
        r.weightKg = RunState.weightKg;
        r.steps = totalSteps;
        r.avgCadence = el > 0 ? (int) Math.round(totalSteps * 60000.0 / el) : 0;
        r.finished = finished;
        r.courseName = course != null ? course.name : null;
        r.splits = new ArrayList<>(splits);
        r.route = new double[route.size()];
        for (int i = 0; i < route.size(); i++) r.route[i] = route.get(i);
        RunStorage.save(this, r);
    }

    // ───────────────────────── 알림 (잠금화면 컨트롤) ─────────────────────────

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "러닝 기록",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification.Builder baseBuilder() {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_run)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC) // 잠금화면에서도 내용·버튼 표시
                .setContentIntent(pi);
    }

    private Notification buildNotification() {
        long act = elapsed();
        String title;
        String text;
        String stats = Fmt.km(distanceM) + " km · " + Fmt.duration(act);

        if (autoPaused) {
            title = "자동 일시정지됨";
            text = "다시 달리면 자동으로 재개돼요 · " + stats;
        } else if (manualPaused) {
            title = "일시정지됨";
            text = stats;
        } else if (engine != null && !engine.isDone()) {
            title = RunState.segLabel + " · " + Fmt.duration(RunState.segRemainingMs) + " 남음";
            text = stats + " · " + RunState.nextLabel;
        } else if (engine != null) {
            title = "코스 완료 · 기록 중";
            text = stats;
        } else {
            title = "러닝 중";
            text = stats + " · 평균 " + Fmt.pace(RunState.avgPace) + "/km";
        }

        Notification.Builder b = baseBuilder()
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true);

        // 버튼: 달리는 중 → [일시정지] (+ 코스면 [건너뛰기]) / 멈춘 중 → [재개] [종료]
        if (isPaused()) {
            b.addAction(action("재개", ACTION_RESUME, 2));
            b.addAction(action("종료", ACTION_STOP, 3));
        } else {
            b.addAction(action("일시정지", ACTION_PAUSE, 1));
            if (engine != null && !engine.isDone()) b.addAction(action("건너뛰기", ACTION_SKIP, 4));
        }
        return b.build();
    }

    private Notification.Action action(String title, String act, int requestCode) {
        Intent i = new Intent(this, RunService.class).setAction(act);
        PendingIntent pi = PendingIntent.getService(this, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_run), title, pi)
                .build();
    }

    private void notifyNow() {
        if (!active) return;
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification());
    }
}
