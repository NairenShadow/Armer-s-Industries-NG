# Compile tous les plugins du pack dans dist\ (Windows).
# Usage :  powershell -ExecutionPolicy Bypass -File build.ps1 "C:\chemin\vers\server.jar"
# Il faut un JDK 9 ou plus recent (javac et jar dans le PATH).
param([Parameter(Mandatory = $true)][string]$ServeurJar)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$cp = @($ServeurJar)
Get-ChildItem libs\*.jar -ErrorAction SilentlyContinue | ForEach-Object { $cp += $_.FullName }
Remove-Item build, dist -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory build, dist | Out-Null

function Compiler($src, $res, $nom) {
    $cls = "build\$nom"
    New-Item -ItemType Directory $cls -Force | Out-Null
    $fichiers = Get-ChildItem $src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    & javac --release 8 -nowarn -encoding UTF-8 -cp ($script:cp -join ";") -d $cls $fichiers
    if ($LASTEXITCODE -ne 0) { throw "Echec de compilation : $nom" }
    Copy-Item "$res\*.yml" $cls -ErrorAction SilentlyContinue
    Push-Location $cls; & jar cf "..\..\dist\$nom.jar" .; Pop-Location
    $script:cp += (Resolve-Path $cls).Path
    Write-Host "  dist\$nom.jar"
}

Write-Host "Compilation du pack ShadowState-NG (Armer's Industries) :"
foreach ($p in "SSNG-Core", "SSNG-Ponts", "SSNG-Ressources", "SSNG-Carte", "SSNG-Assaut", "SSNG-Iles", "SSNG-Machines") {
    $d = "plugins\$p"
    if (-not (Test-Path $d)) { continue }
    Compiler "$d\src" "$d\res" $p
    if (Test-Path "$d\ponts") {
        Get-ChildItem "$d\ponts" -Directory | ForEach-Object { Compiler "$($_.FullName)\src" $_.FullName "SSNG-Pont-$($_.Name)" }
    }
}
Write-Host "Termine : copie les .jar de dist\ dans le dossier plugins\ de ton serveur."
