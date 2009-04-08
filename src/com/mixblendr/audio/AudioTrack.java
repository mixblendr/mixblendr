/**
 *
 */
package com.mixblendr.audio;

import static com.mixblendr.util.Debug.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.tritonus.share.sampled.AudioUtils;
import org.tritonus.share.sampled.FloatSampleBuffer;

/**
 * Class that manages all the components of a track: volume, balance, mute,
 * solo, effects. It owns a Playlist object which provides the audio data,
 * before the AudioTrack instance applies volume, balance and the effects.
 * 
 * @author Florian Bomers
 */
public class AudioTrack {

	/**
	 * the different states for SOLO mode: no solo mode, or this track is solo,
	 * or another track is solo.
	 */
	public enum SoloState {
		NONE, SOLO, OTHER_SOLO
	}

	/** counter for the unique track ID */
	private static int IDCounter = 0;

	/** a unique number identifying this track */
	private int ID;

	/**
	 * the index of the track list in AudioMixer. Package private so that
	 * AudioMixer can easily set it.
	 */
	int index;

	/** arbitrary name of this track */
	private String name;

	/** linear volume, 0..1 */
	private double volume;

	/** muted or not */
	private boolean mute;

	/** solo state */
	private SoloState solo;

	/** pan/balance [-1..0..1] */
	private double balance;

	/** list of tracks that are read from */
	private List<AudioEffect> effects;

	/** the playlist providing the samples */
	private Playlist playlist;

	private AudioState state;

	/** The current effective volume per channel */
	private double[] effectiveVolume;

	/**
	 * remember the last volume per channel, and, if necessary, fade to the new
	 * volume
	 */
	private double[] lastEffectiveVolume;

	private boolean automationEnabled;

	/**
	 * Create a new empty audio track.
	 */
	public AudioTrack(AudioState state) {
		ID = ++IDCounter;
		this.state = state;
		index = -1;
		name = "Track " + ID;
		volume = 1.0;
		effectiveVolume = new double[state.getChannels()];
		lastEffectiveVolume = new double[state.getChannels()];
		balance = 0.0;
		mute = false;
		solo = SoloState.NONE;
		effects = new ArrayList<AudioEffect>();
		playlist = new Playlist(state, this);
		automationEnabled = false;
		calcEffectiveVolume();
		applyEffVolToLastEffVol();
	}

	/**
	 * @return the state
	 */
	public AudioState getState() {
		return state;
	}

	/** copy the effective volume to the last effective volume array */
	private void applyEffVolToLastEffVol() {
		for (int c = 0; c < state.getChannels(); c++) {
			lastEffectiveVolume[c] = effectiveVolume[c];
		}
	}

	/**
	 * calculate the effective volume for left and right channel, based on
	 * volume, pan, mute, and solo
	 */
	private synchronized void calcEffectiveVolume() {
		if (state.getChannels() != 1 && state.getChannels() != 2) {
			throw new IllegalStateException(
					"currently, only mono or stereo mode supported");
		}
		// if muted, we're silent anyway
		if ((mute && solo != SoloState.SOLO) || (solo == SoloState.OTHER_SOLO)) {
			for (int c = 0; c < state.getChannels(); c++) {
				effectiveVolume[c] = 0.0;
			}
		} else if (state.getChannels() == 1) {
			// balance is ignored
			effectiveVolume[0] = volume * state.getMasterVolume();
		} else {
			// FIXME: use non-linear balance?
			// stereo: left volume
			effectiveVolume[0] = volume * state.getMasterVolume()
					* ((balance <= 0.0) ? 1.0 : 1 - balance);
			// stereo: right volume
			effectiveVolume[1] = volume * state.getMasterVolume()
					* ((balance >= 0.0) ? 1.0 : 1 + balance);
		}
	}

