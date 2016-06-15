package io.xol.chunkstories.api.entity.interfaces;

import io.xol.chunkstories.api.entity.Entity;
import io.xol.chunkstories.entity.core.components.EntityComponentFlying;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public interface EntityFlying extends Entity
{
	public EntityComponentFlying getFlyingComponent();
}