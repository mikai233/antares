$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ConfRoot = Join-Path $Root "config/luban"
$DataDir = if ($env:LUBAN_DATA_DIR) { $env:LUBAN_DATA_DIR } else { Join-Path $ConfRoot "Datas" }
$LubanExamplesRoot = if ($env:LUBAN_EXAMPLES_ROOT) { $env:LUBAN_EXAMPLES_ROOT } else { "C:\tmp\luban_examples" }
$LubanDll = Join-Path $LubanExamplesRoot "Tools/Luban/Luban.dll"
$JavaCorelib = Join-Path $LubanExamplesRoot "Projects/Java_bin/src/main/corelib/luban"
$OutputCodeDir = Join-Path $Root "common/src/generated/luban/java"
$OutputDataDir = Join-Path $Root "common/build/generated/luban/resources/luban"

if (-not (Test-Path $LubanDll -PathType Leaf)) {
    Write-Error "Luban tool not found: $LubanDll`nSet LUBAN_EXAMPLES_ROOT or clone https://github.com/focus-creative-games/luban_examples"
}

if (-not (Test-Path $JavaCorelib -PathType Container)) {
    Write-Error "Luban Java corelib not found: $JavaCorelib"
}

if (-not (Test-Path $DataDir -PathType Container)) {
    Write-Error "Luban data dir not found: $DataDir"
}

if (Test-Path $OutputCodeDir) {
    Remove-Item $OutputCodeDir -Recurse -Force
}
if (Test-Path $OutputDataDir) {
    Remove-Item $OutputDataDir -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $OutputCodeDir | Out-Null
New-Item -ItemType Directory -Force -Path $OutputDataDir | Out-Null

dotnet $LubanDll `
  -t server `
  -c java-bin `
  -d bin `
  --conf (Join-Path $ConfRoot "luban.conf") `
  -x "inputDataDir=$DataDir" `
  -x "outputCodeDir=$OutputCodeDir" `
  -x "outputDataDir=$OutputDataDir"

$OutputCorelibDir = Join-Path $OutputCodeDir "luban"
New-Item -ItemType Directory -Force -Path $OutputCorelibDir | Out-Null
Copy-Item (Join-Path $JavaCorelib "*.java") $OutputCorelibDir -Force

Get-ChildItem -Path $OutputCodeDir -Recurse -Filter *.java |
    Where-Object { $_.FullName -notlike "$OutputCorelibDir*" } |
    ForEach-Object {
        $packageLine = Select-String -Path $_.FullName -Pattern '^package\s+(.+);' | Select-Object -First 1
        if (-not $packageLine) {
            return
        }
        $packageName = $packageLine.Matches[0].Groups[1].Value
        $targetDir = Join-Path $OutputCodeDir ($packageName -replace '\.', '\')
        New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
        $targetFile = Join-Path $targetDir $_.Name
        if ($_.FullName -ne $targetFile) {
            Move-Item $_.FullName $targetFile -Force
        }
    }

Write-Host "Generated Luban Java code into $OutputCodeDir"
Write-Host "Generated Luban binary data into $OutputDataDir"
Write-Host "Using Luban Excel data dir $DataDir"
