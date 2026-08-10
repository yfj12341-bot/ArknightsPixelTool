# ArknightsPixelTool

为《明日方舟》像素画活动准备的 Android 像素画生成器：导入任意图片，经过调整、裁剪和多种采样策略，生成 24×24 到 64×64 的像素画；既可使用活动固定 40 色调色板，也可自动取色或自定义颜色上限。生成完成后还支持直接在网格上手动逐格修改，并一键分享为 PNG。

> 工程名：`ArknightsPixelTool`（GitHub 仓库名）。内部包名仍为 `com.pixelpainter.app` / `com.pixelpainter.core`。

## 功能特性

- **图片调整**：旋转（0–360°）、亮度、对比度，带防抖的实时预览
- **手动 1:1 裁剪**：拖动图片、双指缩放来调整裁剪区域
- **三种调色板模式**：
  - 自动 40 色：从图片自身提取调色板
  - 固定调色板：活动 40 色，编号 1–40，顺序固定
  - 自定义：24–64 偶数画幅，颜色上限 1–256
- **三种下采样方式**：平滑平均、主体色（保留细线边缘）、Box 平均
- **可选 Floyd–Steinberg 抖动**（固定调色板下自动关闭）
- **结果预览**：双指缩放/平移、网格显示与隐藏、加粗中线、色号显示、点击查看色号
- **手动编辑**：使用调色板或自定义颜色（RGB / HSL）逐格修改，支持撤销与重做
- **分享导出**：将生成结果分享为 PNG

## 使用流程

1. 从相册导入图片
2. 按需调整旋转、亮度、对比度
3. 拖动或双指缩放图片，确定 1:1 裁剪区域
4. 选择调色板模式、画幅、颜色上限、下采样方式，点击生成
5. 在结果预览中缩放检查，开启网格或色号辅助
6. 开启编辑模式逐格修改，可撤销/重做
7. 分享 PNG 输出

## 项目结构

```text
ArknightsPixelTool/
├── app/                                  # Android 应用（Jetpack Compose + Material 3）
│   └── src/main/
│       ├── kotlin/com/pixelpainter/app/
│       │   ├── MainActivity.kt           # Activity 入口
│       │   ├── PixelPainterApp.kt        # 主界面与全部交互逻辑
│       │   ├── BitmapImage.kt            # 图片解码与位图转换
│       │   └── ShareSupport.kt           # PNG 分享（FileProvider）
│       ├── res/                          # 图标、主题、文案等资源
│       └── AndroidManifest.xml
├── core/                                 # 纯 Kotlin 算法库（不依赖 Android）
│   └── src/
│       ├── main/kotlin/com/pixelpainter/core/
│       │   ├── PixelArtConverter.kt      # 转换入口（管线编排）
│       │   ├── AutoPalette.kt            # 自动取色（中位切分 + Lab Lloyd 迭代）
│       │   ├── ColorMath.kt              # 颜色空间转换（sRGB / OKLab / Lab）
│       │   ├── Downsample.kt             # 三种下采样算法
│       │   ├── ImageAdjustments.kt       # 旋转 / 亮度 / 对比度
│       │   ├── NearestColorMapper.kt     # 最近色映射 + Floyd–Steinberg 抖动
│       │   ├── Palette.kt                # 调色板模型 + 活动 40 色（SamplePalettes）
│       │   ├── PaletteCodec.kt           # JSON 调色板编解码
│       │   ├── PaletteMatcher.kt         # OKLab 最近色查找
│       │   └── RgbImage.kt               # 基础图像类型
│       └── test/kotlin/...               # 核心算法单元测试
├── gradle/                               # Gradle Wrapper 与版本目录（libs.versions.toml）
├── build.gradle.kts                      # 根构建脚本
├── settings.gradle.kts                   # 工程设置（rootProject.name、模块）
├── gradle.properties                     # Gradle / AndroidX 配置
├── gradlew / gradlew.bat                 # Gradle Wrapper 启动脚本
├── .gitignore
├── LICENSE
└── README.md
```

## 技术要点

- **自动取色**：中位切分（median cut）初始化 + Lab 空间 Lloyd 迭代细化
- **颜色匹配**：OKLab 空间最近色映射
- **下采样**：
  - 平滑平均：区域面积平均
  - 主体色：先映射到调色板，再做块内多数投票，优先保留线条与边缘
  - Box 平均：Pillow 风格面积加权采样
- **抖动**：Floyd–Steinberg 误差扩散
- **图片调整**：纯 Kotlin 实现，不依赖 Android 图形 API
- **界面**：Jetpack Compose + Material 3，全部使用 Kotlin

## 构建

环境要求：

- JDK 17
- Android SDK 35（platform + build-tools 35.0.1）

在项目根目录执行：

```bash
./gradlew :core:test          # 运行核心算法单元测试
./gradlew :app:assembleDebug  # 构建 Debug APK
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 固定 40 色调色板

活动固定调色板按以下顺序编号 1–40，代码位于 `core` 模块的 `Palette.kt`（`SamplePalettes`）：

| 编号 | RGB | 编号 | RGB | 编号 | RGB | 编号 | RGB |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | (34, 34, 34) | 11 | (252, 239, 234) | 21 | (194, 218, 114) | 31 | (90, 69, 157) |
| 2 | (180, 180, 180) | 12 | (251, 246, 232) | 22 | (108, 110, 0) | 32 | (186, 163, 215) |
| 3 | (234, 231, 223) | 13 | (220, 210, 200) | 23 | (170, 139, 82) | 33 | (182, 188, 223) |
| 4 | (255, 255, 255) | 14 | (226, 206, 171) | 24 | (169, 143, 116) | 34 | (169, 172, 190) |
| 5 | (211, 47, 54) | 15 | (213, 99, 34) | 25 | (170, 146, 40) | 35 | (99, 171, 185) |
| 6 | (156, 10, 0) | 16 | (212, 140, 66) | 26 | (63, 43, 18) | 36 | (180, 210, 220) |
| 7 | (214, 12, 74) | 17 | (242, 153, 0) | 27 | (116, 73, 31) | 37 | (145, 216, 230) |
| 8 | (230, 150, 141) | 18 | (249, 201, 51) | 28 | (83, 70, 88) | 38 | (71, 174, 160) |
| 9 | (254, 152, 117) | 19 | (252, 228, 153) | 29 | (42, 36, 70) | 39 | (182, 211, 200) |
| 10 | (247, 208, 192) | 20 | (179, 180, 122) | 30 | (57, 69, 153) | 40 | (39, 56, 100) |

## 许可证

[MIT](LICENSE)