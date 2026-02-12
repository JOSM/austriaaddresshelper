// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.austriaaddresshelper;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.SubPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.TabPreferenceSetting;
import org.openstreetmap.josm.tools.GBC;

public class AustriaAddressHelperPreferenceSetting implements SubPreferenceSetting {

    private final JTextField url = new JTextField();
    private final JCheckBox checkDuplicates = new JCheckBox(tr("Check existing addresses"));

    @Override
    public TabPreferenceSetting getTabPreferenceSetting(PreferenceTabbedPane gui) {
        return gui.getPluginPreference();
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        url.setText(AustriaAddressHelperAction.baseUrl.get());
        checkDuplicates.setSelected(AustriaAddressHelperAction.checkDuplicates.get());

        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(new JLabel(tr("Server URL:")), GBC.eol().fill(GBC.HORIZONTAL));
        panel.add(url, GBC.eop().fill(GBC.HORIZONTAL));
        panel.add(checkDuplicates, GBC.eop().fill(GBC.HORIZONTAL));
        getTabPreferenceSetting(gui).addSubTab(this, tr("Austria Address Helper"), panel);
    }

    @Override
    public boolean ok() {
        AustriaAddressHelperAction.baseUrl.put(url.getText());
        AustriaAddressHelperAction.checkDuplicates.put(checkDuplicates.isSelected());
        return false;
    }

    @Override
    public boolean isExpert() {
        return false;
    }
}
