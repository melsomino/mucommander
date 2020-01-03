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

package com.mucommander.ui.main;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.MalformedURLException;
import java.util.*;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;

import com.mucommander.adb.AndroidMenu;
import com.mucommander.adb.AdbUtils;
import com.mucommander.bonjour.BonjourDirectory;
import com.mucommander.utils.FileIconsCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mucommander.bonjour.BonjourMenu;
import com.mucommander.bonjour.BonjourService;
import com.mucommander.bookmark.Bookmark;
import com.mucommander.bookmark.BookmarkListener;
import com.mucommander.bookmark.BookmarkManager;
import com.mucommander.commons.conf.ConfigurationEvent;
import com.mucommander.commons.conf.ConfigurationListener;
import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.FileFactory;
import com.mucommander.commons.file.FileProtocols;
import com.mucommander.commons.file.FileURL;
import com.mucommander.commons.file.filter.PathFilter;
import com.mucommander.commons.file.filter.RegexpPathFilter;
import com.mucommander.commons.file.impl.local.LocalFile;
import com.mucommander.commons.runtime.OsFamily;
import com.mucommander.conf.MuConfigurations;
import com.mucommander.conf.MuPreference;
import com.mucommander.conf.MuPreferences;
import com.mucommander.utils.text.Translator;
import com.mucommander.ui.action.MuAction;
import com.mucommander.ui.action.impl.OpenLocationAction;
import com.mucommander.ui.button.PopupButton;
import com.mucommander.ui.dialog.server.FTPPanel;
import com.mucommander.ui.dialog.server.HTTPPanel;
import com.mucommander.ui.dialog.server.NFSPanel;
import com.mucommander.ui.dialog.server.SFTPPanel;
import com.mucommander.ui.dialog.server.SMBPanel;
import com.mucommander.ui.dialog.server.ServerConnectDialog;
import com.mucommander.ui.dialog.server.ServerPanel;
import com.mucommander.ui.event.LocationEvent;
import com.mucommander.ui.event.LocationListener;
import com.mucommander.ui.helper.MnemonicHelper;
import com.mucommander.ui.icon.CustomFileIconProvider;
import com.mucommander.ui.icon.FileIcons;
import com.mucommander.ui.icon.IconManager;
import ru.trolsoft.ui.TMenuSeparator;


/**
 * <code>DrivePopupButton</code> is a button which, when clicked, pops up a menu with a list of volumes items that be used
 * to change the current folder.
 *
 * @author Maxence Bernard
 */
public class DrivePopupButton extends PopupButton implements BookmarkListener, ConfigurationListener, LocationListener {
	private static Logger logger;
	
    /** FolderPanel instance that contains this button */
    private FolderPanel folderPanel;
	
    /** Current volumes */
    private static AbstractFile volumes[];

    /** static FileSystemView instance, has a (non-null) value only under Windows */
    private static FileSystemView fileSystemView;

    /** Caches extended drive names, has a (non-null) value only under Windows */
    private static Map<AbstractFile, String> extendedNameCache;
    
    /** Caches drive icons */
    private static Map<AbstractFile, Icon> iconCache = new HashMap<>();
    

    /** Filters out volumes from the list based on the exclude regexp defined in the configuration, null if the regexp
     * is not defined. */
    private static PathFilter volumeFilter;


    static {
        if (OsFamily.WINDOWS.isCurrent()) {
            fileSystemView = FileSystemView.getFileSystemView();
            extendedNameCache = new HashMap<>();
        }

        try {
            String excludeRegexp = MuConfigurations.getPreferences().getVariable(MuPreference.VOLUME_EXCLUDE_REGEXP);
            if (excludeRegexp != null) {
                volumeFilter = new RegexpPathFilter(excludeRegexp, true);
                volumeFilter.setInverted(true);
            }
        } catch(PatternSyntaxException e) {
            getLogger().info("Invalid regexp for conf variable " + MuPreferences.VOLUME_EXCLUDE_REGEXP, e);
        }

        // Initialize the volumes list
        volumes = getDisplayableVolumes();
    }


