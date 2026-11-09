package com.giulio.papouhud;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: XP accumulata nella fornace aperta nel menu {@code containerId}. */
record FurnaceXpPayload(int containerId, float xp) implements CustomPacketPayload {
	static final Type<FurnaceXpPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("papouhud", "furnace_xp"));
	static final StreamCodec<RegistryFriendlyByteBuf, FurnaceXpPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FurnaceXpPayload::containerId,
			ByteBufCodecs.FLOAT, FurnaceXpPayload::xp,
			FurnaceXpPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
