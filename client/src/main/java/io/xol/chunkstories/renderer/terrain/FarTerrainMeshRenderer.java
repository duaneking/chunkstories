//
// This file is a part of the Chunk Stories API codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.chunkstories.renderer.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.joml.Vector3dc;
import org.lwjgl.BufferUtils;

import io.xol.chunkstories.Constants;
import io.xol.chunkstories.api.physics.CollisionBox;
import io.xol.chunkstories.api.rendering.CameraInterface;
import io.xol.chunkstories.api.rendering.Primitive;
import io.xol.chunkstories.api.rendering.RenderingInterface;
import io.xol.chunkstories.api.rendering.pipeline.PipelineConfiguration.BlendMode;
import io.xol.chunkstories.api.rendering.pipeline.PipelineConfiguration.CullingMode;
import io.xol.chunkstories.api.rendering.pipeline.PipelineConfiguration.DepthTestMode;
import io.xol.chunkstories.api.rendering.pipeline.PipelineConfiguration.PolygonFillMode;
import io.xol.chunkstories.api.rendering.pipeline.ShaderInterface;
import io.xol.chunkstories.api.rendering.textures.Texture1D;
import io.xol.chunkstories.api.rendering.textures.Texture2D;
import io.xol.chunkstories.api.rendering.textures.TextureFormat;
import io.xol.chunkstories.api.rendering.vertex.VertexFormat;
import io.xol.chunkstories.api.rendering.world.WorldRenderer.FarTerrainRenderer;
import io.xol.chunkstories.api.voxel.textures.VoxelTexture;
import io.xol.chunkstories.client.RenderingConfig;
import io.xol.chunkstories.renderer.WorldRendererImplementation;
import io.xol.chunkstories.renderer.terrain.FarTerrainBaker.RegionMesh;
import io.xol.chunkstories.voxel.VoxelTextureAtlased;
import io.xol.chunkstories.world.WorldClientCommon;
import io.xol.chunkstories.world.summary.RegionSummaryImplementation;
import io.xol.engine.graphics.textures.Texture1DGL;
import io.xol.engine.graphics.textures.TexturesHandler;



public class FarTerrainMeshRenderer implements FarTerrainRenderer
{
	private static final int TRIANGLES_PER_FACE = 2; // 2 triangles per face
	private static final int TRIANGLE_SIZE = 3; // 3 vertex per triangles
	private static final int VERTEX_SIZE = 3; // A vertex is 3 coordinates : xyz

	//private static final int[] offsets = { 0, 65536, 81920, 86016, 87040, 87296, 87360, 87376, 87380, 87381 };

	//Single 6Mb Buffer
	private ByteBuffer regionMeshBuffer = BufferUtils.createByteBuffer(256 * 256 * 5 * TRIANGLE_SIZE * VERTEX_SIZE * TRIANGLES_PER_FACE * (8 + 4));

	private final WorldClientCommon world;
	private final WorldRendererImplementation worldRenderer;

	private List<FarTerrainBaker.RegionMesh> renderedRegions = new ArrayList<FarTerrainBaker.RegionMesh>();

	//TODO use a texture
	private boolean blocksTexturesSummaryDone = false;
	private Texture1DGL blockTexturesSummary = new Texture1DGL(TextureFormat.RGBA_8BPP);
	//private int blocksTexturesSummaryId = -1;

	private int centerChunkX = -1;
	private int centerChunkZ = -1;
	
	//private int lastRegionX = -1;
	//private int lastRegionZ = -1;

	@SuppressWarnings("unused")
	private int lastLevelDetail = -1;

	private int cameraChunkX, cameraChunkZ;

	private AtomicBoolean isTerrainUpdateRunning = new AtomicBoolean();
	private AtomicInteger farTerrainUpdatesToTakeIntoAccount = new AtomicInteger();
	private long lastTerrainUpdateTiming;
	private long timeToWaitBetweenTerrainUpdates = 2500;

	public FarTerrainMeshRenderer(WorldClientCommon world, WorldRendererImplementation worldRenderer)
	{
		this.world = world;
		this.worldRenderer = worldRenderer;
		getBlocksTexturesSummary();
	}

	public void markFarTerrainMeshDirty()
	{
		farTerrainUpdatesToTakeIntoAccount.incrementAndGet();
		//terrainDirty = true;
	}

	public void markVoxelTexturesSummaryDirty()
	{
		blocksTexturesSummaryDone = false;
	}

