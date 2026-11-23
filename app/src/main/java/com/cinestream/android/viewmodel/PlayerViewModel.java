package com.cinestream.android.viewmodel;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.cinestream.android.model.Episode;
import com.cinestream.android.model.PlayerState;
import com.cinestream.android.model.ShowDetails;
import com.cinestream.android.service.MediaRepository;
import com.cinestream.android.service.PlayerSources;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class PlayerViewModel extends ViewModel {
    private final SavedStateHandle saved;
    private final MutableLiveData<PlayerState> state = new MutableLiveData<>();
    private final MediaRepository repository = new MediaRepository();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private String originalId;
    private boolean tv;
    private int generation;
    private Future<?> pending;

    public PlayerViewModel(SavedStateHandle saved) {
        this.saved = saved;
    }

    public LiveData<PlayerState> state() {
        return state;
    }

    public void initialize(String id, boolean tv) {
        if (originalId != null) {
            return;
        }
        this.originalId = id;
        this.tv = tv;
        if (tv) {
            retry();
        } else {
            state.setValue(new PlayerState(id, resolveSource(id), 0, 0, false, null, Collections.emptyList(), null));
        }
    }

    public void retry() {
        if (!tv) {
            return;
        }
        PlayerState current = state.getValue();
        if (current != null && current.show != null) {
            season(current.season);
            return;
        }
        int token = begin();
        state.setValue(new PlayerState(originalId, resolveSource(originalId), 0, 0, true, null, Collections.emptyList(), null));
        pending = executor.submit(() -> {
            try {
                ShowDetails show = repository.show(originalId);
                int requested = integer("season");
                int season = show.seasons.contains(requested) ? requested : show.seasons.get(0);
                List<Episode> episodes = repository.episodes(show, season);
                main.post(() -> {
                    if (generation == token) {
                        ready(show, season, episodes);
                    }
                });
            } catch (Exception error) {
                failed(token, null, 0, error);
            }
        });
    }

    public void season(int season) {
        PlayerState current = state.getValue();
        if (current == null || current.show == null || !current.show.seasons.contains(season)) {
            return;
        }
        if (season != current.season) {
            saved.set("episode", 0);
        }
        saved.set("season", season);
        ShowDetails show = current.show;
        int token = begin();
        state.setValue(new PlayerState(show.id, resolveSource(show.id), season, 0, true, show, Collections.emptyList(), null));
        pending = executor.submit(() -> {
            try {
                List<Episode> episodes = repository.episodes(show, season);
                main.post(() -> {
                    if (generation == token) {
                        ready(show, season, episodes);
                    }
                });
            } catch (Exception error) {
                failed(token, show, season, error);
            }
        });
    }

    private int begin() {
        if (pending != null) {
            pending.cancel(true);
        }
        return ++generation;
    }

    private void ready(ShowDetails show, int season, List<Episode> episodes) {
        int requested = integer("episode"), selected = 0;
        for (Episode episode : episodes) {
            if (!episode.hasAired()) {
                continue;
            }
            if (selected == 0) {
                selected = episode.number;
            }
            if (episode.number == requested) {
                selected = requested;
                break;
            }
        }
        saved.set("season", season);
        saved.set("episode", selected);
        state.setValue(new PlayerState(show.id, resolveSource(show.id), season, selected, false, show, episodes, null));
    }

    public void episode(int number) {
        PlayerState current = state.getValue();
        if (current == null || current.loading) {
            return;
        }
        for (Episode episode : current.episodes) {
            if (episode.number == number && episode.hasAired()) {
                saved.set("episode", number);
                state.setValue(new PlayerState(current.playbackId, current.source, current.season, number,
                    false, current.show, current.episodes, null));
                return;
            }
        }
    }

    public void source(String source) {
        PlayerState current = state.getValue();
        if (current == null || !PlayerSources.supports(source, current.playbackId)) {
            return;
        }
        saved.set("source", source);
        state.setValue(new PlayerState(current.playbackId, source, current.season, current.episode,
            current.loading, current.show, current.episodes, current.error));
    }

    private String resolveSource(String id) {
        String source = saved.get("source");
        return source != null && PlayerSources.supports(source, id) ? source : PlayerSources.ALL.get(0);
    }

    private int integer(String key) {
        Integer value = saved.get(key);
        return value == null ? 0 : value;
    }

    private void failed(int token, ShowDetails show, int season, Exception error) {
        // Do not surface raw URLs or headers from low-level network exceptions.
        String message = error instanceof java.io.IOException && error.getMessage() != null
            && !error.getMessage().contains("https:") ? error.getMessage() : "Episode guide unavailable. Check your connection and retry.";
        main.post(() -> {
            if (generation != token) {
                return;
            }
            String id = show == null ? originalId : show.id;
            state.setValue(new PlayerState(id, resolveSource(id), season, 0, false, show, Collections.emptyList(), message));
        });
    }

    @Override
    protected void onCleared() {
        generation++;
        if (pending != null) {
            pending.cancel(true);
        }
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
