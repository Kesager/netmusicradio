package com.github.tartaricacid.netmusicradio.client.api;

import com.github.tartaricacid.netmusic.client.api.IAudioStreamHandler;
import com.github.tartaricacid.netmusicradio.NetMusicRadioAddon;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class AudioStreamHandlerRegistrar {
    private static boolean registered = false;

    private AudioStreamHandlerRegistrar() {
    }

    public static synchronized void registerAll() {
        if (registered) {
            return;
        }
        registered = true;

        try {
            Class<?> managerClass = Class.forName(
                    "com.github.tartaricacid.netmusic.client.api.AudioStreamHandlerManager"
            );
            forceRegisterHandler(managerClass, new ShoutcastStreamHandler());
            NetMusicRadioAddon.LOGGER.info("Registered ShoutcastStreamHandler successfully");
        } catch (Throwable t) {
            NetMusicRadioAddon.LOGGER.error("Failed to register audio stream handlers", t);
        }
    }

    private static void forceRegisterHandler(Class<?> managerClass, IAudioStreamHandler handler) throws Exception {
        Field handlersField = managerClass.getDeclaredField("HANDLERS");
        handlersField.setAccessible(true);

        Object currentHandlers = handlersField.get(null);

        List<IAudioStreamHandler> newList = new ArrayList<>();
        if (currentHandlers instanceof List) {
            @SuppressWarnings("unchecked")
            List<IAudioStreamHandler> existing = (List<IAudioStreamHandler>) currentHandlers;
            newList.addAll(existing);
        }

        newList.add(handler);
        newList.sort((h1, h2) -> Integer.compare(h2.getPriority(), h1.getPriority()));

        handlersField.set(null, new ArrayList<>(newList));

        NetMusicRadioAddon.LOGGER.debug("Force-registered handler: {} with priority: {}",
                handler.getClass().getSimpleName(), handler.getPriority());
    }

    public static boolean isRegistered() {
        return registered;
    }
}