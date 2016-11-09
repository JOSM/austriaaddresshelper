package org.openstreetmap.josm.plugins.austriaaddresshelper;

import com.owlike.genson.Genson;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.coor.CoordinateFormat;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Created by tom on 02/08/15.
 */
public class AustriaAddressHelperAction extends JosmAction {
    final String baseUrl = "https://bev-reverse-geocoder.thomaskonrad.at/reverse-geocode/json";

    public AustriaAddressHelperAction() {
        super(tr("Fetch Address"), new ImageProvider("icon.png"), tr("Fetch Address"),
                Shortcut.registerShortcut("Fetch Address", tr("Fetch Address"),
                        KeyEvent.VK_B, Shortcut.ALT_CTRL), false, "fetchAddress",
                false);
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

        for (OsmPrimitive selectedObject : sel) {
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

                URLConnection connection = url.openConnection();
                connection.setRequestProperty("User-Agent", "JOSM Plugin Austria Address Helper v1.0");
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                String inputLine;
                String wholeResponse = "";

                while ((inputLine = in.readLine()) != null)
                    wholeResponse += inputLine;

                in.close();

                HashMap result = (HashMap)new Genson().deserialize(wholeResponse, Object.class);
                ArrayList addressItems = (ArrayList)result.get("results");

                if (addressItems.size() > 0) {
                    HashMap firstAddress = (HashMap)addressItems.get(0);

                    String country = "AT";
                    String city = (String)firstAddress.get("municipality");
                    String postcode = (String)firstAddress.get("postcode");
                    String streetOrPlace;
                    String housenumber = (String)firstAddress.get("house_number");

                    selectedObject.put("addr:country", country);
                    selectedObject.put("addr:city", city);
                    selectedObject.put("addr:postcode", postcode);

                    if ((firstAddress.get("address_type")).equals("place")) {
                        streetOrPlace = (String)firstAddress.get("street");
                        selectedObject.put("addr:place", streetOrPlace);
                    } else {
                        streetOrPlace = (String)firstAddress.get("street");
                        selectedObject.put("addr:street", streetOrPlace);
                    }

                    selectedObject.put("addr:housenumber", housenumber);

                    // Set or add the address source.
                    String copyright = (String)result.get("copyright");
                    copyright = "Adressdaten: " + copyright;
                    String source = selectedObject.get("source");

                    if (source == null) {
                        selectedObject.put("source", copyright);
                    } else if (!source.contains(copyright)) {
                        selectedObject.put("source", source + "; " + copyright);
                    }

                    selectedObject.setModified(true);

                    new Notification(
                            "<strong>" + tr("Austria Address Helper") + "</strong><br />" +
                            tr("Successfully added address to selected object:") + "<br />" +
                            encodeHTML(streetOrPlace) + " " + encodeHTML(housenumber) + ", " + encodeHTML(postcode) + " " + encodeHTML(city) + " (" + encodeHTML(country) + ")"
                    )
                            .setIcon(JOptionPane.INFORMATION_MESSAGE)
                            .show();
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
        }
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
