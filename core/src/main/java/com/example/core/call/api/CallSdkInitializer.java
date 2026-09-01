package com.example.core.call.api;

import android.content.Context;

import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.SDKOptions;

public final class CallSdkInitializer {
    private static boolean yunxinInitialized;

    private CallSdkInitializer() {
    }

    public static synchronized void initializeYunxin(Context context, String appKey) {
        if (yunxinInitialized) {
            return;
        }
        String normalizedAppKey = appKey == null ? "" : appKey.trim();
        if (normalizedAppKey.isEmpty() || normalizedAppKey.startsWith("YOUR_")) {
            return;
        }

        SDKOptions options = new SDKOptions();
        options.appKey = normalizedAppKey;
        // This project uses V2NIMLoginService and V2NIMSignallingService, so the
        // SDK must run in V2 mode. NIMClient.init(...) initializes the legacy
        // mode and V2 signalling then fails locally with 191001 (misuse).
        NIMClient.initV2(context.getApplicationContext(), options);
        yunxinInitialized = true;
    }

    public static synchronized boolean isYunxinInitialized() {
        return yunxinInitialized;
    }
}
