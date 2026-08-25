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
import java.net.URL;
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
    private static final int PROGRESS_BAR_LENGTH = 20;
    private static volatile Path pendingPath;

    /**
     * When Create: Blueprinted is installed, uploads are routed through its render
     * pipeline instead of our own isometric renderer: the delegate kicks off
     * Blueprinted's screenshot render, which hands the finished image back to
     * {@link #uploadWithImage} via our ShareProvider.
     */
    @FunctionalInterface
    public interface BlueprintedShareDelegate {
        void share(String schematicFileName);
    }

    private static volatile BlueprintedShareDelegate blueprintedShare;

    public static void setBlueprintedShareDelegate(BlueprintedShareDelegate delegate) {
        blueprintedShare = delegate;
    }

    public static void onSchematicSaved(Path filePath) {
        if (!ConfigValues.enabled) return;

        boolean wantsSave = ConfigValues.saveFeaturedFrames;

        if (ConfigValues.autoUpload) {
            if (ConfigValues.promptBeforeUpload) {
                pendingPath = filePath;
                sendPrompt(filePath, "createschematichelper.confirm.message",
                        "createschematichelper.confirm.upload", "/csh upload");
            } else {
                uploadAsync(filePath);
            }
        } else if (wantsSave) {
            if (ConfigValues.promptBeforeUpload) {
                pendingPath = filePath;
                sendPrompt(filePath, "createschematichelper.confirm.render_message",
                        "createschematichelper.confirm.render", "/csh render");
            } else {
                renderAsync(filePath);
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

    public static void confirmPendingRender() {
        Path path = pendingPath;
        pendingPath = null;
        if (path != null) {
            renderAsync(path);
        }
    }

    private static void renderAsync(Path filePath) {
        sendChatMessage(Component.translatable("createschematichelper.upload.rendering")
                .withStyle(ChatFormatting.GRAY));
        sendProgressBar("Rendering", 0, 1);

        SchematicIsometricRenderer.renderViews(filePath, (stage, current, total) -> {
            switch (stage) {
                case "rendering" -> sendProgressBar("Rendering", current, total);
                case "processing" -> sendProgressBar("Processing", current, total);
            }
        })
        .thenAcceptAsync(frames -> {
            try {
                saveFramesLocally(filePath, frames);
                sendChatMessage(Component.translatable("createschematichelper.render.success")
                        .withStyle(ChatFormatting.GREEN));
            } catch (Exception e) {
                LOGGER.error("Failed to save rendered frames", e);
                sendChatMessage(Component.translatable("createschematichelper.render.failed")
                        .withStyle(ChatFormatting.YELLOW));
            }
        })
        .exceptionally(ex -> {
            LOGGER.error("Failed to render previews", ex);
            sendChatMessage(Component.translatable("createschematichelper.render.failed")
                    .withStyle(ChatFormatting.YELLOW));
            return null;
        });
    }

    /**
     * Entry point for our Create: Blueprinted ShareProvider. Blueprinted has already
     * rendered its screenshot of the schematic; this uploads the schematic file with
     * that image attached. Runs quietly on success — Blueprinted announces the
     * resulting link itself — but reports specific failures in chat.
     *
     * @return a future completing with the uploaded schematic's URL, or exceptionally on failure
     */
    public static CompletableFuture<URL> uploadWithImage(String schematicName, byte[] imageBytes) {
        Path schematicsDir = Minecraft.getInstance().gameDirectory.toPath().resolve("schematics");
        Path filePath = schematicsDir.resolve(schematicName);
        if (!Files.exists(filePath) && !schematicName.endsWith(".nbt")) {
            filePath = schematicsDir.resolve(schematicName + ".nbt");
        }
        if (!Files.exists(filePath)) {
            LOGGER.error("Cannot share schematic, file not found: {}", schematicName);
            sendChatMessage(Component.translatable("createschematichelper.share.missing_file", schematicName)
                    .withStyle(ChatFormatting.YELLOW));
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Schematic file not found: " + schematicName));
        }

        Path resolvedPath = filePath;
        List<RenderedFrame> frames = imageBytes != null && imageBytes.length > 0
                ? List.of(new RenderedFrame("preview.png", imageBytes, "image/png"))
                : List.of();
        return CompletableFuture.supplyAsync(() -> {
            sendChatMessage(Component.translatable("createschematichelper.upload.private_note")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            URL url;
            try {
                url = upload(resolvedPath, frames, false);
            } catch (Exception e) {
                LOGGER.error("Failed to upload schematic", e);
                throw new RuntimeException("Failed to upload schematic", e);
            }
            if (url == null) {
                // The specific reason (too large, duplicate, server error) was already
                // reported in chat by upload()
                throw new RuntimeException("Schematic upload was rejected");
            }
            return url;
        });
    }

    private static void uploadAsync(Path filePath) {
        BlueprintedShareDelegate delegate = blueprintedShare;
        if (delegate != null) {
            try {
                delegate.share(filePath.getFileName().toString());
                return;
            } catch (Throwable t) {
                LOGGER.error("Blueprinted share pipeline failed, falling back to built-in renderer", t);
            }
        }

        sendChatMessage(Component.translatable("createschematichelper.upload.uploading")
                .withStyle(ChatFormatting.GRAY));
        sendChatMessage(Component.translatable("createschematichelper.upload.private_note")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        sendProgressBar("Rendering", 0, 1);

        SchematicIsometricRenderer.renderViews(filePath, (stage, current, total) -> {
            switch (stage) {
                case "rendering" -> sendProgressBar("Rendering", current, total);
                case "processing" -> sendProgressBar("Processing", current, total);
            }
        })
        .thenAcceptAsync(frames -> {
            try {
                saveFramesLocally(filePath, frames);
                sendProgressBar("Uploading", 0, 0);
                upload(filePath, frames, true);
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
                    sendProgressBar("Uploading", 0, 0);
                    upload(filePath, List.of(), true);
                } catch (Exception e) {
                    LOGGER.error("Failed to upload schematic", e);
                    sendChatMessage(Component.translatable("createschematichelper.upload.failed")
                            .withStyle(ChatFormatting.YELLOW));
                }
            });
            return null;
        });
    }

    private static URL upload(Path filePath, List<RenderedFrame> frames, boolean announceSuccess) throws Exception {
        long fileSize = Files.size(filePath);
        if (fileSize > MAX_FILE_SIZE) {
            sendChatMessage(Component.translatable("createschematichelper.upload.too_large")
                    .withStyle(ChatFormatting.RED));
            return null;
        }

        String fileName = filePath.getFileName().toString();
        byte[] fileBytes = Files.readAllBytes(filePath);
        LOGGER.info("Read schematic file: {} ({} bytes)", fileName, fileBytes.length);

        if (fileBytes.length == 0) {
            LOGGER.error("Schematic file is empty: {}", filePath);
            sendChatMessage(Component.translatable("createschematichelper.upload.failed")
                    .withStyle(ChatFormatting.YELLOW));
            return null;
        }

        HttpResponse<String> response = sendUploadRequest(fileName, fileBytes, frames);
        LOGGER.info("Upload response: HTTP {} — {}", response.statusCode(), response.body());

        if (response.statusCode() != 200 && response.statusCode() != 409 && !frames.isEmpty()) {
            LOGGER.warn("Upload with {} frames failed (HTTP {}), retrying without images", frames.size(), response.statusCode());
            response = sendUploadRequest(fileName, fileBytes, List.of());
            LOGGER.info("Retry response: HTTP {} — {}", response.statusCode(), response.body());
        }

        return handleResponse(response, ConfigValues.baseUrl, announceSuccess);
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

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static URL handleResponse(HttpResponse<String> response, String baseUrl, boolean announceSuccess) {
        int status = response.statusCode();
        String body = response.body();

        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            if (status == 200) {
                String token = json.get("token").getAsString();
                String url = baseUrl + "/u/" + token;

                if (announceSuccess) {
                    MutableComponent prefix = Component.translatable("createschematichelper.upload.success")
                            .withStyle(ChatFormatting.GREEN);
                    MutableComponent link = Component.literal(url)
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));

                    sendChatMessage(prefix.append(link));
                }
                return URI.create(url).toURL();
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
        return null;
    }

    private static byte[] buildMultipartBody(String boundary, String fileName, byte[] fileBytes, List<RenderedFrame> frames) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String crlf = "\r\n";
        String safeFileName = fileName.replace("\"", "").replace("\r", "").replace("\n", "");

        writePart(baos, boundary, crlf, "file", safeFileName, "application/octet-stream", fileBytes);

        for (RenderedFrame frame : frames) {
            writePart(baos, boundary, crlf, "images", frame.filename(), frame.mimeType(), frame.data());
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
        if (!ConfigValues.saveFeaturedFrames) return;

        try {
            String baseName = schematicPath.getFileName().toString().replaceFirst("\\.nbt$", "");
            Path dir = schematicPath.getParent().resolve(baseName + "_renders");
            Files.createDirectories(dir);

            for (RenderedFrame frame : frames) {
                Files.write(dir.resolve(frame.filename()), frame.data());
            }
            LOGGER.info("Saved rendered frames to {}", dir);
        } catch (Exception e) {
            LOGGER.error("Failed to save frames locally", e);
        }
    }

    private static void sendProgressBar(String stage, int current, int total) {
        MutableComponent msg;
        if (total > 0) {
            int filled = Math.min(PROGRESS_BAR_LENGTH, current * PROGRESS_BAR_LENGTH / total);
            msg = Component.literal(stage + " ").withStyle(ChatFormatting.WHITE);
            msg.append(Component.literal("█".repeat(filled)).withStyle(ChatFormatting.GREEN));
            msg.append(Component.literal("░".repeat(PROGRESS_BAR_LENGTH - filled)).withStyle(ChatFormatting.DARK_GRAY));
            msg.append(Component.literal(" " + current + "/" + total).withStyle(ChatFormatting.GRAY));
        } else {
            msg = Component.literal(stage + "...").withStyle(ChatFormatting.WHITE);
        }

        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.displayClientMessage(msg, true);
            }
        });
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
