/*
 * Copyright (C) 2011-2014 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package net.majorkernelpanic.streaming.rtp;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;
import android.text.format.Time;
import android.util.Log;

import net.majorkernelpanic.jni.FFmpegJni;
import net.majorkernelpanic.spydroid.SpydroidApplication;
import net.majorkernelpanic.spydroid.ui.SpydroidActivity;

import static android.R.attr.path;

/**
 * 
 *   RFC 3984.
 *   
 *   H.264 streaming over RTP.
 *   
 *   Must be fed with an InputStream containing H.264 NAL units preceded by their length (4 bytes).
 *   The stream must start with mpeg4 or 3gpp header, it will be skipped.
 *   
 */
public class H264Packetizer extends AbstractPacketizer implements Runnable {

	public final static String TAG = "H264Packetizer";

	private Thread t = null;
	private int naluLength = 0;
	private long delay = 0, oldtime = 0;
	private Statistics stats = new Statistics();
	private byte[] sps = null, pps = null;
	byte[] header = new byte[5];	
	private int count = 0;
	private int streamType = 1;
	private String path;
	private String oldPath;
	long oldtime1 = 0;
	private BufferedOutputStream outputStream;
	private BufferedOutputStream oldOutputStream;
	public static int channel = 0;
	private int videoChannel = 1;
	private static boolean isThreadFfmpegBegin = false;
    private static int timeCount = 0;
	private static String dir = Environment.getExternalStorageDirectory().getAbsolutePath();

	private static int count1 = 0;
	private static int count2 = 0;

	private boolean isFirstCreateFile = false;
	private boolean willCreateNewFile = false;
	private SpydroidApplication mApplication;
	public static final int SIZETYPE_B = 1;//获取文件大小单位为B的double值
	public static final int SIZETYPE_KB = 2;//获取文件大小单位为KB的double值
	public static final int SIZETYPE_MB = 3;//获取文件大小单位为MB的double值
	public static final int SIZETYPE_GB = 4;//获取文件大小单位为GB的double值

	//private ArrayList<Map<String, String>> pathMaps = new ArrayList<Map<String, String>>();
	private static ArrayList<String[]> pathMaps = new ArrayList<String[]>();

	public H264Packetizer() {
		super();
		socket.setClockFrequency(90000);

		mApplication = SpydroidApplication.getInstance();
		File spy_dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/SPY");
		if (spy_dir.exists()) {
			Log.e(TAG, "SPY dir exist");
		} else {
			Log.e(TAG, "SPY dir not exist, will create it");
			spy_dir.mkdir();
		}
	}

	public void start() {
		Log.d(TAG,"start.......");
		//createfile();
		if (t == null) {
			t = new Thread(this);
			t.start();
		}
	}

	public void stop() throws IOException {
		if (!isThreadFfmpegBegin) {
			getMp4FromFfmpeg();
			isThreadFfmpegBegin = true;
		}

		if (outputStream != null) {
			outputStream.flush();
			outputStream.close();
		}

		if (t != null) {
			try {
				is.close();
			} catch (IOException e) {}
			t.interrupt();
			try {
				t.join();
			} catch (InterruptedException e) {}
			t = null;
		}
	}

	public void setStreamParameters(byte[] pps, byte[] sps) {
		this.pps = pps;
		this.sps = sps;
	}	

