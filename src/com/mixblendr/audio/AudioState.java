/**
 *
 */
package com.mixblendr.audio;

import java.util.ArrayList;
import java.util.List;

import org.tritonus.share.sampled.*;

import com.mixblendr.util.Debug;

/**
 * Sort of a global class to unify some state parameters of the audio engine and
 * the playback engine.
 * 
 * @author Florian Bomers
 */
public class AudioState {

	public static final double TEMPO_MIN = 10;
	public static final double TEMPO_MAX = 240;

	/** minimum number of samples for the duration of the loop */
	public static final long MIN_LOOP_SAMPLES = 1000;

	/** minimum number of beats in the loop. Set to 0 to disable a minimum length */
	public static final int MIN_LOOP_BEATS = 1;

	private int channels = 2;
	private float sampleRate = 44100f;
	private long sampleSlicePosition = 0;
	private int sampleSliceSize = 1000;
	private boolean loopEnabled = false;
	private long loopStart = 0;
	private long loopEnd = 0;
	private double tempo = 120.0; // in bpm
	private int beatsPerMeasure = 4;
	private double masterVolume = 0.7;
	private boolean timeDisplayInBeats = true;
	// private boolean automationActive = false;

	private AudioFileFactory audioFileFactory;
	private AutomationEventDispatcher automationDispatcher;
	private AudioEventDispatcher audioDispatcher;

	private List<StateListener> stateListeners;

	AudioState() {
		automationDispatcher = new AutomationEventDispatcher();
		audioDispatcher = new AudioEventDispatcher();
		stateListeners = new ArrayList<StateListener>();
	}

	/**
	 * @param channels the channels to set
	 */
	void setChannels(int channels) {
		this.channels = channels;
	}

	/**
	 * @return the channels
	 */
	public int getChannels() {
		return channels;
	}

	/**
	 * @param sampleRate the sampleRate to set
	 */
	void setSampleRate(float sampleRate) {
		this.sampleRate = sampleRate;
	}

	/**
	 * @return the sampleRate
	 */
	public float getSampleRate() {
		return sampleRate;
	}

	/** utility method to convert a sample position to a millisecond position */
	public double sample2millis(long samplePos) {
		return AudioUtils.frames2MillisD(samplePos, sampleRate);
	}

	/** utility method to convert a sample position to a second position */
	public double sample2seconds(long samplePos) {
		return samplePos / ((double) sampleRate);
	}

	/** utility method to convert a millisecond position to a sample position */
	public long millis2sample(double millis) {
		return AudioUtils.millis2Frames(millis, sampleRate);
	}

	/** utility method to convert a second position to a sample position */
	public long seconds2sample(double seconds) {
		return (long) (seconds * sampleRate);
	}

	/**
	 * utility method to convert a second position to a a double precision
	 * sample position
	 */
	public double seconds2sampleD(double seconds) {
		return (long) (seconds * sampleRate);
	}

	/**
	 * utility method to convert a sample position to a beat count, using the
	 * current tempo
	 */
	public double sample2beat(long sample) {
		return seconds2beat(sample2seconds(sample));
	}

	/**
	 * utility method to convert a seconds position to a beat count, using the
	 * current tempo
	 */
	public double seconds2beat(double second) {
		return second * this.tempo / 60.0;
	}

	/**
	 * utility method to convert a beat position to a second position, using the
	 * current tempo
	 */
	public double beat2seconds(double beat) {
		return beat * 60.0 / this.tempo;
	}

	/**
	 * utility method to convert a beat position to a sample position, using the
	 * current tempo
	 */
	public long beat2sample(double beat) {
		return seconds2sample(beat2seconds(beat));
	}

	/**
	 * utility method to convert a beat position to a double precision sample
	 * position, using the current tempo
	 */
	public double beat2sampleD(double beat) {
		return seconds2sampleD(beat2seconds(beat));
	}

	/**
	 * utility method to convert a measure:beat position to a sample position,
	 * using the current tempo and time signature
	 * 
	 * @param beat the beat, 0...getBeatsPerMeasure()-1
	 */
	public long measure2sample(long measure, int beat) {
		return beat2sample(measure2beats(measure, beat));
	}

	/**
	 * utility method to convert a measure:beat position to a seconds position,
	 * using the current tempo and time signature
	 * 
	 * @param beat the beat, 0...getBeatsPerMeasure()-1
	 */
	public double measure2seconds(long measure, int beat) {
		return beat2seconds(measure2beats(measure, beat));
	}

	/**
	 * utility method to convert a measure:beat position to beats, using the
	 * current time signature
	 * 
	 * @param beat the beat, 0...getBeatsPerMeasure()-1
	 */
	public double measure2beats(long measure, int beat) {
		return (double) (measure * beatsPerMeasure) + beat;
	}

