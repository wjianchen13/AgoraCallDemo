package com.example.core.call.signaling.yunxin;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.core.call.model.CallType;
import com.example.core.call.signaling.SignalingCallback;
import com.example.core.call.signaling.SignalingClient;
import com.example.core.call.signaling.SignalingClientListener;
import com.example.core.call.signaling.SignalingSession;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.v2.V2NIMFailureCallback;
import com.netease.nimlib.sdk.v2.V2NIMSuccessCallback;
import com.netease.nimlib.sdk.v2.auth.V2NIMLoginService;
import com.netease.nimlib.sdk.v2.avsignalling.V2NIMSignallingListener;
import com.netease.nimlib.sdk.v2.avsignalling.V2NIMSignallingService;
import com.netease.nimlib.sdk.v2.avsignalling.config.V2NIMSignallingConfig;
import com.netease.nimlib.sdk.v2.avsignalling.config.V2NIMSignallingPushConfig;
import com.netease.nimlib.sdk.v2.avsignalling.enums.V2NIMSignallingChannelType;
import com.netease.nimlib.sdk.v2.avsignalling.enums.V2NIMSignallingEventType;
import com.netease.nimlib.sdk.v2.avsignalling.model.V2NIMSignallingChannelInfo;
import com.netease.nimlib.sdk.v2.avsignalling.model.V2NIMSignallingEvent;
import com.netease.nimlib.sdk.v2.avsignalling.model.V2NIMSignallingRoomInfo;
import com.netease.nimlib.sdk.v2.avsignalling.params.V2NIMSignallingCallParams;
import com.netease.nimlib.sdk.v2.avsignalling.params.V2NIMSignallingCallSetupParams;
import com.netease.nimlib.sdk.v2.avsignalling.params.V2NIMSignallingCancelInviteParams;
import com.netease.nimlib.sdk.v2.avsignalling.params.V2NIMSignallingRejectInviteParams;

public final class YunxinSignalingClient implements SignalingClient, V2NIMSignallingListener {
    private static final int LOCAL_ERROR = -1;

    private final V2NIMLoginService loginService;
    private final V2NIMSignallingService signalingService;
    private SignalingClientListener listener;
    private SignalingSession activeSession;
    private SignalingCallback<Void> pendingCancelCallback;
    private final List<SignalingSession> pendingIncomingSessions = new ArrayList<>();
    private String localAccountId = "";
    private boolean loggedIn;
    private boolean released;

    public YunxinSignalingClient() {
        loginService = NIMClient.getService(V2NIMLoginService.class);
        signalingService = NIMClient.getService(V2NIMSignallingService.class);
        if (signalingService != null) {
            signalingService.addSignallingListener(this);
        }
    }

