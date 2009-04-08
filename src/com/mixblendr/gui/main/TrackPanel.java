/**
 *
 */
package com.mixblendr.gui.main;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import com.mixblendr.audio.AudioFile;
import com.mixblendr.audio.AudioListener;
import com.mixblendr.audio.AudioRegion;
import com.mixblendr.audio.AudioTrack;
import com.mixblendr.audio.AudioRegion.State;
import com.mixblendr.skin.ControlDelegate;
import com.mixblendr.skin.MPanel;
import com.mixblendr.util.Debug;
import com.mixblendr.util.Utils;

import static com.mixblendr.util.Debug.*;
import static com.mixblendr.gui.main.RegionDragTool.Type.*;

/**
 * A container control that manages a list of regions and displays them in a
 * row.
 * 
 * @author Florian Bomers
 */
public class TrackPanel extends JComponent implements GraphScale.Listener,
		AudioListener, DropTargetListener, MouseListener {

	private final static boolean DEBUG = false;
	private final static boolean DEBUG_DRAG = false;
	private final static boolean DEBUG_DRAG_MOVE = false;
	private final static boolean DEBUG_REPAINT = false;

	private static final Color BOTTOM_LINE_COLOR = Color.black;

	private AudioTrack track;

	private GraphScale scale;

	private Globals globals;

	private boolean needToRecalculateWidth;

	private int cachedWidth;

	private int fixedHeight = -1;

	// if enlarging this track by dragging, this track will keep that size as a
	// minimum
	private int minimumWidth = 0;

	private ControlDelegate waveBackground, waveBackgroundSel;

	@SuppressWarnings("unused")
	private DropTarget dropTarget;

	/** create a new empty track panel */
	public TrackPanel() {
		this(null, null, null);
	}

	/**
	 * create a new empty track panel
	 * 
	 * @param waveBackground a template control delegate which is used as
	 *            background for GraphRegions.
	 */
	public TrackPanel(AudioTrack track, Globals globals,
			ControlDelegate waveBackground) {
		super();
		setWaveBackground(waveBackground);
		setOpaque(false);
		setLayout(null);
		setGlobals(globals);
		// TODO: allow setting this from skin file
		setToolTipText("drag audio files here");
		// drop support
		dropTarget = new DropTarget(this, this);
		addMouseListener(this);
		init(track);
	}

	/**
	 * @param globals
	 */
	private void setGlobals(Globals globals) {
		if (globals != this.globals) {
			this.globals = globals;
			if (globals != null) {
				this.scale = globals.getScale();
			}
		}
	}

	/**
	 * @return the wave background template
	 */
	public ControlDelegate getWaveBackground() {
		return waveBackground;
	}

	/**
	 * @return the wave background template for selected graphs
	 */
	public ControlDelegate getWaveBackgroundSel() {
		return waveBackgroundSel;
	}

	/**
	 * Set the background template for the wave display. It will be cloned for
	 * each actual RegionGraph instance.
	 * 
	 * @param waveBackground the new wave background template
	 */
	public void setWaveBackground(ControlDelegate waveBackground) {
		this.waveBackground = waveBackground;
	}

	/**
	 * Set the background template for the wave display when selectred. It will
	 * be cloned for each actual RegionGraph instance.
	 * 
	 * @param waveBackgroundSel the new wave background template for selected
	 *            graph
	 */
	public void setWaveBackgroundSel(ControlDelegate waveBackgroundSel) {
		this.waveBackgroundSel = waveBackgroundSel;
	}

	/**
	 * initialize this panel with the given track and the scale. Set both to
	 * null to unregister this panel. This method will always invoke the
	 * update() method.
	 * <p>
	 * call this method with both parameters null to release resources and to
	 * unregister from listeners, etc.
	 */
	public void init(AudioTrack aTrack, Globals aGlobals) {
		setGlobals(aGlobals);
		init(aTrack);
	}

	/**
	 * initialize this panel with the given track and the scale. Set both to
	 * null to unregister this panel. This method will always invoke the
	 * update() method.
	 * <p>
	 * call this method with parameter null to release resources and to
	 * unregister from listeners, etc.
	 */
	public void init(AudioTrack aTrack) {
		if (aTrack != this.track) {
			if (globals != null && this.track == null) {
				globals.getState().getAudioEventDispatcher().addListener(this);
				if (scale != null) {
					scale.addListener(this);
				}
			} else if (globals != null && aTrack == null) {
				globals.getState().getAudioEventDispatcher().removeListener(
						this);
				if (scale != null) {
					scale.removeListener(this);
				}
			}
			this.track = aTrack;
		}
		needToRecalculateWidth = true;
		update();
	}

	@Override
	public void paint(Graphics g) {
		if (DEBUG_REPAINT) {
			Rectangle clip = g.getClipBounds();
			debug("TrackPanel: paint(x=" + clip.x + ", w=" + clip.width + ")");
		}
		// only paint the bottom line
		g.setColor(BOTTOM_LINE_COLOR);
		int h = getHeight();
		g.drawLine(0, h - 1, getWidth(), h - 1);
		paintChildren(g);
	}

	/** add this region in form of an AudioRegion object to this panel */
	private void addRegion(AudioRegion region) {
		addRegion(region, -1);
	}

	/**
	 * add this region in form of an AudioRegion object to this panel
	 * 
	 * @param index the insertion index
	 */
	private void addRegion(AudioRegion region, int index) {
		RegionGraph rg = new RegionGraph(region, globals);
		rg.setWaveBackground(getClonedBackground());
		rg.setWaveBackgroundSel(getClonedBackgroundSel());
		if (globals != null && globals.getGlobalKeyListener() != null) {
			rg.addKeyListener(globals.getGlobalKeyListener());
		}
		add(rg, index);
	}

	/**
	 * Set a fixed height of this panel.
	 * 
	 * @param fixedHeight the fixedHeight to set, or -1 to let this panel's
	 *            height be determined by the layout manager.
	 */
	public void setFixedHeight(int fixedHeight) {
		this.fixedHeight = fixedHeight;
		if (fixedHeight >= 0) {
			setBounds(getX(), getY(), getWidth(), fixedHeight);
		}
		doValidate();
	}

	/**
	 * @return the fixedHeight, or -1 if no fixed height is set
	 */
	public int getFixedHeight() {
		return fixedHeight;
	}

	/**
	 * Get the component index of the graph belonging to the specified region.
	 * Returns -1 if not found.
	 */
	public int getRegionGraphIndex(AudioRegion region) {
		int count = getComponentCount();
		for (int i = 0; i < count; i++) {
			Component c = getComponent(i);
			if ((c != null) && (c instanceof RegionGraph)
					&& (((RegionGraph) c).getRegion() == region)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Get the Graph instance belonging to the specified region. Returns null if
	 * not found.
	 */
	public RegionGraph getRegionGraph(AudioRegion region) {
		int i = getRegionGraphIndex(region);
		if (i >= 0) {
			return (RegionGraph) getComponent(i);
		}
		return null;
	}

	/**
	 * Fill list with all region graph instances.
	 */
	// $$fb: NOT: Also remove all non-Graph components from this panel.
	public void getRegionGraphs(List<RegionGraph> list) {
		list.clear();
		for (int i = getComponentCount() - 1; i >= 0; i--) {
			Component c = getComponent(i);
			if (c instanceof RegionGraph) {
				list.add((RegionGraph) c);
				// } else {
				// remove(c);
			}
		}
	}

	/**
	 * prevent re-instanciation of the temporary list used in update() by using
	 * a global array
	 */
	private List<AudioRegion> regionList = new ArrayList<AudioRegion>();
	private List<RegionGraph> graphList = new ArrayList<RegionGraph>();

	/** search for a graph in list which is associated with the region */
	private int findRegion(List<RegionGraph> list, AudioRegion region) {
		int c = list.size();
		for (int i = 0; i < c; i++) {
			RegionGraph rg = list.get(i);
			if (rg != null && rg.getRegion() == region) {
				return i;
			}
		}
		return -1;
	}

	/** this method should be called when the regions in the track change */
	public synchronized void update() {
		getRegionGraphs(graphList);
		needToRecalculateWidth = true;
		if (track == null || scale == null) {
			removeAll();
			return;
		}
		track.getPlaylist().getAudioRegions(regionList);
		// go through all regions and try to match them
		for (AudioRegion region : regionList) {
			int i = findRegion(graphList, region);
			if (i >= 0) {
				// found, remove from graph list
				graphList.set(i, null);
			} else {
				// not found, add it
				addRegion(region);
			}
		}
		// all the graphs left in the graph list are now obsolete
		for (RegionGraph graph : graphList) {
			removeGraphImpl(graph);
		}
		// now call audioDataChanged() on all graphs
		getRegionGraphs(graphList);
		for (RegionGraph graph : graphList) {
			graph.audioDataChanged();
		}
		needToRecalculateWidth = true;
		doValidate();
	}

	/** re-layout this component */
	private void doValidate() {
		revalidate();
	}

	/** get the audio track associated with this panel */
	public AudioTrack getTrack() {
		return track;
	}

	/**
	 * Set a new name for the track
	 * 
	 * @param name the new track name
	 */
	public void setTrackName(String name) {
		if (track != null) {
			track.setName(name);
		}
	}

	/**
	 * return a new instance of the background, or null, if no wave background
	 * is set
	 */
	private ControlDelegate getClonedBackground() {
		if (waveBackground != null) {
			return (ControlDelegate) waveBackground.clone();
		}
		return null;
	}

	/**
	 * return a new instance of the selected background, or null, if no wave
	 * selected background is set
	 */
	private ControlDelegate getClonedBackgroundSel() {
		if (waveBackgroundSel != null) {
			return (ControlDelegate) waveBackgroundSel.clone();
		}
		return null;
	}

	/**
	 * return a new instance of the selected background, or if that's null, the
	 * normal wave background
	 */
	private ControlDelegate getDragPanelBackground() {
		if (waveBackgroundSel != null) {
			return getClonedBackgroundSel();
		}
		return getClonedBackground();
	}

	/** remove the graph from this component and unregister its region property */
	private void removeGraphImpl(RegionGraph graph) {
		if (graph != null) {
			remove(graph);
			// remove references
			graph.init((AudioRegion) null);
			if (globals != null && globals.getGlobalKeyListener() != null) {
				graph.removeKeyListener(globals.getGlobalKeyListener());
			}
		}
	}

	/**
	 * remove the graph from this component and from the track, unregister its
	 * region property, and repaint
	 */
	public boolean removeGraph(RegionGraph graph) {
		boolean ret = false;
		if (graph != null && graph.getParent() == this) {
			Rectangle r = graph.getBounds();
			AudioRegion ar = graph.getRegion();
			if (ar != null && track != null) {
				ret = track.getPlaylist().removeObject(ar);
			}
			// remove references
			removeGraphImpl(graph);
			if (ret) {
				repaint(r);
			}
		}
		return ret;
	}

	/**
	 * resize all components so that the height equals the height of this panel.
	 * Also, resize this panel to have the width of the component that is the
	 * far right one
	 */
	@Override
	public void doLayout() {
		super.doLayout();
		int count = getComponentCount();
		int max = minimumWidth;
		int h = fixedHeight >= 0 ? fixedHeight : getHeight();
		for (int i = 0; i < count; i++) {
			Component c = getComponent(i);
			c.setBounds(c.getX(), 0, c.getWidth(), h);
			if (c.getX() + c.getWidth() > max) {
				max = c.getX() + c.getWidth();
			}
		}
		needToRecalculateWidth = false;
		cachedWidth = max;
		// do NOT call setBounds here, will cause infinite loops
	}

	/**
	 * Calculate the width of this panel, and set cachedWidth.
	 * 
	 * @return the width of all graph child objects
	 */
	private int getGraphWidth() {
		if (needToRecalculateWidth) {
			int count = getComponentCount();
			int max = minimumWidth;
			for (int i = 0; i < count; i++) {
				Component c = getComponent(i);
				if (c.getX() + c.getWidth() > max) {
					max = c.getX() + c.getWidth();
				}
			}
			needToRecalculateWidth = false;
			cachedWidth = max;
		}
		return cachedWidth;
	}

	@Override
	public Dimension getPreferredSize() {
		if (DEBUG) {
			Debug.debug("TrackPanel.getPreferredWidth=" + getGraphWidth());
		}
		return new Dimension(getGraphWidth(), fixedHeight);
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(getGraphWidth(), fixedHeight > 0 ? fixedHeight : 0);
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, fixedHeight >= 0 ? fixedHeight
				: Integer.MAX_VALUE);
	}

	@Override
	public void setBounds(int x, int y, int w, int h) {
		// super.setBounds(x, y, getGraphWidth(), h);
		super.setBounds(x, y, w, h);
		if (DEBUG) {
			Debug.debug("TrackPanel: resize to " + x + "," + y + "," + w + ","
					+ h);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.gui.graph.GraphScale.Listener#scaleChanged(com.mixblendr.gui.graph.GraphScale,
	 *      double)
	 */
	public void scaleChanged(GraphScale aScale, double oldScaleFactor) {
		needToRecalculateWidth = true;
		doValidate();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioListener#audioFileDownloadError(com.mixblendr.audio.AudioFile,
	 *      java.lang.Throwable)
	 */
	public void audioFileDownloadError(AudioFile file, Throwable t) {
		// TODO: display error message in region?
	}

	/**
	 * Set flag to recalculate the width of this panel, and revalidate it.
	 * 
	 * @see com.mixblendr.audio.AudioListener#audioRegionStateChange(com.mixblendr.audio.AudioTrack,
	 *      com.mixblendr.audio.AudioRegion,
	 *      com.mixblendr.audio.AudioRegion.State)
	 */
	public void audioRegionStateChange(AudioTrack aTrack, AudioRegion region,
			State state) {
		if (aTrack == this.track) {
			needToRecalculateWidth = true;
			doValidate();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioListener#audioTrackNameChanged(com.mixblendr.audio.AudioTrack)
	 */
	public void audioTrackNameChanged(AudioTrack aTrack) {
		// nothing to do
	}

	private RegionDragTool.Type dragType = T_NONE;
	private URL dragURL = null;
	private RegionGraph dragRegionGraph = null;
	private long dragSampleCount = 0;
	private JComponent dragPanel = null;
	// a cache of the AudioRegions in the track
	private List<AudioRegion> dragRegionList;
	private int dragCurrAction;
	private int dragOffsetX;
	private JComponent parentPanel;

	/**
	 * Remove the drag panel, repainting the space after some milliseconds
	 */
	private void removeDragPanel() {
		if (dragPanel != null && dragPanel.getParent() == this) {
			remove(dragPanel);
			if (DEBUG_REPAINT) {
				debug("TrackPanel: repaint1 from " + dragPanel.getX()
						+ " width=" + dragPanel.getWidth() + " (30ms)");
			}
			repaint(30, dragPanel.getX(), 0, dragPanel.getWidth(), getHeight());
			if (DEBUG_DRAG) {
				debug("Removed drag panel");
			}
		}
	}

	/**
	 * clear all variables used for dragging
	 */
	private void clearDragVars() {
		removeDragPanel();
		dragType = T_NONE;
		dragURL = null;
		dragRegionGraph = null;
		dragSampleCount = 0;
		dragRegionList = null;
		dragCurrAction = 0;
		dragOffsetX = 0;
		// do not clear drag panel -- keep it for later drags
	}

	private static boolean isNumber(char c) {
		return (c >= '0') && (c <= '9');
	}

	/** create the drag panel and initialize it with the correct width and height */
	private boolean createDragPanel() {
		if (dragSampleCount > 0 && scale != null) {
			if (dragPanel == null || !(dragPanel instanceof MPanel)) {
				dragPanel = new MPanel(getDragPanelBackground());
				((MPanel) dragPanel).setBackgroundTransparency(RegionGraph.BACKGROUND_TRANSPARENCY);
			}
			// TODO: for internal drags, create image of original panel and use
			// on MPanel
			int width = scale.sample2pixel(dragSampleCount);
			if (width < 5) {
				width = 5;
			}
			dragPanel.setBounds(0, 0, width, getHeight());
			if (DEBUG_DRAG) {
				debug("Created drag panel: width=" + width + " ("
						+ dragSampleCount + " samples)");
			}
			return true;
		}
		if (DEBUG_DRAG) {
			debug("Could not create drag panel!");
		}
		return false;
	}

	private boolean extractDragSampleCount(String filename) {
		dragSampleCount = 0;
		// first, remove extension
		int dotPos = filename.lastIndexOf('.');
		if (dotPos >= 0) {
			filename = filename.substring(0, dotPos);
		}
		int i = filename.length();
		while (isNumber(filename.charAt(--i))) {
			// nothing
		}
		i++;
		if (i < filename.length()) {
			String number = filename.substring(i);
			try {
				dragSampleCount = Long.parseLong(number);
			} catch (NumberFormatException nfe) {
				if (DEBUG_DRAG) {
					debug("Cannot convert number '" + number
							+ "' from filename " + filename);
				}
			}
		}
		return dragSampleCount > 0;
	}

	/**
	 * From the given filename, remove the sample count and the extension
	 * 
	 * @param filename the filename of which to extract the pure name
	 * @return the cleaned filename
	 */
	private String extractFilename(String filename) {
		// first, remove extension
		int dotPos = filename.lastIndexOf('.');
		if (dotPos >= 0) {
			filename = filename.substring(0, dotPos);
		}
		int i = filename.length() - 1;
		while (i >= 0 && isNumber(filename.charAt(i))) {
			i--;
		}
		if (i >= 0 && filename.charAt(i) == '_') {
			i--;
		}
		return filename.substring(0, i + 1);
	}

	private final static long UNKNOWN_SAMPLE_COUNT = 22050;

	private boolean initDrag(DropTargetDragEvent e) {
		boolean ok = false;
		dragType = RegionDragTool.getTransferType(e.getCurrentDataFlavors());
		dragURL = null;
		dragRegionGraph = null;
		dragCurrAction = -1; // force init
		dragSampleCount = 0;
		dragOffsetX = 0;
		switch (dragType) {
		case T_URL:
			// need to call acceptDrag before being able to get the data?
			e.acceptDrag(DnDConstants.ACTION_COPY);
			dragURL = RegionDragTool.getURL(e.getTransferable());
			ok = (dragURL != null) && extractDragSampleCount(dragURL.getFile());
			if (!ok) {
				// FIXME: for now, just simulate the count
				dragSampleCount = UNKNOWN_SAMPLE_COUNT;
				ok = true;
			}
			if (DEBUG_DRAG) {
				Point p = e.getLocation();
				debug("Trackpanel: initDrag " + p.x + "," + p.y + ": "
						+ dragURL);
			}
			break;
		case T_AUDIO_REGION:
			// need to call acceptDrag before being able to get the data?
			e.acceptDrag(e.getDropAction());
			dragRegionGraph = RegionDragTool.getRegionGraph(e.getTransferable());
			ok = (dragRegionGraph != null && dragRegionGraph.getRegion() != null);
			if (ok) {
				dragOffsetX = dragRegionGraph.getDragOffsetX();
				dragSampleCount = dragRegionGraph.getRegion().getEffectiveDurationSamples();
				ok = (dragSampleCount > 0);
			}
			if (DEBUG_DRAG) {
				Point p = e.getLocation();
				debug("Trackpanel: initDrag " + p.x + "," + p.y + ": "
						+ dragRegionGraph);
			}
			break;
		case T_NONE:
			if (DEBUG_DRAG) {
				Point p = e.getLocation();
				debug("Trackpanel: initDrag " + p.x + "," + p.y
						+ ": not the correct type");
			}
			break;
		}
		ok = ok && (track != null);
		if (ok) {
			ok = createDragPanel();
		}
		if (!ok) {
			e.rejectDrag();
			clearDragVars();
			if (DEBUG_DRAG) {
				debug("Trackpanel: initDrag: drag rejected");
			}
		} else {
			dragRegionList = track.getPlaylist().getAudioRegions(dragRegionList);
			updateDragAction(e.getDropAction());
			if (DEBUG_DRAG) {
				debug("Trackpanel: initDrag: dragging " + dragSampleCount
						+ " samples");
			}
			// get parent panel
			if (parentPanel == null) {
				Container parent = getParent();
				if (parent != null && parent instanceof JComponent) {
					parentPanel = (JComponent) parent;
				}
			}
			if (parentPanel == null || globals.getAllRegionsViewPort() == null) {
				error("cannot get viewport parent (disables auto scrolling)");
			}
		}
		return ok;
	}

	/** if dragging internally, on COPY the original panel is visible */
	private void updateDragAction(int newAction) {
		if (newAction != dragCurrAction) {
			dragCurrAction = newAction;
			if (dragRegionGraph != null) {
				dragRegionGraph.setVisible(newAction == DnDConstants.ACTION_COPY);
			}
		}
	}

	/**
	 * When dragging over the track panel, try to find a spot where the drag
	 * object can be dropped. If snapToBeats is checked, align to beats. If the
	 * track's boundary is reached, enlarge the track.
	 * 
	 * @param dragLocation the current location of the drag
	 * @param doPaint if true, repaint the drag panel
	 * @return the insert position, or -1 if no suitable insert
	 */
	private long handleDragOver(Point dragLocation, boolean doPaint) {
		if (dragSampleCount <= 0 || scale == null || dragRegionList == null
				|| globals == null || dragPanel == null) {
			if (DEBUG_DRAG) {
				String because = "";
				if (dragSampleCount <= 0)
					because = "dragSampleCount=" + dragSampleCount;
				if (scale == null) because = "scale=" + scale;
				if (dragRegionList == null)
					because = "dragRegionList=" + dragRegionList;
				if (globals == null) because = "globals=" + globals;
				if (dragPanel == null) because = "dragPanel=" + dragPanel;
				Debug.debug("Cannot accept drag because " + because);
			}
			return -1;
		}
		lastDragXLocation = dragLocation.x;

		// the inserted container is centered around the mouse pointer
		long start = scale.pixel2sample(dragLocation.x - dragOffsetX)
				- (dragSampleCount / 2);
		long middle = start + (dragSampleCount / 2);
		if (start < 0) {
			start = 0;
		}
		if (middle < 0) {
			middle = 0;
		}
		start = globals.getSnapToBeats().getSnappedSample(start,
				dragSampleCount);
		long end = start + dragSampleCount;
		long insertSample = -1;
		int count = dragRegionList.size();
		if (count == 0) {
			insertSample = start;
		} else {
			AudioRegion thisRegion;
			AudioRegion nextRegion = null;
			for (int i = 0; i <= count; i++) {
				thisRegion = nextRegion;
				long thisRegionStart;
				long thisRegionEnd;
				if (thisRegion == null) {
					thisRegionStart = 0;
					thisRegionEnd = 0;
				} else {
					thisRegionStart = thisRegion.getStartTimeSamples();
					thisRegionEnd = thisRegionStart
							+ thisRegion.getEffectiveDurationSamples();
				}
				long nextRegionStart;
				// if we're dragging+move this region, you can drop on its own
				// area
				if (dragRegionGraph != null && i < count
						&& dragCurrAction == DnDConstants.ACTION_MOVE
						&& dragRegionList.get(i) == dragRegionGraph.getRegion()) {
					// skip the dragged region
					i++;
				}
				if (i < count) {
					nextRegion = dragRegionList.get(i);
					nextRegionStart = nextRegion.getStartTimeSamples();
				} else {
					nextRegionStart = Long.MAX_VALUE;
				}
				if (thisRegionStart > start) {
					// do not search further if the start of this region is
					// larger than our start pos
					break;
				}
				if (thisRegionEnd <= start && nextRegionStart >= end) {
					// can insert here directly
					insertSample = start;
					if (DEBUG_DRAG_MOVE) {
						debug("Can insert directly: enough space between region "
								+ (i - 1)
								+ "'s end ("
								+ thisRegionEnd
								+ ") and next region's start ("
								+ nextRegionStart + ")");
					}
					break;
				}
				if (nextRegionStart - thisRegionEnd >= dragSampleCount) {
					// there is enough space at all in between.
					if (start < thisRegionEnd && middle >= thisRegionEnd) {
						// need to shift a little to the right
						insertSample = thisRegionEnd;
						if (DEBUG_DRAG_MOVE) {
							debug("Insert at end of region " + (i - 1) + " ("
									+ thisRegionEnd + ")");
						}
						break;
					}
					if (end > nextRegionStart && middle < nextRegionStart) {
						// need to shift a little to the left
						insertSample = nextRegionStart - dragSampleCount;
						if (DEBUG_DRAG_MOVE) {
							debug("Insert just before region " + i
									+ "'s start (" + nextRegionStart + ")");
						}
						break;
					}
				}
			}
		}
		if (insertSample >= 0) {
			if (doPaint) {
				int oldx = dragPanel.getX();
				int x = scale.sample2pixel(insertSample);
				dragPanel.setBounds(x, 0, dragPanel.getWidth(), getHeight());
				if (dragPanel.getParent() != this) {
					if (DEBUG_DRAG_MOVE) {
						debug("TrackPanel: Adding dragpanel at position " + x);
					}
					add(dragPanel);
					if (DEBUG_REPAINT) {
						debug("TrackPanel: repaint1 from " + x + " width="
								+ dragPanel.getWidth());
					}
					repaint(x, 0, dragPanel.getWidth(), getHeight());
				} else {
					int from = (oldx < x) ? oldx : x;
					int to = ((oldx > x) ? oldx : x) + dragPanel.getWidth();
					if (DEBUG_REPAINT) {
						debug("TrackPanel: repaint2 from " + from + " width="
								+ (to - from + 1));
					}
					repaint(from, 0, to - from + 1, getHeight());
				}
				// now resize the parent container if we exceed the right
				// boundary
				if (parentPanel != null
						&& x + dragPanel.getWidth() > parentPanel.getWidth()) {
					minimumWidth = x + dragPanel.getWidth();
					parentPanel.setBounds(parentPanel.getX(),
							parentPanel.getY(), x + dragPanel.getWidth(),
							parentPanel.getHeight());
				}
			}
		} else {
			removeDragPanel();
		}
		return insertSample;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.dnd.DropTargetListener#dragEnter(java.awt.dnd.DropTargetDragEvent)
	 */
	public void dragEnter(DropTargetDragEvent e) {
		if (DEBUG_DRAG) {
			Debug.debug("dragEnter TrackPanel");
		}
		if (initDrag(e)) {
			if (globals != null) {
				globals.setDraggingMouse(true);
			}
			if (handleDragOver(e.getLocation(), true) < 0) {
				// do not reject here, otherwise an initially rejected drag will
				// not be able to continue
			} else {
				e.acceptDrag(e.getDropAction());
				if (DEBUG_DRAG) {
					Debug.debug("dragEnter acceptDrag action="
							+ e.getDropAction());
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.dnd.DropTargetListener#dragExit(java.awt.dnd.DropTargetEvent)
	 */
	public void dragExit(DropTargetEvent e) {
		if (DEBUG_DRAG) {
			Debug.debug("dragExit TrackPanel");
		}
		clearDragVars();
		if (globals != null) {
			globals.setDraggingMouse(false);
		}
	}

	private int lastDragXLocation = -1;

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.dnd.DropTargetListener#dragOver(java.awt.dnd.DropTargetDragEvent)
	 */
	public void dragOver(DropTargetDragEvent e) {
		Point p = e.getLocation();
		if (DEBUG_DRAG_MOVE) {
			debug("TrackPanel: dragOver " + p.x + "," + p.y);
		}
		// manual autoscrolling
		// FIXME: MacOS does not call the dragOver repeatedly if the mouse
		// cursor stays put
		int movePixels = globals.getAutoScrollPixels(p.x, true);
		movePixels = globals.scroll(movePixels);
		if (movePixels != 0) {
			p.x += movePixels;
			if (Globals.DEBUG_DRAG_SCROLL) {
				debug("    moved p.x to " + p.x);
			}
		}
		if (p.x != lastDragXLocation) {
			if (handleDragOver(p, true) < 0) {
				e.rejectDrag();
				if (DEBUG_DRAG && false) {
					debug("Reject drag action " + e.getDropAction());
				}
			} else {
				e.acceptDrag(e.getDropAction());
				if (DEBUG_DRAG && false) {
					debug("Accept drag action " + e.getDropAction());
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.dnd.DropTargetListener#drop(java.awt.dnd.DropTargetDropEvent)
	 */
	public void drop(DropTargetDropEvent e) {
		if (DEBUG_DRAG) {
			Point p = e.getLocation();
			debug("Trackpanel: drop " + p.x + "," + p.y);
		}
		// the inserted region
		AudioRegion region = null;
		long insertSample = handleDragOver(e.getLocation(), false);
		if (insertSample >= 0) {
			switch (dragType) {
			case T_URL:
				// need to call acceptDrop before being able to get the data?
				e.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
				if (dragURL == null) {
					dragURL = RegionDragTool.getURL(e.getTransferable());
				}
				if (dragURL != null) {
					boolean insertLast = false;
					if (dragSampleCount == UNKNOWN_SAMPLE_COUNT) {
						// insert at last so that clicking a region that
						// overlaps is possible
						insertLast = true;
						if (DEBUG) {
							debug("Insert new region as first component");
						}
						if (!extractDragSampleCount(dragURL.getFile())) {
							// don't know the size
							dragSampleCount = -1;
						}
					}
					region = globals.addRegion(getTrack(), dragURL,
							insertSample, dragSampleCount);
					addRegion(region, insertLast ? getComponentCount() : -1);
					// set the track name to this region's name
					setTrackName(extractFilename(Utils.getBaseName(dragURL.getFile())));
				} else {
					insertSample = -1;
					Debug.displayErrorDialog(this, "Drag'n'Drop error",
							"Cannot drop this link.");
				}
				break;
			case T_AUDIO_REGION:
				e.acceptDrop(e.getDropAction());
				if (dragRegionGraph != null) {
					if (e.getDropAction() == DnDConstants.ACTION_COPY) {
						if (DEBUG_DRAG) {
							debug("Handle drop COPY");
						}
						region = (AudioRegion) dragRegionGraph.getRegion().clone();
						region.setStartTimeSamples(insertSample);
						getTrack().getPlaylist().addObject(region);
						addRegion(region);
					} else {
						region = dragRegionGraph.getRegion();
						if (dragRegionGraph.getParent() == this) {
							if (DEBUG_DRAG) {
								debug("Handle drop MOVE in same track panel");
							}
							region.setStartTimeSamples(insertSample);
						} else {
							if (DEBUG_DRAG) {
								debug("Handle drop MOVE by removing this region graph and adding it in other track panel");
							}
							region.getOwner().removeObject(region);
							region.setStartTimeSamples(insertSample);
							this.getTrack().getPlaylist().addObject(region);
							// addRegion(region);
							add(dragRegionGraph);
						}
					}
					dragRegionGraph.setVisible(true);
				} else {
					insertSample = -1;
					Debug.displayErrorDialog(this, "Drag'n'Drop error",
							"Cannot drop this audio graph.");
				}
				break;
			case T_NONE:
				insertSample = -1;
				break;
			}
		}
		if (insertSample >= 0) {
			update();
			if (region != null) {
				RegionGraph rg = getRegionGraph(region);
				if (rg != null) {
					rg.setSelected(true);
					rg.requestFocus();
				}
			}
		}
		e.dropComplete(insertSample >= 0);
		clearDragVars();
		if (globals != null) {
			globals.setDraggingMouse(false);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.dnd.DropTargetListener#dropActionChanged(java.awt.dnd.DropTargetDragEvent)
	 */
	public void dropActionChanged(DropTargetDragEvent e) {
		if (DEBUG_DRAG) {
			Point p = e.getLocation();
			debug("Trackpanel: dropActionChanged " + p.x + "," + p.y + ": "
					+ e.getDropAction());
		}
		updateDragAction(e.getDropAction());
		if (handleDragOver(e.getLocation(), true) >= 0) {
			if (DEBUG_DRAG && false) {
				debug("Accept drag action " + e.getDropAction());
			}
			e.acceptDrag(e.getDropAction());
		} else {
			e.rejectDrag();
		}
	}

	/**
	 * Method for AWT's auto scrolling (AutoScroll interface), but it doesn't
	 * work well. Better use the manual auto scroll.
	 * 
	 * @see java.awt.dnd.Autoscroll#autoscroll(java.awt.Point)
	 */
	@SuppressWarnings("unused")
	private void autoscroll(Point cursorLocn) {
		int movePixels = globals.getAutoScrollPixels(cursorLocn.x, true);
		if (Globals.DEBUG_DRAG_SCROLL) {
			debug("autoscroll(x=" + cursorLocn.x + ") called. movePixels="
					+ movePixels);
		}
		globals.scroll(movePixels);
	}

	private Insets autoScrollInsets = new Insets(0, Globals.AUTO_SCROLL_PIXEL,
			0, Globals.AUTO_SCROLL_PIXEL);

	/**
	 * Method for AWT's auto scrolling (AutoScroll interface), but it doesn't
	 * work well. Better use the manual auto scroll.
	 * 
	 * @see java.awt.dnd.Autoscroll#getAutoscrollInsets()
	 */
	@SuppressWarnings("unused")
	private Insets getAutoscrollInsets() {
		if (Globals.DEBUG_DRAG_SCROLL) {
			debug("getAutoscrollInsets() called");
		}
		autoScrollInsets.left = globals.getAllRegionsScrollX()
				+ Globals.AUTO_SCROLL_PIXEL;
		autoScrollInsets.right = Globals.AUTO_SCROLL_PIXEL + getWidth()
				- globals.getAllRegionsScrollX()
				- globals.getAllRegionsScrollWidth();
		return autoScrollInsets;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
	 */
	public void mouseClicked(MouseEvent e) {
		// nothing to do
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
	 */
	public void mouseEntered(MouseEvent e) {
		// nothing to do
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
	 */
	public void mouseExited(MouseEvent e) {
		// nothing to do
	}

	/**
	 * Upon pressing the left mouse butoon into white space of the track,
	 * deselect the currently active region.
	 * 
	 * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
	 */
	public void mousePressed(MouseEvent e) {
		if (SwingUtilities.isLeftMouseButton(e) && globals != null) {
			globals.getSelectionManager().setSelected(null);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
	 */
	public void mouseReleased(MouseEvent e) {
		// nothing to do
	}
}
