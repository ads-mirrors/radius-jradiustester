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
import org.tinyradius.util.RadiusException;
import org.tinyradius.util.RadiusUtil;
import org.toughradius.tester.common.CoderUtil;
import org.toughradius.tester.common.DateTimeUtil;
import org.toughradius.tester.common.FileUtil;
import org.toughradius.tester.config.RadiusConfig;
import org.toughradius.tester.entity.RadiusSession;
import org.toughradius.tester.entity.RadiusUser;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@ShellComponent
public class BenchmarkCommand {

    private final static Map<String, RadiusUser> usercache = new HashMap<String,RadiusUser>();

    private final static Random random = new Random();

    @Autowired
    private RadiusConfig radiusConfig;

    private void  reloadUser() throws IOException {
        usercache.clear();
        String userstr = FileUtil.getFileContent(radiusConfig.getUserfile());
        String[] userlines = userstr.split("\r\n");
        Arrays.stream(userlines).forEach(u->{
            String[] attrs = u.split(",");
            usercache.put(attrs[0],new RadiusUser(attrs[0],attrs[1]));
        });
    }

    private RadiusUser getandUser(){
        return usercache.values().stream().findAny().get();
    }



    public RadiusSession sendAuth(RadiusUser user) throws Exception {
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
            System.out.println(dmrep.toLineString());
            return null;
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
        return session;
    }

    private void updateSession(RadiusSession session){
        session.setAcctSessionTime(DateTimeUtil.compareSecond(DateTimeUtil.nowDate(),session.getAcctStartTime()));
        long intotal = session.getAcctInputTotal() + random.nextInt(1048576);
        long outtotal = session.getAcctOutputTotal() + random.nextInt(10485760);
        int intpkts = session.getAcctInputPackets() + random.nextInt(1024);
        int outpkts = session.getAcctOutputPackets() + random.nextInt(10240);
        session.setAcctInputTotal(intotal);
        session.setAcctOutputTotal(outtotal);
        session.setAcctInputPackets(intpkts);
        session.setAcctOutputPackets(outpkts);
    }

    public RadiusSession sendAcct(RadiusSession session, int type) throws Exception {
        RadiusClient cli = radiusConfig.getClient();
        AccountingRequest request = new AccountingRequest(session.getUsername(),type);
        request.setUserName(session.getUsername());
        request.addAttribute("Service-Type","Framed-User");
        request.addAttribute("Framed-Protocol","PPP");
        request.addAttribute("Acct-Session-Id", session.getAcctSessionId());
        request.addAttribute("NAS-IP-Address",radiusConfig.getNasip());
        request.addAttribute("Calling-Station-Id",session.getMacAddr());
        request.addAttribute("Called-Station-Id","00-00-00-00-00-00");
        request.addAttribute("NAS-Identifier",radiusConfig.getNasid());
        request.addAttribute("NAS-Port-Id",session.getNasPortId());
        request.addAttribute("Framed-IP-Address", session.getFramedIpaddr());
        request.addAttribute("NAS-Port","0");
        if(type == AccountingRequest.ACCT_STATUS_TYPE_START){
            request.addAttribute("Acct-Input-Octets","0");
            request.addAttribute("Acct-Output-Octets","0");
            request.addAttribute("Acct-Input-Packets","0");
            request.addAttribute("Acct-Output-Packets","0");
            request.addAttribute("Acct-Session-Time","0");
        }else if(type == AccountingRequest.ACCT_STATUS_TYPE_INTERIM_UPDATE||type == AccountingRequest.ACCT_STATUS_TYPE_STOP){
            updateSession(session);
            request.addAttribute("Acct-Input-Octets",String.valueOf(session.getAcctInputTotal()));
            request.addAttribute("Acct-Output-Octets",String.valueOf(session.getAcctOutputTotal()));
            request.addAttribute("Acct-Input-Packets",String.valueOf(session.getAcctInputPackets()));
            request.addAttribute("Acct-Output-Packets",String.valueOf(session.getAcctOutputPackets()));
            request.addAttribute("Acct-Session-Time",String.valueOf(session.getAcctSessionTime()));
        }
        RadiusPacket dmrep = cli.communicate(request,radiusConfig.getAcctport());
        return session;
    }



