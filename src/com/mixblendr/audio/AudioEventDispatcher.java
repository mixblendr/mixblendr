/**
 *
 */
package com.mixblendr.audio;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Dispatcher to asynchronously deliver audio events to audio listeners.
 * 
 * @author Florian Bomers
 */
public class AudioEventDispatcher extends Thread {

	/** state for event type */
	private final static int TYPE_AUDIO_REGION_STATE = 1;
	/** event type for download error */
	private final static int TYPE_DOWNLOAD_ERROR = 2;

	/** event type for track name change */
	private final static int TYPE_TRACK_NAME_CHANGE = 3;

	/** flag to signal a requested stop of this thread */
	private volatile boolean stopRequested = false;
	/** flag that's set when the list of listeners has changed */
	private volatile boolean listenersChanged = false;

	private LinkedList<AudioEvent> queue;

	private List<AudioListener> listeners;

	/** create a new instance of the thread */
	AudioEventDispatcher() {
		super("Audio Event Dispatcher");
		queue = new LinkedList<AudioEvent>();
		listeners = new ArrayList<AudioListener>();
		// GUI stuff, not very important
		setPriority(Thread.MIN_PRIORITY);
		setDaemon(true);
		start();
	}

	/** call this method to terminate the thread */
	void doStop() {
		stopRequested = true;
		synchronized (this) {
			this.notifyAll();
		}
	}

	synchronized void dispatchEvent(int type) {
		queue.offer(new AudioEvent(type));
		this.notifyAll();
	}

	synchronized void dispatchAudioRegionStateChange(AudioTrack track,
			AudioRegion region, AudioRegion.State state) {
		queue.offer(new AudioEvent(track, region, state));
		this.notifyAll();
	}

	synchronized void dispatchDownloadError(AudioFile file, Throwable t) {
		queue.offer(new AudioEvent(file, t));
		this.notifyAll();
	}

	synchronized void dispatchTrackNameChange(AudioTrack track) {
		queue.offer(new AudioEvent(TYPE_TRACK_NAME_CHANGE, track));
		this.notifyAll();
	}

	/** add a listener for the audio events */
	public void addListener(AudioListener al) {
		listeners.add(al);
		listenersChanged = true;
	}

	/** remove the listener for the audio events */
	public void removeListener(AudioListener al) {
		listeners.add(al);
		listenersChanged = true;
	}

	@Override
	public void run() {
		// avoid additional synchronization on listeners list
		AudioListener[] localListeners = null;
		while (!stopRequested) {
			AudioEvent ae = null;
			synchronized (this) {
				if (queue.isEmpty()) {
					try {
						this.wait();
					} catch (InterruptedException ie) {
						// nothing
					}
					if (stopRequested) {
						break;
					}
				}
				ae = queue.poll();
			}
			if (ae != null) {
				if (localListeners == null || listenersChanged) {
					localListeners = listeners.toArray(new AudioListener[listeners.size()]);
				}
				for (AudioListener al : localListeners) {
					switch (ae.type) {
					case TYPE_AUDIO_REGION_STATE:
						al.audioRegionStateChange((AudioTrack) ae.p1,
								(AudioRegion) ae.p2, (AudioRegion.State) ae.p3);
						break;
					case TYPE_DOWNLOAD_ERROR:
						al.audioFileDownloadError((AudioFile) ae.p1,
								(Throwable) ae.p2);
						break;
					case TYPE_TRACK_NAME_CHANGE:
						al.audioTrackNameChanged((AudioTrack) ae.p1);
						break;
					}
				}
				// IDEA: reuse ae in a pool of AudioEvents to reduce GC
			}
		}
	}

	private static class AudioEvent {
		int type;
		Object p1, p2, p3;

		AudioEvent(int type) {
			super();
			this.type = type;
		}

		AudioEvent(AudioTrack track, AudioRegion region, AudioRegion.State state) {
			this(TYPE_AUDIO_REGION_STATE);
			this.p1 = track;
			this.p2 = region;
			this.p3 = state;
		}

		AudioEvent(AudioFile file, Throwable t) {
			this(TYPE_DOWNLOAD_ERROR);
			this.p1 = file;
			this.p2 = t;
		}

		AudioEvent(int type, AudioTrack track) {
			this(type);
			this.p1 = track;
		}
	}

}
