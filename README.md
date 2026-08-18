# 手动相册清理

一款简单的 Android 相册清理工具，支持左右滑动手势快速整理照片。

## 功能

1. **选择相册**：进入应用后，会列出手机中的所有相册（文件夹）。
2. **左右滑动清理**：选择一个相册后，会全屏显示照片。
   - **向左滑动**：保留照片，移入保留站
   - **向右滑动**：将照片移入回收站
3. **保留站**：保存你想保留的照片，支持批量选择和删除。
4. **回收站**：被移入回收站的照片不会立即从手机删除，你可以进入回收站统一处理：
   - **彻底删除**：从手机中永久删除这些照片
   - **恢复**：将照片从回收站移出，保留在手机中
5. **批量选择**：保留站和回收站均支持选择模式和全选功能，方便批量操作。

## 如何编译安装

### 方法一：使用 Android Studio（推荐）

1. 安装 [Android Studio](https://developer.android.com/studio)。
2. 打开 Android Studio，选择 **Open**，然后选择本项目文件夹 `ManualAlbumCleanerAndroid`。
3. 等待 Gradle 同步完成（首次打开会自动下载依赖）。
4. 用 USB 连接你的安卓手机，并在手机上开启**开发者选项**和**USB 调试**。
5. 点击 Android Studio 顶部的 **Run** 按钮（绿色三角形），或直接构建 APK：
   - 菜单栏选择 **Build > Build Bundle(s) / APK(s) > Build APK(s)**。
   - 构建完成后，右下角会弹出提示，点击 **locate** 即可找到 APK 文件。
6. 将 APK 文件发送到手机安装即可。

### 方法二：使用命令行（需要本地安装 Android SDK 和 Gradle）

```bash
# 进入项目目录
cd ManualAlbumCleanerAndroid

# Linux/Mac 下执行
./gradlew assembleDebug

# Windows 下执行（需要先下载 gradle wrapper）
gradlew.bat assembleDebug
```

构建成功后，APK 位于：
```
app/build/outputs/apk/debug/app-debug.apk
```

### 方法三：直接安装到已连接的设备

```bash
./gradlew installDebug
```

## 需要的权限

- **读取媒体文件/存储权限**：用于浏览相册中的照片。
- **删除媒体文件**：在 Android 11+ 上，应用会调用系统弹窗请求你确认删除操作，不会直接静默删除。

## 注意事项

- 首次打开应用时，请授予存储权限，否则无法读取照片。
- 回收站中的照片**并未真正删除**，只是被记录在一个列表中。只有点击"彻底删除"后，才会从设备中移除。
- 在 Android 11 (API 30) 及以上系统，点击"彻底删除"时会弹出系统确认框，需要用户手动确认。

## 技术栈

- 语言：Kotlin
- UI：XML Layout + Material Design Components
- 图片加载：Glide
- 最低支持 Android 8.0 (API 26)
