package com.example.core.call.rtc;

import android.view.SurfaceView;

public interface RtcClient {
    void initialize(String appId, RtcClientListener listener) throws Exception;

    int join(RtcJoinParams params);

    void leave();

    int renewToken(String token);

    int setMicrophoneEnabled(boolean enabled);

    int setCameraEnabled(boolean enabled);

    int switchCamera();

    int setSpeakerEnabled(boolean enabled);

    void attachLocalVideo(SurfaceView view);

    void attachRemoteVideo(SurfaceView view);

    void release();
}
