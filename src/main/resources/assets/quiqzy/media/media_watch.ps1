# ZERO Client: liest den aktuell laufenden Song aus der Windows-Mediensteuerung (Spotify, Browser, ...).
# Gibt jede halbe Sekunde eine Zeile aus: TITEL<TAB>KÜNSTLER<TAB>ALBUM<TAB>POS_MS<TAB>DAUER_MS<TAB>STATUS<TAB>APP<TAB>COVER_PFAD
param([string]$CoverDir, [int]$OnlySpotify = 1)
$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
[Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null
$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$lastKey = ''
$cover = ''
while ($true) {
  $s = $null
  foreach ($x in $mgr.GetSessions()) { if ($x.SourceAppUserModelId -like '*Spotify*') { $s = $x } }
  if ($s -eq $null -and $OnlySpotify -eq 0) { $s = $mgr.GetCurrentSession() }
  if ($s -eq $null) { [Console]::Out.WriteLine('NONE'); [Console]::Out.Flush(); Start-Sleep -Milliseconds 1000; continue }
  $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
  $tl = $s.GetTimelineProperties()
  $st = $s.GetPlaybackInfo().PlaybackStatus
  $pos = $tl.Position.TotalMilliseconds
  if ($st -eq 'Playing') { $pos += ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds }
  $key = "$($p.Artist)|$($p.Title)"
  if ($key -ne $lastKey) {
    $lastKey = $key
    $cover = ''
    if ($p.Thumbnail -ne $null -and $CoverDir) {
      try {
        $stream = Await ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
        $file = Join-Path $CoverDir ('cover_' + [Math]::Abs($key.GetHashCode()) + '.img')
        $fs = [System.IO.File]::Create($file); $net.CopyTo($fs); $fs.Close(); $net.Close()
        $cover = $file
      } catch { $cover = '' }
    }
  }
  $clean = { param($t) if ($t -eq $null) { '' } else { ($t -replace "[`t`r`n]", ' ') } }
  $line = (& $clean $p.Title) + "`t" + (& $clean $p.Artist) + "`t" + (& $clean $p.AlbumTitle) + "`t" + [long]$pos + "`t" + [long]$tl.EndTime.TotalMilliseconds + "`t" + $st + "`t" + $s.SourceAppUserModelId + "`t" + $cover
  [Console]::Out.WriteLine($line); [Console]::Out.Flush()
  Start-Sleep -Milliseconds 500
}
