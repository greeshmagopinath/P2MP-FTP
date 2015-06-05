package com.p2mp.application;



import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Class UDPReceiver.
 *
 * 
 */
public class UDPReceiver {
	
	/** The socket. */
	static DatagramSocket socket;
	
	/** The expected packet. */
	AtomicInteger expectedPacket = new AtomicInteger(1);
	
	/** The is running. */
	boolean isRunning = true;
	
	/** The probability. */
	double probability=0.05;
	
	/** The received buffer. */
	byte[] receivedBuffer;
	
	int receiverPort;
	
	String filename;
	
	
	/**
	 * @return the filename
	 */
	public String getFilename() {
		return filename;
	}

	/**
	 * @param filename the filename to set
	 */
	public void setFilename(String filename) {
		this.filename = filename;
	}

	/**
	 * @return the receiverPort
	 */
	public int getReceiverPort() {
		return receiverPort;
	}

	/**
	 * @param receiverPort the receiverPort to set
	 */
	public void setReceiverPort(int receiverPort) {
		this.receiverPort = receiverPort;
	}
	
	/**
	 * @return the probability
	 */
	public double getProbability() {
		return probability;
	}

	/**
	 * @param probability the probability to set
	 */
	public void setProbability(double probability) {
		this.probability = probability;
	}


	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {
		int port = Integer.parseInt(args[0]);
		String file = args[1];
		double p = Double.parseDouble(args[2]);
		UDPReceiver udpRec = new UDPReceiver();
		udpRec.setReceiverPort(port);
		udpRec.setFilename(file);
		udpRec.setProbability(p);
		udpRec.runReceiver();
	}

	/**
	 * Run receiver.
	 */
	private void runReceiver() {
		DatagramPacket inPacket = null;
		byte[] inBuffer = null;
		try {
			inBuffer = new byte[2048];
			inPacket = new DatagramPacket(inBuffer, inBuffer.length);
			socket = new DatagramSocket(receiverPort);
		} catch (SocketException e) {
			System.out.println("Could not open the socket: \n" + e.getMessage());
		}

		while (isRunning) {
			try {
				socket.receive(inPacket);
				DataReceiver dataReceiever = new DataReceiver(inPacket);
				dataReceiever.start();
			} catch (Exception e) {
				System.out.println("Error Processing packets at the receiver\n");
			}
		}
		System.exit(1);
	}

	/**
	 * The Class DataReceiver.
	 */
	private class DataReceiver extends Thread {
		
		/** The data received. */
		byte[] dataReceived;
		
		/** The address. */
		byte[] address = new byte[4];
		
		/** The port. */
		int port;
		

		/**
		 * Instantiates a new data receiver.
		 *
		 * @param packetRecieved the packet received
		 */
		public DataReceiver(DatagramPacket packetRecieved) {
			address = packetRecieved.getAddress().getAddress();
			port = packetRecieved.getPort();
			dataReceived = new byte[packetRecieved.getLength()];
			System.arraycopy(packetRecieved.getData(), 0, dataReceived, 0,dataReceived.length);
		}

		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		public void run() {
			double randomNumber = Math.random();
			int dataPacketIndicator = ((dataReceived[6] & 0xFF) << 8) | (dataReceived[7] & 0xFF);
			if (dataPacketIndicator == 0) {
				byte[] ackpacketBytes = new byte[8];
				//expectedPacket = expectedPacket + 1;
				expectedPacket.addAndGet(1);
				ackpacketBytes[0] = (byte) ((expectedPacket.get() >> 24) & 0xFF);
				ackpacketBytes[1] = (byte) ((expectedPacket.get() >> 16) & 0xFF);
				ackpacketBytes[2] = (byte) ((expectedPacket.get() >> 8) & 0xFF);
				ackpacketBytes[3] = (byte) (expectedPacket.get() & 0xFF);
				ackpacketBytes[4]= 0;
				ackpacketBytes[5]= 0;
				ackpacketBytes[6] = -86;
				ackpacketBytes[7] = -86;
				InetAddress ipAddress = null;
				try {
					ipAddress = InetAddress.getByAddress(address);
				} catch (UnknownHostException e) {
				 System.out.println("Error in conversion of address");
				}
				DatagramPacket ackPacket = new DatagramPacket(ackpacketBytes, 0, ackpacketBytes.length,ipAddress, port);
				try {
					//System.out.println("sending ack for last packet" + expectedPacket.get());
					socket.send(ackPacket);
				} catch (IOException e) {
				 System.out.println("Socket not found.");
				}
               System.out.println("End of file reached.Saving the contents to disk");
               saveFileToDiskFromBuffer();
               System.out.println("Shutting down Receiver");
               isRunning = false;
               System.exit(1);
			} else if (checksum(dataReceived) && randomNumber > probability) {
				// Retrieve the sequence number
				int value = ((dataReceived[0] & 0xFF) << 24)
						| ((dataReceived[1] & 0xFF) << 16)
						| ((dataReceived[2] & 0xFF) << 8)
						| (dataReceived[3] & 0xFF);
				//System.out.println("received ack for" + value);
				synchronized (expectedPacket) {
				if (value == expectedPacket.get() && dataPacketIndicator == 21845) {
					//System.out.println("Received the correct sequence" + value);
					bufferReceivedData(dataReceived);
					//expectedPacket = expectedPacket + 1;
					expectedPacket.getAndAdd(1);
					sendAcknowledgementForRecievedPacket(expectedPacket.get());
				} else {
					if (value == expectedPacket.get()-1) {
					//System.out.println("Sending duplicate ack for" + expectedPacket.get());
					sendAcknowledgementForRecievedPacket(expectedPacket.get());}
				}
				}
			} else {
				int sequenceNumber = ((dataReceived[0] & 0xFF) << 24)
						| ((dataReceived[1] & 0xFF) << 16)
						| ((dataReceived[2] & 0xFF) << 8)
						| (dataReceived[3] & 0xFF);
				System.out.println("Packet Loss, Sequence Number =" + sequenceNumber);
			}
		}

