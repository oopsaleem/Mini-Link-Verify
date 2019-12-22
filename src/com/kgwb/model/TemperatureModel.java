package com.kgwb.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class TemperatureModel {
    private SimpleStringProperty siteId;
    private SimpleStringProperty ipAddress;
    private SimpleStringProperty comment;

    public TemperatureModel() {
    }

    public TemperatureModel(MiniLinkDeviceTmprWrapper data) {

        this.siteId = new SimpleStringProperty(data.getSiteId());
        this.ipAddress = new SimpleStringProperty(data.getIpAddress());
        this.comment = new SimpleStringProperty(data.getComment());
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
}
