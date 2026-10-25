# 第三方组件与许可声明

本项目自身代码采用 MIT 许可（见 [LICENSE](LICENSE)），但仓库中包含以下第三方组件。

---

## iperf3 可执行文件

`app/src/main/assets/iperf3/<abi>/iperf3`

| 项目 | 说明 |
|---|---|
| 软件 | iperf3 |
| 版本 | 3.9 |
| 来源 | [ninjab3s/ogt_androidue_utilities](https://github.com/ninjab3s/ogt_androidue_utilities) |
| 许可 | MIT |
| 链接方式 | 静态编译，无外部 so 依赖 |
| 支持架构 | `arm64-v8a` / `armeabi-v7a` / `x86_64` / `x86` |

iperf3 的上游项目为 [ESnet/iperf](https://github.com/esnet/iperf)，同样采用 MIT 许可。

### 为什么内置二进制

Android 应用无法直接分发可执行文件到系统目录，而产线环境通常不允许 root。
将静态编译的 iperf3 打包进 `assets`，应用首次运行时释放到应用私有目录并以
`exec` 方式执行，从而实现「插电即用、无需 root」。

### 更新 iperf3 版本

如需升级到 3.19+ 等更新版本，用 Android NDK 交叉编译后替换对应架构目录下的
`iperf3` 文件即可，替换后请务必跑一遍「配置 → 环境自检」确认可正常执行。

> 注意：iperf3 3.19 起其临时文件回落目录从 `/tmp` 改为 `/data/local/tmp`，
> 但仍然优先读取 `TMPDIR` 环境变量。本项目始终显式注入 `TMPDIR`，
> 因此两个版本都能正常工作。

---

## AndroidX 依赖

| 依赖 | 许可 |
|---|---|
| androidx.core:core-ktx | Apache License 2.0 |
| androidx.appcompat:appcompat | Apache License 2.0 |
| androidx.recyclerview:recyclerview | Apache License 2.0 |
| androidx.constraintlayout:constraintlayout | Apache License 2.0 |
| androidx.leanback:leanback | Apache License 2.0 |
| org.json:json（仅测试期使用） | Public Domain |

Apache License 2.0 全文见：<https://www.apache.org/licenses/LICENSE-2.0>

---

## 图标资源

`app/src/main/res/mipmap-*/` 与 `app/src/main/res/drawable-*/tv_banner.png`
均由本仓库的 `tools/gen_icons.py`（Pillow）程序化生成，无第三方素材来源。
