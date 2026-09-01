package com.example.core.call.api;

import android.view.SurfaceView;

import com.example.core.call.model.CallJoinRequest;
import com.example.core.call.model.CallSnapshot;

public interface CallClient {
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
