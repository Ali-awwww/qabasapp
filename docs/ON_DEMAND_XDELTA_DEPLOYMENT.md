# On-demand XDelta deployment checklist

This feature is implemented in the repository but is **not enabled in the live Supabase project** by these source changes alone.

## Required deployment sequence

1. Apply the migrations that create `app_update_delta_jobs`, `app_update_delta_cache`, and the restricted `replace_github_token` function to the Qabas Supabase project.
2. Deploy Edge Functions:
   - `qabas-xdelta-request`
   - `qabas-xdelta-status`
   - `qabas-xdelta-callback`
   - `qabas-xdelta-admin-dispatch` (owner-only developer release workflow dispatch)
   - `qabas-xdelta-token-admin` (owner-only GitHub token status and secure save)
3. Configure `QABAS_OWNER_EMAIL` for both XDelta admin functions to match the canonical owner account email (`admin@qabas.studio` by default in the Android owner guard). The developer-only XDelta screen sends a GitHub PAT to `qabas-xdelta-token-admin`; the function validates the token against the GitHub account and repository workflow, then stores it through the restricted `replace_github_token` database function. The PAT is never returned to Android or stored in local preferences. Use a fine-grained token scoped to `Ali-awwww/qabasapp` with **Actions: Read and write** and **Contents: Read**. The endpoint can verify account/repository workflow read access; workflow write permission must be confirmed by the owner before relying on dispatch.
4. Add repository Actions secrets:
   - `SUPABASE_URL`: the Qabas Supabase project API URL.
   - `XDELTA_CALLBACK_SECRET`: a dedicated high-entropy callback secret. Configure the same value as a Supabase Edge Function secret and a GitHub Actions repository secret; do **not** use the project service-role key for this callback.
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
