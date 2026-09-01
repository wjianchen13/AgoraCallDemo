package com.example.core.call.rtc.agora;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.core.call.model.CallError;
import com.example.core.call.model.CallType;
import com.example.core.call.rtc.RtcClient;
import com.example.core.call.rtc.RtcClientListener;
import com.example.core.call.rtc.RtcJoinParams;

import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.video.VideoEncoderConfiguration;

public final class AgoraRtcClient implements RtcClient {
    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService releaseExecutor = Executors.newSingleThreadExecutor();

    private RtcEngine engine;
    private RtcClientListener listener;
    private SurfaceView localVideoView;
    private SurfaceView remoteVideoView;
    private int remoteUid;
    private boolean videoCall;

    public AgoraRtcClient(Context context) {
        appContext = context.getApplicationContext();
    }

    @Override
    public void initialize(String appId, RtcClientListener listener) throws Exception {
        this.listener = listener;
        if (engine != null) {
            return;
        }
        RtcEngineConfig config = new RtcEngineConfig();
        config.mContext = appContext;
        config.mAppId = appId;
        config.mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING;
        config.mEventHandler = eventHandler;
        engine = RtcEngine.create(config);
        engine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER);
        engine.setDefaultAudioRoutetoSpeakerphone(true);
    }

    @Override
    public int join(RtcJoinParams params) {
        if (engine == null) {
            return -7;
        }
        videoCall = params.getCallType() == CallType.VIDEO;
        engine.enableAudio();
        if (videoCall) {
            engine.enableVideo();
            engine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(
                    new VideoEncoderConfiguration.VideoDimensions(640, 360),
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                    VideoEncoderConfiguration.STANDARD_BITRATE,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
            ));
            setupLocalVideo();
            engine.startPreview();
        } else {
            engine.disableVideo();
        }

        ChannelMediaOptions options = new ChannelMediaOptions();
        options.channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING;
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER;
        options.autoSubscribeAudio = true;
        options.autoSubscribeVideo = true;
        options.publishMicrophoneTrack = true;
        options.publishCameraTrack = videoCall;

        String token = params.getToken();
        if (token != null && token.isEmpty()) {
            token = null;
        }
        return engine.joinChannel(
                token,
                params.getChannelName(),
                params.getUid(),
                options
        );
    }

    @Override
    public void leave() {
        RtcEngine current = engine;
        if (current == null) {
            return;
        }
        current.stopPreview();
        current.leaveChannel();
        if (remoteUid != 0) {
            current.setupRemoteVideo(new VideoCanvas(
                    null,
                    VideoCanvas.RENDER_MODE_HIDDEN,
                    remoteUid
            ));
        }
        current.setupLocalVideo(new VideoCanvas(
                null,
                VideoCanvas.RENDER_MODE_HIDDEN,
                0
        ));
        remoteUid = 0;
    }

    @Override
    public int renewToken(String token) {
        return engine == null ? -7 : engine.renewToken(token);
    }

    @Override
    public int setMicrophoneEnabled(boolean enabled) {
        if (engine == null) {
            return -7;
        }
        int captureResult = engine.enableLocalAudio(enabled);
        int publishResult = engine.muteLocalAudioStream(!enabled);
        return captureResult != 0 ? captureResult : publishResult;
    }

    @Override
    public int setCameraEnabled(boolean enabled) {
        if (engine == null || !videoCall) {
            return -7;
        }
        int captureResult = engine.enableLocalVideo(enabled);
        int publishResult = engine.muteLocalVideoStream(!enabled);
        return captureResult != 0 ? captureResult : publishResult;
    }

    @Override
    public int switchCamera() {
        return engine == null || !videoCall ? -7 : engine.switchCamera();
    }

    @Override
    public int setSpeakerEnabled(boolean enabled) {
        return engine == null ? -7 : engine.setEnableSpeakerphone(enabled);
    }

    @Override
    public void attachLocalVideo(SurfaceView view) {
        localVideoView = view;
        if (engine != null && videoCall) {
            setupLocalVideo();
        }
    }

    @Override
    public void attachRemoteVideo(SurfaceView view) {
        remoteVideoView = view;
        if (engine != null && remoteUid != 0 && videoCall) {
            setupRemoteVideo(remoteUid);
        }
    }

    private void setupLocalVideo() {
        if (engine == null || localVideoView == null) {
            return;
        }
        engine.setupLocalVideo(new VideoCanvas(
                localVideoView,
                VideoCanvas.RENDER_MODE_HIDDEN,
                0
        ));
    }

    private void setupRemoteVideo(int uid) {
        if (engine == null || remoteVideoView == null) {
            return;
        }
        engine.setupRemoteVideo(new VideoCanvas(
                remoteVideoView,
                VideoCanvas.RENDER_MODE_HIDDEN,
                uid
        ));
    }

    @Override
    public void release() {
        RtcEngine current = engine;
        engine = null;
        listener = null;
        localVideoView = null;
        remoteVideoView = null;
        remoteUid = 0;
        if (current == null) {
            releaseExecutor.shutdown();
            return;
        }
        current.stopPreview();
        current.leaveChannel();
        releaseExecutor.execute(() -> {
            RtcEngine.destroy();
            releaseExecutor.shutdown();
        });
    }

    private void postToMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    private final IRtcEngineEventHandler eventHandler = new IRtcEngineEventHandler() {
        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            postToMain(() -> {
                if (listener != null) {
                    listener.onJoinSuccess(uid);
                }
            });
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            remoteUid = uid;
            postToMain(() -> {
                if (videoCall) {
                    setupRemoteVideo(uid);
                }
                if (listener != null) {
                    listener.onRemoteUserJoined(uid);
                }
            });
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            postToMain(() -> {
                if (engine != null && remoteUid == uid) {
                    engine.setupRemoteVideo(new VideoCanvas(
                            null,
                            VideoCanvas.RENDER_MODE_HIDDEN,
                            uid
                    ));
                    remoteUid = 0;
                }
                if (listener != null) {
                    listener.onRemoteUserLeft(uid, reason);
                }
            });
        }

        @Override
        public void onConnectionStateChanged(int state, int reason) {
            postToMain(() -> {
                if (listener != null) {
                    listener.onConnectionStateChanged(state, reason);
                }
            });
        }

        @Override
        public void onUserMuteAudio(int uid, boolean muted) {
            postToMain(() -> {
                if (listener != null) {
                    listener.onRemoteAudioMuted(uid, muted);
                }
            });
        }

        @Override
        public void onUserMuteVideo(int uid, boolean muted) {
            postToMain(() -> {
                if (listener != null) {
                    listener.onRemoteVideoMuted(uid, muted);
                }
            });
        }

        @Override
        public void onTokenPrivilegeWillExpire(String token) {
            postToMain(() -> {
                if (listener != null) {
                    listener.onTokenWillExpire();
                }
            });
        }

        @Override
        public void onRequestToken() {
            postToMain(() -> {
                if (listener != null) {
                    listener.onTokenRequired();
                }
            });
        }

        @Override
        public void onError(int errorCode) {
            postToMain(() -> {
                if (listener != null) {
                    listener.onError(new CallError(
                            CallError.Domain.RTC,
                            errorCode,
                            "RTC 错误 " + errorCode + "："
                                    + RtcEngine.getErrorDescription(Math.abs(errorCode))
                    ));
                }
            });
        }
    };
}
