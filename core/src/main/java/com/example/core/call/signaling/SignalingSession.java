package com.example.core.call.signaling;

import com.example.core.call.model.CallType;

public final class SignalingSession {
    private final String channelId;
    private final String rtcChannelName;
    private final String requestId;
    private final String callerAccountId;
    private final String calleeAccountId;
    private final CallType callType;
    private final boolean outgoing;

    public SignalingSession(
            String channelId,
            String rtcChannelName,
            String requestId,
            String callerAccountId,
            String calleeAccountId,
            CallType callType,
            boolean outgoing
    ) {
        this.channelId = value(channelId);
        this.rtcChannelName = value(rtcChannelName);
        this.requestId = value(requestId);
        this.callerAccountId = value(callerAccountId);
        this.calleeAccountId = value(calleeAccountId);
        this.callType = callType == null ? CallType.VIDEO : callType;
        this.outgoing = outgoing;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getRtcChannelName() {
        return rtcChannelName;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getCallerAccountId() {
        return callerAccountId;
    }

    public String getCalleeAccountId() {
        return calleeAccountId;
    }

    public CallType getCallType() {
        return callType;
    }

    public boolean isOutgoing() {
        return outgoing;
    }

    public String getPeerAccountId(String localAccountId) {
        return callerAccountId.equals(localAccountId) ? calleeAccountId : callerAccountId;
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
