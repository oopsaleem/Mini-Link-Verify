package com.kgwb.model;

public class MiniLinkDeviceTmprWrapper {
    private String siteId;
    private String ipAddress;
    private String comment;
    public MiniLinkDeviceTmprWrapper(String siteId,
                                     String ipAddress,
                                     String comment) {
        this.siteId = siteId;
        this.ipAddress = ipAddress;
        this.comment = comment;
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
}
