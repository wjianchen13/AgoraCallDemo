package com.example.core.call.signaling;

public interface SignalingClientListener {
    void onIncomingCall(SignalingSession session);

    void onCallAccepted(SignalingSession session);

    void onCallRejected(SignalingSession session);

    void onCallCanceled(SignalingSession session);

    void onRemoteHangup(SignalingSession session);

    void onSignalingError(int code, String message);
}
