package com.afflictions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.afflictions.fear.FearEffects;

/** Fabric has no event for experience gain, so the Breaking tier scales it where the points are handed out. */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@ModifyVariable(method = "giveExperiencePoints", at = @At("HEAD"), argsOnly = true)
	private int afflictions$scaleExperience(int amount) {
		if ((Object) this instanceof ServerPlayer player) return FearEffects.scaleXp(player, amount);
		return amount;
	}
}
