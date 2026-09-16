# YOLOv5-Lite 目标识别（Android · ncnn）— v1.2.1

> 仓库命名：`yolov5-lite-v5-5090`（「5090」为本机 AI 推理产品线统一后缀；本仓库对应 **YOLOv5 / v5lite** 系列）
> 上游原始源码：[ppogg/ncnn-android-v5lite](https://github.com/ppogg/ncnn-android-v5lite)（基于 nihui/ncnn-android-yolov5）

## 简介
基于 ncnn 的 YOLOv5-Lite 安卓实时目标识别 App。本仓库在原始 demo 基础上做了中文化与易用性增强，版本号 **1.2.1**。

## 模型权重（已随仓库提供，源自原始源码）
`app/src/main/assets/` 已包含原始 v5lite 权重（与上游 ppogg 仓库一致，未做任何替换）：

| 文件 | 说明 | 对应下拉模型 |
|---|---|---|
| `e.param` / `e.bin` | YOLOv5-Lite-E | 320-lite-e / 416-lite-e |
| `i8e.param` / `i8e.bin` | YOLOv5-Lite-E int8 量化 | 320-lite-i8e / 416-lite-i8e |
| `s.param` / `s.bin` | YOLOv5-Lite-S | 416-lite-s |
| `i8s.param` / `i8s.bin` | YOLOv5-Lite-S int8 量化 | 416-lite-i8s |
| `c.param` / `c.bin` | YOLOv5-Lite-C（最大） | 512-lite-c |

> 权重直接取自原始上游 `ppogg/ncnn-android-v5lite` 的 `app/src/main/assets/`，满足「权重用原来源码里边的」。

## 本版本（v1.2.1）相对原始 demo 的改动
1. 消除「此应用专为旧版 Android 打造」提示：`compileSdk/targetSdk` 24 → 34。
2. 检测框/状态栏/CSV 中文显示：原生侧 `YOLO_CN_LABELS` + Java `Labels.CN`（80 类中文名）。
3. 顶部状态栏实时显示「检测到 N 个物体：人 ×2、汽车 ×1」。
4. 检测结果自动保存：公共 `Downloads/YOLOv5Lite/detect_年-月-日.csv`，**按天保留历史**，字段：时间、物体、置信度（带 BOM，Excel 中文不乱码），写盘 1.5s 节流。
5. 右上角 ⋮ 菜单：帮助 / 关于（关于弹窗显示版本号 1.2.1，取自 Manifest）。
6. Material 配色 + 标题栏 + 状态栏着色 + 控制区重排。
7. 构建链升级：AGP 8.1.4 / Gradle 8.4 / AndroidX（原 jcenter 已停用）。

## 本地构建与打包
见仓库内 **[本机安装打包方案.md](本机安装打包方案.md)**（安卓 APK + Windows 安装包）。

## 多 YOLO 版本路线图
| 版本区间 | 模型 | 仓库 |
|---|---|---|
| 1.2.1 – 1.9.8 | YOLOv5（v5lite） | 本仓库 |
| 2.0.1 – 2.9.8 | YOLOv8 | [yolov5-lite-v8-5090](https://github.com/g101400/yolov5-lite-v8-5090) |
| 3.0.1 – 3.9.8 | YOLOv11 | [yolov5-lite-v11-5090](https://github.com/g101400/yolov5-lite-v11-5090) |
| 6.0.1 – 6.9.8 | YOLOv26 | [yolov5-lite-v26-5090](https://github.com/g101400/yolov5-lite-v26-5090) |

实施细节与架构改造（ModelRuntime 抽象）见 **[规划与方案.md](规划与方案.md)**。

## 许可
- ncnn 相关代码：BSD 3-Clause（Tencent / nihui）
- 上游 demo：见 [ppogg/ncnn-android-v5lite](https://github.com/ppogg/ncnn-android-v5lite)
