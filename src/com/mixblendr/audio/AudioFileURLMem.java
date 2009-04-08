/**
 *
 */
package com.mixblendr.audio;

import static com.mixblendr.util.Debug.debug;

import java.net.URL;

import javax.sound.sampled.AudioFormat;

/**
 * A specific implementation of AudioFileURL that keeps the files in memory.
 * 
 * @author Florian Bomers
 */
public class AudioFileURLMem extends AudioFileURL {

	private final static boolean TRACE = false;

	/** the array for storing the audio data */
	private byte[] mem = null;

	private int writePos = 0;

	/**
	 * Create a new AudioFile instance from the given URL.
	 * <p>
	 * Note: you should use the AudioFileFactory factory to create audio file
	 * objects.
	 * 
	 * @param state the audio state object
	 * @param url the URL from which to load this audio file
	 */
	public AudioFileURLMem(AudioState state, URL url) {
		super(state, url);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioFile#closeImpl()
	 */
	@Override
	protected synchronized void closeImpl() {
		mem = null;
		super.closeImpl();
	}

	/**
	 * Called by the download thread when the first chunk of the audio file is
	 * successfully downloaded. This method will notify listeners of start of
	 * the download.
	 * 
	 * @param format the audio format of the currently downloaded file
	 * @param fileSize if known, the total file size in bytes, otherwise -1
	 */
	@Override
	void init(AudioFormat format, long fileSize) throws Exception {
		writePos = 0;
		if (fileSize > 0) {
			// maximum size for arrays
			if (fileSize > Integer.MAX_VALUE) {
				fileSize = Integer.MAX_VALUE;
			}
			mem = new byte[(int) fileSize];
			if (TRACE) {
				debug(getName() + ": allocated memory with " + fileSize
						+ " bytes");
			}
		}
		super.init(format, fileSize);
	}

	/** the amount by which the memory size is grown. Currently 64KB */
	private final static int GROW_SIZE = 64 * 1024;

	/** grow the mem array */
	private void grow(int minGrow) {
		int memLength = 0;
		if (mem != null) {
			memLength = mem.length;
		}
		int size = (int) getFileSize();
		if (size < 0) {
			if (minGrow > GROW_SIZE) {
				size = minGrow + memLength;
			} else {
				size = GROW_SIZE + memLength;
			}
		}
		if (mem == null || mem.length < size) {
			byte[] newMem = new byte[size];
			if (mem != null && writePos > 0) {
				System.arraycopy(mem, 0, newMem, 0, writePos);
			}
			mem = newMem;
			if (TRACE) {
				debug(getName() + ": (re)allocated memory with "
						+ (size / 1024) + " KB");
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioFileURL#downloadData(byte[], int, int)
	 */
	@Override
	boolean downloadData(byte[] data, int offset, int length) throws Exception {
		long max = getFileSize();
		if (max >= 0 && ((getAvailableBytes() + length) > max)) {
			length = (int) (max - getAvailableBytes());
		}
		if (length > 0) {
			if (mem == null
					|| (max < 0 && ((getAvailableBytes() + length) > mem.length))) {
				grow(length);
			}
			if (writePos + length > mem.length) {
				length = mem.length - writePos;
			}
			if (length > 0) {
				System.arraycopy(data, offset, mem, writePos, length);
			}
			writePos += length;
			downloadUpdate(data, offset, length);
			return true;
		}
		return false;
	}

	/**
	 * Read from the raw mem data
	 * 
	 * @see com.mixblendr.audio.AudioFile#read(long, byte[], int, int)
	 */
	@Override
	protected synchronized int read(long pos, byte[] buffer, int offset,
			int length) {
		if (!hasDownloadStarted() || mem == null) {
			return 0;
		}
		if (pos + length > mem.length) {
			length = (int) (mem.length - pos);
		}
		if (length > 0) {
			System.arraycopy(mem, (int) pos, buffer, offset, length);
			return length;
		}
		return 0;
	}
}
