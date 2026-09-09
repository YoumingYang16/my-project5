# 安装与使用说明

## 需要安装

- Windows 10/11。
- Docker Desktop，并启用 Docker Compose。
- 仅运行网站不需要在宿主机安装 JDK 或 Maven，Docker 会完成构建。继续开发 Java 源码时建议安装 JDK 21。
- Codex Desktop 可选，用于阅读源码、运行命令和继续修改。
- DeepSeek API Key：用于论文理解和文案生成。
- 阿里云百炼 DashScope/Qwen Image API Key：用于 Qwen 图片生成，需要账号具备图片模型调用权限。
- NVIDIA GPU、ComfyUI、RealVisXL 和 IP-Adapter：可选；没有 GPU 时仍可使用 Qwen + 原图候选。

## 第一次启动

在解压后的根目录打开 PowerShell：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1
```

脚本会：

1. 生成只属于本机的 `.env` 和随机 n8n 加密密钥。
2. 构建并启动 n8n（本地 SQLite 持久卷）、renderer-api、assets-server 和网站。
3. 导入并激活 `My workflow 3`。
4. 检查网站、n8n 和 renderer-api 是否可以访问。

打开：

- 网站：`http://127.0.0.1:8080/`
- n8n 管理页：`http://127.0.0.1:5679/`
- renderer-api 健康检查：`http://127.0.0.1:3001/health`
- 图片资源服务：`http://127.0.0.1:8088/`（根路径没有首页，返回 404 属于正常情况）

## 网站内操作

1. 注册或登录个人账号。
2. 打开 Settings。
3. 分别保存自己的 DeepSeek Key 和 Qwen Key。网站不会在页面中回显完整 Key。
4. 上传 PDF，并填写论文题目、摘要和生成参数。
5. 点击保存并生成。
6. 系统先读取 PDF 文字、图注和原图，再调用 n8n + DeepSeek 生成证据化视觉简报。
7. renderer-api 返回原图、Qwen 和 ComfyUI/IP-Adapter 候选；未安装 ComfyUI 时会使用 Qwen、原图或模板回退。
8. 在候选区选择图片；可使用 cover、contain、smart 或 stretch 编辑模式。

比例预设：

- 网站横图：`900 x 600`，3:2。
- 小红书封面：`1242 x 1656`，3:4。
- 方图：`1080 x 1080`，1:1。
- 宽屏：`1600 x 900`，16:9。

## 停止与再次启动

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1
```

Docker volumes 会保留 n8n、网站 H2 数据、上传文件和 renderer 缓存。删除 volumes 会清空本地数据。

## 常见问题

- n8n 显示 Unauthorized：确认网站 Settings 中已经保存当前登录用户的 DeepSeek Key，再重新生成。
- 只有原图：确认 Qwen Key 有图片模型权限；若选择双模型，还需启动 ComfyUI。
- ComfyUI 不可用：把图片模型选择为 Qwen 或自动，不影响原图与 Qwen 路线。
- 页面仍显示旧资源：强制刷新浏览器，或重新运行 `scripts/start.ps1`。
- 端口冲突：修改根目录 `.env` 中的公开端口，然后重新启动。

## 生产环境数据库

分享包为降低首次安装失败率，n8n 默认使用其官方内置 SQLite 并保存到 Docker 卷。多人长期部署时，可按 n8n 官方部署文档改用 PostgreSQL；网站业务数据库与 n8n 数据库相互独立。