	public void run() {
		channel++;
		if (1 == channel) {
			videoChannel = 1;
		} else if (2 == channel) {
			videoChannel = 2;
		}
		long duration = 0, delta2 = 0;
		long delta3 = 0;
		Log.d(TAG,"H264 packetizer started !");
		stats.reset();
		count = 0;

		//long oldtime1 = 0;
		if (is instanceof MediaCodecInputStream) {
			streamType = 1;
			socket.setCacheSize(0);
		} else {
			streamType = 0;	
			socket.setCacheSize(400);
		}

		if (2 == videoChannel) {
			AACADTSPacketizer.isChannelSecond = true;
			AACADTSPacketizer.isCreateNewFileSecond = true;
		}
		try {
			while (!Thread.interrupted()) {
				//if (1 == videoChannel) {
					if ((int) oldtime1 == 0) {
						oldtime1 = System.nanoTime();
					}
					if (!willCreateNewFile) {
						if ((System.nanoTime() - oldtime1) / 1000000 > 20 * 60 * 1000) {
							willCreateNewFile = true;
						}
					}
				//}
				oldtime = System.nanoTime();
				// We read a NAL units from the input stream and we send them
				send();
				// We measure how long it took to receive NAL units from the phone
				duration = System.nanoTime() - oldtime;
				
				// Every 3 secondes, we send two packets containing NALU type 7 (sps) and 8 (pps)
				// Those should allow the H264 stream to be decoded even if no SDP was sent to the decoder.				
				delta2 += duration/1000000;
				delta3 += duration/1000000;
				if (delta2>3000) {
					delta2 = 0;
					if (sps != null) {
						buffer = socket.requestBuffer();
						socket.markNextPacket();
						socket.updateTimestamp(ts);
						System.arraycopy(sps, 0, buffer, rtphl, sps.length);
						//Log.e(TAG, "sps = [" + sps + "]");
						super.send(rtphl+sps.length);
					}
					if (pps != null) {
						buffer = socket.requestBuffer();
						socket.updateTimestamp(ts);
						socket.markNextPacket();
						System.arraycopy(pps, 0, buffer, rtphl, pps.length);
						super.send(rtphl+pps.length);
					}
				}

				//Every 1 minutes,we detect the phone's memory,if The remaining is less than 1 GB,
				//we delete the oldest file.
				if (delta3 > 60000) {
					delta3 = 0;
					if (getAvailableSize() < 1) {
						DeleteOldFile();
					}
				}

				stats.push(duration);
				// Computes the average duration of a NAL unit
				delay = stats.average();
				//Log.d(TAG,"duration: "+duration/1000000+" delay: "+delay/1000000);

			}
		} catch (IOException e) {
		} catch (InterruptedException e) {}

		Log.d(TAG,"H264 packetizer stopped !");

	}

	/**
	 * Reads a NAL unit in the FIFO and sends it.
	 * If it is too big, we split it in FU-A units (RFC 3984).
	 */
	@SuppressLint("NewApi")
	private void send() throws IOException, InterruptedException {
		int sum = 1, len = 0, type;

		if (streamType == 0) {
			// NAL units are preceeded by their length, we parse the length
			fill(header,0,5);
			ts += delay;
			naluLength = header[3]&0xFF | (header[2]&0xFF)<<8 | (header[1]&0xFF)<<16 | (header[0]&0xFF)<<24;
			if (naluLength>100000 || naluLength<0) resync();
		} else if (streamType == 1) {
			// NAL units are preceeded with 0x00000001
			fill(header,0,5);
			ts = ((MediaCodecInputStream)is).getLastBufferInfo().presentationTimeUs*1000L;
			//ts += delay;
			naluLength = is.available()+1;
			if (!(header[0]==0 && header[1]==0 && header[2]==0)) {
				// Turns out, the NAL units are not preceeded with 0x00000001
				Log.e(TAG, "NAL units are not preceeded by 0x00000001");
				streamType = 2; 
				return;
			}
		} else {
			// Nothing preceededs the NAL units
			fill(header,0,1);
			header[4] = header[0];
			ts = ((MediaCodecInputStream)is).getLastBufferInfo().presentationTimeUs*1000L;
			//ts += delay;
			naluLength = is.available()+1;
		}

		// Parses the NAL unit type
		type = header[4]&0x1F;

		// The stream already contains NAL unit type 7 or 8, we don't need 
		// to add them to the stream ourselves
		if (type == 7 || type == 8) {
			Log.v(TAG,"SPS or PPS present in the stream.");
			count++;
			if (count>4) {
				sps = null;
				pps = null;
			}
		}

		// Small NAL unit => Single NAL unit
		if (naluLength<=MAXPACKETSIZE-rtphl-2) {
			buffer = socket.requestBuffer();
			buffer[rtphl] = header[4];
			len = fill(buffer, rtphl+1,  naluLength-1);
			socket.updateTimestamp(ts);
			socket.markNextPacket();
			super.send(naluLength+rtphl);
		}
		// Large NAL unit => Split nal unit 
		else {
			// Set FU-A header
			header[1] = (byte) (header[4] & 0x1F);  // FU header type
			header[1] += 0x80; // Start bit
			// Set FU-A indicator
			header[0] = (byte) ((header[4] & 0x60) & 0xFF); // FU indicator NRI
			header[0] += 28;

			while (sum < naluLength) {
				buffer = socket.requestBuffer();
				buffer[rtphl] = header[0];
				buffer[rtphl+1] = header[1];
				socket.updateTimestamp(ts);
				if ((len = fill(buffer, rtphl+2,  naluLength-sum > MAXPACKETSIZE-rtphl-2 ? MAXPACKETSIZE-rtphl-2 : naluLength-sum  ))<0) return; sum += len;
				// Last packet before next NAL
				if (sum >= naluLength) {
					// End bit on
					buffer[rtphl+1] += 0x40;
					socket.markNextPacket();
				}
				super.send(len+rtphl+2);
				// Switch start bit
				header[1] = (byte) (header[1] & 0x7F); 
				//Log.d(TAG,"----- FU-A unit, sum:"+sum);
			}
		}
	}

