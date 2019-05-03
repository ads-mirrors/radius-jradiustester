package org.toughradius.tester.entity;

public class RadiusUser {

    private String username;
    private String password;

    public RadiusUser() {
    }

    public RadiusUser(String username, String password) {
        this.username = username.trim();
        this.password = password.trim();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
