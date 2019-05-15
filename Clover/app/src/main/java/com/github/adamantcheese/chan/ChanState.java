/*
 * Clover4 - *chan browser https://github.com/Adamantcheese/Clover4/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan;

import android.os.Parcel;
import android.os.Parcelable;

import com.github.adamantcheese.chan.core.model.orm.Loadable;

public class ChanState implements Parcelable {
    public Loadable board;
    public Loadable thread;

    public ChanState(Loadable board, Loadable thread) {
        this.board = board;
        this.thread = thread;
    }

    public ChanState(Parcel parcel) {
        board = Loadable.readFromParcel(parcel);
        thread = Loadable.readFromParcel(parcel);
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
