# QABAS Crash Telemetry — Implementation Decision

## Status
Draft — pending formal QABAS governance approval.

**Feature ID:** `QABAS-FEAT-001` (working feature identifier; it is not a formal
QABAS document ID).

This file records a proposed technical design only. It is not an approved
QABAS decision, EXEC, or DEVSPEC, and must not be treated as authorization
to merge or release the implementation.

The linked decision, EXEC, and DEVSPEC documents still require their own
official `QABAS-XXX` document IDs from the global registry and the review
and approval sequence required by QABAS-000. The implementation remains
unapproved until that governance sequence is complete.

## Context
QABAS already has `QabasCrashGuard`, local diagnostic reports, ANR monitoring, and Android `ApplicationExitInfo` scanning. The missing capabilities are durable cloud delivery, explicit startup-stage breadcrumbs, and broader classification of process exits.

## Decision
- Extend the existing `QabasCrashGuard`; do not replace it.
- Keep the existing detailed diagnostic text files on-device.
- Persist a separate app-private JSON report atomically before scheduling any upload.
- Use WorkManager with a connected-network constraint, exponential retry, and bounded batches.
- Upload to the existing `public.dev_bug_reports` table using a PostgREST-only client without Supabase Auth, so submissions use the anonymous database role regardless of login state.
- Use a stable report UUID and PostgREST upsert with duplicate-ignore semantics to make retries idempotent.
- Keep the existing RLS policies unchanged. Anonymous inserts require an open status, an empty developer reply, and a null `user_id`; the uploader sends those exact values. The hardened read/update/delete policies remain unchanged.
- Persist startup-stage breadcrumbs and report failures from existing startup `runCatching` blocks without changing their continuation behavior.
- Expand next-launch `ApplicationExitInfo` classification to include signaled termination, excessive resource usage, initialization failure, and dependency death when Android exposes those reasons.

## Diagnostic confidence
The in-process ANR watchdog reports a delayed main-looper response after its configured sampling window. This is useful evidence of a responsiveness incident, but it is not by itself proof that Android recorded a system ANR. Watchdog-only incidents therefore remain **probable**. A system-recorded ANR is separately identified from the next-launch `ApplicationExitInfo` history when that record is available. Other confidence labels must likewise describe evidence strength, not imply certainty beyond the collected data.

## Data minimization
Cloud reports contain the event timestamp, source/thread, exception type/message/stack, startup stage, current/previous screen, last action and its age, a bounded snapshot of the recent in-memory CrashBreadcrumbs ring buffer, a runtime context snapshot, app version, device model, and Android version. Breadcrumb and context sections are sanitized again at the telemetry boundary and capped to bounded lengths. The uploader does not send account email, account number, Supabase UID, authentication tokens, screenshots, or preference contents. Credential-like strings and email addresses are redacted. Exception messages and stack traces can still contain application/runtime values; the privacy notice must disclose this diagnostic collection before release.

## Platform limits
Android does not guarantee an in-process callback for `SIGKILL`, force-stop, power loss, or every native termination. `ApplicationExitInfo` is inspected on the next launch when the OS retains a record. A signal-based exit is classified as a system termination, not automatically asserted to be a code crash. Native tombstone protobuf bytes are not uploaded: Base64 encoding would make their contents opaque to credential/email redaction. Native-exit reports retain the exit classification and system metadata instead.

## Scope boundaries
No UI, login flow, Quran assets, AI features, or unrelated application behavior is changed. No Firebase Crashlytics configuration is introduced; the repository's observability note identifies Firebase project reconciliation as a separate prerequisite.

## Validation
- Unit tests cover anonymous payload constraints, credential/email redaction, and durable local queue persistence.
- CI must compile the Android app, run the unit-test suite, assemble the debug APK, and verify its signature.
- Real delivery still requires a device run and confirmation that a report arrives in the support console.
