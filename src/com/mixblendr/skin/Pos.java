/**
 *
 */
package com.mixblendr.skin;

import java.awt.Dimension;

/**
 * A simple immutable object for holding x and y coordinates.
 * 
 * @author Florian Bomers
 */
public class Pos implements Cloneable {
	int x, y;

	public Pos() {
		this(0, 0);
	}

	/**
	 * @param x
	 * @param y
	 */
	public Pos(int x, int y) {
		super();
		this.x = x;
		this.y = y;
	}

	/**
	 * @return the x position
	 */
	public int getX() {
		return x;
	}

	/**
	 * @return the y position
	 */
	public int getY() {
		return y;
	}

	/** create a new instance of java.awt.Dimension and return it */
	public Dimension getDimension() {
		return new Dimension(x, y);
	}

	@Override
	public Object clone() {
		return new Pos(x, y);
	}

	@Override
	public String toString() {
		return "" + x + ", " + y;
	}
}
