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
if(!(Test-Path tools/json.jar)){Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar' -OutFile tools/json.jar}
