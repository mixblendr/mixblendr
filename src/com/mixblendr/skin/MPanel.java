/**
 *
 */
package com.mixblendr.skin;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.JComponent;
import com.mixblendr.util.Debug;

/**
 * A JPanel which uses an MComponent as delegate for drawing.
 * 
 * @author Florian Bomers
 */
public class MPanel extends JComponent implements MControl {
	private static final boolean TRACE = false;

	/** the delegate handling the painting of this component */
	private ControlDelegate delegate;

	private PaintListener paintListener;

	private float backgroundTransparency = 1.0f;

	/** Create a new MPanel from the given delegate */
	public MPanel(ControlDelegate delegate) {
		super();
		this.delegate = delegate;
		delegate.setOwner(this);
		setBounds(delegate.getCtrlDef().targetBounds.getRectangle());
		setOpaque(delegate.hasImage(ControlState.NORMAL)
				|| delegate.hasHorizontalBackground());
	}

	/**
	 * @return the paintListener
	 */
	public PaintListener getPaintListener() {
		return paintListener;
	}

	/**
	 * @param paintListener the paintListener to set
	 */
	public void setPaintListener(PaintListener paintListener) {
		this.paintListener = paintListener;
	}

	private Rectangle clipCache = new Rectangle();

	/**
	 * First, paint this control using the delegate, then paint the child
	 * controls. If a paint listener is set, it's called at the beginning and
	 * the end of this method.
	 */
	@Override
	public void paint(Graphics g) {
		g.getClipBounds(clipCache);
		if (paintListener != null) {
			paintListener.panelBeforePaint(this, g, clipCache);
		}
		delegate.paint(g, 0, 0, backgroundTransparency);
		paintChildren(g);
		if (paintListener != null) {
			paintListener.panelAfterPaint(this, g, clipCache);
		}
	}

	/**
	 * if no layout manager is set, return the delegate's preferred size,
	 * otherwise use the default implementation
	 */
	@Override
	public Dimension getPreferredSize() {
		if (getLayout() == null) {
			return delegate.getPreferredSize();
		}
		return super.getPreferredSize();
	}

	/**
	 * if no layout manager is set, return the delegate's minimum size,
	 * otherwise use the default implementation
	 */
	@Override
	public Dimension getMinimumSize() {
		if (getLayout() == null) {
			return delegate.getMinimumSize();
		}
		return super.getMinimumSize();
	}

	/**
	 * if no layout manager is set, return the delegate's maximum size,
	 * otherwise use the default implementation
	 */
	@Override
	public Dimension getMaximumSize() {
		if (getLayout() == null) {
			return delegate.getMaximumSize();
		}
		return super.getMaximumSize();
	}

	@Override
	public void setBounds(int x, int y, int w, int h) {
		super.setBounds(x, y, w, h);
		if (TRACE) {
			Debug.debug(delegate.getCtrlDef().fullName + ": bounds now " + x
					+ "," + y + "," + w + "," + h);
			Debug.debug(" -parent:" + getParent());
		}
	}

	/**
	 * @return the delegate
	 */
	public ControlDelegate getDelegate() {
		return delegate;
	}

	/**
	 * @return the backgroundTransparency
	 */
	public float getBackgroundTransparency() {
		return backgroundTransparency;
	}

	/**
	 * @param backgroundTransparency the backgroundTransparency to set
	 */
	public void setBackgroundTransparency(float backgroundTransparency) {
		this.backgroundTransparency = backgroundTransparency;
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

	/**
	 * One panel listener can be set, being called just before painting and
	 * after painting
	 */
	public interface PaintListener {
		/**
		 * this method is called whenever painting starts
		 * 
		 * @param panel the panel to be painted
		 * @param g the graphics context
		 * @param clip as a convenience, the clip bounds of the graphics context
		 */
		public void panelBeforePaint(MPanel panel, Graphics g, Rectangle clip);

		/**
		 * this method is called after painting all child components
		 * 
		 * @param panel the panel to be painted
		 * @param g the graphics context
		 * @param clip as a convenience, the clip bounds of the graphics context
		 */
		public void panelAfterPaint(MPanel panel, Graphics g, Rectangle clip);
	}

}
