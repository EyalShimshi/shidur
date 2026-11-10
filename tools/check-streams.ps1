<#
.SYNOPSIS
Checks the station list's stream and logo addresses, and measures stations' opening clips.

.DESCRIPTION
Needs only PowerShell 7; nothing to install. Requests use the app's style of User-Agent,
because some servers decide by User-Agent whether to add an opening ad (CyberCDN adds clips
for browsers but not for apps).

  ./tools/check-streams.ps1                        # every address: status, type, server
  ./tools/check-streams.ps1 -Report report.md      # only problems, as Markdown; exit code 1 if any
  ./tools/check-streams.ps1 -Intro 102fm-tlv,kan-bet
  ./tools/check-streams.ps1 -Intro all             # every station's opening clip

-Report is what the weekly "Station check" workflow runs. Failing addresses are tried twice.
The "Measure opening clips" workflow runs -Intro from GitHub's servers, for stations your own
network blocks.

-Intro measures each station's first plain (non-HLS) stream: it connects twice, 20 seconds
apart, and matches the audio frames. Audio that both connections start with is a fixed clip
the server sends every new listener. Audio that only the second connection has, before it
joins the first one's timeline, is a clip that changes per connection (a rotating ad).
#>
param(
  [string[]]$Intro,
  [string]$Report,
  [string]$Catalog = (Join-Path $PSScriptRoot '..\app\src\main\assets\catalog.json'),
  [string]$UserAgent = 'Shidur/1.0 (Android; +https://github.com/EyalShimshi/shidur)'
)

$ErrorActionPreference = 'Stop'
$stations = (Get-Content $Catalog -Raw -Encoding UTF8 | ConvertFrom-Json).stations

