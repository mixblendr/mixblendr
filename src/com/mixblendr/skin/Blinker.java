/**
 *
 */
package com.mixblendr.skin;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;

import javax.swing.JComponent;
import javax.swing.Timer;

import static com.mixblendr.util.Debug.*;

/**
 * Static class calling all registered blinking components, calling their
 * setBlinkerActive() method in regular intervals.
 * 
 * @author Florian Bomers
 */
class Blinker implements ActionListener {

	/** the blink interval in milliseconds */
	public final static int BLINK_INTERVAL = 250;

	private static Timer timer = null;

	private static LinkedList<Blinkable> controls = new LinkedList<Blinkable>();

	private Blinker() {
		// nothing to do
	}

	/**
	 * Add this blinkable component from now on it will be called its
	 * setBlinkerActive method in regular intervals with alternating
	 * <code>active</code> parameter.
	 */
	static void add(Blinkable b) {
		synchronized (controls) {
			controls.add(b);
			if (timer == null) {
				timer = new Timer(BLINK_INTERVAL, new Blinker());
			}
		}
		timer.start();
	}

	/** remove this blinkable component */
	static void remove(Blinkable b) {
		synchronized (controls) {
			controls.remove(b);
			if (timer != null && controls.size() == 0) {
				timer.stop();
			}
		}
	}

	private int onCounter = 0;

	/**
	 * Called in regular intervals and call the blinkerActive method.
	 * 
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	public void actionPerformed(ActionEvent e) {
		// let's do the "on" phase shorter than the "off" phase
		onCounter++;
		boolean on;
		if (onCounter == 1) {
			on = false;
		} else if (onCounter == 3) {
			on = true;
			onCounter = 0;
		} else {
			return;
		}
		synchronized (controls) {
			for (Blinkable b : controls) {
				b.setBlinkerActive(on);
			}
		}
		cleanUpAbandonedListeners();
	}

	/**
	 * Sort of a hack to stop the timer if no controls are visible anymore
	 */
	private void cleanUpAbandonedListeners() {
		synchronized (controls) {
			for (int i = controls.size() - 1; i >= 0; i--) {
				Blinkable b = controls.get(i);
				if (b instanceof ControlDelegate) {
					ControlDelegate cd = (ControlDelegate) b;
					boolean alive = false;
					if (cd.getOwner() != null) {
						alive = true;
						if (cd.getOwner() instanceof JComponent) {
							JComponent c = (JComponent) cd.getOwner();
							if (c.getRootPane() == null
									|| !c.getRootPane().isShowing()) {
								alive = false;
							}
						}
					}
					if (!alive) {
						if (DEBUG) {
							debug("Blinker: clean up, removing " + b);
						}
						remove(b);
					}
				}
			}
		}
	}

	/** interface implemented by blinking components */
	interface Blinkable {
		public void setBlinkerActive(boolean active);
	}
}
