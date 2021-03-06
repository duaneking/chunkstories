//
// This file is a part of the Chunk Stories API codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.engine.gui.elements;

import io.xol.chunkstories.api.gui.ClickableGuiElement;
import io.xol.chunkstories.api.gui.FocusableGuiElement;
import io.xol.chunkstories.api.gui.Layer;
import io.xol.chunkstories.api.input.Mouse;
import io.xol.chunkstories.api.input.Mouse.MouseButton;
import org.joml.Vector4f;
import io.xol.chunkstories.api.rendering.RenderingInterface;
import io.xol.chunkstories.client.Client;
import io.xol.engine.graphics.textures.TexturesHandler;
import io.xol.engine.graphics.util.CorneredBoxDrawer;



public class Button extends FocusableGuiElement implements ClickableGuiElement
{
	public String text = "";

	private Runnable action;
	
	public Button(Layer layer, int x, int y, int width, String t)
	{
		this(layer, x, y, width, t, null);
	}
	
	protected int scale() {
		return layer.getGuiScale();
	}
	
	public Button(Layer layer, int x, int y, int width, String t, Runnable r)
	{
		super(layer);
		xPosition = x;
		yPosition = y;
		text = t;
		this.width = width;
		this.height = 32;
		
		this.action = r;
	}

	public float getWidth()
	{
		String localizedText = Client.getInstance().getContent().localization().localize(text);
		int width = Client.getInstance().getContent().fonts().defaultFont().getWidth(localizedText) * scale();
		
		//Can have a predefined with
		if(this.width > width)
			return this.width + 4 * scale();
		
		return width + 4 * scale();
	}
	
	public float getHeight() {
		return height;
	}

	public boolean isMouseOver(Mouse mouse)
	{
		return (mouse.getCursorX() >= xPosition - getWidth() / 2 - 4 && mouse.getCursorX() < xPosition + getWidth() / 2 - 4 && mouse.getCursorY() >= yPosition - height / 2 - 4 && mouse.getCursorY() <= yPosition + height / 2 + 4);
	}

	public void render(RenderingInterface renderer) {
		String localizedText = Client.getInstance().getContent().localization().localize(text);
		float textWidth = Client.getInstance().getContent().fonts().defaultFont().getWidth(localizedText) * scale();
		if (width < 0)
		{
			width = textWidth;
		}
		float textDekal = -textWidth / 2f;
		if (isFocused() || isMouseOver())
		{
			TexturesHandler.getTexture("./textures/gui/scalableButtonOver.png").setLinearFiltering(false);
			CorneredBoxDrawer.drawCorneredBoxTiled(xPosition, yPosition, width + 8, getHeight() + 16, 4, "./textures/gui/scalableButtonOver.png", 32, scale());
		}
		else
		{
			TexturesHandler.getTexture("./textures/gui/scalableButton.png").setLinearFiltering(false);
			CorneredBoxDrawer.drawCorneredBoxTiled(xPosition, yPosition, width + 8, getHeight() + 16, 4, "./textures/gui/scalableButton.png", 32, scale());
		}
		
		renderer.getFontRenderer().drawStringWithShadow(renderer.getFontRenderer().defaultFont(), xPosition + textDekal, yPosition - height / 2, localizedText, scale(), scale(), new Vector4f(1.0f));
	}

	@Override
	public boolean handleClick(MouseButton mouseButton) {
		if(!mouseButton.equals("mouse.left"))
			return false;
		
		if(this.action != null)
			this.action.run();
		
		return true;
	}

	public void setAction(Runnable runnable) {
		this.action = runnable;
	}
}
