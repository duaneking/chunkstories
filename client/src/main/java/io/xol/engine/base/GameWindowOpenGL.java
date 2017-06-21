package io.xol.engine.base;

//(c) 2015-2017 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.JOptionPane;

import org.lwjgl.LWJGLException;

import io.xol.chunkstories.api.rendering.GameWindow;
import io.xol.chunkstories.client.Client;
import io.xol.chunkstories.client.RenderingConfig;
import io.xol.chunkstories.gui.OverlayableScene;
import io.xol.chunkstories.gui.overlays.general.MessageBoxOverlay;
import io.xol.chunkstories.renderer.debug.FrametimeRenderer;
import io.xol.chunkstories.tools.ChunkStoriesLoggerImplementation;
import io.xol.engine.graphics.GLCalls;
import io.xol.engine.graphics.RenderingContext;
import io.xol.engine.graphics.geometry.VertexBufferGL;
import io.xol.engine.graphics.textures.Texture2DGL;
import io.xol.engine.gui.Scene;
import io.xol.engine.misc.CPUModelDetection;
import io.xol.engine.misc.IconLoader;
import io.xol.engine.sound.ALSoundManager;

import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.ARBDebugOutput;
import org.lwjgl.opengl.ARBDebugOutputCallback;
import org.lwjgl.opengl.ContextAttribs;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.PixelFormat;

public class GameWindowOpenGL implements GameWindow
{
	private final long mainGLThreadId;
	private final Thread mainGLThread;

	private Client client;
	public RenderingContext renderingContext;
	private ALSoundManager soundManager;

	private Scene currentScene = null;

	public String windowName;
	private int windowWidth = 1024;
	private int windowHeight = 640;
	
	private boolean forceResizeFlag = false;
	private int targetFPS = 60;

	public final static String engineVersion = "2.3";

	private static GameWindowOpenGL instance;

	private boolean closeRequest = false;

	private String[] modes;

	private long lastTimeMS = 0;
	private int framesSinceLS = 0;
	private int lastFPS = 0;
	private long lastTime = 0;

	public long vramUsageVerticesObjects = 0;

	private Queue<Runnable> mainThreadQueue = new ConcurrentLinkedQueue<Runnable>();

	public GameWindowOpenGL(Client client, String name, int width, int height)
	{
		if (width != -1)
			windowWidth = width;
		if (height != -1)
			windowHeight = height;
		this.windowName = name;

		this.client = client;
		instance = this;

		//Initialize sound
		soundManager = new ALSoundManager();

		mainGLThreadId = Thread.currentThread().getId();
		mainGLThread = Thread.currentThread();
	}

	public void createOpenGLContext()
	{
		System.out.println("Initializing XolioWare Interactive 3D Engine v" + engineVersion + " [game:" + windowName + ", width:" + windowWidth + ", height:" + windowHeight + "]");
		try
		{
			computeDisplayModes();

			Display.setDisplayMode(new DisplayMode(windowWidth, windowHeight));
			Display.setTitle(windowName);
			Display.setResizable(true);
			PixelFormat pixelFormat = new PixelFormat();
			Display.create(pixelFormat, new ContextAttribs(3, 2).withProfileCore(true).withDebug(true));

			systemInfo();
			glInfo();
			switchResolution();

			Keyboard.enableRepeatEvents(true);

			renderingContext = new RenderingContext(this);
		}
		catch (Exception e)
		{
			System.out.println("A fatal error occured ! If you see the dev, show him this message !");
			e.printStackTrace();
			e.printStackTrace(ChunkStoriesLoggerImplementation.getInstance().getPrintWriter());
		}
	}

	private void systemInfo()
	{
		// Will print some debug information on the general context
		ChunkStoriesLoggerImplementation.getInstance().log("Running on " + System.getProperty("os.name"));
		ChunkStoriesLoggerImplementation.getInstance().log(Runtime.getRuntime().availableProcessors() + " avaible CPU cores");
		ChunkStoriesLoggerImplementation.getInstance().log("Trying cpu detection : " + CPUModelDetection.detectModel());
		long allocatedRam = Runtime.getRuntime().maxMemory();
		ChunkStoriesLoggerImplementation.getInstance().log("Allocated ram : " + allocatedRam);
		if (allocatedRam < 1000000000L)
		{
			//Warn user if he gave the game too few ram
			ChunkStoriesLoggerImplementation.getInstance().log("Less than 1Gb of ram detected");
			JOptionPane.showMessageDialog(null, "Not enought ram, we will offer NO support for crashes and issues when launching the game with less than 1Gb of ram allocated to it."
					+ "\n Use the official launcher to launch the game properly, or add -Xmx1G to the java command.");
		}
	}

