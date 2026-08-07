package org.eu.sager.netmusicradio.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LogoManager {
    private static final LogoManager INSTANCE = new LogoManager();
    private static final int LOGO_SIZE = 64;
    private final Map<String, ResourceLocation> logoCache = new ConcurrentHashMap<>();
    private final Map<String, int[]> pendingImages = new ConcurrentHashMap<>();
    private final Map<String, Boolean> loadingLogos = new ConcurrentHashMap<>();

    public static LogoManager getInstance() {
        return INSTANCE;
    }

    public ResourceLocation getLogo(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return logoCache.get(url);
    }

    public boolean isLoading(String url) {
        return url != null && !url.isBlank() && Boolean.TRUE.equals(loadingLogos.get(url));
    }

    public void loadLogo(String url) {
        if (url == null || url.isBlank() || logoCache.containsKey(url) || Boolean.TRUE.equals(loadingLogos.get(url))) {
            return;
        }
        loadingLogos.put(url, true);
        new Thread(() -> {
            try {
                URL imgUrl = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) imgUrl.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (InputStream is = conn.getInputStream()) {
                        BufferedImage image = ImageIO.read(is);
                        if (image != null) {
                            BufferedImage scaled = new BufferedImage(LOGO_SIZE, LOGO_SIZE, BufferedImage.TYPE_INT_ARGB);
                            Graphics2D g = scaled.createGraphics();
                            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            int srcW = image.getWidth();
                            int srcH = image.getHeight();
                            double scale = Math.min((double) LOGO_SIZE / srcW, (double) LOGO_SIZE / srcH);
                            int drawW = (int) Math.round(srcW * scale);
                            int drawH = (int) Math.round(srcH * scale);
                            int drawX = (LOGO_SIZE - drawW) / 2;
                            int drawY = (LOGO_SIZE - drawH) / 2;
                            g.drawImage(image, drawX, drawY, drawW, drawH, null);
                            g.dispose();

                            int[] pixels = new int[LOGO_SIZE * LOGO_SIZE];
                            scaled.getRGB(0, 0, LOGO_SIZE, LOGO_SIZE, pixels, 0, LOGO_SIZE);
                            pendingImages.put(url, pixels);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[NetMusic] Logo load failed: " + url + " - " + e.getMessage());
            } finally {
                loadingLogos.remove(url);
            }
        }, "NetMusic-LogoLoader").start();
    }

    public void tick() {
        if (pendingImages.isEmpty()) return;
        List<Map.Entry<String, int[]>> entries = new ArrayList<>(pendingImages.entrySet());
        for (Map.Entry<String, int[]> entry : entries) {
            String url = entry.getKey();
            int[] pixels = entry.getValue();
            pendingImages.remove(url);

            NativeImage nativeImage = new NativeImage(LOGO_SIZE, LOGO_SIZE, false);
            for (int x = 0; x < LOGO_SIZE; x++) {
                for (int y = 0; y < LOGO_SIZE; y++) {
                    int argb = pixels[y * LOGO_SIZE + x];
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    int a = (argb >> 24) & 0xFF;
                    int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                    nativeImage.setPixelRGBA(x, y, abgr);
                }
            }

            DynamicTexture texture = new DynamicTexture(nativeImage);
            ResourceLocation rl = new ResourceLocation("netmusicradio", "logo_" + Integer.toHexString(url.hashCode()));
            Minecraft.getInstance().getTextureManager().register(rl, texture);
            logoCache.put(url, rl);
        }
    }

    public void clear() {
        for (ResourceLocation rl : logoCache.values()) {
            Minecraft.getInstance().getTextureManager().release(rl);
        }
        logoCache.clear();
        pendingImages.clear();
        loadingLogos.clear();
    }
}