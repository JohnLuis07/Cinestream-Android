package com.cinestream.android.service;

import android.text.Html;

import com.cinestream.android.BuildConfig;
import com.cinestream.android.model.CatalogState;
import com.cinestream.android.model.Episode;
import com.cinestream.android.model.MediaItem;
import com.cinestream.android.model.ShowDetails;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Catalog and exact-ID episode lookups, matching the web app's provider model.
 */
public final class MediaRepository {
    private final ApiClient api = new ApiClient();

    public CatalogState catalog(String filter, String query) throws Exception {
        Map<String, MediaItem> items = new LinkedHashMap<>();
        List<String> notices = new ArrayList<>();
        String tmdbFailure = null;
        boolean tmdbSucceeded = false;
        boolean alternativeSucceeded = false;
        try {
            String path = query.isEmpty() ? "/trending/" + filter + "/day"
                : "/search/" + ("all".equals(filter) ? "multi" : filter);
            JSONArray raw = api.tmdb(path, query.isEmpty() ? "" : "query=" + ApiClient.encode(query)).optJSONArray("results");
            if (raw != null) {
                for (int i = 0; i < raw.length(); i++) {
                    JSONObject entry = raw.getJSONObject(i);
                    String type = entry.optString("media_type", filter);
                    if (!"movie".equals(type) && !"tv".equals(type)) {
                        continue;
                    }
                    if (!"all".equals(filter) && !filter.equals(type)) {
                        continue;
                    }
                    put(items, fromTmdb(entry, type));
                }
            }
            tmdbSucceeded = true;
        } catch (Exception error) {
            tmdbFailure = error.getMessage();
        }

        // OMDb supplements title search; IMDb IDs remain distinct from TMDB IDs.
        if (!query.isEmpty() && !BuildConfig.OMDB_API_KEY.isEmpty()) {
            try {
                String kind = "all".equals(filter) ? "" : "&type=" + ("tv".equals(filter) ? "series" : "movie");
                JSONObject result = (JSONObject) api.get("https://www.omdbapi.com/?apikey="
                    + ApiClient.encode(BuildConfig.OMDB_API_KEY) + "&s=" + ApiClient.encode(query) + kind, "", "OMDb");
                if (!"True".equals(result.optString("Response"))) {
                    String error = result.optString("Error");
                    if (!"Movie not found!".equals(error)) {
                        throw new IOException("OMDb search is unavailable. Check your API key and usage limit.");
                    }
                }
                JSONArray raw = result.optJSONArray("Search");
                if (raw != null) {
                    for (int i = 0; i < raw.length(); i++) {
                        JSONObject entry = raw.getJSONObject(i);
                        String kindName = entry.optString("Type");
                        if (!"movie".equals(kindName) && !"series".equals(kindName)) {
                            continue;
                        }
                        String type = "series".equals(kindName) ? "tv" : "movie";
                        if (!"all".equals(filter) && !filter.equals(type)) {
                            continue;
                        }
                        String year = entry.optString("Year", "N/A");
                        put(items, new MediaItem(entry.optString("imdbID"), entry.optString("Title"), type,
                            "", cleanUrl(entry.optString("Poster")), "", year, "N/A"));
                    }
                }
                alternativeSucceeded = true;
            } catch (Exception error) {
                notices.add("OMDb search is unavailable. Check your API key and connection.");
            }
        }

        if (!"movie".equals(filter) && (!tmdbSucceeded || items.isEmpty())) {
            try {
                JSONArray raw = (JSONArray) api.maze(query.isEmpty() ? "/schedule" : "/search/shows?q=" + ApiClient.encode(query));
                for (int i = 0; i < raw.length(); i++) {
                    JSONObject show = raw.getJSONObject(i).optJSONObject("show");
                    if (show != null) {
                        put(items, fromMaze(show));
                    }
                }
                notices.add(query.isEmpty() ? "TV schedule from TVmaze." : "TV search results from TVmaze.");
                alternativeSucceeded = true;
            } catch (Exception error) {
                if (!tmdbSucceeded && !alternativeSucceeded && items.isEmpty()) {
                    throw error;
                }
                notices.add("TVmaze is temporarily unavailable.");
            }
        }
        if (!tmdbSucceeded) {
            if ("movie".equals(filter) && !alternativeSucceeded) {
                throw new IOException(tmdbFailure == null ? "Movie listings are unavailable." : tmdbFailure);
            }
            notices.add(api.hasTmdb() ? "TMDB is unavailable. Check your credentials or connection."
                : "Configure TMDB in local.properties to include movie charts.");
        }
        List<MediaItem> result = new ArrayList<>(items.values());
        if (!query.isEmpty()) {
            result.sort(Comparator.comparingInt(item ->
                item.title.toLowerCase(Locale.ROOT).equals(query.toLowerCase(Locale.ROOT)) ? 0 : 1));
        }
        if (result.size() > 60) {
            result = new ArrayList<>(result.subList(0, 60));
        }
        return new CatalogState(filter, query, false, result, join(notices), null);
    }

