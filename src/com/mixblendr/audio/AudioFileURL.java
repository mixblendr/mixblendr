/**
 *
 */
package com.mixblendr.audio;

import java.net.*;
import java.util.*;
import javax.sound.sampled.*;
import static com.mixblendr.util.Debug.*;
import static com.mixblendr.util.Utils.*;

/**
 * Descendant of AudioFile which loads the file from the specified URL, saves it
 * to a temporary raw audio file on the user's hard disk and allows random
 * access in the file.
 * 
 * @author Florian Bomers
 */
public abstract class AudioFileURL extends AudioFile {

	private final static boolean TRACE = false;

	private URL url;

	private List<Listener> listeners;

	private boolean downloadEnd = false;

	/**
	 * Create a new AudioFile instance from the given URL. Note: you should use
	 * the AudioFileFactory factory to create audio file objects.
	 * 
	 * @param state the audio state object
	 * @param url the URL from which to load this audio file
	 */
	public AudioFileURL(AudioState state, URL url) {
		super(state, getBaseName(url.getPath()), url.toString());
		this.url = url;
		AudioFileDownloader.getInstance().addJob(this);
	}

	/**
	 * add a listener for the download events. If the download has already
	 * started, a downloadStart event is sent. If download has already ended, a
	 * downloadEnd is immediately sent.
	 */
	public void addListener(Listener l) {
		if (listeners == null) {
			listeners = new ArrayList<Listener>();
		}
		synchronized (listeners) {
			listeners.add(l);
		}
		if (hasDownloadStarted() && !downloadEnd) {
			l.audioFileDownloadStart(this);
		} else if (downloadEnd) {
			l.audioFileDownloadEnd(this);
		}
	}

