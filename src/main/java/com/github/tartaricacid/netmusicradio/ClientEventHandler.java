package com.github.tartaricacid.netmusicradio;

import com.github.tartaricacid.netmusic.client.gui.BigMegaphonePresetPickerScreen;
import com.github.tartaricacid.netmusic.client.gui.BigMegaphoneScreen;
import com.github.tartaricacid.netmusicradio.client.gui.CustomBigMegaphoneScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端事件处理器
 * <p>
 * 功能：
 * 1. 将 BigMegaphoneScreen 替换为自定义的 CustomBigMegaphoneScreen
 * 2. 将 BigMegaphonePresetPickerScreen 替换为自定义的搜索界面
 * 3. 支持任意 HTTP(S) 音频流（包括 Shoutcast/Icecast 流）
 * <p>
 * 修复：
 * - 使用 BlockPos 映射确保搜索屏幕正确传回链接
 * - 改进屏幕替换逻辑
 */
@Mod.EventBusSubscriber(modid = NetMusicRadioAddon.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEventHandler {
    public static final Logger LOGGER = LogManager.getLogger(NetMusicRadioAddon.MODID);

    private static final Map<BlockPos, CustomBigMegaphoneScreen> SCREEN_MAP = new HashMap<>();

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        try {
            Screen screen = event.getScreen();
            if (screen == null) {
                return;
            }

            if (screen instanceof CustomBigMegaphoneScreen) {
                return;
            }

            if (screen instanceof BigMegaphoneScreen) {
                replaceWithCustomScreen((BigMegaphoneScreen) screen);
            } else if (screen instanceof BigMegaphonePresetPickerScreen) {
                replacePresetPickerScreen((BigMegaphonePresetPickerScreen) screen);
            }
        } catch (Throwable t) {
            LOGGER.error("Error in onScreenInit", t);
        }
    }

    private static void replaceWithCustomScreen(BigMegaphoneScreen original) {
        try {
            BlockPos blockPos = getBlockPos(original);
            if (blockPos == null) {
                LOGGER.warn("Failed to get blockPos from BigMegaphoneScreen");
                return;
            }

            CustomBigMegaphoneScreen customScreen = new CustomBigMegaphoneScreen(blockPos);
            SCREEN_MAP.put(blockPos, customScreen);
            Minecraft.getInstance().setScreen(customScreen);
            LOGGER.info("Replaced BigMegaphoneScreen with CustomBigMegaphoneScreen at {}", blockPos);
        } catch (Throwable t) {
            LOGGER.error("Failed to replace BigMegaphoneScreen", t);
        }
    }

    private static BlockPos getBlockPos(BigMegaphoneScreen screen) {
        try {
            var field = BigMegaphoneScreen.class.getDeclaredField("blockPos");
            field.setAccessible(true);
            return (BlockPos) field.get(screen);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to get blockPos from BigMegaphoneScreen", e);
            return null;
        }
    }

    private static void replacePresetPickerScreen(BigMegaphonePresetPickerScreen pickerScreen) {
        try {
            Screen parent = getParentScreen(pickerScreen);
            BlockPos blockPos = findBlockPosFromParent(parent);

            CustomBigMegaphoneScreen customScreen = null;
            if (blockPos != null) {
                customScreen = SCREEN_MAP.get(blockPos);
            }

            if (customScreen == null && parent instanceof BigMegaphoneScreen) {
                BlockPos parentPos = getBlockPos((BigMegaphoneScreen) parent);
                if (parentPos != null) {
                    customScreen = SCREEN_MAP.get(parentPos);
                }
            }

            if (customScreen == null && blockPos != null) {
                customScreen = new CustomBigMegaphoneScreen(blockPos);
                SCREEN_MAP.put(blockPos, customScreen);
            }

            Screen searchParent = customScreen != null ? customScreen : parent;

            Minecraft.getInstance().setScreen(
                    new com.github.tartaricacid.netmusicradio.client.RadioSearchScreen(searchParent));
            LOGGER.info("Replaced BigMegaphonePresetPickerScreen with RadioSearchScreen");
        } catch (Throwable t) {
            LOGGER.error("Failed to replace preset picker", t);
        }
    }

    private static Screen getParentScreen(BigMegaphonePresetPickerScreen pickerScreen) {
        try {
            var field = pickerScreen.getClass().getDeclaredField("parent");
            field.setAccessible(true);
            Object parent = field.get(pickerScreen);
            if (parent instanceof Screen screen) {
                return screen;
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("Failed to get parent from BigMegaphonePresetPickerScreen", e);
        }
        return null;
    }

    private static BlockPos findBlockPosFromParent(Screen parent) {
        if (parent instanceof CustomBigMegaphoneScreen screen) {
            return screen.getBlockPos();
        }
        if (parent instanceof BigMegaphoneScreen screen) {
            return getBlockPos(screen);
        }
        return null;
    }

    public static CustomBigMegaphoneScreen getScreenForBlockPos(BlockPos pos) {
        return SCREEN_MAP.get(pos);
    }
}