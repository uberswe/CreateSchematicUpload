package com.uberswe.createschematichelper;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.schematics.client.SchematicRenderer;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SchematicIsometricRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float ISOMETRIC_PITCH = 35.264f;

    private static final float START_ANGLE = 45f;
    private static final float[] FEATURED_ANGLES = {45f, 135f, 225f, 315f};

    private static final int PIXELS_PER_BLOCK = 32;
    private static final int MAX_FB_SIZE = 1400;
    private static final int MIN_FB_SIZE = 768;
    private static final int TARGET_WIDTH = 1200;
    private static final int FRAMES_PER_BATCH = 8;
    private static final long MAX_TOTAL_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final float QUALITY_HIGH = 0.85f;
    private static final float QUALITY_LOW = 0.75f;

    public static CompletableFuture<List<RenderedFrame>> render360(Path nbtFile) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream is = Files.newInputStream(nbtFile)) {
                return NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
            } catch (Exception e) {
                throw new RuntimeException("Failed to read NBT file", e);
            }
        }).thenCompose(SchematicIsometricRenderer::render360);
    }

    /**
     * Holds all state needed across render batches.
     */
    private record RenderState(
            Minecraft mc,
            RenderTarget renderTarget,
            SchematicRenderer renderer,
            SuperRenderTypeBuffer buffers,
            float[] angles,
            Vec3i size,
            float scale,
            int fbW,
            int fbH
    ) {}

    private static RenderState setupRenderState(CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) {
            throw new IllegalStateException("No active world");
        }

        StructureTemplate template = new StructureTemplate();
        template.load(mc.level.holderLookup(Registries.BLOCK), tag);
        Vec3i size = template.getSize();
        int maxDim = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));

        int maxSupported = Math.min(MAX_FB_SIZE, RenderSystem.maxSupportedTextureSize());
        int fbW, fbH;
        float effectivePixelWidth = PIXELS_PER_BLOCK;

        if (ConfigValues.overrideWidth > 0 && ConfigValues.overrideHeight > 0) {
            fbW = Math.min(ConfigValues.overrideWidth, maxSupported);
            fbH = Math.min(ConfigValues.overrideHeight, maxSupported);
            effectivePixelWidth = (float) fbH / (maxDim * 2.0f);
        } else {
            int theoreticalH = maxDim * PIXELS_PER_BLOCK * 2;
            if (theoreticalH > maxSupported) {
                fbH = maxSupported;
                effectivePixelWidth = (float) maxSupported / (maxDim * 2.0f);
            } else {
                fbH = Math.max(MIN_FB_SIZE, theoreticalH);
            }
            int[] ratio = parseAspectRatio(ConfigValues.aspectRatio);
            fbW = fbH * ratio[0] / ratio[1];
        }

        RenderTarget renderTarget = new TextureTarget(fbW, fbH, true, Minecraft.ON_OSX);

        SchematicLevel schematicLevel = new SchematicLevel(BlockPos.ZERO, mc.level);
        StructurePlaceSettings settings = new StructurePlaceSettings();
        template.placeInWorld(schematicLevel, BlockPos.ZERO, BlockPos.ZERO, settings, mc.level.random, Block.UPDATE_CLIENTS);

        SchematicRenderer renderer = new SchematicRenderer(schematicLevel);

        Vector3f light0 = new Vector3f(-1.0f, 1.2f, -0.8f).normalize();
        Vector3f light1 = new Vector3f(0.5f, -0.2f, 1.0f).normalize();
        RenderSystem.setShaderLights(light0, light1);

        Matrix4f projectionMatrix = new Matrix4f().setOrtho(
                -fbW / 2f, fbW / 2f,
                -fbH / 2f, fbH / 2f,
                -10000f, 10000f
        );
        RenderSystem.setProjectionMatrix(projectionMatrix, RenderSystem.getVertexSorting());

        float scale = effectivePixelWidth / (float) Math.sqrt(2) * 0.85f;

        MultiBufferSource.BufferSource mcBuffers = mc.renderBuffers().bufferSource();
        SuperRenderTypeBuffer buffers = new SuperRenderTypeBuffer() {
            @Override public @NotNull VertexConsumer getEarlyBuffer(@NotNull RenderType type) { return mcBuffers.getBuffer(type); }
            @Override public @NotNull VertexConsumer getBuffer(@NotNull RenderType type) { return mcBuffers.getBuffer(type); }
            @Override public @NotNull VertexConsumer getLateBuffer(@NotNull RenderType type) { return mcBuffers.getBuffer(type); }
            @Override public void draw() { mcBuffers.endBatch(); }
            @Override public void draw(@NotNull RenderType type) { mcBuffers.endBatch(type); }
        };

        float[] angles;
        if (ConfigValues.render360) {
            int frameCount = Math.max(4, ConfigValues.frameCount);
            float degreesPerFrame = 360f / frameCount;
            angles = new float[frameCount];
            for (int i = 0; i < frameCount; i++) {
                angles[i] = START_ANGLE + i * degreesPerFrame;
            }
        } else {
            angles = FEATURED_ANGLES;
        }

        return new RenderState(mc, renderTarget, renderer, buffers, angles, size, scale, fbW, fbH);
    }

    private static void renderBatch(RenderState state, int startIndex, List<NativeImage> images,
                                     CompletableFuture<List<NativeImage>> future) {
        try {
            int end = Math.min(startIndex + FRAMES_PER_BATCH, state.angles.length);

            for (int i = startIndex; i < end; i++) {
                float yRot = state.angles[i];

                state.renderTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                state.renderTarget.clear(Minecraft.ON_OSX);
                state.renderTarget.bindWrite(true);

                PoseStack poseStack = new PoseStack();
                poseStack.pushPose();
                poseStack.scale(state.scale, state.scale, state.scale);
                poseStack.mulPose(Axis.XP.rotationDegrees(ISOMETRIC_PITCH));
                poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
                poseStack.translate(
                        -state.size.getX() / 2f,
                        -state.size.getY() / 2f,
                        -state.size.getZ() / 2f
                );

                state.renderer.render(poseStack, state.buffers);
                state.buffers.draw();
                poseStack.popPose();

                NativeImage image = new NativeImage(state.fbW, state.fbH, false);
                RenderSystem.bindTexture(state.renderTarget.getColorTextureId());
                image.downloadTexture(0, false);
                image.flipY();
                images.add(image);
            }

            // Restore main render target so the game can render its frame between batches
            state.mc.getMainRenderTarget().bindWrite(true);

            if (end < state.angles.length) {
                // Schedule next batch
                RenderSystem.recordRenderCall(() -> renderBatch(state, end, images, future));
            } else {
                // All frames rendered — clean up and complete
                state.renderTarget.destroyBuffers();
                future.complete(images);
            }
        } catch (Exception e) {
            state.renderTarget.destroyBuffers();
            state.mc.getMainRenderTarget().bindWrite(true);
            future.completeExceptionally(e);
        }
    }

    public static CompletableFuture<List<RenderedFrame>> render360(CompoundTag tag) {
        CompletableFuture<List<NativeImage>> renderFuture = new CompletableFuture<>();

        RenderSystem.recordRenderCall(() -> {
            try {
                RenderState state = setupRenderState(tag);
                List<NativeImage> images = new ArrayList<>();
                renderBatch(state, 0, images, renderFuture);
            } catch (Exception e) {
                renderFuture.completeExceptionally(e);
            }
        });

        return renderFuture
                .thenApplyAsync(SchematicIsometricRenderer::cropAndConvert)
                .orTimeout(2, TimeUnit.MINUTES);
    }

    private static List<RenderedFrame> cropAndConvert(List<NativeImage> rawImages) {
        int fbW = rawImages.isEmpty() ? 1 : rawImages.get(0).getWidth();
        int fbH = rawImages.isEmpty() ? 1 : rawImages.get(0).getHeight();
        int unionMinX = fbW, unionMinY = fbH, unionMaxX = -1, unionMaxY = -1;

        for (NativeImage raw : rawImages) {
            int w = raw.getWidth();
            int h = raw.getHeight();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (((raw.getPixelRGBA(x, y) >> 24) & 0xFF) > 0) {
                        if (x < unionMinX) unionMinX = x;
                        if (x > unionMaxX) unionMaxX = x;
                        if (y < unionMinY) unionMinY = y;
                        if (y > unionMaxY) unionMaxY = y;
                    }
                }
            }
        }

        if (unionMaxX < unionMinX || unionMaxY < unionMinY) {
            unionMinX = 0; unionMinY = 0; unionMaxX = 0; unionMaxY = 0;
        }

        int cropW = unionMaxX - unionMinX + 1;
        int cropH = unionMaxY - unionMinY + 1;
        int padding = Math.max(12, Math.max(cropW, cropH) / 8);

        int contentW = cropW + padding * 2;
        int contentH = cropH + padding * 2;
        int[] ratio = parseAspectRatio(ConfigValues.aspectRatio);
        int bgW, bgH;
        if (contentW * ratio[1] > contentH * ratio[0]) {
            bgW = contentW;
            bgH = bgW * ratio[1] / ratio[0];
        } else {
            bgH = contentH;
            bgW = bgH * ratio[0] / ratio[1];
        }
        int padX = (bgW - cropW) / 2;
        int padY = (bgH - cropH) / 2;

        NativeImage background = generateBlueprintBackground(bgW, bgH);
        Set<Integer> featuredIndices = computeFeaturedIndices(rawImages.size());

        List<BufferedImage> composited = new ArrayList<>();
        List<Boolean> featuredFlags = new ArrayList<>();
        for (int i = 0; i < rawImages.size(); i++) {
            NativeImage raw = rawImages.get(i);
            try (raw) {
                NativeImage comp = compositeRegionOnBackground(background, raw,
                        unionMinX, unionMinY, cropW, cropH, padX, padY, bgW, bgH);
                try (comp) {
                    composited.add(toScaledBufferedImage(comp));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process frame {}", i, e);
            }
            featuredFlags.add(featuredIndices.contains(i));
        }
        background.close();

        List<RenderedFrame> result = encodeFrames(composited, featuredFlags, QUALITY_HIGH);
        if (totalBytes(result) > MAX_TOTAL_IMAGE_BYTES) {
            LOGGER.info("Frames exceed {}MB at high quality, re-encoding at {}", MAX_TOTAL_IMAGE_BYTES / 1024 / 1024, QUALITY_LOW);
            result = encodeFrames(composited, featuredFlags, QUALITY_LOW);
        }
        return result;
    }

    private static List<RenderedFrame> encodeFrames(List<BufferedImage> images, List<Boolean> featuredFlags, float quality) {
        List<RenderedFrame> result = new ArrayList<>();
        String format = ConfigValues.imageFormat;
        String ext = format.equals("jpeg") ? "jpg" : format;
        for (int i = 0; i < images.size(); i++) {
            try {
                byte[] imageBytes = encodeImage(images.get(i), format, quality);
                boolean featured = featuredFlags.get(i);
                String filename = String.format(featured ? "frame_%03d_featured.%s" : "frame_%03d.%s", i, ext);
                String mimeType = format.equals("jpeg") ? "image/jpeg" : "image/png";
                result.add(new RenderedFrame(filename, imageBytes, featured, mimeType));
            } catch (Exception e) {
                LOGGER.error("Failed to encode frame {}", i, e);
            }
        }
        return result;
    }

    private static long totalBytes(List<RenderedFrame> frames) {
        long total = 0;
        for (RenderedFrame f : frames) {
            total += f.data().length;
            if (f.featured()) total += f.data().length;
        }
        return total;
    }

    private static Set<Integer> computeFeaturedIndices(int frameCount) {
        if (frameCount <= FEATURED_ANGLES.length) {
            Set<Integer> all = new java.util.HashSet<>();
            for (int i = 0; i < frameCount; i++) all.add(i);
            return all;
        }
        float degreesPerFrame = 360f / frameCount;
        Set<Integer> indices = new java.util.HashSet<>();
        for (float angle : FEATURED_ANGLES) {
            indices.add(Math.round((angle - START_ANGLE) / degreesPerFrame));
        }
        return indices;
    }

    private static int[] parseAspectRatio(String ratio) {
        if (ratio != null && ratio.contains(":")) {
            String[] parts = ratio.split(":");
            try {
                int w = Integer.parseInt(parts[0].trim());
                int h = Integer.parseInt(parts[1].trim());
                if (w > 0 && h > 0) return new int[]{w, h};
            } catch (NumberFormatException ignored) {}
        }
        return new int[]{16, 9};
    }

    private static NativeImage generateBlueprintBackground(int w, int h) {
        NativeImage bg = new NativeImage(w, h, false);

        float cx = w / 2f;
        float cy = h / 2f;
        float maxDist = (float) Math.sqrt(cx * cx + cy * cy);

        int smallGrid = Math.max(4, w / 50);
        int largeGrid = smallGrid * 5;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy) / maxDist;
                float brightness = 1.0f - dist * 0.35f;

                int r = clamp8(Math.round(68 * brightness));
                int g = clamp8(Math.round(108 * brightness));
                int b = clamp8(Math.round(140 * brightness));

                boolean onLargeGrid = (x % largeGrid == 0) || (y % largeGrid == 0);
                boolean onSmallGrid = (x % smallGrid == 0) || (y % smallGrid == 0);

                if (onLargeGrid) {
                    r = clamp8(r + 45);
                    g = clamp8(g + 45);
                    b = clamp8(b + 45);
                } else if (onSmallGrid) {
                    r = clamp8(r + 22);
                    g = clamp8(g + 22);
                    b = clamp8(b + 22);
                }

                bg.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
            }
        }
        return bg;
    }

    private static NativeImage compositeRegionOnBackground(NativeImage background, NativeImage source,
            int srcX, int srcY, int cropW, int cropH, int padX, int padY, int bgW, int bgH) {
        NativeImage result = new NativeImage(bgW, bgH, false);
        for (int y = 0; y < bgH; y++) {
            for (int x = 0; x < bgW; x++) {
                result.setPixelRGBA(x, y, background.getPixelRGBA(x, y));
            }
        }

        for (int y = 0; y < cropH; y++) {
            for (int x = 0; x < cropW; x++) {
                int pixel = source.getPixelRGBA(srcX + x, srcY + y);
                int a = (pixel >> 24) & 0xFF;
                if (a == 0) continue;

                int dx = padX + x;
                int dy = padY + y;
                if (a == 255) {
                    result.setPixelRGBA(dx, dy, pixel);
                } else {
                    int bgPixel = result.getPixelRGBA(dx, dy);
                    int sr = pixel & 0xFF, sg = (pixel >> 8) & 0xFF, sb = (pixel >> 16) & 0xFF;
                    int dr = bgPixel & 0xFF, dg = (bgPixel >> 8) & 0xFF, db = (bgPixel >> 16) & 0xFF;
                    int or = sr + dr * (255 - a) / 255;
                    int og = sg + dg * (255 - a) / 255;
                    int ob = sb + db * (255 - a) / 255;
                    result.setPixelRGBA(dx, dy, 0xFF000000 | (ob << 16) | (og << 8) | or);
                }
            }
        }
        return result;
    }

    private static int clamp8(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static BufferedImage toScaledBufferedImage(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = image.getPixelRGBA(x, y);
                int r = pixel & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = (pixel >> 16) & 0xFF;
                buffered.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        int targetW = TARGET_WIDTH;
        int targetH = targetW * h / w;
        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(buffered, 0, 0, targetW, targetH, null);
        g2d.dispose();
        return scaled;
    }

    private static byte[] encodeImage(BufferedImage buffered, String format, float quality) throws Exception {
        if (format.equals("png")) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(buffered, "png", baos);
            return baos.toByteArray();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        writer.setOutput(ImageIO.createImageOutputStream(baos));
        writer.write(null, new IIOImage(buffered, null, null), param);
        writer.dispose();
        return baos.toByteArray();
    }

    public record RenderedFrame(String filename, byte[] data, boolean featured, String mimeType) {}
}
