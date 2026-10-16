# PRV 全脑切片 —— ABBA 配准 + 脑区定量 SOP

> 本文件记录 20261013-PRV 数据集（脾脏注射 PRV，96h）走通的完整流程、参数与坑。
> 后续 48h / 72h 数据集可直接照此执行。

## 0. 实验背景

- 注射：**脾脏**，PRV（伪狂犬病毒，逆向示踪）
- 取材时间点：96h（已完成）；48h、72h（待做）
- 切片：小鼠全脑冠状切片，Zeiss CZI，每样本 3–12 张
- 通道：**AF405 = DAPI（复染）**，**AF594 = PRV（病毒信号）**
- 像素尺寸：**0.3440 µm**（从 CZI 的 Zeiss 元数据 `Scaling|Distance|Value` 读取，20× Plan-Apochromat）

### 生物学预期（用于自查结果）

脾脏 PRV 逆行标记在 96h 应到达下丘脑自主神经核团与脑干自主核团，典型命中：
PVH（下丘脑室旁核）、MPO/MPN（视前区内/外侧）、DMH、VMH、AHN、LHA、ZI、
脑干 RVLM/C1、A5、孤束核等。**若结果里这些核团不出现，应优先怀疑配准或通道。**

---

## 1. 环境

| 组件 | 版本 | 位置 |
|---|---|---|
| abba-mcp | 0.25.0.post2 (MCP 服务器) | uv tool 安装 |
| ABBA | 0.25.0 | 由 abba-mcp 经 jgo 拉取 |
| Fiji | 2.18.0 | jgo 缓存 |
| JDK | Zulu 21（cjdk 自动下载） | `%LOCALAPPDATA%\cjdk` |
| QuPath | 0.7.0 | `E:\QuPath-0.7.0` |
| qupath-extension-abba | 0.5.0 | `C:\Users\<user>\QuPath\v0.7\extensions` |
| Appose 环境 | deepslice 2.8 GB / itk-elastix 1.4 GB | `%LOCALAPPDATA%\appose` |

### 1.1 必须的本地改动

**a) MCP 注册**（`cordis.patch.yml` 的 `insert:` 段，见 `env/cordis.patch.yml`）

关键环境变量：

- `ABBA_MCP_HOME=D:\abba-mcp` —— 工作目录（图像缓存、fiji.pid、fiji.log）
- `NO_PROXY` / `no_proxy` —— 让 Maven 直连，绕过被劫持的代理
- `_JAVA_OPTIONS=-Djava.net.useSystemProxies=true` —— Java 走系统代理
- `JGO_TIMEOUT=120` —— 默认 10s 读超时对国内镜像不够

**b) jgo 改用国内镜像**（`env/jgo.toml` 的 `[repositories]`）

- `central` 指向腾讯云镜像，`central-fallback` 保留官方源
- `[settings] links = "copy"` —— **必需**：.m2 在 C 盘、.jgo 在 D 盘，
  Windows 跨卷创建链接会报 `WinError 1314 客户端没有所需的特权`
  （注意：写进 `jgo.toml` 的 `[settings]` 无效，必须用 `jgo config set links copy` 写 `~/.config/jgo.conf`）

**c) DeepSlice 源码补丁（中文 Windows 必需）**

`...\appose\deepslice-v1.2.8-tf2.21.0\.pixi\envs\default\Lib\site-packages\DeepSlice\`
下的 `coord_post_processing\spacing_and_indexing.py` 与 `QuickNII_functions.py`：
把 `µ` 改成 `u`、`’` 改成 `'`。否则 Python 用 GBK 输出时报
`UnicodeEncodeError: 'gbk' codec can't encode character '\xb5'`。

**d) 图谱缓存（避免每次都从 Zenodo 下载）**

