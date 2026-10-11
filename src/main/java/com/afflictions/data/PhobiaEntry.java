package com.afflictions.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record PhobiaEntry(int exposureKills) {
	public static final PhobiaEntry EMPTY = new PhobiaEntry(0);

	public static final Codec<PhobiaEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("exposure_kills", 0).forGetter(PhobiaEntry::exposureKills)
	).apply(i, PhobiaEntry::new));

	public static final StreamCodec<io.netty.buffer.ByteBuf, PhobiaEntry> STREAM_CODEC =
			ByteBufCodecs.VAR_INT.map(PhobiaEntry::new, PhobiaEntry::exposureKills);

	public PhobiaEntry withExposureKills(int kills) {
		return new PhobiaEntry(kills);
	}
}
