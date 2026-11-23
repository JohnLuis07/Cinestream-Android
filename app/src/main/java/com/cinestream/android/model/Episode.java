package com.cinestream.android.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class Episode {
    public final int number, season, runtime;
    public final String name, overview, image, airDate;

    public Episode(int number, int season, String name, String overview, String image,
                   String airDate, int runtime) {
        this.number = number;
        this.season = season;
        this.name = name;
        this.overview = overview;
        this.image = image;
        this.airDate = airDate;
        this.runtime = runtime;
    }

    public boolean hasAired() {
        if (!airDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return false;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return airDate.compareTo(format.format(new Date())) <= 0;
    }
}
