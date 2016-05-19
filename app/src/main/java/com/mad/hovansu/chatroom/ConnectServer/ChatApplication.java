package com.mad.hovansu.chatroom.ConnectServer;

import android.app.Application;
import java.net.URISyntaxException;
import io.socket.client.Socket;
import io.socket.client.IO;


/**
 * Created by Hovansu on 20/03/2016.
 */
public class ChatApplication extends Application{
    private io.socket.client.Socket mSocket;
    {
        try {
            mSocket = IO.socket(Constants.CHAT_SERVER_URL);
        } catch (URISyntaxException e){
            throw new RuntimeException(e);
        }
    }
    public Socket getmSocket(){
        return mSocket;
    }
}
