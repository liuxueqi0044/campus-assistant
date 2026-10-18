$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$workRoot = Split-Path -Parent $projectRoot
$toolsRoot = Join-Path $workRoot '.tools'
$jdkHome = Join-Path $toolsRoot 'temurin-17.0.20.1\jdk-17.0.20.1+1'
$mavenHome = Join-Path $toolsRoot 'apache-maven-3.9.16'

if (-not (Test-Path -LiteralPath (Join-Path $jdkHome 'bin\java.exe'))) {
    throw "Portable Java 17 is missing at $jdkHome"
}
if (-not (Test-Path -LiteralPath (Join-Path $mavenHome 'bin\mvn.cmd'))) {
    throw "Portable Maven is missing at $mavenHome"
}

$env:JAVA_HOME = $jdkHome
$env:MAVEN_HOME = $mavenHome
$env:MAVEN_OPTS = '-Dfile.encoding=UTF-8'
$env:Path = (Join-Path $jdkHome 'bin') + ';' + (Join-Path $mavenHome 'bin') + ';' + $env:Path

Write-Output "JAVA_HOME=$env:JAVA_HOME"
Write-Output "MAVEN_HOME=$env:MAVEN_HOME"
