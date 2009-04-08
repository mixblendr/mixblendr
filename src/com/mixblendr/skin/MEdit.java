/**
 *
 */
package com.mixblendr.skin;

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JTextField;

/**
 * An edit control that lets the user enter text. Initially, the text is
 * centered.
 * 
 * @author Florian Bomers
 */
public class MEdit extends JTextField implements MControl {

	/** Create a new MPanel from the given delegate */
	@SuppressWarnings("static-access")
	public MEdit(ControlDelegate delegate) {
		super("");
		this.delegate = delegate;
		delegate.setOwner(this);
		setBounds(delegate.getCtrlDef().targetBounds.getRectangle());
		setOpaque(false);
		setBorder(null);
		setHorizontalAlignment(JTextField.CENTER);
		SkinUtils.setFont(this, delegate, -1);
		// prevent the text field from accepting text
		setTransferHandler(null);
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

	private Color opaqueBackground;
	private final static Color TRANSPARENT_COLOR = new Color(0, 0, 0, 0);

	/** work around a transparency issue on Mac OS X 10.5 */
	@Override
	public void setOpaque(boolean opaque) {
		if (opaque != isOpaque()) {
			if (opaque) {
				super.setBackground(opaqueBackground);
			} else if (opaqueBackground != null) {
				opaqueBackground = getBackground();
				super.setBackground(TRANSPARENT_COLOR);
			}
		}
		super.setOpaque(opaque);
	}

	/** work around a transparency issue on Mac OS X 10.5 */
	@Override
	public void setBackground(Color color) {
		if (isOpaque()) {
			super.setBackground(color);
		} else {
			opaqueBackground = color;
		}
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
