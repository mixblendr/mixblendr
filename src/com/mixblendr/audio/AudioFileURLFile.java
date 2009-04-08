/**
 *
 */
package com.mixblendr.audio;

import static com.mixblendr.util.Debug.debug;
import static com.mixblendr.util.Debug.error;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;

import javax.sound.sampled.AudioFormat;

import com.mixblendr.util.Debug;

/**
 * Implementation of AudioFileURL that stores the audio data in a temporary file
 * on hard disk.
 * 
 * @author Florian Bomers
 */
public class AudioFileURLFile extends AudioFileURL {

	public static final boolean SCRAMBLE_DATA = true;

	public static final boolean INHIBIT_PLAYBACK_DURING_DOWNLOAD = false;

	/**
	 * if using a memory cache, physical reads will be in units of
	 * MEM_CACHE_SIZE
	 */
	private static final boolean USE_MEM_CACHE = false;

	private static final int MEM_CACHE_SIZE = 8 * 1024;

	public static boolean DEBUG = false;

	/** Prefix for temporary audio files created from downloaded streams */
	public static final String TEMP_FILE_PREFIX = "mixblendr";
	/** Suffix for temporary audio files created from downloaded streams */
	public static final String TEMP_FILE_SUFFIX = SCRAMBLE_DATA ? ".dat"
			: ".pcm";

	/** where the temp file resides */
	private File cacheFile;

	private RandomAccessFile writeFile;

	private RandomAccessFile readFile;

	/** a magic used for scrambling */
	private int scrambleMagic;

	private byte[] memCache = null;
	private int memCacheFilled = 0;
	private long memCachePos = 0;

