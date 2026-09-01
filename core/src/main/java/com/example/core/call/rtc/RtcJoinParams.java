package com.example.core.call.rtc;

import com.example.core.call.model.CallType;

public final class RtcJoinParams {
    private final String channelName;
    private final String token;
    private final int uid;
    private final CallType callType;

    public RtcJoinParams(String channelName, String token, int uid, CallType callType) {
        this.channelName = channelName;
        this.token = token;
        this.uid = uid;
        this.callType = callType;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getToken() {
        return token;
    }

    public int getUid() {
        return uid;
    }

    public CallType getCallType() {
        return callType;
    }
}
