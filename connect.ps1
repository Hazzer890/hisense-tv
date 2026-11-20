# Reconnect adb to the Hisense TV. Finds it by MAC address, so it still works if the router hands out a new IP.
# usage: powershell -ExecutionPolicy Bypass -File connect.ps1
$adb = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
$mac = (Get-Content "$PSScriptRoot\tv-mac.txt" -TotalCount 1).Trim()   # kept out of git; format 00-11-22-33-44-55
$lastIp = '192.168.0.215'

function Test-Adb($ip) {
    $c = New-Object Net.Sockets.TcpClient
    try { return $c.ConnectAsync($ip, 5555).Wait(500) } catch { return $false } finally { $c.Close() }
}

$ip = $lastIp
if (-not (Test-Adb $ip)) {
    # Ping the whole /24 so the neighbour table fills, then look the TV up by MAC.
    $prefix = ((Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias Ethernet).IPAddress -replace '\.\d+$', '')
    $pings = 1..254 | ForEach-Object { (New-Object Net.NetworkInformation.Ping).SendPingAsync("$prefix.$_", 400) }
    [void][Threading.Tasks.Task]::WaitAll($pings)
    $ip = (Get-NetNeighbor -AddressFamily IPv4 | Where-Object LinkLayerAddress -eq $mac | Select-Object -First 1).IPAddress
    if (-not $ip) { Write-Error "TV ($mac) not found on $prefix.0/24. Is it switched on?"; exit 1 }
    if (-not (Test-Adb $ip)) { Write-Error "TV is at $ip but port 5555 is closed. Turn on USB debugging in Developer options."; exit 1 }
}

& $adb connect "${ip}:5555" | Out-Null
Start-Sleep 2
$state = ((& $adb devices) -match [regex]::Escape($ip)) -replace '^\S+\s+', ''
"TV at ${ip}:5555 is '$state'"
if ($state -ne 'device') {
    "Not authorised. Restart the TV, stay on the home screen, run this again, and accept 'Allow USB debugging' with 'Always allow' ticked."
    exit 1
}
