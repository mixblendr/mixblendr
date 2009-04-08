/**
 *
 */
package com.mixblendr.audio;

import java.util.ArrayList;
import java.util.List;

import com.mixblendr.util.FatalExceptionListener;
import com.mixblendr.gui.main.Globals;
import com.mixblendr.gui.main.Main;

/**
 * Main player class that owns all the other objects like Mixer, State,
 * AudioOutput, AudioFileFactory. Also, a number of convenience methods.
 * 
 * @author Florian Bomers
 */
public class AudioPlayer {

	public static final boolean INHIBIT_PLAYBACK_DURING_DOWNLOAD = false;

	private AudioState state;
	private AudioOutput output;
	private AudioMixer mixer;
	private AudioFileFactory factory;
	private List<Listener> listeners;
	private FatalExceptionListener fel;
    //private Main fel;

    private static List<AudioPlayer> instances = new ArrayList<AudioPlayer>();




    /**
	 * Create an instance of AudioPlayer and create the state object. You must
	 * call init() to intialize the AudioOutput, AudioMixer, AudioFileFactory
	 * classes.
	 */
	public AudioPlayer(Main fel) {
		this(fel, null);
        
    }

	/**
	 * Create an instance of AudioPlayer and create the state object. You must
	 * call init() to intialize the AudioOutput, AudioMixer, AudioFileFactory
	 * classes.
	 */
	public AudioPlayer(FatalExceptionListener fel,
			AudioFileDownloadListener listener) {
		super();
		state = new AudioState();
		listeners = new ArrayList<Listener>();
		factory = new AudioFileFactory(state, listener);
		this.fel = fel;
		instances.add(this);
	}

	/**
	 * call this to initialize the AudioOutput, AudioMixer, AudioFileFactory
	 * classes.
	 */
	public void init() {

        output = new AudioOutput(this, state);

        mixer = new AudioMixer(state);
		output.setInput(mixer);
		output.setFatalExceptionListener(fel);

    }

	/**
	 * @return the factory
	 */
	public AudioFileFactory getFactory() {
		return factory;
	}

	/**
	 * @return the mixer
	 */
	public AudioMixer getMixer() {
		return mixer;
	}

	/**
	 * @return the output
	 */
	public AudioOutput getOutput() {
		return output;
	}

	/**
	 * @return the state
	 */
	public AudioState getState() {
		return state;
	}

	/** register the specified listener to receive player events */
	public void addListener(Listener l) {
		if (l != null) {
			listeners.add(l);
		}
	}

	/** unregister the specified listener */
	public void removeListener(Listener l) {
		listeners.remove(l);
	}

	/** the static lag from writing to audio device to when it's heard */
	private long audioSampleLag = 0;

	/**
	 * start playback. If INHIBIT_PLAYBACK_DURING_DOWNLOAD is true, it is not
	 * started if there is any download in progress.
	 */
	public synchronized void start() throws Exception {
		if (!isStarted()) {
			if (INHIBIT_PLAYBACK_DURING_DOWNLOAD
					&& AudioFileDownloader.getInstance().isDownloading()) {
				return;
			}
			// reset calculation for interpolating the sample position
			state.knownSampleSystemTime = -1;
            output.start();
			audioSampleLag = output.getSampleLag();
			for (Listener l : listeners) {
				l.onPlaybackStart(this);
			}
		}
	}

    

//    public void setSaveToFile(boolean value)
//    {
//        if (output != null) {
//            output.setPlaying(false);
//        }
//    }

    /** pause playback */
	public void stop(boolean immediate) {
		if (isStarted()) {
            //output.stop(immediate);
            output.stop(true);
            flushPeakCaches();
            for (Listener l : listeners) {
                l.onPlaybackStop(this, immediate);
            }
        }
	}

	/**
	 * stop all currently playing players. This method is used for stopping
	 * playback if a download is started.
	 * 
	 * @see #INHIBIT_PLAYBACK_DURING_DOWNLOAD
	 */
	static void stopAllPlayers() {
		for (AudioPlayer ap : instances) {
			ap.stop(false);
		}
	}

