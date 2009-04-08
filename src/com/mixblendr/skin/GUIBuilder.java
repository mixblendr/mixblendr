/**
 *
 */
package com.mixblendr.skin;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import static com.mixblendr.util.Debug.*;

/**
 * Create a panel with all the GUI controls from a given skin definition file.
 * 
 * @author Florian Bomers
 */
public class GUIBuilder {

	private static final boolean TRACE = false;

	/**
	 * the filename used as default skin definition filename in the image path
	 * (&quot;skin.txt&quot;)
	 */
	public static final String DEFAULT_SKIN_DEFINITION_FILE = "skin.txt";

	public static final String CONTROL_NAME_KNOB = "knob";

	public static final String CONTROL_NAME_TROUGH = "trough";

	private HashMap<String, ControlDelegate> controls = new HashMap<String, ControlDelegate>(
			50);

	private ImageManager imageManager;

	/** the master panel holding all other controls */
	private MPanel masterPanel = null;

	/**
	 * create a new GUIBuilder instance and load the specified skin based on the
	 * base class and the image base path. The skin definition file is expected
	 * along with the images in a file named DEFAULT_SKIN_DEFINITION_FILE.
	 * 
	 * @param skinDir the name of the dir containing the images and the skin
	 *            definition file
	 */
	@SuppressWarnings("unchecked")
	public GUIBuilder(Class imageLoadClass, String skinDir) throws Exception {
		SkinFileReader sfr = new SkinFileReader();
		imageManager = new ImageManager(imageLoadClass, skinDir);
		URL skin = imageManager.getImageURL(DEFAULT_SKIN_DEFINITION_FILE);
		if (TRACE) debug("Parsing skin file");
		sfr.load(skin.openStream());
		if (TRACE) debug("Create controls");
		createControls(sfr.getControlDefinitions());
	}

	/**
	 * for every control in defs, create an ControlDelegate and the
	 * corresponding Swing descendant control.
	 */
	protected void createControls(List<ControlDefinition> defs)
			throws Exception {
		for (ControlDefinition cd : defs) {
			if (TRACE) debug("Create control '" + cd.fullName + "'");
			createControl(cd);
		}
	}

	/**
	 * Create an ControlDelegate and the corresponding Swing descendant control
	 * for the given control description. Throws an exception if the image(s) of
	 * the control cannot be loaded, or if the type of this control cannot be
	 * associated.
	 */
	protected void createControl(ControlDefinition def) throws Exception {
		boolean isMasterPanel = def.fullName.equals(SkinFileReader.MASTER_PANEL_NAME);
		if (isMasterPanel) {
			def.sourcePos[ControlState.NORMAL.ordinal()] = new Pos();
		}
		ControlDelegate cd = createDelegateFromDefinition(def);
		cd.createUI(imageManager);
		MControl c = createControlFromDelegate(cd);
		if (isMasterPanel) {
			masterPanel = (MPanel) c;
		} else if (c != null
				&& c.getDelegate().getCtrlDef().targetBounds.x >= 0) {
			// only assign a parent if position is >= 0
			String parent = def.parent;
			if (parent == null || parent.length() == 0) {
				if (masterPanel == null) {
					throw new Exception(
							"control '"
									+ def.fullName
									+ "' is defined without specifying the master panel first.");
				}
				masterPanel.add((Component) c);
			} else {
				// find the referenced panel
				parent = "panel." + parent;
				ControlDelegate mparent = controls.get(parent);
				if (mparent == null || !(mparent.getOwner() instanceof MPanel)) {
					throw new Exception("Control " + def.fullName
							+ "'s parent panel '" + def.parent
							+ "' cannot be found.");
				}
				MPanel jparent = (MPanel) mparent.getOwner();
				// mc's coordinates are given as relative to the master panel.
				// Need to make the coordinates relative to the panel
				jparent.add((Component) c);
				makeControlPositionRelative(def.fullName, (Component) c,
						masterPanel);
			}
		}
		controls.put(def.fullName, cd);
	}

