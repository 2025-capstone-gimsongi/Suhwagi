package com.capstone.suhwagi;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.*;

import com.google.mediapipe.tasks.vision.core.*;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.*;
import com.google.mediapipe.framework.image.BitmapImageBuilder;

import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import android.graphics.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Arrays;
import java.time.Duration;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwnerKt;

import com.capstone.suhwagi.databinding.ActivityCallBinding;

import java.util.Collection;

import io.livekit.android.ConnectOptions;
import io.livekit.android.LiveKit;
import io.livekit.android.LiveKitOverrides;
import io.livekit.android.RoomOptions;
import io.livekit.android.events.RoomEvent;
import io.livekit.android.room.Room;
import io.livekit.android.room.participant.RemoteParticipant;
import io.livekit.android.room.track.CameraPosition;
import io.livekit.android.room.track.LocalTrackPublication;
import io.livekit.android.room.track.LocalVideoTrack;
import io.livekit.android.room.track.LocalVideoTrackOptions;
import io.livekit.android.room.track.Track;
import io.livekit.android.room.track.TrackPublication;
import io.livekit.android.room.track.VideoPreset43;
import io.livekit.android.room.track.VideoTrack;
import kotlin.Unit;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Dispatchers;

// === 서버 ===
private static final String SERVER_BASE_URL = "https://your.server.com"; // 추후 수정 필요

// === 시퀀스 스펙 ===
private static final int SEQ_LEN = 30;
private static final int FEAT_DIM = 126; // 2손*21점*xyz

// 버퍼 & 전송레이트 제한
private final Deque<float[]> seqBuffer = new ArrayDeque<>();
private volatile long lastSentMs = 0L;

// OkHttp 클라이언트
private final OkHttpClient http = new OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(3))
        .build();

// Mediapipe
private HandLandmarker handLandmarker;
private java.util.concurrent.ExecutorService mpExecutor;
private volatile boolean isRunningInference = false;


public class CallActivity extends AppCompatActivity {
    private static final String URL = "ws://10.0.2.2:7880";
    private static final String DEAF = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ2aWRlbyI6eyJyb29tQ3JlYXRlIjp0cnVlLCJyb29tSm9pbiI6dHJ1ZSwicm9vbSI6ImRldi1yb29tIiwiY2FuUHVibGlzaCI6dHJ1ZSwiY2FuU3Vic2NyaWJlIjp0cnVlLCJjYW5QdWJsaXNoRGF0YSI6dHJ1ZX0sInN1YiI6ImRlYWYiLCJpc3MiOiJkZXZrZXkiLCJuYmYiOjE3NTY4NjEyMDAsImV4cCI6NDkxMjUzNDgwMH0.Y3cYoFm-6ZLWYjzit8gx0K3GOcNWJM0Le1T-KuLlIeY";
    private static final String HEARING = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ2aWRlbyI6eyJyb29tQ3JlYXRlIjp0cnVlLCJyb29tSm9pbiI6dHJ1ZSwicm9vbSI6ImRldi1yb29tIiwiY2FuUHVibGlzaCI6dHJ1ZSwiY2FuU3Vic2NyaWJlIjp0cnVlLCJjYW5QdWJsaXNoRGF0YSI6dHJ1ZX0sInN1YiI6ImhlYXJpbmciLCJpc3MiOiJkZXZrZXkiLCJuYmYiOjE3NTY4NjEyMDAsImV4cCI6NDkxMjUzNDgwMH0.5Jz06F6qRg7kUUBfwQhwTWWZ1hY3k3R9n59D7RhPaeQ";

    private ActivityCallBinding binding;
    private Room room;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (!hasPermissions()) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage("필수 권한을 허용해 주세요.")
                .setPositiveButton("확인", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
            return;
        }

        initRoom();

        boolean isDeaf = getIntent().getBooleanExtra("isDeaf", false);
        if (isDeaf) {
            joinRoom(DEAF);
        } else {
            joinRoom(HEARING);
        }

        mpExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        initMediapipe();
    }   

    private void initMediapipe() {
        try {
            BaseOptions base = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task") // assets에 넣기
                    .setDelegate(BaseOptions.Delegate.GPU)
                    .build();

            HandLandmarkerOptions opts = HandLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setNumHands(2)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setRunningMode(RunningMode.IMAGE)
                    .build();

            handLandmarker = HandLandmarker.createFromOptions(this, opts);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private final VideoSink mediapipeSink = new VideoSink() {
        @Override
        public void onFrame(VideoFrame frame) {
            if (isFinishing() || isDestroyed()) return;

            // 과부하 방지 (샘플링)
            if (isRunningInference) return;
            isRunningInference = true;

            VideoFrame.Buffer buffer = frame.getBuffer();
            VideoFrame.I420Buffer i420 = buffer.toI420();
            final int rotation = frame.getRotation();

            mpExecutor.execute(() -> {
                try {
                    // 1) I420 -> Bitmap (간단 변환; 나중에 Camera2/ScriptIntrinsic로 최적화 가능)
                    Bitmap bmp = i420ToBitmap(i420, rotation);
                    // 2) Mediapipe 실행 → 126차원 피처 추출
                    float[] feat = runMediapipeAndExtract(bmp);
                    // 3) 30프레임 버퍼에 적재 & 전송 트리거
                    if (feat != null) onOneFrameFeatures(feat);
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    i420.release();
                    isRunningInference = false;
                }
            });
        }
    };

    private boolean hasPermissions() {
        String[] permissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        };
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    private void initRoom() {
        RoomOptions options = new RoomOptions(
            false, false, null, null,
            new LocalVideoTrackOptions(false, null, CameraPosition.FRONT, VideoPreset43.H480.getCapture()),
            null, null
        );
        room = LiveKit.INSTANCE.create(this, options, new LiveKitOverrides());
        initRenderers();
    }

    private void initRenderers() {
        room.initVideoRenderer(binding.surfaceLocal);
        binding.surfaceLocal.setZOrderMediaOverlay(true);
        binding.surfaceLocal.setZOrderOnTop(true);
        binding.surfaceLocal.setEnableHardwareScaler(true);
        binding.surfaceLocal.setMirror(true);

        room.initVideoRenderer(binding.surfaceRemote);
        binding.surfaceRemote.setZOrderOnTop(true);
        binding.surfaceRemote.setEnableHardwareScaler(true);
    }

    private void joinRoom(String token) {
        CoroutineScope lifecycleScope = LifecycleOwnerKt.getLifecycleScope(this);
        BuildersKt.launch(lifecycleScope, (CoroutineContext)Dispatchers.getMain(), CoroutineStart.DEFAULT, (outerScope, outerContinuation) -> {
            BuildersKt.launch(outerScope, (CoroutineContext)Dispatchers.getMain(), CoroutineStart.DEFAULT, (innerScope, innerContinuation) -> {
                RoomCoroutinesKt.collectInScope(room.getEvents(), innerScope, (event, continuation) -> {
                    if (event instanceof RoomEvent.TrackSubscribed) {
                        onTrackSubscribed((RoomEvent.TrackSubscribed)event);
                    }
                    return Unit.INSTANCE;
                });
                return Unit.INSTANCE;
            });

            RoomCoroutinesKt.connectInScope(room, outerScope, URL, token, new ConnectOptions(), success -> {
                if (success) {
                    LocalTrackPublication localPublication = room.getLocalParticipant().getTrackPublication(Track.Source.CAMERA);
                    if (localPublication != null && localPublication.getTrack() instanceof LocalVideoTrack) {
                        LocalVideoTrack localTrack = (LocalVideoTrack)localPublication.getTrack();
                        attachLocalVideo(localTrack);
                    }

                    Collection<RemoteParticipant> participants = room.getRemoteParticipants().values();
                    if (!participants.isEmpty()) {
                        TrackPublication remotePublication = participants.iterator().next().getTrackPublication(Track.Source.CAMERA);
                        if (remotePublication != null && remotePublication.getTrack() instanceof VideoTrack) {
                            VideoTrack remoteTrack = (VideoTrack)remotePublication.getTrack();
                            attachRemoteVideo(remoteTrack);
                        }
                    }
                }
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });
    }

    private void onTrackSubscribed(RoomEvent.TrackSubscribed event) {
        Track track = event.getTrack();
        if (track instanceof VideoTrack) {
            attachRemoteVideo((VideoTrack)track);
        }
    }

    private void attachLocalVideo(VideoTrack videoTrack) {
        videoTrack.addRenderer(binding.surfaceLocal);
        videoTrack.addRenderer(mediapipeSink);
    }

    private void attachRemoteVideo(VideoTrack videoTrack) {
        videoTrack.addRenderer(binding.surfaceRemote);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        binding.surfaceLocal.release();
        binding.surfaceRemote.release();

        if (room != null) {
            room.disconnect();
        }
    }
}