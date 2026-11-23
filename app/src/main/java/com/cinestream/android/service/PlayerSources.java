package com.cinestream.android.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PlayerSources {
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
        "Vidsrc.to", "VidLink", "2Embed", "Vsrc", "SuperEmbed", "Embed.su"));

    public static boolean supports(String source, String id) {
        return ALL.contains(source) && (!"VidLink".equals(source) || id.matches("\\d+"));
    }

    public static String url(String source, String id, boolean tv, int season, int episode) {
        if (!id.matches("(?:\\d+|tt\\d+)")) {
            throw new IllegalArgumentException("Invalid title identifier.");
        }
        if (!supports(source, id)) {
            throw new IllegalArgumentException("This source requires a TMDB ID.");
        }
        if (tv && (season < 1 || episode < 1)) {
            throw new IllegalArgumentException("Select an aired episode first.");
        }
        String kind = tv ? "tv" : "movie";
        String suffix = tv ? "/" + season + "/" + episode : "";
        switch (source) {
            case "VidLink":
                return "https://vidlink.pro/" + kind + "/" + id + suffix;
            case "Vsrc":
                return "https://vidsrc.xyz/embed/" + kind + "?" + (id.startsWith("tt") ? "imdb=" : "tmdb=") + id + (tv ? "&season=" + season + "&episode=" + episode : "");
            case "2Embed":
                return tv ? "https://www.2embed.cc/embedtv/" + id + "&s=" + season + "&e=" + episode : "https://www.2embed.cc/embed/" + id;
            case "SuperEmbed":
                return "https://multiembed.mov/?video_id=" + id + (id.startsWith("tt") ? "" : "&tmdb=1") + (tv ? "&s=" + season + "&e=" + episode : "");
            case "Embed.su":
                return "https://embed.su/embed/" + kind + "/" + id + suffix;
            default:
                return "https://vidsrc.to/embed/" + kind + "/" + id + suffix;
        }
    }
}
