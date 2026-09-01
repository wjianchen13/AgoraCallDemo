package com.example.core.call.model;

public enum CallState {
    IDLE,
    PREPARING,
    JOINING,
    WAITING_REMOTE,
    CONNECTED,
    RECONNECTING,
    LEAVING,
    FAILED
}