	/** Dirty and stupid */
	public Texture1D getBlocksTexturesSummary()
	{
		if (!blocksTexturesSummaryDone)
		{
			int size = 512;
			ByteBuffer bb = ByteBuffer.allocateDirect(size * 4);
			bb.order(ByteOrder.LITTLE_ENDIAN);

			int counter = 0;
			Iterator<VoxelTexture> i = world.getContent().voxels().textures().all();
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

			blocksTexturesSummaryDone = true;
		}

		return blockTexturesSummary;
	}

	public void renderTerrain(RenderingInterface renderer, ReadyVoxelMeshesMask mask)
	{
		//Check for world updates
		Vector3dc cameraPosition = renderer.getCamera().getCameraPosition();
		
		int xCoordinates = ((int)(double)cameraPosition.x());
		int zCoordinates = ((int)(double)cameraPosition.z());
		
		xCoordinates %= world.getWorldSize();
		zCoordinates %= world.getWorldSize();
		if(xCoordinates < 0)
			xCoordinates += world.getWorldSize();
		if(zCoordinates < 0)
			zCoordinates += world.getWorldSize();
		
		int chunkCoordinatesX = xCoordinates / 32;
		int chunkCoordinatesZ = zCoordinates / 32;
		
		if(centerChunkX != chunkCoordinatesX || centerChunkZ != chunkCoordinatesZ)
		{
			centerChunkX = chunkCoordinatesX;
			centerChunkZ = chunkCoordinatesZ;
			this.markFarTerrainMeshDirty();
		}
		
		//Setup shader etc
		ShaderInterface terrainShader = renderer.useShader("terrain");
		renderer.setBlendMode(BlendMode.DISABLED);
		renderer.getCamera().setupShader(terrainShader);
		worldRenderer.getSky().setupShader(terrainShader);

		terrainShader.setUniform3f("sunPos", worldRenderer.getSky().getSunPosition());
		terrainShader.setUniform1f("viewDistance", RenderingConfig.viewDistance);
		terrainShader.setUniform1f("shadowVisiblity", worldRenderer.getShadowRenderer().getShadowVisibility());
		worldRenderer.worldTextures.waterNormalTexture.setLinearFiltering(true);
		worldRenderer.worldTextures.waterNormalTexture.setMipMapping(true);

		renderer.bindCubemap("environmentCubemap", worldRenderer.renderBuffers.rbEnvironmentMap);
		renderer.bindTexture2D("sunSetRiseTexture", worldRenderer.worldTextures.sunGlowTexture);
		renderer.bindTexture2D("skyTextureSunny", worldRenderer.worldTextures.skyTextureSunny);
		renderer.bindTexture2D("skyTextureRaining", worldRenderer.worldTextures.skyTextureRaining);
		renderer.bindTexture2D("blockLightmap", worldRenderer.worldTextures.lightmapTexture);
		Texture2D lightColors = TexturesHandler.getTexture("./textures/environement/lightcolors.png");

		renderer.bindTexture2D("lightColors", lightColors);
		renderer.bindTexture2D("normalTexture", worldRenderer.worldTextures.waterNormalTexture);
		
		world.getGenerator().getEnvironment().setupShadowColors(renderer, terrainShader);
		//worldRenderer.setupShadowColors(terrainShader);
		terrainShader.setUniform1f("time", worldRenderer.getSky().time);

		renderer.bindTexture2D("vegetationColorTexture", world.getGenerator().getEnvironment().getGrassTexture(renderer));
		//renderingContext.bindTexture2D("vegetationColorTexture", worldRenderer.getGrassTexture());
		terrainShader.setUniform1f("mapSize", world.getSizeInChunks() * 32);

		//TODO hidden inputs ?
		if(renderer.getClient().getInputsManager().getInputByName("wireframeFarTerrain").isPressed() && RenderingConfig.isDebugAllowed)
			renderer.setPolygonFillMode(PolygonFillMode.WIREFRAME);

		if(!renderer.getClient().getInputsManager().getInputByName("hideFarTerrain").isPressed() && RenderingConfig.isDebugAllowed)
			drawTerrainBits(renderer, mask, terrainShader);
		
		renderer.flush();

		renderer.setPolygonFillMode(PolygonFillMode.FILL);
	}
	
	List<FarTerrainBaker.RegionMesh> regionsMeshesToRenderSorted = new ArrayList<FarTerrainBaker.RegionMesh>();
	List<Integer> temp = new ArrayList<Integer>();
	List<Integer> temp2 = new ArrayList<Integer>();
	
