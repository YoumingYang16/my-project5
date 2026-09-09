# 系统架构与图片流程

```text
浏览器
  -> Spring Boot 网站
     -> PDFBox / PDF 原图与文字提取
     -> n8n My workflow 3
        -> 网站内部单次任务令牌代理
        -> 用户自己的 DeepSeek Key
        -> 证据化视觉简报
     -> renderer-api
        -> 论文原图候选
        -> 用户自己的 Qwen Image Key
        -> ComfyUI / RealVisXL / IP-Adapter（可选）
        -> OCR、基础质量、相关性和重复度检查
     -> 网站本地候选图
     -> 用户审核、编辑和选择
```

## 关键原则

- n8n 不保存用户 DeepSeek Key，只接收高随机、单次使用、短时有效的任务令牌。
- Qwen Key 由网站按当前用户、当前请求传给内部 renderer-api，不写入图片 manifest。
- PDF 原图不会被覆盖，派生尺寸单独保存。
- 网站正式保存的是本地 `/uploads/...` 路径，不保存 Qwen 临时地址或 localhost 资源地址。
- 每篇论文候选尽量包含原图、Qwen 图和 ComfyUI/IP-Adapter 图；某个模型不可用时单篇任务回退，不影响论文和文案数据。

## 当前图片编辑能力

- cover：裁切填充，可拖动裁切框和八个控制点。
- contain：完整保留并添加背景色。
- smart：按用户焦点自动裁切。
- stretch：通过四边和四角边界框拉伸，并同步输出宽高。
- 3:2、3:4、1:1、16:9 预设会直接改变可视边界框。
