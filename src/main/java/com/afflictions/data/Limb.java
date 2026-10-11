package com.afflictions.data;

import java.util.Locale;

import com.mojang.serialization.Codec;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum Limb implements StringRepresentable {
	LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG;

	public static final Codec<Limb> CODEC = StringRepresentable.fromEnum(Limb::values);
	public static final StreamCodec<io.netty.buffer.ByteBuf, Limb> STREAM_CODEC =
			ByteBufCodecs.STRING_UTF8.map(s -> Limb.valueOf(s.toUpperCase(Locale.ROOT)), Limb::name);

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
