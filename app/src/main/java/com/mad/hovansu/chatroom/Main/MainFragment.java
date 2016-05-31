package com.mad.hovansu.chatroom.Main;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.mad.hovansu.chatroom.ChatAction.Message;
import com.mad.hovansu.chatroom.ChatAction.MessageAdapter;
import com.mad.hovansu.chatroom.ConnectServer.ChatApplication;
import com.mad.hovansu.chatroom.LoginAction.LoginActivity;
import com.mad.hovansu.chatroom.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/**
 * Created by Hovansu on 20/03/2016.
 */
public class MainFragment extends Fragment {

    private static final int REQUEST_LOGIN = 0;
    private static final int TYPING_TIMER_LENGTH = 600;
    private RecyclerView mMessagesView;
    private EditText mInputMessageView;
    private List<Message> mMessages = new ArrayList<Message>();
    private RecyclerView.Adapter mAdapter;
    private boolean mTyping = false;
    private Handler mTypingHandler = new Handler();
    private String mUsername;
    private Socket mSocket;
    private String imgDecodableString;
    private MediaRecorder myRecorder;
    private MediaPlayer myPlayer;
    private String outputFile = null;
    private Bitmap emotionHappy;
    private Bitmap emotionSad;
    private Bitmap emotionAngry;

    public MainFragment() {
        super();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mAdapter = new MessageAdapter(activity, mMessages);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        ChatApplication app = (ChatApplication) getActivity().getApplication();
        mSocket = app.getmSocket();
        mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.on("new message", onNewMessage);
        mSocket.on("new image", onNewImage);
        mSocket.on("new record", onNewRecord);
        mSocket.on("user joined", onUserJoined);
        mSocket.on("user left", onUserLeft);
        mSocket.on("typing", onTyping);
        mSocket.on("stop typing", onStopTyping);
        mSocket.connect();

        startSignIn();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mSocket.disconnect();
        mSocket.off(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.off(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.off("new message", onNewMessage);
        mSocket.off("new image", onNewImage);
        mSocket.off("new record", onNewRecord);
        mSocket.off("user joined", onUserJoined);
        mSocket.off("user left", onUserLeft);
        mSocket.off("typing", onTyping);
        mSocket.off("stop typing", onStopTyping);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        emotionHappy = BitmapFactory.decodeResource(getResources(), R.drawable.emoji_happy);
        emotionSad = BitmapFactory.decodeResource(getResources(), R.drawable.emoji_sad);
        emotionAngry = BitmapFactory.decodeResource(getResources(), R.drawable.emoji_angry);

        mMessagesView = (RecyclerView) view.findViewById(R.id.messages);
        mMessagesView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mMessagesView.setAdapter(mAdapter);

        mInputMessageView = (EditText) view.findViewById(R.id.message_input);
        mInputMessageView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int id, KeyEvent event) {
                if (id == R.id.send || id == EditorInfo.IME_NULL) {
                    attemptSend();
                    return true;
                }
                return false;
            }
        });

        mInputMessageView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (null == mUsername) return;
                if (!mSocket.connected()) return;

                if (!mTyping) {
                    mTyping = true;
                    mSocket.emit("typing");
                }