`C:\Users\<user>\cached_atlas\`：

- `ccf2017-mod65000-border-centered-mm-bc.h5`（约 3.1 GB）
- `mouse_brain_ccfv3.xml`、`1.json`

⚠️ 文件名必须完全一致，ABBA 按固定名查找，改名会触发联网下载。

**e) 代理**：Java / Appose 需要联网时 Clash 必须开启（7890）。

---

## 2. 每个样本的配准流程

> 原则：**一个动物一个 ABBA 会话**；图谱对象全程复用。
> 所有 `ABBA - Snapshot` / `Get State` 只读，`elastix`/`deepslice` 是**排队异步**的，
> 命令返回 ok 不代表完成，必须等 `pending_actions == 0`。

### 2.1 步骤

1. **开图谱**（只需一次）
   `ch.epfl.biop.atlas.mouse.allen.ccfv3.command.AllenBrainAdultMouseAtlasCCF2017Command`
   参数 `mapUrl` / `ontologyUrl` 留空 → 用本地缓存

2. **`ABBA - ABBA Start`** —— 冠状面取向
   `x_axis: RL (Right-Left)`, `y_axis: SI (Superior-Inferior)`, `z_axis: AP (Anterior-Posterior)`

3. **`ABBA - Import With Bio-Formats`**
   - `files`: 正向斜杠路径数组
   - `first_slice_position_mm: 0`, `slice_spacing_mm: 0.4`（占位值，后面由 DeepSlice 定）
   - 导入后每个 ScanRegion 会产生 2 个通道切片，另加 `label image` 和
     `Unhandled 16 bits RGB images` 两张**必须删掉**的图

4. **`ABBA - Delete Slices`** `slices_csv: "<n-2>,<n-1>"`（删上面那两张）

5. **`ABBA - Select Slices`** `slices_csv: "*"`

6. **`ABBA - Auto Display Range`** `slice_channels_csv: "0,1"`, 0.1/99.9, 同范围

7. **`ABBA - DeepSlice Registration`**
   - `model: mouse`
   - `slice_channels_csv: "0"` ← **DAPI**（手册要求送核染/自发荧光通道，不要送标记通道）
   - `post_processing: "Keep order + ensure regular spacing"`
   - `allow_slicing_angle_change: true`, `ensemble: true`, `pixel_size_um: 10`
   - **等 3–4 分钟**，然后读 `Get State` 看 `span`
   - **塌陷判据**：若所有切片 z 几乎相同（span < 0.05 mm）→ 定位失败
     - 先 `ABBA - Remove Last Registration` 弹掉，改 `slice_channels_csv: "1"`（PRV）重试
     - 20261013-PRV 中 DAPI 塌陷的是 `RecognizedCode-1` 和 `-10`，其余 4 个 DAPI 正常

8. **`ABBA - Elastix Registration (Affine)`**
   `atlas_channels_csv: "0"`（Nissl）, `slice_channels_csv: "0"`（**DAPI**）, `pixel_size_um: 40`

9. **重新 `ABBA - Select Slices "*"`** ← **重要**：仿射后选区会被清空，不重选样条会报
   `No selected slice`

10. **`ABBA - Elastix Registration (Spline)`**
    `atlas_channels_csv: "0"`, `slice_channels_csv: "0"`, `pixel_size_um: 20`, `nb_control_points_x: 15`

11. **校验**：`ABBA - Snapshot`（`slice_channels_csv:"1"`, `atlas_channel:0`,
    `show_region_borders:true`, `columns:4`, `pixel_size_mm:0.035`）
    看黄色图谱边界是否贴住组织；再看 `Get State` 每张 `reg=3`

12. **`ABBA - Save State`**
    ⚠️ 目标文件已存在时命令会失败（弹「This file already exists!」），**先删旧文件再存**

13. **`ABBA - Export Slices To New QuPath Project`**（选好切片后）
    ⚠️ 目标目录必须不存在或为空
    ⚠️ **务必确认会话的图谱对象存在**，否则报 `getAtlas() is null`，只写出 server.json
    ⚠️ **ROI 集是异步写的**，命令返回后要等 1–2 分钟再检查 `data/<n>/ABBA-RoiSet-*.zip` 是否生成；
       而且**一次只导一个样本**，并发导出会互相干扰导致部分条目缺 ROI 集
    ⚠️ 若目录里已有 `data/<n>/ABBA-RoiSet-*.zip` 才算成功，只有 `server.json` 说明要重导

14. **`ABBA - Close session`** 后开始下一个动物

---

## 3. 定量流程（QuPath 无头脚本）

### 3.1 关键前提：内存

**Fiji 的 JVM 启动即预留约 7 GB commit**，而系统 commit 上限 = 物理内存 + C 盘页面文件。
QuPath 需要**第二个 JVM**，内存不够时启动即失败：

```
insufficient memory for the Java Runtime Environment to continue
os::commit_memory failed; error='页面文件太小' (DOS error 1455)
```

**解决办法（实测唯一可靠）：跑 QuPath 前先停掉 Fiji。**

- 配准状态已保存为 `.abba`，QuPath 项目里也已内嵌 ROI 集，**停 Fiji 不丢任何东西**
- 停掉后 commit 从 19.5/25.7 降到 11.8/25.7，QuPath 一次跑通

另外两个已证实的内存坑：

- **Elastix 每次运行会留下一个约 450 MB 的 `appose.python_worker` 进程不回收**。
  跑多个样本后能累积到 4 GB 以上。清理：按命令行匹配 `*appose.python_worker*` 结束进程。
- Fiji 关闭会话不会释放图像源对象，**遗留 BDV 窗口**会一直占内存。
  可脚本遍历 `java.awt.Window.getWindows()` 找标题含 `Aligning Big Brains` 的 dispose 掉。

### 3.2 运行

```powershell
cd E:\Methods\PRV-ABBA-pipeline\scripts
.\run-quant.ps1 -ProjectRoot "D:\原始数据\扫片\20261013-PRV\_qupath" `
    -Samples RecognizedCode,RecognizedCode-1,RecognizedCode-4,RecognizedCode-7,RecognizedCode-10,RecognizedCode-13
```

