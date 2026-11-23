package com.cinestream.android.viewmodel;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.cinestream.android.model.CatalogState;
import com.cinestream.android.service.MediaRepository;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class CatalogViewModel extends ViewModel {
    private final SavedStateHandle saved;
    private final MutableLiveData<CatalogState> state = new MutableLiveData<>();
    private final MediaRepository repository = new MediaRepository();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private Future<?> pending;
    private int generation;

    public CatalogViewModel(SavedStateHandle saved) {
        this.saved = saved;
        reload();
    }

    public LiveData<CatalogState> state() {
        return state;
    }

    public String draft() {
        String value = saved.get("draft");
        return value == null ? query() : value;
    }

    public void draft(String value) {
        saved.set("draft", value);
    }

    private String query() {
        String value = saved.get("query");
        return value == null ? "" : value;
    }

    private String filter() {
        String value = saved.get("filter");
        return value == null ? "all" : value;
    }

    public void search(String query) {
        saved.set("query", query.trim());
        saved.set("draft", query.trim());
        reload();
    }

    public void filter(String filter) {
        if (!"all".equals(filter) && !"movie".equals(filter) && !"tv".equals(filter)) {
            return;
        }
        if (filter.equals(filter())) {
            return;
        }
        saved.set("filter", filter);
        reload();
    }

    public void reload() {
        final int token = ++generation;
        if (pending != null) {
            pending.cancel(true);
        }
        String filter = filter(), query = query();
        state.setValue(new CatalogState(filter, query, true, Collections.emptyList(), "", null));
        pending = executor.submit(() -> {
            CatalogState result;
            try {
                result = repository.catalog(filter, query);
            } catch (Exception error) {
                result = new CatalogState(filter, query, false, Collections.emptyList(), "",
                    error.getMessage() == null ? "Unable to load titles. Check your connection and retry." : error.getMessage());
            }
            CatalogState ready = result;
            main.post(() -> {
                if (generation == token) {
                    state.setValue(ready);
                }
            });
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
