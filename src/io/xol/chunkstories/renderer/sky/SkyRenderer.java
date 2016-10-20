package io.xol.chunkstories.renderer.sky;

import io.xol.engine.graphics.RenderingContext;
import io.xol.engine.graphics.geometry.FloatBufferAttributeSource;
import io.xol.engine.graphics.textures.Texture2D;
import io.xol.engine.graphics.textures.TexturesHandler;
import io.xol.chunkstories.api.rendering.Primitive;
import io.xol.chunkstories.api.rendering.pipeline.ShaderInterface;
import io.xol.chunkstories.api.rendering.pipeline.PipelineConfiguration.BlendMode;
import io.xol.chunkstories.api.rendering.pipeline.PipelineConfiguration.CullingMode;
import io.xol.chunkstories.api.rendering.pipeline.PipelineConfiguration.DepthTestMode;
import io.xol.chunkstories.api.world.World;
import io.xol.chunkstories.client.RenderingConfig;
import io.xol.chunkstories.renderer.WorldRenderer;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL11.*;

import io.xol.engine.math.Math2;
import io.xol.engine.math.lalgb.Vector3f;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class SkyRenderer
{
	public float time = 0;
	float distance = 1500;
	float height = -500;

	World world;
	WorldRenderer worldRenderer;
	CloudsRenderer cloudsRenderer;
	
	public SkyRenderer(World world, WorldRenderer worldRenderer)
	{
		this.world = world;
		this.worldRenderer = worldRenderer;
		this.cloudsRenderer = new CloudsRenderer(world, this);
	}

	public Vector3f getSunPosition()
	{
		float sunloc = (float) (time * Math.PI * 2 / 1.6 - 0.5);
		float sunangle = 0;
		float sundistance = 1000;
		return new Vector3f((float) (400 + sundistance * Math.sin(rad(sunangle)) * Math.cos(sunloc)), (float) (height + sundistance * Math.sin(sunloc)), (float) (sundistance * Math.cos(rad(sunangle)) * Math.cos(sunloc))).normalise();
	}
	
	public void render(RenderingContext renderingContext)
	{
		renderingContext.setDepthTestMode(DepthTestMode.DISABLED);
		renderingContext.setBlendMode(BlendMode.DISABLED);
		renderingContext.setCullingMode(CullingMode.DISABLED);
		
		glDepthMask(false);

		Vector3f sunPosVector = getSunPosition();
		double[] sunpos = { sunPosVector.x, sunPosVector.y, sunPosVector.z };

		ShaderInterface skyShader = renderingContext.useShader("sky");
		
		
		//skyShader.use(true);
		renderingContext.bindTexture2D("cloudsNoise", TexturesHandler.getTexture("./textures/environement/cloudsStatic.png"));
		
		Texture2D glowTexture = TexturesHandler.getTexture("./textures/environement/glow.png");
		renderingContext.bindTexture2D("sunSetRiseTexture", glowTexture);
		
		glowTexture.setLinearFiltering(true);
		glowTexture.setTextureWrapping(false);
		glowTexture.setTextureWrapping(false);

		Texture2D skyTextureSunny = TexturesHandler.getTexture("./textures/environement/sky.png");
		Texture2D skyTextureRaining = TexturesHandler.getTexture("./textures/environement/sky_rain.png");
		
		renderingContext.bindTexture2D("skyTextureSunny", skyTextureSunny);
		renderingContext.bindTexture2D("skyTextureRaining", skyTextureRaining);
		
		skyShader.setUniform1f("overcastFactor", world.getWeather());
		
		skyTextureSunny.setLinearFiltering(true);
		skyTextureSunny.setMipMapping(false);
		skyTextureSunny.setTextureWrapping(false);
		
		skyTextureRaining.setLinearFiltering(true);
		skyTextureRaining.setMipMapping(false);
		skyTextureRaining.setTextureWrapping(false);

		//skyShader.setUniformSamplerCube(2, "skybox", TexturesHandler.idCubemap("res/textures/skybox"));
		skyShader.setUniform3f("camPos", renderingContext.getCamera().pos.castToSimplePrecision());
		skyShader.setUniform3f("sunPos", (float) sunpos[0], (float) sunpos[1], (float) sunpos[2]);
		skyShader.setUniform1f("time", time);
		renderingContext.getCamera().setupShader(skyShader);

		renderingContext.drawFSQuad();

		renderingContext.setBlendMode(BlendMode.MIX);
		
		glPointSize(1f);

		ShaderInterface starsShader = renderingContext.useShader("stars");
		
		//starsShader.use(true);
		starsShader.setUniform3f("sunPos", (float) sunpos[0], (float) sunpos[1], (float) sunpos[2]);
		starsShader.setUniform3f("color", 1f, 1f, 1f);
		renderingContext.getCamera().setupShader(starsShader);
		int NB_STARS = 500;
		if (stars == null)
		{
			stars = BufferUtils.createFloatBuffer(NB_STARS * 3);
			for (int i = 0; i < NB_STARS; i++)
			{
				Vector3f star = new Vector3f((float) Math.random() * 2f - 1f, (float) Math.random(), (float) Math.random() * 2f - 1f);
				star.normalise();
				star.scale(100f);
				stars.put(new float[] { star.x, star.y, star.z });
			}
		}
		stars.rewind();
		{
			renderingContext.bindAttribute("vertexIn", new FloatBufferAttributeSource(stars, 3));
			renderingContext.draw(Primitive.POINT, 0, NB_STARS);
		}
		
		renderingContext.flush();
		glDepthMask(true);
		
		renderingContext.setBlendMode(BlendMode.DISABLED);
		renderingContext.setDepthTestMode(DepthTestMode.LESS_OR_EQUAL);
		
		cloudsRenderer.renderClouds(renderingContext);
	}

	private FloatBuffer stars = null;

	private double rad(float h)
	{
		return h / 180 * Math.PI;
	}

	public void setupShader(ShaderInterface shaderInterface)
	{
		float fogFactor = Math.min(Math.max(0.0f, world.getWeather() - 0.4f) / 0.1f, 1.0f);
		
		shaderInterface.setUniform1f("fogStartDistance", Math2.mix(RenderingConfig.viewDistance, 32, fogFactor));
		shaderInterface.setUniform1f("fogEndDistance", Math2.mix(1024, 384, fogFactor));
		shaderInterface.setUniform1f("overcastFactor", world.getWeather());
	}
	
	public void destroy()
	{
		cloudsRenderer.destroy();
	}
}
