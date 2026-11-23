package com.cinestream.android.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CatalogState {
    public final String filter, query, notice, error;
    public final boolean loading;
    public final List<MediaItem> items;

    public CatalogState(String filter, String query, boolean loading, List<MediaItem> items,
                        String notice, String error) {
        this.filter = filter;
        this.query = query;
        this.loading = loading;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.notice = notice;
        this.error = error;
    }
}
