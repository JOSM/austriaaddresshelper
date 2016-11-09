package org.openstreetmap.josm.plugins.austriaaddresshelper;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.gui.MapFrame;

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
        MainMenu.add(Main.main.menu.toolsMenu, austriaAddressHelperAction);
    }

    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {

    }
}