	public ArrayList<String> traverseFolder(String path) {
		ArrayList<String> fileNames = new ArrayList<String>();
		File file = new File(path);
		if (file.exists()) {
			LinkedList<File> list = new LinkedList<File>();
			File[] files = file.listFiles();
			for (File file2 : files) {
				if (file2.isDirectory()) {
					continue;
				} else {
					if (file2.toString().contains(".h264") || file2.toString().contains(".aac")) {
						fileNames.add(file2.toString());
					}
				}
			}
		}
		return fileNames;
	}

	public static int getNumbers(String content) {
		Pattern pattern = Pattern.compile("\\d+");
		Matcher matcher = pattern.matcher(content);
		while (matcher.find()) {
			return Integer.parseInt(matcher.group(0));
		}
		return -1;
	}

	public static int getNumbers1(String str) {
		char[] chr2 = new char[40];
		str.getChars(20,str.length(),chr2,0);
		String str_result = new String(chr2);
		return getNumbers(str_result);
	}

	public void sort_list(ArrayList<String> list) {
		Collections.sort(list, new Comparator<Object>() {
			@Override
			public int compare(Object o1, Object o2) {
				char[] chr1 = new char[40];
				((String)o1).getChars(20,((String)o1).length(),chr1,0);
				String str1 = new String(chr1);
				//Log.e(TAG, "str1 = [" + str1 + "]");

				char[] chr2 = new char[40];
				((String)o2).getChars(20,((String)o2).length(),chr2,0);
				String str2 = new String(chr2);
				//Log.e(TAG, "str2 = [" + str2 + "]");

				int s1 = getNumbers(str1);
				int s2 = getNumbers(str2);
				if(s1 >= s2) {
					return 1;
				}
				else {
					return -1;
				}
			}
		});
	}

