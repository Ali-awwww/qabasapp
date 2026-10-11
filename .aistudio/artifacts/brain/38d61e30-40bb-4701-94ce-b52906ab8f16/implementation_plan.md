# Firebase Architecture & Real-Time Cloud Integration Plan

A robust, enterprise-grade Firebase integration blueprint designed to leverage the core competitive strengths of Firebase over Supabase (offline-first SDK caching, instant snapshot listeners without WebSocket plumbing, native Google Credential Manager identity, declarative security rules, and FCM push notifications), deeply integrated into the app's user profile and saved items UI.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The architectural directions confirmed during Phase 1 clarification have been incorporated:
>
> - **Confirmed Features**: Real-time Firestore snapshots with offline persistence cache, native Google Sign-In via Jetpack Credential Manager, hardened Firestore Security Rules with schema validation, and Firebase Cloud Messaging (FCM) foundations.
> - **Confirmed Packaging**: Direct UI integration into the application's existing screens (User Profile, Quran & Inspiration bookmarking) rather than an isolated standalone library.
> - **Confirmed Target Domain**: User profile synchronization and saved Quran verses / spiritual inspirations.
> - **Backend Provisioning Requirement**: Once this plan is approved, the standard 2-step `set_up_firebase` tool workflow (`userConfirmedTermsAcceptedInUI: false` then `true`) will provision Firestore and Google Sign-In credentials.

---

## 1. Overview & Core Concept

### What It Delivers
This feature delivers an offline-first, real-time cloud persistence layer for **Qabas (قبس)**. Users can sign in seamlessly with their Google account via the Android 14+ Jetpack Credential Manager bottom sheet, bookmark verses and spiritual reflections with instant local latency, and have their state synchronized in real-time across devices with zero network blocking.

### Firebase vs. Supabase Strategic Advantages Leveraged
1. **First-Class Offline Persistence & Cache**: Unlike Supabase (which relies on PostgreSQL over REST/WebSockets requiring custom client-side caching libraries like Room/WatermelonDB), the Firebase Firestore Android SDK maintains a native, indexed on-device cache out of the box. Writes and reads occur immediately with zero latency, syncing deterministically with the cloud upon reconnect.
2. **Zero-Plumbing Real-Time Snapshots**: Firestore's `snapshots()` coroutine Flow connects directly to Google's edge infrastructure with multi-region synchronization, automatic reconnect backoff, and delta change-tracking (`DocumentChange.Type.ADDED / MODIFIED / REMOVED`).
3. **Native Google Android Ecosystem & Credential Manager**: Seamless 1-tap Google Sign-In without web redirects or custom OAuth callbacks, binding directly to Firebase Authentication and Google Play Services.
4. **Declarative Document Security Rules**: Schema validation at the document level (`isValidUser`, `isValidSavedItem`) avoiding SQL injection surface area and enabling local unit testing via the Firebase Emulator Suite.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Authentication Gate & Profile Flow**:
   - The user opens the Profile or Settings screen.
   - If unauthenticated, a refined Cybernetic Glassmorphic Card displays a "تسجيل الدخول بحساب Google" button.
   - Tapping invokes Jetpack `CredentialManager` bottom sheet (`GetSignInWithGoogleOption`).
   - Upon authentication, the card transitions dynamically into the authenticated Profile HUD showing user avatar, email, sync status badge ("متزامن لحظياً"), and sign-out controls.
2. **Saved Quran & Inspiration Item Synchronization**:
   - When browsing verses, inspirations, or daily adhkar, clicking the bookmark/heart icon instantly saves the document locally with optimistic UI update.
   - A subtle sync indicator (Cloud Check icon) indicates whether the item is cached locally or committed to the server.
   - Deleting or editing reflects instantly across all open screens and devices in real time.

