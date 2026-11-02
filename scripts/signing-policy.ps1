# Public certificate pin; never establish trust from the key being checked.
function Assert-SigningMaterials([string]$Directory, [string]$PinFile) {
    foreach ($name in @('credentials.json', 'jiakan-release.jks')) {
        if (!(Test-Path -LiteralPath (Join-Path $Directory $name) -PathType Leaf)) { throw "Missing signing material: $name. Restore the original backup." }
    }
    if (!(Test-Path -LiteralPath $PinFile -PathType Leaf)) { throw 'Missing historical release certificate pin.' }
    $pin = (Get-Content -LiteralPath $PinFile -Raw).Trim().ToLowerInvariant()
    if ($pin -notmatch '^[0-9a-f]{64}$') { throw 'Invalid release certificate pin.' }
    return $pin
}
function Assert-CertificatePin([string]$Actual, [string]$Expected) {
    if ($Actual.ToLowerInvariant() -cne $Expected.ToLowerInvariant()) { throw 'Signing certificate differs from the historical release. Restore the original key.' }
}
function Assert-ReleaseKey([string]$Directory, [string]$PinFile, [string]$JavaDirectory) {
    $pin = Assert-SigningMaterials $Directory $PinFile
    $certificate = Join-Path ([IO.Path]::GetTempPath()) ('jiakan-cert-' + [guid]::NewGuid() + '.der')
    try {
        & (Join-Path $JavaDirectory 'bin\keytool.exe') -exportcert -alias jiakan -keystore (Join-Path $Directory 'jiakan-release.jks') -storepass:env JIAKAN_STORE_PASSWORD -file $certificate 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Cannot export release certificate: verify alias and credentials.' }
        Assert-CertificatePin (Get-FileHash -LiteralPath $certificate -Algorithm SHA256).Hash $pin
    } finally { if (Test-Path -LiteralPath $certificate) { Remove-Item -LiteralPath $certificate } }
}