    public ShowDetails show(String id) throws Exception {
        String imdb = id.startsWith("tt") ? id : "";
        if (api.hasTmdb()) {
            try {
                String tmdb = id;
                if (!imdb.isEmpty()) {
                    JSONArray matches = api.tmdb("/find/" + id, "external_source=imdb_id").optJSONArray("tv_results");
                    if (matches == null || matches.length() == 0) {
                        throw new IOException("Show not found.");
                    }
                    tmdb = String.valueOf(matches.getJSONObject(0).getLong("id"));
                }
                JSONObject show = api.tmdb("/tv/" + tmdb, "append_to_response=external_ids");
                JSONObject external = show.optJSONObject("external_ids");
                if (external != null && !external.isNull("imdb_id")) {
                    imdb = external.optString("imdb_id", imdb);
                }
                List<Integer> seasons = new ArrayList<>();
                JSONArray raw = show.optJSONArray("seasons");
                if (raw != null) {
                    for (int i = 0; i < raw.length(); i++) {
                        JSONObject season = raw.getJSONObject(i);
                        if (season.optInt("season_number") > 0 && season.optInt("episode_count") > 0) {
                            seasons.add(season.getInt("season_number"));
                        }
                    }
                }
                Collections.sort(seasons);
                if (!seasons.isEmpty()) {
                    return new ShowDetails(tmdb, "TMDB", seasons, Collections.emptyList());
                }
            } catch (Exception ignored) { /* Fall back only through an exact IMDb identifier. */ }
        }
        if (imdb.isEmpty()) {
            throw new IOException("Episode guide unavailable. Check your TMDB credentials and connection, then retry.");
        }
        JSONObject show = (JSONObject) api.maze("/lookup/shows?imdb=" + ApiClient.encode(imdb));
        JSONArray raw = (JSONArray) api.maze("/shows/" + show.getInt("id") + "/episodes");
        List<Episode> episodes = parseEpisodes(raw, false);
        TreeSet<Integer> seasons = new TreeSet<>();
        for (Episode episode : episodes) {
            seasons.add(episode.season);
        }
        if (seasons.isEmpty()) {
            throw new IOException("No episodes are listed for this show yet.");
        }
        return new ShowDetails(imdb, "TVmaze", new ArrayList<>(seasons), episodes);
    }

    public List<Episode> episodes(ShowDetails show, int season) throws Exception {
        List<Episode> result = new ArrayList<>();
        if ("TVmaze".equals(show.source)) {
            for (Episode episode : show.episodes) {
                if (episode.season == season) {
                    result.add(episode);
                }
            }
        } else {
            result = parseEpisodes(api.tmdb("/tv/" + show.id + "/season/" + season, "").optJSONArray("episodes"), true);
        }
        result.sort(Comparator.comparingInt(episode -> episode.number));
        return result;
    }

    private static List<Episode> parseEpisodes(JSONArray raw, boolean tmdb) throws Exception {
        List<Episode> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (int i = 0; i < raw.length(); i++) {
            JSONObject entry = raw.getJSONObject(i);
            int number = entry.optInt(tmdb ? "episode_number" : "number");
            int season = entry.optInt(tmdb ? "season_number" : "season");
            if (number < 1 || season < 1) {
                continue;
            }
            JSONObject image = entry.optJSONObject("image");
            String imageUrl = tmdb ? tmdbImage(entry, "still_path", "w300") : image == null ? "" : cleanUrl(image.optString("medium"));
            result.add(new Episode(number, season, entry.optString("name", "Episode " + number),
                text(entry, tmdb ? "overview" : "summary"), imageUrl,
                text(entry, tmdb ? "air_date" : "airdate"), entry.optInt("runtime")));
        }
        return result;
    }

    private static MediaItem fromTmdb(JSONObject entry, String type) {
        String date = text(entry, "movie".equals(type) ? "release_date" : "first_air_date");
        return new MediaItem(entry.optString("id"), text(entry, "movie".equals(type) ? "title" : "name"), type,
            text(entry, "overview"), tmdbImage(entry, "poster_path", "w500"), tmdbImage(entry, "backdrop_path", "w780"),
            date.length() >= 4 ? date.substring(0, 4) : "N/A", rating(entry.optDouble("vote_average", Double.NaN)));
    }

    private static MediaItem fromMaze(JSONObject entry) {
        JSONObject external = entry.optJSONObject("externals");
        if (external == null || external.isNull("imdb")) {
            return null;
        }
        JSONObject image = entry.optJSONObject("image");
        JSONObject score = entry.optJSONObject("rating");
        String date = text(entry, "premiered");
        return new MediaItem(external.optString("imdb"), text(entry, "name"), "tv", text(entry, "summary"),
            image == null ? "" : cleanUrl(image.optString("medium")), image == null ? "" : cleanUrl(image.optString("original")),
            date.length() >= 4 ? date.substring(0, 4) : "N/A", rating(score == null ? Double.NaN : score.optDouble("average", Double.NaN)));
    }

    private static void put(Map<String, MediaItem> items, MediaItem item) {
        if (item != null && item.id.matches("(?:\\d+|tt\\d+)") && !item.title.isEmpty()) {
            items.putIfAbsent(item.type + ":" + item.id, item);
        }
    }

    private static String text(JSONObject entry, String key) {
        return entry.isNull(key) ? "" : Html.fromHtml(entry.optString(key), Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    private static String cleanUrl(String url) {
        return url.startsWith("https://") ? url : "";
    }

    private static String tmdbImage(JSONObject entry, String key, String size) {
        String path = text(entry, key);
        return path.startsWith("/") ? "https://image.tmdb.org/t/p/" + size + path : "";
    }

    private static String rating(double value) {
        return Double.isNaN(value) ? "N/A" : String.format(Locale.US, "%.1f", value);
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(value);
        }
        return result.toString();
    }
}
