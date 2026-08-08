package org.eu.sager.netmusicradio.client;

import org.eu.sager.netmusicradio.client.RadioBrowserClient.Station;
import org.eu.sager.netmusicradio.client.gui.CustomBigMegaphoneScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class FavoritesScreen extends Screen {
    private static final int MARGIN = 12;
    private static final int MAX_CONTENT_WIDTH = 400;
    private static final int TITLE_BAR_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 58;
    private static final int TITLE_Y = 6;
    private static final int MIN_ITEM_HEIGHT = 28;
    private static final int MAX_ITEM_HEIGHT = 36;
    private static final int MIN_LOGO_SIZE = 18;
    private static final int MAX_LOGO_SIZE = 26;
    private static final int BTN_HEIGHT = 20;
    private static final int ADD_BTN_WIDTH = 42;
    private static final int ADD_BTN_HEIGHT = 18;
    private static final int ADD_BTN_RIGHT_PAD = 44;
    private static final int FAVORITE_BTN_WIDTH = 20;
    private static final int FAVORITE_BTN_HEIGHT = 18;
    private static final int LOGO_TEXTURE_SIZE = 64;
    private static final int FONT_HEIGHT = 9;
    private static final int CHAR_WIDTH_ESTIMATE = 6;
    private static final int LOGO_TEXT_PAD = 20;
    private static final int NAV_GAP = 4;

    private final Screen parentScreen;
    private int leftPos;
    private int topPos;
    private int page = 0;

    private EditBox pageField;
    private List<Station> results = new ArrayList<>();
    private final List<Button> favoriteButtons = new ArrayList<>();
    private final List<Station> favoriteButtonStations = new ArrayList<>();

    public FavoritesScreen(Screen parentScreen) {
        super(Component.translatable("gui.netmusicradio.favorites.title"));
        this.parentScreen = parentScreen;
    }

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

    @Override
    protected void init() {
        this.leftPos = getContentLeft();
        this.topPos = 0;
        this.results = new ArrayList<>(FavoritesManager.getFavorites());
        rebuildListButtons();
    }

    private void rebuildListButtons() {
        this.clearWidgets();
        this.favoriteButtons.clear();
        this.favoriteButtonStations.clear();
        rebuildListItems();
        rebuildNav();
    }

    private void rebuildListItems() {
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

            int favBtnX = this.leftPos + w - ADD_BTN_RIGHT_PAD - FAVORITE_BTN_WIDTH - NAV_GAP;
            Button favoriteBtn = Button.builder(
                    Component.literal("\u2605"),
                    b -> removeFavorite(station))
                    .pos(favBtnX, y + (itemH - FAVORITE_BTN_HEIGHT) / 2)
                    .size(FAVORITE_BTN_WIDTH, FAVORITE_BTN_HEIGHT).build();
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
        previous.active = this.page > 0 && !this.results.isEmpty();
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
        next.active = this.page < maxPage && !this.results.isEmpty();
        this.addRenderableWidget(next);

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.netmusic.big_megaphone.back"),
                b -> this.onClose())
                .pos(this.leftPos, bottomRowY).size(w, BTN_HEIGHT).build());
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

    private void selectStation(Station station) {
        String candidate = station.url == null ? "" : station.url.trim();
        if (candidate.isBlank()) return;

        String displayName = (station.name == null || station.name.isBlank()) ? candidate : station.name;

        CustomBigMegaphoneScreen targetScreen = findBigMegaphoneScreen();

        if (targetScreen != null) {
            targetScreen.setStation(displayName, candidate);
        }

        if (this.minecraft != null) {
            this.minecraft.setScreen(targetScreen != null ? targetScreen : this.parentScreen);
        }
    }

    private CustomBigMegaphoneScreen findBigMegaphoneScreen() {
        if (parentScreen instanceof CustomBigMegaphoneScreen screen) {
            return screen;
        }
        if (parentScreen instanceof RadioSearchScreen searchScreen && searchScreen.getParentScreen() instanceof CustomBigMegaphoneScreen screen) {
            return screen;
        }
        return null;
    }

    private void removeFavorite(Station station) {
        FavoritesManager.removeFavorite(station);
        this.results = new ArrayList<>(FavoritesManager.getFavorites());
        int maxPage = getMaxPage();
        if (this.page > maxPage) this.page = Math.max(0, maxPage);
        rebuildListButtons();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
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
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        renderBars(graphics);
        renderList(graphics);

        super.render(graphics, mouseX, mouseY, partialTicks);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);

        if (this.results.isEmpty()) {
            String emptyMsg = Component.translatable("gui.netmusicradio.favorites.empty").getString();
            graphics.drawCenteredString(this.font, emptyMsg, this.width / 2,
                    (getListTop() + getListBottom()) / 2 - 4, 0x888888);
        }
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
}