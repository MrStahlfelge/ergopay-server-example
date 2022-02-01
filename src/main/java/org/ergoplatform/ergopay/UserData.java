package org.ergoplatform.ergopay;

class UserData {
    private long lastActiveMs; // used to delete the data after some time
    public String p2pkAddress;

    public long getLastActiveMs() {
        return lastActiveMs;
    }

    public void setActiveNow() {
        this.lastActiveMs = System.currentTimeMillis();
    }
}
