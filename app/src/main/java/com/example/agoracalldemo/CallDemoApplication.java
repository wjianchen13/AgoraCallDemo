package com.example.agoracalldemo;

import android.app.Application;

import com.example.core.call.api.CallClient;
import com.example.core.call.api.CallClientFactory;
import com.example.core.call.api.CallSdkInitializer;

public final class CallDemoApplication extends Application {
    private CallClient callClient;

    @Override
    public void onCreate() {
        super.onCreate();
        CallSdkInitializer.initializeYunxin(this, BuildConfig.YUNXIN_APP_KEY);
        callClient = CallClientFactory.createDemoClient(
                this,
                BuildConfig.AGORA_APP_ID,
                BuildConfig.AGORA_APP_CERT,
                BuildConfig.YUNXIN_APP_KEY
        );
    }

    public CallClient getCallClient() {
        return callClient;
    }

    @Override
    public void onTerminate() {
        if (callClient != null) {
            callClient.release();
            callClient = null;
        }
        super.onTerminate();
    }
}
