package com.heritage.platform.service.ai;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserAiProviderSettingsService {

    private final Map<String, UserSettings> settingsByUser = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Path settingsFile;
    private final SecretKeySpec encryptionKey;

    @org.springframework.beans.factory.annotation.Value("${PLATFORM_AI_ENABLED:false}")
    private boolean platformAiEnabled;

    @org.springframework.beans.factory.annotation.Value("${PLATFORM_TEXT_PROVIDER:deepseek}")
    private String platformTextProvider = "deepseek";

    @org.springframework.beans.factory.annotation.Value("${PLATFORM_TEXT_API_KEY:}")
    private String platformTextApiKey = "";

    @org.springframework.beans.factory.annotation.Value("${PLATFORM_TEXT_MODEL:deepseek-v4-flash}")
    private String platformTextModel = "deepseek-v4-flash";

    @org.springframework.beans.factory.annotation.Value("${PLATFORM_IMAGE_PROVIDER:qwen}")
    private String platformImageProvider = "qwen";

    @org.springframework.beans.factory.annotation.Value("${PLATFORM_IMAGE_API_KEY:}")
    private String platformImageApiKey = "";

    @org.springframework.beans.factory.annotation.Value("${PLATFORM_IMAGE_MODEL:qwen-image-2.0-pro}")
    private String platformImageModel = "qwen-image-2.0-pro";

    public UserAiProviderSettingsService() {
        String secret = System.getenv().getOrDefault("AI_KEY_ENCRYPTION_SECRET", "").trim();
        String dataDirectory = System.getenv().getOrDefault("APP_DATA_DIR", "data").trim();
        this.settingsFile = Path.of(dataDirectory).resolve("user-ai-provider-settings.properties").normalize();
        this.encryptionKey = secret.isBlank() ? null : new SecretKeySpec(sha256(secret), "AES");
        loadPersistedSettings();
    }

    public UserSettings current() {
        String username = currentUsername();
        UserSettings userSettings = username.isBlank()
                ? UserSettings.defaults()
                : settingsByUser.getOrDefault(username, UserSettings.defaults());
        return platformAiEnabled ? platformSettings() : userSettings;
    }

    public boolean platformAiEnabled() {
        return platformAiEnabled;
    }

    public UserSettings configureText(String provider, String apiKey, String model) {
        rejectUserConfigurationWhenPlatformManaged();
        String username = requireCurrentUsername();
        String normalized = normalizeTextProvider(provider);
        validateKey(apiKey, normalized);
        UserSettings result = settingsByUser.compute(username, (ignored, existing) -> {
            UserSettings value = existing == null ? UserSettings.defaults() : existing;
            return value.withText(normalized, apiKey.trim(), normalizeModel(model, defaultTextModel(normalized)));
        });
        persistSettings();
        return result;
    }

    public UserSettings configureImage(String provider, String apiKey, String model) {
        rejectUserConfigurationWhenPlatformManaged();
        String username = requireCurrentUsername();
        String normalized = normalizeImageProvider(provider);
        if (!"comfyui".equals(normalized)) validateKey(apiKey, normalized);
        UserSettings result = settingsByUser.compute(username, (ignored, existing) -> {
            UserSettings value = existing == null ? UserSettings.defaults() : existing;
            return value.withImage(normalized, apiKey == null ? "" : apiKey.trim(), normalizeModel(model, defaultImageModel(normalized)));
        });
        persistSettings();
        return result;
    }

    public UserSettings clearText() {
        String username = requireCurrentUsername();
        UserSettings result = settingsByUser.compute(username, (ignored, existing) -> {
            UserSettings value = existing == null ? UserSettings.defaults() : existing;
            return value.withText("deepseek", "", defaultTextModel("deepseek"));
        });
        persistSettings();
        return result;
    }

    public UserSettings clearImage() {
        String username = requireCurrentUsername();
        UserSettings result = settingsByUser.compute(username, (ignored, existing) -> {
            UserSettings value = existing == null ? UserSettings.defaults() : existing;
            return value.withImage("qwen", "", defaultImageModel("qwen"));
        });
        persistSettings();
        return result;
    }

    public void configureLegacyDeepSeekKey(String apiKey) {
        configureText("deepseek", apiKey, defaultTextModel("deepseek"));
    }

    public void configureLegacyQwenKey(String apiKey) {
        configureImage("qwen", apiKey, defaultImageModel("qwen"));
    }

    public boolean hasCurrentUser() { return !currentUsername().isBlank(); }

    private void loadPersistedSettings() {
        if (encryptionKey == null || !Files.isRegularFile(settingsFile)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(settingsFile)) {
            properties.load(input);
            for (String encodedUsername : properties.stringPropertyNames()) {
                String username = new String(Base64.getUrlDecoder().decode(encodedUsername), StandardCharsets.UTF_8);
                String[] fields = decrypt(properties.getProperty(encodedUsername)).split("\u001f", -1);
                if (fields.length == 6) {
                    settingsByUser.put(username, new UserSettings(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5]));
                }
            }
        } catch (Exception ignored) {
            // Invalid or old entries remain unavailable; never expose key material in logs.
        }
    }

    private synchronized void persistSettings() {
        if (encryptionKey == null) return;
        Properties properties = new Properties();
        settingsByUser.forEach((username, settings) -> {
            String key = Base64.getUrlEncoder().withoutPadding().encodeToString(username.getBytes(StandardCharsets.UTF_8));
            String value = String.join("\u001f", settings.textProvider(), settings.textApiKey(), settings.textModel(),
                    settings.imageProvider(), settings.imageApiKey(), settings.imageModel());
            properties.setProperty(key, encrypt(value));
        });
        try {
            Files.createDirectories(settingsFile.getParent());
            Path temporary = settingsFile.resolveSibling(settingsFile.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Encrypted per-user AI provider settings");
            }
            Files.move(temporary, settingsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to persist AI provider settings.", ex);
        }
    }

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt AI provider settings.", ex);
        }
    }

    private String decrypt(String encoded) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encoded);
        byte[] iv = java.util.Arrays.copyOfRange(combined, 0, 12);
        byte[] ciphertext = java.util.Arrays.copyOfRange(combined, 12, combined.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize AI key encryption.", ex);
        }
    }

    private String normalizeTextProvider(String value) {
        String provider = normalize(value);
        if (!java.util.Set.of("deepseek", "openai", "doubao").contains(provider)) {
            throw new IllegalArgumentException("Unsupported text provider: " + provider + ".");
        }
        return provider;
    }

    private String normalizeImageProvider(String value) {
        String provider = normalize(value);
        if (!java.util.Set.of("qwen", "openai", "doubao", "comfyui").contains(provider)) {
            throw new IllegalArgumentException("Unsupported image provider: " + provider + ".");
        }
        return provider;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private String normalizeModel(String value, String fallback) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private void validateKey(String value, String provider) {
        String key = value == null ? "" : value.trim();
        if (key.length() < 20 || key.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Enter a valid " + provider + " API key (at least 20 characters, without spaces).");
        }
    }

    private void rejectUserConfigurationWhenPlatformManaged() {
        if (platformAiEnabled) {
            throw new IllegalArgumentException("Platform AI mode is enabled; the platform provides the AI services for this test.");
        }
    }

    private UserSettings platformSettings() {
        String textProvider = normalizePlatformProvider(platformTextProvider, "deepseek", java.util.Set.of("deepseek", "openai", "doubao"));
        String imageProvider = normalizePlatformProvider(platformImageProvider, "qwen", java.util.Set.of("qwen", "openai", "doubao", "comfyui"));
        return new UserSettings(
                textProvider,
                safe(platformTextApiKey),
                normalizeModel(platformTextModel, defaultTextModel(textProvider)),
                imageProvider,
                safe(platformImageApiKey),
                normalizeModel(platformImageModel, defaultImageModel(imageProvider))
        );
    }

    private String normalizePlatformProvider(String value, String fallback, java.util.Set<String> allowed) {
        String normalized = normalize(value);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String requireCurrentUsername() {
        String username = currentUsername();
        if (username.isBlank()) throw new IllegalArgumentException("Sign in before configuring an AI provider.");
        return username;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return "";
        String username = authentication.getName();
        return username == null || username.isBlank() || "anonymousUser".equals(username) ? "" : username.trim();
    }

    public static String defaultTextModel(String provider) {
        return switch (provider) {
            case "openai" -> "gpt-4.1-mini";
            case "doubao" -> "doubao-seed-1-6-250615";
            default -> "deepseek-v4-flash";
        };
    }

    public static String defaultImageModel(String provider) {
        return switch (provider) {
            case "openai" -> "gpt-image-1.5";
            case "doubao" -> "doubao-seedream-4-5-251128";
            case "comfyui" -> "local-workflow";
            default -> "qwen-image-2.0-pro";
        };
    }

    public record UserSettings(
            String textProvider, String textApiKey, String textModel,
            String imageProvider, String imageApiKey, String imageModel
    ) {
        public static UserSettings defaults() {
            return new UserSettings("deepseek", "", defaultTextModel("deepseek"), "qwen", "", defaultImageModel("qwen"));
        }

        public UserSettings withText(String provider, String key, String model) {
            return new UserSettings(provider, key, model, imageProvider, imageApiKey, imageModel);
        }

        public UserSettings withImage(String provider, String key, String model) {
            return new UserSettings(textProvider, textApiKey, textModel, provider, key, model);
        }
    }
}
