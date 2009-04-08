/**
 *
 */
package com.mixblendr.skin;

import java.awt.Dimension;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * A Label component which can display text.
 * 
 * @author Florian Bomers
 */
public class MLabel extends JLabel implements MControl {
	private static final long serialVersionUID = 0;

	/**
	 * Create a new MPanel from the given delegate. By default, MLabels are
	 * centered.
	 */
	public MLabel(ControlDelegate delegate) {
		super("", SwingConstants.CENTER);
		this.delegate = delegate;
		delegate.setOwner(this);
		setBounds(delegate.getCtrlDef().targetBounds.getRectangle());
		setOpaque(false);
		SkinUtils.setFont(this, delegate, -1);
	}

	/** the delegate handling the painting of this component */
	private ControlDelegate delegate;

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
