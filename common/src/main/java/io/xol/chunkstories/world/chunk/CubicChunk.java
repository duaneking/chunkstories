//
// This file is a part of the Chunk Stories API codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.chunkstories.world.chunk;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.joml.Vector3dc;

import io.xol.chunkstories.api.Location;
import io.xol.chunkstories.api.entity.Entity;
import io.xol.chunkstories.api.events.voxel.WorldModificationCause;
import io.xol.chunkstories.api.exceptions.world.WorldException;
import io.xol.chunkstories.api.net.packets.PacketVoxelUpdate;
import io.xol.chunkstories.api.rendering.world.chunk.ChunkRenderable;
import io.xol.chunkstories.api.server.RemotePlayer;
import io.xol.chunkstories.api.util.IterableIterator;
import io.xol.chunkstories.api.util.IterableIteratorWrapper;
import io.xol.chunkstories.api.voxel.Voxel;
import io.xol.chunkstories.api.voxel.VoxelFormat;
import io.xol.chunkstories.api.voxel.VoxelSides;
import io.xol.chunkstories.api.voxel.components.VoxelComponent;
import io.xol.chunkstories.api.world.World;
import io.xol.chunkstories.api.world.WorldMaster;
import io.xol.chunkstories.api.world.cell.Cell;
import io.xol.chunkstories.api.world.cell.CellData;
import io.xol.chunkstories.api.world.cell.FutureCell;
import io.xol.chunkstories.api.world.chunk.Chunk;
import io.xol.chunkstories.api.world.chunk.ChunkLightUpdater;
import io.xol.chunkstories.api.world.chunk.Region;
import io.xol.chunkstories.api.world.chunk.WorldUser;
import io.xol.chunkstories.entity.EntitySerializer;
import io.xol.chunkstories.tools.WorldTool;
import io.xol.chunkstories.voxel.components.CellComponentsHolder;
import io.xol.chunkstories.world.WorldImplementation;
import io.xol.chunkstories.world.region.RegionImplementation;
import io.xol.engine.concurrency.SimpleLock;



/**
 * Essential class that holds actual chunk voxel data, entities and voxel
 * component !
 */
public class CubicChunk implements Chunk {
	final protected WorldImplementation world;
	final protected RegionImplementation holdingRegion;
	final protected ChunkHolderImplementation chunkHolder;

	final protected int chunkX;
	final protected int chunkY;
	final protected int chunkZ;
	final protected int uuid;

	// Actual data holding here
	public int[] chunkVoxelData = null;

	// Count unsaved edits atomically, fancy :]
	public final AtomicInteger compr_uncomittedBlockModifications = new AtomicInteger();

	public final ChunkOcclusionUpdater occlusion = new ChunkOcclusionUpdater(this);
	// public final AtomicInteger occl_compr_uncomittedBlockModifications = new
	// AtomicInteger();

	public final ChunkLightBaker lightBaker;

	protected final Map<Integer, CellComponentsHolder> allCellComponents = new HashMap<Integer, CellComponentsHolder>();
	protected final Set<Entity> localEntities = ConcurrentHashMap.newKeySet();

	protected final SimpleLock componentsLock = new SimpleLock();
	protected final SimpleLock entitiesLock = new SimpleLock();

	private Semaphore chunkDataArrayCreation = new Semaphore(1);

	public CubicChunk(ChunkHolderImplementation holder, int chunkX, int chunkY, int chunkZ) {
		this(holder, chunkX, chunkY, chunkZ, null);
	}

