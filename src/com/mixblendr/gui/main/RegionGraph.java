/**
 *
 */
package com.mixblendr.gui.main;

import static com.mixblendr.util.Debug.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import com.mixblendr.audio.*;
import com.mixblendr.gui.graph.Graph;
import com.mixblendr.gui.graph.GraphSection;
import com.mixblendr.skin.ControlDelegate;
import com.mixblendr.skin.MControl;
import com.mixblendr.skin.SkinUtils;
import com.mixblendr.util.Debug;

/**
 * A graph that always displays a region. The GraphSection is always fixed at
 * the wave portion of the region.
 * 
 * @author Florian Bomers
 */
public class RegionGraph extends Graph implements GraphScale.Listener,
		MControl, DragSourceListener, Serializable, KeyListener {

	private final static boolean DEBUG = false;
	private final static boolean DEBUG_DRAG = false;
	private final static boolean DEBUG_REPAINT = false;

	private final static Color COLOR_SCISSOR_CURSOR = new Color(100, 240, 240);
	public final static float BACKGROUND_TRANSPARENCY = 0.9f;
	public final static float DOWNLOADING_GRAPH_TRANSPARENCY = 0.4f;

	public final static float STATUS_FONT_SIZE = 14f;

	private AudioRegion region;

	private GraphScale scale;

	private Globals globals;

	private ControlDelegate waveBackground, waveBackgroundSel;

	private DragSource dragSource;

	private boolean selected;

	/**
	 * the offset in pixels from the middle position. Used during drag
	 * operations
	 */
	private int dragOffsetX;

	/**
	 * HACK: if regions with unknown width are dropped on the track panel, it
	 * might overlap when fully loaded. So for now, such panels' max width will
	 * be calculated at drop time and entered here. Set to 0 for no max width.
	 */
	private int maxDrawWidth = 0;
	
	private boolean downloading = false;

	/**
	 * Create a new region graph.
	 */
	public RegionGraph() {
		this(null, null);
	}

	/**
	 * Create a new region graph and initialize it with the given region.
	 */
	public RegionGraph(AudioRegion region) {
		this(region, null, null);
	}

	/**
	 * Create a new region graph and initialize it with the given region.
	 */
	public RegionGraph(AudioRegion region, Globals globals) {
		this(region, globals.getScale(), globals);
	}

	/**
	 * Create a new region graph, initialize it with the given region, and set
	 * it to auto scale using the specified scale. If globals are given, this
	 * graph will handle the mouse operations on its own.
	 */
	public RegionGraph(AudioRegion region, GraphScale scale, Globals globals) {
		super();
		this.globals = globals;
		showSelCursor = false;
		showSelection = false;
		canSelect = false;
		canChangeVerticalZoom = false;
		showZeroLine = true;
		zeroLineColor = new Color(180, 180, 255);
		showEdge = false;
		showGraphBackground = false;
		showMono = true;

		// cannot set left/right edge, otherwise the cut position would appear
		// off
		leftEdge = 0;
		rightEdge = 0;
		topEdge = 2;
		bottomEdge = 2;
		// no paint areas: graph will not be painted there to not cover waveform
		// container edge
		noPaintLeft = 2;
		noPaintRight = 2;

		setForeground(new Color(220, 220, 255));
		setBackground(new Color(236, 236, 236));
		SkinUtils.setFont(this, null, STATUS_FONT_SIZE);
		init(region);
		setAutoScale(scale);
		setFocusable(true);
		setOpaque(false);
		// dragging
		setTransferHandler(null);
		this.dragSource = DragSource.getDefaultDragSource();
		addKeyListener(this);
	}

	/*
	 * initialize audio data.
	 */
	public void init(AudioRegion aRegion) {
		if (this.region != null) {
			unregister();
		}
		this.region = aRegion;
		if (aRegion == null) {
			init(null, null, null);
		} else {
			init(aRegion.getAudioFile(), null, new GraphSection(
					(int) aRegion.getAudioFileOffset(),
					(int) aRegion.getEffectiveDurationSamples()));
		}
	}

	/**
	 * @return the background delegate
	 */
	public ControlDelegate getWaveBackground() {
		return waveBackground;
	}

	/**
	 * @param waveBackground the background delegate to set
	 */
	public void setWaveBackground(ControlDelegate waveBackground) {
		if (this.waveBackground != waveBackground) {
			if (this.waveBackground != null) {
				this.waveBackground.setOwner(null);
			}
			this.waveBackground = waveBackground;
			if (waveBackground != null) {
				waveBackground.setOwner(this);
			}
		}
	}

	/**
	 * @param waveBackgroundSel the selected background delegate to set
	 */
	public void setWaveBackgroundSel(ControlDelegate waveBackgroundSel) {
		if (this.waveBackgroundSel != waveBackgroundSel) {
			if (this.waveBackgroundSel != null) {
				this.waveBackgroundSel.setOwner(null);
			}
			this.waveBackgroundSel = waveBackgroundSel;
			if (waveBackgroundSel != null) {
				waveBackgroundSel.setOwner(this);
			}
		}
	}

	/**
	 * Override this method to re-set the section when audio data changes
	 */
	@Override
	protected void audioDataChanged() {
		if (this.audioFile != null /*&& !this.audioFile.canPlayBeforeFullyLoaded()*/) {
			if (graphTransparency < 1.0f && this.audioFile.isFullyLoaded()) {
				graphTransparency = 1.0f;
			} else if (graphTransparency == 1.0f && !this.audioFile.isFullyLoaded()) {
				graphTransparency = DOWNLOADING_GRAPH_TRANSPARENCY;
			}
		}
		if (region != null) {
			// inhibit section change event
			this.section.setSection((int) region.getAudioFileOffset(),
					(int) region.getEffectiveDurationSamples(), false);
			if (!downloading) {
				downloading = !region.getAudioFile().isFullyLoaded();
			}
		}
		super.audioDataChanged();
		checkScale();
		calcMaxDrawWidth();
	}

	/* (non-Javadoc)
	 * @see com.mixblendr.gui.graph.Graph#audioFileDownloadStart(com.mixblendr.audio.AudioFile)
	 */
	@Override
	public void audioFileDownloadStart(AudioFile source) {
		downloading = true;
		super.audioFileDownloadStart(source);
	}

	/*
	 * Remove transparency of the graph
	 * 
	 * @see com.mixblendr.audio.AudioFileURL.Listener#audioFileDownloadEnd(com.mixblendr.audio.AudioFile)
	 */
	@Override
	public void audioFileDownloadEnd(AudioFile source) {
		downloading = false;
		if (this.audioFile != null /* && !this.audioFile.canPlayBeforeFullyLoaded() */) {
			if (graphTransparency < 1.0f) {
				graphTransparency = 1.0f;
			}
			repaint();
		}
		super.audioFileDownloadEnd(source);
	}

	/**
	 * Overriden to tell the default implementation of <tt>Component</tt> that
	 * this component is not opaque (the background position grid shines
	 * through)
	 */
	@Override
	public boolean isOpaque() {
		return false;
	}

	/**
	 * draw the background using the images provided in the panel.wave_container
	 * control.
	 */
	@Override
	protected void paintNonGraphArea(Graphics g) {
		if (maxDrawWidth > 0) {
			Rectangle r = g.getClipBounds();

			if (r.x + r.width > maxDrawWidth) {
				// if we are asked to paint beyond the current max draw width,
				// recalculate the maxDrawWidth
				calcMaxDrawWidth();
			}
			if (maxDrawWidth > 0) {
				// now modify clip so that it goes to a maximum of maxDrawWidth
				if (r.x >= maxDrawWidth) {
					// don't paint anything at all
					r.width = 0;
				} else if (r.x + r.width > maxDrawWidth) {
					// reduce width
					r.width = maxDrawWidth - r.x;
				}
				g.setClip(r.x, r.y, r.width, r.height);
			}
		}
		if (DEBUG_REPAINT) {
			debug("RegionGraph " + ID + ": paint non-graph area");
		}
		if (selected && waveBackgroundSel != null) {
			waveBackgroundSel.paint(g, 0, 0, BACKGROUND_TRANSPARENCY);
		} else if (waveBackground != null) {
			waveBackground.paint(g, 0, 0, BACKGROUND_TRANSPARENCY);
		} else {
			super.paintNonGraphArea(g);
		}
	}

	private int scissorCursorX = -1;

	/**
	 * Paint the scissor position cursor
	 */
	@Override
	protected void afterPaint(Graphics g, int drawX, int drawY, int drawW,
			int drawH) {
		if (globals != null
				&& globals.getOperation() == RegionMouseOperation.SCISSOR
				&& scissorCursorX >= drawX && scissorCursorX <= drawX + drawW) {
			g.setColor(COLOR_SCISSOR_CURSOR);
			g.drawLine(scissorCursorX, topEdge, scissorCursorX, getHeight()
					- bottomEdge);
		}
		if (downloading && region != null) {
			String text;
			if (region.getAvailableSamples() > 0) {
				if (region.getDuration() > 0) {
					int percent = (int) (region.getAvailableSamples() * 100 / region.getDuration());
					text = ""+percent+"% loaded"; 
				} else {
					text = ""+region.getAvailableSamples()+" samples loaded"; 
				}
			} else {
				text = "waiting for server...";
			}
			// get viewport's left position
			drawX = noPaintLeft;
			if (drawX + getX() < globals.getAllRegionsScrollX()) {
				drawX = globals.getAllRegionsScrollX() + noPaintLeft - getX();
			}
			g.setColor(Color.black);
			g.drawString(text, drawX, (getHeight() - (int) STATUS_FONT_SIZE) / 2);
		}
	}

	/**
	 * Use this method to let the control reposition itself automatically
	 * according to the scale. Set to null to revert back to non-managed
	 * position and width.
	 */
	public void setAutoScale(GraphScale scale) {
		if (this.scale != null) {
			this.scale.removeListener(this);
		}
		if (scale != null) {
			scale.addListener(this);
		}
		this.scale = scale;
	}

	/**
	 * Calculate the x position, in pixels, given the scale and the region start
	 * time.
	 * 
	 * @param scale the GraphScale used for converting pixels to samples
	 * @param startTime the start time of the region, in samples
	 * @return the x position, in pixels, of the region
	 */
	private final static int calcRegionX(GraphScale scale, long startTime) {
		// double factor = scale.getScaleFactor();
		// return (int) (startTime * factor);
		return scale.sample2pixel(startTime);
	}

	/**
	 * Calculate the width, in pixels, of the region, given the scale, and the
	 * region start time and its duration.
	 * 
	 * @param scale the GraphScale used for converting pixels to samples
	 * @param startTime the start time of the region, in samples
	 * @param duration the effective duration of the region, in samples
	 * @return the width, in pixels, of the region
	 */
	@SuppressWarnings("unused")
	private final static int calcRegionWidth(GraphScale scale, long startTime,
			long duration) {
		int x = calcRegionX(scale, startTime);
		return scale.sample2pixel(startTime + duration) - x;
	}

	/**
	 * Calculate the width, in pixels, of the region, given the scale, the
	 * region start time, its duration, and the x position of the region.
	 * 
	 * @param scale the GraphScale used for converting pixels to samples
	 * @param startTime the start time of the region, in samples
	 * @param duration the effective duration of the region, in samples
	 * @param x the x position of the region, as returned by calcRegionX()
	 * @return the width, in pixels, of the region
	 */
	private final static int calcRegionWidth(GraphScale scale, long startTime,
			long duration, int x) {
		int w = scale.sample2pixel(startTime + duration) - x;
		if (w < 2) {
			return 2;
		}
		return w;
	}

	/**
	 * Go check the next region's start and see if it overlaps into this region.
	 * If yes, set a maximum draw width, so that the rest of this panel is
	 * painted transparent and therefore not visible.
	 */
	private void calcMaxDrawWidth() {
		if (region == null || region.getOwner() == null || scale == null)
			return;
		maxDrawWidth = 0;
		AudioRegion nextRegion = region.getOwner().getRegionAfter(region);
		if (nextRegion != null) {
			long thisStart = region.getStartTimeSamples();
			long thisLength = region.getEffectiveDurationSamples();
			long nextStart = nextRegion.getStartTimeSamples();
			if (thisStart + thisLength > nextStart) {
				// found an overlap
				maxDrawWidth = scale.sample2pixel(nextStart)
						- scale.sample2pixel(thisStart);
			}
		}
		if (DEBUG) {
			debug("RegionGraph " + ID + ": calcMaxDrawWidth=" + maxDrawWidth
					+ "   width=" + getWidth());
		}
	}

	/**
	 * If a scale is set, resize this component to accurately fit the wave data.
	 */
	private void checkScale() {
		if (scale != null && region != null) {
			int x = calcRegionX(scale, this.region.getStartTimeSamples());
			int w = calcRegionWidth(scale, this.region.getStartTimeSamples(),
					this.region.getEffectiveDurationSamples(), x);
			setPreferredSize(new Dimension(w, -1));
			setMinimumSize(new Dimension(w, 1));
			setMaximumSize(new Dimension(w, Integer.MAX_VALUE));
			setBounds(x, getY(), w, getHeight());
			if (DEBUG) {
				Debug.debug("RegionGraph " + ID
						+ ": checkScale: setBounds to x=" + x + " w=" + w
						+ " ( for " + this.region.getEffectiveDurationSamples()
						+ " samples)");
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.gui.graph.GraphScale.Listener#scaleChanged(double,
	 *      double)
	 */
	public void scaleChanged(GraphScale aScale, double oldScaleFactor) {
		checkScale();
	}

	/**
	 * @return the region
	 */
	public AudioRegion getRegion() {
		return region;
	}

	/**
	 * @return the scale
	 */
	public GraphScale getScale() {
		return scale;
	}

	protected long getAbsoluteSamplePositionFromPixel(int x) {
		if (region != null) {
			return toSamplesX(x) - region.getAudioFileOffset()
					+ region.getStartTimeSamples();
		}
		return 0;
	}

	/**
	 * return the current sample position relative to the beginning of the
	 * region
	 */
	protected long getRegionPositionFromPixel(int x) {
		if (region != null) {
			return toSamplesX(x) - region.getAudioFileOffset();
		}
		return 0;
	}

	/** return the audio track that this region is on, or null if not available */
	protected AudioTrack getTrack() {
		if (region != null && region.getOwner() != null) {
			return region.getOwner().getOwner();
		}
		return null;
	}

	/**
	 * return the audio playlist that this region is on, or null if not
	 * available
	 */
	private Playlist getPlaylist() {
		if (region != null) {
			return region.getOwner();
		}
		return null;
	}

	/** if parent is TrackPanel, return it, otherwise null */
	private TrackPanel getOwnerTrackPanel() {
		if (getParent() instanceof TrackPanel) {
			return (TrackPanel) getParent();
		}
		return null;
	}

	/** if the parent is a TrackPanel, call its update method */
	private void updateTrackPanel() {
		TrackPanel tp = getOwnerTrackPanel();
		if (tp != null) {
			tp.update();
		} else {
			error("RegionGraph: Cannot get track panel");
		}
	}

	/**
	 * @return the selected
	 */
	public boolean isSelected() {
		return selected;
	}

	/**
	 * This method will request the RegionSelectionManager to select itself.
	 * 
	 * @param selected the selected to set
	 */
	public void setSelected(boolean selected) {
		if (this.selected != selected) {
			if (globals != null) {
				if (selected) {
					globals.getSelectionManager().setSelected(this);
				} else {
					if (globals.getSelectionManager().getLastSelected() == this) {
						globals.getSelectionManager().setSelected(null);
					} else {
						setSelected(false);
					}
				}
			} else {
				setSelected(selected);
			}
		}
	}

	/**
	 * This method actually selects this region - should only be called from
	 * RegionSelectionManager.
	 * 
	 * @param selected the selected to set
	 */
	void setSelectedImpl(boolean selected) {
		if (this.selected != selected) {
			this.selected = selected;
			if (DEBUG) {
				debug("RegionGraph " + ID + ": selected=" + selected);
			}
			repaint();
		}
	}

	// ////////////////////////////////////// mouse events
	// /////////////////////////////////////

	private static class RegionDragGestureRecognizer extends
			DragGestureRecognizer {

		/**
		 * @param ds
		 * @param c
		 * @param sa
		 * @param dgl
		 */
		public RegionDragGestureRecognizer(InputEvent ev, DragSource ds,
				Component c, int sa, DragGestureListener dgl) {
			super(ds, c, sa, dgl);
			appendEvent(ev);
		}

		@Override
		protected void registerListeners() {
			// nothing
		}

		@Override
		protected void unregisterListeners() {
			// nothing
		}

	}

	/**
	 * Return the offset from where the user pressed down the mouse, relative to
	 * the middle of the graph, where the drag panel usually is positioned
	 */
	int getDragOffsetX() {
		return dragOffsetX;
	}

	private List<InputEvent> dragEventList;

	/**
	 * @param x the mouse x coordinate
	 * @return the sample position where to cut from this mouse position, or -1
	 *         if cutting is not possible at this position
	 */
	private long getCutSample(int x) {
		long sample = getAbsoluteSamplePositionFromPixel(x);
		sample = globals.getSnapToBeats().getSnappedSample(sample);
		// make it a relative position to the region again
		sample -= region.getStartTimeSamples();
		if (sample <= 0 || sample >= region.getEffectiveDurationSamples()) {
			return -1;
		}
		return sample;
	}

	/** set the new scissor cut cursor x position. If needed, repaint */
	private void setScissorCursorPos(int newX) {
		if (DEBUG) {
			debug("RegionGraph: request to set scissor cursor to x=" + newX);
		}
		if (newX < 0) newX = -1;
		if (newX == scissorCursorX) return;
		int x1 = newX;
		int x2 = scissorCursorX;
		if (x1 < 0) x1 = x2;
		if (x2 < 0) x2 = x1;
		scissorCursorX = newX;
		int x = Math.min(x1, x2);
		int w = Math.max(x1, x2) - x + 1;
		repaint(x, 0, w, getHeight());
	}

	/**
	 * depending on the mouse x position and the button state, display the
	 * scissor cursor or not
	 */
	private void updateScissorCursor(MouseEvent e) {
		if (!SwingUtilities.isLeftMouseButton(e)
				&& !SwingUtilities.isMiddleMouseButton(e)
				&& !SwingUtilities.isRightMouseButton(e)) {
			updateScissorCursor(e.getX());
		} else {
			setScissorCursorPos(-1);
		}
	}

	/** depending on the mouse x position, display the scissor cursor or not */
	private void updateScissorCursor(int mouseX) {
		if (mouseX < 0) {
			setScissorCursorPos(-1);
		} else {
			long sample = getCutSample(mouseX);
			// make the sample position relative
			sample += region.getAudioFileOffset();
			if (sample >= 0) {
				if (DEBUG) {
					debug("RegionGraph: scissor cursor sample=" + sample);
				}
				setScissorCursorPos(toPixelX(sample));
			} else {
				setScissorCursorPos(-1);
			}
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		Playlist playlist = getPlaylist();
		if (globals != null && playlist != null) {
			if (globals.getOperation() == RegionMouseOperation.SCISSOR) {
				long sample = getCutSample(e.getX());
				if (sample >= 0) {
					playlist.splitRegion(region, sample);
					updateTrackPanel();
				}
			}
		}
	}

	@Override
	public void mousePressed(MouseEvent ev) {
		if (!hasFocus() && isRequestFocusEnabled()) {
			requestFocus();
		}
		setSelected(true);
		if (globals == null) return;
		globals.setDraggingMouse(true);
		if (globals.getOperation() == RegionMouseOperation.SELECT) {
			try {
				if (dragEventList == null) {
					dragEventList = new ArrayList<InputEvent>(3);
				} else {
					dragEventList.clear();
				}
				dragEventList.add(ev);
			} catch (InvalidDnDOperationException idoe) {
				idoe.printStackTrace();
			}
		} else if (globals.getOperation() == RegionMouseOperation.SCISSOR) {
			setScissorCursorPos(-1);
		}

	}

	@Override
	public void mouseDragged(MouseEvent ev) {
		if (globals == null) return;
		if (globals.getOperation() == RegionMouseOperation.SELECT) {
			if (DEBUG_DRAG) {
				debug("RegionGraph " + ID + ": start drag");
			}
			DragGestureRecognizer dgr = new RegionDragGestureRecognizer(ev,
					dragSource, this, DnDConstants.ACTION_COPY_OR_MOVE, null);
			DragGestureEvent dge = new DragGestureEvent(dgr,
					DnDConstants.ACTION_MOVE, ev.getPoint(), dragEventList);
			Transferable transferable = new RegionTransferable(this);
			dragOffsetX = ev.getX() - (getWidth() / 2);
			// do not set a cursor, otherwise the cursor will not get
			// updated
			dragSource.startDrag(dge, null, transferable, this);
		}
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		if (globals == null) return;
		if (globals.getOperation() == RegionMouseOperation.SCISSOR) {
			updateScissorCursor(e);
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (globals == null) return;
		if (globals.getOperation() == RegionMouseOperation.SCISSOR) {
			updateScissorCursor(e);
		}
		globals.setDraggingMouse(false);
	}

	/** set the cursor, depending on the current region mouse operation */
	private void updateCursor() {
		if (globals != null) {
			if (globals.getOperation() == RegionMouseOperation.SCISSOR) {
				setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
			} else {
				setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
			}
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		if (globals != null) {
			if (!globals.isDraggingMouse()) {
				updateCursor();
				updateScissorCursor(e);
			}
		}
	}

	@Override
	public void mouseExited(MouseEvent e) {
		if (globals == null) return;
		if (globals.getOperation() == RegionMouseOperation.SCISSOR) {
			// remove cut line
			setScissorCursorPos(-1);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.skin.MControl#getDelegate()
	 */
	public ControlDelegate getDelegate() {
		return waveBackground;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.dnd.DragSourceListener#dragDropEnd(java.awt.dnd.DragSourceDropEvent)
	 */
	public void dragDropEnd(DragSourceDropEvent dsde) {
		if (DEBUG_DRAG) {
			debug("RegionGraph " + ID + ": dragDropEnd: "
					+ dsde.getDropAction() + "  success="
					+ dsde.getDropSuccess());
		}
		if (!dsde.getDropSuccess()) {
			if (!hasFocus() && isRequestFocusEnabled()) {
				requestFocus();
			}
			setVisible(true);
		}
		updateCursor();
		if (globals != null) {
			globals.setDraggingMouse(false);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.dnd.DragSourceListener#dragEnter(java.awt.dnd.DragSourceDragEvent)
	 */
	public void dragEnter(DragSourceDragEvent dsde) {
		// ignore
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.dnd.DragSourceListener#dragExit(java.awt.dnd.DragSourceEvent)
	 */
	public void dragExit(DragSourceEvent dse) {
		// ignore
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.dnd.DragSourceListener#dragOver(java.awt.dnd.DragSourceDragEvent)
	 */
	public void dragOver(DragSourceDragEvent dsde) {
		// ignore
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.dnd.DragSourceListener#dropActionChanged(java.awt.dnd.DragSourceDragEvent)
	 */
	public void dropActionChanged(DragSourceDragEvent e) {
		// ignore
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.KeyListener#keyPressed(java.awt.event.KeyEvent)
	 */
	public void keyPressed(KeyEvent e) {
		// System.out.println("Key press " + e.getKeyCode());
		if (region != null && globals != null
				&& (e.getKeyCode() == KeyEvent.VK_DELETE)
				|| (e.getKeyCode() == KeyEvent.VK_BACK_SPACE)) {
			if (globals.confirm("Are you sure that you want to delete "
					+ "this audio region?")) {
				TrackPanel tp = getOwnerTrackPanel();
				if (tp == null || !tp.removeGraph(this)) {
					globals.FYI("Error removing this region.");
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.KeyListener#keyReleased(java.awt.event.KeyEvent)
	 */
	public void keyReleased(KeyEvent e) {
		// ignore
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.KeyListener#keyTyped(java.awt.event.KeyEvent)
	 */
	public void keyTyped(KeyEvent e) {
		// ignore
	}

}
