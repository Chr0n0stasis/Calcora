# Calcora for iOS

这是一个非破坏性的 Compose Multiplatform iOS 宿主。Android 的 `app` 模块保持独立；
iOS 共享模块直接把 `app/src/main/java` 中可复用的 Compose/Graphics、数学排版和业务源码
加入 iOS source set，仅替换 Activity、JNI、偏好存储和系统 API。

## 使用

1. 在 macOS 上安装 Xcode 16、JDK 17+、CMake。
2. 用 Xcode 打开 `iosApp/CalcoraIOS.xcodeproj`。
3. 选择 iPhone 模拟器或设备，运行 `CalcoraIOS` scheme。

Xcode 的首个 Build Phase 会调用根目录的 Gradle wrapper。Gradle 随后：

- 同步既有 Android strings、IBM 3270 字体和 Giac 离线帮助资源；
- 为当前 iOS SDK/架构从同一份 Giac/libtommath/C++ 引擎源码生成静态库；
- 构建并嵌入 `CalcoraShared.framework`。

项目按要求关闭了签名；如需真机安装，再自行设置 Team 和签名即可。
