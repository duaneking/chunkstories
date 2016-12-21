package io.xol.chunkstories.core.entity;

import io.xol.chunkstories.api.rendering.RenderingInterface;
import io.xol.chunkstories.api.rendering.entity.EntityRenderable;
import io.xol.chunkstories.api.rendering.entity.EntityRenderer;
import io.xol.chunkstories.api.rendering.entity.RenderingIterator;
import io.xol.chunkstories.api.world.WorldAuthority;
import io.xol.chunkstories.entity.EntityImplementation;
import io.xol.chunkstories.item.ItemPile;
import io.xol.chunkstories.world.WorldImplementation;
import io.xol.engine.math.lalgb.Matrix4f;
import io.xol.engine.math.lalgb.vector.sp.Vector3fm;


//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class EntityGroundItem extends EntityImplementation implements EntityRenderable
{
	private long spawnTime;
	private final EntityGroundItemPileComponent itemPileWithin;
	
	public EntityGroundItem(WorldImplementation world, double x, double y, double z)
	{
		super(world, x, y, z);
		itemPileWithin = new EntityGroundItemPileComponent(this);
	}
	
	public EntityGroundItem(WorldImplementation world, double x, double y, double z, ItemPile itemPile)
	{
		super(world, x, y, z);
		itemPileWithin = new EntityGroundItemPileComponent(this, itemPile);
		spawnTime = System.currentTimeMillis();
	}

	public ItemPile getItemPile()
	{
		return itemPileWithin.itemPile;
	}
	
	public void setItemPile(ItemPile itemPile)
	{
		itemPileWithin.setItemPile(itemPile);
		spawnTime = System.currentTimeMillis();
	}
	
	public boolean canBePickedUpYet()
	{
		return System.currentTimeMillis() - spawnTime > 2000L;
	}
	
	@Override
	public void tick(WorldAuthority authority)
	{
		this.moveWithCollisionRestrain(0, -0.05, 0, true);
		super.tick(authority);
	}
	
	
	static EntityRenderer<EntityGroundItem> entityRenderer = new EntityGroundItemRenderer();
	
	static class EntityGroundItemRenderer implements EntityRenderer<EntityGroundItem> {

		@Override
		public void setupRender(RenderingInterface renderingInterface)
		{
			//Not much.
		}

		@Override
		public int forEach(RenderingInterface renderingInterface, RenderingIterator<EntityGroundItem> renderableEntitiesIterator)
		{
			int i = 0;
			
			while(renderableEntitiesIterator.hasNext())
			{
				EntityGroundItem e = renderableEntitiesIterator.next();
				
				ItemPile within = e.itemPileWithin.getItemPile();
				if(within != null)
				{
					Matrix4f matrix = new Matrix4f();
					matrix.translate(e.getLocation().add(0.0, 0.25, 0.0).castToSinglePrecision());
					matrix.rotate((float)Math.PI/2, new Vector3fm(1,0 ,0));
					//System.out.println("Rendering ItemPileOnGround "+e+"IS:"+within);
					within.getItem().getItemRenderer().renderItemInWorld(renderingInterface, within, e.getWorld(), e.getLocation(), matrix);
					renderingInterface.flush();
				}
				else
				{
					System.out.println("ded");
				}
				
				i++;
			}
			
			return i;
		}

		@Override
		public void freeRessources()
		{
			//Not much either
		}
		
	}
	
	@Override
	public EntityRenderer<? extends EntityRenderable> getEntityRenderer()
	{
		return entityRenderer;
	}
}