# MAA-Meow Repository Instructions

## Repository role

This repository is a fork of `Aliothmoon/MAA-Meow`.

- `upstream`: the author's repository, `https://github.com/Aliothmoon/MAA-Meow.git`
- `origin`: the personal fork, `https://github.com/keeleys/MAA-Meow`
- `main`: a clean mirror of `upstream/main`; never place personal commits here
- `personal/main`: the integration branch for changes retained only in this fork

Before making changes, inspect `git status`, the current branch, and both remotes. Preserve unrelated user changes and never discard them.

## Required branch policy

Classify every change before implementation.

### Changes intended for upstream

Create a focused topic branch from the latest `main`:

- `feat/<short-name>` for features
- `fix/<short-name>` for bug fixes
- `docs/<short-name>` for documentation
- `refactor/<short-name>` for behavior-preserving refactors
- `chore/<short-name>` for build, dependency, and maintenance work

One branch and one pull request must address one concern. Do not mix formatting, dependency upgrades, or unrelated cleanup into a functional change.

### Fork-only changes

Use `personal/<short-name>` for an independently maintained fork patch. Integrate completed fork patches into `personal/main`, never into `main`, and add or update the entry in `FORK_PATCHES.md`.

If an upstream pull request is rejected but the change is still wanted, preserve its focused commits on a `personal/<short-name>` branch, record the upstream PR and rejection rationale in `FORK_PATCHES.md`, then integrate it into `personal/main`.

If upstream later implements or accepts an equivalent change, remove the fork patch during the next `personal/main` rebase instead of keeping duplicate behavior.

## Sync policy

Use fast-forward-only updates for the mirror branch:

```bash
git fetch upstream --prune --tags
git switch main
git merge --ff-only upstream/main
git push origin main
```

Never merge `personal/main` or a `personal/*` branch into `main`. Never force-push `main`. Rebase focused, unpublished topic branches when useful; do not rewrite shared branches without explicit user approval.

For the complete human workflow, see `docs/zh-cn/develop/FORK_WORKFLOW.md`.

## Commit and pull request rules

Follow Conventional Commits:

```text
<type>(<scope>): <subject>
```

Examples:

- `feat(schedule): 支持顺序任务执行`
- `fix(background): 修复虚拟显示启动失败`
- `docs: 补充 fork 同步流程`

Keep commits small, independently understandable, and safe to cherry-pick. A pull request description must state motivation, scope, verification, compatibility impact, and known risks. Use a Draft PR when the design is not settled.

## Project map

- `app/src/main/java/.../presentation`: Compose UI, navigation, and view models
- `app/src/main/java/.../domain`: use cases, execution state, and domain services
- `app/src/main/java/.../data`: preferences, repositories, resources, updates, and API models
- `app/src/main/java/.../manager`: Shizuku/Root selection, privileged process startup, and service connections
- `app/src/main/java/.../remote`: privileged-process AIDL services, display, capture, and input
- `app/src/main/java/.../maa`: MaaCore JNA bindings, task parameters, and callbacks
- `app/src/main/java/.../schedule`: alarms, receivers, scheduled execution, and schedule UI
- `app/src/main/native`: native bridge, capture, input, and preview
- `annotation-api` and `ksp-processor`: preference schema annotations and code generation
- `hidden-api`: declarations for Android hidden APIs
- `build-logic`: repository-specific Gradle plugins
- `scripts`: MaaCore setup and repository maintenance utilities

The main execution path is approximately:

```text
Compose UI -> ViewModel/UseCase -> domain service -> RemoteServiceManager
-> Shizuku or Root process -> AIDL service -> MaaCore/native bridge
```

## Development expectations

- Build with the JDK and Android tooling specified in `docs/zh-cn/develop/BUILDING.md`.
- Obtain MaaCore artifacts with `python scripts/setup_maa_core.py`; do not commit downloaded binaries, generated build output, IDE state, logs, credentials, or signing files.
- Keep visible strings in Android resources and maintain both Chinese and English resources when applicable.
- User-visible behavior changes require corresponding documentation updates.
- Changes to permissions, Shizuku/Root, AIDL, background execution, alarms, virtual displays, native code, or MaaCore compatibility must document Android version and backend compatibility.
- Preserve cancellation, error handling, binder-death handling, and resource cleanup paths for long-running work.
- Prefer existing repositories, use cases, Koin modules, and Compose patterns over introducing parallel architecture.
- Add focused regression tests for behavior changes. Run the narrowest relevant tests first, followed by the appropriate module test/build task when practical.
- Do not change package name, application ID, signing, update endpoints, or release workflows for a normal upstream contribution.

## Verification handoff

When finishing work, report:

1. the branch and commits created;
2. files and behavior changed;
3. tests or build commands run and their results;
4. device, Android version, and Shizuku/Root backend for runtime-sensitive changes;
5. anything not verified;
6. whether the change is intended for upstream or is fork-only.

