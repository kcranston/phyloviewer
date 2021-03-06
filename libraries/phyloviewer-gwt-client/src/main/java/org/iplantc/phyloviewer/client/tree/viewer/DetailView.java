/**
 * Copyright (c) 2009, iPlant Collaborative, Texas Advanced Computing Center This software is licensed
 * under the CC-GNU GPL version 2.0 or later. License: http://creativecommons.org/licenses/GPL/2.0/
 */

package org.iplantc.phyloviewer.client.tree.viewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.iplantc.core.broadcaster.shared.BroadcastCommand;
import org.iplantc.core.broadcaster.shared.Broadcaster;
import org.iplantc.phyloviewer.client.events.BranchClickEvent;
import org.iplantc.phyloviewer.client.events.BranchClickHandler;
import org.iplantc.phyloviewer.client.events.InteractionMode;
import org.iplantc.phyloviewer.client.events.LabelClickEvent;
import org.iplantc.phyloviewer.client.events.LabelClickHandler;
import org.iplantc.phyloviewer.client.events.LeafClickEvent;
import org.iplantc.phyloviewer.client.events.LeafClickHandler;
import org.iplantc.phyloviewer.client.events.NavigationMode;
import org.iplantc.phyloviewer.client.events.NodeClickEvent;
import org.iplantc.phyloviewer.client.events.NodeClickHandler;
import org.iplantc.phyloviewer.client.events.SelectionMode;
import org.iplantc.phyloviewer.client.tree.viewer.canvas.Canvas;
import org.iplantc.phyloviewer.client.tree.viewer.render.canvas.CanvasGraphics;
import org.iplantc.phyloviewer.shared.layout.ILayoutData;
import org.iplantc.phyloviewer.shared.math.Box2D;
import org.iplantc.phyloviewer.shared.math.Matrix33;
import org.iplantc.phyloviewer.shared.math.Vector2;
import org.iplantc.phyloviewer.shared.model.IDocument;
import org.iplantc.phyloviewer.shared.model.INode;
import org.iplantc.phyloviewer.shared.render.Camera;
import org.iplantc.phyloviewer.shared.render.CameraCladogram;
import org.iplantc.phyloviewer.shared.render.Defaults;
import org.iplantc.phyloviewer.shared.render.IGraphics;
import org.iplantc.phyloviewer.shared.render.RenderPreferences;
import org.iplantc.phyloviewer.shared.render.RenderTree;
import org.iplantc.phyloviewer.shared.render.RenderTreeCladogram;
import org.iplantc.phyloviewer.shared.render.style.GlyphStyle;
import org.iplantc.phyloviewer.shared.render.style.IStyle;
import org.iplantc.phyloviewer.shared.render.style.LabelStyle;
import org.iplantc.phyloviewer.shared.render.style.Style;
import org.iplantc.phyloviewer.shared.scene.Drawable;
import org.iplantc.phyloviewer.shared.scene.DrawableContainer;
import org.iplantc.phyloviewer.shared.scene.Text;
import org.iplantc.phyloviewer.shared.scene.intersect.IntersectTree;
import org.iplantc.phyloviewer.shared.scene.intersect.IntersectTreeBox;
import org.iplantc.phyloviewer.shared.scene.intersect.IntersectTree.Hit;

import com.google.gwt.core.client.Duration;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.HandlesAllKeyEvents;
import com.google.gwt.event.dom.client.HandlesAllMouseEvents;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.HandlerRegistration;

/**
 * The main tree view class.
 */
public class DetailView extends AnimatedView implements Broadcaster
{
	private int renderCount;
	private double[] renderTime = new double[60];
	private boolean drawRenderStats = false;

	private Canvas canvas;
	private CanvasGraphics graphics = null;
	private RenderTree renderer = new RenderTreeCladogram();

	private Map<EventHandler,List<HandlerRegistration>> handlerRegistrations = new HashMap<EventHandler,List<HandlerRegistration>>();
	
	private InteractionMode currentInteractionMode;

	private BroadcastCommand broadcastCommand;
	private Hit lastHit;

	private int eventMask = 0;

	private Set<Drawable> overlays = new HashSet<Drawable>();
	private CanvasGraphics overlayGraphics;
	private IStyle overlayStyle = new Style("overlay", null,
			new LabelStyle("#FF0000"), 
			new GlyphStyle(Defaults.OVERVIEW_FILL_COLOR, Defaults.OVERVIEW_OUTLINE_COLOR, 1.0),
			null);

	public enum DrawableType
	{
		Point, Line, Polygon, Text
	}

