package com.p2pbr.netviz;

import processing.core.*;

import com.maxmind.geoip.*;
import java.util.*;

public class TorNetViz extends PApplet {
	private static final long serialVersionUID = 9075470452122575298L;
    
	// Map and drawing related items.
	PImage mapImage;
	private final String mapFilename = "1024px-Equirectangular-projection.jpg";
	
	// Constants for drawing.
	private final double WINDOW_SIZE = 15; // reference 'max'
	private final double MAX_BANDWIDTH = 100000000.0; 
	private final int mapX = 0;
	private final int mapY = 0;
	private final int WIDTH = 1024;
	private final int HEIGHT = 600;
	private final int FRAMERATE = 10;
	
	// Hookup to the MaxMind database.
	LookupService geoLookup;
	boolean dbConnected = false;
	
	// A clock for drawing items in a timely manner. *cue rimshot*
	private TimeStamp clock = null;
	
	// A priority queue of Pins. These are all loaded in at the
	// beginning of the program, and are popped off and drawn if the
	// LinkedList's timestamp matches the simulated clock.
	private Queue<PinCollection> PinsToDraw = new PriorityQueue<PinCollection>();
	
	// A linked list to hold Pins until they're finished drawing.
	private List<PinCollection> DrawingPins = new LinkedList<PinCollection>();

	// An object that keep together bunches of Pins and labels them with
	// a common timestamp. More memory efficient, nice encapsulation.
	// Lists should be LinkedLists, because deleting is important.
	private class PinCollection implements Comparable<PinCollection> {
		// Tracks the PinCollection's internal time.
		private TimeStamp pinTime;
		
		// Stores all the Pins in a queue.
		private List<Pin> pins;
		
		// Constructor makes a TimeStamp from a string, 
		public PinCollection(String s, List<Pin> newPins) {
			pinTime = new TimeStamp(s);
			pins = newPins;
		}
		
		// Iterates through all the Pins and draws them all.
		// If the list is empty, returns 0.
		public boolean drawThesePins() {
			
			// Drawing everything in the LinkedList.
			Iterator<Pin> iter = pins.iterator();
			while (iter.hasNext()) {
				Pin p = iter.next();
				boolean keep = p.drawSelf();
				if (!keep) {
					iter.remove();
				}
			}
			
			return !pins.isEmpty();
		}
		
		// Return the pinTime.
		public TimeStamp getPinTime() {
			return pinTime;
		}
		
		// Compare method to implement Comparable. Calls TimeStamp comparable.
		public int compareTo(PinCollection other) {
			return pinTime.compareTo(other.pinTime);
		}
	}
	
	// An object that can be drawn on the map by Processing.
	private class Pin {
		// For Processing.
		@SuppressWarnings("unused")
		PApplet parent;
		
		// Location and drawing related fields.
		@SuppressWarnings("unused")
		public PImage mapImage;
		public float x;
		public float y; 
		
		// Constants to be used across all pins.
		private final int STATE_STATIC = 0;
		private final int STATE_ANIMATE = 1;
		private final int ANIMATION_MAX = 20;
		private final int ANIMATION_RADIUS = 400;
		private final int PULSE_MAX = 5;
		
		// Animation related fields.
		public int state;
		public int animation;
		private boolean pulseUp = true;
		private int pulse = -1 * PULSE_MAX;
		
		// Response time. Unreached = -1, "last known" Pin = -2.
		// Used for drawing.
		private float response;
		
		// Last known address Pin, if unreached. Otherwise, null.
		private Pin LastKnown;
		
		// Constructor.
		public Pin(PApplet p, PImage mapImage, String[] pieces) {
			// Process the strings to get the IP address and the timestamp.
			// [0] = ip, [1] = timestamp, [2] = response time, [3] = last known ip
			
			// Acquire the latitude and longitude.
			float latlon = getLatLonByIP(pieces[0]);
			
			// Initialize drawing stuff from latlon.
			this.parent = p;
			this.mapImage = mapImage;
			this.x = map(latlon[1], -180, 180, mapX, mapX+mapImage.width); // uses lon
			this.y = map(latlon[0], 90, -90, mapY, mapY+mapImage.height); // uses lat
			
			// Set the response time.
			response = parseFloat(pieces[2]);
			
			// Set the LastKnown address Pin, if this is unreached.
			if (response == -1) {
				// Creates a string to be processed by the next Pin constructor.
				String[] LKPin = {pieces[3], pieces[1], "-2", "-1"};
				LastKnown = new Pin(LKPin);
			} else {
				LastKnown = null;
			}
			
			// Initialize animation stuff.
			this.state = STATE_ANIMATE;
			this.animation = 1;
		}
		
