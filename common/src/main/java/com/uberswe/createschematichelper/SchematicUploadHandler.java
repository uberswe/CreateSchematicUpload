package com.uberswe.createschematichelper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;

import com.uberswe.createschematichelper.SchematicIsometricRenderer.RenderedFrame;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SchematicUploadHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    public static void onSchematicSaved(Path filePath) {
        if (!ConfigValues.enabled) return;

        if (ConfigValues.autoUpload) {
            uploadAsync(filePath);
        } else {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.setScreen(new SchematicUploadConfirmScreen(filePath)));
        }
    }

    public static void confirmUpload(Path filePath) {
        uploadAsync(filePath);
    }

    private static void uploadAsync(Path filePath) {
        sendChatMessage(Component.translatable("createschematichelper.upload.rendering")
                .withStyle(ChatFormatting.GRAY));

        SchematicIsometricRenderer.render360(filePath)
                .thenAcceptAsync(frames -> {
                    try {
                        saveFramesLocally(filePath, frames);
                        sendChatMessage(Component.translatable("createschematichelper.upload.uploading")
                                .withStyle(ChatFormatting.GRAY));
                        upload(filePath, frames);
                    } catch (Exception e) {
                        LOGGER.error("Failed to upload schematic", e);
                        sendChatMessage(Component.translatable("createschematichelper.upload.failed")
                                .withStyle(ChatFormatting.YELLOW));
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to render previews, uploading without images", ex);
                    CompletableFuture.runAsync(() -> {
                        try {
                            sendChatMessage(Component.translatable("createschematichelper.upload.uploading")
                                    .withStyle(ChatFormatting.GRAY));
                            upload(filePath, List.of());
                        } catch (Exception e) {
                            LOGGER.error("Failed to upload schematic", e);
                            sendChatMessage(Component.translatable("createschematichelper.upload.failed")
                                    .withStyle(ChatFormatting.YELLOW));
                        }
                    });
                    return null;
                });
    }

    private static void upload(Path filePath, List<RenderedFrame> frames) throws Exception {
        long fileSize = Files.size(filePath);
        if (fileSize > MAX_FILE_SIZE) {
            sendChatMessage(Component.translatable("createschematichelper.upload.too_large")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        String fileName = filePath.getFileName().toString();
        byte[] fileBytes = Files.readAllBytes(filePath);
        LOGGER.info("Read schematic file: {} ({} bytes)", fileName, fileBytes.length);

        if (fileBytes.length == 0) {
            LOGGER.error("Schematic file is empty: {}", filePath);
            sendChatMessage(Component.translatable("createschematichelper.upload.failed")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        HttpResponse<String> response = sendUploadRequest(fileName, fileBytes, frames);
        LOGGER.info("Upload response: HTTP {} — {}", response.statusCode(), response.body());

        if (response.statusCode() != 200 && response.statusCode() != 409 && !frames.isEmpty()) {
            LOGGER.warn("Upload with {} frames failed (HTTP {}), retrying without images", frames.size(), response.statusCode());
            response = sendUploadRequest(fileName, fileBytes, List.of());
            LOGGER.info("Retry response: HTTP {} — {}", response.statusCode(), response.body());
        }

        handleResponse(response, ConfigValues.baseUrl, frames.size());
    }

    private static HttpResponse<String> sendUploadRequest(String fileName, byte[] fileBytes, List<RenderedFrame> frames) throws Exception {
        String boundary = UUID.randomUUID().toString();
        byte[] body = buildMultipartBody(boundary, fileName, fileBytes, frames);

        String baseUrl = ConfigValues.baseUrl;
        LOGGER.info("Sending upload: {} ({} frames, body {} bytes) to {}", fileName, frames.size(), body.length, baseUrl);

        long timestamp = System.currentTimeMillis() / 1000;
        Minecraft mc = Minecraft.getInstance();
        String username = mc.player != null ? mc.player.getGameProfile().getName() : "Unknown";
        String message = timestamp + ":" + ConfigValues.modVersion + ":" + username + ":" + fileName;
        String signature = SchematicDownloadHandler.hmacSha256(message);

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/schematics/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Content-Length", String.valueOf(body.length))
                .header("X-Mod-Message", message)
                .header("X-Mod-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofMinutes(5))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void handleResponse(HttpResponse<String> response, String baseUrl, int imageCount) {
        int status = response.statusCode();
        String body = response.body();

        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            if (status == 200) {
                String token = json.get("token").getAsString();
                String url = baseUrl + "/u/" + token;

                MutableComponent prefix = Component.translatable("createschematichelper.upload.success")
                        .withStyle(ChatFormatting.GREEN);
                MutableComponent link = Component.literal(url)
                        .withStyle(style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));

                sendChatMessage(prefix.append(link));
            } else if (status == 409) {
                sendChatMessage(Component.translatable("createschematichelper.upload.already_exists")
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                String error = json.has("error") ? json.get("error").getAsString() : "Unknown error";
                LOGGER.error("Upload failed (HTTP {}): {}", status, error);
                sendChatMessage(Component.translatable("createschematichelper.upload.error", error)
                        .withStyle(ChatFormatting.YELLOW));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse upload response (HTTP {})", status, e);
            sendChatMessage(Component.translatable("createschematichelper.upload.failed")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static byte[] buildMultipartBody(String boundary, String fileName, byte[] fileBytes, List<RenderedFrame> frames) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String crlf = "\r\n";
        String safeFileName = fileName.replace("\"", "").replace("\r", "").replace("\n", "");

        writePart(baos, boundary, crlf, "file", safeFileName, "application/octet-stream", fileBytes);

        for (RenderedFrame frame : frames) {
            if (frame.featured()) {
                writePart(baos, boundary, crlf, "images", frame.filename(), frame.mimeType(), frame.data());
            }
            writePart(baos, boundary, crlf, "rotation_images", frame.filename(), frame.mimeType(), frame.data());
        }

        baos.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
        return baos.toByteArray();
    }

    private static void writePart(ByteArrayOutputStream baos, String boundary, String crlf,
                                   String fieldName, String fileName, String contentType, byte[] data) throws Exception {
        baos.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"" + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Type: " + contentType + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(crlf.getBytes(StandardCharsets.UTF_8));
        baos.write(data);
        baos.write(crlf.getBytes(StandardCharsets.UTF_8));
    }

    private static void saveFramesLocally(Path schematicPath, List<RenderedFrame> frames) {
        if (!ConfigValues.saveFeaturedFrames && !ConfigValues.saveAllFrames) return;

        try {
            String baseName = schematicPath.getFileName().toString().replaceFirst("\\.nbt$", "");
            Path dir = schematicPath.getParent().resolve(baseName + "_renders");
            Files.createDirectories(dir);

            for (RenderedFrame frame : frames) {
                if (ConfigValues.saveAllFrames || (ConfigValues.saveFeaturedFrames && frame.featured())) {
                    Files.write(dir.resolve(frame.filename()), frame.data());
                }
            }
            LOGGER.info("Saved rendered frames to {}", dir);
        } catch (Exception e) {
            LOGGER.error("Failed to save frames locally", e);
        }
    }

    static void sendChatMessage(Component message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(message, false);
            }
        });
    }
}