	private int drawTerrainBits(RenderingInterface renderingContext, ReadyVoxelMeshesMask mask, ShaderInterface terrainShader)
	{
		//Starts asynch regeneration
		if (farTerrainUpdatesToTakeIntoAccount.get() > 0 && (System.currentTimeMillis() - this.lastTerrainUpdateTiming) > this.timeToWaitBetweenTerrainUpdates)
		{
			if(this.isTerrainUpdateRunning.compareAndSet(false, true))
				this.startAsynchSummaryRegeneration(renderingContext.getCamera());
		}

		//Setups stuff
		renderingContext.setCullingMode(CullingMode.COUNTERCLOCKWISE);
		renderingContext.setDepthTestMode(DepthTestMode.LESS_OR_EQUAL);
		
		//Camera is of position
		CameraInterface camera = renderingContext.getCamera();
		int camRX = (int) (camera.getCameraPosition().x() / 256);
		int camRZ = (int) (camera.getCameraPosition().z() / 256);

		int wrapRegionsDistance = world.getSizeInChunks() / 2;
		int worldSizeInRegions = world.getSizeInChunks() / 8;

		int cameraChunkX = (int) (camera.getCameraPosition().x() / 32);
		int cameraChunkZ = (int) (camera.getCameraPosition().z() / 32);
		
		//Update their displayed position to reflect where the camera is
		for(RegionMesh mesh : renderedRegions)
		{
			mesh.regionDisplayedX = mesh.regionSummary.getRegionX();
			mesh.regionDisplayedZ = mesh.regionSummary.getRegionZ();
			
			//We wrap the chunks if they are too far
			if (mesh.regionSummary.getRegionX() * 8 - cameraChunkX > wrapRegionsDistance)
				mesh.regionDisplayedX += -worldSizeInRegions;
			if (mesh.regionSummary.getRegionX() * 8 - cameraChunkX < -wrapRegionsDistance)
				mesh.regionDisplayedX += worldSizeInRegions;
			
			if (mesh.regionSummary.getRegionZ() * 8 - cameraChunkZ > wrapRegionsDistance)
				mesh.regionDisplayedZ += -worldSizeInRegions;
			if (mesh.regionSummary.getRegionZ() * 8 - cameraChunkZ < -wrapRegionsDistance)
				mesh.regionDisplayedZ += worldSizeInRegions;
			//System.out.println(mesh.regionDisplayedX + " : " + cameraChunkX);
		}
		
		//Sort to draw near first
		
		//µ-opt
		//List<FarTerrainBaker.RegionMesh> regionsMeshesToRenderSorted = new ArrayList<FarTerrainBaker.RegionMesh>(renderedRegions);
		regionsMeshesToRenderSorted.clear();
		regionsMeshesToRenderSorted.addAll(renderedRegions);
		
		regionsMeshesToRenderSorted.sort(new Comparator<FarTerrainBaker.RegionMesh>()
		{
			@Override
			public int compare(FarTerrainBaker.RegionMesh a, FarTerrainBaker.RegionMesh b)
			{
				int distanceA = Math.abs(a.regionDisplayedX - camRX) + Math.abs(a.regionDisplayedZ - camRZ);
				int distanceB = Math.abs(b.regionDisplayedX - camRX) + Math.abs(b.regionDisplayedZ - camRZ);
				return distanceA - distanceB;
			}

		});

		//µ-opt
		/*List<Integer> temp = new ArrayList<Integer>();
		List<Integer> temp2 = new ArrayList<Integer>();*/
		temp.clear();
		temp2.clear();
		
		int bitsDrew = 0;
		
		CollisionBox collisionBoxCheck = new CollisionBox(0, 0, 0, 0, 0, 0);
		for (FarTerrainBaker.RegionMesh regionMesh : regionsMeshesToRenderSorted)
		{
			//Frustrum checks (assuming maxHeight of 1024 blocks)
			//TODO do a simple max() and improve accuracy
			float height = 1024f;
			
			//Early-out
			if (!renderingContext.getCamera().isBoxInFrustrum(new CollisionBox(regionMesh.regionDisplayedX * 256, 0, regionMesh.regionDisplayedZ * 256, 256, height, 256)))
				continue;
			
			for(int i = 0; i < 8; i++)
				for(int j = 0; j < 8; j++)
				{
					int delta = regionMesh.regionSummary.max[i][j] - regionMesh.regionSummary.min[i][j];
					
					collisionBoxCheck.xpos = (regionMesh.regionDisplayedX * 8 + i) * 32;
					collisionBoxCheck.ypos = regionMesh.regionSummary.min[i][j];
					collisionBoxCheck.zpos = (regionMesh.regionDisplayedZ * 8 + j) * 32;
					collisionBoxCheck.xw = 32;
					collisionBoxCheck.h = delta + 1;
					collisionBoxCheck.zw = 32;
					
					if (renderingContext.getCamera().isBoxInFrustrum(collisionBoxCheck))
					//if (renderingContext.getCamera().isBoxInFrustrum(new CollisionBox((regionMesh.regionDisplayedX * 8 + i) * 32, regionMesh.regionSummary.min[i][j], (regionMesh.regionDisplayedZ * 8 + j) * 32, 32, delta + 1, 32)))
					{
						if(mask != null)
						{
							if(mask.shouldMaskSlab(regionMesh.regionDisplayedX * 8 + i, regionMesh.regionDisplayedZ * 8 + j, regionMesh.regionSummary.min[i][j], regionMesh.regionSummary.max[i][j]))
								continue;
						}
						temp.add(regionMesh.vertexSectionsOffsets[i][j]);
						temp2.add(regionMesh.vertexSectionsSizes[i][j]);
					}
				}
			
			if(temp.size() == 0)
				continue;
			
			RegionSummaryImplementation regionSummaryData = regionMesh.regionSummary;
			
			//Skip unloaded regions immediately
			if(regionSummaryData.isUnloaded())
				continue;
			
			renderingContext.bindArrayTexture("heights", worldRenderer.getSummariesTexturesHolder().getHeightsArrayTexture());
			renderingContext.bindArrayTexture("topVoxels", worldRenderer.getSummariesTexturesHolder().getTopVoxelsArrayTexture());
			
			renderingContext.bindTexture1D("blocksTexturesSummary", getBlocksTexturesSummary());
			
			int index = worldRenderer.getSummariesTexturesHolder().getSummaryIndex(regionSummaryData.getRegionX(), regionSummaryData.getRegionZ());
			if(index == -1) {
				//System.out.println("index == -1");
				continue;
			}
			
			//System.out.println("index:"+index);
			
			terrainShader.setUniform1i("arrayIndex", index);
			
			/*renderingContext.bindTexture2D("groundTexture", regionSummaryData.voxelTypesTexture);
			regionSummaryData.voxelTypesTexture.setTextureWrapping(false);
			regionSummaryData.voxelTypesTexture.setLinearFiltering(false);
			
			renderingContext.bindTexture2D("heightMap", regionSummaryData.heightsTexture);
			regionSummaryData.heightsTexture.setTextureWrapping(false);
			regionSummaryData.heightsTexture.setLinearFiltering(false);*/
			
			//Actual region position
			terrainShader.setUniform2f("regionPosition", regionSummaryData.getRegionX(), regionSummaryData.getRegionZ());
			//Displayed position
			terrainShader.setUniform2f("visualOffset", regionMesh.regionDisplayedX * 256, regionMesh.regionDisplayedZ * 256);

			//Checks this regionMesh instance has it's stuff uploaded already
			if (!regionMesh.verticesObject.isDataPresent())
				continue;
			
			//else
			//	System.out.println("warning");
			
			int stride = 4 * 2 + 4 + 0 * 4;

			int vertices2draw = (int) (regionMesh.verticesObject.getVramUsage() / stride);

			renderingContext.bindAttribute("vertexIn", regionMesh.verticesObject.asAttributeSource(VertexFormat.SHORT, 3, stride, 0L));
			renderingContext.bindAttribute("normalIn", regionMesh.verticesObject.asAttributeSource(VertexFormat.UBYTE, 4, stride, 8L));

			bitsDrew += vertices2draw;
			
			int[] theStuff = new int[temp.size() * 2];
			for(int i = 0; i < temp.size(); i++)
			{
				theStuff[i * 2] = temp.get(i);
				theStuff[i * 2 + 1] = temp2.get(i);
			}
			temp.clear();
			temp2.clear();
			
			renderingContext.drawMany(Primitive.TRIANGLE, theStuff);
		}

		return bitsDrew;
	}

