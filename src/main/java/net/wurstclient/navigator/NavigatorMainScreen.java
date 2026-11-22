/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.navigator;

import com.mojang.blaze3d.platform.InputConstants;

import java.awt.Rectangle;
import java.util.ArrayList;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.wurstclient.Category;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui.ClickGui;
import net.wurstclient.hacks.TooManyHaxHack;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.RenderUtils;

public final class NavigatorMainScreen extends NavigatorScreen
{
	private static final ArrayList<Feature> navigatorDisplayList =
		new ArrayList<>();
	private static final Identifier LOGO_TEXTURE =
		Identifier.fromNamespaceAndPath("wurst", "wurst_128.png");
	
	private static final int ROW_H = 30;
	private static final int SIDE_W = 96;
	private static final int TAB_TOP = 46;
	private static final int ACCENT = 0xFFA855F7;
	
	private final String[] tabNames;
	private int selectedTab = 0;
	private int hoveredTab = -1;
	private int tabH = 17;
	
	private int px1;
	private int py1;
	private int px2;
	private int py2;
	private int listX1;
	private int listX2;
	private int listTop;
	private int listBottom;
	
	private EditBox searchBar;
	private String lastSearchText = "";
	private String tooltip;
	private int hoveredFeature = -1;
	private int selectedFeature = 0;
	private boolean mouseMoved = false;
	private boolean hoveringArrow;
	private int clickTimer = -1;
	private boolean expanding = false;
	private Feature expandingFeature;
	
	public NavigatorMainScreen()
	{
		hasBackground = false;
		nonScrollableArea = 0;
		
		Category[] cats = Category.values();
		tabNames = new String[cats.length + 2];
		tabNames[0] = "All";
		for(int i = 0; i < cats.length; i++)
			tabNames[i + 1] = cats[i].getName();
		tabNames[cats.length + 1] = "Misc";
		
		rebuildList("");
	}
	
	private void rebuildList(String query)
	{
		Navigator navigator = WurstClient.INSTANCE.getNavigator();
		
		if(query.isEmpty())
			navigator.copyNavigatorList(navigatorDisplayList);
		else
			navigator.getSearchResults(navigatorDisplayList,
				query.toLowerCase().trim());
		
		if(selectedTab > 0)
		{
			Category[] cats = Category.values();
			if(selectedTab <= cats.length)
			{
				Category cat = cats[selectedTab - 1];
				navigatorDisplayList.removeIf(f -> f.getCategory() != cat);
			}else
				navigatorDisplayList.removeIf(f -> f.getCategory() != null);
		}
		
		selectedFeature = 0;
		scroll = 0;
		updateContentHeight();
	}
	
	private void updateContentHeight()
	{
		int visible = listBottom - listTop;
		int total = navigatorDisplayList.size() * ROW_H;
		setContentHeight(height - 120 - (visible - total));
	}
	
	@Override
	protected int getScrollbarX()
	{
		return px2 - 16;
	}
	
	@Override
	protected int getScrollbarTop()
	{
		return listTop;
	}
	
	@Override
	protected int getScrollbarBottom()
	{
		return listBottom;
	}
	
	@Override
	protected void onResize()
	{
		ClickGui gui = WurstClient.INSTANCE.getGui();
		int txtColor = gui.getTxtColor();
		
		int pw = Math.min(width - 30, 460);
		int ph = Math.min(height - 30, 300);
		px1 = middleX - pw / 2;
		px2 = px1 + pw;
		py1 = (height - ph) / 2;
		py2 = py1 + ph;
		listX1 = px1 + SIDE_W + 8;
		listX2 = px2 - 22;
		listTop = py1 + 38;
		listBottom = py2 - 8;
		tabH = Math.max(11, Math.min(17, (ph - TAB_TOP - 8) / tabNames.length));
		
		Font tr = WurstClient.MC.font;
		searchBar = new EditBox(tr, listX1 + 10, py1 + 14, listX2 - listX1 - 20,
			12, Component.literal(""));
		searchBar.setTextColor(txtColor);
		searchBar.setBordered(false);
		searchBar.setMaxLength(128);
		
		addWidget(searchBar);
		setFocused(searchBar);
		searchBar.setFocused(true);
		
		updateContentHeight();
	}
	
