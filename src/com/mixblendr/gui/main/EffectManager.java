/**
 *
 */
package com.mixblendr.gui.main;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JOptionPane;

import com.mixblendr.audio.AudioEffect;
import com.mixblendr.audio.AudioTrack;
import com.mixblendr.effects.Delay;
import com.mixblendr.effects.Delay2;
import com.mixblendr.effects.Flanger;
import com.mixblendr.util.Debug;

/**
 * Class managing the installed effects.
 * 
 * @author Florian Bomers
 */
@SuppressWarnings("unchecked")
class EffectManager {

	public static final String EFFECT_NONE = "<no effect>";

	public static final Class[] EFFECT_CLASSES = {
			Delay.class, Flanger.class, Delay2.class
	};

	private static List<String> effectNames = null;
	private static List<Class> effectClass = null;

	/**
	 * Remove all dependencies, stop audio engine, etc.
	 */
	synchronized void close() {
		// nothing yet
	}

	private static void constructEffectNames() {
		if (effectNames == null) {
			effectNames = new ArrayList<String>(EFFECT_CLASSES.length);
			effectClass = new ArrayList<Class>(EFFECT_CLASSES.length);
			for (Class c : EFFECT_CLASSES) {
				try {
					effectNames.add(((AudioEffect) c.newInstance()).getShortName());
					effectClass.add(c);
				} catch (Exception e) {
					Debug.debug(e);
				}
			}
		}
	}

	/**
	 * Get the list of installed effects.
	 * 
	 * @return the list of installed effects as a String list
	 */
	public static List<String> getEffectNames() {
		constructEffectNames();
		return Collections.unmodifiableList(effectNames);
	}

	/**
	 * Choose an effect using the EffectChooser dialog
	 */
	public static synchronized AudioEffect chooseEffect(Globals globals,
			Component parent, AudioTrack track, AudioEffect def) {
		constructEffectNames();
		int effectCount = effectNames.size();
		Object[] values = new Object[effectCount + 1];
		int sel = 0;
		values[0] = EFFECT_NONE;
		String defName = EFFECT_NONE;
		if (def != null) {
			defName = def.getShortName();
		}

		for (int i = 0; i < effectCount; i++) {
			String val = effectNames.get(i);
			values[i + 1] = val;
			if (def != null && sel == 0 && val.equals(defName)) {
				// the default selection
				sel = i + 1;
			}
		}

		Object selected = JOptionPane.showInputDialog(parent,
				"Select the effect:", "Effect Selector",
				JOptionPane.INFORMATION_MESSAGE, null /* icon */, values,
				values[sel]);
		if (selected != null && !selected.equals(defName)) {
			if (selected == values[0]) {
				def = null;
			} else {
				try {
					for (int i = 0; i < effectCount; i++) {
						if (selected == values[i + 1]) {
							def = (AudioEffect) effectClass.get(i).newInstance();
							def.init(globals.getState(), globals.getPlayer(),
									track);
							break;
						}
					}
				} catch (Exception e) {
					Debug.debug(e);
				}
			}
		}

		return def;
	}

}
