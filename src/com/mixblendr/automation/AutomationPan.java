/**
 *
 */
package com.mixblendr.automation;

import com.mixblendr.audio.AudioState;
import com.mixblendr.audio.AudioTrack;
import com.mixblendr.audio.AutomationObject;

/**
 * An instance of an automation object that changes panorama/balance
 * 
 * @author Florian Bomers
 */
public class AutomationPan extends AutomationObject {

	private double pan;

	/**
	 * Create a new volume automation object
	 * 
	 * @param state
	 * @param pan [-1..0..+1]
	 * @param startSample the sample time when to execute this pan change
	 */
	public AutomationPan(AudioState state, double pan, long startSample) {
		super(state, startSample);
		this.pan = pan;
	}

	/**
	 * @return the pan [-1..0..+1]
	 */
	public double getPan() {
		return pan;
	}

	/**
	 * Change the track's volume to this object's stored volume.
	 * 
	 * @see com.mixblendr.audio.AutomationObject#executeImpl(com.mixblendr.audio.AudioTrack)
	 */
	@Override
	protected void executeImpl(AudioTrack track) {
		track.setBalance(pan);
	}

	/**
	 * @return a string representation of this object (mainly for debugging
	 *         purposes)
	 */
	@Override
	public String toString() {
		return super.toString() + ", linear pan=" + pan;
	}
}
