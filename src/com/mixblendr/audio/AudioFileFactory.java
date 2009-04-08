/**
 *
 */
package com.mixblendr.audio;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import com.mixblendr.util.Debug;

/**
 * Factory class for managing a pool of AudioFile objects.
 * 
 * @author Florian Bomers
 */
public final class AudioFileFactory {

	private static boolean USE_ONLY_MEM_FILES = !AudioFileURLFile.isFileSystemAccessible();
	
	static {
		if (USE_ONLY_MEM_FILES) {
			Debug.error("using in-memory files");
		}
	}
	
	private AudioState state;
	private List<AudioFile> audioFiles;

    public List<AudioFile> getAudioFiles()
    {
        return audioFiles;
    }

    /**
	 * prevent instanciation without state object
	 */
	private AudioFileFactory() {
		super();
		audioFiles = new ArrayList<AudioFile>();
	}

	/**
	 * Create a new AudioManager instance. It will register itself as the audio
	 * manager in the state object.
	 * 
	 * @param state
	 */
	AudioFileFactory(AudioState state) {
		this();
		this.state = state;
		state.setAudioFileFactory(this);
	}

	/**
	 * Create a new AudioManager instance. It will register itself as the audio
	 * manager in the state object. The listener will receive notifications
	 * about download progress.
	 * 
	 * @param state
	 */
	AudioFileFactory(AudioState state, AudioFileDownloadListener listener) {
		this(state);
		AudioFileDownloader.getInstance().setListener(listener);
	}

	/**
	 * Create/Retrieve the audio file object associated with this URL.
	 * 
	 * @param url
	 * @return the instance for audio data from the specified URL
	 */
	public AudioFile getAudioFile(URL url) {
		return getAudioFile(url, -1);
	}

	/**
	 * Create/Retrieve the audio file object associated with this URL. The
	 * duration parameter is only useful if the duration is known in advance.
	 * 
	 * @param url
	 * @param durationInSamples the duration for the file scaled to 44100Hz, or
	 *            -1 if not known.
	 * @return the instance for audio data from the specified URL
	 */
	public AudioFile getAudioFile(URL url, long durationInSamples) {
		String source = url.toString();
		AudioFile ret = findAudioFile(source);
		if (ret == null) {
			if (USE_ONLY_MEM_FILES) {
				ret = new AudioFileURLMem(state, url);
			} else {
				ret = new AudioFileURLFile(state, url);
			}
			if (durationInSamples >= 0) {
				// FIXME: need to set file size in bytes, not in samples...
				// ret.setFileSize(durationInSamples);
			}
			synchronized (audioFiles) {
				audioFiles.add(ret);
			}
		}
		return ret;
	}

	/**
	 * Create/Retrieve the audio file object associated from the given File.
	 * 
	 * @param file
	 * @return the instance for audio data from the specified file
	 */
	public AudioFile getAudioFile(File file) throws MalformedURLException,
			IOException {
		String source = file.getCanonicalPath();
		AudioFile ret = findAudioFile(source);
		if (ret == null) {
			URL url = file.toURL();
			if (USE_ONLY_MEM_FILES) {
				ret = new AudioFileURLMem(state, url);
			} else {
				ret = new AudioFileURLFile(state, url);
			}
			synchronized (audioFiles) {
				audioFiles.add(ret);
			}
		}
		return ret;
	}

	/** search the list of already created audio files for the given one */
	private AudioFile findAudioFile(String source) {
		for (AudioFile af : audioFiles) {
			if (af.getSource().equals(source)) {
				return af;
			}
		}
		return null;
	}

    public int getTrackNumber() {
        int result =0;
        if (audioFiles != null) {
            result = audioFiles.size();
        }
        return result;
    }




    /** close all open files */
	void close() {
		for (AudioFile af : audioFiles) {
			af.close();
		}
		audioFiles.clear();
	}

}