		// More animation stuff. (ask Will, maybe?)
		private int pulseStep() {
			if (pulseUp) { pulse++; }
			else         { pulse--; }
			if (pulse >= PULSE_MAX)      { pulseUp = false; }
			if (pulse <= PULSE_MAX * -1) { pulseUp = true; }
			return pulse;
		}
		
		// Well duh.
		public boolean drawSelf() {
			
			// Draw the last known reached location, if unreached.
			if (LastKnown != null) {
				LastKnown.drawSelf();
			}
			
			// Determine color based on response time.
			int red = 0x00;
			int green = 0x00;
			int blue = 0x00;
			if (response == -1) { // unreached
				red = 0xff;
			} else if (response == -2) { // last known for unreached
				red = 0xff;
				green = 0xff;
			} else { // reached (can modify for speed later)
				green = 0xff;
			}
			
			// Actually draw the sucker.
			int rad = 8;
			rad += this.pulseStep();
			if (state == STATE_STATIC) {
				fill(0x00, 0x00, 0x00, 0x00);
				stroke(red, green, blue);
				ellipse(this.x, this.y, rad, rad);
				return true;
			} else if (state == STATE_ANIMATE) {
				// circle starts large, gets small
				// starts fully opaque, becomes transparent
				fill(0x00, 0x00, 0x00, 0x00);
				stroke(red, green, blue);
				ellipse(this.x, this.y, ANIMATION_RADIUS/this.animation, ANIMATION_RADIUS/this.animation);
		
				// circle starts small, gets to target size
				// starts transparent, becomes opaque
				fill(red, green, blue, 0x00);
				stroke(red, green, blue);
				ellipse(this.x, this.y, rad - (rad/this.animation), rad - (rad/this.animation));
		
				this.animation++;
				if (this.animation >= ANIMATION_MAX) {
					this.state = STATE_STATIC; 
				}
				return true;
			}
			return false;
		}
	}
	
	// An object containing a year, month, date, hour, minute, and second.
	// Used to mark PinCollections with their time, and to keep track of a
	// simulated clock.
	private class TimeStamp implements Comparable<TimeStamp> {
		// Year zero is the year 2000. Heresy indeed.
		// Discarded seconds.
		int year; int month; int date; int hour; int minute;
		
		// Minutes and hours to increment the TimeStamp by on an advancement call.
		int minIncrement; int hrIncrement;

		// Constructs a timestamp from a particular format.
		// YEAR_MONTH_DATE-HOUR:MINUTE:SECOND
		// Does not allow advancement.
		public TimeStamp(String s) {
			this(s, 0);
		}
		
		// Constructs a timestamp from a particular format, and also
		// initializes the advancement mechanism.
		public TimeStamp(String s, int minInc) {
			String[] chopped = s.split("[_-:]");
			for (int i = 0; i < 4; i++) {
				int temp = parseInt(chopped[i]);
				switch (i) {
					case 0: year = temp; break;
					case 1: month = temp; break;
					case 2: date = temp; break;
					case 3: hour = temp; break;
				}
			}
			
			// Set up the increments.
			minIncrement = minInc;
			hrIncrement = 0;
			while (minIncrement >= 60) {
				minIncrement -= 60;
				hrIncrement++;
			}
		}
		
		// Advances the TimeStamp if being used as a clock.
		public void AdvanceClock() {
			minute += minIncrement;
			hour += hrIncrement;
			if (minute >= 60) {
				minute -= 60;
				hour++;
				if (hour >= 24) {
					hour -= 24;
					date++;
					if (ShouldAdvanceMonth()) {
						date = 1;
						month++;
						if (month > 12) {
							month = 1;
							year++;
						}
					}
				}
			}
		}
		
		private boolean ShouldAdvanceMonth() {
			return (date > 31 && ( (month < 8 && month % 2 = 1) || (month >= 8 && month % 2 = 0) ))
						|| (date > 30 && (month == 4 || month == 6 || month == 9 || month == 11))
						|| ((( date > 29 && year % 4 == 0 ) || ( date > 28 && year % 4 != 0 )) && month == 2);
		}
		
		// If the integers are equal, proceed to the next test.
		// If they are not, return the result of the test.
		public int compareTo(TimeStamp other) {
			if (year == other.year) {
				if (month == other.month) {
					if (date == other.date) {
						if (hour == other.hour) {
							return subCompare(minute, other.minute);
						}
						return subCompare(hour, other.hour);
					}
					return subCompare(date, other.date);
				}
				return subCompare(month, other.month);
			}
			return subCompare(year, other.year);
		}
					
		private int subCompare(int ours, int others) {
			if (ours < others) {
				return -1;
			} else if (ours > others) {
				return 1;
			} else { // (ours == others)
				return 0;
			}
		}
	}
	
	// Needs to initialize the clock to the time specified on command line.
	// Also load all the Pins into PinCollections.
	
