# 分享包验证报告

验证日期：2026-08-09

## 已通过

- 从空 Docker 卷运行 `scripts/start.ps1`，首次启动约 36 秒（本机已有基础镜像缓存）。
- 网站 `http://127.0.0.1:8080/`：HTTP 200。
- n8n `/healthz`：HTTP 200。
- renderer-api `/health`：HTTP 200。
- assets-server 固定端口可访问；不存在的图片返回 404 属于正常行为。
- `My workflow 3` 从清理后的 JSON 成功导入并发布，ID 为 `7bF1r4MeLd7YY3UU`。
- n8n 容器通过 Docker 内部网络访问 `website:8080` 和 `renderer-api:3000`，避免误连其他本地项目。
- 网站与 renderer-api Docker 镜像构建成功。
- 网站完整 Maven 测试套件通过，退出码 0。
- 前端 `app.js` 通过 Node.js 语法检查。
- n8n 使用独立 SQLite Docker 卷完成空库初始化，避开 n8n 2.14.2 在全新 PostgreSQL 上遇到的迁移冲突。

## 未在分享包中执行

- 没有使用任何真实 DeepSeek 或 Qwen Key 发起计费调用。
- 没有打包或验证 ComfyUI 模型权重；ComfyUI 是可选本地分支。
- 每位使用者需在网站 Settings 中填写自己的 DeepSeek 与 Qwen Key，再用自己的论文进行最终内容质量测试。

## 安全检查

- 最终包不包含 `.env`、真实 API Key、用户数据库、上传 PDF、生成图片、日志、Docker 卷或 ComfyUI 模型。
- n8n workflow 中不包含凭据引用，用户 DeepSeek Key 通过网站的单次任务令牌代理调用。
- Qwen Key 由当前登录用户配置并仅用于该用户请求。