	@Override
	protected void onKeyPress(KeyEvent context)
	{
		int keyCode = context.key();
		boolean hasShiftDown = context.hasShiftDown();
		
		if(keyCode == InputConstants.KEY_RETURN)
			leftClick(selectedFeature);
		
		if(keyCode == InputConstants.KEY_SPACE)
			expand(selectedFeature);
		
		if(keyCode == InputConstants.KEY_RIGHT
			|| keyCode == InputConstants.KEY_TAB && !hasShiftDown)
		{
			if(selectedFeature + 1 < navigatorDisplayList.size())
				selectedFeature++;
			
		}else if(keyCode == InputConstants.KEY_LEFT
			|| keyCode == InputConstants.KEY_TAB && hasShiftDown)
		{
			if(selectedFeature - 1 > -1)
				selectedFeature--;
			
		}else if(keyCode == InputConstants.KEY_DOWN)
		{
			if(selectedFeature + 1 < navigatorDisplayList.size())
				selectedFeature += 1;
			
		}else if(keyCode == InputConstants.KEY_UP)
			if(selectedFeature - 1 > -1)
				selectedFeature -= 1;
	}
	
	private int getTabAt(double mouseX, double mouseY)
	{
		if(mouseX < px1 + 6 || mouseX > px1 + SIDE_W - 6)
			return -1;
		if(mouseY < py1 + TAB_TOP)
			return -1;
		
		int index = (int)(mouseY - (py1 + TAB_TOP)) / tabH;
		if(index >= tabNames.length)
			return -1;
		
		return index;
	}
	
	@Override
	protected void onMouseClick(MouseButtonEvent context)
	{
		int button = context.button();
		boolean hasShiftDown = context.hasShiftDown();
		
		if(clickTimer != -1)
			return;
		
		// back button
		if(button == InputConstants.MOUSE_BUTTON_4)
		{
			WurstClient.MC.gui.setScreen((Screen)null);
			return;
		}
		
		// category tabs
		int tab = getTabAt(context.x(), context.y());
		if(tab != -1 && button == InputConstants.MOUSE_BUTTON_LEFT)
		{
			selectedTab = tab;
			rebuildList(searchBar.getValue());
			return;
		}
		
		if(hoveredFeature == -1)
			return;
		
		// arrow click, shift click, wheel click
		if(button == InputConstants.MOUSE_BUTTON_LEFT
			&& (hasShiftDown || hoveringArrow)
			|| button == InputConstants.MOUSE_BUTTON_MIDDLE)
		{
			expand(hoveredFeature);
			return;
		}
		
		// left click
		if(button == InputConstants.MOUSE_BUTTON_LEFT)
			leftClick(hoveredFeature);
	}
	
	private void expand(int i)
	{
		if(i < 0 || i >= navigatorDisplayList.size())
			return;
		
		expandingFeature = navigatorDisplayList.get(i);
		expanding = true;
	}
	
	private void leftClick(int i)
	{
		if(i < 0 || i >= navigatorDisplayList.size())
			return;
		
		Feature feature = navigatorDisplayList.get(i);
		
		if(feature.getPrimaryAction().isEmpty())
		{
			expanding = true;
			expandingFeature = feature;
			return;
		}
		
		WurstClient wurst = WurstClient.INSTANCE;
		TooManyHaxHack tooManyHax = wurst.getHax().tooManyHaxHack;
		
		if(tooManyHax.isEnabled() && tooManyHax.isBlocked(feature))
		{
			ChatUtils.error(feature.getName() + " is blocked by TooManyHax.");
			return;
		}
		
		feature.doPrimaryAction();
		wurst.getNavigator().addPreference(feature.getName());
	}
	
