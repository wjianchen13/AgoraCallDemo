package com.example.core.call.model;

public final class CallInviteRequest {
    private final String peerAccountId;
    private final CallType callType;

    public CallInviteRequest(String peerAccountId, CallType callType) {
        this.peerAccountId = peerAccountId == null ? "" : peerAccountId.trim();
        this.callType = callType == null ? CallType.VIDEO : callType;
    }

    public String getPeerAccountId() {
        return peerAccountId;
    }

    public CallType getCallType() {
        return callType;
    }
}
