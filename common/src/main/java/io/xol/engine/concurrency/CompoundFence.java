//
// This file is a part of the Chunk Stories API codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.engine.concurrency;

import java.util.LinkedList;

import io.xol.chunkstories.api.util.concurrency.Fence;

public class CompoundFence extends LinkedList<Fence> implements Fence {

	private static final long serialVersionUID = 1770973697744619763L;

	@Override
	/** Traverse-all :) */
	public void traverse() {
		for(Fence f : this) {
			f.traverse();
		}
	}

}
