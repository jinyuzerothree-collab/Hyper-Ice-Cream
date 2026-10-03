# 主题收录规范（CONTRIBUTING）

社区主题库 = 本仓库 `themes/` 目录 + `index.json` 索引。零服务器，全部走 GitHub。

## 收录流程（贡献者）

1. 在「主题直装」App 内部署主题成功 → 点「分享主题给社区？」→ 填写**署名**与**来源链接** → 生成贡献包（`/sdcard/ThemeToolCommunity/`）
2. 提交 Issue（标题 `收录: <主题名>`），附贡献包内 `meta.json` 内容；mtz 大文件传网盘/Release 附链接
3. 维护者核对后放入 `themes/<主题名>/`（mtz + 预览图）并更新 `index.json`

## 硬性要求

- **原作者声明**：meta.json 的 `author` 字段来自主题包本身，不得篡改
- **来源声明**：`source_url` 尽量填写（你在哪里获取的主题）；无来源的原创主题可注明"原创"
- **sha256**：与 index.json 一致，客户端校验
- 侵权主题：Issue 举报即下架（删除即全网索引失效）

## index.json 字段

```json
{
  "name": "主题名",
  "author": "原作者",
  "contributor": "贡献者署名/匿名ID",
  "version": "版本",
  "ui_version": "适配UI版本",
  "components": ["组件清单"],
  "sha256": "主题包哈希",
  "size_bytes": 0,
  "preview_url": "预览图直链",
  "download_url": "mtz下载直链",
  "source_url": "获取来源（必填，原创填 own）",
  "notes": "备注"
}
```
