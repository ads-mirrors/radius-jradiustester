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
import org.toughradius.tester.common.*;
import org.toughradius.tester.config.RadiusConfig;
import org.toughradius.tester.entity.RadiusSession;
import org.toughradius.tester.entity.RadiusUser;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ShellComponent
public class SimulationCommand {

    private final static Map<String, RadiusUser> usercache = new HashMap<String,RadiusUser>();

    private final static Random random = new Random();

    private final static SpinLock lock = new SpinLock();

    @Autowired
    private RadiusConfig radiusConfig;

    private void  reloadUser() throws IOException {
        usercache.clear();
        File ufile = new File(radiusConfig.getUserfile());
        if(!ufile.exists()){
            FileUtil.writeFile(radiusConfig.getUserfile(),"test01,888888\r\n");
        }
        String userstr = FileUtil.getFileContent(radiusConfig.getUserfile());
        String[] userlines = userstr.split("\n");
        Arrays.stream(userlines).forEach(u->{
            String[] attrs = u.split(",");
            usercache.put(attrs[0],new RadiusUser(attrs[0].trim(),attrs[1].trim()));
        });
    }

    private RadiusUser getRandUser(){
        try{
            lock.lock();
            RadiusUser[] users = usercache.values().toArray(new RadiusUser[]{});
            try{
                Arrays.sort(users);
            }catch(Exception ignore){
            }
            RadiusUser user = users[0];
            user.setHits(user.getHits()+1);
            return user;
        }finally {
            lock.unLock();
        }

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
//            System.out.println(dmrep.toLineString());
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
    public String  simtest(int total,int pool, int uptime, int ttl){
        System.out.println("\r\nstart Simulation test...");
        final RadiusStat stat = new RadiusStat();
        try {
            reloadUser();
        } catch (IOException e) {
            return String.format("reload userfile error %s", e.getMessage());
        }
        List<Future> result = new ArrayList<>();
        ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
        sched.scheduleWithFixedDelay(stat::runStat,5,5, TimeUnit.SECONDS);
        ThreadPoolTaskExecutor executor = radiusConfig.getExecutor(total,pool);
        for(int i=0;i<total;i++){
            Future ft = executor.submit(()->{
                RadiusUser user = getRandUser();
                RadiusSession session = null;
                try {
                    long start = System.currentTimeMillis();
                    session = sendAuth(user);
                    stat.incrAuthReq();
                    if(session==null){
                        stat.incrAuthReject();
                        return;
                    }
                    stat.incrAuthAccept();
                } catch (Exception e) {
                    stat.incrAuthDrop();
                    return;
                }

                try {
                    long start = System.currentTimeMillis();
                    session = sendAcct(session,AccountingRequest.ACCT_STATUS_TYPE_START);
                    stat.incrAcctStart();
                    stat.incrAcctResp();
                } catch (Exception e) {
                    stat.incrAcctDrop();
                    return;
                }

                try {
                    Thread.sleep(uptime*1000);
                } catch (InterruptedException ignore) {
                }

                try {
                    session = sendAcct(session,AccountingRequest.ACCT_STATUS_TYPE_INTERIM_UPDATE);
                    stat.incrAcctUpdate();
                    stat.incrAcctResp();
                } catch (Exception e) {
                    stat.incrAcctDrop();
                }

                try {
                    Thread.sleep(random.nextInt(ttl*1000));
                } catch (InterruptedException ignore) {
                }

                try {
                    session = sendAcct(session,AccountingRequest.ACCT_STATUS_TYPE_STOP);
                    stat.incrAcctStop();
                    stat.incrAcctResp();
                } catch (Exception e) {
                    stat.incrAcctDrop();
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


            StringBuffer buff = new StringBuffer();
            buff.append("\r\n####################################################\r\n");
            buff.append(String.format("#  Bras Simulation: Total = %s, Concurrent = %s ", total,pool)).append("\r\n");
            buff.append("#  AccessRequest: ").append(stat.getAuthReq()).append("\r\n");
            buff.append("#  AccessAccept: ").append(stat.getAuthAccept()).append("\r\n");
            buff.append("#  AccessReject: ").append(stat.getAuthReject()).append("\r\n");
            buff.append("#  AccessDrop: ").append(stat.getAuthDrop()).append("\r\n");
            buff.append("#  AccountingRequest <Start>: ").append(stat.getAcctStart()).append("\r\n");
            buff.append("#  AccountingRequest <Update>: ").append(stat.getAcctUpdate()).append("\r\n");
            buff.append("#  AccountingRequest <Stop>: ").append(stat.getAcctStop()).append("\r\n");
            buff.append("#  AccountingRequest: ").append(stat.getAcctStart()+stat.getAcctUpdate()+stat.getAcctStop()).append("\r\n");
            buff.append("#  AccountingResponse: ").append(stat.getAcctResp()).append("\r\n");
            buff.append("#  AccountingDrop: ").append(stat.getAcctDrop()).append("\r\n");
            buff.append("#  Maximum QPS: ").append(stat.getLastMaxResp()).append("\r\n");
            buff.append("#####################################################\r\n");
            System.out.println(buff.toString());
            if(done == result.size()){
                break;
            }
        }
        sched.shutdown();
        return "";
    }
}
