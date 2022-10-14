import java.io.*;

public class CInternet{
    public static void main(String[] args) throws IOException {


        Proxy proxy = new Proxy();
        proxy.forbidUser.add("127.0.0.1");
        proxy.forbidHost.add("teams.microsoft.com");
        proxy.forbidHost.add("statics.teams.cdn.office.net");
        proxy.forbidHost.add("lp.open.weixin.qq.com");
        proxy.forbidHost.add("test.steampowered.com");
        proxy.forbidHost.add("self.events.data.microsoft.com");
        proxy.forbidHost.add("android.clients.google.com");
        proxy.forbidHost.add("clients4.google.com");
        proxy.forbidHost.add("play.googleapis.com");
        proxy.forbidHost.add("www.googleapis.com");
        proxy.forbidHost.add("cm2-tyo1.cm.steampowered.com");
        proxy.forbidHost.add("assets.msn.cn");
        proxy.forbidHost.add("www.bing.com");
        proxy.forbidHost.add("jwes.hit.edu.cn");
        proxy.reHost_HMap.put("jwts.hit.edu.cn", "today.hit.edu.cn");
        //proxy.reAddrMap.put("http://jwts.hit.edu.cn/loginLdapQian/", "http://today.hit.edu.cn/");

        proxy.execute();
    }

}



