# iPerf-TV（iperf-droid）

给 Android TV 用的 iperf3 图形化测试工具：首页用遥控器点选快捷命令，右上角进入配置页管理多个 iperf3 参数组合（如 `-c 192.168.5.200`），点击即可开测并实时看输出。

| 项目 | 说明 |
|---|---|
| 应用名 | iPerf-TV |
| 包名 | `com.example.iperftv` |
| 最低版本 | Android 5.0（API 21） |
| 目标版本 | Android 14（API 34） |
| 技术栈 | Kotlin + AndroidX ConstraintLayout |
| 许可 | MIT（iperf3 二进制亦为 MIT，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)） |

## 已知问题与修复

### 日志要等测试结束才一次性刷出（已修复）

**原因**：iperf3 把结果写到 stdout。当 stdout 是**管道**时，libc 使用全缓冲（通常 4KB），
而 iperf3 每个 interval 只输出几十字节且默认不 flush，于是输出一直攒在缓冲区里，
直到测试结束才一次性吐出来。

源码位置：iperf3 3.9 `src/iperf_api.c` → `iperf_print_intermediate()` 末尾：

```c
if (test->logfile || test->forceflush)
    iflush(test);
```

也就是说 iperf3 自己提供了 `--forceflush` 开关来强制每个 interval 刷盘。

**修复**：应用在执行前自动补上 `--forceflush`（用户已填写时不重复追加），
实现见 `CommandBuilder.withRealtimeOutput()`，并有 `CommandBuilderTest` 做回归保护。

附带优化：

- 运行页改用 `TextView.append()` 增量追加，替代每行重建整段文本 —— 测试跑几分钟时
  输出可达上千行，全量重建会造成 UI 卡顿，反而让实时效果变差。
- 运行页顶部新增**实时速率条**，每收到一个 interval 结果就刷新一次
  （当前速率 / 已测时长 / UDP 抖动与丢包），解析逻辑见 `IntervalParser`。
- **JSON 模式（`-J`）例外**：iperf3 的 JSON 模式会把全部结果累积进 JSON 树，
  测试结束才一次性打印，中途不会有任何速率行。此时运行页改为显示
  「测试进行中 N 秒」计时器，避免用户误以为卡死。

### `iperf3: error - unable to create a new stream: No such file or directory`

**原因**：iperf3 建立数据流时会调用 `mkstemp()` 创建一个缓冲区文件，路径由环境变量
`TMPDIR` / `TEMP` / `TMP` 决定，取不到则硬编码回落到 `/tmp`。**Android 没有 `/tmp` 目录**，
`mkstemp()` 返回 `ENOENT`，iperf3 随即以 `IECREATESTREAM` 退出并打印该错误。

源码位置：iperf3 3.9 `src/iperf_api.c` → `iperf_new_stream()`；
错误消息映射在 `src/iperf_error.c` → `iperf_strerror()`（`IECREATESTREAM` 带 `perr=1`，
会拼接 `strerror(errno)`，故显示为 `...: No such file or directory`）。

**修复**：应用在启动 iperf3 子进程时显式注入环境变量
`TMPDIR` / `TMP` / `TEMP` → `context.cacheDir/tmp`（应用私有可写目录）。
实现见 `IperfRunner.tempEnv()`，并有 `TempEnvTest` 做回归保护。

### 排查工具

配置页 → **环境自检**，会输出 ABI、二进制路径与大小、可执行权限、TMPDIR 实际取值、
`iperf3 --version` 的执行结果。装机后先跑一次，可提前发现 SELinux 拦截、
ABI 不匹配、临时目录不可写等问题。

## 功能

- **首页快捷按钮网格**：每条快捷命令一个卡片，显示名称和完整命令（`iperf3 -c 192.168.5.200`）。方向键移动焦点，OK 键执行。
- **右上角「配置」入口**：进入配置页，可新增 / 编辑 / 删除快捷命令。
- **常用参数模板**：配置弹窗内置常用模板（TCP 默认、10 秒、JSON 输出、反向、UDP 100M、本机做服务端），点一下即填入，避免遥控器打字。
- **实时输出**：运行页滚动显示 iperf3 逐行输出，带时间戳，右上角「停止」可中断。顶部实时速率条随每个 interval 刷新。
- **智能提示**：常见失败（连不上服务器、端口占用、权限被拒等）自动给出中文排查建议。
- **环境自检**：配置页一键检测 iperf3 可执行环境，产线装机后先跑一次。
- **内置 iperf3 二进制**：assets 内置 arm64-v8a / armeabi-v7a / x86_64 / x86 四套静态编译的 iperf3（v3.9），首次运行释放到应用私有目录并执行，**无需 root**。
- **电视图标**：`tv_banner` 提供 160×90 ~ 640×360 五档密度的横向电视横幅；
  桌面图标提供方形 + 圆形两套，Android 8.0+ 自动使用自适应图标（前景/背景分离，支持主题图标着色）。

