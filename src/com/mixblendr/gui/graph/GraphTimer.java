/*
 * Copyright (c) 1997 - 2007 by Bome Software / Florian Bomers
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * - Redistributions of source code must include the source code of the
 * Mixblendr software or its derivatives.
 * - Redistributions in binary form must be packaged with the Mixblendr
 * software, or its derivatives.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.mixblendr.gui.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A timer
 * <p>
 * (c) copyright 1997-2007 by Bome Software
 * 
 * @author Florian Bomers
 */
public class GraphTimer {

	private static Timer timer = null; // this is of type Timer
	private TimerTask task = null; // this is of type TimerTask
	private static final Object lock = new Object();
	private static int timerCount = 0;

	/** delay of the timer */
	protected int delay;
	/** if periodic timer or not */
	protected boolean periodic;

	private boolean killed = false;

	private int ID;
	private Object userObject;

	private List<Listener> listeners = null;

	/** don't allow a creation w/out parameters */
	@SuppressWarnings("unused")
	private GraphTimer() {
		// nothing
	}

	/**
	 * Creates a new GraphTimer
	 * 
	 * @see #GraphTimer(int,Object,int,boolean)
	 */
	public GraphTimer(int delay, boolean periodic) {
		this(0, null, delay, periodic);
	}

	/**
	 * Creates a new GraphTimer
	 * 
	 * @see #GraphTimer(int,Object, int,boolean)
	 */
	public GraphTimer(Object userObject, int delay, boolean periodic) {
		this(0, userObject, delay, periodic);
	}

	/**
	 * Creates a new GraphTimer
	 * 
	 * @see #GraphTimer(int,Object, int,boolean)
	 */
	public GraphTimer(int ID, int delay, boolean periodic) {
		this(ID, null, delay, periodic);
	}

	/**
	 * Creates a new Timer. It will be initially disabled and have the specified
	 * ID. On every call of
	 * 
	 * @see #enable() the listeners will be called after <tt>delay</tt> ms. If
	 *      <tt>periodic</tt> is true, it will then repeat until
	 * @see #disable() or
	 * @see #kill() is called. The timer may be enabled and disabled without any
	 *      limitations. After the call to kill it should not be used anymore.
	 */

	public GraphTimer(int ID, Object userObject, int delay, boolean periodic) {
		this.delay = delay;
		this.ID = ID;
		this.userObject = userObject;
		this.periodic = periodic;
		synchronized (lock) {
			timerCount++;
		}
	}

	public int getID() {
		return ID;
	}

	public Object getUserObject() {
		return userObject;
	}

	/**
	 * enable the timer (if not killed)
	 */
	public synchronized void enable() {
		if (killed) {
			return;
		}

		disable();
		synchronized (lock) {
			if (timer == null) {
				timer = new java.util.Timer(true);
			}
		}
		task = new Task();
		try {
			if (periodic)
				timer.schedule(task, delay, delay);
			else
				timer.schedule(task, delay);
		} catch (Exception e) {
			//e.printStackTrace();
		}
	}

	/**
	 * disable the timer.
	 */
	public synchronized void disable() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	/**
	 * Send an event to the listeners
	 */
	protected void onTimer() {
		if (listeners != null) {
			for (Listener l : listeners)
				l.timerTick(this);
		}
		if (!periodic) {
			disable();
		}
	}

	/**
	 * Kill the timer -- after this, the timer cannot be restarted.
	 */
	public void kill() {
		if (killed) {
			return;
		}
		killed = true;
		disable();
		listeners = null;
		decTimerCount();
	}

	private void decTimerCount() {
		synchronized (lock) {
			timerCount--;
			if (timerCount <= 0) {
				if (timer != null) {
					timer.cancel();
					timer = null;
				}
				timerCount = 0;
			}
		}
	}

	@Override
	public void finalize() throws Throwable {
		if (!killed) decTimerCount();
		super.finalize();
	}

	/**
	 * return true if the timer is enabled
	 * 
	 * @see GraphTimer#enable()
	 */
	public boolean isEnabled() {
		return task != null;
	}

	/**
	 * enable or disable the timer
	 * 
	 * @see GraphTimer#enable()
	 * @see GraphTimer#disable()
	 * @param e if true, enable otherwise disable
	 */
	public void setEnabled(boolean e) {
		if (e)
			enable();
		else
			disable();
	}

	/**
	 * add a timer listener
	 */
	public synchronized void addListener(Listener tl) {
		if (listeners == null) listeners = new ArrayList<Listener>();
		listeners.add(tl);
	}

	/**
	 * remove a timer listener
	 */
	public synchronized void removeListener(Listener tl) {
		if (listeners != null) {
			listeners.remove(tl);
			if (listeners.size() == 0) listeners = null;
		}
	}

	class Task extends java.util.TimerTask {
		@Override
		public void run() {
			onTimer();
		}

	}

	public interface Listener {
		public void timerTick(GraphTimer aTimer);
	}

}
