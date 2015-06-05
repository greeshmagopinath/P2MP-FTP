package com.p2mp.application;

public class Receiver {
String ipAddress;
int port;
/**
 * @return the ipAddress
 */
public String getIpAddress() {
	return ipAddress;
}
/**
 * @param ipAddress the ipAddress to set
 */
public void setIpAddress(String ipAddress) {
	this.ipAddress = ipAddress;
}
/**
 * @return the port
 */
public int getPort() {
	return port;
}

public Receiver(String ipAddress, int port) {
	super();
	this.ipAddress = ipAddress;
	this.port = port;
}

public Receiver() {
	super();
}

/**
 * @param port the port to set
 */
public void setPort(int port) {
	this.port = port;
}

@Override
public int hashCode() {
	final int prime = 31;
	int result = 1;
	result = prime * result + ((ipAddress == null) ? 0 : ipAddress.hashCode());
	result = prime * result + port;
	return result;
}

@Override
public boolean equals(Object obj) {
	if (this == obj)
		return true;
	if (obj == null)
		return false;
	if (getClass() != obj.getClass())
		return false;
	Receiver other = (Receiver) obj;
	if (ipAddress == null) {
		if (other.ipAddress != null)
			return false;
	} else if (!ipAddress.equals(other.ipAddress))
		return false;
	if (port != other.port)
		return false;
	return true;
}
}
