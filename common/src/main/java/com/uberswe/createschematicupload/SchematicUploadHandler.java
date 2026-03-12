package com.uberswe.createschematicupload;

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
import java.util.concurrent.CompletableFuture;

public class SchematicUploadHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

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
        sendChatMessage(Component.translatable("createschematicupload.upload.uploading")
                .withStyle(ChatFormatting.GRAY));

        CompletableFuture.runAsync(() -> {
            try {
                upload(filePath);
            } catch (Exception e) {
                LOGGER.error("Failed to upload schematic", e);
                sendChatMessage(Component.translatable("createschematicupload.upload.failed")
                        .withStyle(ChatFormatting.YELLOW));
            }
        });
    }

    private static void upload(Path filePath) throws Exception {
        long fileSize = Files.size(filePath);
        if (fileSize > MAX_FILE_SIZE) {
            sendChatMessage(Component.translatable("createschematicupload.upload.too_large")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        String fileName = filePath.getFileName().toString();
        byte[] fileBytes = Files.readAllBytes(filePath);

        String boundary = "----SchematicUpload" + System.currentTimeMillis();
        byte[] body = buildMultipartBody(boundary, fileName, fileBytes);

        String baseUrl = ConfigValues.baseUrl;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/schematics/upload-anonymous"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
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

                MutableComponent prefix = Component.translatable("createschematicupload.upload.success")
                        .withStyle(ChatFormatting.GREEN);
                MutableComponent link = Component.literal(url)
                        .withStyle(style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));

                sendChatMessage(prefix.append(link));
            } else if (status == 409) {
                sendChatMessage(Component.translatable("createschematicupload.upload.already_exists")
                        .withStyle(ChatFormatting.YELLOW));
            } else {
                String error = json.has("error") ? json.get("error").getAsString() : "Unknown error";
                LOGGER.error("Upload failed (HTTP {}): {}", status, error);
                sendChatMessage(Component.translatable("createschematicupload.upload.failed")
                        .withStyle(ChatFormatting.YELLOW));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse upload response (HTTP {})", status, e);
            sendChatMessage(Component.translatable("createschematicupload.upload.failed")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static byte[] buildMultipartBody(String boundary, String fileName, byte[] fileBytes) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String crlf = "\r\n";

        baos.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Type: application/octet-stream" + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(crlf.getBytes(StandardCharsets.UTF_8));
        baos.write(fileBytes);
        baos.write(crlf.getBytes(StandardCharsets.UTF_8));
        baos.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));

        return baos.toByteArray();
    }

    private static void sendChatMessage(Component message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(message, false);
            }
        });
    }
}
