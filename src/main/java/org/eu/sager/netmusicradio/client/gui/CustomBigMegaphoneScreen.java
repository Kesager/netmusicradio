package org.eu.sager.netmusicradio.client.gui;

import com.github.tartaricacid.netmusic.config.GeneralConfig;
import com.github.tartaricacid.netmusic.tileentity.TileEntityBigMegaphone;
import org.eu.sager.netmusicradio.client.RadioSearchScreen;
import org.eu.sager.netmusicradio.client.util.BigMegaphoneUtilProxy;
import org.eu.sager.netmusicradio.network.NetMusicRadioNetwork;
import org.eu.sager.netmusicradio.network.message.ApplyBigMegaphoneConfigMessage;
import org.eu.sager.netmusicradio.network.message.ApplyBigMegaphoneConfigMessage.Action;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 自定义的 BigMegaphone 屏幕
 * <p>
 * 完全独立于 BigMegaphoneScreen 的实现，不继承父类，
 * 移除 URL 验证限制，支持任意 HTTP(S) 音频流（包括 Shoutcast/Icecast）
 * <p>
 * 修复：
 * 1. 每次打开屏幕都强制从 TileEntity 读取 NBT 数据
 * 2. 保存后立即本地更新 UI，不等待服务端同步
 * 3. 移除 loadedFromBlockEntity 标志，确保数据一致性
 */
public class CustomBigMegaphoneScreen extends Screen {
    private static final int WIDTH = 240;

    private final BlockPos blockPos;

    private int leftPos;
    private int topPos;

    private EditBox urlTextField;
    private EditBox nameTextField;
    private CustomRangeSlider rangeSlider;

    private Component tips = Component.empty();

    private String pendingName = null;
    private String pendingUrl = null;

    public CustomBigMegaphoneScreen(BlockPos blockPos) {
        super(Component.translatable("block.netmusic.big_megaphone"));
        this.blockPos = blockPos;
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - WIDTH) / 2;
        this.topPos = (this.height - 180) / 2;

