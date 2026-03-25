package com.uberswe.createschematicdownload.createschematicdownload;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

public class SchematicDownloader {
    private static final String SHARED_SECRET = "5a0841453e5c2588583da1fb215f4af88a5a7d4ee86a720aea4ae27c4065dace";

    @Nullable
    public static String downloadSchematic(String slugOrUrl) {
        String slug = slugOrUrl;

        try {
            URL url = new URI(slugOrUrl).toURL();

            if (url.getHost().equals("createmod.com")) {
                slug = url.getPath().substring("/schematics/".length());
            }
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException ignored) {
        }

        return downloadSchematicBySlug(slug);
    }

    @Nullable
    private static String downloadSchematicBySlug(String slug) {
        Minecraft minecraft = Minecraft.getInstance();
        long timestamp = System.currentTimeMillis() / 1000;

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://createmod.com/api/mod/download"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(minecraft, slug, timestamp).toString(), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                handleRequestError(response);
            }

            byte[] xorKey = deriveXorKey(timestamp);
            byte[] nbtBytes = xorDecode(response.body(), xorKey);
            return writeSchematicFile(minecraft, slug, nbtBytes);
        } catch (Exception e) {
            CreateSchematicDownload.LOGGER.error("Error downloading schematic: {}", e.getMessage());
            return null;
        }
    }

    private static JsonObject buildRequestBody(Minecraft minecraft, String slug, long timestamp) throws NoSuchAlgorithmException, InvalidKeyException {
        String username = minecraft.player != null ? minecraft.player.getGameProfile().getName() : "Unknown";
        String message = timestamp + ":" + CreateSchematicDownloadPlatform.getModVersion() + ":" + username + ":" + slug;

        JsonObject body = new JsonObject();
        body.addProperty("message", message);
        body.addProperty("signature", hmacSha256(message));
        body.addProperty("type", "schematic");
        return body;
    }

    private static void handleRequestError(HttpResponse<byte[]> response) throws Exception {
        String errorBody = new String(response.body(), StandardCharsets.UTF_8);
        String errorMsg;

        try {
            JsonObject errorJson = JsonParser.parseString(errorBody).getAsJsonObject();
            errorMsg = errorJson.has("error") ? errorJson.get("error").getAsString() : errorBody;
        } catch (Exception e) {
            errorMsg = "HTTP " + response.statusCode();
        }

        throw new Exception("Error downloading schematic: " + errorMsg);
    }

    private static String writeSchematicFile(Minecraft minecraft, String slug, byte[] data) throws IOException {
        Path schematicsPath = minecraft.gameDirectory.toPath().resolve("schematics");
        Files.createDirectories(schematicsPath);
        String fileName = slug + ".nbt";
        Path outputFile = schematicsPath.resolve(fileName);
        int counter = 1;

        while (Files.exists(outputFile)) {
            fileName = slug + "_" + counter + ".nbt";
            outputFile = schematicsPath.resolve(fileName);
            counter++;
        }

        Files.write(outputFile, data);
        CreateSchematicDownload.LOGGER.info("Saved schematic to {}", outputFile);
        return fileName;
    }

    private static String hmacSha256(String message) throws InvalidKeyException, NoSuchAlgorithmException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SchematicDownloader.SHARED_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static byte[] deriveXorKey(long timestamp) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(SchematicDownloader.SHARED_SECRET.getBytes(StandardCharsets.UTF_8));
        md.update(Long.toString(timestamp).getBytes(StandardCharsets.UTF_8));
        return md.digest(); // 32 bytes
    }

    private static byte[] xorDecode(byte[] data, byte[] key) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }

        return result;
    }
}
