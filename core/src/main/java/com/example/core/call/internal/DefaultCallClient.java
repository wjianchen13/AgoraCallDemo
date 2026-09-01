package com.example.core.call.internal;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import com.example.core.call.api.CallClient;
import com.example.core.call.api.CallListener;
import com.example.core.call.model.CallError;
import com.example.core.call.model.CallJoinRequest;
import com.example.core.call.model.CallSnapshot;
import com.example.core.call.model.CallState;
import com.example.core.call.model.CallType;
import com.example.core.call.rtc.RtcClient;
import com.example.core.call.rtc.RtcClientListener;
import com.example.core.call.rtc.RtcJoinParams;
import com.example.core.call.rtc.agora.AgoraRtcClient;
import com.example.core.call.token.RtcTokenProvider;

import io.agora.rtc2.Constants;
import io.agora.rtc2.RtcEngine;

public final class DefaultCallClient implements CallClient, RtcClientListener {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<CallListener> listeners = new CopyOnWriteArraySet<>();
    private final String appId;
    private final RtcClient rtcClient;
    private final RtcTokenProvider tokenProvider;

    private CallState state = CallState.IDLE;
    private CallType callType = CallType.VIDEO;
    private String channelName = "";
    private int requestedUid;
    private int localUid;
    private int remoteUid;
    private boolean microphoneEnabled = true;
    private boolean cameraEnabled = true;
    private boolean speakerEnabled = true;
    private String statusMessage = "请输入频道名开始测试";
    private boolean released;

    public DefaultCallClient(Context context, String appId, RtcTokenProvider tokenProvider) {
        this.appId = appId == null ? "" : appId.trim();
        this.tokenProvider = tokenProvider;
        this.rtcClient = new AgoraRtcClient(context);
    }

    @Override
    public void join(CallJoinRequest request) {
        runOnMain(() -> joinOnMain(request));
    }

    private void joinOnMain(CallJoinRequest request) {
        if (released) {
            reportError(new CallError(CallError.Domain.INTERNAL, -1, "CallClient 已释放"));
            return;
        }
        if (state != CallState.IDLE && state != CallState.FAILED) {
            reportError(new CallError(CallError.Domain.VALIDATION, -1, "当前正在通话中"));
            return;
        }
        if (appId.isEmpty()) {
            fail(new CallError(CallError.Domain.VALIDATION, -1, "AGORA_APP_ID 未配置"));
            return;
        }
        if (request == null || request.getChannelName().isEmpty()) {
            fail(new CallError(CallError.Domain.VALIDATION, -1, "频道名不能为空"));
            return;
        }

        channelName = request.getChannelName();
        requestedUid = request.getUid();
        callType = request.getCallType();
        localUid = 0;
        remoteUid = 0;
        microphoneEnabled = true;
        cameraEnabled = callType == CallType.VIDEO;
        speakerEnabled = true;
        updateState(CallState.PREPARING, "正在准备音视频引擎…");

        try {
            rtcClient.initialize(appId, this);
        } catch (Exception error) {
            fail(new CallError(
                    CallError.Domain.RTC,
                    -1,
                    "初始化 RTC 失败：" + error.getMessage()
            ));
            return;
        }

        if (!request.getToken().isEmpty()) {
            joinWithToken(request.getToken());
            return;
        }

        updateState(CallState.PREPARING, "正在获取测试 Token…");
        tokenProvider.requestToken(channelName, requestedUid, new RtcTokenProvider.Callback() {
            @Override
            public void onSuccess(String token) {
                runOnMain(() -> joinWithToken(token));
            }

            @Override
            public void onError(CallError error) {
                runOnMain(() -> fail(error));
            }
        });
    }

