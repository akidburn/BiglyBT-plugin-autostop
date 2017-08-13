package org.biglybt.plugins.stopseeding.core;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.biglybt.plugins.stopseeding.AutoStopPlugin;
import org.biglybt.plugins.stopseeding.Logger;
import org.biglybt.plugins.stopseeding.ResourceConstants;
import org.biglybt.plugins.stopseeding.core.AutoStopConfiguration.StopAction;

import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadException;
import com.biglybt.pif.download.DownloadListener;
import com.biglybt.pif.download.DownloadManagerListener;
//import com.biglybt.pif.download.DownloadPropertyEvent;
//import com.biglybt.pif.download.DownloadPropertyListener;
import com.biglybt.pif.download.DownloadRemovalVetoException;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.torrent.TorrentManager;
import com.biglybt.pif.utils.UTTimer;
import com.biglybt.pif.utils.UTTimerEvent;
import com.biglybt.pif.utils.UTTimerEventPerformer;

public class RatioWatcher implements DownloadListener, DownloadManagerListener, //DownloadPropertyListener,
        UTTimerEventPerformer {

    private static RatioWatcher         instance;

    private Set<Download>               managedDownloads;

    private UTTimer                     timer;

    private TorrentAttribute            attribute;

    private final AutoStopConfiguration configuration;

    private RatioWatcher() {
        managedDownloads = new HashSet<Download> ();
        attribute = AutoStopPlugin.plugin ().getPluginInterface ().getTorrentManager ().getPluginAttribute (
                ResourceConstants.ATTRIBUTE_NAME);
        configuration = AutoStopPlugin.plugin ().getConfiguration ();

        timer = AutoStopPlugin.plugin ().getPluginInterface ().getUtilities ().createTimer ("autostop.ratioWatcher",
                true);

        timer.addPeriodicEvent (AutoStopPlugin.plugin ().getConfiguration ().getScanInterval (), this);

    }

    public static RatioWatcher getInstance () {
        if (null == instance) {
            instance = new RatioWatcher ();
        }
        return instance;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.biglybt.pif.download.DownloadListener#stateChanged(com.biglybt.pif.download.Download,
     *      int, int)
     */
    public void stateChanged (Download download, int old_state, int new_state) {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     *
     * @see com.biglybt.pif.download.DownloadListener#positionChanged(com.biglybt.pif.download.Download,
     *      int, int)
     */
    public void positionChanged (Download download, int oldPosition, int newPosition) {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     *
     * @see com.biglybt.pif.download.DownloadManagerListener#downloadAdded(com.biglybt.pif.download.Download)
     */
    public void downloadAdded (Download download) {
        // Start managing this download

    //    download.addPropertyListener (this);
        download.addListener (this);

        TorrentManager tm = AutoStopPlugin.plugin ().getPluginInterface ().getTorrentManager ();
        TorrentAttribute ta = tm.getPluginAttribute (ResourceConstants.ATTRIBUTE_NAME);

        if (download.getAttribute (ta) == null) {
            download.setAttribute (ta, AutoStopPlugin.plugin ().getConfiguration ().getDefaultRatio (download));
        }

        synchronized (managedDownloads) {
            managedDownloads.add (download);
        }

    }

    /*
     * (non-Javadoc)
     *
     * @see com.biglybt.pif.download.DownloadManagerListener#downloadRemoved(com.biglybt.pif.download.Download)
     */
    public void downloadRemoved (Download download) {

        download.removeListener (this);
       // download.removePropertyListener (this);

        synchronized (managedDownloads) {
            managedDownloads.remove (download);
        }

    }

    /*
     * (non-Javadoc)
     *
     * @see com.biglybt.pif.download.DownloadPropertyListener#propertyChanged(com.biglybt.pif.download.Download,
     *      com.biglybt.pif.download.DownloadPropertyEvent)
     */
  //  public void propertyChanged (Download download, DownloadPropertyEvent event) {
        // TODO Auto-generated method stub

  //  }

    public void perform (UTTimerEvent event) {
        if (!configuration.isEnabled ()) { return; }
        synchronized (managedDownloads) {
            for (Iterator<Download> iter = managedDownloads.iterator (); iter.hasNext ();) {
                Download element = (Download) iter.next ();
                String thresholdString = element.getAttribute (attribute);

                // : Let "Unlimited" pass
                if ("Unlimited".equalsIgnoreCase (thresholdString)) {
                    continue;
                }

                if (element.isComplete ()) {
                    // : TODO perform ratio checks on the download.
                    int ratio = element.getStats ().getShareRatio ();

                    // : convert the ratio string into 1000ths
                    if (thresholdString == null) {
                        thresholdString = configuration.getDefaultRatio (element);
                    }
                    float ratioMultiple = Float.parseFloat (thresholdString);
                    int threshold = Math.round (ratioMultiple * 1000);

                    if (ratio > threshold) {

                        try {

                            // : only stop the download if it's not already
                            // stopped
                            if (element.getState () != Download.ST_STOPPED) {
                                element.stop ();
                            }

                            StopAction action = configuration.getAction ();

                            Logger.info (ResourceConstants.LOG_STOP_SEEDING, AutoStopPlugin.plugin ()
                                    .getPluginInterface ().getUtilities ().getLocaleUtilities ()
                                    .getLocalisedMessageText (action.getResource ()), element.getName ());

                            switch (action) {
                            case StopAndDeleteData:
                                element.remove (true, true);
                                break;
                            case StopAndDeleteTorrent:
                                element.remove (true, false);
                                break;
                            case StopAndRemove:
                                element.remove ();
                                break;
                            case StopSeeding:
                                // : nothing
                                break;
                            }
                        }
                        catch (DownloadException e) {
                            Logger.alert (ResourceConstants.CANNOT_STOP_DOWNLOAD, e);
                        }
                        catch (DownloadRemovalVetoException e) {
                            Logger.warn (ResourceConstants.CANNOT_REMOVE_DOWNLOAD, e.getMessage ());
                        }
                    }
                }
            }
        }
    }

}
