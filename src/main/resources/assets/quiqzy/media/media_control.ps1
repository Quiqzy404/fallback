# ZERO Client: Play/Pause, Weiter, Zurück für die Spotify-Sitzung
param([string]$Action, [int]$OnlySpotify = 1)
$ErrorActionPreference = 'SilentlyContinue'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$s = $null
foreach ($x in $mgr.GetSessions()) { if ($x.SourceAppUserModelId -like '*Spotify*') { $s = $x } }
if ($s -eq $null -and $OnlySpotify -eq 0) { $s = $mgr.GetCurrentSession() }
if ($s -eq $null) { exit }
switch ($Action) {
  'toggle' { Await ($s.TryTogglePlayPauseAsync()) ([bool]) | Out-Null }
  'next' { Await ($s.TrySkipNextAsync()) ([bool]) | Out-Null }
  'prev' { Await ($s.TrySkipPreviousAsync()) ([bool]) | Out-Null }
}
