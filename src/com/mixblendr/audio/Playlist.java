/**
 *
 */
package com.mixblendr.audio;

import java.util.*;

import org.tritonus.share.sampled.FloatSampleBuffer;
import static com.mixblendr.util.Debug.*;

/**
 * The playlist takes care of a series of regions and all the automation data.
 * 
 * @author Florian Bomers
 */
// IDEA: use interpolation search in indexOf()
// IDEA: or use own class for "elements", using interpolation search for
// indexOf().
public class Playlist implements AudioInput {

	private final static boolean TRACE_FADE = false;

	private final static boolean DEBUG_PLAYLIST = false;

	private AudioState state;

	/** maintain the next expected sample position to detect jumps */
	private long nextSamplePos = 0;

	/** the owner of this playlist */
	private AudioTrack owner;

	private List<AutomationObject> elements = null;

	/** prevent instanciation of def constructor */
	private Playlist() {
		super();
		owner = null;
		elements = new ArrayList<AutomationObject>();
		initRegionPlayback();
	}

	/**
	 * @param state
	 * @param owner
	 */
	public Playlist(AudioState state, AudioTrack owner) {
		this();
		this.state = state;
		this.owner = owner;
	}

	/**
	 * @return the owner
	 */
	public AudioTrack getOwner() {
		return owner;
	}

	/**
	 * @return the state
	 */
	public AudioState getState() {
		return state;
	}

	/**
	 * insert this automation object to this playlist. It will be available
	 * immediately for playback. If an automation object of the same type
	 * (except AudioRegions) already exists at this exact sample position, the
	 * old one is overwritten.
	 */
	public synchronized void addObject(AutomationObject ao) {
		ao.owner = this;
		int i = 0;
		int c = elements.size();
		long aost = ao.getStartTimeSamples();
		boolean isRegion = (ao instanceof AudioRegion);
		boolean sameTimeSameClass = false;
		while (i < c) {
			AutomationObject el = elements.get(i);
			long startTime = el.getStartTimeSamples();
			if (startTime >= aost) {
				sameTimeSameClass = (!isRegion && startTime == aost && el.isSameTypeInstance(ao));
				break;
			}
			i++;
		}
		if (sameTimeSameClass) {
			if (DEBUG_PLAYLIST) {
				debug("Overwriting " + elements.get(i) + "  with " + ao);
			}
			// just overwrite this entry
			onRemoval(elements.get(i));
			elements.set(i, ao);
		} else if (i >= c) {
			elements.add(ao);
			if (DEBUG_PLAYLIST) {
				debug("adding at end: " + ao);
			}
		} else {
			elements.add(i, ao);
			if (DEBUG_PLAYLIST) {
				debug("adding at index " + i + ": " + ao);
			}
		}
		if (!sameTimeSameClass && !isRegion && nextSamplePos >= 0) {
			// prevent re-initialization
			// attention: make sure to not delete itself!
			if (i <= currElementIndex) {
				currElementIndex++;
			}
		} else {
			initRegionPlayback();
		}
	}

	/**
	 * remove this automation object from this playlist. This change will be
	 * audible immediately. This method is ignored if <code>ao</code> is
	 * <code>null</code>.
	 * 
	 * @return true if the playlist contained this element
	 */
	public synchronized boolean removeObject(AutomationObject ao) {
		if (ao == null) return false;
		boolean ret = elements.remove(ao);
		if (ret) {
			onRemoval(ao);
			initRegionPlayback();
		}


        return ret;
	}


    public double getStartTime()
    {
        double time =-1;
        for (AutomationObject el : elements)
        {
            if (el instanceof AudioRegion)
            {
                AudioRegion audioRegion = (AudioRegion)el;
                double regionTime = audioRegion.getStartTimeSec();
                if (regionTime > -1)
                {
                    if (time == -1 || time > regionTime)
                    {
                        time = regionTime;
                    }
                }

            }
        }

        return time;
    }

    /**
	 * @return number of automation objects in this playlist
	 */
	public synchronized int getObjectCount() {
		return elements.size();
	}

	/**
	 * @return the number of audio regions among the total list of automation
	 *         objects
	 */
	public synchronized int getAudioRegionCount() {
		int ret = 0;
		for (AutomationObject ao : elements) {
			if (ao instanceof AudioRegion) {
				ret++;
			}
		}
		return ret;
	}

	/**
	 * Get the index of this automation object in the list of objects
	 * 
	 * @param ao the object to find
	 * @return the index of the specified object, or -1 if not found
	 */
	public synchronized int indexOf(AutomationObject ao) {
		// TODO: use binary/interpolation search
		return elements.indexOf(ao);
	}

