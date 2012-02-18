package com.p2pbr.netviz;

import processing.core.*;

import java.util.HashMap;
import java.util.Iterator;
import org.rsg.carnivore.*;
import org.rsg.carnivore.net.*;
import org.rsg.lib.Log;

public class NetViz extends PApplet {
	private static final long serialVersionUID = 9075470452122575298L;

	HashMap nodes = new HashMap();
	double startDiameter = 150.0;
	double shrinkSpeed = 0.99;
	int splitter, x, y;
	CarnivoreP5 c;
	PFont font32;

	public void setup() {
		size(800, 600);
		ellipseMode(CENTER);
		background(0);
	  
		Log.setDebug(true); // Uncomment this for verbose mode
		c = new CarnivoreP5(this);
	}

	public void draw() {
		  if(c.isMacAndPromiscuousModeFailed) {
			  drawError();
		  } else {
			  drawMap();
		  }
	}

	void drawMap(){
		  background(255);
		  drawNodes();
		}

		void drawError(){
		  int x = width/2 - 200; 
		  int y = 75;
		  int lineheight = 32; 
		  
		  background(255);
		  fill(0, 102, 153);
		  text("Please initialize packet sniffing.", x, y);
		  y += lineheight*2;
		  
		  text("Step 1--Open the Terminal.", x, y);
		  y += 20;
		  
		  y += lineheight*4.5;

		  text("Step 2--Type this command:", x, y);
		  y += lineheight;

		  fill(0);
		  text("sudo chmod 777 /dev/bpf*", x, y);
		  y += 10;

		  y += lineheight*6;

		  fill(0, 102, 153);
		  text("Step 3--Quit and relaunch.", x, y);
		  y += lineheight;
		  
		}

		// Iterate through each node 
		synchronized void drawNodes() {
		  Iterator it = nodes.keySet().iterator();
		  while(it.hasNext()){
		    String ip = (String)it.next();
		    float d = Float.valueOf(nodes.get(ip).toString());

		    // Use last two IP address bytes for x/y coords
		    String ip_as_array[] = split(ip, '.');
		    x = Integer.valueOf(ip_as_array[2]) * width / 255; // Scale to applet size
		    y = Integer.valueOf(ip_as_array[3]) * height / 255; // Scale to applet size
		    
		    // Draw the node
		    stroke(0);
		    fill(color(100, 100, 100, 200)); // Rim
		    ellipse(x, y, d, d);             // Node circle
		    noStroke();
		    fill(color(100, 100, 100, 50));  // Halo
		    ellipse(x, y, d + 20, d + 20);
		    
		    // Shrink the nodes a little
		    if(d > 50)
		      nodes.put(ip, "" + (d * shrinkSpeed));
		  }  
		}

		// Called each time a new packet arrives
		public synchronized void packetEvent(CarnivorePacket packet){
		  println("[PDE] packetEvent: " + packet);

		  // Remember these nodes in our hash map
		  nodes.put(packet.receiverAddress.toString(), "" + (startDiameter));
		  nodes.put(packet.senderAddress.toString(), "" + (startDiameter));
		}

	public static void main(String args[]) {
	    PApplet.main(new String[] { "--present", "com.p2pbr.netviz.NetViz" });
	}
}
