/**
 *
 */
package com.mixblendr.audio;

import java.util.ArrayList;
import java.util.List;

import org.tritonus.share.sampled.FloatSampleBuffer;

import com.mixblendr.util.Debug;
import static com.mixblendr.util.Debug.*;

/**
 * A class managing a multi-channel peak cache (in a float sample buffer) for an
 * entire audio file.
 * 
 * @author Florian Bomers
 */
public class AudioPeakCache {

	private static final boolean DEBUG_PEAK_CACHE = false;

	/**
	 * if that flag is set, the peack cache only calculates the cache for the
	 * first channel
	 */
	private static boolean ALWAYS_USE_ONE_CHANNEL = true;

	/** the min cache: for every sample block, it holds the min value */
	private FloatSampleBuffer minCache;

	/** the max cache: for every sample block, it holds the max value */
	private FloatSampleBuffer maxCache;

	/** number of samples added to the cache, to account for boundaries */
	private long handledSampleCount = 0;

	/**
	 * This many samples are summarized in one cache element
	 * (2^SCALE_SHIFT=SCALE_FACTOR)
	 */
	public static final int SCALE_FACTOR = 128;

	/**
	 * to this power of 2 the cache will contain max and min values
	 * (2^SCALE_SHIFT=SCALE_FACTOR).
	 */
	public static final int SCALE_SHIFT = 7;

	/** the scale factor remainder portion */
	public static final int SCALE_MASK = 0x7F;

	private static final int CACHE_INCREASE_ELEMENTS = 1024 * 20;

	/**
	 * now that's a cache of the cache :) this list stores cache arrays to be
	 * recycled
	 */
	private static List<float[]> cachePool = new ArrayList<float[]>(10);
	private static final boolean USE_POOL = true;

	static {
		if (USE_POOL) {
			// initialize the pool
			int inc = CACHE_INCREASE_ELEMENTS;
			synchronized (cachePool) {
				if (cachePool.size() == 0) {
					// pre-fill
					for (int size = inc; size < inc * 6; size += inc) {
						for (int i = 0; i < 4; i++) {
							cachePool.add(new float[size]);
						}
					}
				}
			}
			if (DEBUG_PEAK_CACHE) {
				debug("PeakCache: initialized the cache pool with "
						+ cachePool.size() + " arrays.");
			}
		}
	}

	/** create an empty audio cache */
	public AudioPeakCache() {
	}

	/** create an empty audio cache with the given number of samples and channels */
	public AudioPeakCache(int channelCount, long sampleCount) {
		this();
		init(channelCount, sampleCount);
	}

	private static float[] newArray(int size) {
		// first look into cache pool
		if (USE_POOL) {
			synchronized (cachePool) {
				for (int i = 0; i < cachePool.size(); i++) {
					float[] d = cachePool.get(i);
					if (d != null && d.length == size) {
						if (DEBUG_PEAK_CACHE) {
							debug("    recycling a " + size
									+ "-sample array from pool.");
						}
						cachePool.remove(i);
						return d;
					}
				}
			}
		}
		if (DEBUG_PEAK_CACHE) {
			debug("    newly allocating a " + size + "-sample array.");
		}
		return new float[size];
	}

	private static int getCacheSizeFromSampleCount(long sampleCount) {
		int cacheCount = (int) ((sampleCount >> SCALE_SHIFT) + 1);
		// align to full CACHE_INCREASE_ELEMENTS boundaries
		int remainder = cacheCount % CACHE_INCREASE_ELEMENTS;
		if (remainder > 0) {
			cacheCount += CACHE_INCREASE_ELEMENTS - remainder;
		}
		return cacheCount;
	}

	private void enlargeBuffer(int cacheCount, FloatSampleBuffer fsb) {
		int oldCount = fsb.getSampleCount();
		for (int c = 0; c < fsb.getChannelCount(); c++) {
			if (DEBUG_PEAK_CACHE) {
				if (fsb == minCache) {
					debug("PeakCache min channel " + (c + 1) + ": ");
				} else {
					debug("PeakCache max channel " + (c + 1) + ": ");
				}
			}
			float[] newData = newArray(cacheCount);
			float[] oldData = fsb.setRawChannel(c, newData);
			if (oldCount > 0) {
				System.arraycopy(oldData, 0, newData, 0, oldCount);
				if (oldData.length % CACHE_INCREASE_ELEMENTS == 0) {
					// put the old array back in the pool
					synchronized (cachePool) {
						cachePool.add(oldData);
					}
					if (DEBUG_PEAK_CACHE) {
						debug("    put back an array of " + oldData.length
								+ " samples to pool.");
					}
				}
			}
		}
		// we can safely set to the new size -- even with setting
		// keepSamples to false: the existing array is large enough
		// anyway.
		fsb.setSampleCount(cacheCount, false);
	}

	private void enlarge(int cacheCount) {
		int oldCount = minCache.getSampleCount();
		if (cacheCount > oldCount) {
			// sample rate is arbitrary
			if (!USE_POOL) {
				minCache.changeSampleCount(cacheCount, true);
				maxCache.changeSampleCount(cacheCount, true);
			} else {
				enlargeBuffer(cacheCount, minCache);
				enlargeBuffer(cacheCount, maxCache);
			}
			if (DEBUG_PEAK_CACHE) {
				debug("PeakCache: enlarged from " + oldCount
						+ " cache elements to to " + cacheCount + " (=2 * "
						+ (minCache.getChannelCount() * cacheCount * 4)
						+ " bytes)");
			}
		}
	}