    /**
     * Creates a new <code>DrivePopupButton</code> which is to be added to the given FolderPanel.
     *
     * @param folderPanel the FolderPanel instance this button will be added to
     */
    DrivePopupButton(FolderPanel folderPanel) {
        this.folderPanel = folderPanel;
		
        // Listen to location events to update the button when the current folder changes
        folderPanel.getLocationManager().addLocationListener(this);

        // Listen to bookmark changes to update the button if a bookmark corresponding to the current folder
        // has been added/edited/removed
        BookmarkManager.addBookmarkListener(this);

        // Listen to configuration changes to update the button if the system file icons policy has changed
        MuConfigurations.addPreferencesListener(this);

        // Use new JButton decorations introduced in Mac OS X 10.5 (Leopard)
        //if (OsFamily.MAC_OS_X.isCurrent() && OsVersion.MAC_OS_X_10_5.isCurrentOrHigher()) {
            //setMargin(new Insets(6,8,6,8));
            //putClientProperty("JComponent.sizeVariant", "small");
            //putClientProperty("JComponent.sizeVariant", "large");
            //putClientProperty("JButton.buttonType", "textured");
        //}
    }


   /**
     * Updates the button's label and icon to reflect the current folder and match one of the current volumes:
     * <<ul>
     *	<li>If the specified folder corresponds to a bookmark, the bookmark's name will be displayed
     *	<li>If the specified folder corresponds to a local file, the enclosing volume's name will be displayed
     *	<li>If the specified folder corresponds to a remote file, the protocol's name will be displayed
     * </ul>
     * The button's icon will be the current folder's one.
     */
    private void updateButton() {
        AbstractFile currentFolder = folderPanel.getCurrentFolder();

        setText(buildLabel(currentFolder));
//        setToolTipText(newToolTip);
        // Set the folder icon based on the current system icons policy
        setIcon(FileIcons.getFileIcon(currentFolder));
    }

    private String buildLabel(AbstractFile currentFolder) {
        String currentPath = currentFolder != null ? currentFolder.getAbsolutePath() : null;
        FileURL currentURL = currentFolder != null ? currentFolder.getURL() : null;

//        String newToolTip = null;

        // First tries to find a bookmark matching the specified folder
        List<Bookmark> bookmarks = BookmarkManager.getBookmarks();

        String newLabel = null;
        for (Bookmark b : bookmarks) {
            if (currentPath != null && currentPath.equals(b.getLocation())) {
                // Note: if several bookmarks match current folder, the first one will be used
                newLabel = b.getName();
                break;
            }
        }
        if (newLabel != null) {
            return newLabel;
        }

        // If no bookmark matched current folder
        String protocol = currentURL != null ? currentURL.getScheme() : null;
        if (!FileProtocols.FILE.equals(protocol)) {
            // Remote file, use the protocol's name
            return protocol != null ? protocol.toUpperCase() : "";
        } else {
            // Local file, use volume's name

            // Patch for Windows UNC network paths (weakly characterized by having a host different from 'localhost'):
            // display 'SMB' which is the underlying protocol
            if (OsFamily.WINDOWS.isCurrent() && !FileURL.LOCALHOST.equals(currentURL.getHost())) {
                return "SMB";
            } else {
                // getCanonicalPath() must be avoided under Windows for the following reasons:
                // a) it is not necessary, Windows doesn't have symlinks
                // b) it triggers the dreaded 'No disk in drive' error popup dialog.
                // c) when network drives are present but not mounted (e.g. X:\ mapped onto an SMB share),
                // getCanonicalPath which is I/O bound will take a looooong time to execute

                int bestIndex = getBestIndex(getVolumePath(currentFolder));
                return volumes[bestIndex].getName();

                // Not used because the call to FileSystemView is slow
//                    if(fileSystemView!=null)
//                        newToolTip = getWindowsExtendedDriveName(volumes[bestIndex]);
            }
        }
    }

