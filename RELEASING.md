# Releasing calc

How calc releases work, and how to cut one. Everything tagged `v*` on `main`
is built, gated, signed and published to GitHub Releases by CI — no manual
build steps, releases are reproducible from any checkout.

## The pipeline at a glance

```
push tag v1.2.3
      │
      ▼
Release workflow (.github/workflows/release.yml)
      │  1. lint + unit tests          ← hard gate, tag is abandoned if red
      │  2. decrypt signing key from repo secrets → keystore.properties
      │  3. ./gradlew :app:assembleRelease  (R8 minify + resource shrink)
      ▼
GitHub Release published
       tagged latest, APK attached as calc-v1.2.3.apk,
       release notes = the annotated tag message
```

A separate CI workflow (`.github/workflows/ci.yml`) runs the same lint,
unit-test and debug/release-assembly battery on every push and PR to `main`.

## One-time setup (already done for v1.0.0)

The signing key never lives in git. It exists in exactly two places:
the local gitignored copy and the repo's encrypted GitHub secrets.

```bash
# generate the key (once)
keytool -genkeypair -v -keystore calc-release.jks -alias calc \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=com.sumon.calc, O=sumon317"

# local side: gitignored, read by gradle
printf 'storeFile=calc-release.jks\nstorePassword=%s\nkeyAlias=calc\nkeyPassword=%s\n' \
  "$PASS" "$PASS" > keystore.properties

# CI side: encrypted repo secrets
base64 -w0 calc-release.jks > /tmp/key.b64
gh secret set RELEASE_KEYSTORE < /tmp/key.b64
gh secret set RELEASE_STORE_PASSWORD --body "$PASS"
gh secret set RELEASE_KEY_PASSWORD --body "$PASS"
```

**Back up `calc-release.jks` and its passwords offline (password manager,
encrypted archive).** Losing the key means the app can never be updated
again — a replacement key changes the signature, so Android refuses to
install an update over the old build and users have to uninstall first.

## Secrets reference

| Secret                   | Contents                              |
|--------------------------|---------------------------------------|
| `RELEASE_KEYSTORE`       | `calc-release.jks` base64-encoded     |
| `RELEASE_STORE_PASSWORD` | keystore password                     |
| `RELEASE_KEY_PASSWORD`   | private-key password (same key/alias) |

If `RELEASE_KEYSTORE` is unset, the workflow logs a warning and falls back
to **debug signing** — builds still work, but the APK is not publish-grade
and lacks update compatibility. Always release with secrets set.

## Cutting a release

1. **Start from green.** Confirm CI is passing on the commit you want to
   release (`gh run list --branch main --limit 3 --status success`).
2. **Bump the version** in `app/build.gradle.kts`:
   `versionName` = the new tag without the `v` (e.g. `1.1.0`);
   `versionCode` = previous + 1 — **never reuse or skip a versionCode**;
   Android uses it to order updates.
3. **Commit and push** (`chore(release): prepare v1.1.0` style), wait for CI
   to go green on that commit.
4. **Create the annotated tag**, notes embedded in the message — these become
   the published release body verbatim:

   ```bash
   git tag -a v1.1.0 -m "$(cat <<'EOF'
   calc v1.1.0

   ## Features
   - …
   EOF
   )"
   git push origin v1.1.0
   ```

5. **Verify the result** (`gh run watch` the Release workflow if you want to
   see it happen):

   ```bash
   gh release view v1.1.0          # notes + calc-v1.1.0.apk asset present?
   gh release download v1.1.0      # optional: sanity-install the APK
   ```

## Versioning

Semver-ish (`MAJOR.MINOR.PATCH`, no `v` in `versionName`). Informational
releases with no feature change should still tick `versionCode` — the tag and
versionName may be `1.0.1`-style bumps or empty patches; the APK's update
precedence is decided by `versionCode` alone.

## Troubleshooting

| Symptom                                        | Likely cause / fix                                              |
|------------------------------------------------|-----------------------------------------------------------------|
| Release workflow fails at lint/tests           | Merge-blocked-quality code on the tag — fix on `main`, re-tag a new commit |
| Fails decrypting / signing                     | Secrets missing or stale — re-check `gh secret list` and re-upload the keystore |
| Workflow skipped entirely                      | The tag was not on `main`'s history or the tag name doesn't match `v*` |
| Release body shows the commit message instead of the curated tag notes | The CI checkout skipped the annotated tag object — fixed by `fetch-tags: true` on the Release job's checkout (this happened on v1.0.0; repair a live release with `gh release edit vX.Y.Z --notes-file`) |
| "App not installed" when sideloading an update | Signature mismatch — uninstall the old build first, or (correctly) release with the same key |
| Same version published twice                   | Re-pushed an existing tag — delete the GitHub release + tag, address CI, then re-tag from the fixed commit |