	/**
	 * @return a String with the number <code>num</code> prepended with as
	 *         many zeroes as necessary to return a string with exactly
	 *         <code>digits</code> characters.
	 */
	private static final String formatNum(int num, int digits) {
		// TODO: optimize with StringBuffer or so
		String result = Integer.toString(num);
		while (result.length() < digits)
			result = "0" + result;
		return result;
	}

	/**
	 * Return a time string in the form min:sec:tenths
	 * 
	 * @param timeMillis the time in milliseconds
	 * @return the formatted string
	 */
	public static final String millis2timeTenthsString(long timeMillis) {
		// TODO: optimize with StringBuffer or so
		return Long.toString(timeMillis / 60000) + ":"
				+ formatNum((int) (timeMillis / 1000) % 60, 2) + "."
				+ Integer.toString((int) (timeMillis / 100) % 10);
	}

	/**
	 * Return a time string in the form min:sec
	 * 
	 * @param timeMillis the time in milliseconds
	 * @return the formatted string
	 */
	public static final String millis2timeString(long timeMillis) {
		// TODO: optimize with StringBuffer or so
		return Long.toString(timeMillis / 60000) + ":"
				+ formatNum((int) (timeMillis / 1000) % 60, 2);
	}

	/** return the passed sample position as a string with min:sec.tenths */
	public final String samples2timeTenthsString(long sample) {
		return millis2timeTenthsString((long) AudioUtils.frames2MillisD(sample,
				sampleRate));
	}

	/** return the passed sample position as a string with min:sec */
	public final String samples2timeString(long sample) {
		return millis2timeString((long) AudioUtils.frames2MillisD(sample,
				sampleRate));
	}

	/**
	 * return the passed sample position as a string with min:sec or as
	 * min:sec.tenths if includeTenths is true.
	 */
	public final String samples2timeString(long sample, boolean includeTenths) {
		if (includeTenths) {
			return samples2timeTenthsString(sample);
		}
		return samples2timeString(sample);
	}

	/**
	 * return the passed sample position as a string with measures:beat using
	 * the current tempo
	 */
	public final String samples2MeasureString(long sample) {
		long beat = (long) sample2beat(sample);
		return Long.toString((beat / beatsPerMeasure) + 1) + ":"
				+ ((beat % beatsPerMeasure) + 1);
	}

	/**
	 * return the passed sample position as a string with measures:beat:tenths
	 * using the current tempo
	 */
	public final String samples2measureTenthsString(long sample) {
		double beat = sample2beat(sample);
		long lBeat = (long) beat;
		int tenths = (int) ((beat - lBeat) * 10);
		return Long.toString((lBeat / beatsPerMeasure) + 1) + ":"
				+ ((lBeat % beatsPerMeasure) + 1) + "." + tenths;
	}

	/**
	 * return the passed sample position as a string with measures:beat (or
	 * measure:beat.tenths if includeTenths is true) using the current tempo
	 */
	public final String samples2measureString(long sample, boolean includeTenths) {
		if (includeTenths) {
			return samples2measureTenthsString(sample);
		}
		return samples2MeasureString(sample);
	}

	/**
	 * Return the passed sample position as a string, either as measure:beat (if
	 * isTimeDisplayInBeats=true), or as a time string. If includeTenths is
	 * true, the returned string will be appended with a digit for tenths of a
	 * beat, or tenths of a second.
	 */
	public final String samples2display(long sample, boolean includeTenths) {
		if (timeDisplayInBeats) {
			if (includeTenths) {
				return samples2measureTenthsString(sample);
			}
			return samples2MeasureString(sample);
		}
		if (includeTenths) {
			return samples2timeTenthsString(sample);
		}
		return samples2timeString(sample);
	}

	/**
	 * @param sampleSlicePosition the sampleSlicePosition to set
	 */
	void setSampleSlicePosition(long sampleSlicePosition) {
		this.sampleSlicePosition = sampleSlicePosition;
	}

	/**
	 * The audio engine generates audio in slices of e.g. each 10 milliseconds
	 * (see getSampleSliceSize()). The currently processed sample time is this
	 * value. After this slice is rendered, this value will jump by
	 * getSampleSliceSize().
	 * 
	 * @return the sample slice position in samples
	 * @see #getSliceSizeSamples()
	 */
	public long getSampleSlicePosition() {
		return sampleSlicePosition;
	}

	/** initialized with the system's nano time */
	private static final long startTimeNanos = System.nanoTime();

