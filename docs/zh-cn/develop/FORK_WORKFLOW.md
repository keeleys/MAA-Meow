# Fork 同步与二次开发规范

本文档用于同时满足两个目标：持续跟进 `Aliothmoon/MAA-Meow`，以及维护尚未被上游接受的个人功能。

## 远端与长期分支

| 名称 | 用途 | 是否允许个人提交 |
|---|---|---|
| `upstream/main` | 作者仓库主分支 | 否，只读参考 |
| `origin/main` | 个人 fork 中的上游镜像 | 否 |
| `main` | 本地上游镜像 | 否 |
| `personal/main` | 个人版本集成与发布 | 是 |

`main` 必须始终能够通过 fast-forward 同步 `upstream/main`。所有开发都从最新 `main` 新建分支，不直接在 `main` 或 `personal/main` 上开发。

## 同步上游

```bash
git fetch upstream --prune --tags
git switch main
git merge --ff-only upstream/main
git push origin main
```

如果 `git merge --ff-only` 失败，说明 `main` 已经混入了个人提交。此时停止操作并检查历史，不要通过普通 merge 或 force push 掩盖分叉。

查看同步状态：

```bash
git rev-list --left-right --count main...upstream/main
git log --oneline main..upstream/main
```

第一条命令输出 `0 0` 表示本地和上游完全一致。

## 开发准备提交给上游的功能

```bash
git fetch upstream --prune
git switch main
git merge --ff-only upstream/main
git switch -c feat/example
```

分支前缀：

| 前缀 | 场景 |
|---|---|
| `feat/` | 新功能 |
| `fix/` | Bug 修复 |
| `docs/` | 文档 |
| `refactor/` | 不改变行为的重构 |
| `chore/` | 构建、依赖、工具维护 |

开发完成后先同步上游并解决冲突：

```bash
git fetch upstream --prune
git rebase upstream/main
git push -u origin feat/example
```

创建 PR 时以 `Aliothmoon/MAA-Meow:main` 为目标分支。一个 PR 只解决一个问题，并遵守仓库已有的 `PULL_REQUEST_GUIDELINES.md`。

## PR 被接受

上游合并后：

1. 按“同步上游”流程更新 `main`。
2. 删除本地和 `origin` 上已完成的 topic 分支。
3. 如果该功能曾临时进入 `personal/main`，同步时删除对应个人补丁。
4. 更新 `FORK_PATCHES.md`，将条目标记为已上游化或直接移除。

## PR 未被接受但个人版本需要保留

将功能保留成独立补丁分支：

```bash
git switch feat/example
git branch -m personal/example
git switch personal/main
git merge --no-ff personal/example
```

随后在 `FORK_PATCHES.md` 记录功能、提交、上游 PR、保留原因、影响路径和验证情况。不要把它合入 `main`。

每次上游更新后，建议在备份分支或确认工作区干净后，将 `personal/main` 重新建立在最新 `main` 之上，并逐项重放仍需要的个人补丁。若上游已经实现等价能力，应放弃对应个人补丁。

推荐启用冲突复用：

```bash
git config rerere.enabled true
```

## 提交规范

提交信息使用 Conventional Commits：

```text
<type>(<scope>): <subject>
```

示例：

```text
feat(schedule): 添加顺序任务执行
fix(remote): 修复 binder 重连状态丢失
docs: 补充个人补丁维护说明
```

提交需要足够小并保持单一目的，使其能够独立 review、rebase、cherry-pick 或移除。

## 开发验证

提交前至少完成与改动范围匹配的验证：

- Kotlin 逻辑：相关单元测试。
- UI：中英文文案、浅色/深色、必要的截图。
- 后台与定时任务：Android 版本、厂商系统、电池策略。
- Shizuku/Root：注明实际验证的提权后端。
- AIDL/远程进程：启动、断连、binder death、重连和清理路径。
- Native/MaaCore：ABI、MaaCore 版本、截图及输入链路。
- 构建系统：至少执行相关 Gradle 配置或构建任务。

不要只写“已测试”；PR 中应给出命令、设备和操作路径。

## 发布个人版本

个人版本从 `personal/main` 打标签和构建，不从 `main` 发布。若向他人分发，需要遵守 AGPL-3.0，并明确源码、版本差异、签名、更新源以及与官方版本的关系。

