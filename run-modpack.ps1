# Builds the mod and launches the NeoForge dev client with the World Traveler 2 modpack's mods and configs (see
# syncModpack in build.gradle.kts), in the run-modpack folder.
# Usage: right-click > Run with PowerShell, or from a terminal: .\run-modpack.ps1
# Another instance: .\gradlew.bat runModpackClient -PmodpackInstance="C:\path\to\instance"

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

.\gradlew.bat runModpackClient
