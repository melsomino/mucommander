/*
 * This file is part of muCommander, http://www.mucommander.com
 * Copyright (C) 2002-2012 Maxence Bernard
 *
 * muCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * muCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mucommander.ui.action.impl;

import com.mucommander.bookmark.Bookmark;
import com.mucommander.bookmark.BookmarkManager;
import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.FileProtocols;
import com.mucommander.commons.file.FileURL;
import com.mucommander.commons.file.impl.local.LocalFile;
import com.mucommander.conf.MuConfigurations;
import com.mucommander.conf.MuPreference;
import com.mucommander.conf.MuPreferences;
import com.mucommander.desktop.DesktopManager;
import com.mucommander.job.TempExecJob;
import com.mucommander.utils.text.Translator;
import com.mucommander.ui.action.*;
import com.mucommander.ui.dialog.InformationDialog;
import com.mucommander.ui.dialog.file.ProgressDialog;
import com.mucommander.ui.main.FolderPanel;
import com.mucommander.ui.main.MainFrame;
import com.mucommander.ui.main.quicklist.RecentExecutedFilesQL;
import com.mucommander.ui.main.tabs.FileTableTabs;

import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This action 'opens' the currently selected file or folder in the active FileTable.
 * This means different things depending on the kind of file that is currently selected:
 * <ul>
 * <li>For browsable files (directory, archive...): shows file contents
 * <li>For local file that are not an archive or archive entry: executes file with native file associations
 * <li>For any other file type, remote or local: copies file to a temporary local file and executes it with native file associations
 * </ul>
 *
 * @author Maxence Bernard, Nicolas Rinaudo
 */
public class OpenAction extends MuAction {
    /**
     * Creates a new <code>OpenAction</code> with the specified parameters.
     * @param mainFrame  frame to which the action is attached.
     * @param properties action's properties.
     */
	OpenAction(MainFrame mainFrame, Map<String, Object> properties) {
        super(mainFrame, properties);
    }

    /**
     * Opens the specified file in the specified folder panel.
     * <p>
     * <code>file</code> will be opened using the following rules:
     * <ul>
     *   <li>
     *     If <code>file</code> is {@link com.mucommander.commons.file.AbstractFile#isBrowsable() browsable},
     *     it will be opened in <code>destination</code>.
     *   </li>
     *   <li>
     *     If <code>file</code> is local, it will be opened using its native associations.
     *   </li>
     *   <li>
     *     If <code>file</code> is remote, it will first be copied in a temporary local file and
     *     then opened using its native association.
     *   </li>
     * </ul>
	 *
     * @param file        file to open.
     * @param destination if <code>file</code> is browsable, folder panel in which to open the file.
     */
    protected void open(final AbstractFile file, FolderPanel destination) {
    	AbstractFile resolvedFile = resolveIfSymlink(file);
    	if (resolvedFile == null) {
    	    return;
        }

        if (resolvedFile.isBrowsable()) {				// Opens browsable files in the destination FolderPanel.
			resolvedFile = cdFollowsSymlinks() ? resolvedFile : file;
    		openBrowsable(resolvedFile, destination);
        } else if (resolvedFile.isLocalFile()) {		// Opens local files using their native associations.
			openLocalFile(resolvedFile);
		} else {			// Copies non-local file in a temporary local file and opens them using their native association.
            ProgressDialog progressDialog = new ProgressDialog(mainFrame, Translator.get("copy_dialog.copying"));
            TempExecJob job = new TempExecJob(progressDialog, mainFrame, resolvedFile);
            progressDialog.start(job);
        }
    }

	private void openLocalFile(AbstractFile file) {
		try {
			DesktopManager.open(file);
			RecentExecutedFilesQL.addFile(file);
		} catch (IOException e) {
			InformationDialog.showErrorDialog(mainFrame);
		}
	}

	AbstractFile resolveIfSymlink(AbstractFile file) {
        if (!file.isSymlink()) {
            return file;
        }
        AbstractFile resolvedFile = resolveSymlink(file);

        if (resolvedFile == null) {
            InformationDialog.showErrorDialog(mainFrame, Translator.get("cannot_open_cyclic_symlink"));
            return null;
        }
        return resolvedFile;
    }

    private void openBrowsable(AbstractFile file, FolderPanel destination) {
		if (BookmarkManager.isBookmark(file)) {
			openBookmark(file, destination);
		} else {
			FileTableTabs tabs = destination.getTabs();
			if (tabs.getCurrentTab().isLocked()) {
				tabs.add(file);
			} else {
				destination.tryChangeCurrentFolder(file);
			}
		}
	}

	private void openBookmark(AbstractFile file, FolderPanel destination) {
		Bookmark bookmark = BookmarkManager.getBookmark(file.getName());
		if (bookmark == null) {
			return;
		}
		String bookmarkLocation = bookmark.getLocation();
		try {
			FileURL bookmarkURL = FileURL.getFileURL(bookmarkLocation);
			FileTableTabs tabs = destination.getTabs();
			if (tabs.getCurrentTab().isLocked()) {
				tabs.add(bookmarkURL);
			} else {
				destination.tryChangeCurrentFolder(bookmarkURL);
			}
		} catch (MalformedURLException ignore) {}
	}

	private static boolean cdFollowsSymlinks() {
		return MuConfigurations.getPreferences().getVariable(MuPreference.CD_FOLLOWS_SYMLINKS, MuPreferences.DEFAULT_CD_FOLLOWS_SYMLINKS);
	}

    private AbstractFile resolveSymlink(AbstractFile symlink) {
    	return resolveSymlink(symlink, new HashSet<>());
    }

    private AbstractFile resolveSymlink(AbstractFile file, Set<AbstractFile> visitedFiles) {
    	if (visitedFiles.contains(file)) {
			return null;
		}
		visitedFiles.add(file);
    	return file.isSymlink() ? resolveSymlink(file.getCanonicalFile(), visitedFiles) : file;
    }

    /**
     * Opens the currently selected file in the active folder panel.
     */
    @Override
    public void performAction() {
		// Retrieves the currently selected file, aborts if none.
		// Note: a CachedFile instance is retrieved to avoid blocking the event thread.
		AbstractFile file  = mainFrame.getActiveTable().getSelectedFile(true, true);
        if (file != null) {
            open(file, mainFrame.getActivePanel());
		}
    }

	@Override
	public ActionDescriptor getDescriptor() {
		return new Descriptor();
	}


    public static final class Descriptor extends AbstractActionDescriptor {
    	public static final String ACTION_ID = "Open";
    	
		public String getId() {
			return ACTION_ID;
		}

		public ActionCategory getCategory() {
			return ActionCategory.NAVIGATION;
		}

		public KeyStroke getDefaultAltKeyStroke() {
			return null;
		}

		public KeyStroke getDefaultKeyStroke() {
			return KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
		}

		public MuAction createAction(MainFrame mainFrame, Map<String,Object> properties) {
			return new OpenAction(mainFrame, properties);
		}
    }
}
