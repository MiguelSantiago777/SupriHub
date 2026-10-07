<#
.SYNOPSIS
  Gera o .env.prod com segredos aleatórios para o deploy de produção.

.EXAMPLE
  .\scripts\gerar-env-producao.ps1 -Url https://suprihub.institutonata.org.br -AdminEmail admin@institutonata.org.br

  Depois: docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d --build
  O arquivo gerado contém segredos: não versione (já está no .gitignore) e guarde uma cópia segura.
#>
param(
    [string]$Url = '',
    [string]$AdminEmail = '',
    [string]$Out = '.env.prod',
    [switch]$Force
)
$ErrorActionPreference = 'Stop'

if ((Test-Path $Out) -and -not $Force) {
    Write-Error "$Out já existe. Trocar os segredos derruba as sessões e exige recriar banco/RabbitMQ; use -Force se for isso mesmo."
}

# n bytes aleatórios em base64 URL-safe, sem padding
function New-Secret([int]$Bytes) {
    $buffer = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($buffer).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

# Senha do admin: 20 caracteres, com letras e números (exigência da política de senha)
do {
    $adminPassword = (New-Secret 24).Substring(0, 20)
} until ($adminPassword -match '[A-Za-z]' -and $adminPassword -match '[0-9]')

$generatedAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$content = @"
# Gerado por scripts/gerar-env-producao.ps1 em $generatedAt. CONTÉM SEGREDOS.
SPRING_PROFILES_ACTIVE=prod

# URL pública (HTTPS) — usada nos links dos e-mails e no CORS
FRONTEND_URL=$Url
FRONTEND_PORT=80

DB_NAME=suprihub
DB_USER=suprihub
DB_PASSWORD=$(New-Secret 32)

RABBITMQ_USER=suprihub
RABBITMQ_PASSWORD=$(New-Secret 32)

JWT_SECRET=$(New-Secret 48)
JWT_SECURE_COOKIE=true

# Primeiro administrador (criado só se ainda não existir nenhum ADMIN)
BOOTSTRAP_ADMIN_EMAIL=$AdminEmail
BOOTSTRAP_ADMIN_PASSWORD=$adminPassword
DEMO_USERS=false

# SMTP de produção
MAIL_FROM=Contrata + NATA <nao-responda@institutonata.org.br>
MAIL_HOST=
MAIL_PORT=587
MAIL_USER=
MAIL_PASSWORD=
MAIL_SMTP_AUTH=true
MAIL_STARTTLS=true

# Captcha (Cloudflare Turnstile): crie o site em https://dash.cloudflare.com → Turnstile
CAPTCHA_SITE_KEY=
CAPTCHA_SECRET_KEY=
"@

# UTF-8 sem BOM e fim de linha LF (o docker compose lê o arquivo como está)
$outPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Out)
[System.IO.File]::WriteAllText($outPath,($content -replace "`r`n", "`n") + "`n",
    (New-Object System.Text.UTF8Encoding $false))

Write-Host "Gerado: $Out"
Write-Host ''
Write-Host "Senha inicial do ADMIN (troque depois do primeiro acesso): $adminPassword"
Write-Host ''
$missing = @()
if (-not $Url) { $missing += 'FRONTEND_URL' }
if (-not $AdminEmail) { $missing += 'BOOTSTRAP_ADMIN_EMAIL' }
$missing += 'MAIL_HOST/MAIL_USER/MAIL_PASSWORD', 'CAPTCHA_SITE_KEY/CAPTCHA_SECRET_KEY'
Write-Host "Preencha no ${Out}: $($missing -join ', ')"
Write-Host "Depois: docker compose --env-file $Out -f docker-compose.yml -f docker-compose.prod.yml up -d --build"
