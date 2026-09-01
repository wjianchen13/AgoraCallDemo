package com.example.core.call.signaling;

import com.example.core.call.model.CallType;

public interface SignalingClient {
    void setListener(SignalingClientListener listener);

    void login(String accountId, String token, SignalingCallback<Void> callback);

    void logout(SignalingCallback<Void> callback);

    void startCall(
            String calleeAccountId,
            String rtcChannelName,
            CallType callType,
            SignalingCallback<SignalingSession> callback
    );

    void accept(SignalingSession session, SignalingCallback<SignalingSession> callback);

    void reject(SignalingSession session, SignalingCallback<Void> callback);

    void cancel(SignalingSession session, SignalingCallback<Void> callback);

    void leave(SignalingSession session, SignalingCallback<Void> callback);

    void release();
}
