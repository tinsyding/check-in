# 极方考勤点名工具

极方考勤点名工具。支持学生名单导入、课表时间匹配、一键全到/补录、席卡 PDF 生成与局域网打印、考勤长图导出及校管家考勤同步。

## 功能介绍

### 考勤点名
- 网格卡片展示学号、姓名与考勤状态（未到/已到/缺勤/迟到/请假）。
- 支持一键全到与点按快速切换考勤状态。
- 根据当前星期与上下课时间段，自动匹配并推荐当前上课班级。
- 支持手动加人、临时调班、调出学生一键调回及备注修改。
- 点名状态即时保存至本地，退出应用或切换页面不丢失。
- 支持重置当前班级或全校考勤状态。

### 考勤长图与历史快照
- 基于原生 Canvas 渲染班级考勤汇总长图（包含统计数据与人员明细）。
- 支持保存到系统相册及通过微信等应用分享。
- 导出长图时自动生成历史快照，可在历史记录中查看长图并一键恢复指定快照。
- 分享长图后提示是否同步当前班级考勤至校管家（当天已同步班型不重复提示）。

### 名单导入与内置浏览器
- 内置浏览器，支持直接在应用内登录教务后台并下载解析学生名单 CSV。
- 支持从本地导入标准格式的 CSV 学生名单（含班级、学号、姓名、上课时间等）。

### 校管家考勤同步
- 点击点名页顶部同步按钮，自动登录校管家并匹配当日课程。
- 提交前提供预览弹窗，核对出勤统计、未匹配学员与课消金额。
- 支持按「班级号+班型」匹配 α1/β1/β2/β3 课程（含线上课）。
- 匹配学员统一按出勤计费提交，自动跳过已点名课程，不触发微信/短信通知。
- 支持每日 18:00 本地定时检查（基于 WorkManager），未完成同步的班级会通过系统通知提醒，无需外部推送服务。

## 技术栈

- **开发语言**：[Kotlin](https://kotlinlang.org/)
- **UI 框架**：[Jetpack Compose](https://developer.android.com/jetpack/compose) + Material 3
- **架构模式**：MVVM + 单向数据流 (UDF)
- **导航组件**：[Navigation Compose](https://developer.android.com/jetpack/compose/navigation)
- **本地存储**：SharedPreferences（配置与临时状态）+ JSON 文件（历史记录归档）
- **后台任务**：WorkManager（每日同步提醒）
- **PDF 与长图**：`android.graphics.pdf.PdfDocument` / `android.graphics.Canvas`
- **网络与同步**：
  - 原生 Socket 实现 LPR (RFC 1179) 及 FTP 打印协议
  - 原生 HTTPS 客户端对接校管家 API
- **构建系统**：Gradle Version Catalog (`libs.versions.toml`) + Kotlin DSL
- **CI/CD**：GitHub Actions 自动构建与 Release 发布

## 目录结构

```text
check-in/
├── .github/
│   └── workflows/
│       └── android-release.yml   # GitHub Actions 构建与发布工作流
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── assets/fonts/     # 字体资源 (如书法字体 cursive.ttf)
│   │       ├── java/com/jf/checkin/
│   │       │   ├── CheckInApplication.kt    # 全局 Application
│   │       │   ├── data/
│   │       │   │   ├── model/               # 数据实体 (Student, AttendanceRecord 等)
│   │       │   │   ├── parser/              # CSV 学生名单解析器
│   │       │   │   ├── remote/              # 校管家 (TMS) API 客户端
│   │       │   │   └── repository/          # 数据仓库 (本地存储与学生管理)
│   │       │   ├── logic/                   # 业务逻辑 (PDF 生成、长图绘制、网络打印、时间匹配)
│   │       │   └── ui/
│   │       │       ├── MainActivity.kt      # 宿主 Activity 与导航入口
│   │       │       ├── rollcall/            # 点名主界面
│   │       │       ├── history/             # 历史记录界面
│   │       │       ├── webview/             # 内置浏览器界面
│   │       │       ├── settings/            # 设置与导入界面
│   │       │       └── theme/               # Material 3 主题配置
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts          # App 模块配置与依赖
├── gradle/
│   └── libs.versions.toml        # 统一版本依赖清单
├── build.gradle.kts              # 根工程构建脚本
└── settings.gradle.kts           # 模块与仓库配置
```

## 本地构建

### 环境要求

- Android Studio Ladybug (2024.2.1) 或更高版本
- JDK 17
- Android SDK：Compile SDK 35，Min SDK 26 (Android 8.0+)

### 编译命令

```bash
# 克隆仓库
git clone git@github.com:tinsyding/check-in.git
cd check-in

# 编译 Debug APK (输出: app/build/outputs/apk/debug/app-debug.apk)
./gradlew assembleDebug

# 编译 Release APK (输出: app/build/outputs/apk/release/app-release-unsigned.apk)
./gradlew assembleRelease

# 安装到已连接设备
./gradlew installDebug
```

## 下载与发布

可在 [Releases](https://github.com/tinsyding/check-in/releases) 页面下载最新构建的 APK 安装包。推送代码至 `main` 分支时会触发 GitHub Actions 自动打包发布。

## 开源协议

本项目基于 [MIT License](LICENSE) 开源。
