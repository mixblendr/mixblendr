/**
 *
 */
package com.mixblendr.skin;

import java.awt.event.MouseEvent;

/**
 * A toggle button as descendant of MButton.
 * 
 * @author Florian Bomers
 */
public class MToggle extends MButton {

	private boolean selected;

	/** Create a new MToggle from the given delegate */
	public MToggle(ControlDelegate delegate) {
		super(delegate);
	}

	/**
	 * @return the selected
	 */
	public boolean isSelected() {
		return selected;
	}

	/** set the delegate's DOWN state to true if selected or if held down */
	@Override
	protected void updateDelegateDown() {
		delegate.setDown(this.isDown() || this.selected);
	}

	/**
	 * Set the selected state of this toggle button, do not send an action
	 * event.
	 * 
	 * @param selected if to select the toggle or not
	 */
	public void setSelected(boolean selected) {
		setSelected(selected, false);
	}

	/**
	 * Set the selected state of this toggle button, possibly sending an action
	 * event.
	 * 
	 * @param selected if to select the toggle or not
	 * @param sendEvent if true, fire an action event if the selected state
	 *            changed
	 */
	public void setSelected(boolean selected, boolean sendEvent) {
		if (this.selected != selected) {
			this.selected = selected;
			updateDelegateDown();
			if (sendEvent) {
				fireActionEvent();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
	 */
	@Override
	public void mouseClicked(MouseEvent e) {
		setSelected(!selected, false);
		super.mouseClicked(e);
	}

}
