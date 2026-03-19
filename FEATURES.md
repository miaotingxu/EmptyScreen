# EmptyScreen 功能文档

## 一、项目概述

EmptyScreen是一款专为Android TV/智能电视设计的全屏WebView展示应用，支持网页浏览、视频播放、开机自启、前台保活、自动内存清理等功能。应用可作为系统桌面使用，适用于数字标牌、信息展示等场景。

### 1.1 技术栈

| 技术 | 说明 |
|------|------|
| 开发语言 | Java |
| 最低SDK | API 21 (Android 5.0) |
| 目标SDK | API 34 (Android 14) |
| 架构模式 | 单Activity多页面 |
| 数据存储 | SharedPreferences |

### 1.2 项目结构

```
com.haier.emptyscreen/
├── LauncherActivity.java      # 启动页 - 3秒延时跳转 + 内存监控重启
├── MainActivity.java          # 主页面 - WebView全屏展示
├── SettingsActivity.java      # 设置页面 - 配置管理
├── VideoPlayerActivity.java   # 视频播放页面
├── EmptyScreenApplication.java # Application入口
├── adapter/
│   ├── StorageDeviceAdapter.java  # 存储设备列表适配器
│   └── FileBrowserAdapter.java    # 文件浏览器适配器
├── model/
│   └── VideoFile.java             # 视频文件数据模型
├── receiver/
│   └── BootReceiver.java          # 开机启动接收器
├── service/
│   └── ForegroundService.java     # 前台保活服务
├── utils/
│   ├── LogUtils.java              # 日志工具
│   ├── PrefsManager.java          # 配置管理
│   ├── NetworkUtils.java          # 网络工具
│   ├── MemoryUtils.java           # 内存工具
│   ├── MemoryCleaner.java         # 内存清理工具
│   ├── StorageUtils.java          # 存储设备工具
│   ├── UrlValidator.java          # URL验证工具
│   ├── FocusUtils.java            # TV焦点工具
│   └── OrientationHelper.java     # 屏幕方向工具
└── webview/
    └── CustomWebViewClient.java   # WebView客户端
```

## 二、页面结构

```
┌─────────────────────────────────────────┐
│              应用启动                     │
│         (BootReceiver/AlarmManager)      │
└─────────────────┬───────────────────────┘
                  ▼
┌─────────────────────────────────────────┐
│        LauncherActivity (启动页)         │
│    显示Logo + 3秒延时 + 内存监控          │
│    内存超80%自动重启优化                  │
└─────────────────┬───────────────────────┘
                  │ 3秒后自动跳转
                  ▼
┌─────────────────────────────────────────┐
│           MainActivity (主页)            │
│         全屏WebView展示网页              │
│    ┌─────────────────────────────────┐  │
│    │  右上角: 内存占用百分比          │  │
│    │  右下角: 设置按钮                │  │
│    └─────────────────────────────────┘  │
└─────────────────┬───────────────────────┘
                  │ 点击设置/菜单键
                  ▼
┌─────────────────────────────────────────┐
│         SettingsActivity (设置页)        │
│   网页设置/网络信息/内存/时间/清理配置    │
└─────────────────┬───────────────────────┘
                  │ 选择视频文件
                  ▼
┌─────────────────────────────────────────┐
│      VideoPlayerActivity (视频播放)      │
│   本地/网络视频播放/循环模式/遥控器控制   │
└─────────────────────────────────────────┘
```

## 三、核心功能模块

### 3.1 LauncherActivity (启动页)

**职责**: 启动页展示、3秒延时跳转、内存监控与自动重启

| 功能 | 说明 | 实现方式 |
|------|------|----------|
| 启动页展示 | 显示应用Logo、名称和加载进度 | ConstraintLayout居中布局 |
| 3秒延时跳转 | 固定延时3秒后自动跳转MainActivity | CountDownTimer精确计时 |
| 内存监控 | 实时检测系统内存占用情况 | Handler定时检查(500ms间隔) |
| 内存超限重启 | 内存使用率超过80%时自动重启应用 | MemoryCleaner清理 + 进程重启 |
| 冷启动优化 | 减少白屏时间，提升用户体验 | 专用启动主题 + windowBackground |
| 横竖屏适配 | 支持不同屏幕方向自适应布局 | layout-land/layout-port布局 |

**交互流程:**
1. 启动 → 显示Logo和加载动画 → 开始3秒倒计时
2. 倒计时结束 → 内存正常 → 跳转MainActivity
3. 内存超80% → 显示优化提示 → 执行内存清理 → 重启LauncherActivity

**内存重启机制:**
- 检测间隔: 500ms
- 触发阈值: 80%
- 处理流程: 清理缓存 → 清理WebView → 执行GC → 重启应用
- 恢复时间: ≤2秒

### 3.2 MainActivity (主页)

