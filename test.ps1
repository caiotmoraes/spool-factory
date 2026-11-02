param([switch]$Live)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$toolsRoot=if($env:SPOOL_TOOLS_DIR){$env:SPOOL_TOOLS_DIR}else{Join-Path $PSScriptRoot 'tools'}
$jdk=(Get-ChildItem -LiteralPath "$toolsRoot/jdk" -Directory | Select-Object -First 1).FullName
$json=Join-Path $toolsRoot 'json.jar'
New-Item -ItemType Directory -Force build/tests | Out-Null
$sources=@('src/br/com/caiot/u1spool/EdgeDetector.java','src/br/com/caiot/u1spool/Api.java','src/br/com/caiot/u1spool/AssociationLedger.java','tests/EdgeDetectorTest.java','tests/ApiTest.java','tests/AssociationLedgerTest.java')
& "$jdk/bin/javac.exe" -encoding UTF-8 -cp $json -d build/tests @sources
if($LASTEXITCODE){throw 'Test compilation failed'}
$testArgs=@()
if($Live){if(!$env:SPOOL_PRINTER_URL -or !$env:SPOOLMAN_URL){throw 'Set SPOOL_PRINTER_URL and SPOOLMAN_URL for optional read-only integration checks.'};$testArgs=@($env:SPOOL_PRINTER_URL,$env:SPOOLMAN_URL)}
foreach($test in @('EdgeDetectorTest','ApiTest','AssociationLedgerTest')){
 $parameters=if($test -eq 'ApiTest'){$testArgs}else{@()}
 & "$jdk/bin/java.exe" -cp "build/tests;$json" "br.com.caiot.u1spool.$test" @parameters
 if($LASTEXITCODE){throw "Test failed: $test"}
}
