package com.p2pbr.netviz;

import processing.core.*;

import de.bezier.data.sql.*;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import org.rsg.carnivore.*;
import org.rsg.carnivore.net.*;
import org.rsg.lib.Log;

public class NetViz extends PApplet {
	private static final long serialVersionUID = 9075470452122575298L;

    SQLite db;
    boolean dbConnected = false;
    
    HashMap<String, Pin> pins = new HashMap<String, Pin>();
    HashSet<String> countries = new HashSet<String>();
    HashSet<String> cities = new HashSet<String>();
    
    PImage mapImage;
    private final String mapFilename = "1024px-Equirectangular-projection.jpg";
    int ctr = 0;
    int average[];
    int avgBin = 0;
    int newPackets = 0;
    
    Pin localPin, broadcastPin, loopbackPin, autoconfigPin;
    
    // CONSTANTS
    private final double WINDOW_SIZE = 15; // reference 'max'
    private final double MAX_CNT = 125000; // 1Mbps in bytes/s.
    private final float WINDOW_WEIGHT = 0.55;
    private final float MAX_BANDWIDTH = 100000000.0; 
    private final int BINS = 5;
    private final int mapX = 0;
    private final int mapY = 0;
    private final int WIDTH = 1024;
    private final int HEIGHT = 600;
    private final int DEAD_TIMER_CAP = 60;  //10 frames after losing the last of its bytes, a pin vanishes
    
    private final String LOCAL_IP = "LOCAL";
    private final String LOOPBACK_IP = "LOOPBACK";
    private final String BROADCAST_IP = "BROADCAST";
    private final String AUTOCONFIG_IP = "AUTOCONFIG";
    private final String TESTNET_IP = "TESTNET";
    private final String OTHER_IP = "OTHER";
    
    LinkedList<pkt> inWindow = new LinkedList<pkt>();
    LinkedList<pkt> inNow = new LinkedList<pkt>();
    CarnivoreP5 c;
    
    Date lastTime;
    
    int lastBG[]  = new int[3];
    
    private class Pin {
      PApplet parent;
    
      public int state;
      public int animation;
      
      private final int STATE_STATIC = 0;
      private final int STATE_ANIMATE = 1;
      
      private final int ANIMATION_MAX = 20;
      private final int ANIMATION_RADIUS = 400;
      
      private final int PULSE_MAX = 5;
      
      private int pulse = -1 * PULSE_MAX;
      
      public float lat;
      public float lon;
      
      public String country;
      public String city;
     
      public PImage mapImage;
      public float x;
      public float y; 
      
      public int bytes = 0;
      
      private int deadTimer = 1;
      private boolean pulseUp = true;
      
      public Pin(PApplet p, PImage mapImage, float lat, float lon, String country, String city) {
        this.parent = p;
        this.mapImage = mapImage;
        this.x = map(lon, -180, 180, mapX, mapX+mapImage.width);
        this.y = map(lat, 90, -90, mapY, mapY+mapImage.height);
        this.lat = lat;
        this.lon = lon;
        this.country = country;
        this.city = city;
        
        this.state = STATE_ANIMATE;
        this.animation = 1;
      }

      private int pulseStep() {
        if (pulseUp) { pulse++; }
        else         { pulse--; }
        if (pulse >= PULSE_MAX)      { pulseUp = false; }
        if (pulse <= PULSE_MAX * -1) { pulseUp = true; }
        return pulse;
      }

