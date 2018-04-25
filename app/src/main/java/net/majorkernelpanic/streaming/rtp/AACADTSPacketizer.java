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
import java.io.FileOutputStream;
import java.io.IOException;

import net.majorkernelpanic.spydroid.SpydroidApplication;
import net.majorkernelpanic.streaming.audio.AACStream;

import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.text.format.Time;

/**
 *   
 *   RFC 3640.  
 *
 *   This packetizer must be fed with an InputStream containing ADTS AAC. 
 *   AAC will basically be rewrapped in an RTP stream and sent over the network.
 *   This packetizer only implements the aac-hbr mode (High Bit-rate AAC) and
 *   each packet only carry a single and complete AAC access unit.
 * 
 */
public class AACADTSPacketizer extends AbstractPacketizer implements Runnable {

	private final static String TAG = "AACADTSPacketizer";
	private String path;
	private BufferedOutputStream outputStream;

	private Thread t;
	private int samplingRate = 8000;

	public static String oldPath = null;
	public static String oldPathSecond = null;
	public static BufferedOutputStream oldOutputStream = null;
	public static BufferedOutputStream oldOutputStreamSecond = null;
	public static boolean willCreateNewFile = false;
	public static boolean willCreateNewFileSecond = false;

	public static boolean firstCreateNewFile = false;
	public static boolean firstCreateNewFileSecond = false;

	public static boolean isChannelSecond = false;

    public static int count1 = 0;
    public static int count2 = 0;

	private BufferedOutputStream outputStream_second;
	private String path_second;

	public static boolean isCreateNewFileSecond = false;


	public AACADTSPacketizer() {
		super();
		File spy_dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/SPY");
		if (spy_dir.exists()) {
			Log.e(TAG, "SPY dir exist");
		} else {
			Log.e(TAG, "SPY dir not exist, will create it");
			spy_dir.mkdir();
		}
	}

	public void start() {
		//Log.e(TAG, "david0103 aac file start");
		//createfile();
		if (t==null) {
			t = new Thread(this);
			t.start();
		}
	}

	public void stop() throws IOException {
		if (outputStream != null) {
			outputStream.flush();
			outputStream.close();
		}

		if (t != null) {
			try {
				is.close();
			} catch (IOException ignore) {}
			t.interrupt();
			try {
				t.join();
			} catch (InterruptedException e) {}
			t = null;
		}
	}

	public void setSamplingRate(int samplingRate) {
		this.samplingRate = samplingRate;
		socket.setClockFrequency(samplingRate);
	}

