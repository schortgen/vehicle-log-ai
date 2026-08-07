$sdk = "C:\Users\ameba\AppData\Local\Android\Sdk"

[Environment]::SetEnvironmentVariable(
    "Path",
    ([Environment]::GetEnvironmentVariable("Path","User") + ";" +
    "$sdk\cmdline-tools\latest\bin;" +
    "$sdk\platform-tools"),
    "User"
)
