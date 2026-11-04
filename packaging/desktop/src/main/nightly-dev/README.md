# Nightly Dev update identity (Outlaw1297 fork)

Frostguard Nightly Dev embeds this fork-local Ed25519 public key and checks:

`https://github.com/Outlaw1297/wosbot/releases/download/nightly_dev/frostguard-nightly_dev-manifest.json`

It never uses the official Stable/Nightly update feed.

`update-signing-private-key.base64` signs that rolling channel from the
`Build — Windows Nightly Dev` workflow. Prefer moving the private key into the
repository secret `FROSTGUARD_NIGHTLY_DEV_UPDATE_SIGNING_PRIVATE_KEY_BASE64` and
deleting the committed private key before any upstream contribution of this
channel.
