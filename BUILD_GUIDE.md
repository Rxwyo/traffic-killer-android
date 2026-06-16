# 流量杀手 Android 版 - 构建指南

## 方案说明

本项目提供两种 Android 版本构建方式：

### 方案一：Android Studio 项目（完整功能）
- 位置：`traffic-killer-android/`
- 功能：原生 Android 应用，支持后台下载、悬浮窗
- 构建方式：用 Android Studio 打开项目，点击 Build

### 方案二：PWA（渐进式 Web 应用）
- 位置：`traffic-killer-pwa/`
- 功能：WebView 包装的 Web 版，可"安装"到手机桌面
- 构建方式：无需构建，直接用浏览器打开 HTML 文件

---

## 方案一：Android Studio 构建步骤

### 1. 安装 Android Studio
下载地址：https://developer.android.com/studio
（国内镜像：https://developer.android.google.cn/studio）

### 2. 打开项目
1. 启动 Android Studio
2. 选择 "Open an existing project"
3. 选择 `traffic-killer-android/` 文件夹

### 3. 等待 Gradle 同步
- Android Studio 会自动下载 Gradle 和必要的 SDK 组件
- 首次同步可能需要 10-30 分钟（取决于网络）

### 4. 构建 APK
1. 点击菜单 **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. 等待构建完成
3. APK 位置：`app/build/outputs/apk/debug/app-debug.apk`

### 5. 安装到手机
1. 将 APK 文件复制到手机
2. 在手机上允许"安装未知来源应用"
3. 点击 APK 文件安装

---

## 方案二：PWA 版本（无需 Android SDK）

### 使用方法
1. 将 `traffic-killer-pwa/` 文件夹复制到手机
2. 用手机浏览器打开 `index.html`
3. 在浏览器菜单中选择"添加到主屏幕"
4. 即可像原生应用一样使用

### 优点
- 无需安装 Android Studio
- 无需构建
- 跨平台（Android、iOS、Windows 都可用）

---

## 常见问题

### Q: 构建失败怎么办？
A: 
1. 检查网络（需要访问 Google 服务）
2. 尝试使用国内镜像：在 `build.gradle.kts` 中添加：
```kotlin
repositories {
    maven { url 'https://maven.aliyun.com/repository/public' }
    maven { url 'https://maven.aliyun.com/repository/google' }
    google()
    mavenCentral()
}
```

### Q: 找不到设备怎么办？
A:
1. 手机开启"开发者选项"（连续点击"版本号"7次）
2. 开启"USB 调试"
3. 用 USB 连接电脑
4. 在 Android Studio 中选择设备

### Q: 悬浮窗不显示？
A:
1. 在手机设置中允许应用"显示在其他应用上层"
2. 通常在"应用管理 → 特殊访问权限 → 显示在其他应用上层"

---

## 项目结构

```
traffic-killer-android/
├── app/
│   ├── src/main/
│   │   ├── java/com/traffickiller/
│   │   │   ├── MainActivity.java       # 主界面
│   │   │   └── DownloadService.java    # 后台下载服务
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml  # 主界面布局
│   │   │   │   └── layout_float.xml   # 悬浮窗布局
│   │   │   └── values/
│   │   │       └── strings.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 技术支持

如有问题，请检查：
1. JDK 版本（需要 17+）
2. Android SDK 版本（需要 34+）
3. Gradle 版本（需要 8.5+）

---

## 免责声明

本应用仅供测试网络设备稳定性使用，请遵守当地法律法规，勿用于非法用途。