	@Override
	protected void onUpdate()
	{
		String newText = searchBar.getValue();
		if(clickTimer == -1 && !newText.equals(lastSearchText))
		{
			lastSearchText = newText;
			rebuildList(newText);
		}
		
		if(expanding)
			if(clickTimer < 4)
				clickTimer++;
			else
				WurstClient.MC.gui.setScreen(
					new NavigatorFeatureScreen(expandingFeature, this));
		else if(!expanding && clickTimer > -1)
			clickTimer--;
		
		scrollbarLocked = clickTimer != -1;
	}
	
	@Override
	public void mouseMoved(double mouseX, double mouseY)
	{
		mouseMoved = true;
	}
	
	private static void fillRounded(GuiGraphicsExtractor extractor, int x1,
		int y1, int x2, int y2, int r, int color)
	{
		if(r <= 0 || x2 - x1 < 2 * r + 1 || y2 - y1 < 2 * r + 1)
		{
			extractor.fill(x1, y1, x2, y2, color);
			return;
		}
		
		for(int i = 0; i < r; i++)
		{
			double dy = r - i - 0.5;
			int inset = (int)Math.round(r - Math.sqrt(r * r - dy * dy));
			extractor.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
			extractor.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
		}
		extractor.fill(x1, y1 + r, x2, y2 - r, color);
	}
	
	private static String fitText(Font tr, String s, int maxWidth)
	{
		if(tr.width(s) <= maxWidth)
			return s;
		
		s = s.substring(0, Math.min(s.length(), Math.max(maxWidth / 4, 1)));
		while(!s.isEmpty() && tr.width(s + "...") > maxWidth)
			s = s.substring(0, s.length() - 1);
		
		return s + "...";
	}
	
	@Override
	protected void onRender(GuiGraphicsExtractor extractor, int mouseX,
		int mouseY, float partialTicks)
	{
		ClickGui gui = WurstClient.INSTANCE.getGui();
		int txtColor = gui.getTxtColor();
		float[] ac = gui.getAcColor();
		Font tr = minecraft.font;
		
		boolean clickTimerRunning = clickTimer != -1;
		tooltip = null;
		
		if(!clickTimerRunning)
		{
			// glow
			for(int k = 5; k >= 1; k--)
				fillRounded(extractor, px1 - k * 3, py1 - k * 3, px2 + k * 3,
					py2 + k * 3, 8 + k * 3, 0x14A855F7);
			
			// panel
			float op = (float)Math.max(0.78, gui.getOpacity());
			fillRounded(extractor, px1, py1, px2, py2, 8,
				RenderUtils.toIntColor(gui.getBgColor(), op));
			
			// border
			int borderColor = RenderUtils.toIntColor(ac, 0.6F);
			extractor.fill(px1 + 8, py1, px2 - 8, py1 + 1, borderColor);
			extractor.fill(px1 + 8, py2 - 1, px2 - 8, py2, borderColor);
			extractor.fill(px1, py1 + 8, px1 + 1, py2 - 8, borderColor);
			extractor.fill(px2 - 1, py1 + 8, px2, py2 - 8, borderColor);
			
			// divider
			extractor.fill(px1 + SIDE_W, py1 + 10, px1 + SIDE_W + 1, py2 - 10,
				RenderUtils.toIntColor(ac, 0.3F));
			
			// category tab backgrounds
			hoveredTab = getTabAt(mouseX, mouseY);
			for(int i = 0; i < tabNames.length; i++)
			{
				int ty1 = py1 + TAB_TOP + i * tabH;
				int ty2 = ty1 + tabH - 2;
				
				if(i == selectedTab)
					fillRounded(extractor, px1 + 6, ty1, px1 + SIDE_W - 6, ty2,
						4, RenderUtils.toIntColor(ac, 0.45F));
				else if(i == hoveredTab)
					fillRounded(extractor, px1 + 6, ty1, px1 + SIDE_W - 6, ty2,
						4, RenderUtils.toIntColor(ac, 0.15F));
			}
			
			// search box background
			fillRounded(extractor, listX1, py1 + 8, listX2, py1 + 28, 5,
				RenderUtils.toIntColor(ac, 0.14F));
			
			extractor.guiRenderState.up();
			
			// logo & version
			extractor.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, px1 + 8,
				py1 + 8, 0, 0, 80, 20, 80, 20);
			extractor.text(tr, "v" + WurstClient.VERSION, px1 + 12, py1 + 31,
				0xFFA9A3B8, false);
			
			// category tab names
			for(int i = 0; i < tabNames.length; i++)
			{
				int ty1 = py1 + TAB_TOP + i * tabH;
				int textY = ty1 + (tabH - 2 - tr.lineHeight) / 2 + 1;
				extractor.text(tr, tabNames[i], px1 + 14, textY,
					i == selectedTab ? 0xFFFFFFFF : txtColor, false);
			}
			
			// search bar
			if(searchBar.getValue().isEmpty())
				extractor.text(tr, "Search features...", listX1 + 10, py1 + 16,
					0xFF8A8A9A, false);
			searchBar.extractRenderState(extractor, mouseX, mouseY,
				partialTicks);
		}
		
