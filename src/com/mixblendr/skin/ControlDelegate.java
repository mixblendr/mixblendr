/**
 *
 */
package com.mixblendr.skin;

import java.awt.*;

import com.mixblendr.util.Utils;

import static com.mixblendr.util.Debug.*;

/**
 * Delegate class for Mixblendr Controls. Mixblendr controls derive from their
 * Swing counterparts and delegate the important methods like paint and
 * getDimension to this delegate class. ControlDelegates do their entire
 * rendering by way of images.
 * 
 * @author Florian Bomers
 */
public class ControlDelegate implements Blinker.Blinkable, Cloneable {
	private static final boolean TRACE = false;
	private static final boolean DEBUG_REPAINT = false;

	/**
	 * the current state, is derived from the down, hovering, and blinking
	 * properties.
	 */
	protected ControlState state = ControlState.NORMAL;

	private ControlDefinition ctrlDef;

	private MControl owner;

	protected boolean down = false;

	protected boolean hovering = false;

	protected boolean blinking = false;

	protected Image[] sourceImages = new Image[ControlState.length];

	protected Pos[] sourcePos = new Pos[ControlState.length];

	/** set and reset by the Blinker class */
	protected boolean blinkActive = false;

	/**
	 * Create a new ControlDelegate delegate class. Use createUI() to actually
	 * load and realize the images and prepare the component for actual usage.
	 * 
	 * @param ctrlDef
	 */
	ControlDelegate(ControlDefinition ctrlDef) {
		this.ctrlDef = ctrlDef;
	}

	/**
	 * Assign all parameters of this control to <code>cd</code>, except
	 * owner.
	 * 
	 * @param cd the instance to transfer all properties to.
	 */
	void assignTo(ControlDelegate cd) {
		assignStateTo(cd);
		cd.ctrlDef = ctrlDef;
		cd.owner = null;
		for (int i = 0; i < ControlState.length; i++) {
			cd.sourceImages[i] = sourceImages[i];
			cd.sourcePos[i] = sourcePos[i];
		}
	}

	/**
	 * Assign the state of this control to <code>cd</code>. The owner and
	 * control definition are not assigned.
	 * 
	 * @param cd the instance to transfer the state to.
	 */
	void assignStateTo(ControlDelegate cd) {
		cd.state = state;
		cd.down = down;
		cd.hovering = hovering;
		cd.blinking = blinking;
		cd.blinkActive = blinkActive;
	}

	/** create a copy of this delegate instance */
	@Override
	public Object clone() {
		ControlDelegate cd = new ControlDelegate(ctrlDef);
		assignTo(cd);
		return cd;
	}

	private void updateTooltip() {
		if (owner != null && ctrlDef != null && ctrlDef.tooltip != null
				&& ctrlDef.tooltip != "") {
			owner.setToolTipText(ctrlDef.tooltip);
		}
	}

	/**
	 * @param owner
	 */
	// FIXME: shouldn't be public, but RegionGraph needs to access it
	public void setOwner(MControl owner) {
		this.owner = owner;
		updateTooltip();
	}

	/**
	 * load the images needed by this control from the image manager, according
	 * to the definition in ControlDefinition
	 */
	public void createUI(ImageManager imageManager) throws Exception {
		for (ControlState lState : ControlState.values()) {
			int i = lState.ordinal();
			String filename = ctrlDef.sourceImage[i];
			if (filename != null && filename.length() > 0) {
				sourceImages[i] = imageManager.getImage(filename);
				sourcePos[i] = ctrlDef.sourcePos[i];
				if (sourcePos[i] == null) {
					throw new Exception("Required position of control '"
							+ ctrlDef.fullName + "' for state " + lState
							+ " is not available");
				}
			} else {
				sourceImages[i] = null;
				sourcePos[i] = null;
			}
		}
	}

	private static Rectangle cacheClip = new Rectangle();

	/** draw an image at the specified position */
	private static void copyImage(Image src, int srcX, int srcY, Graphics dest,
			int destX, int destY, int width, int height) {
		Rectangle clip = cacheClip;
		clip.width = -1; // detect missing clip
		dest.getClipBounds(clip);
		if (clip.width == 0 || clip.height == 0) return;
		if (clip.width > 0 && clip.height > 0) {
			// check x coordinate
			if (destX < clip.x) {
				int diff = clip.x - destX;
				srcX += diff;
				width -= diff;
				destX += diff;
			}
			// check width
			if (destX + width > clip.x + clip.width) {
				width = clip.x + clip.width - destX;
			}
			// check y coordinate
			if (destY < clip.y) {
				int diff = clip.y - destY;
				srcY += diff;
				height -= diff;
				destY += diff;
			}
			// check height
			if (destY + height > clip.y + clip.height) {
				height = clip.y + clip.height - destY;
			}
		}
		if (DEBUG_REPAINT) {
			debug("  drawImage: img=(" + srcX + "," + srcY + ") dest=(" + destX
					+ "," + destY + ") w=" + width + " h=" + height);
		}
		if (width > 0 && height > 0) {
			dest.drawImage(src, destX, destY, destX + width, destY + height,
					srcX, srcY, srcX + width, srcY + height, null);
		}
	}