      public boolean drawSelf() {
        int rad = 8;
        if (bytes > 0) {
          rad = (int)Math.log(bytes)*5;      
        }
        if (rad < 8) {
          rad = 8; 
        }
        rad += this.pulseStep();
    
        if (state == STATE_STATIC) {
          //println("static, rad="+rad);
          if (bytes > 0) {
            int variation = (int)(parent.random(3));
            //println("bytes >0");
            fill(0x00, 0x00, 0x00, 0x00);
            stroke(0xff, 0x00, 0xff);
            ellipse(this.x, this.y, rad, rad);
            return true;
          }
          else if (deadTimer <= DEAD_TIMER_CAP) {
            stroke(0xff, 0xff, 0x00, 0xaa/deadTimer);  //no bytes left in window - display as transparent
            fill(0x00, 0x00);
            ellipse(this.x, this.y, rad, rad);
            deadTimer++;
            return true;
          }
          else {
            return false;
          }
        }
        else if (state == STATE_ANIMATE) {
          //println("animating");
          // circle starts large, gets small
          // starts fully opaque, becomes transparent
          fill(0x00, 0x00, 0x00, 0x00);
          stroke(0xff, 0xff, 0x00);
          ellipse(this.x, this.y, ANIMATION_RADIUS/this.animation, ANIMATION_RADIUS/this.animation);
      
          // circle starts small, gets to target size
          // starts transparent, becomes opaque
          fill(0xff, 0xff, 0x00, 0x00);
          stroke(0xff, 0xff, 0x00);
          ellipse(this.x, this.y, rad - (rad/this.animation), rad - (rad/this.animation));
      
          this.animation++;
          if (this.animation >= ANIMATION_MAX) {
            this.state = STATE_STATIC; 
          }
          return true;
        }
        return false;
      }
        
      public void addBytes(int bytes) {
        this.bytes += bytes;
        if (bytes > 0) {
          deadTimer = 1; 
        }
      }
      public void subBytes(int bytes) {
        this.bytes -= bytes; 
      }
    }
    
    private class pkt {
      public Date time;
      public int bytes;
      public IPAddress ip;
    }
    
    void setup() {
      // connect to the database of geolocation data
      db = new SQLite(this, "hostip.sqlite3"); //open database file!
      if (db.connect()) {
        dbConnected = true;  
      }
      else {
        dbConnected = false; 
      }
            
      // load the map image
      mapImage = loadImage(mapFilename);
      
      // setup pins for local, loopback, autoconfig, broadcast.
      //public Pin(PImage mapImage, float lat, float lon, String country, String city) {
      localPin = new Pin(this, mapImage, -105, -160, null, null);
      broadcastPin = new Pin(this, mapImage, -105, -120, null, null);
      loopbackPin = new Pin(this, mapImage, -105, -80, null, null);
      autoconfigPin = new Pin(this, mapImage, -105, -40, null, null);
    
      // 
      size(WIDTH, HEIGHT);
      background(0x00, 0x55, 0xcc);
      frameRate(10);
      lastBG[0] = 0x00;
      lastBG[1] = 0x55;
      lastBG[2] = 0xcc;
      CarnivoreP5 c = new CarnivoreP5(this);
      c.setShouldSkipUDP(false);
      Log.setDebug(false); // Uncomment this for verbose mode
      //c.setVolumeLimit(4);
      // Use the "Create Font" tool to add a 12 point font to your sketch,
      // then use its name as the parameter to loadFont().
      //font = loadFont("CourierNew-12.vlw");
      //textFont(font);
    }
    
    void draw() {
      // draw background color according to general traffic rates
      int bg[] = getBackgroundColorFromTrafficSpeed();
      int r = lastBG[0];
      if (bg[0] > lastBG[0]) {
        r += 8;
      } 
      else if (bg[0] < lastBG[0]) {
        r -= 3; 
      }
      int g = lastBG[1];
      int b = lastBG[2];
      background(r,g,b);
      lastBG[0] = r;
      lastBG[1] = g;
      lastBG[2] = b;
      
      drawPointsForNewPackets();
      drawDbConnectedIndicator();
    
      // draw map
      image(mapImage, mapX, mapY);
      
      drawPinsOnMap();
      
    }
    
    synchronized private void drawPinsOnMap() {
      // draw pins on map
      Iterator<Pin> iter = pins.values().iterator();
      while (iter.hasNext()) {
        Pin p = iter.next();
        boolean keep = p.drawSelf(); 
        if (!keep) {
          iter.remove();
        } 
      }
      localPin.drawSelf();
      broadcastPin.drawSelf();
      loopbackPin.drawSelf();
      autoconfigPin.drawSelf();
    }
    private void drawDbConnectedIndicator() {
      if (dbConnected) {
        fill(0x00, 0xFF, 0x00); 
      }
      else {
        fill(0xFF, 0x00, 0x00); 
      }
      ellipse(15, 15, 10, 10);
    }
    private void drawPointsForNewPackets() {
      // draw new packets
      for (int i=0; i<this.newPackets/2; i++) {
        stroke(0xFF, 0xFF, 0xFF);
        int x = (int)(random(WIDTH-1));
        int y = (int)(random(mapImage.height, HEIGHT-1));
        point(x,y);
      }
      this.newPackets = this.newPackets/2;
      stroke(0);  
      
    }
    
