package com.github.tartaricacid.netmusicradio.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Minimal client for radio-browser API. Performs simple search and returns deduplicated stations.
 */
public final class RadioBrowserClient {
    private static final Gson GSON = new Gson();
    private static final String BASE = "https://de1.api.radio-browser.info/json/stations/search";

    private RadioBrowserClient() {
    }

    public static class Station {
        public final String name;
        public final String url;
        public final String country;
        public final String logoUrl;

        public Station(String name, String url, String country) {
            this(name, url, country, "");
        }

        public Station(String name, String url, String country, String logoUrl) {
            this.name = name == null ? "" : name;
            this.url = url == null ? "" : url;
            this.country = country == null ? "" : country;
            this.logoUrl = logoUrl == null ? "" : logoUrl;
        }
    }

    public static List<Station> search(String name, String country, int limit) {
        try {
            StringBuilder sb = new StringBuilder(BASE);
            sb.append("?limit=").append(limit);
            if (name != null && !name.isBlank()) sb.append("&name=").append(URLEncoder.encode(name, StandardCharsets.UTF_8));
            if (country != null && !country.isBlank()) sb.append("&country=").append(URLEncoder.encode(country, StandardCharsets.UTF_8));

            URL url = new URL(sb.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "NetMusicRadioAddon/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                JsonArray arr = GSON.fromJson(reader, JsonArray.class);
                List<Station> list = new ArrayList<>();
                HashSet<String> seen = new HashSet<>();
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();
                    String sname = o.has("name") ? o.get("name").getAsString() : "";
                    String surl = o.has("url_resolved") ? o.get("url_resolved").getAsString() : (o.has("url") ? o.get("url").getAsString() : "");
                    String scountry = o.has("country") ? o.get("country").getAsString() : "";
                    String slogo = o.has("logo_url") ? o.get("logo_url").getAsString() : "";
                    if (surl == null || surl.isBlank()) continue;
                    String key = surl.trim().toLowerCase();
                    if (seen.contains(key)) continue;
                    seen.add(key);
                    list.add(new Station(sname, surl, scountry, slogo));
                }
                return list;
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}