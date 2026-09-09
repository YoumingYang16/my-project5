# 使用 Cloudflare 部署 PaperLens

## 推荐架构

```text
手机/电脑
   ↓ HTTPS
Cloudflare DNS + Tunnel
   ↓ outbound-only connection
cloudflared → Caddy → website
                         ├─ n8n
                         ├─ renderer-api
                         ├─ assets-server
                         └─ MySQL
```

Cloudflare Pages 不适合直接部署当前项目，因为当前项目包含 Spring Boot、n8n、图片渲染、PDF 上传和持久化数据库。Cloudflare Tunnel 适合作为现有 Docker 系统的公网入口，不需要在服务器安全组开放 80/443。

## Cloudflare 控制台步骤

1. 在 Cloudflare 添加域名，并把域名 DNS 托管切换到 Cloudflare。
2. 打开 Zero Trust → Networks → Tunnels → Create a tunnel。
3. 选择 Docker，复制 Tunnel Token。
4. 创建 Public Hostname，例如 `paperlens.example.com`。
5. Service 填写 `http://caddy:80`。这个地址是 Docker Compose 网络内的 Caddy，不是公网地址。
6. 在服务器复制 `.env.production.example` 为 `.env`，填入真实域名和所有随机密钥。
7. 增加：

   ```dotenv
   CLOUDFLARE_TUNNEL_TOKEN=你的TunnelToken
   ASSETS_PUBLIC_URL=https://你的域名/assets
   ```

8. 启动：

   ```bash
   docker compose \
     --env-file .env \
     -f docker-compose.yml \
     -f docker-compose.production.yml \
     -f docker-compose.cloudflare.yml \
     config --quiet

   docker compose \
     --env-file .env \
     -f docker-compose.yml \
     -f docker-compose.production.yml \
     -f docker-compose.cloudflare.yml \
     up -d --build
   ```

## 手机使用

公网域名通过 Cloudflare 自动提供 HTTPS。手机打开同一域名后，选择浏览器的“添加到主屏幕”，即可安装 PaperLens PWA。无需另做 Android 或 iOS 客户端。

## 重要限制：生图请求时长

Cloudflare 代理对单次 HTTP 响应有时限。当前图片生成链路可能调用 DeepSeek、Qwen、ComfyUI、OCR 和模板渲染，某些任务可能超过 Cloudflare 的单请求等待时间，导致浏览器看到 524，但服务器任务可能仍在继续。

正式上线前建议把生图接口改成：

```text
POST /generate → 立即返回 jobId
GET /generate/{jobId} → 查询进度
GET /generate/{jobId}/result → 获取候选图
```

在这个异步改造完成前，Cloudflare Tunnel 适合论文上传、文案生成和较快的图片任务；长时间 ComfyUI 任务仍有超时风险。Cloudflare 只负责入口，不会改变图片质量或模型调用逻辑。

## 安全边界

- n8n、renderer-api、ComfyUI 和 MySQL 不配置 Public Hostname。
- 不把 API Key 写进镜像、Git 或 Tunnel 配置。
- 用户自己的 DeepSeek/Qwen/Doubao Key 继续由网站加密保存。
- 可在 Cloudflare Access 中给 n8n 管理页增加管理员身份保护，但普通用户不应看到 n8n。
