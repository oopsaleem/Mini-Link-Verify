package com.kgwb.model;

public class MiniLinkDeviceVerifyWrapper {
    private String siteId;
    private String ipAddress;
    private String comment;
    private String bridge_basics;
    private String scheduler_profile10;
    private String interface_ethernet_status;

    public MiniLinkDeviceVerifyWrapper(String siteId,
                                       String ipAddress,
                                       String comment,
                                       String bridge_basics,
                                       String scheduler_profile10,
                                       String interface_ethernet_status
    ) {
        this.siteId = siteId;
        this.ipAddress = ipAddress;
        this.comment = comment;
        this.bridge_basics = bridge_basics;
        this.scheduler_profile10 = scheduler_profile10;
        this.interface_ethernet_status = interface_ethernet_status;
    }

    public String getSiteId() {
        return siteId;
    }
    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }


    public String getComment() {
        return comment;
    }
    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getBridge_basics() {
        return bridge_basics;
    }

    public void setBridge_basics(String bridge_basics) {
        this.bridge_basics = bridge_basics;
    }

    public String getScheduler_profile10() {
        return scheduler_profile10;
    }

    public void setScheduler_profile10(String scheduler_profile10) {
        this.scheduler_profile10 = scheduler_profile10;
    }

    public String getInterface_ethernet_status() {
        return interface_ethernet_status;
    }

    public void setInterface_ethernet_status(String interface_ethernet_status) {
        this.interface_ethernet_status = interface_ethernet_status;
    }
}