    int[] getBackgroundColorFromTrafficSpeed() {
    
      int lastSecondBytes = sumList(inNow);
      int lastWindowBytes = sumList(inWindow);
      
      prune();
      
      double logSecondBytes = Math.log10(lastSecondBytes);
    //  int logWindowBytes = int(Math.log((double)lastWindowBytes));
      
      double logBandwidth = Math.log(MAX_BANDWIDTH);
      
      float windowFraction = (float)lastSecondBytes/(lastWindowBytes/(float)WINDOW_SIZE);
      double absFraction = (double)logSecondBytes/logBandwidth;
      
      //System.out.println(logBandwidth +" "+ lastSecondBytes+" "+ + logSecondBytes +" "+ absFraction);
      
      if (absFraction > 1) {
        absFraction = 1;   
      }
      int r = (int)(Math.round(255 * absFraction));
      if (r > 255) {
        r = 255; 
      }
      int toReturn[] = {r, 0x55, 0xcc};
      return toReturn;
    }
    
    synchronized void prune() {
      pkt p;
      // prune old data from last second buffer
      while (inNow.size() > 0) {
        p = inNow.remove();
        if (! (p.time.getTime() + 1000 < new Date().getTime())) {
          inNow.addFirst(p);
          break; 
        }
        else {
          Pin pin;
          String reserved = isReserved(p.ip);
          if (reserved == null) {
            pin = pins.get(p.ip.toString());
          }
          else if (reserved.equals(LOCAL_IP)) { 
            pin = localPin;
          }
          else if (reserved.equals(BROADCAST_IP)) {
            pin = broadcastPin;
          }
          else if (reserved.equals(LOOPBACK_IP)) {
            pin = loopbackPin;
          }
          else if (reserved.equals(AUTOCONFIG_IP)) {  
            pin = autoconfigPin;
          }
          else {
            pin = null; 
          }
          if (pin != null) {
            pin.subBytes(p.bytes);
          }
        }
      }  
      // prune old data from last minute buffer
      while (inWindow.size() > 0) {
        p = inWindow.remove();
        if (! (p.time.getTime() + 1000*WINDOW_SIZE < new Date().getTime())) {
           inWindow.addFirst(p);
           break;
        } 
      } 
    }
    synchronized int sumList(LinkedList<pkt> l) {
      Date nowTime = new Date();
      Iterator<pkt> iter = l.descendingIterator();
      int sum = 0;
      int count = 0;
      while (iter.hasNext()) {
        pkt p = iter.next();
        sum += p.bytes;
        count++;
      }
    //  System.out.println(count+" "+sum); 
      return sum;
    }
    
    String getCityByIP(IPAddress ip) {
      if (!dbConnected) {
        return null; 
      }
    
      db.query("SELECT city as \"City\" FROM ip4_"+ip.octet1()+" WHERE b="+ip.octet2()+" AND c="+ip.octet3()+";");
      String city = "NONE";
      while (db.next()) {
        city = db.getString("City"); 
      }
      if (!city.equals("NONE")) {
        db.query("SELECT name as \"Name\" FROM cityByCountry WHERE city="+city);
      }
      String cityName = "NONE";
      while (db.next()) {
        cityName = db.getString("Name");
      }
      return cityName;
    }
    String getCountryByIP(IPAddress ip) {
      if (!dbConnected) {
        return null; 
      }
    
      db.query("SELECT country as \"Country\" FROM ip4_"+ip.octet1()+" WHERE b="+ip.octet2()+" AND c="+ip.octet3()+";");
      String country = "NONE";
      while (db.next()) {
        country = db.getString("Country"); 
      }
      if (!country.equals("NONE")) {
        db.query("SELECT name as \"Name\" FROM countries WHERE id="+country);
      }
      String countryName = "NONE";
      while (db.next()) {
        countryName = db.getString("Name");
      }
      return countryName;
      
    }
    
