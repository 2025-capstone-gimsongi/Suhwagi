package com.capstone.suhwagi;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;

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
    }

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