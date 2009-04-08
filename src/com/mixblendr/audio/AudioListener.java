/**
 *
 */
package com.mixblendr.audio;

/**
 * Listener for various audio events, send from the AudioEventDispatcher thread.
 * 
 * @author Florian Bomers
 */
public interface AudioListener {

	/**
	 * Called when a region's state has changed. This is mainly caused during
	 * download of the underlying audio file.
	 * 
	 * @param track the track on which this event happened
	 * @param region the region which state has changed
	 */
	public void audioRegionStateChange(AudioTrack track, AudioRegion region,
			AudioRegion.State state);

	/**
	 * Called when an audio file encountered an error while downloading.
	 * 
	 * @param file the file that encountered this problem
	 */
	public void audioFileDownloadError(AudioFile file, Throwable t);

	/**
	 * Called when tha name of a track changes
	 */
	public void audioTrackNameChanged(AudioTrack track);

}