	/**
	 * @return an array of all audio regions in this playlist
	 */
	public synchronized AudioRegion[] getAudioRegions() {
		return getAudioRegions((AudioRegion[]) null);
	}

	/**
	 * Fill template with all audioregions in this playlist. If the template is
	 * null or has fewer elements than regions in this playlist, a new array is
	 * created. If template has more elements, the remaining elements are set to
	 * null.
	 * 
	 * @param template if non-null, this array is filled, if it has enough
	 *            elements and it is returned. Otherwise, a new array is
	 *            created.
	 * @return an array of all audio regions in this playlist
	 */
	public synchronized AudioRegion[] getAudioRegions(AudioRegion[] template) {
		int count = getAudioRegionCount();
		if (template == null || template.length < count) {
			template = new AudioRegion[count];
		}
		int i = 0;
		for (AutomationObject ao : elements) {
			if (ao instanceof AudioRegion) {
				template[i++] = (AudioRegion) ao;
			}
		}
		// nullify any remaining elements
		while (i < template.length) {
			template[i++] = null;
		}
		return template;
	}

	/**
	 * Fill the given list with all audioregions in this playlist.
	 * 
	 * @param list the list to be filled, or null to create a new list.
	 * @return the list, or the newly created list
	 */
	public synchronized List<AudioRegion> getAudioRegions(List<AudioRegion> list) {
		if (list == null) {
			list = new ArrayList<AudioRegion>();
		} else {
			list.clear();
		}
		for (AutomationObject ao : elements) {
			if (ao instanceof AudioRegion) {
				list.add((AudioRegion) ao);
			}
		}
		return list;
	}

	/**
	 * Return the region following the specified region
	 * 
	 * @param region the region left to the returned region
	 * @return the region following after <code>region</code>, or null if no
	 *         region following.
	 */
	public synchronized AudioRegion getRegionAfter(AudioRegion region) {
		int i = indexOf(region);
		if (i >= 0) {
			i++;
			for (; i < elements.size(); i++) {
				if (elements.get(i) instanceof AudioRegion) {
					return (AudioRegion) elements.get(i);
				}
			}
		}
		return null;
	}

	/**
	 * @return the indexed automation object
	 */
	public synchronized AutomationObject getObject(int index) {
		return elements.get(index);
	}

	/**
	 * called internally when an automation object is removed. This method is
	 * ignored if ao is null.
	 */
	private void onRemoval(AutomationObject ao) {
		if (ao == null) return;
		if (ao.owner == this) {
			ao.owner = null;
			if (ao instanceof AudioRegion) {
				((AudioRegion) ao).close();
			}
		}
	}

	/**
	 * Convenience method to cut the passed region at splitSample. The passed
	 * region will be truncated to <code>splitSample</code> samples. A new
	 * region will be created as a clone of <code>region</code>. It starts
	 * with splitSample and has the remaining duration of region. This method
	 * will add the new region automatically to this playlist.
	 * 
	 * @param region the region to split, it will be the first portion that
	 *            remains
	 * @param splitSample the sample position where to cut this region in two
	 * @return the second, new region or null if the split point is after the
	 *         current region
	 */
	public synchronized AudioRegion splitRegion(AudioRegion region,
			long splitSample) {
		// first see if there is anything left
		long newDuration = region.getDuration();
		if (newDuration >= 0) {
			newDuration -= splitSample;
			if (newDuration < 0) {
				newDuration = 0;
			}
		}
		AudioRegion newRegion = null;
		// only if the split point is actually in the file
		if (newDuration != 0) {
			newRegion = (AudioRegion) region.clone();
			newRegion.setStartTimeSamples(newRegion.getStartTimeSamples()
					+ splitSample);
			newRegion.setAudioFileOffset(newRegion.getAudioFileOffset()
					+ splitSample);
			newRegion.setDuration(newDuration);
			addObject(newRegion);
		}
		region.setDuration(splitSample);
		return newRegion;
	}

	/**
	 * Get the duration of this track. This may change depending on the
	 * availability of currently downloaded media
	 */
	public long getDurationSamples() {
		if (elements.size() == 0) {
			return 0;
		}
		AutomationObject ao = elements.get(elements.size() - 1);
		if (ao instanceof AudioRegion) {
			return ao.getStartTimeSamples()
					+ ((AudioRegion) ao).getAvailableSamples();
		}
		return ao.getStartTimeSamples();
	}

	/**
	 * Remove all elements from this playlist.
	 */
	public synchronized void clear() {
		for (AutomationObject ao : elements) {
			onRemoval(ao);
		}
		elements.clear();
		initRegionPlayback();
	}

