package org.openstreetmap.josm.plugins.austriaaddresshelper;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.coor.conversion.DecimalDegreesCoordinateFormat;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.StringProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.io.OverpassDownloadReader;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Created by tom on 02/08/15.
 */
public class AustriaAddressHelperAction extends JosmAction {
    static final StringProperty baseUrl = new StringProperty("austriaaddresshelper.url",
            "https://bev-reverse-geocoder.thomaskonrad.at/reverse-geocode/json");
    static final BooleanProperty checkDuplicates = new BooleanProperty("austriaaddresshelper.check-duplicates", true);
    static boolean addressTypeDialogCanceled = false;

    protected static HashMap<HashMap<String, String>, String> rememberedAddressTypeChoices = new HashMap<>();
    protected static String[] tagsToCheckForDuplicates = {"addr:city", "addr:postcode", "addr:place", "addr:street", "addr:hamlet", "addr:housenumber"};
    protected static String[] streetTypeTags = {"addr:street", "addr:place", "addr:hamlet", "addr:suburb"};
    protected static String[] objectTypesToCheckforDuplicates = {"way", "node", "relation"};
    protected static String streetTypeTagPlaceholder = "___street_type_tag___";

    public AustriaAddressHelperAction() {
        super(tr("Fetch Address"), new ImageProvider("icon.png"), tr("Fetch Address"),
                Shortcut.registerShortcut("Fetch Address", tr("Fetch Address"),
                        KeyEvent.VK_A, Shortcut.CTRL_SHIFT), false, "fetchAddress",
                true);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        // Get the currently selected object
        final Collection<OsmPrimitive> sel = MainApplication.getLayerManager().getEditDataSet().getSelected();

        if (sel.size() != 1) {
            new Notification(tr("Austria Address Helper<br>Please select exactly one object."))
                    .setIcon(JOptionPane.ERROR_MESSAGE)
                    .show();

            return;
        }

        final List<Command> commands = new ArrayList<>();
        for (OsmPrimitive selectedObject : sel) {
        	Map<String, String> newObject = loadAddress(selectedObject);
        	if(newObject != null){
        		commands.add(new ChangePropertyCommand(Collections.singleton(selectedObject), newObject));
        	}
        }
        if (!commands.isEmpty()) {
            UndoRedoHandler.getInstance().add(new SequenceCommand(trn("Add address", "Add addresses", commands.size()), commands));
        }
    }
    
