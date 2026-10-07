#!/usr/bin/env bash
# Gera o .env.prod com segredos aleatórios para o deploy de produção.
#
#   ./scripts/gerar-env-producao.sh [--url https://suprihub.org.br] [--admin-email admin@org.br] [--out .env.prod] [--force]
#
# Depois: docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d --build
# O arquivo gerado contém segredos: não versione (já está no .gitignore) e guarde uma cópia segura.
set -euo pipefail

OUT=".env.prod"
URL=""
ADMIN_EMAIL=""
FORCE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --url) URL="$2"; shift 2 ;;
    --admin-email) ADMIN_EMAIL="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --force) FORCE=1; shift ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    *) echo "Opção desconhecida: $1" >&2; exit 2 ;;
  esac
done

if [ -e "$OUT" ] && [ "$FORCE" -ne 1 ]; then
  echo "$OUT já existe. Trocar os segredos derruba as sessões e exige recriar banco/RabbitMQ;" >&2
  echo "use --force se for isso mesmo." >&2
  exit 1
fi

# n bytes aleatórios em base64 URL-safe, sem padding
rand() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 "$1"
  else
    head -c "$1" /dev/urandom | base64
  fi | tr -d '\n=' | tr '+/' '-_'
}

# Senha do admin: 20 caracteres, com letras e números (exigência da política de senha)
admin_password() {
  local p
  while :; do
    p="$(rand 24 | cut -c1-20)"
    [[ "$p" =~ [A-Za-z] && "$p" =~ [0-9] ]] && { echo "$p"; return; }
  done
}

ADMIN_PASSWORD="$(admin_password)"
umask 077
cat > "$OUT" <<EOF
# Gerado por scripts/gerar-env-producao.sh em $(date -u +%Y-%m-%dT%H:%M:%SZ). CONTÉM SEGREDOS.
SPRING_PROFILES_ACTIVE=prod

# URL pública (HTTPS) — usada nos links dos e-mails e no CORS
FRONTEND_URL=${URL}
FRONTEND_PORT=80

DB_NAME=suprihub
DB_USER=suprihub
DB_PASSWORD=$(rand 32)

RABBITMQ_USER=suprihub
RABBITMQ_PASSWORD=$(rand 32)

JWT_SECRET=$(rand 48)
JWT_SECURE_COOKIE=true

# Primeiro administrador (criado só se ainda não existir nenhum ADMIN)
BOOTSTRAP_ADMIN_EMAIL=${ADMIN_EMAIL}
BOOTSTRAP_ADMIN_PASSWORD=${ADMIN_PASSWORD}
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
EOF

echo "Gerado: $OUT"
echo
echo "Senha inicial do ADMIN (troque depois do primeiro acesso): $ADMIN_PASSWORD"
echo
MISSING=""
[ -z "$URL" ] && MISSING="$MISSING FRONTEND_URL"
[ -z "$ADMIN_EMAIL" ] && MISSING="$MISSING BOOTSTRAP_ADMIN_EMAIL"
MISSING="$MISSING MAIL_HOST/MAIL_USER/MAIL_PASSWORD CAPTCHA_SITE_KEY/CAPTCHA_SECRET_KEY"
echo "Preencha no $OUT:$MISSING"
echo "Depois: docker compose --env-file $OUT -f docker-compose.yml -f docker-compose.prod.yml up -d --build"
