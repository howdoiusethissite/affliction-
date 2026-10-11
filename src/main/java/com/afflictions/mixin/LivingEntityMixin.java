package com.afflictions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.afflictions.health.MaxHealthManager;
import com.afflictions.infection.InfectionManager;

/** Fabric has no event for finishing an eat or drink, so treatments hook the moment the use completes. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void afflictions$onCompleteUsing(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			ItemStack stack = player.getUseItem().copy();
			if (stack.isEmpty()) return;
			InfectionManager.onItemConsumed(player, stack);
			MaxHealthManager.onItemConsumed(player, stack);
		}
	}
}