**职责**: 全屏WebView展示、内存监控、视频入口、设置入口

| 功能 | 说明 | 实现方式 |
|------|------|----------|
| WebView展示 | 全屏加载用户配置的URL网页 | CustomWebViewClient |
| 内存监控 | 右上角实时显示系统内存占用百分比 | Handler定时更新 |
| 设置入口 | 右下角浮动按钮进入设置页 | ImageButton |
| 视频入口 | 遥控器菜单键打开存储选择对话框 | onKeyDown |
| 错误处理 | 网络异常/URL无效时显示错误页和重试按钮 | WebViewErrorCallback |
| 全屏模式 | 沉浸式全屏，隐藏系统UI | SYSTEM_UI_FLAG_IMMERSIVE_STICKY |

**交互流程:**
1. 启动 → 检查网络 → 加载URL → 显示网页
2. 网络异常 → 显示错误页 → 点击重试 → 重新加载
3. 按菜单键 → 显示存储选择对话框 → 选择视频 → 播放
4. 点击设置按钮 → 跳转设置页

### 3.3 SettingsActivity (设置页)

**职责**: 应用配置管理、系统信息展示

**布局顺序（从上到下）:**
1. **网页设置** - URL输入框 + 保存按钮（首要展示项）
2. **网络信息 + 内存信息** - 并排展示（横屏）/ 顺序展示（竖屏）
3. **时间设置** - 开机延迟 + 后台拉起延迟配置
4. **内存清理设置** - 自动清理开关/阈值/间隔/立即清理按钮

| 模块 | 功能 | 配置项 |
|------|------|--------|
| 网页设置 | URL输入框 + 保存按钮 | URL字符串 |
| 网络信息 | 显示网络状态/类型/名称/IP地址 | 只读展示 |
| 内存信息 | 进度条展示内存占用百分比 | 只读展示 |
| 时间设置 | 开机延迟启动 + 后台拉起延迟配置 | 30-500秒 |
| 内存清理 | 自动清理开关/阈值/间隔/手动清理 | 阈值50-95%/间隔30-300秒 |
| 系统设置 | 跳转Android系统设置 | - |

### 3.4 VideoPlayerActivity (视频播放)

**职责**: 本地/网络视频播放、遥控器控制

| 功能 | 说明 |
|------|------|
| 视频播放 | 支持本地文件和网络流媒体 |
| 循环模式 | 单文件循环/文件夹循环 |
| 进度控制 | SeekBar拖动/快进快退 |
| 音量控制 | 系统音量调节 |
| 控制面板 | 自动隐藏(5秒无操作) |

**遥控器按键映射:**

| 按键 | 功能 |
|------|------|
| 方向键左 | 快退10秒 |
| 方向键右 | 快进10秒 |
| 确认键 | 显示/隐藏控制面板 |
| 菜单键 | 切换循环模式 |
| 音量键 | 调节音量 |
| 媒体播放/暂停 | 播放/暂停 |
| 返回键 | 退出播放 |

### 3.5 ForegroundService (前台服务)

**职责**: 前台保活、后台拉起、内存监控

| 功能 | 说明 | 实现方式 |
|------|------|----------|
| 前台服务 | 保持应用在后台运行 | Notification + startForeground |
| 生命周期监控 | 监控三个目标Activity状态 | ActivityLifecycleCallbacks |
| 后台拉起 | 应用进入后台后延迟拉起首页 | Handler定时检查 |
| 内存监控 | 定时检查内存使用率 | MemoryUtils |
| 自动清理 | 内存超阈值时自动清理 | MemoryCleaner |

**目标Activity**: LauncherActivity, MainActivity, SettingsActivity, VideoPlayerActivity

**工作原理**:
```
1. 使用计数器跟踪目标Activity的resumed状态
2. 当所有目标Activity都paused时，标记应用进入后台
3. 后台时间超过配置阈值后，启动MainActivity
4. 定时检查内存使用率，超阈值触发清理
```

### 3.6 BootReceiver (开机启动)

**职责**: 开机后延迟启动应用

| 功能 | 说明 |
|------|------|
| 开机广播 | 接收BOOT_COMPLETED等广播 |
| 延迟启动 | 使用AlarmManager延迟启动 |
| 进程安全 | 即使进程被杀死也能唤醒 |

**支持的广播**:
- `android.intent.action.BOOT_COMPLETED`
- `android.intent.action.QUICKBOOT_POWERON`
- `com.htc.intent.action.QUICKBOOT_POWERON`

## 四、工具类详解

### 4.1 LogUtils (日志工具)

| 方法 | 说明 |
|------|------|
| d/i/w/e(String format, Object... args) | 可变参数日志输出 |
| logToFile(String message) | 写入日志文件 |
| getLogFilePath() | 获取日志文件路径 |

