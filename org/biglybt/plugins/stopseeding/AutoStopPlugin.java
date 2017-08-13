/*
 *******************************************************************************
 * Copyright (c) 2004 Chris Rose and AIMedia

 This file is part of AZAutoStop.

 AZAutoStop is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 AZAutoStop is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with AZAutoStop; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

 *
 * Contributors:
 *     Chris Rose
 *******************************************************************************/

package org.biglybt.plugins.stopseeding;

import java.util.ArrayList;
import java.util.List;

import org.biglybt.plugins.stopseeding.core.AutoStopConfiguration;
import org.biglybt.plugins.stopseeding.core.RatioWatcher;
import org.biglybt.plugins.stopseeding.ui.menu.InputDialog;
import org.eclipse.swt.widgets.Display;
import com.biglybt.pif.Plugin;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.logging.LoggerChannelListener;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.pif.ui.tables.TableContextMenuItem;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.ui.tables.TableRow;
import com.biglybt.pif.utils.LocaleUtilities;

/**
 * This is a plugin to allow Azureus users to set torrents to stop automatically
 * after a certain upload/download ratio has been reached. Inspired by my ISP
 * and their occasionally draconian abuse limiters.
 *
 * @author Chris Rose
 * @version 0.2
 */
public class AutoStopPlugin implements Plugin {

    /**
     *
     */
    public static class SingleDownloadListener {

        protected List<Download> getDownload (Object data) {
        	List<Download> result = new ArrayList<Download>();
        	
        	if ( data instanceof TableRow ){
        	
        		result.add((Download)((TableRow)data).getDataSource());
        		
        	}else if ( data instanceof TableRow[] ){
        		
        		for( TableRow row: (TableRow[])data ){
        			
               		result.add((Download)row.getDataSource());
        		}
        	}
        	
        	return( result );
        }
        
    	/*
        protected List<Download> getDownload (Object data) {
            List<Download> downloads = new ArrayList<Download> ();
            if (data.getClass ().isArray ()) {
                // : get the data source using internal methods; there's got to be a better way

                Method getDataSource;
                try {
                    getDataSource = data.getClass ().getComponentType ().getMethod ("getDataSource");
                }
                catch (NoSuchMethodException e) {
                    Logger.alert (ResourceConstants.ERROR_NO_DATASOURCE, e);
                    return null;
                }
                catch (Exception e) {
                    Logger.alert (ResourceConstants.ERROR_GENERAL, e);
                    return null;
                }
                int items = Array.getLength (data);
                for (int i = 0; i < items; i++) {
                    Object element = Array.get (data, i);
                    Object obj;
                    try {
                        obj = getDataSource.invoke (element);
                    }
                    catch (Exception e) {
                        Logger.alert (ResourceConstants.ERROR_GENERAL, e);
                        return null;
                    }
                    if (! (obj instanceof Download)) {
                        // : not one of our useful sorts of items; return now.
                        continue;
                    }
                    downloads.add ((Download) obj);
                }

                if (downloads.size () < 1) {
                    return null;
                }

                return downloads;
            }
            else {
                Object obj;
                try {
                    Method getDataSource = data.getClass ().getMethod ("getDataSource");
                    obj = getDataSource.invoke (data);
                }
                catch (NoSuchMethodException e) {
                    Logger.alert (ResourceConstants.ERROR_NO_DATASOURCE, e);
                    return null;
                }
                catch (Exception e) {
                    Logger.alert (ResourceConstants.ERROR_GENERAL, e);
                    return null;
                }
                if (! (obj instanceof Download)) {
                    // : not one of ours; we can return null, now
                    return null;
                }
                return Collections.singletonList ((Download) obj);
            }
        }
        */

        /**
         * @param downloads
         * @return
         */
        protected String getDownloadRatio (List<Download> downloads) {
            String ratio = null;
            for (Download download : downloads) {
                String currentRatio = download.getAttribute (AutoStopPlugin.plugin ().getAttribute ());

                if (ratio == null) { ratio = currentRatio; } // store the ratio in the buffer

                // : if the ratio has changed at all, then we'll work as with mixed ratios.
                if (! ratio.equals (currentRatio)) {
                    ratio = null;
                    break;
                }
            }
            return ratio;
        }
    }

