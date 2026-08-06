package com.github.tartaricacid.netmusicradio.client;

import com.github.tartaricacid.netmusicradio.client.RadioBrowserClient.Station;
import com.github.tartaricacid.netmusicradio.client.util.BigMegaphoneUtilProxy;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Search screen implemented in the addon (no dependency on NetMusic internals).
 */
public class RadioSearchScreen extends Screen {
    private static final int PAGE_SIZE = 5;
    private final Screen parentScreen;
    private int leftPos;
    private int topPos;
    private int page = 0;

    private EditBox searchField;
    private EditBox countryField;

    private List<Station> results = new ArrayList<>();
    private boolean showingLocal = false;
    private boolean loading = false;
    private String statusMessage = "";

    public RadioSearchScreen(Screen parent) {
        super(Component.translatable("gui.netmusic.big_megaphone.preset_picker"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - 240) / 2;
        this.topPos = (this.height - 170) / 2;
        // search input
        this.searchField = new EditBox(this.font, this.leftPos, this.topPos + 8, 160, 18, Component.literal("Search"));
        this.searchField.setMaxLength(128);
        this.addRenderableWidget(this.searchField);
        // country input
        this.countryField = new EditBox(this.font, this.leftPos + 162, this.topPos + 8, 76, 18, Component.literal("Country"));
        this.countryField.setMaxLength(64);
        this.addRenderableWidget(this.countryField);

        this.addRenderableWidget(Button.builder(Component.literal("Search"), b -> doSearch())
               .pos(this.leftPos, this.topPos + 30).size(76, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Local"), b -> showLocal())
               .pos(this.leftPos + 82, this.topPos + 30).size(76, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusic.big_megaphone.back"), b -> this.onClose())
                .pos(this.leftPos + 164, this.topPos + 30).size(76, 20).build());

        this.rebuildResultButtons();
    }

    private void doSearch() {
        String q = this.searchField.getValue().trim();
        String country = this.countryField.getValue().trim();
        if (q.isBlank() && country.isBlank()) {
            this.statusMessage = "Enter a station name or country to search.";
            this.results.clear();
            this.page = 0;
            this.rebuildResultButtons();
            return;
        }
        this.showingLocal = false;
        this.page = 0;
        this.loading = true;
        this.statusMessage = "Searching...";
        this.rebuildResultButtons();
        this.results.clear();

        new Thread(() -> {
            List<Station> found = RadioBrowserClient.search(q, country, 50);
            List<Station> filtered = new ArrayList<>();
            for (Station s : found) {
                if (s == null || s.url == null || s.url.isBlank()) continue;
                if (!BigMegaphoneUtilProxy.isValidStreamUrl(s.url)) continue;
                filtered.add(s);
            }
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    this.loading = false;
                    this.results = filtered;
                    if (this.results.isEmpty()) {
                        this.statusMessage = "No radio stations found.";
                    } else {
                        this.statusMessage = "Found " + this.results.size() + " stations.";
                    }
                    this.rebuildResultButtons();
                });
            }
        }, "NetMusic-RadioSearch").start();
    }

    private void showLocal() {
        this.showingLocal = true;
        this.loading = false;
        this.page = 0;
        this.statusMessage = "Showing local presets.";
        List<Station> local = new ArrayList<>();
        try {
            var manager = Minecraft.getInstance().getResourceManager();
            var opt = manager.getResource(new net.minecraft.resources.ResourceLocation("netmusic", "broadcasting_presets.json"));
            if (opt.isPresent()) {
                try (InputStreamReader reader = new InputStreamReader(opt.get().open(), StandardCharsets.UTF_8)) {
                    List<java.util.Map<String, String>> loaded = new Gson().fromJson(reader, new TypeToken<List<java.util.Map<String, String>>>(){}.getType());
                    if (loaded != null) {
                        for (var map : loaded) {
                            if (map == null) continue;
                            String name = map.getOrDefault("name", "");
                            String url = map.getOrDefault("url", "");
                            if (url == null || url.isBlank()) continue;
                            if (!BigMegaphoneUtilProxy.isValidStreamUrl(url)) continue;
                            local.add(new Station(name, url, ""));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            this.statusMessage = "Failed to load local presets.";
        }
        this.results = local;
        this.rebuildResultButtons();
    }

    private void rebuildResultButtons() {
        this.clearWidgets();
        // re-add search fields and top buttons
        this.addRenderableWidget(this.searchField);
        this.addRenderableWidget(this.countryField);
        this.addRenderableWidget(Button.builder(Component.literal("Search"), b -> doSearch())
                .pos(this.leftPos, this.topPos + 30).size(76, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Local"), b -> showLocal())
                .pos(this.leftPos + 82, this.topPos + 30).size(76, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusic.big_megaphone.back"), b -> this.onClose())
                .pos(this.leftPos + 164, this.topPos + 30).size(76, 20).build());

        int contentTop = this.topPos + 56;
        if (this.loading) {
            this.statusMessage = "Searching...";
        }

        if (this.results.isEmpty()) {
        if (this.statusMessage.isBlank()) {
            this.statusMessage = this.loading ? "Searching..." : "No stations found.";
        }
        } else {
        int start = this.page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, this.results.size());
        for (int i = start; i < end; i++) {
            int index = i - start;
            Station station = this.results.get(i);
            Component text = Component.literal((station.name == null || station.name.isBlank()) ? station.url : station.name + " — " + station.country);
            this.addRenderableWidget(Button.builder(text, b -> this.selectStation(station))
                    .pos(this.leftPos, contentTop + index * 22).size(240, 20)
                    .build());
        }
        }

        Button previous = Button.builder(Component.translatable("gui.netmusic.big_megaphone.page.previous"), b -> doPrevious())
                .pos(this.leftPos, this.topPos + 156)
                .size(76, 20)
                .build();
        previous.active = this.page > 0 && !this.loading && !this.results.isEmpty();
        this.addRenderableWidget(previous);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusic.big_megaphone.back"), b -> this.onClose())
                .pos(this.leftPos + 82, this.topPos + 156)
                .size(76, 20)
                .build());

        int maxPage = this.getMaxPage();
        Button next = Button.builder(Component.translatable("gui.netmusic.big_megaphone.page.next"), b -> doNext(maxPage))
                .pos(this.leftPos + 164, this.topPos + 156)
                .size(76, 20)
                .build();
        next.active = this.page < maxPage && !this.loading && !this.results.isEmpty();
        this.addRenderableWidget(next);
    }

    private void doNext(int maxPage) {
        if (this.page < maxPage) {
            this.page++;
            this.rebuildResultButtons();
        }
    }

    private void doPrevious() {
        if (this.page > 0) {
            this.page--;
            this.rebuildResultButtons();
        }
    }

    private int getMaxPage() {
        int size = this.results.size();
        return size == 0 ? 0 : (size - 1) / PAGE_SIZE;
    }

    private void selectStation(Station station) {
        String candidate = station.url == null ? "" : station.url.trim();
        if (candidate.isBlank()) {
            this.statusMessage = "Station URL is empty.";
            return;
        }

        try {
            var method = parentScreen.getClass().getMethod("applyPresetStation", String.class, String.class);
            method.invoke(parentScreen, (station.name == null || station.name.isBlank()) ? candidate : station.name, candidate);
        } catch (Exception ignored) {
            // If parent screen doesn't expose applyPresetStation, do nothing special.
        }

        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.topPos + 6, 0xFFFFFF);
        if (!this.statusMessage.isBlank()) {
            graphics.drawString(this.font, this.statusMessage, this.leftPos, this.topPos + 50, 0xAAAAAA, false);
        }
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
