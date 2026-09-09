# 交给 Codex 的使用说明

同学解压后，可以在 Codex Desktop 中打开整个文件夹，并发送下面这段话：

```text
请先阅读 README.md、docs/INSTALL-AND-USE.md、docs/ARCHITECTURE.md 和 docs/SECURITY.md。
先只读分析 website、image-workflow 和 scripts，确认本机是否安装 Docker Desktop；只有继续开发 Java 源码时才检查 JDK 21。
不要读取、打印或提交 .env 和任何 API Key。
然后运行配置检查、Java 测试和 Docker Compose 检查；若通过，使用 scripts/setup.ps1 和 scripts/start.ps1 启动。
保持文案模块接口、网站已有 UI 和用户数据结构不变，再根据我的需求修改。
```

建议让 Codex 先完成：

1. `docker version` 和 `docker compose version`。
2. 若要在 Docker 外开发 Java，再运行 `java -version` 确认 JDK 21。
3. `docker compose --env-file .env config`。
4. 在 `website` 中运行 `mvnw.cmd test`。
5. 检查 `http://127.0.0.1:8080/`、`5679/healthz` 和 `3001/health`。

协作时请明确告诉 Codex：

- 不要把真实 Key 写入源码或 n8n workflow。
- 不要提交 `uploads`、`data`、`.env`、日志和生成图片。
- 修改图片模块时不要改变文案 DTO、Prompt、接口和页面状态。
- 修改 n8n 后重新导出 workflow，并做一次网站到 n8n 的真实执行验证。
- 任何数据库删除、Docker volume 删除和批量图片清空操作都要先备份。
