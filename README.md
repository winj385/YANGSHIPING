# 央视频电视直播 App (YangshipinTV)

把 `https://www.yangshipin.cn/tv/home` 用系统 WebView 包成一个**安卓电视直播 App**：  
全屏无地址栏、伪装成电脑浏览器、只留播放画面、48 个频道、遥控器换台、带黑色遮罩的秒切、  
20 秒无画面自动切央视网备用源、启动检查 WebView 内核版本，最后可打包并签名 APK。

> ⚠️ 本仓库是**完整可构建的源码工程**。当前开发机没有 Android SDK / JDK，无法直接在这里  
> 编译出 APK，因此提供了三种构建方式（见下方「构建与签名」）。任选其一即可拿到签好名的 APK。

---

## 需求对照（十条）

| # | 要求                                          | 实现位置                                                                                             |
| - | ------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| 1 | 系统 WebView 全屏加载，无地址栏/浏览器控件                  | `MainActivity.kt` + 全屏沉浸式主题 `themes.xml` + `layout/activity_main.xml`                            |
| 2 | 伪装成电脑浏览器，但 Chrome 版本号读设备真实内核，不写死            | `WebViewSetup.buildDesktopUserAgent()`：从 `WebSettings.getDefaultUserAgent()` 解析真实 `Chrome/xx` 版本 |
| 3 | 注入脚本只留播放画面，按「位置+大小」判断，不写死 class 名           | `assets/inject.js`：`pinPlayer()` / `removeOverlays()`（几何判定，含登录弹窗、下载横幅、右侧节目单）                     |
| 4 | 频道表 CCTV-1..17 + 31 省级卫视 = 48；OK 滑出列表，▲▼ 换台 | `ChannelRepository.kt` + `MainActivity` 的 `onKeyDown`                                            |
| 5 | 读取正在播放栏目；列表显示每台节目；换台快、不整页刷新                 | `inject.js` `getNowPlaying()` + `switchTo()`（驱动页面自身控件，非整页 reload）                                |
| 6 | 换台黑色遮罩显示频道号+名，出画面即撤；20s 无画面切 tv.cctv.com    | `MainActivity` `showMask/hideMask` + `startReadyPolling` + `startFallbackTimer`                  |
| 7 | 启动检查 WebView 内核版本，过旧直接提示                    | `WebViewSetup.getWebViewVersion()` + `checkWebViewVersionThenInit()`                             |
| 8 | 打包 APK 并签名                                  | Gradle `signingConfigs` + `build_and_sign.*` + GitHub Actions `build.yml`                        |

> 说明：第 5 条「列表显示**每个**台正在播什么」——单页一次只加载一个频道，所以  
> **当前台**的节目名会从页面实时抓取并显示；其余台在切到该台后才会显示其节目名。  
> 若要 48 台同时显示节目单，需要接一个 EPG 节目单接口（见「可定制」）。

---

## 项目结构

```
YangshipinTV/
├── app/
│   ├── build.gradle                 # 模块构建 + 签名配置
│   └── src/main/
│       ├── AndroidManifest.xml       # 电视 LAUNCHER/LEANBACK_LAUNCHER、全屏、横屏
│       ├── assets/inject.js          # 注入脚本（钉播放器/清浮层/换台/读节目）
│       ├── java/com/example/yangshiptv/
│       │   ├── MainActivity.kt       # 主控：WebView、遥控器、遮罩、回退、版本检查
│       │   ├── Channel.kt            # 频道数据类
│       │   ├── ChannelRepository.kt  # 48 个频道的名称与别名
│       │   ├── WebViewSetup.kt       # 动态 UA + WebView 版本检测
│       │   ├── WebAppBridge.kt       # JS→App 桥（回传节目名）
│       │   └── ChannelAdapter.kt     # 左侧频道列表适配器
│       └── res/                      # 布局、主题、图标、TV banner
├── build.gradle / settings.gradle / gradle.properties
├── .github/workflows/build.yml       # 云端构建并签名 APK
├── build_and_sign.sh / .bat          # 本地一键构建脚本
└── keystore.properties.template      # 本地签名配置模板
```

