/**
 *
 */
package com.mixblendr.skin;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

/**
 * A version of JButton which uses the ControlDelegate delegate for actual
 * painting.
 * 
 * @author Florian Bomers
 */
public class MButton extends JComponent implements MControl, MouseListener {

	private List<ActionListener> listeners;

	private boolean down;

	private String text;
	private int textWidth = 0;
	private int textAscent = 0;
	private int textHeight = 0;

	/** Create a new MButton from the given delegate */
	public MButton(ControlDelegate delegate) {
		super();
		this.delegate = delegate;
		delegate.setOwner(this);
		setBounds(delegate.getCtrlDef().targetBounds.getRectangle());
		setOpaque(true);
		addMouseListener(this);
		SkinUtils.setFont(this, delegate, 12);
	}

	/** the delegate handling the painting of this component */
	protected ControlDelegate delegate;

	@Override
	public void paint(Graphics g) {
		delegate.paint(g);
		if (textWidth != 0) {
			g.setFont(getFont());
			int x = (getWidth() - textWidth) / 2;
			if (x < 0) {
				x = 0;
			}
			int y = ((getHeight() - textHeight) / 2) + textAscent;
			g.drawString(text, x, y);
		}
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

	/*
	 * do not override the setUI method to always set the UI to null, will
	 * inhibit mouse events
	 */

	/**
	 * @return the delegate
	 */
	public ControlDelegate getDelegate() {
		return delegate;
	}

	/**
	 * Change the control delegate. This is a special function and should be
	 * rarely done. The owner of cd will be set to this button. The button's
	 * size and position will not be changed. This method can be used to replace
	 * the set of images for the button.
	 */
	public void setDelegate(ControlDelegate cd) {
		if (cd == delegate) return;
		if (delegate != null) {
			delegate.setOwner(null);
			if (cd != null) {
				delegate.assignStateTo(cd);
			}
		}
		this.delegate = cd;
		if (cd != null) {
			cd.setOwner(this);
		}
		repaint();
	}

	/** set the delegate's DOWN state to true if held down */
	protected void updateDelegateDown() {
		delegate.setDown(this.down);
	}

	protected void setDown(boolean down) {
		if (this.down != down) {
			this.down = down;
			updateDelegateDown();
		}
	}

	/**
	 * @return the down state
	 */
	public boolean isDown() {
		return down;
	}

	/**
	 * @return the text
	 */
	public String getText() {
		return text;
	}

	/**
	 * @param text the text to set
	 */
	public void setText(String text) {
		this.text = text;
		FontMetrics fm = getFontMetrics(getFont());
		textWidth = fm.stringWidth(text);
		textAscent = fm.getAscent();
		textHeight = fm.getHeight();
		repaint();
	}

	public void addActionListener(ActionListener l) {
		if (listeners == null) {
			listeners = new ArrayList<ActionListener>();
		}
		listeners.add(l);
	}

	public void removeActionListener(ActionListener l) {
		if (listeners != null) {
			listeners.remove(l);
		}
	}

	protected void fireActionEvent() {
		// fire action listener
		if (listeners != null && listeners.size() > 0) {
			ActionEvent ae = new ActionEvent(this, 0, null);
			for (ActionListener l : listeners) {
				l.actionPerformed(ae);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
	 */
	public void mouseClicked(MouseEvent e) {
		fireActionEvent();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
	 */
	public void mouseEntered(MouseEvent e) {
		if (!SwingUtilities.isLeftMouseButton(e)) {
			delegate.setHovering(true);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
	 */
	public void mouseExited(MouseEvent e) {
		if (!SwingUtilities.isLeftMouseButton(e)) {
			delegate.setHovering(false);
		}
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
		// do not activate focus for now (will disable global key listener)
		// if (isFocusable() && isRequestFocusEnabled()) {
		// requestFocus();
		// }
		if (SwingUtilities.isLeftMouseButton(e)) {
			setDown(true);
		}
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
		if (SwingUtilities.isLeftMouseButton(e)) {
			setDown(false);
			delegate.setHovering(e.getX() >= 0 && e.getX() <= getWidth()
					&& e.getY() >= 0 && e.getY() <= getHeight());
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
