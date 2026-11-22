# خادم المسلم / Muslim AI Assistant — Brand

Official name decided by the owner (2026-11-22): **خادم المسلم** (Arabic),
**Muslim AI Assistant** (English). The app was previously branded "مساعد المسلم"
under the Qabas (قبس — a spark of light) name.

## Identity

- Motif: a geometric lantern with radiating light. No human or animal imagery.
- Palette: deep emerald `#004E37`, gold `#E3C878`, dim gold `#C9A24A`,
  cream `#F5EFE0`, warm white flame `#FFF7D6`.
- No music, no audio in brand assets.

## Assets

| File | Use |
| --- | --- |
| `docs/brand/khadim-logo.svg` | Master logo lockup (emblem + wordmark), SVG source |
| `app/src/main/res/drawable/ic_khadim_logo.xml` (108×108 vector) | Lantern emblem — app icon (`AndroidManifest`), in-app logo (About screen via `CyberneticLogo`, notification fallback rendered to bitmap) and adaptive-icon foreground |
| `app/src/main/res/drawable/ic_khadim_splash.xml` (360×640 vector) | Splash artwork (hanging lantern, upper half) |
| `app/src/main/res/drawable/splash_background.xml` | Cold-start window background (emerald + centred splash) |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` | Adaptive-icon scaffolding (kept for a future switch back to `@mipmap/ic_launcher`; the manifest currently points at the vector drawable so the icon works on all API levels incl. 24–25) |
| `~/workspace/rename-khadim/khadim-play-store-icon-512.png` | 512×512 Play Store icon (outside the repo) |

> All brand art in the repo is vector (UTF-8 text): the former raster files
> (`img_qabas_logo.png`, `img_khadim_emblem.png`, `img_khadim_splash.png`,
> `img_qabas_splash.jpg`, `mipmap-*/ic_launcher*.png`) were replaced or
> removed so the branch stays pushable through the GitHub snapshot-push
> connector (text only, no binaries). The removed PNGs linger as dead files
> on the remote branch until deleted via the GitHub web UI; nothing in the
> code references them.

## Splash flow

1. Cold start: `MainActivity` uses `Theme.Khadim.Splash`
   (`windowBackground` = `splash_background`) from the manifest.
2. `MainActivity.onCreate()` calls `setTheme(R.style.Theme_MyApplication)`
   before `super.onCreate()`.
3. Compose shows `KhadimSplashScreen` as an overlay (max ~1.4 s, ~0.4 s with
   reduced motion) with a fade-in, a pulsing golden glow around the lantern,
   and the wordmark fading in — then the normal app content underneath is
   revealed. App initialisation keeps running underneath the overlay.
