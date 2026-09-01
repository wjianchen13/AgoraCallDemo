# 声网 1v1 的例子

# 文档
实现音视频互动
https://doc.shengwang.cn/doc/rtc/android/get-started/quick-start

## 运行测试

1. 使用两台 Android 真机安装并启动 `app`。
2. 两台设备填写相同频道名，UID 保持 `0`（由声网自动分配）。
3. Token 留空时，Demo 会使用 `local.properties` 中的 App ID 和 App Certificate，
   通过声网官方 Token Builder 在本地生成测试 Token。
4. 两台设备分别点击“加入频道”，即可测试视频、语音、静音、摄像头、镜头切换、
   扬声器和挂断。

如果手动填写 Token，Demo 会优先使用输入的 Token，不再请求测试 Token。

> `AGORA_APP_CERT` 仅可用于本地 Demo。正式环境必须把 App Certificate 保存在服务端，
> 由业务服务端签发 RTC Token，不能将证书打进 APK。






