# Methods

实验方法学仓库：流程文档、脚本、环境配置。**不放原始数据。**

### [PRV-ABBA-pipeline](PRV-ABBA-pipeline/)

小鼠**脾脏注射 PRV** 全脑冠状切片的 **ABBA 脑区配准 + 逐脑区病毒信号定量**流程。
已完成时间点 96h（6 个 CZI / 41 张切片）；48h、72h 待做。

从 [PRV-ABBA-pipeline/README.md](PRV-ABBA-pipeline/README.md) 看起，
完整操作细节在 [SOP-脑区配准与PRV定量.md](PRV-ABBA-pipeline/SOP-脑区配准与PRV定量.md)。

### 为什么有这个仓库

配准与定量的参数、踩过的坑、环境配置，**过几个月一定会忘**。
放这里，下次拿到 48h/72h 的数据能照着原样跑，不用重新摸索。

### 命名约定

| 类型 | 编码 |
|---|---|
| 脚本（`.ps1` `.groovy` `.toml` `.yml`） | **纯 ASCII** —— PowerShell 5.1 按 GBK 读 `.ps1`，中文路径字面量会乱码 |
| 文档（`.md`）内容 | 中文 |
| 目录名 | ASCII |

### 目录

```
Methods/
  README.md
  PRV-ABBA-pipeline/
    README.md                      入门：一句话流程 + 三个最容易踩的坑
    SOP-脑区配准与PRV定量.md        完整 SOP
    scripts/
      quant-PRV-per-region.groovy  QuPath 逐脑区定量脚本
      run-quant.ps1                批量驱动
    env/
      cordis.patch.example.yml     ABBA MCP 注册模板
      jgo.toml / jgo.conf          Maven 镜像与跨卷设置
    notes/
      Fiji内定量备选方案.md         QuPath 不可用时的退路
```

数据与结果不在此仓库，在 `D:\原始数据\扫片\<数据集>\`。
