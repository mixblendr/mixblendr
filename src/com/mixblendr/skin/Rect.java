/**
 *
 */
package com.mixblendr.skin;

import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * A simplified Rectangle object
 * 
 * @author Florian Bomers
 */
public class Rect implements Cloneable {
	int x, y, w, h;

	public Rect() {
		this(0, 0, 0, 0);
	}

	/**
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 */
	public Rect(int x, int y, int w, int h) {
		super();
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	/**
	 * @return the height
	 */
	public int getHeight() {
		return h;
	}

	/**
	 * @return the width
	 */
	public int getWidth() {
		return w;
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

	/** create a new instance of java.awt.Rectangle and return it */
	public Rectangle getRectangle() {
		return new Rectangle(x, y, w, h);
	}

	/**
	 * create a new instance of java.awt.Dimension for the width and height
	 * attributes and return it
	 */
	public Dimension getDimension() {
		return new Dimension(w, h);
	}

	/** create a new instance of Pos for the x and y attributes and return it */
	public Pos getPos() {
		return new Pos(x, y);
	}

	@Override
	public Object clone() {
		return new Rect(x, y, w, h);
	}

	@Override
	public String toString() {
		return "" + x + ", " + y + ", " + w + ", " + h;
	}
}
