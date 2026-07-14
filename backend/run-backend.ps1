$ErrorActionPreference = "Stop"

$BackendDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ToolsDir = Join-Path $BackendDir ".tools"
$MavenVersion = "3.9.9"
$JdkVersion = "21.0.6_7"
$MavenHome = Join-Path $ToolsDir "apache-maven-$MavenVersion"
$MavenCmd = Join-Path $MavenHome "bin\mvn.cmd"
$JdkHome = Join-Path $ToolsDir "jdk-21.0.6+7"
$JdkJava = Join-Path $JdkHome "bin\java.exe"

New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null

function Download-File($Url, $OutFile) {
  Write-Host "Downloading $Url"
  $ProgressPreference = "SilentlyContinue"
  Invoke-WebRequest -Uri $Url -OutFile $OutFile
}

function Expand-Zip($ZipPath, $Destination) {
  if (Test-Path $Destination) {
    Remove-Item -Recurse -Force $Destination
  }
  Expand-Archive -LiteralPath $ZipPath -DestinationPath (Split-Path -Parent $Destination) -Force
}

if (-not (Get-Command java -ErrorAction SilentlyContinue) -and -not (Test-Path $JdkJava)) {
  $JdkZip = Join-Path $ToolsDir "temurin-jdk-21.zip"
  $JdkUrl = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-$JdkVersion/OpenJDK21U-jdk_x64_windows_hotspot_21.0.6_7.zip"
  Download-File $JdkUrl $JdkZip
  Expand-Zip $JdkZip $JdkHome
}

if (Test-Path $JdkJava) {
  $env:JAVA_HOME = $JdkHome
  $env:Path = "$JdkHome\bin;$env:Path"
}

if (-not (Test-Path $MavenCmd)) {
  $MavenZip = Join-Path $ToolsDir "apache-maven-$MavenVersion-bin.zip"
  $MavenUrl = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip"
  Download-File $MavenUrl $MavenZip
  Expand-Zip $MavenZip $MavenHome
}

Write-Host ""
Write-Host "Checking Java..."
& java -version

if ($env:SPRING_PROFILES_ACTIVE -ne "local-h2") {
  Write-Host ""
  Write-Host "Checking PostgreSQL at localhost:5432..."
  $postgresReady = $false
  try {
    $client = New-Object System.Net.Sockets.TcpClient
    $async = $client.BeginConnect("127.0.0.1", 5432, $null, $null)
    $postgresReady = $async.AsyncWaitHandle.WaitOne(1500, $false)
    $client.Close()
  } catch {
    $postgresReady = $false
  }

  if (-not $postgresReady) {
    Write-Host ""
    Write-Host "PostgreSQL is not reachable on localhost:5432." -ForegroundColor Yellow
    Write-Host "Start PostgreSQL first, or run this backend with local H2 database:"
    Write-Host "  .\run-backend-h2.cmd"
    exit 1
  }
} else {
  Write-Host ""
  Write-Host "Using local H2 database profile. PostgreSQL is not required for this run."
}

Set-Location $BackendDir
Write-Host ""
Write-Host "Starting Spring Boot backend on http://localhost:8080"
if (Test-Path (Join-Path $BackendDir "target")) {
  Write-Host "Removing old compiled backend classes..."
  Remove-Item -Recurse -Force (Join-Path $BackendDir "target")
}
& $MavenCmd -e clean spring-boot:run
