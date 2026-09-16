#requires -Version 5.1
param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw '请在 Windows 上运行此脚本。' }
if (-not $JdkHome) { throw '请设置 JAVA_HOME 或传入 -JdkHome，指向 BellSoft Liberica JDK 17 Full（Windows x64）。' }
$JdkHome = (Resolve-Path -LiteralPath $JdkHome).Path
$env:JAVA_HOME = $JdkHome
$projectDir = Split-Path -Parent $PSScriptRoot
Set-Location $projectDir
$appName = '欢欢'
$appVersion = ([xml](Get-Content -LiteralPath (Join-Path $projectDir 'pom.xml') -Raw)).project.version
if ($appVersion -notmatch '^\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?$') { throw 'pom.xml 中的版本号格式不合法。' }
$iconFile = Join-Path $projectDir 'packaging\icons\huanhuan.ico'
if (-not (Test-Path -LiteralPath $iconFile -PathType Leaf)) { throw "缺少应用图标：$iconFile" }

# Windows 7 兼容性依赖具体发行版及其 JavaFX 原生库，不能只检查 Java 主版本。
$releaseInfo = Get-Content -LiteralPath "$JdkHome\release" -Raw
if ($releaseInfo -notmatch '(?m)^IMPLEMENTOR="BellSoft"' -or
    $releaseInfo -notmatch '(?m)^JAVA_VERSION="17\.' -or
    $releaseInfo -notmatch '(?m)^OS_ARCH="(amd64|x86_64)"' -or
    $releaseInfo -notmatch '(?m)^OS_NAME="Windows"') {
    throw 'Windows 7 版本必须使用 BellSoft Liberica JDK 17 Full，Windows x64 发行包。'
}
foreach ($tool in @('java.exe', 'javac.exe', 'jlink.exe')) {
    if (-not (Test-Path -LiteralPath "$JdkHome\bin\$tool" -PathType Leaf)) { throw "JDK 缺少 $tool。" }
}
foreach ($module in @('javafx.base', 'javafx.graphics', 'javafx.controls')) {
    if (-not (Test-Path -LiteralPath "$JdkHome\jmods\$module.jmod" -PathType Leaf)) {
        throw "缺少 $module 模块，请下载 Liberica JDK 17 的 Full 版（不是 Standard 或 Lite）。"
    }
}
$modules = & "$JdkHome\bin\java.exe" --list-modules
if ($LASTEXITCODE -ne 0 -or -not ($modules -match '^javafx.controls@17\.')) { throw '需要包含 JavaFX 17 的 Liberica JDK 17 Full。' }

# 测试也加载 Full 发行版自带的 JavaFX 模块，与最终运行时保持一致。
& .\mvnw.cmd -B '-DargLine=--add-modules=javafx.controls' clean package
if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败。' }

$imageDir = Join-Path $projectDir 'target\package-image'
# 英文 Windows 上 JDK 17 / Launch4j 的原生工具可能丢失中文命令行路径。
# 目录保持 ASCII 名称，避免内置 JDK 17 在英文系统中找不到 java.dll；EXE 保留中文名称。
$appDir = Join-Path $imageDir 'huanhuan'
$inputDir = Join-Path $appDir 'app'
$runtimeDir = Join-Path $appDir 'runtime'
$distDir = Join-Path $projectDir 'dist'
New-Item -ItemType Directory -Force -Path "$inputDir\lib", $distDir | Out-Null
Copy-Item 'target\record-timeliness.jar' $inputDir
Get-ChildItem 'target\lib\*.jar' | Where-Object { $_.Name -notlike 'javafx-*' } | Copy-Item -Destination "$inputDir\lib"

# 将 Liberica 自带 JavaFX 和原生库一同加入运行时，不混入 Maven 的 Windows JavaFX 二进制。
& "$JdkHome\bin\jlink.exe" --module-path "$JdkHome\jmods" `
    --add-modules java.se,jdk.unsupported,jdk.crypto.ec,javafx.controls `
    --strip-debug --no-header-files --no-man-pages --output $runtimeDir
if ($LASTEXITCODE -ne 0) { throw '运行时组装失败。' }
$runtimeModules = & "$runtimeDir\bin\java.exe" --list-modules
if ($LASTEXITCODE -ne 0 -or -not ($runtimeModules -match '^javafx.controls@17\.')) { throw '生成的运行时缺少 JavaFX 17。' }

# Launch4j 生成兼容旧系统的启动器；不要替换成 JDK 21 的 jpackage 启动器。
& .\mvnw.cmd -B -Pwindows-launcher launch4j:launch4j
if ($LASTEXITCODE -ne 0) { throw 'EXE 启动器生成失败。' }
if (-not (Test-Path -LiteralPath (Join-Path $appDir 'huanhuan.exe') -PathType Leaf)) { throw '未找到生成的 EXE。' }
Rename-Item -LiteralPath (Join-Path $appDir 'huanhuan.exe') -NewName "$appName.exe"
$archive = Join-Path $distDir "$appName-$appVersion-windows-x64.zip"
Compress-Archive -LiteralPath $appDir -DestinationPath $archive -Force
Write-Host "已生成：$archive；解压后运行 huanhuan\$appName.exe。目标：Windows 7 SP1 / 10 / 11 x64。"
