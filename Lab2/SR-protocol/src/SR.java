import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.LinkedList;
import java.util.List;

/**
 * 可收发的SR协议实现
 */
public class SR {
    private InetAddress host;   // 目的主机地址
    private int targetPort; // 目的端口
    private int localPort;     // 本地端口
    private int windowSize = 16;    // 窗口大小
    private int recvMaxNum = 4; // 最大接收次数
    private long baseNum = 0;          // 窗口base序号
    private int loss = 10;          // 模拟丢包
    public SR(String host, int targetPort, int localPort) throws UnknownHostException {
        this.localPort = localPort;
        this.targetPort = targetPort;
        this.host = InetAddress.getByName(host);
    }
    /**
     * 向目的主机端口发送内容
     * @param content 待发送内容
     * @throws IOException
     */
    public void send(byte[] content) throws IOException {
        int sendIndex = 0;
        int length;
        int maxLength = 1024;   // 最大数据长度
        DatagramSocket datagramSocket = new DatagramSocket(localPort);
        List<ByteArrayOutputStream> datagramBuffer = new LinkedList<>();    // 对当前窗口的内容进行缓存，方便重发
        List<Integer> timers = new LinkedList<>();  // 当前窗口的数据帧已发送次数
        long sendSeq = baseNum;    // 发送的数据帧的序列号
        do {
            // 循环将窗口发满
            while (timers.size() < windowSize && sendIndex < content.length && sendSeq < 256) {
                timers.add(0);
                datagramBuffer.add(new ByteArrayOutputStream());
                length = Math.min(content.length - sendIndex, maxLength);

                // 拼接数据帧，按照 base + seq + data 的顺序拼接
                ByteArrayOutputStream one = new ByteArrayOutputStream();
                byte[] temp = new byte[1];
                temp[0] = ((Long) baseNum).byteValue();
                one.write(temp, 0, 1);
                temp = new byte[1];
                temp[0] = ((Long) sendSeq).byteValue();
                one.write(temp, 0, 1);
                one.write(content, sendIndex, length);

                // 向目的主机发送
                DatagramPacket packet = new DatagramPacket(one.toByteArray(), one.size(), host, targetPort);
                datagramSocket.send(packet);

                // 将发送的内容暂存在缓存中
                datagramBuffer.get((int) (sendSeq - baseNum)).write(content, sendIndex, length);
                sendIndex += length;
                System.out.println("发送数据包：base " + baseNum + " seq " + sendSeq);
                sendSeq++;
            }
            // 设置超时时间1000ms
            datagramSocket.setSoTimeout(1000);
            DatagramPacket recvPacket;
            // 循环从目的主机接收ack
            try {
                while (!checkWindow(timers)) {
                    byte[] recv = new byte[1500];
                    recvPacket = new DatagramPacket(recv, recv.length);
                    datagramSocket.receive(recvPacket);
                    // 取出ack的序列号
                    int ack = (int) ((recv[0] & 0x0FF) - baseNum);
                    timers.set(ack, -1);
                }
            } catch (SocketTimeoutException ignore) {
            }
            // 重发所有超过最大确认次数的数据帧
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
                    System.err.println("重新发送数据包：base " + baseNum + " seq " + (i + baseNum));
                    timers.set(i, 0);
                }
            }
            int i = 0;
            int timeSize = timers.size();
            // 确认并删除已经确认过的缓存（滑动窗口）
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

            // 更新发送序号
            if (baseNum >= 256) {
                baseNum -= 256;
                sendSeq -= 256;
            }

        } while (sendIndex < content.length || timers.size() != 0);
        datagramSocket.close();
    }

    /**
    *从目的主机接收
    * @return 接收到的有序的字节
    * @throws IOException
    */
    public ByteArrayOutputStream recv() throws IOException{
        int count = 0; //接收到的数据帧个数
        int num = 0; //接收到同一个数据帧的次数
        long max = 0; //当前最大接收到的序列号
        long recvBase = -1; //接收窗口的base
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        DatagramSocket datagramSocket = new DatagramSocket(localPort); //sever监听socket
        datagramSocket.setSoTimeout(1000);
        List<ByteArrayOutputStream> datagramBuffer = new LinkedList<>();//缓存窗口中接受数据帧
        DatagramPacket recvPacket;

        for(int i = 0; i<windowSize;i++){
            datagramBuffer.add(new ByteArrayOutputStream());
        }

        while(true) {
            try {
                byte[] recvByte = new byte[1500];
                recvPacket = new DatagramPacket(recvByte, recvByte.length, host, targetPort);
                datagramSocket.receive(recvPacket);

                //进行模拟丢包->接收之后不处理
                if (count % loss != 0) {
                    long base = recvByte[0] & 0x0FF;
                    long seq = recvByte[1] & 0x0FF;
                    if (recvBase == -1) {
                        recvBase = base;
                    }
                    // 若发送端base更新（即已经确认了几个数据帧）
                    if (base != recvBase) {
                        // 从缓存中取出已经确认完成的数据帧拼接
                        ByteArrayOutputStream temp = getBytes(datagramBuffer, (base - recvBase) > 0 ? (base - recvBase) : max + 1);
                        // 空出缓存
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
                    // 将接收到的数据帧写入缓存
                    ByteArrayOutputStream recvBytes = new ByteArrayOutputStream();
                    recvBytes.write(recvByte, 2, recvPacket.getLength() - 2);
                    datagramBuffer.set((int) (seq - base), recvBytes);
                    // 返回ACK
                    recvByte = new byte[1];
                    recvByte[0] = ((Long)seq).byteValue();
                    recvPacket = new DatagramPacket(recvByte, recvByte.length, host, targetPort);
                    datagramSocket.send(recvPacket);
                    System.out.println("接收到数据包：base " + base + " seq " + seq);
                }
                count++;
                num = 0;
            } catch (SocketTimeoutException e) {
                num++;
            }
            //System.out.println(result.size());
            // 超出最大接收时间，则接收结束，写出数据
            if (num > recvMaxNum) {
                ByteArrayOutputStream temp = getBytes(datagramBuffer, windowSize);
                result.write(temp.toByteArray(), 0, temp.size());
                break;
            }
        }
        datagramSocket.close();
        System.out.println("大小："+result.size());
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
