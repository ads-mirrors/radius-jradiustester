package org.toughradius.tester.command;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.toughradius.tester.common.FileUtil;
import org.toughradius.tester.common.StringUtil;
import org.toughradius.tester.config.RadiusConfig;

@ShellComponent
public class ConfigCommand {

    @Autowired
    private RadiusConfig radiusConfig;

    @ShellMethod("setup radius config")
    public String setup(String server, int authport, int acctport) {
        radiusConfig.setServer(server);
        radiusConfig.setAuthport(authport);
        radiusConfig.setAcctport(acctport);
        return "setup done";
    }

    @ShellMethod("setup radius server")
    public String setupServer(String server) {
        radiusConfig.setServer(server);
        return "setup done";
    }

    @ShellMethod("setup radius authport")
    public String setupAuthport(int port) {
        radiusConfig.setAuthport(port);
        return "setup done";
    }

    @ShellMethod("setup radius acctport")
    public String setupAcctport(int port) {
        radiusConfig.setAcctport(port);
        return "setup done";
    }


    @ShellMethod("setup radius secret")
    public String setupSecret(String secret) {
        radiusConfig.setSecret(secret);
        return "setup done";
    }

    @ShellMethod("setup radius tester userfile")
    public String setupUserfile(String userfile) {
        radiusConfig.setUserfile(userfile);
        return "setup done";
    }


    @ShellMethod("gen test users")
    public String genUsers(String prefix,String passwd,int num) {
        int width = String.valueOf(num).length();
        StringBuilder buff = new StringBuilder();
        for(int i = 0;i<num;i++){
            buff.append(prefix).append(String.format("%0"+width+"d",i+1)).append(",").append(passwd).append("\r\n");
        }
        FileUtil.writeFile(radiusConfig.getUserfile(),buff.toString());
        return "done";
    }


    @ShellMethod("print radius config")
    public String printConfig() {
        return radiusConfig.toString();
    }

}
