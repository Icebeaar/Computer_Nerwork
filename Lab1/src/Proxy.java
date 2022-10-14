import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;

public class Proxy {

    /**
     * ��ʼ���������ض�������
     */
    Map<String, String> reHost_HMap = new HashMap<>();

    Map<String, String> reAddrMap = new HashMap<>();

    Set<String> forbidHost = new HashSet<>();

    Set<String> forbidUser = new HashSet<>();

    /**
     * �ض�������
     *
     * @param oriHost Դ����
     * @return �ض��������
     */
    String redirectHost(String oriHost) {
        Set<String> keywordSet = reHost_HMap.keySet();
        for (String keyword : keywordSet) {
            if (oriHost.contains(keyword)) {
                System.out.println("Դ����: " + oriHost);
                String redHost = reHost_HMap.get(oriHost);
                System.out.println("�ض�������Host: " + redHost);
                return redHost;
            }
        }
        return oriHost;
    }

    /**
     * �ض����ַ
     * ����redirectAddrMap�д�ŵ�Host����Address Map��ȡ�ض����ַ
     *
     * @param oriHost Դ����
     * @return �ض����ĵ�ַ
     */
    String redirectAddr(String oriHost, String visitAddr) {
        Set<String> keywordSet = reAddrMap.keySet();
        for (String keyword : keywordSet) {
            if (oriHost != null && keyword.contains(oriHost)) {
                //ֱ����ת
                if (visitAddr.equals(keyword)) {
                    return reAddrMap.get(keyword);
                }
                if (visitAddr.contains(oriHost)) {
                    String[] temp = visitAddr.split(oriHost);  // ���ո�ָ�
                    String redHost = reHost_HMap.get(oriHost);
                    return temp[0] + redHost + temp[1];
                }

            }
        }
        return visitAddr;
    }


    Map<String, String> parse(String header) {
        if (header.length() == 0) {
            return new HashMap<>();
        }
        String[] lines = header.split("\\n");
        String method = null;
        String visitAddr = null;
        String httpVersion = null;
        String hostName = null;
        String path = null;
        String fullPath = null;
        String portString = null;
        for (String line : lines) {
            if ((line.contains("GET") || line.contains("POST") || line.contains("CONNECT")) && method == null) {
                // ����GET website HTTP/1.1
                String[] temp = line.split("\\s");  // ���ո�ָ�
                method = temp[0];
                visitAddr = temp[1];
                httpVersion = temp[2];

                String[] temp1 = visitAddr.split(":");
                if (visitAddr.contains("http://") || visitAddr.contains("https://")) {

                    if (temp1.length >= 3) {
                        portString = temp1[2];
                    }
                } else {

                    if (temp1.length >= 2) {
                        portString = temp1[1];
                    }
                }

            } else if (line.contains("Host: ") && hostName == null) {
                String[] temp = line.split("\\s");
                hostName = temp[1];
                String[] temp1 = visitAddr.split("\\?");
                fullPath = temp1[0];
                String[] temp2 = fullPath.split(hostName);
                if (temp2.length > 1) {
                    path = temp2[1];
                } else {
                    path = "";
                }
                int colonIndex = hostName.indexOf(':');
                if (colonIndex != -1) {
                    hostName = hostName.substring(0, colonIndex);
                }
            }
        }

        Map<String, String> map = new HashMap<>();
        // �������map
        map.put("method", method);
        map.put("visitAddr", visitAddr);
        map.put("httpVersion", httpVersion);
        map.put("host", hostName);
        map.put("path", path);
        map.put("fullPath", fullPath);


        map.put("port", Objects.requireNonNullElse(portString, "80"));
        return map;
    }

