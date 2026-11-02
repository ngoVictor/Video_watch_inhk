param([string[]]$Tasks = @(':core:test', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    $localJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools\jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
    if (!$env:JAVA_HOME -or !(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { throw 'JDK missing: configure JAVA_HOME or restore .tools/jdk.' }
    if (!$env:ANDROID_USER_HOME) { $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-user' }
    New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
    $probe = Join-Path $env:ANDROID_USER_HOME ('write-check-' + [guid]::NewGuid() + '.tmp')
    try { [IO.File]::WriteAllText($probe, ''); Remove-Item -LiteralPath $probe }
    catch { throw 'ANDROID_USER_HOME must point to a writable directory.' }
    if (!$env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-cache' }
    $localGradle = Join-Path $projectRoot '.tools\gradle-8.11.1\bin\gradle.bat'
    if (Test-Path -LiteralPath $localGradle) { & $localGradle @Tasks --console=plain }
    else { & '.\gradlew.bat' @Tasks --console=plain }
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally { Pop-Location }