    public static Map<String, String> loadAddress(OsmPrimitive selectedObject){
        LatLon center = selectedObject.getBBox().getCenter();

        try {
            URL url = new URL(baseUrl.get()
                    + "?lat=" + URLEncoder.encode(DecimalDegreesCoordinateFormat.INSTANCE.latToString(center), "UTF-8")
                    + "&lon=" + URLEncoder.encode(DecimalDegreesCoordinateFormat.INSTANCE.lonToString(center), "UTF-8")
                    + "&distance=30"
                    + "&limit=1"
                    + "&epsg=4326"
            );

            final JsonObject json;
            try (BufferedReader in = HttpClient.create(url)
                    .setReasonForRequest("JOSM Plugin Austria Address Helper")
                    .setHeader("User-Agent", "JOSM Plugin Austria Address Helper")
                    .connect()
                    .getContentReader();
                 JsonReader reader = Json.createReader(in)) {
                json = reader.readObject();
            }

            final JsonArray addressItems = json.getJsonArray("results");
            if (addressItems.size() > 0) {
                final JsonObject firstAddress = addressItems.getJsonObject(0);

                String country = "AT";
                String municipality = firstAddress.getString("municipality");
                String locality = firstAddress.getString("locality");
                String postcode = firstAddress.getString("postcode");
                String streetOrPlace;
                String houseNumber = firstAddress.getString("house_number");

                final Map<String, String> newObject = new TreeMap<>();

                newObject.put("addr:country", country);

                // Some municipalities have a specific combination of postcode and street multiple times in several
                // localities. For example, the street "Feldgasse" in the municipality of Großebersdorf with the
                // the postcode 2203 exists four times, namely in the localities Eibesbrunn, Großebersdorf,
                // Manhartsbrunn, and Putzing. If this is the case, we need to set the "addr:city" tag to the value of
                // the locality and not the municipality so that the address is unique. If there is one such case in a
                // municipality, all addresses in the municipality have the locality in the addr:city tag (such
                // municipalities get the attribute "municipality_has_ambiguous_addresses").
                if (firstAddress.getBoolean("municipality_has_ambiguous_addresses")) {
                    newObject.put("addr:city", locality);
                } else {
                    newObject.put("addr:city", municipality);
                }

                newObject.put("addr:postcode", postcode);

                streetOrPlace = firstAddress.getString("street");

                // First remove addr:street and addr:place tags.
                newObject.remove("addr:street");
                newObject.remove("addr:place");

                // Decide whether the address type is 'street' or 'place'.
                if (firstAddress.getString("address_type").equals("place")) {
                    newObject.put("addr:place", streetOrPlace);
                } else if (firstAddress.getString("address_type").equals("street")) {
                    newObject.put("addr:street", streetOrPlace);
                } else {
                    // Get remembered choice or ask the user.
                    String addressType = getRememberedAddressTypeOrAsk(streetOrPlace, houseNumber, postcode, municipality);

                    // If the address type is neither "street" nor "place", show a warning and return.
                    if (addressType == null || !AddressTypeDialog.ALLOWED_ADDRESS_TYPES.contains(addressType)) {
                        new Notification(
                                "<strong>" + tr("Austria Address Helper") + "</strong><br />" +
                                        tr("No address type selected. Aborting.")
                        )
                                .setIcon(JOptionPane.WARNING_MESSAGE)
                                .setDuration(2500)
                                .show();

                        return null;
                    } else {
                        newObject.put("addr:" + addressType, streetOrPlace);
                    }
                }

                newObject.put("addr:housenumber", houseNumber);

                // Search for duplicates.
                List<String> existingObjectsWithThatAddress = checkDuplicates.get()
                        ? getUrlsOfObjectsWithThatAddress(newObject, center)
                        : Collections.emptyList();

                int dialogAnswer = -2;

                if (existingObjectsWithThatAddress == null) {
                    dialogAnswer = JOptionPane.showOptionDialog(MainApplication.getMainFrame(), tr("Unable to check whether this address already exists in OpenStreetMap: Continue anyway?"), tr("Address Duplicate Check Failed"),
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, null, null);
                } else if (existingObjectsWithThatAddress.size() > 0) {
                    StringBuilder urlList = new StringBuilder();
                    urlList.append("<ul>");

                    for (String duplicateUrl: existingObjectsWithThatAddress) {
                        urlList.append("<li><a href=\"");
                        urlList.append(encodeHTML(duplicateUrl));
                        urlList.append("\">");
                        urlList.append(encodeHTML(duplicateUrl));
                        urlList.append("</a></li>");
                    }

                    urlList.append("</ul>");

                    Object[] options = { tr("Yes"), tr("No") };

                    dialogAnswer = JOptionPane.showOptionDialog(
                            MainApplication.getMainFrame(),
                            new MessageWithLink(
                                    tr("The following objects in OpenStreetMap already have this address:") +
                                    urlList.toString() +
                                    tr("Are you sure that you want to add it?")
                            ),
                            tr("Duplicate Address"),
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE,
                            null,
                            options,
                            options[1]
                    );
                }

                // Either no dialog was shown or "yes" was selected.
                if (dialogAnswer == -2 || dialogAnswer == JOptionPane.YES_OPTION) {
                    // Set the date of the data source.
                    final String addressDate = json.getString("address_date");
                    newObject.put("at_bev:addr_date", addressDate);

                    // Set or add the address source.
                    final String copyright = "Adressdaten: " + json.getString("copyright");

                    // Add the data source to the changeset (not to the object because that can be changed easily).
                    MainApplication.getLayerManager().getEditDataSet().addChangeSetTag("source", copyright);

                    // Get the distance between the building center and the address coordinates.
                    final double distanceToAddressCoordinates = firstAddress.getJsonNumber("distance").doubleValue();

                    new Notification(
                            "<strong>" + tr("Austria Address Helper") + "</strong><br />" +
                                    tr("Successfully added address to selected object:") + "<br />" +
                                    encodeHTML(streetOrPlace) + " " + encodeHTML(houseNumber) + ", " + encodeHTML(postcode) + " " + encodeHTML(municipality) + " (" + encodeHTML(country) + ")<br/>" +
                                    "<strong>" + tr("Distance between building center and address coordinates:") + "</strong> " +
                                    new DecimalFormat("#.##").format(distanceToAddressCoordinates) + " " + tr("meters")
                    )
                            .setIcon(JOptionPane.INFORMATION_MESSAGE)
                            .setDuration(2500)
                            .show();

                    return newObject;
                } else {
                    return null;
                }
            } else {
                new Notification(
                        "<strong>" + tr("Austria Address Helper") + "</strong><br />" +
                        tr("No address was found for this object.")
                )
                        .setIcon(JOptionPane.ERROR_MESSAGE)
                        .show();
            }
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            new Notification(
                    "<strong>" + tr("Austria Address Helper") + "</strong>" +
                            tr("An unexpected exception occurred:") + e.toString()
            )
                    .setIcon(JOptionPane.ERROR_MESSAGE)
                    .show();
        }

        return null;
    }

