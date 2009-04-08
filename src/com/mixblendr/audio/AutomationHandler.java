/**
 *
 */
package com.mixblendr.audio;

/**
 * Base class for a certain automation type. Automation handlers are created
 * automatically upon first usage of an AutomationObject. Every distinct
 * automation type has a different instance of AutomationHandler.
 * <p>
 * The handler is specifically used for managing the state during tracking (i.e.
 * user moves the GUI control). It is also used for optimizing chasing events.
 * 
 * @author Florian Bomers
 */
public class AutomationHandler {
	private AudioTrack trackingTrack;

	private AutomationObject lastChasingObject;

	/**
	 * @return the lastChasingObject
	 */
	AutomationObject getLastChasingObject() {
		return lastChasingObject;
	}

	/**
	 * @param lastChasingObject the lastChasingObject to set
	 */
	void setLastChasingObject(AutomationObject lastChasingObject) {
		this.lastChasingObject = lastChasingObject;
	}

	/**
	 * @return true if tracking is currently active on this track
	 */
	public boolean isTracking(AudioTrack track) {
		return (track != null) && (trackingTrack == track);
	}

	/**
	 * Set this automation type's status to tracking or not. This method is
	 * called by automation type implementors when the user touches the GUI
	 * control corresponding to this automation type. Once tracking is
	 * activated, all automation objects of this type on this track are ignored.
	 * It is very important that the implementor makes sure that tracking is
	 * turned off when the user releases the control. Otherwise, automation for
	 * this type will be defunct.
	 * 
	 * @param track the track to activate tracking
	 */
	public void setTracking(AudioTrack track, boolean on) {
		if (!on && trackingTrack == track) {
			trackingTrack = null;
		} else if (on) {
			trackingTrack = track;
		}
	}

}
