//
// This file is a part of the Chunk Stories API codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.engine.graphics.textures;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import io.xol.chunkstories.api.exceptions.rendering.NotEnoughtTextureUnitsException;
import io.xol.chunkstories.api.rendering.RenderingInterface;
import io.xol.chunkstories.api.rendering.pipeline.TexturingConfiguration;
import io.xol.chunkstories.api.rendering.textures.ArrayTexture;
import io.xol.chunkstories.api.rendering.textures.Cubemap;
import io.xol.chunkstories.api.rendering.textures.Texture;
import io.xol.chunkstories.api.rendering.textures.Texture1D;
import io.xol.chunkstories.api.rendering.textures.Texture2D;
import io.xol.chunkstories.client.RenderingConfig;
import io.xol.engine.graphics.shaders.ShaderProgram;

import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.glUniform1i;

public class TexturingConfigurationImplementation implements TexturingConfiguration
{
	private Map<String, Texture1D> textures1d;
	private Map<String, Texture2D> textures2d;
	private Map<String, Cubemap> cubemaps;
	private Map<String, ArrayTexture> arrayTextures;

	public TexturingConfigurationImplementation()
	{
		this.textures1d = new HashMap<String, Texture1D>();
		this.textures2d = new HashMap<String, Texture2D>();
		this.cubemaps = new HashMap<String, Cubemap>();
		this.arrayTextures = new HashMap<String, ArrayTexture>();
	}

	public TexturingConfigurationImplementation(Map<String, Texture1D> textures1d, Map<String, Texture2D> textures2d, Map<String, Cubemap> cubemaps, Map<String, ArrayTexture> arrayTextures)
	{
		this.textures1d = textures1d;
		this.textures2d = textures2d;
		this.cubemaps = cubemaps;
		this.arrayTextures = arrayTextures;
	}

	public TexturingConfigurationImplementation bindTexture1D(String textureSamplerName, Texture1D texture)
	{
		Map<String, Texture1D> textures1d = new HashMap<String, Texture1D>();
		for (Entry<String, Texture1D> e : this.textures1d.entrySet())
		{
			textures1d.put(e.getKey(), e.getValue());
		}
		textures1d.put(textureSamplerName, texture);

		return new TexturingConfigurationImplementation(textures1d, textures2d, cubemaps, arrayTextures);
	}

	public TexturingConfigurationImplementation bindTexture2D(String textureSamplerName, Texture2D texture)
	{
		Map<String, Texture2D> textures2d = new HashMap<String, Texture2D>();
		for (Entry<String, Texture2D> e : this.textures2d.entrySet())
		{
			textures2d.put(e.getKey(), e.getValue());
		}
		textures2d.put(textureSamplerName, texture);

		return new TexturingConfigurationImplementation(textures1d, textures2d, cubemaps, arrayTextures);
	}

	public TexturingConfigurationImplementation bindCubemap(String cubemapSamplerName, Cubemap cubemapTexture)
	{
		Map<String, Cubemap> cubemaps = new HashMap<String, Cubemap>();
		for (Entry<String, Cubemap> e : this.cubemaps.entrySet())
		{
			cubemaps.put(e.getKey(), e.getValue());
		}
		cubemaps.put(cubemapSamplerName, cubemapTexture);

		return new TexturingConfigurationImplementation(textures1d, textures2d, cubemaps, arrayTextures);
	}

	public TexturingConfigurationImplementation bindArrayTexture(String textureSamplerName, ArrayTexture texture) {
		Map<String, ArrayTexture> arrayTextures = new HashMap<String, ArrayTexture>();
		for (Entry<String, ArrayTexture> e : this.arrayTextures.entrySet())
		{
			arrayTextures.put(e.getKey(), e.getValue());
		}
		arrayTextures.put(textureSamplerName, texture);

		return new TexturingConfigurationImplementation(textures1d, textures2d, cubemaps, arrayTextures);
	}

	@Override
	public boolean isCompatibleWith(TexturingConfiguration boundTextures)
	{
		TexturingConfigurationImplementation b = (TexturingConfigurationImplementation) boundTextures;

		//Early-out, it's the same object
		if (b == this)
			return true;

		//Check for texture conflicts
		for (Entry<String, Texture1D> entry : textures1d.entrySet())
		{
			Texture1D conflicting = b.textures1d.get(entry.getKey());
			if (!conflicting.equals(entry.getValue()))
			{
				System.out.println("Conflicting textures 1d");
				return false;
			}
		}

		for (Entry<String, Texture2D> entry : textures2d.entrySet())
		{
			Texture2D conflicting = b.textures2d.get(entry.getKey());
			if (!conflicting.equals(entry.getValue()))
			{
				System.out.println("Conflicting textures 2d");
				return false;
			}
		}

		for (Entry<String, Cubemap> entry : cubemaps.entrySet())
		{
			Cubemap conflicting = b.cubemaps.get(entry.getKey());
			if (!conflicting.equals(entry.getValue()))
			{
				System.out.println("Conflicting Cubemap");
				return false;
			}
		}

		//I guess it's fine
		return true;
	}

