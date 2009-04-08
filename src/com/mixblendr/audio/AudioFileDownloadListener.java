/**
 *
 */
package com.mixblendr.audio;

/**
 * Notifications about download progress
 * 
 * @author Florian Bomers
 */
public interface AudioFileDownloadListener {
	public void downloadStarted(AudioFile af);

	public void downloadEnded(AudioFile af);

}