	/**
	 * Initialize this peak cache with empty cache arrays.
	 * 
	 * @param channelCount number of channels in audio data
	 * @param sampleCount number of samples in audio data
	 */
	private void init(int channelCount, long sampleCount) {
		// scale sample count
		int cacheCount = getCacheSizeFromSampleCount(sampleCount);
		if (minCache == null) {
			if (ALWAYS_USE_ONE_CHANNEL) {
				channelCount = 1;
			}
			// sample rate is arbitrary
			minCache = new FloatSampleBuffer(channelCount, 0, 44100);
			maxCache = new FloatSampleBuffer(channelCount, 0, 44100);
			if (DEBUG_PEAK_CACHE) {
				debug("PeakCache: created cache.");
			}
		}
		enlarge(cacheCount);
	}

	/**
	 * The number of samples currently represented by the cache data.
	 * @return the handledSampleCount
	 */
	public long getHandledSampleCount() {
		return handledSampleCount;
	}

	/**
	 * The number of usable elements in the cache data float arrays.
	 * @return the handledCacheElementCount
	 */
	public int getHandledCacheElementCount() {
		return (int) (handledSampleCount >> SCALE_SHIFT);
	}

	/**
	 * @return the maxCache
	 */
	public FloatSampleBuffer getMaxCache() {
		return maxCache;
	}

	/**
	 * @return the minCache
	 */
	public FloatSampleBuffer getMinCache() {
		return minCache;
	}

	/**
	 * calculate min and max (using the predefined values for min and max) and
	 * write it to minCache and maxCache, analyzing the samples in audio from
	 * the given start point for count samples.
	 */
	private final static void calcMinMax(float min, float max,
			float[] aMinCache, float[] aMaxCache, int cacheIndex,
			float[] audio, int start, int count) {
		for (int i = 0; i < count; i++) {
			float sample = audio[start + i];
			if (sample > max) {
				max = sample;
			}
			if (sample < min) {
				min = sample;
			}
		}
		aMinCache[cacheIndex] = min;
		aMaxCache[cacheIndex] = max;
	}

	/**
	 * calculate min and max and write it to minCache and maxCache, analyzing
	 * the samples in audio for a full cache block.
	 */
	private final static void calcMinMax(float[] aMinCache, float[] aMaxCache,
			int cacheIndex, float[] audio, int start) {
		float min = 0f, max = 0f;
		for (int i = 0; i < SCALE_FACTOR; i++) {
			float sample = audio[start + i];
			if (sample > max) {
				max = sample;
			}
			if (sample < min) {
				min = sample;
			}
		}
		aMinCache[cacheIndex] = min;
		aMaxCache[cacheIndex] = max;
	}

	/**
	 * update the cache for this region. If the cache buffer is too small,
	 * enlarge it. If the channel count smaller than in a previous call to
	 * update(), an IllegalArgumentException is thrown. For overlapping regions,
	 * convention is to use the value already in the cache for the beginning
	 * segment, and to overwrite the last element.
	 * 
	 * @throws IllegalArgumentException if the number of channels in buffer is
	 *             smaller than number of channels in cache (from
	 *             initialization)
	 */
	public void update(long startSample, FloatSampleBuffer buffer) {
		int sampleCount = buffer.getSampleCount();
		init(buffer.getChannelCount(), startSample + sampleCount);
		if (buffer.getChannelCount() < minCache.getChannelCount()) {
			throw new IllegalArgumentException(
					"Cannot calc peak for different channels");
		}
		for (int c = 0; c < minCache.getChannelCount(); c++) {
			int thisCount = sampleCount;
			if (Debug.DEBUG && false) {
				Debug.debug("Calc peak cache for " + thisCount
						+ " samples on channel " + c + " from sample "
						+ startSample);
			}
			float[] min = minCache.getChannel(c);
			float[] max = maxCache.getChannel(c);
			float[] audio = buffer.getChannel(c);
			int cacheIndex = (int) (startSample >> SCALE_SHIFT);
			int remainder = (int) (startSample & SCALE_MASK);
			// index in audio
			int sampleIndex = 0;
			if (remainder > 0) {
				// if the remainder is non-zero, we start in the middle of a
				// cache block and should take the existing cache value into
				// consideration
				int count = SCALE_FACTOR - remainder;
				if (count > thisCount) {
					count = thisCount;
				}
				calcMinMax(min[cacheIndex], max[cacheIndex], min, max,
						cacheIndex, audio, sampleIndex, count);
				cacheIndex++;
				sampleIndex += count;
				thisCount -= count;
			}
			// now sampleIndex points to the beginning of a cache block
			int fullCount = thisCount >> SCALE_SHIFT;
			for (int i = 0; i < fullCount; i++) {
				calcMinMax(min, max, cacheIndex, audio, sampleIndex);
				cacheIndex++;
				sampleIndex += SCALE_FACTOR;
				thisCount -= SCALE_FACTOR;
			}
			// if there are samples remaining, calc the remainder
			if (thisCount > 0) {
				calcMinMax(0f, 0f, min, max, cacheIndex, audio, sampleIndex,
						thisCount);
			}
		}
		if (startSample + buffer.getSampleCount() > handledSampleCount) {
			handledSampleCount = startSample + buffer.getSampleCount();
		}
	}

}