	private static Texture[] boundTextures = new Texture[RenderingConfig.gl_MaxTextureUnits];
	//private static Map<Integer, Texture> boundTextures = new HashMap<Integer, Texture>(16);

	/**
	 * Setups the required texturing units and links the shaders uniforms to them
	 */
	public void setup(RenderingInterface renderingInterface) throws NotEnoughtTextureUnitsException
	{
		ShaderProgram shaderProgram = (ShaderProgram) renderingInterface.currentShader();

		int textureUnitId = 0;

		//Foreach texture
		for (Entry<String, Texture1D> entry : textures1d.entrySet())
		{
			//Check it ain't null
			Texture texture = entry.getValue();
			if (texture == null)
				continue;

			//Check it is used in the shader
			int textureLocation = shaderProgram.getUniformLocation(entry.getKey());
			if (textureLocation == -1)
				continue;

			if (!(boundTextures[textureUnitId] == texture))
			{
				//Select a valid, free texturing unit
				selectTextureUnit(textureUnitId);

				//Bind the texture to this texturing unit
				texture.bind();
			}
			//Set the uniform location to this texturing unit
			glUniform1i(shaderProgram.getUniformLocation(entry.getKey()), textureUnitId);
			//shaderProgram.setUniform1i(entry.getKey(), textureUnitId);

			boundTextures[textureUnitId] = texture;

			//Increase the counter
			textureUnitId++;
		}

		for (Entry<String, Texture2D> entry : textures2d.entrySet())
		{
			//Check it ain't null
			Texture texture = entry.getValue();
			if (texture == null)
				continue;
			
			//Check it is used in the shader
			int textureLocation = shaderProgram.getUniformLocation(entry.getKey());
			if (textureLocation == -1)
				continue;

			if (!(boundTextures[textureUnitId] == texture))
			{
				//Select a valid, free texturing unit
				selectTextureUnit(textureUnitId);

				//Bind the texture to this texturing unit
				texture.bind();
			}
			//Set the uniform location to this texturing unit
			glUniform1i(shaderProgram.getUniformLocation(entry.getKey()), textureUnitId);
			//shaderProgram.setUniform1i(entry.getKey(), textureUnitId);
			
			boundTextures[textureUnitId] = texture;

			//Increase the counter
			textureUnitId++;
		}

		for (Entry<String, Cubemap> entry : cubemaps.entrySet())
		{
			//Check it ain't null
			Texture texture = entry.getValue();
			if (texture == null)
				continue;

			//Check it is used in the shader
			int textureLocation = shaderProgram.getUniformLocation(entry.getKey());
			if (textureLocation == -1)
				continue;

			if (!(boundTextures[textureUnitId] == texture))
			{
				//Select a valid, free texturing unit
				selectTextureUnit(textureUnitId);

				//Bind the texture to this texturing unit
				texture.bind();
			}
			//Set the uniform location to this texturing unit
			glUniform1i(shaderProgram.getUniformLocation(entry.getKey()), textureUnitId);
			//shaderProgram.setUniform1i(entry.getKey(), textureUnitId);

			boundTextures[textureUnitId] = texture;

			//Increase the counter
			textureUnitId++;
		}
		
		for (Entry<String, ArrayTexture> entry : arrayTextures.entrySet())
		{
			//Check it ain't null
			Texture texture = entry.getValue();
			if (texture == null)
				continue;

			//Check it is used in the shader
			int textureLocation = shaderProgram.getUniformLocation(entry.getKey());
			if (textureLocation == -1)
				continue;

			if (!(boundTextures[textureUnitId] == texture))
			{
				//Select a valid, free texturing unit
				selectTextureUnit(textureUnitId);

				//Bind the texture to this texturing unit
				texture.bind();
			}
			//Set the uniform location to this texturing unit
			glUniform1i(shaderProgram.getUniformLocation(entry.getKey()), textureUnitId);
			//shaderProgram.setUniform1i(entry.getKey(), textureUnitId);

			boundTextures[textureUnitId] = texture;

			//Increase the counter
			textureUnitId++;
		}
	}

	private void selectTextureUnit(int id) throws NotEnoughtTextureUnitsException
	{
		if (id >= RenderingConfig.gl_MaxTextureUnits)
			throw new NotEnoughtTextureUnitsException();
		glActiveTexture(GL_TEXTURE0 + id);
	}

	public static void resetBoundTextures()
	{
		for(int i = 0; i < boundTextures.length; i++)
			boundTextures[i] = null;
	}
	
	/*@Override
	public Map<String, Texture2D> getBoundTextures2D()
	{
		return textures2d;
	}*/
}
