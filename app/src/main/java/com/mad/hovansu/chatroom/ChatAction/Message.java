package com.mad.hovansu.chatroom.ChatAction;

import android.graphics.Bitmap;

/**
 * Created by Hovansu on 20/03/2016.
 */

public class Message {

    public static final int TYPE_MESSAGE = 0;
    public static final int TYPE_LOG = 1;
    public static final int TYPE_ACTION = 2;
    public static final int TYPE_IMAGE = 3;
    public static final int TYPE_MY_MESSAGE = 4;
    public static final int TYPE_MY_IMAGE = 5;

    private int mType;
    private String mMessage;
    private String mUsername;
    private Bitmap mImage;

    private Message() {}

    public int getType() {
        return mType;
    };

    public String getMessage() {
        return mMessage;
    };

    public String getUsername() {
        return mUsername;
    };

    public Bitmap getImage() {
        return mImage;
    }

    public static class Builder {
        private final int mType;
        private String mUsername;
        private String mMessage;
        private Bitmap mImage;

        public Builder(int type) {
            mType = type;
        }

        public Builder username(String username) {
            mUsername = username;
            return this;
        }

        public Builder message(String message) {
            mMessage = message;
            return this;
        }

        public Builder image(Bitmap image){
            mImage = image;
            return this;
        }

        public Message build() {
            Message message = new Message();
            message.mType = mType;
            message.mUsername = mUsername;
            message.mMessage = mMessage;
            message.mImage = mImage;
            return message;
        }
    }
}
