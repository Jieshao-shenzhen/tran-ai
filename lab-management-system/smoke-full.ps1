$ErrorActionPreference = "Stop"
$dir = "c:\Users\Administrator\Documents\trae_projects\tran-ai\lab-management-system"
Set-Location $dir

function Cleanup {
    Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*spring-boot:run*' } | ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

# 1. 独立进程启动两个服务
$biz = Start-Process -FilePath "cmd.exe" -WorkingDirectory $dir -PassThru -WindowStyle Hidden -ArgumentList "/c", "cd /d `"$dir`" && mvnw.cmd -f pom.xml -pl lab-business-service spring-boot:run > run-business.log 2>&1"
$rep = Start-Process -FilePath "cmd.exe" -WorkingDirectory $dir -PassThru -WindowStyle Hidden -ArgumentList "/c", "cd /d `"$dir`" && mvnw.cmd -f pom.xml -pl lab-report-service spring-boot:run > run-report.log 2>&1"
"STARTED cmd biz=$($biz.Id) rep=$($rep.Id)"

# 2. 轮询端口
$deadline = (Get-Date).AddSeconds(120)
$bizOk = $false
$repOk = $false
while ((Get-Date) -lt $deadline -and (-not $bizOk -or -not $repOk)) {
    if (-not $bizOk) { $bizOk = [bool](Get-NetTCPConnection -State Listen -LocalPort 9300 -ErrorAction SilentlyContinue) }
    if (-not $repOk) { $repOk = [bool](Get-NetTCPConnection -State Listen -LocalPort 9400 -ErrorAction SilentlyContinue) }
    if (-not $bizOk -or -not $repOk) { Start-Sleep -Seconds 3 }
}
"BIZ_OK=$bizOk REP_OK=$repOk"

if (-not ($bizOk -and $repOk)) {
    "---- run-business tail ----"
    if (Test-Path run-business.log) { Get-Content run-business.log -Tail 30 }
    "---- run-report tail ----"
    if (Test-Path run-report.log) { Get-Content run-report.log -Tail 30 }
    Cleanup
    exit 2
}

# 3. 冒烟：预约 -> 审批 -> 报修 -> 派单 -> 完工 -> 统计
$base = "http://localhost:9300"
$h1 = @{ "X-User-Id" = "5"; "Content-Type" = "application/json" }
$r1 = Invoke-RestMethod -Method Post -Uri "$base/api/v1/reservations" -Headers $h1 -Body '{"roomId":1,"purpose":"x","startTime":"2026-09-09T09:00:00","endTime":"2026-09-09T11:00:00","peopleNum":2}'
$id = $r1.data.id
"S1 create reservation id=$id"
$r2 = Invoke-RestMethod -Method Post -Uri "$base/api/v1/reservations/$id/approve" -Headers @{ "X-User-Id" = "9" }
"S2 approve code=$($r2.code)"
$h3 = @{ "X-User-Id" = "3"; "Content-Type" = "application/json" }
$r3 = Invoke-RestMethod -Method Post -Uri "$base/api/v1/repairs" -Headers $h3 -Body '{"deviceId":2,"roomId":1,"description":"kbd"}'
$rid = $r3.data.id
"S3 create repair id=$rid"
$r4 = Invoke-RestMethod -Method Post -Uri "$base/api/v1/repairs/$rid/assign?assigneeId=7"
"S4 assign code=$($r4.code)"
$r5 = Invoke-RestMethod -Method Post -Uri "$base/api/v1/repairs/$rid/finish" -Headers @{ "Content-Type" = "application/json" } -Body '{"result":"fixed"}'
"S5 finish code=$($r5.code)"

Start-Sleep -Seconds 3

$ov = Invoke-RestMethod -Method Get -Uri "http://localhost:9400/api/v1/stats/overview"
"S6 overview code=$($ov.code) totalApproved=$($ov.data.totalApproved) totalUsed=$($ov.data.totalUsed) repairCompleted=$($ov.data.repairCompleted)"

$ru = Invoke-RestMethod -Method Get -Uri "http://localhost:9400/api/v1/stats/room-usage?from=2026-09-01&to=2026-09-30"
"S7 room-usage code=$($ru.code) count=$($ru.data.Count) rows=$($ru.data | ConvertTo-Json -Compress)"

# 4. 清理两个服务
"STOPPING SERVICES"
Cleanup
Start-Sleep -Seconds 4
$b2 = [bool](Get-NetTCPConnection -State Listen -LocalPort 9300 -ErrorAction SilentlyContinue)
$r2 = [bool](Get-NetTCPConnection -State Listen -LocalPort 9400 -ErrorAction SilentlyContinue)
if ($b2) { "9300 STILL UP" } else { "9300 RELEASED" }
if ($r2) { "9400 STILL UP" } else { "9400 RELEASED" }
exit 0