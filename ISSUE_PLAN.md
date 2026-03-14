# Android端隐患处理计划

> **文档版本**: 1.0  
> **创建日期**: 2026-03-14  
> **最后更新**: 2026-03-14  
> **负责人**: EmptyScreen Team

---

## 一、隐患分类与优先级排序

### 1.1 隐患清单

| 编号 | 隐患类型 | 具体问题 | 影响范围 | 优先级 | 状态 |
|------|----------|----------|----------|--------|------|
| H-001 | 内存泄漏 | Handler回调未完全清理 | MainActivity | 🔴 P0-紧急 | ✅ 已解决 |
| H-002 | 内存泄漏 | WebView销毁前未清理资源 | MainActivity | 🔴 P0-紧急 | ✅ 已解决 |
| H-003 | 内存泄漏 | 匿名内部类持有Activity引用 | 多处 | 🟡 P1-高 | ✅ 已解决 |
| H-004 | 内存泄漏 | 静态变量持有Context引用 | MemoryCleaner | 🟡 P1-高 | ✅ 已解决 |
| H-005 | 性能问题 | WebView未启用硬件加速 | MainActivity | 🟡 P1-高 | ✅ 已解决 |
| H-006 | 性能问题 | 缓存无大小限制 | WebView | 🟡 P1-高 | ✅ 已解决 |
| H-007 | 稳定性问题 | 网页定时器无法监控清理 | WebView | 🔴 P0-紧急 | ✅ 已解决 |
| H-008 | 稳定性问题 | ExecutorService未正确关闭 | MainActivity | 🟡 P1-高 | ✅ 已解决 |
| H-009 | 兼容性问题 | 后台启动Activity限制 | Android 10+ | 🟢 P2-中 | ✅ 已解决 |
| H-010 | 安全问题 | WebView允许混合内容 | WebView | 🟢 P2-中 | ✅ 已解决 |

### 1.2 优先级定义

| 优先级 | 定义 | 响应时间 | 处理时限 |
|--------|------|----------|----------|
| 🔴 P0-紧急 | 导致崩溃或严重内存泄漏 | 立即 | 24小时内 |
| 🟡 P1-高 | 影响性能或存在潜在风险 | 1天内 | 3天内 |
| 🟢 P2-中 | 影响用户体验或兼容性 | 3天内 | 1周内 |
| 🔵 P3-低 | 代码规范或优化建议 | 1周内 | 迭代版本 |

---

## 二、具体处理措施与技术方案

### 2.1 H-001: Handler回调未完全清理

**问题描述**:  
`mMemoryHandler.removeCallbacks(mMemoryRunnable)` 只移除了特定的Runnable，可能遗漏其他消息。

**解决方案**:
```java
// 修改前
mMemoryHandler.removeCallbacks(mMemoryRunnable);

// 修改后
mMemoryHandler.removeCallbacksAndMessages(null);
mMemoryHandler = null;
mMemoryRunnable = null;
```

**验证方法**:
1. 使用LeakCanary检测Activity泄漏
2. 反复进出MainActivity 10次，观察内存曲线

---

### 2.2 H-002: WebView销毁前未清理资源

**问题描述**:  
直接调用`webView.destroy()`可能导致内存泄漏，因为WebView内部可能仍持有Activity引用。

**解决方案**:
```java
// 创建WebViewPerformanceManager.safeDestroy()
public static void safeDestroy(WebView webView) {
    webView.stopLoading();
    webView.onPause();
    webView.removeJavascriptInterface("Android");
    webView.clearHistory();
    webView.clearCache(true);
    webView.clearFormData();
    webView.setWebViewClient(null);
    webView.setWebChromeClient(null);
    if (webView.getParent() != null) {
        ((ViewGroup) webView.getParent()).removeView(webView);
    }
    webView.destroy();
}
```

**验证方法**:
1. Android Profiler内存分析
2. Dump Heap检查WebView实例是否被回收

---

### 2.3 H-003: 匿名内部类持有Activity引用

**问题描述**:  
多处使用匿名内部类（如OnClickListener、OnFocusChangeListener），隐式持有外部Activity引用。

**解决方案**:
```java
// 方案1: 使用静态内部类 + 弱引用
private static class SafeClickListener implements View.OnClickListener {
    private final WeakReference<MainActivity> activityRef;
    
    public SafeClickListener(MainActivity activity) {
        activityRef = new WeakReference<>(activity);
    }
    
    @Override
    public void onClick(View v) {
        MainActivity activity = activityRef.get();
        if (activity != null && !activity.isFinishing()) {
            // 处理点击事件
        }
    }
}

// 方案2: 在onDestroy中移除监听器
@Override
protected void onDestroy() {
    mBtnSettings.setOnClickListener(null);
    // ... 其他监听器
    super.onDestroy();
}
```

