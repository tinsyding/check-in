# 班级点名签到 (Check-in) 📱

[![Android Release](https://github.com/tinsyding/check-in/actions/workflows/android-release.yml/badge.svg)](https://github.com/tinsyding/check-in/actions/workflows/android-release.yml)
[![Latest Release](https://img.shields.io/github/v/release/tinsyding/check-in?color=blue&label=release)](https://github.com/tinsyding/check-in/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-orange.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.09.00-4285F4.svg)](https://developer.android.com/jetpack/compose)

现代、高效、轻量级的 Android 课堂与培训签到管理工具。支持学生名单智能匹配、一键全到/补录、席卡 PDF 生成与局域网网络打印、考勤长图分享、以及内置教务系统网页下载名单等丰富功能。

---

## 🌟 核心功能特性

### 1. 📋 智能考勤点名
- **班级学生卡片网格**：直观展示学生学号、姓名与考勤状态（未到/已到/缺勤/迟到/请假）。
- **一键全到 / 快速切换**：支持“全到/取消全到”快速切换，单指点按快速标记考勤。
- **课表时间智能匹配**：根据当前星期与上下课时间段，自动匹配并推荐当前上课班级。
- **人员动态调度**：支持手动加人、临时调班、调出学生一键调回、修改学生备注等。
- **考勤状态持久化**：点名状态即时保存至 DataStore，退出应用或切换页面不丢失。
- **全校状态一键清空**：支持全校或当前班级考勤重置，便于每日点名重开。

### 2. 🪪 A4 席卡生成与局域网网络打印
- **A4 折叠席卡 PDF**：自动按学生名单排版折叠立牌席卡，内置大字书法毛笔字体（`cursive.ttf`）。
- **局域网直接打印**：支持 **FTP** 及 **LPR (Line Printer Remote / 515端口)** 协议直接向局域网激光/网络打印机发送 PDF 打印任务，免除驱动烦恼。
- **系统打印与分享**：可直接调起 Android 打印服务或通过微信/邮件分享席卡 PDF 文件。

### 3. 📊 考勤汇总长图导出与分享
- **考勤长图自动渲染**：基于原生 Canvas 高清渲染当前班级考勤汇总表（包含应到、实到、缺勤、迟到、请假统计与人员明细）。
- **一键存相册与分享**：一键保存到系统相册，或通过微信/企业微信一键分享点名报告。

### 4. 📜 历史记录与快照状态还原
- **考勤归档**：每次导出长图自动记录历史档案。
- **快照一键恢复**：在历史记录中可“一键恢复”指定时刻的签到快照回主界面，支持查看历史长图与记录管理。

### 5. 🌐 内置教务浏览器与名单导入
- **内置 Web 浏览器**：支持在应用内直接登录教务/培训后台系统，一键下载并自动解析学生名单 CSV。
- **本地 CSV 灵活导入**：支持标准 CSV 格式导入学生名单（包含班级代号、学号、姓名、星期与时间等）。

---

## 🛠️ 技术架构与选型

本项目基于现代 Android 架构设计规范构建：

- **编程语言**：100% [Kotlin](https://kotlinlang.org/)
- **UI 框架**：[Jetpack Compose](https://developer.android.com/jetpack/compose) + Material Design 3 (M3)
- **架构模式**：MVVM (Model-View-ViewModel) + 单向数据流 (UDF)
- **导航组件**：[Navigation Compose](https://developer.android.com/jetpack/compose/navigation)
- **数据存储**：[Jetpack DataStore (Preferences)](https://developer.android.com/topic/libraries/architecture/datastore) 轻量持久化
- **图形与文档生成**：
  - `android.graphics.pdf.PdfDocument` 生成矢量席卡
  - `android.graphics.Canvas` 高清长图绘制
- **网络协议**：基于标准 Socket 实现网络打印机 LPR (RFC 1179) 与 FTP 协议
- **构建管理**：Gradle Version Catalog (`libs.versions.toml`) + Kotlin DSL (`build.gradle.kts`)
- **CI/CD 流水线**：GitHub Actions 全自动化构建、版本递增、APK 签名与 GitHub Release 发布

---

## 📁 项目目录结构

```text
check-in/
├── .github/
│   └── workflows/
│       └── android-release.yml   # GitHub Actions 自动化构建与发布工作流
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── assets/fonts/     # 字体资源 (如书法字体 cursive.ttf)
│   │       ├── java/com/jf/checkin/
│   │       │   ├── CheckInApplication.kt    # 全局 Application
│   │       │   ├── data/
│   │       │   │   ├── model/               # 数据实体 (Student, AttendanceRecord 等)
│   │       │   │   ├── parser/              # CSV 学生名单解析器
│   │       │   │   └── repository/          # 数据仓库 (DataStore 存储与学生管理)
│   │       │   ├── logic/                   # 业务逻辑 (PDF生成, 长图生成, 网络打印, 时间匹配)
│   │       │   └── ui/
│   │       │       ├── MainActivity.kt      # 宿主 Activity 与导航入口
│   │       │       ├── rollcall/            # 点名主界面 (RollCallScreen, ViewModel, StudentCard)
│   │       │       ├── history/             # 点名历史界面 (HistoryScreen)
│   │       │       ├── webview/             # 内置浏览器界面 (JfBrowserScreen)
│   │       │       ├── settings/            # 设置与导入界面 (SettingsScreen)
│   │       │       └── theme/               # Material3 主题与配色
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts          # App 模块配置与依赖
├── gradle/
│   └── libs.versions.toml        # 统一版本依赖清单
├── build.gradle.kts              # 根工程构建脚本
└── settings.gradle.kts           # 模块与仓库源配置
```

---

## 🚀 快速上手与本地构建

### 环境要求
- **Android Studio**：Ladybug (2024.2.1) 或更高版本
- **JDK**：OpenJDK 17
- **Android SDK**：Compile SDK 35，Min SDK 26 (Android 8.0+)

### 命令行编译

1. **克隆代码仓库**：
   ```bash
   git clone git@github.com:tinsyding/check-in.git
   cd check-in
   ```

2. **编译 Debug 版 APK**：
   ```bash
   ./gradlew assembleDebug
   # 输出路径: app/build/outputs/apk/debug/app-debug.apk
   ```

3. **编译 Release 版 APK**：
   ```bash
   ./gradlew assembleRelease
   # 输出路径: app/build/outputs/apk/release/app-release-unsigned.apk
   ```

4. **直接安装到已连接设备**：
   ```bash
   ./gradlew installDebug
   ```

---

## 📦 获取已发布安装包 (APK)

本项目通过 GitHub Actions 实现了持续集成与部署（CI/CD）：
每次推送代码至 `main` 分支时，自动化工作流会自动生成构建产物并发布：

- 访问 [GitHub Releases 页面](https://github.com/tinsyding/check-in/releases) 即可直接下载最新版本的 APK 安装包。

---

## 📄 开源与许可证

本项目采用 [MIT License](LICENSE) 开源。
