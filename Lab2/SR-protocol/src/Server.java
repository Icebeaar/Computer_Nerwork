import java.io.*;
public class Server {
    public static void main(String[] args) throws IOException{
        File file1 = new File("img.png");
        File file2 = new File("serverrecv.png");
        if(!file2.exists()){
            if(!file2.createNewFile()){
                System.out.println("�����ļ�ʧ�ܣ�");
                return;
            }
        }
        SR server = new SR("127.0.0.1", 8080, 7070);
        System.out.println("��ʼ�� 127.0.0.1:8080 �������ļ�");
        ByteArrayOutputStream byteArrayOutputStream;
        if((byteArrayOutputStream = server.recv()).size() != 0) {
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            fileOutputStream.write(byteArrayOutputStream.toByteArray(), 0, byteArrayOutputStream.size());
            fileOutputStream.close();
            System.out.println("��ȡ�ļ����\n��Ϊ"+file2.getName()+"\n");
        }
        byteArrayOutputStream = new ByteArrayOutputStream();
        Client.cloneStream(byteArrayOutputStream, new FileInputStream(file1));
        System.out.println("��ʼ�� 127.0.0.1:7070 �����ļ�");
        server.send(byteArrayOutputStream.toByteArray());
    }
}
