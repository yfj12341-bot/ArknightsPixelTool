# ArknightsPixelTool

为《明日方舟》奇象巡展活动准备的 Android 像素画生成器：导入任意图片，经过调整、裁剪和多种采样策略，生成 24×24 到 64×64 的像素画；既可使用活动固定 40 色调色板，也可自动取色或自定义颜色上限。生成完成后还支持直接在网格上手动逐格修改，并一键分享为 PNG。也可以用作像素图的快速生成工具。v0.2.0 起支持将像素画自动填充到游戏编辑界面。

## 功能特性

- **图片调整**：旋转、亮度、对比度，带防抖的实时预览
- **手动 1:1 裁剪**：拖动图片、双指缩放来调整裁剪区域
- **三种调色板模式**：
  - 自动 40 色：从图片自身提取调色板
  - 固定调色板：活动 40 色，编号 1–40，顺序固定
  - 自定义：24–64 偶数画幅，颜色上限 1–256
- **三种下采样方式**：平滑平均、主体色、Box 平均
- **可选 Floyd–Steinberg 抖动**
- **结果预览**：双指缩放/平移、网格显示与隐藏、加粗中线、色号显示、点击查看色号
- **手动编辑**：使用调色板或自定义颜色（RGB / HSL）逐格修改，支持撤销与重做
- **分享导出**：将生成结果分享为 PNG
- **自动填充到游戏**：框选游戏内画布与调色盘位置，通过无障碍服务自动点击填充生成结果
- **多种速度档位**：非常快 / 快 / 中等 / 慢四档可选，适应不同设备与网络延迟

## 使用流程

1. 从相册导入图片
2. 按需调整旋转、亮度、对比度
3. 拖动或双指缩放图片，确定 1:1 裁剪区域
4. 选择调色板模式、画幅、颜色上限、下采样方式，点击生成
5. 在结果预览中缩放检查，开启网格或色号辅助
6. 开启编辑模式逐格修改，可撤销/重做
7. 分享 PNG 输出
8. （可选）自动填充：在系统设置中开启「像素画助手自动填充」无障碍服务，回到应用框选游戏内画布与调色盘位置，选择速度档位后自动填充

## 模式选择

- **平滑平均**：生成图片较柔和，颜色过渡平滑，适合大部分场景使用。
- **主体色**：生成图片较锐利，颜色对比鲜明，适合简单图标与已有像素风格的图片。
- **Box平均**：生成图片大体类似“平滑平均”模式，颜色过渡略硬，按需使用。

## 项目结构

```text
ArknightsPixelTool/
├── app/                                  # Android 应用（Jetpack Compose + Material 3）
│   └── src/main/
│       ├── kotlin/com/pixelpainter/app/
│       │   ├── MainActivity.kt           # Activity 入口
│       │   ├── PixelPainterApp.kt        # 主界面与全部交互逻辑
│       │   ├── BitmapImage.kt            # 图片解码与位图转换
│       │   ├── ShareSupport.kt           # PNG 分享（FileProvider）
│       │   └── autofill/                 # 自动填充（无障碍服务、框选、速度档位）
│       ├── res/                          # 图标、主题、文案等资源
│       ├── test/kotlin/...               # 自动填充单元测试
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

- **自动取色**：中位切分初始化 + Lab 空间 Lloyd 迭代细化
- **颜色匹配**：OKLab 空间最近色映射
- **下采样**：
  - 平滑平均：区域面积平均
  - 主体色：先映射到调色板，再做块内多数投票，优先保留线条与边缘
  - Box 平均：面积加权采样
- **抖动**：Floyd–Steinberg 误差扩散
- **图片调整**：纯 Kotlin 实现，不依赖 Android 图形 API
- **界面**：Jetpack Compose + Material 3，全部使用 Kotlin
- **自动填充**：基于无障碍服务自动点击，框选画布与调色盘位置，支持调色盘分页与颜色校准
- **速度档位**：非常快 / 快 / 中等 / 慢四档，可调点击与翻页延迟

## 版本历史

- **v0.2.0**：新增自动填充到游戏（框选画布与调色盘、四档速度档位）；关于对话框新增更新日志
- **v0.1.0**：初始版本，图片转像素画、三种调色板、手动编辑与 PNG 分享

## 构建

环境要求：

- JDK 17
- Android SDK 35（platform + build-tools 35.0.1）

在项目根目录执行：

```bash
./gradlew :core:test          # 运行核心算法单元测试
./gradlew :app:test           # 运行应用模块单元测试（自动填充等）
./gradlew :app:assembleDebug  # 构建 Debug APK
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 使用声明

- 本项目为玩家自制工具，与《明日方舟》及其官方（上海鹰角网络科技有限公司）无任何关联，未获得官方授权或认可。
- 游戏名称、商标及相关素材版权归其各自所有者所有。
- 本工具仅供学习、交流与个人娱乐使用，禁止用于任何违法或违规用途（包括但不限于外挂、作弊、破坏游戏公平性、侵犯他人权益等）。
- 使用本工具产生的一切后果由使用者自行承担。

## 许可证

[MIT](LICENSE)