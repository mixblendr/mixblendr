package com.mixblendr.audio;

import com.mixblendr.util.FatalExceptionListener;
import static com.mixblendr.util.Debug.error;

import java.io.*;
import java.net.*;
public class Sender
{
    private String hostUrl = "http://localhost/getfile.php";

    /** listener for exceptions in io thread */
    protected FatalExceptionListener fatalExceptionListener = null;

    /**
     * @param fatalExceptionListener the fatalExceptionListener to set
     */
    void setFatalExceptionListener(FatalExceptionListener fatalExceptionListener) {
        this.fatalExceptionListener = fatalExceptionListener;
    }


    public Sender(){

    }

    public Sender(String url) {
        hostUrl = url;    
    }

    public void sendFile(File tempFile, String filename)   {
        try
        {
            URL url = new URL(hostUrl);
            String boundary = MultiPartFormOutputStream.createBoundary();
            URLConnection urlConnection = MultiPartFormOutputStream.createConnection(url);
            urlConnection.setRequestProperty("Accept", "*/*");
            urlConnection.setRequestProperty("Content-Type",
                MultiPartFormOutputStream.getContentType(boundary));

            // set some other request headers...
            urlConnection.setRequestProperty("Connection", "Keep-Alive");
            urlConnection.setRequestProperty("Cache-Control", "no-cache");
            // no need to connect cuz getOutputStream() does it
            MultiPartFormOutputStream out =
                new MultiPartFormOutputStream(urlConnection.getOutputStream(), boundary);
            // write a text field element
            //out.writeField("myText", "text field text");
            // upload a file
            FileInputStream fis = new FileInputStream(tempFile);
            //out.writeFile("uploaded", "text/plain", filename, fis);
            out.writeFile("uploaded", "binary", filename, fis);
            out.close();

            // read response from server
            BufferedReader in = new BufferedReader(
                new InputStreamReader(urlConnection.getInputStream()));
            String line = "";
            boolean bResult = false;
            while((line = in.readLine()) != null) {
                 System.out.println(line);
                if (line.indexOf("OK") != -1)
                {
                    bResult = true;
                }
            }
            in.close();

            if (bResult) {
                fatalExceptionListener.setSuccess();
                //fatalExceptionListener.showMessage("Publishing", "Publishing is successfull!");
            }
            else {
                fatalExceptionListener.setFailed();
                //fatalExceptionListener.fatalExceptionOccured(null, "Publishing track to the server is failed.");    
            }

        }
        catch (Throwable t) {
            if (fatalExceptionListener != null) {
                fatalExceptionListener.hideProgressDialog();
                fatalExceptionListener.fatalExceptionOccured(t,"Sending file to server is failed.");
            } else {
                error(t);
            }

        }
    }
}
