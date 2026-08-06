package com.github.tartaricacid.netmusicradio;

import com.github.tartaricacid.netmusic.client.gui.BigMegaphonePresetPickerScreen;
import com.github.tartaricacid.netmusic.client.gui.BigMegaphoneScreen;
import com.github.tartaricacid.netmusicradio.client.RadioSearchScreen;
import com.github.tartaricacid.netmusicradio.network.message.ApplyBigMegaphoneConfigMessage;
import com.github.tartaricacid.netmusicradio.network.message.ApplyBigMegaphoneConfigMessage.Action;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(modid = NetMusicRadioAddon.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEventHandler {
    private static final Logger LOGGER = LogManager.getLogger(NetMusicRadioAddon.MODID);

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        try {
            Screen screen = event.getScreen();
            if (screen == null) {
                LOGGER.warn("Screen init event fired without a screen object: {}", event.getClass().getName());
                return;
            }
            LOGGER.info("Screen init event fired: {} screen={}", event.getClass().getName(), screen.getClass().getName());
            if (screen instanceof BigMegaphonePresetPickerScreen) {
                replacePresetPickerScreen((BigMegaphonePresetPickerScreen) screen);
            } else if (screen instanceof BigMegaphoneScreen) {
                handleBigMegaphoneScreen((BigMegaphoneScreen) screen);
            }
        } catch (Throwable t) {
            LOGGER.error("Error in onScreenInit", t);
        }
    }

    private static void replacePresetPickerScreen(BigMegaphonePresetPickerScreen pickerScreen) {
        try {
            var parentField = pickerScreen.getClass().getDeclaredField("parent");
            parentField.setAccessible(true);
            Object parent = parentField.get(pickerScreen);
            if (parent instanceof Screen screen) {
                Minecraft.getInstance().setScreen(new RadioSearchScreen(screen));
                LOGGER.info("Replaced BigMegaphonePresetPickerScreen with RadioSearchScreen");
            } else {
                LOGGER.warn("Preset picker parent is not a Screen: {}", parent == null ? "null" : parent.getClass().getName());
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to replace preset picker with radio search", t);
        }
    }

    private static void handleBigMegaphoneScreen(BigMegaphoneScreen screen) {
        try {
            int leftPos = getPrivateField(screen, "leftPos", int.class);
            int topPos = getPrivateField(screen, "topPos", int.class);
            int presetY = topPos + 139;
            Button presetButton = findButtonAt(screen, leftPos, presetY);
            if (presetButton != null) {
                LOGGER.info("Found BigMegaphone preset button at {}x{}", leftPos, presetY);
                replaceButtonAction(screen, presetButton, b -> openRadioSearch(screen));
                LOGGER.info("Replaced BigMegaphone preset button action with radio search");
            } else {
                LOGGER.warn("Could not find BigMegaphone preset button to override, adding radio search button instead");
                addSearchButton(screen, leftPos, presetY);
            }

            int saveY = topPos + 114;
            Button saveButton = findButtonAt(screen, leftPos, saveY);
            if (saveButton != null) {
                replaceButtonAction(screen, saveButton, b -> sendCustomBigMegaphoneAction(screen, Action.SAVE));
                LOGGER.info("Patched BigMegaphone save button to bypass suffix restriction");
            }

            Button startButton = findButtonAt(screen, leftPos + 82, saveY);
            if (startButton != null) {
                replaceButtonAction(screen, startButton, b -> sendCustomBigMegaphoneAction(screen, Action.START));
                LOGGER.info("Patched BigMegaphone start button to bypass suffix restriction");
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to patch BigMegaphoneScreen controls", t);
        }
    }

    private static void sendCustomBigMegaphoneAction(Screen screen, Action action) {
        try {
            BlockPos blockPos = getPrivateField(screen, "blockPos", BlockPos.class);
            Object urlField = getPrivateField(screen, "urlTextField", Object.class);
            Object nameField = getPrivateField(screen, "nameTextField", Object.class);
            Object rangeSlider = getPrivateField(screen, "rangeSlider", Object.class);

            String url = "";
            String name = "";
            int range = 32;

            if (urlField instanceof net.minecraft.client.gui.components.EditBox editBox) {
                url = editBox.getValue().trim();
            }
            if (nameField instanceof net.minecraft.client.gui.components.EditBox editBox) {
                name = editBox.getValue().trim();
            }
            if (rangeSlider != null) {
                try {
                    var method = rangeSlider.getClass().getMethod("getCurrentRange");
                    range = (Integer) method.invoke(rangeSlider);
                } catch (Exception ignored) {
                }
            }

            if (action != Action.STOP) {
                if (url.isBlank() || name.isBlank()) {
                    return;
                }
            }

            ApplyBigMegaphoneConfigMessage message = new ApplyBigMegaphoneConfigMessage(blockPos, url, name, range, action);
            com.github.tartaricacid.netmusicradio.network.NetMusicRadioNetwork.INSTANCE.sendToServer(message);
        } catch (Throwable t) {
            LOGGER.error("Failed to send custom BigMegaphone action", t);
        }
    }

    private static void openRadioSearch(Screen parent) {
        Minecraft.getInstance().setScreen(new RadioSearchScreen(parent));
    }

    private static Button findButtonAt(Screen screen, int x, int y) {
        try {
            var childrenMethod = Screen.class.getMethod("children");
            var children = (java.util.List<?>) childrenMethod.invoke(screen);
            for (Object child : children) {
                if (child instanceof Button button) {
                    if (isButtonAt(button, x, y)) {
                        return button;
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Error while searching for BigMegaphone preset button", t);
        }
        return null;
    }

    private static boolean isButtonAt(Button button, int x, int y) {
        return Math.abs(button.getX() - x) <= 1 && Math.abs(button.getY() - y) <= 1;
    }

    private static void replaceButtonAction(Screen screen, Button button, Button.OnPress onPress) {
        try {
            Field onPressField = findFieldInHierarchy(button.getClass(), "onPress");
            if (onPressField == null) {
                throw new NoSuchFieldException("onPress");
            }
            onPressField.setAccessible(true);
            onPressField.set(button, onPress);
        } catch (Throwable t) {
            LOGGER.error("Failed to set button onPress, adding supplementary radio search button instead", t);
            addSearchButton(screen, button.getX(), button.getY() + 24);
        }
    }

    private static Field findFieldInHierarchy(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static void addSearchButton(Screen screen, int x, int y) {
        try {
            Button searchButton = Button.builder(Component.literal("Radio Search"), btn -> openRadioSearch(screen))
                    .bounds(x, y, 240, 20)
                    .build();
            var addWidget = Screen.class.getDeclaredMethod("addRenderableWidget", GuiEventListener.class);
            addWidget.setAccessible(true);
            addWidget.invoke(screen, searchButton);
        } catch (Throwable t) {
            LOGGER.error("Failed to add radio search button to BigMegaphoneScreen", t);
        }
    }

    private static <T> T getPrivateField(Object instance, String fieldName, Class<T> type) throws ReflectiveOperationException {
        var field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return type.cast(field.get(instance));
    }
}