	/** remove the listener for the download events */
	public void removeListener(Listener al) {
		if (listeners != null) {
			synchronized (listeners) {
				listeners.remove(al);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioFile#closeImpl()
	 */
	@Override
	protected synchronized void closeImpl() {
		AudioFileDownloader.getInstance().killJob(this);
	}

	/**
	 * @return the url
	 */
	public URL getURL() {
		return url;
	}

	/**
	 * @return true if the download has started, is in progress, or has already
	 *         terminated.
	 */
	public boolean hasDownloadStarted() {
		return (getFormat() != null);
	}

	/**
	 * @return the progress of downloading, espressed as a percentage, 0..100,
	 *         or -1 if the file size is not known.
	 */
	public int getDownloadPercent() {
		if (!hasDownloadStarted() || getFileSize() < 0) {
			return -1;
		} else if (isFullyLoaded()) {
			return 100;
		} else if (getFileSize() == 0) {
			return 100;
		}
		return (int) (getAvailableBytes() * 100 / getFileSize());
	}

	/** @return if this audio file object is fully loaded */
	@Override
	public boolean isFullyLoaded() {
		return downloadEnd;
	}

	/**
	 * Called by the download thread before starting the actual download. This
	 * method will notify listeners of start of the download.
	 * 
	 * @param format the audio format of the currently downloaded file
	 * @param fileSize if known, the total file size in bytes, otherwise -1
	 * @throws Exception when a non-recoverable error occurs
	 */
	void init(AudioFormat format, long fileSize) throws Exception {
		setFormat(format);
		if (fileSize < 0) {
			fileSize = -1;
		}
		setFileSize(fileSize);
		setAvailableBytes(0);
		downloadEnd = false;
		if (listeners != null) {
			// prevent deadlock by using a local copy of the listeners
			Listener[] lListeners;
			synchronized (listeners) {
				lListeners = listeners.toArray(new Listener[listeners.size()]);
			}
			// allow listeners to remove themselves during the event handler
			for (Listener l : lListeners) {
				l.audioFileDownloadStart(this);
			}
		}
		debug(getName() + ": download started. FileSize=" + fileSize
				+ " bytes, format=" + format);
	}

	/**
	 * called by the implementation of downloadData() when more data comes
	 * available. This method will update the available size and notify the
	 * listeners with the downloadUpdate event.
	 * <p>
	 * If a peak cache is used, it's updated with the data.
	 * 
	 * @param data the new byte data
	 * @param offset the offset in data
	 * @param newDownloadedBytes the new number of downloaded bytes
	 */
	protected void downloadUpdate(byte[] data, int offset,
			long newDownloadedBytes) {
		updatePeakCache(getAvailableBytes(), data, offset,
				(int) newDownloadedBytes);
		setAvailableBytes(getAvailableBytes() + newDownloadedBytes);
		if (listeners != null) {
			// prevent deadlock by using a local copy of the listeners
			Listener[] lListeners;
			synchronized (listeners) {
				lListeners = listeners.toArray(new Listener[listeners.size()]);
			}
			// allow listeners to remove themselves during the event handler
			for (Listener l : lListeners) {
				l.audioFileDownloadUpdate(this);
			}
		}
		if (TRACE) {
			debug(getName() + ": " + getDownloadPercent() + "%");
		}
	}

	/**
	 * Called by AudioFileDownloader whenever a new chunk of data is available.
	 * Implementations must call downloadUpdate() with the length as parameter.
	 * The implementation should not update the available sample count manually.
	 * 
	 * @param data the new audio data
	 * @param offset the offset in data where actual data starts
	 * @param length the number of bytes available in data
	 * @return true if everything OK, false if download should be finished now
	 * @throws Exception when a non-recoverable error occurs
	 */
	abstract boolean downloadData(byte[] data, int offset, int length)
			throws Exception;

	/**
	 * called by the download thread when an error occured when trying to
	 * download or during download. downloadEnd() will still be called
	 * afterwards. This method will notify the listeners with the downloadError
	 * event.
	 */
	void downloadError(Throwable t) {
		getState().getAudioEventDispatcher().dispatchDownloadError(this, t);
		if (listeners != null) {
			// prevent deadlock by using a local copy of the listeners
			Listener[] lListeners;
			synchronized (listeners) {
				lListeners = listeners.toArray(new Listener[listeners.size()]);
			}
			// allow listeners to remove themselves during the event handler
			for (Listener l : lListeners) {
				l.audioFileDownloadError(this);
			}
		}
	}

	/**
	 * called by the download thread when the last chunk of data was written to
	 * the temporary file. This value may be different from the intial value of
	 * FileSize. This method will notify the listeners with the downloadEnd
	 * event.
	 */
	void downloadEnd() {
		debug(getName() + ": download end. FileSize supposed to be ="
				+ getFileSize() + " bytes, actual=" + getAvailableBytes());
		setFileSize(getAvailableBytes());
		downloadEnd = true;
		// notify the listeners of download end
		if (listeners != null) {
			// prevent deadlock by using a local copy of the listeners
			Listener[] lListeners;
			synchronized (listeners) {
				lListeners = listeners.toArray(new Listener[listeners.size()]);
			}
			// allow listeners to remove themselves during the event handler
			for (Listener l : lListeners) {
				l.audioFileDownloadEnd(this);
			}
		}
	}

	/**
	 * package private listener for sending notifications about the download
	 * progress to owners
	 */
	public interface Listener {
		/**
		 * called when download starts
		 */
		void audioFileDownloadStart(AudioFile source);

		/**
		 * called when new data is available
		 */
		void audioFileDownloadUpdate(AudioFile source);

		/**
		 * called when an error occured during download. downloadEnd() will
		 * still be called afterwards.
		 */
		void audioFileDownloadError(AudioFile source);

		/**
		 * called when the last chunk of data is available.
		 */
		void audioFileDownloadEnd(AudioFile source);
	}

	@Override
	public String toString() {
		String dur;
		if (!hasDownloadStarted()) {
			return "FileURL: " + url.toString();
		}
		if (getDurationSamples() >= 0) {
			dur = "" + getState().sample2seconds(getDurationSamples()) + "s";
		} else {
			dur = "unknown";
		}
		return "FileURL " + getName() + ": duration=" + dur + ", available="
				+ getState().sample2seconds(getAvailableSamples()) + "s";
	}

}