    private void joinWithToken(String token) {
        if (released || state != CallState.PREPARING) {
            return;
        }
        updateState(CallState.JOINING, "正在加入频道 " + channelName + "…");
        int result = rtcClient.join(new RtcJoinParams(
                channelName,
                token,
                requestedUid,
                callType
        ));
        if (result != 0) {
            fail(new CallError(
                    CallError.Domain.RTC,
                    result,
                    "加入频道失败：" + RtcEngine.getErrorDescription(Math.abs(result))
            ));
        }
    }

    @Override
    public void leave() {
        runOnMain(() -> {
            if (state == CallState.IDLE || state == CallState.LEAVING) {
                return;
            }
            updateState(CallState.LEAVING, "正在离开频道…");
            rtcClient.leave();
            resetSession();
            updateState(CallState.IDLE, "已离开频道");
        });
    }

    @Override
    public void setMicrophoneEnabled(boolean enabled) {
        runOnMain(() -> {
            if (!isInChannel()) {
                return;
            }
            int result = rtcClient.setMicrophoneEnabled(enabled);
            if (result == 0) {
                microphoneEnabled = enabled;
                notifySnapshot();
            } else {
                reportRtcOperationError(result, "切换麦克风失败");
            }
        });
    }

    @Override
    public void setCameraEnabled(boolean enabled) {
        runOnMain(() -> {
            if (!isInChannel() || callType != CallType.VIDEO) {
                return;
            }
            int result = rtcClient.setCameraEnabled(enabled);
            if (result == 0) {
                cameraEnabled = enabled;
                notifySnapshot();
            } else {
                reportRtcOperationError(result, "切换摄像头失败");
            }
        });
    }

    @Override
    public void switchCamera() {
        runOnMain(() -> {
            if (!isInChannel() || callType != CallType.VIDEO) {
                return;
            }
            int result = rtcClient.switchCamera();
            if (result != 0) {
                reportRtcOperationError(result, "切换前后摄像头失败");
            }
        });
    }

    @Override
    public void setSpeakerEnabled(boolean enabled) {
        runOnMain(() -> {
            if (!isInChannel()) {
                return;
            }
            int result = rtcClient.setSpeakerEnabled(enabled);
            if (result == 0) {
                speakerEnabled = enabled;
                notifySnapshot();
            } else {
                reportRtcOperationError(result, "切换扬声器失败");
            }
        });
    }

    @Override
    public void attachLocalVideo(SurfaceView view) {
        runOnMain(() -> rtcClient.attachLocalVideo(view));
    }

    @Override
    public void attachRemoteVideo(SurfaceView view) {
        runOnMain(() -> rtcClient.attachRemoteVideo(view));
    }

    @Override
    public CallSnapshot getSnapshot() {
        return snapshot();
    }

    @Override
    public void addListener(CallListener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        runOnMain(() -> listener.onCallSnapshotChanged(snapshot()));
    }

