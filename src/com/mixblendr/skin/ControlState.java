/**
 *
 */
package com.mixblendr.skin;

/**
 * The different states that a control can have
 * 
 * @author Florian Bomers
 */
public enum ControlState {
	NORMAL("normal"), HOVER("hover"), HOVERDOWN("hover.down"), DOWN("down"), BLINK(
			"blink"), PROGRESS("progress"), BACKGROUND_LEFT("bg_left"), // for
																		// panels
	BACKGROUND_LR_TILE("bg_lr_tile"), // for panels
	BACKGROUND_RIGHT("bg_right"); // for panels

	private String id;

	public static final int length = ControlState.values().length;

	/**
	 * Create a static ControlState object with the given id
	 * 
	 * @param id
	 */
	private ControlState(String id) {
		this.id = id;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return true if the passed id matches this control state
	 */
	public boolean matchesID(String aID) {
		return this.id.equalsIgnoreCase(aID);
	}

}
