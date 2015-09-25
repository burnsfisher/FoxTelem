package telemetry;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;

import common.Config;
import common.Log;
import common.Spacecraft;

/**
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
 * 
 * This stores the payloads for all of the satellites.  The class callings the methods does not need to know
 * how the data is stored.  The data could be moved into an SQL database in the future and it should make no 
 * difference to code outside of this class
 * 
 *
 */
public class PayloadDbStore implements Runnable {
	public static final int DATA_COL = 0;
	public static final int UPTIME_COL = 1;
	public static final int RESETS_COL = 2;
	private boolean running = true;
	private boolean done = false;
	
	private SortedFramePartArrayList payloadQueue;
	
	public static Connection derby;
	private final int INITIAL_QUEUE_SIZE = 1000;
	SatPayloadDbStore[] payloadStore;
	SatPictureStore[] pictureStore;
	
	public PayloadDbStore() {
		payloadQueue = new SortedFramePartArrayList(INITIAL_QUEUE_SIZE);
		ArrayList<Spacecraft> sats = Config.satManager.getSpacecraftList();
		// Connect to the database and create it if it does not exist
		String driver = "org.apache.derby.jdbc.EmbeddedDriver";
		try {
			Class.forName(driver);
		} catch(java.lang.ClassNotFoundException e) {
			System.out.println(e);
		}
		String dbName="FOXDB";
		String connectionURL = "jdbc:derby:" + dbName + ";create=true";
		
		try {
			derby = DriverManager.getConnection(connectionURL);
			System.out.println("Connected to FOXDB ..");
		} catch (Throwable e) {
			System.out.println("Exception(s) thrown");
			errorPrint(e);
			Log.errorDialog("FATAL", "Can not connect to the Payload Store.  Maybe another version of FoxTelem is running. Exiting..");
		}
		payloadStore = new SatPayloadDbStore[sats.size()];
		pictureStore = new SatPictureStore[sats.size()];
		for (int s=0; s<sats.size(); s++) {
			payloadStore[s] = new SatPayloadDbStore(sats.get(s));
			if (sats.get(s).hasCamera()) pictureStore[s] = new SatPictureStore(sats.get(s).foxId);;
			
		}
	}
	
	public boolean hasQueuedFrames() {
		if (payloadQueue.size() > 0) return true;
		return false;
	}
	
	private SatPayloadDbStore getPayloadStoreById(int id) {
		for (SatPayloadDbStore store : payloadStore)
			if (store != null)
				if (store.foxId == id) return store;
		return null;
	}
	private SatPictureStore getPictureStoreById(int id) {
		for (SatPictureStore store : pictureStore)
			if (store != null)
				if (store.foxId == id) return store;
		return null;
	}

	public void setUpdatedAll() {
		for (SatPayloadDbStore store : payloadStore)
		if (store != null)
			store.setUpdatedAll();
	}

	public void setUpdatedAll(int id) {
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			store.setUpdatedAll();
	}
	
	public boolean getUpdatedRt(int id) { 
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			return store.getUpdatedRt();
		return false;
	}
	
	public void setUpdatedRt(int id, boolean u) {
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			store.setUpdatedRt(u);
	}
	
	public boolean getUpdatedMax(int id) { 
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			return store.getUpdatedMax();
		return false;
	}
	public void setUpdatedMax(int id, boolean u) {
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			store.setUpdatedMax(u);
	}

	public boolean getUpdatedMin(int id) { 
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			return store.getUpdatedMin();
		return false;
	}
	public void setUpdatedMin(int id, boolean u) {
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			store.setUpdatedMin(u);
	}
	public boolean getUpdatedRad(int id) { 
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			return store.getUpdatedRad();
		return false;
	}
	public void setUpdatedRad(int id, boolean u) {
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			store.setUpdatedRad(u);
	}
	public boolean getUpdatedCamera(int id) { 
		SatPictureStore store = getPictureStoreById(id);
		if (store != null)
			return store.getUpdatedCamera();
		return false;
	}
	public void setUpdatedCamera(int id, boolean u) {
		SatPictureStore store = getPictureStoreById(id);
		if (store != null)
			store.setUpdatedCamera(u);
	}
	public int getTotalNumberOfFrames() {
		int total = 0;
		for (SatPayloadDbStore store : payloadStore)
			total += store.getNumberOfFrames();
		return total;
	}
	public int getTotalNumberOfTelemFrames() { 
		int total = 0;
		for (SatPayloadDbStore store : payloadStore)
			total += store.getNumberOfTelemFrames();
		return total;
	}
	public int getTotalNumberOfRadFrames() { 
		int total = 0;
		for (SatPayloadDbStore store : payloadStore)
			total += store.getNumberOfRadFrames();
		return total;
	}

