/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.ui.fragment;

import android.app.DialogFragment;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.ui.theme.ThemeHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FolderPickFragment extends DialogFragment {
    private TextView statusPath;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    boolean hasParent = false;

    private FolderPickListener listener;
    private File currentPath;
    private List<File> directories;
    private FrameLayout okButton;
    private TextView okButtonIcon;

    public static FolderPickFragment newInstance(FolderPickListener listener, File startingPath) {
        FolderPickFragment fragment = new FolderPickFragment();
        fragment.listener = listener;
        fragment.currentPath = startingPath;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (listener == null || currentPath == null) {
            dismiss();
        }

        setStyle(STYLE_NO_TITLE, 0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        if (listener == null || currentPath == null) {
            return null;
        }

        View container = inflater.inflate(R.layout.fragment_folder_pick, parent);

        statusPath = (TextView) container.findViewById(R.id.folder_status);
        listView = (ListView) container.findViewById(R.id.folder_list);
        okButton = (FrameLayout) container.findViewById(R.id.pick_ok);
        okButtonIcon = (TextView) container.findViewById(R.id.pick_ok_icon);

        container.findViewById(R.id.pick_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        okButton.findViewById(R.id.pick_ok).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.folderPicked(currentPath);
                dismiss();
            }
        });

        if (!ThemeHelper.getInstance().getTheme().isLightTheme) {
            ((TextView) container.findViewById(R.id.pick_back_icon)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_arrow_back_white_24dp, 0, 0, 0);
            ((TextView) container.findViewById(R.id.pick_ok_icon)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_done_white_24dp, 0, 0, 0);
        }

        adapter = new ArrayAdapter<String>(inflater.getContext(), 0) {
            @Override
            public View getView(final int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_1, null);
                }
                TextView text = (TextView) convertView;

                String name = getItem(position);

                text.setText(name);

                text.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (hasParent) {
                            if (position == 0) {
                                File parent = currentPath.getParentFile();
                                moveTo(parent);
                            } else if (position > 0 && position <= directories.size()) {
                                File dir = directories.get(position - 1);
                                moveTo(dir);
                            }
                        } else {
                            if (position >= 0 && position < directories.size()) {
                                File dir = directories.get(position);
                                moveTo(dir);
                            }
                        }
                    }
                });

                return text;
            }
        };

        listView.setAdapter(adapter);

        if (currentPath == null || !currentPath.exists()) {
            currentPath = Environment.getExternalStorageDirectory();
        }

        moveTo(currentPath);

        return container;
    }

    private boolean validPath(File path) {
        return path != null && path.isDirectory() && path.canRead() && path.canWrite();
    }

    private void moveTo(File path) {
        if (path != null && path.isDirectory()) {
            File[] listFiles = path.listFiles();
            if (listFiles != null) {
                currentPath = path;
                statusPath.setText(currentPath.getAbsolutePath());
                List<File> dirs = new ArrayList<>();
                for (File file : path.listFiles()) {
                    if (file.isDirectory()) {
                        dirs.add(file);
                    }
                }

                setDirs(dirs);
            }
        }

        validState();
    }

    private void validState() {
        if (validPath(currentPath)) {
            okButton.setEnabled(true);
            okButtonIcon.setEnabled(true);
        } else {
            okButton.setEnabled(false);
            okButtonIcon.setEnabled(false);
        }
    }

    private void setDirs(List<File> dirs) {
        directories = dirs;
        adapter.clear();

        if (currentPath.getParent() != null) {
            adapter.add("..");
            hasParent = true;
        } else {
            hasParent = false;
        }
        for (File file : dirs) {
            adapter.add(file.getName());
        }
        adapter.notifyDataSetChanged();
    }

    public interface FolderPickListener {
        void folderPicked(File path);
    }
}