		// feature list
		if(!clickTimerRunning)
			hoveredFeature = -1;
		
		if(clickTimerRunning)
			extractor.enableScissor(0, 0, width, height);
		else
			extractor.enableScissor(listX1 - 2, listTop, listX2 + 2,
				listBottom);
		
		boolean mouseInList = mouseX >= listX1 && mouseX < listX2
			&& mouseY >= listTop && mouseY < listBottom;
		int rowW = listX2 - listX1;
		
		for(int i = Math.max(-scroll / ROW_H - 1, 0); i < navigatorDisplayList
			.size(); i++)
		{
			int rowY = listTop + i * ROW_H + scroll;
			
			if(rowY + ROW_H < listTop)
				continue;
			if(rowY > listBottom)
				break;
			
			renderFeature(extractor, mouseX, mouseY, partialTicks, i, listX1,
				rowY, rowW, mouseInList);
		}
		
		extractor.disableScissor();
		
		// tooltip
		if(tooltip != null)
		{
			extractor.guiRenderState.up();
			
			String[] lines = tooltip.split("\n");
			
			int tw = 0;
			int th = lines.length * tr.lineHeight;
			for(String line : lines)
			{
				int lw = tr.width(line);
				if(lw > tw)
					tw = lw;
			}
			int sw = minecraft.gui.screen().width;
			int sh = minecraft.gui.screen().height;
			
			int xt1 = mouseX + tw + 11 <= sw ? mouseX + 8 : mouseX - tw - 8;
			int xt2 = xt1 + tw + 3;
			int yt1 = mouseY + th - 2 <= sh ? mouseY - 4 : mouseY - th - 4;
			int yt2 = yt1 + th + 2;
			
			// background
			int bgColor = RenderUtils.toIntColor(gui.getBgColor(),
				gui.getTooltipOpacity());
			extractor.fill(xt1, yt1, xt2, yt2, bgColor);
			
			// outline
			int acColor = RenderUtils.toIntColor(gui.getAcColor(), 0.5F);
			RenderUtils.drawBorder2D(extractor, xt1, yt1, xt2, yt2, acColor);
			
			// text
			extractor.guiRenderState.up();
			for(int i = 0; i < lines.length; i++)
				extractor.text(tr, lines[i], xt1 + 2,
					yt1 + 2 + i * tr.lineHeight, txtColor, false);
		}
	}
	
	private void renderFeature(GuiGraphicsExtractor extractor, int mouseX,
		int mouseY, float partialTicks, int i, int x, int y, int w,
		boolean mouseInList)
	{
		ClickGui gui = WurstClient.INSTANCE.getGui();
		boolean clickTimerRunning = clickTimer != -1;
		
		Feature feature = navigatorDisplayList.get(i);
		Rectangle area = new Rectangle(x, y, w, ROW_H - 4);
		
		// click animation
		if(clickTimerRunning)
		{
			if(feature != expandingFeature)
				return;
			
			float factor;
			if(expanding)
				if(clickTimer == 4)
					factor = 1F;
				else
					factor = (clickTimer + partialTicks) / 4F;
			else if(clickTimer == 0)
				factor = 0F;
			else
				factor = (clickTimer - partialTicks) / 4F;
			float antiFactor = 1 - factor;
			
			area.x = (int)(area.x * antiFactor + (middleX - 154) * factor);
			area.y = (int)(area.y * antiFactor + 60 * factor);
			area.width = (int)(area.width * antiFactor + 308 * factor);
			area.height =
				(int)(area.height * antiFactor + (height - 103) * factor);
			
			drawBackgroundBox(extractor, area.x, area.y, area.x + area.width,
				area.y + area.height);
			return;
		}
		
		boolean hovering = mouseInList && area.contains(mouseX, mouseY);
		if(hovering)
		{
			hoveredFeature = i;
			
			if(mouseMoved)
			{
				selectedFeature = i;
				mouseMoved = false;
			}
		}
		
		boolean renderAsHovered = hovering || selectedFeature == i;
		boolean enabled = feature.isEnabled();
		float[] ac = gui.getAcColor();
		
		// tooltip
		if(hovering)
			tooltip = feature.getWrappedDescription(200);
		
		// row background
		int rowColor;
		if(renderAsHovered)
			rowColor = RenderUtils.toIntColor(ac, 0.22F);
		else if(enabled)
			rowColor = RenderUtils.toIntColor(ac, 0.10F);
		else
			rowColor = 0x14FFFFFF;
		fillRounded(extractor, area.x, area.y, area.x + area.width,
			area.y + area.height, 4, rowColor);
		
		// enabled marker & ON pill
		int arrowX = area.x + area.width - 12;
		if(enabled)
		{
			extractor.fill(area.x, area.y + 5, area.x + 2,
				area.y + area.height - 5, ACCENT);
			fillRounded(extractor, arrowX - 32, area.y + 7, arrowX - 6,
				area.y + 19, 4, RenderUtils.toIntColor(ac, 0.45F));
		}
		
		// hovering the arrow
		if(hovering)
			hoveringArrow = mouseX >= area.x + area.width - 22;
		
		extractor.guiRenderState.up();
		
		Font tr = minecraft.font;
		
		// name
		int nameColor = enabled ? ACCENT : gui.getTxtColor();
		extractor.text(tr, feature.getName(), area.x + 8, area.y + 4,
			nameColor, false);
		
		// description
		String desc = feature.getDescription().replace("\n", " ")
			.replaceAll("\u00a7.", "");
		desc = fitText(tr, desc, area.width - 78);
		extractor.text(tr, desc, area.x + 8, area.y + 15, 0xFFA9A3B8, false);
		
		// ON text & arrow
		if(enabled)
			extractor.text(tr, "ON", arrowX - 32 + (26 - tr.width("ON")) / 2,
				area.y + 9, 0xFFFFFFFF, false);
		extractor.text(tr, ">", arrowX, area.y + 9,
			hovering && hoveringArrow ? 0xFFFFFFFF : 0xFF8A8A9A, false);
	}
	
	public void setExpanding(boolean expanding)
	{
		this.expanding = expanding;
	}
	
	@Override
	protected void onMouseDrag(double mouseX, double mouseY, int button,
		double double_3, double double_4)
	{
		
	}
	
	@Override
	protected void onMouseRelease(double x, double y, int button)
	{
		
	}
}
