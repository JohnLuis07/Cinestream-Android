package com.cinestream.android.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlayerState {
    public final String playbackId, source, error;
    public final int season, episode;
    public final boolean loading;
    public final ShowDetails show;
    public final List<Episode> episodes;

    public PlayerState(String playbackId, String source, int season, int episode, boolean loading,
                       ShowDetails show, List<Episode> episodes, String error) {
        this.playbackId = playbackId;
        this.source = source;
        this.season = season;
        this.episode = episode;
        this.loading = loading;
        this.show = show;
        this.episodes = Collections.unmodifiableList(new ArrayList<>(episodes));
        this.error = error;
    }

    public Episode selected() {
        for (Episode value : episodes) {
            if (value.number == episode) {
                return value;
            }
        }
        return null;
    }
}