        this.initUrlEditBox();
        this.initNameEditBox();
        this.initRangeSlider(32);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusic.big_megaphone.presets"),
                        b -> this.openPresetPicker())
                .pos(this.leftPos, this.topPos + 139).size(WIDTH, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusic.big_megaphone.save"),
                        b -> this.sendAction(Action.SAVE))
                .pos(this.leftPos, this.topPos + 114).size(76, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusic.big_megaphone.start"),
                        b -> this.sendAction(Action.START))
                .pos(this.leftPos + 82, this.topPos + 114).size(76, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.netmusic.big_megaphone.stop"),
                        b -> this.sendAction(Action.STOP))
                .pos(this.leftPos + 164, this.topPos + 114).size(76, 20).build());

        this.loadFromBlockEntity();

        if (pendingName != null && pendingUrl != null) {
            this.nameTextField.setValue(pendingName);
            this.urlTextField.setValue(pendingUrl);
            this.pendingName = null;
            this.pendingUrl = null;
        }
    }

    private void initUrlEditBox() {
        this.urlTextField = new EditBox(this.font, this.leftPos, this.topPos + 14, WIDTH, 18,
                Component.literal("Megaphone URL Box"));
        this.urlTextField.setMaxLength(1024);
        this.urlTextField.setTextColor(0xF3EFE0);
        this.addRenderableWidget(this.urlTextField);
    }

    private void initNameEditBox() {
        this.nameTextField = new EditBox(this.font, this.leftPos, this.topPos + 37, WIDTH, 18,
                Component.literal("Megaphone Name Box"));
        this.nameTextField.setMaxLength(256);
        this.nameTextField.setTextColor(0xF3EFE0);
        this.addRenderableWidget(this.nameTextField);
    }

    private void initRangeSlider(int range) {
        int maxRange = Math.max(1, GeneralConfig.BIG_MEGAPHONE_MAX_RANGE.get());
        double value = maxRange == 1 ? 0 : (double) (Mth.clamp(range, 1, maxRange) - 1) / (maxRange - 1);
        this.rangeSlider = new CustomRangeSlider(this.leftPos, this.topPos + 60, WIDTH, 20, value, maxRange);
        this.addRenderableWidget(this.rangeSlider);
    }

    private void openPresetPicker() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new RadioSearchScreen(this));
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.urlTextField.tick();
        this.nameTextField.tick();

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !(level.getBlockEntity(this.blockPos) instanceof TileEntityBigMegaphone)) {
            this.onClose();
        }
    }

    /**
     * 从 TileEntity 读取 NBT 数据并填充到 UI
     * <p>
     * 每次调用都会强制读取最新数据，不使用缓存标志
     */
    private void loadFromBlockEntity() {
        Minecraft minecraft = this.getMinecraft();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        BlockEntity blockEntity = minecraft.level.getBlockEntity(this.blockPos);
        if (blockEntity instanceof TileEntityBigMegaphone megaphone) {
            this.urlTextField.setValue(megaphone.getStreamUrl());
            this.nameTextField.setValue(megaphone.getDisplayName());
            this.rangeSlider.setRange(megaphone.getMaxRange());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, this.tips, this.width / 2, this.topPos + 92, 0xCF0000);

        super.render(graphics, mouseX, mouseY, partialTicks);

        if (Util.isBlank(this.urlTextField.getValue()) && !this.urlTextField.isFocused()) {
            MutableComponent placeHolder = Component.translatable("gui.netmusic.big_megaphone.url.tips");
            graphics.drawString(this.font, placeHolder, this.leftPos + 5, this.topPos + 19, 0xaaaaaa, false);
        }

        if (Util.isBlank(this.nameTextField.getValue()) && !this.nameTextField.isFocused()) {
            MutableComponent placeHolder = Component.translatable("gui.netmusic.big_megaphone.name.tips");
            graphics.drawString(this.font, placeHolder, this.leftPos + 5, this.topPos + 42, 0xaaaaaa, false);
        }
    }

    /**
     * 发送操作到服务器
     * <p>
     * 对于 SAVE 和 START 操作，发送后立即本地更新 TileEntity 数据，
     * 确保即使服务器同步延迟，下次打开屏幕也能读取到正确的数据
     */
    private void sendAction(Action action) {
        this.tips = Component.empty();
        String url = this.urlTextField.getValue().trim();
        String name = this.nameTextField.getValue().trim();
        int range = this.rangeSlider.getCurrentRange();

        if (action != Action.STOP) {
            if (Util.isBlank(url)) {
                this.tips = Component.translatable("gui.netmusic.big_megaphone.url.empty");
                return;
            }
            if (!BigMegaphoneUtilProxy.isValidStreamUrl(url)) {
                this.tips = Component.translatable("gui.netmusic.big_megaphone.url.invalid");
                return;
            }
            if (Util.isBlank(name)) {
                this.tips = Component.translatable("gui.netmusic.big_megaphone.name.empty");
                return;
            }
        }

        ApplyBigMegaphoneConfigMessage message = new ApplyBigMegaphoneConfigMessage(
                this.blockPos, url, name, range, action);
        NetMusicRadioNetwork.INSTANCE.sendToServer(message);

        if (action == Action.SAVE || action == Action.START) {
            updateLocalTileEntity(url, name, range);
        }
    }

    /**
     * 本地更新 TileEntity 数据，确保 UI 状态与服务端一致
     */
    private void updateLocalTileEntity(String url, String name, int range) {
        Minecraft minecraft = this.getMinecraft();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        BlockEntity blockEntity = minecraft.level.getBlockEntity(this.blockPos);
        if (blockEntity instanceof TileEntityBigMegaphone megaphone) {
            megaphone.applyConfig(url, name, range);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.urlTextField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.urlTextField);
            return true;
        }
        if (this.nameTextField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.nameTextField);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.urlTextField.isFocused()) {
            return this.urlTextField.keyPressed(keyCode, scanCode, modifiers);
        }
        if (this.nameTextField.isFocused()) {
            return this.nameTextField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void setStation(String name, String url) {
        this.pendingName = name;
        this.pendingUrl = url;

        if (this.nameTextField != null) {
            this.nameTextField.setValue(name);
        }
        if (this.urlTextField != null) {
            this.urlTextField.setValue(url);
        }
    }

    private static class CustomRangeSlider extends AbstractSliderButton {
        private final int maxRange;

        protected CustomRangeSlider(int x, int y, int width, int height, double value, int maxRange) {
            super(x, y, width, height, Component.empty(), value);
            this.maxRange = maxRange;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("gui.netmusic.big_megaphone.range", this.getCurrentRange()));
        }

        @Override
        protected void applyValue() {
            this.updateMessage();
        }

        public int getCurrentRange() {
            if (this.maxRange <= 1) {
                return 1;
            }
            return Mth.clamp((int) Math.round(1 + this.value * (this.maxRange - 1)), 1, this.maxRange);
        }

        public void setRange(int range) {
            if (this.maxRange <= 1) {
                this.value = 0;
            } else {
                this.value = (double) (Mth.clamp(range, 1, this.maxRange) - 1) / (double) (this.maxRange - 1);
            }
            this.updateMessage();
        }
    }
}