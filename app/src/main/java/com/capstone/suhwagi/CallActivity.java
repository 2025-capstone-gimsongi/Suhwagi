package com.capstone.suhwagi;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.capstone.suhwagi.databinding.ActivityCallBinding;
import com.capstone.suhwagi.observer.CreateSdpObserver;
import com.capstone.suhwagi.observer.SetRemoteSdpObserver;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class CallActivity extends AppCompatActivity {
    private ActivityCallBinding binding;
    private DatabaseReference call;

    private EglBase base;
    private PeerConnectionFactory factory;
    private PeerConnection connection;
    private VideoCapturer capturer;
    private SurfaceTextureHelper helper;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private VideoTrack remoteVideoTrack;
    private AudioSource audioSource;

    private ValueEventListener answerListener;
    private ChildEventListener iceListener;
    private final List<IceCandidate> pendingCandidates = new ArrayList<>();
    private final AtomicBoolean remoteSdpSet = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (!hasPermissions()) {
            finish();
            return;
        }

        base = EglBase.create();
        factory = createFactory();
        initRenderers();

        Intent intent = getIntent();
        String id = intent.getStringExtra("id");
        call = FirebaseDatabase.getInstance().getReference("calls").child(id);

        boolean isCaller = intent.getBooleanExtra("isCaller", false);
        if (isCaller) {
            createCall();
        } else {
            joinCall();
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

    private PeerConnectionFactory createFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions()
        );
        return PeerConnectionFactory.builder()
            .setOptions(new PeerConnectionFactory.Options())
            .setVideoDecoderFactory(new DefaultVideoDecoderFactory(base.getEglBaseContext()))
            .setVideoEncoderFactory(new DefaultVideoEncoderFactory(base.getEglBaseContext(), false, false))
            .createPeerConnectionFactory();
    }

    private void initRenderers() {
        binding.surfaceLocal.init(base.getEglBaseContext(), null);
        binding.surfaceLocal.setZOrderMediaOverlay(true);
        binding.surfaceLocal.setZOrderOnTop(true);
        binding.surfaceLocal.setEnableHardwareScaler(true);
        binding.surfaceLocal.setMirror(true);

        binding.surfaceRemote.init(base.getEglBaseContext(), null);
        binding.surfaceRemote.setZOrderOnTop(true);
        binding.surfaceRemote.setEnableHardwareScaler(true);
    }

    private void createCall() {
        connection = createPeerConnection(true);
        addLocalStream();

        answerListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String answer = snapshot.getValue(String.class);
                if (answer != null) {
                    call.child("answer").removeEventListener(this);

                    connection.setRemoteDescription(
                        new SetRemoteSdpObserver(connection, call, pendingCandidates, remoteSdpSet),
                        new SessionDescription(SessionDescription.Type.ANSWER, answer)
                    );
                }
            }

            @Override public void onCancelled(DatabaseError error) {}
        };
        connection.createOffer(
            new CreateSdpObserver(connection, call, pendingCandidates, remoteSdpSet, answerListener),
            new MediaConstraints()
        );
    }

    private void joinCall() {
        connection = createPeerConnection(false);
        addLocalStream();

        call.child("offer").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String offer = snapshot.getValue(String.class);
                if (offer != null) {
                    connection.setRemoteDescription(
                        new SetRemoteSdpObserver(connection, call, pendingCandidates, remoteSdpSet),
                        new SessionDescription(SessionDescription.Type.OFFER, offer)
                    );
                }
            }

            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private PeerConnection createPeerConnection(boolean isCaller) {
        List<PeerConnection.IceServer> servers = new ArrayList<>();
        servers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        String username = "LKpris1Lwi9f7jNhWw0xWBIhOvLv4TxGddWz4egkJI56hk5yK6LTylgon4-youb5AAAAAGhTKNxnaW1zb25naTIwMjU=";
        String password = "3a1e3106-4c87-11f0-862a-0242ac120004";
        String[] turns = {
            "turn:hk-turn1.xirsys.com:80?transport=udp",
            "turn:hk-turn1.xirsys.com:3478?transport=udp",
            "turn:hk-turn1.xirsys.com:80?transport=tcp",
            "turn:hk-turn1.xirsys.com:3478?transport=tcp",
            "turns:hk-turn1.xirsys.com:443?transport=tcp",
            "turns:hk-turn1.xirsys.com:5349?transport=tcp"
        };
        for (String turn : turns) {
            servers.add(PeerConnection.IceServer.builder(turn)
                .setUsername(username).setPassword(password).createIceServer());
        }

        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(servers);
        config.sdpSemantics = PeerConnection.SdpSemantics.PLAN_B;

        PeerConnection.Observer observer = new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate candidate) {
                Map<String, Object> map = new HashMap<>();
                map.put("sdpMid", candidate.sdpMid);
                map.put("sdpMLineIndex", candidate.sdpMLineIndex);
                map.put("sdp", candidate.sdp);

                call.child(isCaller ? "callerCandidates" : "calleeCandidates")
                    .push().setValue(map);
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
                if (newState == PeerConnection.IceConnectionState.COMPLETED) {
                    call.child(isCaller ? "calleeCandidates" : "callerCandidates")
                        .removeEventListener(iceListener);
                } else if (newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                    runOnUiThread(() -> binding.surfaceRemote.clearImage());
                }
            }

            @Override
            public void onAddStream(MediaStream stream) {
                if (!stream.videoTracks.isEmpty()) {
                    remoteVideoTrack = stream.videoTracks.get(0);
                    runOnUiThread(() -> remoteVideoTrack.addSink(binding.surfaceRemote));
                }
            }

            @Override public void onDataChannel(DataChannel dataChannel) {}
            @Override public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}
            @Override public void onIceConnectionReceivingChange(boolean receiving) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {}
            @Override public void onRemoveStream(MediaStream stream) {}
            @Override public void onRenegotiationNeeded() {}
            @Override public void onSignalingChange(PeerConnection.SignalingState newState) {}
        };

        iceListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String sdpMid = snapshot.child("sdpMid").getValue(String.class);
                Integer sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(Integer.class);
                String sdp = snapshot.child("sdp").getValue(String.class);

                if (sdpMid != null && sdpMLineIndex != null && sdp != null) {
                    IceCandidate candidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
                    if (remoteSdpSet.get()) {
                        connection.addIceCandidate(candidate);
                    } else {
                        pendingCandidates.add(candidate);
                    }
                }
            }

            @Override public void onCancelled(DatabaseError error) {}
            @Override public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onChildRemoved(DataSnapshot snapshot) {}
        };
        call.child(isCaller ? "calleeCandidates" : "callerCandidates")
            .addChildEventListener(iceListener);

        return factory.createPeerConnection(config, observer);
    }

    private void addLocalStream() {
        Camera1Enumerator enumerator = new Camera1Enumerator(true);
        for (String name : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(name)) {
                capturer = enumerator.createCapturer(name, null);
                break;
            }
        }
        helper = SurfaceTextureHelper.create("CaptureThread", base.getEglBaseContext());

        videoSource = factory.createVideoSource(capturer.isScreencast());
        capturer.initialize(helper, this, videoSource.getCapturerObserver());
        capturer.startCapture(640, 480, 30);
        localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource);

        audioSource = factory.createAudioSource(new MediaConstraints());
        AudioTrack localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource);

        MediaStream localStream = factory.createLocalMediaStream("ARDAMS");
        localStream.addTrack(localVideoTrack);
        localStream.addTrack(localAudioTrack);

        connection.addStream(localStream);
        localVideoTrack.addSink(binding.surfaceLocal);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (call != null) {
            if (answerListener != null) {
                call.child("answer").removeEventListener(answerListener);
            }
            if (iceListener != null) {
                call.child("calleeCandidates").removeEventListener(iceListener);
                call.child("callerCandidates").removeEventListener(iceListener);
            }
        }
        pendingCandidates.clear();

        if (localVideoTrack != null) localVideoTrack.removeSink(binding.surfaceLocal);
        if (remoteVideoTrack != null) remoteVideoTrack.removeSink(binding.surfaceRemote);
        binding.surfaceLocal.release();
        binding.surfaceRemote.release();
        if (connection != null) {
            connection.close();
            connection.dispose();
        }

        if (capturer != null) {
            try {
                capturer.stopCapture();
            } catch (InterruptedException ignored) {
            }
            capturer.dispose();
        }
        if (videoSource != null) videoSource.dispose();
        if (audioSource != null) audioSource.dispose();

        if (helper != null) helper.dispose();
        if (factory != null) factory.dispose();
        if (base != null) base.release();
    }
}