	/**
	 * called by the automation objects whenever its start time was changed, so
	 * that it can be sorted again into the list of objects
	 */
	synchronized void automationObjectStartChanged(AutomationObject ao) {
		assert (ao.owner == this);
		if (!(ao instanceof AudioRegion) && nextSamplePos >= 0) {
			// prevent re-initialization
			int index = indexOf(ao);
			if (index >= 0 && index < currElementIndex) {
				currElementIndex--;
			}
			elements.remove(index);
		} else {
			elements.remove(ao);
		}
		addObject(ao);
	}

	/** force the current playback position to be reinitialized */
	private void initRegionPlayback() {
		nextSamplePos = -1;
	}

	/** index in elements of the next object to execute */
	private int currElementIndex = 0;
	/** the currently playing region */
	private AudioRegion currentRegion = null;

	/** buffer to contain the overlayed fade in portion */
	private FloatSampleBuffer fadeInBuffer;
	/** buffer to contain the overlayed fade out portion */
	private FloatSampleBuffer fadeOutBuffer;

	/**
	 * if non-negative, the absolute sample position where the fade in buffer is
	 * applied
	 */
	private long fadeInStartSample = -1;

	/**
	 * if non-negative, the absolute sample position where the fade out buffer
	 * is applied
	 */
	private long fadeOutStartSample = -1;

	/**
	 * return the number of samples that regions are faded out or faded in if
	 * cut in the middle of the underlying audio file
	 */
	private int getFadeSampleCount() {
		return ((int) state.getSampleRate()) / 400;
	}

	/**
	 * Write the audio data for a portion of the current region to the buffer.
	 * 
	 * @param buffer
	 * @param writeOffset
	 * @param count
	 * @param forceFadeOut if true, initialize the fadeOutBuffer to be used
	 */
	private void writeCurrentRegion(FloatSampleBuffer buffer, int writeOffset,
			int count, boolean forceFadeOut) {
		boolean doSilence = (currentRegion == null);
		if (!doSilence) {
			int written = currentRegion.read(buffer, writeOffset, count);
			if (written == 0) {
				doSilence = true;
			} else {
				boolean currentRegionEnd = (currentRegion.isPlaybackEndReached() || forceFadeOut);
				if (currentRegionEnd
						&& currentRegion.needFadeOutAtCurrentPlaybackPosition()) {
					// initialize the fadeout buffer
					if (fadeOutBuffer == null) {
						fadeOutBuffer = new FloatSampleBuffer(
								state.getChannels(), getFadeSampleCount(),
								state.getSampleRate());
					}
					fadeOutStartSample = currentRegion.getStartTimeSamples()
							+ currentRegion.getPlaybackPosition();
					currentRegion.fillFadeOutBuffer(fadeOutBuffer);
				}
				if (currentRegionEnd) {
					currentRegion = null;
				}
			}
		}
		if (doSilence) {
			buffer.makeSilence(writeOffset, count);
		}
	}

	/** initialize fadeInStartSample */
	private void initNewCurrRegion(long currPos) {
		// search next region and initialize pendingSampleToNextRegion
		for (int i = currElementIndex; i < elements.size(); i++) {
			if (elements.get(i) instanceof AudioRegion) {
				AudioRegion fadeInRegion = (AudioRegion) elements.get(i);
				if (fadeInRegion.needFadeInToPreventClick()) {
					if (fadeInBuffer == null) {
						fadeInBuffer = new FloatSampleBuffer(
								state.getChannels(), getFadeSampleCount(),
								state.getSampleRate());
					}
					fadeInStartSample = fadeInRegion.getStartTimeSamples()
							- fadeInBuffer.getSampleCount();
					fadeInRegion.fillFadeInBuffer(fadeInBuffer);
				}
				break;
			}
		}
	}

