/**
 *
 */
package com.mixblendr.audio;

/**
 * Listener for automation events. Subscribe to these events in the AudioState
 * class.
 * 
 * @author Florian Bomers
 */
public interface AutomationListener {

	/**
	 * Called from a separate dispatcher thread whenever an automation event
	 * occured. Note: events in one track are guaranteed to be sent in order of
	 * their start time. However, it's possible that automation events on
	 * different tracks come slightly out of order (maximum slice time
	 * difference).
	 * 
	 * @param track the track on which this event happened
	 * @param ao the automation object that happened
	 */
	public void automationEvent(AudioTrack track, AutomationObject ao);

}
