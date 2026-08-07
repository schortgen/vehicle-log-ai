[Environment]::SetEnvironmentVariable(
    'ANDROID_SDK_ROOT',
    'C:\Users\ameba\AppData\Local\Android\Sdk',
    'User'
)

# Refresh this session’s variable
$env:ANDROID_SDK_ROOT = [Environment]::GetEnvironmentVariable('ANDROID_SDK_ROOT','User')

Write-Host "ANDROID_SDK_ROOT = $env:ANDROID_SDK_ROOT"
