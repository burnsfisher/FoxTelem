package telemetry.FoxBPSK;

import common.Config;
import common.FoxSpacecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

import common.Log;
import common.Spacecraft;
import decoder.Decoder;
import decoder.FoxDecoder;
import telemetry.BitArrayLayout;
import telemetry.FoxFramePart;
import telemetry.FoxPayloadStore;
import telemetry.Frame;
import telemetry.HighSpeedTrailer;
import telemetry.PayloadMaxValues;
import telemetry.PayloadMinValues;
import telemetry.PayloadRadExpData;
import telemetry.PayloadRtValues;
import telemetry.PayloadStore;
import telemetry.PayloadUwExperiment;
import telemetry.PayloadWOD;
import telemetry.PayloadWODRad;
import telemetry.PayloadWODUwExperiment;
import telemetry.uw.CanPacket;

/**
	 * 
	 * FOX 1 Telemetry Decoder
	 * @author chris.e.thompson g0kla/ac2cz
	 *
	 * Copyright (C) 2015 amsat.org
	 *
	 * This program is free software: you can redistribute it and/or modify
	 * it under the terms of the GNU General Public License as published by
	 * the Free Software Foundation, either version 3 of the License, or
	 * (at your option) any later version.
	 *
	 * This program is distributed in the hope that it will be useful,
	 * but WITHOUT ANY WARRANTY; without even the implied warranty of
	 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	 * GNU General Public License for more details.
	 *
	 * You should have received a copy of the GNU General Public License
	 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
	 *
	 */
	public class FoxBPSKFrame extends Frame {
		
		public static final int MAX_FRAME_SIZE = 476;
		public static final int MAX_HEADER_SIZE = 8;
		public static final int PAYLOAD_SIZE = 78;
		public static final int MAX_PAYLOAD_SIZE = MAX_FRAME_SIZE - MAX_HEADER_SIZE; 
		public static final int MAX_TRAILER_SIZE = 96;
		
		public static final int NUMBER_DEFAULT_PAYLOADS = 6; 
		
		public static final int ALL_WOD_FRAME = 0;
		public static final int REALTIME_FRAME = 1;
		public static final int MINMAX_FRAME = 2;
		public static final int REALTIME_BEACON = 3;
		public static final int WOD_BEACON = 4;
		public static final int CAN_PACKET_SCIENCE_FRAME = 5;
		public static final int CAN_PACKET_CAMERA_FRAME = 6;
		public static final int TYPES_OF_FRAME = 7;
		
		private boolean canPacketFrame = false;

		public FoxFramePart[] payload;
		
		HighSpeedTrailer trailer = null;

		int numberBytesAdded = 0;
		
		public FoxBPSKFrame() {
			super();
			bytes = new byte[MAX_HEADER_SIZE + MAX_PAYLOAD_SIZE + MAX_TRAILER_SIZE];
		}

		public FoxBPSKFrame(BufferedReader input) throws IOException {
			super(input);
		}
		
		public FoxBPSKHeader getHeader() { return (FoxBPSKHeader)header; }
		
		public void addNext8Bits(byte b) {
			if (corrupt) return;
			if (numberBytesAdded < MAX_HEADER_SIZE-1) {
				if (header == null)
					header = new FoxBPSKHeader();
				header.addNext8Bits(b);
			} else if (numberBytesAdded == MAX_HEADER_SIZE-1) {
				// first non header byte
				header.copyBitsToFields(); // make sure the id is populated
				fox = (FoxSpacecraft) Config.satManager.getSpacecraft(header.id);
				if (fox != null) {
					initPayloads(header.id, header.getType());
					if (payload[0] == null) {
						if (Config.debugFrames)
							Log.errorDialog("ERROR","FOX ID: " + header.id + " Type: " + header.getType() + " not valid. Decode not possible.\n"
									+ "Turn off Debug Frames to prevent this message in future.");
						else
							Log.println("FOX ID: " + header.id + " Type: " + header.getType() + " not valid. Decode not possible.");

						corrupt = true;
						return;
					}
					if (Config.debugFrames)
						Log.println(header.toString());
				} else {
					if (Config.debugFrames)
						Log.errorDialog("ERROR","FOX ID: " + header.id + " is not configured in the spacecraft directory.  Decode not possible.\n"
								+ "Turn off Debug Frames to prevent this message in future.");
					else
						Log.println("FOX ID: " + header.id + " is not configured in the spacecraft directory.  Decode not possible.");							
					corrupt = true;
					return;
				}
			} else if (numberBytesAdded < MAX_HEADER_SIZE + PAYLOAD_SIZE) {
				payload[0].addNext8Bits(b);
			} else if (canPacketFrame)
				payload[0].addNext8Bits(b);
			else if (numberBytesAdded < MAX_HEADER_SIZE + PAYLOAD_SIZE*2)
				payload[1].addNext8Bits(b);
			else if (numberBytesAdded < MAX_HEADER_SIZE + PAYLOAD_SIZE*3)
				payload[2].addNext8Bits(b);
			else if (numberBytesAdded < MAX_HEADER_SIZE + PAYLOAD_SIZE*4)
				payload[3].addNext8Bits(b);
			else if (numberBytesAdded < MAX_HEADER_SIZE + PAYLOAD_SIZE*5)
				payload[4].addNext8Bits(b);
			else if (numberBytesAdded < MAX_HEADER_SIZE + PAYLOAD_SIZE*6)
				payload[5].addNext8Bits(b);
			else if (numberBytesAdded < MAX_HEADER_SIZE + MAX_PAYLOAD_SIZE + MAX_TRAILER_SIZE)
				;//trailer.addNext8Bits(b); //FEC ;
			else
				Log.println("ERROR: attempt to add byte past end of frame");

			bytes[numberBytesAdded] = b;
			numberBytesAdded++;
		}
		
		

		/**
		 *  Here is how the frames are defined in the IHU:
		 *  
            // 0 ALL_WOD_FRAME
            {WOD_SCI_PAYLOAD6, WOD_HK_PAYLOAD5,  WOD_SCI_PAYLOAD6,WOD_HK_PAYLOAD5,  WOD_SCI_PAYLOAD6, WOD_HK_PAYLOAD5},
            // 1 REALTIME_FRAME (Realtime plus WOD, actually)
            {WOD_SCI_PAYLOAD6,WOD_HK_PAYLOAD5,WOD_SCI_PAYLOAD6, WOD_HK_PAYLOAD5, REALTIME_PAYLOAD1,RAD_EXP_PAYLOAD4},
            // 2 MINMAX_FRAME (Min/Max plus WOD)
            {WOD_SCI_PAYLOAD6,WOD_HK_PAYLOAD5,WOD_SCI_PAYLOAD6, WOD_HK_PAYLOAD5,MAX_VALS_PAYLOAD2,MIN_VALS_PAYLOAD3},
            // 3 REALTIME_BEACON
            {REALTIME_PAYLOAD1, WOD_HK_PAYLOAD5,  WOD_HK_PAYLOAD5, WOD_HK_PAYLOAD5,  WOD_HK_PAYLOAD5, REALTIME_PAYLOAD1},
            // 4 WOD_BEACON
            {WOD_HK_PAYLOAD5,WOD_HK_PAYLOAD5,WOD_HK_PAYLOAD5,WOD_HK_PAYLOAD5, WOD_HK_PAYLOAD5,WOD_HK_PAYLOAD5}
		 *
		 * @param type
		 */
		private void initPayloads(int foxId, int type) {
			switch (type) {
			case ALL_WOD_FRAME:
				payload = new FoxFramePart[NUMBER_DEFAULT_PAYLOADS];
				for (int i=0; i<NUMBER_DEFAULT_PAYLOADS; i+=2 ) {
					if (foxId == FoxSpacecraft.HUSKY_SAT) {
						payload[i] = new PayloadWODUwExperiment(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));
						payload[i].captureHeaderInfo(header.id, header.uptime, header.resets);
					} else {
						payload[i] = new PayloadWODRad(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));						
					}
					payload[i+1] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				}
				break;
			case REALTIME_FRAME:
				payload = new FoxFramePart[NUMBER_DEFAULT_PAYLOADS];
				if (foxId == FoxSpacecraft.HUSKY_SAT) {
					payload[0] = new PayloadWODUwExperiment(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));
					payload[0].captureHeaderInfo(header.id, header.uptime, header.resets);
				} else
					payload[0] = new PayloadWODRad(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));
				payload[1] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				if (foxId == FoxSpacecraft.HUSKY_SAT) {
					payload[2] = new PayloadWODUwExperiment(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));
					payload[2].captureHeaderInfo(header.id, header.uptime, header.resets);
				} else
					payload[2] = new PayloadWODRad(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));
				payload[3] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[4] = new PayloadRtValues(Config.satManager.getLayoutByName(header.id, Spacecraft.REAL_TIME_LAYOUT));
				if (foxId == FoxSpacecraft.HUSKY_SAT) {
					payload[5] = new PayloadUwExperiment(Config.satManager.getLayoutByName(header.id, Spacecraft.RAD_LAYOUT));
					payload[5].captureHeaderInfo(header.id, header.uptime, header.resets);
				} else
					payload[5] = new PayloadRadExpData(Config.satManager.getLayoutByName(header.id, Spacecraft.RAD_LAYOUT));
				break;
			case MINMAX_FRAME:
				payload = new FoxFramePart[NUMBER_DEFAULT_PAYLOADS];
				if (foxId == FoxSpacecraft.HUSKY_SAT) {
					payload[0] = new PayloadWODUwExperiment(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));
					payload[0].captureHeaderInfo(header.id, header.uptime, header.resets);
				} else
					payload[0] = new PayloadWODRad(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));
				payload[1] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				if (foxId == FoxSpacecraft.HUSKY_SAT) {
					payload[2] = new PayloadWODUwExperiment(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));
					payload[2].captureHeaderInfo(header.id, header.uptime, header.resets);
				} else
					payload[2] = new PayloadWODRad(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));
				payload[3] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[4] = new PayloadMaxValues(Config.satManager.getLayoutByName(header.id, Spacecraft.MAX_LAYOUT));
				payload[5] = new PayloadMinValues(Config.satManager.getLayoutByName(header.id, Spacecraft.MIN_LAYOUT));
				break;
			case REALTIME_BEACON:
				payload = new FoxFramePart[NUMBER_DEFAULT_PAYLOADS];
				payload[0] = new PayloadRtValues(Config.satManager.getLayoutByName(header.id, Spacecraft.REAL_TIME_LAYOUT));
				payload[1] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[2] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[3] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[4] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[5] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				break;
			case WOD_BEACON:
				payload = new FoxFramePart[NUMBER_DEFAULT_PAYLOADS];
				for (int i=0; i<NUMBER_DEFAULT_PAYLOADS; i++ ) {
					payload[i] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				}
				break;
			case CAN_PACKET_SCIENCE_FRAME:
				payload = new FoxFramePart[1];
				payload[0] = new PayloadUwExperiment(Config.satManager.getLayoutByName(header.id, Spacecraft.RAD_LAYOUT));
				payload[0].captureHeaderInfo(header.id, header.uptime, header.resets);
				canPacketFrame = true;
				break;
			case CAN_PACKET_CAMERA_FRAME:
				payload = new FoxFramePart[1];
				payload[0] = new PayloadUwExperiment(Config.satManager.getLayoutByName(header.id, Spacecraft.RAD_LAYOUT));
				payload[0].captureHeaderInfo(header.id, header.uptime, header.resets); 
				canPacketFrame = true;
				break;
			default: 
				break;
			}
		}

		public boolean savePayloads(FoxPayloadStore payloadStore) {

			header.copyBitsToFields(); // make sure we have defaulted the extended FoxId correctly
			for (int i=0; i<payload.length; i++ ) {
				if (payload[i] != null) {
					payload[i].copyBitsToFields();
					if (payload[i] instanceof PayloadUwExperiment) { // Also saves WOD UW Payloads
						((PayloadUwExperiment)payload[i]).savePayloads(payloadStore);
					} else
						if (!payloadStore.add(header.getFoxId(), header.getUptime(), header.getResets(), payload[i]))
							return false;
				}
			}
			return true;			
		}
		
		public static int getMaxDataBytes() {
			return MAX_HEADER_SIZE + MAX_PAYLOAD_SIZE;
		}
		
		public static int getMaxBytes() {
			return MAX_HEADER_SIZE + MAX_PAYLOAD_SIZE + MAX_TRAILER_SIZE;
		}

		/**
		 * Get a buffer containing all of the CAN Packets in this frame.  There may be multiple payloads that have CAN Packets,
		 * so we need to check all of them.  First we gather the bytes from each payload in the PCAN format.  We return an 
		 * array of those byte arrays.  The calling routine will send each PCAN packet individually
		 */
		public byte[][] getPayloadBytes() {

			byte[][] allBuffers = null;

			Spacecraft sat = Config.satManager.getSpacecraft(foxId);
			if (sat.sendToLocalServer()) {
				int totalBuffers = 0;
				for (int i=0; i< payload.length; i++) {
					// if this payload should be output then add to the byte buffer
					if (payload[i] instanceof PayloadUwExperiment) {
						byte[][] buffer = ((PayloadUwExperiment)payload[i]).getCANPacketBytes(); 
						totalBuffers += buffer.length; 
					}
				}
					
				allBuffers = new byte[totalBuffers][];
				int startPosition = 0;
				for (int p=0; p< payload.length; p++) {
					// if this payload should be output then add its byte buffers to the output
					if (payload[p] instanceof PayloadUwExperiment) {
						byte[][] buffer = ((PayloadUwExperiment)payload[p]).getCANPacketBytes(); 
						for (int j=0; j < buffer.length; j++) {
							allBuffers[j + startPosition] = buffer[j];
						}
						startPosition += buffer.length;
					}
				}

			}
			return allBuffers;				
		}

		public String toString() {
			String s = new String();
			s = "\n" + header.toString();
			
			if (payload != null) {
				for (int i=0; i < payload.length; i++) {
					s = s + "\n"+ payload[i].toString() +
					"\n"; 
				}
			} 
			
			return s;
		}
}