                mTypingHandler.removeCallbacks(onTypingTimeout);
                mTypingHandler.postDelayed(onTypingTimeout, TYPING_TIMER_LENGTH);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        ImageButton sendButton = (ImageButton) view.findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptSend();
            }
        });

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == Activity.RESULT_OK
                    && null != data) {
                Uri selectedImage = data.getData();
                if(selectedImage!=null){
                String[] filePathColumn = {MediaStore.Images.Media.DATA};

                    Cursor cursor = getActivity().getContentResolver().query(selectedImage,
                            filePathColumn, null, null, null);
                    // Move to first row
                    cursor.moveToFirst();
                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    imgDecodableString = cursor.getString(columnIndex);
                    cursor.close();
                    //Log.d("onActivityResult",imgDecodableString);
                    MainFragment fragment = (MainFragment) getFragmentManager().findFragmentById(R.id.chat);
                    fragment.sendImage(imgDecodableString);
                } else {
                    Bitmap photo = (Bitmap) data.getExtras().get("data");
                    MainFragment fragment = (MainFragment) getFragmentManager().findFragmentById(R.id.chat);
                    fragment.sendImage(scaleDown(photo, 480, true));
                }
            } else {
                if (Activity.RESULT_OK != resultCode) {
                    //getActivity().finish();
                    return;
                }
                mUsername = data.getStringExtra("username");
                int numUsers = data.getIntExtra("numUsers", 1);

                //addLog(getResources().getString(R.string.message_welcome));
                addParticipantsLog(numUsers);
            }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id){
            case R.id.action_leave:
                leave();
                return true;
            case R.id.action_attach:
                openGallery();
                return true;
            case R.id.action_camera:
                dispatchTakePictureIntent();
                return true;
            case R.id.emoji_happy:
                //Toast.makeText(getActivity().getApplicationContext(), "You send Happy", Toast.LENGTH_LONG).show();
                sendImage(emotionHappy);
                return true;
            case R.id.emoji_sad:
                //Toast.makeText(getActivity().getApplicationContext(), "You send Sad", Toast.LENGTH_LONG).show();
                sendImage(emotionSad);
                return true;
            case R.id.emoji_angry:
                //Toast.makeText(getActivity().getApplicationContext(), "You send Angry", Toast.LENGTH_LONG).show();
                sendImage(emotionAngry);
                return true;
            case R.id.start_record:
                Toast.makeText(getActivity().getApplicationContext(), "Bắt đầu thu âm...", Toast.LENGTH_LONG).show();
                startRecord();
                return true;
            case R.id.pause_record:
                Toast.makeText(getActivity().getApplicationContext(), "Dừng thu âm", Toast.LENGTH_LONG).show();
                stopRecord();
                return true;
            case R.id.send_record:
                Toast.makeText(getActivity().getApplicationContext(), "Gửi đoạn thu âm", Toast.LENGTH_LONG).show();
                sendRecord();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void openGallery()
    {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, 1);
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, 1);
        }
    }

    private void addLog(String message) {
        mMessages.add(new Message.Builder(Message.TYPE_LOG)
                .message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addParticipantsLog(int numUsers) {
        addLog(getResources().getQuantityString(R.plurals.message_participants, numUsers, numUsers));
    }

    private void addMessage(String username, String message) {
        mMessages.add(new Message.Builder(Message.TYPE_MESSAGE)
                .username(username).message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addMyMessage(String message){
        mMessages.add(new Message.Builder(Message.TYPE_MY_MESSAGE).message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addImage(String username, Bitmap bmp){
        mMessages.add(new Message.Builder(Message.TYPE_IMAGE).username(username).image(bmp).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addMyImage(Bitmap bmp){
        mMessages.add(new Message.Builder(Message.TYPE_MY_IMAGE).image(bmp).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addTyping(String username) {
        mMessages.add(new Message.Builder(Message.TYPE_ACTION)
                .username(username).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void removeTyping(String username) {
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            Message message = mMessages.get(i);
            if (message.getType() == Message.TYPE_ACTION && message.getUsername().equals(username)) {
                mMessages.remove(i);
                mAdapter.notifyItemRemoved(i);
            }
        }
    }

    private void attemptSend() {
        if (null == mUsername) return;
        if (!mSocket.connected()) return;

        mTyping = false;

        String message = mInputMessageView.getText().toString().trim();
        if (TextUtils.isEmpty(message)) {
            mInputMessageView.requestFocus();
            return;
        }
        if(message.equals(":)")){
            mInputMessageView.setText("");
            sendImage(emotionHappy);
        } else if(message.equals(":(")){
            mInputMessageView.setText("");
            sendImage(emotionSad);
        } else if(message.equals(">.<")) {
            mInputMessageView.setText("");
            sendImage(emotionAngry);
        } else {
            mInputMessageView.setText("");
                addMyMessage(message);
            // perform the sending message attempt.
            mSocket.emit("new message", message);
        }
    }

    public void sendImage(String path){
        if (null == mUsername) return;
        if (!mSocket.connected()) return;
        mTyping = false;
        String imageText = encodeImage(path);
        Bitmap bmp = decodeImage(imageText);
        addMyImage(bmp);
        mSocket.emit("new image", imageText);
    }

    public void sendImage(Bitmap bitmap){
        if (null == mUsername) return;
        if (!mSocket.connected()) return;
        mTyping = false;
        String imageText = encodeImage(bitmap);
        addMyImage(bitmap);
        mSocket.emit("new image", imageText);
    }

    public void sendRecord(){
        //get hovansu.3gpp
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/hovansu.3gpp";
        byte[] soundByte = FileLocal_To_Byte(path);
        addMyMessage("Bạn vừa gửi đoạn ghi âm");
        mSocket.emit("new record", soundByte);
    }

    private void startSignIn() {
        mUsername = null;
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        startActivityForResult(intent, REQUEST_LOGIN);
    }

    private void leave() {
        mUsername = null;
        mSocket.disconnect();
        mSocket.connect();
        startSignIn();
    }

    public void startRecord(){
        try {
            outputFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/hovansu.3gpp";
            myRecorder = new MediaRecorder();
            myRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            myRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            myRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
            myRecorder.setOutputFile(outputFile);
            myRecorder.prepare();
            myRecorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopRecord(){
        try {
            myRecorder.stop();
            myRecorder.release();
            myRecorder  = null;
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private void scrollToBottom() {
        mMessagesView.scrollToPosition(mAdapter.getItemCount() - 1);
    }

    private String encodeImage(String path){
        File imageFile = new File(path);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(imageFile);
        } catch (FileNotFoundException e){
            e.printStackTrace();
        }
        Bitmap bm = BitmapFactory.decodeStream(fis);
        Bitmap bmResize = scaleDown(bm, 480, true);
        //Bitmap bmResize = Bitmap.createScaledBitmap(bm, 640, 480, true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmResize.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] b = baos.toByteArray();
        String encImage = Base64.encodeToString(b, Base64.DEFAULT);
        return encImage;
    }

    public static Bitmap scaleDown(Bitmap realImage, float maxImageSize,
                                   boolean filter) {
        float ratio = Math.min(
                (float) maxImageSize / realImage.getWidth(),
                (float) maxImageSize / realImage.getHeight());
        int width = Math.round((float) ratio * realImage.getWidth());
        int height = Math.round((float) ratio * realImage.getHeight());

        Bitmap newBitmap = Bitmap.createScaledBitmap(realImage, width,
                height, filter);
        return newBitmap;
    }

    private String encodeImage(Bitmap bitmap){
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] b = baos.toByteArray();
        String encImage = Base64.encodeToString(b, Base64.DEFAULT);
        return encImage;
    }

    private  Bitmap decodeImage(String data){
        byte[] b = Base64.decode(data, Base64.DEFAULT);
        Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
        return bmp;
    }

    public byte[] FileLocal_To_Byte(String path){
        File file = new File(path);
        int size = (int) file.length();
        byte[] bytes = new byte[size];
        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
            buf.read(bytes, 0, bytes.length);
            buf.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return bytes;
    }

    private void playMp3FromByte(byte[] mp3SoundByteArray) {
        try {

            File tempMp3 = File.createTempFile("myloho", "mp3", getActivity().getCacheDir());
            tempMp3.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(tempMp3);
            fos.write(mp3SoundByteArray);
            fos.close();

            MediaPlayer mediaPlayer = new MediaPlayer();

            FileInputStream fis = new FileInputStream(tempMp3);
            mediaPlayer.setDataSource(fis.getFD());

            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException ex) {
            String s = ex.toString();
            ex.printStackTrace();
        }
    }

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity().getApplicationContext(),
                            R.string.error_connect, Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    String message;
                    try {
                        username = data.getString("username");
                        message = data.getString("message");
                        removeTyping(username);
                        addMessage(username, message);
                    } catch (JSONException e) {
                        return;
                    }
                }
            });
        }
    };

    private Emitter.Listener onNewImage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    String message;
                    try {
                        username = data.getString("username");
                        message = data.getString("message");
                        removeTyping(username);
                        addImage(username, decodeImage(message));
                    } catch (JSONException e) {
                        return;
                    }
                }
            });
        }
    };
    private Emitter.Listener onNewRecord = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    String message = "Vừa gửi đoạn ghi âm";
                    byte[] soundByte;
                    try {
                        username = data.getString("username");
                        soundByte = (byte[]) data.get("message");
                        removeTyping(username);
                        addMessage(username, message);
                        playMp3FromByte(soundByte);
                    } catch (JSONException e) {
                        return;
                    }
                }
            });
        }
    };

    private Emitter.Listener onUserJoined = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_joined, username));
                    addParticipantsLog(numUsers);
                }
            });
        }
    };

    private Emitter.Listener onUserLeft = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_left, username));
                    addParticipantsLog(numUsers);
                    removeTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        return;
                    }
                    addTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onStopTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        return;
                    }
                    removeTyping(username);
                }
            });
        }
    };

    private Runnable onTypingTimeout = new Runnable() {
        @Override
        public void run() {
            if (!mTyping) return;
            mTyping = false;
            mSocket.emit("stop typing");
        }
    };
}