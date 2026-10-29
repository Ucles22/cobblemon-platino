package com.platino;

import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.ResourceManager;

final class RecoloredTexture extends AbstractTexture {
	RecoloredTexture(NativeImage image) {
		try {
			TextureUtil.prepareImage(getGlId(), 0, image.getWidth(), image.getHeight());
			image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), false, false, false, false);
		} finally {
			image.close();
		}
	}

	@Override
	public void load(ResourceManager manager) {
	}
}
