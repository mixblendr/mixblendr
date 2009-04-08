/**
 *
 */
package com.mixblendr.effects;

import static com.mixblendr.util.Debug.debug;

import java.awt.Font;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;

import org.tritonus.share.sampled.FloatSampleBuffer;

import com.mixblendr.audio.AudioTrack;
import com.mixblendr.audio.AutomationHandler;
import com.mixblendr.audio.AutomationManager;
import com.mixblendr.audio.AutomationObject;
import com.mixblendr.util.GUIUtils;

/**
 * A new approach to the delay effect. Compresses the delay buffer when changing
 * delay time instead of fading in/out.
 * 
 * @author Florian Bomers
 */
public class Delay2 extends GUIEffectsBase {

	private static final boolean DEBUG_DELAY2 = false;

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
	private double delayBufferReadPos;
	private double delayBufferWritePos;

	// automation support
	private static AutomationHandler delayTimeHandler = AutomationManager.getHandler(DelayTimeAutomation.class);
	private static AutomationHandler feedbackHandler = AutomationManager.getHandler(FeedbackAutomation.class);
	private static AutomationHandler balanceHandler = AutomationManager.getHandler(BalanceAutomation.class);

	/** create a new instance of the Delay effect */
	public Delay2() {
		super("Delay 2");
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
			} else if (delayTimeSamples > delayBuffer.getSampleCount()) {
				delayTimeSamples = delayBuffer.getSampleCount();
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

	/** maximum of 2 seconds delay buffer */
	private static final int MAX_DELAY_SAMPLES = 88200;

	@Override
	public void initImpl() {
		if (state == null) return;
		if (delayBuffer == null) {
			delayBuffer = new FloatSampleBuffer(state.getChannels(),
					MAX_DELAY_SAMPLES, state.getSampleRate());
		}
		// default values
		setDelayTimeBeats(1 / 8.0);
		setFeedback(0.5);
		setBalance(0.0);
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
			int delayBufferCount = delayBuffer.getSampleCount();

			// by which read/write pos are increased for every sample, to
			// account for non-integral delay times
			double writePosInc = ((int) delayTimeSamples) / delayTimeSamples;
			double readPosInc = writePosInc;

			// local variables as optimization
			double writePos = delayBufferWritePos;
			double readPos = delayBufferReadPos;

			// if delay time has changed, read pos (dependent on write Pos)
			// needs
			// to slowly drift away/towards writePos so that it will reach the
			// target delay eventually
			double targetReadPos = writePos - delayTimeSamples;
			if (targetReadPos < 0) {
				targetReadPos += delayBufferCount;
			}
			if (readPos == writePos) {
				// at beginning, no transition necessary
				readPos = targetReadPos;
			} else {
				readPosInc += (targetReadPos - readPos) / sampleCount;
				// do not increase too much, otherwise will be audible
				// high-pitched...
				if (readPosInc > 3) {
					readPosInc = 1.5;
				}
			}
			double startReadPos = readPos;

			for (int c = 0; c < buffer.getChannelCount(); c++) {
				int thisCount = sampleCount;
				float[] delay = delayBuffer.getChannel(c);
				float[] inout = buffer.getChannel(c);
				int thisOffset = offset;

				// re-initialize for every channel
				writePos = delayBufferWritePos;
				readPos = startReadPos;

				while (thisCount > 0) {
					// the actual delay line!
					float io = inout[thisOffset];
					int readOffset = (int) readPos;
					int writeOffset = (int) writePos;
					float dr = delay[readOffset];
					inout[thisOffset] = (float) (dr * delayVol + io * srcVol);
					delay[writeOffset] = (float) (dr * feedback + io);
					thisOffset++;
					thisCount--;
					writePos += writePosInc;
					if ((int) writePos >= delayBufferCount) {
						writePos -= delayBufferCount;
					}
					readPos += readPosInc;
					if ((int) readPos >= delayBufferCount) {
						readPos -= delayBufferCount;
					}
				}
			}
			// store state for next audio block
			delayBufferReadPos = readPos;
			delayBufferWritePos = writePos;
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
		JLabel title = GUIUtils.createLabel("Delay2 Effect",
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
			if (DEBUG_DELAY2) debug("Delay Time tracking: " + on);
		} else if (src == sFeedback.slider) {
			feedbackHandler.setTracking(track, on);
			if (DEBUG_DELAY2) debug("Feedback tracking: " + on);
		} else if (src == sBalance.slider) {
			balanceHandler.setTracking(track, on);
			if (DEBUG_DELAY2) debug("Balance tracking: " + on);
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
			updateGUIDelayTimeLabel();
			if (noUpdate == 0) {
				updateDelayTimeFromGUI();
			}
		} else if (src == sFeedback.slider) {
			updateGUIFeedbackLabel();
			if (noUpdate == 0) {
				updateFeedbackFromGUI();
			}
		} else if (src == sBalance.slider) {
			updateGUIBalanceLabel();
			if (noUpdate == 0) {
				updateBalanceFromGUI();
			}
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
