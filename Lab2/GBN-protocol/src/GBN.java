import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

/**
 * ���շ���GBNЭ��ʵ��
 *
 * @author Ziyang Guo
 */
public class GBN {

    private InetAddress host;   // Ŀ��������ַ
    private int targetPort; // Ŀ�Ķ˿�
    private int myPort;     // ���ض˿�
    private int windowSize = 16;    // ���ڴ�С
    private long base = 0;          // ����base���
    private int receiveMaxTime = 4; // ����Խ��մ���
    private int loss = 10;

    public GBN(String host, int targetPort, int myPort) throws UnknownHostException {
        this.myPort = myPort;
        this.targetPort = targetPort;
        this.host = InetAddress.getByName(host);
    }

    /**
     * ��Ŀ�������˿ڷ�������
     *
     * @param content �����͵�����
     * @throws IOException IO�쳣
     */
    public void send(byte[] content) throws IOException {
        int sendIndex = 0;  // ���͵����ֽ����
        int length;
        int maxLength = 1024;   // ������ݳ���
        DatagramSocket datagramSocket = new DatagramSocket(myPort);
        List<ByteArrayOutputStream> datagramBuffer = new LinkedList<>();    // �Ե�ǰ���ڵ����ݽ��л��棬�����ط�
        List<Integer> timers = new LinkedList<>();  // ��ǰ���ڵ�����֡�ѷ��ʹ���
        long sendSeq = base;    // ���͵�����֡�����к�
        do {
            // ѭ�������ڷ���
            while(timers.size() < windowSize && sendIndex < content.length && sendSeq < 256) {
                timers.add(0);
                datagramBuffer.add(new ByteArrayOutputStream());
                length = Math.min(content.length - sendIndex, maxLength);

                // ƴ������֡������ seq + data ��˳��ƴ��
                ByteArrayOutputStream one = new ByteArrayOutputStream();
                byte[] temp = new byte[1];
                temp[0] = new Long(sendSeq).byteValue();
                one.write(temp, 0, 1);
                one.write(content, sendIndex, length);

                // ��Ŀ����������
                DatagramPacket packet = new DatagramPacket(one.toByteArray(), one.size(), host, targetPort);
                datagramSocket.send(packet);

                // �����͵������ݴ��ڻ�����
                datagramBuffer.get((int)(sendSeq - base)).write(content, sendIndex, length);
                sendIndex += length;
                System.out.println("�������ݰ���base " + base + " seq " + sendSeq);
                sendSeq ++;
            }

            // ���ó�ʱʱ��1000ms
            datagramSocket.setSoTimeout(1000);
            DatagramPacket receivePacket;

            // ѭ����Ŀ����������ack
            try {
                while(!checkWindow(timers)) {
                    byte[] recv = new byte[1500];
                    receivePacket = new DatagramPacket(recv, recv.length);
                    datagramSocket.receive(receivePacket);
                    // ȡ��ack�����к�
                    int ack = (int)((recv[0] & 0x0FF) - base);
                    timers.set(ack, -1);
                }
            } catch (SocketTimeoutException e) {
                // ����socket��ʱ���ش�����δȷ�Ϸ���
                for(int i = 0; i < timers.size(); i ++) {
                    int tempTime = timers.get(i);
                    if(tempTime != -1) {
                        ByteArrayOutputStream resender = new ByteArrayOutputStream();
                        byte[] temp = new byte[1];
                        temp[0] = new Long(i + base).byteValue();
                        resender.write(temp, 0, 1);
                        resender.write(datagramBuffer.get(i).toByteArray(), 0, datagramBuffer.get(i).size());
                        DatagramPacket datagramPacket = new DatagramPacket(resender.toByteArray(), resender.size(), host, targetPort);
                        datagramSocket.send(datagramPacket);
                        System.err.println("���·������ݰ���base " + base + " seq " + (i + base));
                        timers.set(i, 0);
                    }
                }
            }
            int i = 0;
            int s = timers.size();
            // ȷ�ϲ�ɾ�������Ѿ�ȷ�Ϲ��Ļ��棨���ڻ�����
            while(i < s) {
                if(timers.get(i) == -1) {
                    timers.remove(i);
                    datagramBuffer.remove(i);
                    base ++;
                    s --;
                } else {
                    break;
                }
            }

            // ���·������
            if(base >= 256) {
                base -= 256;
                sendSeq -= 256;
            }

        } while (sendIndex < content.length || timers.size() != 0);
        datagramSocket.close();
    }

    /**
     * ��Ŀ����������
     *
     * @return ���յ���������ֽ�
     * @throws IOException IO�쳣
     */
    public ByteArrayOutputStream receive() throws IOException {
        int time = 0;
        int count = 0;
        long receiveBase = 0;  // �������յ��ķ���
        ByteArrayOutputStream result = new ByteArrayOutputStream(); // ���������
        DatagramSocket datagramSocket = new DatagramSocket(myPort); // server����socket
        DatagramPacket receivePacket;

        datagramSocket.setSoTimeout(1000);
        while (true) {
            count ++;
            try {
                byte[] recv = new byte[1500];
                receivePacket = new DatagramPacket(recv, recv.length, host, targetPort);
                datagramSocket.receive(receivePacket);

                long seq = recv[0] & 0x0FF;
                // �������������յķ��飬����
                if(receiveBase != seq) {
                    continue;
                }

                // ģ�ⶪ��
                if(count % loss == 0) {
                    continue;
                }

                result.write(recv, 1, receivePacket.getLength() - 1);
                receiveBase ++;

                recv = new byte[1];
                recv[0] = new Long(seq).byteValue();
                receivePacket = new DatagramPacket(recv, recv.length, host, targetPort);
                datagramSocket.send(receivePacket);
                System.out.println("���յ����ݰ���seq " + seq);

                time = 0;
            } catch (SocketTimeoutException e) {
                time ++;
            }
            // ����������ʱ�䣬����ս�����д������
            if(time > receiveMaxTime) {
                break;
            }
        }
        datagramSocket.close();
        return result;
    }

    private boolean checkWindow(List<Integer> timers) {
        for (Integer timer : timers) {
            if (timer != -1)
                return false;
        }
        return true;
    }

}