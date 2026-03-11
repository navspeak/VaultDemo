# Vault Encryption Demo (Spring Boot + HashiCorp Vault)

This project demonstrates how to use **HashiCorp Vault Transit Engine** with a **Spring Boot application** to perform:

- Encryption
- Decryption
- Key Rotation

The setup runs **Vault locally using Docker in production-like mode (not dev mode)**.

---

# Architecture

```
Spring Boot Application
        │
        │ AppRole Login
        ▼
Vault Auth API
        │
        │ client token issued
        ▼
Transit Secrets Engine
        │
        ├ encrypt
        ├ decrypt
        └ rotate keys
```

---

# Roles

| Role | Responsibility |
|-----|-----|
| Vault Operator / DevOps | Initialize Vault, unseal Vault, configure secrets engines and policies |
| Application Developer | Configure application authentication |
| Application | Use Vault encryption APIs |

---

# 1. Start Vault Locally

Operator runs:

```bash
docker compose up -d
```

Vault starts **sealed and uninitialized**.

---

# 2. Initialize Vault

Vault must be initialized once.

```bash
docker exec -it vault vault operator init
```

Example output:

```
Unseal Key 1: <key1>
Unseal Key 2: <key2>
Unseal Key 3: <key3>
Unseal Key 4: <key4>
Unseal Key 5: <key5>

Initial Root Token: <root-token>
```

Important:

Vault uses **Shamir Secret Sharing**

- 5 keys generated
- 3 keys required to unseal
- Root token is administrative

⚠️ Never commit these values to Git.

Production storage examples:

- password managers
- HSM
- secret vaults
- distributed among operators

---

# 3. Unseal Vault

Vault is sealed after initialization.

Run:

```bash
docker exec -it vault vault operator unseal
```

Enter **Unseal Key 1**

Run again:

```bash
docker exec -it vault vault operator unseal
```

Enter **Unseal Key 2**

Run again:

```bash
docker exec -it vault vault operator unseal
```

Enter **Unseal Key 3**

Vault becomes **unsealed**.

---

# 4. Login to Vault

```bash
docker exec -it vault vault login
```

Enter the **Initial Root Token**.

---

# 5. Verify Vault Status

```bash
docker exec -it vault vault status
```

Expected output:

```
Sealed: false
Initialized: true
```

You can also verify with:

```bash
curl http://localhost:8200/v1/sys/health
```

---

# Why Vault Uses Unsealing

Vault encrypts all stored data.

At startup:

- storage is encrypted
- master key is split using Shamir shares
- multiple operators must reconstruct it

This prevents **one person unlocking Vault alone**.

---

# Production Alternative: Auto-Unseal

Most production setups use **Auto-Unseal**.

Examples:

- AWS KMS
- GCP KMS
- Azure Key Vault
- HSM
- Transit Auto-Unseal

Vault retrieves the key automatically.

---

# 6. Enable Transit Engine

Transit provides **Encryption as a Service**.

```bash
docker exec -it vault vault secrets enable transit
```

This creates API paths:

```
/v1/transit/*
```

Transit supports:

- encryption
- decryption
- signing
- verification
- key rotation

---

# 7. Create Encryption Key

```bash
docker exec -it vault vault write transit/keys/file-key type=aes256-gcm96
```

This key will be used by the application.

---

# 8. Enable AppRole Authentication

```bash
docker exec -it vault vault auth enable approle
```

AppRole is designed for **machine-to-machine authentication**.

---

# 9. Create Policy

Create file:

```
vault/policies/app-policy.hcl
```

Policy contents:

```hcl
path "transit/encrypt/file-key" {
  capabilities = ["update"]
}

path "transit/decrypt/file-key" {
  capabilities = ["update"]
}

path "transit/keys/file-key" {
  capabilities = ["read"]
}

path "transit/keys/file-key/rotate" {
  capabilities = ["update"]
}
```

Load policy:

```bash
docker exec -it vault vault policy write app-policy /vault/policies/app-policy.hcl
```

---

# 10. Create AppRole

```bash
docker exec -it vault vault write auth/approle/role/vault-demo \
token_policies="app-policy" \
token_ttl="1h" \
token_max_ttl="4h"
```

---

# 11. Retrieve Role ID

```bash
docker exec -it vault vault read auth/approle/role/vault-demo/role-id
```

---

# 12. Generate Secret ID

```bash
docker exec -it vault vault write -f auth/approle/role/vault-demo/secret-id
```

You now have:

```
role_id
secret_id
```

These will be used by the application.

---

# 13. Provide Credentials to Application

Windows:

```bash
set VAULT_ROLE_ID=...
set VAULT_SECRET_ID=...
```

Linux / Mac:

```bash
export VAULT_ROLE_ID=...
export VAULT_SECRET_ID=...
```

---

# 14. Spring Boot Configuration

`application.yml`

```yaml
app:
  vault:
    uri: http://localhost:8200
    auth-type: approle
    role-id: ${VAULT_ROLE_ID}
    secret-id: ${VAULT_SECRET_ID}
    transit:
      mount-path: transit
      key-name: file-key
```

---

# Stopping Vault

```bash
docker compose down
```

When Vault restarts it will be **sealed again** and must be unsealed.

---

# Summary

| Step | Performed By |
|----|----|
| Start Vault | Operator |
| Initialize Vault | Operator |
| Unseal Vault | Operator |
| Enable Transit | Operator |
| Create Key | Operator |
| Create Policy | Operator |
| Create AppRole | Operator |
| Provide role_id/secret_id | Operator |
| Configure Application | Developer |
| Use Vault APIs | Application |

---

# What This Demo Shows

- Production-style Vault setup
- AppRole authentication
- Vault Transit encryption
- Key rotation
- Secure secret management