### Visual Identity & Theme
- **Theme Posture**: Cybernetic Glassmorphism + Linear Minimalist HUD (Dark spatial slate `#090D16`, glowing emerald accent `#10B981`, cyan telemetry `#06B6D4`, muted border `#1E293B`).
- **Typography**: Cairo Display font for Arabic headings and body copy, ensuring zero clipping and comfortable line heights.
- **Micro-interactions**: Spring transitions on bookmark toggles, subtle pulse glow when syncing, and clear empty states for bookmarks.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Offline Cache Strategy
- **Chosen Approach**: Firestore Enterprise cache with `PersistentCacheSettings` and `ServerTimestampBehavior.ESTIMATE`.
- **Why**: Allows instant UI reads and writes without waiting for network ACK, fulfilling the app's high responsiveness expectation.
- **Supabase Contrast**: In Supabase, offline queueing requires managing local SQLite/Room tables and manually synchronizing CRUD mutations.

### Decision 2: Multi-User Isolation & Security Rules
- **Chosen Approach**: Granular path-based documents under `/users/{userId}/saved_items/{itemId}` and `/users/{userId}` with strict schema assertions.
- **Why**: Provides total tenant isolation; users can only read and write their own data, verified both locally via emulator unit tests and on live Firestore instances.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      Jetpack Compose UI                     │
│   ┌───────────────────────┐     ┌───────────────────────┐   │
│   │   ProfileScreen HUD   │     │   SavedItemsScreen    │   │
│   │ (GoogleSignInButton)  │     │ (Real-time Bookmarks) │   │
│   └───────────┬───────────┘     └───────────┬───────────┘   │
└───────────────┼─────────────────────────────┼───────────────┘
                │                             │
┌───────────────▼─────────────────────────────▼───────────────┐
│                    ViewModels (MVVM)                        │
│   - AuthViewModel (AuthState, UserProfile)                  │
│   - SavedItemsViewModel (UiState<List<SavedItem>>)          │
└───────────────────────────────┬─────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────┐
│                      Repository Layer                       │
│   - AuthRepository (CredentialManager + FirebaseAuth)       │
│   - SavedItemsRepository (FirebaseFirestore.snapshots())    │
└───────────────────────────────┬─────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────┐
│                 Firebase Android SDK Core                   │
│   ┌───────────────────────┐     ┌───────────────────────┐   │
│   │  Local SQLite Cache   │◄────┤ Firestore Connection  │   │
│   │  (Instant Read/Write) │     │ (Snapshot Listeners)  │   │
│   └───────────────────────┘     └───────────┬───────────┘   │
└─────────────────────────────────────────────┼───────────────┘
                                              │ TLS / gRPC
                                              ▼
                                 ┌─────────────────────────┐
                                 │   Google Cloud Backend  │
                                 │  - Firebase Auth        │
                                 │  - Cloud Firestore      │
                                 │  - Firestore Rules      │
                                 │  - Cloud Messaging FCM  │
                                 └─────────────────────────┘
```

### Data Schema (`firebase-blueprint.json` & Firestore Models)

1. **User Profile (`/users/{userId}`)**:
   ```json
   {
     "userId": "string",
     "displayName": "string",
     "email": "string",
     "photoUrl": "string?",
     "fcmToken": "string?",
     "createdAt": "timestamp",
     "updatedAt": "timestamp"
   }
   ```

2. **Saved Item (`/users/{userId}/saved_items/{itemId}`)**:
   ```json
   {
     "id": "string",
     "userId": "string",
     "itemType": "QURAN_AYAH | INSPIRATION | DHIKR",
     "title": "string",
     "content": "string",
     "reference": "string",
     "tags": ["string"],
     "createdAt": "timestamp",
     "updatedAt": "timestamp"
   }
   ```

### Implementation Steps (Post-Approval)
1. **Backend Provisioning**: Call `set_up_firebase` tool to provision the Firestore database and Google Sign-In client configuration.
2. **Schema & Security Rules**: Define `firebase-blueprint.json` and strict `firestore.rules` validating `request.auth.uid == userId` and schema fields.
3. **Local Rules Verification**: Run JavaScript and Robolectric emulator tests to verify security before deployment.
4. **Data Models & Repository**: Author `SavedItem.kt`, `UserProfile.kt`, `SavedItemsRepository.kt`, and `AuthRepository.kt` with robust error logging (`handleFirestoreError`).
5. **UI Integration**: Update Profile and Quran/Inspiration screens with real-time reactive Compose state collectors and Google Sign-In buttons.
