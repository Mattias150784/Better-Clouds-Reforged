package net.cloud.betterclouds.forge.mixin;

import net.cloud.betterclouds.forge.Config;
import net.cloud.betterclouds.forge.Main;
import net.cloud.betterclouds.forge.clouds.Debug;
import net.cloud.betterclouds.forge.clouds.Renderer;
import net.cloud.betterclouds.forge.compat.Telemetry;
import net.cloud.betterclouds.forge.renderdoc.RenderDoc;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL32;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.cloud.betterclouds.forge.Main.glCompat;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Unique
    private final Vector3d tempVector = new Vector3d();

    @Unique
    private Renderer cloudRenderer;
    @Shadow
    private Frustum frustum;

    @Unique
    private double profTimeAcc;
    @Unique
    private int profFrames;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(MinecraftClient client, EntityRenderDispatcher entityRenderDispatcher, BlockEntityRenderDispatcher blockEntityRenderDispatcher, BufferBuilderStorage bufferBuilders, CallbackInfo ci) {
        if (glCompat.isIncompatible()) return;
        cloudRenderer = new Renderer(client);
    }

    @Shadow
    private @Nullable Frustum capturedFrustum;

    @Shadow
    @Final
    private Vector3d capturedFrustumPosition;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private @Nullable ClientWorld world;

    @Shadow
    private int ticks;

    @Inject(at = @At("TAIL"), method = "reload(Lnet/minecraft/resource/ResourceManager;)V")
    private void onReload(ResourceManager manager, CallbackInfo ci) {
        if (glCompat.isIncompatible()) return;
        try {
            if (cloudRenderer != null) cloudRenderer.reload(manager);
        } catch (Exception e) {
            Telemetry.INSTANCE.sendUnhandledException(e);
            System.out.println(e);
        }
    }

    @Inject(at = @At("TAIL"), method = "setWorld")
    private void onSetWorld(ClientWorld world, CallbackInfo ci) {
        if (cloudRenderer != null) cloudRenderer.setWorld(world);
    }

    @Inject(at = @At("HEAD"), method = "renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FDDD)V", cancellable = true)
    private void renderClouds(MatrixStack matrices, Matrix4f projMat, float tickDelta, double camX, double camY, double camZ, CallbackInfo ci) {
        if (cloudRenderer == null) return;
        if (glCompat.isIncompatible()) return;
        if (world == null) return;
        //TODO if (!Config.enabledDimensions.contains(world.getDimensionKey())) return;
        if (!Config.enabled.get()) return;

        client.getProfiler().push(Main.MODID);
        glCompat.pushDebugGroupDev("Better Clouds");

        Vector3d cam = tempVector.set(camX, camY, camZ);
        Frustum frustum = this.frustum;
        Vector3d frustumPos = cam;
        if (capturedFrustum != null) {
            frustum = capturedFrustum;
            frustum.setPosition(capturedFrustumPosition.x, this.capturedFrustumPosition.y, this.capturedFrustumPosition.z);
            frustumPos = capturedFrustumPosition;
        }

        if (Main.isProfilingEnabled()) GL32.glFinish();
        long startTime = System.nanoTime();

        int ticks = this.ticks;
        if(Debug.animationPause >= 0) {
            if(Debug.animationPause == 0) Debug.animationPause = ticks;
            else ticks = Debug.animationPause;
            tickDelta = 0;
        }

        matrices.push();
        try {
            Renderer.PrepareResult prepareResult = cloudRenderer.prepare(matrices, projMat, ticks, tickDelta, cam);
            if(RenderDoc.isFrameCapturing()) glCompat.debugMessage("renderer prepare returned " + prepareResult.name());
            if (prepareResult == Renderer.PrepareResult.RENDER) {
                ci.cancel();
                cloudRenderer.render(ticks, tickDelta, cam, frustumPos, frustum);
            } else if(prepareResult == Renderer.PrepareResult.NO_RENDER) {
                ci.cancel();
            }
        } catch (Exception e) {
            Telemetry.INSTANCE.sendUnhandledException(e);
            throw e;
        }
        matrices.pop();

        if (Main.isProfilingEnabled()) {
            GL32.glFinish();
            profTimeAcc += (System.nanoTime() - startTime) / 1e6;
            profFrames++;
            if (profFrames >= Debug.profileInterval) {
                Main.debugChatMessage("profiling.cpuTimes", profTimeAcc / profFrames);
                profFrames = 0;
                profTimeAcc = 0;
            }
        }

        client.getProfiler().pop();
        glCompat.popDebugGroupDev();
    }


    @Inject(at = @At("HEAD"), method = "close")
    private void close(CallbackInfo ci) {
        if (cloudRenderer != null) cloudRenderer.close();
    }
}
