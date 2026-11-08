# On-demand XDelta deployment checklist

This feature is implemented in the repository but is **not enabled in the live Supabase project** by these source changes alone.

## Required deployment sequence

1. Apply the migrations that create `app_update_delta_jobs` and `app_update_delta_cache` to the Qabas Supabase project.
2. Deploy Edge Functions:
   - `qabas-xdelta-request`
   - `qabas-xdelta-status`
   - `qabas-xdelta-callback`
3. Confirm the server-side `app_secrets` entry named `github_token` is active and its GitHub credential has Actions workflow-dispatch and release-asset write access for `Ali-awwww/qabasapp`.
4. Add repository Actions secrets:
   - `SUPABASE_URL`: the Qabas Supabase project API URL.
   - `SUPABASE_SERVICE_ROLE_KEY`: the service-role secret for that project. Keep it out of client builds, source control, logs, and PR comments.
5. Merge the workflow changes to the default branch (`main`). On-demand dispatches target `main`, which must contain the declared workflow inputs and completion callback.
6. Only after reviewing the deployed schema, function permissions, and workflow secrets, perform a controlled test with an approved old APK base and an active target release.

## Flow and safety checks

- The Android client sends only installed version code and SHA-256 to the authenticated request function.
- Server-side checks bind both base and target identities to the approved `app_updates` registry.
- The shared cache reserves each exact base/target digest pair to prevent concurrent duplicate builds.
- GitHub Actions validates both downloaded APK digests, reconstructs the target APK from the patch, checks the reconstructed SHA-256, and discards patches that are not smaller than the full APK.
- The completion callback uses the repository Actions secret and only accepts GitHub release download URLs for this repository.
- The Android client validates patch SHA-256, installed base SHA-256, and reconstructed target APK identity/hash; it falls back to the approved full APK on unavailable/invalid delta metadata or patch failure.

## Validation still required

- Android: `./gradlew :app:assembleDebug` and `./gradlew :app:test`.
- Supabase: migration application, Edge Function deployment, and authenticated integration tests.
- GitHub Actions: one real workflow-dispatch run with the repository secrets configured.
- Device: at least one controlled installation/update test measuring actual patch and full APK sizes.

Do not enable production distribution before these validations pass.
