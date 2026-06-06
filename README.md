# StreamShop — 直播电商 App

一个完整的直播电商应用，由 Android 客户端、Node.js 后端、React 管理后台三部分组成。

## 项目结构

```
StreamShop/
├── app/                    # Android 客户端 (Java, Gradle)
├── backend/                # 后端 API 服务 (Node.js, Express, TypeScript)
├── admin/                  # 管理后台 (React, Vite, TypeScript)
└── project.txt            # 项目需求文档
```

## 功能完成情况

### Android 客户端

| 模块 | 功能 | 状态 |
|------|------|------|
| 内容流 | 短视频上下滑切换、播放/暂停/静音/进度展示 | 已完成 |
| 商品卡 | 视频浮层商品卡、商品详情半屏弹窗、加入购物车/立即购买 | 已完成 |
| 直播间 | 直播间列表、模拟直播、WebSocket 实时弹幕、在线人数、讲解商品 | 已完成 |
| 互动 | 点赞/取消点赞、评论列表/发送评论、收藏视频/商品 | 已完成 |
| 购物车 | 商品数量修改、删除、勾选结算 | 已完成 |
| 下单 | 订单确认页、模拟支付、支付结果页、订单列表（按状态筛选） | 已完成 |
| 登录注册 | JWT 登录/注册、Token 自动携带 | 已完成 |
| 个人中心 | 头像上传裁剪、我的视频管理（新建/编辑/删除）、我的点赞、我的收藏 | 已完成 |
| 文件上传 | 视频/封面/商品图/头像从手机本地上传 | 已完成 |

### 后端 API

| 模块 | 端点 | 状态 |
|------|------|------|
| 用户 | `POST /api/auth/register`, `POST /api/auth/login`, `PATCH /api/auth/me` | 已完成 |
| 视频 | `GET/POST/PATCH/DELETE /api/videos` | 已完成 |
| 商品 | `GET/POST/PATCH/DELETE /api/products` | 已完成 |
| 订单 | `GET/POST /api/orders`, `GET /api/orders/:id` | 已完成 |
| 购物车 | `GET/POST/PATCH/DELETE /api/cart` | 已完成 |
| 互动 | 点赞/收藏/评论 CRUD | 已完成 |
| 直播 | 直播间 CRUD、WebSocket 实时推送 | 已完成 |
| 营销 | 优惠券、秒杀倒计时 | 已完成 |
| 上传 | `POST /api/upload` (multer 文件上传) | 已完成 |
| 推荐 | `GET /api/recommend` (基于标签/类目) | 已完成 |

### 管理后台

| 页面 | 功能 | 状态 |
|------|------|------|
| 登录 | 管理员登录 | 已完成 |
| 仪表盘 | 数据概览看板 | 已完成 |
| 视频管理 | 列表/新建/编辑/删除/上下架 | 已完成 |
| 商品管理 | 列表/新建/编辑/删除/上下架 | 已完成 |
| 订单管理 | 订单列表/详情 | 已完成 |
| 直播间 | 创建/配置直播间、设置讲解商品 | 已完成 |
| 优惠券 | 优惠券管理 | 已完成 |

## 技术栈

| 层 | 技术 |
|-----|------|
| Android | Java 11, Gradle Kotlin DSL, ExoPlayer (Media3), Glide, OkHttp, Gson, Android-Image-Cropper |
| 后端 | Node.js, Express, TypeScript, JWT (jsonwebtoken), bcryptjs, WebSocket (ws), multer |
| 管理后台 | React 19, TypeScript, Vite, React Router |
| 数据存储 | 本地 JSON 文件持久化 + 可选 MongoDB Atlas 云端同步 |

## 快速开始

### 1. 启动后端

```bash
cd backend
npm install
npm run seed      # 初始化本地种子数据（仅本地首次需要，日常不要反复运行）
npm run dev       # 启动开发服务器，默认端口 3000
```

如果需要和队友同步云端数据，在 `backend/.env` 中配置：

```bash
MONGODB_URI=你的 MongoDB Atlas 连接串
```

配置后启动后端会自动连接 `streamshop` 数据库：云端已有集合时会先拉取到本地 JSON；云端为空但本地 JSON 有数据时会把本地数据推到云端。后续通过接口新增、修改、删除的数据也会同步到 MongoDB。

### 2. 启动管理后台

```bash
cd admin
npm install
npm run dev       # 启动 Vite 开发服务器
```

### 3. 运行 Android App

1. 用 Android Studio 打开项目根目录 `StreamShop/`
2. 修改 `app/.../data/remote/ApiClient.java` 中的 `BASE_URL` 为电脑局域网 IP
3. Sync Gradle → Run

### 注意事项

- Android 模拟器连接电脑后端需要使用局域网 IP（雷电模拟器等不支持 `10.0.2.2`）；当前客户端配置指向 `10.208.69.9:3000`，如果换电脑或网络，需要同步调整 API/WebSocket/直播推流地址
- `npm run seed` 会**重置本地 JSON 数据**，日常重启不需要运行，直接 `npm run dev` 即可
- 后端 JSON 数据存储在 `backend/src/data/`，首次运行 `npm run seed` 会自动生成；配置 MongoDB 后，本地 JSON 仍作为运行时数据文件，并与云端集合双向同步
- MongoDB 只同步 JSON 元数据，不同步 `backend/uploads/` 里的视频、封面、商品图和头像文件；从云端拉到的旧 IP 媒体链接会按当前后端地址重写，缺少本地视频文件的内容会在视频流、搜索、点赞/收藏列表中隐藏

## 已完成迭代

1. 基础架构：Android 项目搭建、后端 API 框架、JSON 文件存储
2. 内容流：短视频上下滑、商品卡片、购物车
3. 直播模块：直播间列表、WebSocket 实时通信、弹幕系统
4. 订单系统：下单流程、模拟支付、订单管理
5. 互动功能：点赞、评论、收藏
6. 个人中心 + 运营后台：视频管理、头像上传裁剪、管理后台全部页面、优惠券/秒杀
