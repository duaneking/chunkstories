package io.xol.chunkstories.server.commands.debug;

import io.xol.chunkstories.api.plugin.commands.Command;
import io.xol.chunkstories.api.plugin.commands.CommandEmitter;
import io.xol.chunkstories.api.server.ServerInterface;
import io.xol.chunkstories.server.commands.ServerCommandBasic;
import io.xol.chunkstories.workers.WorkerThreadPool;

//(c) 2015-2017 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class DebugTasksCommand extends ServerCommandBasic{

	public DebugTasksCommand(ServerInterface serverConsole) {
		super(serverConsole);
		server.getPluginManager().registerCommand("tasks").setHandler(this);
	}

	@Override
	public boolean handleCommand(CommandEmitter emitter, Command command, String[] arguments) {
		if (command.getName().equals("tasks") && emitter.hasPermission("server.debug"))
		{
			emitter.sendMessage("#00FFD0Tasks in the pipeline: " + server.tasks().submittedTasks());
			((WorkerThreadPool) server.tasks()).dumpTasks();
			return true;
		}
		return false;
	}

}