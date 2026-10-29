<#
.SYNOPSIS
    把本地仓库推送到 GitHub，并打印后续需要在仓库 Settings 中配置的 Secrets。

.PARAMETER Repo
    仓库地址，例如 https://github.com/<账号>/<仓库名>.git
    留空则只提示手动执行的命令。

.EXAMPLE
    ./scripts/push-to-github.ps1 -Repo https://github.com/kylin/grib-service.git
#>
param(
    [Parameter(Mandatory = $false)]
    [string]$Repo
)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

Write-Host '==> 当前状态' -ForegroundColor Cyan
git status --short
git log --oneline -1

if (-not $Repo) {
    Write-Host ''
    Write-Host '请指定仓库地址，例如：' -ForegroundColor Yellow
    Write-Host '  ./scripts/push-to-github.ps1 -Repo https://github.com/<账号>/<仓库名>.git'
    Write-Host ''
    Write-Host '或者手动执行：' -ForegroundColor Yellow
    Write-Host '  git remote add origin https://github.com/<账号>/<仓库名>.git'
    Write-Host '  git push -u origin main'
    exit 1
}

if (-not (git remote)) {
    git remote add origin $Repo
    Write-Host "==> 已添加 remote: $Repo" -ForegroundColor Green
} else {
    git remote set-url origin $Repo
    Write-Host "==> 已更新 remote: $Repo" -ForegroundColor Green
}

Write-Host '==> 推送到 main 分支' -ForegroundColor Cyan
git push -u origin main

Write-Host ''
Write-Host '==> 接下来在仓库 Settings -> Secrets and variables -> Actions 中添加：' -ForegroundColor Cyan
Write-Host '   必填: DEPLOY_HOST, DEPLOY_USER, DEPLOY_SSH_KEY, DEPLOY_SSH_KNOWN_HOSTS'
Write-Host '   选填: DEPLOY_PORT, DEPLOY_PATH, DEPLOY_HEALTH_URL, GHCR_PAT, GHCR_USER'
Write-Host ''
Write-Host '详细说明见 docs/CI_CD.md' -ForegroundColor Cyan