	public CubicChunk(ChunkHolderImplementation holder, int chunkX, int chunkY, int chunkZ, CompressedData data) {
		this.chunkHolder = holder;

		this.holdingRegion = holder.getRegion();
		this.world = holdingRegion.getWorld();

		this.chunkX = chunkX;
		this.chunkY = chunkY;
		this.chunkZ = chunkZ;

		this.uuid = ((chunkX << world.getWorldInfo().getSize().bitlengthOfVerticalChunksCoordinates) | chunkY) << world
				.getWorldInfo().getSize().bitlengthOfHorizontalChunksCoordinates | chunkZ;

		lightBaker = new ChunkLightBaker(this);
		
		if (data != null) {
			try {
				this.chunkVoxelData = data.getVoxelData();

				if (data.voxelComponentsCompressedData != null) {
					ByteArrayInputStream bais = new ByteArrayInputStream(data.voxelComponentsCompressedData);
					DataInputStream dis = new DataInputStream(bais);

					byte[] smallArray = new byte[4096];
					ByteArrayInputStream bias = new ByteArrayInputStream(smallArray);
					DataInputStream dias = new DataInputStream(bias);

					byte keepGoing = dis.readByte();
					while (keepGoing != 0x00) {
						int index = dis.readInt();
						CellComponentsHolder components = new CellComponentsHolder(this, index);
						allCellComponents.put(index, components);

						boolean once = true;
						
						String componentName = dis.readUTF();
						while (!componentName.equals("\n")) {
							// System.out.println("componentName: "+componentName);

							// Read however many bytes this component wrote
							int bytes = dis.readShort();
							dis.readFully(smallArray, 0, bytes);

							if(once) {
								// Call the block's onPlace method as to make it spawn the necessary components
								FreshChunkCell peek = peek(components.getX(), components.getY(), components.getZ());
								FreshFutureCell future = new FreshFutureCell(peek);
								
								//peek.getVoxel().onPlace(future, null);
								peek.getVoxel().whenPlaced(future);
								once = false;
							}

							VoxelComponent component = components.get(componentName);
							if (component == null) {
								System.out.println("Error, a component named " + componentName + " was saved, but it was not recreated by the voxel whenPlaced() method.");
							} else {
								// Hope for the best
								component.pull(holder.getRegion().handler, dias);
							}
							
							dias.reset();
							componentName = dis.readUTF();
						}
						keepGoing = dis.readByte();
					}
				}

				if (data.entitiesCompressedData != null) {
					ByteArrayInputStream bais = new ByteArrayInputStream(data.entitiesCompressedData);
					DataInputStream dis = new DataInputStream(bais);

					// Read entities until we hit -1
					Entity entity = null;
					do {
						entity = EntitySerializer.readEntityFromStream(dis, holder.getRegion().handler, world);
						if (entity != null) {
							this.addEntity(entity);
							world.addEntity(entity);
						}
					} while (entity != null);
				}
			} catch (UnloadableChunkDataException | IOException e) {

				System.out.println(e.getMessage());
				e.printStackTrace();
			}
		}

		// Send chunk to whoever already subscribed
		if (data == null)
			data = new CompressedData(null, null, null);
	}

	public int getChunkX() {
		return chunkX;
	}

	public int getChunkY() {
		return chunkY;
	}

	public int getChunkZ() {
		return chunkZ;
	}

	private int sanitizeCoordinate(int a) {
		return a & 0x1F;
	}

	@Override
	public ActualChunkVoxelContext peek(int x, int y, int z) {
		return new ActualChunkVoxelContext(x, y, z, peekRaw(x, y, z));
	}

	@Override
	public ActualChunkVoxelContext peek(Vector3dc location) {
		return peek((int) (double) location.x(), (int) (double) location.y(), (int) (double) location.z());
	}

	@Override
	public Voxel peekSimple(int x, int y, int z) {
		return world.getContentTranslator().getVoxelForId(VoxelFormat.id(peekRaw(x, y, z)));
	}

	@Override
	public int peekRaw(int x, int y, int z) {
		if (chunkVoxelData == null) // Empty chunk ? Use the heightmap to figure out wether or not that cell should
									// be skylit.
			return VoxelFormat.format(0, 0, world.getRegionsSummariesHolder().getHeightAtWorldCoordinates(x, z) >= y ? 0 : 15, 0);
		else {
			//Thread.dumpStack();
			//System.out.println(x+":"+y+":"+z);
			x = sanitizeCoordinate(x);
			y = sanitizeCoordinate(y);
			z = sanitizeCoordinate(z);
			return chunkVoxelData[x * 32 * 32 + y * 32 + z];
		}
	}