## 工程结构

```
iperf-droid/
├── settings.gradle / build.gradle / gradle.properties
├── build.sh                 # 一键构建脚本
├── gradlew / gradlew.bat    # Gradle Wrapper（8.7）
├── gradle/wrapper/
├── LICENSE                  # MIT
├── THIRD_PARTY_NOTICES.md   # 第三方组件许可声明
└── app/
    ├── build.gradle
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/iperf3/<abi>/iperf3     # 预编译二进制
        │   ├── java/com/example/iperftv/
        │   │   ├── MainActivity.kt     # 首页网格
        │   │   ├── ConfigActivity.kt   # 配置页
        │   │   ├── RunActivity.kt      # 运行与输出
        │   │   ├── Shortcut.kt         # 数据模型 + 本地存储
        │   │   ├── IperfRunner.kt      # 释放二进制 + 子进程执行
        │   │   ├── CommandBuilder.kt   # 命令拼装（含实时输出开关）
        │   │   ├── ArgsParser.kt       # 命令行参数拆分
        │   │   ├── IntervalParser.kt   # 实时速率解析
        │   │   ├── ResultParser.kt     # JSON 摘要 + 故障诊断
        │   │   └── SelfCheck.kt        # 环境自检
        │   └── res/...
        └── test/java/com/example/iperftv/   # 单元测试
```

## 构建

需要 JDK 17 与 Android SDK（compileSdk 34、build-tools 34.0.0）。仓库自带 Gradle Wrapper，无需预装 Gradle。

```bash
# 方式一：用仓库内脚本（会自动探测 JAVA_HOME / ANDROID_HOME）
./build.sh

# 方式二：手动
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug      # 产物: app/build/outputs/apk/debug/app-debug.apk
```

产物：`app/build/outputs/apk/debug/app-debug.apk`，用 `adb install -r` 装到电视即可。

### 运行单元测试

```bash
./gradlew testDebugUnitTest
```

测试报告输出在 `app/build/reports/tests/testDebugUnitTest/index.html`。

## 使用

1. 电视上打开「iPerf-TV」。
2. 右上角「配置」→ 「新增快捷命令」→ 填名称（如“默认服务器”）和参数（如 `-c 192.168.5.200`）→ 确定。
3. 返回首页，点击对应卡片即开始测试，运行页实时看输出。

### 参数说明

配置里填的是 iperf3 的参数（不含 `iperf3` 本体），常见用法：

| 参数 | 含义 |
|---|---|
| `-c 192.168.5.200` | 以客户端连指定服务器（最常用） |
| `-t 10` | 持续 10 秒 |
| `-J` | 输出 JSON，便于程序解析 |
| `-R` | 反向模式（服务器发数据到电视） |
| `-u -b 100M` | UDP 模式，限速 100M |
| `-s` | 本机作为服务端（需单独一台机器做客户端连过来） |
| `-p 5201` | 指定端口 |

### 说明

- iperf3 二进制来自 `ninjab3s/ogt_androidue_utilities`（iperf3 v3.9，静态链接，MIT）。若需更新版本，可从 `ciniaogui/android-iperf3` 用 NDK 重新编译后替换 `assets/iperf3/<abi>/iperf3`。
- Android 10+ 允许应用执行自身私有目录下的二进制，但 `execve` 需对文件有可执行权限（应用已处理 `setExecutable`）。
- 若电视是 Android 9 以下或 CPU ABI 不在支持列表内，assets 里已有的 arm64 兜底仍可尝试运行。

### 重新生成图标

图标由脚本程序化绘制，无外部素材依赖。如需调整配色或布局：

```bash
pip install pillow
python tools/gen_icons.py
```

脚本会重写 `app/src/main/res/mipmap-*/` 与 `app/src/main/res/drawable-*/tv_banner.png`，
并在 `app/preview/` 输出预览图。主题色需与 `app/src/main/res/values/colors.xml` 保持一致。

## 许可

本项目采用 MIT 许可，详见 [LICENSE](LICENSE)。

仓库内包含 iperf3 的预编译二进制（上游项目 [ESnet/iperf](https://github.com/esnet/iperf)，
同为 MIT 许可）。完整的第三方组件许可声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 致谢

- [ESnet/iperf](https://github.com/esnet/iperf) —— iperf3 上游项目
- [ninjab3s/ogt_androidue_utilities](https://github.com/ninjab3s/ogt_androidue_utilities) —— Android 平台的静态编译 iperf3 二进制
- [ciniaogui/android-iperf3](https://github.com/ciniaogui/android-iperf3) —— iperf3 的 Android NDK 交叉编译参考
