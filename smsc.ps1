
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ────────────────── Helper for ADB  ──────────────────
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Error "adb.exe not found. Add it to PATH or copy next to this script."; exit
}

# ────────────────── 1. prompts ───────────────────────────
Function Read-PositiveInt ($prompt, $default) {
    do {
        $val = Read-Host "$prompt [$default]"
        if ([string]::IsNullOrWhiteSpace($val)) { $val = $default }
    } while (-not $val -match '^[1-9][0-9]*$')
    return [int]$val
}

$recvCount = Read-PositiveInt "How many recent RECEIVED SMS" 5
$includeSent = (Read-Host "Include SENT messages as well? (y/N)").ToLower() -eq 'y'
$sentCount = 0
if ($includeSent) { $sentCount = Read-PositiveInt "How many recent SENT SMS" 1 }

$exportCsv = (Read-Host "Export to CSV file too? (y/N)").ToLower() -eq 'y'
$csvPath   = ""
if ($exportCsv) {
    $csvPath = Read-Host "Enter CSV file path + file name .csv" 
    if (-not $csvPath) { $csvPath = "$PSScriptRoot\sms_export.csv" }
    "type,id,address,smsc,date,body" | Out-File -FilePath $csvPath -Encoding UTF8
}

# ────────────────── 2. Functions  ────────────────────
Function Invoke-ContentQuery ($uri) {
    & adb shell "content query --uri $uri --projection _id,address,service_center,date,body --sort 'date DESC'"
}

Function Aggregate-Rows ([string[]]$rawLines) {
    $rows = [System.Collections.Generic.List[string]]::new()
    $current = ""
    foreach ($line in $rawLines) {
        if ($line -match '^Row:\s+') {
            if ($current) { $rows.Add($current) }
            $current = $line -replace '^Row:\s+', ''
        } else {
            $current += " $line"
        }
    }
    if ($current) { $rows.Add($current) }
    return ,$rows  
}

Function Print-And-Export ($rows, $limit, $label) {
    Write-Host "`n== $($label.ToUpper()) SMS ($limit requested) ==" -ForegroundColor Cyan
    $rows = $rows | Select-Object -First $limit
    foreach ($row in $rows) {
        $id    = ($row -split ' ')[0] -replace '_id=', ''
        $addr  = ($row -match 'address=[^ ]+')  ? ($Matches[0] -replace 'address=','') : ''
        $smsc  = ($row -match 'service_center=[^ ]+') ? ($Matches[0] -replace 'service_center=','') : ''
        $epoch = ($row -match 'date=[0-9]+') ? ($Matches[0] -replace 'date=','') : 0
        $body  = ($row -split 'body=')[1]

        $date  = [DateTimeOffset]::FromUnixTimeMilliseconds([int64]$epoch).ToOffset(
                 [System.TimeZoneInfo]::FindSystemTimeZoneById("W. Europe Standard Time").BaseUtcOffset).ToString("yyyy-MM-dd HH:mm:ss")

        Write-Host "----------------------------------------"
        Write-Host "ID:    $id"
        Write-Host "From:  $addr"
        Write-Host "SMSC:  $(if ($smsc) { $smsc } else { '<none>' })"
        Write-Host "Date:  $date"
        Write-Host "Body:  $body"

        if ($exportCsv) {
            '"{0}","{1}","{2}","{3}","{4}","{5}"' -f $label,$id,$addr,$smsc,$date,
                ($body -replace '"','""') | Out-File -FilePath $csvPath -Append -Encoding UTF8
        }
    }
    Write-Host "----------------------------------------"
}

# ────────────────── 3. Grant READ_SMS  ─────────────────────
& adb wait-for-device | Out-Null
$hasRead = & adb shell appops get --uid shell READ_SMS 2>&1
if ($hasRead -notmatch 'allow') {
    & adb shell cmd appops set --uid shell READ_SMS allow 2>&1 | Out-Null
}

# ────────────────── 4. Fetch and display ───────────────────────────────
$inboxRaw = Invoke-ContentQuery 'content://sms/inbox'
$inRows   = Aggregate-Rows $inboxRaw
Print-And-Export $inRows $recvCount "received"

if ($includeSent -and $sentCount -gt 0) {
    $sentRaw = Invoke-ContentQuery 'content://sms/sent'
    $sRows   = Aggregate-Rows $sentRaw
    Print-And-Export $sRows $sentCount "sent"
}

if ($exportCsv) { Write-Host "`nCSV saved to $csvPath" -ForegroundColor Green }