	/**
	 * @return the system time, expressed in samples since starting the Java VM
	 */
	final long getSampleSystemTime() {
		long us = (System.nanoTime() - startTimeNanos) / 1000L;
		return (long) (us / 1000000.0 * sampleRate);
	}

	/** the system time of the known sample time, in samples */
	long knownSampleSystemTime = -1;

	/** the slice position at the known sample system time, in samples */
	long knownSampleSlicePos = -1;

	/**
	 * remember the time (in samples) of the last write operation to the
	 * soundcard
	 */
	private long lastWriteTime = 0;

	/**
	 * called by AudioOutput whenever a buffer was just written to the
	 * soundcard. A heuristic tries to guess when the buffer will actually be
	 * played, for getSamplePlaybackPosition()
	 */
	final void bufferWrittenToOutput() {
		long newTime = getSampleSystemTime();
		if (knownSampleSystemTime < 0
				|| (newTime - lastWriteTime >= sampleSliceSize)) {
			knownSampleSystemTime = newTime;
			knownSampleSlicePos = sampleSlicePosition;
		}
		lastWriteTime = newTime;
	}

	/**
	 * The number of samples that are rendered at once in the audio engine.
	 * 
	 * @return the slice size in samples
	 */
	public int getSliceSizeSamples() {
		return sampleSliceSize;
	}

	/**
	 * The duration of the the sample slice in milliseconds.
	 * 
	 * @return the sample slice size in milliseconds
	 */
	public double getSliceSizeMillis() {
		return AudioUtils.frames2MillisD(sampleSliceSize, sampleRate);
	}

	/**
	 * @param sampleSliceSize the slice size in samples to set
	 */
	void setSliceSize(int sampleSliceSize) {
		this.sampleSliceSize = sampleSliceSize;
	}

	/**
	 * @return the start time of the loop region, in samples. Use AudioPlayer to
	 *         set the loop region.
	 */
	public long getLoopStartSamples() {
		return loopStart;
	}

	/**
	 * Get the duration of the loop region, in samples (which is loopEnd -
	 * loopStart). If the returned value is 0, there is no loop region. Use
	 * AudioPlayer to set the loop region.
	 * 
	 * @return the duration of the loop region, in samples.
	 */
	public long getLoopDurationSamples() {
		return loopEnd - loopStart;
	}

	/**
	 * Get the end time of the loop region, in samples (the last sample is not
	 * played). Use AudioPlayer to set the loop region.
	 * 
	 * @return the end time of the loop region, in samples
	 */
	public long getLoopEndSamples() {
		return loopEnd;
	}

	/**
	 * apply the new loop start and end time. Set both to the same value to
	 * remove the loop region, unless a minimum beat time is defined above.
	 */
	void setLoopSamples(long start, long end) {
		if (start < 0) {
			start = 0;
		}
		if (end < 0) {
			end = 0;
		}
		if (end < start) {
			long h = end;
			end = start;
			start = h;
		}
		if (end - start < MIN_LOOP_SAMPLES) {
			end = start;
		}
		if (MIN_LOOP_BEATS > 0 && (end - start < beat2sample(MIN_LOOP_BEATS))) {
			end = start + beat2sample(MIN_LOOP_BEATS);
		}
		if (this.loopStart != start || this.loopEnd != end) {
			long oldStart = this.loopStart;
			long oldEnd = this.loopEnd;
			this.loopStart = start;
			this.loopEnd = end;
			for (StateListener tl : stateListeners) {
				try {
					tl.loopChanged(oldStart, oldEnd, start, end);
				} catch (Throwable t) {
					Debug.debug(t);
				}
			}
		}
	}

	/**
	 * Return if looping is enabled or not. Use AudioPlayer to enable or disable
	 * looping.
	 * 
	 * @return if looping is enabled
	 */
	public boolean isLoopEnabled() {
		return loopEnabled;
	}

	/**
	 * @param loopEnabled the loopEnabled to set
	 */
	void setLoopEnabled(boolean loopEnabled) {
		if (this.loopEnabled != loopEnabled) {
			this.loopEnabled = loopEnabled;
			// fire loop change event
			for (StateListener tl : stateListeners) {
				try {
					tl.loopChanged(loopStart, loopEnd, loopStart, loopEnd);
				} catch (Throwable t) {
					Debug.debug(t);
				}
			}
		}
	}

	/**
	 * @return the beatsPerMeasure, e.g. 4 for a 4/4 measure
	 */
	public int getBeatsPerMeasure() {
		return beatsPerMeasure;
	}

	/**
	 * @param beatsPerMeasure the beatsPerMeasure to set, e.g. 4 for a 4/4
	 *            measure
	 */
	void setBeatsPerMeasure(int beatsPerMeasure) {
		this.beatsPerMeasure = beatsPerMeasure;
	}