	/**
	 * Make the position of control c relative to its new parent. On entry, the
	 * component's location is relative to <code>referenceComponent</code>
	 * (which should be in the parent hierarchy of c). On exit, the control's
	 * position is relative to its immediate parent.
	 * 
	 * @param name the full name of the control, for debugging
	 * @param c the component which shall be repositioned to the relative
	 *            position of its new parent.
	 * @param referenceComponent the reference in which coordinates c's bounds
	 *            are set before calling this method
	 */
	public static void makeControlPositionRelative(String name, Component c,
			Component referenceComponent) {
		Rectangle r = c.getBounds();
		Container cparent = c.getParent();
		while (cparent != null && cparent != referenceComponent) {
			r.translate(-cparent.getX(), -cparent.getY());
			cparent = cparent.getParent();
		}
		if (TRACE && false) {
			debug(name + ": move from " + c.getLocation() + " to "
					+ r.getLocation());
		}
		c.setBounds(r);
	}

	/**
	 * Create an instance of ControlDelegate for the given control definition.
	 * 
	 * @param def the control definition to use as base for this control
	 * @return the ControlDelegate instance
	 */
	private ControlDelegate createDelegateFromDefinition(ControlDefinition def) {
		// String type = def.getType();
		ControlDelegate ret;
		// if (type.equals(CONTROL_NAME_TROUGH)) {
		// ret = new ControlDelegateSlider(def);
		// } else {
		ret = new ControlDelegate(def);
		// }
		return ret;
	}

	/**
	 * create an MControl (like MButton, MPanel, etc.) from the given
	 * ControlDelegate.
	 * 
	 * @return the control, or null if the control type does not have a control
	 *         associated, like the knob of a slider.
	 * @throws Exception if the control type is not known
	 */
	public MControl createControlFromDelegate(ControlDelegate mc)
			throws Exception {
		String type = mc.getCtrlDef().getType();
		MControl ret;
		if (type.equals("button")) {
			ret = new MButton(mc);
		} else if (type.equals("panel")) {
			ret = new MPanel(mc);
		} else if (type.equals("toggle")) {
			ret = new MToggle(mc);
		} else if (type.equals("edit")) {
			ret = new MEdit(mc);
		} else if (type.equals("label")) {
			ret = new MLabel(mc);
		} else if (type.equals("LED")) {
			ret = new MLED(mc);
		} else if (type.equals(CONTROL_NAME_KNOB)) {
			ret = null;
		} else if (type.equals(CONTROL_NAME_TROUGH)) {
			ret = new MSlider(this, mc);
		} else {
			throw new Exception("Type '" + type + "' of control "
					+ mc.getCtrlDef().fullName + " is not supported.");
		}
		return ret;
	}

	/**
	 * Return the instance of the master panel that houses all other controls.
	 * 
	 * @return the masterPanel
	 */
	public MPanel getMasterPanel() {
		return masterPanel;
	}

	/** get a control by its type.name. Return null if this control is not found. */
	public ControlDelegate getDelegate(String typeAndName) {
		ControlDelegate ret = controls.get(typeAndName);
		if (ret == null && typeAndName.indexOf("test") < 0) {
			debug("Warning: cannot find control definition for " + typeAndName);
		}
		return ret;
	}

	/**
	 * get the associated MControl from its type.name. Return null if this
	 * control is not found.
	 */
	public MControl getControl(String typeAndName) {
		ControlDelegate mc = getDelegate(typeAndName);
		if (mc != null) {
			return mc.getOwner();
		}
		return null;
	}

	/** get a control by its type.name. Throw an exception if not found. */
	public ControlDelegate getDelegateExc(String typeAndName) throws Exception {
		ControlDelegate mc = getDelegate(typeAndName);
		if (mc == null) {
			throw new Exception("Error: cannot find control definition for "
					+ typeAndName);
		}
		return mc;
	}

	/**
	 * get the associated MControl from its type.name. Throw an exception if not
	 * found.
	 */
	public MControl getControlExc(String typeAndName) throws Exception {
		return getDelegateExc(typeAndName).getOwner();
	}
}
