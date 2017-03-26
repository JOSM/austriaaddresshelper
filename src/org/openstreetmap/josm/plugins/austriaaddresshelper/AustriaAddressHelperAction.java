package org.openstreetmap.josm.plugins.austriaaddresshelper;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.CoordinateFormat;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.swing.*;
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
import java.util.Collection;
import java.util.List;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;

/**
 * Created by tom on 02/08/15.
 */
public class AustriaAddressHelperAction extends JosmAction {
    static final String baseUrl = "https://bev-reverse-geocoder.thomaskonrad.at/reverse-geocode/json";

    public AustriaAddressHelperAction() {
        super(tr("Fetch Address"), new ImageProvider("icon.png"), tr("Fetch Address"),
                Shortcut.registerShortcut("Fetch Address", tr("Fetch Address"),
                        KeyEvent.VK_A, Shortcut.CTRL_SHIFT), false, "fetchAddress",
                true);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        // Get the currently selected object
        final Collection<OsmPrimitive> sel = Main.getLayerManager().getEditDataSet().getSelected();

        if (sel.size() != 1) {
            new Notification(tr("Austria Address Helper<br>Please select exactly one object."))
                    .setIcon(JOptionPane.ERROR_MESSAGE)
                    .show();

            return;
        }

        final List<Command> commands = new ArrayList<>();
        for (OsmPrimitive selectedObject : sel) {
        	OsmPrimitive newObject = loadAddress(selectedObject);
        	if(newObject != null){
        		commands.add(new ChangeCommand(selectedObject, newObject));
        	}
        }
        if (!commands.isEmpty()) {
            Main.main.undoRedo.add(new SequenceCommand(trn("Add address", "Add addresses", commands.size()), commands));
        }
    }
    
    public static OsmPrimitive loadAddress(OsmPrimitive selectedObject){
        boolean noExceptionThrown = false;
        Exception exception = null;

        LatLon center = selectedObject.getBBox().getCenter();

        try {
            URL url = new URL(baseUrl
                    + "?lat=" + URLEncoder.encode(center.latToString(CoordinateFormat.DECIMAL_DEGREES), "UTF-8")
                    + "&lon=" + URLEncoder.encode(center.lonToString(CoordinateFormat.DECIMAL_DEGREES), "UTF-8")
                    + "&distance=30"
                    + "&limit=1"
                    + "&epsg=4326"
            );

            final JsonObject json;
            try (BufferedReader in = HttpClient.create(url)
                    .setReasonForRequest("JOSM Plugin Austria Address Helper v0.3.2")
                    .setHeader("User-Agent", "JOSM Plugin Austria Address Helper v0.3.2")
                    .connect()
                    .getContentReader();
                 JsonReader reader = Json.createReader(in)) {
                json = reader.readObject();
            }

            final JsonArray addressItems = json.getJsonArray("results");
            if (addressItems.size() > 0) {
                final JsonObject firstAddress = addressItems.getJsonObject(0);

                String country = "AT";
                String city = firstAddress.getString("municipality");
                String postcode = firstAddress.getString("postcode");
                String streetOrPlace;
                String housenumber = firstAddress.getString("house_number");

                final OsmPrimitive newObject = selectedObject instanceof Node
                        ? new Node(((Node) selectedObject))
                        : selectedObject instanceof Way
                        ? new Way((Way) selectedObject)
                        : selectedObject instanceof Relation
                        ? new Relation((Relation) selectedObject)
                        : null;
                newObject.put("addr:country", country);
                newObject.put("addr:city", city);
                newObject.put("addr:postcode", postcode);

                if ((firstAddress.get("address_type")).equals("place")) {
                    streetOrPlace = firstAddress.getString("street");
                    newObject.put("addr:place", streetOrPlace);
                } else {
                    streetOrPlace = firstAddress.getString("street");
                    newObject.put("addr:street", streetOrPlace);
                }

                newObject.put("addr:housenumber", housenumber);

                // Set the date of the data source.
                final String addressDate = json.getString("address_date");
                newObject.put("at_bev:addr_date", addressDate);

                // Set or add the address source.
                final String copyright = "Adressdaten: " + json.getString("copyright");

                // Add the data source to the changeset (not to the object because that can be changed easily).
                Main.getLayerManager().getEditDataSet().addChangeSetTag("source", copyright);

                // Get the distance between the building center and the address coordinates.
                final double distanceToAddressCoordinates = firstAddress.getJsonNumber("distance").doubleValue();

                new Notification(
                        "<strong>" + tr("Austria Address Helper") + "</strong><br />" +
                        tr("Successfully added address to selected object:") + "<br />" +
                        encodeHTML(streetOrPlace) + " " + encodeHTML(housenumber) + ", " + encodeHTML(postcode) + " " + encodeHTML(city) + " (" + encodeHTML(country) + ")<br/>" +
                        "<strong>" + tr("Distance between building center and address coordinates:") + "</strong> " +
                        new DecimalFormat("#.##").format(distanceToAddressCoordinates) + " " + tr("meters")
                )
                        .setIcon(JOptionPane.INFORMATION_MESSAGE)
                        .setDuration(2500)
                        .show();
                noExceptionThrown = true;
                return newObject;
            } else {
                new Notification(
                        "<strong>" + tr("Austria Address Helper") + "</strong><br />" +
                        tr("No address was found for this object.")
                )
                        .setIcon(JOptionPane.ERROR_MESSAGE)
                        .show();
            }

            noExceptionThrown = true;
        } catch (MalformedURLException e) {
            e.printStackTrace();
            exception = e;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            exception = e;
        } catch (IOException e) {
            e.printStackTrace();
            exception = e;
        } catch (NullPointerException e) {
            e.printStackTrace();
            exception = e;
        } finally {
            if (!noExceptionThrown) {
                new Notification(
                        "<strong>" + tr("Austria Address Helper") + "</strong>" +
                        tr("An unexpected exception occurred:") + exception.toString()
                )
                        .setIcon(JOptionPane.ERROR_MESSAGE)
                        .show();
            }
        }
        return null;

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
}
