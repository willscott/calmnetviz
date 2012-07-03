Calm Net Viz
=====

About
-----

This is a collection of visualization tools for network traffic.  The most
developed is a geographical view of traffic, which pinpoints  remote
servers on a world map.

Installation
-----

The tool has been tested on Mac and Linux.  The build enivronment will require
some non-trivial changes to support windows - most noticably in creating an
appropriate carnivore jni binary.

Prerequisites:

* Pcap
* Java
* Ant, Awk (for building)

Building:

> ant

Running
-----

> ant run -Dtarget=Viz
