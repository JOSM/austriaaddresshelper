package org.openstreetmap.josm.plugins.austriaaddresshelper;

import static org.openstreetmap.josm.tools.I18n.tr;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.ExtendedDialog;

import javax.swing.*;
import javax.swing.plaf.metal.MetalToggleButtonUI;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * @author Thomas Konrad (mail@thomaskonrad.at)
 */
public class AddressTypeDialog extends ExtendedDialog {

    protected String placeName;
    protected String houseNumber;
    protected String postcode;
    protected String city;

    protected static final String[] BUTTON_TEXTS = new String[] {tr("OK"), tr("Cancel")};
    protected static final String[] BUTTON_ICONS = new String[] {"ok.png", "cancel.png"};

    public static final String ADDRESS_TYPE_STREET = "street";
    public static final String ADDRESS_TYPE_PLACE = "place";
    public static final List<String> ALLOWED_ADDRESS_TYPES = Arrays.asList(new String[]{ AddressTypeDialog.ADDRESS_TYPE_STREET, AddressTypeDialog.ADDRESS_TYPE_PLACE });

    JToggleButton streetButton;
    JToggleButton placeButton;
    JCheckBox rememberChoiceCheckbox;

    public AddressTypeDialog(String placeName, String houseNumber, String postcode, String city) {
        super(Main.parent, tr("Please choose the address type"), BUTTON_TEXTS, true);

        this.placeName = placeName;
        this.houseNumber = houseNumber;
        this.postcode = postcode;
        this.city = city;

        setButtonIcons(BUTTON_ICONS);
        setMinimumSize(new Dimension(700, 400));
        setLocationRelativeTo(null);

        JPanel contentPane = new JPanel(new BorderLayout());
        setContent(contentPane, false);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        JLabel descriptionLabel = new JLabel(tr("<html>We could not automatically determine whether <strong>{0}</strong>" +
                " (part of the address {0} {1}, {2} {3}) is a <strong>street</strong> or a " +
                "<strong>place</strong> name. If <strong>{0}</strong> is an actual street, please choose \"street\" " +
                "below. If, however, <strong>{0}</strong> is the name of a village, hamlet, territorial zone or any " +
                "object other than a street, please choose \"place\".</html>", placeName, houseNumber, postcode, city));
        descriptionLabel.setPreferredSize(new Dimension(700, 80));
        mainPanel.add(descriptionLabel, BorderLayout.NORTH);

        JPanel radioButtonPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        radioButtonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        JPanel streetPanel = new JPanel(new BorderLayout());
        ImageIcon streetIcon = new ImageIcon(getClass().getResource("/images/street-icon.png"));
        streetButton = new JToggleButton(tr("<html>{0} is a <strong>street</strong>.</html>", placeName), streetIcon);
        streetButton.setMargin(new Insets(10, 10, 10, 10));
        streetButton.addItemListener(e -> enableOKButton());
        streetPanel.add(streetButton, BorderLayout.CENTER);

        JPanel placePanel = new JPanel(new BorderLayout());
        ImageIcon placeIcon = new ImageIcon(getClass().getResource("/images/place-icon.png"));
        placeButton = new JToggleButton(tr("<html>{0} is a <strong>place</strong>.</html>", placeName), placeIcon);
        placeButton.setMargin(new Insets(10, 10, 10, 10));
        placeButton.addItemListener(e -> enableOKButton());
        placePanel.add(placeButton, BorderLayout.CENTER);

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(streetButton);
        buttonGroup.add(placeButton);

        radioButtonPanel.add(streetPanel);
        radioButtonPanel.add(placePanel);

        mainPanel.add(radioButtonPanel, BorderLayout.CENTER);

        rememberChoiceCheckbox = new JCheckBox(tr("<html>Remember my choice for <strong>{0}</strong> in " +
                "{1} {2} for this session.</html>", placeName, postcode, city));
        mainPanel.add(rememberChoiceCheckbox, BorderLayout.SOUTH);

        contentPane.add(mainPanel, BorderLayout.CENTER);

        // Disable the OK when the dialog is opened (we'll enable it when a choice is made).
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                super.windowOpened(e);

                // We use this little trick to put the focus away from the first toggle button.
                if (defaultButton != null) {
                    defaultButton.requestFocusInWindow();
                }

                disableOKButton();
            }
        });
    }

    public String getAddressType() {
        if (streetButton != null && streetButton.isSelected()) {
            return ADDRESS_TYPE_STREET;
        }

        if (placeButton != null && placeButton.isSelected()) {
            return ADDRESS_TYPE_PLACE;
        }

        return null;
    }

    public boolean rememberChoice() {
        return rememberChoiceCheckbox.isSelected();
    }

    public HashMap<String, String> getRememberedChoice() {
        HashMap<String, String> choice = new HashMap<String, String>();
        choice.put("place_name", placeName);
        choice.put("postcode", postcode);
        choice.put("city", city);

        return choice;
    }

    protected void disableOKButton() {
        if (this.defaultButton != null) {
            this.defaultButton.setEnabled(false);
        }
    }

    protected void enableOKButton() {
        if (this.defaultButton != null) {
            this.defaultButton.setEnabled(true);
        }
    }
}
