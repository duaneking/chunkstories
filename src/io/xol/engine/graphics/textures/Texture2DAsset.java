package io.xol.engine.graphics.textures;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

import de.matthiasmann.twl.utils.PNGDecoder;
import de.matthiasmann.twl.utils.PNGDecoder.Format;
import io.xol.chunkstories.api.mods.Asset;
import io.xol.chunkstories.tools.ChunkStoriesLogger;
import io.xol.engine.base.GameWindowOpenGL;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class Texture2DAsset extends Texture2D
{
	//String name;
	Asset asset;
	String assetName;
	
	public Texture2DAsset(TextureFormat type)
	{
		super(type);
	}

	public Texture2DAsset(Asset asset)
	{
		this(TextureFormat.RGBA_8BPP);
		this.assetName = asset.getName();
		this.asset = asset;
		loadTextureFromAsset();
	}

	public void bind()
	{
		super.bind();
		
		if (scheduledForLoad && asset != null)
		{
			long ms = System.currentTimeMillis();
			System.out.print("main thread called, actually loading the texture ... ");
			this.loadTextureFromAsset();
			System.out.print((System.currentTimeMillis()-ms) + "ms \n");
		}
	}
	
	public int loadTextureFromAsset()
	{
		if (!GameWindowOpenGL.isMainGLWindow())
		{
			System.out.println("isn't main thread, scheduling load");
			scheduledForLoad = true;
			return -1;
		}
		scheduledForLoad = false;

		//TODO we probably don't need half this shit
		bind();
		try
		{
			InputStream is = asset.read();
			PNGDecoder decoder = new PNGDecoder(is);
			width = decoder.getWidth();
			height = decoder.getHeight();
			ByteBuffer temp = ByteBuffer.allocateDirect(4 * width * height);
			decoder.decode(temp, width * 4, Format.RGBA);
			is.close();
			
			//ChunkStoriesLogger.getInstance().log("decoded " + width + " by " + height + " pixels (" + name + ")", ChunkStoriesLogger.LogType.RENDERING, ChunkStoriesLogger.LogLevel.DEBUG);
			temp.flip();
			bind();
			glTexImage2D(GL_TEXTURE_2D, 0, type.getInternalFormat(), width, height, 0, type.getFormat(), type.getType(), (ByteBuffer) temp);
		
			applyTextureParameters();

		}
		catch (FileNotFoundException e)
		{
			ChunkStoriesLogger.getInstance().info("Clouldn't find file : " + e.getMessage());
		}
		catch (IOException e)
		{
			ChunkStoriesLogger.getInstance().warning("Error loading file : " + e.getMessage());
			e.printStackTrace();
		}
		mipmapsUpToDate = false;
		return glId;
	}
	
	// Texture modifications

	public void setAsset(Asset newAsset)
	{
		this.asset = newAsset;
	}

	public String getName()
	{
		if(assetName != null)
			return assetName;
		
		//TODO split loaded textures from vanilla ones
		throw new UnsupportedOperationException();
	}
}
