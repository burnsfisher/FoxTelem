package telemetry;

import java.io.BufferedReader;
import java.io.IOException;

import common.Config;
import common.Log;

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
public class SlowSpeedFrame extends Frame {
	
	public static final int MAX_HEADER_SIZE = 6;
	public static final int MAX_PAYLOAD_SIZE = 58;
	public static final int MAX_TRAILER_SIZE = 32;
	
	//SlowSpeedHeader header = null;
	FramePart payload = null;
	FramePart fecTrailer = null;
	
	public SlowSpeedFrame() {
		super();
		header = new SlowSpeedHeader();
		fecTrailer = new SlowSpeedTrailer();
		bytes = new byte[MAX_HEADER_SIZE + MAX_PAYLOAD_SIZE + MAX_TRAILER_SIZE];
	}
	
	public SlowSpeedFrame(BufferedReader input) throws IOException {
		super(input);
	}
	
	public int getType() {
		return header.getType();
	}

	public SlowSpeedHeader getHeader() {
		return (SlowSpeedHeader)header;
	}

	public FramePart getPayload() {
		return payload;
	}

	
	public void addNext8Bits(byte b) {
		if (numberBytesAdded < MAX_HEADER_SIZE)
			header.addNext8Bits(b);
		else if (numberBytesAdded < MAX_HEADER_SIZE + MAX_PAYLOAD_SIZE)
			payload.addNext8Bits(b);
		else if (numberBytesAdded < MAX_HEADER_SIZE + MAX_PAYLOAD_SIZE + MAX_TRAILER_SIZE)
			fecTrailer.addNext8Bits(b); //FEC ;
		else
			Log.println("ERROR: attempt to add byte past end of frame");

		bytes[numberBytesAdded] = b;
		numberBytesAdded++;

		if (numberBytesAdded == MAX_HEADER_SIZE) {
			// Then we 
			header.copyBitsToFields();
			if (Config.debugFrames) Log.println("DECODING PAYLOAD TYPE: " + header.type);
			int type = header.type;
			fox = Config.satManager.getSpacecraft(header.id);
			if (fox != null) {
				if (type == FramePart.TYPE_DEBUG) payload = new PayloadRtValues(Config.satManager.getRtLayout(header.id));
				if (type == FramePart.TYPE_REAL_TIME) payload = new PayloadRtValues(Config.satManager.getRtLayout(header.id));
				if (type == FramePart.TYPE_MAX_VALUES) payload = new PayloadMaxValues(Config.satManager.getMaxLayout(header.id));
				if (type == FramePart.TYPE_MIN_VALUES) payload = new PayloadMinValues(Config.satManager.getMinLayout(header.id));
				if (type == FramePart.TYPE_RAD_EXP_DATA) payload = new PayloadRadExpData(Config.satManager.getRadLayout(header.id));
				if (type > FramePart.TYPE_RAD_EXP_DATA) {
					Log.println("INVALID payload type, defaulting to Real Time Values");
					payload = new PayloadRtValues(Config.satManager.getRtLayout(header.id));
				}
			} else {
				Log.errorDialog("Missing or Invalid Fox Id", "FOX ID: " + header.id + " is not configured in the spacecraft directory.  Decode not possible.");
			}
		}
		
		if (numberBytesAdded == MAX_HEADER_SIZE + MAX_PAYLOAD_SIZE) {
			payload.copyBitsToFields();
		}
	}
	
	public static int getMaxBytes() {
		return MAX_HEADER_SIZE + MAX_PAYLOAD_SIZE + MAX_TRAILER_SIZE;
	}
	
	public String toString() {
		return "\n" + header.toString() 
				+ "\n\n"+ payload.toString() + "\n"; //\n"+ fecTrailer.toString() + "\n";
	}

}