	/**
	 * @return the tempo in bpm
	 */
	public double getTempo() {
		return tempo;
	}

	/**
	 * Set a new tempo. If there are any tempo listeners registered, they will
	 * be called synchronously from within this method call.
	 * 
	 * @param tempo the tempo to set in bpm
	 */
	public void setTempo(double tempo) {
		if (tempo < TEMPO_MIN) {
			tempo = TEMPO_MIN;
		} else if (tempo > TEMPO_MAX) {
			tempo = TEMPO_MAX;
		}
		if (this.tempo != tempo) {
			this.tempo = tempo;
			for (StateListener tl : stateListeners) {
				try {
					tl.tempoChanged();
				} catch (Throwable t) {
					Debug.debug(t);
				}
			}
		}
	}

	/**
	 * The default is to display the time display in beats. If this is changed
	 * to false, the time is displayed as min:sec.tenths.
	 * 
	 * @return true if time is displayed in beats
	 */
	public boolean isTimeDisplayInBeats() {
		return timeDisplayInBeats;
	}

	/**
	 * The default is to display the time display in beats. Use this function to
	 * change to a display in min:sec.tenths. Changing this value will cause a
	 * synchronous displayModeChanged() event to registered listeners.
	 * 
	 * @param timeDisplayInBeats if display is in beats
	 */
	public void setTimeDisplayInBeats(boolean timeDisplayInBeats) {
		if (this.timeDisplayInBeats != timeDisplayInBeats) {
			this.timeDisplayInBeats = timeDisplayInBeats;
			for (StateListener tl : stateListeners) {
				try {
					tl.displayModeChanged();
				} catch (Throwable t) {
					Debug.debug(t);
				}
			}
		}
	}

	/**
	 * Register the specified state listener to receive synchronous tempo/loop
	 * events
	 * 
	 * @param tl the state listener to register
	 */
	public void addStateListener(StateListener tl) {
		if (tl != null) {
			stateListeners.add(tl);
		}
	}

	/**
	 * Unregister the specified state listener to not receive tempo/loop events
	 * anymore.
	 * 
	 * @param tl the state listener to remove
	 */
	public void removeStateListener(StateListener tl) {
		if (tl != null) {
			stateListeners.remove(tl);
		}
	}

	/**
	 * @return the audio file factory
	 */
	public AudioFileFactory getAudioFileFactory() {
		return audioFileFactory;
	}

	/**
	 * Only AudioFileFactory itself should ever call this method.
	 * 
	 * @param audioFileFactory the audioFileFactory to set
	 */
	void setAudioFileFactory(AudioFileFactory audioFileFactory) {
		this.audioFileFactory = audioFileFactory;
	}

	/**
	 * @return the single instance of the automation events automationDispatcher
	 */
	public AutomationEventDispatcher getAutomationEventDispatcher() {
		return automationDispatcher;
	}

	/**
	 * @return the single instance of the audio event dispatcher
	 */
	public AudioEventDispatcher getAudioEventDispatcher() {
		return audioDispatcher;
	}

	/**
	 * @return the masterVolume as a linear factor [0..1]
	 */
	public double getMasterVolume() {
		return masterVolume;
	}

	/**
	 * @param masterVolume the masterVolume to set as a linear factor [0..1]
	 */
	public void setMasterVolume(double masterVolume) {
		this.masterVolume = masterVolume;
	}

	/**
	 * @return the masterVolume in decibels [-inf..0]
	 */
	public double getMasterVolumeDB() {
		return AudioUtils.linear2decibel(masterVolume);
	}

	/**
	 * @param decibels the masterVolume to set in decibels [-inf..0]
	 */
	public void setMasterVolumeDB(double decibels) {
		this.masterVolume = AudioUtils.decibel2linear(decibels);
	}

	/** interface for listeners of tempo and loop changes */
	public interface StateListener {
		/**
		 * this method is called synchronously by the AudioState when the
		 * state's tempo changes
		 */
		public void tempoChanged();

		/**
		 * This method is called synchronously when the TImeDisplayInBeats
		 * property changes.
		 */
		public void displayModeChanged();

		/**
		 * This method is called synchronously by the AudioState when the
		 * state's loop change. The duration can be calculated with loopEnd -
		 * loopStart. This method is also called when looping is enabled or
		 * disabled, in that case oldStart=newStart and oldEnd=newEnd.
		 * 
		 * @param oldStart the old loop start position in samples
		 * @param oldEnd the old loop end position in samples
		 * @param newStart the new loop start position in samples
		 * @param newEnd the new loop end position in samples
		 */
		public void loopChanged(long oldStart, long oldEnd, long newStart,
				long newEnd);
	}

}