	public void setup() {
		// connect to the database of geolocation data
		try {
			geoLookup = new LookupService("GeoLiteCity.dat");
			dbConnected = true;
		} catch(Exception e) {
			dbConnected = false;
		}
		
		// Setup the clock at a rounded increment.
			// [data time] 		(1440 mins / day) divided by
			// [animation time] (FRAMERATE frames/second * INPUT second / day)
		int minIncr = 1440 / (FRAMERATE * INPUT_ONE_DAY_IN_SECS);
		clock = new TimeStamp(STARTING_INPUT_STRING, minIncr);
		
		// Fetch and process files into Pins.
		CreatePins();
		
		// load the map image
		mapImage = loadImage(mapFilename);
		size(WIDTH, HEIGHT);
		background(0x00, 0x55, 0xcc);
		
		// set the frame rate for Processing
		frameRate(FRAMERATE);
	}
	
	// Make Pin objects from each line of the input files.
	// All .viz files are in a single location, labelled by timestamp.
	public void CreatePins() {
		File measures = new File("./connectivity/viz");
		
		// This filter returns the directories that are equal to or
		// after the command line arg starting timestamp.
		FileFilter filter = new FileFilter() {
			public boolean accept(File file) {
				return file.getName().compareTo(STARTING_INPUT_STRING) >= 0 &&
					   file.getName().compareTo(ENDING_INPUT_STRING) <= 0;
			}
		};
		
		// For each file in the array:
		File[] vizFiles = measures.listFiles(filter);
		for (int i = 0; i < vizFiles.length; i++) {
			
			// Create a new scanner.
			Scanner scotty = new Scanner(vizFiles[i]);
			
			// Get the first line, held outside the loop.
			String[] currPin = scotty.nextLine().split("\t");
			
			// Create a new list of Pins.
			List<Pin> pinJar = new LinkedList<Pin>();
			
			// Running until there are no more lines in the file:
			boolean keepRunning = true;
			while(keepRunning) {
				
				// Create a Pin from currPin, put it in the list.
				pinJar.add(new Pin(p, mapImage, currPin));
				
				// Get the next line.
				String[] nextPin = scotty.nextLine().split("\t");
				
				// While currPin and nextPin have the same timestamp,
				// Keep making pins and pushing them onto the list.
				while (currPin[1].compareTo(nextPin[1]) == 0) {
					pinJar.add(new Pin(p, mapImage, nextPin));
					
					// There are no more lines to read:
					if (!scotty.hasNextLine()) {
						keepRunning = false;
						break;
					}
					
					// Drop the last pin, get the next pin.
					currPin = nextPin;
					nextPin = scotty.nextLine().split("\t");
				}
				
				// When they no longer have the same timestamp, or out of lines:
				
				// Package the Pins in a PinCollection, put it in the PriorityQueue.
				// Ensures usage of currPin's timestamp, which is the same
				// as the rest of the list.
				PinsToDraw.add(new PinCollection(currPin[1], pinJar));
				
				// Create a new list to fill with pins.
				pinJar = new LinkedList<Pin>();
				
				// Save the new timestamp'd nextPin into currPin.
				currPin = nextPin;
			}
		}
	}
	
	// Called by Processing, FRAMERATE number of times a second.
	// Check the pins in the PriorityQueue, put the appropriate ones
	// into the LinkedList. Draw all the pins in the LinkedList.
	public void draw() {
		// draw map
		image(mapImage, mapX, mapY);
		
		// Advance the clock by a precalculated amount of time.
		clock.AdvanceClock();
		
		// Now we have to update the Pins.
		// Move PinCollections from the PriorityQueue over into the
		// List, if their timestamp is less than or equal to the clock time.
		while (PinsToDraw.peek().getPinTime().compareTo(clock) <= 0) {
			DrawingPins.add(PinsToDraw.remove());
		}
		
		// Keep drawing everything in the LinkedList.
		Iterator<PinCollection> iter = DrawingPins.iterator();
		while (iter.hasNext()) {
			PinCollection p = iter.next();
			boolean keep = p.drawThesePins();
			if (!keep) {
				iter.remove();
			}
		}
	}
	
	// Self-explanatory.
	float[] getLatLonByIP(String ip) {
		float lat = 1000;
		float lon = 1000;
		if (!dbConnected) {
			return null;
		}

		Location loc = geoLookup.getLocation(ip);
		if (loc != null) {
			lat = loc.latitude;
			lon = loc.longitude;
		}
		
		float[] latlon = {lat, lon};
		return latlon;
	}

	public static void main(String args[]) {
		// Ratio of simulated to real-time seconds is a command line arg to
		// the program, and needs to be encapsulated.
		PApplet.main(new String[] { "--present", "com.p2pbr.netviz.TorNetViz" });
	}
}

