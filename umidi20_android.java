/*-
 * Copyright (c) 2017 Hans Petter Selasky <hselasky@FreeBSD.org>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package org.selasky.umidi20;

import android.media.midi.*;
import android.content.*;
import android.app.*;
import android.os.*;
import java.lang.*;

class UMidi20Recv extends MidiReceiver {
	int devindex = 0;
	public native void onSendNative(byte[] msg, int off, int count, int devindex);

	@Override
	public void onSend(byte[] msg, int off, int count, long ts) {
		onSendNative(msg, off, count, devindex);
	}
};

class UMidi20RxDev implements MidiManager.OnDeviceOpenedListener {
	MidiDevice dev = null;
	UMidi20Recv recv;
	MidiOutputPort outp = null;
	int opened = 0;

	public void onDeviceOpened(MidiDevice _dev) {
		dev = _dev;
	}

	public void closeDevice() {
		if (opened != 0) {
			try {
				outp.close();
				dev.close();
			}
			catch (Exception e) {
			}
			opened = 0;
		}
	}

	public void openDevice(MidiManager m, MidiDeviceInfo info, int portindex) {
		if (opened == 0) {
		    m.openDevice(info, this,
		        new Handler(Looper.getMainLooper()));
		    if (dev == null)
			  return;
		    outp = dev.openOutputPort(portindex);
		    if (outp == null) {
			try {
				dev.close();
			}
			catch (Exception e) {
			}
			return;
		    }
		    outp.connect(recv);
		    opened = 1;
		}
	}
};

class UMidi20TxDev implements MidiManager.OnDeviceOpenedListener {
	MidiDevice dev = null;
	MidiInputPort inp = null;
	int opened = 0;

	public void onDeviceOpened(MidiDevice _dev) {
		dev = _dev;
	}

	public void closeDevice() {
		if (opened != 0) {
		    try {
			inp.close();
			dev.close();
		    }
		    catch (Exception e) {
		    }
		    opened = 0;
		}
	}

	public void send(int midi, int len) {
		if (opened != 0) {
			byte[] buffer = new byte[4];
			buffer[0] = (byte)midi;
			buffer[1] = (byte)(midi >> 8);
			buffer[2] = (byte)(midi >> 16);
			buffer[3] = (byte)(midi >> 24);
			try {
			    inp.send(buffer, 0, len);
			}
			catch (Exception e) {
			}
		}
	}

	public void openDevice(MidiManager m, MidiDeviceInfo info, int portindex) {
		if (opened == 0) {
		    m.openDevice(info, this,
		        new Handler(Looper.getMainLooper()));
		    if (dev == null)
			  return;
		    inp = dev.openInputPort(portindex);
		    if (inp == null) {
			try {
			    dev.close();
			}
			catch (Exception e) {
			}
			return;
		    }
		    opened = 1;
		}
	}
};

class UMidi20Context extends Application {
	private Context context;

	@Override
	public onCreate() {
	    super.onCreate();
	    this.context = getApplicationContext();
	}

	public static Context getApplicationContext() {
	    return this.context;
	}
};

class UMidi20Main extends Thread {
	public UMidi20RxDev[] rx_dev;
	public UMidi20TxDev[] tx_dev;
	public MidiDeviceInfo[] rx_info;
	public MidiDeviceInfo[] tx_info;
	public Context context;
	public MidiManager m;
	public native int getAction();
	public native void setRxDevices(int num);
	public native void setTxDevices(int num);
	public native void storeRxDevice(int num, String desc);
	public native void storeTxDevice(int num, String desc);
	public UMidi20Main() {
		start();
	}
    	public void run() {
	    context = UMidi20Context.getApplicationContext();
	    m = (MidiManager)context.getSystemService(Context.MIDI_SERVICE);
	    rx_dev = new UMidi20RxDev [16];
	    tx_dev = new UMidi20TxDev [16];
	    for (int i = 0; i != 16; i++) {
			rx_dev[i].recv.devindex = i;
	    }
	    while (true) {
		int action = getAction();
		int n;

		switch (action & 0xFF) {
		case 0:
			rx_info = m.getDevices();
			n = 0;
			for (int i = 0; i != rx_info.length; i++) {
			    for (int j = 0; j != rx_info[i].getOutputPortCount(); j++) {
				n++;
			    }
			}
			setRxDevices(n);
			n = 0;
			for (int i = 0; i != rx_info.length; i++) {
			    for (int j = 0; j != rx_info[i].getOutputPortCount(); j++) {
				storeRxDevice(n++, rx_info[i].toString());
			    }
			}
			break;
		case 1:
			tx_info = m.getDevices();
			n = 0;
			for (int i = 0; i != tx_info.length; i++) {
			    for (int j = 0; j != tx_info[i].getOutputPortCount(); j++) {
				n++;
			    }
			}
			setTxDevices(n);
			n = 0;
			for (int i = 0; i != tx_info.length; i++) {
			    for (int j = 0; j != tx_info[i].getOutputPortCount(); j++) {
				storeTxDevice(n++, tx_info[i].toString());
			    }
			}
			break;
		case 2:
			tx_dev[(action >> 8) & 0xF].send(getAction(), (action >> 12) & 0xF);
			break;
		case 3:
			n = (action >> 12);
			for (int i = 0; i != tx_info.length; i++) {
			    for (int j = 0; j != tx_info[i].getOutputPortCount(); j++) {
				if (n == 0)
				    tx_dev[(action >> 8) & 0xF].openDevice(m, tx_info[i], j);
				n--;
			    }
			}
			break;
		case 4:
			n = (action >> 12);
			for (int i = 0; i != rx_info.length; i++) {
			    for (int j = 0; j != rx_info[i].getOutputPortCount(); j++) {
				if (n == 0)
				    rx_dev[(action >> 8) & 0xF].openDevice(m, rx_info[i], j);
				n--;
			    }
			}
			break;
		case 5:
			tx_dev[(action >> 8) & 0xF].closeDevice();
			break;
		case 6:
			rx_dev[(action >> 8) & 0xF].closeDevice();
			break;
		default:
			break;
		}
	    }
	}
};