	public void run() {

		Log.e(TAG,"AAC ADTS packetizer started !");

		// "A packet SHALL carry either one or more complete Access Units, or a
		// single fragment of an Access Unit.  Fragments of the same Access Unit
		// have the same time stamp but different RTP sequence numbers.  The
		// marker bit in the RTP header is 1 on the last fragment of an Access
		// Unit, and 0 on all other fragments." RFC 3640

		// Adts header fields that we need to parse
		boolean protection;
		int frameLength, sum, length, nbau, nbpk, samplingRateIndex, profile;
		long oldtime = SystemClock.elapsedRealtime(), now = oldtime;
		byte[] header = new byte[8]; 

		long oldtime1 = 0;
		try {
			while (!Thread.interrupted()) {
//				if (isCreateNewFileSecond) {
//					Log.e(TAG, "aac create second channel file");
//					createfile_sencond();
//					isCreateNewFileSecond = false;
//				}

				if (firstCreateNewFile) {
					//Log.e(TAG, "david0103 aac create file");
					createfile();
					firstCreateNewFile = false;
				} else if (firstCreateNewFileSecond) {
					//Log.e(TAG, "david0103 aac create file second");
					createfile_sencond();
					firstCreateNewFileSecond = false;
				}
				if (willCreateNewFile) {
					Log.e(TAG, "aac willCreateNewFile");
					oldPath = path;
					oldOutputStream = outputStream;
					createfile();
					if (oldOutputStream != null) {
						oldOutputStream.flush();
						oldOutputStream.close();
					}
					willCreateNewFile = false;
				} else if (willCreateNewFileSecond) {
					oldPathSecond = path_second;
					oldOutputStreamSecond = outputStream_second;
					createfile_sencond();
					if (oldOutputStreamSecond != null) {
						oldOutputStreamSecond.flush();
						oldOutputStreamSecond.close();
					}
					willCreateNewFileSecond = false;
				}
				// Synchronisation: ADTS packet starts with 12bits set to 1
				header[0] = (byte)0xFF;
				while (true) {
					if ( (is.read()&0xFF) == 0xFF ) {
						header[1] = (byte) is.read();
						if ( (header[1]&0xF0) == 0xF0) {
							if (outputStream != null)
								outputStream.write(header, 0, 2);
							if (isChannelSecond) {
								if (outputStream_second != null)
									outputStream_second.write(header, 0, 2);
							}

							break;
						}
					}
				}

				// Parse adts header (ADTS packets start with a 7 or 9 byte long header)
				fill(header, 2, 5);

				// The protection bit indicates whether or not the header contains the two extra bytes
				protection = (header[1]&0x01)>0 ? true : false;
				frameLength = (header[3]&0x03) << 11 | 
						(header[4]&0xFF) << 3 | 
						(header[5]&0xFF) >> 5 ;
				frameLength -= (protection ? 7 : 9);

				// Number of AAC frames in the ADTS frame
				nbau = (header[6]&0x03) + 1;

				// The number of RTP packets that will be sent for this ADTS frame
				nbpk = frameLength/MAXPACKETSIZE + 1;

				// Read CRS if any
				if (!protection) is.read(header,0,2);

				samplingRate = AACStream.AUDIO_SAMPLING_RATES[(header[2]&0x3C) >> 2];
				profile = ( (header[2]&0xC0) >> 6 ) + 1 ;

				// We update the RTP timestamp
				ts +=  1024L*1000000000L/samplingRate; //stats.average();

				//Log.d(TAG,"frameLength: "+frameLength+" protection: "+protection+" p: "+profile+" sr: "+samplingRate);

				sum = 0;
				while (sum<frameLength) {

					buffer = socket.requestBuffer();
					socket.updateTimestamp(ts);

					// Read frame
					if (frameLength-sum > MAXPACKETSIZE-rtphl-4) {
						length = MAXPACKETSIZE-rtphl-4;
					}
					else {
						length = frameLength-sum;
						socket.markNextPacket();
					}
					sum += length;
					fill(buffer, rtphl+4, length);

					// AU-headers-length field: contains the size in bits of a AU-header
					// 13+3 = 16 bits -> 13bits for AU-size and 3bits for AU-Index / AU-Index-delta 
					// 13 bits will be enough because ADTS uses 13 bits for frame length
					buffer[rtphl] = 0;
					buffer[rtphl+1] = 0x10; 

					// AU-size
					buffer[rtphl+2] = (byte) (frameLength>>5);
					buffer[rtphl+3] = (byte) (frameLength<<3);

					// AU-Index
					buffer[rtphl+3] &= 0xF8;
					buffer[rtphl+3] |= 0x00;

					send(rtphl+4+length);

				}

			}
		} catch (IOException e) {
			// Ignore
		} catch (ArrayIndexOutOfBoundsException e) {
			Log.e(TAG,"ArrayIndexOutOfBoundsException: "+(e.getMessage()!=null?e.getMessage():"unknown error"));
			e.printStackTrace();
		} catch (InterruptedException ignore) {}

		Log.d(TAG,"AAC ADTS packetizer stopped !");

	}

	private int fill(byte[] buffer, int offset,int length) throws IOException {
		int sum = 0, len;
		while (sum<length) {
			len = is.read(buffer, offset+sum, length-sum);
			if (len<0) {
				throw new IOException("End of stream");
			} else {
				if (outputStream != null)
					outputStream.write(buffer, offset+sum, length-sum);
				if (isChannelSecond) {
					if (outputStream_second != null)
						outputStream_second.write(buffer, offset+sum, length-sum);
				}
				sum+=len;
			}
		}
		return sum;
	}

	private void createfile(){
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
        String filename = "SPY_" + count1;
		path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SPY" + "/" + filename + ".aac";
		Log.e(TAG, "aac createfile() path = [" + path + "]");
		File file = new File(path);
		if(file.exists()){
			file.delete();
		}
		try {
			outputStream = new BufferedOutputStream(new FileOutputStream(file));
		} catch (Exception e){
			e.printStackTrace();
		}
		count1++;
	}

	private void createfile_sencond(){
		Time t=new Time();
		t.setToNow();
		int year=t.year;
		int month=t.month +1;
		int day=t.monthDay;
		int hour=t.hour;
		int minute=t.minute;
		int second=t.second;
		Log.i(TAG, ""+year+month+day+hour+minute+second);
		//String filename=""+year+month+day+hour+minute+second+"_second";
        String filename= "SPY_" + count2;;
		path_second = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SPY" + "/" + filename + "_second.aac";
		Log.e(TAG, "aac createfile() path = [" + path + "]");
		File file = new File(path_second);
		if(file.exists()){
			file.delete();
		}
		try {
			outputStream_second = new BufferedOutputStream(new FileOutputStream(file));
		} catch (Exception e){
			e.printStackTrace();
		}
		count2++;
	}
}
