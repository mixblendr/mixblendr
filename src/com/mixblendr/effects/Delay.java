/**
 *
 */
package com.mixblendr.effects;

import java.awt.Font;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.ChangeEvent;

import org.tritonus.share.sampled.FloatSampleBuffer;
import com.mixblendr.audio.*;
import com.mixblendr.util.GUIUtils;

import static com.mixblendr.util.Debug.*;

/**
 * A delay effect.
 * 
 * @author Florian Bomers
 */
public class Delay extends GUIEffectsBase {

	private static final boolean DEBUG_DELAY = false;

	// delay params
	private final static String[] DELAY_TIME_BEAT_CAPTIONS = {
			"1/256", "1/128", "1/64", "1/32", "1/16", "1/8", "1/4", "1/2",
	};

	private final static double[] DELAY_TIME_BEAT_FRACTIONS = {
			1 / 64.0, 1 / 32.0, 1 / 16.0, 1 / 8.0, 1 / 4.0, 1 / 2.0, 1.0, 2.0,
	};

	private double delayTimeBeats;
	private double delayTimeSamples;
	private double feedback;
	private double balance;

	// runtime state
	private FloatSampleBuffer delayBuffer;
	private double delayBufferPos;

	// apply a micro fade in/out when changing the buffer size
	private final static int FADEOUT_BUFFER_SAMPLECOUNT = 100;
	private FloatSampleBuffer fadeOutBuffer;
	private boolean nextBufferDoFade = true;

	// automation support
	private static AutomationHandler delayTimeHandler = AutomationManager.getHandler(DelayTimeAutomation.class);
	private static AutomationHandler feedbackHandler = AutomationManager.getHandler(FeedbackAutomation.class);
	private static AutomationHandler balanceHandler = AutomationManager.getHandler(BalanceAutomation.class);

	/** create a new instance of the Delay effect */
	public Delay() {
		super("Delay");
		assert (DELAY_TIME_BEAT_CAPTIONS.length == DELAY_TIME_BEAT_FRACTIONS.length);
	}

	/**
	 * @return the delay time in beats
	 */
	public double getDelayTimeBeats() {
		return delayTimeBeats;
	}

	/**
	 * @param delayTimeBeats the delay time in beats to set
	 */
	public void setDelayTimeBeats(double delayTimeBeats) {
		if (state == null) return;
		synchronized (lock) {
			this.delayTimeBeats = delayTimeBeats;
			this.delayTimeSamples = state.beat2sample(delayTimeBeats);
			if (delayTimeSamples <= 0) {
				delayTimeSamples = 0.1;
			}
			int oldSampleCount = delayBuffer.getSampleCount();
			int newSampleCount = (int) (delayTimeSamples + 1);

			if (newSampleCount != oldSampleCount) {
				// copying some samples from the current position to the fadeout
				// buffer
				int fadeOutCount = FADEOUT_BUFFER_SAMPLECOUNT;
				if (fadeOutCount > oldSampleCount) {
					fadeOutCount = oldSampleCount;
				}
				fadeOutBuffer.changeSampleCount(fadeOutCount, false);
				// take advantage of copyTo()'s adjustment of "count"
				int copied = delayBuffer.copyTo((int) delayBufferPos,
						fadeOutBuffer, 0, fadeOutCount);
				delayBuffer.copyTo(0, fadeOutBuffer, copied, fadeOutCount
						- copied);
				if (DEBUG_DELAY) {
					debug("Fading out old delay buffer:");
					debug("- old size=" + oldSampleCount
							+ " samples  new size=" + newSampleCount
							+ " samples");
					debug("- copied " + copied
							+ " samples from delay buffer at "
							+ ((int) delayBufferPos)
							+ " to fade buffer at position 0");
					debug("- copied "
							+ (fadeOutCount - copied)
							+ " samples from delay buffer's beginning to fade buffer at position "
							+ copied);
					debug("- apply new delay buffer size and silence it");
					debug("- next rendering time, fade in the signal");
				}
				// silence the new buffer
				delayBuffer.changeSampleCount(newSampleCount, false);
				delayBuffer.makeSilence();
				delayBufferPos = 0;
				nextBufferDoFade = true;
			}
		}
	}

	/**
	 * @return the delay time in samples
	 */
	public double getDelayTimeSamples() {
		return delayTimeSamples;
	}

	/**
	 * @return the delay time in milliseconds
	 */
	public double getDelayTimeMillis() {
		if (state == null) return 1;
		return state.beat2seconds(delayTimeBeats) * 1000.0;
	}

