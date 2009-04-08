/**
 *
 */
package com.mixblendr.gui.main;

import com.mixblendr.audio.AudioState;

/**
 * Class handling the snap to beats feature.
 * 
 * @author Florian Bomers
 */
public class SnapToBeats {

	private AudioState state;
	private GraphScale scale;

	private boolean enabled = true;

	private static final boolean GRAVITY_WHEN_DISABLED = false;

	/** the number of pixels that should be magnetic if snapping is turned off */
	private final static int SNAP_PIXELS = 6;

	/** prevent instanciation without state */
	@SuppressWarnings("unused")
	private SnapToBeats() {
		super();
	}

	public SnapToBeats(AudioState state, GraphScale scale) {
		this.state = state;
		this.scale = scale;
	}

	private long getNearestBeatSample(long sample) {
		double beat = Math.round(state.sample2beat(sample));
		return state.beat2sample(beat);
	}

	/**
	 * Given a sample position, return it (snap off) or the sample position of
	 * the nearest next beat. If the sample is within a few pixels of a beat, be
	 * magnetic to it, too.
	 */
	public long getSnappedSample(long sample) {
		return getSnappedSample(sample, 0);
	}

	/**
	 * Given a sample position and its length, return the sample position (snap
	 * off, or not near any beat markers) or the sample position of the nearest
	 * next beat. Snapping also applies to the end of the region. If snapping is
	 * enabled, only beats will be returned. If snapping is off, a few pixels
	 * around a beat will be a magnetic area.
	 */
	public long getSnappedSample(long sample, long lenInSamples) {
		return getSnappedSample(sample, lenInSamples,
				(int) scale.pixel2sample(SNAP_PIXELS));
	}

	/**
	 * Given a sample position and its length, return the sample position (snap
	 * off, or not near any beat markers) or the sample position of the nearest
	 * next beat. Snapping also applies to the end of the region. If snapping is
	 * enabled, only beats will be returned. If snapping is off, the snapSamples
	 * will be a magnetic area.
	 * 
	 * @param snapSamples the number of samples leading to a snap
	 */
	private long getSnappedSample(long sample, long lenInSamples,
			int snapSamples) {
		if (enabled) {
			sample = getNearestBeatSample(sample);
		} else if (GRAVITY_WHEN_DISABLED) {
			long beatSample = getNearestBeatSample(sample);
			if (Math.abs(beatSample - sample) <= snapSamples) {
				sample = beatSample;
			} else if (lenInSamples > 0) {
				// now check for the other end
				long endSample = sample + lenInSamples;
				beatSample = getNearestBeatSample(endSample);
				if (Math.abs(beatSample - endSample) <= snapSamples) {
					sample = beatSample - lenInSamples;
				}
			}
		}
		return sample;
	}

	/**
	 * Return true if snapping to samples is enabled. If it is enabled, snapped
	 * samples always fall on beats.
	 * 
	 * @return the enabled
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Enable or disable snapping to samples.
	 * 
	 * @param enabled the enabled to set
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
