/**
 *
 */
package com.mixblendr.audio;

import org.tritonus.share.sampled.FloatSampleBuffer;
import org.tritonus.share.sampled.FloatSampleTools;
import javax.sound.sampled.*;
import static com.mixblendr.util.Debug.*;

/**
 * Abstraction of an audio file, a generic source of audio data. The audio data
 * is raw, in the specified audio format. For now, the audio file must be PCM
 * and must have the same sample rate as the engine's sample rate. This is
 * achieved by converting the data during the download. The files can be in
 * mono, they'll be converted automatically.
 * 
 * @author Florian Bomers
 */
public abstract class AudioFile {

	private static final boolean DEBUG_PEAK_CACHE = false;
	
	private String name;

	private String source;

	private AudioState state;

	private AudioFormat format;

	private long fileSize;

	private long available;

	/** cached byte buffer to prevent re-instanciation of temporary byte buffers */
	private byte[] byteBuffer = null;

	private boolean usePeakCache = true;

	private AudioPeakCache peakCache;

	/** Determine if this file can be played before it's fully loaded */
	private boolean playBeforeFullyLoaded = true;

	/**
	 * private def constructor to prevent instanciation without state
	 */
	private AudioFile() {
		super();
		fileSize = -1;
	}

	/**
	 * Create a new AudioFile object and initialize the state object.
	 */
	protected AudioFile(AudioState state, String name, String source) {
		this();
		this.state = state;
		this.name = name;
		this.source = source;
	}

	/**
	 * close this audio file and release any resources. Will clean up and call
	 * closeImpl().
	 */
	public synchronized void close() {
		closeImpl();
		byteBuffer = null;
	}

	protected abstract void closeImpl();

	/** @return if this audio file object is fully loaded */
	public boolean isFullyLoaded() {
		return (available == fileSize);
	}

	/**
	 * @return true if this file can be played already during download
	 */
	public boolean canPlayBeforeFullyLoaded() {
		return playBeforeFullyLoaded;
	}

	/**
	 * This parameter can be set to false by overriding classes to inhibit playback while
	 * the file is still downloaded, i.e. as long as isFullyLoaded() returns
	 * false, the read() method of AudioFile will return silence.
	 * 
	 * @param canPlayBeforeFullyLoaded if this file can be played during
	 *            download
	 */
	protected void setCanPlayBeforeFullyLoaded(boolean canPlayBeforeFullyLoaded) {
		this.playBeforeFullyLoaded = canPlayBeforeFullyLoaded;
	}

	/**
	 * @return the file size in bytes, or -1 if not (yet) known
	 */
	public long getFileSize() {
		return fileSize;
	}

	/**
	 * @param fileSize the fileSize to set in bytes
	 */
	protected void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	/** @return the same as getDurationSamples, or -1 if not known (yet) */
	public long getFileSizeSamples() {
		return getDurationSamples();
	}

	/** @return the duration of this file in samples, or -1 if not known */
	public long getDurationSamples() {
		if (fileSize < 0 || format == null) {
			return -1;
		}
		return (fileSize / format.getFrameSize());
	}

	/**
	 * Return the number of actual bytes already available, from the beginning
	 * of the file.
	 * 
	 * @return the available bytes, or 0 if none available
	 */
	public long getAvailableBytes() {
		return available;
	}

	/**
	 * Return the number of actual samples already available, from the beginning
	 * of the file.
	 * 
	 * @return the available bytes, or 0 if none available
	 */
	public long getAvailableSamples() {
		if (format == null) {
			return 0;
		}
		return available / format.getFrameSize();
	}

	/**
	 * @param available the available to set in bytes
	 */
	protected void setAvailableBytes(long available) {
		this.available = available;
	}

	/**
	 * @return the duration in samples, or if the set duration is -1, the
	 *         available samples
	 */
	public long getEffectiveDurationSamples() {
		if (fileSize < 0) {
			return getAvailableSamples();
		}
		return getDurationSamples();
	}

	/**
	 * Retrieve the name, an arbitrary identifier of this audio file to be
	 * presented to the user. Usually, this will be the filename.
	 * 
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * A string with the complete source description of this file, e.g. a
	 * filename or a URL.
	 * 
	 * @return the source
	 */
	public String getSource() {
		return source;
	}

	/**
	 * @return the state
	 */
	public AudioState getState() {
		return state;
	}

	/**
	 * @return the format, or null if the audio data is not (yet) loaded
	 */
	public AudioFormat getFormat() {
		return format;
	}

	/**
	 * @param format the format to set
	 */
	public void setFormat(AudioFormat format) {
		this.format = format;
	}

	/**
	 * @return the peakCache
	 */
	public AudioPeakCache getPeakCache() {
		return peakCache;
	}

	/**
	 * @return the usePeakCache
	 */
	public boolean isUsingPeakCache() {
		return usePeakCache;
	}

	private FloatSampleBuffer peakCacheConversionBuffer;

	/**
	 * should be called by descendants when audio data is available to update
	 * the peak cache. If directly using files, this method or
	 * updatePeakCache(long, FloatSampleBuffer) should be called in a loop when
	 * initializing.
	 * 
	 * @see #updatePeakCache(long, FloatSampleBuffer)
	 */
	protected void updatePeakCache(long startByte, byte[] data, int offset,
			int length) {
		if (usePeakCache) {
			if (peakCacheConversionBuffer == null) {
				peakCacheConversionBuffer = new FloatSampleBuffer(data, offset,
						length, getFormat());
				if (DEBUG_PEAK_CACHE) {
					debug(this.name+": created peakCacheConversionBuffer with size "+peakCacheConversionBuffer.getSampleCount()+" samples.");
				}
			} else {
				peakCacheConversionBuffer.initFromByteArray(data, offset,
						length, getFormat());
			}
			updatePeakCache(startByte / format.getFrameSize(),
					peakCacheConversionBuffer);
		}
	}