	private void glInfo()
	{
		// Will print some debug information on the openGL context
		String glVersion = glGetString(GL_VERSION);
		ChunkStoriesLoggerImplementation.getInstance().log("Render device : " + glGetString(GL_RENDERER) + " made by " + glGetString(GL_VENDOR) + " driver version " + glVersion);
		// Check OpenGL 3.x capacity
		glVersion = glVersion.split(" ")[0];
		float glVersionf = Float.parseFloat(glVersion.split("\\.")[0] + "." + glVersion.split("\\.")[1]);
		ChunkStoriesLoggerImplementation.getInstance().log("OpenGL VERSION STRING = " + glGetString(GL_VERSION) + " parsed: " + glVersionf);
		ChunkStoriesLoggerImplementation.getInstance().log("OpenGL Extensions avaible : " + glGetString(GL_EXTENSIONS));
		if (glVersionf < 3.2f)
		{
			RenderingConfig.gl_openGL3Capable = false;
			if (GLContext.getCapabilities().GL_EXT_framebuffer_object && GLContext.getCapabilities().GL_ARB_texture_rg)
			{
				RenderingConfig.gl_fbExtCapable = true;
				ChunkStoriesLoggerImplementation.getInstance().log("Pre-OpenGL 3.0 Hardware with needed extensions support detected.");
			}
			else
			{
				// bien le moyen-�ge ?
				ChunkStoriesLoggerImplementation.getInstance().log("Pre-OpenGL 3.0 Hardware without needed extensions support detected.");
				ChunkStoriesLoggerImplementation.getInstance().log("This game isn't made to run in those conditions, please update your drivers or upgrade your graphics card.");
				JOptionPane.showMessageDialog(null, "Pre-OpenGL 3.0 Hardware without needed extensions support detected.\n" + "This game isn't made to run in those conditions, please update your drivers or upgrade your graphics card.");
				// If you feel brave after all
				if (!RenderingConfig.ignoreObsoleteHardware)
					Runtime.getRuntime().exit(0);
			}
		}
		else
			System.out.println("OpenGL 3.2+ Hardware detected.");

		//Check for various limitations
		RenderingConfig.gl_MaxTextureUnits = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);
		RenderingConfig.gl_IsInstancingSupported = GLContext.getCapabilities().GL_ARB_draw_instanced;
		RenderingConfig.gl_InstancedArrays = GLContext.getCapabilities().GL_ARB_instanced_arrays;

