# 备选方案：不依赖 QuPath，直接在 Fiji 内定量

## 什么时候用

QuPath 起不来（内存不足、未安装、版本冲突）时的退路。
**正常情况下用 QuPath 脚本**（每张切片 20–60 秒）—— 本方案明显更慢。

## 原理

Fiji 自带 Bio-Formats，可以直接读 CZI 金字塔层；ROI 集本来就是 ImageJ 二进制格式。
所以整条链路不需要 QuPath、也不需要第二个 JVM。

## 已验证可用的接口

- 读 CZI：`loci.formats.ImageReader`
  - **必须** `setFlattenedResolutions(false)` 才能访问金字塔层级
  - `r.setSeries(n)` → `r.getSizeX()` → `r.setResolution(level)` → `r.getSizeX()` 得到该层宽度
  - 降采样倍数 `s = 全分辨率宽 / 该层宽`（注意 CZI 的可能不是整数 2 的幂）
  - 像素：`r.openBytes(r.getIndex(0, channel, 0), x, y, w, h)`
    返回 byte[]，UINT16 小端 → `ByteBuffer.wrap(b).order(LITTLE_ENDIAN).asShortBuffer()`
- 读 ROI：`ij.io.RoiDecoder`（ZipInputStream 逐条读 `.roi`）
  - `roi.getName()` 是**图谱 atlas_id**（如 1026、6、262），不是脑区缩写
  - 映射：读 `<样本>-Ontology.json`，遍历 `root.children` 递归，取
    `data.atlas_id → data.acronym`（已验证：6→ACB、262→PL、SNr=330、VTA=376）

## 性能陷阱

**`ShapeRoi.getMask()` / `roi.contains()` 会对每个脑区做全分辨率栅格化**，
这是主要瓶颈。实测「逐脑区读盘 + 逐像素 contains」跑 3 张切片 30 分钟都完不成。

### 推荐算法：标签图单遍扫描

1. 先把该层的两个通道整层读进内存（8× 降采样时约 12.8 MP，每通道 25 MB）
2. 建一张同尺寸的 `TYPE_BYTE_INDEXED` 标签图，用**恒等调色板**
   （`r[i]=g[i]=b[i]=i`），关闭抗锯齿
3. 每批最多 255 个脑区，用 `g2.fill(shape)` 把缩放后的形状画进去（栅格化交给 Java2D 原生代码）
4. 对整层做**一遍**扫描，按标签值累积 sum/max/count

这样栅格化是原生的，逐像素循环只跑一遍。
（本方案在 20261013-PRV 上实现并提交过，但因当时 4 个作业并发争抢 CPU 未在时限内返回，
未取得完整结果 —— 若要走这条路，请单独运行、不要并发。）

## 形状缩放的坑

`roi.getShape()` 返回的是**全分辨率坐标**的形状，要按 1/s 缩放后再画：

```groovy
g2.transform(AffineTransform.getScaleInstance(1.0d/s, 1.0d/s))
g2.fill(shape)
```
