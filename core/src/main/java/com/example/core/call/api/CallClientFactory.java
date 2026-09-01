package com.example.core.call.api;

import android.content.Context;

import com.example.core.call.internal.DefaultCallClient;
import com.example.core.call.signaling.SignalingClient;
import com.example.core.call.signaling.yunxin.YunxinSignalingClient;
import com.example.core.call.token.AgoraDemoTokenProvider;

public final class CallClientFactory {
    private CallClientFactory() {
    }

    public static CallClient createDemoClient(
            Context context,
            String appId,
            String appCertificate
    ) {
        return createDemoClient(context, appId, appCertificate, "");
    }

    public static CallClient createDemoClient(
            Context context,
            String appId,
            String appCertificate,
            String yunxinAppKey
    ) {
        CallSdkInitializer.initializeYunxin(context, yunxinAppKey);
        SignalingClient signalingClient = CallSdkInitializer.isYunxinInitialized()
                ? new YunxinSignalingClient()
                : null;
        return new DefaultCallClient(
                context.getApplicationContext(),
                appId,
                new AgoraDemoTokenProvider(appId, appCertificate),
                signalingClient
        );
    }
}
