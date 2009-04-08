/**
 *
 */
package com.mixblendr.skin;

import java.awt.Dimension;

/**
 * A class describing a control, as read from the skin definition file.
 * 
 * @author Florian Bomers
 */
public class ControlDefinition {

	/** The full name as type.name, e.g. button.start */
	String fullName;

	/** location where to position this control, and size */
	Rect targetBounds;

	/** location of the control's image for the different states */
	Pos[] sourcePos = new Pos[ControlState.length];
	/** image filenames for the different states */
	String[] sourceImage = new String[ControlState.length];

	/** the normal tooltip */
	String tooltip;

	/** the tooltip when the control is down */
	String tooltipDown;

	/** for sliders, the knob name */
	String knob;

	/** the parent panel name */
	String parent;

	/** the name of the font to use (only for edit and label) */
	String fontname;

	public ControlDefinition() {
		// nothing
	}

	/** create a new ControlDefinition instance by cloning the other */
	public ControlDefinition(ControlDefinition c) {
		if (c != null) {
			assign(c);
		}
	}

	/** assigns all values from <code>c</code> to this ControlDefinition */
	public void assign(ControlDefinition c) {
		if (c != null) {
			this.fullName = c.fullName;
			this.knob = c.knob;
			this.tooltip = c.tooltip;
			this.tooltipDown = c.tooltipDown;
			this.parent = c.parent;
			if (c.targetBounds != null) {
				this.targetBounds = (Rect) c.targetBounds.clone();
			} else {
				this.targetBounds = null;
			}
			this.fontname = c.fontname;
			for (int i = 0; i < ControlState.length; i++) {
				this.sourceImage[i] = c.sourceImage[i];
				if (c.sourcePos[i] != null) {
					this.sourcePos[i] = (Pos) c.sourcePos[i].clone();
				} else {
					this.sourcePos[i] = null;
				}
			}
		}
	}

	/**
	 * Assign the images from <code>c</code> to this ControlDefinition
	 * 
	 * @param c the control to copy images from
	 */
	public void assignImagesFrom(ControlDefinition c) {
		for (int i = 0; i < ControlState.length; i++) {
			this.sourceImage[i] = c.sourceImage[i];
		}
	}

	/** return the width and height of this control (derived from targetBounds) */
	public Dimension getSize() {
		if (targetBounds != null) {
			return targetBounds.getDimension();
		}
		return new Dimension();
	}

	/** return the actual instance the target Rect */
	public Rect getTargetBounds() {
		if (targetBounds != null) {
			return targetBounds;
		}
		return new Rect();
	}

	/** return the target x position */
	public int getTargetX() {
		if (targetBounds != null) {
			return targetBounds.x;
		}
		return 0;
	}

	/** return the target y position */
	public int getTargetY() {
		if (targetBounds != null) {
			return targetBounds.y;
		}
		return 0;
	}

	/** return the target width */
	public int getTargetWidth() {
		if (targetBounds != null) {
			return targetBounds.w;
		}
		return 0;
	}

	/** return the target height */
	public int getTargetHeight() {
		if (targetBounds != null) {
			return targetBounds.h;
		}
		return 0;
	}

	/**
	 * returns the type of this control, i.e. the word before the dot of the
	 * full name. Returns null if the full name does not contain a dot.
	 */
	public String getType() {
		int i = fullName.indexOf('.');
		if (i < 0) {
			return null;
		}
		return fullName.substring(0, i).trim();
	}

	/**
	 * returns the name of this control, i.e. the word after the dot of the full
	 * name. Returns null if the full name does not contain a dot.
	 */
	public String getName() {
		int i = fullName.indexOf('.');
		if (i < 0) {
			return null;
		}
		return fullName.substring(i + 1).trim();
	}

	public boolean hasSourceImage(ControlState cs) {
		return sourceImage[cs.ordinal()] != null;
	}

	/** for debugging purposes: print the contents of this control */
	void dump() {
		System.out.println("Control '" + fullName + "':");
		System.out.println("  target bounds: " + targetBounds);
		if (tooltip != "" && tooltip != null)
			System.out.println("  tooltip=" + tooltip);
		if (tooltipDown != "" && tooltipDown != null)
			System.out.println("  tooltip.down=" + tooltipDown);
		if (knob != "" && knob != null) System.out.println("  knob=" + knob);
		if (parent != "" && parent != null)
			System.out.println("  parent=" + parent);
		for (ControlState cs : ControlState.values()) {
			int i = cs.ordinal();
			if (sourceImage[i] != "" && sourceImage[i] != null) {
				if (sourcePos[i] == null) {
					System.out.println("  " + cs + "=" + sourceImage[i]);
				} else {
					System.out.println("  " + cs + "=" + sourceImage[i] + ", "
							+ sourcePos[i]);
				}
			}
		}
	}

}
