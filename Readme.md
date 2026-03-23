# Pre-req
1. Login to get `CLIENT_TOKEN`
```html
curl -X POST ${VAULT_ADDR}/v1/auth/${MOUNT_PATH}/login/${USER_NAME} \
     -D '{"password": "XXX"}

```
2. Fetch `ROLE_ID`
```html
curl -X GET ${VAULT_ADDR}/v1/auth/${MOUNT_PATH}/approle/role/${APP_ROLE}/role-id \
     -H "X-Vault-Token: ${CLIENT_TOKEN}"
```
3. Generate `SECRET_ID`
```html
curl -X POST ${VAULT_ADDR}/v1/auth/${MOUNT_PATH}/approle/role/${APP_ROLE}/role-id \
    -H "X-Vault-Token: ${CLIENT_TOKEN}"
```

# Set-up
```yml
app:
  vault:
    uri: ${VAULT_URL}
    role-id: ${VAULT_ROLE_ID}
    secret-id: ${VAULT_SECRET_ID}
#    root-token: ${VAULT_ROOT_TOKEN:} => Needed for Rotation
    transit:
      mount-path: ${MOUNT_PATH}
      key-name: ${KEY-NAME}
```
> NOTE :️  `Key-name` isn't the key-material. It is like a **service endpoint** that vault uses to get the KEK

# Flow
```
[ USER APP ]                               [ HASHICORP VAULT ]
|======= ENCRYPTION FLOW =======|            |
|--------1. Generate DEK (Random Bytes)      | (NOTE: DEK stays in App)
|                                            |
|------- 2. Send DEK to /transit/wrap ------>|
|                                            |--- [ KEK (Master Key) ]
|                                            |          |
|<------ 3. Receive Wrapped DEK (Cipher) <---| <---(Encrypts DEK)
|                                            |
|-- 4. Encrypt Huge File using DEK           | (NOTE: Vault NOT involved | DEK is thrown awat)
|      (Streaming AES-GCM)                   |
|                                            |
|-- 5. Store File + Wrapped DEK -------------|
|                                            |
|======= ENCRYPTION FLOW =======|            |
|------- 6. Send Wrapped DEK to /unwrap ---->|
|                                            |--- [ KEK (Master Key) ]
|                                            |          |
|<------ 7. Receive Original DEK <-----------| <---(Decrypts DEK)
|                                            |
|-- 8. Decrypt Huge File using DEK --------->| (Vault NOT involved)
|      (Streaming AES-GCM)                   |
```
---
# Encryption Strategies 
## Option 1: Monolithic Envelope (All-in-Memory)
Suitable For smaller file sizes (~<50 MB)
1. **DEK Generation**: The application generates a one-time Data Encryption Key (DEK) in memory.
2. **In-Memory Processing**: The application reads the entire file into a byte array.
3. **Envelope Creation**:
   * The file is encrypted, Base64-encoded, and stored directly inside a single JSON "envelope" alongside the wrapped DEK.
4. **Pros**: Self-contained and simpler
5. **Trade-off**: High memory overhead. Attempting this with large files will result in `OutOfMemoryError` due to JVM heap limits and Base64 expansion (~33% size increase).

## Option 2: Detached Metadata Streaming (Sidecar Pattern)
1. **DEK Generation:** The application generates a one-time DEK but never stores the file content in memory.
2. **Streaming Transformation**:
   * Using CipherOutputStream, the file is processed in small chunks (e.g., 8KB). 
   * Data flows from the source file, through the AES engine, and directly to the disk.
3. **Binary Separation**:
   * To avoid the overhead of Base64 and JSON parsing, the encrypted data is stored as a raw .bin file.
   * Metadata Linking: A companion .json file is created containing only the "Wrapped DEK" and the IV (Initialization Vector).
4. **Pros**: Constant memory footprint (e.g., < 100MB RAM) regardless of whether the file is 1GB or 100GB.
5. **Trade-off**: Two files (the `.bin` + `.json`). Must ensure they stay together. Losing the small .json file, causes the encrypted .bin file becomes permanently, unrecoverable.

## Option 3. Single-File Header Streaming
- Robust method for handling large-scale encryption. It avoids "file fragmentation" by attaching the metadata directly to the encrypted data.

**How it Works:**
1. **Header Generation**: The app generates a small JSON object (Metadata) containing the `Wrapped DEK`, `IV`, and `Key Version`.
2. **Length Prefixing**: The app writes a 4-byte integer to the start of the file. This integer tells the decrypter exactly how many bytes to read to find the JSON metadata.
3. **The Metadata "Stamp"**: The JSON metadata is written immediately after the length prefix.
4. **The Encrypted Payload**: The rest of the file is filled by streaming the original data through the AES/GCM cipher.

**Key Advantages:**
1. **Single Artifact**: No risk of losing the "key" (the JSON) because it is physically attached to the data.
2. **Constant RAM**: We still process the 1GB of data in small, efficient chunks.
3. **Self-Describing**: The file contains everything it needs (except the Master Key in Vault) to be decrypted.
```
Offset (h)  00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F  Decoded Text
-------------------------------------------------------------------------------
00000000    00 00 00 7A 7B 22 77 72 61 70 70 65 64 44 65 6B  ...z{"wrappedDek
00000010    22 3A 22 68 76 2E 62 31 2E 2E 2E 22 2C 22 69 76  ":"hv.b1...","iv
00000020    22 3A 22 64 47 56 7A 64 47 39 79 49 48 4E 30 59  ":"dGVzdG9yIHN0Y
...         ... (more JSON metadata) ...                     ...
00000070    5A 58 68 4C 62 33 4A 6B 22 7D AD 4F 91 22 C1 3E  ZXhLb3Jk"}.O."Á>
00000080    F2 88 10 A3 44 55 B1 09 23 88 11 02 FF 67 21 00  ò..£DU±.#...ÿg!.
```
**Breaking Down the Hex**:
- `00 00 00 7A` (**The Length Prefix**): These are the first 4 bytes. In Hex, 7A equals 122. This tells your Java code: "The next 122 bytes are your JSON metadata."
- `7B 22 77 72...` (**The Metadata Stamp**): This is the UTF-8 encoding for {"wr.... Your code reads exactly 122 bytes into a string and hands it to Jackson to parse your wrappedDek and iv.
- `7D` (**The Closing Brace**): This is the } character, marking the end of the JSON.
- `AD 4F 91...` (**The Encrypted Payload**): Immediately following the JSON is the raw binary ciphertext. Your CipherInputStream starts reading from exactly this offset, streaming the 1GB file until the end.

---

* [EncryptionDecyption](src/main/java/com/example/vaultdemo/service/EnvelopeCryptoService.java)
* [Resource](src/main/resources)
* [WrapUnwarp](src/main/java/com/example/vaultdemo/service/VaultTransitService.java)