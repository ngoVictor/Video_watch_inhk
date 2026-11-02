param([Parameter(Mandatory=$true)][ValidatePattern('^emulator-[0-9]+$')][string]$Serial)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
if(!$env:ANDROID_USER_HOME){$env:ANDROID_USER_HOME=Join-Path $root '.tools/android-user'}
$adb=Join-Path $root '.tools/android-sdk/platform-tools/adb.exe'
$isEmulator=(& $adb -s $Serial shell getprop ro.kernel.qemu | Out-String).Trim()
$name=(& $adb -s $Serial emu avd name | Out-String)
if($isEmulator -ne '1' -or $name -notmatch '(?m)^jiakan_tv\r?$'){throw 'Only the explicitly selected JiaKan project emulator is supported.'}
$output=& $adb -s $Serial shell am instrument -w hk.jiakan.tv.test/hk.jiakan.tv.RepairInstrumentation 2>&1
$exitCode=$LASTEXITCODE
$output | Write-Output
if($exitCode -ne 0 -or ($output | Out-String) -notmatch 'PASS: [0-9]+ device assertions'){throw 'Device checks failed or produced no successful completion marker.'}