	public ChunkCell poke(int x, int y, int z, Voxel voxel, int sunlight, int blocklight, int metadata,
			WorldModificationCause cause) {
		return pokeInternal(x, y, z, voxel, sunlight, blocklight, metadata, 0x00, false, true, true, cause);
	}

	public void pokeSimple(int x, int y, int z, Voxel voxel, int sunlight, int blocklight, int metadata) {
		pokeInternal(x, y, z, voxel, sunlight, blocklight, metadata, 0x00, false, true, false, null);
	}

	public void pokeSimpleSilently(int x, int y, int z, Voxel voxel, int sunlight, int blocklight, int metadata) {
		pokeInternal(x, y, z, voxel, sunlight, blocklight, metadata, 0x00, false, false, false, null);
	}

	public void pokeRaw(int x, int y, int z, int raw_data_bits) {
		pokeInternal(x, y, z, null, 0, 0, 0, raw_data_bits, true, true, false, null);
	}

	public void pokeRawSilently(int x, int y, int z, int raw_data_bits) {
		pokeInternal(x, y, z, null, 0, 0, 0, raw_data_bits, true, false, false, null);
	}

	class FreshFutureCell extends FutureCell implements FreshChunkCell {

		public FreshFutureCell(CellData ogContext) {
			super(ogContext);
		}

		@Override
		public Chunk getChunk() {
			return CubicChunk.this;
		}

		@Override
		public CellComponentsHolder components() {
			return CubicChunk.this.components(x, y, z);
		}

		@Override
		public void registerComponent(String name, VoxelComponent component) {
			components().put(name, component);
		}
		
	}
	