	/**
	 * Create a view with the given width and height.
	 * 
	 * @param width
	 * @param height
	 */
	public DetailView(int width, int height)
	{
		this.setStylePrimaryName("detailView");

		this.setCamera(new CameraCladogram());

		this.canvas = new Canvas();
		graphics = new CanvasGraphics(canvas);
		overlayGraphics = new CanvasGraphics(canvas);
		this.resize(width, height);
		this.add(canvas);

		this.addMouseMoveHandler(new MouseMoveHandler()
		{

			@Override
			public void onMouseMove(MouseMoveEvent arg0)
			{
				IntersectTree intersector = createIntersector(arg0.getX(), arg0.getY());
				intersector.intersect();

				Hit hit = intersector.getClosestHit();

				int x = arg0.getClientX();
				int y = arg0.getClientY();

				if(lastHit != null && hit != null)
				{
					// Check the drawables and make sure they are the same.
					if(lastHit.getDrawable() != hit.getDrawable())
					{
						handleMouseOut(lastHit, x, y);
						handleMouseOver(hit, x, y);
					}
				}
				else if(lastHit == null && hit != null)
				{
					handleMouseOver(hit, x, y);
				}
				else if(lastHit != null && hit == null)
				{
					handleMouseOut(lastHit, x, y);
				}

				lastHit = hit;
			}
		});

		this.addMouseDownHandler(new MouseDownHandler()
		{

			@Override
			public void onMouseDown(MouseDownEvent arg0)
			{
				if(arg0.getNativeButton() == 1)
				{
					Hit hit = lastHit;
					int x = arg0.getClientX();
					int y = arg0.getClientY();

					handleMouseClick(hit, x, y);
				}
			}

		});
	}

	@Override
	public void render()
	{
		try
		{
			if(this.isReady())
			{
				Duration duration = new Duration();
				Matrix33 viewMatrix = new Matrix33();

				Camera camera = getCamera();
				if(camera != null)
				{
					viewMatrix = camera.getMatrix(getWidth(), getHeight());
				}

				renderer.renderTree(graphics, viewMatrix);

				overlayGraphics.clearDrawnTextExtents();
				
				if(drawRenderStats)
				{
					renderStats(duration.elapsedMillis());
				}
				
				for (Drawable drawable : overlays)
				{
					drawable.draw(overlayGraphics, overlayStyle);
				}
			}
		}
		catch(Exception e)
		{
			Logger.getLogger("").log(Level.WARNING,
					"An exception was caught in DetailView.render: " + e.getMessage());
		}
	}
	
	@Override
	public void renderTo(IGraphics g)
	{
		if(this.isReady())
		{
			Matrix33 viewMatrix = new Matrix33();

			Camera camera = getCamera();
			if(camera != null)
			{
				viewMatrix = camera.getMatrix(g.getWidth(), g.getHeight());
			}

			renderer.renderTree(g, viewMatrix);			
		}
	}

	@Override
	public void resize(int width, int height)
	{
		graphics.setSize(width, height);
		overlayGraphics.setSize(width, height);
		canvas.setWidth(width);
		canvas.setHeight(height);
	}
	
	protected int getHeight()
	{
		return canvas.getHeight();
	}

	protected int getWidth()
	{
		return canvas.getWidth();
	}

	protected RenderTree getRenderer()
	{
		return renderer;
	}

	public void setRenderer(RenderTree renderer)
	{
		this.renderer = renderer;
	}

	@Override
	public void setDocument(IDocument document)
	{
		super.setDocument(document);
		this.getCamera().reset();

		if(renderer != null)
		{
			renderer.setDocument(document);
		}
	}

	@Override
	protected boolean isReady()
	{
		boolean documentReady = this.getDocument() != null && this.getDocument().isReady();
		boolean ready = documentReady && graphics != null && getCamera() != null;

		return ready;
	}

	private void renderStats(double time)
	{
		renderCount++;
		int index = renderCount % 60;
		renderTime[index] = time;

		String text = renderCount + " frames, last: " + Math.round(1.0 / time * 1000) + " FPS";

		if(renderCount >= 60)
		{
			double totalTime = 0;

			for(double t : renderTime)
			{
				totalTime += t;
			}
			double fps = (60.0 / totalTime) * 1000;

			text += " average: " + Math.round(fps) + " FPS";
		}

		Text textDrawable = new Text(text, new Vector2(0, overlayGraphics.getHeight()), new Vector2(5, -5));
		textDrawable.draw(overlayGraphics, overlayStyle);
	}

	@Override
	public String exportImageURL()
	{
		return canvas.toDataURL();
	}

