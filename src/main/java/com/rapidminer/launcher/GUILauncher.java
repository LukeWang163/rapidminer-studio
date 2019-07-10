package com.rapidminer.launcher;

import com.rapidminer.RapidMiner;
import com.rapidminer.gui.RapidMinerGUI;
import com.rapidminer.gui.ToolbarGUIStartupListener;
import com.rapidminer.gui.license.LicenseGUIStartupListener;
import com.rapidminer.gui.license.onboarding.OnboardingGUIStartupListener;
import com.rapidminer.license.LicenseManagerRegistry;
import com.rapidminer.license.internal.DefaultLicenseManager;
import com.rapidminer.license.verification.JarVerifier;
import com.rapidminer.tools.PlatformUtilities;
import com.rapidminer.tools.net.UserProvidedTLSCertificateLoader;
import com.rapidminer.tools.update.internal.MarketplaceGUIStartupListener;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.awt.BorderLayout;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;















@SuppressFBWarnings
public final class GUILauncher
{
    private static final Logger LOGGER = Logger.getLogger(GUILauncher.class.getName());



    private static JProgressBar bar;



    private static JFrame dialog;



    private static boolean updateGUI(File rmHome, File updateZip, File updateScript) {
        try {
            SwingUtilities.invokeAndWait(new Runnable()
            {
                public void run()
                {
                    dialog = new JFrame("Updating RapidMiner");
                    bar = new JProgressBar(false, '?');
                    dialog.setLayout(new BorderLayout());
                    dialog.add(new JLabel("Updating RapidMiner"), "North");
                    dialog.add(bar, "Center");
                    dialog.pack();
                    dialog.setLocationRelativeTo(null);
                    dialog.setVisible(true);
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Cannot show update dialog.", e);
            return false;
        }
        boolean success = true;
        if (updateZip != null) {
            try {
                success &= updateDiffZip(rmHome, updateZip, true);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Update from " + updateZip + " failed: " + e, e);
                JOptionPane.showMessageDialog(dialog, "Update from " + updateZip + " failed: " + e, "Update Failed", 0);

                success = false;
            }
        }
        if (updateScript != null) {
            try {
                success &= executeUpdateScript(rmHome, new FileInputStream(updateScript), true);
                if (!updateScript.delete()) {
                    updateScript.deleteOnExit();
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Update script " + updateScript + " failed: " + e, e);
                JOptionPane.showMessageDialog(dialog, "Update from " + updateScript + " failed: " + e, "Update Failed", 0);

                success = false;
            }
        }
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                dialog.dispose();
            }
        });
        return success;
    }


    private static boolean executeUpdateScript(File rmHome, InputStream in, boolean gui) throws IOException {
        try {
            Set<String> toDelete = new HashSet<String>();
            BufferedReader updateReader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = updateReader.readLine()) != null) {
                String[] split = line.split(" ", 2);
                if (split.length != 2) {
                    LOGGER.warning("Ignoring unparseable update script entry: " + line);
                }
                if ("DELETE".equals(split[0])) {
                    toDelete.add(split[1].trim()); continue;
                }
                LOGGER.warning("Ignoring unparseable update script entry: " + line);
            }

            for (String string : toDelete) {
                File file = new File(rmHome, string);
                LOGGER.info("DELETE " + file);
                if (!file.delete()) {
                    file.deleteOnExit();
                }
            }
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Cannot read update script: " + e, e);
            if (gui) {
                JOptionPane.showMessageDialog(dialog, "Cannot read update script: " + e, "Update Failed", 0);
            }

            return false;
        } finally {
            try {
                in.close();
            } catch (IOException iOException) {}
        }
    }






    @SuppressFBWarnings({"REC_CATCH_EXCEPTION"})
    private static boolean updateDiffZip(File rmHome, File updateZip, boolean gui) {
        LOGGER.info("Updating using update file " + updateZip);
        zip = null;
        try {
            zip = new ZipFile(updateZip);
        } catch (Exception e1) {
            LOGGER.log(Level.SEVERE, "Update file corrupt: " + e1, e1);
            if (gui) {
                JOptionPane.showMessageDialog(dialog, "Update file corrupt: " + e1, "Update Failed", 0);
            }

            return false;
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException iOException) {}
            }
        }


        final int size = zip.size();
        Enumeration<? extends ZipEntry> enumeration = zip.entries();
        int i = 0;
        while (enumeration.hasMoreElements()) {
            i++;
            ZipEntry entry = (ZipEntry)enumeration.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (!"META-INF/UPDATE".equals(name)) {
                if (name.startsWith("rapidminer/")) {
                    name = name.substring("rapidminer/".length());
                }
                File dest = new File(rmHome, name);
                try(InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(dest)) {
                    LOGGER.info("UPDATE " + dest);
                    parent = dest.getParentFile();
                    if (parent != null && !parent.exists() &&
                            !parent.mkdirs()) {
                        JOptionPane.showMessageDialog(dialog, "Cannot create directory " + parent.toString(), "Update Failed", 0);
                    }


                    final int fi = i;
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        public void run()
                        {
                            bar.setValue(fi * 1000 / size);
                        }
                    });
                    byte[] buf = new byte[10240];

                    int length;
                    while ((length = in.read(buf)) != -1) {
                        out.write(buf, 0, length);
                    }
                } catch (Exception e2) {
                    LOGGER.log(Level.WARNING, "Updating " + dest + " failed: " + e2, e2);
                    if (gui) {
                        JOptionPane.showMessageDialog(dialog, "Updating " + dest + " failed: " + e2, "Update Failed", 0);
                    }

                    return false;
                }
            }
        }


        ZipEntry updateEntry = zip.getEntry("META-INF/UPDATE");
        if (updateEntry != null) {
            try {
                executeUpdateScript(rmHome, zip.getInputStream(updateEntry), gui);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Cannot read update script: " + e, e);
                if (gui) {
                    JOptionPane.showMessageDialog(dialog, "Cannot read update script: " + e, "Update Failed", 0);
                }
            }
        }

        try {
            zip.close();
        } catch (IOException e1) {
            LOGGER.log(Level.WARNING, "Cannot close update file: " + enumeration, enumeration);
        }
        try {
            if (updateZip.delete()) {
                return true;
            }
            JOptionPane.showMessageDialog(dialog, "Could not delete update file " + updateZip + ". Probably you do not have administrator permissions. Please delete this file manually.", "Update Failed", 0);



            return false;
        }
        catch (Exception ex) {
            JOptionPane.showMessageDialog(dialog, "Could not delete update file " + updateZip + ". Probably you do not have administrator permissions. Please delete this file manually.", "Update Failed", 0);



            return false;
        }
    }





    public static void main(String[] args) throws Exception {
        PlatformUtilities.initialize();

        RapidMiner.setExecutionMode(RapidMiner.ExecutionMode.UI);
        UserProvidedTLSCertificateLoader.INSTANCE.init();
        LicenseManagerRegistry.INSTANCE.set(new DefaultLicenseManager());

        try {
            JarVerifier.verify(new Class[] { LicenseManagerRegistry.INSTANCE.get().getClass(), RapidMiner.class, GUILauncher.class });
        } catch (GeneralSecurityException e) {
            LOGGER.log(Level.SEVERE, "Failed to verify RapidMiner Studio installation: " + e.getMessage(), e);
            System.exit(1);
        }

        String rapidMinerHomeProperty = System.getProperty("rapidminer.home");
        if (rapidMinerHomeProperty == null) {
            LOGGER.info("RapidMiner HOME is not set. Ignoring potential update installation. (If that happens, you weren't able to download updates anyway.)");
        } else {

            File rmHome = new File(rapidMinerHomeProperty);
            File updateDir = new File(rmHome, "update");

            File updateScript = new File(updateDir, "UPDATE");
            if (!updateScript.exists()) {
                updateScript = null;
            }
            File[] updates = updateDir.listFiles(pathname -> pathname.getName().startsWith("rmupdate"));
            File updateZip = null;
            if (updates != null)
                switch (updates.length) {
                    case 0:
                        break;
                    case 1:
                        updateZip = updates[0];
                        break;
                    default:
                        LOGGER.warning("Multiple updates found: " + Arrays.toString(updates) + ". Ignoring all.");
                        break;
                }
            if ((updateZip != null || updateScript != null) &&
                    updateGUI(rmHome, updateZip, updateScript)) {
                RapidMiner.relaunch();


                return;
            }
        }

        registerGUIStartupListeners();


        RapidMinerGUI.main(args);
    }




    private static void registerGUIStartupListeners() {
        RapidMinerGUI.registerStartupListener(new MarketplaceGUIStartupListener());
        RapidMinerGUI.registerStartupListener(new OnboardingGUIStartupListener());
        RapidMinerGUI.registerStartupListener(new LicenseGUIStartupListener());
        RapidMinerGUI.registerStartupListener(new ToolbarGUIStartupListener());
    }




    public static String getLongVersion() {
        version = GUILauncher.class.getPackage().getImplementationVersion();
        if (version == null) {
            LOGGER.info("Implementation version not set.");
            return "?.?.?";
        }
        return version.split("-")[0];
    }





    public static String getShortVersion() {
        version = getLongVersion();
        int lastDot = version.lastIndexOf('.');
        if (lastDot != -1) {
            return version.substring(0, lastDot);
        }

        return version;
    }



    public static boolean isDevelopmentBuild() { return (PlatformUtilities.getReleasePlatform() == null); }
}
