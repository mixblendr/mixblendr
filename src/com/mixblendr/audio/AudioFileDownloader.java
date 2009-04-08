/**
 *
 */
package com.mixblendr.audio;

import java.util.*;
import java.io.*;
import java.net.*;
import javax.sound.sampled.*;
import javax.sound.sampled.spi.FormatConversionProvider;
import javax.sound.sampled.spi.AudioFileReader;

import org.tritonus.share.sampled.*;
import static com.mixblendr.util.Debug.*;

/**
 * Class to asynchronously download URLs. Use a queue of URL's and a number of
 * threads for simultaneous downloading.
 * <p>
 * This class cannot be instanciated, use getInstance() to get the singleton
 * instance.
 * 
 * @author Florian Bomers
 */
class AudioFileDownloader {

	private final static boolean DEBUG = false;

	public static final int MAX_CONCURRENT_DOWNLOADS = 3;

	// read from network this size.
	private static int TEMP_BUFFER_SIZE_SLICE = 1024 * 20;
	// write to file this size. Must be a multiple of TEMP_BUFFER_SIZE_SLICE
	private static int TEMP_BUFFER_SIZE_FILE = TEMP_BUFFER_SIZE_SLICE * 6;
	// number of bytes to read at a time from the net if using memory. Must be a
	// multiple of TEMP_BUFFER_SIZE_SLICE
	private static int TEMP_BUFFER_SIZE_MEM = TEMP_BUFFER_SIZE_SLICE * 2;
	
	// the number of milliseconds to wait for each downloaded slice
	private static int WAIT_TIME_SLICE_MILLIS = 0;

	/** threads will terminate themselves after this timeout in milliseconds */
	public static final int TIMEOUT = 10000;

	/** the singleton instance of this class */
	private static AudioFileDownloader instance = new AudioFileDownloader();

	protected List<DownloadJob> jobs;

	protected List<DownloadThread> threads;

	protected AudioFileDownloadListener listener;

	/** private constructor, only one static instance exists */
	private AudioFileDownloader() {
		super();
		jobs = new ArrayList<DownloadJob>();
		threads = new ArrayList<DownloadThread>(MAX_CONCURRENT_DOWNLOADS);
	}

	/** retrieve the only instance of the AudioFileDownloader */
	public static AudioFileDownloader getInstance() {
		return instance;
	}

	/**
	 * Start downloading this AudioFileURL instance asynchronously. The audio
	 * file instance is notified when data is read, and when downloading is
	 * finished, or when an error occurs.
	 */
	void addJob(AudioFileURL af) {
		int jobsSize;
		synchronized (jobs) {
			jobs.add(new DownloadJob(af));
			jobsSize = jobs.size();
		}
		if (DEBUG) {
			debug("AudioFileDownloader: added job to queue, now " + jobsSize
					+ " pending jobs");
		}
		// create threads, if necessary
		synchronized (threads) {
			if (jobsSize > threads.size()
					&& threads.size() < MAX_CONCURRENT_DOWNLOADS) {
				threads.add(new DownloadThread());
			}
			// wake up waiting threads
			threads.notifyAll();
		}
	}

	/**
	 * if this file is scheduled for downloading, or currently downloaded, the
	 * download will be interrupted
	 */
	void killJob(AudioFileURL af) {
		synchronized (jobs) {
			for (DownloadJob job : jobs) {
				if (job.af == af) {
					jobs.remove(job);
					break;
				}
			}
		}
		synchronized (threads) {
			for (DownloadThread thread : threads) {
				thread.killIfProcessing(af);
			}
		}
	}

	/** kill all threads */
	void killAll() {
		synchronized (jobs) {
			jobs.clear();
		}
		synchronized (threads) {
			for (DownloadThread thread : threads) {
				thread.kill();
			}
		}
	}

