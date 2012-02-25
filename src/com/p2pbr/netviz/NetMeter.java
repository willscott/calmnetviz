package com.p2pbr.netviz;

import processing.core.*;

import java.util.Date;
import java.util.LinkedList;

import org.rsg.carnivore.*;
import org.rsg.lib.Log;

public class NetMeter extends PApplet {
	private static final long serialVersionUID = 6707035246409474675L;

	private class pkt {
		public Date time;
		public int bytes;
	}
	double knownWindow = 60; // reference 'max'
	double MAX_CNT = 125000; // 1Mbps in bytes/s.
	LinkedList<pkt> inWindow = new LinkedList<pkt>();
	LinkedList<pkt> inNow = new LinkedList<pkt>();
	int inTotal = 0;
	int windowTotal = 0;
	CarnivoreP5 c;
	PFont font32;

	public void setup() {
		size(800, 600);
		ellipseMode(CENTER);
		background(0);
	  
		Log.setDebug(false);
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
		  
		  background(255);
		  fill(0, 102, 153);
		  text("Please initialize packet sniffing!", x, y);
	}

		// Iterate through each node 
	synchronized void drawNodes() {
		pkt p;
		while (inNow.size() > 0) {
			p = inNow.remove();
			if (p.time.getTime() + 1000 < new Date().getTime()) {
				inTotal -= p.bytes;
			} else {
				inNow.addFirst(p);
				break;
			}
		}
		while (inWindow.size() > 0) {
			p = inWindow.remove();
			if (p.time.getTime() + 1000*knownWindow < new Date().getTime()) {
				windowTotal -= p.bytes;
			} else {
				inWindow.addFirst(p);
				break;
			}
		}
		double windowDuration = knownWindow;
		if (inWindow.size() > 0) {
			p = inWindow.getFirst();
			windowDuration = (new Date().getTime() - p.time.getTime()) / 1000.0;
		}
		
		float windowFraction = (float) (height * (windowTotal/windowDuration)/MAX_CNT);
		background(255);
		fill(102);
		rectMode(CORNER);
		rect(0, height - windowFraction, width, windowFraction);

		
		int x = width/2 - 200;
		int y = 75;
		fill(0, 0, 0);
		text("Awesome: " + inTotal + " vs " + (windowTotal/windowDuration), x, y);
	}

	// Called each time a new packet arrives
	public synchronized void packetEvent(CarnivorePacket packet){
		  pkt pkt = new pkt();
		  pkt.time = new Date();
		  pkt.bytes = packet.data.length;
		  if (inTotal + pkt.bytes > MAX_CNT)
		  {
			  pkt.bytes = (int) (MAX_CNT - inTotal);
		  }
		  if (pkt.bytes == 0) {
			  return;
		  }
		  inWindow.add(pkt);
		  inNow.add(pkt);
		  inTotal += pkt.bytes;
		  windowTotal += pkt.bytes;
	}

	public static void main(String args[]) {
	    PApplet.main(new String[] { "--present", "com.p2pbr.netviz.NetMeter" });
	}
}
