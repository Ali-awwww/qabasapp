# Qabas Studio — Observability and release services

This document records which integrations are safe to automate now and which require the real Firebase/Google Play project configuration.

## Enabled by repository configuration

- **Dependabot:** weekly Gradle/version-catalog and GitHub Actions dependency update pull requests.
- **Secret scanning:** this repository is public. GitHub documents automatic secret scanning for public repositories; this repository-level Gitleaks workflow adds a CI scan over full Git history and pull-request changes. Review alerts in GitHub's Security tab and rotate any credential confirmed as exposed.

## Firebase App Distribution — CI hook prepared

The Android CI workflow will distribute the successful non-PR APK only when all three GitHub Actions secrets exist:

- `FIREBASE_APP_ID`: the Firebase Android App ID registered for package `com.aistudio.qabas.kxmpzq`.
- `FIREBASE_SERVICE_ACCOUNT_JSON`: service-account JSON with Firebase App Distribution permissions. Store the entire JSON as a GitHub Actions secret; never commit it.
- `FIREBASE_TESTER_GROUPS`: an existing Firebase App Distribution group alias (or comma-separated aliases).

Without these secrets the distribution step is skipped and the normal APK build/release workflow is unchanged. The service account should have only the permissions required to distribute builds.

## Crashlytics and Performance Monitoring — blocked on Firebase configuration reconciliation

The app currently has no active Crashlytics or Performance Monitoring integration:
- Crashlytics dependencies are commented out in `app/build.gradle.kts`.
- Performance Monitoring SDK/plugin are not configured.
- The repository does not contain `app/google-services.json`; it is generated/injected during builds.
- The fallback Firebase project identifiers are inconsistent: the Gradle fallback uses `gen-lang-client-0140553990`, while the Android CI fallback uses `gen-lang-client-0938365900`. CI may instead use the `GOOGLE_SERVICES_JSON` secret.

Do not enable telemetry against either fallback until the owner confirms which Firebase project is authoritative and supplies its downloaded `google-services.json` for the exact package above. Once reconciled, add Crashlytics and Performance Monitoring SDK/plugin configuration, then validate real delivery in the Firebase console. Telemetry collection and the app privacy notice should be reviewed before rollout.

## Android Vitals — Google Play Console setup

Android Vitals is a Play Console reporting feature, not an Android SDK or repository workflow. It becomes useful after the matching application is published on Google Play and devices report vitals. Review the app's Android Vitals dashboard in Play Console; no code change is required here.

## Validation boundaries

- CI can validate the APK build and static secret scanning.
- CI cannot prove that Firebase services receive telemetry without the correct Firebase app registration/configuration and a real device run.
- App Distribution is not exercised by pull-request builds because secrets are not exposed to PR workflows.
- Android Vitals cannot be validated from GitHub Actions.
