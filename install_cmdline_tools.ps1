# --------------------------------------------------------------
# Android Command‑Line Tools Installer (fixed URL)
# --------------------------------------------------------------

# 1️⃣ Updated download URL (generic latest)
$url = "https://dl.google.com/android/repository/commandlinetools-win-latest.zip"
$zip = "$env:TEMP\cmdline-tools.zip"
$destRoot = "C:\Android\SDK"
$dest = Join-Path $destRoot "cmdline-tools"

# Ensure the SDK root exists
if (-not (Test-Path $destRoot)) {
    Write-Host "Creating SDK root at $destRoot"
    New-Item -ItemType Directory -Path $destRoot | Out-Null
}

# 2️⃣ Download the zip
Write-Host "`nDownloading Android command‑line tools..."
Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing

# 3️⃣ Extract the archive
Write-Host "Extracting to $dest ..."
Expand-Archive -LiteralPath $zip -DestinationPath $dest -Force

# 4️⃣ Rename the inner folder to "latest"
$inner = Join-Path $dest "cmdline-tools"
if (Test-Path $inner) {
    Write-Host "Renaming '$inner' → 'latest' ..."
    Rename-Item -Path $inner -NewName "latest" -Force
} else {
    Write-Warning "Folder $inner not found – skipping rename"
}

# 5️⃣ Clean up
Write-Host "Removing temporary zip..."
Remove-Item $zip -Force

Write-Host "`n✅ Android command‑line tools installed successfully."
Write-Host "Add the following to your user environment (once):"
# [Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT','C:\\Android\\SDK','User')
# [Environment]::SetEnvironmentVariable('ANDROID_HOME','C:\\Android\\SDK','User')
# [Environment]::SetEnvironmentVariable('Path', $env:Path + ';C:\\Android\\SDK\\cmdline-tools\\latest\\bin;C:\\Android\\SDK\\platform-tools','User')
Write-Host "`nOpen a new terminal to apply the changes."
