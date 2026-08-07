package org.eu.sager.netmusicradio.client;

import org.eu.sager.netmusicradio.client.RadioBrowserClient.Station;
import org.eu.sager.netmusicradio.client.gui.CustomBigMegaphoneScreen;
import org.eu.sager.netmusicradio.client.util.BigMegaphoneUtilProxy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RadioSearchScreen extends Screen {
    private static final int MARGIN = 12;
    private static final int MAX_CONTENT_WIDTH = 400;
    private static final int TITLE_BAR_HEIGHT = 56;
    private static final int FOOTER_HEIGHT = 58;
    private static final int TITLE_Y = 6;
    private static final int SUBTITLE_Y = 19;
    private static final int HEADER_INPUT_Y = 32;
    private static final int MIN_ITEM_HEIGHT = 28;
    private static final int MAX_ITEM_HEIGHT = 36;
    private static final int MIN_LOGO_SIZE = 18;
    private static final int MAX_LOGO_SIZE = 26;
    private static final int BTN_HEIGHT = 20;
    private static final int SEARCH_BTN_WIDTH = 24;
    private static final int ADD_BTN_WIDTH = 42;
    private static final int ADD_BTN_HEIGHT = 18;
    private static final int ADD_BTN_RIGHT_PAD = 44;
    private static final int FAVORITE_BTN_WIDTH = 20;
    private static final int FAVORITE_BTN_HEIGHT = 18;
    private static final int FAVORITES_BTN_WIDTH = 60;
    private static final int LOGO_TEXTURE_SIZE = 64;
    private static final int FONT_HEIGHT = 9;
    private static final int CHAR_WIDTH_ESTIMATE = 6;
    private static final int LOGO_TEXT_PAD = 20;
    private static final int NAV_GAP = 4;
    private static final int SEARCH_RESULT_LIMIT = 50;

    private final Screen parentScreen;
    private int leftPos;
    private int topPos;
    private int page = 0;

    private EditBox searchField;
    private EditBox pageField;
    private Checkbox onlineCheckbox;
    private Button countryBtn;
    private Button searchBtn;

    private final CountryDropdown dropdown = new CountryDropdown();

    private List<Station> results = new ArrayList<>();

    private boolean onlineSearch = false;
    private boolean autoSearchPending = false;
    private boolean loading = false;
    private String statusMessage = "";
    private String selectedCountry = "";
    private int hoveredItemIndex = -1;
    private final List<Button> favoriteButtons = new ArrayList<>();
    private final List<Station> favoriteButtonStations = new ArrayList<>();

    public RadioSearchScreen(Screen parent) {
        super(Component.translatable("gui.netmusicradio.preset_picker.title"));
        this.parentScreen = parent;
    }

    // ===== 布局计算 =====

    private int getPanelWidth() {
        return Math.min(this.width - MARGIN * 2, MAX_CONTENT_WIDTH);
    }

    private int getPanelHeight() {
        return this.height;
    }

    private int getContentLeft() {
        return (this.width - getPanelWidth()) / 2;
    }

    private int getListTop() {
        return TITLE_BAR_HEIGHT;
    }

    private int getListBottom() {
        return this.height - FOOTER_HEIGHT;
    }

    private int getItemHeight() {
        int available = getListBottom() - getListTop();
        int maxItems = Math.max(3, available / MIN_ITEM_HEIGHT);
        int itemH = available / maxItems;
        return Math.max(MIN_ITEM_HEIGHT, Math.min(MAX_ITEM_HEIGHT, itemH));
    }

    private int getPageSize() {
        int available = getListBottom() - getListTop();
        return Math.max(3, available / getItemHeight());
    }

    private int getLogoSize() {
        int s = getItemHeight() - 8;
        return Math.max(MIN_LOGO_SIZE, Math.min(MAX_LOGO_SIZE, s));
    }

    private int getMaxPage() {
        int size = this.results.size();
        int pageSize = getPageSize();
        return size == 0 ? 0 : (size - 1) / pageSize;
    }

    // ===== 初始化 =====

    @Override
    protected void init() {
        this.leftPos = getContentLeft();
        this.topPos = 0;

        int w = getPanelWidth();
        int countryW = (int) (w * 0.4);
        int searchW = w - countryW - SEARCH_BTN_WIDTH - NAV_GAP * 2;

        this.searchField = new EditBox(this.font, this.leftPos, this.topPos + HEADER_INPUT_Y, searchW, BTN_HEIGHT,
                Component.translatable("gui.netmusicradio.search.name"));
        this.searchField.setMaxLength(128);
        this.addRenderableWidget(this.searchField);

        this.autoSearchPending = true;
        StationDatabase.ensureLoadedAsync();
        FavoritesManager.load();

        rebuildListButtons();
    }

    private void syncOnlineCheckbox() {
        if (this.onlineCheckbox != null) {
            this.onlineSearch = this.onlineCheckbox.selected();
        }
    }

    // ===== 控件重建 =====

    private void rebuildListButtons() {
        this.clearWidgets();
        this.favoriteButtons.clear();
        this.favoriteButtonStations.clear();
        rebuildHeader();
        rebuildListItems();
        rebuildNav();
    }

    private void rebuildHeader() {
        int w = getPanelWidth();
        int countryW = (int) (w * 0.4);
        int searchW = w - countryW - SEARCH_BTN_WIDTH - NAV_GAP * 2;

        this.addRenderableWidget(this.searchField);

        String countryLabel = dropdown.getButtonLabel();
        this.countryBtn = Button.builder(
                Component.literal(countryLabel + " \u25BE"),
                b -> dropdown.toggle())
                .pos(this.leftPos + searchW + NAV_GAP, this.topPos + HEADER_INPUT_Y)
                .size(countryW, BTN_HEIGHT).build();
        this.addRenderableWidget(this.countryBtn);

        this.searchBtn = Button.builder(
                Component.literal("\uD83D\uDD0D"),
                b -> doSearch())
                .pos(this.leftPos + searchW + countryW + NAV_GAP * 2, this.topPos + HEADER_INPUT_Y)
                .size(SEARCH_BTN_WIDTH, BTN_HEIGHT).build();
        this.addRenderableWidget(this.searchBtn);

        dropdown.rebuildWidgets();
    }

    private void rebuildListItems() {
        if (dropdown.isOpen()) return;

        int w = getPanelWidth();
        int listTop = getListTop();
        int itemH = getItemHeight();
        int pageSize = getPageSize();

        int start = this.page * pageSize;
        int end = Math.min(start + pageSize, this.results.size());

        for (int i = start; i < end; i++) {
            int index = i - start;
            Station station = this.results.get(i);
            int y = listTop + index * itemH;

            boolean isFav = FavoritesManager.isFavorite(station);
            String favText = isFav ? "\u2605" : "\u2606";
            int favBtnX = this.leftPos + w - ADD_BTN_RIGHT_PAD - FAVORITE_BTN_WIDTH - NAV_GAP;
            Button favoriteBtn = Button.builder(
                    Component.literal(favText),
                    b -> toggleFavorite(station))
                    .pos(favBtnX, y + (itemH - FAVORITE_BTN_HEIGHT) / 2)
                    .size(FAVORITE_BTN_WIDTH, FAVORITE_BTN_HEIGHT).build();
            favoriteBtn.visible = isFav;
            this.addRenderableWidget(favoriteBtn);
            this.favoriteButtons.add(favoriteBtn);
            this.favoriteButtonStations.add(station);

            Button addBtn = Button.builder(
                    Component.translatable("gui.netmusicradio.search.add"),
                    b -> selectStation(station))
                    .pos(this.leftPos + w - ADD_BTN_RIGHT_PAD, y + (itemH - ADD_BTN_HEIGHT) / 2)
                    .size(ADD_BTN_WIDTH, ADD_BTN_HEIGHT).build();
            this.addRenderableWidget(addBtn);
        }
    }

    private void rebuildNav() {
        int w = getPanelWidth();
        int h = getPanelHeight();
        int topRowY = this.topPos + h - FOOTER_HEIGHT + 4;
        int bottomRowY = this.topPos + h - BTN_HEIGHT - 4;

        int navBtnW = (w - NAV_GAP * 2) / 3;

        Button previous = Button.builder(
                Component.translatable("gui.netmusic.big_megaphone.page.previous"),
                b -> doPrevious())
                .pos(this.leftPos, topRowY).size(navBtnW, BTN_HEIGHT).build();
        previous.active = this.page > 0 && !this.loading && !this.results.isEmpty();
        this.addRenderableWidget(previous);

        int maxPage = getMaxPage();
        String pageHint = (this.results.isEmpty() ? 0 : this.page + 1) + "/" + (maxPage + 1);
        this.pageField = new EditBox(this.font, this.leftPos + navBtnW + NAV_GAP, topRowY,
                navBtnW, BTN_HEIGHT, Component.literal(pageHint));
        this.pageField.setMaxLength(8);
        this.pageField.setValue("");
        this.pageField.setHint(Component.literal(pageHint));
        this.pageField.setResponder(s -> {});
        this.pageField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        this.pageField.setFormatter((s, i) -> {
            int textWidth = this.font.width(s);
            int pad = Math.max(0, (navBtnW - textWidth) / 2);
            return Component.literal(" ".repeat(pad) + s).getVisualOrderText();
        });
        this.addRenderableWidget(this.pageField);

        Button next = Button.builder(
                Component.translatable("gui.netmusic.big_megaphone.page.next"),
                b -> doNext(maxPage))
                .pos(this.leftPos + (navBtnW + NAV_GAP) * 2, topRowY).size(navBtnW, BTN_HEIGHT).build();
        next.active = this.page < maxPage && !this.loading && !this.results.isEmpty();
        this.addRenderableWidget(next);

        int checkboxW = (int) (w * 0.5);
        int backW = w - checkboxW - NAV_GAP - FAVORITES_BTN_WIDTH - NAV_GAP;

        this.onlineCheckbox = new Checkbox(
                this.leftPos, bottomRowY,
                checkboxW, BTN_HEIGHT,
                Component.translatable("gui.netmusicradio.search.online_search"),
                this.onlineSearch);
        this.addRenderableWidget(this.onlineCheckbox);

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.netmusic.big_megaphone.back"),
                b -> this.onClose())
                .pos(this.leftPos + checkboxW + NAV_GAP, bottomRowY).size(backW, BTN_HEIGHT).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.netmusicradio.favorites.title"),
                b -> openFavoritesScreen())
                .pos(this.leftPos + checkboxW + NAV_GAP + backW + NAV_GAP, bottomRowY)
                .size(FAVORITES_BTN_WIDTH, BTN_HEIGHT).build());
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

    // ===== 搜索 =====

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
            searchOnline(q, country);
        } else {
            searchLocal(q, country);
        }
    }

    private void searchOnline(String q, String country) {
        new Thread(() -> {
            List<Station> found = RadioBrowserClient.search(q, country, SEARCH_RESULT_LIMIT);
            List<Station> filtered = filterValidStations(found);
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    this.loading = false;
                    this.results = filtered;
                    this.statusMessage = this.results.isEmpty()
                            ? Component.translatable("gui.netmusicradio.search.no_results").getString()
                            : Component.translatable("gui.netmusicradio.search.found", this.results.size()).getString();
                    rebuildListButtons();
                });
            }
        }, "NetMusic-RadioSearch").start();
    }

    private void searchLocal(String q, String country) {
        if (!StationDatabase.isLoaded()) {
            StationDatabase.ensureLoadedAsync();
            this.statusMessage = Component.translatable("gui.netmusicradio.search.loading_local").getString();
            this.loading = false;
            this.results.clear();
            this.page = 0;
            rebuildListButtons();
            return;
        }
        List<Station> found = StationDatabase.search(q, country, SEARCH_RESULT_LIMIT);
        List<Station> filtered = filterValidStations(found);
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

    private List<Station> filterValidStations(List<Station> stations) {
        List<Station> filtered = new ArrayList<>();
        for (Station s : stations) {
            if (s == null || s.url == null || s.url.isBlank()) continue;
            if (!BigMegaphoneUtilProxy.isValidStreamUrl(s.url)) continue;
            filtered.add(s);
        }
        return filtered;
    }

    // ===== 选中电台 =====

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

    private void toggleFavorite(Station station) {
        if (FavoritesManager.isFavorite(station)) {
            FavoritesManager.removeFavorite(station);
        } else {
            FavoritesManager.addFavorite(station);
        }
        rebuildListButtons();
    }

    private void openFavoritesScreen() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new FavoritesScreen(this));
        }
    }

    private int getHoveredItemIndex(double mouseX, double mouseY) {
        int listTop = getListTop();
        int listBottom = getListBottom();
        int itemH = getItemHeight();
        int pageSize = getPageSize();

        if (mouseY < listTop || mouseY >= listBottom) return -1;
        if (mouseX < this.leftPos || mouseX >= this.leftPos + getPanelWidth()) return -1;

        int relY = (int) mouseY - listTop;
        int index = relY / itemH;
        if (index < 0 || index >= pageSize) return -1;

        int start = this.page * pageSize;
        if (start + index >= this.results.size()) return -1;

        return index;
    }

    // ===== 事件处理 =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dropdown.isOpen()) {
            if (dropdown.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            syncOnlineCheckbox();
            return super.mouseClicked(mouseX, mouseY, button);
        }
        syncOnlineCheckbox();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (dropdown.isOpen() && dropdown.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        if (this.results.isEmpty()) return false;
        int maxPage = getMaxPage();
        if (delta > 0 && this.page > 0) {
            this.page--;
            rebuildListButtons();
            return true;
        } else if (delta < 0 && this.page < maxPage) {
            this.page++;
            rebuildListButtons();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (dropdown.isOpen() && dropdown.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (this.getFocused() == this.pageField && (keyCode == 257 || keyCode == 335)) {
            jumpToPage();
            return true;
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

    private void jumpToPage() {
        if (this.pageField == null) return;
        String value = this.pageField.getValue().trim();
        if (value.isEmpty()) return;
        try {
            int target = Integer.parseInt(value) - 1;
            int maxPage = getMaxPage();
            if (target < 0) target = 0;
            if (target > maxPage) target = maxPage;
            this.page = target;
        } catch (NumberFormatException ignored) {
        }
        this.pageField.setValue("");
        rebuildListButtons();
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (dropdown.isOpen()) {
            boolean result = super.charTyped(codePoint, modifiers);
            dropdown.updateFiltered();
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

    // ===== 渲染 =====

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        this.hoveredItemIndex = getHoveredItemIndex(mouseX, mouseY);
        for (int i = 0; i < this.favoriteButtons.size(); i++) {
            Button btn = this.favoriteButtons.get(i);
            Station station = this.favoriteButtonStations.get(i);
            boolean isFav = FavoritesManager.isFavorite(station);
            btn.visible = isFav || i == this.hoveredItemIndex;
        }

        renderBars(graphics);
        renderList(graphics);

        super.render(graphics, mouseX, mouseY, partialTicks);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);

        if (!this.statusMessage.isBlank()) {
            graphics.drawCenteredString(this.font, this.statusMessage, this.width / 2, SUBTITLE_Y, 0xCCCCCC);
        }

        dropdown.render(graphics, mouseX, mouseY, partialTicks);
    }

    private void renderBars(GuiGraphics graphics) {
        int h = getPanelHeight();

        graphics.fill(0, 0, this.width, TITLE_BAR_HEIGHT, 0xFF333333);
        graphics.fill(0, TITLE_BAR_HEIGHT, this.width, TITLE_BAR_HEIGHT + 1, 0xFF555555);

        graphics.fill(0, h - FOOTER_HEIGHT, this.width, h, 0xFF333333);
        graphics.fill(0, h - FOOTER_HEIGHT - 1, this.width, h - FOOTER_HEIGHT, 0xFF555555);
    }

    private void renderList(GuiGraphics graphics) {
        int w = getPanelWidth();
        int lx = this.leftPos;
        int listTop = getListTop();
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
            renderStationLogo(graphics, station, logoX, logoY, logoS);
            renderStationInfo(graphics, station, lx, y, w, logoS, itemH);
        }
    }

    private void renderStationLogo(GuiGraphics graphics, Station station, int logoX, int logoY, int logoS) {
        String logoUrl = station.logoUrl;
        if (logoUrl != null && !logoUrl.isBlank()) {
            ResourceLocation logoRl = LogoManager.getInstance().getLogo(logoUrl);
            if (logoRl != null) {
                graphics.blit(logoRl, logoX, logoY, logoS, logoS, 0, 0,
                        LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE);
            } else {
                LogoManager.getInstance().loadLogo(logoUrl);
                graphics.fill(logoX, logoY, logoX + logoS, logoY + logoS, 0xFF222222);
            }
        } else {
            graphics.fill(logoX, logoY, logoX + logoS, logoY + logoS, 0xFF222222);
        }
    }

    private void renderStationInfo(GuiGraphics graphics, Station station, int lx, int y, int w, int logoS, int itemH) {
        int textX = lx + 4 + logoS + 4;
        int maxLen = (w - logoS - LOGO_TEXT_PAD) / CHAR_WIDTH_ESTIMATE;

        String name = station.name == null || station.name.isBlank() ? station.url : station.name;
        String displayName = name.length() > maxLen ? name.substring(0, Math.max(0, maxLen - 3)) + "..." : name;
        graphics.drawString(this.font, displayName, textX, y + (itemH / 2) - 7, 0xFFFFFF);

        String country = station.country != null ? station.country : "";
        String desc = station.description != null ? station.description : "";
        String secondary = !desc.isBlank() ? desc : (!country.isBlank() ? country :
                Component.translatable("gui.netmusicradio.search.unknown_region").getString());
        if (secondary.length() > maxLen) {
            secondary = secondary.substring(0, Math.max(0, maxLen - 3)) + "...";
        }
        graphics.drawString(this.font, secondary, textX, y + (itemH / 2) + 5, 0xAAAAAA);
    }

    // ===== tick =====

    @Override
    public void tick() {
        super.tick();
        LogoManager.getInstance().tick();
        if (this.autoSearchPending && StationDatabase.isLoaded()) {
            this.autoSearchPending = false;
            tryAutoSelectCountry();
        }
        if (!this.onlineSearch
                && this.statusMessage.equals(Component.translatable("gui.netmusicradio.search.loading_local").getString())
                && StationDatabase.isLoaded()) {
            this.statusMessage = "";
            rebuildListButtons();
        }
        dropdown.tick();
    }

    private void tryAutoSelectCountry() {
        String systemCountry = Locale.getDefault().getDisplayCountry(Locale.ENGLISH);
        if (systemCountry == null || systemCountry.isBlank()) return;

        List<String> available = StationDatabase.getCountries();
        String matched = null;
        String systemLower = systemCountry.toLowerCase(Locale.ROOT);

        for (String c : available) {
            String cLower = c.toLowerCase(Locale.ROOT);
            if (cLower.equals(systemLower)) {
                matched = c;
                break;
            }
        }

        if (matched == null) {
            for (String c : available) {
                String cLower = c.toLowerCase(Locale.ROOT);
                if (cLower.contains(systemLower) || systemLower.contains(cLower)) {
                    matched = c;
                    break;
                }
            }
        }

        if (matched != null) {
            this.selectedCountry = matched;
            doSearch();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===== 国家/地区下拉菜单 =====

    private class CountryDropdown {
        private static final int ITEM_HEIGHT = 18;
        private static final int VISIBLE_ITEMS = 6;
        private static final int SEARCH_HEIGHT = 24;
        private static final int DROPDOWN_OFFSET_Y = 2;

        private boolean open = false;
        private int scrollOffset = 0;
        private boolean countriesLoaded = false;
        private List<String> filteredCountries = new ArrayList<>();
        private EditBox searchField;
        private int clearBtnX;
        private int clearBtnY;
        private int clearBtnW;
        private int clearBtnH;
        private String clearBtnText = "";

        boolean isOpen() {
            return open;
        }

        String getButtonLabel() {
            if (selectedCountry.isBlank()) {
                return Component.translatable("gui.netmusicradio.search.country").getString();
            }
            return selectedCountry;
        }

        void toggle() {
            if (open) {
                close();
            } else {
                openDropdown();
            }
        }

        void openDropdown() {
            this.open = true;
            this.scrollOffset = 0;
            this.countriesLoaded = StationDatabase.isLoaded();
            if (!StationDatabase.isLoaded()) {
                StationDatabase.ensureLoadedAsync();
            }
            this.filteredCountries = new ArrayList<>(StationDatabase.getCountries());
            rebuildListButtons();
            setFocused(this.searchField);
        }

        void close() {
            this.open = false;
            this.countriesLoaded = false;
            this.searchField = null;
            this.scrollOffset = 0;
            this.filteredCountries.clear();
            setFocused(RadioSearchScreen.this.searchField);
            rebuildListButtons();
        }

        void rebuildWidgets() {
            if (!open) return;
            int dropdownX = countryBtn.getX();
            int dropdownY = countryBtn.getY() + countryBtn.getHeight() + DROPDOWN_OFFSET_Y;
            int dropdownW = countryBtn.getWidth();
            clearBtnText = Component.translatable("gui.netmusicradio.search.country.clear").getString();
            clearBtnW = font.width(clearBtnText) + 8;
            clearBtnH = SEARCH_HEIGHT - 4;
            clearBtnX = dropdownX + dropdownW - clearBtnW - 2;
            clearBtnY = dropdownY + 2;

            if (searchField == null) {
                searchField = new EditBox(font, dropdownX + 2, dropdownY + 2,
                        dropdownW - clearBtnW - 6, SEARCH_HEIGHT - 4,
                        Component.translatable("gui.netmusicradio.search.country.hint"));
                searchField.setMaxLength(64);
            } else {
                searchField.setX(dropdownX + 2);
                searchField.setY(dropdownY + 2);
                searchField.setWidth(dropdownW - clearBtnW - 6);
            }
            searchField.visible = true;
        }

        void clear() {
            selectedCountry = "";
            if (searchField != null) {
                searchField.setValue("");
                updateFiltered();
            }
        }

        void select(String country) {
            selectedCountry = (country == null || country.isBlank()) ? "" : country;
            close();
            syncOnlineCheckbox();
        }

        void updateFiltered() {
            if (!open) return;
            String query = searchField != null ? searchField.getValue().trim().toLowerCase() : "";
            List<String> all = StationDatabase.getCountries();
            if (query.isBlank()) {
                filteredCountries = new ArrayList<>(all);
            } else {
                List<String> filtered = new ArrayList<>();
                for (String c : all) {
                    if (c.toLowerCase().contains(query)) filtered.add(c);
                }
                filteredCountries = filtered;
            }
            scrollOffset = 0;
        }

        int getHeight() {
            return SEARCH_HEIGHT + VISIBLE_ITEMS * ITEM_HEIGHT + DROPDOWN_OFFSET_Y;
        }

        private int[] getBounds() {
            return new int[]{
                    countryBtn.getX(),
                    countryBtn.getY() + countryBtn.getHeight() + DROPDOWN_OFFSET_Y,
                    countryBtn.getWidth(),
                    getHeight()
            };
        }

        private boolean isClearBtnHovered(double mouseX, double mouseY) {
            return !selectedCountry.isBlank()
                    && mouseX >= clearBtnX && mouseX <= clearBtnX + clearBtnW
                    && mouseY >= clearBtnY && mouseY <= clearBtnY + clearBtnH;
        }

        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!open) return false;
            int[] b = getBounds();
            int dx = b[0], dy = b[1], dw = b[2], dh = b[3];

            if (mouseX < dx || mouseX > dx + dw || mouseY < dy || mouseY > dy + dh) {
                close();
                syncOnlineCheckbox();
                return false;
            }

            int searchBottom = dy + SEARCH_HEIGHT;
            if (mouseY >= dy && mouseY <= searchBottom) {
                if (isClearBtnHovered(mouseX, mouseY)) {
                    clear();
                    return true;
                }
                if (searchField != null) {
                    setFocused(searchField);
                    return searchField.mouseClicked(mouseX, mouseY, button);
                }
                return true;
            }

            int relY = (int) mouseY - dy - SEARCH_HEIGHT;
            if (relY >= 0) {
                int itemIndex = relY / ITEM_HEIGHT + scrollOffset;
                if (itemIndex >= 0 && itemIndex < filteredCountries.size()) {
                    select(filteredCountries.get(itemIndex));
                    return true;
                }
            }
            return true;
        }

        boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (!open) return false;
            int[] b = getBounds();
            if (mouseX < b[0] || mouseX > b[0] + b[2] || mouseY < b[1] || mouseY > b[1] + b[3]) {
                return false;
            }
            int maxScroll = Math.max(0, filteredCountries.size() - VISIBLE_ITEMS);
            if (delta > 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            } else if (delta < 0 && scrollOffset < maxScroll) {
                scrollOffset++;
                return true;
            }
            return false;
        }

        boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!open) return false;
            if (keyCode == 266) {
                if (scrollOffset > 0) {
                    scrollOffset--;
                    return true;
                }
            } else if (keyCode == 267) {
                int maxScroll = Math.max(0, filteredCountries.size() - VISIBLE_ITEMS);
                if (scrollOffset < maxScroll) {
                    scrollOffset++;
                    return true;
                }
            } else if (keyCode == 268) {
                close();
                return true;
            }
            if (searchField != null && getFocused() == searchField) {
                if (searchField.keyPressed(keyCode, scanCode, modifiers)) {
                    updateFiltered();
                    return true;
                }
            }
            return false;
        }

        void tick() {
            if (open && StationDatabase.isLoaded() && !countriesLoaded) {
                countriesLoaded = true;
                filteredCountries = new ArrayList<>(StationDatabase.getCountries());
                if (searchField != null) updateFiltered();
            }
        }

        void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if (!open) return;

            int[] b = getBounds();
            int dx = b[0], dy = b[1], dw = b[2], dh = b[3];

            graphics.fill(dx, dy, dx + dw, dy + dh, 0xFF252525);
            graphics.fill(dx, dy, dx + dw, dy + 1, 0xFF555555);
            graphics.fill(dx, dy + dh - 1, dx + dw, dy + dh, 0xFF555555);

            if (searchField != null) {
                searchField.render(graphics, mouseX, mouseY, partialTicks);
            }

            if (!selectedCountry.isBlank()) {
                boolean hovered = isClearBtnHovered(mouseX, mouseY);
                int bgColor = hovered ? 0xFF555555 : 0xFF333333;
                graphics.fill(clearBtnX, clearBtnY, clearBtnX + clearBtnW, clearBtnY + clearBtnH, bgColor);
                graphics.drawCenteredString(font, clearBtnText,
                        clearBtnX + clearBtnW / 2, clearBtnY + (clearBtnH - FONT_HEIGHT) / 2, 0xFFFFFF);
            }

            int listStartY = dy + SEARCH_HEIGHT;
            int visibleCount = Math.min(VISIBLE_ITEMS, filteredCountries.size());

            for (int i = 0; i < visibleCount; i++) {
                int idx = i + scrollOffset;
                if (idx >= filteredCountries.size()) break;
                String countryName = filteredCountries.get(idx);
                int itemY = listStartY + i * ITEM_HEIGHT;

                boolean selected = countryName.equals(selectedCountry);
                if (selected) {
                    graphics.fill(dx + 1, itemY, dx + dw - 1, itemY + ITEM_HEIGHT - 1, 0xFF3A5A3A);
                }

                int maxLen = (dw - 10) / CHAR_WIDTH_ESTIMATE;
                String display = countryName.length() > maxLen
                        ? countryName.substring(0, Math.max(0, maxLen - 3)) + "..."
                        : countryName;
                graphics.drawString(font, display, dx + 4, itemY + (ITEM_HEIGHT - FONT_HEIGHT) / 2,
                        selected ? 0xFFFFFF : 0xD0D0D0);
            }

            if (filteredCountries.isEmpty()) {
                if (!StationDatabase.isLoaded()) {
                    String loading = Component.translatable("gui.netmusicradio.search.loading_local").getString();
                    graphics.drawString(font, loading, dx + 4, listStartY + 4, 0x888888);
                } else {
                    String noResult = Component.translatable("gui.netmusicradio.search.no_results").getString();
                    graphics.drawString(font, noResult, dx + 4, listStartY + 4, 0x888888);
                }
            }

            if (filteredCountries.size() > VISIBLE_ITEMS) {
                int trackH = VISIBLE_ITEMS * ITEM_HEIGHT;
                graphics.fill(dx + dw - 3, listStartY, dx + dw - 1, listStartY + trackH, 0xFF1A1A1A);
                int thumbH = Math.max(4, trackH * VISIBLE_ITEMS / filteredCountries.size());
                int thumbY = listStartY + (scrollOffset * ITEM_HEIGHT * VISIBLE_ITEMS) / filteredCountries.size();
                thumbY = Math.min(thumbY, listStartY + trackH - thumbH);
                graphics.fill(dx + dw - 3, thumbY, dx + dw - 1, thumbY + thumbH, 0xFF666666);
            }
        }
    }
}