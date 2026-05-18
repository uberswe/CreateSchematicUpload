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
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
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

    private static final int FRAME_COUNT = 120;
    private static final float DEGREES_PER_FRAME = 360f / FRAME_COUNT;
    private static final float START_ANGLE = 45f;
    private static final float[] FEATURED_ANGLES = {45f, 135f, 225f, 315f};

    private static final int PIXELS_PER_BLOCK = 32;
    private static final int MAX_FB_SIZE = 2048;
    private static final int MIN_FB_SIZE = 256;

    public static CompletableFuture<List<RenderedFrame>> render360(Path nbtFile) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream is = Files.newInputStream(nbtFile)) {
                return NbtIo.readCompressed(is);
            } catch (Exception e) {
                throw new RuntimeException("Failed to read NBT file", e);
            }
        }).thenCompose((CompoundTag tag) -> SchematicIsometricRenderer.render360(tag));
    }

    public static CompletableFuture<List<RenderedFrame>> render360(CompoundTag tag) {
        CompletableFuture<List<NativeImage>> renderFuture = new CompletableFuture<>();

        RenderSystem.recordRenderCall(() -> {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget renderTarget = null;
            try {
                if (mc.level == null) {
                    renderFuture.completeExceptionally(new IllegalStateException("No active world"));
                    return;
                }

                StructureTemplate template = new StructureTemplate();
                template.load(mc.level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
                Vec3i size = template.getSize();
                int maxDim = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));

                int maxSupported = Math.min(MAX_FB_SIZE, RenderSystem.maxSupportedTextureSize());
                int theoreticalH = maxDim * PIXELS_PER_BLOCK * 2;
                int fbH;
                float effectivePixelWidth = PIXELS_PER_BLOCK;

                if (theoreticalH > maxSupported) {
                    fbH = maxSupported;
                    effectivePixelWidth = (float) maxSupported / (maxDim * 2.0f);
                } else {
                    fbH = Math.max(MIN_FB_SIZE, theoreticalH);
                }
                int fbW = fbH * 16 / 9;

                renderTarget = new TextureTarget(fbW, fbH, true, Minecraft.ON_OSX);

                SchematicLevel schematicLevel = new FixedLightSchematicLevel(BlockPos.ZERO, mc.level);
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

                float scale = effectivePixelWidth / (float) Math.sqrt(2);

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
                    angles = new float[FRAME_COUNT];
                    for (int i = 0; i < FRAME_COUNT; i++) {
                        angles[i] = START_ANGLE + i * DEGREES_PER_FRAME;
                    }
                } else {
                    angles = FEATURED_ANGLES;
                }

                List<NativeImage> images = new ArrayList<>();
                for (int i = 0; i < angles.length; i++) {
                    float yRot = angles[i];

                    renderTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                    renderTarget.clear(Minecraft.ON_OSX);
                    renderTarget.bindWrite(true);

                    PoseStack poseStack = new PoseStack();
                    poseStack.pushPose();
                    poseStack.scale(scale, scale, scale);
                    poseStack.mulPose(Axis.XP.rotationDegrees(ISOMETRIC_PITCH));
                    poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
                    poseStack.translate(-size.getX() / 2f, -size.getY() / 2f, -size.getZ() / 2f);

                    renderer.render(poseStack, buffers);
                    buffers.draw();
                    poseStack.popPose();

                    NativeImage image = new NativeImage(fbW, fbH, false);
                    RenderSystem.bindTexture(renderTarget.getColorTextureId());
                    image.downloadTexture(0, false);
                    image.flipY();
                    images.add(image);
                }

                renderTarget.destroyBuffers();
                mc.getMainRenderTarget().bindWrite(true);
                renderFuture.complete(images);
            } catch (Exception e) {
                if (renderTarget != null) {
                    renderTarget.destroyBuffers();
                    mc.getMainRenderTarget().bindWrite(true);
                }
                renderFuture.completeExceptionally(e);
            }
        });

        return renderFuture
                .thenApplyAsync(SchematicIsometricRenderer::cropAndConvert)
                .orTimeout(2, TimeUnit.MINUTES);
    }

    private static List<RenderedFrame> cropAndConvert(List<NativeImage> rawImages) {
        int fbSize = rawImages.isEmpty() ? 1 : rawImages.get(0).getWidth();
        int unionMinX = fbSize, unionMinY = fbSize, unionMaxX = -1, unionMaxY = -1;

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
        int bgW = cropW + padding * 2;
        int bgH = cropH + padding * 2;
        NativeImage background = generateBlueprintBackground(bgW, bgH);

        Set<Integer> featuredIndices = computeFeaturedIndices(rawImages.size());

        List<RenderedFrame> result = new ArrayList<>();
        for (int i = 0; i < rawImages.size(); i++) {
            NativeImage raw = rawImages.get(i);
            try (raw) {
                NativeImage composited = compositeRegionOnBackground(background, raw,
                        unionMinX, unionMinY, cropW, cropH, padding, bgW, bgH);
                try (composited) {
                    String format = ConfigValues.imageFormat;
                    String ext = format.equals("jpeg") ? "jpg" : format;
                    byte[] imageBytes = toImageBytes(composited, format);
                    boolean featured = featuredIndices.contains(i);
                    String filename = String.format(featured ? "frame_%03d_featured.%s" : "frame_%03d.%s", i, ext);
                    String mimeType = format.equals("jpeg") ? "image/jpeg" : "image/png";
                    result.add(new RenderedFrame(filename, imageBytes, featured, mimeType));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process frame {}", i, e);
            }
        }
        background.close();
        return result;
    }

    private static Set<Integer> computeFeaturedIndices(int frameCount) {
        if (frameCount <= FEATURED_ANGLES.length) {
            Set<Integer> all = new java.util.HashSet<>();
            for (int i = 0; i < frameCount; i++) all.add(i);
            return all;
        }
        Set<Integer> indices = new java.util.HashSet<>();
        for (float angle : FEATURED_ANGLES) {
            indices.add(Math.round((angle - START_ANGLE) / DEGREES_PER_FRAME));
        }
        return indices;
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
            int srcX, int srcY, int cropW, int cropH, int padding, int bgW, int bgH) {
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

                int dx = padding + x;
                int dy = padding + y;
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

    private static byte[] toImageBytes(NativeImage image, String format) throws Exception {
        if (format.equals("png")) {
            Path temp = Files.createTempFile("schematic_render_", ".png");
            try {
                image.writeToFile(temp);
                return Files.readAllBytes(temp);
            } finally {
                Files.deleteIfExists(temp);
            }
        }

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

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);
        writer.setOutput(ImageIO.createImageOutputStream(baos));
        writer.write(null, new IIOImage(buffered, null, null), param);
        writer.dispose();
        return baos.toByteArray();
    }

    public record RenderedFrame(String filename, byte[] data, boolean featured, String mimeType) {}

    private static class FixedLightSchematicLevel extends SchematicLevel {
        public FixedLightSchematicLevel(BlockPos anchor, Level level) {
            super(anchor, level);
        }

        @Override
        public int getBrightness(@NotNull LightLayer layer, @NotNull BlockPos pos) {
            return 15;
        }

        @Override
        public int getMaxLocalRawBrightness(@NotNull BlockPos pos) {
            return 15;
        }

        @Override
        public int getSkyDarken() {
            return 0;
        }

        @Override
        public boolean isRaining() {
            return false;
        }

        @Override
        public boolean isThundering() {
            return false;
        }

        @Override
        public float getRainLevel(float delta) {
            return 0f;
        }

        @Override
        public float getThunderLevel(float delta) {
            return 0f;
        }
    }
}
