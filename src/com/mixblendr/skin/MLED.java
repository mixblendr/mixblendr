/**
 *
 */
package com.mixblendr.skin;

import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JComponent;

/**
 * An LED control that has 2 states: normal and down. Also blinking state is
 * possible.
 * 
 * @author Florian Bomers
 */
public class MLED extends JComponent implements MControl {
	private static final long serialVersionUID = 0;

	/** the delegate handling the painting of this component */
	private ControlDelegate delegate;

	/** Create a new MLED from the given delegate */
	public MLED(ControlDelegate delegate) {
		super();
		this.delegate = delegate;
		delegate.setOwner(this);
		setBounds(delegate.getCtrlDef().targetBounds.getRectangle());
		setOpaque(true);
	}

	@Override
	public void paint(Graphics g) {
		delegate.paint(g);
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

	/** light up this LED */
	public void setSelected(boolean on) {
		if (delegate.isDown() != on) {
			delegate.setDown(on);
			repaint();
		}
	}

	/** return if this LED is lit */
	public boolean isSelected() {
		return delegate.isDown();
	}

	/**
	 * @return the delegate
	 */
	public ControlDelegate getDelegate() {
		return delegate;
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
