package paulscode.android.mupen64plusae.netplay.room;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

class NetplayRoomClientHandler
{
    static final String TAG = "ClientHandler";

    static final int ID_GET_ROOM_DATA = 0;
    static final int ID_REGISTER_TO_ROOM = 1;
    static final int ID_SEND_ROOM_DATA = 2;
    static final int ID_SEND_REGISTRATION_DATA = 3;
    static final int ID_SEND_START_PLAY = 4;

    static final int SIZE_SEND_ROOM_DATA = 63;
    static final int SIZE_SEND_REGISTRATION_DATA = 12;
    static final int ID_SIZE = 4;

    static final int DEVICE_NAME_MAX = 30;
    static final int ROM_MD5_MAX = 33;

    String mDeviceName;
    String mRomMd5;
    int mRegId;
    static int mPlayerNumber = 0;
    int mServerPort;
    String mClientDeviceName;
    Thread mClientThread;
    boolean mRunning = true;
    OutputStream mSocketOutputStream;
    InputStream mSocketInputStream;

    ByteBuffer mSendBuffer = ByteBuffer.allocate(100);
    ByteBuffer mReceiveBuffer = ByteBuffer.allocate(100);

    NetplayRoomClientHandler(String deviceName, String romMd5, int regId, int serverPort, Socket socket)
    {
        mDeviceName = deviceName;
        mRomMd5 = romMd5;
        mRegId = regId;
        mServerPort = serverPort;
        mClientDeviceName = "";

        mSendBuffer.order(ByteOrder.BIG_ENDIAN);
        mSendBuffer.mark();
        mReceiveBuffer.order(ByteOrder.BIG_ENDIAN);
        mReceiveBuffer.mark();

        try {
            mSocketOutputStream = socket.getOutputStream();
            mSocketInputStream = socket.getInputStream();
            mRunning = true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        mClientThread = new Thread(this::runClient);
        mClientThread.start();
    }

    private void handleGetRoomData() throws IOException
    {
        mSendBuffer.reset();
        // Message id
        mSendBuffer.putInt(ID_SEND_ROOM_DATA);

        // Device name, 30 bytes
        byte[] deviceNameBytes = mDeviceName.getBytes(StandardCharsets.ISO_8859_1);
        byte[] sendDeviceNameBytes = new byte[DEVICE_NAME_MAX];
        Arrays.fill(sendDeviceNameBytes, (byte)0);
        System.arraycopy(deviceNameBytes, 0, sendDeviceNameBytes, 0, Math.min(deviceNameBytes.length, sendDeviceNameBytes.length));
        sendDeviceNameBytes[sendDeviceNameBytes.length-1] = 0;
        mSendBuffer.put(sendDeviceNameBytes, 0, sendDeviceNameBytes.length);

        // Rom MD5, 33 bytes
        byte[] romMd5Bytes = mRomMd5.getBytes(StandardCharsets.ISO_8859_1);
        byte[] sendRomMd5Bytes = new byte[ROM_MD5_MAX];
        System.arraycopy(romMd5Bytes, 0, sendRomMd5Bytes, 0, Math.min(romMd5Bytes.length, sendRomMd5Bytes.length));
        sendRomMd5Bytes[sendRomMd5Bytes.length-1] = 0;
        mSendBuffer.put(sendRomMd5Bytes, 0, sendRomMd5Bytes.length);

        mSocketOutputStream.write(mSendBuffer.array(), 0, mSendBuffer.position());
    }

    public synchronized static void resetPlayers()
    {
        mPlayerNumber = 0;
    }

    private synchronized static int getNextPlayer()
    {
        ++mPlayerNumber;
        return mPlayerNumber;
    }

    private void handleRegisterToRoom() throws IOException
    {
        Log.i(TAG, "Requesting room registration");

        byte[] receiveDeviceNameBytes = new byte[DEVICE_NAME_MAX];

        int offset = 0;
        while (offset < DEVICE_NAME_MAX) {
            int bytesRead = mSocketInputStream.read(receiveDeviceNameBytes, offset, DEVICE_NAME_MAX - offset);
            offset += bytesRead != -1 ? bytesRead : 0;
        }

        mClientDeviceName = new String(receiveDeviceNameBytes);

        mSendBuffer.reset();
        // Message id
        mSendBuffer.putInt(ID_SEND_REGISTRATION_DATA);
        // Registration id
        mSendBuffer.putInt(mRegId);
        // Player number
        int playerNumber = getNextPlayer();
        mSendBuffer.putInt(playerNumber);
        // Server port
        mSendBuffer.putInt(mServerPort);
        mSocketOutputStream.write(mSendBuffer.array(), 0, mSendBuffer.position());
    }

    public void sendStart()
    {
        mSendBuffer.reset();
        mSendBuffer.putInt(ID_SEND_START_PLAY);

        try {
            mSocketOutputStream.write(mSendBuffer.array(), 0, mSendBuffer.position());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void runClient()
    {
        while (mRunning)
        {
            // First read the whole message
            try {

                int offset = 0;
                mReceiveBuffer.reset();
                while (offset < ID_SIZE) {
                    int bytesRead = mSocketInputStream.read(mReceiveBuffer.array(), offset, ID_SIZE - offset);
                    offset += bytesRead != -1 ? bytesRead : 0;
                }

                int id = mReceiveBuffer.getInt();

                Log.i(TAG, "Got message with id=" + id);

                if (id == -1) {
                    mRunning = false;
                }

                if (id == ID_GET_ROOM_DATA) {
                    handleGetRoomData();
                }
                else if (id == ID_REGISTER_TO_ROOM) {
                    handleRegisterToRoom();
                }
                else {
                    mRunning = false;
                }
            } catch (IOException e) {
                e.printStackTrace();
                mRunning = false;
                break;
            }
        }
    }
}