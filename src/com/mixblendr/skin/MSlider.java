/**
 *
 */
package com.mixblendr.skin;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ComponentUI;

import com.mixblendr.util.Utils;

/**
 * A horizontal slider with trough, knob, optional progress bar.
 * 
 * @author Florian Bomers
 */
public class MSlider extends JComponent implements MControl, MouseListener,
		MouseMotionListener {

	/** the delegate handling the painting of this component */
	protected ControlDelegate delegate;

	private boolean snapToCenter = false;

	private int snapPixels = 6;

	/** value: 0..1 */
	private double value;

	/** progress (as partial overlay of the trough): 0..1 */
	private double progress = 0;

	/** the knob control */
	private ControlDelegate knob;

	/** the current position of the knob */
	private Rectangle knobBounds;

	/** the listeners */
	private List<Listener> listeners;

	/**
	 * Create a new MSlider and initialize the delegate
	 */
	private MSlider(ControlDelegate delegate) {
		super();
		this.delegate = delegate;
		delegate.setOwner(this);
		setBounds(delegate.getCtrlDef().targetBounds.getRectangle());
		setOpaque(true);
		// register listeners
		addMouseListener(this);
		addMouseMotionListener(this);
	}

	/**
	 * Create a new MSlider from the given delegate
	 * 
	 * @param builder the builder instance to get the knob from
	 * @throws Exception if no knob is assigned, or if the named knob control
	 *             does not exist
	 */
	public MSlider(GUIBuilder builder, ControlDelegate delegate)
			throws Exception {
		this(delegate);
		// check the knob
		String knobName = delegate.getCtrlDef().knob;
		if (!Utils.isDefined(knobName)) {
			throw new Exception("knob is not specified for slider "
					+ delegate.getCtrlDef().fullName);
		}
		String fullKnobName = GUIBuilder.CONTROL_NAME_KNOB + "." + knobName;
		// need to clone the knob, because it can be shared
		init((ControlDelegate) builder.getDelegateExc(fullKnobName).clone());
	}

	/**
	 * Create a new MSlider, creating a copy of the given slider
	 * 
	 * @param slider the slider to copy
	 */
	public MSlider(MSlider slider) {
		this(slider.delegate);
		this.snapPixels = slider.snapPixels;
		this.snapToCenter = slider.snapToCenter;
		init((ControlDelegate) slider.knob.clone());
	}

	/**
	 * Init the knob
	 * 
	 * @throws Exception
	 */
	private void init(ControlDelegate aKnob) {
		this.knob = aKnob;
		knobBounds = new Rectangle(0, 0, aKnob.getCtrlDef().targetBounds.w,
				aKnob.getCtrlDef().targetBounds.h);
		// force recalculation
		value = 0.1;
		setValue(0);
	}

	@Override
	public void paint(Graphics g) {
		delegate.paint(g);
		if (delegate.hasImage(ControlState.PROGRESS) && progress > 0.0) {
			Rect r = delegate.getCtrlDef().targetBounds;
			delegate.paint(g, ControlState.PROGRESS, 0, 0,
					(int) (r.w * progress), r.h, 1.0f);
		}
		knob.paint(g, knobBounds.x, 0, (knob.isHovering()) ? 1.0f : 0.7f);
	}

	@Override
	public Dimension getPreferredSize() {
		return delegate.getPreferredSize();
	}

	@Override
	public Dimension getMinimumSize() {
		return delegate.getMinimumSize();
	}

	@Override
	public Dimension getMaximumSize() {
		return delegate.getMaximumSize();
	}

	/**
	 * @return the progress [0..1]
	 */
	public double getProgress() {
		return progress;
	}

	/**
	 * @param progress the progress to set [0..1]
	 */
	public void setProgress(double progress) {
		this.progress = progress;
		repaint();
	}

	/**
	 * @return the snap area in pixels
	 */
	public int getSnapPixels() {
		return snapPixels;
	}

	/**
	 * @param snapPixels the snap area in pixels to set
	 */
	public void setSnapPixels(int snapPixels) {
		this.snapPixels = snapPixels;
	}

	/**
	 * @return the value [0..1]
	 */
	public double getValue() {
		return value;
	}

	/**
	 * @param value the value to set [0..1]
	 */
	public void setValue(double value) {
		setValue(value, false);
	}

	/**
	 * @param value the value to set [0..1]
	 * @param interactive if true, the change is resulting from a mouse drag
	 *            operation
	 */
	void setValue(double value, boolean interactive) {
		if (value < 0.0) {
			value = 0.0;
		} else if (value > 1.0) {
			value = 1.0;
		}
		if (this.value != value) {
			this.value = value;
			// adjust knob bounds
			knobBounds.x = getPixelFromValue(value);
			if (listeners != null) {
				for (Listener l : listeners) {
					l.sliderValueChanged(this);
					if (interactive) {
						l.sliderValueTracked(this);
					}
				}
			}
			repaint();
		}
	}

	/**
	 * @return the snapToCenter
	 */
	public boolean isSnapToCenter() {
		return snapToCenter;
	}

	/**
	 * @param snapToCenter the snapToCenter to set
	 */
	public void setSnapToCenter(boolean snapToCenter) {
		this.snapToCenter = snapToCenter;
		repaint();
	}

	/**
	 * @return the dragging
	 */
	public boolean isDragging() {
		return dragging;
	}

	/**
	 * add a listener to this class. It will receive change information from now
	 * on
	 */
	public void addListener(Listener l) {
		if (listeners == null) {
			listeners = new ArrayList<Listener>();
		}
		listeners.add(l);
	}

	/** remove the listener from the list of listeners. */
	public void removeListener(Listener l) {
		if (listeners != null) {
			listeners.remove(l);
		}
	}

	private void setDown(boolean down) {
		if (isDown() != down) {
			delegate.setDown(down);
			knob.setDown(down);
			repaint();
		}
	}

	/** returns true if the user is currently clicking the slider */
	public boolean isDown() {
		return delegate.isDown();
	}

	private void setHovering(boolean hover) {
		if (hover != isHovering()) {
			delegate.setHovering(hover);
			knob.setHovering(hover);
			repaint();
		}
	}

	/** returns true if the mouse is currently hovering on the slider */
	public boolean isHovering() {
		return delegate.isHovering();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
	 */
	public void mouseClicked(MouseEvent e) {
		// nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
	 */
	public void mouseEntered(MouseEvent e) {
		if (!SwingUtilities.isLeftMouseButton(e) && !dragging) {
			setHovering(true);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
	 */
	public void mouseExited(MouseEvent e) {
		if (!dragging) {
			setHovering(false);
		}
	}

	private int mouseDownX, mouseDownY;
	private boolean dragging;

	/**
	 * implement slow motion, i.e. if this is 0.5, then moving the mouse by 2
	 * pixels will move the knob only by one pixel
	 */
	private double dragSlowMotion = 0.7;

	private double mouseDownValue;

	/** calculate the left/top pixel of the knob rect from the given value */
	protected int getPixelFromValue(double val) {
		int pixelWidth = getWidth() - knobBounds.width;
		return (int) ((pixelWidth * value) + 0.5);
	}

	/** calculate the value from the left/top pixel of the knob rect */
	protected double getValueFromPixel(int pixel) {
		int pixelWidth = getWidth() - knobBounds.width;
		if (pixelWidth <= 0) {
			return 0;
		}
		return ((double) pixel) / pixelWidth;
	}

	/**
	 * Calculate the new position, based on the dragged mouse position, relative
	 * to the mouse start position.
	 * 
	 * @param x
	 * @param y
	 */
	private void handleDragging(int x, int y) {
		int pixelWidth = getWidth() - knobBounds.width;
		double pixelFactor = pixelWidth / dragSlowMotion;
		int pixelOffset = x - mouseDownX;
		if (snapToCenter) {
			pixelFactor = pixelWidth / dragSlowMotion;
			if (mouseDownValue >= 0.5) {
				// calculate the negative mouse pixel offset where it snaps when
				// moving to the left
				int pixelOffsetSnapMax = (int) ((0.5 - mouseDownValue) * pixelFactor);
				int pixelOffsetSnapMin = pixelOffsetSnapMax - (2 * snapPixels);
				if (pixelOffset <= pixelOffsetSnapMax
						&& pixelOffset >= pixelOffsetSnapMin) {
					setValue(0.5, true);
					return;
				}
				// remove the snap area from the offset
				if (pixelOffset <= pixelOffsetSnapMin) {
					pixelOffset += 2 * snapPixels;
				}
			} else {
				// calculate the positive mouse pixel offset where it snaps when
				// moving to the right
				int pixelOffsetSnapMin = (int) ((0.5 - mouseDownValue) * pixelFactor);
				int pixelOffsetSnapMax = pixelOffsetSnapMin + (2 * snapPixels);
				if (pixelOffset <= pixelOffsetSnapMax
						&& pixelOffset >= pixelOffsetSnapMin) {
					setValue(0.5, true);
					return;
				}
				// remove the snap area from the offset
				if (pixelOffset >= pixelOffsetSnapMax) {
					pixelOffset -= 2 * snapPixels;
				}
			}
		}
		setValue(mouseDownValue + (pixelOffset / pixelFactor), true);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
	 */
	public void mousePressed(MouseEvent e) {
		if (!isEnabled()) {
			return;
		}

		mouseDownX = e.getX();
		mouseDownY = e.getY();
		mouseDownValue = value;

		// do not activate focus for now (will disable global key listener)
		// if (isRequestFocusEnabled()) {
		// requestFocus();
		// }
		setDown(true);

		// send tracking start event
		if (listeners != null) {
			for (Listener l : listeners) {
				l.sliderTrackingStart(this);
			}
		}

		if (!knobBounds.contains(mouseDownX, mouseDownY)) {
			// snap the middle of the knob to the mouse position
			// $$fb for now, don't support this
			return;
		}
		dragging = true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
	 */
	public void mouseReleased(MouseEvent e) {
		if (!isEnabled()) {
			return;
		}
		if (dragging) {
			dragging = false;
			handleDragging(e.getX(), e.getY());
		}

		setDown(false);
		setHovering(e.getX() >= 0 && e.getX() <= getWidth() && e.getY() >= 0
				&& e.getY() <= getHeight());
		// send tracking end event
		if (listeners != null) {
			for (Listener l : listeners) {
				l.sliderTrackingEnd(this);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent)
	 */
	public void mouseDragged(MouseEvent e) {
		if (dragging) {
			handleDragging(e.getX(), e.getY());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseMotionListener#mouseMoved(java.awt.event.MouseEvent)
	 */
	public void mouseMoved(MouseEvent e) {
		// nothing
	}

	/** override the setUI method to always set the UI to null */
	@Override
	public void setUI(ComponentUI ui) {
		super.setUI(null);
	}

	/**
	 * @return the delegate
	 */
	public ControlDelegate getDelegate() {
		return delegate;
	}

	/**
	 * @return the knob
	 */
	public ControlDelegate getKnob() {
		return knob;
	}

	/**
	 * interface to be implemented by classes to receive change information for
	 * this slider
	 */
	public interface Listener {

		/**
		 * event is fired to the listeners whenever the value of the slider
		 * changes, no matter how and why
		 */
		public void sliderValueChanged(MSlider source);

		/**
		 * this event is fired when the user changes the slider with the mouse.
		 * It is always sent in addition to a sliderValueChanged event. It may
		 * or may not come in between TrackingStart and TrackingEnd events.
		 */
		public void sliderValueTracked(MSlider source);

		/**
		 * this event is fired when the user presses the mouse down in this
		 * control.
		 */
		public void sliderTrackingStart(MSlider source);

		/**
		 * this event is fired when the user releases the mouse button in this
		 * control.
		 */
		public void sliderTrackingEnd(MSlider source);
	}

	/**
	 * return a string representation of this MControl by including the
	 * control's type and name as specified in the skin definition file.
	 */
	@Override
	public String toString() {
		if (delegate != null && delegate.getCtrlDef() != null) {
			return delegate.getCtrlDef().fullName + ": " + super.toString();
		}
		return super.toString();
	}
}