	public int getNumberOfFrames(int id) { 
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			return store.getNumberOfFrames();
		return 0;
	}
	public int getNumberOfTelemFrames(int id) { 
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			return store.getNumberOfTelemFrames();
		return 0;
	}
	public int getNumberOfRadFrames(int id) { 
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			return store.getNumberOfRadFrames();
		return 0;
	}
	
	
	public int getNumberOfPictureCounters(int id) { 
		SatPictureStore store = getPictureStoreById(id);
		if (store != null)
			return store.getNumberOfPictureCounters();
		return 0;
	}
	/*
	public int getNumberOfPictureLines(int id) { 
		SatPictureStore store = getPictureStoreById(id);
		if (store != null)
			return store.getNumberOfPictureLines();
		return 0;
	}
	*/

	public SortedJpegList getJpegIndex(int id) {
		SatPictureStore store = getPictureStoreById(id);
		if (store != null)
			return store.jpegIndex;
		return null;
	}

	public boolean add(int id, long uptime, int resets, FramePart f) {
		f.captureHeaderInfo(id, uptime, resets);
		return payloadQueue.add(f);
	}

	public boolean addToDb(int id, long uptime, int resets, FramePart f) {
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			try {
				return store.add(id, uptime, resets, f);
			} catch (IOException e) {
				// FIXME We dont want to stop the decoder but we want to warn the user...
				e.printStackTrace(Log.getWriter());
			}
		return false;
	}
	
	

	/**
	 * Add an array of payloads, usually when we have a set of radiation data from the high speed
	 * @param f
	 * @return
	 */
	public boolean add(int id, long uptime, int resets, PayloadRadExpData[] f) {
		for (int i=0; i< f.length; i++) {
			if (f[i].hasData()) {
				f[i].captureHeaderInfo(id, uptime, resets);
				f[i].type = 100 + i; // store the index in the type field so it is unique
				payloadQueue.add(f[i]);
			}
		}
		return true;
	}

	public boolean addToDb(int id, long uptime, int resets, PayloadRadExpData[] f) {
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			return store.add(id, uptime, resets, f);
		return false;
		
	}

	/**
	 * Add a camera payload.  This is added to the picture line store one line at a time.  We do not store the actual
	 * camera payloads as there is no additional information that we need beyond the lines.  The raw frame are sent to the server
	 * @param id
	 * @param uptime
	 * @param resets
	 * @param f
	 * @return
	 */
	public boolean addToDb(int id, long uptime, int resets, PayloadCameraData f) {
		SatPictureStore store = getPictureStoreById(id);
		if (store != null) {
			ArrayList<PictureScanLine> lines = f.pictureLines;
			for (PictureScanLine line : lines) {
				// Capture the header into the line
				line.id = id;
				line.resets = resets;
				line.uptime = uptime;
				try {
					if (!store.add(id, resets, uptime, line))
						return false;
				} catch (IOException e) {
					// FIXME We don't want to stop the decoder but we want to warn the user...
					// this probably means we did not store the camera payload or could not create the Jpeg.  Perhaps the header was missing etc
					e.printStackTrace(Log.getWriter());
				} catch (ArrayIndexOutOfBoundsException e) {
					// FIXME We dont want to stop the decoder but we want to warn the user...
					Log.println("CORRUPT CAMERA DATA, line not written: " + id + " " + resets + " " + uptime);
					e.printStackTrace(Log.getWriter());
				}
			}
		}
		return true;
	}

	public PayloadRtValues getLatestRt(int id) {
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			try {
				return store.getLatestRt();
			} catch (SQLException e) {
				e.printStackTrace(Log.getWriter());
				return null;
			}
		return null;
	}