	@Override
	public void setRenderPreferences(RenderPreferences rp)
	{
		super.setRenderPreferences(rp);
		renderer.setRenderPreferences(rp);
	}

	/**
	 * @return the node at the given location, in this View's screen coordinate system
	 */
	public INode getNodeAt(int x, int y)
	{
		IntersectTree intersector = createIntersector(x, y);
		intersector.intersect();

		Hit hit = intersector.getClosestHit();
		return hit != null ? hit.getNode() : null;
	}

	/**
	 * Create an intersector
	 * 
	 * @param x position in screen coordinates
	 * @param y position in screen coordinates
	 * @return An object to perform intersections.
	 */
	private IntersectTree createIntersector(int x, int y)
	{
		Vector2 position = getPositionInLayoutSpace(new Vector2(x, y));

		// Calculate the maximum size of a pixel side
		Vector2 v0 = getPositionInLayoutSpace(new Vector2(0, 0));
		Vector2 v1 = getPositionInLayoutSpace(new Vector2(1, 1));
		Vector2 distanceInObjectSpace = v1.subtract(v0);
		double distance = Math.max(distanceInObjectSpace.getX(), distanceInObjectSpace.getY());

		DrawableContainer container = renderer != null ? renderer.getDrawableContainer() : null;

		IntersectTree intersector = new IntersectTree(getDocument(), container, position, distance);
		return intersector;
	}

	/**
	 * Find nodes within a given rectangle
	 * @param screenBox the rectangle, in screen coordinates
	 * @return the set of nodes in the rectangle
	 */
	public Set<INode> getNodesIn(Box2D screenBox)
	{
		Set<INode> nodes = Collections.emptySet();

		if(getTree() != null)
		{
			Box2D range = getBoxInLayoutSpace(screenBox);
			INode root = getTree().getRootNode();
			ILayoutData layout = getLayout();

			nodes = IntersectTreeBox.intersect(root, layout, range);
		}

		return nodes;
	}

	/**
	 * Transforms a screen location to the tree layout coordinate system
	 * @param position the position
	 * @return the corresponding position in layout space
	 */
	public Vector2 getPositionInLayoutSpace(Vector2 position)
	{
		if(graphics != null)
		{
			Matrix33 IM = graphics.getScreenToObjectMatrix();
			return IM.transform(position);
		}

		return position;
	}

	/**
	 * Transforms a screen rectangle to the tree layout coordinate system
	 * @param box the rectangle
	 * @return the corresponding rectangle in tree layout space
	 */
	public Box2D getBoxInLayoutSpace(Box2D box)
	{
		Vector2 min = getPositionInLayoutSpace(box.getMin());
		Vector2 max = getPositionInLayoutSpace(box.getMax());
		return new Box2D(min, max);
	}
	
	/**
	 * Switch the user interface interaction mode
	 * @see NavigationMode
	 * @see SelectionMode
	 */
	public void setInteractionMode(InteractionMode mode)
	{
		if (currentInteractionMode != null)
		{
			unregister(currentInteractionMode.getMouseHandler());
			unregister(currentInteractionMode.getKeyHandler());
			removeStyleName(currentInteractionMode.getStyleName());
		}
		
		this.addMouseHandler(mode.getMouseHandler());
		this.addKeyboardHandler(mode.getKeyHandler());
		addStyleName(mode.getStyleName());
		this.currentInteractionMode = mode;
	}

	private void addMouseHandler(HandlesAllMouseEvents handler)
	{
		List<HandlerRegistration> registrations = handlerRegistrations.get(handler);
		if(registrations == null)
		{
			registrations = new ArrayList<HandlerRegistration>();
			handlerRegistrations.put(handler, registrations);
		}

		// add this handler for all supported events
		registrations.add(this.addMouseDownHandler(handler));
		registrations.add(this.addMouseUpHandler(handler));
		registrations.add(this.addMouseOutHandler(handler));
		registrations.add(this.addMouseOverHandler(handler));
		registrations.add(this.addMouseMoveHandler(handler));
		registrations.add(this.addMouseWheelHandler(handler));

		if(handler instanceof ClickHandler)
		{
			registrations.add(this.addClickHandler((ClickHandler)handler));
		}

		if(handler instanceof DoubleClickHandler)
		{
			registrations.add(this.addDoubleClickHandler((DoubleClickHandler)handler));
		}
	}
	
