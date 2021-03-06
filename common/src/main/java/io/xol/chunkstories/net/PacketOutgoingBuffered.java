//
// This file is a part of the Chunk Stories API codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.chunkstories.net;

import java.io.DataOutputStream;
import java.io.IOException;

public class PacketOutgoingBuffered implements PacketOutgoing {
	final PacketsContextCommon context;
	final short id;
	final int payload_size;
	final byte[] payload;

	public PacketOutgoingBuffered(PacketsContextCommon context, short id, int payload_size, byte[] payload) {
		this.context = context;

		this.id = id;
		this.payload_size = payload_size;
		this.payload = payload;
	}

	private void writePacketIdHeader(DataOutputStream out, short id) throws IOException {
		if (id < 127)
			out.writeByte((byte) id);
		else {
			out.writeByte((byte) (0x80 | id >> 8));
			out.writeByte((byte) (id % 256));
		}
	}

	public void write(DataOutputStream out) throws IOException {
		writePacketIdHeader(out, id);
		out.writeInt(payload_size);
		out.write(payload);
	}
}
