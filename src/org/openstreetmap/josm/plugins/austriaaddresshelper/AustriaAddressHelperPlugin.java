package org.openstreetmap.josm.plugins.austriaaddresshelper;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

public class AustriaAddressHelperPlugin extends Plugin {

    AustriaAddressHelperAction austriaAddressHelperAction;

    /**
     * Will be invoked by JOSM to bootstrap the plugin
     *
     * @param info  information about the plugin and its local installation
     */
    public AustriaAddressHelperPlugin(PluginInformation info) {
        super(info);
        // init your plugin

        austriaAddressHelperAction = new AustriaAddressHelperAction();
        MainMenu.add(MainApplication.getMenu().toolsMenu, austriaAddressHelperAction);
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {

    }
}
