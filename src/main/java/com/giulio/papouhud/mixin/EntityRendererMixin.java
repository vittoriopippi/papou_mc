package com.giulio.papouhud.mixin;

import com.giulio.papouhud.MobHealthLabel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** La vita dei mob sopra la testa (vedi MobHealthLabel): letta con lo stato dell'entita', disegnata con il suo nome. */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void papouhud$extractMobHealth(T entity, S state, float partialTick, CallbackInfo ci) {
		MobHealthLabel.extract(entity, state, partialTick);
	}

	@Inject(method = "submit", at = @At("TAIL"))
	private void papouhud$submitMobHealth(S state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera, CallbackInfo ci) {
		MobHealthLabel.submit(state, poseStack, collector, camera);
	}
}