**特点**: 
- 统一TAG: "EmptyScreen"
- 支持可变参数格式化
- 日志写入外部存储文件

### 4.2 PrefsManager (配置管理)

| 配置项 | 默认值 | 范围 | 说明 |
|--------|--------|------|------|
| URL | https://www.baidu.com | - | 加载的网页地址 |
| 开机延迟 | 10秒 | 30-300秒 | 开机后延迟启动应用 |
| 前台延迟 | 30秒 | 30-500秒 | 后台后延迟拉起应用 |
| 内存清理开关 | 开启 | - | 是否启用自动清理 |
| 清理阈值 | 80% | 50-95% | 触发清理的内存百分比 |
| 清理间隔 | 60秒 | 30-300秒 | 内存检查间隔 |

### 4.3 MemoryCleaner (内存清理)

| 方法 | 功能 |
|------|------|
| cleanMemory(Context) | 执行内存清理 |
| shouldCleanMemory(Context, int) | 判断是否需要清理 |
| getCleanLogs() | 获取清理日志列表 |
| getLatestCleanLog() | 获取最近清理日志 |

**清理策略**:
1. 应用缓存清理 (内部/外部缓存目录)
2. WebView缓存清理 (clearCache/clearHistory/freeMemory)
3. Dalvik GC (System.gc)
4. Runtime GC (Runtime.getRuntime().gc)

### 4.4 NetworkUtils (网络工具)

| 方法 | 功能 |
|------|------|
| isNetworkConnected(Context) | 检查网络是否连接 |
| getNetworkTypeName(Context) | 获取网络类型名称 |
| getSSID(Context) | 获取WiFi名称 |
| getLocalIpAddress(Context) | 获取本地IP地址 |

### 4.5 StorageUtils (存储工具)

| 方法 | 功能 |
|------|------|
| getStorageDevices(Context) | 获取所有存储设备列表 |
| isVideoFile(String) | 判断是否为视频文件 |

**支持的视频格式**: mp4, mkv, avi, mov, wmv, flv, webm, m4v, 3gp, ts

### 4.6 FocusUtils (TV焦点工具)

| 方法 | 功能 |
|------|------|
| applyFocusAnimation(View, boolean) | 应用焦点动画(放大1.05倍) |
| scrollToFocusedView(ScrollView, View) | 滚动到焦点视图 |
| setupTVFocus(View) | 设置视图可获取焦点 |

## 五、电视遥控器支持

### 5.1 焦点导航

| 按键 | 功能 |
|------|------|
| 方向键上 | 焦点移至上一个可交互元素 |
| 方向键下 | 焦点移至下一个可交互元素 |
| 方向键左 | 焦点移至左侧元素/快退 |
| 方向键右 | 焦点移至右侧元素/快进 |
| 确认键 | 点击当前焦点元素 |
| 返回键 | 返回上一页/退出应用 |
| 菜单键 | 打开存储选择/切换循环模式 |

### 5.2 焦点视觉反馈

| 状态 | 效果 |
|------|------|
| 获得焦点 | 元素放大1.05倍 |
| 失去焦点 | 恢复原始状态 |

### 5.3 AndroidManifest配置

```xml
<uses-feature android:name="android.software.leanback" android:required="false" />
<uses-feature android:name="android.hardware.type.tv" android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />

<intent-filter>
    <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
</intent-filter>
```

## 六、系统桌面模式

应用可配置为系统桌面应用，支持按HOME键返回应用。

### 6.1 AndroidManifest配置

