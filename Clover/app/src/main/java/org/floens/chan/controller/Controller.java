package org.floens.chan.controller;

import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;

import org.floens.chan.ui.toolbar.NavigationItem;

public abstract class Controller {
    public Context context;
    public View view;

    public Controller stackSiblingController;
    public NavigationController navigationController;
    public NavigationItem navigationItem = new NavigationItem();

    public Controller(Context context) {
        this.context = context;
    }

    public void onCreate() {
    }

    public void onShow() {
    }

    public void onHide() {
    }

    public void onDestroy() {
    }

    public View inflateRes(int resId) {
        return LayoutInflater.from(context).inflate(resId, null);
    }

    public void onConfigurationChanged(Configuration newConfig) {
    }

    public boolean onBack() {
        return false;
    }
}