或单样本：

```powershell
& "E:\QuPath-0.7.0\QuPath-0.7.0 (console).exe" script `
   -p "...\_qupath\<样本>\project.qpproj" `
   -s "...\_pipeline\scripts\quant-PRV-per-region.groovy"
```

单张切片约 20–60 秒（取决于切片大小）。

### 3.3 脚本做什么

1. `AtlasTools.loadWarpedAtlasAnnotations(imageData, "acronym", true, true)`
   —— 把 ABBA 导出的图谱 ROI 载入为 QuPath 标注（层级：Root → Left/Right root → 各脑区）
2. 取**叶子**标注（无子节点）＝ 最细脑区，每张 235–460 个
3. 对每个脑区：按 ROI 包围盒以 8× 降采样读像素，用 ROI 几何掩膜，
   统计 AF594(AF594→band 1) 与 AF405(band 0) 的均值/峰值/像素数
4. 输出 `QCSV` 行：图像名、脑区缩写、面积 mm²、PRV 均值、PRV 峰值、DAPI 均值、像素数

⚠️ **已知缺陷（已修）**：`server.readRegion()` 越界时返回 `null` 而非抛异常，
原脚本只 catch 异常，导致 `RecognizedCode-10` 少一张切片。现版本已加边界裁剪 + null 判断。

---

## 4. 结果判读

### 4.1 为什么用「峰值」而不是「均值」

PRV 是**稀疏点状**信号，阳性细胞只占区域的一小部分，
区域**均值会被未标记组织稀释**（实测富集度很少超过 1.5，区分不开）。
**区域内亮度峰值**才是可靠判据 —— 带毒细胞的亮度远高于背景。

### 4.2 阈值与可信度

- 全部区域实例的峰值中位数约 **150**（背景水平）
- 阈值取 **250**（≈ 中位数的 1.6 倍）
- **必须用「跨动物重复出现」作为可信度主判据** —— 单张切片的孤立亮点可能是切片边缘、
  杂质、折叠，不能采信。
- **未按区域体积归一化**：CP、CA1 这类大区域天然有更多机会出现亮点，排名应打折扣。

### 4.3 20261013-PRV（96h）结果摘要

41 张切片、9546 个区域实例；峰值 ≥250 的有 371 个区域实例、159 个脑区种类；
其中 **27 个脑区在 ≥3 只动物中重复出现**：

