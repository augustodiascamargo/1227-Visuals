# 1227 media bridge (optional, Windows 10/11).
# Reads what the Windows media controls know about the current player (Spotify, a browser
# playing YouTube, ...): title, artist, album, play state and the timeline (position and length),
# and prints one JSON line about twice a second. Read only: it never controls playback, never
# touches the network and stops when Minecraft closes.
# Arguments: <minecraft process id> <path for the cover image>
param([int]$ParentPid = 0, [string]$CoverPath = "")

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Emit($obj) {
    [Console]::Out.WriteLine(($obj | ConvertTo-Json -Compress))
    [Console]::Out.Flush()
}

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1
    function Await($op, [Type]$type) {
        $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
        [void]$task.Wait(5000)
        return $task.Result
    }
    [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
    [void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
    $propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
    $streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType]
    $mgr = Await ($mgrType::RequestAsync()) $mgrType
} catch {
    Emit @{ error = "unsupported: " + $_.Exception.Message }
    exit 2
}

$lastKey = ""
$coverSeq = 0
$loop = 0
while ($true) {
    $loop++
    if ($ParentPid -gt 0 -and ($loop % 10) -eq 0) {
        if (-not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { exit 0 }
    }
    try {
        $s = $mgr.GetCurrentSession()
        if ($null -eq $s) {
            Emit @{ state = "none" }
        } else {
            $p = Await ($s.TryGetMediaPropertiesAsync()) $propsType
            $tl = $s.GetTimelineProperties()
            $pb = $s.GetPlaybackInfo()
            $key = "" + $p.Artist + "|" + $p.Title
            if ($key -ne $lastKey) {
                $lastKey = $key
                if ($CoverPath -ne "" -and $null -ne $p.Thumbnail) {
                    try {
                        $ras = Await ($p.Thumbnail.OpenReadAsync()) $streamType
                        $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($ras)
                        $fs = [System.IO.File]::Create($CoverPath)
                        $net.CopyTo($fs)
                        $fs.Close()
                        $net.Close()
                        $coverSeq++
                    } catch { }
                }
            }
            $age = ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds
            Emit @{
                state  = "" + $pb.PlaybackStatus
                app    = "" + $s.SourceAppUserModelId
                title  = "" + $p.Title
                artist = "" + $p.Artist
                album  = "" + $p.AlbumTitle
                pos    = [math]::Round($tl.Position.TotalMilliseconds)
                start  = [math]::Round($tl.StartTime.TotalMilliseconds)
                end    = [math]::Round($tl.EndTime.TotalMilliseconds)
                age    = [math]::Round($age)
                cover  = $coverSeq
            }
        }
    } catch {
        Emit @{ error = "" + $_.Exception.Message }
    }
    Start-Sleep -Milliseconds 400
}
