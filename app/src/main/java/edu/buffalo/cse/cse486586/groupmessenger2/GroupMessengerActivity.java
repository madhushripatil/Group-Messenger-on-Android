package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final ArrayList<String> remotePorts = new ArrayList<String>();
    static final int SERVER_PORT = 10000;
    static final String SCHEME = "content";
    static final String AUTHORITY = "edu.buffalo.cse.cse486586.groupmessenger2.provider";
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    private ContentResolver mContentResolver;
    private Uri mUri = null;
    private static int largestPropSeqNum = 0;
    private static int largestAgreedSeqNum = 0;

    private static String crashedAVD = "";
    public int uniqueMsgId = 1;
    Map<Integer, Message> messageMap = new TreeMap<Integer, Message>();

    public GroupMessengerActivity() {
        remotePorts.add("11108");
        remotePorts.add("11112");
        remotePorts.add("11116");
        remotePorts.add("11120");
        remotePorts.add("11124");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(AUTHORITY);
        uriBuilder.scheme(SCHEME);
        mUri = uriBuilder.build();
        mContentResolver = getContentResolver();

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * Calculate the port number that this AVD listens on.
         * It is just a hack that I came up with to get around the networking limitations of AVDs.
         * The explanation is provided in the PA1 spec.
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        
        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            /*
             * Log is a good way to debug your code. LogCat prints out all the messages that
             * Log class writes.
             *
             * Please read http://developer.android.com/tools/debugging/debugging-projects.html
             * and http://developer.android.com/tools/debugging/debugging-log.html
             * for more information on debugging.
             */
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        final EditText editText = (EditText) findViewById(R.id.editText1);

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /*
                 * If the key is pressed (i.e., KeyEvent.ACTION_DOWN) and it is an enter key
                 * (i.e., KeyEvent.KEYCODE_ENTER), then we display the string. Then we create
                 * an AsyncTask that sends the string to the remote AVD.
                 */
                String msg = editText.getText().toString() + "\n";
                editText.setText(""); // This is one way to reset the input box.

                /*
                 * Note that the following AsyncTask uses AsyncTask.SERIAL_EXECUTOR, not
                 * AsyncTask.THREAD_POOL_EXECUTOR as the above ServerTask does. To understand
                 * the difference, please take a look at
                 * http://developer.android.com/reference/android/os/AsyncTask.html
                 */
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    /***
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     *
     * Please make sure you understand how AsyncTask works by reading
     * http://developer.android.com/reference/android/os/AsyncTask.html
     *
     * @author stevko
     *
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Socket socketConnection = null;
            int cnt = 0;

            // Server listens to establish any connections on the socket
            while(true) {
                try {

                    // Server waits for a connection to be established on this socket
                    // Reference: https://developer.android.com/reference/java/net/ServerSocket.html#accept()
                    socketConnection = serverSocket.accept();
                    DataInputStream inputStream = new DataInputStream(socketConnection.getInputStream());

                    // Server reads data from the socket's input stream
                    // Reference: https://developer.android.com/reference/java/io/DataInputStream#readUTF()
                    String clientMessage = inputStream.readUTF();

                    DataOutputStream outputStream;
                    String []tokens = clientMessage.split("##");

                    largestPropSeqNum = Math.max(largestPropSeqNum, largestAgreedSeqNum) + 1;

                    if("NewMsg".equals(tokens[3])) {

                        Log.e("SERVER-CODE",  "@@Local sequence number from Server:  " + String.valueOf(largestPropSeqNum));
                        Message m = new Message(Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]), tokens[0], largestPropSeqNum, tokens[3]);
                        messageMap.put(largestPropSeqNum, m);
                        Log.i("ARRIVED_NEW_MSG", m.msg +"  " + m.senderId);
                        outputStream = new DataOutputStream(socketConnection.getOutputStream());
                        outputStream.writeUTF(String.valueOf(largestPropSeqNum));
                    }

                    clientMessage = inputStream.readUTF();
                    tokens = clientMessage.split("##");
                    Log.i("SEQUENCE_MSG_FOR", clientMessage);
                    Log.e("SERVER-MSG RCVD", "Message rcvd with proposed Number: " + clientMessage);

                    for(Map.Entry<Integer, Message> mm : messageMap.entrySet()) {
                        if(mm.getValue().messageId == Integer.parseInt(tokens[1]) && mm.getValue().senderId == Integer.parseInt(tokens[2])) {
                            messageMap.remove(mm.getKey());
                            break;
                        }
                    }
                    largestAgreedSeqNum = Math.max(largestAgreedSeqNum,Integer.parseInt(tokens[4]));
                    Message m = new Message(Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]), tokens[0], largestAgreedSeqNum, tokens[3]);
                    messageMap.put(largestAgreedSeqNum, m);

                    for(Map.Entry<Integer, Message> mm : messageMap.entrySet()) {
                        Message m2 = mm.getValue();
                        String finalMsg = m2.msg;
                        if(m2.deliverable) {
                            ContentValues cv = new ContentValues();
                            cv.put(KEY_FIELD, cnt);
                            cv.put(VALUE_FIELD, finalMsg.trim());
                            mContentResolver.insert(mUri, cv);
                            Log.e(TAG, "Message in file " + m2.sequenceNumber + ": " + finalMsg);
                            Log.e(TAG,"File  " + cnt + " created!");
                            cnt++;
                            messageMap.remove(mm.getKey());
                        }

                        // Sends the read message to UI thread to be displayed on the UI
                        // Reference: https://developer.android.com/reference/android/os/AsyncTask.html#publishProgress(Progress...)
                        String temp = String.valueOf(cnt) + finalMsg;
                        publishProgress(temp);
                    }

                } catch (IOException e) {
                    if(socketConnection != null) {
                        try {
                            socketConnection.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                    Log.e(TAG, "Error reading data from input stream!");
                }
            }
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView textView = (TextView) findViewById(R.id.textView1);
            textView.append(strReceived + "\n");

            return;
        }
    }

    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            String msgToSend = msgs[0];
            String newMsg = msgToSend;
            int max = -1;
            uniqueMsgId++;
            Log.e(TAG, "@@@@@ Sending new msg:  " + msgToSend);

            List<Socket> socketList = new ArrayList<Socket>();
            List<DataInputStream> inputStream = new ArrayList<DataInputStream>();
            List<DataOutputStream> outputStream = new ArrayList<DataOutputStream>();
            List<Integer> proposedNums = new ArrayList<Integer>();

            Socket s = null;
            DataInputStream inStream = null;
            DataOutputStream outStream = null;
            String p = null;

            int i = 0;
            for(i=0; i<remotePorts.size(); i++) {
                try {

                    p = remotePorts.get(i);
                    s = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(p));
                    socketList.add(s);
                    outStream = new DataOutputStream(s.getOutputStream());
                    outputStream.add(outStream);
                    msgToSend = newMsg;
                    // new msg being sent
                    msgToSend = msgToSend + "##" + String.valueOf(uniqueMsgId) + "##" + msgs[1] + "##" + "NewMsg";

                    // Write client's message on the output stream
                    // Reference: https://developer.android.com/reference/java/io/DataOutputStream#writeUTF(java.lang.String)
                    outStream.writeUTF(msgToSend);

                    inStream = new DataInputStream(s.getInputStream());
                    inputStream.add(inStream);

                    Log.e("READ PORT: ", p);
                    String proposedNumber = inStream.readUTF();
                    int propNum = Integer.parseInt(proposedNumber);
                    proposedNums.add(propNum);
                    outStream.flush();

                } catch (Exception e){

                    e.printStackTrace();
                    Log.e("FAILED", "FAILED SOCKET ");
                    crashedAVD = p;
                    Log.e("CLIENT_TASK FAILURE 1", "FAILED SOCKET " + crashedAVD);
                    i--;
                    if(remotePorts.remove(crashedAVD)) {
                        try {
                            inStream.close();
                            outStream.close();
                            s.close();
                            inputStream.remove(inStream);
                            outputStream.remove(outStream);
                            socketList.remove(s);
                        } catch (Exception e1) {
                            Log.e("IOException", "IOException");
                        }
                    }
                }
            }

            Log.e(TAG, "Proposed Numbers received: \n");
            for(Integer pno : proposedNums) {
                Log.e(TAG, String.valueOf(pno));
            }
            if(proposedNums.size()>0) {
                max = Collections.max(proposedNums);
                Log.e(TAG ,"@@@@@ Max Number final:  " + max);

                if (max != -1) {
                    msgToSend = newMsg;
                    msgToSend = msgToSend + "##" + String.valueOf(uniqueMsgId) + "##" + msgs[1] + "##" + "OldMsg" + "##" + max;
                    Log.e(TAG, "Message with final Proposed Number: " + msgToSend);
                }
            }

            int j = 0;

            for(DataOutputStream d: outputStream) {

                Log.e("SIZE of OUTPUTSTREAM", "  " + outputStream.size());
                Log.e("SIZE of INPUTSTREAM", "  " + inputStream.size());
                Log.e("SIZE of PORTS", "  " + remotePorts.size());
                Log.e("SIZE of SOCKETS", "  " + socketList.size());

                try {
                    s = socketList.get(j);
                    inStream = inputStream.get(j);
                    outStream = outputStream.get(j);
                    p = remotePorts.get(j);

                    // accepted proposal number
                    d.writeUTF(msgToSend);

                } catch (Exception e ) {
                    e.printStackTrace();
                    Log.e("FAILED", "FAILED SOCKET ");
                    crashedAVD = p;
                    Log.e("CLIENT_TASK FAILURE 2", "FAILED SOCKET " + crashedAVD);

                    if(remotePorts.remove(crashedAVD)) {
                        try {
                            inStream.close();
                            outStream.close();
                            s.close();
                            inputStream.remove(inStream);
                            outputStream.remove(outStream);
                            socketList.remove(s);
                        } catch (Exception e1) {
                            Log.e("IOException", "IOException");
                        }
                    }
                } finally {
                    try {
                        inStream.close();
                        outStream.close();
                        s.close();
                    } catch (Exception e) {
                        Log.e("SOCKETCLOSE", "Socket closed!");
                    }
                }
            }
            return null;
        }
    }

    public class Message {
        int messageId;
        int senderId;
        String msg;
        int sequenceNumber = 0;
        boolean deliverable = false;

        Message(int messageId, int senderId, String msg, int sequenceNumber, String newMsg) {

            this.messageId = messageId;
            this.senderId = senderId;
            this.msg = msg;
            this.sequenceNumber = sequenceNumber;

            if (!(newMsg.equals("NewMsg"))) {
                deliverable = true;
            }
        }
    }
}
