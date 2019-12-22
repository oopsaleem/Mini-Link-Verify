package com.kgwb.model;

public class SlotTmprWrapper {
    private int slot;
    private int temp;
    private int high;
    private int exce;

    public SlotTmprWrapper(int slot, int temp, int high, int exce) {
        this.slot = slot;
        this.temp = temp;
        this.high = high;
        this.exce = exce;
    }

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public int getTemp() {
        return temp;
    }

    public void setTemp(int temp) {
        this.temp = temp;
    }

    public int getHigh() {
        return high;
    }

    public void setHigh(int high) {
        this.high = high;
    }

    public int getExce() {
        return exce;
    }

    public void setExce(int exce) {
        this.exce = exce;
    }
}
