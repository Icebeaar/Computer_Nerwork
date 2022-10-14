import java.io.*;
public class Server {
    public static void main(String[] args) throws IOException{
        File file1 = new File("img.png");
        File file2 = new File("serverrecv.png");
        if(!file2.exists()){
            if(!file2.createNewFile()){
                System.out.println("创建文件失败！");
                return;
            }
        }
        SR server = new SR("127.0.0.1", 8080, 7070);
        System.out.println("开始从 127.0.0.1:8080 处接收文件");
        ByteArrayOutputStream byteArrayOutputStream;
        if((byteArrayOutputStream = server.recv()).size() != 0) {
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            fileOutputStream.write(byteArrayOutputStream.toByteArray(), 0, byteArrayOutputStream.size());
            fileOutputStream.close();
            System.out.println("获取文件完成\n存为"+file2.getName()+"\n");
        }
        byteArrayOutputStream = new ByteArrayOutputStream();
        Client.cloneStream(byteArrayOutputStream, new FileInputStream(file1));
        System.out.println("开始向 127.0.0.1:7070 发送文件");
        server.send(byteArrayOutputStream.toByteArray());
    }
}
