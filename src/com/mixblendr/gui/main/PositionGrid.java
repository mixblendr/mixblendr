/**
 *
 */
package com.mixblendr.gui.main;

import static com.mixblendr.util.Debug.debug;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.mixblendr.audio.AudioState;
import com.mixblendr.skin.ControlDelegate;
import com.mixblendr.skin.GUIBuilder;
import com.mixblendr.skin.MPanel;
import com.mixblendr.skin.SkinUtils;
import com.mixblendr.util.Utils;

/**
 * The class responsible for painting the position grid. It's implemented as a
 * MPanel.PaintListener.
 * 
 * @author Florian Bomers
 */
class PositionGrid implements MPanel.PaintListener, ChangeListener,
		AudioState.StateListener, GraphScale.Listener, MouseListener,
		MouseMotionListener {
	private static final Color BAR_COLOR = new Color(0, 0, 0);
	private static final Color BEAT_COLOR = new Color(160, 160, 160);
	private static final Color LOOP_COLOR = new Color(230, 230, 230);
	private Globals globals;
	private AudioState state;
	private GraphScale scale;
	private MPanel panel;
	private ControlDelegate posKnob;
	private long playPosition = 0;

	/**
	 * @param globals
	 */
	PositionGrid(Globals globals, GUIBuilder builder) throws Exception {
		super();
		setGlobals(globals);
		panel = (MPanel) builder.getControlExc("panel.positiongrid");
		posKnob = builder.getDelegateExc("knob.position");
		panel.setPaintListener(this);
		panel.addMouseListener(this);
		panel.addMouseMotionListener(this);
		globals.getAllRegionsViewPort().addChangeListener(this);
		SkinUtils.setFont(panel, panel.getDelegate(), 8);
	}

	/**
	 * @param globals
	 */
	void setGlobals(Globals globals) {
		if (globals == this.globals) return;
		if (this.scale != null) {
			scale.removeListener(this);
		}
		if (state != null) {
			state.removeStateListener(this);
		}
		if (globals != null) {
			this.globals = globals;
			this.state = globals.getState();
			this.scale = globals.getScale();
			globals.getScale().addListener(this);
			state.addStateListener(this);
		} else {
			this.globals = null;
			this.state = null;
			this.scale = null;
		}
		update(true);
	}

	/**
	 * The last play position set
	 * 
	 * @return the playPosition
	 */
	public long getPlayPosition() {
		return playPosition;
	}

	/**
	 * Cache the current play position so that it is always synchronized with
	 * the region's play cursor.
	 * 
	 * @param playPosition the playPosition to set
	 */
	public void setPlayPosition(long playPosition) {
		this.playPosition = playPosition;
	}

	/**
	 * recalculate the position grid's state variables depending on scale and
	 * tempo
	 */
	public void update(boolean updateAllRegions) {
		if (panel != null) {
			panel.repaint();
		}
		if (updateAllRegions) {
			globals.getAllRegionsViewPort().repaint();
		}
	}

	/**
	 * repaint the area of old and new play position. Will also repaint the
	 * corresponding are of the allRegions area. If the sample position has not
	 * changed, repaintArea.width is set to 0 and nothing is repainted.
	 * 
	 * @param repaintArea [out] fills in x and width with the repaint area
	 */
	void repaintPlayPosition(long newSamplePosition, Rectangle repaintArea) {
		if (newSamplePosition == playPosition) {
			if (repaintArea != null) {
				repaintArea.width = 0;
			}
			return;
		}
		int xOffset = globals.getAllRegionsScrollX();
		int pixel1 = scale.sample2pixel(playPosition) - 1;
		int pixel2 = scale.sample2pixel(newSamplePosition) - 1;
		if (pixel1 > pixel2) {
			int h = pixel1;
			pixel1 = pixel2;
			pixel2 = h;
		}
		pixel2 += 2;
		if (repaintArea != null) {
			repaintArea.x = pixel1;
			repaintArea.width = pixel2 - pixel1 + 1;
		}
		// paint grid knob
		pixel1 -= xOffset;
		pixel2 -= xOffset;
		int knobWidth = posKnob.getCtrlDef().getTargetWidth();
		pixel2 += knobWidth;
		if (pixel2 >= panel.getWidth()) {
			pixel2 = panel.getWidth();
		}
		if (pixel1 < 0) {
			pixel1 = 0;
		}
		if (pixel1 < panel.getWidth() && pixel2 >= 0) {
			panel.repaint(pixel1, 0, pixel2 - pixel1 + 1, panel.getHeight());
		}

		setPlayPosition(newSamplePosition);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.skin.MPanel.PaintListener#panelBeforePaint(com.mixblendr.skin.MPanel,
	 *      java.awt.Graphics, java.awt.Rectangle)
	 */
	public void panelBeforePaint(MPanel aPanel, Graphics g, Rectangle clip) {
		// nothing to do
	}

	/**
	 * calculate the sample position from the given pixel of the position grid
	 * panel.
	 * 
	 * @param relative if true, the pixel is the relative position to this
	 *            panel, otherwise it's an absolute pixel as if the position
	 *            grid's zero pixel started at sample 0.
	 */
	private long getSamplePosition(int pixel, boolean relative) {
		if (relative) {
			pixel += globals.getAllRegionsScrollX();
		}
		return scale.pixel2sample(pixel);
	}

	/**
	 * Paint the grid units, bar numbers, and the position knob if isPosGrid is
	 * true. Otherwise, only paints the bar/beat lines.
	 * 
	 * @param aPanel the panel to paint on
	 * @param g the graphics context to use for painting
	 * @param clip the graphics context's clip area, as a convenience
	 * @param isPosGrid if true, paint the grid markers, otherwise just bar/beat
	 *            lines
	 */
	void paintGrid(JComponent aPanel, Graphics g, Rectangle clip,
			boolean isPosGrid) {
		if (globals == null) return;
		if (scale == null || state == null) return;
		int xOffset;
		if (!isPosGrid) {
			// allRegions panel is in the viewport, no need to adapt
			xOffset = 0;
			// Debug.debug("lines: view position: "+p.x+" clip.x="+clip.x);
		} else {
			xOffset = globals.getAllRegionsScrollX();
			// Debug.debug("grid : view position: "+p.x);
			g.setFont(aPanel.getFont());
		}
		int startPixel = xOffset + clip.x - 1;
		if (startPixel < 0) {
			startPixel = 0;
		}
		long startSample = scale.pixel2sample(startPixel);
		int beat;
		int beatsPerMeasure;
		boolean isBeats = state.isTimeDisplayInBeats();
		if (isBeats) {
			beat = (int) state.sample2beat(startSample);
			beatsPerMeasure = state.getBeatsPerMeasure();
		} else {
			// if display in min:seconds, a beat corresponds to one second, and
			// a measure to 10 seconds
			beat = (int) state.sample2seconds(startSample);
			beatsPerMeasure = 10;
		}
		int height = aPanel.getHeight();
		int barHeight = height / 2;
		int beatHeight = height / 4;
		if (!isPosGrid) {
			barHeight = height;
			beatHeight = height;
		}
		int endPixel = clip.x + clip.width + xOffset;

		// adjust the painted measure lines so that there is at least 35 pixels
		// between measures.
		// must calc beatIncFactor directly, otherwise there'll be issues that
		// "measurePixelWidth" approaches 0.
		double scaleFactor = scale.getScaleFactor();
		int beatIncFactor;
		if (isBeats) {
			beatIncFactor = (int) (beatsPerMeasure * 9.0 / (scaleFactor * state.beat2sampleD(beatsPerMeasure * 1.0)));
		} else {
			beatIncFactor = (int) (beatsPerMeasure * 9.0 / (scaleFactor * state.seconds2sampleD(beatsPerMeasure * 1.0)));
		}
		int beatInc;
		if (beatIncFactor < 1) {
			beatInc = 1;
		} else {
			beatInc = beatsPerMeasure * beatIncFactor;
			// adjust beat to fall on a line
			if (beat > 0) {
				beat -= (beat % beatInc);
			}
		}

		// paint loop, if loop is enabled
		if (isPosGrid && state.isLoopEnabled()) {
			if (state.getLoopDurationSamples() > 0) {

				// draw loop region
				int loopHeight = height / 3;
				int loopX1 = scale.sample2pixel(state.getLoopStartSamples());
				int loopX2 = scale.sample2pixel(state.getLoopEndSamples());
				if (loopX1 < startPixel) loopX1 = startPixel;
				if (loopX2 > endPixel) loopX2 = endPixel;
				if (loopX2 - loopX1 > 0) {
					g.setColor(LOOP_COLOR);
					g.fillRect(loopX1 - xOffset, height - loopHeight, loopX2
							- loopX1, loopHeight - 1);
				}
			}
		}

		while (true) {
			int pixel;
			if (isBeats) {
				pixel = (int) (scale.sample2pixel(state.beat2sampleD(beat)) + 0.5);
			} else {
				pixel = (int) (scale.sample2pixel(state.seconds2sampleD(beat)) + 0.5);
			}
			if (pixel > endPixel) {
				break;
			}
			// adjust to viewport for position grid
			pixel -= xOffset;
			if ((beat % beatsPerMeasure) == 0) {
				g.setColor(BAR_COLOR);
				g.drawLine(pixel, height - barHeight, pixel, height);
				if (isPosGrid) {
					String s;
					if (isBeats) {
						s = Integer.toString((beat / beatsPerMeasure) + 1);
					} else {
						s = Integer.toString(beat / 60) + ":"
								+ Integer.toString(beat % 60);
					}
					g.drawString(s, pixel + 2, height - beatHeight);
				}
			} else {
				g.setColor(BEAT_COLOR);
				g.drawLine(pixel, height - beatHeight, pixel, height);
			}
			beat += beatInc;
		}
		if (isPosGrid) {
			// draw knob
			int pixel = scale.sample2pixel(playPosition);
			if (pixel + posKnob.getCtrlDef().getTargetWidth() >= startPixel
					&& pixel <= endPixel) {
				pixel -= xOffset;
				posKnob.paint(g, pixel, height
						- posKnob.getCtrlDef().getTargetHeight(), 0.7f);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.skin.MPanel.PaintListener#panelAfterPaint(com.mixblendr.skin.MPanel,
	 *      java.awt.Graphics, java.awt.Rectangle)
	 */
	public void panelAfterPaint(MPanel aPanel, Graphics g, Rectangle clip) {
		paintGrid(aPanel, g, clip, true);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.swing.event.ChangeListener#stateChanged(javax.swing.event.ChangeEvent)
	 */
	public void stateChanged(ChangeEvent e) {
		update(false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioState.StateListener#displayModeChanged()
	 */
	public void displayModeChanged() {
		update(true);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioState.StateListener#tempoChanged()
	 */
	public void tempoChanged() {
		update(true);
	}

	/**
	 * Upon loop changes, repaint the area covering the old loop and the new
	 * loop region
	 * 
	 * @see com.mixblendr.audio.AudioState.StateListener#loopChanged(long, long,
	 *      long, long)
	 */
	public void loopChanged(long oldStart, long oldEnd, long newStart,
			long newEnd) {
		if (scale == null || panel == null) return;
		int X1 = scale.sample2pixel(oldStart);
		int X2 = scale.sample2pixel(oldEnd);
		int X3 = scale.sample2pixel(newStart);
		int X4 = scale.sample2pixel(newEnd);
		int min = convertToRelative(Utils.min(X1, X2, X3, X4));
		int max = convertToRelative(Utils.max(X1, X2, X3, X4));
		if (Globals.DEBUG_DRAG_SCROLL) {
			debug("Loop changed: x1=" + X1 + " x2=" + X2 + " x3=" + X3 + " x4="
					+ X4 + " -> min=" + min + " max=" + max);
		}
		if (min < 0) {
			min = 0;
		}
		if (max > panel.getWidth()) {
			max = panel.getWidth();
		}
		panel.repaint(min, 0, max - min + 1, panel.getHeight());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.gui.main.GraphScale.Listener#scaleChanged(com.mixblendr.gui.main.GraphScale,
	 *      double)
	 */
	public void scaleChanged(GraphScale aScale, double oldScaleFactor) {
		update(true);
	}

	/**
	 * given the mouse's x coordinate, set the current playback position
	 * 
	 * @param relative if true, the pixel is the relative position to this
	 *            panel, otherwise it's an absolute pixel as if the position
	 *            grid's zero pixel started at sample 0.
	 */
	private void setPlayPosFromMouse(int x, boolean relative) {
		if (globals == null || globals.getPlayer() == null) return;
		globals.getPlayer().setPositionSamples(getSamplePosition(x, relative));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
	 */
	public void mouseClicked(MouseEvent e) {
		if (globals != null) {
			if (e.getClickCount() == 2) {
				setPlayPosFromMouse(e.getX(), true);
				globals.togglePlayback();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
	 */
	public void mouseEntered(MouseEvent e) {
		updateCursor(e);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
	 */
	public void mouseExited(MouseEvent e) {
		// nothing to do
	}

	private int mouseDownOffset;
	private int currCursor = 0;
	private int currDragMode = 0;
	private long dragLoopStart, dragLoopEnd;

	/**
	 * how many pixels to left and right of loop boundary will the cursor change
	 * to a change-loop cursor
	 */
	private static final int LOOP_HANDLE_SNAP = 5;

	private static final int HIT_NONE = 0;
	private static final int HIT_ARROW_HEAD = 1;
	private static final int HIT_LOOP_START = 2;
	private static final int HIT_LOOP_END = 3;
	private static final int HIT_LOOP_CREATE = 4;
	private static final int HIT_LOOP_MOVE = 5;

	private void changeCursor(int newCursor) {
		if (currCursor != newCursor) {
			panel.setCursor(Cursor.getPredefinedCursor(newCursor));
			currCursor = newCursor;
		}
	}

	/**
	 * Given the mouse x coordinates, return one of HIT_ARROW_HEAD,
	 * HIT_LOOP_START, HIT_LOOP_END, HIT_NONE.
	 * 
	 * @param x the x position to test
	 * @param y the y position to test
	 * @param relativeX if true, the x position is relative to this component
	 * @return one of the HIT_* values.
	 */
	private int getHitInfo(int x, int y, boolean relativeX) {
		if (globals == null || state == null || panel == null || scale == null) {
			return HIT_NONE;
		}
		if (relativeX) {
			// adapt x to be absolute
			x = convertToAbsolute(x);
		}
		int height = panel.getHeight();
		int knobX1 = scale.sample2pixel(playPosition);
		int knobX2 = knobX1 + posKnob.getCtrlDef().getTargetWidth();
		if (x >= knobX1 && x < knobX2) {
			return HIT_ARROW_HEAD;
		}
		// check for loop
		if (state.isLoopEnabled()) {
			if (y >= (2 * height / 3) && state.getLoopDurationSamples() > 0) {
				int loopX1 = scale.sample2pixel(state.getLoopStartSamples());
				if (Math.abs(x - loopX1) <= LOOP_HANDLE_SNAP) {
					return HIT_LOOP_START;
				}
				int loopX2 = scale.sample2pixel(state.getLoopEndSamples());
				if (Math.abs(x - loopX2) <= LOOP_HANDLE_SNAP) {
					return HIT_LOOP_END;
				}
				if (loopX1 < x && x < loopX2) {
					return HIT_LOOP_MOVE;
				}
			}
			// eventually, if in loop area, can create a loop by dragging
			if (y >= (2 * height / 3)) {
				return HIT_LOOP_CREATE;
			}
		}
		return HIT_NONE;
	}

	/**
	 * @param x the absolute x position
	 */
	private void dragChangeLoopSize(int x) {
		long sample = globals.getSnapToBeats().getSnappedSample(
				scale.pixel2sample(x));
		if (sample < 0) {
			sample = 0;
		}
		if (currDragMode == HIT_LOOP_CREATE) {
			// for creating a loop, do it here
			dragLoopStart = sample;
			dragLoopEnd = sample;
			// patch the drag mode
			currDragMode = HIT_LOOP_END;
		}
		long start = dragLoopStart;
		long end = dragLoopEnd;
		if (currDragMode == HIT_LOOP_END) {
			end = sample;
		} else {
			start = sample;
		}
		long duration = end - start;
		if (duration < 0) {
			start = end;
			duration = -duration;
		}
		// the following call will synchronously repaint the loop area
		// by way of the state listener
		globals.getPlayer().setLoopSamples(start, duration);
	}

	/**
	 * convert the relative position (e.g. mouse position) to an absolute
	 * position that can be used for scale.sample2pixel.
	 */
	private int convertToAbsolute(int x) {
		return x + globals.getAllRegionsScrollX();
	}

	/**
	 * convert the absolute position to a relative position that can be used for
	 * painting on this component
	 */
	private int convertToRelative(int x) {
		return x - globals.getAllRegionsScrollX();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
	 */
	public void mousePressed(MouseEvent e) {
		if (state == null) return;
		if (globals != null) {
			globals.setDraggingMouse(true);
		}
		int x = convertToAbsolute(e.getX());
		currDragMode = getHitInfo(x, e.getY(), false);
		if (currDragMode != HIT_NONE) {
			dragLoopStart = state.getLoopStartSamples();
			dragLoopEnd = state.getLoopEndSamples();
			if (currDragMode == HIT_ARROW_HEAD) {
				mouseDownOffset = x - scale.sample2pixel(playPosition);
			} else if (currDragMode == HIT_LOOP_MOVE) {
				mouseDownOffset = x - scale.sample2pixel(dragLoopStart);
			} else {
				mouseDownOffset = 0;
			}
			mouseDragged(e);
			e.consume();
		} else {
			setPlayPosFromMouse(x, false);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
	 */
	public void mouseReleased(MouseEvent e) {
		if (currDragMode != HIT_NONE) {
			mouseDragged(e);
			currDragMode = HIT_NONE;
			e.consume();
		}
		// re-adapt the cursor
		updateCursor(e);
		if (globals != null) {
			globals.setDraggingMouse(false);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent)
	 */
	public void mouseDragged(MouseEvent e) {
		int x = e.getX();
		int movePixels = globals.getAutoScrollPixels(x, false);
		movePixels = globals.scroll(movePixels);
		if (movePixels != 0) {
			x += movePixels;
			if (Globals.DEBUG_DRAG_SCROLL) {
				debug("    moved x to " + x);
			}
		}
		if (currDragMode != HIT_NONE) {
			x = convertToAbsolute(x - mouseDownOffset);
			switch (currDragMode) {
			case HIT_ARROW_HEAD:
				setPlayPosFromMouse(x, false);
				break;
			case HIT_LOOP_CREATE:
				// fall through
			case HIT_LOOP_START:
				// fall through
			case HIT_LOOP_END:
				dragChangeLoopSize(x);
				break;
			case HIT_LOOP_MOVE:
				long sample = globals.getSnapToBeats().getSnappedSample(
						scale.pixel2sample(x));
				if (sample < 0) {
					sample = 0;
				}
				globals.getPlayer().setLoopSamples(sample,
						dragLoopEnd - dragLoopStart);
			}
		}
	}

	/** select the cursor, depending on the given mouse position */
	private void updateCursor(MouseEvent e) {
		if (!SwingUtilities.isLeftMouseButton(e)) {
			updateCursor(e.getX(), e.getY());
		}
	}

	/** select the cursor, depending on the given mouse position */
	private void updateCursor(int x, int y) {
		switch (getHitInfo(x, y, true)) {
		case HIT_ARROW_HEAD:
			changeCursor(Cursor.HAND_CURSOR);
			break;
		case HIT_LOOP_CREATE:
			changeCursor(Cursor.TEXT_CURSOR);
			break;
		case HIT_LOOP_START:
			changeCursor(Cursor.W_RESIZE_CURSOR);
			break;
		case HIT_LOOP_END:
			changeCursor(Cursor.E_RESIZE_CURSOR);
			break;
		case HIT_LOOP_MOVE:
			changeCursor(Cursor.HAND_CURSOR);
			break;
		default:
			changeCursor(Cursor.DEFAULT_CURSOR);
			break;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseMotionListener#mouseMoved(java.awt.event.MouseEvent)
	 */
	public void mouseMoved(MouseEvent e) {
		updateCursor(e);
	}

}
