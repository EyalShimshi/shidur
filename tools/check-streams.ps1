<#
.SYNOPSIS
Checks the station list's stream addresses, and measures stations' opening clips.

.DESCRIPTION
Needs only PowerShell 7; nothing to install. Requests use the app's style of User-Agent,
because some servers decide by User-Agent whether to add an opening ad (CyberCDN adds clips
for browsers but not for apps).

  ./tools/check-streams.ps1                        # every address: status, type, server
  ./tools/check-streams.ps1 -Intro 102fm-tlv,kan-bet

-Intro measures each station's first plain (non-HLS) stream: it connects twice, 20 seconds
apart, and matches the audio frames. Audio that both connections start with is a fixed clip
the server sends every new listener. Audio that only the second connection has, before it
joins the first one's timeline, is a clip that changes per connection (a rotating ad).
#>
param(
  [string[]]$Intro,
  [string]$Catalog = (Join-Path $PSScriptRoot '..\app\src\main\assets\catalog.json'),
  [string]$UserAgent = 'Shidur/0.3 (Android; +https://github.com/EyalShimshi/shidur)'
)

$ErrorActionPreference = 'Stop'
$stations = (Get-Content $Catalog -Raw -Encoding UTF8 | ConvertFrom-Json).stations

if (-not $Intro) {
  $targets = foreach ($s in $stations) { $i = 0; foreach ($st in $s.streams) { [pscustomobject]@{ Station = $s.id; Index = $i; Url = $st.url }; $i++ } }
  $targets | ForEach-Object -ThrottleLimit 10 -Parallel {
    $t = $_
    $client = [System.Net.Http.HttpClient]::new(); $client.Timeout = [TimeSpan]::FromSeconds(12)
    $client.DefaultRequestHeaders.UserAgent.ParseAdd($using:UserAgent)
    try {
      $resp = $client.GetAsync($t.Url, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
      $status = [int]$resp.StatusCode
      $note = if ($status -ge 300 -and $status -lt 400) { "redirect to $($resp.Headers.Location) (the app follows it)" } else { $resp.RequestMessage.RequestUri.Host }
      $type = if ($resp.Content.Headers.ContentType) { $resp.Content.Headers.ContentType.MediaType } else { '' }
      $resp.Dispose()
      [pscustomobject]@{ Station = $t.Station; I = $t.Index; Status = $status; Type = $type; Note = $note }
    } catch {
      $e = $_.Exception; while ($e.InnerException) { $e = $e.InnerException }
      [pscustomobject]@{ Station = $t.Station; I = $t.Index; Status = 'ERR'; Type = ''; Note = $e.Message }
    } finally { $client.Dispose() }
  } | Sort-Object Station, I | Format-Table -AutoSize -Wrap
  'Behind a proxy that buffers plain-http responses, http:// addresses can time out even when they work.'
  return
}

Add-Type -TypeDefinition @'
using System; using System.Collections.Generic;
public static class OpeningClip {
  static readonly int[] BR = {0,32,40,48,56,64,80,96,112,128,160,192,224,256,320,0};
  static readonly int[] SR = {44100,48000,32000,0};
  static readonly int[] ASR = {96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000,7350,0,0,0};
  public static byte[] StripIcy(byte[] raw, int metaint) {
    if (metaint <= 0) return raw;
    var o = new List<byte>(raw.Length); int i = 0;
    while (i < raw.Length) { int n = Math.Min(metaint, raw.Length - i); for (int k = 0; k < n; k++) o.Add(raw[i + k]); i += n; if (i >= raw.Length) break; i += 1 + raw[i] * 16; }
    return o.ToArray();
  }
  // Hashes of three consecutive MP3 or AAC (ADTS) frames, with each frame's duration in seconds.
  static List<KeyValuePair<uint,double>> Frames(byte[] b) {
    var f = new List<KeyValuePair<uint,double>>(); int i = 0;
    while (i + 9 < b.Length) {
      int len = 0, start = 0; double dur = 0;
      if (b[i] == 0xFF && (b[i+1] & 0xF6) == 0xF0) { len = ((b[i+3] & 3) << 11) | (b[i+4] << 3) | (b[i+5] >> 5); int sr = ASR[(b[i+2] >> 2) & 0xF]; if (len > 9 && sr > 0) { start = 7; dur = 1024.0 / sr; } else len = 0; }
      else if (b[i] == 0xFF && (b[i+1] & 0xFE) == 0xFA) { int br = BR[b[i+2] >> 4], sr = SR[(b[i+2] >> 2) & 3]; if (br > 0 && sr > 0) { len = 144000 * br / sr + ((b[i+2] >> 1) & 1); start = 4; dur = 1152.0 / sr; } }
      if (len == 0) { i++; continue; }
      if (i + len > b.Length) break;
      uint h = 2166136261; for (int k = i + start; k < i + len; k++) { h ^= b[k]; h *= 16777619; }
      f.Add(new KeyValuePair<uint,double>(h, dur)); i += len;
    }
    var g = new List<KeyValuePair<uint,double>>();
    for (int k = 0; k + 2 < f.Count; k++) g.Add(new KeyValuePair<uint,double>(unchecked(f[k].Key * 31 + f[k+1].Key * 17 + f[k+2].Key), f[k].Value));
    return g;
  }
  // Runs of the second connection's audio: { start s, end s, offset into the first connection in s (NaN: not in it) }.
  public static List<double[]> Runs(byte[] first, byte[] second) {
    var fa = Frames(first); var fb = Frames(second); var ta = new double[fa.Count]; double t = 0;
    var idx = new Dictionary<uint,int>(); for (int k = 0; k < fa.Count; k++) { ta[k] = t; t += fa[k].Value; if (!idx.ContainsKey(fa[k].Key)) idx[fa[k].Key] = k; }
    var runs = new List<double[]>(); double tb = 0;
    foreach (var f in fb) {
      int j; double off = idx.TryGetValue(f.Key, out j) ? Math.Round(ta[j] - tb) : double.NaN;
      var last = runs.Count > 0 ? runs[runs.Count - 1] : null;
      if (last != null && last[2].Equals(off)) last[1] = tb + f.Value; else runs.Add(new[] { tb, tb + f.Value, off });
      tb += f.Value;
    }
    runs.RemoveAll(r => r[1] - r[0] < 0.5);
    return runs;
  }
  public static double Seconds(byte[] b) { double t = 0; foreach (var f in Frames(b)) t += f.Value; return t; }
}
'@

$jobs = foreach ($id in $Intro) {
  $station = $stations | Where-Object id -eq $id
  if (-not $station) { Write-Warning "No station '$id'"; continue }
  $stream = $station.streams | Where-Object { $_.format -ne 'hls' -and $_.url -notmatch '\.m3u8' } | Select-Object -First 1
  if (-not $stream) { Write-Warning "$id has no plain stream to measure"; continue }
  foreach ($leg in 'first', 'second') { [pscustomobject]@{ Id = $id; Leg = $leg; Url = $stream.url; Intro = $stream.introSeconds } }
}
$downloads = $jobs | ForEach-Object -ThrottleLimit 16 -Parallel {
  $j = $_
  if ($j.Leg -eq 'second') { Start-Sleep -Seconds 20 }
  $seconds = if ($j.Leg -eq 'second') { 18 } else { 40 }
  $client = [System.Net.Http.HttpClient]::new(); $client.Timeout = [TimeSpan]::FromSeconds(70)
  $request = [System.Net.Http.HttpRequestMessage]::new('GET', $j.Url)
  $request.Headers.TryAddWithoutValidation('User-Agent', $using:UserAgent) | Out-Null
  $request.Headers.TryAddWithoutValidation('Icy-MetaData', '1') | Out-Null
  try {
    $resp = $client.SendAsync($request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
    $metaint = 0; $values = $null
    if ($resp.Headers.TryGetValues('icy-metaint', [ref]$values)) { $metaint = [int]($values | Select-Object -First 1) }
    $body = $resp.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
    $data = [IO.MemoryStream]::new(); $buffer = [byte[]]::new(16384); $clock = [Diagnostics.Stopwatch]::StartNew(); $firstSecond = -1
    while ($clock.Elapsed.TotalSeconds -lt $seconds) {
      $n = $body.Read($buffer, 0, $buffer.Length); if ($n -le 0) { break }
      if ($firstSecond -lt 0) { $clock.Restart(); $firstSecond = 0 }
      $data.Write($buffer, 0, $n)
      if ($firstSecond -eq 0 -and $clock.Elapsed.TotalSeconds -ge 1) { $firstSecond = $data.Length }
    }
    $resp.Dispose()
    [pscustomobject]@{ Id = $j.Id; Leg = $j.Leg; Intro = $j.Intro; Metaint = $metaint; Data = $data.ToArray(); FirstSecond = $firstSecond; Error = $null }
  } catch { [pscustomobject]@{ Id = $j.Id; Leg = $j.Leg; Data = $null; Error = $_.Exception.Message } } finally { $client.Dispose() }
}

foreach ($id in $Intro) {
  $first = $downloads | Where-Object { $_.Id -eq $id -and $_.Leg -eq 'first' }
  $second = $downloads | Where-Object { $_.Id -eq $id -and $_.Leg -eq 'second' }
  if (-not $first.Data -or -not $second.Data) { "{0}: download failed {1} {2}" -f $id, $first.Error, $second.Error; continue }
  $a = [OpeningClip]::StripIcy($first.Data, $first.Metaint)
  $b = [OpeningClip]::StripIcy($second.Data, $second.Metaint)
  $runs = [OpeningClip]::Runs($a, $b)
  $burst = if ($first.FirstSecond -gt 0) { [OpeningClip]::Seconds($a[0..([Math]::Min($a.Length, $first.FirstSecond) - 1)]) } else { 0 }
  $catalogIntro = if ($first.Intro) { "$($first.Intro) s" } else { 'none' }
  $verdict =
    if (-not $runs.Count -or -not ($runs | Where-Object { -not [double]::IsNaN($_[2]) })) { 'inconclusive: the two connections share no audio (different servers?)' }
    elseif ($runs[0][2] -eq 0) { 'fixed opening clip of {0:N1} s' -f $runs[0][1] }
    elseif ([double]::IsNaN($runs[0][2])) { 'opening clip of {0:N1} s that changes per connection' -f $runs[0][1] }
    else { 'no opening clip' }
  '{0}: {1}. Opening burst: {2:N0} s of audio in the first second. Catalog introSeconds: {3}' -f $id, $verdict, $burst, $catalogIntro
}
