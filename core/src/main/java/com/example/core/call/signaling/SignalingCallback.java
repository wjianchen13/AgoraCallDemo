package com.example.core.call.signaling;

public interface SignalingCallback<T> {
    void onSuccess(T result);

    void onFailure(int code, String message);
}
