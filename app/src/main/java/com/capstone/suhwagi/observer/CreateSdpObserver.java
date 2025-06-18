package com.capstone.suhwagi.observer;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CreateSdpObserver extends BaseSdpObserver {
    private final ValueEventListener answerListener;

    public CreateSdpObserver(PeerConnection connection, DatabaseReference call,
                             List<IceCandidate> pendingCandidates, AtomicBoolean remoteSdpSet,
                             ValueEventListener answerListener) {
        super(connection, call, pendingCandidates, remoteSdpSet);
        this.answerListener = answerListener;
    }

    @Override
    public void onCreateSuccess(SessionDescription sdp) {
        connection.setLocalDescription(
            new SetLocalSdpObserver(connection, call, pendingCandidates, remoteSdpSet, answerListener),
            sdp
        );
    }

    @Override public void onCreateFailure(String error) {}
    @Override public void onSetFailure(String error) {}
    @Override public void onSetSuccess() {}
}