		/**
		 * Save file to disk from buffer.
		 */
		private void saveFileToDiskFromBuffer() {
			File FileToBeSaved = new File(filename);
			FileOutputStream fos;
			try {
				fos = new FileOutputStream(FileToBeSaved);
			    fos.write(receivedBuffer);
		        fos.flush();
		        fos.close();
		   } catch (FileNotFoundException e) {
				System.out.println("Problem");
			} catch (IOException e) {
				System.out.println("Problem");
			}
	    }

		/**
		 * Buffer received data.
		 *
		 * @param dataReceived the data received
		 */
		private void bufferReceivedData(byte[] dataReceived) {
			if (receivedBuffer ==  null) {
				receivedBuffer = new byte[dataReceived.length-8];
				System.arraycopy(dataReceived, 8, receivedBuffer, 0, dataReceived.length-8);
				String message=new String(receivedBuffer);
			} else 
			{
			byte[] newarray = new byte[dataReceived.length-8 + receivedBuffer.length];
			System.arraycopy(receivedBuffer, 0, newarray, 0, receivedBuffer.length);
			System.arraycopy(dataReceived, 8, newarray, receivedBuffer.length, dataReceived.length-8);
			receivedBuffer = newarray;
			}
			}

		/**
		 * Send acknowledgement for recieved packet.
		 *
		 * @param ackpacket the ackpacket
		 */
		private void sendAcknowledgementForRecievedPacket(int ackpacket) {
			//System.out.println("received"+ ackpacket);
			
			byte[] ackpacketBytes = new byte[8];
			ackpacketBytes[0] = (byte) ((ackpacket >> 24) & 0xFF);
			ackpacketBytes[1] = (byte) ((ackpacket >> 16) & 0xFF);
			ackpacketBytes[2] = (byte) ((ackpacket >> 8) & 0xFF);
			ackpacketBytes[3] = (byte) (ackpacket & 0xFF);
			ackpacketBytes[4]= 0;
			ackpacketBytes[5]= 0;
			ackpacketBytes[6] = -86;
			ackpacketBytes[7] = -86;
			InetAddress ipAddress = null;
			try {
				ipAddress = InetAddress.getByAddress(address);
			} catch (UnknownHostException e) {
			 System.out.println("Error in conversion of address");
			}
			DatagramPacket ackPacket = new DatagramPacket(ackpacketBytes, 0, ackpacketBytes.length,ipAddress, port);
			try {
				//System.out.println("Sending ack for packet:" + ackpacket);
				socket.send(ackPacket);
				
			} catch (IOException e) {
			 System.out.println("Socket not found.");
			}
		}

		/**
		 * Checksum.
		 *
		 * @return true, if successful
		 */
		private boolean checksum(byte[] dataReceived) {
			int sum;
			InternetChecksum checksum=new InternetChecksum();
			sum=(int) checksum.calculateChecksum(dataReceived);
			if (sum==0)
			 return true;
			else 
			 return false;
		}
	}
}