	/**
	 * stop playback, close output device, clear and remove mixer references,
	 * and close the audio file factory
	 */
	public void close() {
		stop(true);
		output.close();
		mixer.clear();
		factory.close();
		instances.remove(this);
	}

	public boolean isStarted() {
		return output.isStarted();
	}

	/** flush the peak arrays of all tracks */
	private void flushPeakCaches() {
		for (int i = 0; i < mixer.getTrackCount(); i++) {
			mixer.getTrack(i).flushPeakCache();
		}
	}

	/** convenience method for creating a new track and adding it to the mixer */
	public AudioTrack addAudioTrack() {
		AudioTrack at = new AudioTrack(state);
		mixer.addTrack(at);
		return at;
	}

	/** convenience method for removing a track */
	public void removeAudioTrack(AudioTrack at) {
		if (at != null) {
			mixer.removeTrack(at);
		}
	}

	/**
	 * return an interpolated exact position in samples. It uses the last sample
	 * slice time and calculates the time difference using System.nanoTime().
	 */
	private final long getSamplePlaybackPosition() {
		if (state.knownSampleSlicePos < 0 || !isStarted()) {
			return state.getSampleSlicePosition();
		}
		long ret = state.getSampleSystemTime() - state.knownSampleSystemTime
				+ state.knownSampleSlicePos - audioSampleLag;
		if (ret < 0) {
			return 0;
		}
		return ret;
	}

	/** return the current playback position, as accurate as possible */
	public long getPositionSamples() {
		// this is inaccurate!
		// return state.getSampleSlicePosition();
		// use interpolated position
		return getSamplePlaybackPosition();
	}

	/**
	 * wind to the specified sample position. During playback, the new position
	 * will be set asynchronously
	 * 
	 * @param pos the new playback position in samples
	 */
	public void setPositionSamples(long pos) {
		if (pos < 0) {
			pos = 0;
		}
		if (state.getSampleSlicePosition() != pos) {
			if (isStarted()) {
				mixer.setRequestedPlaybackPosition(pos);
			} else {
				state.setSampleSlicePosition(pos);
				for (Listener l : listeners) {
					l.onPlaybackPositionChanged(this, pos);
				}
			}
			flushPeakCaches();
		}
	}

	/**
	 * @return true if looping is enabled
	 */
	public boolean isLoopEnabled() {
		return state.isLoopEnabled();
	}

	/**
	 * Set to to enable looping
	 */
	public void setLoopEnabled(boolean enable) {
		state.setLoopEnabled(enable);
	}

	/**
	 * Set the loop region in samples.
	 * 
	 * @param start the start sample of the looped portion
	 * @param duration the number of samples in the looped region
	 * @throws IllegalArgumentException if duration or start are negative
	 */
	public void setLoopSamples(long start, long duration) {
		if (duration < 0) {
			throw new IllegalArgumentException("duration is negative");
		}
		if (start < 0) {
			throw new IllegalArgumentException("start position is negative");
		}
		state.setLoopSamples(start, start + duration);
	}

	/**
	 * Set the current tempo in beats per minute. This setting will not modify
	 * actual playback speed, it just changes the way samples are converted to
	 * bars and beats.
	 * 
	 * @param tempo the tempo in beats per minute.
	 */
	public void setTempo(double tempo) {
		state.setTempo(tempo);
	}

	/** interface to be implemented by player listeners */
	public interface Listener {
		/**
		 * this event is called to registered listeners when playback starts.
		 * This event is called synchronously in the context of the thread
		 * calling the start method.
		 */
		public void onPlaybackStart(AudioPlayer player);

		/**
		 * this event is called to registered listeners when playback stops.
		 * This event is called synchronously in the context of the thread
		 * calling the stop method.
		 */
		public void onPlaybackStop(AudioPlayer player, boolean immediate);

		/**
		 * this event is called to registered listeners when the sample position
		 * is changed in non-playback mode This event is called synchronously in
		 * the context of the thread calling the setSamplePosition() method.
		 */
		public void onPlaybackPositionChanged(AudioPlayer player, long samplePos);
	}

}