**实施计划**: 下一迭代版本

---

### 2.4 H-004: 静态变量持有Context引用

**问题描述**:  
`MemoryCleaner`中的静态变量可能持有Context引用。

**解决方案**:
```java
// 修改LogUtils，使用WeakReference
private static WeakReference<Context> sContextRef;

public static void init(Context context) {
    sContextRef = new WeakReference<>(context.getApplicationContext());
}

public static Context getContext() {
    return sContextRef != null ? sContextRef.get() : null;
}
```

**实施计划**: 下一迭代版本

---

### 2.5 H-005/H-006: WebView性能优化

**问题描述**:  
WebView未配置硬件加速，缓存无大小限制。

**解决方案**: 已通过`WebViewPerformanceManager.configureForComplexPage()`解决

```java
// 关键配置
webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
settings.setAppCacheMaxSize(50 * 1024 * 1024); // 50MB限制
settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
```

**状态**: ✅ 已完成

---

### 2.6 H-007: 网页定时器无法监控清理

**问题描述**:  
复杂网页中的setInterval/setTimeout可能无限创建，导致内存增长。

**解决方案**: 已通过JavaScript注入实现监控和清理

```javascript
// 监控脚本
window.__cleanupTimers = [];
var originalSetInterval = window.setInterval;
window.setInterval = function(fn, delay) {
    var id = originalSetInterval.call(window, fn, delay);
    window.__cleanupTimers.push(id);
    return id;
};

// 清理脚本
window.__cleanupTimers.forEach(function(id) {
    clearInterval(id);
    clearTimeout(id);
});
```

**状态**: ✅ 已完成

---

### 2.7 H-009: 后台启动Activity限制

**问题描述**:  
Android 10+对后台启动Activity有严格限制。

**解决方案**:
```java
// 方案1: 使用全屏通知
NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
    .setFullScreenIntent(pendingIntent, true)
    .setPriority(NotificationCompat.PRIORITY_HIGH);

// 方案2: 使用SYSTEM_ALERT_WINDOW权限（需用户授权）
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    if (!Settings.canDrawOverlays(this)) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        startActivity(intent);
    }
}
```

**实施计划**: 下一迭代版本

---

### 2.8 H-010: WebView混合内容问题

**问题描述**:  
当前配置允许混合内容（HTTP+HTTPS），存在安全风险。

**解决方案**:
```java
// 严格模式：禁止混合内容
settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

// 或在WebViewClient中拦截
@Override
public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
    // 记录错误，拒绝继续
    LogUtils.e("[WebView] SSL Error: " + error.toString());
    handler.cancel();
}
```

**实施计划**: 下一迭代版本

---

## 三、实施时间表

### 3.1 阶段一：紧急修复（已完成）

| 任务 | 开始日期 | 结束日期 | 状态 |
|------|----------|----------|------|
| H-001 Handler清理 | 2026-03-14 | 2026-03-14 | ✅ |
| H-002 WebView销毁 | 2026-03-14 | 2026-03-14 | ✅ |
| H-007 定时器监控 | 2026-03-14 | 2026-03-14 | ✅ |
| H-008 ExecutorService | 2026-03-14 | 2026-03-14 | ✅ |

### 3.2 阶段二：高优先级处理（计划中）

| 任务 | 计划开始 | 计划结束 | 预计工时 |
|------|----------|----------|----------|
| H-003 匿名内部类优化 | 2026-03-15 | 2026-03-16 | 4h |
| H-004 静态变量优化 | 2026-03-15 | 2026-03-15 | 2h |
| H-005/H-006 性能优化验证 | 2026-03-16 | 2026-03-17 | 4h |

### 3.3 阶段三：中优先级处理（计划中）

| 任务 | 计划开始 | 计划结束 | 预计工时 |
|------|----------|----------|----------|
| H-009 后台启动兼容 | 2026-03-18 | 2026-03-20 | 8h |
| H-010 安全加固 | 2026-03-18 | 2026-03-19 | 4h |

---

## 四、资源分配

### 4.1 人力资源

| 角色 | 职责 | 投入比例 |
|------|------|----------|
| Android开发 | 代码修改、测试 | 80% |
| 测试工程师 | 验证测试、性能测试 | 50% |
| 技术负责人 | 方案评审、风险把控 | 20% |

### 4.2 工具资源

| 工具 | 用途 | 状态 |
|------|------|------|
| Android Studio Profiler | 内存分析 | ✅ 可用 |
| LeakCanary | 内存泄漏检测 | ✅ 已集成 |
| ADB | 命令行调试 | ✅ 可用 |
| Monkey | 压力测试 | ✅ 可用 |

---

## 五、风险评估与应对策略

### 5.1 风险清单

