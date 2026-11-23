package com.cinestream.android.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShowDetails {
    public final String id, source;
    public final List<Integer> seasons;
    public final List<Episode> episodes;

    public ShowDetails(String id, String source, List<Integer> seasons, List<Episode> episodes) {
        this.id = id;
        this.source = source;
        this.seasons = Collections.unmodifiableList(new ArrayList<>(seasons));
        this.episodes = Collections.unmodifiableList(new ArrayList<>(episodes));
    }
}
