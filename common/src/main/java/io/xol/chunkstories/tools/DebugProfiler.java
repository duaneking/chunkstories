//
// This file is a part of the Chunk Stories API codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.chunkstories.tools;

import java.util.LinkedList;



public class DebugProfiler
{

	// Quick & Dirty profiler for the game

	public long lastReset = 0;
	public long currentSection = 0;
	public String currentSectionName = "default";

	//public String profiling = "";

	class ProfileSection
	{
		String name;
		long timeTookNs;

		public ProfileSection(String name, long timeTookNs)
		{
			this.name = name;
			this.timeTookNs = timeTookNs;
		}

		@Override
		public String toString()
		{
			return "[" + name + ":" + Math.floor(timeTookNs / 10000) / 100.0f + "]";
		}

		@Override
		public boolean equals(Object obj)
		{
			if (obj instanceof ProfileSection)
				return ((ProfileSection) obj).name.equals(name);
			return super.equals(obj);
		}

		@Override
		public int hashCode()
		{
			return name.hashCode();
		}
	}

	public LinkedList<ProfileSection> sections = new LinkedList<ProfileSection>();

	public StringBuilder reset(String name)
	{
		//Timings
		long took = System.nanoTime() - currentSection;

		//Reset timers
		currentSection = System.nanoTime();
		lastReset = System.nanoTime();

		//Build string and reset sections
		StringBuilder txt = new StringBuilder("");
		for (ProfileSection section : sections)
		{
			txt.append(section);
			txt.append(" ");
			//txt += section + " ";
		}
		sections.clear();
		//Add new section
		ProfileSection section = new ProfileSection(name, took);
		sections.add(section);

		return txt;
	}

	public StringBuilder reset()
	{
		return reset("default");
	}

	public void startSection(String name)
	{
		long took = System.nanoTime() - currentSection;
		ProfileSection section = new ProfileSection(name, took);
		sections.add(section);
		currentSection = System.nanoTime();
		currentSectionName = name;
	}

	public int timeTook()
	{
		return (int) (System.nanoTime() - lastReset) / 1000000;
	}
}