	/**
	 * @return the iD
	 */
	public int getID() {
		return ID;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Set a new arbitrary name of this track.
	 * 
	 * @param name the name to set
	 */
	public void setName(String name) {
		if (name == null) return;
		if (!name.equals(this.name)) {
			this.name = name;
			state.getAudioEventDispatcher().dispatchTrackNameChange(this);
		}
	}

	/**
	 * @return the index of this track in AudioMixer's list of tracks, or -1 if
	 *         this track does not belong to a mixer.
	 */
	public int getIndex() {
		return index;
	}

	/**
	 * @return the playlist instance
	 */
	public Playlist getPlaylist() {
		return playlist;
	}

	/**
	 * @return the balance [L -1...0...+1 R]
	 */
	public double getBalance() {
		return balance;
	}

	/**
	 * @param balance the balance to set [L -1...0...+1 R]
	 */
	public void setBalance(double balance) {
		this.balance = balance;
		calcEffectiveVolume();
	}

	/**
	 * @return the linear volume of this track [0..1]
	 */
	public double getVolume() {
		return volume;
	}

	/**
	 * @param volume specify the new linear volume of this track [0..1]
	 */
	public void setVolume(double volume) {
		this.volume = volume;
		calcEffectiveVolume();
	}

	/**
	 * @return the volume of this track in decibel [-inf..0]
	 */
	public double getVolumeDB() {
		return AudioUtils.linear2decibel(volume);
	}

	/**
	 * @param decibel specify the new volume of this track in decibel [-inf..0]
	 */
	public void setVolumeDB(double decibel) {
		setVolume(AudioUtils.decibel2linear(decibel));
	}

	/**
	 * @return the mute state
	 */
	public boolean isMute() {
		return mute;
	}

	/**
	 * A muted track is not audible, unless the solo state is set to SOLO.
	 * 
	 * @param mute the mute state to set
	 */
	public void setMute(boolean mute) {
		this.mute = mute;
		calcEffectiveVolume();
	}

	/**
	 * Return the current solo state.<br>
	 * Note: to set solo, use the AudioMixer class.
	 * 
	 * @return the solo state
	 */
	public SoloState getSolo() {
		return solo;
	}

	/**
	 * A track is audible if not muted, and not in NO_SOLO state. A SOLO state
	 * will override the mute state.
	 * 
	 * @param solo the solo state to set
	 */
	void setSoloImpl(SoloState solo) {
		this.solo = solo;
		calcEffectiveVolume();
	}

	/**
	 * @return the number of effects
	 */
	public int getEffectCount() {
		return effects.size();
	}

	/**
	 * Add a new, initialized, effect. This effect will be used immediately.
	 * 
	 * @param e the effect to add
	 */
	public void addEffect(AudioEffect e) {
		synchronized (effects) {
			effects.add(e);
		}
	}

	/**
	 * Remove the specified effect.
	 * 
	 * @param e the effect to remove
	 * @return if the effect was actually removed
	 */
	public boolean removeEffect(AudioEffect e) {
		synchronized (effects) {
			return effects.remove(e);
		}
	}

	/**
	 * Remove all effects.
	 */
	public void clearEffects() {
		synchronized (effects) {
			effects.clear();
		}
	}

	/**
	 * Get a list of all effects.
	 * 
	 * @return a non-modifiable view of the list of effects
	 */
	public List<AudioEffect> getEffects() {
		synchronized (effects) {
			return Collections.unmodifiableList(effects);
		}
	}

	/**
	 * Return the specified effect.
	 * 
	 * @param aIndex the number of the effect, 0...getEfectCount()-1
	 * @return the indexed effect
	 */
	public AudioEffect getEffect(int aIndex) {
		synchronized (effects) {
			return effects.get(aIndex);
		}
	}

	/**
	 * convenience method for creating a region, and adding it to the track's
	 * playlist
	 */
	public AudioRegion addRegion(AudioFile af, long startTimeSamples) {
		return addRegion(af, startTimeSamples, -1);
	}

	/**
	 * convenience method for creating a region, and adding it to the track's
	 * playlist
	 * 
	 * @param durationInSamples the duration in samples, or -1 if not known
	 */
	public AudioRegion addRegion(AudioFile af, long startTimeSamples,
			long durationInSamples) {
		AudioRegion r = new AudioRegion(state, af);
		r.setStartTimeSamples(startTimeSamples);
		if (durationInSamples >= 0) {
			r.setDuration(durationInSamples);
		}
		playlist.addObject(r);
		return r;
	}

	/** Add an automation object to this track. It will be effective immediately. */
	public void addAutomationObject(AutomationObject ao) {
		playlist.addObject(ao);
		// debug("Adding "+ao);
	}

	/**
	 * Get the duration of this track. This may change depending on the
	 * availability of currently downloaded media
	 */
	public long getDurationSamples() {
		return playlist.getDurationSamples();
	}

	/** @return if automation is currently enabled */
	public boolean isAutomationEnabled() {
		// return state.isAutomationEnabled();
		return automationEnabled;
	}

	/** set if automation is currently enabled */
	public void setAutomationEnabled(boolean on) {
		// state.setAutomationEnabled(on);
		automationEnabled = on;
	}

	/**
	 * This class maintains the peak levels of the last PEAK_ARRAY_SIZE buffers
	 * rendered in this track.
	 */
	private float[] peakArray = new float[PEAK_ARRAY_SIZE];
	/** current write position into the peak array */
	private int peakArrayIndex = 0;

	private static final int PEAK_ARRAY_SIZE = 500;

	/** add the current peak level to the rotating array of levels */
	private void handlePeak(float level) {
		peakArray[peakArrayIndex++] = level;
		if (peakArrayIndex >= peakArray.length) {
			peakArrayIndex = 0;
		}
	}

	/** remove any values from the peak cache */
	void flushPeakCache() {
		for (int i = 0; i < PEAK_ARRAY_SIZE; i++) {
			peakArray[i] = 0.0f;
		}
	}

	/**
	 * Retrieve a (historic) peak level for peak level meters. For better
	 * results when jumping, use getPeakLevel(int, int).
	 * 
	 * @param samplePosition the time for when the peak level is seeked. This
	 *            time should be lower or equal the current slice position.
	 * @param durationSamples the duration in samples of the period for which
	 *            the peak is being displayed.
	 * @return the current linear peak level [0..1]
	 */
	public float getPeakLevel(long samplePosition, int durationSamples) {
		long currSlicePos = state.getSampleSlicePosition();
		int lag = (int) (currSlicePos - samplePosition + durationSamples);
		return getPeakLevel(lag, durationSamples);
	}

	/**
	 * Retrieve a (historic) peak level for peak level meters.
	 * 
	 * @param currLagSamples the lag time from current sample slice position to
	 *            audible playback position
	 * @param durationSamples the duration in samples of the period for which
	 *            the peak is being displayed.
	 * @return the current linear peak level [0..1]
	 */
	public float getPeakLevel(int currLagSamples, int durationSamples) {
		// long currSlicePos = state.getSampleSlicePosition();
		int sliceDuration = state.getSliceSizeSamples();
		int goBackIndexes = currLagSamples / sliceDuration;
		int currIndex = peakArrayIndex - goBackIndexes;
		if (currIndex < 0) currIndex += PEAK_ARRAY_SIZE;
		int peakCount = (durationSamples / sliceDuration) + 1;
		float max = 0.0f;
		while (peakCount > 0) {
			if (currIndex >= PEAK_ARRAY_SIZE) {
				currIndex -= PEAK_ARRAY_SIZE;
			}
			if (currIndex >= 0 && currIndex < PEAK_ARRAY_SIZE
					&& peakArray[currIndex] > max) {
				max = peakArray[currIndex];
			}
			currIndex++;
			peakCount--;
		}
		return max;
	}

	/**
	 * calculate the maximum level in this buffer. The return value is capped to
	 * the maximum level 1.0. The peak level is calculated from all channels.
	 * 
	 * @param buffer the buffer to calculate the peak level of
	 * @return the peak level, capped to 1.0, i.e. [0..1]
	 */
	private final static float getMaxLevel(FloatSampleBuffer buffer) {
		float max = 0.0f;
		int sampleCount = buffer.getSampleCount();
		for (int c = 0; c < buffer.getChannelCount(); c++) {
			float[] samples = buffer.getChannel(c);
			for (int i = 0; i < sampleCount; i++) {
				float sample = samples[i];
				if (sample > max) {
					if (sample >= 1.0f) {
						// clipping: shortcut
						return 1.0f;
					}
					max = sample;
				}
			}
		}
		return max;
	}

	/**
	 * Read a new chunk of audio data from this track's source. It will not
	 * modify the sample count of the buffer; if not a full buffer of data is
	 * available, the remainder is silenced. Any call(s) to this method should
	 * be followed by readEffects() to apply the effects and calculate the peak
	 * level.
	 */
	public void readSource(long samplePos, FloatSampleBuffer buffer,
			int offset, int sampleCount) {
		// will we generate samples at all?
		boolean silent = true;
		for (int c = 0; c < effectiveVolume.length; c++) {
			if (effectiveVolume[c] != 0.0 || lastEffectiveVolume[c] != 0.0) {
				silent = false;
				break;
			}
		}
		// TODO: should really calculate the volume factor AFTER reading from
		// the playlist, or let the playlist process automation effects prior to
		// reading from the regions.

		// $$fb always need to call playlist.read() in order to execute
		// automation. Otherwise could optimize by not reading if silent.

		// read the actual audio data from the playlist
		if (playlist.read(samplePos, buffer, offset, sampleCount) && !silent) {
			// has successfully read the audio data, now apply the volume
			// (fading when volume changed)
			for (int c = 0; c < buffer.getChannelCount(); c++) {
				double startVolume = lastEffectiveVolume[c];
				double endVolume = effectiveVolume[c];
				// fade from lastVolume to currVolume
				double volIncrease = (endVolume - startVolume) / sampleCount;

				float[] data = buffer.getChannel(c);
				if (volIncrease != 0.0) {
					// for fading, multiply every sample with the
					// increasing/decreasing volume factor
					for (int i = 0; i < sampleCount; i++) {
						data[i + offset] *= startVolume;
						startVolume += volIncrease;
					}
					// remember this changed volume for next time
					lastEffectiveVolume[c] = endVolume;
				} else if (startVolume != 1.0) {
					for (int i = 0; i < sampleCount; i++) {
						data[i + offset] *= startVolume;
					}
				}
			}
		} else {
			// no audio data: silence the buffer
			buffer.makeSilence(offset, sampleCount);
			applyEffVolToLastEffVol();
		}
	}

	/**
	 * Apply the effects of this track to the provided buffer. This method
	 * should always be called after reading from source using readSource().
	 * 
	 * @param samplePos the position in samples when this buffer will be heard
	 * @param buffer the buffer to apply the effects to and calculate the new
	 *            peak.
	 */
	public void readEffects(long samplePos, FloatSampleBuffer buffer) {
		synchronized (effects) {
			// apply the effects
			for (AudioEffect effect : effects) {
				try {
					effect.process(samplePos, buffer, 0,
							buffer.getSampleCount());
				} catch (Throwable t) {
					error("Exception occured during effects processing:");
					error(t);
				}
			}
		}
		// calculate volume level and store in rotating array
		handlePeak(getMaxLevel(buffer));
	}

	/** @return a String representation of this track, e.g. &quot;Track 1&quot; */
	@Override
	public String toString() {
		return "Track " + index;
	}


    public double getStartTime()
    {
        return playlist.getStartTime();
    }

}
