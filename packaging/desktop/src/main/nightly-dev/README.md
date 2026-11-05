# Nightly Dev update identity (Outlaw1297 fork)

Frostguard Nightly Dev embeds this fork-local Ed25519 public key and checks:

`https://github.com/Outlaw1297/wosbot/releases/download/nightly_dev/frostguard-nightly_dev-manifest.json`

It never uses the official Stable/Nightly update feed.

The private key lives only in the repository secret
`FROSTGUARD_NIGHTLY_DEV_UPDATE_SIGNING_PRIVATE_KEY_BASE64` (Base64 PKCS#8
Ed25519). `Build — Windows Nightly Dev` reads that secret when it signs the
rolling channel. If the secret is empty, signing fails and no manifest is
published. Do not commit a private key.

The key was rotated in November 2026. Existing Nightly Dev installs, which
trust the old public key, need a one-time reinstall of the MSI before they can
verify updates signed by `frostguard-nightly-dev-outlaw1297-2026-11b`.
