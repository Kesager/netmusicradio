package com.github.tartaricacid.netmusicradio.client.util;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;

public final class BigMegaphoneUtilProxy {
    private static final Method remoteIsValid;

    static {
        Method m = null;
        try {
            Class<?> cls = Class.forName("com.github.tartaricacid.netmusic.util.BigMegaphoneUtil");
            m = cls.getMethod("isValidStreamUrl", String.class);
        } catch (Throwable ignored) {
        }
        remoteIsValid = m;
    }

    public static boolean isValidStreamUrl(String url) {
        boolean remoteChecked = false;
        try {
            if (remoteIsValid != null) {
                Boolean r = (Boolean) remoteIsValid.invoke(null, url);
                if (r != null && r) {
                    return true;
                }
                remoteChecked = true;
            }
        } catch (Throwable ignored) {
        }

        // Fallback: relaxed validation (accept common stream protocols and extensions).
        // If NetMusic's own validator rejects a non-extension streaming URL, still allow it when
        // the URL looks like a valid HTTP(S) address.
        try {
            if (url == null || url.isBlank()) return false;
            URL u = URI.create(url.trim()).toURL();
            String protocol = u.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol) && !"file".equalsIgnoreCase(protocol)) {
                return false;
            }
            String path = u.getPath();
            if (path != null) {
                String lower = path.toLowerCase();
                if (lower.endsWith(".m3u8") || lower.endsWith(".m3u") || lower.endsWith(".pls") || lower.endsWith(".mp3") || lower.endsWith(".aac") || lower.endsWith(".ogg") || lower.endsWith(".wav") || lower.endsWith(".flac") || lower.endsWith(".m4a")) {
                    return true;
                }
            }
            return true; // accept other http(s)/file URLs as well
        } catch (Exception e) {
            return false;
        }
    }
}
