# 云信信令 + 声网 RTC 1V1 Demo

# 文档
实现音视频互动
https://doc.shengwang.cn/doc/rtc/android/get-started/quick-start

## 运行测试

1. 在 `local.properties` 中替换 `YUNXIN_APP_KEY` 占位值。
2. 使用两台 Android 真机安装并启动 `app`，分别输入两个不同的云信账号和 Token。
3. 两台设备登录云信后，一端输入另一端账号并发起呼叫。
4. 被叫接听后，core 会自动让双方进入同一个声网频道。
5. 可测试视频、语音、静音、摄像头、镜头切换、扬声器、拒绝、取消和挂断。

> `AGORA_APP_CERT` 仅可用于本地 Demo。正式环境必须把 App Certificate 保存在服务端，
> 由业务服务端签发 RTC Token，不能将证书打进 APK。

详细接入流程与注意事项见 `log/云信信令.txt`。






