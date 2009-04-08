package com.mixblendr.gui.main;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: dpanferov
 * Date: Oct 28, 2008
 * Time: 9:32:43 PM
 * To change this template use File | Settings | File Templates.
 */
public class ProgressDialog extends JDialog implements ChangeListener
{
    JLabel statusLabel = new JLabel();
    //JLabel statusLabel;
    private String textMessage;

    public String getTextMessage()
    {
        return textMessage;
    }

    public void setTextMessage(String textMessage)
    {
        this.textMessage = textMessage;
    }

    public ProgressDialog(Frame owner) throws HeadlessException {
        super(owner, "Saving data", false);
        init();
    }

    public ProgressDialog(Dialog owner) throws HeadlessException {
        super(owner);
        init();
    }

    private void init()
    {
        JPanel content = (JPanel)getContentPane();
        content.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        content.add(statusLabel, BorderLayout.NORTH);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    public void stateChanged(final ChangeEvent ce) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    stateChanged(ce);
                }
            });
            return;
        }
        statusLabel.setText(textMessage);
    }
}
