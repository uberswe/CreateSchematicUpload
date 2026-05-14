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

    private static final int PIXELS_PER_BLOCK = 48;
    private static final int MAX_FB_SIZE = 2048;
    private static final int MIN_FB_SIZE = 512;

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
                int theoreticalFbSize = maxDim * PIXELS_PER_BLOCK * 4;
                int fbSize;
                float effectivePixelWidth = PIXELS_PER_BLOCK;

                if (theoreticalFbSize > maxSupported) {
                    fbSize = maxSupported;
                    effectivePixelWidth = (float) maxSupported / (maxDim * 4.0f);
                } else {
                    fbSize = Math.max(MIN_FB_SIZE, theoreticalFbSize);
                }

                renderTarget = new TextureTarget(fbSize, fbSize, true, Minecraft.ON_OSX);

                SchematicLevel schematicLevel = new SchematicLevel(BlockPos.ZERO, mc.level);
                StructurePlaceSettings settings = new StructurePlaceSettings();
                template.placeInWorld(schematicLevel, BlockPos.ZERO, BlockPos.ZERO, settings, mc.level.random, Block.UPDATE_CLIENTS);

                SchematicRenderer renderer = new SchematicRenderer(schematicLevel);

                Vector3f light0 = new Vector3f(-1.0f, 1.2f, -0.8f).normalize();
                Vector3f light1 = new Vector3f(0.5f, -0.2f, 1.0f).normalize();
                RenderSystem.setShaderLights(light0, light1);

                Matrix4f projectionMatrix = new Matrix4f().setOrtho(
                        -fbSize / 2f, fbSize / 2f,
                        -fbSize / 2f, fbSize / 2f,
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

                    NativeImage image = new NativeImage(fbSize, fbSize, false);
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
                NativeImage cropped = cropTransparent(raw);
                try (cropped) {
                    byte[] png = toPngBytes(cropped);
                    boolean featured = FEATURED_FRAMES.contains(i);
                    String filename = String.format(featured ? "frame_%03d_featured.png" : "frame_%03d.png", i);
                    result.add(new RenderedFrame(filename, png, featured));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process frame {}", i, e);
            }
        }
        return result;
    }

    private static NativeImage cropTransparent(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((image.getPixelRGBA(x, y) >> 24) & 0xFF) > 0) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            NativeImage fallback = new NativeImage(1, 1, false);
            fallback.setPixelRGBA(0, 0, 0);
            return fallback;
        }

        int cropW = maxX - minX + 1;
        int cropH = maxY - minY + 1;
        NativeImage cropped = new NativeImage(cropW, cropH, false);
        for (int y = 0; y < cropH; y++) {
            for (int x = 0; x < cropW; x++) {
                cropped.setPixelRGBA(x, y, image.getPixelRGBA(minX + x, minY + y));
            }
        }
        return cropped;
    }

    private static byte[] toPngBytes(NativeImage image) throws Exception {
        Path temp = Files.createTempFile("schematic_render_", ".png");
        try {
            image.writeToFile(temp);
            return Files.readAllBytes(temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public record RenderedFrame(String filename, byte[] data, boolean featured) {}
}