    private int getBestIndex(String currentPath) {
        int bestLength = -1;
        int bestIndex = 0;
        for (int i = 0; i < volumes.length; i++) {
            String volumePath = getVolumePath(volumes[i]).toLowerCase();
            int len = volumePath.length();
            if (currentPath.startsWith(volumePath) && len > bestLength) {
                bestIndex = i;
                bestLength = len;
            }
        }
        return bestIndex;
    }

    @NotNull
    private String getVolumePath(AbstractFile file) {
        if (OsFamily.WINDOWS.isCurrent()) {
            return file.getAbsolutePath(false);
        } else {
            return file.getCanonicalPath(false);
        }
    }


    /**
     * Returns the extended name of the given local file, e.g. "Local Disk (C:)" for C:\. The returned value is
     * interesting only under Windows. This method is I/O bound and very slow so it should not be called from the main
     * event thread.
     *
     * @param localFile the file for which to return the extended name
     * @return the extended name of the given local file
     */
    private static String getExtendedDriveName(AbstractFile localFile) {
        // Note: fileSystemView.getSystemDisplayName(java.io.File) is unfortunately very very slow
        String name = fileSystemView.getSystemDisplayName((java.io.File)localFile.getUnderlyingFileObject());

        if (name == null || name.isEmpty()) {   // This happens for CD/DVD drives when they don't contain any disc
            return localFile.getName();
        }

        return name;
    }


    /**
     * Returns the list of volumes to be displayed in the popup menu.
     *
     * <p>The raw list of volumes is fetched using {@link LocalFile#getVolumes()} and then
     * filtered using the regexp defined in the {@link MuPreferences#VOLUME_EXCLUDE_REGEXP} configuration variable
     * (if defined).
     *
     * @return the list of volumes to be displayed in the popup menu
     */
    private static AbstractFile[] getDisplayableVolumes() {
        AbstractFile[] volumes = LocalFile.getVolumes();

        if (volumeFilter != null) {
            return volumeFilter.filter(volumes);
        }

        return volumes;
    }


    ////////////////////////////////
    // PopupButton implementation //
    ////////////////////////////////

    @Override
    public JPopupMenu getPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        // Update the list of volumes in case new ones were mounted
        volumes = getDisplayableVolumes();

        // Add volumes
        final MainFrame mainFrame = folderPanel.getMainFrame();

        MnemonicHelper mnemonicHelper = new MnemonicHelper();   // Provides mnemonics and ensures uniqueness

        addVolumes(popupMenu, mainFrame, mnemonicHelper);
        popupMenu.add(new TMenuSeparator());

        addBookmarks(popupMenu, mainFrame, mnemonicHelper);
        popupMenu.add(new TMenuSeparator());

        // Add 'Network shares' shortcut
        if (FileFactory.isRegisteredProtocol(FileProtocols.SMB)) {
            MuAction action = new CustomOpenLocationAction(mainFrame, new Bookmark(Translator.get("drive_popup.network_shares"), "smb:///", null));
            action.setIcon(IconManager.getIcon(IconManager.IconSet.FILE, CustomFileIconProvider.NETWORK_ICON_NAME));
            setMnemonic(popupMenu.add(action), mnemonicHelper);
        }

        if (BonjourDirectory.isActive()) {
            // Add Bonjour services menu
            setMnemonic(popupMenu.add(new BonjourMenu() {
                @Override
                public MuAction getMenuItemAction(BonjourService bs) {
                    return new CustomOpenLocationAction(mainFrame, bs);
                }
            }), mnemonicHelper);
        }

        addAdbDevices(popupMenu, mainFrame, mnemonicHelper);
        popupMenu.add(new TMenuSeparator());