	/**
	 * @return the feedback [0..1]
	 */
	public double getFeedback() {
		return feedback;
	}

	/**
	 * @param feedback the feedback to set [0..1]
	 */
	public void setFeedback(double feedback) {
		this.feedback = feedback;
	}

	/**
	 * @return the balance [-1...0...+1]
	 */
	public double getBalance() {
		return balance;
	}

	/**
	 * @param balance the balance to set [-1...0...+1]
	 */
	public void setBalance(double balance) {
		this.balance = balance;
	}

	// --------------------------------- AudioEffect methods

	@Override
	public void initImpl() {
		if (state == null) return;
		if (delayBuffer == null) {
			delayBuffer = new FloatSampleBuffer(state.getChannels(), 0,
					state.getSampleRate());
			fadeOutBuffer = new FloatSampleBuffer(state.getChannels(), 0,
					state.getSampleRate());
		}
		// default values
		setDelayTimeBeats(1 / 8.0);
		setFeedback(0.5);
		setBalance(0.0);
		nextBufferDoFade = true;
	}

	@Override
	public void exitImpl() {
		delayBuffer = null;
	}

	/**
	 * the actual delay processor: feed the current buffer to the circular delay
	 * buffer, and add the current delay buffer contents to the output buffer
	 */
	@Override
	public boolean process(long samplePos, FloatSampleBuffer buffer,
			int offset, int sampleCount) {
		// sanity
		if (delayBuffer == null || sampleCount == 0) return false;
		synchronized (lock) {
			double srcVol;
			double delayVol;
			if (balance < 0) {
				srcVol = 1.0;
				delayVol = (1 + balance);
			} else {
				srcVol = 1 - balance;
				delayVol = 1.0;
			}
			int delayCount = delayBuffer.getSampleCount();
			double delayBufferInc = delayCount / delayTimeSamples;
			double delayPos = delayBufferPos;
			boolean doFade = nextBufferDoFade;
			nextBufferDoFade = false;
			float inoutVol = 1.0f;
			float inoutVolInc = 0.0f;
			if (doFade) {
				inoutVolInc = 1.0f / buffer.getSampleCount();
				if (DEBUG_DELAY) {
					debug("- Fading in...");
				}
			}
			for (int c = 0; c < buffer.getChannelCount(); c++) {
				int thisCount = sampleCount;
				float[] delay = delayBuffer.getChannel(c);
				float[] inout = buffer.getChannel(c);
				int thisOffset = offset;
				delayPos = delayBufferPos;
				while ((int) delayPos >= delayCount) {
					delayPos -= delayCount;
				}
				if (doFade) {
					inoutVol = 0.0f;
				}
				while (thisCount > 0) {
					// the actual delay line!
					float io = inout[thisOffset];
					int dOffset = (int) delayPos;
					float d = delay[dOffset];
					inout[thisOffset] = (float) (d * delayVol * feedback + io
							* srcVol);
					delay[dOffset] = (float) (d * feedback + (io * inoutVol));
					thisOffset++;
					thisCount--;
					delayPos += delayBufferInc;
					if ((int) delayPos >= delayCount) {
						delayPos -= delayCount;
					}
					if (doFade) {
						inoutVol += inoutVolInc;
					}
				}
			}
			// now apply the fade buffer
			if (doFade) {
				// fade out the fade buffer
				int fadeOutCount = fadeOutBuffer.getSampleCount();
				if (fadeOutCount > buffer.getSampleCount()) {
					fadeOutCount = buffer.getSampleCount();
				}
				fadeOutBuffer.linearFade((float) (delayVol * feedback), 0.0f,
						0, fadeOutCount);
				buffer.mix(fadeOutBuffer, 0, 0, fadeOutCount);
				if (DEBUG_DELAY) {
					debug("- Applied " + fadeOutCount + " from fadeOutBuffer");
				}

			}
			delayBufferPos = delayPos;
			return true;
		}
	}

	// --------------------------------- GUI stuff

	private SliderStrip sDelayTime;
	private SliderStrip sFeedback;
	private SliderStrip sBalance;
	/**
	 * if this flag is non-zero, controls are currently set programmatically
	 * rather than from user interaction
	 */
	private int noUpdate = 0;