    @ShellMethod("run Simulation test")
    public String  simtest(int total,int pool){
        System.out.println("\r\nstart Simulation test...");
        try {
            reloadUser();
        } catch (IOException e) {
            return String.format("reload userfile error %s", e.getMessage());
        }
        AtomicInteger casttotal = new AtomicInteger();
        AtomicInteger authDrop = new AtomicInteger();
        AtomicInteger authReq = new AtomicInteger();
        AtomicInteger authAccept = new AtomicInteger();
        AtomicInteger authRejectt = new AtomicInteger();
        AtomicInteger acctStart = new AtomicInteger();
        AtomicInteger acctUpdate = new AtomicInteger();
        AtomicInteger acctStop = new AtomicInteger();
        AtomicInteger acctResp = new AtomicInteger();
        AtomicInteger acctDrop = new AtomicInteger();
        AtomicInteger sleep = new AtomicInteger();
        List<Future> result = new ArrayList<>();
        ThreadPoolTaskExecutor executor = radiusConfig.getExecutor(total,pool);
        for(int i=0;i<total;i++){
            Future ft = executor.submit(()->{
                RadiusUser user = getandUser();
                RadiusSession session = null;
                try {
                    long start = System.currentTimeMillis();
                    session = sendAuth(user);
                    authReq.getAndIncrement();
                    if(session==null){
                        authRejectt.getAndIncrement();
                        return;
                    }
                    authAccept.getAndIncrement();
                    casttotal.getAndAdd((int) (System.currentTimeMillis()-start));
                } catch (Exception e) {
//                    System.out.println(String.format("send auth failure %s", e.getMessage()));
                    authDrop.getAndIncrement();
                    return;
                }

                try {
                    long start = System.currentTimeMillis();
                    session = sendAcct(session,AccountingRequest.ACCT_STATUS_TYPE_START);
                    acctStart.getAndIncrement();
                    acctResp.getAndIncrement();
                    casttotal.getAndAdd((int) (System.currentTimeMillis()-start));
//                    System.out.println(String.format("%s-%s online!", session.getUsername(),session.getAcctSessionId()));
                } catch (Exception e) {
//                    System.out.println(String.format("send accounting start failure %s", e.getMessage()));
                    acctDrop.getAndIncrement();
                    return;
                }

                try {
                    Thread.sleep(random.nextInt(5000));
                } catch (InterruptedException ignore) {
                }

                try {
                    long start = System.currentTimeMillis();
                    session = sendAcct(session,AccountingRequest.ACCT_STATUS_TYPE_INTERIM_UPDATE);
                    acctUpdate.getAndIncrement();
                    acctResp.getAndIncrement();
                    casttotal.getAndAdd((int) (System.currentTimeMillis()-start));
//                    System.out.println(String.format("%s-%s online update!", session.getUsername(),session.getAcctSessionId()));
                } catch (Exception e) {
                    acctDrop.getAndIncrement();
//                    System.out.println(String.format("send accounting update failure %s", e.getMessage()));
                }

                try {
                    Thread.sleep(random.nextInt(5000));
                } catch (InterruptedException ignore) {
                }

                try {
                    long start = System.currentTimeMillis();
                    session = sendAcct(session,AccountingRequest.ACCT_STATUS_TYPE_STOP);
                    acctStop.getAndIncrement();
                    acctResp.getAndIncrement();
                    casttotal.getAndAdd((int) (System.currentTimeMillis()-start));
//                    System.out.println(String.format("%s-%s offline!", session.getUsername(),session.getAcctSessionId()));
                } catch (Exception e) {
                    acctDrop.getAndIncrement();
//                    System.out.println(String.format("send accounting stop failure %s", e.getMessage()));
                }
            });
            result.add(ft);
        }
        executor.shutdown();
        while(true){
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignore) {
            }
            long done = result.stream().filter(Future::isDone).count();
            System.out.println(String.format("done task = %s", done));

            int cast = casttotal.intValue();

            StringBuffer buff = new StringBuffer();
            buff.append("\r\n####################################################\r\n");
            buff.append(String.format("#  Bras Simulation: Total = %s, Concurrent = %s ", total,pool)).append("\r\n");
            buff.append("#  AccessRequest: ").append(authReq.intValue()).append("\r\n");
            buff.append("#  AccessAccept: ").append(authAccept.intValue()).append("\r\n");
            buff.append("#  AccessReject: ").append(authRejectt.intValue()).append("\r\n");
            buff.append("#  AccessDrop: ").append(authDrop.intValue()).append("\r\n");
            buff.append("#  AccountingRequest <Start>: ").append(acctStart.intValue()).append("\r\n");
            buff.append("#  AccountingRequest <Update>: ").append(acctUpdate.intValue()).append("\r\n");
            buff.append("#  AccountingRequest <Stop>: ").append(acctStop.intValue()).append("\r\n");
            buff.append("#  AccountingRequest: ").append(acctStart.intValue()+acctUpdate.intValue()+acctStop.intValue()).append("\r\n");
            buff.append("#  AccountingResponse: ").append(acctResp.intValue()).append("\r\n");
            buff.append("#  AccountingDrop: ").append(acctDrop.intValue()).append("\r\n");
//            buff.append("#  Current Cast: ").append(cast).append(" ms").append("\r\n");
//            double qps = (acctStart.intValue()+acctUpdate.intValue()+acctStop.intValue()+authReq.intValue()) /cast*1.00;
//            buff.append("#  Current QPS: ").append(qps).append("\r\n");
            buff.append("#####################################################\r\n");
            System.out.println(buff.toString());
            if(done == result.size()){
                break;
            }
        }
        return "";
    }
}
