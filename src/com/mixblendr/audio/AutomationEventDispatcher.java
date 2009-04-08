/**
 *
 */
package com.mixblendr.audio;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Dispatcher for automation events.
 * 
 * @author Florian Bomers
 */
public class AutomationEventDispatcher extends Thread {
	/** flag to signal a requested stop of this thread */
	private volatile boolean stopRequested = false;
	/** flag that's set when the list of listeners has changed */
	private volatile boolean listenersChanged = false;

	private LinkedList<AutomationEvent> queue;

	private List<AutomationListener> listeners;

	/** create a new instance of the thread */
	AutomationEventDispatcher() {
		super("Automation Event Dispatcher");
		queue = new LinkedList<AutomationEvent>();
		listeners = new ArrayList<AutomationListener>();
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

	synchronized void dispatchEvent(AutomationObject ao, AudioTrack track) {
		queue.offer(new AutomationEvent(ao, track));
		this.notifyAll();
	}

	/** add a listener for the automation events */
	public void addListener(AutomationListener al) {
		listeners.add(al);
		listenersChanged = true;
	}

	/** remove the listener for the automation events */
	public void removeListener(AutomationListener al) {
		listeners.add(al);
		listenersChanged = true;
	}

	@Override
	public void run() {
		// avoid additional synchronization on listeners list
		AutomationListener[] localListeners = null;
		while (!stopRequested) {
			AutomationEvent ae = null;
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
					localListeners = listeners.toArray(new AutomationListener[listeners.size()]);
				}
				for (AutomationListener al : localListeners) {
					al.automationEvent(ae.track, ae.ao);
				}
				// IDEA: reuse ae in a pool of AutomationEvents to reduce GC
			}
		}
	}

	private static class AutomationEvent {
		AutomationObject ao;
		AudioTrack track;

		/**
		 * @param ao
		 * @param track
		 */
		public AutomationEvent(AutomationObject ao, AudioTrack track) {
			super();
			this.ao = ao;
			this.track = track;
		}

	}

}
