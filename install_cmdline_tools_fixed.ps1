# Android Command-Line Tools Installer (robust version)

# URL for latest command-line tools zip
$url = "https://dl.google.com/android/repository/commandlinetools-win-latest.zip"
# Temporary zip location
$zip = Join-Path $env:TEMP "cmdline-tools.zip"

# Target SDK root and destination for cmdline-tools
$sdkRoot = "C:\Android\SDK"
$dest = Join-Path $sdkRoot "cmdline-tools"

# Ensure SDK root exists
if (-not (Test-Path $sdkRoot)) {
    Write-Host "Creating SDK root at $sdkRoot"
    New-Item -ItemType Directory -Path $sdkRoot -Force | Out-Null
}

# Ensure destination exists
if (-not (Test-Path $dest)) {
    Write-Host "Creating cmdline-tools folder at $dest"
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
}

# Download the zip
Write-Host "Downloading Android command-line tools..."
Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing

# Extract the archive (the zip contains a folder named 'cmdline-tools')
Write-Host "Extracting to $dest ..."
Expand-Archive -LiteralPath $zip -DestinationPath $dest -Force

# The extracted folder is $dest\cmdline-tools – rename it to 'latest'
$inner = Join-Path $dest "cmdline-tools"
if (Test-Path $inner) {
    Write-Host "Renaming '$inner' to 'latest' ..."
    Rename-Item -Path $inner -NewName "latest" -Force
} else {
    Write-Warning "Folder $inner not found – skipping rename"
}

# Clean up zip file
Write-Host "Removing temporary zip..."
Remove-Item $zip -Force

Write-Host "✅ Android command-line tools installed successfully."
Write-Host "Add the following to your user environment (once):"
[Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT',$sdkRoot,'User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME',$sdkRoot,'User')
[Environment]::SetEnvironmentVariable('Path',$env:Path + ";$sdkRoot\cmdline-tools\latest\bin;$sdkRoot\platform-tools",'User')
Write-Host "Open a new terminal to apply the changes."
