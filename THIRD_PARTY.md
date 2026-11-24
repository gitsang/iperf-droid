# Third-Party Components and Licenses

This project's own code is MIT licensed (see [LICENSE](LICENSE)), but the repository
bundles the following third-party components.

---

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

### Why the binaries are bundled

An Android app cannot simply drop an executable into a system directory, and
production floors usually forbid root. Bundling a statically compiled iperf3 in
`assets` and unpacking it into the app's private directory on first run — then
executing it with `exec` — gives "plug in and go" behaviour with no root needed.

### Updating iperf3

To move to a newer release such as 3.19+, cross-compile with the Android NDK and
replace the `iperf3` file under each architecture directory. After replacing it,
always run "Configuration → Environment self-check" to confirm it still executes.

> Note: from 3.19 on, iperf3's temporary-file fallback directory changed from
> `/tmp` to `/data/local/tmp`, but it still reads the `TMPDIR` environment variable
> first. This project always injects `TMPDIR` explicitly, so both versions work.

---

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

---

## Icon assets

`app/src/main/res/mipmap-*/` and `app/src/main/res/drawable-*/tv_banner.png` are all
generated programmatically by this repository's `tools/gen_icons.py` (Pillow).
No third-party artwork is used.
