package com.afflictions.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Tier is one of wood, iron, redstone, netherite. */
public record ImplantEntry(String tier, int charge) {
	public static final Codec<ImplantEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("tier").forGetter(ImplantEntry::tier),
			Codec.INT.optionalFieldOf("charge", 0).forGetter(ImplantEntry::charge)
	).apply(i, ImplantEntry::new));

	public static final StreamCodec<io.netty.buffer.ByteBuf, ImplantEntry> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, ImplantEntry::tier,
			ByteBufCodecs.VAR_INT, ImplantEntry::charge,
			ImplantEntry::new);

	public ImplantEntry withTier(String tier) {
		return new ImplantEntry(tier, this.charge);
	}

	public ImplantEntry withCharge(int charge) {
		return new ImplantEntry(this.tier, charge);
	}
}
