package com.capstone.suhwagi.observer;

import com.google.firebase.database.DatabaseReference;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SdpObserver;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseSdpObserver implements SdpObserver {
    protected final PeerConnection connection;
    protected final DatabaseReference call;
    protected final List<IceCandidate> pendingCandidates;
    protected final AtomicBoolean remoteSdpSet;

    protected BaseSdpObserver(PeerConnection connection, DatabaseReference call,
                              List<IceCandidate> pendingCandidates, AtomicBoolean remoteSdpSet) {
        this.connection = connection;
        this.call = call;
        this.pendingCandidates = pendingCandidates;
        this.remoteSdpSet = remoteSdpSet;
    }
}