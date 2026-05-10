#Requires -Version 5.0
<#
.SYNOPSIS
    在仓库 .debug-server 目录启动带 JDWP(5005) 的 Spigot/Paper 等服务端，可选先 Maven 构建并复制插件。

.PARAMETER WorkspaceRoot
    仓库根目录；默认为本脚本上级目录。

.PARAMETER Build
    启动前先执行 mvn clean package。

.PARAMETER Deploy
    将 target/XlingranAuth-*.jar 复制到 .debug-server/plugins。
#>
param(
    [string] $WorkspaceRoot = "",
    [switch] $Build,
    [switch] $Deploy
)

$ErrorActionPreference = "Stop"

function Write-Info([string] $msg) { Write-Host "[XlingranAuth:debug] $msg" }
function Write-ReadyMarker() { Write-Host "__DEBUG_SERVER_READY__" }

function Test-JdwpPortOpen {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $c.Connect("127.0.0.1", 5005)
        $c.Close()
        return $true
    } catch {
        return $false
    }
}

function Resolve-ServerJar {
    param([System.IO.FileInfo[]] $jars)
    if (-not $jars -or $jars.Count -eq 0) { return $null }

    $preferExact = @(
        "server.jar",
        "paperclip.jar"
    )
    foreach ($name in $preferExact) {
        $hit = $jars | Where-Object { $_.Name -ieq $name } | Select-Object -First 1
        if ($hit) { return $hit }
    }

    $preferLike = @(
        "paper-*.jar",
        "spigot-*.jar",
        "purpur-*.jar",
        "leaf-*.jar",
        "pufferfish-*.jar"
    )
    foreach ($pat in $preferLike) {
        $hit = $jars | Where-Object { $_.Name -like $pat } | Sort-Object Name | Select-Object -First 1
        if ($hit) { return $hit }
    }

    return $jars | Sort-Object Length -Descending | Select-Object -First 1
}

if (-not $WorkspaceRoot) {
    $WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}
$serverDir = Join-Path $WorkspaceRoot ".debug-server"
$pluginsDir = Join-Path $serverDir "plugins"

if (Test-JdwpPortOpen) {
    Write-Info "检测到 127.0.0.1:5005 已有监听，跳过启动 JVM（可直接附加调试）。"
    Write-ReadyMarker
    exit 0
}

if ($Build) {
    Write-Info "执行 Maven package（优先使用仓库内 Maven Wrapper）..."
    Push-Location $WorkspaceRoot
    try {
        $mvnwCmd = Join-Path $WorkspaceRoot "mvnw.cmd"
        if (Test-Path -LiteralPath $mvnwCmd) {
            & $mvnwCmd @("clean", "package", "-B", "-q")
        } elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
            & mvn @("clean", "package", "-B", "-q")
        } else {
            throw "未找到 mvnw.cmd 或全局 mvn。请将仓库完整克隆（含 Maven Wrapper）或安装 Apache Maven。"
        }
        if ($LASTEXITCODE -ne 0) { throw "Maven 退出码 $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

New-Item -ItemType Directory -Force -Path $pluginsDir | Out-Null

if ($Deploy) {
    Write-Info "复制插件到 .debug-server/plugins ..."
    $built = Get-ChildItem -Path (Join-Path $WorkspaceRoot "target") -Filter "XlingranAuth-*.jar" -File -ErrorAction SilentlyContinue
    if (-not $built) {
        throw "未找到 target/XlingranAuth-*.jar。请先 Build 或手动 mvn package。"
    }
    Copy-Item -Force $built.FullName -Destination $pluginsDir
}

$eula = Join-Path $serverDir "eula.txt"
if (-not (Test-Path -LiteralPath $eula)) {
    @"
# 由 XlingranAuth 调试脚本生成。请阅读 https://aka.ms/MinecraftEULA 后改为 eula=true
eula=false
"@ | Set-Content -LiteralPath $eula -Encoding UTF8
    throw "已生成 eula.txt（eula=false）。请阅读 Mojang EULA 后将该文件改为 eula=true 再重试。"
}
$eulaText = Get-Content -LiteralPath $eula -Raw
if ($eulaText -notmatch '(?im)^\s*eula\s*=\s*true\s*$') {
    throw "请在 $eula 中设置 eula=true 后再启动调试服。"
}

$jars = @(Get-ChildItem -LiteralPath $serverDir -Filter "*.jar" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.DirectoryName -eq $serverDir })
$jar = Resolve-ServerJar -jars $jars
if (-not $jar) {
    throw "在 $serverDir 下未找到服务端 jar。请放入 Paper / Spigot / Purpur 等可执行 jar（如 paper-1.21.x.jar、spigot-*.jar、server.jar）。"
}

$javaExe = $null
if ($env:DEBUG_JAVA_EXE) {
    $javaExe = $env:DEBUG_JAVA_EXE
}
elseif ($env:JAVA_HOME) {
    $cand = Join-Path $env:JAVA_HOME "bin/java.exe"
    if (Test-Path -LiteralPath $cand) { $javaExe = $cand }
    else {
        $cand2 = Join-Path $env:JAVA_HOME "bin/java"
        if (Test-Path -LiteralPath $cand2) { $javaExe = $cand2 }
    }
}
if (-not $javaExe) {
    $g = Get-Command java -ErrorAction SilentlyContinue
    if (-not $g) {
        throw "未找到 java。请安装 JDK 并配置 PATH，或设置 JAVA_HOME / DEBUG_JAVA_EXE（指向 java 可执行文件）。"
    }
    $javaExe = $g.Source
}

$mem = if ($env:DEBUG_MC_MEM) { $env:DEBUG_MC_MEM } else { "-Xms512M -Xmx1024M" }
$jdwp = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
$extra = if ($env:DEBUG_MC_JAVA_OPTS) { $env:DEBUG_MC_JAVA_OPTS } else { "" }

Write-Info "工作目录: $serverDir"
Write-Info "服务端: $($jar.Name)"
Write-Info "Java: $javaExe"
Write-Info "JDWP: 5005（在 Cursor 中选「附加到 Minecraft 服务端」或一键配置）"
Write-Host ""

Push-Location $serverDir
try {
    $argList = @()
    foreach ($t in ($mem -split '\s+')) { if ($t) { $argList += $t } }
    foreach ($t in ($extra -split '\s+')) { if ($t) { $argList += $t } }
    $argList += $jdwp
    $argList += "-jar"
    $argList += $jar.Name
    $argList += "nogui"

    & $javaExe @argList
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
