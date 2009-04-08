/**
 *
 */
package com.mixblendr.gui.main;

import com.mixblendr.util.Debug;

/**
 * Manager which region is currently selected. Use Globals to get an instance.
 * Currently, only one region graph can be selected at any given time.
 * 
 * @author Florian Bomers
 */
public class RegionSelectionManager {

	private static final boolean DEBUG = false;

	private RegionGraph selected;

	/**
	 * Create a new instance of the region selection manager
	 */
	RegionSelectionManager() {
		super();
	}

	/**
	 * return the region graph that was last selected. If no currently selected
	 * region, return null.
	 * 
	 * @return the last selected region, or null if no selection
	 */
	public RegionGraph getLastSelected() {
		return selected;
	}

	/**
	 * Set the newly selected region. Only this region will be selected after
	 * this call.
	 */
	public void setSelected(RegionGraph region) {
		if (this.selected == region) {
			// nothing to do
			return;
		}
		if (this.selected != null) {
			if (DEBUG) Debug.debug("Unselecting: " + this.selected);
			this.selected.setSelectedImpl(false);
		}
		this.selected = region;
		if (DEBUG) Debug.debug("Selected: " + this.selected);
		if (region != null) {
			region.setSelectedImpl(true);
		}
	}

}
