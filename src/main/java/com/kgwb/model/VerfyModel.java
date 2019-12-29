package com.kgwb.model;

import javafx.beans.property.SimpleStringProperty;

public class VerfyModel {
    private SimpleStringProperty siteId;
    private SimpleStringProperty ipAddress;
    private SimpleStringProperty comment;
    private SimpleStringProperty bridge_basics;
    private SimpleStringProperty scheduler_profile10;
    private SimpleStringProperty interface_ethernet_status;

    public VerfyModel() {
    }

    public VerfyModel(MiniLinkDeviceVerifyWrapper data) {

        this.siteId = new SimpleStringProperty(data.getSiteId());
        this.ipAddress = new SimpleStringProperty(data.getIpAddress());
        this.comment = new SimpleStringProperty(data.getComment());
        this.bridge_basics = new SimpleStringProperty(data.getBridge_basics());
        this.scheduler_profile10 = new SimpleStringProperty(data.getScheduler_profile10());
        this.interface_ethernet_status = new SimpleStringProperty(data.getInterface_ethernet_status());
    }

    public String getSiteId() {
        return siteId.get();
    }

    public SimpleStringProperty siteIdProperty() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId.set(siteId);
    }

    public String getIpAddress() {
        return ipAddress.get();
    }

    public SimpleStringProperty ipAddressProperty() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress.set(ipAddress);
    }

    public String getComment() {
        return comment.get();
    }

    public SimpleStringProperty commentProperty() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment.set(comment);
    }

    public String getBridge_basics() {
        return bridge_basics.get();
    }

    public SimpleStringProperty bridge_basicsProperty() {
        return bridge_basics;
    }

    public void setBridge_basics(String bridge_basics) {
        this.bridge_basics.set(bridge_basics);
    }

    public String getScheduler_profile10() {
        return scheduler_profile10.get();
    }

    public SimpleStringProperty scheduler_profile10Property() {
        return scheduler_profile10;
    }

    public void setScheduler_profile10(String scheduler_profile10) {
        this.scheduler_profile10.set(scheduler_profile10);
    }

    public String getInterface_ethernet_status() {
        return interface_ethernet_status.get();
    }

    public SimpleStringProperty interface_ethernet_statusProperty() {
        return interface_ethernet_status;
    }

    public void setInterface_ethernet_status(String interface_ethernet_status) {
        this.interface_ethernet_status.set(interface_ethernet_status);
    }
}
