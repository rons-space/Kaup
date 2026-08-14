#Requires -Version 5.1
<#
.SYNOPSIS
    Closes the code review findings that sprints 0 through 4 actually resolved.

.DESCRIPTION
    The PowerShell twin of close-resolved-issues.sh, for maintainers on Windows
    who do not want to go through Git Bash or WSL. Both scripts read the same
    scripts/resolved-issues.tsv, so they cannot drift into disagreeing about
    what has been fixed.

    Why either script exists: GitHub only auto-closes an issue when the pull
    request referencing it merges into the default branch. Every sprint PR
    targets dev, so none of the "Closes #NNN" references ever fired, and
    promoting dev to main does not fire them retroactively. The integration
    token that filed the issues can create them but cannot close them, so this
    has to run as a real user.

    Idempotent: closing an already closed issue is a no-op.

.PARAMETER Repo
    Target repository. Defaults to rons-space/Kaup.

.PARAMETER WhatIf
    Lists what would be closed without calling GitHub.

.EXAMPLE
    .\scripts\close-resolved-issues.ps1

.EXAMPLE
    .\scripts\close-resolved-issues.ps1 -WhatIf
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$Repo = $(if ($env:REPO) { $env:REPO } else { 'rons-space/Kaup' })
)

$ErrorActionPreference = 'Stop'

# A non-zero exit from gh is an expected outcome here, not a fatal one: an issue
# someone already closed by hand should be counted and stepped over. PowerShell
# 7.5 turned $PSNativeCommandUseErrorActionPreference on by default, which would
# otherwise combine with 'Stop' above to abort the run on the first such issue
# and leave the rest of the list untouched. The variable does not exist on
# Windows PowerShell 5.1, hence the guard.
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Error 'gh is required. Install GitHub CLI from https://cli.github.com and run: gh auth login'
    exit 1
}

$dataPath = Join-Path $PSScriptRoot 'resolved-issues.tsv'
if (-not (Test-Path $dataPath)) {
    Write-Error "missing $dataPath"
    exit 1
}

$section = ''
$closed = 0
$failed = 0

foreach ($line in Get-Content -LiteralPath $dataPath) {
    if ($line -match '^\s*#' -or [string]::IsNullOrWhiteSpace($line)) { continue }

    # Split on the first two tabs only, so a reason containing a tab would fail
    # loudly here rather than silently truncating a closing comment.
    $parts = $line -split "`t", 3
    if ($parts.Count -ne 3) {
        Write-Warning "skipping malformed row: $line"
        continue
    }

    $lineSection, $issue, $reason = $parts

    if ($lineSection -ne $section) {
        $section = $lineSection
        Write-Host $section
    }

    if (-not $PSCmdlet.ShouldProcess("#$issue", 'close')) { continue }

    # gh writes to stderr on failure, which PowerShell would otherwise turn into
    # a terminating error under $ErrorActionPreference = 'Stop'.
    $null = gh issue close $issue --repo $Repo --comment $reason 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  closed #$issue"
        $closed++
    }
    else {
        Write-Host "  #$issue FAILED (already closed, or insufficient permission)" -ForegroundColor Yellow
        $failed++
    }
}

Write-Host ''
Write-Host "$closed closed, $failed failed or already closed"

Write-Host ''
Write-Host 'Deliberately NOT closed:'
Write-Host '  #159  the encryption claims were withdrawn, but the database is still'
Write-Host '        plaintext. There is no SQLCipher anywhere. Real work remains.'
Write-Host '  #178  the vulnerable ktor pin is gone and the build is proven green,'
Write-Host '        but the toolchain skew half of the finding is unreviewed.'
Write-Host '  #203  ALPHA_DESTRUCTIVE_MIGRATION exists with a TODO, but nothing'
Write-Host '        enforces its removal before v0.2-alpha. Schema 7 has now landed'
Write-Host '        inside the destructive window, so this is still the binding'
Write-Host '        constraint on the next schema change.'
Write-Host ''
Write-Host 'Still open and worth knowing:'
Write-Host '  #269  moves LineItem.quantity to Quantity, the last Double in the money'
Write-Host '        path. Not a schema change, so it is not gated on the migration'
Write-Host '        window.'
Write-Host '  #174  :core-data and :feature-auth still have no test harness, so the'
Write-Host '        transactional glue added in sprint 4, OverrideAuthorizer and'
Write-Host '        HotpCodeIssuer, has no automated coverage. The pure policy under'
Write-Host '        it does. This is the largest known gap in sprint 4.'
