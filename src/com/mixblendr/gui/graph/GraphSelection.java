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
 * The selected portion in the audio graph
 * <p>
 * (c) copyright 1997-2007 by Bome Software
 * 
 * @author Florian Bomers
 */
public class GraphSelection implements Cloneable {
	/**
	 * Fields and methods defining a selection of a wave. The selection values
	 * are in samples. If length=0 then the selection defines one point and
	 * getEnd() should not be used.
	 */

	protected int start = 0;
	protected int length = 0;

	private List<Listener> listeners = null;

	public GraphSelection() {
		// nopthing
	}

	public GraphSelection(GraphSelection selection) {
		setSelection(selection.start, selection.length);
	}

	/**
	 * doubliquer les valeurs dans une autre classe zoom
	 */
	@Override
	public Object clone() {
		return new GraphSelection(this);
	}

	public int getStart() {
		return start;
	}

	public int getLength() {
		return length;
	}

	public int getEnd() {
		return start + length - 1;
	}

	/**
	 * Returns whether this selection defines one point. <br>
	 * This is the case, when you clicked once in a graph, the point is marked
	 * as a green line.<br>
	 * The definition is: (start=end) => one point is selected. <br>
	 * S'il y a une selection et si Debut == Fin <br>
	 * alors un point est selectionne.
	 */
	public boolean onePoint() {
		return (length == 0);
	}

	public boolean equals(GraphSelection sel) {
		return sel.start == start && sel.length == length;
	}

	/**
	 * Sets this selection to one point. <br>
	 * etabli les coordonnees du point selectionne.
	 */
	// public void setPoint(int pos) {
	// start=pos;
	// end=pos;
	// }
	public void addListener(Listener zl) {
		if (listeners == null) {
			listeners = new ArrayList<Listener>();
		}
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
	 * sets new selection range. If start and length don't fit to the current
	 * audio data, start is adjusted, i.e. the length is preserved, if possible
	 */
	public void setSelection(int start, int length) {
		if (start < 0) start = 0;
		if (this.start != start || this.length != length) {
			GraphSelection oldSel = (GraphSelection) clone();
			this.start = start;
			this.length = length;
			if (listeners != null) {
				for (Listener l : listeners) {
					l.selectionChanged(this, oldSel);
				}
			}
		}
	}

	public void setSelection(GraphSelection sel) {
		if (sel != null) {
			setSelection(sel.start, sel.length);
		}
	}

	@Override
	public String toString() {
		return "Selection start=" + start + " length=" + length;
	}

	public interface Listener {
		public void selectionChanged(GraphSelection sel, GraphSelection oldSel);
	}
}
