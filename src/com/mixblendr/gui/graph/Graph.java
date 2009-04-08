/*
 * Copyright (c) 1997 - 2007 by Bome Software / Florian Bomers
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * - Redistributions of source code must include the source code of the
 * Mixblendr software or its derivatives.
 * - Redistributions in binary form must be packaged with the Mixblendr
 * software, or its derivatives.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.mixblendr.gui.graph;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

import javax.swing.JComponent;

import org.tritonus.share.sampled.FloatSampleBuffer;

import com.mixblendr.audio.AudioFile;
import com.mixblendr.audio.AudioFileURL;
import com.mixblendr.audio.AudioPeakCache;
import static com.mixblendr.util.Debug.*;

/**
 * Display the graph of a region. It handles the section and current selection.
 * If enabled, the mouse can be used to select a region. If enabled, the
 * vertical zoom can be changed by little knob at the far right.
 * <p>
 * (c) copyright 1997-2007 by Bome Software
 * 
 * @author Florian Bomers
 */
public class Graph extends JComponent implements MouseMotionListener,
		MouseListener, GraphSelection.Listener, GraphSection.Listener,
		GraphTimer.Listener, AudioFileURL.Listener {

	private final static boolean DEBUG = false;
	private final static boolean DEBUG_DRAW = false;

	public final static int ALL_CHANNELS = -1;

	/* the region which provides the audio data */
	protected AudioFile audioFile;

	/**
	 * the selection (is marked in graph) Like the CacheManager, also selection
	 * objects may be shared. To share it, overwrite createSelection()
	 */
	protected GraphSelection sel;

	/** current section */
	protected GraphSection section;

	/** when showing the playback cursor, it is at this sample position */
	protected int currPlaybackCursor = -1;

	private java.util.List<Graph> audioChangeListeners = null;

	/**
	 * Flag to always show the graph as a mono graph - no matter how many
	 * channels the audio file actually has.
	 */
	protected boolean showMono = false;

	// //////////////////////////////////////////// general graph colors
	// /////////////////////////////
	/** color of the zero line */
	protected Color zeroLineColor = Color.green;

	/** color of selection box */
	protected Color selColor = Color.red;

	// set graph color with setForegroundColor()
	
	/** color of disabled graph */
	protected Color disabledGraphColor = Color.darkGray;

	/** color of disabled zeroLine */
	protected Color disabledZeroLineColor = Color.gray;

	/** set to a value 0..1 for transparency */
	protected float graphTransparency = 1.0f;

	protected Color edgeColor = Color.darkGray;

	/** if the edge is painted opr not */
	protected boolean showEdge = true;

	/** whether to clear the background of the waveform or not */
	protected boolean showGraphBackground = true;

	/**
	 * if selection is one point, this color is used for marking the vertical
	 * line
	 */
	protected Color selCursorColor = Color.green;

	/** color of position pointer (for playback) */
	protected Color playbackCursorColor = Color.yellow;

	// //////////////////////////////////////////// graph parameters
	// ////////////////////////////////
	/** minimum value of sample values (e.g. -1) */
	protected float minSampleValue;
	/** maximum value of sample values (e.g. +1) */
	protected float maxSampleValue;

	/** value where the zero-line is painted (e.g. 0) */
	protected float zeroSampleValue;

	// these four are ONLY used in PerformanceVars, etc.
	protected int leftEdge = 0;
	protected int topEdge = 0;
	protected int rightEdge = 0;
	protected int bottomEdge = 0;

	protected boolean showAnalogDots = true;

	protected boolean showZeroLine = true;

	/** number of pixels at left side which are not painted */
	protected int noPaintLeft = 0;
	/** number of pixels at right side which are not painted */
	protected int noPaintRight = 0;

	/**
	 * holder of some pre-calculated values that change rarely (for speed of
	 * drawing)
	 */
	protected PerformanceVars pv = new PerformanceVars();

	// //////////////////////////////////////// parameters for level of
	// interaction /////////////////////
	protected boolean showSelCursor = true;
	protected boolean showSelection = true;
	protected boolean canSelect = true;
	protected boolean canChangeVerticalZoom = true;

	// /////////////////////////////////////////// Vertical zoom
	// /////////////////////////////////////
	private boolean changingVerticalZoom = false;
	private int changingVerticalZoomDelta = 0;
	private double changingVerticalZoomInitialValue = 0.0;
	private static final int verticalZoomHandleHeight = 6; // should be an even
	// value
	private static final int verticalZoomHandleSnap = 3;
	private static final double maxVerticalZoom = 3.0; // 0..x per cent

	private static final boolean SELECT_RIGHT = false;
	private static final boolean SELECT_ALL_CHANNELS = true;
	private static final boolean SELECT_LEFT = false;

	protected double currVerticalZoom = 1.0; // normal view
	private boolean changeVerticalZoomCursorSet = false;
	private Color verticalZoomThumbColor = Color.yellow;

	// ///////////////////////////////////////// selecting with mouse
	// ////////////////////////////////
	/** used during scrolling while selecting */
	protected GraphTimer selScrollTimer = null;
	/** current x position of the mouse while selecting */
	protected int selectingScrollX = 0;
	/** if currently selecting with mouse */
	protected boolean selecting = false;
	/** the start sample during selecting (where the mouse was put down) */
	protected int selStartSample = 0;

	protected static int IDctr = 0;
	protected int ID = IDctr++;

	/**
	 * Instanciates a new Graph panel. Calls init()
	 */
	public Graph() {
		init();
	}

	/**
	 * Is called by constructor. Resets background and foreground colors (to
	 * black and white), adds mouseListeners, creates cacheManager, Selection
	 * and Section, and calls removeAll.
	 */
	protected void init() {
		setBackground(Color.black);
		setForeground(Color.white);
		addMouseListener(this);
		addMouseMotionListener(this);
		createSelection();
		sel.addListener(this);
		createSection();
		section.addListener(this);
		setOpaque(true);
		removeAll();
	}

	/**
	 * Creates the selection object. Should be overriden if another selection
	 * instance is to be used.
	 */
	protected void createSelection() {
		sel = new GraphSelection();
	}

	/**
	 * Creates the GraphSection object. Should be overriden if another section
	 * instance is to be used.
	 */
	protected void createSection() {
		section = new GraphSection();
	}

	/*
	 * initialize audio data.
	 */
	public void init(AudioFile file) {
		init(file, null, null);
	}

	/**
	 * initialize audio data.
	 * 
	 * @param file the audio file to render
	 * @param sel1 the new selection, or null to not select anything
	 * @param section1 the new visible section, or null to set to all samples
	 */
	public void init(AudioFile file, GraphSelection sel1, GraphSection section1) {
		if (file == null) {
			removeAll();
		} else {
			if (this.audioFile != file) {
				unregister();
				this.audioFile = file;
				register();
			}
			currVerticalZoom = 1.0;
			maxSampleValue = 1.0f;
			minSampleValue = -1.0f;
			zeroSampleValue = 0.0f;
			if (section1 == null) {
				this.section.setSection(0,
						(int) file.getEffectiveDurationSamples());
			} else {
				this.section.setSection(section1);
			}
			if (sel1 == null) {
				this.sel.setSelection(-1, 0);
			} else {
				this.sel.setSelection(sel1);
			}
			// will cause a repaint()
			audioDataChanged();
			notifyAudioChangeListeners();
		}
	}

	/**
	 * Removes the references to the audio data and resets selection and
	 * section.
	 */
	@Override
	public void removeAll() {
		unregister();
		audioFile = null; // pas de Samples
		section.setSection(0, 0);
		sel.setSelection(0, 0);
		// will issue repaint()
		audioDataChanged();
		notifyAudioChangeListeners();
	}

	/** add itself as listener for audioFile events */
	protected void register() {
		if ((audioFile != null) && (audioFile instanceof AudioFileURL)) {
			((AudioFileURL) audioFile).addListener(this);
		}
	}

	/** remove itself from any listeners */
	protected void unregister() {
		if ((audioFile != null) && (audioFile instanceof AudioFileURL)) {
			((AudioFileURL) audioFile).removeListener(this);
		}
	}

	/**
	 * rebuilds the cache in the specified region. The region is redisplayed
	 * using repaintSampleRange.
	 */
	public void updateData(int fromSample, int toSample) {
		repaintSampleRange(fromSample - pv.samplesOnePixel - 1, toSample
				+ pv.samplesOnePixel + 1);
	}

	private void notifyAudioChangeListeners() {
		if (audioChangeListeners != null) {
			for (Graph g : audioChangeListeners)
				g.audioDataChanged();
		}
	}

	public void addAudioChangeListener(Graph g) {
		if (audioChangeListeners == null)
			audioChangeListeners = new ArrayList<Graph>();
		audioChangeListeners.add(g);
	}

	public void removeAudioChangeListener(Graph g) {
		if (audioChangeListeners != null) {
			audioChangeListeners.remove(g);
			if (audioChangeListeners.size() == 0) audioChangeListeners = null;
		}
	}

	/**
	 * This function is called by this graph to all its registered
	 * audioChangeListeners. It is guaranteed that whenever audioData changes,
	 * the listeners are called. It is called after setting audioData and
	 * channelCount. This function calculates important data in Graph so
	 * overwriting functions MUST call super.audioDataChanged() just before
	 * returning from this function.
	 */
	protected void audioDataChanged() {
		pv.update();
		repaint(); // is asynchronous
	}

	// ////////////////////////////////////// audio graph position/size methods
	// ///////////////////
	/** Returns the width of the audio graph. */
	protected int getGraphWidth() {
		return pv.graphWidth;
		/*
		 * if (canPaintGraph()) return getWidth()-leftEdge-rightEdge; else
		 * return 0;
		 */
	}

	/**
	 * Returns height of a single audio channel graph. All channels have equal
	 * height !
	 */
	protected int getGraphHeight() {
		return pv.graphHeight;
		/*
		 * if (canPaintGraph()) return
		 * (getHeight()-topEdge-bottomEdge)/channelCount; else return 0;
		 */
	}

	/**
	 * Returns the last pixel occupied by graph nr channel. if
	 * 
	 * @see #ALL_CHANNELS is specified, the bottom value of the last channel is
	 *      returned.
	 */
	protected int getGraphBottom(int channel) {
		if (channel == ALL_CHANNELS)
			channel = getChannelCount();
		else
			channel++;
		return channel * pv.graphHeight + getGraphTop(0);
	}

	/**
	 * Returns the x position of the graph display. It is relative to the origin
	 * of this component.
	 */
	protected int getGraphLeft() {
		return leftEdge;
	}

	/**
	 * Returns y coordinate of graph nr. channel. It is relative to origin of
	 * this component.
	 */
	protected int getGraphTop(int channel) {
		return topEdge + channel * getGraphHeight();
	}

	/**
	 * Returns bounds rect of the graph nr. channel. The coordinates are
	 * relative to origin of this component.
	 */
	protected Rectangle getGraphBounds(int channel) {
		if (channel != ALL_CHANNELS) {
			return new Rectangle(getGraphLeft(), getGraphTop(channel),
					getGraphWidth(), getGraphHeight());
		}
		return new Rectangle(getGraphLeft(), getGraphTop(0), getGraphWidth(),
				getGraphHeight() * getChannelCount());
	}

	/**
	 * Returns the boundaries of all graphs in the range fromSample...toSample.
	 * The resulting rectangles origin is relative to the component's origin.
	 */
	protected Rectangle getGraphBoundaries(int fromSample, int toSample) {
		if (fromSample < section.getStart()) fromSample = section.getStart();
		if (toSample > section.getEnd()) toSample = section.getEnd();
		if (fromSample > toSample || fromSample > section.getEnd()
				|| toSample < section.getStart())
			return new Rectangle(0, 0, 0, 0);
		int x1 = toPixelX(fromSample);
		int x2 = toPixelEndX(toSample);
		return new Rectangle(getGraphLeft() + x1, getGraphTop(0), x2 - x1 + 1,
				getGraphHeight() * getChannelCount());
	}

	// /////////////////////////////////// pixel <-> sample conversion
	// //////////////////////////////////

	/**
	 * Returns how many pixel correspond to one sample. It is truncated to
	 * integer.
	 * 
	 * @see PerformanceVars#pixelOneSample
	 */
	protected int pixelOneSample() {
		return getGraphWidth() / section.getLength();
	}

	/**
	 * Returns how many pixel correspond to one sample as a double value.
	 * 
	 * @see PerformanceVars#pixelOneSampleF
	 */
	protected double pixelOneSampleF() {
		return ((double) getGraphWidth()) / section.getLength();
	}

	/**
	 * Returns how many samples correspond to one pixel. It is truncated to
	 * integer.
	 */
	public int samplesOnePixel() {
		if (getGraphWidth() > 0) {
			// avoid division by zero
			return section.getLength() / getGraphWidth();
		}
		return 1;
	}

	/**
	 * Returns how many samples correspond to one pixel as a double value.
	 * 
	 * @see PerformanceVars#samplesOnePixelF
	 */
	protected double samplesOnePixelF() {
		if (getGraphWidth() > 1) {
			// avoid division by zero
			return ((double) section.getLength()) / getGraphWidth();
		}
		return 1.0;
	}

	/**
	 * this converts a sample value sample to the y-pixel-coordinate. It is
	 * relative to graphs' height.
	 */
	protected int toPixelY(double sample) {
		return pv.graphHeight - 1
				- (int) ((sample - pv.toPixelYHelp1) * pv.toPixelYHelp2);
	}

	// truncSectionStartPixel is the truncated pixel position of
	// section.getStart()
	// when the graph would start at pixel 0 (and not at section.getStart())
	// -> the truncated section.getStart() value is always the far left visible
	// coordinate
	/**
	 * returns the sample index of the x pixel. It is truncated to integer. x is
	 * relative to the <B>graphs</B> 0,0 coordinate.
	 */
	protected int toSamplesX(int x) { // pixel a samples
		if (x == 0 || !pv.canPaintGraph) return section.getStart();
		// if (pv.pixelOneSample<=1)
		return (int) ((x + pv.truncSectionStartPixel) * pv.samplesOnePixelF);
		// else
		// return (int)
		// (0.5+(x+pv.truncSectionStartPixel)*pv.samplesOnePixelF);
	}

	/**
	 * return the sample index of the x pixel as a double value. x is relative
	 * to the <B>graphs</B> 0,0 coordinate.
	 */
	protected double toSamplesXF(int x) { // pixel to samples
		if (x == 0 || !pv.canPaintGraph) return section.getStart();
		return (x + pv.truncSectionStartPixel) * pv.samplesOnePixelF;
	}

	/** returns the last pixel "owned" by this sample. */
	protected int toSamplesEndX(int x) { // pixel a samples
		int first = toSamplesX(x);
		int next = toSamplesX(x + 1);
		if (first == next) {
			return first;
		}
		return next - 1;
	}

	/**
	 * Returns the x pixel coordinate of the given sample position. It is
	 * rounded up to the nearest integer. The resulting pixel is relative to the
	 * <B>graph's</B> origin.
	 */
	protected int toPixelX(long sample) {
		if (sample == section.getStart() || !pv.canPaintGraph) return 0;
		return ((int) Math.ceil(sample * pv.pixelOneSampleF))
				- pv.truncSectionStartPixel;
	}

	/**
	 * Returns the x pixel coordinate of the given sample position. It is
	 * rounded up to the nearest integer. The resulting pixel is relative to the
	 * <B>graph's</B> origin.
	 */
	protected int toPixelX(int sample) {
		if (sample == section.getStart() || !pv.canPaintGraph) return 0;
		return ((int) Math.ceil(sample * pv.pixelOneSampleF))
				- pv.truncSectionStartPixel;
	}

	/**
	 * Returns the x pixel coordinate of sample with index x as a double value.
	 * The resulting pixel is relative to the <B>graphs</B> origin.
	 */
	protected double toPixelXF(int x) {
		return x * pv.pixelOneSampleF - pv.truncSectionStartPixel;
	}

	/** returns the last pixel "owned" by sample x */
	protected int toPixelEndX(int x) {
		// todo: maybe this can be optimized...
		int first = toPixelX(x);
		int next = toPixelX(x + 1);
		if (first == next) {
			return first;
		}
		return next - 1;
	}

	// /////////////////////////////// Overriden methods of Component
	// ///////////////////////////

	/**
	 * Overriden to update the PerformanceVars.
	 */
	@Override
	public void setBounds(int x, int y, int width, int height) {
		super.setBounds(x, y, width, height);
		pv.update();
	}

	/**
	 * Overriden in order to prevent a clearRect of the default implementation.
	 * This component is totally opaque, so a clearRect of the background is not
	 * necessary.
	 */
	@Override
	public void update(Graphics g) {
		// S*ystem.out.println("Graph.update");
		// paint(g);
		// super.update(g);
	}

	/**
	 * Overriden to tell the default implementation of <tt>Component</tt> that
	 * this component is totally opaque (i.e. it paints every pixel)
	 */
	@Override
	public boolean isOpaque() {
		return true;
	}

	// ///////////////////////////////////// high-level paint methods
	// ////////////////////////////

	/**
	 * Returns, whether the graph can be painted. This is true, if the area of
	 * the graph is non-empty and there is audio data to be displayed.
	 */
	protected boolean canPaintGraph() {
		return pv.canPaintGraph;
	}

	/**
	 * This repaints the requested range asynchronously. This means many calls
	 * at once will only result in one call to paint(Graphics). Only the audio
	 * graphs are repainted, not the borders.
	 */
	public void repaintSampleRange(int fromSample, int toSample) {
		Rectangle r = getGraphBoundaries(fromSample, toSample);
		if (!r.isEmpty()) {
			if (DEBUG_DRAW) {
				debug("Calling repaint with x=" + r.x + " width=" + r.width);
			}
			repaint(r.x, r.y, r.width, r.height);
		}
	}

	/**
	 * This redraws the requested range synchronously, i.e. directly. Only the
	 * audio graphs are repainted, not the borders. This function should not be
	 * used, use repaintSampleRange instead as it compensates succesive calls.
	 */
	public void redrawWaveSynchronously(int fromSample, int toSample) {
		if (canPaintGraph()) {
			Graphics g = getGraphics();
			if (g != null) {
				Rectangle r;
				if (fromSample == section.getStart()
						&& toSample == section.getEnd())
					r = getGraphBounds(ALL_CHANNELS);
				else
					r = getGraphBoundaries(fromSample, toSample);
				if (!r.isEmpty()) {
					g.setClip(r);
					paintGraphArea(g); // paint only graphs
				}

				g.dispose();
			}
		}
	}

	// ///////////////////////////////////// low-level paint methods
	// ////////////////////////////

	/**
	 * Draws this component. The clip area of g is respected. First,
	 * paintNonGraphArea is called, then paintGraphArea.
	 */
	@Override
	public void paint(Graphics g) {
		if (isShowing() && g != null) {
			paintNonGraphArea(g);
			paintGraphArea(g);
		}
	}

	/**
	 * Paints the borders of this component (if any). If the vertical zoom may
	 * be changed, its handle is painted as well.
	 */
	protected void paintNonGraphArea(Graphics G) {
		// pre-condition: G!=null
		// note: hitClip likes to throw nullpointer Exceptions, if the clip
		// is completely outside
		int nonx = pv.graphX + pv.graphWidth;
		int nonw = pv.width - pv.graphX - pv.graphWidth;
		if (showEdge) {
			G.setColor(edgeColor);
			if (pv.graphX > 0
					&& G.hitClip(0, pv.graphY, pv.graphX, pv.allGraphsHeight)) {
				G.fillRect(0, pv.graphY, pv.graphX, pv.allGraphsHeight);
			}
			if (pv.graphY > 0 && G.hitClip(0, 0, pv.width, pv.graphY)) {
				G.fillRect(0, 0, pv.width, pv.graphY);
			}
			if (pv.graphY + pv.allGraphsHeight < pv.height
					&& G.hitClip(0, pv.graphY + pv.allGraphsHeight, pv.width,
							pv.height - pv.graphY - pv.allGraphsHeight + 1)) {
				G.fillRect(0, pv.graphY + pv.allGraphsHeight, pv.width,
						pv.height - pv.graphY - pv.allGraphsHeight);
			}
			if (nonx < pv.width
					&& G.hitClip(pv.graphX + pv.graphWidth, pv.graphY, pv.width
							- nonx + 1, pv.allGraphsHeight)) {
				G.fillRect(nonx, pv.graphY, nonw, pv.allGraphsHeight);
			}
		}
		if (canChangeVerticalZoom) {
			if (nonx < pv.width
					&& G.hitClip(pv.graphX + pv.graphWidth, pv.graphY, pv.width
							- nonx + 1, pv.allGraphsHeight)) {
				// draw vertical zoom knob
				G.setColor(verticalZoomThumbColor);
				if (DEBUG_DRAW) {
					debug("call to verticalZoomToPixel(" + currVerticalZoom
							+ ") = " + verticalZoomToPixel());
				}
				int handleY = verticalZoomToPixel();
				G.fillRect(nonx, handleY + 1, nonw,
						verticalZoomHandleHeight - 2);
				G.fillRect(nonx + 1, handleY, nonw - 2,
						verticalZoomHandleHeight);
			}
		}
	}

	/**
	 * Draws the background of the graphs in the specified range. If there is a
	 * selected part in the clip area given by the <tt>draw</tt>...
	 * parameters, it is painted as well. The enabled state (whether a channel
	 * is selected) of the graphs is respected. If a graph is not enabled, the
	 * selection is not drawn.
	 */
	protected void paintGraphBackground(Graphics G, int drawX, int drawY,
			int drawW, int drawH, int fromSample, int toSample) {
		if (DEBUG_DRAW) {
			debug("paintGraphBackground() enter.");
		}

		if (!showSelection || sel.onePoint()
				|| (sel.getStart() > section.getEnd())
				|| (sel.getEnd() < section.getStart())
				|| (sel.getStart() > toSample) || (sel.getEnd() < fromSample)) {
			if (DEBUG_DRAW) {
				debug("Painting background from " + drawX + " to "
						+ (drawW + drawX - 1));
			}
			G.setColor(getBackground());
			G.fillRect(drawX, drawY, drawW, drawH);
		} else { // il y a une selection a dessiner
			// calculer la zone de selection qu'il faut dessiner
			int sx = toPixelX(Math.max(sel.getStart(), section.getStart()))
					+ pv.graphX;
			Rectangle selRect = new Rectangle(sx, pv.graphY,
					toPixelEndX(Math.min(sel.getEnd(), section.getEnd()))
							+ pv.graphX - sx + 1, pv.allGraphsHeight);
			selRect = selRect.intersection(new Rectangle(drawX, drawY, drawW,
					drawH));
			if (selRect.isEmpty()) {
				G.setColor(getBackground());
				G.fillRect(drawX, drawY, drawW, drawH);
				if (DEBUG_DRAW) {
					debug("Graph: Painting background: x=" + drawX + " width="
							+ drawW);
				}
			} else {
				if (DEBUG_DRAW) {
					debug("Graph: Painting selection rect:" + selRect);
				}
				if (drawW != selRect.width) {
					// clear left of selection
					int w = selRect.x - drawX;
					if (w > 0) {
						G.setColor(getBackground());
						G.fillRect(drawX, drawY, w, drawH);
					}

					// clear right of selection
					int x = selRect.x + selRect.width;
					w = drawX + drawW - x;
					if (w > 0) {
						G.setColor(getBackground());
						G.fillRect(x, drawY, w, drawH);
					}
				}
				// draw selection Rect
				G.setColor(selColor);
				if (SELECT_ALL_CHANNELS) {
					G.fillRect(selRect.x, drawY, selRect.width, drawH);
				} else if (SELECT_LEFT) {
					G.fillRect(selRect.x, pv.graphY, selRect.width,
							pv.graphHeight);
					G.setColor(getBackground());
					G.fillRect(selRect.x, getGraphTop(1), selRect.width,
							pv.graphHeight);
				} else if (SELECT_RIGHT) {
					G.fillRect(selRect.x, getGraphTop(1), selRect.width,
							pv.graphHeight);
					G.setColor(getBackground());
					G.fillRect(selRect.x, pv.graphY, selRect.width,
							pv.graphHeight);
				}
			}
		}
	}

	/**
	 * Set the transparency level.
	 * 
	 * @param g the graphics to set transparency
	 * @param alpha the transparency 0..1
	 * @return true if alpha is smaller than 1.0f and if the graphics' composite
	 *         was changed
	 */
	private static boolean setGraphicsTransparency(Graphics g, float alpha) {
		if (g instanceof Graphics2D) {
			Graphics2D g2d = (Graphics2D) g;
			Composite comp = g2d.getComposite();
			if (comp instanceof AlphaComposite) {
				AlphaComposite acomp = (AlphaComposite) comp;

				if (acomp.getAlpha() != alpha) {
					if (alpha < 1.0f) {
						g2d.setComposite(AlphaComposite.getInstance(
								AlphaComposite.SRC_OVER, alpha));
						return true;
					}
					g2d.setComposite(AlphaComposite.SrcOver);
				}
			}
		}
		return false;
	}

	/**
	 * Paints all parts belonging to the graphs: Selection/background, graphs,
	 * selCursor and positionCursor.
	 */
	protected void paintGraphArea(Graphics G) {
		if (G == null) {
			if (DEBUG_DRAW) {
				debug("Graph: graphics is null!");
			}
			return;
		}

		if (!canPaintGraph()) {
			if (DEBUG_DRAW) {
				debug("Graph: cannot Paint !");
			}
			G.setColor(getBackground());
			G.fillRect(pv.graphX, pv.graphY, pv.graphWidth, pv.graphHeight);
			return;
		}
		int drawX, drawY, drawW, drawH;

		Rectangle clip1 = G.getClipBounds();
		if (clip1 == null) {
			drawX = pv.graphX;
			drawW = pv.graphWidth;
			drawY = pv.graphY;
			drawH = pv.allGraphsHeight;
			if (DEBUG_DRAW) {
				debug("Graph: clipBounds returned null! Drawing entire graph area.");
			}
		} else {
			clip1 = clip1.intersection(getGraphBounds(ALL_CHANNELS));
			if (clip1.isEmpty()) {
				if (DEBUG_DRAW) {
					debug("Graph: cliprect is empty");
				}
				return; // nothing todo
			}

			drawX = clip1.x;
			drawW = clip1.width;
			drawY = clip1.y;
			drawH = clip1.height;
			if (DEBUG_DRAW) {
				debug("Graph paint with clip=" + G.getClip());
				debug("Graph paint with x1=" + drawX + " width=" + drawW);
			}
		}

		// if there are no-paint areas, modify paint area
		if (noPaintLeft > 0 || noPaintRight > 0) {
			if (drawX < pv.graphX + noPaintLeft) {
				int diff = pv.graphX + noPaintLeft - drawX;
				drawW -= diff;
				drawX += diff;
			}
			if (drawX + drawW > pv.graphX + pv.graphWidth - noPaintRight) {
				drawW = pv.graphX + pv.graphWidth - noPaintRight - drawX;
			}
		}

		G.clipRect(drawX, drawY, drawW, drawH);

		int fromSample = 0;
		int toSample = 0;
		if (section.getLength() > 0) {
			fromSample = minMax(toSamplesX(drawX - pv.graphX),
					section.getStart(), section.getEnd());
			toSample = minMax(toSamplesEndX(drawX + drawW - 1 - pv.graphX),
					section.getStart(), section.getEnd());
		}

		if (showGraphBackground) {
			paintGraphBackground(G, drawX, drawY, drawW, drawH, fromSample,
					toSample);
		}

		// now draw single channels
		for (int c = 0; c < getChannelCount(); c++) {
			boolean enabled = SELECT_ALL_CHANNELS || (c == 0 && SELECT_LEFT)
					|| (c == 1 && SELECT_RIGHT);
			// use new translated Graphics for drawing of graph
			Graphics graphG = G.create(pv.graphX, getGraphTop(c),
					pv.graphWidth, getGraphHeight());
			Rectangle graphRect = graphG.getClipBounds();
			if (!graphRect.isEmpty()) {
				if (getSampleCount() > 0) {
					if (!enabled) {
						graphG.setColor(disabledGraphColor);
					}  else {
						graphG.setColor(getForeground());
					}
					boolean hasSetAlpha = false;
					if (graphTransparency < 1.0f) {
						hasSetAlpha = setGraphicsTransparency(graphG, graphTransparency);
					}
					// finally draw graph
					drawGraph(graphG, graphRect.x, graphRect.x
							+ graphRect.width - 1, c);
					if (hasSetAlpha) {
						setGraphicsTransparency(graphG, 1.0f);
					}
				}
				// draw zero line
				if (showZeroLine) {
					if (!enabled) {
						graphG.setColor(disabledZeroLineColor);
					} else {
						graphG.setColor(zeroLineColor);
					}
					// why doesn't work it when using the cliprect for x and
					// width ??
					graphG.drawLine(0 /* graphRect.x */,
							toPixelY((int) zeroSampleValue),
							pv.graphWidth/* graphRect.width */,
							toPixelY((int) zeroSampleValue));
					if (DEBUG_DRAW) {
						debug("draw zero-line: x=" + graphRect.x + " width="
								+ graphRect.width);
					}
				}
			} else {
				if (DEBUG_DRAW) {
					debug("ClipRect for graph channel " + c + " is empty");
				}
			}

			graphG.dispose();
		}

		if (DEBUG_DRAW) {
			debug("drawGraph from " + drawX + " to " + (drawX + drawW) + " == "
					+ fromSample + " to " + toSample + " samples");
		}

		if (getSampleCount() > 0
				&& ((sel.onePoint() && showSelCursor) || (currPlaybackCursor != -1))) {
			// draw cursor
			int y = 0;
			int h = 0;
			if (SELECT_ALL_CHANNELS || getChannelCount() == 1) {
				y = pv.graphY;
				h = pv.allGraphsHeight;
			} else if (SELECT_LEFT) {
				y = pv.graphY;
				h = pv.graphHeight;
			} else if (SELECT_RIGHT) {
				y = getGraphTop(1);
				h = pv.graphHeight;
			}
			if (sel.onePoint() && showSelCursor && fromSample <= sel.start
					&& toSample >= sel.start) {
				int x = toPixelX(sel.start) + pv.graphX;
				G.setColor(selCursorColor);
				G.drawLine(x, y, x, y + h);
			}
			// draw playback cursor
			if (fromSample <= currPlaybackCursor
					&& toSample >= currPlaybackCursor) {
				int x = toPixelX(currPlaybackCursor) + pv.graphX;
				G.setColor(playbackCursorColor);
				G.drawLine(x, y, x, y + h);
			}
		}
		// allow inheritant classes to draw things
		afterPaint(G, drawX, drawY, drawW, drawH);
	}

	/**
	 * this may be overridden for extensions. It is called after all drawing in
	 * the graph area has been done
	 */
	protected void afterPaint(Graphics g, int drawX, int drawY, int drawW,
			int drawH) {
		// nothing
	}

	/**
	 * used during selecting, from and to in samples. Only the range from
	 * <tt>fromSample</tt> to <tt>toSample</tt> is drawn.
	 */
	private void paintSelectingPart(int fromSample, int toSample) {
		Graphics g = getGraphics();
		if (g != null) {
			if (SELECT_ALL_CHANNELS || getChannelCount() == 1) {
				// assumes that above and below graphs is nothing
				g.setClip(getGraphBounds(ALL_CHANNELS));
			} else if (SELECT_LEFT) {
				g.setClip(getGraphBounds(0));
			} else if (SELECT_RIGHT) {
				g.setClip(getGraphBounds(1));
			}
			int pixelFrom = toPixelX(fromSample);
			int width = toPixelEndX(toSample) - pixelFrom + 1;
			g.clipRect(pixelFrom + pv.graphX, 0, width, pv.height);
			paintGraphArea(g);
			g.dispose();
		}
	}

	/**
	 * Is used during Scrolling to draw the area that has just been scrolled
	 * away.
	 */
	private void paintScrollPart(Graphics g, int pixelOffset) {
		int from, w;
		if (pixelOffset < 0) {
			// draw on left
			from = 0;
			w = -pixelOffset;
		} else {
			from = pv.graphWidth - 1 - pixelOffset;
			w = pixelOffset;
		}
		if (g != null) {
			g.clipRect(pv.graphX + from, pv.graphY, w + pv.graphX,
					pv.allGraphsHeight);
			paintGraphArea(g);
		}
	}

	// //////////////////////////////// drawing the wave form
	// ///////////////////////////////////////

	/**
	 * For drawing the analog wave form, used by drawInterpolatedSamples.
	 * returns the interpolated pixel at the double <tt>sampleX</tt> position.
	 * 
	 * @param sampleCount number of samples available in samples
	 */
	private double interpolate(double sampleX, float[] samples, int sampleCount) {
		int quality = 3;
		if (sampleX == Math.floor(sampleX)) {
			return (sampleX < 0 || sampleX >= samples.length) ? 0
					: samples[(int) sampleX];
		}
		int from = (int) (sampleX) - quality;
		if (from < 0) from = 0;
		int to = (int) (sampleX) + quality;
		if (to >= sampleCount) to = sampleCount - 1;
		double Y = 0.0;
		double X = sampleX - from;
		if (X == 0.0) {
			return 0.0;
		}
		int i = from;
		while (i <= to) {
			double fac = Math.PI * X;
			Y += samples[i] * (Math.sin(fac) / fac);
			i++;
			X -= 1;
		}
		return Y;
	}

	/**
	 * Draws the interpolated, analog wave form. It is used when sampleOnePixel
	 * is <= 1
	 * 
	 * @param sampleCount number of samples available in samples
	 */
	private void drawInterpolatedSamples(Graphics G, int pixelFrom,
			int pixelTo, long startSample, float[] samples, int sampleCount) {
		if (samples == null) return;
		pixelFrom--;
		pixelTo++;
		int pixelX = pixelFrom;
		int oldY = toPixelY(interpolate(
				toSamplesXF(pixelX) - 0.5 - startSample, samples, sampleCount));
		while (pixelX++ < pixelTo) {
			int newY = toPixelY(interpolate(toSamplesXF(pixelX) - 0.5
					- startSample, samples, sampleCount));
			G.drawLine(pixelX - 1, oldY, pixelX, newY);
			oldY = newY;
		}
	}

	/**
	 * Draws the exact samples into the analog wave form as dots. It is used
	 * when pixelOnesample is >= 4
	 * 
	 * @param sampleCount number of samples available in samples
	 */
	private void drawDotSamples(Graphics G, int pixelFrom, int pixelTo,
			long startSample, float[] samples, int sampleCount) {
		if (samples == null) return;
		// "dot" the sample values
		int w = 1;
		if (pv.pixelOneSample > 15) w = 2;
		int sample = toSamplesX(pixelFrom) - (int) startSample;
		int sampleTo = toSamplesEndX(pixelTo + 1) - (int) startSample;
		if (sample < 0) sample = 0;
		if (sampleTo >= sampleCount) sampleTo = sampleCount - 1;
		while (sample <= sampleTo) {
			int pixelX = ((int) (0.5 + (sample + startSample + 0.5)
					* pv.pixelOneSampleF))
					- pv.truncSectionStartPixel;
			G.fillRect(pixelX - w, toPixelY(samples[sample]) - w, 1 + 2 * w,
					1 + 2 * w);
			sample++;
		}
	}

	/**
	 * Used by drawMinMaxSamples. Returns in <tt>ce</tt> the minimum and
	 * maximum sample value of the samples corresponding to <tt>pixelX</tt>.
	 * 
	 * @param sampleCount number of samples available in samples
	 */
	private final void calculateDisplayElement(int pixelX, CacheElement ce,
			long startSample, float[] samples, int sampleCount) {
		if (samples == null) return;

		int from = toSamplesX(pixelX) - (int) startSample;
		int to = toSamplesEndX(pixelX) - (int) startSample;

		// coming from outerspace...
		if (from >= sampleCount || to < 0) {
			ce.min = 0;
			ce.max = 0;
			return;
		}
		// truncate, if necessary
		if (to >= samples.length || from < 0) {
			from = minMax(from, 0, sampleCount - 1);
			to = minMax(to, 0, sampleCount - 1);
		}

		if (from >= to) {
			ce.min = samples[from];
			ce.max = ce.min;
			return;
		}

		float mi = 1.0f;
		float ma = -1.0f;
		for (int i = from; i <= to; i++) {
			float s = samples[i];
			if (s < mi) mi = s;
			if (s > ma) ma = s;
		}
		ce.min = mi;
		ce.max = ma;
		// debug("Calc Cache for pixelX=" + pixelX + " startSample=" +
		// startSample
		// + ": from=" + from + " to=" + to + ": min=" + mi + " max=" + ma);
	}

	/**
	 * Draws the graph as minimum-maximum lines. It is used when sampleOnePixel
	 * is < cacheElementSize.
	 * 
	 * @param sampleCount number of samples available in samples
	 */
	private void drawMinMaxSamples(Graphics G, int pixelFrom, int pixelTo,
			long startSample, float[] samples, int sampleCount) {
		if (samples == null) return;
		if (DEBUG_DRAW) {
			debug("Draw minmax samples: pixelX1=" + pixelFrom + " pixelX2="
					+ pixelTo + "  startSample=" + startSample);
		}
		CacheElement ce = new CacheElement();
		int pixelX = pixelFrom;
		// pixelTo++;
		while (pixelX <= pixelTo) {
			calculateDisplayElement(pixelX, ce, startSample, samples,
					sampleCount);
			G.drawLine(pixelX, toPixelY(ce.min), pixelX, toPixelY(ce.max));
			pixelX++;
		}
	}

	/**
	 * Draws the graph as minimum-maximum lines using the pre-calculated cache
	 * values. Uses the CacheManager. It is used when sampleOnePixel is >=
	 * cacheElementSize.
	 */
	private void drawCacheSamples(Graphics G, int pixelFrom, int pixelTo,
			int channel) {
		// draw from cache
		if (audioFile == null) {
			// cannot paint from cache
			return;
		}
		AudioPeakCache peakCache = audioFile.getPeakCache();
		if (peakCache == null) {
			// cannot paint from cache
			return;
		}
		FloatSampleBuffer minCache = peakCache.getMinCache();
		FloatSampleBuffer maxCache = peakCache.getMaxCache();
		if (minCache == null || maxCache == null
				|| minCache.getSampleCount() == 0) {
			// cannot paint from cache
			return;
		}
		float[] minData = minCache.getChannel(channel % minCache.getChannelCount());
		float[] maxData = maxCache.getChannel(channel % maxCache.getChannelCount());

		if (DEBUG) {
			if (minData.length != maxData.length) {
				debug("minData.length != maxData.length! minCache.getSampleCount()="
						+ minCache.getSampleCount()
						+ " maxCache.getSampleCount()="
						+ maxCache.getSampleCount());
			}
		}

		int pixelX = pixelFrom;
		int index = toSamplesX(pixelX) >> AudioPeakCache.SCALE_SHIFT;
		int cacheIndex = index;
		int sample = index << AudioPeakCache.SCALE_SHIFT;
		int cacheElementSize = AudioPeakCache.SCALE_FACTOR;
		int sampleNext;
		float mi, ma;
		int maxCacheIndex = peakCache.getHandledCacheElementCount();
		// just for sanity
		if (maxCacheIndex > maxData.length) {
			maxCacheIndex = maxData.length;
		}
		if (maxCacheIndex > minData.length) {
			maxCacheIndex = minData.length;
		}
		while (pixelX <= pixelTo) {
			// def:: a cache block belongs to a pixel, if the cache start is in
			// the pixel's sample range
			sampleNext = toSamplesX(pixelX + 1);
			if (cacheIndex >= maxCacheIndex) {
				break;
			}
			mi = minData[cacheIndex];
			ma = maxData[cacheIndex];
			cacheIndex++;
			sample += cacheElementSize;
			while (sample < sampleNext) {
				if (cacheIndex >= maxCacheIndex) {
					break;
				}
				if (minData[cacheIndex] < mi) mi = minData[cacheIndex];
				if (maxData[cacheIndex] > ma) ma = maxData[cacheIndex];
				cacheIndex++;
				sample += cacheElementSize;
			}
			G.drawLine(pixelX, toPixelY(mi), pixelX, toPixelY(ma));
			pixelX++;
		}
	}

	private float[] floatCache;

	/** @return the number of samples available in floatcache */
	private int getAudioData(int channel, long startSample, int sampleCount) {
		if (sampleCount < 0) return 0;
		if (audioFile == null) return 0;
		if (floatCache == null || floatCache.length < sampleCount) {
			floatCache = new float[sampleCount];
		}
		return audioFile.readChannelData(channel, startSample, floatCache, 0,
				sampleCount);
	}

	/**
	 * Draws the actual graph waveform between pixels <tt>from</tt> and
	 * <tt>to</tt> for </tt>channel</tt>. Depending on samplesOnePixel, the
	 * graph is drawn as analog waveform (eventually with dots),
	 * on-the-fly-calculated min-max values, or using the cached min-max values
	 * of CacheManager.
	 */
	protected void drawGraph(Graphics G, int from, int to, int channel) {
		try {
			if (pv.samplesOnePixelF >= 2
					&& pv.samplesOnePixelF >= AudioPeakCache.SCALE_FACTOR) {
				drawCacheSamples(G, from, to, channel);
			} else {
				// need to use actual audio data
				// TODO: remember cache until section is changed
				long startOffset = toSamplesX(from) - 5;
				if (startOffset < 0) startOffset = 0;
				long endOffset = toSamplesEndX(to) + 5;
				int count = getAudioData(channel, startOffset, (int) (endOffset
						- startOffset + 1));
				if (pv.samplesOnePixelF < 2) {
					drawInterpolatedSamples(G, from, to, startOffset,
							floatCache, count);
					if (pv.pixelOneSample > 4 && showAnalogDots) {
						drawDotSamples(G, from, to, startOffset, floatCache,
								count);
					}
				} else /*
						 * if (pv.samplesOnePixelF <
						 * AudioPeakCache.SCALE_FACTOR)
						 */{
					drawMinMaxSamples(G, from, to, startOffset, floatCache,
							count);
				}
			}
		} catch (Throwable t) {
			//t.printStackTrace();
			// Globals.debugOut(t,"Error in Graph (x="+x+", Display.length="
			// +Display.length+", FWidth="+FWidth+")");
		}

	}

	// ////////////////////////////// Playback Cursor
	// ///////////////////////////////////////////

	public void paintPlaybackCursor(int oldSample, int newSample) {
		if (canPaintGraph() && getSampleCount() > 0) {
			Graphics g = getGraphics();
			if (g != null) {
				int oldPixel = -1;
				if (oldSample != -1 && oldSample >= section.getStart()
						&& oldSample <= section.getEnd())
					oldPixel = toPixelX(oldSample) + getGraphLeft();
				int newPixel = -1;
				if (newSample != -1 && newSample >= section.getStart()
						&& newSample <= section.getEnd())
					newPixel = toPixelX(newSample) + getGraphLeft();
				Rectangle graphs = getGraphBounds(ALL_CHANNELS);
				if (newPixel != oldPixel && oldPixel < graphs.x + graphs.width
						&& oldPixel >= graphs.x) {
					g.setClip(new Rectangle(Math.max(oldPixel - 1, 0),
							graphs.y, 3, graphs.height));
					paintGraphArea(g);
				}
				if (newPixel != oldPixel && newPixel < graphs.x + graphs.width
						&& newPixel >= graphs.x) {
					g.setClip(new Rectangle(Math.max(newPixel - 1, 0),
							graphs.y, 3, graphs.height));
					paintGraphArea(g);
				}
				g.dispose();
			}
		}
	}

	/**
	 */
	public void paintPlaybackCursor(int pos) {
		if (currPlaybackCursor != pos) {
			int old = currPlaybackCursor;
			currPlaybackCursor = pos;
			paintPlaybackCursor(old, currPlaybackCursor);
		}
	}

	/**
	 */
	public void clearPlaybackCursor() {
		int old = currPlaybackCursor;
		currPlaybackCursor = -1;
		paintPlaybackCursor(old, currPlaybackCursor);
	}

	public static int minMax(int value, int min, int max) {
		if (value < min)
			return min;
		else if (value > max)
			return max;
		else
			return value;
	}

	/**
	 * scroll the graph by pixelDelta pixels without updating the section ->
	 * this has to be done before ! When pixelDelta<0 -> scroll to right side
	 * redrawAdd is a positive number by which the updated region is expanded
	 * (in pixel)
	 */
	protected void scroll(int pixelDelta, int redrawAdd) {
		if (pixelDelta < 0) redrawAdd = -redrawAdd;
		// move at least one pixel
		if (Math.abs(pixelDelta) >= 1) {
			if (Math.abs(pixelDelta) >= getGraphWidth()) {
				if (DEBUG_DRAW) {
					debug("Graph.scrollA redraw");
				}
				// repaint tout le graph
				repaint(); // redraw();
			} else {
				if (DEBUG_DRAW) {
					debug("Graph.scroll copyArea, move " + pixelDelta
							+ " pixel.");
				}
				// copier le vieux graph et redessiner
				// seulement la nouvelle partie
				Graphics g = getGraphics();
				if (g != null) {
					// assumes nothing below/above graphs
					Rectangle graphRect = getGraphBounds(ALL_CHANNELS);
					g.setClip(graphRect);
					g.copyArea(graphRect.x, graphRect.y, graphRect.width,
							graphRect.height, -pixelDelta, 0);
					// redessiner la nouvelle partie
					paintScrollPart(g, pixelDelta + redrawAdd);
					g.dispose();
				}
			}
		} else if (redrawAdd != 0) {
			// only draw the redrawAdd, if necessary
			Graphics g = getGraphics();
			if (g != null) {
				paintScrollPart(g, redrawAdd);
				g.dispose();
			}
		}
	}

	// interface SelectionListener
	public void selectionChanged(GraphSelection sel1, GraphSelection oldSel) {
		// S*ystem.out.println("selectionChanged: newSel="+sel);
		pv.updateSelectionVars();
		int oldStart = oldSel.getStart();
		int oldEnd = oldSel.getEnd();
		boolean oldOnePoint = oldSel.onePoint();
		if (false /* selection channel has changed */) {
			// OK, it CAN be optimized, but for now it's OK
			repaint();
			return;
		}

		// redraw ranges
		int s1 = -1, s2 = -1, e1 = -1, e2 = -1;
		if (oldOnePoint || sel1.onePoint()) {
			// repaint old selection
			s1 = oldStart;
			e1 = oldEnd;
			// draw new selection
			s2 = sel1.getStart();
			e2 = sel1.getEnd();
			if (oldOnePoint) e1 = s1;
			if (sel1.onePoint()) e2 = s2;
		} else {
			// need to redraw the difference zone
			// first the sel.getStart() difference
			if (oldStart != sel1.getStart()) {
				s1 = Math.min(oldStart, sel1.getStart());
				e1 = Math.max(oldStart, sel1.getStart());
				if (s1 < 0) s1 = 0;
				if (e1 < 0) e1 = 0;
			}
			// then the sel.getEnd() difference
			if (oldEnd != sel1.getEnd()) {
				s2 = Math.min(oldEnd, sel1.getEnd());
				e2 = Math.max(oldEnd, sel1.getEnd());
				if (s2 < 0) s2 = 0;
				if (e2 < 0) e2 = 0;
			}
		}
		// now do some optimizing of the 2 ranges:
		// maybe they overlap
		if (s1 >= 0 && s2 >= 0) { // both ranges have been set
			if ((s1 >= s2 && s1 <= e2) // 1st start contained in 2nd range
					|| (s2 >= s1 && s2 <= e1) // 2nd start contained in 1st
					// range
					|| (e1 <= e2 && e1 >= s2) // 1st end contained in 2nd
					// range
					|| (e2 <= e1 && e2 >= s1)) { // 2nd end contained in 1st
				// range
				// unify both
				s1 = Math.min(s1, s2);
				e1 = Math.max(e1, e2);
				s2 = -1;
				e2 = -1;
			}
		}
		// throw out, what is not visible
		if (s1 >= 0) {
			if (e1 < section.getStart() || s1 > section.getEnd())
				s1 = -1;
			else {
				if (s1 < section.getStart()) s1 = section.getStart();
				if (e1 > section.getEnd()) e1 = section.getEnd();
			}
		}
		if (s2 >= 0) {
			if (e2 < section.getStart() || s2 > section.getEnd())
				s2 = -1;
			else {
				if (s2 < section.getStart()) s2 = section.getStart();
				if (e2 > section.getEnd()) e2 = section.getEnd();
			}
		}
		// S*ystem.out.println("selectionChanged: draw "+s1+" -> "+e1+" and
		// "+s2+" -> "+e2+" samples");
		// finally, draw it !
		if (s1 >= 0) {
			paintSelectingPart(s1, e1);
		}
		if (s2 >= 0) {
			paintSelectingPart(s2, e2);
		}
	}

	/**
	 * scroll event of BomeTimer object.
	 */
	public void timerTick(GraphTimer timer) {
		if (selecting) selectingNewSel(selectingScrollX);
	}

	/**
	 * met a jour la selection
	 */
	public void setSelection(GraphSelection newSel) {
		sel.setSelection(newSel);
	}

	/**
	 * retourne la selection
	 */
	public GraphSelection getSelection() {
		return sel;
	}

	/**
	 * Returns the number of samples in the associated audio file.
	 */
	public long getSampleCount() {
		if (audioFile != null) {
			return audioFile.getEffectiveDurationSamples();
		}
		return 0;
	}

	/**
	 * Returns the number of channels.
	 */
	public int getChannelCount() {
		if (audioFile != null && audioFile.getFormat() != null) {
			if (showMono) {
				return 1;
			}
			return audioFile.getFormat().getChannels();
		}
		return 1;
	}

	/**
	 * sets new section values (with repaint)
	 */
	public void setSection(GraphSection newSection) {
		section.setSection(newSection);
	}

	/**
	 * Returns the section object.
	 */
	public GraphSection getSection() {
		return section;
	}

	public void setShowAnalogDots(boolean value) {
		if (value != showAnalogDots) {
			showAnalogDots = value;
			repaint();
		}
	}

	public void setSelColor(Color col) {
		selColor = col;
	}

	public Color getSelColor() {
		return selColor;
	}

	public void getEdgeColor(Color c) {
		edgeColor = c;
	}

	public Color getEdgeColor() {
		return edgeColor;
	}

	public void setEdges(int left, int right, int top, int bottom) {
		leftEdge = left;
		topEdge = top;
		rightEdge = right;
		bottomEdge = bottom;
	}

	public void setShowSelCursor(boolean value) {
		showSelCursor = value;
	}

	public boolean isShowingSelCursor() {
		return showSelCursor;
	}

	public void setCanSelect(boolean value) {
		canSelect = value;
	}

	public boolean getCanSelect() {
		return canSelect;
	}

	public void setCanChangeVerticalZoom(boolean value) {
		canChangeVerticalZoom = value;
	}

	public boolean getCanChangeVerticalZoom() {
		return canChangeVerticalZoom;
	}

	/**
	 * Scrolls the visible area by at least <tt>minimumPixel</tt> pixels. If
	 * <tt>minimumPixel</tt> is > 0, then it is scrolled to the right, i.e.
	 * the new section start is increased. Otherwise it will be scrolled to the
	 * left.
	 */
	public void scrollSection(int minimumPixel) {
		if (minimumPixel == 0) return;
		int diff = 0;
		int sgn = minimumPixel < 0 ? -1 : 1;
		if (minimumPixel < 0) minimumPixel = -minimumPixel;
		if (pv.samplesOnePixelF <= Math.abs(minimumPixel)) {
			diff = (int) Math.ceil(pv.samplesOnePixelF / minimumPixel);
		} else
			diff = (int) Math.ceil(minimumPixel * pv.samplesOnePixelF);
		int newStart = section.getStart() + sgn * diff;
		if (newStart + section.getLength() > getSampleCount())
			newStart = (int) (getSampleCount() - section.getLength());
		section.setSection(newStart, section.getLength());
	}

	/**
	 * Returns the number of samples to be scrolled while selecting. The return
	 * value is negative, if scrolling to left should be done, positive if
	 * scrolling to right, and zero, if no scrolling is appropriate. x is
	 * relative to the graphs origin.
	 */
	private int getSelectingScrollAmount(int x) {
		double diff = 0;
		int sgn = 0;
		if ((x - pv.graphWidth >= 4)
				&& (section.getEnd() < getSampleCount() - 1)) {
			diff = (x - pv.graphWidth - 3);
			sgn = 1;
		} else if ((x <= -5) && (section.getStart() > 0)) {
			diff = (-x - 4); // move to left
			sgn = -1;
		} else
			return 0;
		if (pv.samplesOnePixelF > 1) {
			diff = Math.exp((diff - 1) / 10);
			int newSectionStart = (int) Math.ceil((pv.truncSectionStartPixel + sgn
					* diff)
					* pv.samplesOnePixelF);
			return newSectionStart - section.getStart();
		}
		diff = Math.exp((diff - 1) / 20);
		return sgn * ((int) Math.ceil(diff));
	}

	// interface Section.Listener
	public void sectionChanged(GraphSection sec, GraphSection oldSec) {
		// S*ystem.out.println("sectionChanged. New start="+sec.getStart());
		pv.update();
		// special case: length stays the same, so I can scroll
		if (sec.getLength() == oldSec.getLength()) {
			int oldTruncSectionStartPixel = (int) (oldSec.getStart() * pv.pixelOneSampleF);
			int pixelDelta = pv.truncSectionStartPixel
					- oldTruncSectionStartPixel;
			scroll(pixelDelta, 0);
		} else
			repaint();
	}

	/**
	 * Calculate the new selection while selecting. x is the mouse coordinate
	 * relative to the graphs' origin.
	 */
	private void selectingNewSel(int x) {
		// S*ystem.out.println("SelectingNewSel("+x+")");
		int newStart, newEnd;
		// int sampleX=-1;
		int move = 0;
		int newSectionStart = 0;
		// if mouse is far right / far left, move the window
		move = getSelectingScrollAmount(x);
		if (move != 0) {
			newSectionStart = minMax(section.getStart() + move, 0,
					(int) (getSampleCount() - section.getLength()));
			move = newSectionStart - section.getStart(); // move in samples
		}

		if (move < 0) {
			// mouse moves to left
			if (selStartSample < newSectionStart) {
				newStart = selStartSample;
				newEnd = newSectionStart - 1;
			} else {
				newStart = newSectionStart;
				newEnd = selStartSample - 1;
			}
		} else if (move > 0) {
			int sampleX = newSectionStart + section.getLength() - 1; // extend
			// to
			// right
			if (selStartSample < sampleX) {
				newStart = selStartSample;
				newEnd = sampleX + 1;
			} else {
				newStart = sampleX;
				newEnd = selStartSample;
			}
		} else {
			int sampleX;
			if (pv.pixelOneSample >= 1)
				sampleX = (int) (toSamplesXF(x) + 0.5);
			else
				sampleX = toSamplesX(x);
			if (selStartSample < sampleX) {
				newStart = selStartSample;
				if (pv.pixelOneSample < 1)
					sampleX = toSamplesEndX(x);
				else
					sampleX--;
				newEnd = minMax(sampleX, section.getStart(), section.getEnd());
			} else {
				newStart = minMax(sampleX, section.getStart(), section.getEnd());
				newEnd = selStartSample - 1;
			}
		}
		int newLength = newEnd - newStart + 1;
		// check whether we hit one point->sel.onePoint !
		if ((pv.pixelOneSample < 1 && toPixelX(newStart) == toPixelX(newEnd))) {
			// || (pv.pixelOneSample>=1 && newLength<0.5*pv.samplesOnePixelF))
			// {
			// yes: make'em both the same point
			newStart = selStartSample;
			newLength = 0;
		}

		if (move != 0) {
			sel.setSelection(newStart, newLength);
			/*
			 * if (moveRedrawAdd>0) { //S*ystem.out.println("need to redraw from
			 * old sel "+moveRedrawAdd+" samples"); }
			 */
			// scroll(section.getStart()+move, moveRedrawAdd);
			section.setSection(section.getStart() + move, section.getLength());
		} else
			sel.setSelection(newStart, newLength);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioFileURL.Listener#audioFileDownloadEnd(com.mixblendr.audio.AudioFile)
	 */
	public void audioFileDownloadEnd(AudioFile source) {
		// remove ourself from the listener list
		if (source instanceof AudioFileURL) {
			((AudioFileURL) source).removeListener(this);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioFileURL.Listener#audioFileDownloadError(com.mixblendr.audio.AudioFile)
	 */
	public void audioFileDownloadError(AudioFile source) {
		// nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioFileURL.Listener#audioFileDownloadStart(com.mixblendr.audio.AudioFile)
	 */
	public void audioFileDownloadStart(AudioFile source) {
		// nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioFileURL.Listener#audioFileDownloadUpdate(com.mixblendr.audio.AudioFile)
	 */
	public void audioFileDownloadUpdate(AudioFile source) {
		audioDataChanged();
	}

	// //////////////////// Vertical zoom methods ///////////////////

	/* get top coordinate of changeVerticalZoomHandle */
	private int verticalZoomToPixel() {
		int graphH = getGraphHeight() * getChannelCount()
				- verticalZoomHandleHeight;
		int middleY = getGraphTop(0) + graphH / 2;
		if (currVerticalZoom == 1.0) return middleY;
		graphH -= 2 * verticalZoomHandleSnap;

		double normalizedRes; // in range -maxVerticalZoom...+maxVerticalZoom
		if (currVerticalZoom < 1)
			normalizedRes = -1.0 / currVerticalZoom + 1.0;
		else
			normalizedRes = currVerticalZoom - 1.0;
		// S*ystem.out.println("currRes="+currVerticalZoom+"
		// normalized="+normalizedRes);
		int diff = (int) Math.round(graphH * normalizedRes
				/ (2 * maxVerticalZoom));
		if (diff < 0)
			return Math.min(middleY - diff + verticalZoomHandleSnap,
					getGraphHeight() * getChannelCount()
							- verticalZoomHandleHeight);
		else if (diff == 0)
			return middleY;
		else
			return Math.max(middleY - diff - verticalZoomHandleSnap,
					getGraphTop(0));
	}

	// x and y are relative to origin of the entire component
	private double pixelToVerticalZoom(int x, int y) {
		if (changingVerticalZoom) {
			// initial value when going outside 50pixel boundary
			if (x < getGraphLeft() + getGraphWidth() - 50 || x > pv.width + 50)
				return changingVerticalZoomInitialValue;
		}
		if (y <= getGraphTop(0)) return 1.0 + maxVerticalZoom;
		int graphH = getGraphHeight() * getChannelCount()
				- verticalZoomHandleHeight;
		if (y >= getGraphTop(0) + graphH) return 1.0 / (maxVerticalZoom + 1.0);
		int middleY = getGraphTop(0) + graphH / 2;
		if (y >= middleY - verticalZoomHandleSnap
				&& y <= middleY + verticalZoomHandleSnap) return 1.0;
		graphH -= 2 * verticalZoomHandleSnap;
		int diff;
		if (y > middleY) {
			diff = y - verticalZoomHandleSnap - middleY;
			return 1.0 / (1.0 + (diff * maxVerticalZoom * 2 / graphH));
		}
		diff = middleY - y - verticalZoomHandleSnap;
		return 1.0 + (diff * maxVerticalZoom * 2 / graphH);

	}

	private boolean nearChangeVerticalZoomArea(int x, int y) {
		if (x < getGraphLeft() + getGraphWidth() || x >= pv.width)
			return false;
		int handleY = verticalZoomToPixel();
		// S*ystem.out.println("call to
		// verticalZoomToPixel("+currVerticalZoom+") = "+handleY);
		return y >= (handleY - 3)
				&& y < (handleY + verticalZoomHandleHeight + 3);
	}

	private void setVerticalZoom(double newVerticalZoom) {
		if (newVerticalZoom != currVerticalZoom) {
			currVerticalZoom = newVerticalZoom;
			pv.updateVerticalZoomVars();
			// getToolkit().sync();
			repaint(getGraphLeft(), getGraphTop(0), pv.width - getGraphLeft(),
					getGraphHeight() * getChannelCount());
		}
	}

	// x and y is relative to the entire component
	private void setVerticalZoomCursor(int x, int y, boolean shift) {
		if (canChangeVerticalZoom) {
			if (changingVerticalZoom
					|| (!shift && nearChangeVerticalZoomArea(x, y))) {
				if (!changeVerticalZoomCursorSet) {
					setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
					changeVerticalZoomCursorSet = true;
				}
			} else if (changeVerticalZoomCursorSet) {
				setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				changeVerticalZoomCursorSet = false;
			}
		}
	}

	// ////////////////////////////////////// mouse events
	// /////////////////////////////////////

	public void mouseClicked(MouseEvent e) {
		// nothing
	}

	public void mousePressed(MouseEvent ev) {
		selecting = false;
		changingVerticalZoom = false;
		if (!canPaintGraph() || getSampleCount() == 0) return;
		// S*ystem.out.println("mousePressed: ev="+ev);
		// sometimes mouseDown is called w/out a mouse event
		if (ev.isPopupTrigger()) return;
		// double-click selectionne la fenetre
		if (canSelect && ev.getClickCount() == 2) {
			sel.setSelection(section.getStart(), section.getLength());
		} else {
			int x = ev.getX() - getGraphLeft();
			if (canSelect && ev.isShiftDown()) {
				int s = -1;
				if (x <= 0)
					s = section.getStart() - 1;
				else if (x >= getGraphWidth())
					s = section.getEnd() + 1;
				else {
					if (pv.pixelOneSample > 1)
						s = minMax((int) (toSamplesXF(x) + 0.5),
								section.getStart() - 1, section.getEnd() + 1);
					else
						s = minMax(toSamplesX(x), section.getStart() - 1,
								section.getEnd() + 1);
				}
				int newStart, newEnd;
				if (s > sel.getStart() + (sel.getLength() / 2)) {
					// extend selection to right side, keeping old start point
					newStart = sel.getStart();
					newEnd = s - 1;
				} else {
					// extend selection to left
					newStart = s;
					newEnd = sel.getEnd();
				}
				newStart = minMax(newStart, section.getStart(),
						section.getEnd());
				newEnd = minMax(newEnd, section.getStart(), section.getEnd());
				sel.setSelection(newStart, newEnd - newStart + 1);
			} else if (canChangeVerticalZoom
					&& nearChangeVerticalZoomArea(ev.getX(), ev.getY())) {
				changingVerticalZoomInitialValue = currVerticalZoom;
				changingVerticalZoomDelta = ev.getY() - verticalZoomToPixel();
				changingVerticalZoom = true;
			} else if (canSelect) {
				selecting = true;
				selectingScrollX = x;
				if (pv.pixelOneSample > 1)
					selStartSample = minMax(
							(int) (toSamplesXF(selectingScrollX) + 0.5),
							section.getStart() - 1, section.getEnd() + 1);
				else
					selStartSample = minMax(toSamplesX(selectingScrollX),
							section.getStart() - 1, section.getEnd() + 1);
				// S*ystem.out.println("Clicked x="+x+" == sample
				// "+selStartSample+" ==? pixel "+toPixelX(selStartSample));
				selectingNewSel(selectingScrollX);
				if (section.getLength() < getSampleCount()) {
					if (selScrollTimer == null) {
						selScrollTimer = new GraphTimer(0, 60, true);
						selScrollTimer.addListener(this);
					}
				} else if (selScrollTimer != null) {
					selScrollTimer.kill();
					selScrollTimer = null;
				}
			}
		}
	}

	/**
	 * end of selecting
	 */
	public void mouseReleased(MouseEvent e) {
		if (selScrollTimer != null) {
			selScrollTimer.kill();
			selScrollTimer = null;
		}
		int x = e.getX() - getGraphLeft();
		if (selecting && getSelectingScrollAmount(x) == 0) selectingNewSel(x);
		selecting = false; // fin de selectionner
		if (changingVerticalZoom) {
			// S*ystem.out.println("call to pixelToVerticalZoom("+e.getY()+") =
			// "+pixelToVerticalZoom(e.getY()));
			setVerticalZoom(pixelToVerticalZoom(e.getX(), e.getY()
					- changingVerticalZoomDelta));
			changingVerticalZoom = false;
		}
		setVerticalZoomCursor(e.getX(), e.getY(), e.isShiftDown());
	}

	public void mouseEntered(MouseEvent e) {
		setVerticalZoomCursor(e.getX(), e.getY(), e.isShiftDown());
	}

	public void mouseExited(MouseEvent e) {
		setVerticalZoomCursor(-100, -100, e.isShiftDown());
	}

	/**
	 * while selecting
	 */
	public void mouseDragged(MouseEvent ev) {
		if (selecting) {
			selectingScrollX = ev.getX() - getGraphLeft();
			if (getSelectingScrollAmount(selectingScrollX) == 0) {
				if (selScrollTimer != null) selScrollTimer.disable();
				selectingNewSel(selectingScrollX);
			} else if (selScrollTimer != null && !selScrollTimer.isEnabled()) {
				selectingNewSel(selectingScrollX);
				selScrollTimer.enable();
			}
		} else if (changingVerticalZoom) {
			// S*ystem.out.println("call to pixelToVerticalZoom("+ev.getY()+") =
			// "+pixelToVerticalZoom(ev.getY()));
			setVerticalZoom(pixelToVerticalZoom(ev.getX(), ev.getY()
					- changingVerticalZoomDelta));
		}
	}

	public void mouseMoved(MouseEvent e) {
		setVerticalZoomCursor(e.getX(), e.getY(), e.isShiftDown());
	}

	class CacheElement {
		public float min;
		public float max;
	}

	/**
	 * Variables for speed reasons. Most performance-sensitive functions are
	 * doubled here with pre calculated values.
	 */
	class PerformanceVars {
		boolean canPaintGraph;
		int graphX, graphY, graphWidth, graphHeight, allGraphsHeight;
		int width, height;
		int pixelOneSample;
		int samplesOnePixel;
		double samplesOnePixelF, pixelOneSampleF;
		double samplesOnePixelD;
		int truncSectionStartPixel;
		double toPixelYHelp1;
		double toPixelYHelp2;

		@SuppressWarnings("cast")
		public void update() {
			width = getWidth();
			height = getHeight();
			if (DEBUG && false) {
				debug("graph " + ID + ": x=" + getX() + " width=" + width
						+ " height=" + height);
			}
			canPaintGraph = (audioFile != null) && (width > 0) && (height > 0)
					&& (getChannelCount() > 0);
			if (canPaintGraph) {
				graphX = leftEdge;
				graphY = topEdge;
				graphWidth = width - leftEdge - rightEdge;
				graphHeight = (height - topEdge - bottomEdge)
						/ getChannelCount();
				canPaintGraph = graphWidth > 0 && graphHeight > 0;
			}
			if (canPaintGraph) {
				allGraphsHeight = getChannelCount() * graphHeight;
				pixelOneSampleF = (section.getLength() <= 0) ? 1
						: (((double) graphWidth) / section.getLength());
				pixelOneSample = (int) pixelOneSampleF;
				samplesOnePixelF = ((double) section.getLength()) / graphWidth;
				samplesOnePixel = (int) samplesOnePixel;
				truncSectionStartPixel = (int) (section.getStart() / samplesOnePixelF);
			} else {
				graphX = 0;
				graphY = 0;
				graphWidth = 0;
				graphHeight = 0;
				allGraphsHeight = 0;
				pixelOneSample = 1;
				pixelOneSampleF = 1.0;
				samplesOnePixel = 1;
				samplesOnePixelF = 1.0;
				truncSectionStartPixel = 0;
			}
			updateVerticalZoomVars();
		}

		public void updateVerticalZoomVars() {
			if (canPaintGraph) {
				toPixelYHelp1 = minSampleValue / currVerticalZoom;
				toPixelYHelp2 = graphHeight
						/ Math.abs(maxSampleValue - minSampleValue)
						* currVerticalZoom;
			} else {
				toPixelYHelp1 = 0.0;
				toPixelYHelp2 = 0.0;
			}
		}

		public void updateSelectionVars() {
			// nothing
		}

	}

}
