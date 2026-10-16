# PRV 全脑切片处理流程包

> 位置：`E:\Methods\PRV-ABBA-pipeline\`　　原始数据不在本目录，见 `D:\原始数据\扫片\`

小鼠**脾脏注射 PRV** 全脑冠状切片的 **ABBA 脑区配准 + 逐脑区病毒信号定量**流程。

已完成时间点：**96h**（20261013-PRV，6 个 CZI / 41 张切片）。
待做：48h、72h。

## 从哪看起

| 文件 | 内容 |
|---|---|
| **SOP-脑区配准与PRV定量.md** | **主文档**：环境安装、逐步配准流程、定量流程、结果判读、故障速查 |
| scripts\quant-PRV-per-region.groovy | QuPath 无头定量脚本（逐脑区统计 PRV / DAPI） |
| scripts\run-quant.ps1 | 批量跑定量并汇总的驱动脚本 |
| env\cordis.patch.yml | DSH 里注册 abba-mcp 的配置（含必需的环境变量） |
| env\jgo.toml | jgo 的 Maven 镜像与依赖配置 |
| env\jgo.conf | jgo 全局设置（`links = copy`，跨卷必需） |

## 一句话流程

```
解压 CZI → ABBA 导入 → 删标签/概览图 → DeepSlice 定位(DAPI)
        → Elastix 仿射(DAPI↔Nissl) → Elastix 样条 → Snapshot 校验 → Save State
        → Export to QuPath Project（确认 ROI 集生成）
        → 停掉 Fiji → QuPath 无头脚本逐脑区定量 → 按峰值 + 跨动物重复判读
```

## 三个最容易踩的坑

1. **通道**：`AF405 = DAPI`，`AF594 = PRV`。配准要送 **DAPI**（手册要求核染/自发荧光通道），
   定量才看 PRV。送错通道会让配准质量下降。
2. **内存**：Fiji 的 JVM 启动即预留约 7 GB commit。跑 QuPath 前**必须停掉 Fiji**，
   否则第二个 JVM 起不来（`DOS error 1455`）。停 Fiji 不丢任何东西。
3. **导出是异步的**：`Export Slices To New QuPath Project` 返回 ok 后，
   `data/<n>/ABBA-RoiSet-*.zip` 还要等 1–2 分钟才写完，且**必须一次导一个样本**。
   只有 `server.json` 说明导出失败，要重导。

详见 SOP。
