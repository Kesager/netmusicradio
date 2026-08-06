package com.github.tartaricacid.netmusicradio.client;

import com.github.tartaricacid.netmusicradio.client.RadioBrowserClient.Station;
import com.github.tartaricacid.netmusicradio.client.gui.CustomBigMegaphoneScreen;
import com.github.tartaricacid.netmusicradio.client.util.BigMegaphoneUtilProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class RadioSearchScreen extends Screen {
    private static final int MARGIN = 12;
    private static final int HEADER_HEIGHT = 72;
    private static final int FOOTER_HEIGHT = 36;
    private static final int MIN_ITEM_HEIGHT = 24;
    private static final int MAX_ITEM_HEIGHT = 32;
    private static final int MIN_LOGO_SIZE = 16;
    private static final int MAX_LOGO_SIZE = 24;

    private final Screen parentScreen;
    private int leftPos;
    private int topPos;
    private int page = 0;

    private EditBox searchField;
    private EditBox countryField;

    private List<Station> results = new ArrayList<>();

    private boolean onlineSearch = false;
    private boolean loading = false;
    private String statusMessage = "";

    public RadioSearchScreen(Screen parent) {
        super(Component.translatable("gui.netmusicradio.preset_picker.title"));
        this.parentScreen = parent;
    }

    private int getPanelWidth() {
        return Math.min(320, this.width - MARGIN * 2);
    }

    private int getPanelHeight() {
        return Math.min(420, this.height - MARGIN * 2);
    }

    private int getItemHeight() {
        int available = getPanelHeight() - HEADER_HEIGHT - FOOTER_HEIGHT;
        int maxItems = Math.max(3, available / MIN_ITEM_HEIGHT);
        int itemH = available / maxItems;
        return Math.max(MIN_ITEM_HEIGHT, Math.min(MAX_ITEM_HEIGHT, itemH));
    }

    private int getPageSize() {
        int available = getPanelHeight() - HEADER_HEIGHT - FOOTER_HEIGHT;
        return Math.max(3, available / getItemHeight());
    }

    private int getLogoSize() {
        int s = getItemHeight() - 8;
        return Math.max(MIN_LOGO_SIZE, Math.min(MAX_LOGO_SIZE, s));
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - getPanelWidth()) / 2;
        this.topPos = (this.height - getPanelHeight()) / 2;

        int w = getPanelWidth();
        int searchW = (int) (w * 0.55);
        int countryW = w - searchW - 4;

        this.searchField = new EditBox(this.font, this.leftPos, this.topPos + 8, searchW, 18,
                Component.translatable("gui.netmusicradio.search.name"));
        this.searchField.setMaxLength(128);
        this.addRenderableWidget(this.searchField);

        this.countryField = new EditBox(this.font, this.leftPos + searchW + 4, this.topPos + 8, countryW, 18,
                Component.translatable("gui.netmusicradio.search.country"));
        this.countryField.setMaxLength(64);
        this.addRenderableWidget(this.countryField);

        int btnW = (w - 4) / 3;
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.netmusicradio.search.button"),
                b -> doSearch())
                .pos(this.leftPos, this.topPos + 30).size(btnW, 18).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.netmusic.big_megaphone.back"),
                b -> this.onClose())
                .pos(this.leftPos + (btnW + 2) * 2, this.topPos + 30).size(btnW, 18).build());

        rebuildListButtons();
    }

    private void toggleOnlineSearch() {
        this.onlineSearch = !this.onlineSearch;
        rebuildListButtons();
    }

    private void doSearch() {
        String q = this.searchField.getValue().trim();
        String country = this.countryField.getValue().trim();
        if (q.isBlank() && country.isBlank()) {
            this.statusMessage = Component.translatable("gui.netmusicradio.search.enter_hint").getString();
            this.results.clear();
            this.page = 0;
            rebuildListButtons();
            return;
        }
        this.page = 0;
        this.loading = true;
        this.statusMessage = Component.translatable("gui.netmusicradio.search.searching").getString();
        rebuildListButtons();
        this.results.clear();

        if (this.onlineSearch) {
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
                            this.statusMessage = Component.translatable("gui.netmusicradio.search.no_results").getString();
                        } else {
                            this.statusMessage = Component.translatable("gui.netmusicradio.search.found", this.results.size()).getString();
                        }
                        rebuildListButtons();
                    });
                }
            }, "NetMusic-RadioSearch").start();
        } else {
            if (!StationDatabase.isLoaded()) {
                StationDatabase.ensureLoadedAsync();
                this.statusMessage = Component.translatable("gui.netmusicradio.search.loading_local").getString();
                this.loading = false;
                this.results.clear();
                this.page = 0;
                rebuildListButtons();
                return;
            }
            List<Station> found = StationDatabase.search(q, country, 50);
            List<Station> filtered = new ArrayList<>();
            for (Station s : found) {
                if (s == null || s.url == null || s.url.isBlank()) continue;
                if (!BigMegaphoneUtilProxy.isValidStreamUrl(s.url)) continue;
                filtered.add(s);
            }
            this.loading = false;
            this.results = filtered;
            if (StationDatabase.getLoadError() != null) {
                this.statusMessage = StationDatabase.getLoadError();
            } else if (this.results.isEmpty()) {
                this.statusMessage = Component.translatable("gui.netmusicradio.search.no_results").getString();
            } else {
                this.statusMessage = Component.translatable("gui.netmusicradio.search.found", this.results.size()).getString();
            }
            rebuildListButtons();
        }
    }

    private void rebuildListButtons() {
        this.clearWidgets();

        int w = getPanelWidth();
        int searchW = (int) (w * 0.55);
        int countryW = w - searchW - 4;

        this.addRenderableWidget(this.searchField);
        this.addRenderableWidget(this.countryField);

        int btnW = (w - 4) / 3;
        Button searchBtn = Button.builder(
                Component.translatable("gui.netmusicradio.search.button"),
                b -> doSearch())
                .pos(this.leftPos, this.topPos + 30).size(btnW, 18).build();
        this.addRenderableWidget(searchBtn);

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.netmusic.big_megaphone.back"),
                b -> this.onClose())
                .pos(this.leftPos + (btnW + 2) * 2, this.topPos + 30).size(btnW, 18).build());

        int checkboxY = this.topPos + HEADER_HEIGHT - 14;
        int checkboxX = this.leftPos + 2;

        Button onlineBtn = Button.builder(
                Component.translatable(this.onlineSearch ? "gui.netmusicradio.search.online_enabled" : "gui.netmusicradio.search.online_disabled"),
                b -> toggleOnlineSearch())
                .pos(checkboxX, checkboxY).size(Math.min(160, w - 4), 16).build();
        this.addRenderableWidget(onlineBtn);

        int listTop = this.topPos + HEADER_HEIGHT;
        int itemH = getItemHeight();
        int pageSize = getPageSize();
        int logoS = getLogoSize();

        int start = this.page * pageSize;
        int end = Math.min(start + pageSize, this.results.size());

        for (int i = start; i < end; i++) {
            int index = i - start;
            Station station = this.results.get(i);
            int y = listTop + index * itemH;

            Button addBtn = Button.builder(
                    Component.translatable("gui.netmusicradio.search.add"),
                    b -> selectStation(station))
                    .pos(this.leftPos + w - 44, y + (itemH - 16) / 2).size(42, 16).build();
            this.addRenderableWidget(addBtn);
        }

        int navY = listTop + pageSize * itemH + 4;
        int navBtnW = (w - 4) / 3;

        Button previous = Button.builder(
                Component.translatable("gui.netmusic.big_megaphone.page.previous"),
                b -> doPrevious())
                .pos(this.leftPos, navY).size(navBtnW, 18).build();
        previous.active = this.page > 0 && !this.loading && !this.results.isEmpty();
        this.addRenderableWidget(previous);

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.netmusic.big_megaphone.back"),
                b -> this.onClose())
                .pos(this.leftPos + navBtnW + 2, navY).size(navBtnW, 18).build());

        int maxPage = getMaxPage();
        Button next = Button.builder(
                Component.translatable("gui.netmusic.big_megaphone.page.next"),
                b -> doNext(maxPage))
                .pos(this.leftPos + (navBtnW + 2) * 2, navY).size(navBtnW, 18).build();
        next.active = this.page < maxPage && !this.loading && !this.results.isEmpty();
        this.addRenderableWidget(next);
    }

    private void doNext(int maxPage) {
        if (this.page < maxPage) {
            this.page++;
            rebuildListButtons();
        }
    }

    private void doPrevious() {
        if (this.page > 0) {
            this.page--;
            rebuildListButtons();
        }
    }

    private int getMaxPage() {
        int size = this.results.size();
        int pageSize = getPageSize();
        return size == 0 ? 0 : (size - 1) / pageSize;
    }

    private void selectStation(Station station) {
        String candidate = station.url == null ? "" : station.url.trim();
        if (candidate.isBlank()) {
            this.statusMessage = Component.translatable("gui.netmusicradio.search.empty_url").getString();
            return;
        }

        String displayName = (station.name == null || station.name.isBlank()) ? candidate : station.name;

        CustomBigMegaphoneScreen targetScreen = null;

        if (parentScreen instanceof CustomBigMegaphoneScreen screen) {
            targetScreen = screen;
        }

        if (targetScreen != null) {
            targetScreen.setStation(displayName, candidate);
        }

        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.results.isEmpty()) return false;
        int maxPage = getMaxPage();
        if (delta > 0 && this.page < maxPage) {
            this.page++;
            rebuildListButtons();
            return true;
        } else if (delta < 0 && this.page > 0) {
            this.page--;
            rebuildListButtons();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 264 && this.page < getMaxPage()) {
            this.page++;
            rebuildListButtons();
            return true;
        } else if (keyCode == 265 && this.page > 0) {
            this.page--;
            rebuildListButtons();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

        int w = getPanelWidth();
        int h = getPanelHeight();
        int lx = (this.width - w) / 2;
        int ly = (this.height - h) / 2;

        graphics.fill(lx, ly, lx + w, ly + h, 0xFF1A1A1A);
        graphics.fill(lx, ly, lx + w, ly + 1, 0xFF555555);
        graphics.fill(lx, ly + h - 1, lx + w, ly + h, 0xFF555555);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, ly + 2, 0xFFFFFF);

        if (!this.statusMessage.isBlank()) {
            graphics.drawString(this.font, this.statusMessage, lx, ly + 48, 0xAAAAAA, false);
        }

        int listTop = ly + HEADER_HEIGHT;
        int itemH = getItemHeight();
        int pageSize = getPageSize();
        int logoS = getLogoSize();
        int innerPad = (itemH - logoS) / 2;

        int start = this.page * pageSize;
        int end = Math.min(start + pageSize, this.results.size());

        for (int i = start; i < end; i++) {
            int index = i - start;
            Station station = this.results.get(i);
            int y = listTop + index * itemH;

            graphics.fill(lx, y, lx + w, y + itemH - 1, 0xFF2A2A2A);
            graphics.fill(lx, y, lx + w, y + 1, 0xFF3A3A3A);

            int logoX = lx + 4;
            int logoY = y + innerPad;
            graphics.fill(logoX, logoY, logoX + logoS, logoY + logoS, 0xFF222222);

            String name = station.name == null || station.name.isBlank() ? station.url : station.name;
            int maxNameLen = (w - logoS - 20) / 6;
            String displayName = name.length() > maxNameLen ? name.substring(0, Math.max(0, maxNameLen - 3)) + "..." : name;
            graphics.drawString(this.font, displayName, logoX + logoS + 4, y + (itemH / 2) - 6, 0xFFFFFF);

            String country = station.country != null ? station.country : "";
            String countryText = country.isBlank() ?
                    Component.translatable("gui.netmusicradio.search.unknown_region").getString() :
                    country;
            int maxCtryLen = (w - logoS - 20) / 6;
            if (countryText.length() > maxCtryLen) {
                countryText = countryText.substring(0, Math.max(0, maxCtryLen - 3)) + "...";
            }
            graphics.drawString(this.font, countryText, logoX + logoS + 4, y + (itemH / 2) + 6, 0xAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.onlineSearch
                && this.statusMessage.equals(Component.translatable("gui.netmusicradio.search.loading_local").getString())
                && StationDatabase.isLoaded()) {
            this.statusMessage = "";
            rebuildListButtons();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}