	/**
	 * Read the next chunk of audio data at the current AudioState position.
	 * 
	 * @see com.mixblendr.audio.AudioInput#read(long,
	 *      org.tritonus.share.sampled.FloatSampleBuffer, int, int)
	 */
	public synchronized boolean read(long samplePos, FloatSampleBuffer buffer,
			int offset, int sampleCount) {
		if (nextSamplePos != samplePos) {
			// go through the list of objects and find the current position
			// also, do chasing for automation objects
			nextSamplePos = samplePos;
			int i;
			currentRegion = null;
			// add concept of initial/default object in AutomationHandler?
			// and use that as initial value.
			for (i = 0; i < elements.size(); i++) {
				AutomationObject ao = elements.get(i);
				long startTime = ao.getStartTimeSamples();
				if (ao instanceof AudioRegion) {
					if (startTime <= samplePos) {
						currentRegion = (AudioRegion) ao;
					}
				}
				if (startTime == samplePos) {
					break;
				} else if (startTime > samplePos) {
					break;
				}
				ao.getHandler().setLastChasingObject(ao);
			}
			currElementIndex = i;
			// set playback position of the region
			if (currentRegion != null) {
				currentRegion.setPlaybackPosition(samplePos
						- currentRegion.getStartTimeSamples());
			}
			initNewCurrRegion(samplePos);
			// now execute all chasing objects
			for (AutomationHandler ah : AutomationManager.types.values()) {
				AutomationObject last = ah.getLastChasingObject();
				if (last != null
				// sanity
						&& last.owner == this) {
					last.execute(owner);
					ah.setLastChasingObject(null);
				}
			}
		}
		nextSamplePos += sampleCount;

		int writtenSamples = 0;
		// read from automation objects
		while (writtenSamples < sampleCount) {
			AutomationObject ao;
			if (currElementIndex >= 0 && currElementIndex < elements.size()) {
				ao = elements.get(currElementIndex);
				if (ao.getStartTimeSamples() >= nextSamplePos) {
					break;
				}
				currElementIndex++;
				ao.execute(owner);
				// magic for regions
				if (ao instanceof AudioRegion) {
					// if there is a region still playing, play it out
					int toWrite = (int) (ao.getStartTimeSamples() - samplePos - writtenSamples);
					writeCurrentRegion(buffer, offset + writtenSamples,
							toWrite, true);
					writtenSamples += toWrite;
					// from now on, play the new region
					currentRegion = (AudioRegion) ao;
					currentRegion.setPlaybackPosition(0);
					initNewCurrRegion(samplePos + writtenSamples);
				} else {
					// during automation recording, and tracking, existing
					// automation objects are removed
					if (owner.isAutomationEnabled()
							&& ao.getHandler().isTracking(owner)) {
						if (DEBUG_PLAYLIST) {
							debug("tracked: removing " + ao);
						}
						elements.remove(ao);
						onRemoval(ao);
						currElementIndex--;
					}
				}
			} else {
				break;
			}
		}
		if (currentRegion != null) {
			// write current portion of this region
			int toWrite = sampleCount - writtenSamples;
			writeCurrentRegion(buffer, offset + writtenSamples, toWrite, false);
			writtenSamples += toWrite;
		}

		boolean ret = (writtenSamples > 0 || fadeOutStartSample > 0 || fadeInStartSample > 0);
		if (ret && writtenSamples < sampleCount) {
			buffer.makeSilence(offset + writtenSamples, sampleCount
					- writtenSamples);
		}

		// apply fade out to avoid clicks
		if (fadeOutStartSample > 0) {
			int length = fadeOutBuffer.getSampleCount();
			if (fadeOutStartSample + length < samplePos) {
				// fade out is over
				fadeOutStartSample = -1;
			} else if (fadeOutStartSample < nextSamplePos) {
				int sourceOffset = 0;
				int thisOffset = (int) (fadeOutStartSample - samplePos);
				if (thisOffset < 0) {
					sourceOffset = (int) (samplePos - fadeOutStartSample);
					thisOffset = 0;
				}
				length -= sourceOffset;
				if (thisOffset + length > sampleCount) {
					length = sampleCount - thisOffset;
				}
				if (length > 0) {
					buffer.mix(fadeOutBuffer, sourceOffset,
							thisOffset + offset, length);
					if (TRACE_FADE) {
						onnl("fo ");
					}
					if (length + sourceOffset == fadeOutBuffer.getSampleCount()) {
						fadeOutStartSample = -1;
					}
				}
			}
		}
		// apply fade in to avoid click
		if (fadeInStartSample >= 0) {
			int length = fadeInBuffer.getSampleCount();
			if (fadeInStartSample + length < samplePos) {
				// fade in is over
				fadeInStartSample = -1;
				// fadeInRegion = null;
			} else if (fadeInStartSample < nextSamplePos) {
				int sourceOffset = (int) (samplePos - fadeInStartSample);
				int thisOffset = 0;
				if (sourceOffset < 0) {
					sourceOffset = 0;
					thisOffset = (int) (fadeInStartSample - samplePos);
				}
				length -= sourceOffset;
				if (thisOffset + length > sampleCount) {
					length = sampleCount - thisOffset;
				}
				if (length > 0) {
					buffer.mix(fadeInBuffer, sourceOffset, thisOffset + offset,
							length);
					if (TRACE_FADE) {
						onnl("fi ");
					}
					if (length + sourceOffset == fadeInBuffer.getSampleCount()) {
						// fade in is just over
						fadeInStartSample = -1;
					}
				}
			}
		}
		return ret;
	}

}
