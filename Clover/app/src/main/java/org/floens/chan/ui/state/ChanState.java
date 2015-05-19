package org.floens.chan.ui.state;

import android.os.Parcel;
import android.os.Parcelable;

import org.floens.chan.core.model.Loadable;

public class ChanState implements Parcelable {
    public Loadable board;
    public Loadable thread;

    public ChanState(Loadable board, Loadable thread) {
        this.board = board;
        this.thread = thread;
    }

    public ChanState(Parcel parcel) {
        board = new Loadable();
        board.readFromParcel(parcel);
        thread = new Loadable();
        thread.readFromParcel(parcel);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        board.writeToParcel(dest);
        thread.writeToParcel(dest);
    }

    public static final Parcelable.Creator<ChanState> CREATOR = new Parcelable.Creator<ChanState>() {
        @Override
        public ChanState createFromParcel(Parcel source) {
            return new ChanState(source);
        }

        @Override
        public ChanState[] newArray(int size) {
            return new ChanState[size];
        }
    };
}
