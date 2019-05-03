package org.toughradius.tester.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.tinyradius.util.RadiusClient;

import java.net.SocketException;

import static org.tinyradius.packet.AccessRequest.AUTH_PAP;

@Configuration
public class RadiusConfig {

    private String server = "127.0.0.1";
    private int authport = 1812;
    private int acctport = 1813;
    private int timeout = 5000;
    private int retry = 3;
    private String authProtocol = AUTH_PAP;
    private String userfile = "/etc/jradiustester-user.txt";
    private String secret = "secret";
    private String nasid = "jradiustester";
    private String nasip = "127.0.0.1";

    public RadiusClient getClient() throws SocketException {
        RadiusClient cli = new RadiusClient(getServer(),getSecret());
        cli.setAcctPort(getAcctport());
        cli.setAuthPort(getAuthport());
        cli.setRetryCount(retry);
        cli.setSocketTimeout(timeout);
        cli.setAuthProtocol(getAuthProtocol());
        return cli;
    }

    public ThreadPoolTaskExecutor getExecutor(int queueSize,int poolSize){
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(poolSize);
        taskExecutor.setQueueCapacity(queueSize);
        taskExecutor.setKeepAliveSeconds(60);
        taskExecutor.setThreadNamePrefix("TASK_EXECUTOR");
        taskExecutor.setMaxPoolSize(poolSize);
        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        taskExecutor.initialize();
        return taskExecutor;
    }


    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public int getAuthport() {
        return authport;
    }

    public void setAuthport(int authport) {
        this.authport = authport;
    }

    public int getAcctport() {
        return acctport;
    }

    public void setAcctport(int acctport) {
        this.acctport = acctport;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getRetry() {
        return retry;
    }

    public void setRetry(int retry) {
        this.retry = retry;
    }

    public String getUserfile() {
        return userfile;
    }

    public void setUserfile(String userfile) {
        this.userfile = userfile;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getNasid() {
        return nasid;
    }

    public void setNasid(String nasid) {
        this.nasid = nasid;
    }

    public String getAuthProtocol() {
        return authProtocol;
    }

    public void setAuthProtocol(String authProtocol) {
        this.authProtocol = authProtocol;
    }

    public String getNasip() {
        return nasip;
    }

    public void setNasip(String nasip) {
        this.nasip = nasip;
    }

    @Override
    public String toString() {
        return "RadiusConfig{" +
                "server='" + server + '\'' +
                ", authport=" + authport +
                ", acctport=" + acctport +
                ", timeout=" + timeout +
                ", retry=" + retry +
                ", authProtocol='" + authProtocol + '\'' +
                ", userfile='" + userfile + '\'' +
                ", secret='" + secret + '\'' +
                ", nasid='" + nasid + '\'' +
                ", nasip='" + nasip + '\'' +
                '}';
    }

}
