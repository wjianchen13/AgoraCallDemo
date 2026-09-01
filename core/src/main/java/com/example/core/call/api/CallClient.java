package com.example.core.call.api;

import android.view.SurfaceView;

import com.example.core.call.model.CallJoinRequest;
import com.example.core.call.model.CallInviteRequest;
import com.example.core.call.model.CallSnapshot;
import com.example.core.call.model.SignalingLoginRequest;

public interface CallClient {
    void loginSignaling(SignalingLoginRequest request);

    void logoutSignaling();

    void startCall(CallInviteRequest request);

    void acceptCall();

    void rejectCall();

    void join(CallJoinRequest request);

    void leave();

    void setMicrophoneEnabled(boolean enabled);

    void setCameraEnabled(boolean enabled);

    void switchCamera();

    void setSpeakerEnabled(boolean enabled);

    void attachLocalVideo(SurfaceView view);

    void attachRemoteVideo(SurfaceView view);

    CallSnapshot getSnapshot();

    void addListener(CallListener listener);

    void removeListener(CallListener listener);

    void release();
}
