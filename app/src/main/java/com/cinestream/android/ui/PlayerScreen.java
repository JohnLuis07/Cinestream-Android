package com.cinestream.android.ui;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.ComponentActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cinestream.android.R;
import com.cinestream.android.adapter.EpisodeAdapter;
import com.cinestream.android.databinding.ScreenPlayerBinding;
import com.cinestream.android.model.Episode;
import com.cinestream.android.model.PlayerState;
import com.cinestream.android.service.PlayerSources;
import com.cinestream.android.viewmodel.PlayerViewModel;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Native controls around a restricted WebView used only for third-party playback.
 */
public final class PlayerScreen implements FeatureScreen {
    // Explicit ad-network hosts only. Do not match generic URL words such as "ad"
    // or block unknown hosts: playback relies on third-party frames and media CDNs.
    private static final Set<String> AD_HOSTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "googletagservices.com", "popads.net", "popcash.net", "adsterra.com",
        "propellerads.com", "exoclick.com", "trafficjunky.net", "juicyads.com",
        "hilltopads.net", "clickadu.com", "ad-maven.com")));

    private final ComponentActivity activity;
    private final ScreenPlayerBinding binding;
    private final PlayerViewModel viewModel;
    private final boolean tv;
    private final EpisodeAdapter episodes;
    private final Observer<PlayerState> observer = this::render;
    private String currentUrl = "";
    private boolean closed, pageFailed;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private View fullscreenView;
    private boolean expandedVideo;
    private final ViewGroup.LayoutParams videoLayoutParams;

    public PlayerScreen(ComponentActivity activity, String id, boolean tv, String title, String overview) {
        this.activity = activity;
        this.tv = tv;
        String displayTitle = title == null ? activity.getString(R.string.app_name) : title;
        String displayOverview = overview == null ? "" : overview;
        binding = ScreenPlayerBinding.inflate(activity.getLayoutInflater());
        videoLayoutParams = binding.videoStage.getLayoutParams();
        viewModel = new ViewModelProvider(activity).get(PlayerViewModel.class);
        episodes = new EpisodeAdapter(tv, displayTitle, displayOverview, new EpisodeAdapter.Actions() {
            @Override
            public void selectEpisode(int number) {
                viewModel.episode(number);
            }

            @Override
            public void selectSeason(int number) {
                viewModel.season(number);
            }

            @Override
            public void selectSource(String source) {
                viewModel.source(source);
            }

            @Override
            public void retry() {
                viewModel.retry();
            }

            @Override
            public void move(int direction) {
                PlayerScreen.this.move(direction);
            }
        });
        binding.episodeList.setLayoutManager(new LinearLayoutManager(activity));
        binding.episodeList.setAdapter(episodes);
        binding.episodeList.setItemAnimator(null);
        binding.title.setText(displayTitle);
        binding.closeButton.setOnClickListener(view -> activity.finish());
        binding.reloadButton.setOnClickListener(view -> reload());
        binding.expandButton.setOnClickListener(view -> expandVideo());
        binding.exitFullscreenButton.setOnClickListener(view -> exitFullscreen());
        binding.getRoot().addOnLayoutChangeListener((view, left, top, right, bottom,
                                                     oldLeft, oldTop, oldRight, oldBottom) -> resizeVideo());
        binding.content.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                   oldLeft, oldTop, oldRight, oldBottom) -> resizeVideo());
        configureWebView();
        viewModel.initialize(id, tv);
        viewModel.state().observe(activity, observer);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = binding.webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true);
        binding.webView.setBackgroundColor(android.graphics.Color.BLACK);
        // No JavaScript-to-native bridge, file chooser, permission grants, or popup windows.
        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // WebView invokes this on a worker thread. Consult immutable rules only;
                // no view access, network lookups, or shared activity state here.
                if (!isAdRequest(request.getUrl())) {
                    return null;
                }
                return new WebResourceResponse("text/plain", "UTF-8", 204, "No Content",
                    Collections.singletonMap("Cache-Control", "no-store"),
                    new ByteArrayInputStream(new byte[0]));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (isAdRequest(request.getUrl())) {
                    return true;
                }
                if (!"https".equalsIgnoreCase(request.getUrl().getScheme())) {
                    return true;
                }
                if (!request.isForMainFrame()) {
                    return false;
                }
                String host = Uri.parse(currentUrl).getHost();
                String next = request.getUrl().getHost();
                return host == null || next == null || !(next.equals(host) || next.endsWith("." + host));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!currentUrl.isEmpty() && !"about:blank".equals(url)) {
                    binding.webProgress.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                binding.webProgress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && request.getUrl().toString().equals(currentUrl)) {
                    showPageError();
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && request.getUrl().toString().equals(currentUrl)
                    && response.getStatusCode() >= 400) {
                    showPageError();
                }
            }
        });
        binding.webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                          android.os.Message resultMsg) {
                // Refuse popups even when they follow a user tap on the player.
                return false;
            }

            @Override
            public void onProgressChanged(WebView view, int progress) {
                binding.webProgress.setProgress(progress);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenCallback != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                if (expandedVideo) {
                    exitFullscreen();
                }
                fullscreenCallback = callback;
                fullscreenView = view;
                binding.fullscreenHost.addView(view, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                showFullscreen();
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });
    }

    private static boolean isAdRequest(Uri uri) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        // Check whole DNS labels: a listed domain and its subdomains match, but
        // a similar name or a domain appearing only in a path/query never does.
        while (!host.isEmpty()) {
            if (AD_HOSTS.contains(host)) {
                return true;
            }
            int dot = host.indexOf('.');
            if (dot < 0) {
                break;
            }
            host = host.substring(dot + 1);
        }
        return false;
    }

    private void render(PlayerState state) {
        episodes.submit(state);
        Episode selected = state.selected();
        String guideStatus = EpisodeAdapter.guideStatus(activity, state);
        boolean playable = !tv || !state.loading && state.error == null && selected != null;
        if (playable) {
            String url = PlayerSources.url(state.source, state.playbackId, tv, state.season, state.episode);
            if (!url.equals(currentUrl)) {
                load(url);
            } else if (!pageFailed) {
                binding.videoStatus.setVisibility(View.GONE);
            }
        } else {
            if (!currentUrl.isEmpty()) {
                currentUrl = "";
                binding.webView.stopLoading();
                binding.webView.loadUrl("about:blank");
            }
            binding.videoStatus.setText(guideStatus);
            binding.videoStatus.setVisibility(View.VISIBLE);
            binding.webProgress.setVisibility(View.GONE);
        }
        binding.reloadButton.setEnabled(playable);
        binding.reloadButton.setAlpha(playable ? 1f : 0.35f);
        binding.expandButton.setEnabled(playable);
        binding.expandButton.setAlpha(playable ? 1f : 0.35f);
    }

    private void move(int direction) {
        PlayerState state = viewModel.state().getValue();
        Episode episode = state == null ? null : EpisodeAdapter.neighbor(state, direction);
        if (episode != null) {
            viewModel.episode(episode.number);
        }
    }

    private void load(String url) {
        currentUrl = url;
        pageFailed = false;
        binding.videoStatus.setVisibility(View.GONE);
        binding.webProgress.setVisibility(View.VISIBLE);
        binding.webView.loadUrl(url);
    }

    private void reload() {
        if (!currentUrl.isEmpty()) {
            load(currentUrl);
        }
    }

    private void showPageError() {
        pageFailed = true;
        binding.webProgress.setVisibility(View.GONE);
        binding.videoStatus.setText(R.string.playback_error);
        binding.videoStatus.setVisibility(View.VISIBLE);
    }

    private void expandVideo() {
        if (expandedVideo || fullscreenCallback != null || currentUrl.isEmpty()) {
            return;
        }
        expandedVideo = true;
        binding.content.removeView(binding.videoStage);
        binding.fullscreenHost.addView(binding.videoStage, 0,
            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        showFullscreen();
    }

    private void showFullscreen() {
        binding.fullscreenHost.setVisibility(View.VISIBLE);
        binding.content.setVisibility(View.GONE);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), binding.getRoot());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void resizeVideo() {
        if (expandedVideo || fullscreenCallback != null || closed) {
            return;
        }
        int availableHeight = binding.getRoot().getHeight() - binding.getRoot().getPaddingTop()
            - binding.getRoot().getPaddingBottom();
        int height = Math.min(binding.content.getWidth() * 9 / 16, (int) (availableHeight * 0.40f));
        if (height > 0 && binding.videoStage.getLayoutParams().height != height) {
            ViewGroup.LayoutParams params = binding.videoStage.getLayoutParams();
            params.height = height;
            binding.videoStage.setLayoutParams(params);
        }
    }

    public void configurationChanged() {
        // Keep the existing WebView and its playback session when rotating/resizing.
        binding.webView.invalidate();
        binding.getRoot().requestLayout();
        binding.getRoot().post(this::resizeVideo);
    }

    public boolean exitFullscreen() {
        if (fullscreenCallback == null && !expandedVideo) {
            return false;
        }
        WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        fullscreenCallback = null;
        if (fullscreenView != null) {
            binding.fullscreenHost.removeView(fullscreenView);
            fullscreenView = null;
        }
        if (expandedVideo) {
            binding.fullscreenHost.removeView(binding.videoStage);
            expandedVideo = false;
            binding.content.addView(binding.videoStage, 1, videoLayoutParams);
        }
        binding.fullscreenHost.setVisibility(View.GONE);
        binding.content.setVisibility(View.VISIBLE);
        WindowCompat.getInsetsController(activity.getWindow(), binding.getRoot()).show(WindowInsetsCompat.Type.systemBars());
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (callback != null) {
            callback.onCustomViewHidden();
        }
        binding.getRoot().post(this::resizeVideo);
        return true;
    }

    public void pause() {
        binding.webView.onPause();
    }

    public void resume() {
        if (!closed) {
            binding.webView.onResume();
        }
    }

    @Override
    public View root() {
        return binding.getRoot();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        viewModel.state().removeObserver(observer);
        exitFullscreen();
        binding.episodeList.setAdapter(null);
        binding.webView.stopLoading();
        binding.webView.setWebChromeClient(null);
        binding.webView.setWebViewClient(new WebViewClient());
        binding.videoStage.removeView(binding.webView);
        binding.webView.removeAllViews();
        binding.webView.destroy();
    }
}