	/**
	 * should be called by descendants when audio data is available to update
	 * the peak cache. If directly using files, this method or
	 * updatePeakCache(long, byte[], int, int) should be called in a loop when
	 * initializing.
	 * 
	 * @param startSample the sample where new audio data for the peak cache is
	 *            available
	 * @param audio the buffer containing the new audio data
	 * @see #updatePeakCache(long, byte[], int, int)
	 */
	private void updatePeakCache(long startSample, FloatSampleBuffer audio) {
		if (usePeakCache) {
			if (peakCache == null) {
				long fileSizeBytes = getFileSize();
				long fileSizeSamples;
				if (fileSizeBytes < 0) {
					fileSizeSamples = audio.getSampleCount();
				} else {
					fileSizeSamples = fileSizeBytes / format.getFrameSize();
				}
				if (DEBUG_PEAK_CACHE) {
					debug(this.name+": created peakCache with size "+fileSizeSamples+" samples.");
				}
				peakCache = new AudioPeakCache(audio.getChannelCount(),
						fileSizeSamples);
			}
			peakCache.update(startSample, audio);
		}
	}

	/**
	 * Called by read(int, FloatSampleBuffer) for actual byte data. The
	 * implementation of it makes sure that this method will never called with a
	 * value to exceed the available bytes.
	 * 
	 * @param pos the position, in bytes, where to start reading from
	 * @param buffer the byte buffer to read into
	 * @param offset the offset, in bytes, in the byte buffer
	 * @param length the number of bytes to read
	 * @return the actual number of bytes read.
	 */
	protected abstract int read(long pos, byte[] buffer, int offset, int length);

	/**
	 * Read a chunk of audio data at the specified sample position. This method
	 * will not convert the audio format to the format of buffer, except for the
	 * number of channels. The readPos is given as a sample offset from the
	 * beginning of the file. If for some reason the buffer cannot be completely
	 * filled (EOF reached, not enough bytes available), it is appended with
	 * silence.
	 * 
	 * @param readPos the position in samples where to start reading
	 * @param buffer the buffer into which the audio data is read
	 * @param offset the offset in samples in buffer where to start reading data
	 *            into
	 * @param count the number of samples to write into buffer
	 * @return true if the buffer was completely filled, false if the buffer was
	 *         not touched.
	 */
	public synchronized boolean read(long readPos, FloatSampleBuffer buffer,
			int offset, int count) {
		if (readPos < 0) {
			return false;
		}
		if (!playBeforeFullyLoaded && !isFullyLoaded()) {
			return false;
		}
		// note: avail will return 0 if format == null
		long avail = getAvailableSamples();
		int readCount = count;
		if (readPos >= avail) {
			// requested portion is after what is available
			return false;
		}
		if (readPos + readCount > avail) {
			readCount = (int) (avail - readPos);
		}
		int byteCount = readCount * format.getFrameSize();
		if (byteBuffer == null || byteBuffer.length < byteCount) {
			byteBuffer = new byte[byteCount];
		}
		byteCount = read(readPos * format.getFrameSize(), byteBuffer, 0,
				byteCount);
		if (byteCount <= 0) {
			// cannot read from underlying stream
			// FIXME: issue error message here?
			return false;
		}
		// write to float sample buffer. This will set the buffer to a new audio
		// format
		int samplesWritten = buffer.writeByteBuffer(byteBuffer, 0, format,
				offset, byteCount / format.getFrameSize());
		if (samplesWritten < count) {
			// need to append silence
			buffer.makeSilence(samplesWritten, count - samplesWritten);
		}
		// now expand channels if necessary
		if (buffer.getChannelCount() > format.getChannels()) {
			for (int i = format.getChannels(); i < buffer.getChannelCount(); i++) {
				int readChannel = i % format.getChannels();
				buffer.copyChannel(readChannel, offset, i, offset,
						samplesWritten);
			}
		}
		return true;
	}

	/**
	 * a raw method to get audio data for one channel in float format
	 * 
	 * @param channel the channel of which the data is seeked
	 * @param readPos the offset in the audio file where to start reading
	 * @param data the float array where to write the data
	 * @param offset offset in data, where to start writing
	 * @param count the number of samples to read form file
	 * @return the number of samples actually written to data
	 */
	public synchronized int readChannelData(int channel, long readPos,
			float[] data, int offset, int count) {
		if (readPos < 0) {
			// FIXME: does this mean error, or should it return the end portion?
			return 0;
		}
		if (!playBeforeFullyLoaded && !isFullyLoaded()) {
			return 0;
		}
		// note: avail will return 0 if format == null
		long avail = getAvailableSamples();
		int readCount = count;
		if (readPos >= avail) {
			// requested portion is after what is available
			return 0;
		}
		if (readPos + readCount > avail) {
			readCount = (int) (avail - readPos);
		}
		int byteCount = readCount * format.getFrameSize();
		if (byteBuffer == null || byteBuffer.length < byteCount) {
			byteBuffer = new byte[byteCount];
		}
		byteCount = read(readPos * format.getFrameSize(), byteBuffer, 0,
				byteCount);
		if (byteCount <= 0) {
			// cannot read from underlying stream
			return 0;
		}
		count = byteCount / format.getFrameSize();
		if (data.length < count) {
			count = data.length;
		}
		// now convert to float data
		FloatSampleTools.byte2float(channel, byteBuffer, 0, data, offset,
				count, format);
		return count;
	}

	@Override
	public String toString() {
		return "AudioFile " + getName();
	}

}
