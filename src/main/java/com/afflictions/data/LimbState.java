package com.afflictions.data;

import java.util.Locale;

import com.mojang.serialization.Codec;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum LimbState implements StringRepresentable {
	INTACT, AMPUTATED;

	public static final Codec<LimbState> CODEC = StringRepresentable.fromEnum(LimbState::values);
	public static final StreamCodec<io.netty.buffer.ByteBuf, LimbState> STREAM_CODEC =
			ByteBufCodecs.STRING_UTF8.map(s -> LimbState.valueOf(s.toUpperCase(Locale.ROOT)), LimbState::name);

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
