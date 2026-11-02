$ErrorActionPreference='Stop'
. (Join-Path (Split-Path $PSScriptRoot) 'signing-policy.ps1')
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('jiakan-signing-test-'+[guid]::NewGuid())
New-Item -ItemType Directory $fixture | Out-Null
$pinFile=Join-Path $fixture 'pin.txt'
$pin='a'*64
Set-Content -LiteralPath $pinFile $pin
$count=0
function ExpectFailure([scriptblock]$Action) {
    $failed=$false
    try { & $Action | Out-Null } catch { $failed=$true }
    if (!$failed) { throw 'Expected rejection did not occur.' }
}
try {
    foreach($mask in 0..3) {
        foreach($name in @('credentials.json','jiakan-release.jks')) { $path=Join-Path $fixture $name; if(Test-Path -LiteralPath $path){Remove-Item -LiteralPath $path} }
        if($mask -band 1){Set-Content (Join-Path $fixture 'credentials.json') '{}'}
        if($mask -band 2){Set-Content (Join-Path $fixture 'jiakan-release.jks') 'fixture'}
        if($mask -eq 3){if((Assert-SigningMaterials $fixture $pinFile) -ne $pin){throw 'Wrong pin'}}
        else{ExpectFailure {Assert-SigningMaterials $fixture $pinFile}}
        $count++
    }
    Assert-CertificatePin $pin $pin; $count++
    ExpectFailure {Assert-CertificatePin ('b'*64) $pin}; $count++
    $env:JIAKAN_STORE_PASSWORD='fixture-only'
    $jdk=(Get-ChildItem (Join-Path (Split-Path (Split-Path $PSScriptRoot)) '.tools/jdk') -Directory | Select-Object -First 1).FullName
    ExpectFailure {Assert-ReleaseKey $fixture $pinFile $jdk}; $count++
    $testKey=Join-Path $fixture 'jiakan-release.jks'
    Remove-Item -LiteralPath $testKey
    & (Join-Path $jdk 'bin/keytool.exe') -genkeypair -keystore $testKey -storetype JKS -alias wrong -keyalg RSA -keysize 2048 -validity 1 -dname 'CN=Disposable test' -storepass:env JIAKAN_STORE_PASSWORD -keypass:env JIAKAN_STORE_PASSWORD 2>&1 | Out-Null
    if($LASTEXITCODE -ne 0){throw 'Fixture generation failed'}
    ExpectFailure {Assert-ReleaseKey $fixture $pinFile $jdk}; $count++
    & (Join-Path $jdk 'bin/keytool.exe') -genkeypair -keystore $testKey -storetype JKS -alias jiakan -keyalg RSA -keysize 2048 -validity 1 -dname 'CN=Disposable test' -storepass:env JIAKAN_STORE_PASSWORD -keypass:env JIAKAN_STORE_PASSWORD 2>&1 | Out-Null
    if($LASTEXITCODE -ne 0){throw 'Fixture generation failed'}
    ExpectFailure {Assert-ReleaseKey $fixture $pinFile $jdk}; $count++
    $cert=Join-Path $fixture 'test.der'
    & (Join-Path $jdk 'bin/keytool.exe') -exportcert -keystore $testKey -alias jiakan -storepass:env JIAKAN_STORE_PASSWORD -file $cert 2>&1 | Out-Null
    if($LASTEXITCODE -ne 0){throw 'Fixture export failed'}
    Set-Content -LiteralPath $pinFile (Get-FileHash -LiteralPath $cert -Algorithm SHA256).Hash
    Assert-ReleaseKey $fixture $pinFile $jdk; $count++
    Write-Output "$count signing policy checks passed"
} finally {
    Remove-Item Env:JIAKAN_STORE_PASSWORD -ErrorAction SilentlyContinue
    Get-ChildItem -LiteralPath $fixture -File | ForEach-Object {Remove-Item -LiteralPath $_.FullName}
    Remove-Item -LiteralPath $fixture
}
