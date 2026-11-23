package com.cinestream.android.service;

import com.cinestream.android.BuildConfig;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Blocking HTTPS requests. Call only from a worker executor. Never log credential URLs.
 */
public final class ApiClient {
    private static final class ApiException extends IOException {
        ApiException(String message) {
            super(message);
        }
    }

    public static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public boolean hasTmdb() {
        return !BuildConfig.TMDB_API_KEY.isEmpty() || !BuildConfig.TMDB_ACCESS_TOKEN.isEmpty();
    }

    public JSONObject tmdb(String path, String parameters) throws Exception {
        if (!hasTmdb()) {
            throw new IOException("Movie listings need TMDB_API_KEY or TMDB_ACCESS_TOKEN in local.properties. Rebuild after configuring it.");
        }
        String query = "language=en-US" + (parameters.isEmpty() ? "" : "&" + parameters);
        if (BuildConfig.TMDB_ACCESS_TOKEN.isEmpty()) {
            query += "&api_key=" + encode(BuildConfig.TMDB_API_KEY);
        }
        return (JSONObject) get("https://api.themoviedb.org/3" + path + "?" + query,
            BuildConfig.TMDB_ACCESS_TOKEN, "TMDB");
    }

    public Object maze(String path) throws Exception {
        return get("https://api.tvmaze.com" + path, "", "TVmaze");
    }

    public Object get(String address, String bearer, String provider) throws Exception {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("Request cancelled.");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        if (!bearer.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearer);
        }
        try {
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) {
                throw new ApiException(provider + " rejected the configured credentials. Update local.properties and rebuild.");
            }
            if (status != 200) {
                throw new ApiException(provider + " is unavailable (HTTP " + status + "). Please retry.");
            }
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new ApiException("Request cancelled.");
                    }
                    if (output.size() + count > 8 * 1024 * 1024) {
                        throw new ApiException(provider + " returned too much data.");
                    }
                    output.write(buffer, 0, count);
                }
                return new JSONTokener(output.toString(StandardCharsets.UTF_8.name())).nextValue();
            }
        } catch (java.net.SocketTimeoutException timeout) {
            throw new IOException(provider + " timed out. Check your connection and retry.");
        } catch (ApiException error) {
            throw error;
        } catch (IOException error) {
            throw new IOException(provider + " could not connect. Check your connection and retry.");
        } catch (org.json.JSONException error) {
            throw new IOException(provider + " returned an unreadable response. Please retry.");
        } finally {
            connection.disconnect();
        }
    }
}
