package com.example.core.call.model;

public final class CallSnapshot {
    private final CallState state;
    private final CallType callType;
    private final String channelName;
    private final int localUid;
    private final int remoteUid;
    private final boolean microphoneEnabled;
    private final boolean cameraEnabled;
    private final boolean speakerEnabled;
    private final String statusMessage;
    private final boolean signalingLoggedIn;
    private final String localAccountId;
    private final String peerAccountId;

    public CallSnapshot(
            CallState state,
            CallType callType,
            String channelName,
            int localUid,
            int remoteUid,
            boolean microphoneEnabled,
            boolean cameraEnabled,
            boolean speakerEnabled,
            String statusMessage,
            boolean signalingLoggedIn,
            String localAccountId,
            String peerAccountId
    ) {
        this.state = state;
        this.callType = callType;
        this.channelName = channelName;
        this.localUid = localUid;
        this.remoteUid = remoteUid;
        this.microphoneEnabled = microphoneEnabled;
        this.cameraEnabled = cameraEnabled;
        this.speakerEnabled = speakerEnabled;
        this.statusMessage = statusMessage;
        this.signalingLoggedIn = signalingLoggedIn;
        this.localAccountId = localAccountId;
        this.peerAccountId = peerAccountId;
    }

    public CallState getState() {
        return state;
    }

    public CallType getCallType() {
        return callType;
    }

    public String getChannelName() {
        return channelName;
    }

    public int getLocalUid() {
        return localUid;
    }

    public int getRemoteUid() {
        return remoteUid;
    }

    public boolean isMicrophoneEnabled() {
        return microphoneEnabled;
    }

    public boolean isCameraEnabled() {
        return cameraEnabled;
    }

    public boolean isSpeakerEnabled() {
        return speakerEnabled;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public boolean isSignalingLoggedIn() {
        return signalingLoggedIn;
    }

    public String getLocalAccountId() {
        return localAccountId;
    }

    public String getPeerAccountId() {
        return peerAccountId;
    }
}
