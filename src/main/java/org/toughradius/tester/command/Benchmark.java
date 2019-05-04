package org.toughradius.tester.command;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.tinyradius.attribute.RadiusAttribute;
import org.tinyradius.packet.AccessRequest;
import org.tinyradius.packet.AccountingRequest;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.util.RadiusClient;
import org.tinyradius.util.RadiusUtil;
import org.toughradius.tester.common.CoderUtil;
import org.toughradius.tester.common.DateTimeUtil;
import org.toughradius.tester.config.RadiusConfig;
import org.toughradius.tester.entity.RadiusSession;
import org.toughradius.tester.entity.RadiusUser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@ShellComponent
public class Benchmark {

    private final static Random random = new Random();

    @Autowired
    private RadiusConfig radiusConfig;


    private boolean tester(RadiusUser user) throws Exception {
        RadiusClient cli = radiusConfig.getClient();
        AccessRequest request = new AccessRequest();
        String nasportid = RadiusUtil.randomNasPortId();
        String mac = RadiusUtil.randomMac();
        String stype = "Framed-User";
        request.setUserName(user.getUsername());
        request.setUserPassword(user.getPassword());
        request.setAuthProtocol(radiusConfig.getAuthProtocol());
        request.addAttribute("Service-Type",stype);
        request.addAttribute("Framed-Protocol","PPP");
        request.addAttribute("NAS-IP-Address",radiusConfig.getNasip());
        request.addAttribute("Calling-Station-Id",mac);
        request.addAttribute("NAS-Identifier",radiusConfig.getNasid());
        request.addAttribute("NAS-Port-Id", nasportid);
        RadiusPacket dmrep = cli.communicate(request,radiusConfig.getAuthport());
        if(dmrep.getPacketType()==RadiusPacket.ACCESS_REJECT){
            return false;
        }
        RadiusAttribute ipattr = dmrep.getAttribute("Framed-IP-Address");
        RadiusAttribute stattr = dmrep.getAttribute("Session-Timeout");
        String ipaddr = RadiusUtil.randIpaddr();
        if(ipattr!=null){
            ipaddr = ipattr.getAttributeValue();
        }
        int sessionTimeout = 300;
        if(stattr!=null){
            sessionTimeout = stattr.getIntValue();
        }
        RadiusSession session = new RadiusSession();
        session.setUsername(user.getUsername());
        session.setNasId(radiusConfig.getNasid());
        session.setNasAddr(radiusConfig.getNasip());
        session.setSessionTimeout(sessionTimeout);
        session.setFramedIpaddr(ipaddr);
        session.setFramedNetmask("255.255.255.0");
        session.setMacAddr(mac);
        session.setNasPort(0L);
        session.setNasPortId(nasportid);
        session.setServiceType(0);
        session.setAcctSessionId(CoderUtil.randomUuid());
        session.setAcctSessionTime(0);
        session.setAcctInputTotal(0L);
        session.setAcctOutputTotal(0L);
        session.setAcctInputPackets(0);
        session.setAcctOutputPackets(0);
        session.setAcctStartTime(DateTimeUtil.nowDate());
        AccountingRequest acctrequest = new AccountingRequest(session.getUsername(),AccountingRequest.ACCT_STATUS_TYPE_START);
        acctrequest.setUserName(session.getUsername());
        acctrequest.addAttribute("Service-Type","Framed-User");
        acctrequest.addAttribute("Framed-Protocol","PPP");
        acctrequest.addAttribute("Acct-Session-Id", session.getAcctSessionId());
        acctrequest.addAttribute("NAS-IP-Address",radiusConfig.getNasip());
        acctrequest.addAttribute("Calling-Station-Id",session.getMacAddr());
        acctrequest.addAttribute("Called-Station-Id","00-00-00-00-00-00");
        acctrequest.addAttribute("NAS-Identifier",radiusConfig.getNasid());
        acctrequest.addAttribute("NAS-Port-Id",session.getNasPortId());
        acctrequest.addAttribute("Framed-IP-Address", session.getFramedIpaddr());
        acctrequest.addAttribute("NAS-Port","0");
        acctrequest.addAttribute("Acct-Input-Octets","0");
        acctrequest.addAttribute("Acct-Output-Octets","0");
        acctrequest.addAttribute("Acct-Input-Packets","0");
        acctrequest.addAttribute("Acct-Output-Packets","0");
        acctrequest.addAttribute("Acct-Session-Time","0");
        RadiusPacket acctresp = cli.communicate(request,radiusConfig.getAcctport());
        return acctresp.getPacketType() == AccountingRequest.ACCOUNTING_RESPONSE;
    }

//
//    @ShellMethod("run Benchmark test")
//    public String  bmtest(int total,int pool){
//        System.out.println("\r\nstart Benchmark test...");
//        AtomicInteger authDrop = new AtomicInteger();
//        AtomicInteger authReq = new AtomicInteger();
//        AtomicInteger authAccept = new AtomicInteger();
//        AtomicInteger authRejectt = new AtomicInteger();
//        AtomicInteger acctStart = new AtomicInteger();
//        AtomicInteger acctUpdate = new AtomicInteger();
//        AtomicInteger acctStop = new AtomicInteger();
//        AtomicInteger acctResp = new AtomicInteger();
//        AtomicInteger acctDrop = new AtomicInteger();
//        List<Future> result = new ArrayList<>();
//        ThreadPoolTaskExecutor executor = radiusConfig.getExecutor(total,pool);
//        for(int i=0;i<total;i++){
//            Future ft = executor.submit(()->{
//                RadiusUser user = getandUser();
//                RadiusSession session = null;
//                try {
//                    long start = System.currentTimeMillis();
//                    session = sendAuth(user);
//                    authReq.getAndIncrement();
//                    if(session==null){
//                        authRejectt.getAndIncrement();
//                        return;
//                    }
//                    authAccept.getAndIncrement();
//                } catch (Exception e) {
//                    authDrop.getAndIncrement();
//                    return;
//                }
//
//                try {
//                    long start = System.currentTimeMillis();
//                    session = sendAcct(session,AccountingRequest.ACCT_STATUS_TYPE_START);
//                    acctStart.getAndIncrement();
//                    acctResp.getAndIncrement();
//                } catch (Exception e) {
//                    acctDrop.getAndIncrement();
//                    return;
//                }
//
//                try {
//                    Thread.sleep(random.nextInt(5000));
//                } catch (InterruptedException ignore) {
//                }
//
//                try {
//                    session = sendAcct(session,AccountingRequest.ACCT_STATUS_TYPE_INTERIM_UPDATE);
//                    acctUpdate.getAndIncrement();
//                    acctResp.getAndIncrement();
//                } catch (Exception e) {
//                    acctDrop.getAndIncrement();
//                }
//
//                try {
//                    Thread.sleep(random.nextInt(5000));
//                } catch (InterruptedException ignore) {
//                }
//
//                try {
//                    session = sendAcct(session,AccountingRequest.ACCT_STATUS_TYPE_STOP);
//                    acctStop.getAndIncrement();
//                    acctResp.getAndIncrement();
//                } catch (Exception e) {
//                    acctDrop.getAndIncrement();
//                }
//            });
//            result.add(ft);
//        }
//        executor.shutdown();
//        while(true){
//            try {
//                Thread.sleep(3000);
//            } catch (InterruptedException ignore) {
//            }
//            long done = result.stream().filter(Future::isDone).count();
//            System.out.println(String.format("done task = %s", done));
//
//
//            StringBuffer buff = new StringBuffer();
//            buff.append("\r\n####################################################\r\n");
//            buff.append(String.format("#  Bras Simulation: Total = %s, Concurrent = %s ", total,pool)).append("\r\n");
//            buff.append("#  AccessRequest: ").append(authReq.intValue()).append("\r\n");
//            buff.append("#  AccessAccept: ").append(authAccept.intValue()).append("\r\n");
//            buff.append("#  AccessReject: ").append(authRejectt.intValue()).append("\r\n");
//            buff.append("#  AccessDrop: ").append(authDrop.intValue()).append("\r\n");
//            buff.append("#  AccountingRequest <Start>: ").append(acctStart.intValue()).append("\r\n");
//            buff.append("#  AccountingRequest <Update>: ").append(acctUpdate.intValue()).append("\r\n");
//            buff.append("#  AccountingRequest <Stop>: ").append(acctStop.intValue()).append("\r\n");
//            buff.append("#  AccountingRequest: ").append(acctStart.intValue()+acctUpdate.intValue()+acctStop.intValue()).append("\r\n");
//            buff.append("#  AccountingResponse: ").append(acctResp.intValue()).append("\r\n");
//            buff.append("#  AccountingDrop: ").append(acctDrop.intValue()).append("\r\n");
//            buff.append("#####################################################\r\n");
//            System.out.println(buff.toString());
//            if(done == result.size()){
//                break;
//            }
//        }
//        return "";
//    }

}