	/** returns true if there are any files currently being downloaded */
	public boolean isDownloading() {
		synchronized (jobs) {
			if (jobs.size() > 0) {
				return true;
			}
		}
		synchronized (threads) {
			for (DownloadThread dt : threads) {
				if (dt.active) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * @return the listener
	 */
	public AudioFileDownloadListener getListener() {
		return listener;
	}

	/**
	 * @param listener the listener to set
	 */
	public void setListener(AudioFileDownloadListener listener) {
		this.listener = listener;
	}

	protected static int IDCounter = 0;

	/** if tritonus mp3 decoder is available */
	protected static boolean hasMP3converter = true;
	/** the tritonus mp3 converter provider, if available */
	protected static org.tritonus.sampled.convert.javalayer.MpegFormatConversionProvider mp3converter = null;

	/** if tritonus ogg/vorbis decoder is available */
	protected static boolean hasVorbisConverter = true;
	/** the tritonus ogg/vorbis converter provider, if available */
	protected static org.tritonus.sampled.convert.jorbis.JorbisFormatConversionProvider vorbisConverter = null;

	/**
	 * Shadow for AudioSystem.getAudioInputStream(AudioFormat,
	 * AudioInputStream): Convert ais to a stream with PCM encoding. Because
	 * applets do not support plugin installation from the web, directly call
	 * the known conversion providers.
	 * <p>
	 * Note: this method should only be used for conversion from MP3 or VORBIS
	 * to PCM. For any other conversion, use the conventional AudioSystem
	 * equivalent.
	 * 
	 * @param ais
	 * @param newFormat the converted format
	 * @return the converted stream
	 * @throws Exception on error
	 */
	public static AudioInputStream getAudioInputStream(AudioFormat newFormat,
			AudioInputStream ais) throws Exception {
		AudioFormat format = ais.getFormat();
		Exception firstException = null;
		FormatConversionProvider prov = null;
		// MPEG stream?
		if (format.getEncoding().toString().startsWith("MPEG")) {
			// try Tritonus converter
			if (hasMP3converter) {
				try {
					if (mp3converter == null) {
						mp3converter = new org.tritonus.sampled.convert.javalayer.MpegFormatConversionProvider();
					}
					prov = mp3converter;
				} catch (Exception e) {
					hasMP3converter = false;
					debug(e);
					firstException = new Exception(
							"Bad installation: Tritonus mp3 decoder (and/or javalayer lib) not available");
				}
			}
		} else if (format.getEncoding().toString().startsWith("VORBIS")) {
			// try Tritonus jorbis converter
			if (hasVorbisConverter) {
				try {
					if (vorbisConverter == null) {
						vorbisConverter = new org.tritonus.sampled.convert.jorbis.JorbisFormatConversionProvider();
					}
					prov = vorbisConverter;
				} catch (Exception e) {
					hasVorbisConverter = false;
					debug(e);
					firstException = new Exception(
							"Bad installation: Tritonus p-vorbis decoder not available");
				}
			}
		}
		if (prov != null) {
			ais = prov.getAudioInputStream(newFormat, ais);
		} else {
			try {
				// try AudioSystem...
				ais = AudioSystem.getAudioInputStream(newFormat, ais);
			} catch (Exception e) {
				if (firstException != null) {
					throw firstException;
				}
				throw e;
			}
		}
		return ais;
	}

	/** if tritonus mp3 reader is available */
	protected static boolean hasMP3reader = true;
	/** the tritonus mp3 reader provider, if available */
	protected static org.tritonus.sampled.file.mpeg.MpegAudioFileReader mp3reader = null;

	/** if tritonus ogg/vorbis reader is available */
	protected static boolean hasVorbisReader = true;
	/** the tritonus ogg/vorbis reader provider, if available */
	protected static org.tritonus.sampled.file.jorbis.JorbisAudioFileReader vorbisReader = null;

	/**
	 * Shadow for AudioSystem.getAudioInputStream(URL url): Because applets do
	 * not support plugin installation from the web, directly call the known
	 * file reader providers.
	 * <p>
	 * Note: for optimization, this method will check the URL for occurence of
	 * &quot;.mp3 and &quot;.ogg&quot; and only then try to instanciate the
	 * corresponding provider.
	 * 
	 * @param url the URL to load the file from
	 * @return the file stream
	 * @throws Exception on error
	 */
	public static AudioInputStream getAudioInputStream(URL url)
			throws Exception {
		Exception firstException = null;
		AudioFileReader prov = null;

		if (url.getPath().indexOf(".mp3") >= 0) {
			// try Tritonus MP3 reader
			if (hasMP3reader) {
				try {
					if (mp3reader == null) {
						mp3reader = new org.tritonus.sampled.file.mpeg.MpegAudioFileReader();
					}
					prov = mp3reader;
				} catch (Exception e) {
					hasMP3reader = false;
					debug(e);
				}
			}
			if (prov == null) {
				firstException = new Exception("MP3 reader not available "
						+ "[tritonus-mp3.jar and javalayer.jar]");
			}
		} else if (url.getPath().indexOf(".ogg") >= 0) {
			// try Tritonus jorbis reader
			if (hasVorbisReader) {
				try {
					if (vorbisReader == null) {
						vorbisReader = new org.tritonus.sampled.file.jorbis.JorbisAudioFileReader();
					}
					prov = vorbisReader;
				} catch (Exception e) {
					hasVorbisReader = false;
					debug(e);
				}
			}
			if (prov == null) {
				firstException = new Exception(
						"Vorbis reader not available [tritonus-jorbis.jar, jorbis.jar and jogg.jar]");
			}
		}
		// TODO: use Tritonus' Wave reader
		// SunBug: Sun's wave reader is somehow broken if fmt cunk's length is
		// 0x12 instead of 0x10.
		if (prov != null) {
			return prov.getAudioInputStream(url);
		}
		try {
			// try AudioSystem...
			return AudioSystem.getAudioInputStream(url);
		} catch (Exception e) {
			if (firstException != null) {
				throw firstException;
			}
			throw e;
		}
	}

	protected static boolean hasTritonusSRC = true;
	protected static org.tritonus.sampled.convert.SampleRateConversionProvider tritonusSRC = null;

	/** a thread for downloading a URL */
	private class DownloadThread extends Thread {

		private byte[] tempBuffer;

		private boolean killed = false;

		public DownloadThread() {
			super("AudioDownloadThread " + IDCounter);
			IDCounter++;
			setDaemon(true);
			setPriority(Thread.MIN_PRIORITY);
			start();
		}

		private DownloadJob currentJob = null;
		private AudioInputStream currentAIS = null;

		public synchronized void kill() {
			if (DEBUG) {
				debug(getName() + ": getting killed");
			}
			killed = true;
			if (currentAIS != null) {
				try {
					currentAIS.close();
				} catch (IOException ioe) {
					// nothing
				}
				currentAIS = null;
			}
			if (currentJob != null) {
				currentJob.af.downloadEnd();
				if (listener != null) {
					listener.downloadEnded(currentJob.af);
				}
				currentJob = null;
			}
			synchronized (threads) {
				threads.notifyAll();
			}
		}

		/**
		 * kill this thread if it's currently downloading the specified audio
		 * file
		 */
		public void killIfProcessing(AudioFile af) {
			if (currentJob != null && currentJob.af == af) {
				kill();
			}
		}

		/**
		 * do the actual download - convert the file to the state's sample rate
		 * while writing
		 */
		private void download(DownloadJob job) {
			if (DEBUG) {
				debug(getName() + ": Starting download " + job.af.getName());
			}
			if (AudioPlayer.INHIBIT_PLAYBACK_DURING_DOWNLOAD) {
				AudioPlayer.stopAllPlayers();
			}
			if (listener != null) {
				listener.downloadStarted(job.af);
			}
			currentJob = job;
			AudioInputStream ais = null;
			if (tempBuffer == null) {
				if (job.af instanceof AudioFileURLMem) {
					tempBuffer = new byte[TEMP_BUFFER_SIZE_MEM];
				} else {
					tempBuffer = new byte[TEMP_BUFFER_SIZE_FILE];
				}
			}
			// first, try to see if that audio file is supported at all
			try {
				if (killed) return;
				ais = getAudioInputStream(job.af.getURL());
				if (killed) return;
				if (!AudioUtils.isPCM(ais.getFormat())) {
					// first need to convert to PCM
					AudioFormat newFormat = new AudioFormat(
							ais.getFormat().getSampleRate(), 16,
							ais.getFormat().getChannels(), true, false);
					ais = getAudioInputStream(newFormat, ais);
					if (killed) return;
				}
				// convert to the state's sample rate, if necessary
				if (Math.abs(ais.getFormat().getSampleRate()
						- job.af.getState().getSampleRate()) > 0.0001) {
					// need to convert sample rate
					AudioFormat newFormat = new AudioFormat(
							job.af.getState().getSampleRate(), 16,
							ais.getFormat().getChannels(), true, false);
					FormatConversionProvider prov = null;
					if (hasTritonusSRC) {
						try {
							if (tritonusSRC == null) {
								tritonusSRC = new org.tritonus.sampled.convert.SampleRateConversionProvider();
							}
							prov = tritonusSRC;
						} catch (Exception e) {
							hasTritonusSRC = false;
							debug(e);
						}
					}
					if (prov == null) {
						throw new Exception(
								"Tritonus sample rate converter not available [tritonus-src.jar]");
					}
					ais = prov.getAudioInputStream(newFormat, ais);
					if (killed) return;
				}
				if (killed) return;
				job.af.init(ais.getFormat(), ais.getFrameLength()
						* ais.getFormat().getFrameSize());
				currentAIS = ais;
				// FINALLY read from the (converted) stream and pass on the data
				// to the AudioFile
				int pos = 0;
				while (!killed) {
					int read = ais.read(tempBuffer, pos, TEMP_BUFFER_SIZE_SLICE);
					if (killed) break;
					if (read < 0) {
						// send out remaining data
						if (pos > 0) {
							job.af.downloadData(tempBuffer, 0, pos);
						}
						break;
					} else if (read == 0) {
						Thread.yield();
					} else {
						pos += read;
						if (pos + TEMP_BUFFER_SIZE_SLICE > tempBuffer.length) {
							if (!job.af.downloadData(tempBuffer, 0, pos)) {
								// AudioFile requests end of stream
								break;
							}
							pos = 0;
						}
						if (WAIT_TIME_SLICE_MILLIS > 0) {
							Thread.sleep(WAIT_TIME_SLICE_MILLIS);
						} else {
							Thread.yield();
						}
					}
				}
			} catch (Throwable t) {
				if (!killed) {
					error(t);
					job.af.downloadError(t);
				}
			} finally {
				job.af.downloadEnd();
				// clean up
				if (ais != null) {
					try {
						ais.close();
					} catch (IOException ioe) {
						// nothing
					}
				}
				synchronized (this) {
					currentAIS = null;
					currentJob = null;
				}
				if (listener != null) {
					listener.downloadEnded(job.af);
				}
			}
		}

		volatile boolean active = false;

		/**
		 * main thread method: get the first element in the jobs queue and
		 * download it
		 */
		@Override
		public void run() {
			if (DEBUG) {
				debug(getName() + ": start");
			}
			boolean hasWaited = false;
			while (true) {
				DownloadJob job = null;
				if (!killed) {
					synchronized (jobs) {
						if (!jobs.isEmpty()) {
							active = true;
							job = jobs.remove(0);
							if (DEBUG) {
								debug(getName()
										+ ": retrieved job from queue, now "
										+ jobs.size() + " jobs left");
							}
						}
					}
				}
				if (job == null || killed) {
					active = false;
					synchronized (threads) {
						if (hasWaited || killed) {
							threads.remove(this);
							break;
						}
						try {
							threads.wait(TIMEOUT);
						} catch (InterruptedException ie) {
							// nothing
						}
						hasWaited = true;
					}
				} else {
					hasWaited = false;
					download(job);
					active = false;
				}
			}
			if (DEBUG) {
				debug(getName() + ": end");
			}
		}
	}

	/** a job that is queued for processing by the download thread */
	private static class DownloadJob {
		public AudioFileURL af;

		/**
		 * @param af
		 */
		public DownloadJob(AudioFileURL af) {
			super();
			this.af = af;
		}

	}

}
