package org.toughradius.tester.command;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.tinyradius.packet.AccessRequest;
import org.tinyradius.packet.AccountingRequest;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusClient;
import org.tinyradius.util.RadiusException;
import org.tinyradius.util.RadiusUtil;
import org.toughradius.tester.common.CoderUtil;
import org.toughradius.tester.config.RadiusConfig;

import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

@ShellComponent
public class TesterCommand {

    private final static Map<String,String> ipmap = new HashMap<>();
    private final static Map<String,String> nasportidmap = new HashMap<>();
    private final static Map<String,String> macmap = new HashMap<>();

    @Autowired
    private RadiusConfig radiusConfig;

    @ShellMethod("send radius auth")
    public String sendAuth(String user, String pwd) {
        try {
            long start = System.currentTimeMillis();
            RadiusClient cli = radiusConfig.getClient();
            AccessRequest request = new AccessRequest();
            request.setUserName(user);
            request.setUserPassword(pwd);
            request.setAuthProtocol(radiusConfig.getAuthProtocol());
            request.addAttribute("Service-Type","Framed-User");
            request.addAttribute("Framed-Protocol","PPP");
            request.addAttribute("NAS-IP-Address",radiusConfig.getNasip());
            request.addAttribute("Calling-Station-Id",getMacaddr(user));
            request.addAttribute("NAS-Identifier",radiusConfig.getNasid());
            request.addAttribute("NAS-Port-Id", getNasPortId(user));
            System.out.println(request.toString());
            RadiusPacket dmrep = cli.communicate(request,radiusConfig.getAuthport());
            System.out.println(dmrep.toString());
            System.out.println(String.format("cast time: %s ms", System.currentTimeMillis()-start));
            return "done";
        } catch (Exception e) {
            return String.format("send radius auth failure %s", e.getMessage());
        }
    }

    @ShellMethod("send radius accounting, type(start:1 stop:2 update:3)")
    public String sendAcct(String user, int type) {
        try {
            long start = System.currentTimeMillis();
            RadiusClient cli = radiusConfig.getClient();
            AccountingRequest request = new AccountingRequest(user,type);
            request.setUserName(user);
            request.addAttribute("Service-Type","Framed-User");
            request.addAttribute("Framed-Protocol","PPP");
            request.addAttribute("Acct-Session-Id", CoderUtil.md5Encoder(user));
            request.addAttribute("NAS-IP-Address",radiusConfig.getNasip());
            request.addAttribute("Calling-Station-Id",getMacaddr(user));
            request.addAttribute("Called-Station-Id","00-00-00-00-00-00");
            request.addAttribute("NAS-Identifier",radiusConfig.getNasid());
            request.addAttribute("NAS-Port-Id",getNasPortId(user));
            request.addAttribute("Framed-IP-Address",getIpaddr(user));
            request.addAttribute("NAS-Port","0");
            if(type == AccountingRequest.ACCT_STATUS_TYPE_START){
                request.addAttribute("Acct-Input-Octets","0");
                request.addAttribute("Acct-Output-Octets","0");
                request.addAttribute("Acct-Input-Packets","0");
                request.addAttribute("Acct-Output-Packets","0");
                request.addAttribute("Acct-Session-Time","0");
            }else if(type == AccountingRequest.ACCT_STATUS_TYPE_INTERIM_UPDATE){
                request.addAttribute("Acct-Input-Octets","1048576");
                request.addAttribute("Acct-Output-Octets","8388608");
                request.addAttribute("Acct-Input-Packets","1048576");
                request.addAttribute("Acct-Output-Packets","8388608");
                request.addAttribute("Acct-Session-Time","60");
            }else if(type == AccountingRequest.ACCT_STATUS_TYPE_STOP){
                request.addAttribute("Acct-Input-Octets","2097152");
                request.addAttribute("Acct-Output-Octets","16777216");
                request.addAttribute("Acct-Input-Packets","2097152");
                request.addAttribute("Acct-Output-Packets","16777216");
                request.addAttribute("Acct-Session-Time","120");
            }
            System.out.println(request.toString());
            RadiusPacket dmrep = cli.communicate(request,radiusConfig.getAcctport());
            System.out.println(dmrep.toString());
            System.out.println(String.format("cast time: %s ms", System.currentTimeMillis()-start));
            return "done";
        } catch (Exception e) {
            return String.format("send radius accounting failure %s", e.getMessage());
        }
    }

    @ShellMethod("send radius auth and accounting")
    public String sendAll(String user, String pwd) {
        sendAuth(user,pwd);
        sendAcct(user,AccountingRequest.ACCT_STATUS_TYPE_START);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignore) {
        }
        sendAcct(user,AccountingRequest.ACCT_STATUS_TYPE_INTERIM_UPDATE);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignore) {
        }
        sendAcct(user,AccountingRequest.ACCT_STATUS_TYPE_STOP);
        return "";
    }

    public String getIpaddr(String user) {
        if(!ipmap.containsKey(user)){
            ipmap.put(user, RadiusUtil.randIpaddr());
        }
        return ipmap.get(user);
    }


    public String getNasPortId(String user) {
        if(!nasportidmap.containsKey(user)){
            nasportidmap.put(user, RadiusUtil.randIpaddr());
        }
        return nasportidmap.get(user);
    }


    public String getMacaddr(String user) {
        if(!macmap.containsKey(user)){
            macmap.put(user, RadiusUtil.randIpaddr());
        }
        return macmap.get(user);
    }
}