	private void startAsynchSummaryRegeneration(CameraInterface camera)
	{
		cameraChunkX = (int) (camera.getCameraPosition().x() / 32);
		cameraChunkZ = (int) (camera.getCameraPosition().z() / 32);
		
		Thread asynchGenerateThread = new Thread()
		{
			@Override
			public void run()
			{
				int tookIntoAccount = farTerrainUpdatesToTakeIntoAccount.get();
				
				Thread.currentThread().setName("Far terrain rebuilder thread");
				Thread.currentThread().setPriority(Constants.TERRAIN_RENDERER_THREAD_PRIORITY);

				FarTerrainBaker baker = new FarTerrainBaker(regionMeshBuffer, world, cameraChunkX, cameraChunkZ);
				List<RegionMesh> previousMeshes = renderedRegions;
				
				renderedRegions = baker.generateArround();
				
				if(previousMeshes != null) {
					for(RegionMesh rm : previousMeshes) {
						rm.delete();
					}
				}
				
				farTerrainUpdatesToTakeIntoAccount.addAndGet(-tookIntoAccount);
				lastTerrainUpdateTiming = System.currentTimeMillis();
				isTerrainUpdateRunning.set(false);
			}
		};
		asynchGenerateThread.start();
	}

	/**
	 * 
	 */
	/*private void generateArround()
	{
		List<FarTerrainBakerThread.RegionMesh> regionsToRender_NewList = new ArrayList<FarTerrainBakerThread.RegionMesh>();
		int summaryDistance = 32;

		//Double check we won't run this concurrently
		synchronized (regionMeshBuffer)
		{
			//Iterate over X chunks but skip whole regions
			int currentChunkX = cameraChunkX - summaryDistance;
			while (currentChunkX < cameraChunkX + summaryDistance)
			{
				//Computes where are we
				int currentRegionX = (int) Math.floor(currentChunkX / 8);
				//if(currentChunkX < 0)
				//	currentRegionX--;
				int nextRegionX = currentRegionX + 1;
				int nextChunkX = nextRegionX * 8;

				//Iterate over Z chunks but skip whole regions
				int currentChunkZ = cameraChunkZ - summaryDistance;
				while (currentChunkZ < cameraChunkZ + summaryDistance)
				{
					//Computes where are we
					int currentRegionZ = (int) Math.floor(currentChunkZ / 8);
					int nextRegionZ = currentRegionZ + 1;
					int nextChunkZ = nextRegionZ * 8;

					//Clear shit
					regionMeshBuffer.clear();

					int rx = currentChunkX / 8;
					int rz = currentChunkZ / 8;
					if (currentChunkZ < 0 && currentChunkZ % 8 != 0)
						rz--;
					if (currentChunkX < 0 && currentChunkX % 8 != 0)
						rx--;

					RegionSummaryImplementation summary = world.getRegionsSummariesHolder().getRegionSummaryWorldCoordinates(currentChunkX * 32, currentChunkZ * 32);

					if (summary == null || !summary.isLoaded())
					{
						currentChunkZ = nextChunkZ;
						continue;
					}

					int rcx = currentChunkX % world.getSizeInChunks();
					if (rcx < 0)
						rcx += world.getSizeInChunks();
					int rcz = currentChunkZ % world.getSizeInChunks();
					if (rcz < 0)
						rcz += world.getSizeInChunks();

					int[] heightMap = summary.getHeightData();
					int[] ids = summary.getVoxelData();

					@SuppressWarnings("unused")
					int vertexCount = 0;

					//Details cache array
					int[] lodsArray = new int[100];
					for (int scx = -1; scx < 9; scx++)
						for (int scz = -1; scz < 9; scz++)
						{
							int regionMiddleX = currentRegionX * 8 + scx;
							int regionMiddleZ = currentRegionZ * 8 + scz;
							int detail = (int) (Math.sqrt(Math.abs(regionMiddleX - cameraChunkX) * Math.abs(regionMiddleX - cameraChunkX) + Math.abs(regionMiddleZ - cameraChunkZ) * Math.abs(regionMiddleZ - cameraChunkZ))
									/ (RenderingConfig.hqTerrain ? 6f : 4f));

							if (detail > 5)
								detail = 5;

							if (!RenderingConfig.hqTerrain && detail < 2)
								detail = 2;

							lodsArray[(scx + 1) * 10 + (scz + 1)] = detail;
						}

					for (int scx = 0; scx < 8; scx++)
						for (int scz = 0; scz < 8; scz++)
						{
							int chunkLod = lodsArray[(scx + 1) * 10 + (scz + 1)];
							int cellSize = (int) Math.pow(2, chunkLod);

							int x0 = (scx * 32) / cellSize;
							int y0 = (scz * 32) / cellSize;
							HeightmapMesher mesher = new HeightmapMesher(heightMap, ids, offsets[chunkLod], 32 / cellSize, x0, y0, 256 / cellSize);
							int test = 0;
							Surface surf = mesher.nextSurface();
							while (surf != null)
							{
								//Top
								addVertexBytes(regionMeshBuffer, scx * 32 + (surf.getX()) * cellSize, surf.getLevel(), scz * 32 + (surf.getY()) * cellSize, 0, 1, 0, surf.getId());
								addVertexBytes(regionMeshBuffer, scx * 32 + (surf.getX() + surf.getW()) * cellSize, surf.getLevel(), scz * 32 + (surf.getY() + surf.getH()) * cellSize, 0, 1, 0, surf.getId());
								addVertexBytes(regionMeshBuffer, scx * 32 + (surf.getX() + surf.getW()) * cellSize, surf.getLevel(), scz * 32 + (surf.getY()) * cellSize, 0, 1, 0, surf.getId());

								addVertexBytes(regionMeshBuffer, scx * 32 + (surf.getX()) * cellSize, surf.getLevel(), scz * 32 + (surf.getY()) * cellSize, 0, 1, 0, surf.getId());
								addVertexBytes(regionMeshBuffer, scx * 32 + (surf.getX()) * cellSize, surf.getLevel(), scz * 32 + (surf.getY() + surf.getH()) * cellSize, 0, 1, 0, surf.getId());
								addVertexBytes(regionMeshBuffer, scx * 32 + (surf.getX() + surf.getW()) * cellSize, surf.getLevel(), scz * 32 + (surf.getY() + surf.getH()) * cellSize, 0, 1, 0, surf.getId());

								vertexCount += 6;

								//Left side
								int vx = scx * 32 + (surf.getX()) * cellSize;
								int vz = scz * 32 + (surf.getY()) * cellSize;
								int heightCurrent = getHeight(heightMap, world, vx - cellSize, vz, currentRegionX, currentRegionZ, lodsArray[((int) Math.floor((vx - cellSize) / 32f) + 1) * 10 + (scz + 1)]);
								int d = 0;
								for (int i = 1; i < surf.getH() + 1; i++)
								{
									int newHeight = (i < surf.getH()) ? getHeight(heightMap, world, vx - cellSize, vz + i * cellSize, currentRegionX, currentRegionZ,
											lodsArray[((int) Math.floor((vx - cellSize) / 32f) + 1) * 10 + ((int) Math.floor((vz + (i) * cellSize) / 32f) + 1)]) : -1;
									if (newHeight != heightCurrent)
									{
										if (heightCurrent != surf.getLevel())
										{
											int side = heightCurrent > surf.getLevel() ? 1 : -1;
											addVertexBytes(regionMeshBuffer, vx, surf.getLevel(), vz + d * cellSize, side, 0, 0, surf.getId());
											addVertexBytes(regionMeshBuffer, vx, heightCurrent, vz + d * cellSize, side, 0, 0, surf.getId());
											addVertexBytes(regionMeshBuffer, vx, heightCurrent, vz + (i) * cellSize, side, 0, 0, surf.getId());

											addVertexBytes(regionMeshBuffer, vx, surf.getLevel(), vz + d * cellSize, side, 0, 0, surf.getId());
											addVertexBytes(regionMeshBuffer, vx, heightCurrent, vz + (i) * cellSize, side, 0, 0, surf.getId());
											addVertexBytes(regionMeshBuffer, vx, surf.getLevel(), vz + (i) * cellSize, side, 0, 0, surf.getId());
											vertexCount += 6;
										}
										heightCurrent = newHeight;
										d = i;
									}
								}
								//Bot side
								heightCurrent = getHeight(heightMap, world, vx, vz - cellSize, currentRegionX, currentRegionZ, lodsArray[((int) Math.floor((vx) / 32f) + 1) * 10 + ((int) Math.floor((vz - cellSize) / 32f) + 1)]);
								d = 0;
								for (int i = 1; i < surf.getW() + 1; i++)
								{
									int newHeight = (i < surf.getW()) ? getHeight(heightMap, world, vx + i * cellSize, vz - cellSize, currentRegionX, currentRegionZ,
											lodsArray[((int) Math.floor((vx + i * cellSize) / 32f) + 1) * 10 + ((int) Math.floor((vz - cellSize) / 32f) + 1)]) : -1;
									if (newHeight != heightCurrent)
									{
										if (heightCurrent != surf.getLevel())
										{
											int side = heightCurrent > surf.getLevel() ? 1 : -1;
											addVertexBytes(regionMeshBuffer, vx + d * cellSize, surf.getLevel(), vz, 0, 0, side, surf.getId());
											addVertexBytes(regionMeshBuffer, vx + (i) * cellSize, heightCurrent, vz, 0, 0, side, surf.getId());
											addVertexBytes(regionMeshBuffer, vx + d * cellSize, heightCurrent, vz, 0, 0, side, surf.getId());

											addVertexBytes(regionMeshBuffer, vx + (i) * cellSize, heightCurrent, vz, 0, 0, side, surf.getId());
											addVertexBytes(regionMeshBuffer, vx + d * cellSize, surf.getLevel(), vz, 0, 0, side, surf.getId());
											addVertexBytes(regionMeshBuffer, vx + (i) * cellSize, surf.getLevel(), vz, 0, 0, side, surf.getId());
											vertexCount += 6;
										}
										heightCurrent = newHeight;
										d = i;
									}
								}

								//Next
								surf = mesher.nextSurface();
								test++;
							}
							if (test > 32 * 32 / (cellSize * cellSize))
							{
								System.out.println("Meshing made more than reasonnable vertices");
							}
							//If the next side has a coarser resolution we want to fill in the gaps
							//We go alongside the two other sides of the mesh and we add another skirt to match the coarser mesh on the side
							int nextMeshDetailsX = lodsArray[(scx + 2) * 10 + (scz + 1)];
							if (nextMeshDetailsX > chunkLod)
							{
								int vx = scx * 32 + 32;
								for (int vz = scz * 32; vz < scz * 32 + 32; vz += cellSize)
								{

									int height = getHeight(heightMap, world, vx - 1, vz, currentRegionX, currentRegionZ, chunkLod);
									int heightNext = getHeight(heightMap, world, vx + 1, vz, currentRegionX, currentRegionZ, nextMeshDetailsX);

									if (heightNext > height)
									{
										int gapData = getIds(ids, world, vx - 1, vz, currentRegionX, currentRegionZ, chunkLod);

										addVertexBytes(regionMeshBuffer, vx, height, vz, 1, 0, 0, gapData);
										addVertexBytes(regionMeshBuffer, vx, heightNext, vz + cellSize, 1, 0, 0, gapData);
										addVertexBytes(regionMeshBuffer, vx, heightNext, vz, 1, 0, 0, gapData);

										addVertexBytes(regionMeshBuffer, vx, height, vz, 1, 0, 0, gapData);
										addVertexBytes(regionMeshBuffer, vx, height, vz + cellSize, 1, 0, 0, gapData);
										addVertexBytes(regionMeshBuffer, vx, heightNext, vz + cellSize, 1, 0, 0, gapData);
										vertexCount += 6;
									}
									else if (heightNext < height)
									{
										int gapData = getIds(ids, world, vx + 1, vz, currentRegionX, currentRegionZ, chunkLod);

										addVertexBytes(regionMeshBuffer, vx, height, vz, -1, 0, 0, gapData);
										addVertexBytes(regionMeshBuffer, vx, heightNext, vz, -1, 0, 0, gapData);
										addVertexBytes(regionMeshBuffer, vx, heightNext, vz + cellSize, -1, 0, 0, gapData);

										addVertexBytes(regionMeshBuffer, vx, height, vz, -1, 0, 0, gapData);
										addVertexBytes(regionMeshBuffer, vx, heightNext, vz + cellSize, -1, 0, 0, gapData);
										addVertexBytes(regionMeshBuffer, vx, height, vz + cellSize, -1, 0, 0, gapData);
										vertexCount += 6;
									}
								}
							}

							int nextMeshDetailsZ = lodsArray[(scx + 1) * 10 + (scz + 2)];
							if (nextMeshDetailsZ > chunkLod)
							{
								int vz = scz * 32 + 32;
								for (int vx = scx * 32; vx < scx * 32 + 32; vx += cellSize)
								{
									int height = getHeight(heightMap, world, vx, vz - 1, currentRegionX, currentRegionZ, chunkLod);
									int heightNext = getHeight(heightMap, world, vx, vz + 1, currentRegionX, currentRegionZ, nextMeshDetailsZ);

									if (heightNext > height)
									{
										int gapData = getIds(heightMap, world, vx, vz - 1, currentRegionX, currentRegionZ, nextMeshDetailsZ);

										addVertexBytes(regionMeshBuffer, vx, height, vz, 0, 0, 1, gapData);
										addVertexBytes(regionMeshBuffer, vx, heightNext, vz, 0, 0, 1, gapData);
										addVertexBytes(regionMeshBuffer, vx + cellSize, heightNext, vz, 0, 0, 1, gapData);

										addVertexBytes(regionMeshBuffer, vx, height, vz, 0, 0, 1, gapData);
										addVertexBytes(regionMeshBuffer, vx + cellSize, heightNext, vz, 0, 0, 1, gapData);
										addVertexBytes(regionMeshBuffer, vx + cellSize, height, vz, 0, 0, 1, gapData);
										vertexCount += 6;
									}
									else if (heightNext < height)
									{
										int gapData = getIds(heightMap, world, vx, vz + 1, currentRegionX, currentRegionZ, nextMeshDetailsZ);

										addVertexBytes(regionMeshBuffer, vx, height, vz, 0, 0, -1, gapData);
										addVertexBytes(regionMeshBuffer, vx + cellSize, heightNext, vz, 0, 0, -1, gapData);
										addVertexBytes(regionMeshBuffer, vx, heightNext, vz, 0, 0, -1, gapData);

										addVertexBytes(regionMeshBuffer, vx, height, vz, 0, 0, -1, gapData);
										addVertexBytes(regionMeshBuffer, vx + cellSize, height, vz, 0, 0, -1, gapData);
										addVertexBytes(regionMeshBuffer, vx + cellSize, heightNext, vz, 0, 0, -1, gapData);
										vertexCount += 6;
									}
								}
							}

						}


					byte[] vboContent = new byte[regionMeshBuffer.position()];
					regionMeshBuffer.flip();
					regionMeshBuffer.get(vboContent);
					

					FarTerrainBakerThread.RegionMesh regionMesh = new FarTerrainBakerThread.RegionMesh(rx, rz, summary, vboContent);
					//regionMesh.regionSummary.sendNewModel(vboContent);

					lastRegionX = rcx / 8;
					lastRegionZ = rcz / 8;

					regionsToRender_NewList.add(regionMesh);

					currentChunkZ = nextChunkZ;
				}

				currentChunkX = nextChunkX;
			}

			lastLevelDetail = -1;
			lastRegionX = -1;
			lastRegionZ = -1;

			for (FarTerrainBakerThread.RegionMesh rs : renderedRegions)
			{
				rs.delete();
			}
			renderedRegions = regionsToRender_NewList;
			//regionsToRender.clear();
		}

		this.isTerrainUpdateRunning.set(false);
		this.lastTerrainUpdateTiming = System.currentTimeMillis();
	}*/

