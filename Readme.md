###  Vault 101
Refer [vault_101](VAULT_101.md)
### Start containarized VAULT
Refer [Steps.txt](steps.txt)

### Code will call vault as:
1. POST /v1/transit/encrypt/file-key 
2. POST /v1/transit/decrypt/file-key

### Resulting lifecycle
```
File -> Base64 encode -> Vault encrypt(file-key)
                   |
                   v
             ciphertext returned
                   |
                   v
           stored with encrypted file
```
- For decrypt:
``` 
ciphertext -> Vault decrypt(file-key) -> plaintext
 ```
### When you rotate the key

- Operator runs: `vault write -f transit/keys/file-key/rotate`
- Vault creates key version 2.
- Important feature:
```
old ciphertext -> decrypted using old key version
new encryption -> uses new key version
```
- So no re-encryption required.

### What ciphertext looks like

Vault returns something like:
```
vault:v1:AbCdEfGh123...
```
- The v1 tells Vault which key version encrypted the data.

### file-key is a the key-name for the demo app

- purpose = encrypt files
- In real systems you might see: `file-key`, `pii-key`, `payment-key`, `session-key`,`document-key`

### How envelope encryption curl would look
1. **GENERATE DEK**: `openssl rand -base64 32` => `ddeF4q7bkfw25OT0Hsayx78uPdaBL+3+v/8RW1AKpAE=`
2. **GET TOKEN**:
3. export VAULT_ROLE_ID=18fb2599-ee9d-e4fa-4e00-55410667cda7
   export VAULT_SECRET_ID=ad2fa0e7-41d3-c9f1-f669-44e6d1e29a7c
```
curl \
   --request POST \
   --data '{"role_id":"18fb2599-ee9d-e4fa-4e00-55410667cda7","secret_id":"ad2fa0e7-41d3-c9f1-f669-44e6d1e29a7c"}' \
   http://localhost:8200/v1/auth/approle/login
--
{"request_id":"16d8b3c9-98d9-3812-8f90-d5a32b8642b7","lease_id":"","renewable":false,"lease_duration":0,
"data":null,"wrap_info":null,"warnings":null,"auth":{
"client_token":"hvs.CAESIFXr5DmN__YcbnOl1lHDX0WfqcMN3WBQqT2hf5Ihxk-UGh4KHGh2cy4wVThiTm9iMXdRa3QxMGJiOWpZaVoyQXA","accessor":"QJcED4a27xDswMDeZTvv7Ouc","policies":["app-policy","default"],"token_policies"
:["app-policy","default"],"metadata":{"role_name":"vault-demo"},"lease_duration":3600,"renewable":true,"entity_id":"d29c0066-247b-ae19-3c20-e19f6eacd4fd","token_type":"service","orphan":true,"mfa_requirement":null,"num_uses":0},"mount_type":""}

```
3. **ENCRYPT DEK using VAULT**:
```
curl \
--header "X-Vault-Token: hvs.CAESIFXr5DmN__YcbnOl1lHDX0WfqcMN3WBQqT2hf5Ihxk-UGh4KHGh2cy4wVThiTm9iMXdRa3QxMGJiOWpZaVoyQXA" \
--request POST \
--data '{"plaintext":"ddeF4q7bkfw25OT0Hsayx78uPdaBL+3+v/8RW1AKpAE="}' \
http://localhost:8200/v1/transit/encrypt/file-key
---
{"request_id":"b58ed704-df1c-d1f3-24ca-0bfef3732386","lease_id":"","renewable":false,"lease_duration":0,
"data":{"ciphertext":"vault:v1:gx8HutEC3m0/FMctuh8Lh/wBwK+bKmit8tEK2K0kjrBeYqP6Jv6VppEBm6FrraSiZXK9G/J0+Jr2RpkE",
"key_version":1},"wrap_info":null,"warnings":null,"auth":null,"mount_type":"transit"}
```
- DEK (plaintext): `ddeF4q7bkfw25OT0Hsayx78uPdaBL+3+v/8RW1AKpAE=`
- encrypted DEK (Vault ciphertext): `vault:v1:CaJUEhy0Qc5iOYQBh9wxj0sGRrqEjjq5I6eF6PfW0ZKe`

