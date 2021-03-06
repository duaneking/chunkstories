//
// This file is a part of the Chunk Stories API codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.chunkstories.renderer.chunks;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.xol.chunkstories.api.rendering.Primitive;
import io.xol.chunkstories.api.rendering.RenderingInterface;
import io.xol.chunkstories.api.rendering.vertex.VertexBuffer;
import io.xol.chunkstories.api.rendering.vertex.VertexFormat;
import io.xol.chunkstories.api.rendering.world.WorldRenderer;
import io.xol.chunkstories.api.rendering.world.chunk.ChunkMeshDataSubtypes.LodLevel;
import io.xol.chunkstories.api.rendering.world.chunk.ChunkMeshDataSubtypes.ShadingType;
import io.xol.chunkstories.api.rendering.world.chunk.ChunkMeshDataSubtypes.VertexLayout;
import io.xol.chunkstories.api.rendering.world.chunk.ChunkRenderable.ChunkMeshUpdater;
import io.xol.chunkstories.api.util.IterableIterator;
import io.xol.chunkstories.api.util.concurrency.Fence;
import io.xol.chunkstories.api.voxel.Voxel;
import io.xol.chunkstories.api.voxel.components.VoxelComponentDynamicRenderer.VoxelDynamicRenderer;
import io.xol.chunkstories.api.workers.Task;
import io.xol.chunkstories.api.world.chunk.Chunk.ChunkCell;
import io.xol.chunkstories.renderer.chunks.ChunkMeshDataSections.DynamicallyRenderedVoxelClass;
import io.xol.chunkstories.world.chunk.ClientChunk;
import io.xol.chunkstories.world.chunk.CubicChunk;
import io.xol.engine.concurrency.SimpleLock;

/**
 * Responsible of holding all rendering information about one chunk
 * ie : VBO creation, uploading and deletion, as well as decals
 * TODO actually handle decals here retard
 */
public class ChunkRenderDataHolder implements ChunkMeshUpdater
{
	private final CubicChunk chunk;
	private final WorldRenderer worldRenderer;
	
	final AtomicInteger unbakedUpdates = new AtomicInteger(1);
	public final SimpleLock onlyOneUpdateAtATime = new SimpleLock();
	
	protected TaskBakeChunk task = null;
	final Lock taskLock = new ReentrantLock();
	
	boolean isDestroyed = false;
	
	private Semaphore noDrawDeleteConflicts = new Semaphore(1); // Prevents deleting the object we're drawing from
	private Semaphore oneUploadAtATime = new Semaphore(1); // Enforces one update at a time
	private ChunkMeshDataSections currentData = null;
	protected VertexBuffer verticesObject = null;
	
	public ChunkRenderDataHolder(CubicChunk chunk, WorldRenderer worldRenderer)
	{
		this.chunk = chunk;
		this.worldRenderer = worldRenderer;
	}
	
	public CubicChunk getChunk() {
		return chunk;
	}
	
	/**
	 * Frees the ressources allocated to this ChunkRenderData
	 */
	public void destroy()
	{
		noDrawDeleteConflicts.acquireUninterruptibly();
		
		isDestroyed = true;
		
		currentData = null;
		//Deallocate the VBO
		if(verticesObject != null)
			verticesObject.destroy();
		
		noDrawDeleteConflicts.release();
		
		Task task = this.task;
		if(task != null)
			task.cancel();
	}
	
	/*public void renderChunkBounds(RenderingContext renderingContext)
	{
		//if(chunk.chunkZ != 5)
		//	return;
		FakeImmediateModeDebugRenderer.glColor4f(5, 0, (float) Math.random() * 0.01f, 1);
		SelectionRenderer.cubeVertices(chunk.getChunkX() * 32 + 16, chunk.getChunkY() * 32, chunk.getChunkZ() * 32 + 16, 32, 32, 32);
	}*/

	public boolean isDataAvailable()
	{
		return currentData != null;
	}

