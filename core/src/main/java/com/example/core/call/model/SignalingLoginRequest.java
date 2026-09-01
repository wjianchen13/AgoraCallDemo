package com.example.core.call.model;

public final class SignalingLoginRequest {
    private final String accountId;
    private final String token;
    private final int rtcUid;

    public SignalingLoginRequest(String accountId, String token, int rtcUid) {
        this.accountId = normalize(accountId);
        this.token = normalize(token);
        this.rtcUid = rtcUid;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getToken() {
        return token;
    }

    public int getRtcUid() {
        return rtcUid;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
