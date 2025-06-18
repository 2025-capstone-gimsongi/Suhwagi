package com.capstone.suhwagi.observer;

import com.google.firebase.database.DatabaseReference;

import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SetRemoteSdpObserver extends BaseSdpObserver {
    public SetRemoteSdpObserver(PeerConnection connection, DatabaseReference call,
                                List<IceCandidate> pendingCandidates, AtomicBoolean remoteSdpSet) {
        super(connection, call, pendingCandidates, remoteSdpSet);
    }

    @Override
    public void onSetSuccess() {
        remoteSdpSet.set(true);
        for (IceCandidate candidate : pendingCandidates) {
            connection.addIceCandidate(candidate);
        }
        pendingCandidates.clear();

        if (connection.getRemoteDescription().type == SessionDescription.Type.OFFER) {
            connection.createAnswer(
                new CreateSdpObserver(connection, call, pendingCandidates, remoteSdpSet, null),
                new MediaConstraints()
            );
        }
    }

    @Override public void onCreateFailure(String error) {}
    @Override public void onCreateSuccess(SessionDescription sdp) {}
    @Override public void onSetFailure(String error) {}
}