    protected static ArrayList<String> getUrlsOfObjectsWithThatAddress(Map<String, String> newObject, LatLon position) {
        ArrayList<String> urls = new ArrayList<>();

        final String header = "[out:json][timeout:10]";

        // Just a rough bounding box.
        String bbox ="[bbox:" +
                (position.getY() - 0.075) + "," +
                (position.getX() - 0.1  ) + "," +
                (position.getY() + 0.075) + "," +
                (position.getX() + 0.1  ) + "]";

        StringBuilder filterLineBuilder = new StringBuilder();

        // Iterate over all tags of the new object.
        for (String key: newObject.keySet()) {
            String value = newObject.get(key);

            // Only filter for relevant tags.
            if (Arrays.asList(tagsToCheckForDuplicates).contains(key)) {
                filterLineBuilder.append("[\"");

                String tagName;

                // If it is one of addr:street, addr:place, addr:hamlet, etc., we'll put a placeholder and repeat all
                // three in the later filter. This is because it is not quite clear every time which of those tags fits
                // best.
                if (Arrays.asList(streetTypeTags).contains(key)) {
                    tagName = streetTypeTagPlaceholder;
                } else {
                    tagName = key;
                }

                filterLineBuilder.append(tagName.replaceAll("\"", "\\\\\"")); // Escape double quotes

                filterLineBuilder.append("\"=\"");
                filterLineBuilder.append(value.replaceAll("\"", "\\\\\"")); // Escape double quotes
                filterLineBuilder.append("\"]");
            }
        }

        String filterLine = filterLineBuilder.toString();

        StringBuilder filterBuilder = new StringBuilder();

        // Iterate over all object types and street-type tags and build all combinations.
        for (String streetTypeTag: streetTypeTags) {
            for (String objectType: objectTypesToCheckforDuplicates) {
                filterBuilder.append(objectType);
                filterBuilder.append(filterLine.replaceAll(streetTypeTagPlaceholder, streetTypeTag));
                filterBuilder.append(";");
            }
        }

        String filter = filterBuilder.toString();

        final String footer = "out body;";

        // Build the whole Overpass API query.
        String query = header + bbox + ";" + "(" + filter + ");" + footer;

        boolean noExceptionThrown = false;
        Exception exception = null;

        try {
            URL url = new URL(OverpassDownloadReader.OVERPASS_SERVER.get() + "interpreter"
                    + "?data=" + URLEncoder.encode(query, "UTF-8")
            );

            final JsonObject json;

            try (BufferedReader in = HttpClient.create(url)
                    .setReasonForRequest("JOSM Plugin Austria Address Helper")
                    .setHeader("User-Agent", "JOSM Plugin Austria Address Helper")
                    .connect()
                    .getContentReader();
                JsonReader reader = Json.createReader(in)) {
                json = reader.readObject();
            }

            final JsonArray items = json.getJsonArray("elements");

            if (items.size() > 0) {
                for (JsonValue item: items) {
                    JsonObject itemObject = item.asJsonObject();

                    try {
                        String type = itemObject.getString("type");
                        int osmId = itemObject.getInt("id");

                        urls.add("https://www.openstreetmap.org/" + URLEncoder.encode(type, "UTF-8") + "/" + URLEncoder.encode(Integer.toString(osmId), "UTF-8"));
                    } catch (NullPointerException e) {
                        urls.add("<Could not generate URL>");
                    }
                }
            }

            noExceptionThrown = true;
        } catch (MalformedURLException | UnsupportedEncodingException | NullPointerException e) {
            e.printStackTrace();
            exception = e;
        } catch (IOException e) {
            e.printStackTrace();
            exception = e;
        } finally {
            if (!noExceptionThrown) {
                new Notification(
                        "<strong>" + tr("Austria Address Helper") + "</strong>" +
                                tr("An unexpected exception occurred while checking for address duplicates:") + exception.toString()
                )
                .setIcon(JOptionPane.ERROR_MESSAGE)
                .show();

                urls = null;
            }
        }

        return urls;
    }