	/**
	 * Set the transparency level.
	 * 
	 * @param g the graphics to set transparency
	 * @param alpha the transparency 0..1
	 * @return true if alpha is smaller than 1.0f and if the graphics' composite
	 *         was changed
	 */
	private static boolean setGraphicsTransparency(Graphics g, float alpha) {
		if (g instanceof Graphics2D) {
			Graphics2D g2d = (Graphics2D) g;
			Composite comp = g2d.getComposite();
			if (comp instanceof AlphaComposite) {
				AlphaComposite acomp = (AlphaComposite) comp;

				if (acomp.getAlpha() != alpha) {
					if (alpha < 1.0f) {
						g2d.setComposite(AlphaComposite.getInstance(
								AlphaComposite.SRC_OVER, alpha));
						return true;
					}
					g2d.setComposite(AlphaComposite.SrcOver);
				}
			}
		}
		return false;
	}

	/**
	 * Paint the component. Return false if the image could not be found.
	 * 
	 * @param g the graphics to paint to
	 * @return true if successfully painted
	 */
	public boolean paint(Graphics g) {
		return paint(g, 0, 0, 1.0f);
	}

	/**
	 * Paint the component. Return false if the image could not be found.
	 * 
	 * @param g the graphics to paint to
	 * @param x the x position where to paint
	 * @param y the y position where to paint
	 * @param alpha a transparency value
	 * @return true if successfully painted
	 */
	public boolean paint(Graphics g, int x, int y, float alpha) {
		Rect r = ctrlDef.targetBounds;
		if ((owner != null) && hasHorizontalBackground()) {
			boolean hasSetAlpha = setGraphicsTransparency(g, alpha);
			// control can resize horizontally
			int width = owner.getWidth();
			Pos p1 = sourcePos[ControlState.BACKGROUND_LEFT.ordinal()];
			Pos p2 = sourcePos[ControlState.BACKGROUND_LR_TILE.ordinal()];
			Pos p3 = sourcePos[ControlState.BACKGROUND_RIGHT.ordinal()];
			// derive the 3 components' widths from the positions and from
			// the original target width
			int wL = p2.x - p1.x;
			int wTile = p3.x - p2.x;
			int wR = r.w - wTile - wL;
			// if the width is not enough to paint the tile, only paint the left
			// and right images
			if (wL + wR >= width) {
				wL -= (wL + wR - width) / 2;
				int newWR = width - wL;
				int xR = x + wL - (wR - newWR);
				// paint right side first, full width to just keep the right
				// side of it
				paint(g, ControlState.BACKGROUND_RIGHT, xR, y, wR, r.h, alpha);
				// left side
				paint(g, ControlState.BACKGROUND_LEFT, x, y, wL, r.h, alpha);
			} else {
				// paint the left side
				paint(g, ControlState.BACKGROUND_LEFT, x, y, wL, r.h, alpha);
				x += wL;
				width -= wL;
				// paint the tile(s)
				while (width > wR) {
					int tileWidth = width - wR;
					if (tileWidth > wTile) {
						tileWidth = wTile;
					}
					paint(g, ControlState.BACKGROUND_LR_TILE, x, y, tileWidth,
							r.h, alpha);
					x += tileWidth;
					width -= tileWidth;
				}
				// paint right side
				paint(g, ControlState.BACKGROUND_RIGHT, x, y, wR, r.h, alpha);
			}
			if (hasSetAlpha) {
				setGraphicsTransparency(g, 1.0f);
			}
			return true;
		}
		// if not tile, paint the static state image
		return paint(g, state, x, y, r.w, r.h, alpha);
	}

	/**
	 * Paint the component. Return false if the image could not be found.
	 * 
	 * @param g the graphics to paint to
	 * @param aState the state to paint
	 * @param x the x position where to paint
	 * @param y the y position where to paint
	 * @param w the width to paint
	 * @param h the height to paint
	 * @param alpha a transparency value
	 * @return true if successfully painted
	 */
	boolean paint(Graphics g, ControlState aState, int x, int y, int w, int h,
			float alpha) {
		// if (TRACE) debug(ctrlDef.fullName + ": paint " + state);
		if (hasImage(aState)) {
			Image img = sourceImages[aState.ordinal()];
			Pos p = sourcePos[aState.ordinal()];
			if (w > 0 && h > 0) {
				boolean hasSetAlpha = setGraphicsTransparency(g, alpha);
				if (DEBUG_REPAINT) {
					debug(getCtrlDef().getName() + ": paintImage img=(" + p.x
							+ "," + p.y + ") dest=(" + x + "," + y + ") w=" + w
							+ " h=" + h);
					debug("  clip=" + g.getClipBounds());
				}
				copyImage(img, p.x, p.y, g, x, y, w, h);
				if (hasSetAlpha) {
					setGraphicsTransparency(g, 1.0f);
				}
			}
			return true;
		}
		if (TRACE) {
			if (aState != ControlState.NORMAL) {
				debug(ctrlDef.fullName + ": can't find image for state "
						+ aState);
			}
		}
		return false;
	}

