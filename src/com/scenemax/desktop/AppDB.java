package com.scenemax.desktop;


import java.io.File;
import java.sql.*;

public class AppDB {

    private static int DB_VERSION=1;
    private static AppDB _instance = null;
    private String url = "jdbc:sqlite:data/scenemax3d.db";

    private AppDB() {

        boolean shouldInitDb = DB_VERSION==1 && !new File("data/scenemax3d.db").exists();
        getConnection();
        if(shouldInitDb) {
            initDB();

        } else {
            upgradeDbIfNeeded();
        }
    }

    private void upgradeDbIfNeeded() {
        String dbVer=getParam("db_ver");
        if(dbVer!=null && dbVer.length()>0) {
            int currDbVer = Integer.parseInt(dbVer);

            for(int i=currDbVer;i<DB_VERSION;++i) {
                switch(i) {
                    case 2:
                        break;
                    case 3:
                        break;
                }
            }

            setParam("db_ver",String.valueOf(DB_VERSION));
        }
    }

    public void setParam(String name, String val) {

        String currVal = getParam(name);
        String sql;
        Connection conn = this.getConnection();

        if(currVal==null) {
            sql = "INSERT INTO params (name,value) VALUES(?,?);";
            try (
                PreparedStatement st = conn.prepareStatement(sql)) {
                st.setString(1, name);
                st.setString(2, val);
                st.executeUpdate();

            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        } else {
            sql = "UPDATE params SET value=? WHERE name=?;";
            try (
                PreparedStatement st = conn.prepareStatement(sql)) {
                st.setString(1, val);
                st.setString(2, name);
                st.executeUpdate();

            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }


    }

    public String getParam(String name) {
        String sql = "SELECT value FROM params WHERE name=?";

        try (Connection conn = this.getConnection();
            PreparedStatement pstmt  = conn.prepareStatement(sql)){
            // set the value
            pstmt.setString(1,name);
            //
            ResultSet rs  = pstmt.executeQuery();
            if(rs.next()) {
                String retval = rs.getString("value");
                return retval;
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }

    private void initDB() {
        String sql = "" +
                "CREATE TABLE params (" +
                "name TEXT PRIMARY KEY, " +
                "value TEXT" +
                ")";
        dml(sql);
        setParam("db_ver","1");
    }

    private Connection getConnection() {

        Connection conn = null;
        try {

            // create a connection to the database
            conn = DriverManager.getConnection(url);

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return conn;
    }

    private void dml(String sql) {

        Connection conn = getConnection();

        try (
            Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        }
    }


    public static AppDB getInstance() {

        if(_instance==null) {
            _instance=new AppDB();
        }

        return _instance;
    }


}
