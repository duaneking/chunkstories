package io.xol.chunkstories.renderer.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;

import io.xol.chunkstories.api.rendering.textures.Texture1D;
import io.xol.chunkstories.api.rendering.textures.TextureFormat;
import io.xol.chunkstories.api.voxel.textures.VoxelTexture;
import io.xol.chunkstories.voxel.VoxelTextureAtlased;
import io.xol.chunkstories.voxel.VoxelsStore;
import io.xol.engine.graphics.textures.Texture1DGL;

public class VoxelTexturesColours {

	private Texture1DGL blockTexturesSummary;
	
	public VoxelTexturesColours() {
		blockTexturesSummary = new Texture1DGL(TextureFormat.RGBA_8BPP);
		
		int size = 512;
		ByteBuffer bb = ByteBuffer.allocateDirect(size * 4);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		int counter = 0;
		Iterator<VoxelTexture> i = VoxelsStore.get().textures().all();
		while (i.hasNext() && counter < size)
		{
			VoxelTextureAtlased voxelTexture = (VoxelTextureAtlased)i.next();

			bb.put((byte) (voxelTexture.getColor().x() * 255));
			bb.put((byte) (voxelTexture.getColor().y() * 255));
			bb.put((byte) (voxelTexture.getColor().z() * 255));
			bb.put((byte) (voxelTexture.getColor().w() * 255));

			voxelTexture.positionInColorIndex = counter;
			counter++;
		}

		//Padding
		while (counter < size)
		{
			bb.put((byte) (0));
			bb.put((byte) (0));
			bb.put((byte) (0));
			bb.put((byte) (0));
			counter++;
		}

		bb.flip();

		blockTexturesSummary.uploadTextureData(size, bb);
		blockTexturesSummary.setLinearFiltering(false);
	}
	
	public Texture1D get() {
		return blockTexturesSummary;
	}
}