	public void setData(ChunkMeshDataSections newData)
	{
		if(newData == null)
			throw new NullPointerException("setData() requires non-null ata");
		
		oneUploadAtATime.acquireUninterruptibly();
		noDrawDeleteConflicts.acquireUninterruptibly();
		
		//Meh that's a waste of time then
		if(isDestroyed) {
			noDrawDeleteConflicts.release();
			oneUploadAtATime.release();
			newData.notNeeded(); //<-- Free the data
			return;
		}
		
		//currentData = data;
		
		//No verticesObject already created; create one, fill it and then change the bails
		if(verticesObject == null) {
			VertexBuffer wip = worldRenderer.getRenderingInterface().newVertexBuffer();
			Fence fence = wip.uploadData(newData.dataToUpload);
			
			//We unlock while waiting for the upload
			noDrawDeleteConflicts.release();
			fence.traverse();
			
			//Then we lock again
			noDrawDeleteConflicts.acquireUninterruptibly();
			verticesObject = wip;
			currentData = newData;
			
			//And we're good !
		}
		//Already a VerticesObject present hum, we create another one then delete the old one
		else {
			VertexBuffer wip = worldRenderer.getRenderingInterface().newVertexBuffer();
			Fence fence = wip.uploadData(newData.dataToUpload);
			
			//We unlock while waiting for the upload
			noDrawDeleteConflicts.release();
			fence.traverse();
			
			//Then we lock again
			noDrawDeleteConflicts.acquireUninterruptibly();
			
			//We delete the OLD one
			verticesObject.destroy();
			
			//We swap the new one in
			verticesObject = wip;
			currentData = newData;
		}
		
		newData.consumed();
		
		noDrawDeleteConflicts.release();
		oneUploadAtATime.release();
	}

	public int renderPass(RenderingInterface renderingInterface, RenderLodLevel renderLodLevel, ShadingType shadingType)
	{
		try {
			noDrawDeleteConflicts.acquireUninterruptibly();
			
			if(currentData == null)
				return 0;
			
			return renderSections(renderingInterface, renderLodLevel, shadingType);
		}
		finally {
			noDrawDeleteConflicts.release();
		}
	}
	
	/** Render the lodLevel+shading type combination using any VertexLayout */
	public int renderSections(RenderingInterface renderingContext, RenderLodLevel renderLodLevel, ShadingType renderPass)
	{
		int total = 0;
		for(VertexLayout vertexLayout : VertexLayout.values())
			total += this.renderSection(renderingContext, vertexLayout, renderLodLevel, renderPass);
		return total;
	}
	
	int array[] = new int[4];
	
	public int renderSection(RenderingInterface renderingContext, VertexLayout vertexLayout, RenderLodLevel renderLodLevel, ShadingType renderPass)
	{
		LodLevel any = LodLevel.ANY;
		LodLevel also = renderLodLevel.equals(RenderLodLevel.HIGH) ? LodLevel.HIGH : LodLevel.LOW; 
		
		//Check size isn't 0
		int sizeAny = currentData.vertices_type_size[vertexLayout.ordinal()][any.ordinal()][renderPass.ordinal()];
		int sizeAlso = currentData.vertices_type_size[vertexLayout.ordinal()][also.ordinal()][renderPass.ordinal()];
		
		if(sizeAny + sizeAlso == 0)
			return 0;
		
		int offsetAny = currentData.vertices_type_offset[vertexLayout.ordinal()][any.ordinal()][renderPass.ordinal()];
		int offsetAlso = currentData.vertices_type_offset[vertexLayout.ordinal()][also.ordinal()][renderPass.ordinal()];

		int offset = 0;
		
		if(offsetAlso < offsetAny)
			System.out.println("prout.");
		
		offset = offsetAny;
		offsetAny = 0;
		offsetAlso -= offsetAny;
		
		switch(vertexLayout) {
		case WHOLE_BLOCKS:
			// Raw blocks ( integer faces coordinates ) alignment :
			// Vertex data : [VERTEX_POS(4b)][TEXCOORD(4b)][COLORS(4b)][NORMALS(4b)] Stride 16 bits
			renderingContext.bindAttribute("vertexIn", verticesObject.asAttributeSource(VertexFormat.UBYTE, 4, 16, offset + 0));
			renderingContext.bindAttribute("texCoordIn", verticesObject.asAttributeSource(VertexFormat.USHORT, 2, 16, offset + 4));
			renderingContext.bindAttribute("colorIn", verticesObject.asAttributeSource(VertexFormat.NORMALIZED_UBYTE, 4, 16, offset + 8));
			renderingContext.bindAttribute("normalIn", verticesObject.asAttributeSource(VertexFormat.U1010102, 4, 16, offset + 12));
			//renderingContext.draw(Primitive.TRIANGLE, 0, size);
			//return size; 
			break;
		case INTRICATE:
			// Complex blocks ( fp faces coordinates ) alignment :
			// Vertex data : [VERTEX_POS(12b)][TEXCOORD(4b)][COLORS(4b)][NORMALS(4b)] Stride 24 bits
			renderingContext.bindAttribute("vertexIn", verticesObject.asAttributeSource(VertexFormat.FLOAT, 3, 24, offset + 0));
			renderingContext.bindAttribute("texCoordIn", verticesObject.asAttributeSource(VertexFormat.USHORT, 2, 24, offset + 12));
			renderingContext.bindAttribute("colorIn", verticesObject.asAttributeSource(VertexFormat.NORMALIZED_UBYTE, 4, 24, offset + 16));
			renderingContext.bindAttribute("normalIn", verticesObject.asAttributeSource(VertexFormat.U1010102, 4, 24, offset + 20));
			//renderingContext.draw(Primitive.TRIANGLE, 0, size);
			//return size;
			break;
		default:
			throw new RuntimeException("Unsupported vertex layout in "+this);
		}
		
		if(sizeAlso == 0) {
			renderingContext.draw(Primitive.TRIANGLE, offsetAny / vertexLayout.bytesPerVertex, sizeAny);
			return sizeAny;
		}
		else if(sizeAny == 0) {
			renderingContext.draw(Primitive.TRIANGLE, offsetAlso / vertexLayout.bytesPerVertex, sizeAlso);
			return sizeAlso;
		}
		else {
			array[0] = offsetAny / vertexLayout.bytesPerVertex;
			array[2] = offsetAlso / vertexLayout.bytesPerVertex;
			array[1] = sizeAny;
			array[3] = sizeAlso;
			renderingContext.drawMany(Primitive.TRIANGLE, array);
			return sizeAny + sizeAlso;
		}
		
		//throw new RuntimeException("Unsupported vertex layout in "+this);
	}
	
