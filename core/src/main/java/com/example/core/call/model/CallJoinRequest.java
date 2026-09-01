package com.example.core.call.model;

public final class CallJoinRequest {
    private final String channelName;
    private final int uid;
    private final CallType callType;
    private final String token;

    public CallJoinRequest(String channelName, int uid, CallType callType, String token) {
        this.channelName = channelName == null ? "" : channelName.trim();
        this.uid = uid;
        this.callType = callType == null ? CallType.VIDEO : callType;
        this.token = token == null ? "" : token.trim();
    }

    public String getChannelName() {
        return channelName;
    }

    public int getUid() {
        return uid;
    }

    public CallType getCallType() {
        return callType;
    }

    public String getToken() {
        return token;
    }
}
