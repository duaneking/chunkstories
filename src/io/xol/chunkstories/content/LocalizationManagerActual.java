package io.xol.chunkstories.content;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.xol.chunkstories.api.Content;
import io.xol.chunkstories.api.Content.LocalizationManager;
import io.xol.chunkstories.api.Content.Translation;
import io.xol.chunkstories.api.mods.Asset;
import io.xol.chunkstories.api.mods.ModsManager;
import io.xol.chunkstories.client.Client;
import io.xol.chunkstories.content.GameContentStore;
import io.xol.engine.animation.BVHAnimation;

//(c) 2015-2017 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

public class LocalizationManagerActual implements LocalizationManager
{
	// This class holds static model info

	private final GameContentStore store;
	private final ModsManager modsManager;
	
	private Map<String, Asset> translations = new HashMap<String, Asset>();
	private Translation activeTranslation;
	
	public LocalizationManagerActual(GameContentStore store, String translation)
	{
		this.store = store;
		this.modsManager = store.modsManager();
		
		reload();
		
		loadTranslation(translation);
	}

	private void loadTranslation(String translation)
	{
		activeTranslation = new ActualTranslation(translations.get(translation));
	}

	public void reload()
	{
		translations.clear();
		
		Iterator<Asset> i = modsManager.getAllAssetsByPrefix("./lang/");
		while(i.hasNext())
		{
			Asset a = i.next();
			if(a.getName().endsWith("lang.info"))
			{
				String abrigedName = a.getName().substring(7, a.getName().length() - 10);
				if(abrigedName.contains("/"))
					continue;
				//System.out.println("Found translation: "+abrigedName);
				translations.put(abrigedName, a);
			}
		}
		
		
	}
	
	public class ActualTranslation implements Translation {

		private Asset a;
		private Map<String, String> strings = new HashMap<String, String>();
		
		public ActualTranslation(Asset a)
		{
			this.a = a;
			//System.out.println("Loading translation from asset asset: "+a);
			
			String prefix = a.getName().substring(0, a.getName().length() - 9);
			Iterator<Asset> i = modsManager.getAllAssetsByPrefix(prefix);
			while(i.hasNext())
			{
				Asset a2 = i.next();
				if(a2.getName().endsWith(".lang"))
				{
					BufferedReader reader = new BufferedReader(new InputStreamReader(a2.read()));
					String line = "";

					try {
						while ((line = reader.readLine()) != null)
						{
							String name = line.split(" ")[0];
							
							int indexOf = line.indexOf(" ");
							if(indexOf == -1)
								continue;
							String text = line.substring(indexOf + 1);
							
							//System.out.println("name: "+name+" text: "+text);
							strings.put(name, text);
						}	
						reader.close();
					}
					catch(IOException e)
					{
						
					}
					
				}
			}
		}

		@Override
		public String getLocalizedString(String stringName)
		{
			return strings.get(stringName);
		}

		@Override
		public String localize(String text)
		{
			char[] array = text.toCharArray();
			StringBuilder sb = new StringBuilder();
			for(int i = 0; i < array.length; i++)
			{
				char c = array[i];
				if(c == '#')
				{
					if(i < array.length - 1 && array[i + 1] == '{')
					{
						int endIndex = text.indexOf("}", i + 1);
						String word = text.substring(i + 2, endIndex);
						//System.out.println("Found word: "+word);
						
						String translated = getLocalizedString(word);
						sb.append(translated != null ? translated : word);
						i+=(translated != null ? translated : word).length() + 2;
					}
					else
						sb.append(c);
				}
				else
					sb.append(c);
			}
			return sb.toString();
		}
		
	}

	@Override
	public String getLocalizedString(String stringName)
	{
		return activeTranslation.getLocalizedString(stringName);
	}

	@Override
	public String localize(String text)
	{
		return activeTranslation.localize(text);
	}

	@Override
	public Content parent()
	{
		return this.store;
	}
}
