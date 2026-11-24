# PM3Droid — Proxmark3 Easy 512KB Android 客户端 (KernelSU Root 直连版)

专为具备临时/永久 Root 权限（KernelSU / Magisk）的 Android 设备打造的 Proxmark3 Easy 512KB 客户端，采用 **云端 NDK 交叉编译 RRG/Iceman 原生核心 + Kotlin / Jetpack Compose 前端** 架构。

## 核心特性
1. **免 `chmod` 进程直连**：通过 Root 管道直接以 `uid=0` 拉起打包在 `jniLibs` 中的 `libproxmark3.so`，绕过 Android 10~16 私有目录 `noexec` 限制与 SELinux `/dev` 访问限制。
2. **适配高通 `dwc3` 控制器枚举延迟**：针对 PM3 Easy 插入手机后约 4.5 秒的 Bootloader -> 冰人固件切换延迟，内置 `su` 轮询探测机制，节点就绪后自动连接。
3. **双轨交互界面**：
   - **工作台 (Dashboard)**：`hw tune` 天线电压体检、HF/LF 一键寻卡、结果自动解析高亮卡片、M1 全自动破解 (`autopwn`)、写入魔术卡 (`cload`)、一键卡片模拟 (`sim`)。
   - **原生终端 (Terminal)**：完整冰人命令行交互、ANSI 转义字符过滤与语法高亮、常用指令快捷胶囊栏、输入历史切换。
   - **卡包仓库 (Dumps)**：统一管理本地 `.bin` / `.json` 卡片数据，支持 16 进制扇区染色预览（厂商块/数据块/控制位与密钥区）及一键写卡/模拟。

---

## 零本地环境云端编译步骤（GitHub Actions）

无需在电脑上安装 Android Studio 或 NDK，直接通过浏览器操作即可出包：

1. 在 GitHub 上新建一个私有或公开仓库（例如 `PM3Droid`）。
2. 将本压缩包解压后的所有文件上传到该仓库根目录（确保 `.github/workflows/build_apk.yml` 路径完整）。
3. 进入仓库顶部的 **Actions** 标签页，选择 **Build PM3Droid APK (ARM64)**，点击 **Run workflow** 触发云端构建。
4. 等待约 5~8 分钟（首次构建会自动交叉编译 `readline`、`jansson`、`bzip2` 与最新版 `RfidResearchGroup/proxmark3` `arm64-v8a` 核心），构建完成后在页面底部的 **Artifacts** 中下载 `PM3Droid-arm64-debug` 解压安装即可。

---

## 手机端使用说明

1. 安装 APK 后，打开 **KernelSU 管理器**，在超级用户列表中找到 **PM3Droid** 并开启授权（**注意**：如果你使用了 HMA 隐藏应用列表或 KernelSU 默认卸载模块功能，请勿将 PM3Droid 加入隔离名单，否则无法调用 `su`）。
2. 打开手机系统设置中的 **OTG 连接** 开关。
3. 使用带 5.1kΩ 下拉电阻的 OTG 转接头连接 PM3 Easy 512KB，App 顶部状态灯会从 **黄灯闪烁（等待节点枚举，约 4.5s）** 自动变为 **绿灯常亮（已就绪）**。
