<#
.SYNOPSIS
  Checks 16 KB page-size ELF alignment of every native library (.so) inside an AAB or APK.

.DESCRIPTION
  Google Play requires apps with native code that target Android 15+ to support 16 KB page sizes.
  Each .so for a 64-bit ABI (arm64-v8a, x86_64) must have every PT_LOAD segment aligned to >= 0x4000 (16384).
  32-bit ABIs (armeabi-v7a, x86) are listed for information only (not required by Play).
  Pure PowerShell / .NET, no SDK tools needed. Reads the archive only, never extracts to disk.
  For an APK also run the zip-entry alignment check (see NOTES below).

.PARAMETER Path
  Path to a .aab or .apk (e.g. app\build\outputs\bundle\release\app-release.aab).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\check_elf_alignment.ps1 app\build\outputs\bundle\release\app-release.aab

.NOTES
  Exit code 0 = all 64-bit libs OK, 1 = at least one 64-bit lib misaligned, 2 = bad input.
  APK zip alignment (separate from ELF alignment): zipalign -c -P 16 -v 4 your.apk   (build-tools 35.0.0+)
#>
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Path
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    Write-Host "File not found: $Path" -ForegroundColor Red
    exit 2
}

$required = 16384
$required64 = @('arm64-v8a', 'x86_64')
$bad64 = 0
$total = 0

function Get-LoadAlignments([byte[]]$b) {
    # Returns @{ Bits = 32|64; Aligns = [uint64[]] } or $null when not a little-endian ELF.
    if ($b.Length -lt 64 -or $b[0] -ne 0x7F -or $b[1] -ne 0x45 -or $b[2] -ne 0x4C -or $b[3] -ne 0x46) { return $null }
    if ($b[5] -ne 1) { return $null }
    $aligns = New-Object System.Collections.Generic.List[uint64]
    if ($b[4] -eq 2) {
        $phoff = [BitConverter]::ToUInt64($b, 0x20)
        $phentsize = [BitConverter]::ToUInt16($b, 0x36)
        $phnum = [BitConverter]::ToUInt16($b, 0x38)
        for ($i = 0; $i -lt $phnum; $i++) {
            $o = [int]($phoff + [uint64]($i * $phentsize))
            if ([BitConverter]::ToUInt32($b, $o) -eq 1) { $aligns.Add([BitConverter]::ToUInt64($b, $o + 48)) }
        }
        return @{ Bits = 64; Aligns = $aligns }
    }
    if ($b[4] -eq 1) {
        $phoff = [BitConverter]::ToUInt32($b, 0x1C)
        $phentsize = [BitConverter]::ToUInt16($b, 0x2A)
        $phnum = [BitConverter]::ToUInt16($b, 0x2C)
        for ($i = 0; $i -lt $phnum; $i++) {
            $o = [int]($phoff + ($i * $phentsize))
            if ([BitConverter]::ToUInt32($b, $o) -eq 1) { $aligns.Add([uint64][BitConverter]::ToUInt32($b, $o + 28)) }
        }
        return @{ Bits = 32; Aligns = $aligns }
    }
    return $null
}

$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Path).Path)
try {
    foreach ($entry in ($zip.Entries | Sort-Object FullName)) {
        if ($entry.FullName -notmatch '(^|/)lib/([^/]+)/[^/]+\.so$') { continue }
        $abi = $Matches[2]
        $total++
        $ms = New-Object System.IO.MemoryStream
        $s = $entry.Open()
        try { $s.CopyTo($ms) } finally { $s.Dispose() }
        $info = Get-LoadAlignments $ms.ToArray()
        $ms.Dispose()
        if ($null -eq $info) {
            Write-Host ("SKIP    {0}  (not a little-endian ELF)" -f $entry.FullName)
            continue
        }
        $min = ($info.Aligns | Measure-Object -Minimum).Minimum
        $ok = ($info.Aligns.Count -gt 0) -and ($min -ge $required)
        $is64 = $required64 -contains $abi
        if ($ok) {
            Write-Host ("ALIGNED {0}  (PT_LOAD align 2**{1})" -f $entry.FullName, [int][Math]::Log($min, 2))
        } elseif ($is64) {
            $bad64++
            Write-Host ("FAIL    {0}  (PT_LOAD align {1}, need >= {2})" -f $entry.FullName, $min, $required) -ForegroundColor Red
        } else {
            Write-Host ("info    {0}  (32-bit ABI, align {1}; not required by Play)" -f $entry.FullName, $min)
        }
    }
} finally {
    $zip.Dispose()
}

Write-Host ""
Write-Host ("Native libs found: {0}. 64-bit misaligned: {1}." -f $total, $bad64)
if ($total -eq 0) { Write-Host "No lib/*/*.so entries: nothing to check (wrong file?)." }
if ($bad64 -gt 0) {
    Write-Host "Fix: bump the owning dependency (Unity Ads / androidx.graphics.path / datastore) to a 16 KB-aligned release." -ForegroundColor Yellow
    exit 1
}
exit 0