---

## 构建与签名（三选一）

### 方式 A：Android Studio（最省事，推荐）

1. 用 Android Studio 打开 `YangshipinTV` 目录（会自动同步 Gradle）。
2. 菜单 **Build → Generate Signed Bundle / APK → APK**，按提示创建/选择你的 keystore。
3. 产物：`app/build/outputs/apk/release/app-release.apk`（已签名）。

### 方式 B：本地命令行

需要本地已安装 `gradle` 与 `keytool`（JDK 自带）：

```bash
# macOS / Linux
./build_and_sign.sh
# Windows
build_and_sign.bat
```

脚本会在没有 `keystore.properties` 时自动生成一个本地调试 keystore 并签名，  
产物同样是 `app/build/outputs/apk/release/app-release.apk`。

### 方式 C：GitHub Actions 云端构建（本机无需装任何东西）

1. 把本仓库推到 GitHub。
2. 仓库 **Settings → Secrets and variables → Actions** 添加（可选，不填也会自动生成临时 keystore）：
   - `KEYSTORE_BASE64`：你 keystore 文件的 base64（`base64 -w0 your.keystore`）
   - `KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`
3. **Actions → Build & Sign APK → Run workflow**。
4. 跑完后在 **Artifacts** 下载 `yangshipin-tv-release`（里面就是签好名的 `app-release.apk`）。

> 调试用最快路径：直接 `gradle assembleDebug` 也能得到一个**已签名（debug）**、  
> 可直接 `adb install` 的 APK，个人电视侧载完全可用。

---

## 安装到安卓电视

```bash
# 电视和电脑在同一 Wi-Fi，开启「无线调试 / 网络 ADB」，拿到电视 IP
adb connect 192.168.x.x:5555
adb install app/build/outputs/apk/release/app-release.apk
```

或在电视上用 U 盘 / 文件管理器直接安装 APK。  
打开后即为全屏直播；遥控器 **OK** 滑出频道列表，**▲/▼** 直接换台，**返回** 先收起列表。

---

## 上机后可能需要微调的地方（诚实说明）

网页端（央视频）的 DOM 结构会经常改，所以脚本**刻意不写死 class 名**，全部按  
「位置 + 大小 + 可见文字」判断。少数情况下你可能需要按真机表现微调：

1. **频道别名** `ChannelRepository.kt` 的 `aliases`：脚本靠页面上的可见文字匹配频道控件。  
   如果某台切不动，多半是该台在页面上的文字和别名对不上，加一个别名即可（如 `"CCTV5"`）。
2. **换台机制** `inject.js` 的 `switchTo()`：优先点击页面自身的频道控件（不整页刷新）。  
   若页面改版导致找不到控件，会回退到 `Channel.fallbackUrl` 整页跳转——此时会有一次闪现，  
   属于可接受的兜底。
3. **浮层判定阈值** `removeOverlays()`：登录弹窗 / 下载横幅 / 右侧节目单的判定区间  
   （屏幕占比、贴边距离）都在函数顶部，按真机截图调即可。
4. **播放器识别** `findPlayer()`：优先 `<video>`，否则取「最大且最居中」的元素。  
   极少数自定义播放器若识别偏差，可放宽面积/居中权重。

---

## 已知限制

- 单页只能拿到**当前频道**的节目名；48 台同显节目单需外接 EPG 接口（可在 `WebAppBridge`  
  与 `startEpgPolling()` 处扩展）。
- 备用源 `tv.cctv.com` 对**省级卫视**覆盖有限，卫视超时回退后可能仍是央视门户页。
- 旧款电视若 WebView 内核过旧（检测阈值 `WebViewSetup.MIN_WEBVIEW_MAJOR = 70`），  
  启动会弹窗提示，可「继续尝试」或先去系统里更新 Android System WebView。