	public PayloadMaxValues getLatestMax(int id) {
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			try {
				return store.getLatestMax();
			} catch (SQLException e) {
				e.printStackTrace(Log.getWriter());
				return null;
			}
		return null;
	}

	public PayloadMinValues getLatestMin(int id) {
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			try {
				return store.getLatestMin();
			} catch (SQLException e) {
				e.printStackTrace(Log.getWriter());
				return null;
			}
		return null;

	}

	public PayloadRadExpData getLatestRad(int id) {
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			try {
				return store.getLatestRad();
			} catch (SQLException e) {
				e.printStackTrace(Log.getWriter());
				return null;
			}
		return null;

	}

	/**
	 * Try to return an array with "period" entries for this attribute, starting with the most 
	 * recent
	 * 
	 * @param name
	 * @param period
	 * @return
	 * @throws SQLException 
	 */
	public double[][] getRtGraphData(String name, int period, Spacecraft fox, int fromReset, long fromUptime) {
		SatPayloadDbStore store = getPayloadStoreById(fox.foxId);
		if (store != null)
			return store.getRtGraphData(name, period, fox, fromReset, fromUptime);
		return null;
	}

	public double[][] getMaxGraphData(String name, int period, Spacecraft fox, int fromReset, long fromUptime) {
		SatPayloadDbStore store = getPayloadStoreById(fox.foxId);
		if (store != null)
			return store.getMaxGraphData(name, period, fox, fromReset, fromUptime);
		return null;		
	}

	public double[][] getMinGraphData(String name, int period, Spacecraft fox, int fromReset, long fromUptime) {
		SatPayloadDbStore store = getPayloadStoreById(fox.foxId);
		if (store != null)
			return store.getMinGraphData(name, period, fox, fromReset, fromUptime);
		return null;		
	}

	/**
	 * Return an array of radiation data with "period" entries for this sat id and from the given reset and
	 * uptime.
	 * @param period
	 * @param id
	 * @param fromReset
	 * @param fromUptime
	 * @return
	 * @throws SQLException 
	 */
	public String[][] getRadData(int period, int id, int fromReset, long fromUptime) {
		SatPayloadDbStore store = getPayloadStoreById(id);
		if (store != null)
			return store.getRadData(period, id, fromReset, fromUptime);
		return null;
	}


	/**
	 * Delete all of the log files.  This is called from the main window by the user
	 */
	public void deleteAll() {
		for (SatPayloadDbStore store : payloadStore)
			if (store != null)
				store.deleteAll();
		for (SatPictureStore store : pictureStore)
			if (store != null)
				store.deleteAll();

	}
	
	static void errorPrint(Throwable e) {
		if (e instanceof SQLException)
			SQLExceptionPrint((SQLException)e);
		else {
			System.out.println("A non SQL error occured.");
			e.printStackTrace();
		}
	} // END errorPrint

	// Iterates through a stack of SQLExceptions
	static void SQLExceptionPrint(SQLException sqle) {
		while (sqle != null) {
			System.out.println("\n---SQLException Caught---\n");
			System.out.println("SQLState: " + (sqle).getSQLState());
			System.out.println("Severity: " + (sqle).getErrorCode());
			System.out.println("Message: " + (sqle).getMessage());
			sqle.printStackTrace();
			sqle = sqle.getNextException();
		}
	} // END SQLExceptionPrint

	/**
	 * The run thread is for inserts, so that we minimize the load on the decoder.  We check the queue of payloads and add any that are in it
	 */
	@Override
	public void run() {

		running = true;
		done = false;
		while(running) {
			try {
				Thread.sleep(100); // check for new inserts multiple times per second
			} catch (InterruptedException e) {
				Log.println("ERROR: PayloadStore thread interrupted");
				e.printStackTrace(Log.getWriter());
			} 	
			if (payloadQueue.size() > 0) {
				for (int i=0; i< payloadQueue.size(); i++) {
					FramePart f = payloadQueue.get(i);
					if (Config.debugFieldValues)
						Log.println(f.toString());
					addToDb(f.id, f.uptime, f.resets, f);
					payloadQueue.remove(i);
				}
			}
		}

		done = true;
	}


}
