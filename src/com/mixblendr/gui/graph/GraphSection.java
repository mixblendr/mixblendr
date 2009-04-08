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

/**
 * The section of the audio graph: the currently visible portion of the wave
 * form. (used to be called Zoom)
 * <p>
 * (c) copyright 1997-2007 by Bome Software
 * 
 * @author Florian Bomers
 */
public class GraphSection implements Cloneable {

	/** Start of visible area in samples. */
	int start = 0;
	/** length of the visible area in samples. */
	int length = 0;

	private List<Listener> listeners = null;

	public GraphSection() {
		// nothing
	}

	public GraphSection(GraphSection section) {
		setSection(section.start, section.length);
	}

	public GraphSection(int start, int length) {
		setSection(start, length);
	}

	/**
	 * create a duplicate GraphSection object
	 */
	@Override
	public Object clone() {
		return new GraphSection(this);
	}

	/** return the start sample of the visible section */
	public int getStart() {
		return start;
	}

	/** return the length in samples of the visible section */
	public int getLength() {
		return length;
	}

	/** get the last sample that is still displayed, or -1 if the length is 0 */
	public int getEnd() {
		return start + length - 1;
	}

	/** return if the specified section is equal to this one */
	public boolean equals(GraphSection section) {
		return section.start == start && section.length == length;
	}

	public void addListener(Listener zl) {
		if (listeners == null) listeners = new ArrayList<Listener>();
		listeners.add(zl);
	}

	public void removeListener(Listener zl) {
		if (listeners != null) {
			listeners.remove(zl);
			if (listeners.size() == 0) {
				listeners = null;
			}
		}
	}

	/**
	 * sets new visible range. If start and length don't fit to the current
	 * audio data, start is adjusted, i.e. the length is preserved, if possible
	 */
	public void setSection(int start, int length) {
		setSection(start, length, true);
	}

	/**
	 * sets new visible range. If start and length don't fit to the current
	 * audio data, start is adjusted, i.e. the length is preserved, if possible
	 * 
	 * @param sendEvent if true, listeners are updated. This should usually not
	 *            set to false.
	 */
	public void setSection(int start, int length, boolean sendEvent) {
		if (start < 0) start = 0;
		if (this.start != start || this.length != length) {
			GraphSection oldSection = (GraphSection) clone();
			this.start = start;
			this.length = length;
			if (sendEvent && listeners != null) {
				for (Listener l : listeners)
					l.sectionChanged(this, oldSection);
			}
		}
	}

	/**
	 * sets new visible range. If start and length don't fit to the current
	 * audio data, start is adjusted, i.e. the length is preserved, if possible
	 */
	public void setSection(GraphSection section) {
		if (section != null) {
			setSection(section.start, section.length);
		}
	}

	@Override
	public String toString() {
		return "Section start=" + start + " length=" + length;
	}

	/** implementors will receive an event if the current section changes */
	public interface Listener {
		public void sectionChanged(GraphSection section, GraphSection oldSection);
	}
}
