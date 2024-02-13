package me.contaria.seedqueue.mixin.client.render;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import me.contaria.seedqueue.SeedQueue;
import me.contaria.seedqueue.interfaces.SQWorldRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin implements SQWorldRenderer {

    @Shadow
    @Final
    private MinecraftClient client;
    @Shadow
    private ClientWorld world;
    @Shadow
    private int frame;

    @Shadow
    protected abstract void setupTerrain(Camera camera, Frustum frustum, boolean hasForcedFrustum, int frame, boolean spectator);

    @Shadow
    protected abstract void updateChunks(long limitTime);

    @WrapWithCondition(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"
            )
    )
    private boolean doNotClearOnWallScreen(int mask, boolean getError, MatrixStack matrices) {
        return !SeedQueue.isOnWall();
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;updateChunks(J)V"
            )
    )
    private long limitChunkUpdatesOnWall(long limitTime) {
        if (SeedQueue.isOnWall()) {
            return 0;
        }
        return limitTime;
    }

    @Override
    public void seedQueue$buildChunks(MatrixStack matrices, Camera camera, Matrix4f projectionMatrix) {
        Profiler profiler = this.world.getProfiler();

        profiler.swap("light_updates");
        this.world.getChunkManager().getLightingProvider().doLightUpdates(Integer.MAX_VALUE, true, true);

        profiler.swap("culling");
        Vec3d pos = camera.getPos();
        Frustum frustum = new Frustum(matrices.peek().getModel(), projectionMatrix);
        frustum.setPosition(pos.getX(), pos.getY(), pos.getZ());

        profiler.swap("terrain_setup");
        this.setupTerrain(camera, frustum, false, this.frame++, this.client.player.isSpectator());

        profiler.swap("updatechunks");
        this.updateChunks(0);
    }
}
