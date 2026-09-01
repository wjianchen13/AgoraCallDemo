package com.example.core.call.token;

import com.example.core.call.model.CallError;

public interface RtcTokenProvider {
    interface Callback {
        void onSuccess(String token);

        void onError(CallError error);
    }

    void requestToken(String channelName, int uid, Callback callback);

    void release();
}