    /**
     *
     */
    public static class RatioSelectorListener extends SingleDownloadListener implements MenuItemListener {

        /*
         * (non-Javadoc)
         *
         * @see com.biglybt.pif.ui.menus.MenuItemListener#selected(com.biglybt.pif.ui.menus.MenuItem,
         *      java.lang.Object)
         */
  //      @SuppressWarnings ("deprecation")
        public void selected (MenuItem menu, Object target) {
            List<Download> downloads = getDownload (target);

            // : do nothing if there were no downloads selected.
            if (downloads == null || downloads.size () == 0) { return; }

            // : prepare our utilities
            LocaleUtilities lu = AutoStopPlugin.plugin ().getPluginInterface ().getUtilities ().getLocaleUtilities ();
            Display display = Display.getDefault();

            // : if there is only one ratio for all selected downloads, default to it
            String ratio = getDownloadRatio (downloads);

            // : otherwise start with the default ratio.
            if (ratio == null) {
                ratio = AutoStopPlugin.plugin ().getConfiguration ().getGlobalDefaultRatio ();
            }

            // : prepare the input dialog with either the single name of the download or a string "# downloads"
            String nameString;
            if (downloads.size () == 1) {
                nameString = downloads.get (0).getName ();
            }
            else {
                nameString = String.format ("%d downloads", downloads.size ());
            }
            InputDialog dialog = new InputDialog (display, ratio, lu.getLocalisedMessageText (ResourceConstants.SET_CUSTOM_RATIO_TITLE), lu
                    .getLocalisedMessageText (ResourceConstants.SET_CUSTOM_RATIO_CONTENTS, new String[] { nameString }));

            dialog.getShell ().pack ();
            dialog.getShell ().open ();
            while (!dialog.getShell ().isDisposed ()) {
                if (!display.readAndDispatch ()) display.sleep ();
            }

            boolean result = dialog.getResult ();

            // : update the ratios.
            if (result) {
                ratio = dialog.getValue ();
                for (Download download : downloads) {
                    download.setAttribute (AutoStopPlugin.plugin ().getAttribute (), ratio);
                }
            }
        }

    }

    public static class MenuTextUpdater extends SingleDownloadListener implements MenuItemFillListener {

        public MenuTextUpdater () {
        }

        /*
         * (non-Javadoc)
         *
         * @see com.biglybt.pif.ui.menus.MenuItemFillListener#menuWillBeShown(com.biglybt.pif.ui.menus.MenuItem,
         *      java.lang.Object)
         */
        public void menuWillBeShown (MenuItem menu, Object data) {
            List<Download> downloads = getDownload (data);
            LocaleUtilities locale = AutoStopPlugin.plugin ().getPluginInterface ().getUtilities ().getLocaleUtilities ();

            if (downloads == null || downloads.size () == 0) {
                menu.setEnabled (false);
                menu.setText (locale.getLocalisedMessageText (ResourceConstants.SET_CUSTOM_RATIO_NO_SELECTION));

                return;
            }

            String ratio = getDownloadRatio (downloads);

            if (ratio == null) {
                menu.setEnabled (true);
                menu.setText (locale.getLocalisedMessageText (ResourceConstants.SET_CUSTOM_RATIO) + " "
                        + locale.getLocalisedMessageText (ResourceConstants.SET_CUSTOM_RATIO_NO_DISPLAY_RATIO));
            }
            else {
                menu.setEnabled (true);
                menu.setText (locale.getLocalisedMessageText (ResourceConstants.SET_CUSTOM_RATIO) + " "
                        + locale.getLocalisedMessageText (ResourceConstants.SET_CUSTOM_RATIO_DISPLAY_RATIO,
                                new String[] { ratio }));
            }

            // : cannot invoke on multiple downloads because the selected method doesn't work that way.
            if (downloads.size () != 1) {
                menu.setEnabled (false);
            }
        }

    }