        // Add 'connect to server' shortcuts
        setMnemonic(popupMenu.add(new ServerConnectAction("SMB...", SMBPanel.class)), mnemonicHelper);
        setMnemonic(popupMenu.add(new ServerConnectAction("FTP...", FTPPanel.class)), mnemonicHelper);
        setMnemonic(popupMenu.add(new ServerConnectAction("SFTP...", SFTPPanel.class)), mnemonicHelper);
        setMnemonic(popupMenu.add(new ServerConnectAction("HTTP...", HTTPPanel.class)), mnemonicHelper);
        setMnemonic(popupMenu.add(new ServerConnectAction("NFS...", NFSPanel.class)), mnemonicHelper);

        return popupMenu;
    }


    private void addVolumes(JPopupMenu popupMenu, MainFrame mainFrame, MnemonicHelper mnemonicHelper) {
        boolean useExtendedDriveNames = fileSystemView != null;
        List<JMenuItem> itemsV = new ArrayList<>();

        int nbVolumes = volumes.length;
        for (int i = 0; i < nbVolumes; i++) {
            MuAction action = new CustomOpenLocationAction(mainFrame, volumes[i]);
            String volumeName = volumes[i].getName();

            // If several volumes have the same filename, use the volume's path for the action's label instead of the
            // volume's path, to disambiguate
            for (int j = 0; j < nbVolumes; j++) {
                if (j != i && volumes[j].getName().equalsIgnoreCase(volumeName)) {
                    action.setLabel(volumes[i].getAbsolutePath());
                    break;
                }
            }

            JMenuItem item = popupMenu.add(action);
            setMnemonic(item, mnemonicHelper);

            // Set icon from cache
            Icon icon = iconCache.get(volumes[i]);
            if (icon != null) {
                item.setIcon(icon);
            }

            if (useExtendedDriveNames) {
                // Use the last known value (if any) while we update it in a separate thread
                String previousExtendedName = extendedNameCache.get(volumes[i]);
                if (previousExtendedName != null) {
                    item.setText(previousExtendedName);
                }

            }
            itemsV.add(item);   // JMenu offers no way to retrieve a particular JMenuItem, so we have to keep them
        }

        new RefreshDriveNamesAndIcons(popupMenu, itemsV).start();
    }

    private void addBookmarks(JPopupMenu popupMenu, MainFrame mainFrame, MnemonicHelper mnemonicHelper) {
        // Add bookmarks
        List<Bookmark> bookmarks = BookmarkManager.getBookmarks();
        if (!bookmarks.isEmpty()) {
            addBookmarksGroup(popupMenu, mainFrame, mnemonicHelper, bookmarks, null);
        } else {
            // No bookmark : add a disabled menu item saying there is no bookmark
            popupMenu.add(Translator.get("bookmarks_menu.no_bookmark")).setEnabled(false);
        }
    }

    private void addBookmarksGroup(JComponent parentMenu, MainFrame mainFrame, MnemonicHelper mnemonicHelper,
                                   List<Bookmark> bookmarks, String parent) {
        for (Bookmark b : bookmarks) {
            if ((b.getParent() == null && parent == null) || (parent != null && parent.equals(b.getParent()))) {
                if (b.getName().equals(BookmarkManager.BOOKMARKS_SEPARATOR) && b.getLocation().isEmpty()) {
                    parentMenu.add(new TMenuSeparator());
                    continue;
                }
                if (b.getLocation().isEmpty()) {
                    JMenu groupMenu = new JMenu(b.getName());
                    parentMenu.add(groupMenu);
                    addBookmarksGroup(groupMenu, mainFrame, mnemonicHelper, bookmarks, b.getName());
                    setMnemonic(groupMenu, mnemonicHelper);
                } else {
                    JMenuItem item = createBookmarkMenuItem(parentMenu, mainFrame, b);
                    setMnemonic(item, mnemonicHelper);
                }
            }
        }
    }

    private JMenuItem createBookmarkMenuItem(JComponent parentMenu, MainFrame mainFrame, Bookmark b) {
        JMenuItem item;
        if (parentMenu instanceof JPopupMenu) {
            item = ((JPopupMenu)parentMenu).add(new CustomOpenLocationAction(mainFrame, b));
        } else {
            item = ((JMenu)parentMenu).add(new CustomOpenLocationAction(mainFrame, b));
        }
        //JMenuItem item = popupMenu.add(new CustomOpenLocationAction(mainFrame, b));
        String location = b.getLocation();
        if (!location.contains("://")) {
            AbstractFile file = FileFactory.getFile(location);
            if (file != null) {
                Icon icon = FileIconsCache.getInstance().getIcon(file);
                if (icon != null) {
                    item.setIcon(icon);
                }
//                        Image image = FileIconsCache.getInstance().getImageIcon(file);
//                        if (image != null) {
//                            item.setIcon(new ImageIcon(image));
//                        }
            }
        } else if (location.startsWith("ftp://") || location.startsWith("sftp://") || location.startsWith("http://")) {
            item.setIcon(IconManager.getIcon(IconManager.IconSet.FILE, CustomFileIconProvider.NETWORK_ICON_NAME));
        } else if (location.startsWith("adb://")) {
            item.setIcon(IconManager.getIcon(IconManager.IconSet.FILE, CustomFileIconProvider.ANDROID_ICON_NAME));
        }
        return item;
    }


    private void addAdbDevices(JPopupMenu popupMenu, MainFrame mainFrame, MnemonicHelper mnemonicHelper) {
        if (AdbUtils.checkAdb()) {
            setMnemonic(popupMenu.add(new AndroidMenu() {
                @Override
                public MuAction getMenuItemAction(String deviceSerial) {
                    FileURL url = getDeviceURL(deviceSerial);
                    return new CustomOpenLocationAction(mainFrame, url);
                }

                @Nullable
                private FileURL getDeviceURL(String deviceSerial) {
                    try {
                        return FileURL.getFileURL("adb://" + deviceSerial);
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            }), mnemonicHelper);
        }
    }



    /**
     *  Calls to getExtendedDriveName(String) are very slow, so they are performed in a separate thread so as
     *  to not lock the main even thread. The popup menu gets first displayed with the short drive names, and
     * then refreshed with the extended names as they are retrieved.        
     */
    private class RefreshDriveNamesAndIcons extends Thread {
        
        private JPopupMenu popupMenu;
        private List<JMenuItem> items;

        RefreshDriveNamesAndIcons(JPopupMenu popupMenu, List<JMenuItem> items) {
            super("RefreshDriveNamesAndIcons");
            this.popupMenu = popupMenu;
            this.items = items;
        }
        
        @Override
        public void run() {
            final boolean useExtendedDriveNames = fileSystemView != null;
            for (int i = 0; i < items.size(); i++) {
                final JMenuItem item = items.get(i);
                final String extendedName = getExtendedDriverName(useExtendedDriveNames, volumes[i]);
                final Icon icon = getIcon(volumes[i]);

                SwingUtilities.invokeLater(() -> {
                    if (useExtendedDriveNames) {
                        item.setText(extendedName);
                    }
                    if (icon != null) {
                        item.setIcon(icon);
                    }
                });
                
            }

            // Re-calculate the popup menu's dimensions
            SwingUtilities.invokeLater(() -> {
                popupMenu.invalidate();
                popupMenu.pack();
            });
        }

        @Nullable
        private Icon getIcon(AbstractFile file) {
            // Set system icon for volumes, only if system icons are available on the current platform
            final Icon icon = FileIcons.hasProperSystemIcons() ? FileIcons.getSystemFileIcon(file) : null;
            if (icon != null) {
                iconCache.put(file, icon);
            }
            return icon;
        }

        @Nullable
        private String getExtendedDriverName(boolean useExtendedDriveNames, AbstractFile file) {
            if (useExtendedDriveNames) {
                // Under Windows, show the extended drive name (e.g. "Local Disk (C:)" instead of just "C:") but use
                // the simple drive name for the mnemonic (i.e. 'C' instead of 'L').
                String extendedName = getExtendedDriveName(file);

                // Keep the extended name for later (see above)
                extendedNameCache.put(file, extendedName);
                return extendedName;
            }
            return null;
        }

    }


    /**
     * Convenience method that sets a mnemonic to the given JMenuItem, using the specified MnemonicHelper.
     *
     * @param menuItem the menu item for which to set a mnemonic
     * @param mnemonicHelper the MnemonicHelper instance to be used to determine the mnemonic's character.
     */
    private void setMnemonic(JMenuItem menuItem, MnemonicHelper mnemonicHelper) {
        menuItem.setMnemonic(mnemonicHelper.getMnemonic(menuItem.getText()));
    }


    //////////////////////////////
    // BookmarkListener methods //
    //////////////////////////////
	
    public void bookmarksChanged() {
        // Refresh label in case a bookmark with the current location was changed
        updateButton();
    }


    ///////////////////////////////////
    // ConfigurationListener methods //
    ///////////////////////////////////

    /**
     * Listens to certain configuration variables.
     */
    public void configurationChanged(ConfigurationEvent event) {
        String var = event.getVariable();

        // Update the button's icon if the system file icons policy has changed
        if (var.equals(MuPreferences.USE_SYSTEM_FILE_ICONS)) {
            updateButton();
        }
    }


    ////////////////////////
    // Overridden methods //
    ////////////////////////

    @Override
    public Dimension getPreferredSize() {
        // Limit button's maximum width to something reasonable and leave enough space for location field,
        // as bookmarks name can be as long as users want them to be.
        // Note: would be better to use JButton.setMaximumSize() but it doesn't seem to work
        Dimension d = super.getPreferredSize();
        if (d.width > 160) {
            d.width = 160;
        }
        return d;
    }


    ///////////////////
    // Inner classes //
    ///////////////////

    /**
     * This action pops up {@link com.mucommander.ui.dialog.server.ServerConnectDialog} for a specified
     * protocol.
     */
    private class ServerConnectAction extends AbstractAction {
        private Class<? extends ServerPanel> serverPanelClass;

        private ServerConnectAction(String label, Class<? extends ServerPanel> serverPanelClass) {
            super(label);
            this.serverPanelClass = serverPanelClass;
        }

        public void actionPerformed(ActionEvent actionEvent) {
            new ServerConnectDialog(folderPanel, serverPanelClass).showDialog();
        }
    }


    /**
     * This modified {@link OpenLocationAction} changes the current folder on the {@link FolderPanel} that contains
     * this button, instead of the currently active {@link FolderPanel}.  
     */
    private class CustomOpenLocationAction extends OpenLocationAction {

        CustomOpenLocationAction(MainFrame mainFrame, Bookmark bookmark) {
            super(mainFrame, new HashMap<>(), bookmark);
        }

        CustomOpenLocationAction(MainFrame mainFrame, AbstractFile file) {
            super(mainFrame, new HashMap<>(), file);
        }

        CustomOpenLocationAction(MainFrame mainFrame, BonjourService bs) {
            super(mainFrame, new HashMap<>(), bs);
        }

        CustomOpenLocationAction(MainFrame mainFrame, FileURL url) {
            super(mainFrame, new HashMap<>(), url);
        }

        ////////////////////////
        // Overridden methods //
        ////////////////////////

        @Override
        protected FolderPanel getFolderPanel() {
            return folderPanel;
        }
    }

	/**********************************
	 * LocationListener Implementation
	 **********************************/
	
	public void locationChanged(LocationEvent e) {
        // Update the button's label to reflect the new current folder
        updateButton();
    }
    
	public void locationChanging(LocationEvent locationEvent) { }

	public void locationCancelled(LocationEvent locationEvent) { }

	public void locationFailed(LocationEvent locationEvent) {}

	private static Logger getLogger() {
        if (logger == null) {
            logger = LoggerFactory.getLogger(DrivePopupButton.class);
        }
        return logger;
    }
}