    float[] getLatLonByIP(IPAddress ip) {
      float lat = 1000;
      float lng = 1000;
      if (!dbConnected) {
        return null;
      }
      db.query("SELECT city as \"City\" FROM ip4_"+ip.octet1()+" WHERE b="+ip.octet2()+" AND c="+ip.octet3()+";");
      String city = "NONE";
      while (db.next()) {
        city = db.getString("City"); 
      }
      if (!city.equals("NONE") && !city.equals("0")) {
        db.query("SELECT lat AS \"Latitude\", lng AS \"Longitude\" FROM cityByCountry WHERE city="+city);  
        while (db.next()) {
          lat = db.getFloat("Latitude");
          lng = db.getFloat("Longitude"); 
        }
      }
      else {
        String country = getCountryByIP(ip);
        String q = "SELECT lat AS \"Latitude\", lng AS \"Longitude\" FROM countryLatLon WHERE name=\""+country+"\"";
        //println(q);
        db.query(q);
        while (db.next()) {
          lat = db.getFloat("Latitude");
          lng = db.getFloat("Longitude"); 
        }
      }
      
      float[] latlon = {lat, lng};
      return latlon;
    }
    // Called each time a new packet arrives
    synchronized void packetEvent(CarnivorePacket packet) {
      pkt pkt = new pkt();
      pkt.time = new Date();
      pkt.ip = packet.senderAddress;
      pkt.bytes = packet.data.length;
      
      if (pkt.bytes == 0) {
        return;
      }
      IPAddress ip = packet.senderAddress;
      
      String reserved = isReserved(ip);
      if (reserved == null) {
        Pin p;
        if (pins.containsKey(packet.senderAddress.toString())) {
          p = pins.get(packet.senderAddress.toString()); 
        }
        else {
          String country = getCountryByIP(ip);
          String city = getCityByIP(ip);
          float[] latlon = getLatLonByIP(ip);
          float lat = latlon[0];
          float lon = latlon[1];
    
          p = new Pin(this, mapImage, lat, lon, country, city);
          pins.put(ip.toString(), p);
        }
        p.addBytes(pkt.bytes);  
      }  
      else if (reserved == LOCAL_IP) { 
        localPin.addBytes(pkt.bytes);
      }
      else if (reserved == BROADCAST_IP) {
        broadcastPin.addBytes(pkt.bytes);
      }
      else if (reserved == LOOPBACK_IP) {
        loopbackPin.addBytes(pkt.bytes);
      }
      else if (reserved == AUTOCONFIG_IP) {  
        autoconfigPin.addBytes(pkt.bytes);
      }
      else { //testnet or other - shouldn't see these, ignore them?
        return;
      }
      //println(reserved);
      inWindow.add(pkt);
      inNow.add(pkt);
      
      newPackets++;
    }
    
    private String isReserved(IPAddress ip) {
      if (    ip.octet1() == 10    ||
             (ip.octet1() == 172 && ip.octet2() >= 16 && ip.octet2() <= 31)  ||
             (ip.octet1() == 192 && ip.octet2() == 168)   ) { 
               
               return LOCAL_IP;
               
      }
      if (    ip.octet1() == 127     ) {
        
               return LOOPBACK_IP; 
      }
      if (    ip.octet1() == 0     || 
              ip.octet1() == 255 && ip.octet2() == 255 && ip.octet3() == 255 && ip.octet4() == 255   ) {
                
               return BROADCAST_IP;          
      }
      if (    (ip.octet1() == 169 && ip.octet2() == 254)    ) {
        
               return AUTOCONFIG_IP; 
      }         
      if (    (ip.octet1() == 198 && ip.octet2() == 51 && ip.octet3() == 100)  ||
              (ip.octet1() == 203 && ip.octet2() == 0  && ip.octet3() == 113)  ||
              (ip.octet1() == 192 && ip.octet2() == 0  && ip.octet3() == 2)     ) {
                
               return TESTNET_IP;          
      }
      if (    ip.octet1() >= 224   ||      
              ip.octet1() >= 240   ||
              (ip.octet1() == 192 && ip.octet2() == 88 && ip.octet3() == 99)   ||          
              (ip.octet1() == 198 && ip.octet2() >= 18 && ip.octet2() <= 19)    ) {
      
               return OTHER_IP;          
      }
      return null;
    }

	public static void main(String args[]) {
	    PApplet.main(new String[] { "--present", "com.p2pbr.netviz.NetViz" });
	}
}
