param([int]$VersionCode = 20, [string]$VersionName = '0.5.0-alpha', [string]$PrivateCatalogDir = '', [string]$SigningDirectory = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    $localJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools\jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
    . (Join-Path $PSScriptRoot 'signing-policy.ps1')
    if (!$SigningDirectory) { $SigningDirectory = Join-Path $projectRoot '.tools\signing' }
    $pinFile = Join-Path $projectRoot 'publishing\signing-certificate.sha256'
    $null = Assert-SigningMaterials $signingDirectory $pinFile
    $passwordFile = Join-Path $signingDirectory 'credentials.json'
    $keystoreFile = Join-Path $signingDirectory 'jiakan-release.jks'
    $credentials = Get-Content -LiteralPath $passwordFile -Raw | ConvertFrom-Json
    $env:JIAKAN_STORE_PASSWORD = $credentials.password
    $env:JIAKAN_KEYSTORE = $keystoreFile
    Assert-ReleaseKey $signingDirectory $pinFile $env:JAVA_HOME
    if ($VersionCode -lt 1 -or $VersionName -notmatch '^[A-Za-z0-9._-]+$') { throw 'Invalid release version.' }
    $buildTasks = @(':core:test', ':app:testReleaseUnitTest', ':app:lintRelease', ':app:assembleRelease', "-PappVersionCode=$VersionCode", "-PappVersionName=$VersionName")
    if ($PrivateCatalogDir) { $buildTasks += "-PprivateCatalogDir=$PrivateCatalogDir" }
    & (Join-Path $PSScriptRoot 'build.ps1') -Tasks $buildTasks
    $output = Join-Path $projectRoot 'artifacts\release'
    New-Item -ItemType Directory -Force $output | Out-Null
    $variant = if ($PrivateCatalogDir) { "-private" } else { "" }
    $apk = Join-Path $output "jiakan-tv-$VersionName$variant.apk"
    $built = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
    python (Join-Path $PSScriptRoot 'release_metadata.py') --apk $built --version-code $VersionCode --version-name $VersionName
    if ($LASTEXITCODE -ne 0) { throw 'APK identity verification failed' }
    if ((Test-Path -LiteralPath $apk) -and (Get-FileHash -LiteralPath $apk).Hash -ne (Get-FileHash -LiteralPath $built).Hash) { throw 'A different APK already exists for this version; choose a new version.' }
    if (!(Test-Path -LiteralPath $apk)) { Copy-Item -LiteralPath $built -Destination $apk }
    $sha = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath "$apk.sha256" -Value "$sha  $(Split-Path -Leaf $apk)"
    Write-Output "Signed APK: $apk"
} finally {
    Remove-Item Env:JIAKAN_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:JIAKAN_KEYSTORE -ErrorAction SilentlyContinue
    Pop-Location
}
