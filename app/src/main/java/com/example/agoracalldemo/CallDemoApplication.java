package com.example.agoracalldemo;

import android.app.Application;

import com.example.core.call.api.CallSdkInitializer;

public final class CallDemoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CallSdkInitializer.initializeYunxin(this, BuildConfig.YUNXIN_APP_KEY);
    }
}
