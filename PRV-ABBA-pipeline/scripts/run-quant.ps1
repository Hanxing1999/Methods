# run-quant.ps1 -- batch per-region PRV quantification with QuPath (headless).
#
# IMPORTANT: stop Fiji before running this. Fiji's JVM reserves ~7 GB of commit
# and QuPath needs a second JVM; it fails with "DOS error 1455" otherwise.
#
# Usage:
#   .\run-quant.ps1 -ProjectRoot "D:\data\<dataset>\_qupath" `
#       -Samples RecognizedCode,RecognizedCode-1
param(
  [Parameter(Mandatory=$true)][string]$ProjectRoot,
  [Parameter(Mandatory=$true)][string[]]$Samples,
  [string]$Script = (Join-Path $PSScriptRoot "quant-PRV-per-region.groovy"),
  [string]$QuPath = "E:\QuPath-0.7.0\QuPath-0.7.0 (console).exe"
)
if (-not (Test-Path -LiteralPath $QuPath)) { Write-Error "QuPath not found: $QuPath"; exit 1 }
if (-not (Test-Path -LiteralPath $Script)) { Write-Error "script not found: $Script"; exit 1 }
$totalRows = 0; $totalSlices = 0
foreach ($s in $Samples) {
  $proj = Join-Path $ProjectRoot "$s\project.qpproj"
  if (-not (Test-Path -LiteralPath $proj)) { Write-Host "[skip] $s : project not found"; continue }
  Write-Host "=== $s ==="
  $raw = Join-Path $ProjectRoot "raw-$s.txt"
  & $QuPath script -p $proj -s $Script 2>&1 | Set-Content -LiteralPath $raw -Encoding UTF8
  $lines = Get-Content -LiteralPath $raw
  ($lines | Where-Object { $_ -like "QCSV`t*" -or $_ -like "QTIME`t*" }) |
      Set-Content -LiteralPath (Join-Path $ProjectRoot "quant-$s.tsv") -Encoding UTF8
  $rows   = @($lines | Where-Object { $_ -like "QCSV`t*" -and $_ -notlike "*PIXEL_UM*" })
  $slices = @($lines | Where-Object { $_ -like "QTIME`t*" })
  $totalRows += $rows.Count; $totalSlices += $slices.Count
  Write-Host ("  regions={0}  slices={1}" -f $rows.Count, $slices.Count)
}
Write-Host ""
Write-Host ("TOTAL regions={0}  slices={1}" -f $totalRows, $totalSlices)