    private PluginInterface       pluginInterface;

    private LocaleUtilities       lu;

    private AutoStopConfiguration configuration;

    private LoggerChannelListener listener;

    private BasicPluginViewModel  vm;

    private TableManager          tm;

    private TorrentAttribute      attribute;

    private static AutoStopPlugin instance;

    public static AutoStopPlugin plugin () {
        if (instance == null) { throw new RuntimeException ("No instance has been created yet."); }
        return instance;
    }

    /**
     * @return
     */
    public TorrentAttribute getAttribute () {
        return attribute;
    }

    public AutoStopPlugin () {
        if (instance != null) { throw new RuntimeException ("Cannot create more than one instance of the plugin."); }
        instance = this;
    }

    /**
     * This method is called when the plugin is loaded / initialized. In this
     * instance it manufactures the arrays for the string list parameters,
     * creates the config model and page (this will be getting more complex in
     * the future) and technically activates a timer. This last is not working
     * yet.
     *
     * @see com.biglybt.pif.Plugin#initialize(com.biglybt.pif.PluginInterface)
     * @param pluginInterface
     *            the interface that the plugin must use to communicate with
     *            Azureus
     */
    public void initialize (PluginInterface pluginInterface) {

        this.pluginInterface = pluginInterface;
        this.lu = pluginInterface.getUtilities ().getLocaleUtilities ();
        this.tm = pluginInterface.getUIManager ().getTableManager ();
        this.attribute = pluginInterface.getTorrentManager ().getPluginAttribute (ResourceConstants.ATTRIBUTE_NAME);

        // : Initialize the configuration engine.
        configuration = new AutoStopConfiguration (this);

        vm = pluginInterface.getUIManager ().createBasicPluginViewModel (lu.getLocalisedMessageText (ResourceConstants.PLUGIN_NAME));
        vm.getActivity ().setVisible (false);
        vm.getProgress ().setVisible (false);

        // : Configure the logger window
        if (configuration.isLoggingEnabled ()) {
            startLogger ();
        }
        else {
            stopLogger ();
        }

        MenuItemFillListener textAppender = new MenuTextUpdater ();

        // : create the per-torrent menu item
        TableContextMenuItem item = tm.addContextMenuItem (TableManager.TABLE_MYTORRENTS_COMPLETE, ResourceConstants.SET_CUSTOM_RATIO);
        item.addFillListener (textAppender);
        item.addListener (new RatioSelectorListener ());
        item = tm.addContextMenuItem (TableManager.TABLE_MYTORRENTS_INCOMPLETE, ResourceConstants.SET_CUSTOM_RATIO);
        item.addFillListener (textAppender);
        item.addListener (new RatioSelectorListener ());

        // : initialize the ratio listener
        pluginInterface.getDownloadManager ().addListener (RatioWatcher.getInstance ());

        Logger.info (ResourceConstants.PLUGIN_STARTUP);
    }

    /**
     * @return
     */
    public PluginInterface getPluginInterface () {
        return pluginInterface;
    }

    public AutoStopConfiguration getConfiguration () {
        return configuration;
    }

    public void startLogger () {
        // : Create the logger view model.

        Logger.getLogger ().addListener (getListener ());

        Logger.info (ResourceConstants.LOGGER_STARTUP);
    }

    private LoggerChannelListener getListener () {
        if (listener == null) {
            listener = new LoggerChannelListener () {

                public void messageLogged (int type, String content) {
                    vm.getLogArea ().appendText (content + "\n");
                }

                public void messageLogged (String str, Throwable error) {
                    vm.getLogArea ().appendText (str + "\n");
                    vm.getLogArea ().appendText (error.getLocalizedMessage () + "\n");
                }
            };
        }
        return listener;
    }

    public void stopLogger () {
        Logger.info (ResourceConstants.LOGGER_SHUTDOWN);
        Logger.getLogger ().removeListener (getListener ());
        listener = null;

        vm.getLogArea ().setEnabled (false);
    }
}