```xml
<intent-filter android:priority="100">
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

### 6.2 Activity属性

```xml
android:launchMode="singleTask"
android:stateNotNeeded="true"
```

## 七、权限清单

| 权限 | 用途 | 必需 |
|------|------|------|
| INTERNET | 网络访问 | 是 |
| ACCESS_NETWORK_STATE | 网络状态检测 | 是 |
| ACCESS_WIFI_STATE | WiFi信息获取 | 是 |
| RECEIVE_BOOT_COMPLETED | 开机广播接收 | 是 |
| FOREGROUND_SERVICE | 前台服务 | 是 |
| FOREGROUND_SERVICE_MEDIA_PLAYBACK | 媒体播放类型前台服务 | 是 |
| WAKE_LOCK | 保持唤醒 | 是 |
| READ_EXTERNAL_STORAGE | 读取存储(Android 9及以下) | 否 |
| READ_MEDIA_VIDEO | 读取视频文件(Android 13+) | 否 |
| MANAGE_EXTERNAL_STORAGE | 完全存储访问(Android 11+) | 否 |
| SET_WALLPAPER | 设置壁纸 | 否 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 请求忽略电池优化 | 否 |

## 八、屏幕适配

| 方向 | 布局目录 | 特点 |
|------|----------|------|
| 横屏 | layout-land/ | 固定宽度800dp居中，网络+内存并排 |
| 竖屏 | layout-port/ | 全宽布局，顺序排列 |
| 默认 | layout/ | 同竖屏布局 |

## 九、UI设计规范

### 9.1 颜色系统

| 用途 | 颜色值 |
|------|--------|
| 背景 | #1A1A1A |
| 卡片背景 | #2A2A2A |
| 输入框背景 | #3A3A3A |
| 主色调 | #4CAF50 (绿色) |
| 次要色调 | #2196F3 (蓝色) |
| 警告色 | #FF9800 (橙色) |
| 错误色 | #FF5252 (红色) |
| 主文字 | #FFFFFF |
| 次要文字 | #AAAAAA |
| 辅助文字 | #888888 |

### 9.2 尺寸规范

| 元素 | 尺寸 |
|------|------|
| 按钮高度 | 48dp |
| 输入框高度 | 48dp |
| 图标按钮 | 48dp |
| 卡片圆角 | 12dp |
| 输入框圆角 | 8dp |
| 卡片内边距 | 16-20dp |
| 元素间距 | 16dp |

## 十、日志系统

### 10.1 日志格式

```
[EmptyScreen] [ClassName] Message
```

### 10.2 日志级别

| 级别 | 方法 | 用途 |
|------|------|------|
| DEBUG | d() | 调试信息 |
| INFO | i() | 一般信息 |
| WARN | w() | 警告信息 |
| ERROR | e() | 错误信息 |

### 10.3 日志文件

- 路径: `/storage/emulated/0/Android/data/com.haier.emptyscreen/files/logs/`
- 格式: `log_yyyyMMdd.txt`
- 单文件最大: 5MB

## 十一、兼容性说明

### 11.1 Android版本兼容

| 版本 | 兼容性 | 特殊处理 |
|------|--------|----------|
| Android 5.0-6.0 | ✅ 完全兼容 | 无 |
| Android 7.0-8.1 | ✅ 完全兼容 | 通知渠道 |
| Android 9 | ✅ 完全兼容 | 网络安全配置 |
| Android 10 | ✅ 完全兼容 | 存储权限变更 |
| Android 11+ | ✅ 完全兼容 | MANAGE_EXTERNAL_STORAGE |
| Android 12+ | ✅ 完全兼容 | 前台服务类型 |

### 11.2 后台启动Activity限制

| Android版本 | 后台启动 | 解决方案 |
|-------------|----------|----------|
| Android 9及以下 | ✅ 无限制 | - |
| Android 10-11 | ⚠️ 部分限制 | 前台服务可启动 |
| Android 12+ | ❌ 严格限制 | 需用户交互 |

## 十二、更新日志

| 日期 | 版本 | 更新内容 |
|------|------|----------|
| 2026-03-14 | 1.0 | 初始版本，实现WebView全屏展示和设置页面 |
| 2026-03-14 | 1.0 | 添加内存占用显示功能 |
| 2026-03-14 | 1.0 | 实现屏幕方向自动适配功能 |
| 2026-03-14 | 1.0 | 添加电视遥控器焦点导航支持 |
| 2026-03-14 | 1.0 | 重新设计设置页面布局，网页设置置顶，优化对齐 |
| 2026-03-14 | 1.0 | 添加视频播放功能，支持本地/网络视频 |
| 2026-03-14 | 1.0 | 添加开机自启功能，使用AlarmManager延迟启动 |
| 2026-03-14 | 1.0 | 添加前台保活服务，后台自动拉起首页 |
| 2026-03-14 | 1.0 | 统一日志TAG为"EmptyScreen"，支持可变参数 |
| 2026-03-14 | 1.0 | 配置为系统桌面应用，支持HOME键返回 |
| 2026-03-14 | 1.0 | 添加自动内存清理功能，支持阈值/间隔配置 |
| 2026-03-14 | 1.0 | 优化TV遥控器焦点处理，所有控件可操作 |
| 2026-03-14 | 1.0 | 新增WebViewPerformanceManager，优化复杂网页性能 |
| 2026-03-14 | 1.0 | 修复内存泄漏问题，完善资源释放机制 |
| 2026-03-14 | 1.0 | 创建隐患处理计划文档，建立长期监控机制 |
| 2026-03-16 | 1.0 | 新增LauncherActivity启动页，支持3秒延时跳转和内存监控重启功能 |

---

## 十三、相关文档

| 文档 | 说明 |
|------|------|
| [ISSUE_PLAN.md](./ISSUE_PLAN.md) | Android端隐患处理计划 |
| [WebViewPerformanceManager.java](./app/src/main/java/com/haier/emptyscreen/webview/WebViewPerformanceManager.java) | WebView性能管理类 |

---

> **文档维护说明:** 新增功能或逻辑变更后，请同步更新本文档对应章节。
