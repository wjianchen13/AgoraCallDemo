package com.example.core.call.rtc;

import com.example.core.call.model.CallError;

public interface RtcClientListener {
    void onJoinSuccess(int localUid);

    void onRemoteUserJoined(int remoteUid);

    void onRemoteUserLeft(int remoteUid, int reason);

    void onConnectionStateChanged(int state, int reason);

    void onRemoteAudioMuted(int remoteUid, boolean muted);

    void onRemoteVideoMuted(int remoteUid, boolean muted);

    void onTokenWillExpire();

    void onTokenRequired();

    void onError(CallError error);
}
