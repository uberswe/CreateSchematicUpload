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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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

    private static final Set<Integer> FEATURED_FRAMES = Set.of(
            Math.round(45f / DEGREES_PER_FRAME),
            Math.round(135f / DEGREES_PER_FRAME),
            Math.round(225f / DEGREES_PER_FRAME),
            Math.round(315f / DEGREES_PER_FRAME)
    );

    private static final int PIXELS_PER_BLOCK = 32;
    private static final int MAX_FB_SIZE = 2048;
    private static final int MIN_FB_SIZE = 256;

    public static CompletableFuture<List<RenderedFrame>> render360(Path nbtFile) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream is = Files.newInputStream(nbtFile)) {
                return NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
            } catch (Exception e) {
                throw new RuntimeException("Failed to read NBT file", e);
            }
        }).thenCompose(SchematicIsometricRenderer::render360);
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
                template.load(mc.level.holderLookup(Registries.BLOCK), tag);
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
                placeFloor(schematicLevel, size, maxDim);

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

                List<NativeImage> images = new ArrayList<>();
                for (int i = 0; i < FRAME_COUNT; i++) {
                    float yRot = i * DEGREES_PER_FRAME;

                    renderTarget.setClearColor(0.78f, 0.78f, 0.76f, 1.0f);
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
        List<RenderedFrame> result = new ArrayList<>();
        for (int i = 0; i < rawImages.size(); i++) {
            NativeImage raw = rawImages.get(i);
            try (raw) {
                String format = ConfigValues.imageFormat;
                String ext = format.equals("jpeg") ? "jpg" : format;
                byte[] imageBytes = toImageBytes(raw, format);
                boolean featured = FEATURED_FRAMES.contains(i);
                String filename = String.format(featured ? "frame_%03d_featured.%s" : "frame_%03d.%s", i, ext);
                String mimeType = format.equals("jpeg") ? "image/jpeg" : "image/png";
                result.add(new RenderedFrame(filename, imageBytes, featured, mimeType));
            } catch (Exception e) {
                LOGGER.error("Failed to process frame {}", i, e);
            }
        }
        return result;
    }

    private static void placeFloor(SchematicLevel level, Vec3i schematicSize, int maxDim) {
        BlockState concrete = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
        int extent = maxDim * 4 + 16;
        int cx = schematicSize.getX() / 2;
        int cz = schematicSize.getZ() / 2;
        for (int x = cx - extent; x <= cx + extent; x++) {
            for (int z = cz - extent; z <= cz + extent; z++) {
                boolean useSnow = ((Math.floorDiv(x, 4) + Math.floorDiv(z, 4)) % 2 == 0);
                level.setBlock(new BlockPos(x, -1, z), useSnow ? concrete : snow, Block.UPDATE_CLIENTS);
            }
        }
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