	private void addKeyboardHandler(HandlesAllKeyEvents handler)
	{
		List<HandlerRegistration> registrations = handlerRegistrations.get(handler);
		if(registrations == null)
		{
			registrations = new ArrayList<HandlerRegistration>();
			handlerRegistrations.put(handler, registrations);
		}
		
		registrations.add(this.addKeyDownHandler(handler));
		registrations.add(this.addKeyPressHandler(handler));
		registrations.add(this.addKeyUpHandler(handler));
	}

	/**
	 * Removes all handler registrations for the given handler
	 */
	private void unregister(EventHandler handler)
	{
		List<HandlerRegistration> registrations = handlerRegistrations.get(handler);
		if(registrations != null)
		{
			for(HandlerRegistration registration : registrations)
			{
				registration.removeHandler();
			}
		}

		registrations.clear();
	}

	@Override
	protected void initEventListeners()
	{
		super.initEventListeners();

		EventBus eventBus = getEventBus();
		if(eventBus != null)
		{
			eventBus.addHandler(NodeClickEvent.TYPE, new NodeClickHandler()
			{

				@Override
				public void onNodeClick(NodeClickEvent event)
				{
					broadcastEvent("node_clicked", event.getNodeId(), event.getClientX(),
							event.getClientY(), event.getMetaDataString());
				}

			});

			eventBus.addHandler(LeafClickEvent.TYPE, new LeafClickHandler()
			{

				@Override
				public void onLeafClick(LeafClickEvent event)
				{
					broadcastEvent("leaf_clicked", event.getNodeId(), event.getClientX(),
							event.getClientY(), event.getMetaDataString());
				}

			});

			eventBus.addHandler(BranchClickEvent.TYPE, new BranchClickHandler()
			{

				@Override
				public void onBranchClick(BranchClickEvent event)
				{
					broadcastEvent("branch_clicked", event.getNodeId(), event.getClientX(),
							event.getClientY(), event.getMetaDataString());
				}

			});

			eventBus.addHandler(LabelClickEvent.TYPE, new LabelClickHandler()
			{

				@Override
				public void onLabelClick(LabelClickEvent event)
				{
					broadcastEvent("label_clicked", event.getNodeId(), event.getClientX(),
							event.getClientY(), event.getMetaDataString());
				}

			});
		}
	}

	private void broadcastEvent(String type, int id, int clientX, int clientY, String metaDataString)
	{
		if(broadcastCommand != null)
		{
			String metadata = metaDataString != null ? metaDataString: "";
			
			String json = "{\"event\":\"" + type + "\",\"id\":\"" + id + "\",\"mouse\":{\"x\":"
					+ clientX + ",\"y\":" + clientY + "}" + ",\"metadata\":" + metadata + "}";
			broadcastCommand.broadcast(json);
		}
	}

	@Override
	public void setBroadcastCommand(BroadcastCommand cmdBroadcast)
	{
		this.broadcastCommand = cmdBroadcast;
	}

	/**
	 * Clear all highlighted nodes.
	 */
	public void clearHighlights()
	{
		RenderTree renderer = this.getRenderer();
		if(renderer != null)
		{
			RenderPreferences prefs = renderer.getRenderPreferences();
			if(prefs != null)
			{
				prefs.clearAllHighlights();
				this.requestRender();
			}
		}
	}

	/**
	 * Highlight given node
	 * 
	 * @param node id
	 */
	public void highlightNode(Integer id)
	{
		RenderTree renderer = this.getRenderer();
		if(renderer != null)
		{
			RenderPreferences prefs = renderer.getRenderPreferences();
			if(prefs != null)
			{
				prefs.highlightNode(id);
				this.requestRender();
			}
		}
	}

	/**
	 * Highlight node and subtree for given id.
	 * 
	 * @param node id
	 */
	public void highlightSubtree(Integer id)
	{
		RenderTree renderer = this.getRenderer();
		if(renderer != null)
		{
			RenderPreferences prefs = renderer.getRenderPreferences();
			if(prefs != null)
			{
				prefs.highlightSubtree(id);
				this.requestRender();
			}
		}
	}

	/**
	 * Highlight branch to given node id.
	 * 
	 * @param node id
	 */
	public void highlightBranch(Integer id)
	{
		RenderTree renderer = this.getRenderer();
		if(renderer != null)
		{
			RenderPreferences prefs = renderer.getRenderPreferences();
			if(prefs != null)
			{
				prefs.highlightBranch(id);
				this.requestRender();
			}
		}
	}

	/**
	 * @return true if this DetailView should display elapsed rendering time statistics
	 */
	public boolean isDrawRenderStats()
	{
		return drawRenderStats;
	}