LHA(5/6)、CP(5/6)、DMH(4/6)、FS(4/6)、CA1(4/6)、act(4/6)、PIR(4/6)、LD(4/6)、
CEAm、AHN、MPN、BST、ACB、SI、VMH、ZI、SSs2/3、DG-mo、CEAl、MOs2/3、
MOp2/3、LSr、MPO、SSs5、PVi、MOs5、MD（均 3/6）

**下丘脑自主神经核团占主导**（LHA、DMH、VMH、MPN、MPO、AHN、ZI、PVi），
与脾脏 PRV 逆向标记 96h 的预期相符。PVH 峰值 2094、MPN 2098、PSTN 1138 也很突出。

详见 `..\20261013-PRV\_qupath\PRV-result-ALL.md`。

---

## 5. 48h / 72h 数据集怎么做

1. 按第 2 节逐样本配准（每样一个会话），状态存到该数据集的 `_abba_states\`
2. `ABBA - Export Slices To New QuPath Project` 到该数据集的 `_qupath\<样本>\`，
   **确认每个 `data/<n>/` 都有 `ABBA-RoiSet-*.zip`**
3. **停掉 Fiji**
4. 用 `run-quant.ps1` 批量定量
5. 把各 `quant-<样本>.tsv` 合并，按第 4 节判读
6. 三个时间点可做对照：随时间推移，标记应逐步向更高级中枢扩散、
   **阳性脑区数量应递增**（48h < 72h < 96h）。若趋势相反，优先查配准与通道。

---

## 6. 常见故障速查

| 现象 | 原因 | 处理 |
|---|---|---|
| `UnicodeEncodeError: 'gbk' codec` | DeepSlice 源码含 `µ` | 改源码为 `u`（见 1.1c） |
| `WinError 1314 客户端没有所需的特权` | jgo 跨卷建链接 | `jgo config set links copy` |
| `ReadTimeout ... mirrors.cloud.tencent.com` | jgo 默认 10s 超时 | `JGO_TIMEOUT=120` |
| `UnknownHostException: zenodo.org` | 图谱缓存文件名不对 | 核对 1.1d 的三个文件名 |
| 报告 Fiji 已启动很久但没起来 | `fiji.pid` 指向已死进程 | 删 `D:\abba-mcp\fiji.pid` |
| DeepSlice 位置全塌陷（span≈0） | 该通道图像区分度不足 | 换通道重试，或 `ensemble` 开关切换 |
| Elastix 报 `No selected slice` | 前一步操作清空了选区 | 重新 `Select Slices "*"` |
| `Save State` 失败 | 目标文件已存在 | 先删旧文件 |
| QuPath 启动即 OOM | Fiji 占着 7 GB commit | **停 Fiji** |
| QuPath 报 `getRaster() on null` | `readRegion` 越界返回 null | 用带边界裁剪的脚本版本 |
| QuPath 导出项目缺 ROI 集 | 并发导出 / 图谱对象丢失 | 一次一个样本重导，并确认图谱存在 |

---

## 7. 目录约定

```
E:\Methods\                         ← 方法学（跨数据集复用，不含原始数据）
  README.md
  PRV-ABBA-pipeline\
    README.md                        入门：一句话流程 + 三个最容易踩的坑
    SOP-脑区配准与PRV定量.md           ← 本文件
    scripts\
      quant-PRV-per-region.groovy    QuPath 逐脑区定量脚本
      run-quant.ps1                  批量驱动
    env\                             MCP 与 jgo 配置备份
    notes\                           备选方案

D:\原始数据\扫片\                    ← 只放数据与结果
  <数据集>\  例如 20261013-PRV\
    hx\                             解压后的 CZI
    _abba_states\                   每样本 .abba 配准状态
    _qupath\                        每样本 QuPath 项目 + 定量结果
    配准标注图\                      图谱叠加核验图
```

**原则：方法学放 E 盘、数据与结果放 D 盘，互不混杂。**
这样同一套流程可以原样套用到 48h / 72h 或其它数据集，不必复制脚本。
