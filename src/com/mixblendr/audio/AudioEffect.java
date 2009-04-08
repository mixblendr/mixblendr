/**
 *
 */
package com.mixblendr.audio;

import org.tritonus.share.sampled.*;

/**
 * Interface implemented by audio effects processing the audio stream
 * 
 * @author Florian Bomers
 */
public interface AudioEffect {

	/** called by the audio system to initialize this effect instance */
	public void init(AudioState state, AudioPlayer player, AudioTrack track);

	/**
	 * Called when this effect is not used anymore. It should free all
	 * resources, in particular any frames should be properly disposed of. It
	 * can still be re-initialized by a subsequent call to init().
	 */
	public void exit();

	/** the short name that appears in the main GUI */
	public String getShortName();

	/**
	 * @return the track that this effect is assigned to. <code>null</code> if
	 *         not assigned.
	 */
	public AudioTrack getTrack();

	/**
	 * @return true if this effect has a settings window and the
	 *         showSettingsWindow() method can be called.
	 */
	public boolean hasSettingsWindow();

	/**
	 * Display a settings window, e.g. a JFrame. This method is called when the
	 * user clicks the Settings button next to the effect name. If this effect
	 * does not provide a settings window, hasSettingsWindow() should return
	 * false, and showSettingsWindow() will never be called.
	 */
	public void showSettingsWindow();

	/**
	 * Process this buffer. Do not modify the sample count or channel count.
	 * 
	 * @param samplePos the sample position of when this buffer will be heard.
	 *            Will be approximate to state.getAudioSliceTime().
	 * @param buffer the sample buffer to which the effect is applied
	 * @param offset in buffer (in samples) where to start processing
	 * @param count number of samples to process, starting at offset
	 * @return true if anything modifed at all
	 */
	public boolean process(long samplePos, FloatSampleBuffer buffer,
			int offset, int count);
}
