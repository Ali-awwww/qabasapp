# Canonical cloud identity rollout

This change makes Supabase Auth the source of truth for Qabas account identity.

## What the migration changes

- `public.users.id` and `public.users.external_id` are set to the authenticated Supabase Auth UID for profiles that can be matched unambiguously.
- Existing ownership references are migrated only when a unique Auth-email mapping proves the target UID. Ambiguous profile/email cases are left unchanged for manual review.
- Existing duplicate/empty handles and account numbers are repaired before unique indexes are created.
- A private, transactional counter issues permanent `#QBS-` account numbers. The counter update is part of the profile insert transaction, so failed inserts do not consume a number. Deleting a user does not decrement the counter.
- A database trigger owns UID, account number, role, status, and eligibility fields. Client writes cannot choose or change those values.
- Username and account-number sign-in is resolved only inside the `qabas-auth-identifier-login` Edge Function. The function validates the password with Supabase Auth and returns an Auth session, never an unauthenticated profile-email lookup result.

## Deployment order

1. Take a database backup and test against a staging project first.
2. **Reconcile migration history before applying this migration.** At implementation time, the connected project had older applied migrations while repository migrations `20261102172825_repair_reels_and_social_permissions.sql` and `202611030001_fix_social_permissions_and_sequencing.sql` were not present in the remote migration history. Do not run a blind production `supabase db push`; inspect the pending migration list and apply/reconcile each migration in order in staging.
3. Apply `202611030002_canonical_cloud_user_identity.sql` only after the earlier migrations are understood and validated.
4. Deploy the identifier-login function:
   `supabase functions deploy qabas-auth-identifier-login --project-ref egkgygbvoeevakwkbvfu`
5. Confirm the Edge Function runtime has the standard `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY` environment variables. The service-role key must remain server-side and must never be added to the Android build or app config.
6. Build and distribute a new Android release. Existing installed APKs do not contain the new identifier-login flow.
7. Run the smoke tests below before treating the rollout as complete.

## Legacy-account behavior

- Existing Auth users with a uniquely matching `public.users.email` are mapped to their Auth UID and keep their current handle/account number when those values are unique.
- Existing Auth users without a profile row receive a cloud profile on their first successful email/password login after the migration; the database then issues their handle and account number.
- Those users must sign in by email once before username/account-number sign-in is available.
- Ambiguous email/profile matches and content references without an unambiguous ownership mapping are intentionally not reassigned. Review these manually rather than guessing.
- Old local SharedPreferences values are caches only. After authentication, the app overwrites them from the canonical cloud profile.

## Security and administrator roles

The migration uses trusted `auth.jwt() -> 'app_metadata' ->> 'role'` claims for privileged user-directory reads/updates and role assignment. It does not trust client-editable `user_metadata`. Ensure the companion role-hardening change (PR #27, `fix(auth): trust server-managed Supabase app_metadata roles`) is reviewed and merged before relying on those dashboard/admin privileges.

The identifier-login Edge Function has `verify_jwt = false` because it is called before a user has a session. It only accepts bounded identifier/password input, resolves a unique handle/account number server-side, delegates password verification and rate limiting to Supabase Auth, returns generic credential failures, and never returns the lookup email by itself.

## Smoke tests

- Register a new user with a chosen handle; verify `users.id = auth.users.id`, `external_id = id`, a unique `custom_id`, and a unique `#QBS-` account number.
- Register two users concurrently; verify neither handle nor account number collides.
- Force an insert transaction to fail in staging; verify the transactional account-number counter rolls back.
- Sign out, sign in by email, then sign in on a second device by `@handle` and by `#QBS-` number. Verify the same UID, handle, and number are restored.
- Publish a reel, comment, like/unlike, bookmark/unbookmark, and follow/unfollow. Verify each written owner ID equals the current Auth UID.
- Attempt to spoof a different `user_id`, handle, account number, or role from a normal client; verify the database rejects or ignores the protected fields.
- Review rows that were deliberately left unmatched by the migration before any manual reassignment.