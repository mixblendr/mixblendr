/**
 *
 */
package com.mixblendr.audio;

import org.tritonus.share.sampled.FloatSampleBuffer;

/**
 * Interface for classes just reading a float sample buffer (sort of a small
 * version of Tritonus' FloatSampleInput)
 * 
 * @see org.tritonus.share.sampled.FloatSampleInput
 * @author Florian Bomers
 */
public interface AudioInput {

	/**
	 * Read the next chunk of audio data at the given position. The buffer's
	 * size or channel count should not be modified. If anything is read at all,
	 * the implementation should fill the entire buffer and return true. If not
	 * the entire buffer can be filled, fill up with silence. If nothing was
	 * written to the buffer, return false.
	 * 
	 * @param samplePos the playback position when this audio buffer will be
	 *            played
	 * @param buffer the buffer to be filled
	 * @param offset the offset in samples where to start writing samples to
	 *            buffer
	 * @param sampleCount the number of samples to write to buffer at offset
	 * @return true if the buffer was completely filled, false if the buffer was
	 *         not touched.
	 */
	public boolean read(long samplePos, FloatSampleBuffer buffer, int offset,
			int sampleCount);
}