    @Override
    public void setListener(SignalingClientListener listener) {
        this.listener = listener;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void login(String accountId, String token, SignalingCallback<Void> callback) {
        if (!ensureAvailable(callback)) {
            return;
        }
        // Set before login so offline INVITE events delivered during data sync can be identified.
        localAccountId = accountId;
        // Yunxin 10.10.1 declares V2NIMSuccessCallback<Void>, while its runtime may
        // deliver LoginInfo. A raw callback avoids the synthetic Void cast generated
        // for a typed lambda/anonymous callback.
        V2NIMSuccessCallback loginSuccessCallback = new V2NIMSuccessCallback() {
            @Override
            public void onSuccess(Object ignored) {
                loggedIn = true;
                callback.onSuccess(null);
                if (listener != null && !pendingIncomingSessions.isEmpty()) {
                    List<SignalingSession> sessions =
                            new ArrayList<>(pendingIncomingSessions);
                    pendingIncomingSessions.clear();
                    for (SignalingSession session : sessions) {
                        listener.onIncomingCall(session);
                    }
                }
            }
        };
        loginService.login(accountId, token, null, loginSuccessCallback, error -> {
            localAccountId = "";
            loggedIn = false;
            pendingIncomingSessions.clear();
            callback.onFailure(error.getCode(), error.getDesc());
        });
    }

    @Override
    public void logout(SignalingCallback<Void> callback) {
        if (!ensureAvailable(callback)) {
            return;
        }
        loginService.logout(result -> {
            localAccountId = "";
            loggedIn = false;
            activeSession = null;
            pendingIncomingSessions.clear();
            callback.onSuccess(null);
        }, failure(callback));
    }

    @Override
    public void startCall(
            String calleeAccountId,
            String rtcChannelName,
            CallType callType,
            SignalingCallback<SignalingSession> callback
    ) {
        if (!ensureAvailable(callback)) {
            return;
        }
        String requestId = UUID.randomUUID().toString();
        SignalingSession pending = new SignalingSession(
                "",
                rtcChannelName,
                requestId,
                localAccountId,
                calleeAccountId,
                callType,
                true
        );
        activeSession = pending;

        V2NIMSignallingCallParams params = new V2NIMSignallingCallParams.Builder(
                calleeAccountId,
                requestId,
                toChannelType(callType)
        )
                .channelName(rtcChannelName)
                .channelExtension(buildChannelExtension(callType))
                .serverExtension(buildOperationExtension("call"))
                .signallingConfig(defaultConfig())
                .pushConfig(disabledPushConfig())
                .build();

        signalingService.call(params, result -> {
            V2NIMSignallingChannelInfo info = result == null || result.getRoomInfo() == null
                    ? null
                    : result.getRoomInfo().getChannelInfo();
            activeSession = withChannelInfo(pending, info);
            callback.onSuccess(activeSession);
            if (pendingCancelCallback != null) {
                SignalingCallback<Void> cancelCallback = pendingCancelCallback;
                pendingCancelCallback = null;
                cancel(activeSession, cancelCallback);
            }
        }, error -> {
            activeSession = null;
            if (pendingCancelCallback != null) {
                SignalingCallback<Void> cancelCallback = pendingCancelCallback;
                pendingCancelCallback = null;
                cancelCallback.onSuccess(null);
            }
            callback.onFailure(error.getCode(), error.getDesc());
        });
    }

    @Override
    public void accept(SignalingSession session, SignalingCallback<SignalingSession> callback) {
        if (!ensureAvailable(callback)) {
            return;
        }
        V2NIMSignallingCallSetupParams params = new V2NIMSignallingCallSetupParams.Builder(
                session.getChannelId(),
                session.getCallerAccountId(),
                session.getRequestId()
        )
                .serverExtension(buildOperationExtension("accept"))
                .signallingConfig(defaultConfig())
                .build();
        signalingService.callSetup(params, result -> {
            V2NIMSignallingChannelInfo info = result == null || result.getRoomInfo() == null
                    ? null
                    : result.getRoomInfo().getChannelInfo();
            activeSession = withChannelInfo(session, info);
            callback.onSuccess(activeSession);
        }, failure(callback));
    }

    @Override
    public void reject(SignalingSession session, SignalingCallback<Void> callback) {
        if (!ensureAvailable(callback)) {
            return;
        }
        V2NIMSignallingRejectInviteParams params =
                new V2NIMSignallingRejectInviteParams.Builder(
                        session.getChannelId(),
                        session.getCallerAccountId(),
                        session.getRequestId()
                )
                        .serverExtension(buildOperationExtension("reject"))
                        .offlineEnabled(true)
                        .build();
        signalingService.rejectInvite(params, result -> {
            if (sameSession(activeSession, session)) {
                activeSession = null;
            }
            callback.onSuccess(null);
        }, failure(callback));
    }

    @Override
    public void cancel(SignalingSession session, SignalingCallback<Void> callback) {
        if (!ensureAvailable(callback)) {
            return;
        }
        SignalingSession target = session == null ? activeSession : session;
        if (target == null) {
            callback.onFailure(LOCAL_ERROR, "没有可取消的云信呼叫");
            return;
        }
        if (target.getChannelId().isEmpty()) {
            pendingCancelCallback = callback;
            return;
        }
        V2NIMSignallingCancelInviteParams params =
                new V2NIMSignallingCancelInviteParams.Builder(
                        target.getChannelId(),
                        target.getCalleeAccountId(),
                        target.getRequestId()
                )
                        .serverExtension(buildOperationExtension("cancel"))
                        .offlineEnabled(true)
                        .pushConfig(disabledPushConfig())
                        .build();
        signalingService.cancelInvite(params, result -> {
            if (sameSession(activeSession, target)) {
                activeSession = null;
            }
            callback.onSuccess(null);
        }, failure(callback));
    }

    @Override
    public void leave(SignalingSession session, SignalingCallback<Void> callback) {
        if (!ensureAvailable(callback)) {
            return;
        }
        signalingService.leaveRoom(
                session.getChannelId(),
                true,
                buildOperationExtension("hangup"),
                result -> {
                    if (sameSession(activeSession, session)) {
                        activeSession = null;
                    }
                    callback.onSuccess(null);
                },
                failure(callback)
        );
    }

    @Override
    public void onOnlineEvent(V2NIMSignallingEvent event) {
        handleEvent(event);
    }

    @Override
    public void onOfflineEvent(List<V2NIMSignallingEvent> events) {
        if (events == null) {
            return;
        }
        for (V2NIMSignallingEvent event : events) {
            handleEvent(event);
        }
    }

    @Override
    public void onMultiClientEvent(V2NIMSignallingEvent event) {
        handleEvent(event);
    }

    @Override
    public void onSyncRoomInfoList(List<V2NIMSignallingRoomInfo> roomInfoList) {
        // The demo does not restore an ongoing RTC session after process death.
    }

    private void handleEvent(V2NIMSignallingEvent event) {
        if (released || event == null || listener == null) {
            return;
        }
        V2NIMSignallingEventType type = event.getEventType();
        if (type == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_INVITE) {
            if (!localAccountId.equals(event.getInviteeAccountId())) {
                return;
            }
            SignalingSession incomingSession = fromInviteEvent(event);
            if (activeSession == null) {
                activeSession = incomingSession;
            }
            if (!loggedIn) {
                pendingIncomingSessions.add(incomingSession);
                return;
            }
            listener.onIncomingCall(incomingSession);
            return;
        }
        if (!matchesActiveSession(event)) {
            return;
        }

        SignalingSession session = withChannelInfo(activeSession, event.getChannelInfo());
        activeSession = session;
        if (type == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_ACCEPT) {
            listener.onCallAccepted(session);
        } else if (type == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_REJECT) {
            removePending(session);
            activeSession = null;
            listener.onCallRejected(session);
        } else if (type == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_CANCEL_INVITE) {
            removePending(session);
            activeSession = null;
            listener.onCallCanceled(session);
        } else if (type == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_LEAVE
                || type == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_CLOSE
                || type == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_KICK) {
            removePending(session);
            activeSession = null;
            listener.onRemoteHangup(session);
        }
    }

    private boolean matchesActiveSession(V2NIMSignallingEvent event) {
        if (activeSession == null) {
            return false;
        }
        if (!activeSession.getRequestId().isEmpty()
                && activeSession.getRequestId().equals(event.getRequestId())) {
            return true;
        }
        V2NIMSignallingChannelInfo info = event.getChannelInfo();
        return info != null
                && !activeSession.getChannelId().isEmpty()
                && activeSession.getChannelId().equals(info.getChannelId());
    }

    private static boolean sameSession(SignalingSession first, SignalingSession second) {
        if (first == null || second == null) {
            return false;
        }
        if (!first.getRequestId().isEmpty() && first.getRequestId().equals(second.getRequestId())) {
            return true;
        }
        return !first.getChannelId().isEmpty()
                && first.getChannelId().equals(second.getChannelId());
    }

    private void removePending(SignalingSession session) {
        pendingIncomingSessions.removeIf(pending -> sameSession(pending, session));
    }

    private SignalingSession fromInviteEvent(V2NIMSignallingEvent event) {
        V2NIMSignallingChannelInfo info = event.getChannelInfo();
        String channelId = info == null ? "" : info.getChannelId();
        String channelName = info == null ? "" : info.getChannelName();
        CallType callType = info == null
                ? CallType.VIDEO
                : fromChannelType(info.getChannelType());
        return new SignalingSession(
                channelId,
                channelName,
                event.getRequestId(),
                event.getInviterAccountId(),
                event.getInviteeAccountId(),
                callType,
                false
        );
    }

    private static SignalingSession withChannelInfo(
            SignalingSession session,
            V2NIMSignallingChannelInfo info
    ) {
        if (session == null || info == null) {
            return session;
        }
        return new SignalingSession(
                info.getChannelId(),
                info.getChannelName(),
                session.getRequestId(),
                session.getCallerAccountId(),
                session.getCalleeAccountId(),
                session.getCallType(),
                session.isOutgoing()
        );
    }

    private <T> boolean ensureAvailable(SignalingCallback<T> callback) {
        if (released) {
            callback.onFailure(LOCAL_ERROR, "云信信令客户端已释放");
            return false;
        }
        if (loginService == null || signalingService == null) {
            callback.onFailure(LOCAL_ERROR, "云信 SDK 未初始化或信令模块不可用");
            return false;
        }
        return true;
    }

    private static <T> V2NIMFailureCallback failure(SignalingCallback<T> callback) {
        return error -> callback.onFailure(error.getCode(), error.getDesc());
    }

    private static V2NIMSignallingConfig defaultConfig() {
        V2NIMSignallingConfig config = new V2NIMSignallingConfig();
        config.setOfflineEnabled(true);
        config.setUnreadEnabled(true);
        return config;
    }

    private static V2NIMSignallingPushConfig disabledPushConfig() {
        V2NIMSignallingPushConfig config = new V2NIMSignallingPushConfig();
        config.setPushEnabled(false);
        return config;
    }

    private static String buildChannelExtension(CallType callType) {
        try {
            return new JSONObject()
                    .put("version", 1)
                    .put("rtcVendor", "agora")
                    .put("callType", callType.name())
                    .toString();
        } catch (JSONException ignored) {
            return "{}";
        }
    }

    private static String buildOperationExtension(String operation) {
        try {
            return new JSONObject()
                    .put("version", 1)
                    .put("operation", operation)
                    .toString();
        } catch (JSONException ignored) {
            return "{}";
        }
    }

    private static V2NIMSignallingChannelType toChannelType(CallType type) {
        return type == CallType.AUDIO
                ? V2NIMSignallingChannelType.V2NIM_SIGNALLING_CHANNEL_TYPE_AUDIO
                : V2NIMSignallingChannelType.V2NIM_SIGNALLING_CHANNEL_TYPE_VIDEO;
    }

    private static CallType fromChannelType(V2NIMSignallingChannelType type) {
        return type == V2NIMSignallingChannelType.V2NIM_SIGNALLING_CHANNEL_TYPE_AUDIO
                ? CallType.AUDIO
                : CallType.VIDEO;
    }

    @Override
    public void release() {
        if (released) {
            return;
        }
        released = true;
        if (signalingService != null) {
            signalingService.removeSignallingListener(this);
        }
        listener = null;
        activeSession = null;
        pendingCancelCallback = null;
        pendingIncomingSessions.clear();
    }
}