	/*private int getHeight(int[] heightMap, WorldImplementation world, int x, int z, int rx, int rz, int level)
	{
		if (x < 0 || z < 0 || x >= 256 || z >= 256)
			return world.getRegionsSummariesHolder().getHeightMipmapped(rx * 256 + x, rz * 256 + z, level);
		else
			return getDataMipmapped(heightMap, x, z, level);
	}

	private int getIds(int[] ids, WorldImplementation world, int x, int z, int rx, int rz, int level)
	{
		if (x < 0 || z < 0 || x >= 256 || z >= 256)
			return world.getRegionsSummariesHolder().getDataMipmapped(rx * 256 + x, rz * 256 + z, level);
		else
			return getDataMipmapped(ids, x, z, level);
	}

	private int getDataMipmapped(int[] summaryData, int x, int z, int level)
	{
		if (level > 8)
			return -1;
		int resolution = 256 >> level;
		x >>= level;
		z >>= level;
		int offset = offsets[level];
		//System.out.println(level+"l"+offset+"reso"+resolution+"x:"+x+"z:"+z);
		return summaryData[offset + resolution * x + z];
	}*/

	/*public void uploadGeneratedMeshes()
	{
		for (RegionMesh rs : renderedRegions)
		{
			if (rs == null)
				continue;
			if (rs.regionSummary == null)
				continue;
			//TODO investigate
			boolean generated = rs.regionSummary.uploadNeededData();
			if (generated)
			{
				
			}
			if (!rs.regionSummary.summaryLoaded.get())
				rs.regionSummary = world.getRegionsSummariesHolder().getRegionSummaryWorldCoordinates(rs.regionSummary.getRegionX() * 256, rs.regionSummary.getRegionZ() * 256);
		}
	}*/

	/*private void addVertexBytes(ByteBuffer terrain, int x, int y, int z, int nx, int ny, int nz, int voxelData)
	{
		terrain.putShort((short) x);
		terrain.putShort((short) (y + 1));
		terrain.putShort((short) z);
		terrain.putShort((short) 0x00);

		terrain.put((byte) (nx + 1));
		terrain.put((byte) (ny + 1));
		terrain.put((byte) (nz + 1));
		terrain.put((byte) (0x00));
	}*/

	public void destroy()
	{
		blockTexturesSummary.destroy();
		//glDeleteTextures(blocksTexturesSummaryId);
	}

}
