/**
 *
 */
package com.mixblendr.automation;

import com.mixblendr.audio.AudioState;
import com.mixblendr.audio.AudioTrack;
import com.mixblendr.audio.AutomationObject;

/**
 * An instance of an automation object that changes volume
 * 
 * @author Florian Bomers
 */
public class AutomationVolume extends AutomationObject {

	private double volume;

	/**
	 * Create a new volume automation object
	 * 
	 * @param state
	 * @param volume the linear volume [0..1]
	 * @param startSample the sample time when to execute this volume change
	 */
	public AutomationVolume(AudioState state, double volume, long startSample) {
		super(state, startSample);
		this.volume = volume;
	}

	/**
	 * @return the linear volume [0..1]
	 */
	public double getVolume() {
		return volume;
	}

	/**
	 * Change the track's volume to this object's stored volume.
	 * 
	 * @see com.mixblendr.audio.AutomationObject#executeImpl(com.mixblendr.audio.AudioTrack)
	 */
	@Override
	protected void executeImpl(AudioTrack track) {
		track.setVolume(volume);
		// Debug.debug(toString());
	}

	/**
	 * @return a string representation of this object (mainly for debugging
	 *         purposes)
	 */
	@Override
	public String toString() {
		return super.toString() + ", linear vol=" + volume;
	}
}
