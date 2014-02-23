package org.floens.chan.utils;

import org.floens.chan.ChanApplication;

public class ChanPreferences {
	public static boolean getOpenLinkConfirmation() {
		return ChanApplication.getPreferences().getBoolean("preference_open_link_confirmation", true);
	}
	
	public static String getDefaultName() {
		return ChanApplication.getPreferences().getString("preference_default_name", "");
	}
	
	public static String getDefaultEmail() {
		return ChanApplication.getPreferences().getString("preference_default_email", "");
	}
	
	public static boolean getDeveloper() {
		return ChanApplication.getPreferences().getBoolean("preference_developer", false);
	}
	
	public static void setDeveloper(boolean developer) {
		ChanApplication.getPreferences().edit().putBoolean("preference_developer", developer).commit();
	}
}
