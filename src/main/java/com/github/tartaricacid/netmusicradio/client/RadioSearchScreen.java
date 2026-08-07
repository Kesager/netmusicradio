package com.github.tartaricacid.netmusicradio.client;

import com.github.tartaricacid.netmusicradio.client.RadioBrowserClient.Station;
import com.github.tartaricacid.netmusicradio.client.gui.CustomBigMegaphoneScreen;
import com.github.tartaricacid.netmusicradio.client.util.BigMegaphoneUtilProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RadioSearchScreen extends Screen {
    private static final int MARGIN = 12;
    private static final int HEADER_HEIGHT = 54;
    private static final int FOOTER_HEIGHT = 36;
    private static final int MIN_ITEM_HEIGHT = 24;
    private static final int MAX_ITEM_HEIGHT = 32;
    private static final int MIN_LOGO_SIZE = 16;
    private static final int MAX_LOGO_SIZE = 24;
    private static final int DROPDOWN_ITEM_HEIGHT = 18;
    private static final int DROPDOWN_VISIBLE_ITEMS = 6;
    private static final int DROPDOWN_SEARCH_HEIGHT = 24;

    private final Screen parentScreen;
    private int leftPos;
    private int topPos;
    private int page = 0;
    private boolean countriesLoaded = false;

    private EditBox searchField;
    private Checkbox onlineCheckbox;
    private Button countryBtn;
    private Button searchBtn;
    private Button clearBtn;
    private EditBox countrySearchField;

    private List<Station> results = new ArrayList<>();
    private List<String> filteredCountries = new ArrayList<>();

    private boolean onlineSearch = false;
    private boolean loading = false;
    private String statusMessage = "";
    private String selectedCountry = "";
    private boolean countryDropdownOpen = false;
    private int countryScrollOffset = 0;

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

    private int getDropdownHeight() {
        return DROPDOWN_SEARCH_HEIGHT + DROPDOWN_VISIBLE_ITEMS * DROPDOWN_ITEM_HEIGHT + 2;
    }

    private void handleDropdownClear() {
        this.selectedCountry = "";
        if (this.clearBtn != null) {
            this.clearBtn.visible = false;
        }
        if (this.countrySearchField != null) {
            this.countrySearchField.setValue("");
            updateFilteredCountries();
        }
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - getPanelWidth()) / 2;
        this.topPos = (this.height - getPanelHeight()) / 2;

        int w = getPanelWidth();
        int searchW = (int) (w * 0.55);

        this.searchField = new EditBox(this.font, this.leftPos, this.topPos + 8, searchW, 18,
                Component.translatable("gui.netmusicradio.search.name"));
        this.searchField.setMaxLength(128);
        this.addRenderableWidget(this.searchField);

        rebuildListButtons();
    }

    private void syncOnlineCheckbox() {
        if (this.onlineCheckbox != null) {
            this.onlineSearch = this.onlineCheckbox.selected();
        }
    }

    private void openCountryDropdown() {
        this.countryDropdownOpen = true;
        this.countryScrollOffset = 0;
        this.countriesLoaded = StationDatabase.isLoaded();
        if (!StationDatabase.isLoaded()) {
            StationDatabase.ensureLoadedAsync();
        }
        this.filteredCountries = new ArrayList<>(StationDatabase.getCountries());
        int searchW = (int) (getPanelWidth() * 0.55);
        int countryW = getPanelWidth() - searchW - 4;
        int dropdownX = this.leftPos + searchW + 4;
        int dropdownY = this.topPos + 8 + 18 + 2;
        this.countrySearchField = new EditBox(this.font, dropdownX + 2, dropdownY + 2, countryW - 26, DROPDOWN_SEARCH_HEIGHT - 4,
                Component.translatable("gui.netmusicradio.search.country.hint"));
        this.countrySearchField.setMaxLength(64);
        String clearText = Component.translatable("gui.netmusicradio.search.country.clear").getString();
        int clearBtnW = this.font.width(clearText) + 8;
        this.clearBtn = Button.builder(
                Component.literal(clearText),
                b -> handleDropdownClear())
                .pos(dropdownX + countryW - clearBtnW - 2, dropdownY + 2)
                .size(clearBtnW, DROPDOWN_SEARCH_HEIGHT - 4)
                .build();
        this.clearBtn.visible = false;
        rebuildListButtons();
        this.setFocused(this.countrySearchField);
    }

    private void closeCountryDropdown() {
        this.countryDropdownOpen = false;
        this.countriesLoaded = false;
        if (this.countrySearchField != null) {
            this.countrySearchField = null;
        }
        if (this.clearBtn != null) {
            this.clearBtn = null;
        }
        this.countryScrollOffset = 0;
        this.filteredCountries.clear();
        this.setFocused(this.searchField);
        if (this.onlineCheckbox != null) {
            this.onlineCheckbox.visible = true;
        }
        if (this.searchBtn != null) {
            this.searchBtn.visible = true;
        }
    }

    private void updateFilteredCountries() {
        if (!this.countryDropdownOpen) return;
        String query = this.countrySearchField.getValue().trim().toLowerCase();
        List<String> all = StationDatabase.getCountries();
        if (query.isBlank()) {
            this.filteredCountries = new ArrayList<>(all);
        } else {
            List<String> filtered = new ArrayList<>();
            for (String c : all) {
                if (c.toLowerCase().contains(query)) {
                    filtered.add(c);
                }
            }
            this.filteredCountries = filtered;
        }
        if (this.clearBtn != null) {
            this.clearBtn.visible = !this.selectedCountry.isBlank();
        }
        this.countryScrollOffset = 0;
    }

    private void selectCountry(String country) {
        if (country == null || country.isBlank()) {
            this.selectedCountry = "";
        } else {
            this.selectedCountry = country;
        }
        closeCountryDropdown();
        rebuildListButtons();
        syncOnlineCheckbox();
    }

    private void doSearch() {
        String q = this.searchField.getValue().trim();
        String country = this.selectedCountry;
        if (q.isBlank() && (country == null || country.isBlank())) {
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

        String countryLabel;
        if (this.selectedCountry.isBlank()) {
            countryLabel = Component.translatable("gui.netmusicradio.search.country").getString();
        } else {
            countryLabel = this.selectedCountry;
        }
        this.countryBtn = Button.builder(
                Component.literal(countryLabel + " \u25BE"),
                b -> {
                    if (this.countryDropdownOpen) {
                        closeCountryDropdown();
                    } else {
                        openCountryDropdown();
                    }
                })
                .pos(this.leftPos + searchW + 4, this.topPos + 8).size(countryW, 18).build();
        this.addRenderableWidget(this.countryBtn);

        int btnW = (w - 2) / 2;
        this.searchBtn = Button.builder(
                Component.translatable("gui.netmusicradio.search.button"),
                b -> doSearch())
                .pos(this.leftPos, this.topPos + 30).size(btnW, 18).build();
        this.searchBtn.visible = !this.countryDropdownOpen;
        this.addRenderableWidget(this.searchBtn);

        this.onlineCheckbox = new Checkbox(
                this.leftPos + btnW + 2, this.topPos + 30,
                btnW, 18,
                Component.translatable("gui.netmusicradio.search.online_search"),
                this.onlineSearch);
        this.onlineCheckbox.visible = !this.countryDropdownOpen;
        this.addRenderableWidget(this.onlineCheckbox);

        if (this.countryDropdownOpen && this.countrySearchField != null) {
            int dropdownX = this.countryBtn.getX();
            int dropdownY = this.countryBtn.getY() + this.countryBtn.getHeight() + 2;
            String clearText = Component.translatable("gui.netmusicradio.search.country.clear").getString();
            int clearBtnW = this.font.width(clearText) + 8;
            this.countrySearchField.setX(dropdownX + 2);
            this.countrySearchField.setY(dropdownY + 2);
            this.countrySearchField.setWidth(countryW - clearBtnW - 6);
            this.countrySearchField.visible = true;
            if (this.clearBtn != null) {
                this.clearBtn.setX(dropdownX + countryW - clearBtnW - 2);
                this.clearBtn.setY(dropdownY + 2);
                this.clearBtn.visible = !this.selectedCountry.isBlank();
                this.addRenderableWidget(this.clearBtn);
            }
        }

        int listTop = this.topPos + HEADER_HEIGHT;
        int itemH = getItemHeight();
        int pageSize = getPageSize();

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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.countryDropdownOpen) {
            int dropdownX = this.countryBtn.getX();
            int dropdownY = this.countryBtn.getY() + this.countryBtn.getHeight() + 2;
            int dropdownW = this.countryBtn.getWidth();
            int dropdownH = getDropdownHeight();

            if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownW
                    && mouseY >= dropdownY && mouseY <= dropdownY + dropdownH) {

                int searchBottom = dropdownY + DROPDOWN_SEARCH_HEIGHT;
                if (mouseY >= dropdownY && mouseY <= searchBottom && this.countrySearchField != null) {
                    if (this.clearBtn != null && this.clearBtn.visible
                            && mouseX >= this.clearBtn.getX() && mouseX <= this.clearBtn.getX() + this.clearBtn.getWidth()
                            && mouseY >= this.clearBtn.getY() && mouseY <= this.clearBtn.getY() + this.clearBtn.getHeight()) {
                        return super.mouseClicked(mouseX, mouseY, button);
                    }
                    this.setFocused(this.countrySearchField);
                    return this.countrySearchField.mouseClicked(mouseX, mouseY, button);
                }

                int relY = (int) mouseY - dropdownY - DROPDOWN_SEARCH_HEIGHT;
                if (relY >= 0) {
                    int itemIndex = relY / DROPDOWN_ITEM_HEIGHT + this.countryScrollOffset;
                    if (itemIndex >= 0 && itemIndex < this.filteredCountries.size()) {
                        selectCountry(this.filteredCountries.get(itemIndex));
                        return true;
                    }
                }

                return true;
            } else {
                closeCountryDropdown();
                syncOnlineCheckbox();
                return super.mouseClicked(mouseX, mouseY, button);
            }
        }
        syncOnlineCheckbox();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.countryDropdownOpen) {
            int dropdownX = this.countryBtn.getX();
            int dropdownY = this.countryBtn.getY() + this.countryBtn.getHeight() + 2;
            int dropdownW = this.countryBtn.getWidth();
            int dropdownH = getDropdownHeight();

            if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownW
                    && mouseY >= dropdownY && mouseY <= dropdownY + dropdownH) {
                int maxScroll = Math.max(0, this.filteredCountries.size() - DROPDOWN_VISIBLE_ITEMS);
                if (delta > 0 && this.countryScrollOffset > 0) {
                    this.countryScrollOffset--;
                    return true;
                } else if (delta < 0 && this.countryScrollOffset < maxScroll) {
                    this.countryScrollOffset++;
                    return true;
                }
                return false;
            }
        }

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
        if (this.countryDropdownOpen) {
            if (keyCode == 266) {
                if (this.countryScrollOffset > 0) {
                    this.countryScrollOffset--;
                    return true;
                }
            } else if (keyCode == 267) {
                int maxScroll = Math.max(0, this.filteredCountries.size() - DROPDOWN_VISIBLE_ITEMS);
                if (this.countryScrollOffset < maxScroll) {
                    this.countryScrollOffset++;
                    return true;
                }
            } else if (keyCode == 268) {
                closeCountryDropdown();
                return true;
            }
            if (this.countrySearchField != null && this.getFocused() == this.countrySearchField) {
                if (this.countrySearchField.keyPressed(keyCode, scanCode, modifiers)) {
                    updateFilteredCountries();
                    return true;
                }
            }
        }
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
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.countryDropdownOpen) {
            boolean result = super.charTyped(codePoint, modifiers);
            updateFilteredCountries();
            return result;
        }
        return super.charTyped(codePoint, modifiers);
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
            String logoUrl = station.logoUrl;
            if (logoUrl != null && !logoUrl.isBlank()) {
                ResourceLocation logoRl = LogoManager.getInstance().getLogo(logoUrl);
                if (logoRl != null) {
                    graphics.blit(logoRl, logoX, logoY, logoS, logoS, 0, 0, 64, 64, 64, 64);
                } else {
                    LogoManager.getInstance().loadLogo(logoUrl);
                    graphics.fill(logoX, logoY, logoX + logoS, logoY + logoS, 0xFF222222);
                }
            } else {
                graphics.fill(logoX, logoY, logoX + logoS, logoY + logoS, 0xFF222222);
            }

            String name = station.name == null || station.name.isBlank() ? station.url : station.name;
            int maxNameLen = (w - logoS - 20) / 6;
            String displayName = name.length() > maxNameLen ? name.substring(0, Math.max(0, maxNameLen - 3)) + "..." : name;
            graphics.drawString(this.font, displayName, logoX + logoS + 4, y + (itemH / 2) - 6, 0xFFFFFF);

            String country = station.country != null ? station.country : "";
            String desc = station.description != null ? station.description : "";
            String secondary = !desc.isBlank() ? desc : (!country.isBlank() ? country :
                    Component.translatable("gui.netmusicradio.search.unknown_region").getString());
            int maxCtryLen = (w - logoS - 20) / 6;
            if (secondary.length() > maxCtryLen) {
                secondary = secondary.substring(0, Math.max(0, maxCtryLen - 3)) + "...";
            }
            graphics.drawString(this.font, secondary, logoX + logoS + 4, y + (itemH / 2) + 6, 0xAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);

        if (this.countryDropdownOpen) {
            int dropdownX = this.countryBtn.getX();
            int dropdownY = this.countryBtn.getY() + this.countryBtn.getHeight() + 2;
            int dropdownW = this.countryBtn.getWidth();
            int dropdownH = getDropdownHeight();

            graphics.fill(dropdownX, dropdownY, dropdownX + dropdownW, dropdownY + dropdownH, 0xFF252525);
            graphics.fill(dropdownX, dropdownY, dropdownX + dropdownW, dropdownY + 1, 0xFF555555);
            graphics.fill(dropdownX, dropdownY + dropdownH - 1, dropdownX + dropdownW, dropdownY + dropdownH, 0xFF555555);

            if (this.countrySearchField != null) {
                this.countrySearchField.render(graphics, mouseX, mouseY, partialTicks);
            }

            int listStartY = dropdownY + DROPDOWN_SEARCH_HEIGHT;
            int visibleCount = Math.min(DROPDOWN_VISIBLE_ITEMS, this.filteredCountries.size());

            for (int i = 0; i < visibleCount; i++) {
                int idx = i + this.countryScrollOffset;
                if (idx >= this.filteredCountries.size()) break;
                String countryName = this.filteredCountries.get(idx);
                int itemY = listStartY + i * DROPDOWN_ITEM_HEIGHT;

                boolean selected = countryName.equals(this.selectedCountry);
                if (selected) {
                    graphics.fill(dropdownX + 1, itemY, dropdownX + dropdownW - 1, itemY + DROPDOWN_ITEM_HEIGHT - 1, 0xFF3A5A3A);
                }

                int maxLen = (dropdownW - 10) / 6;
                String display = countryName.length() > maxLen
                        ? countryName.substring(0, Math.max(0, maxLen - 3)) + "..."
                        : countryName;
                graphics.drawString(this.font, display, dropdownX + 4, itemY + (DROPDOWN_ITEM_HEIGHT - 9) / 2,
                        selected ? 0xFFFFFF : 0xD0D0D0);
            }

            if (this.filteredCountries.isEmpty()) {
                if (!StationDatabase.isLoaded()) {
                    String loading = Component.translatable("gui.netmusicradio.search.loading_local").getString();
                    graphics.drawString(this.font, loading, dropdownX + 4, listStartY + 4, 0x888888);
                } else {
                    String noResult = Component.translatable("gui.netmusicradio.search.no_results").getString();
                    graphics.drawString(this.font, noResult, dropdownX + 4, listStartY + 4, 0x888888);
                }
            }

            if (this.filteredCountries.size() > DROPDOWN_VISIBLE_ITEMS) {
                int trackH = DROPDOWN_VISIBLE_ITEMS * DROPDOWN_ITEM_HEIGHT;
                graphics.fill(dropdownX + dropdownW - 3, listStartY, dropdownX + dropdownW - 1, listStartY + trackH, 0xFF1A1A1A);
                int thumbH = Math.max(4, trackH * DROPDOWN_VISIBLE_ITEMS / this.filteredCountries.size());
                int thumbY = listStartY + (this.countryScrollOffset * DROPDOWN_ITEM_HEIGHT * DROPDOWN_VISIBLE_ITEMS) / this.filteredCountries.size();
                thumbY = Math.min(thumbY, listStartY + trackH - thumbH);
                graphics.fill(dropdownX + dropdownW - 3, thumbY, dropdownX + dropdownW - 1, thumbY + thumbH, 0xFF666666);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        LogoManager.getInstance().tick();
        if (!this.onlineSearch
                && this.statusMessage.equals(Component.translatable("gui.netmusicradio.search.loading_local").getString())
                && StationDatabase.isLoaded()) {
            this.statusMessage = "";
            rebuildListButtons();
        }
        if (this.countryDropdownOpen && StationDatabase.isLoaded() && !this.countriesLoaded) {
            this.countriesLoaded = true;
            this.filteredCountries = new ArrayList<>(StationDatabase.getCountries());
            if (this.countrySearchField != null) {
                updateFilteredCountries();
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}