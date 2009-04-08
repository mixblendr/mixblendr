package com.mixblendr.audio;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: dpanferov
 * Date: 2008.11.24.
 * Time: 14:42:30
 * To change this template use File | Settings | File Templates.
 */
public class PublishingDialog extends JDialog implements ActionListener
{
    public PublishingDialog(Rectangle r)
    {
        super((Frame)null,"Publishing...", true);
        final JOptionPane optionPane = new JOptionPane(
                        "Uploading... Please wait",
                        JOptionPane.INFORMATION_MESSAGE);

        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setBounds((int)r.getCenterX(),(int)r.getCenterY(), 300,200);
        pack();
        setVisible(true);
    }

    public void isUpploadSuccess() {

    }


    public void isUpploadFailed() {

    }
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == JButton.class)
        {
            dispose();
        }
    }
    
    public void setMessage(String message)
    {
        JOptionPane pane =(JOptionPane) this.getContentPane();
        pane.setMessage(message);
    }

}
