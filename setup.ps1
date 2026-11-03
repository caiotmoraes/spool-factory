$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
New-Item -ItemType Directory -Force tools | Out-Null
function FetchZip($url,$file,$destination,$algorithm,$hash){
 Invoke-WebRequest $url -OutFile $file
 if((Get-FileHash $file -Algorithm $algorithm).Hash.ToLower() -ne $hash.ToLower()){throw "Checksum inválido: $file"}
 Expand-Archive -LiteralPath $file -DestinationPath $destination -Force
}
if(!(Test-Path tools/jdk)){
 $metadata=Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
 FetchZip $metadata[0].binary.package.link 'tools/jdk.zip' 'tools/jdk' 'SHA256' $metadata[0].binary.package.checksum
}
if(!(Test-Path tools/platform)){
 FetchZip 'https://dl.google.com/android/repository/platform-35_r02.zip' 'tools/platform.zip' 'tools/platform' 'SHA1' '0bb560a90a7a2cbd0dd8348224d518b638fe7949'
}
if(!(Test-Path tools/build)){
 FetchZip 'https://dl.google.com/android/repository/build-tools_r35_windows.zip' 'tools/build.zip' 'tools/build' 'SHA1' 'af059bb67cf7786f45ee0db85e2d24985df1b4b6'
}
if(!(Test-Path tools/json.jar)){
 $jsonPath='org/json/json/20240303/json-20240303.jar'
 $downloaded=$false
 foreach($mirror in @('https://repo.maven.apache.org/maven2/','https://repo1.maven.org/maven2/')){
  try{
   Invoke-WebRequest ($mirror+$jsonPath) -OutFile tools/json.jar -MaximumRetryCount 3 -RetryIntervalSec 2
   if((Get-FileHash tools/json.jar -Algorithm SHA256).Hash.ToLower() -ne '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'){throw 'JSON test dependency checksum mismatch'}
   $downloaded=$true;break
  }catch{Write-Warning "Test dependency download failed from ${mirror}: $($_.Exception.Message)"}
 }
 if(!$downloaded){throw 'Could not download verified org.json test dependency from Maven Central.'}
}
