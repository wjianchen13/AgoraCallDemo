package com.example.core.call.token;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.core.call.model.CallError;

import io.agora.media.RtcTokenBuilder2;

/**
 * Demo-only token provider mirroring the official APIExample token tool.
 * Never ship an App Certificate in a production APK.
 */
public final class AgoraDemoTokenProvider implements RtcTokenProvider {
    private static final int TOKEN_EXPIRE_SECONDS = 3600;
    private final String appId;
    private final String appCertificate;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AgoraDemoTokenProvider(String appId, String appCertificate) {
        this.appId = appId == null ? "" : appId.trim();
        this.appCertificate = appCertificate == null ? "" : appCertificate.trim();
    }

    @Override
    public void requestToken(String channelName, int uid, Callback callback) {
        if (appCertificate.isEmpty()) {
            callback.onSuccess("");
            return;
        }
        executor.execute(() -> generateToken(channelName, uid, callback));
    }

    private void generateToken(String channelName, int uid, Callback callback) {
        try {
            RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();
            String token = tokenBuilder.buildTokenWithUid(
                    appId,
                    appCertificate,
                    channelName,
                    uid,
                    RtcTokenBuilder2.Role.ROLE_PUBLISHER,
                    TOKEN_EXPIRE_SECONDS,
                    TOKEN_EXPIRE_SECONDS
            );
            if (token.isEmpty()) {
                throw new IllegalStateException("Token Builder returned an empty token");
            }
            callback.onSuccess(token);
        } catch (Exception error) {
            callback.onError(new CallError(
                    CallError.Domain.TOKEN,
                    -1,
                    "生成测试 Token 失败：" + error.getMessage()
            ));
        }
    }

    @Override
    public void release() {
        executor.shutdownNow();
    }
}