    /**
     * �����ض�����
     */
    String RedirectHTTP(String header, String newHost, String newAddr) {
        if (header.length() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String[] lines = header.split("\\n");
        for (String line : lines) {
            if ((line.contains("GET") || line.contains("POST") || line.contains("CONNECT"))) {
                // ����GET website HTTP/1.1
                String[] temp = line.split("\\s");  // ���ո�ָ�
                sb.append(temp[0]).append(" ").append(newAddr).append(" ").append(temp[2]).append("\n");

            } else if (line.contains("Host: ")) {
                String[] temp = line.split("\\s");
                sb.append(temp[0]).append(newHost).append("\n");

            } else if (line.contains("Referer: ")) {
                String[] temp = line.split("\\s");
                sb.append(temp[0]).append(newAddr).append("\n");

            } else if (line.contains("Accept: ")) {
                String[] temp = line.split("\\s");
                sb.append(temp[0]).append("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8").append("\n");

            } else {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    void execute() throws IOException {
        // ����ָ���Ķ˿�
        int port = 10240;
        ServerSocket server = new ServerSocket(port);
        // server��һֱ�ȴ����ӵĵ���
        System.out.println("�������������,�����˿ڣ�" + server.getLocalPort());


        while (true) {
            Socket clientSocket = server.accept();
            String UserIP = clientSocket.getInetAddress().getHostAddress();
            System.out.println("��ȡ��һ�����ӣ����� " + UserIP);
            if (forbidUser.contains(UserIP)) {
                System.out.println("�û�:" + UserIP + "�ѱ���ֹ");
                PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
                pw.println("Forbid User!");
                pw.close();
                clientSocket.close();
                continue;
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    String host = "", path = "", fullPath = "";
                    try {
                        // ����header
                        InputStreamReader r = new InputStreamReader(clientSocket.getInputStream());
                        OutputStream clientOutput = clientSocket.getOutputStream();

                        BufferedReader br = new BufferedReader(r);
                        String readLine = br.readLine();

                        StringBuilder headerBuilder = new StringBuilder();

                        while (readLine != null && !readLine.equals("")) {
                            headerBuilder.append(readLine).append("\n");
                            readLine = br.readLine();
                        }

                        if (headerBuilder.toString().length() == 0) {
                            System.out.println("HTTPͷΪ�գ�");
                            return;
                        }
                        String header = headerBuilder.toString();

                        System.out.println("\n-----------------");
                        System.out.print("�����������ȡ��HTTPͷ�� ����" + headerBuilder.toString().length() + "\n" + headerBuilder);
                        System.out.println("-----------------");

                        // ������������֮���ж�


                        Map<String, String> map = parse(headerBuilder.toString());

                        host = map.get("host"); // host
                        path = map.get("path");
                        fullPath = map.get("fullPath");
                        // �˿�
                        int visitPort = Integer.parseInt(map.getOrDefault("port", "80"));
                        // ���ʵ���վ
                        String visitAddr = map.get("visitAddr");
                        // method
                        String method = map.getOrDefault("method", "GET");
                        if (forbidHost.contains(host)) {
                            System.out.println("�����˽�ֹ���ʵ���վ��");
                            PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
                            pw.println("Website: " + visitAddr + " is forbid to visit because " + host + "  is a forbid host");
                            pw.close();
                            clientSocket.close();
                            return;

                        }
                        System.out.println("��ʼ��ַ��" + visitAddr);
                        String redirectHost = redirectHost(host);
                        if (!host.equals(redirectHost)) {
                            visitAddr = redirectAddr(host, visitAddr);
                            host = redirectHost;
                            String[] temp1 = visitAddr.split("\\?");
                            fullPath = temp1[0];
                            String[] temp2 = fullPath.split(host);
                            if (temp2.length > 1) {
                                path = temp2[1];
                            } else {
                                path = "";
                            }
                            header = RedirectHTTP(header, host, visitAddr);
                        }


                        String pathname = "cache/" + (host + path).replace('/', '_').replace(':', '+') + ".cache";
                        File cacheFile = new File(pathname);
                        boolean useCache = false;
                        boolean existCache = cacheFile.exists() && cacheFile.length() != 0;
                        String lastModified = "Thu, 01 Jul 1970 20:00:00 GMT";

                        if (existCache) {
                            System.out.println(visitAddr + "���ڱ��ػ����ļ�");
                            // ����޸�ʱ��
                            long time = cacheFile.lastModified();
                            SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
                            Calendar cal = Calendar.getInstance();
                            cal.setTimeInMillis(time);
                            cal.set(Calendar.HOUR, -7);
                            cal.setTimeZone(TimeZone.getTimeZone("GMT"));
                            lastModified = formatter.format(cal.getTime());
                            System.out.println("���潨��ʱ�䣺" + cal.getTime());
                        }


                        Socket proxySocket = new Socket(host, 80);
                        System.out.println("�����׽����ѽ���!:" + proxySocket);
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(proxySocket.getOutputStream()));


                        StringBuffer requestBuffer = new StringBuffer();
                        requestBuffer.append(header);
                        if (existCache)
                            requestBuffer.append("If-Modified-Since: ").append(lastModified).append("\n");


                        writer.write(requestBuffer.append("\n").toString()); // ���ͱ���
                        writer.flush();

                        System.out.println("\n-----------------");
                        System.out.print("���������ת�����ģ�\n" + requestBuffer.toString());
                        System.out.println("-----------------");


                        // ��Զ�̷�������������������
                        BufferedInputStream remoteInputStream = new BufferedInputStream(proxySocket.getInputStream());
                        System.out.println("��ȡ���ԣ�" + host + "��������");

                        // ��ʹ��һ��С�ֽڻ�����ͷ��
                        byte[] tempBytes = new byte[20];/////
                        int len = remoteInputStream.read(tempBytes);
                        String res = new String(tempBytes);
                        useCache = (res.contains("304") || (res.contains("200"))) && cacheFile.length() != 0;

                        System.out.println("HTTP ״̬��" + res + "\n-----------------");

                        if (useCache) {
                            // �û���
                            // ���������������������
                            System.out.println(visitAddr + " ���ػ�����δ���ڣ�ʹ�û���");
                            System.out.println(visitAddr + " ����ʹ�û������,���泤��" + cacheFile.length());
                            // �����ļ���д
                            FileInputStream fileInputStream = new FileInputStream(cacheFile);
                            int bufferLength = 1;
                            byte[] buffer = new byte[bufferLength];

                            while (true) {
                                int count = fileInputStream.read(buffer);
                                //System.out.println(count + "�ӻ����м�����ҳ..." + visitAddr);
                                if (count == -1) {
                                    System.out.println("�ӻ����м�����ɣ�");
                                    break;
                                }
                                clientOutput.write(buffer);
                            }
                            clientOutput.flush();
                        } else {
                            System.out.println(visitAddr + " ���ػ�����ڻ򲻿��ã���ʹ�û���");
                            clientOutput.write(tempBytes);
                        }

                        FileOutputStream fileOutputStream =
                                new FileOutputStream(
                                        pathname);
                        if (!useCache) {
                            fileOutputStream.write(tempBytes);
                        }
                        int bufferLength = 1;
                        byte[] buffer = new byte[bufferLength];
                        while (true) {
                            int count = remoteInputStream.read(buffer);
                            if (count == -1) {
                                break;
                            }
                            if (!useCache) {
                                clientOutput.write(buffer);
                                fileOutputStream.write(buffer);

                            }
                        }
                        fileOutputStream.flush();   // ������ļ�
                        fileOutputStream.close();   // �ر��ļ���

                        clientOutput.flush();   // ����������
                        writer.close();

                        remoteInputStream.close();
                        System.out.println(host + "�����Ѿ���ɣ�");

                        proxySocket.close();    // �ر�����Զ�̷�������socket
                        clientSocket.close();// �ر������������socket

                    } catch (IOException e) {
                        System.out.println(host + "�����쳣��");
                        e.printStackTrace();


                    } catch (StringIndexOutOfBoundsException e) {
                        System.out.println(host + "���ر��ĳ����쳣��");
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

}
