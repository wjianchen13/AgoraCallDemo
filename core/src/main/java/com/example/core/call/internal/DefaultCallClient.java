package com.example.core.call.internal;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

import com.example.core.call.api.CallClient;
import com.example.core.call.api.CallListener;
import com.example.core.call.model.CallError;
import com.example.core.call.model.CallInviteRequest;
import com.example.core.call.model.CallJoinRequest;
import com.example.core.call.model.CallSnapshot;
import com.example.core.call.model.CallState;
import com.example.core.call.model.CallType;
import com.example.core.call.model.SignalingLoginRequest;
import com.example.core.call.rtc.RtcClient;
import com.example.core.call.rtc.RtcClientListener;
import com.example.core.call.rtc.RtcJoinParams;
import com.example.core.call.rtc.agora.AgoraRtcClient;
import com.example.core.call.signaling.SignalingCallback;
import com.example.core.call.signaling.SignalingClient;
import com.example.core.call.signaling.SignalingClientListener;
import com.example.core.call.signaling.SignalingSession;
import com.example.core.call.token.RtcTokenProvider;

import io.agora.rtc2.Constants;
import io.agora.rtc2.RtcEngine;

public final class DefaultCallClient implements
        CallClient,
        RtcClientListener,
        SignalingClientListener {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<CallListener> listeners = new CopyOnWriteArraySet<>();
    private final String appId;
    private final RtcClient rtcClient;
    private final RtcTokenProvider tokenProvider;
    private final SignalingClient signalingClient;

    private CallState state = CallState.IDLE;
    private CallType callType = CallType.VIDEO;
    private String channelName = "";
    private int requestedUid;
    private int localUid;
    private int remoteUid;
    private boolean microphoneEnabled = true;
    private boolean cameraEnabled = true;
    private boolean speakerEnabled = true;
    private String statusMessage = "请先登录云信账号";
    private boolean signalingLoggedIn;
    private String localAccountId = "";
    private String peerAccountId = "";
    private int signalingRtcUid;
    private SignalingSession signalingSession;
    private boolean released;

    public DefaultCallClient(
            Context context,
            String appId,
            RtcTokenProvider tokenProvider,
            SignalingClient signalingClient
    ) {
        this.appId = appId == null ? "" : appId.trim();
        this.tokenProvider = tokenProvider;
        this.rtcClient = new AgoraRtcClient(context);
        this.signalingClient = signalingClient;
        if (signalingClient != null) {
            signalingClient.setListener(this);
        }
    }

    @Override
    public void loginSignaling(SignalingLoginRequest request) {
        runOnMain(() -> loginSignalingOnMain(request));
    }

    private void loginSignalingOnMain(SignalingLoginRequest request) {
        if (released) {
            reportError(new CallError(CallError.Domain.INTERNAL, -1, "CallClient 已释放"));
            return;
        }
        if (signalingClient == null) {
            fail(new CallError(CallError.Domain.VALIDATION, -1,
                    "YUNXIN_APP_KEY 未配置，请替换占位变量"));
            return;
        }
        if (state != CallState.IDLE && state != CallState.FAILED) {
            reportValidationError("当前状态不能登录云信");
            return;
        }
        if (request == null || request.getAccountId().isEmpty() || request.getToken().isEmpty()) {
            reportValidationError("云信账号和 Token 不能为空");
            return;
        }
        if (request.getAccountId().startsWith("YOUR_")
                || request.getToken().startsWith("YOUR_")) {
            reportValidationError("请先替换云信账号和 Token 占位值");
            return;
        }
        localAccountId = request.getAccountId();
        signalingRtcUid = request.getRtcUid();
        updateState(CallState.LOGGING_IN, "正在登录云信账号 " + localAccountId + "…");
        signalingClient.login(localAccountId, request.getToken(), new SignalingCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnMain(() -> {
                    signalingLoggedIn = true;
                    updateState(CallState.READY, "云信已登录，可以发起呼叫");
                });
            }

            @Override
            public void onFailure(int code, String message) {
                runOnMain(() -> {
                    signalingLoggedIn = false;
                    fail(signalingError(code, "云信登录失败", message));
                });
            }
        });
    }

    @Override
    public void logoutSignaling() {
        runOnMain(() -> {
            if (!signalingLoggedIn || signalingClient == null || isCallInProgress()) {
                return;
            }
            signalingClient.logout(new SignalingCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    runOnMain(() -> {
                        signalingLoggedIn = false;
                        localAccountId = "";
                        updateState(CallState.IDLE, "云信已退出登录");
                    });
                }

                @Override
                public void onFailure(int code, String message) {
                    runOnMain(() -> reportError(signalingError(code, "云信退出失败", message)));
                }
            });
        });
    }

    @Override
    public void startCall(CallInviteRequest request) {
        runOnMain(() -> startCallOnMain(request));
    }

    private void startCallOnMain(CallInviteRequest request) {
        if (!signalingLoggedIn || signalingClient == null || state != CallState.READY) {
            reportValidationError("请先登录云信账号");
            return;
        }
        if (request == null || request.getPeerAccountId().isEmpty()) {
            reportValidationError("对方云信账号不能为空");
            return;
        }
        if (localAccountId.equals(request.getPeerAccountId())) {
            reportValidationError("不能呼叫当前登录账号");
            return;
        }

        callType = request.getCallType();
        peerAccountId = request.getPeerAccountId();
        String rtcChannel = "call_" + UUID.randomUUID().toString().replace("-", "");
        updateState(CallState.CALLING, "正在呼叫 " + peerAccountId + "…");
        signalingClient.startCall(peerAccountId, rtcChannel, callType,
                new SignalingCallback<SignalingSession>() {
                    @Override
                    public void onSuccess(SignalingSession result) {
                        runOnMain(() -> {
                            if (state == CallState.CALLING) {
                                signalingSession = result;
                                updateState(CallState.CALLING,
                                        "等待 " + peerAccountId + " 接听…");
                            }
                        });
                    }

                    @Override
                    public void onFailure(int code, String message) {
                        runOnMain(() -> {
                            signalingSession = null;
                            peerAccountId = "";
                            state = CallState.READY;
                            statusMessage = "呼叫失败";
                            notifySnapshot();
                            reportError(signalingError(code, "发起呼叫失败", message));
                        });
                    }
                });
    }

    @Override
    public void acceptCall() {
        runOnMain(() -> {
            if (state != CallState.RINGING || signalingSession == null) {
                return;
            }
            updateState(CallState.ACCEPTING, "正在接听 " + peerAccountId + "…");
            signalingClient.accept(signalingSession,
                    new SignalingCallback<SignalingSession>() {
                        @Override
                        public void onSuccess(SignalingSession result) {
                            runOnMain(() -> {
                                signalingSession = result;
                                startRtcForSignalingSession();
                            });
                        }

                        @Override
                        public void onFailure(int code, String message) {
                            runOnMain(() -> {
                                finishSignaledCall("接听失败，可以重新呼叫");
                                reportError(signalingError(code, "接听失败", message));
                            });
                        }
                    });
        });
    }

    @Override
    public void rejectCall() {
        runOnMain(() -> {
            if (state != CallState.RINGING || signalingSession == null) {
                return;
            }
            signalingClient.reject(signalingSession, new SignalingCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    runOnMain(() -> finishSignaledCall("已拒绝来电"));
                }

                @Override
                public void onFailure(int code, String message) {
                    runOnMain(() -> reportError(signalingError(code, "拒绝来电失败", message)));
                }
            });
        });
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
        if (state != CallState.IDLE && state != CallState.READY && state != CallState.FAILED) {
            reportValidationError("当前正在通话中");
            return;
        }
        if (request == null || request.getChannelName().isEmpty()) {
            fail(new CallError(CallError.Domain.VALIDATION, -1, "频道名不能为空"));
            return;
        }
        beginRtcJoin(request);
    }

    private void startRtcForSignalingSession() {
        if (signalingSession == null || signalingSession.getRtcChannelName().isEmpty()) {
            finishSignaledCall("信令频道信息无效");
            reportValidationError("云信未返回有效频道名");
            return;
        }
        beginRtcJoin(new CallJoinRequest(
                signalingSession.getRtcChannelName(),
                signalingRtcUid,
                signalingSession.getCallType(),
                ""
        ));
    }

    private void beginRtcJoin(CallJoinRequest request) {
        if (appId.isEmpty()) {
            fail(new CallError(CallError.Domain.VALIDATION, -1, "AGORA_APP_ID 未配置"));
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
            fail(new CallError(CallError.Domain.RTC, -1,
                    "初始化 RTC 失败：" + error.getMessage()));
            return;
        }
        if (!request.getToken().isEmpty()) {
            joinWithToken(request.getToken());
            return;
        }
        updateState(CallState.PREPARING, "正在生成声网测试 Token…");
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
        updateState(CallState.JOINING, "正在加入声网频道 " + channelName + "…");
        int result = rtcClient.join(new RtcJoinParams(channelName, token, requestedUid, callType));
        if (result != 0) {
            fail(new CallError(CallError.Domain.RTC, result,
                    "加入频道失败：" + RtcEngine.getErrorDescription(Math.abs(result))));
        }
    }

    @Override
    public void leave() {
        runOnMain(this::leaveOnMain);
    }

    private void leaveOnMain() {
        if (state == CallState.IDLE || state == CallState.READY
                || state == CallState.LOGGING_IN || state == CallState.LEAVING) {
            return;
        }
        if (state == CallState.CALLING && signalingClient != null) {
            updateState(CallState.LEAVING, "正在取消呼叫…");
            signalingClient.cancel(signalingSession, noOpSignalingCallback());
            finishSignaledCall("已取消呼叫");
            return;
        }
        if (signalingSession == null || signalingClient == null) {
            updateState(CallState.LEAVING, "正在离开频道…");
            rtcClient.leave();
            resetRtcSession();
            updateState(signalingLoggedIn ? CallState.READY : CallState.IDLE, "已离开频道");
            return;
        }

        SignalingSession session = signalingSession;
        CallState previousState = state;
        updateState(CallState.LEAVING, "正在结束通话…");
        SignalingCallback<Void> callback = new SignalingCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
            }

            @Override
            public void onFailure(int code, String message) {
                runOnMain(() -> reportError(signalingError(code,
                        "结束信令通话失败", message)));
            }
        };
        if (previousState == CallState.RINGING) {
            signalingClient.reject(session, callback);
        } else {
            signalingClient.leave(session, callback);
        }
        finishSignaledCall("通话已结束");
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
            if (signalingClient != null && state == CallState.CALLING) {
                signalingClient.cancel(signalingSession, noOpSignalingCallback());
            } else if (signalingClient != null && signalingSession != null) {
                if (state == CallState.RINGING) {
                    signalingClient.reject(signalingSession, noOpSignalingCallback());
                } else if (isCallInProgress()) {
                    signalingClient.leave(signalingSession, noOpSignalingCallback());
                }
            }
            released = true;
            rtcClient.release();
            tokenProvider.release();
            if (signalingClient != null) {
                signalingClient.release();
            }
            listeners.clear();
            resetRtcSession();
            signalingSession = null;
        });
    }

    @Override
    public void onIncomingCall(SignalingSession session) {
        runOnMain(() -> {
            if (!signalingLoggedIn || state != CallState.READY) {
                if (signalingClient != null) {
                    signalingClient.reject(session, noOpSignalingCallback());
                }
                return;
            }
            signalingSession = session;
            peerAccountId = session.getCallerAccountId();
            callType = session.getCallType();
            updateState(CallState.RINGING,
                    peerAccountId + (callType == CallType.VIDEO
                            ? " 邀请你视频通话"
                            : " 邀请你语音通话"));
        });
    }

    @Override
    public void onCallAccepted(SignalingSession session) {
        runOnMain(() -> {
            if (state != CallState.CALLING) {
                return;
            }
            signalingSession = session;
            startRtcForSignalingSession();
        });
    }

    @Override
    public void onCallRejected(SignalingSession session) {
        runOnMain(() -> {
            if (isCallInProgress()) {
                finishSignaledCall("对方已拒绝通话");
            }
        });
    }

    @Override
    public void onCallCanceled(SignalingSession session) {
        runOnMain(() -> {
            if (isCallInProgress()) {
                finishSignaledCall("对方已取消呼叫");
            }
        });
    }

    @Override
    public void onRemoteHangup(SignalingSession session) {
        runOnMain(() -> {
            if (isCallInProgress()) {
                finishSignaledCall("对方已挂断");
            }
        });
    }

    @Override
    public void onSignalingError(int code, String message) {
        runOnMain(() -> reportError(signalingError(code, "云信信令错误", message)));
    }

    @Override
    public void onJoinSuccess(int localUid) {
        this.localUid = localUid;
        if (remoteUid == 0) {
            updateState(CallState.WAITING_REMOTE, "已接通，等待对方进入声网频道…");
        } else {
            updateState(CallState.CONNECTED, "音视频已连接");
        }
    }

    @Override
    public void onRemoteUserJoined(int remoteUid) {
        this.remoteUid = remoteUid;
        updateState(CallState.CONNECTED, "已与 "
                + (peerAccountId.isEmpty() ? unsignedUid(remoteUid) : peerAccountId)
                + " 建立音视频连接");
    }

    @Override
    public void onRemoteUserLeft(int remoteUid, int reason) {
        if (this.remoteUid == remoteUid) {
            this.remoteUid = 0;
        }
        if (signalingSession != null) {
            finishSignaledCall("对方已离开声网频道");
        } else if (isInChannel()) {
            updateState(CallState.WAITING_REMOTE, "远端已离开，等待重新加入…");
        }
    }

    @Override
    public void onConnectionStateChanged(int connectionState, int reason) {
        if (connectionState == Constants.CONNECTION_STATE_RECONNECTING) {
            updateState(CallState.RECONNECTING, "网络异常，正在重连…");
        } else if (connectionState == Constants.CONNECTION_STATE_CONNECTED
                && state == CallState.RECONNECTING) {
            updateState(remoteUid == 0 ? CallState.WAITING_REMOTE : CallState.CONNECTED,
                    remoteUid == 0 ? "重连成功，等待远端…" : "重连成功");
        } else if (connectionState == Constants.CONNECTION_STATE_FAILED) {
            fail(new CallError(CallError.Domain.RTC, reason,
                    "RTC 连接失败，原因码：" + reason));
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
        state = signalingLoggedIn ? CallState.READY : CallState.FAILED;
        statusMessage = error.getMessage();
        rtcClient.leave();
        resetRtcSession();
        signalingSession = null;
        peerAccountId = "";
        notifySnapshot();
        reportError(error);
    }

    private void finishSignaledCall(String message) {
        rtcClient.leave();
        resetRtcSession();
        signalingSession = null;
        peerAccountId = "";
        updateState(signalingLoggedIn ? CallState.READY : CallState.IDLE, message);
    }

    private void reportValidationError(String message) {
        reportError(new CallError(CallError.Domain.VALIDATION, -1, message));
    }

    private static CallError signalingError(int code, String prefix, String detail) {
        String suffix = detail == null || detail.isEmpty() ? "" : "：" + detail;
        return new CallError(CallError.Domain.SIGNALING, code,
                prefix + suffix + "（" + code + "）");
    }

    private void reportRtcOperationError(int code, String prefix) {
        reportError(new CallError(CallError.Domain.RTC, code,
                prefix + "：" + RtcEngine.getErrorDescription(Math.abs(code))));
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
                statusMessage,
                signalingLoggedIn,
                localAccountId,
                peerAccountId
        );
    }

    private boolean isInChannel() {
        return state == CallState.JOINING
                || state == CallState.WAITING_REMOTE
                || state == CallState.CONNECTED
                || state == CallState.RECONNECTING;
    }

    private boolean isCallInProgress() {
        return state == CallState.CALLING
                || state == CallState.RINGING
                || state == CallState.ACCEPTING
                || state == CallState.PREPARING
                || isInChannel()
                || state == CallState.LEAVING;
    }

    private void resetRtcSession() {
        channelName = "";
        requestedUid = 0;
        localUid = 0;
        remoteUid = 0;
        microphoneEnabled = true;
        cameraEnabled = true;
        speakerEnabled = true;
    }

    private static SignalingCallback<Void> noOpSignalingCallback() {
        return new SignalingCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
            }

            @Override
            public void onFailure(int code, String message) {
            }
        };
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
