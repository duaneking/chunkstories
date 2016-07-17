package io.xol.chunkstories.core.entity;

//(c) 2015-2016 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

import static io.xol.chunkstories.renderer.debug.OverlayRenderer.*;

import java.util.ArrayList;
import java.util.List;

import io.xol.engine.math.lalgb.Matrix4f;
import io.xol.engine.math.lalgb.Vector3d;
import io.xol.engine.math.lalgb.Vector3f;
import io.xol.engine.math.lalgb.Vector4f;
import io.xol.chunkstories.api.entity.interfaces.EntityHUD;
import io.xol.chunkstories.api.voxel.VoxelFormat;
import io.xol.chunkstories.entity.EntityImplementation;
import io.xol.chunkstories.physics.CollisionBox;
import io.xol.chunkstories.renderer.Camera;
import io.xol.chunkstories.world.WorldImplementation;
import io.xol.engine.graphics.fonts.TrueTypeFont;
import io.xol.engine.graphics.textures.Texture2D;
import io.xol.engine.graphics.textures.TexturesHandler;
import io.xol.engine.model.ModelLibrary;
import io.xol.engine.model.RenderingContext;
import io.xol.engine.model.animation.BVHAnimation;
import io.xol.engine.model.animation.BVHLibrary;
import io.xol.engine.model.animation.Bone;

public class EntityZombie extends EntityImplementation implements EntityHUD
{
	int i = 0;

	public EntityZombie(WorldImplementation w, double x, double y, double z)
	{
		super(w, x, y, z);
		//anim = new BVHAnimation(new File("res/models/human.bvh"));
	}

	public boolean renderable()
	{
		return true;
	}

	//BVHAnimation anim;

	@Override
	public void render(RenderingContext renderingContext)
	{
		i++;
		i %= 80;
		
		Texture2D playerTexture = TexturesHandler.getTexture("models/zombie_s3.png");
		playerTexture.setLinearFiltering(false);
		
		renderingContext.setDiffuseTexture(playerTexture.getId());
		renderingContext.setNormalTexture(TexturesHandler.getTextureID("textures/normalnormal.png"));
		renderingContext.getCurrentShader().setUniformFloat3("objectPosition", getLocation().castToSP());
		int modelBlockData = world.getVoxelData(getLocation());
		int lightSky = VoxelFormat.sunlight(modelBlockData);
		int lightBlock = VoxelFormat.blocklight(modelBlockData);
		renderingContext.getCurrentShader().setUniformFloat3("givenLightmapCoords", lightBlock / 15f, lightSky / 15f, 0f);

		renderingContext.sendTransformationMatrix(null);
		ModelLibrary.getMesh("./res/models/human.obj").render(renderingContext, BVHLibrary.getAnimation("res/models/human-standstill.bvh"), i);

		
		Matrix4f itemMatrix = BVHLibrary.getAnimation("res/models/human-viewport.bvh").getTransformationForBone("boneItemInHand", i);

		renderingContext.sendTransformationMatrix(itemMatrix);
		renderingContext.setDiffuseTexture(TexturesHandler.getTextureID("res/models/ak47.hq.png"));
		renderingContext.setNormalTexture(TexturesHandler.getTextureID("res/textures/normalnormal.png"));
		//ModelLibrary.getMesh("./res/models/ak47.hq.obj").render(renderingContext);
	}

	@Override
	public void debugDraw()
	{
		// Debug this shit
		BVHAnimation anim = BVHLibrary.getAnimation("res/models/human-viewport.bvh");
		for (Bone b : anim.bones)
		{
			Matrix4f transform = anim.getTransformationForBone(b.name, i);
			//TODO broken
			Vector3d pos = this.getLocation();
			debugDraw(0.2f, 0.2f, 0.2f, (float) pos.x, (float) pos.y , (float) pos.z, transform);
		}
	}
	
	List<float[]> kek = new ArrayList<float[]>();

	void addToList(Matrix4f m, float a, float b, float c, float x, float y, float z)
	{
		a -= x;
		b -= y;
		c -= z;
		Vector4f vertex = new Vector4f(a, b, c, 1);
		if(Math.abs(b) > 0.0)
		{
			vertex.x = 0.0f;
			vertex.z = 0.0f;
		}
		Matrix4f.transform(m, vertex, vertex);
		kek.add(new float[]{vertex.x + x, vertex.y + y, vertex.z + z});
	}
	
	//TODO move this to the BVHAnimation class and clean it up to use modern functions
	public void debugDraw(float r, float g, float b, float xpos, float ypos, float zpos, Matrix4f transform)
	{
		kek.clear();
		
		// glTranslated(xpos-xw/2,ypos,zpos-zw/2);
		float xw = 0.05f;
		float zw = 0.05f;
		float h = 0.15f;
		//System.out.println("Debug drawing at "+xpos+" y:"+ypos+" z:"+(zpos-zw/2));

		//glDisable(GL_TEXTURE_2D);
		glColor4f(r, g, b, 1f);
		glLineWidth(2);
		glDisable(GL_CULL_FACE);
		// glDisable(GL_DEPTH_TEST);
		glEnable(GL_BLEND);
		// glBlendFunc(GL_ONE_MINUS_SRC_COLOR,GL_ONE);
		glBegin(GL_LINES);
		
		addToList(transform, xpos - xw / 2, ypos, zpos - zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos + xw / 2, ypos, zpos - zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos - xw / 2, ypos, zpos + zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos + xw / 2, ypos, zpos + zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos + xw / 2, ypos, zpos + zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos + xw / 2, ypos, zpos - zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos - xw / 2, ypos, zpos - zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos - xw / 2, ypos, zpos + zw / 2, xpos, ypos, zpos);

		addToList(transform, xpos - xw / 2, ypos + h, zpos - zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos + xw / 2, ypos + h, zpos - zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos - xw / 2, ypos + h, zpos + zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos + xw / 2, ypos + h, zpos + zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos + xw / 2, ypos + h, zpos + zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos + xw / 2, ypos + h, zpos - zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos - xw / 2, ypos + h, zpos - zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos - xw / 2, ypos + h, zpos + zw / 2, xpos, ypos, zpos);

		addToList(transform, xpos - xw / 2, ypos, zpos - zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos - xw / 2, ypos + h, zpos - zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos - xw / 2, ypos, zpos + zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos - xw / 2, ypos + h, zpos + zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos + xw / 2, ypos, zpos - zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos + xw / 2, ypos + h, zpos - zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos + xw / 2, ypos, zpos + zw / 2, xpos, ypos, zpos);
		addToList(transform, xpos + xw / 2, ypos + h, zpos + zw / 2, xpos, ypos, zpos);
		
		for(float[] k : kek)
		{
			glVertex3f(k[0], k[1], k[2]);
		}
		glEnd();
	}


	@Override
	public void drawHUD(Camera camera)
	{
		Vector3f posOnScreen = camera.transform3DCoordinate(getLocation().castToSP().add(new Vector3f(0.0f, 2.5f, 0.0f)));
		
		float scale = posOnScreen.z;
		float dekal = TrueTypeFont.arial12.getWidth("Player")*16*scale;
		if(scale > 0)
			TrueTypeFont.arial12.drawStringWithShadow(posOnScreen.x-dekal/2, posOnScreen.y, "", 16*scale, 16*scale, new Vector4f(1,1,1,1));
	}
	
	@Override
	public CollisionBox[] getCollisionBoxes()
	{
		return new CollisionBox[] { new CollisionBox(0.5, 2.00, 0.5) };
	}
}