    @Override
    public void removeListener(CallListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void release() {
        runOnMain(() -> {
            if (released) {
                return;
            }
            released = true;
            rtcClient.release();
            tokenProvider.release();
            listeners.clear();
            resetSession();
        });
    }

    @Override
    public void onJoinSuccess(int localUid) {
        this.localUid = localUid;
        if (remoteUid == 0) {
            updateState(CallState.WAITING_REMOTE, "已加入频道，等待另一台设备…");
        } else {
            updateState(CallState.CONNECTED, "音视频已连接");
        }
    }

    @Override
    public void onRemoteUserJoined(int remoteUid) {
        this.remoteUid = remoteUid;
        updateState(CallState.CONNECTED, "远端用户 " + unsignedUid(remoteUid) + " 已加入");
    }

    @Override
    public void onRemoteUserLeft(int remoteUid, int reason) {
        if (this.remoteUid == remoteUid) {
            this.remoteUid = 0;
        }
        if (isInChannel()) {
            updateState(CallState.WAITING_REMOTE, "远端已离开，等待重新加入…");
        }
    }

    @Override
    public void onConnectionStateChanged(int connectionState, int reason) {
        if (connectionState == Constants.CONNECTION_STATE_RECONNECTING) {
            updateState(CallState.RECONNECTING, "网络异常，正在重连…");
        } else if (connectionState == Constants.CONNECTION_STATE_CONNECTED
                && state == CallState.RECONNECTING) {
            updateState(
                    remoteUid == 0 ? CallState.WAITING_REMOTE : CallState.CONNECTED,
                    remoteUid == 0 ? "重连成功，等待远端…" : "重连成功"
            );
        } else if (connectionState == Constants.CONNECTION_STATE_FAILED) {
            fail(new CallError(
                    CallError.Domain.RTC,
                    reason,
                    "RTC 连接失败，原因码：" + reason
            ));
        }
    }

    @Override
    public void onRemoteAudioMuted(int remoteUid, boolean muted) {
        if (this.remoteUid == remoteUid && state == CallState.CONNECTED) {
            statusMessage = muted ? "对方已关闭麦克风" : "音视频已连接";
            notifySnapshot();
        }
    }

    @Override
    public void onRemoteVideoMuted(int remoteUid, boolean muted) {
        if (this.remoteUid == remoteUid && state == CallState.CONNECTED) {
            statusMessage = muted ? "对方已关闭摄像头" : "音视频已连接";
            notifySnapshot();
        }
    }

    @Override
    public void onTokenWillExpire() {
        renewToken();
    }

    @Override
    public void onTokenRequired() {
        renewToken();
    }

    private void renewToken() {
        if (!isInChannel() || channelName.isEmpty()) {
            return;
        }
        tokenProvider.requestToken(channelName, localUid, new RtcTokenProvider.Callback() {
            @Override
            public void onSuccess(String token) {
                runOnMain(() -> {
                    int result = rtcClient.renewToken(token);
                    if (result != 0) {
                        reportRtcOperationError(result, "更新 Token 失败");
                    }
                });
            }

            @Override
            public void onError(CallError error) {
                runOnMain(() -> reportError(error));
            }
        });
    }

    @Override
    public void onError(CallError error) {
        if (error.getCode() == Constants.ERR_INVALID_TOKEN
                || error.getCode() == Constants.ERR_TOKEN_EXPIRED) {
            fail(error);
        } else {
            reportError(error);
        }
    }

    private void updateState(CallState newState, String message) {
        state = newState;
        statusMessage = message;
        notifySnapshot();
    }

    private void fail(CallError error) {
        state = CallState.FAILED;
        statusMessage = error.getMessage();
        rtcClient.leave();
        notifySnapshot();
        reportError(error);
    }

    private void reportRtcOperationError(int code, String prefix) {
        reportError(new CallError(
                CallError.Domain.RTC,
                code,
                prefix + "：" + RtcEngine.getErrorDescription(Math.abs(code))
        ));
    }

    private void reportError(CallError error) {
        for (CallListener listener : listeners) {
            listener.onCallError(error);
        }
    }

    private void notifySnapshot() {
        CallSnapshot snapshot = snapshot();
        for (CallListener listener : listeners) {
            listener.onCallSnapshotChanged(snapshot);
        }
    }

    private CallSnapshot snapshot() {
        return new CallSnapshot(
                state,
                callType,
                channelName,
                localUid,
                remoteUid,
                microphoneEnabled,
                cameraEnabled,
                speakerEnabled,
                statusMessage
        );
    }

    private boolean isInChannel() {
        return state == CallState.JOINING
                || state == CallState.WAITING_REMOTE
                || state == CallState.CONNECTED
                || state == CallState.RECONNECTING;
    }

    private void resetSession() {
        channelName = "";
        requestedUid = 0;
        localUid = 0;
        remoteUid = 0;
        microphoneEnabled = true;
        cameraEnabled = true;
        speakerEnabled = true;
    }

    private void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    private static String unsignedUid(int uid) {
        return String.valueOf(Integer.toUnsignedLong(uid));
    }
}
