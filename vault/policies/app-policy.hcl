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