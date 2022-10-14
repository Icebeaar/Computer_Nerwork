import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.LinkedList;
import java.util.List;

/**
 * ���շ���SRЭ��ʵ��
 */
public class SR {
    private InetAddress host;   // Ŀ��������ַ
    private int targetPort; // Ŀ�Ķ˿�
    private int localPort;     // ���ض˿�
    private int windowSize = 16;    // ���ڴ�С
    private int recvMaxNum = 4; // �����մ���
    private long baseNum = 0;          // ����base���
    private int loss = 10;          // ģ�ⶪ��
    public SR(String host, int targetPort, int localPort) throws UnknownHostException {
        this.localPort = localPort;
        this.targetPort = targetPort;
        this.host = InetAddress.getByName(host);
    }
    /**
     * ��Ŀ�������˿ڷ�������
     * @param content ����������
     * @throws IOException
     */
    public void send(byte[] content) throws IOException {
        int sendIndex = 0;
        int length;
        int maxLength = 1024;   // ������ݳ���
        DatagramSocket datagramSocket = new DatagramSocket(localPort);
        List<ByteArrayOutputStream> datagramBuffer = new LinkedList<>();    // �Ե�ǰ���ڵ����ݽ��л��棬�����ط�
        List<Integer> timers = new LinkedList<>();  // ��ǰ���ڵ�����֡�ѷ��ʹ���
        long sendSeq = baseNum;    // ���͵�����֡�����к�
        do {
            // ѭ�������ڷ���
            while (timers.size() < windowSize && sendIndex < content.length && sendSeq < 256) {
                timers.add(0);
                datagramBuffer.add(new ByteArrayOutputStream());
                length = Math.min(content.length - sendIndex, maxLength);

                // ƴ������֡������ base + seq + data ��˳��ƴ��
                ByteArrayOutputStream one = new ByteArrayOutputStream();
                byte[] temp = new byte[1];
                temp[0] = ((Long) baseNum).byteValue();
                one.write(temp, 0, 1);
                temp = new byte[1];
                temp[0] = ((Long) sendSeq).byteValue();
                one.write(temp, 0, 1);
                one.write(content, sendIndex, length);

                // ��Ŀ����������
                DatagramPacket packet = new DatagramPacket(one.toByteArray(), one.size(), host, targetPort);
                datagramSocket.send(packet);

                // �����͵������ݴ��ڻ�����
                datagramBuffer.get((int) (sendSeq - baseNum)).write(content, sendIndex, length);
                sendIndex += length;
                System.out.println("�������ݰ���base " + baseNum + " seq " + sendSeq);
                sendSeq++;
            }
            // ���ó�ʱʱ��1000ms
            datagramSocket.setSoTimeout(1000);
            DatagramPacket recvPacket;
            // ѭ����Ŀ����������ack
            try {
                while (!checkWindow(timers)) {
                    byte[] recv = new byte[1500];
                    recvPacket = new DatagramPacket(recv, recv.length);
                    datagramSocket.receive(recvPacket);
                    // ȡ��ack�����к�
                    int ack = (int) ((recv[0] & 0x0FF) - baseNum);
                    timers.set(ack, -1);
                }
            } catch (SocketTimeoutException ignore) {
            }
            // �ط����г������ȷ�ϴ���������֡
            for (int i = 0; i < timers.size(); i++) {
                if (timers.get(i) == 0) {
                    ByteArrayOutputStream resender = new ByteArrayOutputStream();
                    byte[] temp = new byte[1];
                    temp[0] = ((Long) baseNum).byteValue();
                    resender.write(temp, 0, 1);
                    temp = new byte[1];
                    temp[0] = ((Long) (i + baseNum)).byteValue();
                    resender.write(temp, 0, 1);
                    resender.write(datagramBuffer.get(i).toByteArray(), 0, datagramBuffer.get(i).size());
                    DatagramPacket datagramPacket = new DatagramPacket(resender.toByteArray(), resender.size(), host, targetPort);
                    datagramSocket.send(datagramPacket);
                    System.err.println("���·������ݰ���base " + baseNum + " seq " + (i + baseNum));
                    timers.set(i, 0);
                }
            }
            int i = 0;
            int timeSize = timers.size();
            // ȷ�ϲ�ɾ���Ѿ�ȷ�Ϲ��Ļ��棨�������ڣ�
            while (i < timeSize) {
                if (timers.get(i) == -1) {
                    timers.remove(i);
                    datagramBuffer.remove(i);
                    baseNum++;
                    timeSize--;
                } else {
                    break;
                }
            }

            // ���·������
            if (baseNum >= 256) {
                baseNum -= 256;
                sendSeq -= 256;
            }

        } while (sendIndex < content.length || timers.size() != 0);
        datagramSocket.close();
    }

