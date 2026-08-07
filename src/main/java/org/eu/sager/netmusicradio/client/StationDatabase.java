package org.eu.sager.netmusicradio.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StationDatabase {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation REGISTRY = new ResourceLocation("netmusicradio", "netmusicradio/registry.json");

    private static volatile List<RadioBrowserClient.Station> stations = null;
    private static volatile Map<String, List<Integer>> nameIndex = null;
    private static volatile Map<String, List<Integer>> countryIndex = null;
    private static volatile Map<String, List<Integer>> tagIndex = null;
    private static volatile String loadError = null;
    private static final AtomicBoolean loading = new AtomicBoolean(false);

    private StationDatabase() {
    }

    public static void ensureLoadedAsync() {
        if (stations != null || loading.getAndSet(true)) return;
        new Thread(() -> {
            try {
                doLoad();
            } finally {
                loading.set(false);
            }
        }, "NetMusic-StationDB-Loader").start();
    }

    private static synchronized void doLoad() {
        if (stations != null) return;
        try {
            var manager = Minecraft.getInstance().getResourceManager();
            if (manager == null) {
                loadError = "Resource manager not ready";
                stations = Collections.emptyList();
                nameIndex = Collections.emptyMap();
                countryIndex = Collections.emptyMap();
                tagIndex = Collections.emptyMap();
                return;
            }
            var opt = manager.getResource(REGISTRY);
            if (opt.isEmpty()) {
                loadError = "registry.json not found";
                stations = Collections.emptyList();
                nameIndex = Collections.emptyMap();
                countryIndex = Collections.emptyMap();
                tagIndex = Collections.emptyMap();
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(opt.get().open(), StandardCharsets.UTF_8)) {
                List<Map<String, String>> raw = GSON.fromJson(reader,
                        new TypeToken<List<Map<String, String>>>(){}.getType());
                if (raw == null) raw = Collections.emptyList();

                List<RadioBrowserClient.Station> list = new ArrayList<>(raw.size());
                Map<String, List<Integer>> nameIdx = new ConcurrentHashMap<>();
                Map<String, List<Integer>> ctryIdx = new ConcurrentHashMap<>();
                Map<String, List<Integer>> tagIdx = new ConcurrentHashMap<>();

                for (int i = 0; i < raw.size(); i++) {
                    Map<String, String> m = raw.get(i);
                    if (m == null) continue;
                    String name = get(m, "name");
                    String url = get(m, "stream_url");
                    if (url == null || url.isBlank()) continue;
                    String country = get(m, "country");
                    String logo = get(m, "logo_url");
                    String tags = get(m, "tags");
                    String description = get(m, "description");

                    list.add(new RadioBrowserClient.Station(name, url, country, logo, description));

                    addToIndex(nameIdx, normalize(name), i);
                    addToIndex(ctryIdx, normalize(country), i);
                    if (tags != null && !tags.isBlank()) {
                        for (String t : tags.split("\\s+")) {
                            if (!t.isBlank()) addToIndex(tagIdx, normalize(t), i);
                        }
                    }
                }

                stations = Collections.unmodifiableList(list);
                nameIndex = nameIdx;
                countryIndex = ctryIdx;
                tagIndex = tagIdx;
                loadError = null;
            }
        } catch (Exception e) {
            loadError = e.getMessage();
            stations = Collections.emptyList();
            nameIndex = Collections.emptyMap();
            countryIndex = Collections.emptyMap();
            tagIndex = Collections.emptyMap();
        }
    }

    public static List<RadioBrowserClient.Station> search(String nameQuery, String countryQuery, int limit) {
        if (stations == null) {
            ensureLoadedAsync();
            return Collections.emptyList();
        }
        if (stations.isEmpty()) return Collections.emptyList();

        if ((nameQuery == null || nameQuery.isBlank()) && (countryQuery == null || countryQuery.isBlank())) {
            return stations.subList(0, Math.min(limit, stations.size()));
        }

        Set<Integer> resultSet = null;

        if (nameQuery != null && !nameQuery.isBlank()) {
            String nq = normalize(nameQuery);
            Set<Integer> nameMatches = new HashSet<>();
            for (Map.Entry<String, List<Integer>> entry : nameIndex.entrySet()) {
                if (entry.getKey().contains(nq)) {
                    nameMatches.addAll(entry.getValue());
                }
            }
            Set<Integer> tagMatches = new HashSet<>();
            for (Map.Entry<String, List<Integer>> entry : tagIndex.entrySet()) {
                if (entry.getKey().contains(nq)) {
                    tagMatches.addAll(entry.getValue());
                }
            }
            Set<Integer> merged = new HashSet<>(nameMatches.size() + tagMatches.size());
            merged.addAll(nameMatches);
            merged.addAll(tagMatches);
            resultSet = merged;
        }

        if (countryQuery != null && !countryQuery.isBlank()) {
            String cq = normalize(countryQuery);
            Set<Integer> ctryMatches = new HashSet<>();
            for (Map.Entry<String, List<Integer>> entry : countryIndex.entrySet()) {
                if (entry.getKey().contains(cq)) {
                    ctryMatches.addAll(entry.getValue());
                }
            }
            if (resultSet == null) {
                resultSet = ctryMatches;
            } else {
                Set<Integer> intersection = new HashSet<>(resultSet.size());
                for (Integer idx : resultSet) {
                    if (ctryMatches.contains(idx)) intersection.add(idx);
                }
                resultSet = intersection;
            }
        }

        if (resultSet == null || resultSet.isEmpty()) {
            return Collections.emptyList();
        }

        List<RadioBrowserClient.Station> result = new ArrayList<>(resultSet.size());
        for (Integer idx : resultSet) {
            if (idx >= 0 && idx < stations.size()) {
                result.add(stations.get(idx));
            }
        }

        result.sort(Comparator.comparing(s -> s.name, String.CASE_INSENSITIVE_ORDER));

        if (result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    public static List<String> getCountries() {
        if (countryIndex == null) return Collections.emptyList();
        List<String> countries = new ArrayList<>(countryIndex.keySet());
        countries.sort(String.CASE_INSENSITIVE_ORDER);
        return countries;
    }

    public static int size() {
        return stations == null ? 0 : stations.size();
    }

    public static boolean isLoaded() {
        return stations != null;
    }

    public static String getLoadError() {
        return loadError;
    }

    private static String get(Map<String, String> m, String key) {
        String v = m.get(key);
        return v == null ? "" : v;
    }

    private static void addToIndex(Map<String, List<Integer>> idx, String key, int pos) {
        if (key == null || key.isBlank()) return;
        idx.computeIfAbsent(key, k -> new ArrayList<>()).add(pos);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).trim();
    }
}