4. ** DECRYPT THE DEK **:
```
 curl \
--header "X-Vault-Token: hvs.CAESIFXr5DmN__YcbnOl1lHDX0WfqcMN3WBQqT2hf5Ihxk-UGh4KHGh2cy4wVThiTm9iMXdRa3QxMGJiOWpZaVoyQXA" \
--request POST \
--data '{"ciphertext":"vault:v1:CaJUEhy0Qc5iOYQBh9wxj0sGRrqEjjq5I6eF6PfW0ZKe"}' \
http://localhost:8200/v1/transit/decrypt/file-key

{"request_id":"d23833b4-9314-1bf9-5bee-fceb1cd63130","lease_id":"","renewable":false,"lease_duration":0,
"data":{"plaintext":"aGVsbG8="},"wrap_info":null,"warnings":null,"auth":null,"mount_type":"transit"}
```
5 **Rotate the KEK (file-key)**
- Use your root token for rotation:
```
curl \
--header "X-Vault-Token: hvs.CAESIPB_KRetcC3rbpN-Ivrdp6aS0nphCIa2qtcHzD_lpSABGh4KHGh2cy45eUhEcnlKc2pxVmdWdXZ5VVdWVlliVk4" \
--request POST \
http://localhost:8200/v1/transit/keys/file-key/rotate

{"request_id":"14102a6e-8b29-4923-96e8-ee1c9aed1c72","lease_id":"","renewable":false,"lease_duration":0,
"data":{"allow_plaintext_backup":false,"auto_rotate_period":0,"deletion_allowed":false,"derived":false,"exportable":false,
"imported_key":false,"keys":{"1":1773291050,"2":1773295778},"latest_version":2,
"min_available_version":0,"min_decryption_version":1,"min_encryption_version":0,"name":"file-key","supports_decryption":true,
"supports_derivation":true,"supports_encryption":true,"supports_signing":false,"type":"aes256-gcm96"},
"wrap_info":null,"warnings":null,"auth":null,"mount_type":"transit"}

```
- Check metadata:
```
curl \
--header "X-Vault-Token: hvs.CAESIPB_KRetcC3rbpN-Ivrdp6aS0nphCIa2qtcHzD_lpSABGh4KHGh2cy45eUhEcnlKc2pxVmdWdXZ5VVdWVlliVk4" \
http://localhost:8200/v1/transit/keys/file-key

{"request_id":"39061f44-9393-e757-e947-6c849b682e83","lease_id":"","renewable":false,"lease_duration":0,"data":{"allow_plaintext_backup":false,"auto_rotate_period":0,"deletion_allowed":false,"derived":false,"exportable":false,"i
mported_key":false,"keys":{"1":1773291050,"2":1773295778},"latest_version":2,"min_available_version":0,"min_decryption_version":1,"min_encryption_version":0,"name":"file-key","supports_decryption":true,"supports_derivation":true,"supports_encryption":true,"supports_signing":false,"type":"aes256-gcm96"},"wrap_info":null,"warnings":null,"auth":null,"mount_type":"transit"}

# "latest_version": 2
```
6. **Encrypt the same DEK again after rotation**

- This should now produce ciphertext starting with vault:v2:.
```
curl \
--header "X-Vault-Token: hvs.CAESIPB_KRetcC3rbpN-Ivrdp6aS0nphCIa2qtcHzD_lpSABGh4KHGh2cy45eUhEcnlKc2pxVmdWdXZ5VVdWVlliVk4" \
--request POST \
--data '{"plaintext":"ddeF4q7bkfw25OT0Hsayx78uPdaBL+3+v/8RW1AKpAE="}' \
http://localhost:8200/v1/transit/encrypt/file-key

{"request_id":"cfb71009-9b8e-d316-e8d9-fd44a3948723","lease_id":"","renewable":false,"lease_duration":0,
"data":{"ciphertext":"vault:v2:O62BxOfTwJbW5jnEdHlzxAecz99h8ZinLRCwhhYxfjxFVJS3QzGWFm1tg76C7qysAxfhF+cRsONUyI1A",
"key_version":2},"wrap_info":null,"warnings":null,"auth":null,"mount_type":"transit"}


Save the returned ciphertext as your new wrapped DEK.
```

7. **Unwrap the new encrypted DEK**

- Use the new vault:v2:... ciphertext from step 6:
```
curl \
--header "X-Vault-Token: hvs.CAESIPB_KRetcC3rbpN-Ivrdp6aS0nphCIa2qtcHzD_lpSABGh4KHGh2cy45eUhEcnlKc2pxVmdWdXZ5VVdWVlliVk4" \
--request POST \
--data '{"ciphertext":"vault:v2:O62BxOfTwJbW5jnEdHlzxAecz99h8ZinLRCwhhYxfjxFVJS3QzGWFm1tg76C7qysAxfhF+cRsONUyI1A"}' \
http://localhost:8200/v1/transit/decrypt/file-key

{"request_id":"8ec14b33-fb09-4503-795a-79a576270852","lease_id":"","renewable":false,"lease_duration":0,
"data":{"plaintext":"ddeF4q7bkfw25OT0Hsayx78uPdaBL+3+v/8RW1AKpAE="},"wrap_info":null,"warnings":null,"auth":null,"mount_type":"transit"}

It should return the same original DEK
```

8. **Unwrap the old encrypted DEK**

- Use your old vault:v1:... ciphertext:
```
curl \
--header "X-Vault-Token: hvs.CAESIPB_KRetcC3rbpN-Ivrdp6aS0nphCIa2qtcHzD_lpSABGh4KHGh2cy45eUhEcnlKc2pxVmdWdXZ5VVdWVlliVk4" \
--request POST \
--data '{"ciphertext":"vault:v1:CaJUEhy0Qc5iOYQBh9wxj0sGRrqEjjq5I6eF6PfW0ZKe"}' \
http://localhost:8200/v1/transit/decrypt/file-key

This should also return the same original DEK.
```
### What this proves

- file-key in Vault = KEK
- your OpenSSL random value = DEK
- old wrapped DEK = vault:v1:...
- new wrapped DEK after rotation = vault:v2:...
- And both: 
  - old wrapped DEK can still be unwrapped
  - new wrapped DEK can be unwrapped too

---
## Java commandline
```
mvn spring-boot:run -Dspring-boot.run.arguments="login"
mvn spring-boot:run -Dspring-boot.run.arguments="generate-dek"
mvn spring-boot:run -Dspring-boot.run.arguments="wrap-dek ddeF4q7bkfw25OT0Hsayx78uPdaBL+3+v/8RW1AKpAE="
mvn spring-boot:run -Dspring-boot.run.arguments="unwrap-dek vault:v1:...."
mvn spring-boot:run -Dspring-boot.run.arguments="rotate-key"
mvn spring-boot:run -Dspring-boot.run.arguments="demo-rotation"
mvn spring-boot:run -Dspring-boot.run.arguments="encrypt-file C:\temp\input.txt C:\temp\envelope.json"
mvn spring-boot:run -Dspring-boot.run.arguments="decrypt-file C:\temp\envelope.json C:\temp\output.txt"
```
- Important: For rotate-key and demo-rotation, set: `export VAULT_ROOT_TOKEN=xxx`
- Normally App shouldn't do rotation