	/**
	 * Create a new AudioFile instance from the given URL.
	 * <p>
	 * Note: you should use the AudioFileFactory factory to create audio file
	 * objects.
	 * 
	 * @param state the audio state object
	 * @param url the URL from which to load this audio file
	 */
	public AudioFileURLFile(AudioState state, URL url) {
		super(state, url);
		cacheFile = null;
		writeFile = null;
		readFile = null;
		scrambleMagic = (int) (Math.random() * Integer.MAX_VALUE) + 1552;
		if (INHIBIT_PLAYBACK_DURING_DOWNLOAD) {
			setCanPlayBeforeFullyLoaded(false);
		}
		if (USE_MEM_CACHE) {
			// use different memory cache sizes to reduce bursts of HD reads
			// during playback
			memCache = new byte[(int) (MEM_CACHE_SIZE * (Math.random() + 0.5))];
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioFile#closeImpl()
	 */
	@Override
	protected synchronized void closeImpl() {
		closeReadFile();
		closeWriteFile();
		super.closeImpl();
		if (cacheFile != null) {
			if (cacheFile.exists()) {
				boolean deleted = cacheFile.delete();
				if (!deleted && DEBUG) {
					Debug.error("could not delete temp file: "
							+ cacheFile.getName());
				} else {
					Debug.debug("deleted temp file: " + cacheFile.getName());
				}
			}
			cacheFile = null;
		}
	}

	/** close the temporary file opened for reading */
	private void closeReadFile() {
		if (readFile != null) {
			try {
				readFile.close();
			} catch (Exception e) {
				debug(e);
			}
			readFile = null;
		}
	}

	/** close the temporary file opened for writing during download */
	private void closeWriteFile() {
		if (writeFile != null) {
			try {
				writeFile.close();
			} catch (Exception e) {
				debug(e);
			}
			writeFile = null;
		}
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
		this.cacheFile = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
		cacheFile.deleteOnExit();
		writeFile = new RandomAccessFile(cacheFile, "rw");
		super.init(format, fileSize);
		if (DEBUG) {
			Debug.debug(getName() + ": created "
					+ (SCRAMBLE_DATA ? "scrambled " : "") + "temp file: "
					+ cacheFile.getName());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.mixblendr.audio.AudioFileURL#downloadData(byte[], int, int)
	 */
	@Override
	boolean downloadData(byte[] data, int offset, int length) throws Exception {
		long filePointer = writeFile.getFilePointer();
		if (getFileSize() >= 0 && filePointer + length > getFileSize()) {
			length = (int) (getFileSize() - writeFile.getFilePointer());
		}
		if (length > 0) {
			if (SCRAMBLE_DATA) {
				scramble(filePointer, data, offset, length);
			}
			writeFile.write(data, offset, length);
			if (SCRAMBLE_DATA) {
				// FIXME: use a temp buffer to prevent unscrambling
				unscramble(filePointer, data, offset, length);
			}
			downloadUpdate(data, offset, length);
			return true;
		}
		return false;
	}

	/**
	 * called by the download thread when the last chunk of data was written to
	 * the temporary file. This value may be different from the intial value of
	 * FileSize. This method will notify the listeners with the downloadEnd
	 * event.
	 */
	@Override
	void downloadEnd() {
		closeWriteFile();
		super.downloadEnd();
	}

	/**
	 * Read from the temporary raw file (which is opened on demand).
	 * 
	 * @see com.mixblendr.audio.AudioFile#read(long, byte[], int, int)
	 */
	@Override
	protected synchronized int read(long pos, byte[] buffer, int offset,
			int length) {
		if (!hasDownloadStarted() || cacheFile == null) {
			return 0;
		}
		if (readFile == null) {
			try {
				readFile = new RandomAccessFile(cacheFile, "r");
			} catch (Exception e) {
				if (cacheFile != null) {
					cacheFile.delete();
					cacheFile = null;
				}
				error(e);
				// TODO: notify User?
			}
		}
		if (readFile == null) {
			return 0;
		}
		try {
			int ret = 0;
			if (USE_MEM_CACHE) {
				// read-ahead caching to prevent drop-outs.
				// first, try to read as much as possible from cache
				long thisPos = pos;
				int thisOffset = offset;
				int thisLength = length;

				for (int i = 0; i < 2; i++) {
					if (thisPos >= memCachePos
							&& thisPos <= (memCachePos + memCacheFilled)) {
						int canCopy = (int) (memCachePos + memCacheFilled - thisPos);
						if (canCopy > thisLength) {
							canCopy = thisLength;
						}
						System.arraycopy(memCache,
								(int) (thisPos - memCachePos), buffer,
								thisOffset, canCopy);
						thisPos += canCopy;
						thisOffset += canCopy;
						thisLength -= canCopy;
						ret += canCopy;
					}
					if (thisLength > 0) {
						// need to read from HD
						readFile.seek(thisPos);
						memCacheFilled = readFile.read(memCache, 0,
								memCache.length);
						memCachePos = thisPos;
						if (false && DEBUG) {
							Debug.debug("Read " + memCacheFilled
									+ " from file at pos " + thisPos);
						}
					} else {
						break;
					}
				}
				if (false && DEBUG && ret < length) {
					Debug.debug("Only read " + ret + " bytes instead of "
							+ length + " bytes!");
				}
			} else {
				readFile.seek(pos);
				ret = readFile.read(buffer, offset, length);
			}
			if (ret > 0) {
				if (SCRAMBLE_DATA) {
					unscramble(pos, buffer, offset, ret);
				}
				return ret;
			}
		} catch (IOException ioe) {
			error(ioe);
			// TODO: notify user?
		}
		return 0;
	}

	/**
	 * Scramble the given buffer in place.
	 * 
	 * @param pos the file position
	 * @param buffer the buffer to scramble in place
	 * @param offset the byte offset in buffer
	 * @param length the number of bytes to scramble
	 */
	private final void scramble(long pos, byte[] buffer, int offset, int length) {
		int max = offset + length;
		int iPos = (int) pos + 976235;
		int thisScrambleMagic = scrambleMagic - 12348;
		for (; offset < max; offset++) {
			buffer[offset] ^= (byte) (iPos * thisScrambleMagic);
			iPos++;
		}
	}

	/**
	 * Unscramble the given buffer in place. The current implementation will use
	 * the same algorithm as scrambling, so it just calls scramble().
	 * 
	 * @param pos the file position
	 * @param buffer the buffer to unscramble in place
	 * @param offset the offset in buffer
	 * @param length the number of bytes to unscramble
	 */
	private void unscramble(long pos, byte[] buffer, int offset, int length) {
		scramble(pos, buffer, offset, length);
	}

	/**
	 * Determine if we have read/write access to temporary files
	 * 
	 * @return true if we can read/write files
	 */
	public static boolean isFileSystemAccessible() {
		try {
			File cacheFile = File.createTempFile(TEMP_FILE_PREFIX,
					TEMP_FILE_SUFFIX);
			if (cacheFile.delete()) {
				return true;
			}
		} catch (Exception e) {
		}
		return false;
	}

}