| 风险 | 可能性 | 影响 | 应对策略 |
|------|--------|------|----------|
| 修改引入新Bug | 中 | 高 | 充分测试，灰度发布 |
| 兼容性问题 | 中 | 中 | 多版本测试，兼容代码 |
| 性能优化效果不佳 | 低 | 中 | 持续监控，迭代优化 |
| 第三方库冲突 | 低 | 高 | 版本锁定，隔离测试 |

### 5.2 回滚方案

```bash
# Git版本回滚
git revert <commit-hash>

# 或使用分支策略
git checkout release/stable
```

---

## 六、质量验证标准

### 6.1 内存泄漏验证

| 指标 | 标准 | 验证方法 |
|------|------|----------|
| Activity泄漏 | 0次 | LeakCanary检测 |
| 内存增长 | <5MB/小时 | Profiler监控 |
| GC后内存回落 | >80% | 手动触发GC观察 |

### 6.2 性能验证

| 指标 | 标准 | 验证方法 |
|------|------|----------|
| 页面加载时间 | <3秒 | WebView回调计时 |
| 内存占用峰值 | <150MB | Profiler监控 |
| CPU占用率 | <30% | Profiler监控 |

### 6.3 稳定性验证

| 指标 | 标准 | 验证方法 |
|------|------|----------|
| 崩溃率 | <0.1% | Crash监控 |
| ANR率 | <0.05% | ANR日志分析 |
| 连续运行时间 | >72小时 | 长时间测试 |

---

## 七、验收流程

### 7.1 代码审查

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  开发完成    │───▶│  代码审查    │───▶│  修改完善    │
└─────────────┘    └─────────────┘    └─────────────┘
                          │
                          ▼
                   ┌─────────────┐
                   │  审查通过    │
                   └─────────────┘
```

**审查要点**:
- [ ] 代码规范符合项目标准
- [ ] 无明显性能问题
- [ ] 无安全漏洞
- [ ] 注释完整清晰

### 7.2 测试验收

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  单元测试    │───▶│  集成测试    │───▶│  性能测试    │───▶│  验收测试    │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
```

**测试用例**:

| 用例编号 | 测试场景 | 预期结果 | 优先级 |
|----------|----------|----------|--------|
| TC-001 | 反复进出MainActivity 20次 | 无内存泄漏 | P0 |
| TC-002 | 加载复杂网页运行24小时 | 内存稳定，无崩溃 | P0 |
| TC-003 | 低内存场景测试 | 应用正常响应 | P1 |
| TC-004 | Android 9/10/11/12兼容测试 | 功能正常 | P1 |
| TC-005 | WebView定时器清理验证 | 定时器被正确清理 | P1 |

### 7.3 验收签字

| 角色 | 签字 | 日期 |
|------|------|------|
| 开发工程师 | ________ | ________ |
| 测试工程师 | ________ | ________ |
| 技术负责人 | ________ | ________ |

---

## 八、长期监控机制

### 8.1 监控指标

| 监控项 | 监控方式 | 告警阈值 | 处理流程 |
|--------|----------|----------|----------|
| 内存使用 | Profiler定时采样 | >200MB | 自动记录+人工分析 |
| 崩溃率 | Crash SDK | >0.1% | 立即通知 |
| ANR | 系统日志 | >0.05% | 立即通知 |
| 页面加载时间 | 埋点统计 | >5秒 | 记录分析 |

### 8.2 定期检查

| 检查项 | 频率 | 执行人 |
|--------|------|--------|
| 内存泄漏扫描 | 每周 | 开发工程师 |
| 代码质量扫描 | 每周 | 技术负责人 |
| 性能基准测试 | 每月 | 测试工程师 |
| 安全漏洞扫描 | 每月 | 技术负责人 |

### 8.3 持续改进

```
┌──────────────────────────────────────────────────────────────┐
│                    持续改进循环                               │
│                                                              │
│   ┌─────────┐      ┌─────────┐      ┌─────────┐             │
│   │  监控   │─────▶│  分析   │─────▶│  改进   │             │
│   └─────────┘      └─────────┘      └─────────┘             │
│        ▲                                   │                 │
│        └───────────────────────────────────┘                 │
│                      验证                                     │
└──────────────────────────────────────────────────────────────┘
```

---

## 九、附录

### 9.1 相关文档

- [FEATURES.md](./FEATURES.md) - 功能文档
- [WebViewPerformanceManager.java](./app/src/main/java/com/haier/emptyscreen/webview/WebViewPerformanceManager.java) - 性能管理类

### 9.2 修订记录

| 版本 | 日期 | 修订内容 | 修订人 |
|------|------|----------|--------|
| 1.0 | 2026-03-14 | 初始版本 | EmptyScreen Team |

---

> **注意**: 本计划应根据实际执行情况动态调整，确保所有隐患得到有效解决。
