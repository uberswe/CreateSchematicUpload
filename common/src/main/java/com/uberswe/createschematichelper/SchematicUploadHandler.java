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

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SchematicUploadHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static volatile Path pendingPath;

    public static void onSchematicSaved(Path filePath) {
        if (!ConfigValues.enabled) return;

        if (ConfigValues.autoUpload) {
            if (ConfigValues.promptBeforeUpload) {
                pendingPath = filePath;
                sendPrompt(filePath, "createschematichelper.confirm.message",
                        "createschematichelper.confirm.upload", "/csh upload");
            } else {
                uploadAsync(filePath);
            }
        }
    }

    private static void sendPrompt(Path filePath, String messageKey, String buttonKey, String command) {
        String fileName = filePath.getFileName().toString();
        MutableComponent message = Component.translatable(messageKey, fileName)
                .withStyle(ChatFormatting.GRAY);
        MutableComponent button = Component.literal(" [")
                .append(Component.translatable(buttonKey))
                .append("]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
        sendChatMessage(message.append(button));
    }

    public static void confirmUpload(Path filePath) {
        uploadAsync(filePath);
    }

    public static void confirmPendingUpload() {
        Path path = pendingPath;
        pendingPath = null;
        if (path != null) {
            uploadAsync(path);
        }
    }

    private static void uploadAsync(Path filePath) {
        CompletableFuture.runAsync(() -> {
            try {
                sendChatMessage(Component.translatable("createschematichelper.upload.uploading")
                        .withStyle(ChatFormatting.GRAY));
                upload(filePath);
            } catch (Exception e) {
                LOGGER.error("Failed to upload schematic", e);
                sendChatMessage(Component.translatable("createschematichelper.upload.failed")
                        .withStyle(ChatFormatting.YELLOW));
            }
        });
    }

    private static void upload(Path filePath) throws Exception {
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

        String boundary = UUID.randomUUID().toString();
        byte[] body = buildMultipartBody(boundary, fileName, fileBytes);

        String baseUrl = ConfigValues.baseUrl;
        LOGGER.info("Sending upload: {} (body {} bytes) to {}", fileName, body.length, baseUrl);

        long timestamp = System.currentTimeMillis() / 1000;
        Minecraft mc = Minecraft.getInstance();
        String username = mc.player != null ? mc.player.getGameProfile().getName() : "Unknown";
        String message = timestamp + ":" + ConfigValues.modVersion + ":" + username + ":" + fileName;
        String signature = SchematicDownloadHandler.hmacSha256(message);

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/schematics/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Mod-Message", message)
                .header("X-Mod-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofMinutes(5))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        LOGGER.info("Upload response: HTTP {} — {}", response.statusCode(), response.body());
        handleResponse(response, baseUrl);
    }

    private static void handleResponse(HttpResponse<String> response, String baseUrl) {
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

    private static byte[] buildMultipartBody(String boundary, String fileName, byte[] fileBytes) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String crlf = "\r\n";
        String safeFileName = fileName.replace("\"", "").replace("\r", "").replace("\n", "");

        baos.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + safeFileName + "\"" + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Type: application/octet-stream" + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(crlf.getBytes(StandardCharsets.UTF_8));
        baos.write(fileBytes);
        baos.write(crlf.getBytes(StandardCharsets.UTF_8));

        baos.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
        return baos.toByteArray();
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
