import qupath.ext.biop.abba.AtlasTools

double DS = 8.0d
def imageData = getCurrentImageData()
def server = imageData.getServer()
def name = server.getMetadata().getName()
double px = server.getPixelCalibration().getAveragedPixelSizeMicrons()
int SW = server.getWidth(), SH = server.getHeight()
AtlasTools.loadWarpedAtlasAnnotations(imageData, "acronym", true, true)
def leaves = getAnnotationObjects().findAll { it.getPathClass() != null && it.getChildObjects() != null && it.getChildObjects().isEmpty() }
long t0 = System.currentTimeMillis()
println "QCSV	" + name + "	PIXEL_UM	" + px + "	REGIONS	" + leaves.size()
int done = 0
for (ann in leaves) {
  def roi = ann.getROI()
  int bx = Math.max(0, (int)roi.getBoundsX()), by = Math.max(0, (int)roi.getBoundsY())
  int bw = Math.min(SW - bx, (int)Math.ceil(roi.getBoundsWidth()))
  int bh = Math.min(SH - by, (int)Math.ceil(roi.getBoundsHeight()))
  if (bw < 1 || bh < 1) continue
  def img = null
  try { img = server.readRegion(DS, bx, by, bw, bh) } catch (Throwable t) { img = null }
  if (img == null) continue
  def rast = img.getRaster()
  if (rast.getNumBands() < 2) continue
  double sumP = 0, maxP = 0, sumD = 0; long n = 0
  for (int j = 0; j < rast.getHeight(); j++) {
    double fy = by + j * DS
    for (int i = 0; i < rast.getWidth(); i++) {
      double fx = bx + i * DS
      if (!roi.contains((int)fx, (int)fy)) continue
      double vp = rast.getSampleDouble(i, j, 1)
      sumP += vp
      sumD += rast.getSampleDouble(i, j, 0)
      if (vp > maxP) maxP = vp
      n++
    }
  }
  done++
  if (n == 0) continue
  double areaMm2 = n * DS * DS * px * px / 1e6
  println "QCSV	" + name + "	" + ann.getName() + "	" + String.format("%.4f", areaMm2) + "	" + String.format("%.3f", sumP/n) + "	" + String.format("%.1f", maxP) + "	" + String.format("%.3f", sumD/n) + "	" + n
}
println "QTIME	" + name + "	" + (System.currentTimeMillis()-t0) + " ms	processed=" + done