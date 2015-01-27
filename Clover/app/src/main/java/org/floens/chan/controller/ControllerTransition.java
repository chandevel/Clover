package org.floens.chan.controller;

public abstract class ControllerTransition {
    private Callback callback;

    public Controller from;
    public Controller to;

    public abstract void perform();

    public void onCompleted() {
        this.callback.onControllerTransitionCompleted();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        public void onControllerTransitionCompleted();
    }
}