	/**
	 * The 'core' of the core, this private function is responsible for placing and
	 * keeping everyone up to snuff on block modifications. It all comes back to this really.
	 */
	private ActualChunkVoxelContext pokeInternal(final int worldX, final int worldY, final int worldZ, Voxel newVoxel,
			final int sunlight, final int blocklight, final int metadata, int raw_data, final boolean use_raw_data,
			final boolean update, final boolean return_context, final WorldModificationCause cause) {
		int x = sanitizeCoordinate(worldX);
		int y = sanitizeCoordinate(worldY);
		int z = sanitizeCoordinate(worldZ);

		ActualChunkVoxelContext cell_pre = peek(x, y, z);
		Voxel formerVoxel = cell_pre.getVoxel();
		assert formerVoxel != null;
		
		FreshFutureCell future = new FreshFutureCell(cell_pre);

		if (use_raw_data) {
			// We need this for voxel placement logic
			newVoxel = world.getContentTranslator().getVoxelForId(VoxelFormat.id(raw_data));
			// Build the future from parsing the raw data
			future.setVoxel(newVoxel);
			future.setSunlight(VoxelFormat.sunlight(raw_data));
			future.setBlocklight(VoxelFormat.blocklight(raw_data));
			future.setMetaData(VoxelFormat.meta(raw_data));
		} else {
			// Build the raw data from the set parameters by editing the in-place data
			// (because we allow only editing some aspects of the cell data)
			raw_data = cell_pre.getData();
			if (newVoxel != null) {
				raw_data = VoxelFormat.changeId(raw_data, world.getContentTranslator().getIdForVoxel(newVoxel));
				future.setVoxel(newVoxel);
			}
			if (sunlight >= 0) {
				raw_data = VoxelFormat.changeSunlight(raw_data, sunlight);
				future.setSunlight(sunlight);
			}
			if (blocklight >= 0) {
				raw_data = VoxelFormat.changeBlocklight(raw_data, blocklight);
				future.setBlocklight(blocklight);
			}
			if (metadata >= 0) {
				raw_data = VoxelFormat.changeMeta(raw_data, metadata);
				future.setMetaData(metadata);
			}
		}

		try {
			if (newVoxel == null || formerVoxel.equals(newVoxel)) {
				formerVoxel.onModification(cell_pre, future, cause);
			} else {
				formerVoxel.onRemove(cell_pre, cause);
				newVoxel.onPlace(future, cause);
			}
		} catch (WorldException e) {
			// Abort !
			if (return_context)
				return cell_pre;
				//throw e;
			else
				return null;
		}

		// Allocate if it makes sense
		if (chunkVoxelData == null)
			chunkVoxelData = atomicalyCreateInternalData();

		chunkVoxelData[x * 32 * 32 + y * 32 + z] = raw_data;
		
		newVoxel.whenPlaced(future);

		// Update lightning
		if (update)
			lightBaker.computeLightSpread(x, y, z, cell_pre.raw_data, raw_data);

		// Increment the modifications counter
		compr_uncomittedBlockModifications.incrementAndGet();

		// Don't spam the thread creation spawn
		occlusion.unbakedUpdates.incrementAndGet();

		// Update related summary
		if (update)
			world.getRegionsSummariesHolder().updateOnBlockPlaced(x, y, z, future);

		// Mark the nearby chunks to be re-rendered
		if (update) {
			int sx = chunkX;
			int ex = sx;
			int sy = chunkY;
			int ey = sy;
			int sz = chunkZ;
			int ez = sz;

			if (x == 0)
				sx--;
			else if (x == 31)
				ex++;

			if (y == 0)
				sy--;
			else if (y == 31)
				ey++;

			if (z == 0)
				sz--;
			else if (z == 31)
				ez++;

			for (int ix = sx; ix <= ex; ix++)
				for (int iy = sy; iy <= ey; iy++)
					for (int iz = sz; iz <= ez; iz++) {
						Chunk chunk = world.getChunk(ix, iy, iz);
						if (chunk != null && chunk instanceof ChunkRenderable)
							((ChunkRenderable) chunk).meshUpdater().requestMeshUpdate();
					}
		}

		// If this is a 'master' world, notify remote users of the change !
		if (update && world instanceof WorldMaster && !(world instanceof WorldTool)) {
			PacketVoxelUpdate packet = new PacketVoxelUpdate(
					new ActualChunkVoxelContext(chunkX * 32 + x, chunkY * 32 + y, chunkZ * 32 + z, raw_data));

			Iterator<WorldUser> pi = this.chunkHolder.users.iterator();
			while (pi.hasNext()) {
				WorldUser user = pi.next();
				if (!(user instanceof RemotePlayer))
					continue;

				RemotePlayer player = (RemotePlayer) user;

				Entity clientEntity = player.getControlledEntity();
				if (clientEntity == null)
					continue; // Ignore clients that aren't playing

				player.pushPacket(packet);
			}
		}

		if (return_context)
			return new ActualChunkVoxelContext(chunkX * 32 + x, chunkY * 32 + y, chunkZ * 32 + z, raw_data);
		else
			return null;
	}

	@Override
	public CellComponentsHolder components(int x, int y, int z) {
		x &= 0x1f;
		y &= 0x1f;
		z &= 0x1f;
		
		int index = x * 1024 + y * 32 + z;
		//System.out.println(index);

		CellComponentsHolder components = allCellComponents.get(index);
		if (components == null) {
			components = new CellComponentsHolder(this, index);
			allCellComponents.put(index, components);
		}
		return components;
	}

	public void removeComponents(int index) {
		allCellComponents.remove(index);
	}

	private int[] atomicalyCreateInternalData() {
		chunkDataArrayCreation.acquireUninterruptibly();

		// If it's STILL null
		if (chunkVoxelData == null)
			chunkVoxelData = new int[32 * 32 * 32];

		chunkDataArrayCreation.release();

		return chunkVoxelData;
	}

	@Override
	public String toString() {
		return "[CubicChunk x:" + this.chunkX + " y:" + this.chunkY + " z:" + this.chunkZ + " air:" + isAirChunk()
				+ " lS:" + this.lightBaker + "]";
	}

	public boolean isAirChunk() {
		return chunkVoxelData == null;
	}

	@Override
	public World getWorld() {
		return world;
	}

	@Override
	public Region getRegion() {
		return holdingRegion;
	}

