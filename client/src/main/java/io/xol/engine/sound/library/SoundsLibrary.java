//
// This file is a part of the Chunk Stories API codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.engine.sound.library;

import java.util.HashMap;
import java.util.Map;

import io.xol.chunkstories.client.Client;
import io.xol.engine.sound.SoundData;
import io.xol.engine.sound.SoundDataBuffered;
import io.xol.engine.sound.ogg.SoundDataOggSample;
import io.xol.engine.sound.ogg.SoundDataOggStream;

public class SoundsLibrary
{
	// Internal class that stores the sounds
	
	static Map<String, SoundData> soundsData = new HashMap<String, SoundData>();
	
	public static SoundData obtainOggSample(String soundEffect)
	{
		SoundDataOggSample sd = new SoundDataOggSample(Client.getInstance().getContent().getAsset(soundEffect));
		sd.name = soundEffect;
		if(sd.loadedOk())
			return sd;
		return null;
	}

	public static SoundData obtainSample(String soundEffect)
	{
		if(soundEffect == null)
			return null;

		if(!soundEffect.startsWith("./"))
			soundEffect = "./" + soundEffect;
		
		SoundData sd = soundsData.get(soundEffect);
		if(sd != null)
			return sd;
		if (soundEffect.endsWith(".ogg"))
		{
			sd = obtainOggSample(soundEffect);
		}
		else
			return null;
		if(sd == null)
			return null;
		soundsData.put(soundEffect, sd);
		return sd;
	}
	
	public static void clean()
	{
		for(SoundData sd : soundsData.values())
		{
			sd.destroy();
		}
		soundsData.clear();
	}

	/**
	 * This methods returns an unique SoundData used exclusivly for a specific SoundSourceBuffered
	 * @param musicName
	 * @return
	 */
	public static SoundData obtainBufferedSample(String musicName)
	{
		if(!musicName.startsWith("./"))
			musicName = "./" + musicName;
		
		SoundDataBuffered sd;
		if (musicName.endsWith(".ogg"))
		{
			sd = obtainOggStream(musicName);
		}
		else
			return null;
		return sd;
	}

	private static SoundDataBuffered obtainOggStream(String musicName)
	{
		SoundDataOggStream sd;
		
		sd = new SoundDataOggStream(Client.getInstance().getContent().getAsset(musicName).read());
		sd.name = musicName;
		if(sd.loadedOk())
			return sd;
		
		return null;
	}
	
}