	public enum RenderLodLevel {
		HIGH,
		LOW
	}
	
	public void renderExtras(RenderingInterface renderingInterface) {
		noDrawDeleteConflicts.acquireUninterruptibly();
		if(this.currentData != null) {
			//System.out.println("data(ss)");
			Map<Voxel, DynamicallyRenderedVoxelClass> shit = this.currentData.dynamicallyRenderedVoxels;
			for(Entry<Voxel, DynamicallyRenderedVoxelClass> stuff : shit.entrySet()) {
				
				//System.out.println("extra");
				
				VoxelDynamicRenderer renderer = stuff.getValue().renderer;
				renderer.renderVoxels(renderingInterface, new IterableIterator<ChunkCell>() {

					Iterator<Integer> iindex = stuff.getValue().indexes.iterator();
					ChunkCell cvc = null;
					
					@Override
					public boolean hasNext() {
						while(iindex.hasNext()) {
							if(cvc != null)
								return true;
							
							int index = iindex.next();
							cvc = chunk.peek(index / 1024, (index / 32) % 32, index % 32);
							//if(cvc.getVoxel() != stuff.getKey())
							//	cvc = null;
						}

						if(cvc != null)
							return true;
						
						return false;
					}

					@Override
					public ChunkCell next() {
						ChunkCell cvr = cvc;
						cvc = null;
						return cvr;
						//int index = iindex.next();
						//return chunk.peek(index / 1024, (index / 32) % 32, index % 32);
					}
					
				});
			}
		}
		noDrawDeleteConflicts.release();
	}

	@Override
	public Fence requestMeshUpdate() {
		unbakedUpdates.incrementAndGet();
		
		//System.out.println("who did dis");
		//Thread.dumpStack();
		
		Task fence;
		
		taskLock.lock();
		
		if(task == null || task.isDone() || task.isCancelled()) {
			task = new TaskBakeChunk((ClientChunk) chunk);
			chunk.getWorld().getGameContext().tasks().scheduleTask(task);
		}

		fence = task;
		
		taskLock.unlock();
		
		return fence;
	}

	@Override
	public void spawnUpdateTaskIfNeeded() {
		if(unbakedUpdates.get() > 0) {
			taskLock.lock();
			
			if(task == null || task.isDone() || task.isCancelled()) {
				task = new TaskBakeChunk((ClientChunk) chunk);
				chunk.getWorld().getGameContext().tasks().scheduleTask(task);
			}
			
			taskLock.unlock();
		}
	}

	@Override
	public int pendingUpdates() {
		return this.unbakedUpdates.get();
	}
}
