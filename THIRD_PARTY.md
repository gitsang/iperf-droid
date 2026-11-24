# Third-Party Components and Licenses

## iperf3 executables

`app/src/main/assets/iperf3/<abi>/iperf3`

| Item          | Details                                                                                 |
| ------------- | --------------------------------------------------------------------------------------- |
| Software      | iperf3                                                                                  |
| Version       | 3.9                                                                                     |
| Source        | [ninjab3s/ogt_androidue_utilities](https://github.com/ninjab3s/ogt_androidue_utilities) |
| License       | MIT                                                                                     |
| Linking       | Statically compiled, no external `.so` dependencies                                     |
| Architectures | `arm64-v8a` / `armeabi-v7a` / `x86_64` / `x86`                                          |

The upstream project for iperf3 is [ESnet/iperf](https://github.com/esnet/iperf), also MIT licensed.

## AndroidX dependencies

| Dependency                                 | License            |
| ------------------------------------------ | ------------------ |
| androidx.core:core-ktx                     | Apache License 2.0 |
| androidx.appcompat:appcompat               | Apache License 2.0 |
| androidx.recyclerview:recyclerview         | Apache License 2.0 |
| androidx.constraintlayout:constraintlayout | Apache License 2.0 |
| androidx.leanback:leanback                 | Apache License 2.0 |
| org.json:json (test scope only)            | Public Domain      |

The full Apache License 2.0 text: <https://www.apache.org/licenses/LICENSE-2.0>

## Icon assets

`app/src/main/res/mipmap-*/` and `app/src/main/res/drawable-*/tv_banner.png` are all
generated programmatically by this repository's `tools/gen_icons.py` (Pillow).
No third-party artwork is used.

## Acknowledgements

- [ESnet/iperf](https://github.com/esnet/iperf) — the upstream iperf3 project
- [ninjab3s/ogt_androidue_utilities](https://github.com/ninjab3s/ogt_androidue_utilities) — statically compiled iperf3 binaries for Android
- [ciniaogui/android-iperf3](https://github.com/ciniaogui/android-iperf3) — a reference for cross-compiling iperf3 with the Android NDK