	@Override
	protected void initGUI(JPanel main) {
		main.setLayout(new BoxLayout(main, BoxLayout.PAGE_AXIS));
		main.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		// Title
		JLabel title = GUIUtils.createLabel("Delay Effect",
				SwingConstants.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		main.add(title);
		main.add(new JSeparator(SwingConstants.HORIZONTAL));

		// slider for Delay Time
		main.add((sDelayTime = new SliderStrip("Time:", 0,
				DELAY_TIME_BEAT_CAPTIONS.length - 1, 3, "short", "long")));
		sDelayTime.slider.setMajorTickSpacing(1);
		sDelayTime.slider.setPaintTicks(true);
		sDelayTime.slider.setSnapToTicks(true);

		// slider for Feedback and Balance
		main.add((sFeedback = new SliderStrip("Feedback:", 0, 100, 50, "none",
				"full")));
		main.add((sBalance = new SliderStrip("Balance:", -100, 100, 0, "dry",
				"wet")));

		// init labels
		updateGUIDelayTimeLabel();
		updateGUIFeedbackLabel();
		updateGUIBalanceLabel();
	}

	/** update the GUI with the current delay time */
	protected void updateGUIDelayTime() {
		int index = -1;
		for (int i = 0; i < DELAY_TIME_BEAT_FRACTIONS.length; i++) {
			if (delayTimeBeats <= DELAY_TIME_BEAT_FRACTIONS[i]) {
				index = i;
				break;
			}
		}
		if (index < 0) {
			index = DELAY_TIME_BEAT_FRACTIONS.length - 1;
		}
		noUpdate++;
		try {
			// will cause change event and update the label
			sDelayTime.slider.setValue(index);
		} finally {
			noUpdate--;
		}
	}

	/**
	 * read the current value from the slider and set the internal value
	 * accordingly
	 */
	protected void updateDelayTimeFromGUI() {
		int index = sDelayTime.slider.getValue();
		setDelayTimeBeats(DELAY_TIME_BEAT_FRACTIONS[index]);
	}

	/** update the label of the with the current delay time */
	private void updateGUIDelayTimeLabel() {
		sDelayTime.label.setText(DELAY_TIME_BEAT_CAPTIONS[sDelayTime.slider.getValue()]);
	}

	/** update the GUI with the current feedback */
	protected void updateGUIFeedback() {
		// will cause change event and update the label
		noUpdate++;
		try {
			sFeedback.slider.setValue((int) (feedback * 100.0));
		} finally {
			noUpdate--;
		}
	}

	/**
	 * read the current value from the slider and set the internal value
	 * accordingly
	 */
	protected void updateFeedbackFromGUI() {
		int index = sFeedback.slider.getValue();
		setFeedback(index / 100.0);
	}

	/** update the label of the with the current feedback */
	private void updateGUIFeedbackLabel() {
		sFeedback.label.setText(Integer.toString(sFeedback.slider.getValue())
				+ " %");
	}

	/** update the GUI with the current balance */
	protected void updateGUIBalance() {
		// will cause change event and update the label
		noUpdate++;
		try {
			sBalance.slider.setValue((int) (balance * 100.0));
		} finally {
			noUpdate--;
		}
	}

	/**
	 * read the current value from the slider and set the internal value
	 * accordingly
	 */
	protected void updateBalanceFromGUI() {
		int index = sBalance.slider.getValue();
		setBalance(index / 100.0);
	}

	/** update the label of the with the current balance */
	private void updateGUIBalanceLabel() {
		sBalance.label.setText(Integer.toString(sBalance.slider.getValue())
				+ " %");
	}

	// --------------------------------- interface MouseListener

	/**
	 * if automation is currently enabled, add an appropriate automation event,
	 * depending on the given GUI control
	 */
	private void addAutomationEvent(Object src) {
		if ((track != null) && track.isAutomationEnabled()) {
			if (src == sDelayTime.slider) {
				track.addAutomationObject(new DelayTimeAutomation());
			} else if (src == sFeedback.slider) {
				track.addAutomationObject(new FeedbackAutomation());
			} else if (src == sBalance.slider) {
				track.addAutomationObject(new BalanceAutomation());
			}
		}
	}

	/** set tracking for the selected GUI object on or off. */
	private void setTracking(Object src, boolean on) {
		if (src == sDelayTime.slider) {
			delayTimeHandler.setTracking(track, on);
			if (DEBUG_DELAY) debug("Delay Time tracking: " + on);
		} else if (src == sFeedback.slider) {
			feedbackHandler.setTracking(track, on);
			if (DEBUG_DELAY) debug("Feedback tracking: " + on);
		} else if (src == sBalance.slider) {
			balanceHandler.setTracking(track, on);
			if (DEBUG_DELAY) debug("Balance tracking: " + on);
		}
		// add initial automation state
		if (on) {
			addAutomationEvent(src);
		}
	}

	/**
	 * called when the user clicks on a slider. In response, notify the engine
	 * that we're tracking this automation object.
	 */
	@Override
	public void mousePressed(MouseEvent e) {
		setTracking(e.getSource(), true);
	}

	/**
	 * called when the user releases the mouse button from a slider. Notify the
	 * engine that we're not tracking this automation object anymore.
	 */
	@Override
	public void mouseReleased(MouseEvent e) {
		setTracking(e.getSource(), false);
	}

	// ----------------------------------------- interface ChangeListener

	/**
	 * Called when the user or the implementation moves a slider. Update the
	 * slider labels. If not currently set by the implementation, update the
	 * internal value. If automation is active, create an automation object and
	 * add it to the track.
	 */
	@Override
	public void stateChanged(ChangeEvent e) {
		Object src = e.getSource();
		if (src == sDelayTime.slider) {
			if (noUpdate == 0) {
				updateDelayTimeFromGUI();
			}
			updateGUIDelayTimeLabel();
		} else if (src == sFeedback.slider) {
			if (noUpdate == 0) {
				updateFeedbackFromGUI();
			}
			updateGUIFeedbackLabel();
		} else if (src == sBalance.slider) {
			if (noUpdate == 0) {
				updateBalanceFromGUI();
			}
			updateGUIBalanceLabel();
		}
		if (noUpdate == 0) {
			addAutomationEvent(src);
		}
	}

	// ----------------------------------------- AUTOMATION

	/** the automation object to record a change in delay time */
	private class DelayTimeAutomation extends AutomationObject {

		private double aoDelayTimeBeats;

		/**
		 * Create a delay time automation object capturing the current playback
		 * time and the current delay time.
		 */
		public DelayTimeAutomation() {
			super(state, player.getPositionSamples());
			this.aoDelayTimeBeats = getDelayTimeBeats();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.mixblendr.audio.AutomationObject#executeImpl(com.mixblendr.audio.AudioTrack)
		 */
		@Override
		protected void executeImpl(AudioTrack aTrack) {
			if (state == null) return;
			setDelayTimeBeats(aoDelayTimeBeats);
			updateGUIDelayTime();
		}

		/**
		 * @return a string representation of this object (mainly for debugging
		 *         purposes)
		 */
		@Override
		public String toString() {
			return super.toString() + ", delay time=" + aoDelayTimeBeats
					+ " beats";
		}
	}

	/** the automation object to record a change in feedback */
	private class FeedbackAutomation extends AutomationObject {
		private double aoFeedback;

		/**
		 * Create a feedback automation object capturing the current playback
		 * time and the current feedback.
		 */
		public FeedbackAutomation() {
			super(state, player.getPositionSamples());
			this.aoFeedback = getFeedback();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.mixblendr.audio.AutomationObject#executeImpl(com.mixblendr.audio.AudioTrack)
		 */
		@Override
		protected void executeImpl(AudioTrack aTrack) {
			if (state == null) return;
			setFeedback(aoFeedback);
			updateGUIFeedback();
		}

		/**
		 * @return a string representation of this object (mainly for debugging
		 *         purposes)
		 */
		@Override
		public String toString() {
			return super.toString() + ", feedback=" + aoFeedback;
		}
	}

	/** the automation object to record a change in balance */
	private class BalanceAutomation extends AutomationObject {
		private double aoBalance;

		/**
		 * Create a balance automation object capturing the current playback
		 * time and the current balance.
		 */
		public BalanceAutomation() {
			super(state, player.getPositionSamples());
			this.aoBalance = getBalance();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see com.mixblendr.audio.AutomationObject#executeImpl(com.mixblendr.audio.AudioTrack)
		 */
		@Override
		protected void executeImpl(AudioTrack aTrack) {
			if (state == null) return;
			setBalance(aoBalance);
			updateGUIBalance();
		}

		/**
		 * @return a string representation of this object (mainly for debugging
		 *         purposes)
		 */
		@Override
		public String toString() {
			return super.toString() + ", balance=" + aoBalance;
		}
	}

	/* satisfy compiler */
	private static final long serialVersionUID = 0;
}