function Test-Addresses($targets) {
  $targets | ForEach-Object -ThrottleLimit 10 -Parallel {
    $t = $_
    # Redirects are followed here, like the app does, including from https to http.
    $handler = [System.Net.Http.HttpClientHandler]::new(); $handler.AllowAutoRedirect = $false
    $client = [System.Net.Http.HttpClient]::new($handler); $client.Timeout = [TimeSpan]::FromSeconds(15)
    $client.DefaultRequestHeaders.UserAgent.ParseAdd($using:UserAgent)
    try {
      $url = [Uri]$t.Url
      for ($hop = 0; ; $hop++) {
        $resp = $client.GetAsync($url, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
        $status = [int]$resp.StatusCode
        if ($status -lt 300 -or $status -ge 400 -or -not $resp.Headers.Location -or $hop -ge 5) { break }
        $url = [Uri]::new($url, $resp.Headers.Location)
        $resp.Dispose()
      }
      $type = if ($resp.Content.Headers.ContentType) { $resp.Content.Headers.ContentType.MediaType } else { '' }
      $resp.Dispose()
      $problem =
        if ($status -eq 429) { $null }  # Rate-limited (Wikimedia limits GitHub's shared servers), not broken.
        elseif ($status -ge 400) { "HTTP $status" }
        elseif ($t.Kind -eq 'logo' -and $type -notlike 'image/*') { "not an image ($type)" }
        elseif ($t.Kind -eq 'stream' -and $type -like 'text/html*') { 'a web page, not audio' }
        else { $null }
      $t | Add-Member -NotePropertyMembers @{ Status = $status; Type = $type; Note = $url.Host; Problem = $problem } -PassThru
    } catch {
      $e = $_.Exception; while ($e.InnerException) { $e = $e.InnerException }
      $t | Add-Member -NotePropertyMembers @{ Status = 'ERR'; Type = ''; Note = $e.Message; Problem = $e.Message } -PassThru
    } finally { $client.Dispose() }
  }
}

if (-not $Intro) {
  $targets = foreach ($s in $stations) {
    $i = 0
    foreach ($st in $s.streams) { [pscustomobject]@{ Station = $s.id; Kind = 'stream'; Index = $i; Url = $st.url }; $i++ }
    if ($s.logo) { [pscustomobject]@{ Station = $s.id; Kind = 'logo'; Index = 0; Url = $s.logo } }
  }
  $results = @(Test-Addresses $targets)
  if (-not $Report) {
    $results | Sort-Object Station, Kind, Index | Format-Table Station, Kind, Index, Status, Type, Note -AutoSize -Wrap
    'Behind a proxy that buffers plain-http responses, http:// addresses can time out even when they work.'
    return
  }
  # A second chance for anything that failed, so a passing hiccup doesn't open an issue.
  $failed = @($results | Where-Object Problem)
  if ($failed) {
    Start-Sleep -Seconds 20
    $retried = @(Test-Addresses ($failed | Select-Object Station, Kind, Index, Url))
    $results = @($results | Where-Object { -not $_.Problem }) + $retried
  }
  $problems = @($results | Where-Object Problem)
  $down = foreach ($group in $results | Where-Object Kind -eq 'stream' | Group-Object Station) {
    if (-not ($group.Group | Where-Object { -not $_.Problem })) { $group.Name }
  }
  $lines = [System.Collections.Generic.List[string]]::new()
  $lines.Add("Checked $($results.Count) addresses on $((Get-Date).ToUniversalTime().ToString('yyyy-MM-dd')).")
  $lines.Add('')
  if (-not $problems) {
    $lines.Add('Every stream and logo address works.')
  } else {
    if ($down) { $lines.Add("**Stations that don't play at all:** $(($down | Sort-Object) -join ', ')"); $lines.Add('') }
    $lines.Add('| Station | Kind | # | Problem | Address |')
    $lines.Add('|---|---|---|---|---|')
    foreach ($p in $problems | Sort-Object Station, Kind, Index) {
      $lines.Add("| $($p.Station) | $($p.Kind) | $($p.Index) | $($p.Problem -replace '\|', '/') | $($p.Url) |")
    }
    $lines.Add('')
    $lines.Add('`#` is the position in the station''s `streams` list (0 is tried first). To fix: edit `app/src/main/assets/catalog.json` and raise `catalogVersion` (see MAINTAINING.md → "Fix a station"). Some servers block requests from outside Israel, so confirm a failure on a phone before removing an address.')
  }
  Set-Content -Path $Report -Value $lines -Encoding utf8NoBOM
  $lines
  if ($problems) { exit 1 }
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

$jobs = foreach ($id in $(if ($Intro -contains 'all') { $stations.id } else { $Intro })) {
  $station = $stations | Where-Object id -eq $id
  if (-not $station) { Write-Warning "No station '$id'"; continue }
  $stream = $station.streams | Where-Object { $_.format -ne 'hls' -and $_.url -notmatch '\.m3u8' } | Select-Object -First 1
  if (-not $stream) { Write-Warning "$id has no plain stream to measure"; continue }
  foreach ($leg in 'first', 'second') { [pscustomobject]@{ Id = $id; Leg = $leg; Url = $stream.url; Intro = $stream.introSeconds } }
}
# Every connection at once, so each station's second connection starts 20 seconds after its first.
$downloads = $jobs | ForEach-Object -ThrottleLimit ([Math]::Min(64, [Math]::Max(16, @($jobs).Count))) -Parallel {
  $j = $_
  if ($j.Leg -eq 'second') { Start-Sleep -Seconds 20 }
  $seconds = if ($j.Leg -eq 'second') { 18 } else { 40 }
  # Redirects are followed by hand, like the app does, including from https to http.
  $handler = [System.Net.Http.HttpClientHandler]::new(); $handler.AllowAutoRedirect = $false
  $client = [System.Net.Http.HttpClient]::new($handler); $client.Timeout = [TimeSpan]::FromSeconds(70)
  try {
    $url = [Uri]$j.Url
    for ($hop = 0; ; $hop++) {
      $request = [System.Net.Http.HttpRequestMessage]::new('GET', $url)
      $request.Headers.TryAddWithoutValidation('User-Agent', $using:UserAgent) | Out-Null
      $request.Headers.TryAddWithoutValidation('Icy-MetaData', '1') | Out-Null
      $resp = $client.SendAsync($request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
      $status = [int]$resp.StatusCode
      if ($status -lt 300 -or $status -ge 400 -or -not $resp.Headers.Location -or $hop -ge 5) { break }
      $url = [Uri]::new($url, $resp.Headers.Location)
      $resp.Dispose()
    }
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

foreach ($id in ($jobs | Select-Object -ExpandProperty Id -Unique)) {
  $first = $downloads | Where-Object { $_.Id -eq $id -and $_.Leg -eq 'first' }
  $second = $downloads | Where-Object { $_.Id -eq $id -and $_.Leg -eq 'second' }
  if (-not $first.Data -or -not $second.Data) { "{0}: download failed {1} {2}" -f $id, $first.Error, $second.Error; continue }
  $a = [OpeningClip]::StripIcy($first.Data, $first.Metaint)
  $b = [OpeningClip]::StripIcy($second.Data, $second.Metaint)
  $runs = [OpeningClip]::Runs($a, $b)
  $burst = if ($first.FirstSecond -gt 0) { [OpeningClip]::Seconds($a[0..([Math]::Min($a.Length, $first.FirstSecond) - 1)]) } else { 0 }
  $kbps = [Math]::Round($a.Length * 8 / [Math]::Max(1, [OpeningClip]::Seconds($a)) / 1000)
  $catalogIntro = if ($first.Intro) { "$($first.Intro) s" } else { 'none' }
  $verdict =
    if (-not $runs.Count -or -not ($runs | Where-Object { -not [double]::IsNaN($_[2]) })) { 'inconclusive: the two connections share no audio (different servers?)' }
    elseif ($runs[0][2] -eq 0) { 'fixed opening clip of {0:N1} s' -f $runs[0][1] }
    elseif ([double]::IsNaN($runs[0][2])) { 'opening clip of {0:N1} s that changes per connection' -f $runs[0][1] }
    else { 'no opening clip' }
  '{0}: {1}. Opening burst: {2:N0} s of audio in the first second; {3} kbps. Catalog introSeconds: {4}' -f $id, $verdict, $burst, $kbps, $catalogIntro
}
