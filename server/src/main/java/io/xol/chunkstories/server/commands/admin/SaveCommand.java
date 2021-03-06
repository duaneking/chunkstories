//
// This file is a part of the Chunk Stories API codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.chunkstories.server.commands.admin;

import io.xol.chunkstories.api.plugin.commands.Command;
import io.xol.chunkstories.api.plugin.commands.CommandEmitter;
import io.xol.chunkstories.api.server.ServerInterface;
import io.xol.chunkstories.server.commands.ServerCommandBasic;

public class SaveCommand extends ServerCommandBasic {

	public SaveCommand(ServerInterface serverConsole) {
		super(serverConsole);
		server.getPluginManager().registerCommand("save").setHandler(this);
	}

	@Override
	public boolean handleCommand(CommandEmitter emitter, Command command, String[] arguments) {
		if (command.getName().equals("save") && emitter.hasPermission("server.admin.forcesave"))
		{
			emitter.sendMessage("#00FFD0Saving the world...");
			server.getWorld().saveEverything();
			return true;
		}
		return false;
	}

}
