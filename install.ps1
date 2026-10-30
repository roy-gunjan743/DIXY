param(
	[string]$Version,
	[switch]$Uninstall
)

$ErrorActionPreference = 'Stop'
$Repo = if ($env:DIXY_REPO) { $env:DIXY_REPO } else { 'roy-gunjan743/DIXY-CLI' }
$InstallDir = if ($env:DIXY_HOME) { $env:DIXY_HOME } else { Join-Path $env:USERPROFILE '.dixy' }

if ($Uninstall) {
	Remove-Item $InstallDir -Recurse -Force -ErrorAction SilentlyContinue
	$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
	$entries = @($userPath -split ';' | Where-Object { $_ -and $_.Trim() -and $_.TrimEnd('\') -ne $InstallDir.TrimEnd('\') })
	[Environment]::SetEnvironmentVariable('Path', ($entries -join ';'), 'User')
	Write-Host "DIXY removed from $InstallDir"
	exit 0
}

$javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
if (-not $javaCommand) {
	throw 'DIXY requires Java 21 or newer. Install Temurin 21 from https://adoptium.net/ and open a new PowerShell.'
}
$javaVersion = (& $javaCommand.Source -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "(?<major>\d+)') { throw 'DIXY found Java but could not read its version.' }
if ([int]$Matches.major -lt 21) { throw "DIXY requires Java 21 or newer; found Java $($Matches.major)." }

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
$releaseVersion = if ($Version) { $Version.TrimStart('v') } else { $null }
$baseUrl = if ($releaseVersion) { "https://github.com/$Repo/releases/download/v$releaseVersion" } else { "https://github.com/$Repo/releases/latest/download" }
$tempDir = Join-Path ([IO.Path]::GetTempPath()) ([Guid]::NewGuid().ToString())
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

try {
	foreach ($asset in @('dixy-agent.jar', 'dixy-cli.jar', 'dixy.bat', 'VERSION', 'SHA256SUMS')) {
		Invoke-WebRequest -Uri "$baseUrl/$asset" -OutFile (Join-Path $tempDir $asset) -UseBasicParsing
	}
	$checksums = Get-Content (Join-Path $tempDir 'SHA256SUMS')
	foreach ($asset in @('dixy-agent.jar', 'dixy-cli.jar')) {
		$line = $checksums | Where-Object { $_ -match "  $([regex]::Escape($asset))$" } | Select-Object -First 1
		if (-not $line) { throw "SHA-256 entry missing for $asset" }
		$expected = ($line -split '\s+')[0].ToLowerInvariant()
		$actual = (Get-FileHash (Join-Path $tempDir $asset) -Algorithm SHA256).Hash.ToLowerInvariant()
		if ($actual -ne $expected) { throw "SHA-256 verification failed for $asset" }
	}
	Copy-Item (Join-Path $tempDir 'dixy-agent.jar'), (Join-Path $tempDir 'dixy-cli.jar'), (Join-Path $tempDir 'VERSION'), (Join-Path $tempDir 'dixy.bat') $InstallDir -Force

	$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
	$entries = @($userPath -split ';' | Where-Object { $_ -and $_.Trim() })
	if (-not ($entries | Where-Object { $_.TrimEnd('\') -ieq $InstallDir.TrimEnd('\') })) {
		$entries += $InstallDir
		[Environment]::SetEnvironmentVariable('Path', ($entries -join ';'), 'User')
	}
	$env:Path = "$($entries -join ';');$([Environment]::GetEnvironmentVariable('Path', 'Machine'))"
	foreach ($asset in @('dixy-agent.jar', 'dixy-cli.jar', 'dixy.bat', 'VERSION')) {
		if (-not (Test-Path (Join-Path $InstallDir $asset))) { throw "Installation verification failed: $asset" }
	}
	Write-Host "DIXY $((Get-Content (Join-Path $InstallDir 'VERSION') -Raw).Trim()) installed in $InstallDir"
	Write-Host 'Open a new PowerShell window, then run: dixy'
}
finally {
	Remove-Item $tempDir -Recurse -Force -ErrorAction SilentlyContinue
}
