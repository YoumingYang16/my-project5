# 上传 PaperLens 到 GitHub

## 上传哪一部分

只上传本目录 `paperlens-production`，不要上传外层的“自动图片”目录。

需要上传：

- `website/`：Spring Boot 后端和网站前端。
- `image-workflow/`：renderer-api、n8n workflow 和图片处理代码。
- `scripts/`：启动、部署和工作流导入脚本。
- `docs/`：安装、架构和验证文档。
- `docker-compose*.yml`、`Caddyfile*`：本地与 Cloudflare 部署配置。
- `.env*.example`：不含真实密钥的配置示例。

不要上传：

- `.env` 和任何真实 API Key、Tunnel Token。
- 用户 PDF、生成文案、生成图片、Prompt 记录和数据库。
- Docker volumes、ComfyUI 模型、Maven/npm 缓存和日志。
- 外层项目中的 PPT、截图、历史补丁、测试输出和备份目录。

## 推荐：建立私有仓库

1. 打开 <https://github.com/new>。
2. Repository name 填 `paperlens`。
3. Visibility 选择 **Private**。
4. 不要勾选 README、`.gitignore` 或 License，因为本地已经存在。
5. 创建仓库后复制 HTTPS 地址，例如：

   ```text
   https://github.com/你的用户名/paperlens.git
   ```

## 首次上传

在本目录打开 PowerShell：

```powershell
git init -b main
git add .
git status
git commit -m "Initial PaperLens test deployment"
git remote add origin https://github.com/你的用户名/paperlens.git
git push -u origin main
```

GitHub 现在不接受账号密码进行 Git 推送。浏览器登录提示出现时使用 Git Credential Manager；也可以使用 GitHub Personal Access Token，但不要把 Token 写入 `.env` 或源码。

## 后续更新

```powershell
git add .
git status
git commit -m "Describe the update"
git push
```

每次提交前都先看 `git status`。如果出现 `.env`、PDF、用户图片、数据库或模型文件，先停止上传并更新 `.gitignore`。