	public void traverseFolder1(String dir, ArrayList<String> h264FileNames, ArrayList<String> aacFileNames, ArrayList<String> h264FileNames_second, ArrayList<String> aacFileNames_second) {
		File file = new File(dir);
		if (file.exists()) {
			LinkedList<File> list = new LinkedList<File>();
			File[] files = file.listFiles();
			for (File file2 : files) {
				if (file2.isDirectory()) {
					continue;
				} else {
					if (file2.toString().contains("second.h264")) {
						//Log.e(TAG, "second.h264 : " + file2.toString());
						h264FileNames_second.add(file2.toString());
					} else if (file2.toString().contains("second.aac")) {
						//Log.e(TAG, "second.aac : " + file2.toString());
						aacFileNames_second.add(file2.toString());
					} else if (file2.toString().contains(".h264")) {
						//Log.e(TAG, ".h264 : " + file2.toString());
						h264FileNames.add(file2.toString());
					} else if (file2.toString().contains(".aac")) {
						//Log.e(TAG, ".aac : " + file2.toString());
						aacFileNames.add(file2.toString());
					}
				}
			}

			sort_list(h264FileNames);
			sort_list(aacFileNames);
			sort_list(h264FileNames_second);
			sort_list(aacFileNames_second);
		}
	}

	public void getMp4FromFfmpeg() {
		new Thread(new Runnable() {
			String aacPath;
			String h264Path;
            ArrayList<String> h264FileNames = new ArrayList<String>();
			ArrayList<String> aacFileNames = new ArrayList<String>();
			ArrayList<String> h264FileNames_second = new ArrayList<String>();
			ArrayList<String> aacFileNames_second = new ArrayList<String>();
			String dir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SPY";
			String[] commands = new String[10];

			@Override
			public void run() {
				ArrayList<String[]> pathCollect = new ArrayList<String[]>();

				while (true) {
					//Log.e(TAG, "david1215 run in while loop");
					if (!pathMaps.isEmpty()) {
						pathCollect = (ArrayList<String[]>) pathMaps.clone();
					}

					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (pathCollect.isEmpty()) {
						//Log.e(TAG, "david1215 timeCount = " + timeCount);
                        if (timeCount > 30) { //25 minutes
                            break;
                        }
                        try {
                            Thread.sleep(50000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        timeCount++;
                        continue;
                    } else {
						//Log.e(TAG, "david1215 timeCount will set to 0");
                        timeCount = 0;
                    }

					String[] paths = pathCollect.get(0);
					aacPath = paths[0];
					h264Path = paths[1];

					commands[0] = "ffmpeg";
					commands[1] = "-i";
					commands[2] = aacPath;
					commands[3] = "-i";
					commands[4] = h264Path;
					commands[5] = "-map";
					commands[6] = "0:0";
					commands[7] = "-map";
					commands[8] = "1:0";
					if (h264Path.contains("_second.h264")) {
						commands[9] = getOutputFileName_second();
					} else {
						commands[9] = getOutputFileName();
					}

					Log.e(TAG, "getMp4FromFfmeg 111 run aacpath = [" + commands[2] + "]");
					Log.e(TAG, "getMp4FromFfmeg 111 run h264Path = [" + commands[4] + "]");
					Log.e(TAG, "will call ffmpeg to merge mp4 begin");
					int result = FFmpegJni.run(commands);
					Log.e(TAG, "will call ffmpeg to merge mp4 end and result = " + result);

					File fileH264 = new File(h264Path);
					if (fileH264.exists()) {
						Log.e(TAG, "delete h264 file: " + h264Path);
						fileH264.delete();
					}

					File fileAac = new File(aacPath);
					if (fileAac.exists()) {
						//Log.e(TAG, "david1218 delete aac file: " + aacPath);
						fileAac.delete();
					}

					pathMaps.remove(0);
					pathCollect.remove(0);
				}

				//Log.e(TAG, "david1215 run outof while loop");

				h264FileNames.clear();
				aacFileNames.clear();
				h264FileNames_second.clear();
				aacFileNames_second.clear();
				traverseFolder1(dir, h264FileNames, aacFileNames, h264FileNames_second, aacFileNames_second);

				 if (!h264FileNames.isEmpty()) {
					 for (int i = 0; i < h264FileNames.size(); i++) {
						 boolean hasMatchAacFile = false;
						 String h264Name = h264FileNames.get(i);
						 if (i >= aacFileNames.size())
						 	break;
						 String aacName = aacFileNames.get(i);

						 if (getNumbers1(h264Name) != getNumbers1(aacName)) {
							 for (int j=0; j<aacFileNames.size(); j++) {
								 aacName = aacFileNames.get(j);
								 if (getNumbers1(h264Name) == getNumbers1(aacName)) {
									 hasMatchAacFile = true;
									 break;
								 }
							 }
						 } else {
							 hasMatchAacFile = true;
						 }

						 if (!hasMatchAacFile)
							 continue;

						 commands[0] = "ffmpeg";
						 commands[1] = "-i";
						 commands[2] = aacName;
						 commands[3] = "-i";
						 commands[4] = h264Name;
						 commands[5] = "-map";
						 commands[6] = "0:0";
						 commands[7] = "-map";
						 commands[8] = "1:0";
						 commands[9] = getOutputFileName();
						 Log.e(TAG, "getMp4FromFfmeg 222 run aacpath = [" + commands[2] + "]");
						 Log.e(TAG, "getMp4FromFfmeg 222 run h264Path = [" + commands[4] + "]");
						 int result = FFmpegJni.run(commands);
						 try {
							 //Log.e(TAG, "will delete the aac file and h264 file");
							 Runtime.getRuntime().exec("rm " + h264Name + " " + aacName);
						 } catch (IOException e) {
							 e.printStackTrace();
						 }
					 }
				 }

				if (!h264FileNames_second.isEmpty()) {
					for (int i = 0; i < h264FileNames_second.size(); i++) {
						boolean hasMatchAacFile_second = false;
						String h264Name_second = h264FileNames_second.get(i);
						if (i >= aacFileNames_second.size())
							break;
						String aacName_second = aacFileNames_second.get(i);

						if (getNumbers1(h264Name_second) != getNumbers1(aacName_second)) {
							for (int j=0; j<aacFileNames.size(); j++) {
								aacName_second = aacFileNames.get(j);
								if (getNumbers1(h264Name_second) == getNumbers1(aacName_second)) {
									hasMatchAacFile_second = true;
									break;
								}
							}
						} else {
							hasMatchAacFile_second = true;
						}

						if (!hasMatchAacFile_second)
							continue;

						Log.e(TAG, "ruirui second h264FileNames[" + i + "]" + "= [" + h264Name_second + "]");
						commands[0] = "ffmpeg";
						commands[1] = "-i";
						commands[2] = aacName_second;
						commands[3] = "-i";
						commands[4] = h264Name_second;
						commands[5] = "-map";
						commands[6] = "0:0";
						commands[7] = "-map";
						commands[8] = "1:0";
						commands[9] = getOutputFileName_second();
						Log.e(TAG, "getMp4FromFfmeg 333 run aacName_second = [" + commands[2] + "]");
						Log.e(TAG, "getMp4FromFfmeg 333 run h264Name_second = [" + commands[4] + "]");
						int result = FFmpegJni.run(commands);
						try {
							//Log.e(TAG, "will delete the aac file and h264 file");
							Runtime.getRuntime().exec("rm " + h264Name_second + " " + aacName_second);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}).start();
	}

	private int fill(byte[] buffer, int offset,int length) throws IOException {
		int sum = 0, len;
		while (sum<length) {
			len = is.read(buffer, offset+sum, length-sum);
			if (len<0) {
				throw new IOException("End of stream");
			} else {
				if(mApplication.mSave){
					if (!isFirstCreateFile) {
						if((int)buffer[4] == 101) {
							createfile();
							if (sps != null) {
								byte[] buf = new byte[sps.length+4];
								buf[0] = 0x00;
								buf[1] = 0x00;
								buf[2] = 0x00;
								buf[3] = 0x01;
								System.arraycopy(sps, 0, buf, 4, sps.length);
								if (outputStream != null)
									outputStream.write(buf, 0, sps.length+4);
							}
							if (pps != null) {
								byte[] buf = new byte[sps.length+4];
								buf[0] = 0x00;
								buf[1] = 0x00;
								buf[2] = 0x00;
								buf[3] = 0x01;
								System.arraycopy(pps, 0, buf, 4, pps.length);
								if (outputStream != null)
									outputStream.write(buf, 0, pps.length+4);
							}
							isFirstCreateFile = true;
							if (1 == videoChannel) {
								//Log.e(TAG, "david0103 AACADTSPacketizer.firstCreateNewFile = true");
								AACADTSPacketizer.firstCreateNewFile = true;
							} else if (2 == videoChannel) {
								//Log.e(TAG, "david0103 AACADTSPacketizer.firstCreateNewFileSecond = true");
								AACADTSPacketizer.firstCreateNewFileSecond = true;
							}
						}
					}
					if (willCreateNewFile) {
						if (5 == length) {
							if((int)buffer[4] == 101) {
								if (1 == videoChannel) {
									AACADTSPacketizer.willCreateNewFile = true;
									oldtime1 = 0;
									oldPath = path;
									oldOutputStream = outputStream;
									new Thread(new Runnable() {
										@Override
										public void run() {
											while (true) {
												if ((AACADTSPacketizer.oldPath != null) && (oldPath != null)) {
													if (oldOutputStream != null) {
														try {
															oldOutputStream.flush();
															oldOutputStream.close();
														} catch (IOException e) {
															e.printStackTrace();
														}
													}

													if (AACADTSPacketizer.oldOutputStream != null) {
														try {
															AACADTSPacketizer.oldOutputStream.flush();
															AACADTSPacketizer.oldOutputStream.close();
														} catch (IOException e) {
															e.printStackTrace();
														}
													}

													String[] name = new String[2];
													name[0] = new String(AACADTSPacketizer.oldPath);
													name[1] = new String(oldPath);
													pathMaps.add(name);

													if (!isThreadFfmpegBegin) {
														getMp4FromFfmpeg();
														isThreadFfmpegBegin = true;
													}


													oldOutputStream = null;
													oldPath = null;
													AACADTSPacketizer.oldOutputStream = null;
													AACADTSPacketizer.oldPath = null;
													break;
												}
											}
										}
									}).start();
								} else if (2 == videoChannel) {
									AACADTSPacketizer.willCreateNewFileSecond = true;
									oldtime1 = 0;
									oldPath = path;
									oldOutputStream = outputStream;
									Log.e(TAG, "oldPath = " + oldPath);
									new Thread(new Runnable() {
										@Override
										public void run() {
											while (true) {
												if ((AACADTSPacketizer.oldPathSecond != null) && (oldPath != null)) {
													if (oldOutputStream != null) {
														try {
															oldOutputStream.flush();
															oldOutputStream.close();
														} catch (IOException e) {
															e.printStackTrace();
														}
													}

													if (AACADTSPacketizer.oldOutputStreamSecond != null) {
														try {
															AACADTSPacketizer.oldOutputStreamSecond.flush();
															AACADTSPacketizer.oldOutputStreamSecond.close();
														} catch (IOException e) {
															e.printStackTrace();
														}
													}

													String[] name = new String[2];
													name[0] = new String(AACADTSPacketizer.oldPathSecond);
													name[1] = new String(oldPath);
													pathMaps.add(name);

													oldOutputStream = null;
													oldPath = null;
													AACADTSPacketizer.oldOutputStreamSecond = null;
													AACADTSPacketizer.oldPathSecond = null;
													break;
												}
											}
										}
									}).start();
								}
								createfile();
								if (sps != null) {
									byte[] buf = new byte[sps.length+4];
									buf[0] = 0x00;
									buf[1] = 0x00;
									buf[2] = 0x00;
									buf[3] = 0x01;
									System.arraycopy(sps, 0, buf, 4, sps.length);
									outputStream.write(buf, 0, sps.length+4);
								}
								if (pps != null) {
									byte[] buf = new byte[sps.length+4];
									buf[0] = 0x00;
									buf[1] = 0x00;
									buf[2] = 0x00;
									buf[3] = 0x01;
									System.arraycopy(pps, 0, buf, 4, pps.length);
									outputStream.write(buf, 0, pps.length+4);
								}
								if (oldOutputStream != null) {
									oldOutputStream.flush();
									oldOutputStream.close();
								}
								willCreateNewFile = false;
							}
						}
					}
					if (outputStream != null) {
						outputStream.write(buffer, offset + sum, length - sum);
					}
				}
				sum+=len;
			}
		}
		return sum;
	}

	private void resync() throws IOException {
		int type;

		Log.e(TAG,"Packetizer out of sync ! Let's try to fix that...(NAL length: "+naluLength+")");

		while (true) {

			header[0] = header[1];
			header[1] = header[2];
			header[2] = header[3];
			header[3] = header[4];
			header[4] = (byte) is.read();

			type = header[4]&0x1F;

			if (type == 5 || type == 1) {
				naluLength = header[3]&0xFF | (header[2]&0xFF)<<8 | (header[1]&0xFF)<<16 | (header[0]&0xFF)<<24;
				if (naluLength>0 && naluLength<100000) {
					oldtime = System.nanoTime();
					Log.e(TAG,"A NAL unit may have been found in the bit stream !");
					break;
				}
				if (naluLength==0) {
					Log.e(TAG,"NAL unit with NULL size found...");
				} else if (header[3]==0xFF && header[2]==0xFF && header[1]==0xFF && header[0]==0xFF) {
					Log.e(TAG,"NAL unit with 0xFFFFFFFF size found...");
				}
			}

		}

	}

	private void createfile(){
		Log.e(TAG, "guoyuefeng0424 create file");
		Time t=new Time();
		t.setToNow();
		int year=t.year;
		int month=t.month +1;
		int day=t.monthDay;
		int hour=t.hour;
		int minute=t.minute;
		int second=t.second;
		Log.i(TAG, ""+year+month+day+hour+minute+second);
		//String filename=""+year+month+day+hour+minute+second;
		String filename;
		if (2 == videoChannel) {
			Log.e(TAG, "guoyuefeng0424 create file 111 second");
			//Log.e(TAG, "david0103 videoChannel == 2");
			filename = "SPY_" + count2;
			path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SPY" + "/" + filename + "_second.h264";
			count2++;
		} else {
			Log.e(TAG, "guoyuefeng0424 create file 111");
			//Log.e(TAG, "david0103 videoChannel == 1");
			filename = "SPY_" + count1;
			path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SPY" + "/" + filename + ".h264";
			count1++;
		}
		File file = new File(path);
		if(file.exists()){
			file.delete();
		}
		try {
			outputStream = new BufferedOutputStream(new FileOutputStream(file));
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	private String getOutputFileName(){
		Time t=new Time();
		t.setToNow();
		int year=t.year;
		int month=t.month +1;
		int day=t.monthDay;
		int hour=t.hour;
		int minute=t.minute;
		int second=t.second;
		//Log.i(TAG, "SPY_"+year+month+day+hour+minute+second);
		String filename="SPY_"+year+month+day+hour+minute+second;
		String outputName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SPY" + "/" + filename + ".mp4";
		return outputName;
	}

	private String getOutputFileName_second(){
		Time t=new Time();
		t.setToNow();
		int year=t.year;
		int month=t.month +1;
		int day=t.monthDay;
		int hour=t.hour;
		int minute=t.minute;
		int second=t.second;
		//Log.i(TAG, ""+year+month+day+hour+minute+second);
		String filename="SPY_"+year+month+day+hour+minute+second;
		String outputName = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SPY" + "/" + filename + "_second.mp4";
		return outputName;
	}

	public static double getFileOrFilesSize(String filePath,int sizeType){
		File file=new File(filePath);
		long blockSize=0;
		try {
			if(file.isDirectory()){
				blockSize = getFileSizes(file);
			}else{
				blockSize = getFileSize(file);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.e("获取文件大小","获取失败!");
		}
		return FormetFileSize(blockSize, sizeType);
	}

	/**
	 * 获取指定文件大小
	 * @param
	 * @return
	 * @throws Exception
	 */
	private static long getFileSize(File file) throws Exception
	{
		long size = 0;
		if (file.exists()){
			FileInputStream fis = null;
			fis = new FileInputStream(file);
			size = fis.available();
			fis.close();
		}
		else{
			file.createNewFile();
			Log.e("获取文件大小","文件不存在!");
		}
		return size;
	}

	/**
	 * 获取指定文件夹
	 * @param f
	 * @return
	 * @throws Exception
	 */
	private static long getFileSizes(File f) throws Exception
	{
		long size = 0;
		File flist[] = f.listFiles();
		for (int i = 0; i < flist.length; i++){
			if (flist[i].isDirectory()){
				size = size + getFileSizes(flist[i]);
			}
			else{
				size =size + getFileSize(flist[i]);
			}
		}
		return size;
	}

	/**
	 * 转换文件大小,指定转换的类型
	 * @param fileS
	 * @param sizeType
	 * @return
	 */
	private static double FormetFileSize(long fileS,int sizeType)
	{
		DecimalFormat df = new DecimalFormat("#.00");
		double fileSizeLong = 0;
		switch (sizeType) {
			case SIZETYPE_B:
				fileSizeLong=Double.valueOf(df.format((double) fileS));
				break;
			case SIZETYPE_KB:
				fileSizeLong=Double.valueOf(df.format((double) fileS / 1024));
				break;
			case SIZETYPE_MB:
				fileSizeLong=Double.valueOf(df.format((double) fileS / 1048576));
				break;
			case SIZETYPE_GB:
				fileSizeLong=Double.valueOf(df.format((double) fileS / 1073741824));
				break;
			default:
				break;
		}
		return fileSizeLong;
	}

	private void DeleteOldFile(){
		String path1 = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SPY";
		File parentFile = new File(path1);
		File fileswp = null;
		File[] files = parentFile.listFiles(fileFilter);//通过fileFileter过滤器来获取parentFile路径下的想要类型的子文件
		for (int n = 0; n < files.length; n++) {
			Log.d("wdf","files....." + files[n]);
		}
		for (int i = files.length - 1; i > 0; i--)
		{
			for (int j = 0; j < i; ++j) {
				if ( files[j+1].lastModified() < files[j].lastModified()){
					fileswp = files[j];
					files[j] = files[j+1];
					files[j+1] = fileswp;
				}
			}
		}
		if (files.length > 0)
			files[0].delete();
	}

	public FileFilter fileFilter = new FileFilter() {
		public boolean accept(File file) {
			String tmp = file.getName().toLowerCase();
			if (tmp.endsWith(".mp4")) {
				return true;
			}
			return false;
		}
	};

	/**
	 * 显示存储的剩余空间
	 */
	public long getAvailableSize(){
		long RomSize =getAvailSpace(Environment.getExternalStorageDirectory().getAbsolutePath());//内部存储大小
		Log.d("wdf","RomSize......" + RomSize / 1073741824);
		//换算成GB
		return RomSize / 1073741824;
	}
	/**
	 * 获取某个目录的可用空间
	 */
	public long getAvailSpace(String path){
		StatFs statfs = new StatFs(path);
		long size = statfs.getBlockSize();//获取分区的大小
		long count = statfs.getAvailableBlocks();//获取可用分区块的个数
		return size*count;
	}

	protected void finalize( )
	{
		Log.e(TAG, "h264 finalize");
	}
}