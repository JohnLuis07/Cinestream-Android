package com.cinestream.android.model;

public final class MediaItem {
    public final String id, title, type, overview, poster, backdrop, year, rating;

    public MediaItem(String id, String title, String type, String overview, String poster,
                     String backdrop, String year, String rating) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.overview = overview;
        this.poster = poster;
        this.backdrop = backdrop;
        this.year = year;
        this.rating = rating;
    }

    public boolean isTv() {
        return "tv".equals(type);
    }
}