    /**
    *��Ŀ����������
    * @return ���յ���������ֽ�
    * @throws IOException
    */
    public ByteArrayOutputStream recv() throws IOException{
        int count = 0; //���յ�������֡����
        int num = 0; //���յ�ͬһ������֡�Ĵ���
        long max = 0; //��ǰ�����յ������к�
        long recvBase = -1; //���մ��ڵ�base
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        DatagramSocket datagramSocket = new DatagramSocket(localPort); //sever����socket
        datagramSocket.setSoTimeout(1000);
        List<ByteArrayOutputStream> datagramBuffer = new LinkedList<>();//���洰���н�������֡
        DatagramPacket recvPacket;

        for(int i = 0; i<windowSize;i++){
            datagramBuffer.add(new ByteArrayOutputStream());
        }

        while(true) {
            try {
                byte[] recvByte = new byte[1500];
                recvPacket = new DatagramPacket(recvByte, recvByte.length, host, targetPort);
                datagramSocket.receive(recvPacket);

                //����ģ�ⶪ��->����֮�󲻴���
                if (count % loss != 0) {
                    long base = recvByte[0] & 0x0FF;
                    long seq = recvByte[1] & 0x0FF;
                    if (recvBase == -1) {
                        recvBase = base;
                    }
                    // �����Ͷ�base���£����Ѿ�ȷ���˼�������֡��
                    if (base != recvBase) {
                        // �ӻ�����ȡ���Ѿ�ȷ����ɵ�����֡ƴ��
                        ByteArrayOutputStream temp = getBytes(datagramBuffer, (base - recvBase) > 0 ? (base - recvBase) : max + 1);
                        // �ճ�����
                        for (int i = 0; i < base - recvBase; i++) {
                            datagramBuffer.remove(0);
                            datagramBuffer.add(new ByteArrayOutputStream());
                        }
                        result.write(temp.toByteArray(), 0, temp.size());
                        //System.out.println(result.size()+"-");
                        max -= (base - recvBase);
                        recvBase = base;
                    }
                    if (seq - base > max) {
                        max = seq - base;
                    }
                    // �����յ�������֡д�뻺��
                    ByteArrayOutputStream recvBytes = new ByteArrayOutputStream();
                    recvBytes.write(recvByte, 2, recvPacket.getLength() - 2);
                    datagramBuffer.set((int) (seq - base), recvBytes);
                    // ����ACK
                    recvByte = new byte[1];
                    recvByte[0] = ((Long)seq).byteValue();
                    recvPacket = new DatagramPacket(recvByte, recvByte.length, host, targetPort);
                    datagramSocket.send(recvPacket);
                    System.out.println("���յ����ݰ���base " + base + " seq " + seq);
                }
                count++;
                num = 0;
            } catch (SocketTimeoutException e) {
                num++;
            }
            //System.out.println(result.size());
            // ����������ʱ�䣬����ս�����д������
            if (num > recvMaxNum) {
                ByteArrayOutputStream temp = getBytes(datagramBuffer, windowSize);
                result.write(temp.toByteArray(), 0, temp.size());
                break;
            }
        }
        datagramSocket.close();
        System.out.println("��С��"+result.size());
        return result;
    }
    private ByteArrayOutputStream getBytes(List<ByteArrayOutputStream> buffer, long max) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (int i = 0; i < max; i++) {
            if(i>=buffer.size()) break;
            if (buffer.get(i) != null)
                result.write(buffer.get(i).toByteArray(), 0, buffer.get(i).size());
        }
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
