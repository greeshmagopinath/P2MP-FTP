package com.p2mp.application;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class UDPSender {
	int noOfHosts;
	int MSS;
	String fileName;
	static AtomicInteger sequenceNo = new AtomicInteger(1);
	static byte[] fileContents;;
	static List<Receiver> staticList = new ArrayList<Receiver>();
	static DatagramSocket datagramSocket;
	static AtomicBoolean  ACKRecievedFromAllReceivers= new AtomicBoolean(true);
	static List<Receiver> dynamicList =Collections.synchronizedList(new ArrayList<Receiver>()); 
	static Timer timer = null;
	byte[] packetToBeSent = null;
	static long timeout = 20*3;
	AtomicBoolean[] ack;
	
	 private void resendPackets(byte[] packetToBeSent) throws IOException{
	        
		 if(ack[sequenceNo.get()-1].get() == true){
			// System.out.println("Not Sending packet after timeout since ack already received for " + (sequenceNo.get()-1));
		 } else {
			 sequenceNo.decrementAndGet();
			 synchronized (dynamicList) {
     		for (Iterator<Receiver> iterator = dynamicList.iterator(); iterator.hasNext();) 
         	{
         		Receiver receiver = (Receiver) iterator.next();
         		String ipAddr = receiver.getIpAddress();
         		int port = receiver.getPort();
     			InetAddress IP = InetAddress.getByName(ipAddr);
     			DatagramPacket packet = new DatagramPacket(packetToBeSent, packetToBeSent.length,IP, port);
     			datagramSocket.send(packet);
         	}	
		}
		 	System.out.println("Timeout, sequence number "+sequenceNo.get());
		 	sequenceNo.addAndGet(1);
		 	timer = new Timer();
			timer.schedule(new TimeOut(), timeout);
	    
		 }
	 }	
	
	private void initalize() {
		File file = new File(fileName);
		int len = (int) file.length();
		FileInputStream in = null;
		try
		{
		fileContents = new byte[len];
		in = new FileInputStream(file);
		int bytes_read = 0;
		int n;
		do {
			n = in.read(fileContents, bytes_read, len - bytes_read);
			bytes_read += n;
		} while ((bytes_read < len) && (n != -1));
		} catch(FileNotFoundException fe) {
			System.out.println("File not found.");
		} catch (IOException e) {
		    System.out.println("IO exception");
		} finally {
			try {
				in.close();
			} catch (IOException e) {
			System.out.println("Buffered Reader not closed.");
			}
		}
		
	    try {
			datagramSocket = new DatagramSocket();
		} catch (SocketException e) {
		 System.out.println("Error Starting the socket");
		}
		SocketReceiver socketReceiver = new SocketReceiver();
		socketReceiver.start();
	}
	
	public static void main(String args[]) {
		
		UDPSender udpSender = new UDPSender();
		try {
			if (args != null) {
				int noOfHosts = args.length - 3;
				int argMSS = Integer.parseInt(args[args.length-1]);
				String argFileName = args[args.length-2];
				int argServerPort = Integer.parseInt(args[args.length-3]);
				udpSender.setMSS(argMSS);
				udpSender.setFileName(argFileName);
				for (int i = 0; i < noOfHosts; i++) {
					staticList.add(new Receiver(args[i],argServerPort));
				}
			}
			udpSender.initalize();
			udpSender.sendPackets();
		
		} catch (Exception ex) {
			System.err.println(ex.getMessage());
		}
		}
	private void sendPackets() {
		try 
		{
			  int noOfPackets = fileContents.length / MSS;
		int lasPacketSize = 0;
		if (fileContents.length % MSS != 0) {
			lasPacketSize = fileContents.length % MSS;
			noOfPackets = noOfPackets +1;
		}
		
		ack = new AtomicBoolean[noOfPackets+2];
		for(int i=1;i<ack.length;i++){
			ack[i] = new AtomicBoolean(false);
		}
		//To account for End of file indicator5
		noOfPackets = noOfPackets +1;
		int startIndex = 0;
		int packetSize = getMSS();
		System.out.println("Total number of packets are:" + noOfPackets);
		while(true) {
		while (sequenceNo.get() <= noOfPackets && ACKRecievedFromAllReceivers.get() && ack[sequenceNo.get()].get() == false) {
			ACKRecievedFromAllReceivers.set(false);
			dynamicList.clear();
			dynamicList.addAll(staticList);
			if(sequenceNo.get() == noOfPackets) {
				System.out.println("Sending Last ACK for end of file indicator");
				packetToBeSent = new byte[8];
				packetToBeSent[0] = (byte) ((sequenceNo.get() >> 24) & 0xFF);
				packetToBeSent[1] = (byte) ((sequenceNo.get() >> 16) & 0xFF);
				packetToBeSent[2] = (byte) ((sequenceNo.get() >> 8) & 0xFF);
				packetToBeSent[3] = (byte) (sequenceNo.get() & 0xFF);
				packetToBeSent[4]= 0;
				packetToBeSent[5]= 0;
				packetToBeSent[6] = 0;
				packetToBeSent[7] = 0;
				//checksum
				int sum;
				InternetChecksum checksum=new InternetChecksum();
				sum=(int) checksum.calculateChecksum(packetToBeSent);
				packetToBeSent[4]=(byte)((sum>>8)& 0xFF);
				packetToBeSent[5]=(byte)((sum)& 0xFF);
				//System.out.println("sent packet"+sequenceNo.get());
				sequenceNo.getAndAdd(1);	
				
				for (Iterator<Receiver> iterator = staticList.iterator(); iterator.hasNext();) {
					Receiver receiver = (Receiver) iterator.next();
		     		String ipAddr = receiver.getIpAddress();
		     		int port = receiver.getPort();
					InetAddress IP = InetAddress.getByName(ipAddr);
					DatagramPacket packet = new DatagramPacket(packetToBeSent, packetToBeSent.length,IP, port);
					datagramSocket.send(packet);
				}
			    timer = new Timer();
				timer.schedule(new TimeOut(), timeout);
			} else if(sequenceNo.get() == noOfPackets-1 && lasPacketSize != 0) {
				//System.out.println("There is a last data packet.sending it now as sequence number:" +sequenceNo);
				packetToBeSent = new byte[lasPacketSize+8];
				System.arraycopy(fileContents, startIndex, packetToBeSent, 8,lasPacketSize);
				startIndex += MSS;
				packetToBeSent[0] = (byte) ((sequenceNo.get() >> 24) & 0xFF);
				packetToBeSent[1] = (byte) ((sequenceNo.get() >> 16) & 0xFF);
				packetToBeSent[2] = (byte) ((sequenceNo.get() >> 8) & 0xFF);
				packetToBeSent[3] = (byte) (sequenceNo.get() & 0xFF);
				//insert checksum
				packetToBeSent[4]= 0;
				packetToBeSent[5]= 0;
				packetToBeSent[6] = 85;
				packetToBeSent[7] = 85;
				int sum;
				InternetChecksum checksum=new InternetChecksum();
				sum=(int) checksum.calculateChecksum(packetToBeSent);
				packetToBeSent[4]=(byte)((sum>>8)& 0xFF);
				packetToBeSent[5]=(byte)((sum)& 0xFF);
				//System.out.println("sent packet"+sequenceNo);
				sequenceNo.getAndAdd(1);
				for (Iterator<Receiver> iterator = staticList.iterator(); iterator.hasNext();) {
					Receiver receiver = (Receiver) iterator.next();
		     		String ipAddr = receiver.getIpAddress();
		     		int port = receiver.getPort();
					InetAddress IP = InetAddress.getByName(ipAddr);
					DatagramPacket packet = new DatagramPacket(packetToBeSent, packetToBeSent.length,IP, port);
					datagramSocket.send(packet);
				}
			     timer = new Timer();
				 timer.schedule(new TimeOut(), timeout);
			} else {
				packetToBeSent = new byte[MSS+8];
				System.arraycopy(fileContents, startIndex, packetToBeSent, 8,packetSize);
				String Message=new String(packetToBeSent);
				//System.out.println(Message);
				startIndex += MSS;
				//packetToBeSent = new byte[8];
				packetToBeSent[0] = (byte) ((sequenceNo.get() >> 24) & 0xFF);
				packetToBeSent[1] = (byte) ((sequenceNo.get() >> 16) & 0xFF);
				packetToBeSent[2] = (byte) ((sequenceNo.get() >> 8) & 0xFF);
				packetToBeSent[3] = (byte) (sequenceNo.get() & 0xFF);
				//checksum
				packetToBeSent[4]= 0;
				packetToBeSent[5]= 0;
				packetToBeSent[6] = 85;
				packetToBeSent[7] = 85;
				int sum;
				InternetChecksum checksum=new InternetChecksum();
				sum=(int) checksum.calculateChecksum(packetToBeSent);
				packetToBeSent[4]=(byte)((sum>>8)& 0xFF);
				packetToBeSent[5]=(byte)((sum)& 0xFF);
				//System.out.println("sent packet"+sequenceNo);
				sequenceNo.getAndAdd(1);
				for (Iterator<Receiver> iterator = staticList.iterator(); iterator.hasNext();) {
					Receiver receiver = (Receiver) iterator.next();
		     		String ipAddr = receiver.getIpAddress();
		     		int port = receiver.getPort();
					InetAddress IP = InetAddress.getByName(ipAddr);
					DatagramPacket packet = new DatagramPacket(packetToBeSent, packetToBeSent.length,IP, port);
					datagramSocket.send(packet);
				}
			    timer = new Timer();
				timer.schedule(new TimeOut(), timeout);
			}
		}
		if(sequenceNo.get() == noOfPackets + 1 && ACKRecievedFromAllReceivers.get()) {
			  break;
		}
		}
		} catch(Exception c) {
			System.out.println("Exception Occured.");
		}
			//System.out.println("Done sending all packets and received ACK from all receivers.");
			System.exit(1);
	}

	
	/**
	 * The Class DataReceiver.
	 */
	private class SocketReceiver extends Thread {
		
		public void run() {
			boolean isRunning = true;
			DatagramPacket inPacket = null;
			byte[] inBuffer = null;
				inBuffer = new byte[2048];
				inPacket = new DatagramPacket(inBuffer, inBuffer.length);
			
			while (isRunning) {
				try {
					datagramSocket.receive(inPacket);
					ACKReceiver dataReceiever = new ACKReceiver(inPacket);
					dataReceiever.start();
				} catch (Exception e) {
					System.out.println("Error Processing packets at the receiver\n");
				}
			}
		}

		}
	private class ACKReceiver extends Thread {
		/** The data received. */
		byte[] dataReceived;
		
		/** The address. */
		String address;
		
		int port;
		
		Receiver receiver = new Receiver();
		
		public ACKReceiver(DatagramPacket packetRecieved) {
			address = packetRecieved.getAddress().getHostAddress();
			port = packetRecieved.getPort();
			receiver.setIpAddress(address);
			receiver.setPort(port);
			
			dataReceived = new byte[packetRecieved.getLength()];
			System.arraycopy(packetRecieved.getData(), 0, dataReceived, 0,
					dataReceived.length);
		}
		
		public void run() {
			int dataPacketIndicator = ((dataReceived[6] & 0xFF) << 8) | (dataReceived[7] & 0xFF);
			int ackNumber = ((dataReceived[0] & 0xFF) << 24)
					| ((dataReceived[1] & 0xFF) << 16)
					| ((dataReceived[2] & 0xFF) << 8)
					| (dataReceived[3] & 0xFF);
		//System.out.println("Received ack for " + ackNumber);
		synchronized (ack[ackNumber-1]) {
			if (dataPacketIndicator == 43690 && ackNumber == sequenceNo.get() && ack[ackNumber-1].get()== false) {
				dynamicList.remove(receiver);
				if (dynamicList.isEmpty()) {
					timer.cancel();
					//System.out.println("Received ACK from all receivers.Next packet to be sent is:" + sequenceNo.get());
					ACKRecievedFromAllReceivers.set(true);
					ack[ackNumber-1].set(true);
			}
				}
			}}
		}
	private class TimeOut extends TimerTask {
        public void run() {
        	synchronized (ack[sequenceNo.get()-1]) {
			if(ack[sequenceNo.get()-1].get() == false) {
            //System.out.println("TimeOut Occurred for Packet No:" +sequenceNo);
            timer.cancel(); //Terminate the timer thread
            try {
				
            	resendPackets(packetToBeSent);
			} catch (IOException e) {
			 System.out.println("Exception");
			}}}
        }
    }
	
	/**
	 * @return the noOfHosts
	 */
	public int getNoOfHosts() {
		return noOfHosts;
	}

	/**
	 * @param noOfHosts the noOfHosts to set
	 */
	public void setNoOfHosts(int noOfHosts) {
		this.noOfHosts = noOfHosts;
	}

	
	/**
	 * @return the mSS
	 */
	public int getMSS() {
		return MSS;
	}

	/**
	 * @param mSS the mSS to set
	 */
	public void setMSS(int mSS) {
		MSS = mSS;
	}

	/**
	 * @return the fileName
	 */
	public String getFileName() {
		return fileName;
	}

	/**
	 * @param fileName the fileName to set
	 */
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	/**
	 * @return the staticList
	 */
	public static List<Receiver> getStaticList() {
		return staticList;
	}

	/**
	 * @param staticList the staticList to set
	 */
	public static void setStaticList(ArrayList<Receiver> staticList) {
		UDPSender.staticList = staticList;
	}
}
