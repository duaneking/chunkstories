//
// This file is a part of the Chunk Stories API codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.engine.graphics.fbo;



import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL20.*;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.BufferUtils;

import io.xol.chunkstories.api.rendering.target.RenderTarget;
import io.xol.chunkstories.api.rendering.target.RenderTargetAttachementsConfiguration;
import io.xol.chunkstories.client.Client;

public class FrameBufferObjectGL implements RenderTargetAttachementsConfiguration
{
	RenderTarget[] colorAttachements;
	RenderTarget depthAttachement;

	int fbo_id;

	public FrameBufferObjectGL(RenderTarget depth, RenderTarget... colors)
	{
		fbo_id = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, fbo_id);

		depthAttachement = depth;
		colorAttachements = colors;

		// Initialize color output buffers
		if (colors != null && colors.length > 0)
		{
			scratchBuffer = BufferUtils.createIntBuffer(colors.length);
			int i = 0;
			for (RenderTarget texture : colors)
			{
				texture.attachAsColor(i);
				//glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, texture.getID(), 0);
				scratchBuffer.put(i, GL_COLOR_ATTACHMENT0 + i);
				i++;
			}
			glDrawBuffers(scratchBuffer);
		}
		else
		{
			glDrawBuffers(GL_NONE);
		}
		// Initialize depth output buffer
		if (depthAttachement != null)
			depthAttachement.attachAsDepth();
			//glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthAttachement.getID(), 0);

		glBindFramebuffer(GL_FRAMEBUFFER, 0);
	}

	IntBuffer scratchBuffer;
	List<Integer> drawBuffers = new ArrayList<Integer>();
	
	@Override
	public void setEnabledRenderTargets(boolean... targets)
	{
		bind();
		// ???
		if (depthAttachement != null)
			depthAttachement.attachAsDepth();
		if (targets.length == 0)
		{
			// If no arguments set ALL to renderable
			scratchBuffer.clear();
			int i = 0;
			for (RenderTarget texture : colorAttachements)
			{
				texture.attachAsColor(i);
				//glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, texture.getID(), 0);
				scratchBuffer.put(i, GL_COLOR_ATTACHMENT0 + i);
				i++;
			}
			glDrawBuffers(scratchBuffer);
		}
		else
		{
			drawBuffers.clear();
			int i = 0;
			for (boolean b : targets)
			{
				if (b)
					drawBuffers.add(GL_COLOR_ATTACHMENT0 + i);
				i++;
			}
			if (drawBuffers.size() > 0)
			{
				IntBuffer scratchBuffer = BufferUtils.createIntBuffer(drawBuffers.size());
				i = 0;
				for (int b : drawBuffers)
				{
					scratchBuffer.put(i, b);
					i++;
				}
				glDrawBuffers(scratchBuffer);
			}
			else
				glDrawBuffers(GL_NONE);
		}
	}
	
	/* (non-Javadoc)
	 * @see io.xol.engine.graphics.fbo.RenderTargetAttachementsConfiguration#setDepthAttachement(io.xol.chunkstories.api.rendering.target.RenderTarget)
	 */
	@Override
	public void setDepthAttachement(RenderTarget depthAttachement)
	{
		this.depthAttachement = depthAttachement;
		if(depthAttachement != null)
			depthAttachement.attachAsDepth();
	}
	
	/* (non-Javadoc)
	 * @see io.xol.engine.graphics.fbo.RenderTargetAttachementsConfiguration#setColorAttachement(int, io.xol.chunkstories.api.rendering.target.RenderTarget)
	 */
	@Override
	public void setColorAttachement(int index, RenderTarget colorAttachement)
	{
		this.colorAttachements[index] = colorAttachement;
		if(colorAttachement != null)
			colorAttachement.attachAsColor(index);
	}
	
	/* (non-Javadoc)
	 * @see io.xol.engine.graphics.fbo.RenderTargetAttachementsConfiguration#setColorAttachements(io.xol.chunkstories.api.rendering.target.RenderTarget)
	 */
	@Override
	public void setColorAttachements(RenderTarget... colorAttachements)
	{
		scratchBuffer = BufferUtils.createIntBuffer(colorAttachements.length);
		this.colorAttachements = colorAttachements;
		
		int i = 0;
		for (RenderTarget colorAttachement : colorAttachements)
		{
			colorAttachement.attachAsColor(i);
			//glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, texture.getID(), 0);
			scratchBuffer.put(i, GL_COLOR_ATTACHMENT0 + i);
			i++;
		}
	}

	/* (non-Javadoc)
	 * @see io.xol.engine.graphics.fbo.RenderTargetAttachementsConfiguration#resizeFBO(int, int)
	 */
	@Override
	public void resizeFBO(int w, int h)
	{
		if (depthAttachement != null)
		{
			depthAttachement.resize(w, h);
		}
		if (colorAttachements != null)
		{
			for (RenderTarget t : colorAttachements)
			{
				t.resize(w, h);
			}
		}
	}

	void bind()
	{
		//Don't rebind twice
		if(fbo_id == bound)
			return;
		Client.getInstance().getGameWindow().getRenderingContext().flush();
		glBindFramebuffer(GL_FRAMEBUFFER, fbo_id);
		RenderTarget ok = this.depthAttachement != null ? depthAttachement : (this.colorAttachements != null && this.colorAttachements.length > 0 ? this.colorAttachements[0] : null);
		if(ok != null)
			glViewport(0, 0, ok.getWidth(), ok.getHeight());
		else
			System.out.println("fck off");
		bound = fbo_id;
	}

	static void unbind()
	{
		Client.getInstance().getGameWindow().getRenderingContext().flush();
		glBindFramebuffer(GL_FRAMEBUFFER, 0);
		glViewport(0, 0, Client.getInstance().getGameWindow().getWidth(), Client.getInstance().getGameWindow().getHeight());
		bound = 0;
	}
	
	static int bound = 0;

	/* (non-Javadoc)
	 * @see io.xol.engine.graphics.fbo.RenderTargetAttachementsConfiguration#destroy(boolean)
	 */
	@Override
	public void destroy(boolean texturesToo)
	{
		glDeleteFramebuffers(fbo_id);
		if (texturesToo)
		{
			if (depthAttachement != null)
				depthAttachement.destroy();
			for (RenderTarget tex : colorAttachements)
			{
				if (tex != null)
					tex.destroy();
			}
		}
	}
}
