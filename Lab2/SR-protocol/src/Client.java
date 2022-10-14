import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Client {
    public static void main(String[] args) throws IOException, InterruptedException {
        File file1 = new File("img.png");
        File file2 = new File("clientrecv.png");
        if(!file2.exists()) {
            if(!file2.createNewFile()) {
                System.out.println("创建文件失败！");
                return;
            }
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        cloneStream(byteArrayOutputStream, new FileInputStream(file1));
        SR client = new SR("127.0.0.1", 7070, 8080);
        System.out.println("开始向 127.0.0.1:7070 发送文件");
        client.send(byteArrayOutputStream.toByteArray());

        System.out.println("开始从 127.0.0.1:7070 处接收文件");
        Thread.sleep(50);
        if((byteArrayOutputStream = client.recv()).size() != 0) {
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            fileOutputStream.write(byteArrayOutputStream.toByteArray());
            fileOutputStream.close();
            System.out.println("获取文件完成\n存为"+file2.getName());
        }
    }

    public static void cloneStream(ByteArrayOutputStream res, InputStream in) throws IOException {
        byte[] buffer = new byte[1024];
        int length;
        while((length = in.read(buffer)) >= 0) {
            res.write(buffer, 0, length);
        }
        res.flush();
    }
}
