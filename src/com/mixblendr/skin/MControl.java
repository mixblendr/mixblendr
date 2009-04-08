/**
 *
 */
package com.mixblendr.skin;

/**
 * Interface implemented by all MControls like MPanel and MButton. It's sort of
 * a base class for all controls used in this skin system, still allowing them
 * to be based on Swing components.
 * 
 * @author Florian Bomers
 */
public interface MControl {

	/**
	 * Return the delegate of this MControl.
	 * 
	 * @return the delegate
	 */
	public ControlDelegate getDelegate();

	/** set the tool tip of this MControl */
	public void setToolTipText(String s);

	/** repaint the MControl */
	public void repaint();

	/** get current width of the control */
	public int getWidth();

	/** get current height of the control */
	public int getHeight();
}
