# PaperLens 公网部署

## 方案

使用 Ubuntu 云服务器、Docker Compose 和 Caddy：

```text
HTTPS 域名 → Caddy → website
                       ├─ n8n（内部）
                       ├─ renderer-api（内部）
                       ├─ assets-server（仅由 Caddy 转发）
                       └─ MySQL（内部）
```

Caddy 自动申请和续期 HTTPS 证书。只有 80/443 对公网开放；n8n、renderer-api、ComfyUI、MySQL 不暴露公网。

## 服务器准备

在 Ubuntu 22.04/24.04 上安装 Docker Engine 和 Compose 插件，开放安全组端口 `80`、`443`，并将域名 A/AAAA 记录指向服务器 IP。不要开放 `5679`、`3001`、`8088`、`3306`。

## 配置

```bash
cp .env.production.example .env
```

编辑 `.env`：

- `PAPERLENS_DOMAIN` 改成真实域名。
- 生成并填写所有密码、n8n 加密密钥和 `AI_KEY_ENCRYPTION_SECRET`。
- `ASSETS_PUBLIC_URL` 使用同一域名的 `/assets` 路径。
- Qwen/Doubao/DeepSeek 的服务器级 Key 保持为空；用户 Key 由网站 Settings 保存。

## 启动

```bash
docker compose -f docker-compose.yml -f docker-compose.production.yml config
docker compose -f docker-compose.yml -f docker-compose.production.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.production.yml ps
```

首次启动后打开：

```text
https://你的域名/
```

手机浏览器打开同一链接，选择“添加到主屏幕”即可安装 PaperLens PWA。生成论文图片仍然由服务器执行，用户不需要安装 Docker、ComfyUI 或模型。

## 发布前检查

1. 先注册普通用户并配置个人 API Key。
2. 上传一篇测试论文，生成文案和图片。
3. 检查手机端上传、预览、模板编辑和下载。
4. 检查重启后数据库、论文和图片仍存在。
5. 检查 n8n、renderer-api、MySQL 端口不能从公网访问。

当前本机没有云服务器、域名和 SSH 登录凭据，因此这份配置已准备完成，但还不能代替用户执行最后的远程部署。
