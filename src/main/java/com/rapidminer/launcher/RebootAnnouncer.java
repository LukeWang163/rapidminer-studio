package com.rapidminer.launcher;

import com.rapidminer.gui.ApplicationFrame;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;


















final class RebootAnnouncer
{
    public static void main(String[] args) throws InvocationTargetException, InterruptedException {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (ClassNotFoundException|InstantiationException|IllegalAccessException|javax.swing.UnsupportedLookAndFeelException classNotFoundException) {}



        JFrame frame = new JFrame("RapidMiner Studio Updater");
        ImageIcon img = new ImageIcon(RebootAnnouncer.class.getResource("/rapidminer_frame_icon_128.png"));
        frame.setIconImage(img.getImage());

        JPanel contentPanel = new JPanel(new GridBagLayout());

        String message = "";
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            message = "<html><div style=\"width:400px\"><p style=\"font-weight:bold;margin-bottom:10px;\">This update of RapidMiner Studio requires a system reboot.</p><p style=\"font-weight:normal;\">You can either reboot now or, if that is not possible, start RapidMiner Studio via the file <em>RapidMiner-Studio.bat</em> inside your RapidMiner Studio installation directory.</p></div></html>";
        } else {
            message = "<html><div style=\"width:400px\"><p style=\"font-weight:bold;margin-bottom:10px;\">Please restart RapidMiner Studio manually for the update changes to take effect.</p></div></html>";
        }

        JLabel label = new JLabel();
        label.setText(message);
        label.setIcon(new ImageIcon(RebootAnnouncer.class.getResource("/information.png")));
        label.setIconTextGap(24);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridwidth = 0;

        contentPanel.add(label, gbc);

        JButton okayButton = new JButton("Okay");
        okayButton.setMnemonic('O');

        okayButton.addActionListener(new ActionListener()
        {
            @SuppressFBWarnings
            public void actionPerformed(ActionEvent e)
            {
                System.exit(0);
            }
        });

        contentPanel.add(okayButton, gbc);

        frame.setAlwaysOnTop(true);
        frame.setContentPane(contentPanel);
        frame.setDefaultCloseOperation(3);
        frame.pack();
        frame.setLocationRelativeTo(ApplicationFrame.getApplicationFrame());
        frame.setVisible(true);
        frame.toFront();
    }
}
