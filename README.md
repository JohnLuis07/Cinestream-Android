# CineStream Android

Native Java Android version of the CineStream web app in `WEB VER/CINESTREAM-main`.
Uses the same single-module Gradle, binding-backed screen, ViewModel and RecyclerView
conventions as the local Stride Android project. The catalog is native Android UI;
only external playback providers use a WebView.

## Open in Android Studio

1. Open **this root folder** (the folder containing `settings.gradle`).
2. Use JDK 17 or 21, with Android SDK Platform 35 installed.
3. Copy `local.properties.example` to `local.properties`, set `sdk.dir`, and add your
   own metadata API credentials. Gradle properties and environment variables with
   the same names are also supported and take precedence over the local file.
4. Sync Gradle, select the `app` configuration, and run on Android 7.0 / API 24 or newer.

```properties
TMDB_API_KEY=your_own_tmdb_api_key
# Alternatively, a TMDB API Read Access Token (takes precedence over the key):
TMDB_ACCESS_TOKEN=
# Optional supplemental movie/TV search:
OMDB_API_KEY=
```

The Android app does not read or copy the web app's `.env` file. Rebuild after
changing credentials. TV schedule and search work through TVmaze without a key;
movie charts require TMDB. Movie search can also use OMDb. API errors, missing
configuration, empty results, and retry actions are shown in the catalog.
Credentials embedded in a client APK can be extracted: use client-appropriate
metadata keys. Privileged credentials belong in a backend, never in this app.

## Features

- Dark CineStream branding, responsive featured title and poster grid.
- Discover, Movies and TV Shows filters; submitted title search and clear action.
- TMDB trending/search, optional OMDb search, and TVmaze fallback with IMDb IDs.
- Native season/episode lists, real episode metadata, upcoming-episode disabling,
  previous/next aired episodes within a season, guide retry, and source selection.
- The web version's six playback sources, with VidLink disabled for IMDb IDs.
- Embedded playback, reload, HTML video fullscreen, and back-to-exit fullscreen.
- ViewModels retain loaded catalog/guide state across rotation; saved search,
  filter, season, episode, and provider selections restore after process recreation.
- Background HTTPS requests, timeouts, stale-result protection, and bounded image cache.

Catalog metadata does not supply video streams. External players can be unavailable,
show advertising, require cookies, or reject Android WebView playback. Loading a
provider page does not establish that the title is playable. Playback progress is
controlled by those providers and is not synchronized with the app.

The WebView enables JavaScript and DOM storage for playback, allows HTTPS resources,
and accepts third-party cookies for embedded provider sessions. It has no native
JavaScript bridge, file/content access, permission grants, or popup windows. Top-level
navigation is restricted to the selected provider host and its subdomains. Sources
requiring unrelated top-level redirects may not work; select another source.

## Structure

```text
app/src/main/java/com/cinestream/android/
  MainActivity.java, PlayerActivity.java
  ui/          Binding-backed CatalogScreen and PlayerScreen
  adapter/     Catalog, episode and horizontal choice RecyclerViews
  model/       Media, show, episode and immutable screen states
  service/     HTTPS client, metadata repository and playback URL rules
  viewmodel/   Lifecycle and saved-state-aware catalog/player state
  utils/       Image loading and edge-to-edge window insets
app/src/main/res/
  layout/      Screen and recycled-item XML
  drawable/    Brand icon, placeholders and backgrounds
  values/      Strings, colors and styles
WEB VER/       Existing web source
```

Build configuration matches Stride: Android Gradle Plugin 8.7.3, Gradle 8.11.1,
compile/target SDK 35, min SDK 24, Java source with View Binding enabled.
The wrapper scripts and wrapper JAR were copied from the local Stride project.
Launching, device testing, commits, and publishing are left to the project owner,
as specified in `AGENTS.md`.

## Manual device checks

1. Without credentials, browse/search TV and confirm Movies explains missing configuration.
2. With valid TMDB credentials, browse each filter and search for a known movie/series.
3. Open a movie, switch sources, reload, enter/exit fullscreen, and return to the catalog.
4. Open a TV series, select seasons/episodes, confirm upcoming episodes are disabled,
   and check previous/next controls at the season boundaries.
5. Rotate during loading and playback; verify selections remain and old audio stops.
6. Check offline/retry, no-result search, invalid credentials, large fonts, and system insets.

## Data attribution

[TMDB](https://www.themoviedb.org), [OMDb](https://www.omdbapi.com), and
[TVmaze](https://www.tvmaze.com/api). TVmaze metadata is licensed under
[CC BY-SA](https://creativecommons.org/licenses/by-sa/4.0/).
This product uses the TMDB API but is not endorsed or certified by TMDB.
Review each provider's usage and branding terms before distribution.