	/**
	 * Decide if the image for the given state exists. This is true if the image
	 * is loaded and if the associated source position is set.
	 * 
	 * @param aState the state to check image availability for
	 * @return true if the image and the associated position exists
	 */
	public boolean hasImage(ControlState aState) {
		Image img = sourceImages[aState.ordinal()];
		Rect r = ctrlDef.targetBounds;
		Pos p = sourcePos[aState.ordinal()];
		return (img != null && r != null && p != null);
	}

	/**
	 * Decide if this delegate has a horizontally resizable background image.
	 * This is true if hasImage() is true for all three BACKGROUND_LEFT,
	 * BACKGROUND_RIGHT and BACKGROUND_LR_TILE states.
	 * 
	 * @return true if the horizontally resizable background exists
	 */
	public boolean hasHorizontalBackground() {
		return hasImage(ControlState.BACKGROUND_LEFT)
				&& hasImage(ControlState.BACKGROUND_RIGHT)
				&& hasImage(ControlState.BACKGROUND_LR_TILE);
	}

	public Dimension getPreferredSize() {
		return ctrlDef.getSize();
	}

	public Dimension getMinimumSize() {
		Dimension ret = getPreferredSize();
		if (hasHorizontalBackground()) {
			ret.width = 1;
		}
		return ret;
	}

	public Dimension getMaximumSize() {
		Dimension ret = getPreferredSize();
		if (hasHorizontalBackground()) {
			ret.width = Integer.MAX_VALUE;
		}
		return ret;
	}

	/**
	 * Deduct the state from the properties down, hovering, and blinking. Does
	 * not call update on the component.
	 */
	protected void updateState() {
		ControlState newState;
		if (down) {
			if (hovering && hasImage(ControlState.HOVERDOWN)) {
				newState = ControlState.HOVERDOWN;
			} else {
				if (blinkActive && hasImage(ControlState.BLINK)) {
					newState = ControlState.BLINK;
				} else {
					newState = ControlState.DOWN;
				}
			}
		} else {
			if (hovering && hasImage(ControlState.HOVER)) {
				newState = ControlState.HOVER;
			} else {
				newState = ControlState.NORMAL;
			}
		}
		if (owner != null && Utils.isDefined(ctrlDef.tooltip)
				&& Utils.isDefined(ctrlDef.tooltipDown)) {
			if (down) {
				owner.setToolTipText(ctrlDef.tooltipDown);
			} else {
				owner.setToolTipText(ctrlDef.tooltip);
			}
		}
		if (state != newState) {
			state = newState;
			if (TRACE) {
				debug("Control " + ctrlDef.fullName + ": state=" + state);
			}
			if (owner != null) {
				owner.repaint();
			}
		}
	}

	/**
	 * @return the blinking
	 */
	public boolean isBlinking() {
		return blinking;
	}

	/**
	 * @param blinking the blinking to set
	 */
	public void setBlinking(boolean blinking) {
		if (this.blinking != blinking) {
			this.blinking = blinking;
			if (TRACE) {
				debug("Control " + ctrlDef.fullName + ": blinking=" + blinking);
			}
			updateState();
			if (blinking) {
				Blinker.add(this);
			} else {
				Blinker.remove(this);
			}
		}
	}

	/**
	 * @return the down
	 */
	public boolean isDown() {
		return down;
	}

	/**
	 * @param down the down to set
	 */
	public void setDown(boolean down) {
		if (down != this.down) {
			this.down = down;
			updateState();
			if (TRACE) {
				debug("Control " + ctrlDef.fullName + ": down=" + down
						+ "  state=" + state);
			}
			if (sourceImages[ControlState.BLINK.ordinal()] != null) {
				// for now, let's blink it whenever it's down
				setBlinking(down);
			}
		}
	}

	/**
	 * @return the hovering
	 */
	public boolean isHovering() {
		return hovering;
	}

	/**
	 * @param hovering the hovering to set
	 */
	public void setHovering(boolean hovering) {
		if (this.hovering != hovering) {
			this.hovering = hovering;
			if (TRACE) {
				debug("Control " + ctrlDef.fullName + ": hovering=" + hovering
						+ "  state=" + state);
			}
			updateState();
		}
	}

	/**
	 * @return the ctrlDef
	 */
	public ControlDefinition getCtrlDef() {
		return ctrlDef;
	}

	/**
	 * @return the owner
	 */
	public MControl getOwner() {
		return owner;
	}

	/**
	 * @return the current state
	 */
	public ControlState getState() {
		return state;
	}

	/**
	 * update the blinkActive field, recalculate the current state, and then
	 * repaint the component
	 */
	public void setBlinkerActive(boolean active) {
		this.blinkActive = active;
		updateState();
		if (owner != null) {
			owner.repaint();
		}
	}

	@Override
	public String toString() {
		return "ControlDelegate: " + getCtrlDef();
	}

}
