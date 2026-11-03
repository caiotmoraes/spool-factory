$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$translationCatalog=Get-Content -LiteralPath 'assets/translations.json' -Raw
("'use strict';`nwindow.TRANSLATIONS="+$translationCatalog.Trim()+";`n") | Set-Content -LiteralPath 'assets/i18n-data.js' -Encoding utf8
$toolsRoot=if($env:SPOOL_TOOLS_DIR){$env:SPOOL_TOOLS_DIR}else{Join-Path $PSScriptRoot 'tools'}
$jdk=(Get-ChildItem -LiteralPath "$toolsRoot/jdk" -Directory | Select-Object -First 1).FullName
$bt=(Get-ChildItem -LiteralPath "$toolsRoot/build" -Directory | Select-Object -First 1).FullName
$platform=(Get-ChildItem -LiteralPath "$toolsRoot/platform" -Directory | Select-Object -First 1).FullName
$android=Join-Path $platform 'android.jar'
[xml]$manifest=Get-Content -LiteralPath AndroidManifest.xml
$version=$manifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
$output="release/Spool-Factory-$version.apk"
function Run([string]$exe,[string[]]$arguments){& $exe @arguments;if($LASTEXITCODE -ne 0){throw "Build failed: $exe ($LASTEXITCODE)"}}
foreach($d in @('build','build/generated','build/classes','build/dex','release','signing')){New-Item -ItemType Directory -Force $d | Out-Null}
Run "$bt/aapt2.exe" @('compile','--dir','res','-o','build/resources.zip')
Run "$bt/aapt2.exe" @('link','-o','build/unsigned.apk','--manifest','AndroidManifest.xml','-I',$android,'-A','assets','--java','build/generated','build/resources.zip')
$sources=@(Get-ChildItem -Recurse src,build/generated -Filter '*.java' | ForEach-Object {$_.FullName})
Run "$jdk/bin/javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-classpath',$android,'-d','build/classes')+$sources)
Run "$jdk/bin/jar.exe" @("-J-Djava.io.tmpdir=$PSScriptRoot/build",'cf','build/classes.jar','-C','build/classes','.')
Run "$jdk/bin/java.exe" @('-cp',"$bt/lib/d8.jar",'com.android.tools.r8.D8','--lib',$android,'--min-api','26','--output','build/dex','build/classes.jar')
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk=[IO.Compression.ZipFile]::Open((Join-Path $PSScriptRoot 'build/unsigned.apk'),[IO.Compression.ZipArchiveMode]::Update)
try{$old=$apk.GetEntry('classes.dex');if($old){$old.Delete()};[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apk,(Join-Path $PSScriptRoot 'build/dex/classes.dex'),'classes.dex') | Out-Null}finally{$apk.Dispose()}
Run "$bt/zipalign.exe" @('-f','-p','4','build/unsigned.apk','build/aligned.apk')
$key=if($env:SPOOL_SIGNING_KEY){$env:SPOOL_SIGNING_KEY}else{Join-Path $PSScriptRoot 'signing/development.p12'}
$alias=if($env:SPOOL_SIGNING_ALIAS){$env:SPOOL_SIGNING_ALIAS}else{'spool-factory'}
if(!$env:SPOOL_SIGNING_KEY){
 $passwordPath=Join-Path $PSScriptRoot 'signing/development-password.txt'
 if(!(Test-Path -LiteralPath $passwordPath)){[guid]::NewGuid().ToString('N') | Set-Content -LiteralPath $passwordPath}
 $env:SPOOL_SIGNING_PASSWORD=(Get-Content -LiteralPath $passwordPath -Raw).Trim()
 if(!(Test-Path -LiteralPath $key)){Run "$jdk/bin/keytool.exe" @('-genkeypair','-keystore',$key,'-storetype','PKCS12','-storepass:env','SPOOL_SIGNING_PASSWORD','-keypass:env','SPOOL_SIGNING_PASSWORD','-alias',$alias,'-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Spool Factory Development')}
}
if(!$env:SPOOL_SIGNING_PASSWORD){throw 'Set SPOOL_SIGNING_PASSWORD for the selected signing key.'}
Run "$jdk/bin/java.exe" @('-jar',"$bt/lib/apksigner.jar",'sign','--ks',$key,'--ks-pass','env:SPOOL_SIGNING_PASSWORD','--key-pass','env:SPOOL_SIGNING_PASSWORD','--ks-key-alias',$alias,'--out',$output,'build/aligned.apk')
Run "$jdk/bin/java.exe" @('-jar',"$bt/lib/apksigner.jar",'verify','--verbose','--print-certs',$output)
Run "$bt/aapt2.exe" @('dump','badging',$output)
Copy-Item -LiteralPath $output -Destination 'release/Spool-Factory.apk' -Force
$hash=(Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLower()
"$hash  Spool-Factory.apk" | Set-Content -Encoding ascii 'release/SHA256SUMS.txt'
