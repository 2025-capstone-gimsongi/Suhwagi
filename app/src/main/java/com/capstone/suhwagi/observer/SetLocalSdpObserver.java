package com.capstone.suhwagi.observer;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SetLocalSdpObserver extends BaseSdpObserver {
    private final ValueEventListener answerListener;

    public SetLocalSdpObserver(PeerConnection connection, DatabaseReference call,
                               List<IceCandidate> pendingCandidates, AtomicBoolean remoteSdpSet,
                               ValueEventListener answerListener) {
        super(connection, call, pendingCandidates, remoteSdpSet);
        this.answerListener = answerListener;
    }

    @Override
    public void onSetSuccess() {
        SessionDescription sdp = connection.getLocalDescription();
        String type = sdp.type == SessionDescription.Type.OFFER ? "offer" : "answer";
        call.child(type).setValue(sdp.description);

        if (type.equals("offer")) {
            call.child("answer").addValueEventListener(answerListener);
        }
    }

    @Override public void onCreateFailure(String error) {}
    @Override public void onCreateSuccess(SessionDescription sdp) {}
    @Override public void onSetFailure(String error) {}
}
