# 原创声明与第三方依赖

StreamShop 是作者原创开发的项目。以下仅说明所使用的第三方依赖，不影响项目业务实现的原创归属。

本文件根据当前依赖声明整理，不将依赖库、协议和第三方数据宣称为项目原创。具体版本以 `app/build.gradle.kts`、Gradle版本目录及各端的package/lock文件为准；以下是主要来源，不是完整法律许可证清单。

| 用途 | 上游 |
|---|---|
| Android组件与视频播放 | [AndroidX / Media3](https://developer.android.com/jetpack/androidx) |
| HTTP | [OkHttp](https://github.com/square/okhttp) |
| JSON | [Gson](https://github.com/google/gson) |
| 图片加载 | [Glide](https://github.com/bumptech/glide) |
| 图片裁剪 | [Android-Image-Cropper](https://github.com/CanHub/Android-Image-Cropper) |
| RTMP 推流 | [RootEncoder](https://github.com/pedroSG94/RootEncoder)，构建声明使用旧名称 `rtmp-rtsp-stream-client-java:2.2.6` |
| 图表 | [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) |
| 后端Web | [Express](https://github.com/expressjs/express) |
| WebSocket | [ws](https://github.com/websockets/ws) |
| 媒体服务 | [node-media-server](https://github.com/illuspas/Node-Media-Server) |
| MongoDB驱动 | [node-mongodb-native](https://github.com/mongodb/node-mongodb-native) |
| Web原型 | [React](https://github.com/facebook/react) / [Vite](https://github.com/vitejs/vite) |

各依赖适用其上游许可证，本次未替原作者变更授权。演示视频、图片、图标或种子数据如有外部来源，应在发布/分发前逐项补全文件来源与使用许可；目前没有足够记录证明全部媒体素材的授权，因此本文件不声明素材均为原创或已获许可。

项目是学习演示，不是字节跳动官方产品，也不提供真实支付能力。现有代码的许可范围以仓库已有声明及权利人授权为准，本次文档整理不擅自为未授权代码补发许可证。
