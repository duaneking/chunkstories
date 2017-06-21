package io.xol.engine.graphics.textures;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import de.matthiasmann.twl.utils.PNGDecoder;
import de.matthiasmann.twl.utils.PNGDecoder.Format;
import io.xol.chunkstories.api.mods.Asset;
import io.xol.chunkstories.api.rendering.target.RenderTarget;
import io.xol.chunkstories.api.rendering.textures.Cubemap;
import io.xol.chunkstories.api.rendering.textures.TextureFormat;
import io.xol.chunkstories.api.util.ChunkStoriesLogger.LogLevel;
import io.xol.chunkstories.client.Client;
import io.xol.chunkstories.tools.ChunkStoriesLoggerImplementation;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

//(c) 2015-2017 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class CubemapGL extends TextureGL implements Cubemap
{
	String name;
	
	Face faces[] = new Face[6];
	int size;
	
	public CubemapGL(TextureFormat type, int size)
	{
		super(type);
		this.size = size;

		aquireID();
		bind();
		
		glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		// Anti seam
		glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		
		for(int i = 0; i < 6; i++)
			faces[i] = new Face(i);
	}
	
	public CubemapGL(String name)
	{
		this(TextureFormat.RGBA_8BPP, 0);
		this.name = name;
		loadCubemapFromDisk();
	}
	
	public void bind()
	{
		if(glId == -1)
			aquireID();
		
		//Don't bother
		if (glId == -2)
		{
			ChunkStoriesLoggerImplementation.getInstance().log("Critical mess-up: Tried to bind a destroyed Cubemap "+this+". Terminating process immediately.", LogLevel.CRITICAL);
			ChunkStoriesLoggerImplementation.getInstance().save();
			Thread.dumpStack();
			System.exit(-803);
			//throw new RuntimeException("Tryed to bind a destroyed VerticesBuffer");
		}
		
		glBindTexture(GL_TEXTURE_CUBE_MAP, glId);
	}
	
	public int loadCubemapFromDisk()
	{
		bind();
		
		ByteBuffer temp;
		String[] names = { "right", "left", "top", "bottom", "front", "back" };
		if (Client.getInstance().getContent().getAsset((name + "/front.png")) == null)
		{
			ChunkStoriesLoggerImplementation.getInstance().log("Can't find front.png from CS-format skybox, trying MC format.", ChunkStoriesLoggerImplementation.LogType.RENDERING, ChunkStoriesLoggerImplementation.LogLevel.WARN);
			names = new String[] { "panorama_1", "panorama_3", "panorama_4", "panorama_5", "panorama_0", "panorama_2" };
		}
		try
		{
			for (int i = 0; i < 6; i++)
			{
				Asset pngFile = Client.getInstance().getContent().getAsset(name + "/" + names[i] + ".png");
				if(pngFile == null)
					throw new FileNotFoundException(name + "/" + names[i] + ".png");
				PNGDecoder decoder = new PNGDecoder(pngFile.read());
				temp = ByteBuffer.allocateDirect(4 * decoder.getWidth() * decoder.getHeight());
				decoder.decode(temp, decoder.getWidth() * 4, Format.RGBA);
				temp.flip();
				
				this.size = decoder.getHeight();
				glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, type.getInternalFormat(), decoder.getWidth(), decoder.getHeight(), 0, type.getFormat(), type.getType(), temp);
				// Anti alias
				glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
				glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
				// Anti seam
				glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
				glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
			}
		}
		catch(FileNotFoundException e)
		{
			ChunkStoriesLoggerImplementation.getInstance().info("Clouldn't find file : "+e.getMessage());
		}
		catch (IOException e)
		{
			ChunkStoriesLoggerImplementation.getInstance().log("Failed to load properly cubemap : " + name, ChunkStoriesLoggerImplementation.LogType.RENDERING, ChunkStoriesLoggerImplementation.LogLevel.WARN);
		}
		return glId;
	}
	
	/* (non-Javadoc)
	 * @see io.xol.engine.graphics.textures.CubemapI#getSize()
	 */
	@Override
	public int getSize()
	{
		return size;
	}
	
	public int getID()
	{
		if(glId == -1)
			aquireID();
		return glId;
	}
	
	public void free()
	{
		if(glId == -1)
			return;
		glDeleteTextures(glId);
		glId = -1;
	}
	
	public class Face implements RenderTarget {
		
		int face;
		int textureType;

		public Face(int i)
		{
			face = i;
			textureType = GL_TEXTURE_CUBE_MAP_POSITIVE_X + i;

			glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, type.getInternalFormat(), size, size, 0, type.getFormat(), type.getType(), (ByteBuffer)null);
		}
		
		@Override
		public void attachAsDepth()
		{
			glFramebufferTexture2D( GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, textureType, getID(), 0);
		}

		@Override
		public void attachAsColor(int colorAttachement)
		{
			glFramebufferTexture2D( GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + colorAttachement, textureType, getID(), 0);
		}

		@Override
		public void resize(int w, int h)
		{
			throw new UnsupportedOperationException("Individual cubemaps face should not be resized.");
		}

		@Override
		public boolean destroy()
		{
			if(glId == -1)
				return false;
			glDeleteTextures(glId);
			glId = -1;
			return true;
		}

		@Override
		public int getWidth()
		{
			return size;
		}

		@Override
		public int getHeight()
		{
			return size;
		}
	}

	/* (non-Javadoc)
	 * @see io.xol.engine.graphics.textures.CubemapI#getFace(int)
	 */
	@Override
	public RenderTarget getFace(int f)
	{
		return faces[f];
	}

	/* (non-Javadoc)
	 * @see io.xol.engine.graphics.textures.CubemapI#getVramUsage()
	 */
	@Override
	public long getVramUsage()
	{
		return type.getBytesPerTexel() * 6 * size * size;
	}
	
}