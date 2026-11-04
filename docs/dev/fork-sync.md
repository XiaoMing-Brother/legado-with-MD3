# Fork 同步约定（本仓库本地）

## 远程

| 远程名 | 地址 | 用途 |
|--------|------|------|
| `upstream` | `HapeLee/legado-with-MD3` | **只 fetch**，拉上游更新 |
| `xiaoming` | `XiaoMing-Brother/legado-with-MD3` | **唯一 push 目标**，备份自己的改动 |

本地已禁止向 `HapeLee` 推送（无效 push URL + `pre-push` hook）。

## 日常

```powershell
# 1. 上游有更新时
.\tools\sync-from-upstream.ps1

# 2. 解决冲突后，只推 fork
git push xiaoming main
```

等价命令：

```powershell
git fetch upstream
git merge upstream/main
git push xiaoming main
```

## GitHub 上已关闭

- **Dependabot**（不再自动开依赖 PR）
- **update fork** workflow（已禁用）
- **Verify / Auto Build / Build Web / Docs** 等：已去掉 `push`/`pull_request`/定时触发；在 fork 上 push **不会再跑 CI**

若 GitHub 仍显示旧 run，可在 **Settings → Actions → Disable actions** 或批量取消/关闭历史 PR。

将 `.github/` 下这些改动 **push 到 `xiaoming`** 后，新 push 才会完全静默。
