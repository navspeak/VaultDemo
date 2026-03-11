## Running HashiCorp Vault Locally (Prod-like Mode)
- This setup runs Vault locally using Docker without Dev Mode, so that it behaves closer to a production environment.
- In this mode:
  - Vault starts sealed
  - You must initialize Vault
  - You must manually unseal Vault using Shamir keys

### 1. Start Vault Locally
1. `docker compose up -d`

### 2. Initialize Vault
- Since we are not using Dev Mode, Vault must be initialized.

`docker exec -it vault vault operator init`
- You will get 5 unseal keys: 1 initial root token -> Save them (typically in some secret store).
- Output:
```html
    Unseal Key 1: <key1>
  Unseal Key 2: <key2>
  Unseal Key 3: <key3>
  Unseal Key 4: <key4>
  Unseal Key 5: <key5>

  Initial Root Token: <root-token>
```
Important concepts:
- Vault uses Shamir Secret Sharing:
  - 5 key shares generated
  - 3 required to unseal Vault
  - Root token is used for administrative operations
    ⚠️ Save these securely (do NOT commit them to Git).

In production they are stored in:
- secure password managers
- HSM
- secret vaults
- distributed across multiple operators
-
### 3. Unseal Vault
- After initialization Vault is sealed and cannot serve requests.
- You must provide 3 of the 5 unseal keys.
- Run `docker exec -it vault vault operator unseal` <= Enter `Unseal Key 1`.
- Repeat above for `Unseal Key 2` and `Unseal Key 3`
- After the threshold (3 keys), Vault becomes unsealed.
- ``

### 4. Login to Vault
- Use the Initial Root Token. `docker exec -it vault vault login`
- **Verify Vault Status**
  - `docker exec -it vault vault status`

    ```html
    Key             Value
    ---             -----
    Sealed          false
    Initialized     true
    Version         1.x.x
    ```
  -  `curl http://localhost:8200/v1/sys/health`
- **Why Unsealing Exists**
  - Vault keeps its master encryption key encrypted.
  - When Vault starts:
    - Vault storage is encrypted
    - Master key is split into Shamir shares
    - Operators must provide threshold shares to reconstruct the master key
  - This ensures no single person can unlock Vault alone.

- **In Production**
  - Manual unsealing is usually avoided using Auto-Unseal.
  - Examples: `AWS KMS`, `GCP KMS`, `HSM`
  - Vault automatically retrieves the key and unseals itself.
- **Stop Vault**
  - `docker compose down`
  - Note: When Vault restarts, it will be sealed again, and you must unseal it again.

### 5. Enable Transit
- `docker exec -it vault vault secrets enable transit`
- This creates the API path: `/v1/transit/*`
- Now Vault supports: `encryption`, `decryption`, `signing`, `verification``,key rotation`

### 6. Enable AppRole auth
- `docker exec -it vault vault auth enable approle`

### 7. Create policy file

- Create vault/policies/app-policy.hcl on your machine with:
```
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
- Then load it: `docker exec -it vault vault policy write app-policy /vault/policies/app-policy.hcl`

### 8. Create AppRole
```
docker exec -it vault vault write auth/approle/role/vault-demo \
token_policies="app-policy" \
token_ttl="1h" \
token_max_ttl="4h"
```
### Read role_id
- `docker exec -it vault vault read auth/approle/role/vault-demo/role-id`
### Generate secret_id
- `docker exec -it vault vault write -f auth/approle/role/vault-demo/secret-id`
- Now you will have:`role_id` `secret_id`

- 🚀🚀🚀 **These are what your Spring Boot app should use.**

### Your app config
```
set VAULT_ROLE_ID=...
set VAULT_SECRET_ID=...
```
- application.yml:

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

