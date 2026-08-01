$sdk = "C:\Users\ameba\AppData\Local\Android\Sdk"

$currentPath = [Environment]::GetEnvironmentVariable('Path', 'User')

$additions = @(
    "$sdk\cmdline-tools\latest\bin",
    "$sdk\platform-tools"
)

foreach ($item in $additions) {
    if ($currentPath -notlike "*$item*") {
        $currentPath += ";$item"
    }
}

[Environment]::SetEnvironmentVariable(
    'Path',
    $currentPath,
    'User'
)

Write-Host "Android SDK PATH updated successfully"