#!/bin/sh
set -e

echo "Enabling transit secrets engine..."
vault secrets enable transit || true

echo "Creating transit key..."
vault write transit/keys/file-key type=aes256-gcm96 || true

echo "Enabling AppRole auth..."
vault auth enable approle || true

echo "Loading policy..."
vault policy write app-policy /vault/policies/app-policy.hcl

echo "Creating AppRole..."
vault write auth/approle/role/vault-demo \
  token_policies="app-policy" \
  token_ttl="1h" \
  token_max_ttl="4h"

echo
echo "Fetching role_id..."
ROLE_ID=$(vault read -field=role_id auth/approle/role/vault-demo/role-id)

echo "Generating secret_id..."
SECRET_ID=$(vault write -field=secret_id -f auth/approle/role/vault-demo/secret-id)

echo
echo "========================================"
echo "Use the following environment variables:"
echo
echo "Linux / Mac:"
echo "export VAULT_ROLE_ID=$ROLE_ID"
echo "export VAULT_SECRET_ID=$SECRET_ID"
echo
echo "Windows CMD:"
echo "set VAULT_ROLE_ID=$ROLE_ID"
echo "set VAULT_SECRET_ID=$SECRET_ID"
echo
echo "PowerShell:"
echo "\$env:VAULT_ROLE_ID=\"$ROLE_ID\""
echo "\$env:VAULT_SECRET_ID=\"$SECRET_ID\""
echo "========================================"
