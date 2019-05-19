package org.toughradius.tester.entity;

public class RadiusUser implements  Comparable{

    private String username;
    private String password;
    private int hits;

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

    public int getHits() {
        return hits;
    }

    public void setHits(int hits) {
        this.hits = hits;
    }

    @Override
    public int compareTo(Object o) {
        RadiusUser u = (RadiusUser)o;
        return this.getHits() - u.getHits();
    }
}
