package com.uberswe.createschematichelper;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Bakes a pose into vertices before delegating, so geometry emitted in chunk-local
 * coordinates (e.g. {@code BlockRenderDispatcher.renderLiquid}) lands in the schematic's
 * transformed space. Adapted from Create: Blueprinted by salem-5/swzo (MIT).
 */
final class PoseAppliedVertexConsumer implements VertexConsumer {

    private VertexConsumer delegate;
    private final Matrix4f pose = new Matrix4f();
    private final Matrix3f normal = new Matrix3f();
    private float offX, offY, offZ;
    private final Vector3f scratch = new Vector3f();

    void prepare(VertexConsumer delegate, Matrix4f pose, Matrix3f normal, float offX, float offY, float offZ) {
        this.delegate = delegate;
        this.pose.set(pose);
        this.normal.set(normal);
        this.offX = offX;
        this.offY = offY;
        this.offZ = offZ;
    }

    @Override
    public @NotNull VertexConsumer addVertex(float x, float y, float z) {
        pose.transformPosition(x + offX, y + offY, z + offZ, scratch);
        delegate.addVertex(scratch.x(), scratch.y(), scratch.z());
        return this;
    }

    @Override
    public @NotNull VertexConsumer setNormal(float x, float y, float z) {
        normal.transform(x, y, z, scratch);
        delegate.setNormal(scratch.x(), scratch.y(), scratch.z());
        return this;
    }

    @Override
    public @NotNull VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public @NotNull VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public @NotNull VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public @NotNull VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }
}