	public ChunkHolderImplementation holder() {
		return chunkHolder;
	}

	@Override
	public int hashCode() {
		return uuid;
	}

	class ActualChunkVoxelContext extends Cell implements ChunkCell, FreshChunkCell {

		int raw_data;

		public ActualChunkVoxelContext(int x, int y, int z, int data) {
			super((x & 0x1F) + (chunkX << 5), (y & 0x1F) + (chunkY << 5), (z & 0x1F) + (chunkZ << 5), 
					world.getContentTranslator().getVoxelForId(VoxelFormat.id(data)), 
					VoxelFormat.meta(data), VoxelFormat.blocklight(data), VoxelFormat.sunlight(data));
			
			this.raw_data = data;
			
			//System.out.println(chunkX << 5);
			//System.out.println(x+":"+y+":"+z);
			//System.out.println(this.getZ() - z);
			/*this.x = x & 0x1F;
			this.y = y & 0x1F;
			this.z = z & 0x1F;
			this.voxel = world.getContentTranslator().getVoxelForId(data);*/
		}

		@Override
		public World getWorld() {
			return world;
		}

		@Override
		public Location getLocation() {
			return new Location(world, getX(), getY(), getZ());
		}

		@Override
		public int getData() {
			return raw_data;
		}

		@Deprecated
		public int getNeightborData(int side) {
			switch (side) {
			case (0):
				return world.peekRaw(getX() - 1, getY(), getZ());
			case (1):
				return world.peekRaw(getX(), getY(), getZ() + 1);
			case (2):
				return world.peekRaw(getX() + 1, getY(), getZ());
			case (3):
				return world.peekRaw(getX(), getY(), getZ() - 1);
			case (4):
				return world.peekRaw(getX(), getY() + 1, getZ());
			case (5):
				return world.peekRaw(getX(), getY() - 1, getZ());
			}
			throw new RuntimeException("Fuck off");
		}

		@Override
		public Chunk getChunk() {
			return CubicChunk.this;
		}

		@Override
		public CellComponentsHolder components() {
			return CubicChunk.this.components(x, y, z);
		}

		@Override
		public CellData getNeightbor(int side_int) {
			// TODO Fast path for in-chunk neigtbor
			VoxelSides side = VoxelSides.values()[side_int];
			return world.peekSafely(getX() + side.dx, getY() + side.dy, getZ() + side.dz);
		}

		@Override
		public void setVoxel(Voxel voxel) {
			super.setVoxel(voxel);
			poke();
			peek();
		}

		@Override
		public void setMetaData(int metadata) {
			super.setMetaData(metadata);
			poke();
			peek();
		}

		@Override
		public void setSunlight(int sunlight) {
			super.setSunlight(sunlight);
			poke();
			peek();
		}

		@Override
		public void setBlocklight(int blocklight) {
			super.setBlocklight(blocklight);
			poke();
			peek();
		}
		
		private void peek() {
			raw_data = CubicChunk.this.peekRaw(x, y, z);
		}
		
		private void poke() {
			CubicChunk.this.pokeSimple(x, y, z, voxel, sunlight, blocklight, metadata);
		}

		@Override
		public void registerComponent(String name, VoxelComponent component) {
			components().put(name, component);
		}
	}

	@Override
	public void destroy() {
		this.lightBaker.destroy();
	}

	@Override
	public void addEntity(Entity entity) {
		entitiesLock.lock();
		localEntities.add(entity);
		entitiesLock.unlock();
	}

	@Override
	public void removeEntity(Entity entity) {
		entitiesLock.lock();
		localEntities.remove(entity);
		entitiesLock.unlock();
	}

	@Override
	public IterableIterator<Entity> getEntitiesWithinChunk() {
		return new IterableIteratorWrapper<Entity>(localEntities.iterator()) {

			@Override
			public void remove() {

				entitiesLock.lock();
				super.remove();
				entitiesLock.unlock();
			}

		};
	}

	@Override
	public ChunkLightUpdater lightBaker() {
		return lightBaker;
	}
}