	/**
	 * Set whether this DetailView should display elapsed rendering time statistics
	 */
	public void setDrawRenderStats(boolean drawRenderStats)
	{
		this.drawRenderStats = drawRenderStats;
	}

	private void handleMouseClick(Hit hit, int x, int y)
	{
		if(hit != null && hit.getDrawable() != null && isEventTypeAllowed(hit))
		{
			INode node = hit.getNode();
			Drawable.Context context = hit.getDrawable().getContext();
			if(node != null)
			{
				String metaDataString = node.getMetaDataString();
				int nodeId = node.getId();

				if(Drawable.Context.CONTEXT_NODE == context)
				{

					if(node.isLeaf())
					{
						dispatch(new LeafClickEvent(nodeId, x, y, metaDataString));
					}
					else
					{
						dispatch(new NodeClickEvent(nodeId, x, y, metaDataString));
					}
				}

				else if(Drawable.Context.CONTEXT_BRANCH == context)
				{
					dispatch(new BranchClickEvent(nodeId, x, y, metaDataString));
				}

				else if(Drawable.Context.CONTEXT_LABEL == context)
				{
					dispatch(new LabelClickEvent(nodeId, x, y, metaDataString));
				}
			}
		}
	}

	private void handleMouseOver(Hit hit, int x, int y)
	{
		if(hit != null && hit.getDrawable() != null && isEventTypeAllowed(hit))
		{
			INode node = hit.getNode();
			Drawable.Context context = hit.getDrawable().getContext();

			if(node != null)
			{
				String metaDataString = node.getMetaDataString();
				int nodeId = node.getId();

				if(Drawable.Context.CONTEXT_NODE == context)
				{
					if(node.isLeaf())
					{
						broadcastEvent("leaf_mouse_over", nodeId, x, y, metaDataString);
					}
					else
					{
						broadcastEvent("node_mouse_over", nodeId, x, y, metaDataString);
					}
				}

				else if(Drawable.Context.CONTEXT_BRANCH == context)
				{
					broadcastEvent("branch_mouse_over", nodeId, x, y, metaDataString);
				}

				else if(Drawable.Context.CONTEXT_LABEL == context)
				{
					broadcastEvent("label_mouse_over", nodeId, x, y, metaDataString);
				}
			}
		}
	}

	private void handleMouseOut(Hit hit, int x, int y)
	{
		if(hit != null && hit.getDrawable() != null && isEventTypeAllowed(hit))
		{
			INode node = hit.getNode();
			Drawable.Context context = hit.getDrawable().getContext();

			if(node != null)
			{
				String metaDataString = node.getMetaDataString();
				int nodeId = node.getId();

				if(Drawable.Context.CONTEXT_NODE == context)
				{
					if(node.isLeaf())
					{
						broadcastEvent("leaf_mouse_out", nodeId, x, y, metaDataString);
					}
					else
					{
						broadcastEvent("node_mouse_out", nodeId, x, y, metaDataString);
					}
				}

				else if(Drawable.Context.CONTEXT_BRANCH == context)
				{
					broadcastEvent("branch_mouse_out", nodeId, x, y, metaDataString);
				}

				else if(Drawable.Context.CONTEXT_LABEL == context)
				{
					broadcastEvent("label_mouse_out", nodeId, x, y, metaDataString);
				}
			}
		}
	}

	private boolean isEventTypeAllowed(Hit hit)
	{
		if(hit != null && hit.getDrawable() != null)
		{
			return (eventMask & hit.getDrawable().getDrawableType()) == 0;
		}

		return false;
	}

	/**
	 * Filter out events that correspond to the drawable type
	 * 
	 * @param type
	 */
	public void addEventFilter(DrawableType type)
	{
		switch (type)
		{
			case Point:
				eventMask = eventMask | Drawable.TYPE_POINT;
				break;
			case Line:
				eventMask = eventMask | Drawable.TYPE_LINE;
				break;
			case Polygon:
				eventMask = eventMask | Drawable.TYPE_POLYGON;
				break;
			case Text:
				eventMask = eventMask | Drawable.TYPE_TEXT;
				break;
		}
	}

	/**
	 * Allow all events.
	 */
	public void clearEventFilters()
	{
		this.eventMask = 0;
	}
	
	/**
	 * Adds a drawable to the overlay graphics
	 * @return true if it was added
	 */
	public boolean addOverlay(Drawable d)
	{
		return overlays.add(d);
	}
	
	/**
	 * Removes a drawable from the overlay graphics
	 * @return true if it was removed
	 */
	public boolean removeOverlay(Drawable d)
	{
		return overlays.remove(d);
	}
}
