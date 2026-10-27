# Rebuilds libadblock_jni.so and copies it into app/src/main/jniLibs/arm64-v8a.
# The .so is committed, so normal Gradle builds don't need Rust. Run this only after changing the crate.
# Needs: rustup with the aarch64-linux-android target, cargo-ndk, and the Android NDK.
param(
  [string]$Ndk = $env:ANDROID_NDK_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $Ndk) {
  $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
  $Ndk = Get-ChildItem (Join-Path $sdk 'ndk') -Directory | Sort-Object Name | Select-Object -Last 1 -ExpandProperty FullName
}
$env:ANDROID_NDK_HOME = $Ndk
Push-Location $PSScriptRoot
try {
  cargo ndk -t arm64-v8a -P 34 build --release
  if ($LASTEXITCODE -ne 0) { throw "cargo ndk failed" }
  $out = Join-Path $PSScriptRoot '..\..\app\src\main\jniLibs\arm64-v8a'
  New-Item -ItemType Directory -Force $out | Out-Null
  Copy-Item 'target\aarch64-linux-android\release\libadblock_jni.so' $out -Force
  Write-Host "Copied libadblock_jni.so to $out"
} finally {
  Pop-Location
}