		if(GLContext.getCapabilities().GL_ARB_debug_output && RenderingConfig.DEBUG_OPENGL)
		{
			ChunkStoriesLoggerImplementation.getInstance().log("OpenGL debug output extension supported, installing handler");
			
			glEnable(ARBDebugOutput.GL_DEBUG_OUTPUT_SYNCHRONOUS_ARB);
			
			ARBDebugOutput.glDebugMessageCallbackARB(new ARBDebugOutputCallback( new OpenGLDebugOutputCallback(mainGLThread)));
			
			//Don't need nvidia spam
			if(glGetString(GL_VENDOR).toLowerCase().startsWith("nvidia"))
			{
				System.out.println("Disabling Nvidia gimpworks(tm) log-spamming feature");
				ARBDebugOutput.glDebugMessageControlARB(ARBDebugOutput.GL_DEBUG_SOURCE_API_ARB, ARBDebugOutput.GL_DEBUG_TYPE_OTHER_ARB, 0x826B, null, false);
			}
		}
	}

	public void run()
	{
		try
		{
			IconLoader.load();

			//Oops.. Didn't use any VAOs anywhere so we put this there to be GL 3.2 core compliant
			int vao = glGenVertexArrays();
			glBindVertexArray(vao);

			while (!Display.isCloseRequested() && !closeRequest)
			{
				//Update pending actions
				vramUsageVerticesObjects = VertexBufferGL.updateVerticesObjects();
				Texture2DGL.updateTextureObjects();

				//Clear windows
				renderingContext.getRenderTargetManager().clearBoundRenderTargetAll();

				//Resize window logic
				if (Display.wasResized() || forceResizeFlag)
				{
					System.out.println("What the fuck am I doing here " + Display.wasResized());
					
					if (forceResizeFlag)
						forceResizeFlag = false;
					
					windowWidth = Display.getWidth();
					windowHeight = Display.getHeight();
					
					System.out.println(Display.getWidth() + " : " + Display.getHeight());

					glViewport(0, 0, Display.getWidth(), Display.getHeight());
					
					if (currentScene != null)
						currentScene.onResize();
				}

				//Do scene changes etc
				synchronized (mainThreadQueue)
				{
					Iterator<Runnable> i = mainThreadQueue.iterator();
					while(i.hasNext())
					{
						i.next().run();
						i.remove();
					}
				}

				// Update audio
				soundManager.update();

				// Run scene content
				if (currentScene != null)
				{
					// update inputs first
					//if(currentScene instanceof Ingame)
						client.getInputsManager().pollLWJGLInputs();
					InputAbstractor.update(this, currentScene);

					// then do the game logic
					try
					{
						currentScene.guiHandler.rescaleGui(getScalingFactor());
						if (currentScene instanceof OverlayableScene)
						{
							OverlayableScene o = (OverlayableScene) currentScene;
							if (o.currentOverlay != null)
								o.currentOverlay.guiHandler.rescaleGui(getScalingFactor());
						}
						currentScene.update(renderingContext);
					}
					//Fucking tired of handling npes everywhere
					catch (NullPointerException npe)
					{
						npe.printStackTrace();
					}

					renderingContext.getGuiRenderer().drawBuffer();
					tick();
				}

				//Clamp fps
				if (targetFPS != -1)
					sync(targetFPS);

				//Draw graph
				if (Client.getConfig().getBoolean("frametimeGraph", false))
					FrametimeRenderer.draw(renderingContext);

				//Draw last shit
				GameWindowOpenGL.instance.renderingContext.flush();

				//Update the screen
				Display.update();

				//Reset counters
				GLCalls.nextFrame();
			}
			System.out.println("Copyright 2015-2016 XolioWare Interactive");

			soundManager.destroy();
			Client.onClose();
			Display.destroy();
			System.exit(0);
		}
		catch (Exception e)
		{
			System.out.println("A fatal error occured ! If you see the dev, show him this message !");
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public int getScalingFactor()
	{
		return windowWidth > 1024 ? 2 : 1;
	}

	public void sync(int fps)
	{
		if (fps <= 0)
			return;

		long errorMargin = 1000 * 1000; // 1 millisecond error margin for
										// Thread.sleep()
		long sleepTime = 1000000000 / fps; // nanoseconds to sleep this frame

		// if smaller than sleepTime burn for errorMargin + remainder micro &
		// nano seconds
		long burnTime = Math.min(sleepTime, errorMargin + sleepTime % (1000 * 1000));

		long overSleep = 0; // time the sleep or burn goes over by

		try
		{
			while (true)
			{
				long t = ((Sys.getTime() * 1000000000) / Sys.getTimerResolution()) - lastTime;

				if (t < sleepTime - burnTime)
				{
					Thread.sleep(1);
				}
				else if (t < sleepTime)
				{
					// burn the last few CPU cycles to ensure accuracy
					Thread.yield();
				}
				else
				{
					overSleep = Math.min(t - sleepTime, errorMargin);
					break; // exit while loop
				}
			}
		}
		catch (InterruptedException e)
		{
		}

		lastTime = ((Sys.getTime() * 1000000000) / Sys.getTimerResolution()) - overSleep;
	}

	public void computeDisplayModes()
	{
		try
		{
			DisplayMode[] dms = Display.getAvailableDisplayModes();
			Set<DisplayMode> validModes = new HashSet<DisplayMode>();
			//modes = new String[dms.length];
			for (int i = 0; i < dms.length; i++)
			{
				if (dms[i].isFullscreenCapable() && dms[i].getBitsPerPixel() >= 32 && dms[i].getWidth() >= 640)
				{
					validModes.add(dms[i]);
				}
				else
				{
					//ChunkStoriesLogger.getInstance().info("Rejected displayMode : "+dms[i] + "fs:"+dms[i].isFullscreenCapable());
				}
				//modes[i] = dms[i].getWidth() + "x" + dms[i].getHeight();
			}
			modes = new String[validModes.size()];
			int i = 0;
			for (DisplayMode dm : validModes)
			{
				modes[i] = dm.getWidth() + "x" + dm.getHeight();
				i++;
			}
			ChunkStoriesLoggerImplementation.getInstance().info(modes.length + " display modes avaible.");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public String[] getDisplayModes()
	{
		return modes;
	}

	public void switchResolution()
	{
		try
		{
			if (Client.getConfig().getBoolean("fullScreen", false))
			{
				String str[] = Client.getConfig().getProp("fullScreenResolution", "800x600").split("x");
				int w = Integer.parseInt(str[0]);
				int h = Integer.parseInt(str[1]);

				//String newDM = Client.getConfig().getProp("fullScreenResolution", "800x600");
				if (Display.isFullscreen() && windowWidth == w && windowHeight == h)
					return;
				//if (newDM.equals(currentDM))
				//	return;

				// Look for relevant display mode
				DisplayMode displayMode = null;
				DisplayMode[] modes = Display.getAvailableDisplayModes();
				for (int i = 0; i < modes.length; i++)
				{
					if (modes[i].getWidth() == w && modes[i].getHeight() == h && modes[i].isFullscreenCapable() && modes[i].getBitsPerPixel() >= 32)
					{
						displayMode = modes[i];
					}
				}
				if (displayMode != null)
				{
					DisplayMode current = Display.getDisplayMode();
					try
					{
						Display.setDisplayMode(displayMode);
						Display.setFullscreen(true);
					}
					catch (LWJGLException e)
					{
						windowWidth = 800;
						windowHeight = 600;
						current = new DisplayMode(windowWidth, windowHeight);
						ChunkStoriesLoggerImplementation.getInstance().warning("Couldnt set display to " + displayMode + "reverting to default resolution");
						Client.getConfig().setString("fullScreenResolution", current.getWidth() + "x" + current.getHeight());
						Client.getConfig().save();
						Display.setFullscreen(false);
						Display.setDisplayMode(current);
						if (currentScene != null && currentScene instanceof OverlayableScene)
						{
							OverlayableScene scene = ((OverlayableScene) currentScene);
							scene.changeOverlay(new MessageBoxOverlay(scene, scene.currentOverlay, "This resolution failed to be set, try another one !"));
						}
					}
				}
				forceResizeFlag = true;
			}
			else
			{
				if (Display.isFullscreen())
				{
					Display.setFullscreen(false);
					Display.setLocation(0, 0);
					Display.setDisplayMode(Display.getDesktopDisplayMode());
					forceResizeFlag = true;
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public void tick()
	{
		framesSinceLS++;
		if (lastTimeMS + 1000 < System.currentTimeMillis())
		{
			lastFPS = framesSinceLS;
			lastTimeMS = System.currentTimeMillis();
			framesSinceLS = 0;
		}
	}

	public void setTargetFPS(int targetFPS)
	{
		this.targetFPS = targetFPS;
	}

	public int getFPS()
	{
		return lastFPS;
	}

	public void changeScene(Scene scene)
	{
		Mouse.setGrabbed(false);
		if (currentScene != null)
			currentScene.destroy();
		currentScene = scene;
	}

	public void close()
	{
		closeRequest = true;
	}

	public Scene getCurrentScene()
	{
		return currentScene;
	}

	public void handleSpecialKey(int k)
	{
		if (k == 87 /* F11 */)
		{
			Client.getConfig().setString("fullScreen", !Client.getConfig().getBoolean("fullScreen", false) + "");
			String fsReso = Display.getDesktopDisplayMode().getWidth() + "x" + Display.getDesktopDisplayMode().getHeight();
			Client.getConfig().setString("fullScreenResolution", fsReso);
			switchResolution();
		}
	}

	public static GameWindowOpenGL getInstance()
	{
		return instance;
	}

	public ALSoundManager getSoundEngine()
	{
		return soundManager;
	}

	public RenderingContext getRenderingContext()
	{
		return renderingContext;
	}

	public boolean isMainGLWindow()
	{
		return getInstance().isInstanceMainGLWindow();
	}

	public boolean isInstanceMainGLWindow()
	{
		return Thread.currentThread().getId() == mainGLThreadId;
	}

	public int getWidth()
	{
		return windowWidth;
	}
	
	public int getHeight()
	{
		return windowHeight;
	}
	
	public void queueTask(Runnable runnable)
	{
		synchronized (mainThreadQueue)
		{
			mainThreadQueue.add(runnable);
		}
	}
}