    protected static String getRememberedAddressTypeOrAsk(String streetOrPlace, String houseNumber, String postcode, String city) {
        String addressType;

        // First, we'll look if there is a remembered choice for that place, postcode and city.
        String rememberedAddressType = getRememberedChoice(streetOrPlace, postcode, city);

        if (rememberedAddressType != null) {
            return rememberedAddressType;
        }

        // No remembered address type. Show the address type dialog and let the user decide.
        AddressTypeDialog dialog = new AddressTypeDialog(streetOrPlace, houseNumber, postcode, city);
        dialog.showDialog();

        // "OK" was not clicked
        if (dialog.getValue() != 1) {
            return null;
        }

        addressType = dialog.getAddressType();

        // The user has chosen to remember the address type, so store it for this session.
        if (dialog.rememberChoice() && addressType != null && AddressTypeDialog.ALLOWED_ADDRESS_TYPES.contains(addressType)) {
            // The user wants the choice to be remembered. Add the choice to the list of remembered choices.
            rememberedAddressTypeChoices.put(dialog.getRememberedChoice(), dialog.getAddressType());
        }

        return addressType;
    }

    @Override
    protected void updateEnabledState() {
        if (getLayerManager().getEditDataSet() == null) {
            setEnabled(false);
        } else {
            updateEnabledState(getLayerManager().getEditDataSet().getSelected());
        }
    }

    @Override
    protected void updateEnabledState(final Collection<? extends OsmPrimitive> selection) {
        // Enable it only if exactly one object is selected.
        setEnabled(selection != null && selection.size() == 1);
    }

    private static String encodeHTML(String s)
    {
        StringBuffer out = new StringBuffer();
        for(int i=0; i<s.length(); i++)
        {
            char c = s.charAt(i);
            if(c > 127 || c=='"' || c=='<' || c=='>')
            {
                out.append("&#"+(int)c+";");
            }
            else
            {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String getRememberedChoice(String placeName, String postcode, String city) {
        HashMap<String, String> place = new HashMap<>();
        place.put("place_name", placeName);
        place.put("postcode", postcode);
        place.put("city", city);

        return rememberedAddressTypeChoices.get(place);
    }
}
