package org.eu.sager.netmusicradio.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class FavoritesManager {
    private static final File FAVORITES_FILE = new File("config/netmusicradio/favorites.json");
    private static final Gson GSON = new Gson();
    private static final List<RadioBrowserClient.Station> favorites = new ArrayList<>();
    private static final Set<String> favoriteUrls = new HashSet<>();

    public static void load() {
        favorites.clear();
        favoriteUrls.clear();
        if (FAVORITES_FILE.exists()) {
            try (FileReader reader = new FileReader(FAVORITES_FILE)) {
                List<Map<String, String>> raw = GSON.fromJson(reader,
                        new TypeToken<List<Map<String, String>>>() {}.getType());
                if (raw != null) {
                    for (Map<String, String> m : raw) {
                        String name = m.getOrDefault("name", "");
                        String url = m.getOrDefault("url", "");
                        String country = m.getOrDefault("country", "");
                        String logoUrl = m.getOrDefault("logoUrl", "");
                        String description = m.getOrDefault("description", "");
                        if (url.isBlank()) continue;
                        favorites.add(new RadioBrowserClient.Station(name, url, country, logoUrl, description));
                        favoriteUrls.add(url.trim().toLowerCase());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void save() {
        FAVORITES_FILE.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(FAVORITES_FILE)) {
            List<Map<String, String>> raw = new ArrayList<>();
            for (RadioBrowserClient.Station s : favorites) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("name", s.name);
                m.put("url", s.url);
                m.put("country", s.country);
                m.put("logoUrl", s.logoUrl);
                m.put("description", s.description);
                raw.add(m);
            }
            GSON.toJson(raw, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addFavorite(RadioBrowserClient.Station station) {
        String key = station.url.trim().toLowerCase();
        if (!favoriteUrls.contains(key)) {
            favorites.add(station);
            favoriteUrls.add(key);
            save();
        }
    }

    public static void removeFavorite(RadioBrowserClient.Station station) {
        String key = station.url.trim().toLowerCase();
        favoriteUrls.remove(key);
        favorites.removeIf(s -> s.url.trim().equalsIgnoreCase(key));
        save();
    }

    public static boolean isFavorite(RadioBrowserClient.Station station) {
        if (station == null || station.url == null) return false;
        return favoriteUrls.contains(station.url.trim().toLowerCase());
    }

    public static List<RadioBrowserClient.Station> getFavorites() {
        return Collections.unmodifiableList(favorites);
    }
}